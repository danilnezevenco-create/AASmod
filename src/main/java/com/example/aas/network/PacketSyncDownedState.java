package com.example.aas.network;

import com.example.aas.client.ClientHooks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class PacketSyncDownedState {
    private final int entityId;
    private final boolean isDowned;

    public PacketSyncDownedState(int id, boolean downed) {
        this.entityId = id;
        this.isDowned = downed;
    }

    public static void encode(PacketSyncDownedState msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
        buf.writeBoolean(msg.isDowned);
    }

    public static PacketSyncDownedState decode(FriendlyByteBuf buf) {
        return new PacketSyncDownedState(buf.readInt(), buf.readBoolean());
    }

    public static void handle(PacketSyncDownedState msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // МЫ ИСПОЛЬЗУЕМ DistExecutor, чтобы код выполнился ТОЛЬКО на клиенте
            // и вызываем метод из ClientHooks
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.handleDownedState(msg.entityId, msg.isDowned));
        });
        ctx.get().setPacketHandled(true);
    }
}