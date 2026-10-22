package com.example.aas.client.uav;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/** Draws the UAV zone circle (light yellow fill, dark yellow outline) and the UAV icon on the map. */
public final class UavZoneRenderer {
    private UavZoneRenderer() {}

    public static final ResourceLocation UAV_ICON = new ResourceLocation("aas", "textures/gui/uav_icon.png");
    private static final int FILL = 0x40FFF3A6;     // translucent light yellow
    private static final int OUTLINE = 0xFFB8860B;  // dark yellow

    /** Called from AASMapRenderer.render (inside the map scissor) for the own team's active zone. */
    public static void renderZone(GuiGraphics gui, int mapX, int mapY, int mapSize, double centerX, double centerZ, double bpp) {
        if (!UavClientState.active) return;
        float sx = (float) (mapX + mapSize / 2.0 + (UavClientState.cx - centerX) / bpp);
        float sy = (float) (mapY + mapSize / 2.0 + (UavClientState.cz - centerZ) / bpp);
        drawZone(gui, sx, sy, (float) (UavClientState.radius / bpp));
        // the icon sits on the flying UAV itself (it orbits with it), not in the zone centre
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            Vec3 p = UavClientState.position(mc.level.getGameTime() + mc.getPartialTick());
            float ux = (float) (mapX + mapSize / 2.0 + (p.x - centerX) / bpp);
            float uy = (float) (mapY + mapSize / 2.0 + (p.z - centerZ) / bpp);
            drawIcon(gui, ux, uy, 16);
        }
    }

    public static void drawIcon(GuiGraphics gui, float sx, float sy, int size) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        gui.blit(UAV_ICON, Math.round(sx) - size / 2, Math.round(sy) - size / 2, size, size, 0, 0, 32, 32, 32, 32);
    }

    public static void drawZone(GuiGraphics gui, float cx, float cy, float radiusPx) {
        if (radiusPx < 1f) return;
        int segments = Mth.clamp((int) (radiusPx / 2f), 48, 256);
        Matrix4f m = gui.pose().last().pose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder bb = tess.getBuilder();

        // fill: triangle fan
        float fa = (FILL >>> 24 & 255) / 255f, fr = (FILL >> 16 & 255) / 255f, fg = (FILL >> 8 & 255) / 255f, fb = (FILL & 255) / 255f;
        bb.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        bb.vertex(m, cx, cy, 0f).color(fr, fg, fb, fa).endVertex();
        for (int i = 0; i <= segments; i++) {
            float a = i * Mth.TWO_PI / segments;
            bb.vertex(m, cx + Mth.cos(a) * radiusPx, cy + Mth.sin(a) * radiusPx, 0f).color(fr, fg, fb, fa).endVertex();
        }
        tess.end();

        // outline: triangle strip ring, 2 px
        float oa = (OUTLINE >>> 24 & 255) / 255f, or = (OUTLINE >> 16 & 255) / 255f, og = (OUTLINE >> 8 & 255) / 255f, ob = (OUTLINE & 255) / 255f;
        float inner = radiusPx - 1f, outer = radiusPx + 1f;
        bb.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= segments; i++) {
            float a = i * Mth.TWO_PI / segments;
            float c = Mth.cos(a), s = Mth.sin(a);
            bb.vertex(m, cx + c * outer, cy + s * outer, 0f).color(or, og, ob, oa).endVertex();
            bb.vertex(m, cx + c * inner, cy + s * inner, 0f).color(or, og, ob, oa).endVertex();
        }
        tess.end();

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }
}
