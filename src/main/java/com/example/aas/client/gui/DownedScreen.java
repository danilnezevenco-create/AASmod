package com.example.aas.client.gui;

import com.example.aas.network.PacketDownedAction;
import com.example.aas.network.PacketHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

public class DownedScreen extends Screen {
    private static final ResourceLocation HEARTBEAT_ICON = new ResourceLocation("aas", "textures/gui/heartbeat.png");
    private static final ResourceLocation VIGNETTE_TEXTURE = new ResourceLocation("aas", "textures/misc/vignette.png");

    private Button callMedicButton;
    private long lastMedicCallTime = 0;

    // СТАЛО
    public DownedScreen() {
        super(Component.translatable("aas.gui.downed.title"));
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int bottomY = this.height - 50;

        // СТАЛО
        this.addRenderableWidget(new SquadButton(cx - 130, bottomY, 120, 24, Component.translatable("aas.gui.downed.give_up"), b -> {
            PacketHandler.INSTANCE.sendToServer(new PacketDownedAction(1));
            this.onClose();
        }));

        this.callMedicButton = this.addRenderableWidget(new SquadButton(cx + 10, bottomY, 120, 24, Component.translatable("aas.gui.downed.call_medic"), b -> {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastMedicCallTime >= 15000) {
                PacketHandler.INSTANCE.sendToServer(new PacketDownedAction(0));
                lastMedicCallTime = currentTime;
            }
        }));
    }

    @Override
    public void render(GuiGraphics gui, int mx, int my, float pt) {
        renderVignette(gui);

        int cx = this.width / 2;
        int baseY = this.height - 150;
        int boxW = 320;
        int boxH = 135;

        renderSquadFrame(gui, cx - boxW / 2, baseY, boxW, boxH);

        // Иконка
        RenderSystem.enableBlend();
        gui.blit(HEARTBEAT_ICON, cx - 18, baseY + 10, 0, 0, 36, 36, 36, 36);

        // Расстояние до союзников
        String allyStatus = getAllyDistanceStatus();
        gui.drawCenteredString(this.font, allyStatus, cx, baseY + 55, 0xFFFFFF);

        // === ИСПРАВЛЕННЫЙ ТАЙМЕР (Проблема №2) ===
        // Вместо this.screenOpenTime используем глобальную метку из ClientData
        int maxSeconds = com.example.aas.config.AASConfig.MAX_DOWNED_TIME_SECONDS.get();
        long deathTime = com.example.aas.client.ClientData.globalDeathTimestamp;

        // Считаем сколько секунд прошло с момента смерти (падения в нок)
        // Если deathTime вдруг 0, считаем что прошло 0 секунд (чтобы не было багов)
        long elapsedSeconds = (deathTime == 0) ? 0 : (System.currentTimeMillis() - deathTime) / 1000;
        long remainingBleedout = maxSeconds - elapsedSeconds;

        if (remainingBleedout < 0) remainingBleedout = 0;

        // СТАЛО
        String bleedText = Component.translatable("aas.gui.downed.bleeding_out", remainingBleedout).getString();
        gui.drawCenteredString(this.font, bleedText, cx, baseY + 72, 0xAAAAAA);

        // Обновление КД на кнопке
        long remainingCooldown = 15000 - (System.currentTimeMillis() - lastMedicCallTime);
        if (remainingCooldown > 0) {
            callMedicButton.setMessage(Component.translatable("aas.gui.downed.call_medic_cd", (remainingCooldown / 1000 + 1)));
            callMedicButton.active = false;
        } else {
            callMedicButton.setMessage(Component.translatable("aas.gui.downed.call_medic"));
            callMedicButton.active = true;
        }

        super.render(gui, mx, my, pt);
    }

    private String getAllyDistanceStatus() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return "NO NEARBY ALLIES";

        double minDistance = Double.MAX_VALUE;
        Player closestAlly = null; // Переменная для хранения самого близкого игрока
        boolean found = false;

        for (Player other : mc.level.players()) {
            // Пропускаем себя, спектаторов и мертвых
            if (other == mc.player || other.isSpectator() || !other.isAlive()) continue;

            // Пропускаем игроков, которые сами в ноке — их не считаем ближайшим союзником
            if (com.example.aas.client.ClientData.DOWNED_PLAYERS.contains(other.getId())) continue;

            // Проверяем, в одной ли мы команде
            if (mc.player.getTeam() != null && other.getTeam() == mc.player.getTeam()) {
                double dist = mc.player.distanceTo(other);
                if (dist < minDistance) {
                    minDistance = dist;
                    closestAlly = other;
                    found = true;
                }
            }
        }

        if (!found || minDistance > 250) return Component.translatable("aas.gui.downed.no_allies").getString();

        String allyName = closestAlly.getScoreboardName();

        return Component.translatable("aas.gui.downed.closest_ally", (int) minDistance, allyName).getString();
    }

    private void renderSquadFrame(GuiGraphics gui, int x, int y, int w, int h) {
        gui.fill(x, y, x + w, y + h, 0x99000000);
        gui.renderOutline(x, y, w, h, 0x44FFFFFF);
        gui.renderOutline(x + 2, y + 2, w - 4, h - 4, 0xBBFFFFFF);
    }

    private void renderVignette(GuiGraphics gui) {
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // БЫЛО: 0.7f (очень плотно). СТАЛО: 0.35f (прозрачно)
        // Также немного увеличим яркость красного (0.8f)
        RenderSystem.setShaderColor(0.8f, 0.0f, 0.0f, 0.05f);

        gui.blit(VIGNETTE_TEXTURE, 0, 0, 0, 0, this.width, this.height, this.width, this.height);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 0.80f);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    @Override
    public boolean shouldCloseOnEsc() { return false; }

    // КЛАСС КНОПКИ С ИСПРАВЛЕННЫМ ВЫДЕЛЕНИЕМ
    private static class SquadButton extends Button {
        public SquadButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(GuiGraphics gui, int mouseX, int mouseY, float partialTicks) {
            if (!this.visible) return;

            // ИСПРАВЛЕНИЕ: Используем только isHovered() вместо isHoveredOrFocused()
            // Теперь кнопка будет ярко-белой только когда на нее наведена мышь
            int borderColor = this.isHovered() ? 0xFFFFFFFF : 0xFF999999;

            // Если кнопка выключена (идет КД), делаем рамку темной
            if (!this.active) borderColor = 0xFF444444;

            gui.fill(getX(), getY(), getX() + width, getY() + height, 0xCC111111);
            gui.renderOutline(getX(), getY(), width, height, borderColor);

            int textColor = this.active ? 0xFFFFFFFF : 0xFF777777;

            // Текст теперь всегда будет белым (или серым при КД), но без лишней тени
            gui.drawCenteredString(Minecraft.getInstance().font, this.getMessage(), getX() + width / 2, getY() + (height - 8) / 2, textColor);

            // Уголок виден только при наведении мыши
            if (this.active && this.isHovered()) {
                gui.fill(getX(), getY() + height - 2, getX() + 2, getY() + height, 0xFFFFFFFF);
            }
        }
    }
}