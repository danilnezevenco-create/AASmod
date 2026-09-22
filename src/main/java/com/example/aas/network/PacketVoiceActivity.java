package com.example.aas.network;

import com.example.aas.client.ClientData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class PacketVoiceActivity {
    private final String playerName;

    public PacketVoiceActivity(String playerName) {
        this.playerName = playerName;
    }

    public static void encode(PacketVoiceActivity msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.playerName);
    }

    public static PacketVoiceActivity decode(FriendlyByteBuf buf) {
        return new PacketVoiceActivity(buf.readUtf());
    }

    public static void handle(PacketVoiceActivity msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                // Обновляем таймер говорящего
                ClientData.SQUAD_SPEAKERS.put(msg.playerName, System.currentTimeMillis());
            });
        });
        ctx.get().setPacketHandled(true);
    }
}