package com.example.aas.network;

import com.example.aas.uav.UavManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: launch the UAV over a circle. The server re-validates everything. */
public class PacketUavLaunch {
    private final double cx, cz;
    private final int radius;

    public PacketUavLaunch(double cx, double cz, int radius) { this.cx = cx; this.cz = cz; this.radius = radius; }

    public static void encode(PacketUavLaunch m, FriendlyByteBuf b) { b.writeDouble(m.cx); b.writeDouble(m.cz); b.writeVarInt(m.radius); }

    public static PacketUavLaunch decode(FriendlyByteBuf b) { return new PacketUavLaunch(b.readDouble(), b.readDouble(), b.readVarInt()); }

    public static void handle(PacketUavLaunch m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p != null) UavManager.tryLaunch(p, m.cx, m.cz, m.radius);
        });
        ctx.get().setPacketHandled(true);
    }
}
