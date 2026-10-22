package com.example.aas.client.uav;

import com.example.aas.client.ClientData;
import com.example.aas.client.gui.MapGrid;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Random;

/** Camera-feed look (procedural, no shaders), crosshair and the hint plates while looking through the UAV. */
@Mod.EventBusSubscriber(modid = "aas", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class UavViewOverlay {
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

        drawHud(g, mc, w, h, e.getPartialTick());

        // key hints, top-right (same plates as before, mirrored to the right side)
        String[] keys = {"aas.uav.hint.zoom", "aas.uav.hint.exit", "aas.uav.hint.tilt"};
        int y = 12;
        for (String k : keys) {
            Component t = Component.translatable(k);
            int tw = mc.font.width(t);
            g.fill(w - tw - 20, y, w, y + 18, 0xAA000000);
            g.fill(w - 3, y, w, y + 18, 0xFFB8860B);
            g.drawString(mc.font, t, w - tw - 13, y + 5, 0xFFFFFFFF, true);
            y += 22;
        }
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
    }

    // ------------------------------------------------------------------ HUD (reference: gunship / UAV feed)
    private static final int INK = 0xFFFFFFFF, INK_DIM = 0xB0FFFFFF;
    private static final String[] CARDINAL = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    private static void drawHud(GuiGraphics g, Minecraft mc, int w, int h, float partial) {
        if (mc.level == null) return;
        float yaw = UavViewClient.viewYaw(), pitch = UavViewClient.viewPitch();
        double heading = Mth.wrapDegrees(yaw + 180f);             // compass: 0 = north, 90 = east
        if (heading < 0) heading += 360.0;
        double now = mc.level.getGameTime() + partial;
        Vec3 cam = UavClientState.active ? UavClientState.position(now) : mc.gameRenderer.getMainCamera().getPosition();

        drawCompass(g, mc, w, heading);
        drawPitchScale(g, mc, h, -pitch);                          // 90 deg straight down shows as -90
        drawTopLeft(g, mc, now);
        drawReticle(g, w, h, heading);
        drawTarget(g, mc, w, h, cam, yaw, pitch);
        drawAircraft(g, mc, w, h, cam);
    }

    private static void text(GuiGraphics g, Minecraft mc, String t, int x, int y) {
        g.drawString(mc.font, t, x, y, INK, true);
    }

    private static void textRight(GuiGraphics g, Minecraft mc, String t, int xRight, int y) {
        g.drawString(mc.font, t, xRight - mc.font.width(t), y, INK, true);
    }

    /** Compass tape at the top: shows where the camera looks (heading), +-60 degrees around it. */
    private static void drawCompass(GuiGraphics g, Minecraft mc, int w, double heading) {
        int tapeW = (int) Mth.clamp(w * 0.45, 240, 560);
        double ppd = tapeW / 120.0;
        int cx = w / 2, base = 50;
        g.fill(cx - tapeW / 2, base, cx + tapeW / 2, base + 1, INK_DIM);
        int a0 = (int) Math.floor((heading - 60) / 5.0) * 5, a1 = (int) Math.ceil((heading + 60) / 5.0) * 5;
        for (int a = a0; a <= a1; a += 5) {
            int x = cx + (int) Math.round((a - heading) * ppd);
            if (x < cx - tapeW / 2 || x > cx + tapeW / 2) continue;
            int norm = ((a % 360) + 360) % 360;
            boolean major = norm % 15 == 0;
            g.fill(x, base + 1, x + 1, base + (major ? 8 : 4), INK_DIM);
            if (major && Math.abs(x - cx) > 18) {
                String label = norm % 45 == 0 ? CARDINAL[norm / 45] : String.format("%03d", norm);
                g.drawString(mc.font, label, x - mc.font.width(label) / 2, base - 11, INK_DIM, true);
            }
        }
        // current heading box
        String hs = String.format("%03d", Math.floorMod((int) Math.round(heading), 360));
        int bw = mc.font.width(hs) + 10;
        g.fill(cx - bw / 2, base - 36, cx + bw / 2, base - 20, 0xCC000000);
        g.renderOutline(cx - bw / 2, base - 36, bw, 16, INK);
        g.drawString(mc.font, hs, cx - mc.font.width(hs) / 2, base - 32, INK, true);
        for (int i = 0; i < 4; i++) g.fill(cx - 3 + i, base - 20 + i, cx + 4 - i, base - 19 + i, INK);   // little pointer
    }

    /** Vertical camera tilt scale on the left: 0 (horizon) at the top .. -90 (straight down) at the bottom. */
    private static void drawPitchScale(GuiGraphics g, Minecraft mc, int h, float angle) {
        int x = 74, top = (int) (h * 0.26), len = (int) (h * 0.48);
        g.fill(x, top, x + 1, top + len, INK_DIM);
        for (int a = 0; a <= 90; a += 5) {
            int y = top + (int) Math.round(a / 90.0 * len);
            boolean major = a % 15 == 0;
            g.fill(x - (major ? 9 : 4), y, x, y + 1, INK_DIM);
            if (major) {
                String l = a == 0 ? "0" : "-" + a;
                g.drawString(mc.font, l, x - 13 - mc.font.width(l), y - 4, INK_DIM, true);
            }
        }
        float a = Mth.clamp(angle, -90f, 0f);
        int y = top + (int) Math.round(-a / 90.0 * len);
        for (int i = 0; i < 5; i++) g.fill(x + 3 + i, y - 4 + i, x + 4 + i, y + 5 - i, INK);   // pointer, tip towards the scale
        g.drawString(mc.font, String.format(java.util.Locale.ROOT, "%.1f", a), x + 14, y - 4, INK, true);
    }

    /** Top-left: remaining UAV charge (flight time), then the zoom of the camera. */
    private static void drawTopLeft(GuiGraphics g, Minecraft mc, double now) {
        int x = 20, y = 14;
        double total = Math.max(1, UavClientState.endTick - UavClientState.startTick);
        double left = Mth.clamp(UavClientState.endTick - now, 0, total);
        double frac = left / total;
        text(g, mc, "CHARGE", x, y);
        int bx = x + mc.font.width("CHARGE") + 8, bw = 90;
        g.fill(bx, y, bx + bw, y + 9, 0x66000000);
        g.renderOutline(bx - 1, y - 1, bw + 2, 11, INK_DIM);
        int color = frac > 0.5 ? 0xFFE8E8E8 : (frac > 0.2 ? 0xFFFFC840 : 0xFFFF4040);
        g.fill(bx, y, bx + (int) Math.round(bw * frac), y + 9, color);
        int secs = (int) Math.ceil(left / 20.0);
        text(g, mc, String.format("%02d:%02d", secs / 60, secs % 60), x, y + 16);

        // zoom: real magnification versus the normal field of view
        int stage = UavViewClient.zoomStage();
        double base = mc.options.fov().get();
        double mag = stage == 0 ? 1.0 : base / Math.max(1.0, ClientData.uavZoomFov(stage - 1));
        text(g, mc, String.format(java.util.Locale.ROOT, "%.1fX", mag), x, y + 40);
        g.drawString(mc.font, "DAY TV", x, y + 52, INK_DIM, true);
    }

    /** Crosshair with corner brackets; the north arrow orbits around it. */
    private static void drawReticle(GuiGraphics g, int w, int h, double heading) {
        int cx = w / 2, cy = h / 2, s = 24, t = 7;
        // centre cross with a gap
        g.fill(cx - 8, cy, cx - 2, cy + 1, INK); g.fill(cx + 3, cy, cx + 9, cy + 1, INK);
        g.fill(cx, cy - 8, cx + 1, cy - 2, INK); g.fill(cx, cy + 3, cx + 1, cy + 9, INK);
        // corner brackets
        g.fill(cx - s, cy - s, cx - s + t, cy - s + 1, INK_DIM); g.fill(cx - s, cy - s, cx - s + 1, cy - s + t, INK_DIM);
        g.fill(cx + s - t, cy - s, cx + s, cy - s + 1, INK_DIM); g.fill(cx + s - 1, cy - s, cx + s, cy - s + t, INK_DIM);
        g.fill(cx - s, cy + s - 1, cx - s + t, cy + s, INK_DIM); g.fill(cx - s, cy + s - t, cx - s + 1, cy + s, INK_DIM);
        g.fill(cx + s - t, cy + s - 1, cx + s, cy + s, INK_DIM); g.fill(cx + s - 1, cy + s - t, cx + s, cy + s, INK_DIM);

        // north arrow: if the camera looks east (90), north is on the left of the screen
        double a = Math.toRadians(-heading);
        int r = 38;
        float ax = (float) (cx + Math.sin(a) * r), ay = (float) (cy - Math.cos(a) * r);
        g.pose().pushPose();
        g.pose().translate(ax, ay, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees((float) -heading));
        g.pose().scale(1.5f, 1.5f, 1f);
        for (int i = 0; i < 6; i++) g.fill(-i / 2 - (i == 0 ? 0 : 0), -3 + i, i / 2 + 1, -2 + i, INK);   // triangle pointing "up" = north
        g.pose().popPose();
        Minecraft mc = Minecraft.getInstance();
        float lx = (float) (cx + Math.sin(a) * (r + 13)), ly = (float) (cy - Math.cos(a) * (r + 13));
        g.drawString(mc.font, "N", Math.round(lx) - mc.font.width("N") / 2, Math.round(ly) - 4, INK, true);
    }

    private static String grid(double x, double z) {
        int size = ClientData.mapSizeBlocks;
        if (size <= 0) return "--";
        String c = MapGrid.cellName(x - (ClientData.mapCenterX - size / 2.0), z - (ClientData.mapCenterZ - size / 2.0), size);
        return c == null ? "--" : c.replace(" ", " - ");
    }

    /** Right-middle: what the crosshair points at (grid reference + distance from the UAV). */
    private static void drawTarget(GuiGraphics g, Minecraft mc, int w, int h, Vec3 cam, float yaw, float pitch) {
        int xr = w - 20, y = (int) (h * 0.42);
        String gridText = "--", range = "---";
        Vec3 dir = Vec3.directionFromRotation(pitch, yaw);
        BlockHitResult hit = mc.level.clip(new ClipContext(cam, cam.add(dir.scale(900.0)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() == HitResult.Type.BLOCK) {
            Vec3 p = hit.getLocation();
            gridText = grid(p.x, p.z);
            range = (int) Math.round(cam.distanceTo(p)) + "m";
        }
        textRight(g, mc, "TARGET", xr, y);
        textRight(g, mc, "GRID REF " + gridText, xr, y + 18);
        textRight(g, mc, "RANGE " + range, xr, y + 30);
    }

    /** Bottom-right: where the UAV itself is. */
    private static void drawAircraft(GuiGraphics g, Minecraft mc, int w, int h, Vec3 cam) {
        int xr = w - 20, y = (int) (h * 0.74);
        textRight(g, mc, "AIRCRAFT", xr, y);
        textRight(g, mc, "LOCATION " + grid(cam.x, cam.z), xr, y + 18);
        textRight(g, mc, "ELEVATION " + (int) Math.round(cam.y) + "m", xr, y + 30);
    }
}
