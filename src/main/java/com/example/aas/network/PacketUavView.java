package com.example.aas.network;

import com.example.aas.uav.UavExitReason;
import com.example.aas.uav.UavViewManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: request to enter / leave the UAV camera. */
public class PacketUavView {
    private final boolean enter;

    public PacketUavView(boolean enter) { this.enter = enter; }

    public static void encode(PacketUavView m, FriendlyByteBuf b) { b.writeBoolean(m.enter); }

    public static PacketUavView decode(FriendlyByteBuf b) { return new PacketUavView(b.readBoolean()); }

    public static void handle(PacketUavView m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p == null) return;
            if (m.enter) UavViewManager.tryEnter(p);
            else UavViewManager.exit(p, UavExitReason.USER);
        });
        ctx.get().setPacketHandled(true);
    }
}
