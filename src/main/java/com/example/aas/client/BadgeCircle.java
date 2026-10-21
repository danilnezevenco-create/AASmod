// PATH: src\main\java\com\example\aas\client\BadgeCircle.java
package com.example.aas.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Единый кружок с номером отряда для всех меню и HUD.
 * Текстура: assets/aas/textures/gui/squad_badge.png (белый/светло-серый круг на прозрачном фоне,
 * красится в нужный цвет). Размер файла должен быть TEX_SIZE x TEX_SIZE (по умолчанию 32x32);
 * при любом размере кружка на экране картинка масштабируется целиком.
 * Если файла ещё нет — рисуется старый player_circle.png, как раньше.
 */
public final class BadgeCircle {
    /** размер PNG в пикселях (квадрат). Если нарисуешь иначе — поменяй здесь */
    public static final int TEX_SIZE = 32;

    private static final ResourceLocation TEX =
            new ResourceLocation("aas", "textures/gui/squad_badge.png");
    private static final ResourceLocation OLD_TEX =
            new ResourceLocation("aas", "textures/gui/map_icons/player_circle.png");

    private static Boolean hasNewTex = null;

    private BadgeCircle() {}

    private static boolean hasNew() {
        if (hasNewTex == null) {
            hasNewTex = Minecraft.getInstance().getResourceManager().getResource(TEX).isPresent();
        }
        return hasNewTex;
    }

    /** Только картинка кружка (цвет задаётся снаружи через RenderSystem.setShaderColor). */
    public static void blit(GuiGraphics gui, int x, int y, int size) {
        if (hasNew()) {
            Minecraft.getInstance().getTextureManager().getTexture(TEX).setFilter(true, false); // плавное уменьшение
            gui.blit(TEX, x, y, size, size, 0f, 0f, TEX_SIZE, TEX_SIZE, TEX_SIZE, TEX_SIZE);
        } else {
            gui.blit(OLD_TEX, x, y, 0, 0, size, size, size, size);
        }
    }

    /** Кружок + номер по центру. argb — цвет кружка (с альфой). */
    public static void draw(GuiGraphics gui, Font font, int x, int y, int size, int argb, String label) {
        float a = ((argb >>> 24) & 0xFF) / 255f;
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(r, g, b, a);
        blit(gui, x, y, size);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        if (label != null && !label.isEmpty()) {
            gui.drawCenteredString(font, label, x + size / 2, y + (size - 8) / 2, 0xFFFFFFFF);
        }
    }
}