// PATH: src/main/java/com/example/aas/network/PacketSquadMarker.java
package com.example.aas.network;

import com.example.aas.world.AASWorldData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import java.util.List;
import java.util.function.Supplier;

public class PacketSquadMarker {
    private final int x, z, type;
    private final int endX, endZ;
    // Для какой группы ставит метку SL: 0 = отряд, 1 = Bravo, 2 = Charlie.
    // Учитывается только для типов 0..3 (Move/Attack/Defend/Build) и только если отправитель — SL.
    public static final int GROUP_SQUAD = 0, GROUP_BRAVO = 1, GROUP_CHARLIE = 2;
    private final int group;   // конец стрелки (только для type == 7)

    // Старый конструктор: обычные метки и ромбики
    public PacketSquadMarker(int x, int z, int type) {
        this(x, z, type, 0, 0);     // FIX: инициализируем final-поля
    }

    // Новый конструктор: стрелка отряда (type == 7)
    public PacketSquadMarker(int x, int z, int type, int endX, int endZ) {
        this(x, z, type, endX, endZ, GROUP_SQUAD);
    }

    // SL ставит обычную метку (0..3) от имени группы: 0 = отряд, 1 = Bravo, 2 = Charlie
    public PacketSquadMarker(int x, int z, int type, int group) {
        this(x, z, type, 0, 0, group);
    }

    private PacketSquadMarker(int x, int z, int type, int endX, int endZ, int group) {
        this.x = x; this.z = z; this.type = type;
        this.endX = endX; this.endZ = endZ;
        this.group = group;
    }

    public static void encode(PacketSquadMarker msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.x); buf.writeInt(msg.z); buf.writeInt(msg.type);
        buf.writeInt(msg.endX); buf.writeInt(msg.endZ);
        buf.writeInt(msg.group);
    }

    public static PacketSquadMarker decode(FriendlyByteBuf buf) {
        return new PacketSquadMarker(buf.readInt(), buf.readInt(), buf.readInt(),
                buf.readInt(), buf.readInt(), buf.readInt());
    }

    // Добавляет стрелку отряда в список (FIFO, как у ромбиков)
    private static void addArrowMarker(List<AASWorldData.SquadMarker> list, int limit,
                                       PacketSquadMarker msg, long expiry) {
        if (msg.x == msg.endX && msg.z == msg.endZ) return;      // нулевая стрелка не нужна
        while (list.size() >= limit) {
            list.remove(0);
        }
        list.add(AASWorldData.SquadMarker.arrow(msg.x, msg.z, msg.endX, msg.endZ, expiry));
    }

    // Добавляет новую свободную (ромбовидную) метку в список, соблюдая лимит:
    // если лимит превышен - удаляется самая старая метка (FIFO)
    private static void addRhombusMarker(List<AASWorldData.SquadMarker> list, int limit, PacketSquadMarker msg, long expiry) {
        while (list.size() >= limit) {
            list.remove(0); // самая старая метка всегда в начале списка
        }
        list.add(new AASWorldData.SquadMarker(msg.x, 64, msg.z, 6, expiry, false));
    }

    public static void handle(PacketSquadMarker msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ServerLevel level = player.serverLevel();
            AASWorldData data = AASWorldData.get(level);
            long expiry = level.getGameTime() + 6000; // 5 минут жизни
            String pName = player.getScoreboardName();

            for (AASWorldData.Squad s : data.squads) {
                if (s.members.contains(pName)) {

                    // 1. ЛОГИКА ДЛЯ СКВАД ЛИДЕРА
                    if (s.leader.equals(pName)) {
                        // Ромбики (тип 6) - лимит SL_MARKER_LIMIT, старые заменяются новыми (FIFO)
                        if (msg.type == 6) {
                            addRhombusMarker(s.rhombusMarkers, AASWorldData.Squad.SL_MARKER_LIMIT, msg, expiry);
                        }
                        // Стрелка отряда (тип 7) — только SL
                        else if (msg.type == 7) {
                            addArrowMarker(s.rhombusMarkers, AASWorldData.Squad.SL_MARKER_LIMIT, msg, expiry);
                        }
                        // Обычные метки (Move, Attack, Defend, Build)
                        else {
                            if (msg.type < 0 || msg.type > 3) return;
                            AASWorldData.SquadMarker m =
                                    new AASWorldData.SquadMarker(msg.x, 64, msg.z, msg.type, expiry, false);
                            // SL ставит метку вместо фаертима: перезаписывает его метку
                            // (у каждого фаертима она одна), иконки Bravo/Charlie подхватятся сами.
                            if (msg.group == GROUP_BRAVO) {
                                s.bravoMarker = m;
                            } else if (msg.group == GROUP_CHARLIE) {
                                s.charlieMarker = m;
                            } else {
                                s.marker = m;
                            }
                        }
                    }

                    // 2. ЛОГИКА ДЛЯ ФАЕРТИМ ЛИДЕРА БРАВО
                    else if (s.bravoLeader.equals(pName)) {
                        // FTL не может ставить ромбики (6) и стрелку отряда (7) — только SL
                        if (msg.type == 6 || msg.type == 7) return;

                        s.bravoMarker = new AASWorldData.SquadMarker(msg.x, 64, msg.z, msg.type, expiry, false);
                    }

                    // 3. ЛОГИКА ДЛЯ ФАЕРТИМ ЛИДЕРА ЧАРЛИ
                    else if (s.charlieLeader.equals(pName)) {
                        // FIX: здесь тоже нужно запретить тип 7
                        if (msg.type == 6 || msg.type == 7) return;

                        s.charlieMarker = new AASWorldData.SquadMarker(msg.x, 64, msg.z, msg.type, expiry, false);
                    }

                    // 4. Обычные участники отряда
                    else {
                        break;
                    }

                    data.setDirty();
                    // Рассылаем обновление всем игрокам в этом мире
                    PacketHandler.INSTANCE.send(PacketDistributor.DIMENSION.with(level::dimension),
                            new PacketSyncSquads(data.squads));
                    PacketHandler.playMarkerSoundForSquad(level, s.members, player);
                    break;
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}