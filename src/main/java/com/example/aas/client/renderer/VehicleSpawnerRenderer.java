package com.example.aas.client.renderer;

import com.example.aas.block.VehicleSpawnerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.client.resources.language.I18n;
import org.joml.Matrix4f;

public class VehicleSpawnerRenderer implements BlockEntityRenderer<VehicleSpawnerBlockEntity> {
    private final Font font;

    public VehicleSpawnerRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    private String formatTime(long seconds) {
        long m = seconds / 60;
        long s = seconds % 60;
        return I18n.get("aas.hud.vehicle_spawner.time_format", m, s);
    }

    @Override
    public void render(VehicleSpawnerBlockEntity entity, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (Minecraft.getInstance().player.distanceToSqr(entity.getBlockPos().getCenter()) > 4096) return;

        poseStack.pushPose();
        poseStack.translate(0.5, 2.5, 0.5);
        poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-0.025F, -0.025F, 0.025F);

        // --- ЛОГИКА ВРЕМЕНИ ПО ТИКАМ ---
        long currentTick = entity.getLevel().getGameTime();
        // (Целевой тик - Текущий тик) / 20 тиков = секунды
        long timeLeft = Math.max(0, (entity.targetSpawnTick - currentTick) / 20);

        // === ВЕРХНИЙ ТЕКСТ (RESPAWN) ===
        String respawnText;
        int respawnColor;

        // Если таймер запущен (целевой тик в будущем) и техника уже спавнилась раньше
        if (entity.hasSpawnedOnce && entity.targetSpawnTick > currentTick) {
            respawnText = I18n.get("aas.hud.vehicle_spawner.respawning", formatTime(timeLeft));
            respawnColor = 0xFFAA00; // Оранжевый
        } else {
            respawnText = I18n.get("aas.hud.vehicle_spawner.respawn_set", formatTime(entity.respawnTimeSettings));
            respawnColor = 0xAAAAAA; // Серый
        }

        // === НИЖНИЙ ТЕКСТ (INITIAL) ===
        // Актуален только ДО первого спавна техники. После первого спавна
        // "Initial Set" не несёт смысла — просто не рисуем эту строку.
        String initialText = null;
        int initialColor = 0xAAAAAA;

        if (!entity.hasSpawnedOnce) {
            if (entity.targetSpawnTick > currentTick) {
                initialText = I18n.get("aas.hud.vehicle_spawner.starting", formatTime(timeLeft));
                initialColor = 0x55FF55; // Зелёный
            } else {
                initialText = I18n.get("aas.hud.vehicle_spawner.initial_set", formatTime(entity.initialTimeSettings));
                initialColor = 0xAAAAAA; // Серый
            }
        }

        // --- ОТРИСОВКА ---
        Matrix4f matrix = poseStack.last().pose();
        float bgOpacity = 0.25f;

        float x1 = -font.width(respawnText) / 2.0f;
        font.drawInBatch(Component.literal(respawnText), x1, -10, respawnColor, false, matrix, buffer, Font.DisplayMode.SEE_THROUGH, (int) (bgOpacity * 255) << 24, packedLight);
        font.drawInBatch(Component.literal(respawnText), x1, -10, respawnColor, false, matrix, buffer, Font.DisplayMode.NORMAL, 0, packedLight);

        if (initialText != null) {
            float x2 = -font.width(initialText) / 2.0f;
            font.drawInBatch(Component.literal(initialText), x2, 0, initialColor, false, matrix, buffer, Font.DisplayMode.SEE_THROUGH, (int) (bgOpacity * 255) << 24, packedLight);
            font.drawInBatch(Component.literal(initialText), x2, 0, initialColor, false, matrix, buffer, Font.DisplayMode.NORMAL, 0, packedLight);
        }

        poseStack.popPose();
    }
}