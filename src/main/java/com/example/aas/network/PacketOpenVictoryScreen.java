// PATH: src\main\java\com\example\aas\network\PacketOpenVictoryScreen.java
package com.example.aas.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class PacketOpenVictoryScreen {
    public final VictoryData data;

    public PacketOpenVictoryScreen(VictoryData data) {
        this.data = data;
    }

    public static void encode(PacketOpenVictoryScreen msg, FriendlyByteBuf buf) {
        msg.data.encode(buf);
    }

    public static PacketOpenVictoryScreen decode(FriendlyByteBuf buf) {
        return new PacketOpenVictoryScreen(VictoryData.decode(buf));
    }

    public static void handle(PacketOpenVictoryScreen msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // ИСПОЛЬЗУЕМ ПРОСЛОЙКУ ClientHooks
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                com.example.aas.client.ClientHooks.openVictoryScreen(msg.data);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
