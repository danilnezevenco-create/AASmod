package com.example.aas.network;

import com.example.aas.world.AASWorldData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketSquadChat {
    private final String message;
    private final int mode; // 0 = ALL, 1 = TEAM, 2 = SQUAD

    public PacketSquadChat(String message, int mode) {
        this.message = message;
        this.mode = mode;
    }

    public static void encode(PacketSquadChat msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.message);
        buf.writeInt(msg.mode);
    }

    public static PacketSquadChat decode(FriendlyByteBuf buf) {
        return new PacketSquadChat(buf.readUtf(), buf.readInt());
    }

    public static void handle(PacketSquadChat msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null) {
                // Вызываем общую логику
                processChat(sender, msg.message, msg.mode);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    /**
     * Публичный метод для обработки чата.
     * Используется и пакетом (из GUI), и событием ServerChatEvent (обычный чат).
     */
    public static void processChat(ServerPlayer sender, String message, int mode) {
        String senderName = sender.getScoreboardName();
        String senderTeam = (sender.getTeam() != null) ? sender.getTeam().getName() : "NEUTRAL";

        MutableComponent formattedMessage;

        // === MODE 0: ALL (Пурпурный) ===
        if (mode == 0) {
            formattedMessage = Component.literal("[ALL] ").withStyle(ChatFormatting.LIGHT_PURPLE)
                    .append(Component.literal(senderName + ": ").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(message).withStyle(ChatFormatting.LIGHT_PURPLE));

            // Отправляем ВСЕМ игрокам
            sender.server.getPlayerList().broadcastSystemMessage(formattedMessage, false);
        }

        // === MODE 1: TEAM (Синий) ===
        else if (mode == 1) {
            if (senderTeam.equals("NEUTRAL")) {
                sender.sendSystemMessage(Component.literal("You are not in a team!").withStyle(ChatFormatting.RED));
                return;
            }

            formattedMessage = Component.literal("[TEAM] ").withStyle(ChatFormatting.BLUE)
                    .append(Component.literal(senderName + ": ").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(message).withStyle(ChatFormatting.BLUE));

            // Отправляем только сокомандникам
            for (ServerPlayer p : sender.server.getPlayerList().getPlayers()) {
                if (p.getTeam() != null && p.getTeam().getName().equalsIgnoreCase(senderTeam)) {
                    p.sendSystemMessage(formattedMessage);
                }
            }
        }

        // === MODE 2: SQUAD (Зеленый) ===
        else if (mode == 2) {
            AASWorldData data = AASWorldData.get(sender.serverLevel().getServer().overworld());

            // Ищем отряд игрока
            AASWorldData.Squad playerSquad = null;
            for (AASWorldData.Squad s : data.squads) {
                if (s.members.contains(senderName)) {
                    playerSquad = s;
                    break;
                }
            }

            if (playerSquad == null) {
                sender.sendSystemMessage(Component.literal("You are not in a squad!").withStyle(ChatFormatting.RED));
                return;
            }

            formattedMessage = Component.literal("[SQUAD] ").withStyle(ChatFormatting.GREEN)
                    .append(Component.literal(senderName + ": ").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(message).withStyle(ChatFormatting.GREEN));

            // Отправляем только членам отряда
            for (String memberName : playerSquad.members) {
                ServerPlayer member = sender.server.getPlayerList().getPlayerByName(memberName);
                if (member != null) {
                    member.sendSystemMessage(formattedMessage);
                }
            }
        }
    }
}