package com.example.aas.network;

import com.example.aas.world.AASWorldData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class PacketPlaceMapMarker {
    private final int x, z;
    private final String type;

    // Лимиты тактических меток НА ИГРОКА
    private static final int SL_MARKER_LIMIT = 10;
    private static final int FTL_MARKER_LIMIT = 5;

    public PacketPlaceMapMarker(int x, int z, String type) { this.x = x; this.z = z; this.type = type; }
    public static void encode(PacketPlaceMapMarker msg, FriendlyByteBuf buf) { buf.writeInt(msg.x); buf.writeInt(msg.z); buf.writeUtf(msg.type); }
    public static PacketPlaceMapMarker decode(FriendlyByteBuf buf) { return new PacketPlaceMapMarker(buf.readInt(), buf.readInt(), buf.readUtf()); }

    /**
     * Определяет роль игрока: "SL", "FTL" или "NONE".
     */
    private static String getPlayerRole(ServerPlayer player, AASWorldData data) {
        String pName = player.getScoreboardName();
        for (AASWorldData.Squad s : data.squads) {
            if (s.members.contains(pName)) {
                if (s.leader.equals(pName)) return "SL";
                if (s.bravoLeader.equals(pName) || s.charlieLeader.equals(pName)) return "FTL";
                return "NONE";
            }
        }
        return "NONE";
    }

    /**
     * Считает сколько тактических меток поставил конкретный игрок.
     */
    private static int countPlayerMarkers(AASWorldData data, String playerName, long currentTime) {
        int count = 0;
        for (AASWorldData.MapMarker m : data.activeMarkers) {
            if (playerName.equals(m.placedBy) && currentTime < m.expiryTick) {
                count++;
            }
        }
        return count;
    }

    /**
     * Удаляет самую старую метку конкретного игрока.
     */
    private static void removeOldestPlayerMarker(AASWorldData data, String playerName, long currentTime) {
        AASWorldData.MapMarker oldest = null;
        for (AASWorldData.MapMarker m : data.activeMarkers) {
            if (!playerName.equals(m.placedBy)) continue;
            if (currentTime >= m.expiryTick) continue;

            if (oldest == null || m.expiryTick < oldest.expiryTick) {
                oldest = m;
            }
        }
        if (oldest != null) {
            data.activeMarkers.remove(oldest);
        }
    }

    public static void handle(PacketPlaceMapMarker msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || player.getTeam() == null) return;

            ServerLevel level = player.serverLevel();
            AASWorldData data = AASWorldData.get(level);
            String team = player.getTeam().getName().toUpperCase();
            String pName = player.getScoreboardName();
            long currentTime = level.getGameTime();

            if (msg.type.equals("Artillery Request")) {
                // Проверка на SL
                if (!player.getPersistentData().getBoolean("AAS_IsSquadLeader") && !player.isCreative()) return;

                AASWorldData.ArtStrikeRequest activeReq = team.equals("BLUE") ? data.blueArtRequest : data.redArtRequest;
                if (activeReq != null) return;

                BlockPos strikePos = new BlockPos(msg.x, 64, msg.z);
                if (team.equals("BLUE")) data.blueArtRequest = new AASWorldData.ArtStrikeRequest(pName, strikePos);
                else data.redArtRequest = new AASWorldData.ArtStrikeRequest(pName, strikePos);

                data.activeMarkers.add(new AASWorldData.MapMarker(
                        strikePos,
                        "Artillery Request",
                        team,
                        level.getGameTime() + 3600,
                        pName
                ));

                int cmdId = team.equals("BLUE") ? data.blueCMDId : data.redCMDId;
                for (ServerPlayer p : level.players()) {
                    if (p.getTeam() != null && p.getTeam().getName().toUpperCase().equals(team)) {
                        int pSqId = p.getPersistentData().getInt("AAS_SquadID");
                        if (p.getPersistentData().getBoolean("AAS_IsSquadLeader") || (pSqId == cmdId && cmdId != -1)) {
                            p.sendSystemMessage(Component.translatable("aas.msg.artillery_requested", msg.x, msg.z).withStyle(ChatFormatting.GOLD));
                        }
                    }
                }
                data.setDirty();
                PacketHandler.sendToAllClients(level, data);
            } else {
                // === ОБЫЧНЫЕ ТАКТИЧЕСКИЕ МАРКЕРЫ ===

                // Определяем роль игрока
                String role = getPlayerRole(player, data);

                // Только SL и FTL могут ставить тактические метки
                if (role.equals("NONE")) return;

                // Определяем лимит для этого игрока
                int limit = role.equals("SL") ? SL_MARKER_LIMIT : FTL_MARKER_LIMIT;

                // Считаем сколько меток уже поставил ЭТОТ игрок
                int currentCount = countPlayerMarkers(data, pName, currentTime);

                // Если лимит достигнут — удаляем самую старую метку ЭТОГО игрока
                if (currentCount >= limit) {
                    removeOldestPlayerMarker(data, pName, currentTime);
                }

                // Добавляем новый маркер
                data.activeMarkers.add(new AASWorldData.MapMarker(
                        new BlockPos(msg.x, 64, msg.z),
                        msg.type,
                        team,
                        level.getGameTime() + 3600,
                        pName
                ));

                data.setDirty();
                PacketHandler.sendToAllClients(level, data);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}