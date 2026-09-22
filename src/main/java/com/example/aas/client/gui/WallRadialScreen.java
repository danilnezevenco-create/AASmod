package com.example.aas.client.gui;

import com.example.aas.client.ClientPlacementHandler;
import com.example.aas.item.RallyItem;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public class WallRadialScreen extends Screen {

    private static final ResourceLocation SECTOR_TEXTURE_5 = new ResourceLocation("aas", "textures/gui/radial_sector_5.png");
    private final Screen parentScreen;
    private boolean isSwitching = false;

    public WallRadialScreen(Screen parent) {
        super(Component.literal("Walls Menu"));
        this.parentScreen = parent;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        super.init();
        triggerRadioAnim("deploy");
    }

    @Override
    public void onClose() {
        if (!isSwitching) triggerRadioAnim("close");
        super.onClose();
    }

    private void triggerRadioAnim(String animName) {
        if (this.minecraft.player != null) {
            ItemStack stack = this.minecraft.player.getMainHandItem();
            if (stack.getItem() instanceof RallyItem radio) {
                long instanceId = stack.getOrCreateTag().getLong("GeckoLibID");
                radio.triggerAnim(this.minecraft.player, instanceId, "RadioController", animName);
            }
        }
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

            if (isSelected) RenderSystem.setShaderColor(0.4f, 1.0f, 0.4f, 1.0f);
            else RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

            gui.blit(SECTOR_TEXTURE_5, 0, 0, 0, 0, size, size, size, size);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            pose.popPose();
        }

        drawLabel(gui, "1x1 Wall", centerX, centerY - 75, selected == 0);
        drawLabel(gui, "2x2 Wall", centerX + 70, centerY - 25, selected == 1);
        drawLabel(gui, "3x3 Wall", centerX + 45, centerY + 65, selected == 2);
        drawLabel(gui, "Wire Wall", centerX - 45, centerY + 65, selected == 3);
        drawLabel(gui, "Loophole", centerX - 70, centerY - 25, selected == 4);
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
        if (button == 0) {
            int centerX = this.width / 2;
            int centerY = this.height / 2;
            double dx = mouseX - centerX;
            double dy = mouseY - centerY;
            if (Math.sqrt(dx * dx + dy * dy) > 10) {
                double angle = Math.toDegrees(Math.atan2(dy, dx)) + 90;
                if (angle < 0) angle += 360;
                double shiftedAngle = angle + 36;
                if (shiftedAngle >= 360) shiftedAngle -= 360;
                int sector = (int) (shiftedAngle / 72);

                if (sector == 0) ClientPlacementHandler.startPlacing(10);      // 1x1
                else if (sector == 1) ClientPlacementHandler.startPlacing(11); // 2x2
                else if (sector == 2) ClientPlacementHandler.startPlacing(12); // 3x3
                else if (sector == 3) ClientPlacementHandler.startPlacing(15); // Ступенька с колючкой
                else if (sector == 4) ClientPlacementHandler.startPlacing(16); // С амбразурой

                this.onClose();
                return true;
            }
        }
        if (button == 1) {
            Minecraft.getInstance().setScreen(parentScreen);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}