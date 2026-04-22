package com.example.aas.client.gui;

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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public class RadioRadialScreen extends Screen {

    private static final ResourceLocation SECTOR_TEXTURE = new ResourceLocation("aas", "textures/gui/radial_sector.png");

    public RadioRadialScreen() {
        super(Component.literal("Radio Menu"));
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

        boolean rallyOnCooldown = false;
        long secondsLeft = 0;

        Player player = Minecraft.getInstance().player;
        if (player != null) {
            ItemStack stack = player.getMainHandItem();
            long cooldownEnd = stack.getOrCreateTag().getLong("RallyCooldownEnd");
            long gameTime = player.level().getGameTime();

            if (gameTime < cooldownEnd) {
                rallyOnCooldown = true;
                secondsLeft = (cooldownEnd - gameTime) / 20;
            }
        }

        double dx = mouseX - centerX;
        double dy = mouseY - centerY;
        double distance = Math.sqrt(dx * dx + dy * dy);

        double angle = Math.toDegrees(Math.atan2(dy, dx)) + 90;
        if (angle < 0) angle += 360;

        int selected = -1;
        if (distance > 10) {
            if (angle > 300 || angle <= 60) selected = 0;
            else if (angle > 60 && angle <= 180) selected = 1;
            else if (angle > 180 && angle <= 300) selected = 2;
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        PoseStack pose = gui.pose();

        int size = 95;

        for (int i = 0; i < 3; i++) {
            pose.pushPose();
            pose.translate(centerX, centerY, 0);
            pose.mulPose(Axis.ZP.rotationDegrees(i * 120));

            boolean isSelected = (i == selected);
            float scale = isSelected ? 1.15f : 1.0f;
            pose.scale(scale, scale, 1.0f);

            pose.translate(-size / 2.0f, -size, 0);

            if (i == 0 && rallyOnCooldown) {
                RenderSystem.setShaderColor(1.0f, 0.4f, 0.4f, 1.0f);
            } else if (isSelected) {
                RenderSystem.setShaderColor(0.4f, 1.0f, 0.4f, 1.0f);
            } else {
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            }

            gui.blit(SECTOR_TEXTURE, 0, 0, 0, 0, size, size, size, size);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

            pose.popPose();
        }

        String rallyText = "RALLY POINT";
        int rallyColor = (selected == 0) ? 0xFF00FF00 : 0xFFFFFFFF;

        if (rallyOnCooldown) {
            rallyText = "WAIT: " + secondsLeft + "s";
            rallyColor = 0xFFFF5555;
        }

        drawLabel(gui, rallyText, centerX, centerY - 70, selected == 0, rallyColor);
        drawLabel(gui, "DEFENSES", centerX + 60, centerY + 35, selected == 1, (selected == 1 ? 0xFF00FF00 : 0xFFFFFFFF));
        drawLabel(gui, "STATIC GUN", centerX - 60, centerY + 35, selected == 2, (selected == 2 ? 0xFF00FF00 : 0xFFFFFFFF));
    }

    private void drawLabel(GuiGraphics gui, String text, int x, int y, boolean selected, int color) {
        PoseStack pose = gui.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        float textScale = selected ? 1.1f : 0.9f;
        pose.scale(textScale, textScale, 1.0f);
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
            double distance = Math.sqrt(dx * dx + dy * dy);

            if (distance > 10) {
                double angle = Math.toDegrees(Math.atan2(dy, dx)) + 90;
                if (angle < 0) angle += 360;

                int action = -1;
                if (angle > 300 || angle <= 60) action = 0;
                else if (angle > 60 && angle <= 180) action = 1;
                else if (angle > 180 && angle <= 300) action = 2;

                // === ВОТ ЗДЕСЬ БЫЛА ОШИБКА ===
                if (action == 0) {
                    // Rally -> Пакет
                    PacketHandler.INSTANCE.sendToServer(new PacketRadioAction(0));
                    this.onClose();
                }
                else if (action == 1) {
                    // Defenses -> ОТКРЫВАЕМ НОВОЕ МЕНЮ (Пакет НЕ шлем)
                    Minecraft.getInstance().setScreen(new DefenseRadialScreen(this));
                    // Не делаем this.onClose(), setScreen сам заменит текущий экран
                }
                else if (action == 2) {
                    // Gun -> ОТКРЫВАЕМ НОВОЕ МЕНЮ
                    Minecraft.getInstance().setScreen(new StaticGunRadialScreen(this));
                    // this передаем, чтобы знать, куда вернуться при нажатии "Назад"
                }

                if (action != -1) return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}