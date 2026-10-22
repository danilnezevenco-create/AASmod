package com.example.aas.client.uav;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Text-only notification in the style of the downed timer plate (dark plate, red outline, red text). */
@Mod.EventBusSubscriber(modid = "aas", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class UavNoticeOverlay {
    private static Component text;
    private static long start;
    private static final long TOTAL_MS = 4000, FADE_MS = 600;

    public static void push(Component c) { text = c; start = System.currentTimeMillis(); }

    @SubscribeEvent
    public static void onRender(RenderGuiEvent.Post e) {
        if (text == null) return;
        long el = System.currentTimeMillis() - start;
        if (el >= TOTAL_MS) { text = null; return; }
        float a = el > TOTAL_MS - FADE_MS ? (TOTAL_MS - el) / (float) FADE_MS : 1f;
        int alpha = Math.max(4, (int) (a * 255));
        Minecraft mc = Minecraft.getInstance();
        GuiGraphics g = e.getGuiGraphics();
        int w = mc.font.width(text);
        int cx = mc.getWindow().getGuiScaledWidth() / 2;
        int y = 40;
        g.fill(cx - w / 2 - 8, y, cx + w / 2 + 8, y + 18, ((int) (a * 0xAA)) << 24);
        g.renderOutline(cx - w / 2 - 8, y, w + 16, 18, (alpha << 24) | 0xFF5555);
        g.drawCenteredString(mc.font, text, cx, y + 5, (alpha << 24) | 0xFF5555);
    }
}
