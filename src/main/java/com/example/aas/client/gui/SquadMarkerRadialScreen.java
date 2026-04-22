package com.example.aas.client.gui;

import com.example.aas.network.PacketHandler;
import com.example.aas.network.PacketSquadMarker;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class SquadMarkerRadialScreen extends Screen {
    private static final ResourceLocation SECTOR_3 = new ResourceLocation("aas", "textures/gui/radial_sector.png"); // 120 град
    private static final ResourceLocation SECTOR_4 = new ResourceLocation("aas", "textures/gui/radial_sector_4.png"); // 90 град

    private final int targetX, targetZ;
    private int currentLayer = 1; // 0 - Главное (3 кнопки), 1 - Отряд (4 кнопки)

    public SquadMarkerRadialScreen(int x, int z) {
        super(Component.literal("Markers"));
        this.targetX = x;
        this.targetZ = z;
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gui);
        int centerX = width / 2;
        int centerY = height / 2;
        double dx = mouseX - centerX;
        double dy = mouseY - centerY;
        double dist = Math.sqrt(dx * dx + dy * dy);

        if (currentLayer == 0) {
            renderMainLayer(gui, dx, dy, dist, centerX, centerY);
        } else {
            renderSquadLayer(gui, dx, dy, dist, centerX, centerY);
        }
    }

    private void renderMainLayer(GuiGraphics gui, double dx, double dy, double dist, int cx, int cy) {
        double angle = Math.toDegrees(Math.atan2(dy, dx)) + 90;
        if (angle < 0) angle += 360;

        int selected = -1;
        if (dist > 10) {
            if (angle > 300 || angle <= 60) selected = 0; // Up
            else if (angle > 60 && angle <= 180) selected = 1; // Right-Down
            else selected = 2; // Left-Down
        }

        for (int i = 0; i < 3; i++) {
            gui.pose().pushPose();
            gui.pose().translate(cx, cy, 0);
            gui.pose().mulPose(Axis.ZP.rotationDegrees(i * 120));
            if (i == selected) RenderSystem.setShaderColor(0.4f, 1.0f, 0.4f, 1.0f);
            else RenderSystem.setShaderColor(1, 1, 1, 1);
            gui.pose().translate(-47.5, -95, 0);
            gui.blit(SECTOR_3, 0, 0, 0, 0, 95, 95, 95, 95);
            gui.pose().popPose();
        }
        RenderSystem.setShaderColor(1, 1, 1, 1);
        drawLabel(gui, "TEAM", cx, cy - 70, selected == 0, 0xFF55FF55);
        drawLabel(gui, "ENEMY", cx + 60, cy + 30, selected == 1, 0xFFFF5555);
        drawLabel(gui, "SQUAD", cx - 60, cy + 30, selected == 2, 0xFFFFFF55);
    }

    private void renderSquadLayer(GuiGraphics gui, double dx, double dy, double dist, int cx, int cy) {
        double angle = Math.toDegrees(Math.atan2(dy, dx));
        if (angle < 0) angle += 360;
        int selected = -1;
        if (dist > 10) {
            if (angle >= 45 && angle < 135) selected = 1;      // Down (Attack)
            else if (angle >= 135 && angle < 225) selected = 2; // Left (Defend)
            else if (angle >= 225 && angle < 315) selected = 3; // Up (Build)
            else selected = 0;                                  // Right (Move)
        }

        for (int i = 0; i < 4; i++) {
            gui.pose().pushPose();
            gui.pose().translate(cx, cy, 0);
            float rot = (i == 0) ? 90 : (i == 1) ? 180 : (i == 2) ? -90 : 0;
            gui.pose().mulPose(Axis.ZP.rotationDegrees(rot));
            if (i == selected) RenderSystem.setShaderColor(0.4f, 1.0f, 0.4f, 1.0f);
            else RenderSystem.setShaderColor(1, 1, 1, 1);
            gui.pose().translate(-47.5, -95, 0);
            gui.blit(SECTOR_4, 0, 0, 0, 0, 95, 95, 95, 95);
            gui.pose().popPose();
        }
        RenderSystem.setShaderColor(1, 1, 1, 1);
        drawLabel(gui, "MOVE", cx + 60, cy, selected == 0, 0xFF55FF55);
        drawLabel(gui, "ATTACK", cx, cy + 60, selected == 1, 0xFFFFAA00);
        drawLabel(gui, "DEFEND", cx - 60, cy, selected == 2, 0xFF5555FF);
        drawLabel(gui, "BUILD", cx, cy - 60, selected == 3, 0xFFFF55FF);
    }

    private void drawLabel(GuiGraphics gui, String text, int x, int y, boolean sel, int color) {
        gui.drawCenteredString(font, text, x, y - 4, sel ? 0xFFFFFFFF : color);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        int cx = width / 2, cy = height / 2;
        double dx = mx - cx, dy = my - cy;
        double dist = Math.sqrt(dx * dx + dy * dy);

        if (button == 0 && dist > 10) {
            if (currentLayer == 0) {
                double angle = Math.toDegrees(Math.atan2(dy, dx)) + 90;
                if (angle < 0) angle += 360;
                int sel = (angle > 300 || angle <= 60) ? 0 : (angle > 60 && angle <= 180) ? 1 : 2;

                if (sel == 2) { // Нажали на SQUAD
                    currentLayer = 1;
                    return true;
                } else {
                    // Логика для TEAM и ENEMY (пока можно просто закрывать или слать типы 4 и 5)
                    PacketHandler.INSTANCE.sendToServer(new PacketSquadMarker(targetX, targetZ, sel + 4));
                    this.minecraft.setScreen(new com.example.aas.client.gui.SquadSelectionScreen());
                }
            } else {
                double angle = Math.toDegrees(Math.atan2(dy, dx));
                if (angle < 0) angle += 360;
                int type = (angle >= 45 && angle < 135) ? 1 : (angle >= 135 && angle < 225) ? 2 : (angle >= 225 && angle < 315) ? 3 : 0;
                PacketHandler.INSTANCE.sendToServer(new PacketSquadMarker(targetX, targetZ, type));
                this.minecraft.setScreen(new com.example.aas.client.gui.SquadSelectionScreen());
            }
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }
    @Override public boolean isPauseScreen() { return false; }
}