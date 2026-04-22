package com.example.aas.client.gui;

import com.example.aas.block.HubBlockEntity;
import com.example.aas.network.PacketHandler;
import com.example.aas.network.PacketRequestAmmo;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

public class HubAmmoScreen extends Screen {

    private final BlockPos hubPos;
    private final int boxSize = 60;
    private final int gap = 20;

    // Локальные копии кд для рендера
    private int cachedCdAGS = 0;
    private int cachedCdM2 = 0;

    public HubAmmoScreen(BlockPos pos) {
        super(Component.literal("Hub Supply"));
        this.hubPos = pos;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void updateCooldowns() {
        if (Minecraft.getInstance().level != null) {
            BlockEntity be = Minecraft.getInstance().level.getBlockEntity(hubPos);
            if (be instanceof HubBlockEntity hub) {
                this.cachedCdAGS = hub.cooldownAGS;
                this.cachedCdM2 = hub.cooldownM2;
            }
        }
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gui);
        updateCooldowns(); // Обновляем данные

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        // === ЛЕВЫЙ КВАДРАТ (AGS) ===
        int leftX = centerX - boxSize - (gap / 2);
        int leftY = centerY - (boxSize / 2);
        boolean hoverLeft = (mouseX >= leftX && mouseX <= leftX + boxSize && mouseY >= leftY && mouseY <= leftY + boxSize);

        int colorLeft = 0xFFFFFFFF; // Белый
        if (cachedCdAGS > 0) colorLeft = 0xFFFF5555; // Красный (КД)
        else if (hoverLeft) colorLeft = 0xFF55FF55; // Зеленый (Ховер)

        renderBox(gui, leftX, leftY, boxSize, colorLeft, "AGS-30", cachedCdAGS);

        // === ПРАВЫЙ КВАДРАТ (M2) ===
        int rightX = centerX + (gap / 2);
        int rightY = centerY - (boxSize / 2);
        boolean hoverRight = (mouseX >= rightX && mouseX <= rightX + boxSize && mouseY >= rightY && mouseY <= rightY + boxSize);

        int colorRight = 0xFFFFFFFF; // Белый
        if (cachedCdM2 > 0) colorRight = 0xFFFF5555; // Красный (КД)
        else if (hoverRight) colorRight = 0xFF55FF55; // Зеленый (Ховер)

        renderBox(gui, rightX, rightY, boxSize, colorRight, "M2 Ammo", cachedCdM2);

        super.render(gui, mouseX, mouseY, partialTick);
    }

    private void renderBox(GuiGraphics gui, int x, int y, int size, int color, String label, int cooldown) {
        // Рамка
        gui.fill(x - 2, y - 2, x + size + 2, y + size + 2, 0xFF000000);
        // Заливка
        int bg = (color & 0x00FFFFFF) | 0x80000000;
        gui.fill(x, y, x + size, y + size, bg);
        // Контур
        gui.renderOutline(x, y, size, size, color);

        // Текст
        int labelWidth = this.font.width(label);
        gui.drawString(this.font, label, x + (size - labelWidth) / 2, y + (size / 2) - 10, color, true);

        // Таймер
        if (cooldown > 0) {
            String time = (cooldown / 20) + "s";
            int timeW = this.font.width(time);
            gui.drawString(this.font, time, x + (size - timeW) / 2, y + (size / 2) + 5, 0xFFFFFF55, true);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) { // ЛКМ
            updateCooldowns();

            int centerX = this.width / 2;
            int centerY = this.height / 2;

            int leftX = centerX - boxSize - (gap / 2);
            int rightX = centerX + (gap / 2);
            int boxY = centerY - (boxSize / 2);

            // Клик по левому (AGS)
            if (mouseX >= leftX && mouseX <= leftX + boxSize && mouseY >= boxY && mouseY <= boxY + boxSize) {
                if (cachedCdAGS == 0) { // Если нет КД
                    PacketHandler.INSTANCE.sendToServer(new PacketRequestAmmo(hubPos, 0));
                    this.onClose();
                    return true;
                }
            }

            // Клик по правому (M2)
            if (mouseX >= rightX && mouseX <= rightX + boxSize && mouseY >= boxY && mouseY <= boxY + boxSize) {
                if (cachedCdM2 == 0) { // Если нет КД
                    PacketHandler.INSTANCE.sendToServer(new PacketRequestAmmo(hubPos, 1));
                    this.onClose();
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}