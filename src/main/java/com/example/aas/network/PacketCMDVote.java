package com.example.aas.network;

import com.example.aas.world.AASWorldData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;
import java.util.UUID;

public class PacketCMDVote {
    private final boolean agree;
    public PacketCMDVote(boolean agree) { this.agree = agree; }
    public static void encode(PacketCMDVote msg, FriendlyByteBuf buf) { buf.writeBoolean(msg.agree); }
    public static PacketCMDVote decode(FriendlyByteBuf buf) { return new PacketCMDVote(buf.readBoolean()); }

    public static void handle(PacketCMDVote msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || player.getTeam() == null) return;

            AASWorldData data = AASWorldData.get(player.serverLevel());
            String team = player.getTeam().getName().toUpperCase();
            boolean isBlue = team.equals("BLUE");

            // Только лидеры других отрядов могут голосовать
            if (!player.getPersistentData().getBoolean("AAS_IsSquadLeader")) return;

            if (isBlue) {
                // Если я Синий и идет голосование за Синего CMD
                if (data.blueCmdVoteActive && !player.getScoreboardName().equals(data.blueCmdCandidateName)) {
                    data.blueCmdVotes.put(player.getUUID(), msg.agree);
                }
            } else {
                // Если я Красный и идет голосование за Красного CMD
                if (data.redCmdVoteActive && !player.getScoreboardName().equals(data.redCmdCandidateName)) {
                    data.redCmdVotes.put(player.getUUID(), msg.agree);
                }
            }

            data.setDirty();
            PacketHandler.sendToAllClients(player.serverLevel(), data);
        });
        ctx.get().setPacketHandled(true);
    }
}