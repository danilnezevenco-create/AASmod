// PATH: src/main/java/com/example/aas/network/PacketReviveHold.java
package com.example.aas.network;

import com.example.aas.events.DownedHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Клиент -> сервер. Шлётся только при смене состояния (не каждый тик):
 * holding=true  - START подъёма (повторяется раз в 0.5 с, пока сервер не подтвердил),
 * holding=false - CANCEL (клавишу отпустили или цель потеряна).
 * Сервер - авторитетный источник: сам считает время подъёма и перепроверяет цель,
 * дистанцию, предмет в руке и состояние нока.
 */
public class PacketReviveHold {
    private final boolean holding;
    private final int targetEntityId;

    public PacketReviveHold(boolean holding, int targetEntityId) {
        this.holding = holding;
        this.targetEntityId = targetEntityId;
    }

    public static void encode(PacketReviveHold msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.holding);
        buf.writeInt(msg.targetEntityId);
    }

    public static PacketReviveHold decode(FriendlyByteBuf buf) {
        return new PacketReviveHold(buf.readBoolean(), buf.readInt());
    }

    public static void handle(PacketReviveHold msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            DownedHandler.setReviveHold(player, msg.holding, msg.targetEntityId);
        });
        ctx.get().setPacketHandled(true);
    }
}