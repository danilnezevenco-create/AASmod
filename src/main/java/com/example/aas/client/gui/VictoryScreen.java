package com.example.aas.client.gui;

import com.example.aas.client.ClientData;
import com.example.aas.network.VictoryData;
import com.example.aas.util.VictoryTitles;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * Экран победы: заголовок по итогу, кнопка «Счёт», флаги победителей/проигравших с тикетами
 * и три глобальные панели (лучший отряд / медик / логист).
 * Данные приходят снимком VictoryData (одинаковым для всех клиентов).
 */
public class VictoryScreen extends Screen {

    private final VictoryData data;

    // Время открытия экрана для анимации (при повторном открытии по CAPS анимация пропускается)
    private final long openTime;
    private SquadButton continueButton;
    private SquadButton scoreButton;

    // Вертикальная раскладка (считается в computeLayout): весь контент — единый блок по центру экрана
    private static final int TITLE_H = 20;
    private static final int FLAG_W = 128;
    private static final int FLAG_H = 72;
    private static final int BUTTON_H = 20;
    private int titleY, flagY, panelsY, buttonY;

    // Размеры панелей
    private static final int PANEL_W = 150;
    private static final int PANEL_H = 72;
    private static final int HEADER_H = 20;
    private static final int PANEL_GAP = 14;
    private static final int BADGE_SIZE = 24;

    // Цвета кружка номера отряда (как в SquadSelectionScreen / StatisticsScreen)
    private static final int BADGE_CMD = 0xFF4CD964;    // отряд командира — зелёный
    private static final int BADGE_MINE = 0xFF00FF00;   // свой отряд — зелёный
    private static final int BADGE_OTHER = 0xFF3399FF;  // чужой отряд — синий

    private static final ResourceLocation CIRCLE_BADGE = new ResourceLocation("aas", "textures/gui/map_icons/player_circle.png");

    // Ссылки на флаги
    private static final ResourceLocation FLAG_UKRAINE = new ResourceLocation("aas", "textures/gui/flags/ukraine.png");
    private static final ResourceLocation FLAG_RUSSIA = new ResourceLocation("aas", "textures/gui/flags/russia.png");
    private static final ResourceLocation FLAG_USA = new ResourceLocation("aas", "textures/gui/flags/usa.png");
    private static final ResourceLocation FLAG_NATO = new ResourceLocation("aas", "textures/gui/flags/nato.png");
    private static final ResourceLocation FLAG_BLUEFOR = new ResourceLocation("aas", "textures/gui/flags/bluefor.png");
    private static final ResourceLocation FLAG_REDFOR = new ResourceLocation("aas", "textures/gui/flags/redfor.png");
    private static final ResourceLocation FLAG_INSURGENCY = new ResourceLocation("aas", "textures/gui/flags/insurgency.png");
    private static final ResourceLocation FLAG_PMC = new ResourceLocation("aas", "textures/gui/flags/pmc.png");
    private static final ResourceLocation FLAG_GERMANY = new ResourceLocation("aas", "textures/gui/flags/germany.png");
    private static final ResourceLocation FLAG_MILITIA = new ResourceLocation("aas", "textures/gui/flags/militia.png");

    public VictoryScreen(VictoryData data, boolean replay) {
        super(Component.literal("Victory Screen"));
        this.data = data;
        this.openTime = replay ? System.currentTimeMillis() - 10_000L : System.currentTimeMillis();
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        computeLayout();

        this.continueButton = new SquadButton(cx - 60, buttonY, 120, BUTTON_H,
                Component.translatable("aas.victory.continue"), b -> this.onClose());
        this.continueButton.active = false;
        this.addRenderableWidget(this.continueButton);

        // Кнопка «Счёт» — справа сверху. Открывает обычный экран статистики в режиме «видно всё»
        // (враги, их отряды и названия), как у игрока в креативе; ESC возвращает сюда.
        this.scoreButton = new SquadButton(this.width - 90, 10, 80, 20,
                Component.translatable("aas.victory.score"),
                b -> this.minecraft.setScreen(new StatisticsScreen(this, true)));
        this.scoreButton.active = false;
        this.addRenderableWidget(this.scoreButton);
    }

