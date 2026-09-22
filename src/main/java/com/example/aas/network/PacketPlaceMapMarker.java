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

    public PacketPlaceMapMarker(int x, int z, String type) { this.x = x; this.z = z; this.type = type; }
    public static void encode(PacketPlaceMapMarker msg, FriendlyByteBuf buf) { buf.writeInt(msg.x); buf.writeInt(msg.z); buf.writeUtf(msg.type); }
    public static PacketPlaceMapMarker decode(FriendlyByteBuf buf) { return new PacketPlaceMapMarker(buf.readInt(), buf.readInt(), buf.readUtf()); }

    public static void handle(PacketPlaceMapMarker msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || player.getTeam() == null) return;

            ServerLevel level = player.serverLevel();
            AASWorldData data = AASWorldData.get(level);
            String team = player.getTeam().getName().toUpperCase();
            String pName = player.getScoreboardName();

            if (msg.type.equals("Artillery Request")) {
                // Проверка на SL
                if (!player.getPersistentData().getBoolean("AAS_IsSquadLeader") && !player.isCreative()) return;

                AASWorldData.ArtStrikeRequest activeReq = team.equals("BLUE") ? data.blueArtRequest : data.redArtRequest;
                if (activeReq != null) return;

                BlockPos strikePos = new BlockPos(msg.x, 64, msg.z);
                if (team.equals("BLUE")) data.blueArtRequest = new AASWorldData.ArtStrikeRequest(pName, strikePos);
                else data.redArtRequest = new AASWorldData.ArtStrikeRequest(pName, strikePos);

                // Добавляем маркер на карту (на 10 секунд, пока висит запрос)
                data.activeMarkers.add(new AASWorldData.MapMarker(
                        strikePos,
                        "Artillery Request",
                        team,
                        level.getGameTime() + 3600
                ));

                // Сообщение только для CMD и SL
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
                // Обычные маркеры
                data.activeMarkers.add(new AASWorldData.MapMarker(new BlockPos(msg.x, 64, msg.z), msg.type, team, level.getGameTime() + 3600));
                data.setDirty();
                PacketHandler.sendToAllClients(level, data);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}