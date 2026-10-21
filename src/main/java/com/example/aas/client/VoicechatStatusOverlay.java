// PATH: src\main\java\com\example\aas\client\VoicechatStatusOverlay.java
package com.example.aas.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Предупреждение «Simple Voice Chat не подключён» (снизу по центру).
 * Показывается ТОЛЬКО когда игрок пытается говорить (зажата клавиша голосового канала V / B / *),
 * а Simple Voice Chat не работает. После отпускания клавиши висит ещё LINGER_MS, чтобы успеть прочитать.
 * Работает в игре и на экранах смерти / выбора отряда.
 * Источник состояния - ClientData.voicechatConnected (обновляется из AASVoicechatPlugin).
 */
@Mod.EventBusSubscriber(modid = "aas", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class VoicechatStatusOverlay {

    /** сколько мс плашка остаётся после последней попытки говорить */
    private static final long LINGER_MS = 2500;

    private static long lastAttemptMs = 0;

    /** true, если плашку надо показать прямо сейчас (заодно запоминает момент попытки говорить) */
    private static boolean shouldShow() {
        if (ClientData.voicechatConnected) return false;
        long now = System.currentTimeMillis();
        if (VoiceKeyHandler.getCurrentChannel() != 0) lastAttemptMs = now;   // клавиша голоса зажата
        return lastAttemptMs != 0 && now - lastAttemptMs < LINGER_MS;
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !shouldShow()) return;
        draw(event.getGuiGraphics(), mc);
    }

    // На экранах смерти и выбора отряда HUD не виден, поэтому рисуем плашку поверх экрана.
    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof net.minecraft.client.gui.screens.DeathScreen
                || event.getScreen() instanceof com.example.aas.client.gui.SquadSelectionScreen)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !shouldShow()) return;
        draw(event.getGuiGraphics(), mc);
    }

    private static void draw(GuiGraphics gui, Minecraft mc) {
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();

        Component title = Component.translatable("aas.voicechat.disconnected.title");
        Component subtitle = Component.translatable("aas.voicechat.disconnected.subtitle");

        int boxWidth = Math.max(mc.font.width(title), mc.font.width(subtitle)) + 16;
        int centerX = width / 2;
        int y = height - 58; // прямо над хотбаром

        gui.pose().pushPose();
        gui.pose().translate(0, 0, 600);   // поверх всего
        gui.fill(centerX - boxWidth / 2, y - 4, centerX + boxWidth / 2, y + 24, 0xA0000000);
        gui.drawCenteredString(mc.font, title, centerX, y, 0xFFFF5555);
        gui.drawCenteredString(mc.font, subtitle, centerX, y + 12, 0xFFFFFFFF);
        gui.pose().popPose();
    }
}