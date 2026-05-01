// В файле PacketRecoil.java
package com.example.aas.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class PacketRecoil {
    private final float pitch;
    private final float yaw;

    public PacketRecoil(float pitch, float yaw) {
        this.pitch = pitch;
        this.yaw = yaw;
    }

    public static void encode(PacketRecoil msg, FriendlyByteBuf buf) {
        buf.writeFloat(msg.pitch);
        buf.writeFloat(msg.yaw);
    }

    public static PacketRecoil decode(FriendlyByteBuf buf) {
        return new PacketRecoil(buf.readFloat(), buf.readFloat());
    }

    public static void handle(PacketRecoil msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // ИСПОЛЬЗУЕМ КЛАСС-ПРОСЛОЙКУ (ClientHooks), а не прямой вызов
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.example.aas.client.ClientHooks.handleRecoil(msg.pitch, msg.yaw));
        });
        ctx.get().setPacketHandled(true);
    }
}