    /** Раскладывает заголовок, флаги, панели и кнопку одним блоком по центру экрана по вертикали. */
    private void computeLayout() {
        int captionH = 12;                       // название команды над флагом
        int ticketsH = 6 + 9;                    // отступ + строка «Осталось тикетов»
        int flagsBlock = captionH + FLAG_H + ticketsH;
        int fixed = TITLE_H + flagsBlock + PANEL_H + BUTTON_H;
        int gapsBase = 22 + 30 + 16;             // между: заголовок/флаги, флаги/панели, панели/кнопка

        // Если экран свободный — слегка растягиваем отступы, если тесный — сжимаем
        float g = Mth.clamp((this.height - 16 - fixed) / (float) gapsBase, 0.3f, 1.6f);
        int gap1 = Math.round(22 * g), gap2 = Math.round(30 * g), gap3 = Math.round(16 * g);

        int total = fixed + gap1 + gap2 + gap3;
        int top = Math.max(4, (this.height - total) / 2);

        this.titleY = top;
        this.flagY = titleY + TITLE_H + gap1 + captionH;
        this.panelsY = flagY + FLAG_H + ticketsH + gap2;
        this.buttonY = panelsY + PANEL_H + gap3;
    }

    /** Победил ли локальный игрок. Игрок без команды (наблюдатель) видит заголовок победителей. */
    private boolean localPlayerWon() {
        if (this.minecraft == null || this.minecraft.player == null || this.minecraft.player.getTeam() == null) return true;
        String team = this.minecraft.player.getTeam().getName();
        if (team.equalsIgnoreCase("BLUE")) return data.blueWon;
        if (team.equalsIgnoreCase("RED")) return !data.blueWon;
        return true;
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        long elapsed = System.currentTimeMillis() - this.openTime;

        // 1. АНИМАЦИЯ ФОНА (0 -> 1.5 сек)
        float bgAlpha = Mth.clamp(elapsed / 1500.0f, 0.0f, 1.0f);
        // 2. АНИМАЦИЯ КОНТЕНТА (1.5 -> 3.5 сек)
        float contentAlpha = Mth.clamp((elapsed - 1500) / 2000.0f, 0.0f, 1.0f);

        // === ПОЛУПРОЗРАЧНЫЙ ФОН ===
        int topAlpha = (int) (bgAlpha * 100);
        int bottomAlpha = (int) (bgAlpha * 140);
        gui.fillGradient(0, 0, this.width, this.height, (topAlpha << 24), (bottomAlpha << 24));

        if (contentAlpha > 0.01f) {
            int cx = this.width / 2;
            int a = (int) (contentAlpha * 255);
            RenderSystem.enableBlend();

            // --- 1. ЗАГОЛОВОК (зависит от исхода для локального игрока и тикетов победителя) ---
            boolean won = localPlayerWon();
            Component title = Component.translatable(VictoryTitles.titleKey(won, data.winnerTickets()));
            int titleColor = (a << 24) | (won ? 0x55FF55 : 0xFF5555);
            gui.pose().pushPose();
            gui.pose().translate(cx, titleY, 0);
            gui.pose().scale(2.0f, 2.0f, 1.0f);
            gui.drawCenteredString(this.font, title, 0, 0, titleColor);
            gui.pose().popPose();

            // --- 2. ФЛАГИ: победители слева, проигравшие справа ---
            String winTeam = data.blueWon ? "BLUE" : "RED";
            String loseTeam = data.blueWon ? "RED" : "BLUE";
            drawTeamBlock(gui, cx - 120 - FLAG_W / 2, flagY, FLAG_W, FLAG_H, winTeam, data.winnerTickets(), a, contentAlpha);
            drawTeamBlock(gui, cx + 120 - FLAG_W / 2, flagY, FLAG_W, FLAG_H, loseTeam, data.loserTickets(), a, contentAlpha);

            // --- 3. ТРИ ПАНЕЛИ ---
            int totalW = PANEL_W * 3 + PANEL_GAP * 2;
            int px = cx - totalW / 2;
            int py = panelsY;
            drawPanel(gui, px, py, "aas.victory.best_squad", "aas.victory.no_best_squad", data.bestSquad, a, contentAlpha);
            drawPanel(gui, px + PANEL_W + PANEL_GAP, py, "aas.victory.best_medic", "aas.victory.no_best_medic", data.bestMedic, a, contentAlpha);
            drawPanel(gui, px + (PANEL_W + PANEL_GAP) * 2, py, "aas.victory.best_logist", "aas.victory.no_best_logist", data.bestLogist, a, contentAlpha);

            RenderSystem.disableBlend();
        }

        // Обновляем прозрачность кнопок
        this.continueButton.currentAlpha = contentAlpha;
        this.scoreButton.currentAlpha = contentAlpha;
        if (contentAlpha >= 1.0f) {
            this.continueButton.active = true;
            this.scoreButton.active = true;
        }

        super.render(gui, mouseX, mouseY, partialTick);
    }

