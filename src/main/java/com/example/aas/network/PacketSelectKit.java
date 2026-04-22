package com.example.aas.network;

import com.example.aas.world.AASWorldData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class PacketSelectKit {
    public final String kitName;
    public PacketSelectKit(String kitName) { this.kitName = kitName; }

    public static void encode(PacketSelectKit msg, FriendlyByteBuf buf) { buf.writeUtf(msg.kitName); }
    public static PacketSelectKit decode(FriendlyByteBuf buf) { return new PacketSelectKit(buf.readUtf()); }

    // PATH: src\main\java\com\example\aas\network\PacketSelectKit.java
// Замените метод handle целиком:

    // PATH: src/main/java/com/example/aas/network/PacketSelectKit.java

    public static void handle(PacketSelectKit msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                AASWorldData data = AASWorldData.get(player.serverLevel());

                // Ставим класс "в ожидание"
                player.getPersistentData().putString("AAS_PendingKit", msg.kitName);

                // МГНОВЕННАЯ СИНХРОНИЗАЦИЯ:
                // Чтобы у всех сразу появилась иконка у ника этого игрока
                PacketHandler.sendToAllClients(player.serverLevel(), data);

                if (!data.isGameStarted) {
                    player.sendSystemMessage(Component.literal("Класс " + msg.kitName + " забронирован. Вы получите его при старте игры.")
                            .withStyle(ChatFormatting.YELLOW));
                } else {
                    player.sendSystemMessage(Component.literal("Класс " + msg.kitName + " выбран. Перезайдите на базу для получения.")
                            .withStyle(ChatFormatting.YELLOW));
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}