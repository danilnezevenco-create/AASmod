package com.example.aas.network;

import com.example.aas.client.uav.UavViewClient;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client (authoritative): the player is now / no longer looking through the UAV. */
public class PacketUavViewState {
    private final boolean active;
    private final int reason;

    public PacketUavViewState(boolean active, int reason) { this.active = active; this.reason = reason; }

    public static void encode(PacketUavViewState m, FriendlyByteBuf b) { b.writeBoolean(m.active); b.writeVarInt(m.reason); }

    public static PacketUavViewState decode(FriendlyByteBuf b) { return new PacketUavViewState(b.readBoolean(), b.readVarInt()); }

    public static void handle(PacketUavViewState m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> UavViewClient.onServerState(m.active, m.reason)));
        ctx.get().setPacketHandled(true);
    }
}
