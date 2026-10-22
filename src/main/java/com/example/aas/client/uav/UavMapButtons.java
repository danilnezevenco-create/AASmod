package com.example.aas.client.uav;

import com.example.aas.client.ClientData;
import com.example.aas.network.PacketHandler;
import com.example.aas.network.PacketUavView;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** UAV launch button (top-left of the map) and the "eye" button that follows the flying UAV. CMD leader only. */
public class UavMapButtons {
    private static final ResourceLocation EYE_ICON = new ResourceLocation("aas", "textures/gui/eye_icon.png");
    private static final int SIZE = 24, EYE = 20;

    private int lx, ly, ex, ey;
    private boolean eyeVisible;
    private Component tooltip;

    /** null = launch is possible, otherwise the reason (also used as the tooltip). */
    private Component launchBlockReason(Minecraft mc) {
        if (!ClientData.isGameStarted) return Component.translatable("aas.uav.tooltip.not_started");
        if (!mc.player.isAlive()) return Component.translatable("aas.uav.tooltip.dead");
        if (UavClientState.isDowned(mc)) return Component.translatable("aas.uav.tooltip.downed");
        if (UavClientState.active) return Component.translatable("aas.uav.tooltip.active");
        long left = UavClientState.cooldownTicksLeft(mc);
        if (left > 0) return Component.translatable("aas.uav.tooltip.cooldown", (left + 19) / 20);
        return null;
    }

    public void render(GuiGraphics gui, int mouseX, int mouseY, int mapX, int mapY, int mapSize,
                       double panX, double panZ, double bpp, UavMapSelection sel) {
        Minecraft mc = Minecraft.getInstance();
        tooltip = null;
        eyeVisible = false;
        if (mc.player == null || !UavClientState.isCmdLeader(mc)) return;

        // --- launch button ---
        lx = mapX + 6;
        ly = mapY + 6;
        Component reason = launchBlockReason(mc);
        boolean enabled = reason == null;
        boolean hover = inside(mouseX, mouseY, lx, ly, SIZE);
        int bg = sel.isActive() ? 0xCC705800 : 0xAA000000;
        int outline = !enabled ? 0xFF4A4A4A : (hover || sel.isActive() ? 0xFFFFFFFF : 0xFFB8860B);
        gui.fill(lx, ly, lx + SIZE, ly + SIZE, bg);
        gui.renderOutline(lx, ly, SIZE, SIZE, outline);
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, enabled ? 1f : 0.35f);
        gui.blit(UavZoneRenderer.UAV_ICON, lx + 4, ly + 4, 16, 16, 0, 0, 32, 32, 32, 32);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        if (hover) tooltip = enabled ? Component.translatable("aas.uav.tooltip.ready") : reason;

        // --- eye button at the UAV position ---
        if (UavClientState.active && !UavViewClient.isViewing() && mc.level != null) {
            Vec3 p = UavClientState.position(mc.level.getGameTime() + mc.getPartialTick());
            double sx = mapX + mapSize / 2.0 + (p.x - panX) / bpp;
            double sy = mapY + mapSize / 2.0 + (p.z - panZ) / bpp;
            double by = sy + 8 + EYE / 2.0 + 1;   // button centre: right under the 16px UAV icon
            if (sx >= mapX + EYE / 2.0 && sx <= mapX + mapSize - EYE / 2.0 && by >= mapY + EYE / 2.0 && by <= mapY + mapSize - EYE / 2.0) {
                ex = (int) sx - EYE / 2;
                ey = (int) by - EYE / 2;
                eyeVisible = true;
                boolean downed = UavClientState.isDowned(mc);
                boolean eh = inside(mouseX, mouseY, ex, ey, EYE);
                gui.fill(ex, ey, ex + EYE, ey + EYE, 0xCC000000);
                gui.renderOutline(ex, ey, EYE, EYE, downed ? 0xFF4A4A4A : (eh ? 0xFFFFFFFF : 0xFFB8860B));
                RenderSystem.enableBlend();
                RenderSystem.setShaderColor(1f, 1f, 1f, downed ? 0.35f : 1f);
                gui.blit(EYE_ICON, ex + 2, ey + 2, 16, 16, 0, 0, 32, 32, 32, 32);
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                if (eh) tooltip = Component.translatable(downed ? "aas.uav.eye.downed" : "aas.uav.eye.tooltip");
            }
        }
    }

    public void renderTooltip(GuiGraphics gui, Font font, int mouseX, int mouseY) {
        if (tooltip != null) gui.renderTooltip(font, tooltip, mouseX, mouseY);
    }

    public boolean mouseClicked(double mx, double my, int button, UavMapSelection sel) {
        if (button != 0) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !UavClientState.isCmdLeader(mc)) return false;
        if (inside(mx, my, lx, ly, SIZE)) {
            if (sel.isActive()) sel.cancel();
            else if (launchBlockReason(mc) == null) sel.start();
            return true;
        }
        if (eyeVisible && inside(mx, my, ex, ey, EYE)) {
            if (!UavClientState.isDowned(mc)) PacketHandler.INSTANCE.sendToServer(new PacketUavView(true));
            return true;
        }
        return false;
    }

    private static boolean inside(double mx, double my, int x, int y, int size) {
        return mx >= x && mx <= x + size && my >= y && my <= y + size;
    }
}
