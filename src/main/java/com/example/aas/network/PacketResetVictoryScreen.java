package com.example.aas.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/** /aas resetvictoryscreen: сбрасывает у клиентов сохранённый экран победы. */
public class PacketResetVictoryScreen {
    public PacketResetVictoryScreen() {}

    public static void encode(PacketResetVictoryScreen msg, FriendlyByteBuf buf) {}

    public static PacketResetVictoryScreen decode(FriendlyByteBuf buf) {
        return new PacketResetVictoryScreen();
    }

    public static void handle(PacketResetVictoryScreen msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            com.example.aas.client.ClientHooks.resetVictoryScreen();
        }));
        ctx.get().setPacketHandled(true);
    }
}
