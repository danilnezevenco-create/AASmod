package com.example.aas.network;

import com.example.aas.world.AASWorldData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class PacketConfirmArtStrike {
    private final boolean accept;

    public PacketConfirmArtStrike(boolean accept) { this.accept = accept; }
    public static void encode(PacketConfirmArtStrike msg, FriendlyByteBuf buf) { buf.writeBoolean(msg.accept); }
    public static PacketConfirmArtStrike decode(FriendlyByteBuf buf) { return new PacketConfirmArtStrike(buf.readBoolean()); }

    public static void handle(PacketConfirmArtStrike msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ServerLevel level = player.serverLevel();
            AASWorldData data = AASWorldData.get(level);
            String team = player.getTeam() != null ? player.getTeam().getName().toUpperCase() : "";

            // ПРОВЕРКА: Только Лидер (SL) отряда, который является Командиром (CMD)
            int mySquadId = player.getPersistentData().getInt("AAS_SquadID");
            boolean isSL = player.getPersistentData().getBoolean("AAS_IsSquadLeader");
            int teamCmdId = team.equals("BLUE") ? data.blueCMDId : data.redCMDId;

            if (mySquadId != teamCmdId || teamCmdId == -1 || !isSL) return;

            AASWorldData.ArtStrikeRequest request = team.equals("BLUE") ? data.blueArtRequest : data.redArtRequest;

            if (request != null) {
                if (msg.accept) {
                    data.activeStrikes.add(new AASWorldData.ActiveStrike(request.pos, team));
                    level.getServer().getPlayerList().broadcastSystemMessage(
                            Component.literal("STRATEGIC: Artillery Strike Confirmed by Commander!").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), false
                    );
                }
                // Удаляем запрос и маркер с карты после решения
                if (team.equals("BLUE")) data.blueArtRequest = null;
                else data.redArtRequest = null;

                data.activeMarkers.removeIf(m -> m.type.equals("Artillery Request") && m.team.equals(team));

                data.setDirty();
                PacketHandler.sendToAllClients(level, data);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}