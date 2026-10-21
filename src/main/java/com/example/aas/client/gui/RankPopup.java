// PATH: src/main/java/com/example/aas/client/gui/RankPopup.java
package com.example.aas.client.gui;

import com.example.aas.client.KitRankIcons;
import com.example.aas.network.KitRankProgress;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Всплывающее окно с рангом игрока: иконка ранга, уровень и очки (набрано / нужно).
 * Открывается кликом ЛКМ по иконке ранга в списке отряда (меню K и экран смерти).
 * Повторный клик по иконке или клик мимо окна - закрывает.
 */
public final class RankPopup {

    private static final int PAD = 6;
    private static final int ICON = 20;
    private static final int MIN_W = 124;

    private record Hit(String name, int x, int y, int size) {}

    private static final List<Hit> HITS = new ArrayList<>();
    private static Object owner = null;
    private static String openFor = null;
    private static int anchorX, anchorY;
    private static int bx, by, bw, bh; // границы последнего отрисованного окна

    private RankPopup() {}

    /** Вызвать в начале render() экрана. Если экран сменился - окно закрывается. */
    public static void beginFrame(Object screen) {
        if (owner != screen) {
            owner = screen;
            openFor = null;
        }
        HITS.clear();
    }

    /** Вызвать сразу после KitRankIcons.draw(...) для строки игрока. */
    public static void registerIcon(GuiGraphics gui, String member, int x, int y, int size, int mouseX, int mouseY) {
        HITS.add(new Hit(member, x, y, size));
        boolean hover = mouseX >= x && mouseX < x + size && mouseY >= y && mouseY < y + size;
        if (hover) {
            gui.renderOutline(x - 1, y - 1, size + 2, size + 2, 0xAAFFFFFF);
        } else if (member.equals(openFor)) {
            gui.renderOutline(x - 1, y - 1, size + 2, size + 2, 0xFFFFD700);
        }
    }

    /** Вызвать в начале mouseClicked() экрана. true = клик обработан. */
    public static boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;

        for (Hit h : HITS) {
            if (mx >= h.x() && mx < h.x() + h.size() && my >= h.y() && my < h.y() + h.size()) {
                if (h.name().equals(openFor)) {
                    openFor = null;
                } else {
                    openFor = h.name();
                    anchorX = h.x() + h.size() + 4;
                    anchorY = h.y();
                }
                return true;
            }
        }

        if (openFor != null) {
            boolean inside = mx >= bx && mx < bx + bw && my >= by && my < by + bh;
            if (!inside) openFor = null;
            return inside;
        }
        return false;
    }

    /** Вызвать в самом конце render() экрана, чтобы окно было поверх всего. */
    public static void render(GuiGraphics gui, Font font, int screenW, int screenH) {
        if (openFor == null) return;

        int level = KitRankIcons.levelOf(openFor);
        if (level <= 0) { // ранги выключили или игрок сменил кит/вышел
            openFor = null;
            return;
        }
        KitRankProgress p = KitRankIcons.progressOf(openFor);
        boolean maxed = level >= KitRankIcons.MAX_LEVEL || (p != null && p.cost() <= 0);
        boolean showBar = p != null && !maxed;

        Component title = Component.literal(openFor);
        Component levelText = Component.translatable("aas.gui.rank.level", level);
        Component pointsText = null;
        if (maxed) {
            pointsText = Component.translatable("aas.gui.rank.max");
        } else if (p != null) {
            pointsText = Component.translatable("aas.gui.rank.points", p.into(), p.cost(), p.cost() - p.into());
        }

        // ---- размеры ----
        int contentW = Math.max(font.width(title), ICON + 6 + font.width(levelText));
        if (pointsText != null) contentW = Math.max(contentW, font.width(pointsText));
        int w = Math.max(MIN_W, contentW + PAD * 2);

        int iconRowY = PAD + 9 + 4;
        int cur = iconRowY + ICON + 4;
        int barY = cur;
        if (showBar) cur += 6 + 3;
        int textY = cur;
        if (pointsText != null) cur += 9; else cur -= 4;
        int h = cur + PAD;

        int x = Math.max(4, Math.min(anchorX, screenW - w - 4));
        int y = Math.max(4, Math.min(anchorY, screenH - h - 4));
        bx = x; by = y; bw = w; bh = h;

        // ---- отрисовка (поверх всех виджетов) ----
        gui.pose().pushPose();
        gui.pose().translate(0, 0, 400);

        gui.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF555555);
        gui.fill(x, y, x + w, y + h, 0xF0101010);

        gui.drawString(font, title, x + PAD, y + PAD, 0xFFAAAAAA, false);

        KitRankIcons.draw(gui, level, x + PAD, y + iconRowY, ICON);
        gui.drawString(font, levelText, x + PAD + ICON + 6, y + iconRowY + (ICON - 8) / 2, 0xFFFFFFFF, false);

        if (showBar) {
            int barW = w - PAD * 2;
            float frac = Mth.clamp(p.into() / (float) p.cost(), 0f, 1f);
            gui.fill(x + PAD, y + barY, x + PAD + barW, y + barY + 6, 0xFF333333);
            gui.fill(x + PAD, y + barY, x + PAD + (int) (barW * frac), y + barY + 6, 0xFFFFD700);
        }
        if (pointsText != null) {
            gui.drawString(font, pointsText, x + PAD, y + textY, maxed ? 0xFFFFD700 : 0xFFDDDDDD, false);
        }

        gui.pose().popPose();
    }
}