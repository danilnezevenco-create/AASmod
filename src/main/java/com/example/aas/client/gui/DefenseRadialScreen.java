package com.example.aas.client.gui;

import com.example.aas.client.ClientPlacementHandler;
import com.example.aas.network.PacketHandler;
import com.example.aas.network.PacketRadioAction;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class DefenseRadialScreen extends Screen {

    private static final ResourceLocation SECTOR_TEXTURE_5 = new ResourceLocation("aas", "textures/gui/radial_sector_5.png");
    private final Screen parentScreen;

    public DefenseRadialScreen(Screen parent) {
        super(Component.literal("Defense Menu"));
        this.parentScreen = parent;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gui);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        double dx = mouseX - centerX;
        double dy = mouseY - centerY;
        double distance = Math.sqrt(dx * dx + dy * dy);

        double angle = Math.toDegrees(Math.atan2(dy, dx)) + 90;
        if (angle < 0) angle += 360;

        int selected = -1;
        if (distance > 10) {
            double shiftedAngle = angle + 36;
            if (shiftedAngle >= 360) shiftedAngle -= 360;
            selected = (int) (shiftedAngle / 72);
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        PoseStack pose = gui.pose();

        int size = 95;

        for (int i = 0; i < 5; i++) {
            pose.pushPose();
            pose.translate(centerX, centerY, 0);
            pose.mulPose(Axis.ZP.rotationDegrees(i * 72));

            boolean isSelected = (i == selected);
            float scale = isSelected ? 1.15f : 1.0f;
            pose.scale(scale, scale, 1.0f);

            pose.translate(-size / 2.0f, -size, 0);

            if (isSelected) {
                RenderSystem.setShaderColor(0.4f, 1.0f, 0.4f, 1.0f);
            } else {
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            }

            gui.blit(SECTOR_TEXTURE_5, 0, 0, 0, 0, size, size, size, size);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

            pose.popPose();
        }

        // 0 = 1x1, 1 = 2x2, 2 = 3x3, 3 = WIRE, 4 = HUB
        drawLabel(gui, "1x1", centerX, centerY - 75, selected == 0);
        drawLabel(gui, "2x2", centerX + 70, centerY - 25, selected == 1);
        drawLabel(gui, "3x3", centerX + 45, centerY + 65, selected == 2);
        drawLabel(gui, "WIRE", centerX - 45, centerY + 65, selected == 3);
        drawLabel(gui, "HUB", centerX - 70, centerY - 25, selected == 4);
    }

    private void drawLabel(GuiGraphics gui, String text, int x, int y, boolean selected) {
        PoseStack pose = gui.pose();
        pose.pushPose();
        pose.translate(x, y, 0);

        float scale = selected ? 1.1f : 0.9f;
        pose.scale(scale, scale, 1.0f);

        int color = selected ? 0xFF00FF00 : 0xFFFFFFFF;
        int width = this.font.width(text);

        gui.drawString(this.font, text, -width / 2, -4, color, true);

        pose.popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) { // ЛКМ
            int centerX = this.width / 2;
            int centerY = this.height / 2;
            double dx = mouseX - centerX;
            double dy = mouseY - centerY;
            double distance = Math.sqrt(dx * dx + dy * dy);

            if (distance > 10) {
                double angle = Math.toDegrees(Math.atan2(dy, dx)) + 90;
                if (angle < 0) angle += 360;

                double shiftedAngle = angle + 36;
                if (shiftedAngle >= 360) shiftedAngle -= 360;

                int sector = (int) (shiftedAngle / 72);
                int actionId = 10 + sector;

                // === ЛОГИКА ВЫБОРА ===
                if (actionId == 10) {
                    ClientPlacementHandler.startPlacing(10); // Wall 1x1
                    this.onClose();
                }
                else if (actionId == 11) {
                    ClientPlacementHandler.startPlacing(11); // Wall 2x2
                    this.onClose();
                }
                else if (actionId == 12) {
                    ClientPlacementHandler.startPlacing(12); // Wall 3x3
                    this.onClose();
                }
                // ... В методе mouseClicked
                // selected == 3 (Сектор WIRE)
                else if (actionId == 13) {
                    ClientPlacementHandler.startPlacing(13); // ID 13 = Wire
                    this.onClose();
                }
                else {
                    PacketHandler.INSTANCE.sendToServer(new PacketRadioAction(actionId));
                    this.onClose();
                }
                return true;
            }
        }

        if (button == 1) { // ПКМ -> Назад
            Minecraft.getInstance().setScreen(parentScreen);
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }
}