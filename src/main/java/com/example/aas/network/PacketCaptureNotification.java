package com.example.aas.network;

import com.example.aas.client.ClientData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class PacketCaptureNotification {
    private final String pointName;
    private final String team;
    private final boolean neutralized;

    public PacketCaptureNotification(String name, String team, boolean neut) {
        this.pointName = name;
        this.team = team;
        this.neutralized = neut;
    }

    public static void encode(PacketCaptureNotification msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.pointName);
        buf.writeUtf(msg.team);
        buf.writeBoolean(msg.neutralized);
    }

    public static PacketCaptureNotification decode(FriendlyByteBuf buf) {
        return new PacketCaptureNotification(buf.readUtf(), buf.readUtf(), buf.readBoolean());
    }

    public static void handle(PacketCaptureNotification msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ClientData.captureNotifications.add(new ClientData.CaptureNotification(msg.pointName, msg.team, msg.neutralized));
        });
        ctx.get().setPacketHandled(true);
    }
}