    /** Флаг команды + название команды над ним + «Сколько тикетов осталось» под ним. */
    private void drawTeamBlock(GuiGraphics gui, int x, int y, int w, int h, String team, int tickets, int a, float alpha) {
        String name = "RED".equals(team) ? data.redName : data.blueName;
        int nameColor = (a << 24) | ("RED".equals(team) ? 0xFF7777 : 0x77AAFF);
        gui.drawCenteredString(this.font, this.font.plainSubstrByWidth(name, w + 40), x + w / 2, y - 12, nameColor);

        drawFlag(gui, data.factionOf(team), team, x, y, w, h, a, alpha, true);

        Component t = Component.translatable("aas.victory.tickets_left", tickets);
        gui.drawCenteredString(this.font, t, x + w / 2, y + h + 6, (a << 24) | 0xDDDDDD);
    }

    private void drawPanel(GuiGraphics gui, int x, int y, String titleKey, String noneKey,
                           VictoryData.Panel p, int a, float alpha) {
        int bodyY = y + HEADER_H;
        int bodyH = PANEL_H - HEADER_H;

        // Шапка и тело
        gui.fill(x, y, x + PANEL_W, bodyY, (a << 24) | 0x1A1A1A);
        gui.fill(x, bodyY, x + PANEL_W, y + PANEL_H, (a << 24) | 0x0A0A0A);
        gui.renderOutline(x, y, PANEL_W, PANEL_H, (a << 24) | 0xFFFFFF);
        gui.fill(x, bodyY - 1, x + PANEL_W, bodyY, (a << 24) | 0xFFFFFF);
        gui.drawCenteredString(this.font, Component.translatable(titleKey), x + PANEL_W / 2, y + (HEADER_H - 8) / 2, (a << 24) | 0xFFFFFF);

        if (p == null || !p.present) {
            gui.drawCenteredString(this.font, Component.translatable(noneKey), x + PANEL_W / 2, bodyY + (bodyH - 8) / 2, (a << 24) | 0x888888);
            return;
        }

        // Кружок с номером отряда слева (там, где в макете знак вопроса)
        int textX = x + 8;
        if (p.squadNumber > 0) {
            int badgeY = bodyY + (bodyH - BADGE_SIZE) / 2;
            drawBadgeCircle(gui, x + 8, badgeY, BADGE_SIZE, badgeColorFor(p), alpha, String.valueOf(p.squadNumber), a);
            textX = x + 8 + BADGE_SIZE + 6;
        }

        // Название отряда / ник игрока, ниже — ник лидера (только у отряда)
        int maxTextW = x + PANEL_W - 6 - textX;
        gui.drawString(this.font, this.font.plainSubstrByWidth(p.name, maxTextW), textX, bodyY + 7,
                (a << 24) | 0xFFFFFF, false);
        if (p.sub != null && !p.sub.isEmpty()) {
            gui.drawString(this.font, this.font.plainSubstrByWidth(p.sub, maxTextW - 30), textX, bodyY + 20,
                    (a << 24) | 0xAAAAAA, false);
        }

        // Флаг команды — справа внизу панели
        int fw = 26, fh = 15;
        drawFlag(gui, data.factionOf(p.team), p.team, x + PANEL_W - fw - 4, y + PANEL_H - fh - 4, fw, fh, a, alpha, true);
    }

