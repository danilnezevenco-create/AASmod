// PATH: src/main/java/com/example/aas/network/PacketReviveProgress.java
package com.example.aas.network;

import com.example.aas.client.ClientHooks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Сервер -> клиент: прогресс подъёма раненого (0.0 - 1.0).
 * progress = -1f означает "спрятать полоску" (подъём отменён/закончен/сорвался).
 * isBeingRevived = true  -> получатель лежит и его поднимают (полоска "Вас поднимают")
 * isBeingRevived = false -> получатель сам поднимает союзника (полоска "Поднимаем...")
 */
public class PacketReviveProgress {
    private final float progress;
    private final boolean isBeingRevived;

    public PacketReviveProgress(float progress, boolean isBeingRevived) {
        this.progress = progress;
        this.isBeingRevived = isBeingRevived;
    }

    public static void encode(PacketReviveProgress msg, FriendlyByteBuf buf) {
        buf.writeFloat(msg.progress);
        buf.writeBoolean(msg.isBeingRevived);
    }

    public static PacketReviveProgress decode(FriendlyByteBuf buf) {
        return new PacketReviveProgress(buf.readFloat(), buf.readBoolean());
    }

    public static void handle(PacketReviveProgress msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                        ClientHooks.handleReviveProgress(msg.progress, msg.isBeingRevived)));
        ctx.get().setPacketHandled(true);
    }
}