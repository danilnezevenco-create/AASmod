package com.example.aas.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class VictoryScreen extends Screen {

    private final String winnerName;
    private final String winnerFaction;
    private final String subText;
    private final boolean isBlueWinner;

    // Время открытия экрана для анимации
    private final long openTime;
    private SquadButton continueButton;

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

    public VictoryScreen(String winnerName, String winnerFaction, String subText, boolean isBlueWinner) {
        super(Component.literal("Victory Screen"));
        this.winnerName = winnerName;
        this.winnerFaction = winnerFaction;
        this.subText = subText;
        this.isBlueWinner = isBlueWinner;
        this.openTime = System.currentTimeMillis();
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;

        this.continueButton = new SquadButton(cx - 70, cy + 65, 140, 24, Component.literal("CONTINUE"), b -> {
            this.onClose();
        });

        this.continueButton.active = false;
        this.addRenderableWidget(this.continueButton);
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        long elapsed = System.currentTimeMillis() - this.openTime;

        // 1. АНИМАЦИЯ ФОНА (0 -> 1.5 сек)
        float bgAlpha = Mth.clamp(elapsed / 1500.0f, 0.0f, 1.0f);

        // 2. АНИМАЦИЯ КОНТЕНТА (1.5 -> 3.5 сек)
        float contentAlpha = Mth.clamp((elapsed - 1500) / 2000.0f, 0.0f, 1.0f);

        // === ПОЛУПРОЗРАЧНЫЙ ФОН ===
        // 100 и 140 — это уровень прозрачности (из 255). Мир позади будет отлично виден!
        int topAlpha = (int) (bgAlpha * 100);
        int bottomAlpha = (int) (bgAlpha * 140);
        int topBg = (topAlpha << 24) | 0x000000;
        int bottomBg = (bottomAlpha << 24) | 0x000000;
        gui.fillGradient(0, 0, this.width, this.height, topBg, bottomBg);

        // Отрисовка контента, если он начал появляться
        if (contentAlpha > 0.01f) {
            int cx = this.width / 2;
            int cy = this.height / 2;

            int alphaInt = (int)(contentAlpha * 255);
            int frameColor = (alphaInt << 24) | 0xFFFFFF;

            // --- 1. ФЛАГ ---
            ResourceLocation flagTex = getFlagTexture(this.winnerFaction);
            int flagW = 128;
            int flagH = 72;
            int flagX = cx - (flagW / 2);
            int flagY = cy - 95;

            if (flagTex != null) {
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, contentAlpha);
                RenderSystem.enableBlend();
                gui.blit(flagTex, flagX, flagY, 0, 0, flagW, flagH, flagW, flagH);
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

                gui.renderOutline(flagX - 1, flagY - 1, flagW + 2, flagH + 2, frameColor);
            } else {
                int fallbackBase = isBlueWinner ? 0x3366CC : 0xCC3333;
                gui.fill(flagX, flagY, flagX + flagW, flagY + flagH, (alphaInt << 24) | fallbackBase);
                gui.renderOutline(flagX - 1, flagY - 1, flagW + 2, flagH + 2, frameColor);
            }

            RenderSystem.enableBlend();

            // --- 2. ГЛАВНЫЙ ТЕКСТ ---
            int titleColor = (alphaInt << 24) | 0xFFFFFF;
            gui.pose().pushPose();
            gui.pose().translate(cx, cy - 5, 0);
            gui.pose().scale(2.0f, 2.0f, 1.0f);
            gui.drawCenteredString(this.font, this.winnerName + " WINS!", 0, 0, titleColor);
            gui.pose().popPose();

            // --- 3. ПОДЗАГОЛОВОК (ТИКЕТЫ) ---
            int subColor = (alphaInt << 24) | 0xAAAAAA;
            gui.drawCenteredString(this.font, this.subText, cx, cy + 25, subColor);

            RenderSystem.disableBlend();
        }

        // Обновляем прозрачность кнопки
        this.continueButton.currentAlpha = contentAlpha;

        if (contentAlpha >= 1.0f && !this.continueButton.active) {
            this.continueButton.active = true;
        }

        super.render(gui, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private ResourceLocation getFlagTexture(String faction) {
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
    private static class SquadButton extends Button {
        public float currentAlpha = 0.0f;

        public SquadButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(GuiGraphics gui, int mouseX, int mouseY, float partialTicks) {
            if (!this.visible || currentAlpha <= 0.02f) return;

            int a = (int)(currentAlpha * 255);

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