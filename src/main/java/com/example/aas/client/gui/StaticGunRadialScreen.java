package com.example.aas.client.gui;

import com.example.aas.client.ClientPlacementHandler;
import com.example.aas.item.RallyItem; // Добавлен импорт
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack; // Добавлен импорт
import software.bernie.geckolib.animatable.GeoItem;

public class StaticGunRadialScreen extends Screen {

    private static final ResourceLocation SECTOR_TEXTURE = new ResourceLocation("aas", "textures/gui/radial_sector_4.png");
    private final Screen parentScreen;
    private boolean isSwitching = false;

    public StaticGunRadialScreen(Screen parent) {
        super(Component.literal("Static Guns"));
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
        if (!isSwitching) {
            triggerRadioAnim("close");
        }
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

        // 0=Right, 1=Down, 2=Left, 3=Up
        int selected = -1;
        if (distance > 10) {
            double angle = Math.toDegrees(Math.atan2(dy, dx)); // -180 to 180
            if (angle < 0) angle += 360; // 0 to 360

            // Сектора по 90 градусов
            if (angle >= 45 && angle < 135) selected = 1;      // Down
            else if (angle >= 135 && angle < 225) selected = 2; // Left
            else if (angle >= 225 && angle < 315) selected = 3; // Up
            else selected = 0;                                  // Right
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        PoseStack pose = gui.pose();
        int size = 95;

        // Рисуем 4 сектора
        for (int i = 0; i < 4; i++) {
            pose.pushPose();
            pose.translate(centerX, centerY, 0);

            // i=0(Right) -> rot 90
            // i=1(Down) -> rot 180
            // i=2(Left) -> rot 270 (-90)
            // i=3(Up) -> rot 0

            float rot = 0;
            if (i==0) rot = 90;
            if (i==1) rot = 180;
            if (i==2) rot = -90;
            if (i==3) rot = 0;

            pose.mulPose(Axis.ZP.rotationDegrees(rot));

            boolean isSelected = (i == selected);
            float scale = isSelected ? 1.15f : 1.0f;
            pose.scale(scale, scale, 1.0f);

            // Сдвиг, предполагая, что текстура radial_sector_4.png - это "четверть круга"
            pose.translate(-size / 2.0f, -size, 0);

            if (isSelected) RenderSystem.setShaderColor(0.4f, 1.0f, 0.4f, 1.0f);
            else RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

            gui.blit(SECTOR_TEXTURE, 0, 0, 0, 0, size, size, size, size);

            pose.popPose();
        }

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        // === ИЗМЕНЕНИЕ 2: Убраны приписки (Right, Down и т.д.) ===
        drawLabel(gui, "M2 Browning", centerX + 60, centerY, selected == 0);
        drawLabel(gui, "Mortar", centerX, centerY + 60, selected == 1);
        drawLabel(gui, "AGS-30", centerX - 60, centerY, selected == 2);
        drawLabel(gui, "TOW", centerX, centerY - 60, selected == 3);
    }

    private void drawLabel(GuiGraphics gui, String text, int x, int y, boolean selected) {
        int color = selected ? 0xFF00FF00 : 0xFFFFFFFF;
        int width = this.font.width(text);
        // Центрируем текст
        gui.drawString(this.font, text, x - (width / 2), y - 4, color, true);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int centerX = this.width / 2;
            int centerY = this.height / 2;
            double dx = mouseX - centerX;
            double dy = mouseY - centerY;
            double dist = Math.sqrt(dx * dx + dy * dy);

            if (dist > 10) {
                double angle = Math.toDegrees(Math.atan2(dy, dx));
                if (angle < 0) angle += 360;

                int selected = 0;
                if (angle >= 45 && angle < 135) selected = 1;
                else if (angle >= 135 && angle < 225) selected = 2;
                else if (angle >= 225 && angle < 315) selected = 3;
                else selected = 0;

                if (selected == 0) ClientPlacementHandler.startPlacing(20); // M2
                if (selected == 1) ClientPlacementHandler.startPlacing(22); // Mortar
                if (selected == 2) ClientPlacementHandler.startPlacing(21); // AGS
                if (selected == 3) ClientPlacementHandler.startPlacing(23); // TOW

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