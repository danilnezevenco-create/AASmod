// FILE: PacketSyncDragState.java
// PATH: src/main/java/com/example/aas/network/PacketSyncDragState.java
package com.example.aas.network;

import com.example.aas.client.ClientHooks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketSyncDragState {
    private final boolean isDragging;

    public PacketSyncDragState(boolean isDragging) {
        this.isDragging = isDragging;
    }

    public static void encode(PacketSyncDragState msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.isDragging);
    }

    public static PacketSyncDragState decode(FriendlyByteBuf buf) {
        return new PacketSyncDragState(buf.readBoolean());
    }

    public static void handle(PacketSyncDragState msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        ClientHooks.handleDragState(msg.isDragging)));
        ctx.get().setPacketHandled(true);
    }
}