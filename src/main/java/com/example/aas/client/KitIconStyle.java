package com.example.aas.client;

import com.mojang.blaze3d.systems.RenderSystem;

/**
 * Единое место для цветов иконок китов. Текстуры китов (textures/gui/kits/*.png) белые,
 * а цвет задаётся здесь при отрисовке (через shader color).
 *
 * Хочешь сменить цвет — правь константы ниже, больше нигде менять не нужно.
 */
public final class KitIconStyle {
    private KitIconStyle() {}

    /** Цвет иконок китов по умолчанию (меню отрядов, выбор кита и т.п.): #ADE800 */
    public static final int DEFAULT_COLOR = 0xFFADE800;

    // Цвета каналов голоса (как в списке говорящих слева)
    public static final int COLOR_LOCAL   = 0xFF55AAFF;   // V — синий
    public static final int COLOR_SQUAD   = 0xFF55FF55;   // B — зелёный
    public static final int COLOR_COMMAND = 0xFFFFAA00;   // * — золотой

    /** Красит всё, что рисуется дальше (blit), в этот цвет с заданной прозрачностью. */
    public static void apply(int argb, float alpha) {
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        RenderSystem.setShaderColor(r, g, b, alpha);
    }

    public static void reset() {
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    /**
     * Цвет канала, в котором игрок говорит ПРЯМО СЕЙЧАС (командирский > отряд > локальный),
     * или 0, если он не говорит ни в одном из этих каналов.
     */
    public static int speakingColor(String name) {
        long now = System.currentTimeMillis();
        Long t = ClientData.COMMAND_SPEAKERS.get(name);
        if (t != null && now - t < 300) return COLOR_COMMAND;
        t = ClientData.SQUAD_SPEAKERS.get(name);
        if (t != null && now - t < 300) return COLOR_SQUAD;
        t = ClientData.LOCAL_SPEAKERS.get(name);
        if (t != null && now - t < 300) return COLOR_LOCAL;
        return 0;
    }
}