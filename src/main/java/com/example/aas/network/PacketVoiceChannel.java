package com.example.aas.network;

import com.example.aas.voicechat.VoiceChannelState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Клиент -> сервер: какую голосовую клавишу игрок держит сейчас.
 * 0 = ничего, 1 = локальный (V), 2 = отряд (B), 3 = командирский (*).
 * Шлётся при смене состояния и как «пульс» каждые ~0.5 сек, пока клавиша зажата.
 */
public class PacketVoiceChannel {
    private final int channel;

    public PacketVoiceChannel(int channel) {
        this.channel = channel;
    }

    public static void encode(PacketVoiceChannel msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.channel);
    }

    public static PacketVoiceChannel decode(FriendlyByteBuf buf) {
        return new PacketVoiceChannel(buf.readByte());
    }

    public static void handle(PacketVoiceChannel msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            // Допустимость канала (есть ли отряд, лидер ли он) проверяется в момент передачи звука.
            VoiceChannelState.set(player.getUUID(), msg.channel);
        });
        ctx.get().setPacketHandled(true);
    }
}