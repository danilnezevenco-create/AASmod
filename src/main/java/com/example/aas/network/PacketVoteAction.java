package com.example.aas.network;

import com.example.aas.world.AASWorldData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketVoteAction {
    private final boolean agree; // true = F1, false = F2

    public PacketVoteAction(boolean agree) { this.agree = agree; }
    public static void encode(PacketVoteAction msg, FriendlyByteBuf buf) { buf.writeBoolean(msg.agree); }
    public static PacketVoteAction decode(FriendlyByteBuf buf) { return new PacketVoteAction(buf.readBoolean()); }

    public static void handle(PacketVoteAction msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || player.getTeam() == null) return;

            AASWorldData data = AASWorldData.get(player.serverLevel());
            if (!data.voteActive) return;

            data.votes.put(player.getUUID(), msg.agree);
            data.setDirty();

            // Сразу синхронизируем данные для всех
            PacketHandler.sendToAllClients(player.serverLevel(), data);
        });
        ctx.get().setPacketHandled(true);
    }
}