package com.example.aas.client.uav;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Random;

/** Camera-feed look (procedural, no shaders), crosshair and the hint plates while looking through the UAV. */
@Mod.EventBusSubscriber(modid = "aas", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class UavViewOverlay {
    private static final ResourceLocation CROSSHAIR = new ResourceLocation("aas", "textures/gui/uav_crosshair.png");
    private static final Random RND = new Random();
    private static long glitchUntil = 0, nextGlitch = 0;

    @SubscribeEvent
    public static void onRender(RenderGuiEvent.Post e) {
        if (!UavViewClient.isViewing()) return;
        Minecraft mc = Minecraft.getInstance();
        GuiGraphics g = e.getGuiGraphics();
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        long now = System.currentTimeMillis();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        // light flicker
        float flick = 0.035f + 0.03f * (float) Math.sin(now * 0.021) + RND.nextFloat() * 0.02f;
        g.fill(0, 0, w, h, ((int) (Math.max(0f, flick) * 255) << 24));

        // scanlines
        for (int y = 0; y < h; y += 3) g.fill(0, y, w, y + 1, 0x16000000);

        // slow horizontal band
        int band = (int) ((now / 14) % (h + 90)) - 90;
        g.fillGradient(0, band, w, band + 45, 0x00FFFFFF, 0x14FFFFFF);
        g.fillGradient(0, band + 45, w, band + 90, 0x14FFFFFF, 0x00FFFFFF);

        // weak noise
        for (int i = 0; i < 140; i++) {
            int x = RND.nextInt(w), y = RND.nextInt(h);
            g.fill(x, y, x + 1 + RND.nextInt(2), y + 1, RND.nextBoolean() ? 0x30FFFFFF : 0x30000000);
        }

        // rare short glitch
        if (now > nextGlitch) {
            glitchUntil = now + 90 + RND.nextInt(110);
            nextGlitch = now + 3500 + RND.nextInt(5000);
        }
        if (now < glitchUntil) {
            for (int i = 0; i < 4; i++) {
                int y = RND.nextInt(h - 12), hh = 3 + RND.nextInt(9);
                g.fill(0, y, w, y + hh, 0x33FFFFFF);
                g.fill(RND.nextInt(w / 2), y, w, y + 1, 0x55FF2020);
                g.fill(0, y + hh - 1, RND.nextInt(w / 2), y + hh, 0x5520FFFF);
            }
        }

        // vignette (same look as before; top/bottom rings no longer lose their alpha at the very edge)
        int steps = 36;
        for (int i = 0; i < steps; i++) {
            int a = (int) (((steps - i) / (float) steps) * ((steps - i) / (float) steps) * 150);
            int c = a << 24;
            int sx = i * (w / 2 / steps + 1), sy = i * (h / 2 / steps + 1);
            g.fill(sx, 0, sx + (w / 2 / steps + 1), h, c);
            g.fill(w - sx - (w / 2 / steps + 1), 0, w - sx, h, c);
            g.fill(0, sy, w, sy + (h / 2 / steps + 1), c);
            g.fill(0, h - sy - (h / 2 / steps + 1), w, h - sy, c);
        }

        // crosshair (placeholder texture)
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        g.blit(CROSSHAIR, w / 2 - 16, h / 2 - 16, 32, 32, 0, 0, 32, 32, 32, 32);

        // hint plates, top-left, same look as TeamKillOverlay (dark plate + coloured stripe), no icon
        String[] keys = {"aas.uav.hint.zoom", "aas.uav.hint.exit", "aas.uav.hint.tilt"};
        int y = 12;
        for (String k : keys) {
            Component t = Component.translatable(k);
            int tw = mc.font.width(t);
            g.fill(0, y, tw + 20, y + 18, 0xAA000000);
            g.fill(0, y, 3, y + 18, 0xFFB8860B);
            g.drawString(mc.font, t, 10, y + 5, 0xFFFFFFFF, true);
            y += 22;
        }
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }
}
