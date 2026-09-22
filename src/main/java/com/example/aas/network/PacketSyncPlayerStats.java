package com.example.aas.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class PacketSyncPlayerStats {
    public final List<PlayerStatInfo> entries;

    public PacketSyncPlayerStats(List<PlayerStatInfo> entries) {
        this.entries = entries;
    }

    public static void encode(PacketSyncPlayerStats msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entries.size());
        for (PlayerStatInfo e : msg.entries) e.encode(buf);
    }

    public static PacketSyncPlayerStats decode(FriendlyByteBuf buf) {
        int count = buf.readInt();
        List<PlayerStatInfo> list = new ArrayList<>();
        for (int i = 0; i < count; i++) list.add(PlayerStatInfo.decode(buf));
        return new PacketSyncPlayerStats(list);
    }

    public static void handle(PacketSyncPlayerStats msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> com.example.aas.client.ClientData.playerStats = msg.entries);
        ctx.get().setPacketHandled(true);
    }
}