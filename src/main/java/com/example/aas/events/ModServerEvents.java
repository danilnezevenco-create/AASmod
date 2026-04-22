package com.example.aas.events;

import com.example.aas.network.PacketSquadChat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "aas", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ModServerEvents {

    @SubscribeEvent
    public static void onServerChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        String message = event.getMessage().getString();

        // Если это команда (начинается с /), не трогаем
        if (message.startsWith("/")) return;

        // Отменяем стандартную отправку всем
        event.setCanceled(true);

        // Перенаправляем сообщение в нашу систему как TEAM (Mode 1)
        PacketSquadChat.processChat(player, message, 1);
    }
}