package com.example.aas.network;

import com.example.aas.client.ClientData;
import com.example.aas.client.OwnVoiceIndicator;
import com.example.aas.client.VoiceIcons;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Сервер -> клиент: «этот игрок сейчас говорит в таком-то канале» (для списка слева).
 * channel: 1 = локальный (V), 2 = отряд (B), 3 = командирский (*).
 * level:   0 = тихо, 1 = средне, 2 = нормально (измеряется на сервере, см. VoiceLevelMeter).
 */
public class PacketVoiceChannelActivity {
    private final String playerName;
    private final int channel;
    private final int level;

    public PacketVoiceChannelActivity(String playerName, int channel) {
        this(playerName, channel, 2);
    }

    public PacketVoiceChannelActivity(String playerName, int channel, int level) {
        this.playerName = playerName;
        this.channel = channel;
        this.level = level;
    }

    public static void encode(PacketVoiceChannelActivity msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.playerName);
        buf.writeByte(msg.channel);
        buf.writeByte(msg.level);
    }

    public static PacketVoiceChannelActivity decode(FriendlyByteBuf buf) {
        return new PacketVoiceChannelActivity(buf.readUtf(), buf.readByte(), buf.readByte());
    }

    public static void handle(PacketVoiceChannelActivity msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                long now = System.currentTimeMillis();
                switch (msg.channel) {
                    case 1 -> ClientData.LOCAL_SPEAKERS.put(msg.playerName, now);
                    case 2 -> ClientData.SQUAD_SPEAKERS.put(msg.playerName, now);
                    case 3 -> {
                        Long prev = ClientData.COMMAND_SPEAKERS.put(msg.playerName, now);
                        // «пип» рации, когда кто-то начинает говорить в командирском чате
                        OwnVoiceIndicator.onCommandActivity(msg.playerName, prev, now);
                    }
                    default -> { return; }
                }
                VoiceIcons.put(msg.playerName, msg.level);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}