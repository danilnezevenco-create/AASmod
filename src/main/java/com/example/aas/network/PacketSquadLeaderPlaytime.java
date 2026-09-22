package com.example.aas.network;

import com.example.aas.client.ClientData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketSquadLeaderPlaytime {
    private final String leaderName;
    private final long hours;
    private final boolean visible;

    public PacketSquadLeaderPlaytime(String leaderName, long hours, boolean visible) {
        this.leaderName = leaderName;
        this.hours = hours;
        this.visible = visible;
    }

    public static void encode(PacketSquadLeaderPlaytime msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.leaderName);
        buf.writeLong(msg.hours);
        buf.writeBoolean(msg.visible);
    }

    public static PacketSquadLeaderPlaytime decode(FriendlyByteBuf buf) {
        return new PacketSquadLeaderPlaytime(buf.readUtf(), buf.readLong(), buf.readBoolean());
    }

    public static void handle(PacketSquadLeaderPlaytime msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientData.squadLeaderPlaytimeName = msg.leaderName;
            ClientData.squadLeaderPlaytimeHours = msg.hours;
            ClientData.showSquadLeaderPlaytime = msg.visible && msg.hours >= 0;
            if (ClientData.showSquadLeaderPlaytime) {
                ClientData.squadLeaderPlaytimeShownAt = System.currentTimeMillis();
            }
        });
        ctx.get().setPacketHandled(true);
    }
}