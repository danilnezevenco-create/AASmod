package com.example.aas.client.renderer;

import com.example.aas.block.VehicleSpawnerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

public class VehicleSpawnerRenderer implements BlockEntityRenderer<VehicleSpawnerBlockEntity> {
    private final Font font;

    public VehicleSpawnerRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }
    private String formatTime(long seconds) {
        long m = seconds / 60;
        long s = seconds % 60;
        return String.format("%dм %dс", m, s);
    }
    @Override
    public void render(VehicleSpawnerBlockEntity entity, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (Minecraft.getInstance().player.distanceToSqr(entity.getBlockPos().getCenter()) > 4096) return;

        poseStack.pushPose();
        poseStack.translate(0.5, 1.5, 0.5);
        poseStack.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(-0.025F, -0.025F, 0.025F);
        // ИСПОЛЬЗУЕМ СИСТЕМНОЕ ВРЕМЯ НА КЛИЕНТЕ
        long sysTime = System.currentTimeMillis();
        // Вычисляем остаток: (Цель - Сейчас) / 1000 = секунды
        long timeLeft = Math.max(0, (entity.spawnTimestamp - sysTime) / 1000L);

        // === ВЕРХНИЙ ТЕКСТ (RESPAWN) ===
        String respawnText;
        int respawnColor = 0xFFFFFF;

        // Если таймер запущен (больше текущего времени) и техника уже спавнилась раньше
        if (entity.hasSpawnedOnce && entity.spawnTimestamp > sysTime) {
            respawnText = "Respawning: " + formatTime(timeLeft);
            respawnColor = 0xFFAA00; // Оранжевый
        } else {
            respawnText = "Respawn Set: " + formatTime(entity.respawnTimeSettings);
            respawnColor = 0xAAAAAA; // Серый
        }

        // === НИЖНИЙ ТЕКСТ (INITIAL) ===
        String initialText;
        int initialColor = 0xFFFFFF;

        // Если таймер запущен и техника еще НИ РАЗУ не спавнилась
        if (!entity.hasSpawnedOnce && entity.spawnTimestamp > sysTime) {
            initialText = "Starting: " + formatTime(timeLeft);
            initialColor = 0x55FF55; // Зеленый
        } else {
            initialText = "Initial Set: " + formatTime(entity.initialTimeSettings);
            initialColor = 0xAAAAAA; // Серый
        }

        Matrix4f matrix = poseStack.last().pose();
        float bgOpacity = 0.25f;

        float x1 = -font.width(respawnText) / 2.0f;
        font.drawInBatch(Component.literal(respawnText), x1, -10, respawnColor, false, matrix, buffer, Font.DisplayMode.SEE_THROUGH, (int)(bgOpacity * 255) << 24, packedLight);
        font.drawInBatch(Component.literal(respawnText), x1, -10, respawnColor, false, matrix, buffer, Font.DisplayMode.NORMAL, 0, packedLight);

        float x2 = -font.width(initialText) / 2.0f;
        font.drawInBatch(Component.literal(initialText), x2, 0, initialColor, false, matrix, buffer, Font.DisplayMode.SEE_THROUGH, (int)(bgOpacity * 255) << 24, packedLight);
        font.drawInBatch(Component.literal(initialText), x2, 0, initialColor, false, matrix, buffer, Font.DisplayMode.NORMAL, 0, packedLight);

        poseStack.popPose();
    }
}