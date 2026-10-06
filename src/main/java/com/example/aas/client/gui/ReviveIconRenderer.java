// PATH: src/main/java/com/example/aas/client/gui/ReviveIconRenderer.java
package com.example.aas.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Иконка (белая текстура) + кольцо вокруг неё.
 * progress < 0: подъёма нет - рисуется только иконка цветом #D43435, без кольца.
 * progress 0..1: иконка и заливка кольца плавно перетекают из #D43435 в #FCD400,
 * кольцо заполняется по часовой стрелке начиная с 12 часов.
 * Работает и в GUI (drawGui), и в мировой метке (draw с матрицей PoseStack).
 */
public final class ReviveIconRenderer {
    public static final int COLOR_IDLE = 0xD43435;   // никто не поднимает
    public static final int COLOR_DONE = 0xFCD400;   // подъём завершён
    private static final int SEGMENTS = 72;          // гладкость круга

    private ReviveIconRenderer() {}

    public static int lerpColor(int a, int b, float t) {
        int r = (int) Mth.lerp(t, (a >> 16) & 0xFF, (b >> 16) & 0xFF);
        int g = (int) Mth.lerp(t, (a >> 8) & 0xFF, (b >> 8) & 0xFF);
        int bl = (int) Mth.lerp(t, a & 0xFF, b & 0xFF);
        return (r << 16) | (g << 8) | bl;
    }

    /** Для экранов / HUD (GuiGraphics). cx, cy - центр иконки. */
    public static void drawGui(GuiGraphics gui, ResourceLocation icon, float cx, float cy,
                               float iconSize, float ringInner, float ringOuter, float progress) {
        gui.flush(); // дорисовать всё, что GuiGraphics ещё держит в буфере
        draw(gui.pose().last().pose(), icon, cx, cy, iconSize, ringInner, ringOuter, progress);
    }

    /** Общий метод: matrix - матрица текущей позы (GUI или мировая метка). */
    public static void draw(Matrix4f m, ResourceLocation icon, float cx, float cy,
                            float iconSize, float ringInner, float ringOuter, float progress) {
        boolean showRing = progress >= 0f;
        progress = Mth.clamp(progress, 0f, 1f);
        int color = lerpColor(COLOR_IDLE, COLOR_DONE, progress);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        // подложки нет: рисуется только заполненная дуга
        if (showRing && progress > 0f) {
            ring(m, cx, cy, ringInner, ringOuter, progress, color, 255);
        }
        // 3. иконка (белая текстура красится в нужный цвет)
        icon(m, icon, cx, cy, iconSize, color);

        RenderSystem.enableCull();
    }

    private static void ring(Matrix4f m, float cx, float cy, float ri, float ro,
                             float fraction, int rgb, int alpha) {
        if (fraction <= 0f) return;
        int n = Math.max(2, (int) Math.ceil(SEGMENTS * fraction));
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= n; i++) {
            double a = -Math.PI / 2.0 + 2.0 * Math.PI * fraction * i / n; // старт сверху, по часовой
            float cos = (float) Math.cos(a), sin = (float) Math.sin(a);
            buf.vertex(m, cx + cos * ro, cy + sin * ro, 0).color(r, g, b, alpha).endVertex();
            buf.vertex(m, cx + cos * ri, cy + sin * ri, 0).color(r, g, b, alpha).endVertex();
        }
        tess.end();
    }

    private static void icon(Matrix4f m, ResourceLocation tex, float cx, float cy, float size, int rgb) {
        float h = size / 2f;
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;

        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, tex);
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        buf.vertex(m, cx - h, cy + h, 0).uv(0f, 1f).color(r, g, b, 255).endVertex();
        buf.vertex(m, cx + h, cy + h, 0).uv(1f, 1f).color(r, g, b, 255).endVertex();
        buf.vertex(m, cx + h, cy - h, 0).uv(1f, 0f).color(r, g, b, 255).endVertex();
        buf.vertex(m, cx - h, cy - h, 0).uv(0f, 0f).color(r, g, b, 255).endVertex();
        tess.end();
    }
}