package com.example.aas.client;

import com.example.aas.network.PacketHandler;
import com.example.aas.network.PacketVoiceChannel;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.controls.KeyBindsScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Голосовые каналы «по удержанию»: V — локальный, B — отряд, * — командирский.
 *
 * Клавиши читаются НАПРЯМУЮ у GLFW (а не через KeyMapping.isDown), поэтому работают:
 *  - в игре, на карте, в любом экране отряда;
 *  - на экране смерти (мёртвый игрок тоже может говорить);
 *  - с любой перебинженной клавишей или кнопкой мыши (Управление -> AAS).
 * Не срабатывают, если игрок печатает (чат, поле ввода), перебиндит клавиши или стоит на паузе.
 *
 * Приоритет, если зажато несколько: командирский > отряд > локальный.
 */
@Mod.EventBusSubscriber(modid = "aas", value = Dist.CLIENT)
public class VoiceKeyHandler {
    private static int current = 0;
    private static int ticksSinceSend = 0;
    private static final int HEARTBEAT_TICKS = 10;   // 0.5 сек

    /** Какой канал сейчас активен у этого клиента (0 = никакой). Можно использовать для HUD. */
    public static int getCurrentChannel() {
        return current;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();

        int want = 0;
        boolean online = mc.player != null && mc.level != null && mc.getConnection() != null;
        if (online && !inputBlocked(mc)) {
            if (isDown(mc, ModKeyBindings.VOICE_COMMAND_KEY)) want = 3;
            else if (isDown(mc, ModKeyBindings.VOICE_SQUAD_KEY)) want = 2;
            else if (isDown(mc, ModKeyBindings.VOICE_LOCAL_KEY)) want = 1;
        }

        if (!online) {          // вышли из мира — просто сбрасываем локальное состояние
            current = 0;
            ticksSinceSend = 0;
            return;
        }

        ticksSinceSend++;
        if (want != current || (want != 0 && ticksSinceSend >= HEARTBEAT_TICKS)) {
            current = want;
            ticksSinceSend = 0;
            PacketHandler.INSTANCE.sendToServer(new PacketVoiceChannel(want));
        }
    }

    private static boolean inputBlocked(Minecraft mc) {
        Screen s = mc.screen;
        if (s == null) return false;
        if (s instanceof ChatScreen || s instanceof KeyBindsScreen || s instanceof PauseScreen) return true;
        return s.getFocused() instanceof EditBox box && box.isFocused();
    }

    private static boolean isDown(Minecraft mc, KeyMapping km) {
        InputConstants.Key key = km.getKey();
        int code = key.getValue();
        if (code == InputConstants.UNKNOWN.getValue()) return false;
        long window = mc.getWindow().getWindow();
        switch (key.getType()) {
            case MOUSE:
                return GLFW.glfwGetMouseButton(window, code) == GLFW.GLFW_PRESS;
            case KEYSYM:
                return InputConstants.isKeyDown(window, code);
            default:
                return km.isDown();
        }
    }
}