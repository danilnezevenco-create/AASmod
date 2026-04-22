package com.example.aas.client.gui;

import com.example.aas.block.HubBlockEntity;
import com.example.aas.client.ClientData;
import com.example.aas.network.PacketHandler;
import com.example.aas.network.PacketRequestAmmo;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.example.aas.config.AASConfig;

public class HubRadialScreen extends Screen {

    private static final ResourceLocation SECTOR_TEXTURE = new ResourceLocation("aas", "textures/gui/radial_sector_5.png");
    private final BlockPos hubPos;

    private int cdAmmo = 0; // <--- НОВОЕ: КД для патронов
    private int cdAGS = 0;
    private int cdM2 = 0;
    private int cdMortar = 0;
    private int cdTOW = 0;
    private int materials = 0;

    public HubRadialScreen(BlockPos pos) {
        super(Component.literal("Hub Supply"));
        this.hubPos = pos;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private void updateData() {
        // === ВЫЧИСЛЕНИЕ КЛИЕНТСКОГО КУЛДАУНА ===
        long elapsed = System.currentTimeMillis() - ClientData.lastFobResupplyTime;
        if (elapsed < 60000 && !Minecraft.getInstance().player.isCreative()) {
            cdAmmo = (int) ((60000 - elapsed) / 50); // Перевод в тики (для визуала)
        } else {
            cdAmmo = 0;
        }

        if (Minecraft.getInstance().level != null) {
            BlockEntity be = Minecraft.getInstance().level.getBlockEntity(hubPos);
            if (be instanceof HubBlockEntity hub) {
                this.cdAGS = hub.cooldownAGS;
                this.cdM2 = hub.cooldownM2;
                this.cdMortar = hub.cooldownMortar;
                this.cdTOW = hub.cooldownTOW;
                this.materials = hub.getMaterials();
            }
        }
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gui);
        updateData();

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        String matText = "Mats: " + materials;
        gui.drawCenteredString(this.font, matText, centerX, centerY + 5, 0xFFFFAA00);

        double dx = mouseX - centerX;
        double dy = mouseY - centerY;
        double distance = Math.sqrt(dx * dx + dy * dy);

        int selected = -1;
        if (distance > 10) {
            double angle = Math.toDegrees(Math.atan2(dy, dx)) + 90;
            if (angle < 0) angle += 360;
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

            float r = 1.0f, g = 1.0f, b = 1.0f;
            boolean onCooldown = false;
            boolean noMats = false;

            if (i == 0) { // Ammo
                if (cdAmmo > 0) onCooldown = true;
                if (materials < AASConfig.HUB_RESUPPLY_COST.get()) noMats = true;
            } else if (i == 1) { // AGS
                if (cdAGS > 0) onCooldown = true;
                if (materials < 20) noMats = true;
            } else if (i == 2) { // M2
                if (cdM2 > 0) onCooldown = true;
                if (materials < 15) noMats = true;
            } else if (i == 3) { // Mortar
                if (cdMortar > 0) onCooldown = true;
                if (materials < 20) noMats = true;
            } else if (i == 4) { // TOW
                if (cdTOW > 0) onCooldown = true;
                if (materials < 50) noMats = true;
            }

            if (onCooldown || noMats) {
                r = 1.0f; g = 0.4f; b = 0.4f;
            } else if (isSelected) {
                r = 0.4f; g = 1.0f; b = 0.4f;
            }

            RenderSystem.setShaderColor(r, g, b, 1.0f);
            gui.blit(SECTOR_TEXTURE, 0, 0, 0, 0, size, size, size, size);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

            pose.popPose();
        }

        drawLabel(gui, "Resupply (" + AASConfig.HUB_RESUPPLY_COST.get() + ")", centerX, centerY - 75, selected == 0, cdAmmo);
        drawLabel(gui, "AGS-30 (20)", centerX + 70, centerY - 25, selected == 1, cdAGS);
        drawLabel(gui, "M2 (15)", centerX + 45, centerY + 65, selected == 2, cdM2);
        drawLabel(gui, "Mortar (20)", centerX - 45, centerY + 65, selected == 3, cdMortar);
        drawLabel(gui, "TOW (50)", centerX - 70, centerY - 25, selected == 4, cdTOW);
    }

    private void drawLabel(GuiGraphics gui, String text, int x, int y, boolean selected, int cooldownTicks) {
        int color = selected ? 0xFF00FF00 : 0xFFFFFFFF;

        if (cooldownTicks > 0) {
            text = (cooldownTicks / 20) + "s";
            color = 0xFFFF5555;
        }

        int width = this.font.width(text);
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
                double angle = Math.toDegrees(Math.atan2(dy, dx)) + 90;
                if (angle < 0) angle += 360;
                double shiftedAngle = angle + 36;
                if (shiftedAngle >= 360) shiftedAngle -= 360;

                int sector = (int) (shiftedAngle / 72);

                // === ЛОГИКА КУЛДАУНА ===
                if (sector == 0) {
                    if (cdAmmo > 0) return true; // Игнорируем клик, если КД

                    // Если есть маты, ставим КД
                    if (materials >= AASConfig.HUB_RESUPPLY_COST.get() || Minecraft.getInstance().player.isCreative()) {
                        ClientData.lastFobResupplyTime = System.currentTimeMillis();
                    }
                }

                PacketHandler.INSTANCE.sendToServer(new PacketRequestAmmo(hubPos, sector));
                this.onClose();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}