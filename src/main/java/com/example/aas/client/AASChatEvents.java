// PATH: src/main/java/com/example/aas/client/AASChatEvents.java
package com.example.aas.client;

import com.example.aas.client.gui.AASChatScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Подменяет ванильный ChatScreen на AASChatScreen, когда игрок открыл чат кнопкой чата (T).
 * Открытие чата клавишей команд ("/") остаётся ванильным - там нужен предзаполненный "/".
 */
@Mod.EventBusSubscriber(modid = "aas", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AASChatEvents {

    private static long chatKeyPressedAt = -1;

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        if (event.getAction() != GLFW.GLFW_PRESS) return;

        // Ванильный чат откроется на ближайшем тике - запоминаем, что причина именно кнопка чата
        if (mc.options.keyChat.getKey().getValue() == event.getKey()) {
            chatKeyPressedAt = System.currentTimeMillis();
        }
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (chatKeyPressedAt < 0) return;
        long age = System.currentTimeMillis() - chatKeyPressedAt;

        // Ровно ванильный ChatScreen (не подклассы других модов) и сразу после нажатия кнопки чата
        if (age < 250 && event.getNewScreen() != null && event.getNewScreen().getClass() == ChatScreen.class) {
            event.setNewScreen(new AASChatScreen());
            chatKeyPressedAt = -1;
        } else if (age >= 250) {
            chatKeyPressedAt = -1;
        }
    }
}