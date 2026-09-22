package com.example.aas.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import com.example.aas.world.AASWorldData;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class PacketDeleteMarker {
    private final boolean isTactical;

    // Поля для меток отряда (SQUAD)
    private final int squadId;
    private final String list; // "SL", "BRAVO", "CHARLIE"
    private final UUID markerId;

    // Поля для тактических меток (TEAM/ENEMY)
    private final BlockPos pos;
    private final String type;

    // Конструктор для меток отряда
    public PacketDeleteMarker(int squadId, String list, UUID markerId) {
        this.isTactical = false;
        this.squadId = squadId;
        this.list = list;
        this.markerId = markerId;
        this.pos = null;
        this.type = "";
    }

    // Конструктор для тактических меток
    public PacketDeleteMarker(BlockPos pos, String type) {
        this.isTactical = true;
        this.squadId = -1;
        this.list = "";
        this.markerId = null;
        this.pos = pos;
        this.type = type;
    }

    public static void encode(PacketDeleteMarker msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.isTactical);
        if (msg.isTactical) {
            buf.writeBlockPos(msg.pos);
            buf.writeUtf(msg.type);
        } else {
            buf.writeInt(msg.squadId);
            buf.writeUtf(msg.list);
            buf.writeUUID(msg.markerId);
        }
    }

    public static PacketDeleteMarker decode(FriendlyByteBuf buf) {
        boolean tactical = buf.readBoolean();
        if (tactical) {
            return new PacketDeleteMarker(buf.readBlockPos(), buf.readUtf());
        } else {
            return new PacketDeleteMarker(buf.readInt(), buf.readUtf(), buf.readUUID());
        }
    }

    public static void handle(PacketDeleteMarker msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ServerLevel level = player.serverLevel();
            AASWorldData data = AASWorldData.get(level);
            String pName = player.getScoreboardName();
            boolean changed = false;

            // 1. Определяем роль игрока и его отряд на сервере (строго по нику)
            boolean isSL = false;
            boolean isFTL = false;
            int mySquadId = -1;

            for (AASWorldData.Squad s : data.squads) {
                if (s.leader.equals(pName)) {
                    isSL = true;
                    mySquadId = s.id;
                    break;
                } else if (s.bravoLeader.equals(pName) || s.charlieLeader.equals(pName)) {
                    isFTL = true;
                    mySquadId = s.id;
                    break;
                }
            }

            // Если игрок не лидер, прерываем выполнение
            if (!isSL && !isFTL) return;

            if (msg.isTactical) {
                // ТАКТИЧЕСКИЕ МЕТКИ: удалять могут и SL, и FTL своей команды
                if (isSL || isFTL) {
                    String playerTeam = player.getTeam() != null ? player.getTeam().getName() : "NEUTRAL";
                    changed = data.activeMarkers.removeIf(m ->
                            m.pos.equals(msg.pos) &&
                                    m.type.equals(msg.type) &&
                                    m.team.equalsIgnoreCase(playerTeam)
                    );
                }
            } else {
                // МЕТКИ ОТРЯДА
                for (AASWorldData.Squad s : data.squads) {
                    if (s.id != msg.squadId) continue;

                    // Имеет ли игрок право удалять метки ЭТОГО конкретного отряда?
                    // SL может удалять метки ЛЮБОГО отряда своей команды.
                    // FTL может удалять метки ТОЛЬКО своего отряда (mySquadId == s.id).
                    boolean canDeleteThisSquad = isSL || (isFTL && mySquadId == s.id);

                    // Дополнительная защита: SL может удалять метки только СВОЕЙ команды
                    if (isSL) {
                        String playerTeam = player.getTeam() != null ? player.getTeam().getName() : "NEUTRAL";
                        if (!s.team.equalsIgnoreCase(playerTeam)) {
                            continue; // SL не может трогать метки вражеской команды
                        }
                    }

                    if (!canDeleteThisSquad) continue;

                    boolean removed = false;
                    switch (msg.list) {
                        case "SL":
                            if (s.marker != null && s.marker.id.equals(msg.markerId)) {
                                s.marker = null;
                                removed = true;
                            }
                            if (!removed) removed = removeById(s.rhombusMarkers, msg.markerId);
                            break;
                        case "BRAVO":
                            if (s.bravoMarker != null && s.bravoMarker.id.equals(msg.markerId)) {
                                s.bravoMarker = null;
                                removed = true;
                            }
                            if (!removed) removed = removeById(s.bravoRhombusMarkers, msg.markerId);
                            break;
                        case "CHARLIE":
                            if (s.charlieMarker != null && s.charlieMarker.id.equals(msg.markerId)) {
                                s.charlieMarker = null;
                                removed = true;
                            }
                            if (!removed) removed = removeById(s.charlieRhombusMarkers, msg.markerId);
                            break;
                    }

                    if (removed) {
                        changed = true;
                        break;
                    }
                }
            }

            // Если что-то удалили, сохраняем и синхронизируем
            if (changed) {
                data.setDirty();
                if (msg.isTactical) {
                    PacketHandler.sendToAllClients(level, data);
                } else {
                    PacketHandler.INSTANCE.send(PacketDistributor.DIMENSION.with(level::dimension),
                            new PacketSyncSquads(data.squads));
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    // Вспомогательный метод для безопасного удаления по UUID
    private static boolean removeById(List<AASWorldData.SquadMarker> list, UUID id) {
        if (list == null || id == null) return false;
        return list.removeIf(m -> m.id != null && m.id.equals(id));
    }
}