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
    public final boolean isAlt; // выбрана ли альтернативная версия кита

    public PacketSelectKit(String kitName) { this(kitName, false); }
    public PacketSelectKit(String kitName, boolean isAlt) { this.kitName = kitName; this.isAlt = isAlt; }

    public static void encode(PacketSelectKit msg, FriendlyByteBuf buf) { buf.writeUtf(msg.kitName); buf.writeBoolean(msg.isAlt); }
    public static PacketSelectKit decode(FriendlyByteBuf buf) { return new PacketSelectKit(buf.readUtf(), buf.readBoolean()); }

    public static void handle(PacketSelectKit msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                AASWorldData data = AASWorldData.get(player.serverLevel());

                // Ставим класс "в ожидание" вместе с выбранным вариантом (стандарт/альт)
                player.getPersistentData().putString("AAS_PendingKit", msg.kitName);
                player.getPersistentData().putBoolean("AAS_PendingKitAlt", msg.isAlt);

                // МГНОВЕННАЯ СИНХРОНИЗАЦИЯ:
                // Чтобы у всех сразу появилась иконка у ника этого игрока
                PacketHandler.sendToAllClients(player.serverLevel(), data);

                if (!data.isGameStarted) {
                    player.sendSystemMessage(Component.literal(Component.translatable("aas.msg.kit_reserved", msg.kitName).getString())
                            .withStyle(ChatFormatting.YELLOW));
                } else {
                    player.sendSystemMessage(Component.translatable("aas.msg.kit_selected_main", msg.kitName)
                            .withStyle(ChatFormatting.YELLOW));
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