    /** CMD — зелёный, свой отряд — ярко-зелёный, чужой — синий. */
    private int badgeColorFor(VictoryData.Panel p) {
        if (p.cmd) return BADGE_CMD;
        String me = (this.minecraft != null && this.minecraft.player != null) ? this.minecraft.player.getScoreboardName() : null;
        List<String> members = p.squadMembers;
        if (me != null && members != null && members.contains(me)) return BADGE_MINE;
        return BADGE_OTHER;
    }

    private void drawBadgeCircle(GuiGraphics gui, int x, int y, int size, int colorARGB, float alpha, String label, int a) {
        float r = ((colorARGB >> 16) & 0xFF) / 255f;
        float g = ((colorARGB >> 8) & 0xFF) / 255f;
        float b = (colorARGB & 0xFF) / 255f;
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(r, g, b, alpha);
        gui.blit(CIRCLE_BADGE, x, y, 0, 0, size, size, size, size);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        if (label != null && !label.isEmpty()) {
            gui.drawCenteredString(this.font, label, x + size / 2, y + (size - 8) / 2, (a << 24) | 0xFFFFFF);
        }
    }

    /** Рисует флаг фракции; если текстуры нет — цветной прямоугольник команды. */
    private void drawFlag(GuiGraphics gui, String faction, String team, int x, int y, int w, int h,
                          int a, float alpha, boolean outline) {
        ResourceLocation tex = getFlagTexture(faction);
        if (tex != null) {
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
            RenderSystem.enableBlend();
            gui.blit(tex, x, y, 0, 0, w, h, w, h);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        } else {
            int base = "RED".equalsIgnoreCase(team) ? 0xCC3333 : 0x3366CC;
            gui.fill(x, y, x + w, y + h, (a << 24) | base);
        }
        if (outline) gui.renderOutline(x - 1, y - 1, w + 2, h + 2, (a << 24) | 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public static ResourceLocation getFlagTexture(String faction) {
        if (faction == null || faction.equals("none")) return null;
        switch (faction.toLowerCase()) {
            case "ukraine": return FLAG_UKRAINE;
            case "russia": return FLAG_RUSSIA;
            case "usa": return FLAG_USA;
            case "nato": return FLAG_NATO;
            case "bluefor": return FLAG_BLUEFOR;
            case "redfor": return FLAG_REDFOR;
            case "insurgency": return FLAG_INSURGENCY;
            case "pmc": return FLAG_PMC;
            case "germany": return FLAG_GERMANY;
            case "militia": return FLAG_MILITIA;
            default: return null;
        }
    }

    // Класс кнопки с поддержкой прозрачности (Alpha)
    static class SquadButton extends Button {
        public float currentAlpha = 0.0f;

        public SquadButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(GuiGraphics gui, int mouseX, int mouseY, float partialTicks) {
            if (!this.visible || currentAlpha <= 0.02f) return;

            int a = (int) (currentAlpha * 255);

            int bgCol = (a << 24) | 0x111111;
            int borderBase = this.isHovered() ? 0xFFFFFF : 0x999999;
            if (!this.active) borderBase = 0x444444;
            int borderCol = (a << 24) | borderBase;

            RenderSystem.enableBlend();
            gui.fill(getX(), getY(), getX() + width, getY() + height, bgCol);
            gui.renderOutline(getX(), getY(), width, height, borderCol);

            int textBase = this.active ? 0xFFFFFF : 0x777777;
            int textCol = (a << 24) | textBase;

            gui.drawCenteredString(Minecraft.getInstance().font, this.getMessage(), getX() + width / 2, getY() + (height - 8) / 2, textCol);

            if (this.active && this.isHovered()) {
                gui.fill(getX(), getY() + height - 2, getX() + 2, getY() + height, (a << 24) | 0xFFFFFF);
            }
            RenderSystem.disableBlend();
        }
    }
}
