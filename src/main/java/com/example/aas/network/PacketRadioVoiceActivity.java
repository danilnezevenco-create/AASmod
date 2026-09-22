package com.example.aas.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class PacketRadioVoiceActivity {
    private final String playerName;

    public PacketRadioVoiceActivity(String playerName) {
        this.playerName = playerName;
    }

    public static void encode(PacketRadioVoiceActivity msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.playerName);
    }

    public static PacketRadioVoiceActivity decode(FriendlyByteBuf buf) {
        return new PacketRadioVoiceActivity(buf.readUtf());
    }

    public static void handle(PacketRadioVoiceActivity msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                // Безопасный вызов клиентского кода через ширму ClientHooks
                com.example.aas.client.ClientHooks.handleRadioVoiceActivity(msg.playerName);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}