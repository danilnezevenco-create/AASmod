package com.example.aas.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Клиентский помощник для иконок рангов китов (1..20).
 * Текстуры: assets/aas/textures/gui/ranks/rank_1.png ... rank_20.png
 */
public final class KitRankIcons {

    public static final int MAX_LEVEL = 20;

    /** Размер самой текстуры в пикселях (как у иконок китов — 10x10). Если нарисованы другого размера, поменяй тут. */
    public static final int TEX_SIZE = 10;

    /** Сколько места занимает иконка ранга в строке (10 px иконка + 2 px отступ, как у иконки кита). */
    public static final int ROW_ADVANCE = 12;

    private static final ResourceLocation[] ICONS = new ResourceLocation[MAX_LEVEL + 1];

    static {
        for (int i = 1; i <= MAX_LEVEL; i++) {
            ICONS[i] = new ResourceLocation("aas", "textures/gui/ranks/rank_" + i + ".png");
        }
    }

    /** Уровень для показа. 0 = ничего не рисовать (конфиг выключен / нет данных). */
    public static int levelOf(String playerName) {
        if (!ClientData.serverShowKitRanks) return 0;
        Integer lvl = ClientData.playerKitRanks.get(playerName);
        if (lvl == null) return 0;
        return Math.max(0, Math.min(MAX_LEVEL, lvl));
    }

    /** Рисует иконку 10x10. */
    public static void draw(GuiGraphics gui, int level, int x, int y) {
        draw(gui, level, x, y, 10);
    }

    /** Рисует иконку с заданным размером на экране (например 9, как у иконки кита в SquadPanelOverlay). */
    public static void draw(GuiGraphics gui, int level, int x, int y, int size) {
        if (level < 1 || level > MAX_LEVEL) return;
        gui.blit(ICONS[level], x, y, size, size, 0f, 0f, TEX_SIZE, TEX_SIZE, TEX_SIZE, TEX_SIZE);
    }

    private KitRankIcons() {
    }
}