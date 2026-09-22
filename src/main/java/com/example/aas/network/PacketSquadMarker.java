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

    public PacketSquadMarker(int x, int z, int type) {
        this.x = x; this.z = z; this.type = type;
    }

    public static void encode(PacketSquadMarker msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.x); buf.writeInt(msg.z); buf.writeInt(msg.type);
    }

    public static PacketSquadMarker decode(FriendlyByteBuf buf) {
        return new PacketSquadMarker(buf.readInt(), buf.readInt(), buf.readInt());
    }

    // Добавляет новую свободную (ромбовидную) метку в список, соблюдая лимит:
    // если лимит превышен - удаляется самая старая метка (FIFO), как и просил пользователь.
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
                        // Ромбики (Тип 6) - свободные метки, лимит SL_MARKER_LIMIT (10), старые заменяются новыми (FIFO)
                        if (msg.type == 6) {
                            addRhombusMarker(s.rhombusMarkers, AASWorldData.Squad.SL_MARKER_LIMIT, msg, expiry);
                        }
                        // Обычные метки (Move, Attack, Defend, Build)
                        else {
                            s.marker = new AASWorldData.SquadMarker(msg.x, 64, msg.z, msg.type, expiry, false);
                        }
                    }

                    // 2. ЛОГИКА ДЛЯ ФАЕРТИМ ЛИДЕРА БРАВО
                    else if (s.bravoLeader.equals(pName)) {
                        // ФАЕРТИМ НЕ МОЖЕТ СТАВИТЬ РОМБИКИ (type == 6), это может делать только SL!
                        if (msg.type == 6) return;

                        s.bravoMarker = new AASWorldData.SquadMarker(msg.x, 64, msg.z, msg.type, expiry, false);
                    }

                    // 3. ЛОГИКА ДЛЯ ФАЕРТИМ ЛИДЕРА ЧАРЛИ
                    else if (s.charlieLeader.equals(pName)) {
                        // ФАЕРТИМ НЕ МОЖЕТ СТАВИТЬ РОМБИКИ (type == 6), это может делать только SL!
                        if (msg.type == 6) return;

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
                    break;
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
