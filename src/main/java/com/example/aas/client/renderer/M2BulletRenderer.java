package com.example.aas.client.renderer;

import com.example.aas.entity.M2BulletEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

public class M2BulletRenderer extends EntityRenderer<M2BulletEntity> {
    private static final ResourceLocation TEXTURE = new ResourceLocation("aas", "textures/entity/m2_bullet.png");

    public M2BulletRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(M2BulletEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();

        // Поворот по направлению движения
        poseStack.mulPose(Axis.YP.rotationDegrees(entity.getYRot() - 90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(entity.getXRot()));

        // Светящийся рендер (ignore packedLight, use 255)
        // Используем eyes, чтобы светилось в темноте
        VertexConsumer vertexConsumer = buffer.getBuffer(RenderType.eyes(TEXTURE));

        PoseStack.Pose pose = poseStack.last();
        Matrix4f poseMatrix = pose.pose();
        Matrix3f normalMatrix = pose.normal();

        // Рисуем длинный прямоугольник (трассер)
        // Длина 2 блока, ширина 0.05
        float len = 2.0f;
        float width = 0.05f;

        vertex(vertexConsumer, poseMatrix, normalMatrix, -len, 0, -width, 0, 0);
        vertex(vertexConsumer, poseMatrix, normalMatrix, -len, 0, width, 0, 1);
        vertex(vertexConsumer, poseMatrix, normalMatrix, len, 0, width, 1, 1);
        vertex(vertexConsumer, poseMatrix, normalMatrix, len, 0, -width, 1, 0);

        // Вертикальная плоскость (крестом), чтобы было видно сбоку
        poseStack.mulPose(Axis.XP.rotationDegrees(90));
        vertex(vertexConsumer, poseMatrix, normalMatrix, -len, 0, -width, 0, 0);
        vertex(vertexConsumer, poseMatrix, normalMatrix, -len, 0, width, 0, 1);
        vertex(vertexConsumer, poseMatrix, normalMatrix, len, 0, width, 1, 1);
        vertex(vertexConsumer, poseMatrix, normalMatrix, len, 0, -width, 1, 0);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, Matrix3f normal, float x, float y, float z, float u, float v) {
        consumer.vertex(pose, x, y, z)
                .color(255, 255, 255, 255) // Белый цвет
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(15728880) // Максимальный свет (Full Bright)
                .normal(normal, 0, 1, 0)
                .endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(M2BulletEntity entity) {
        return TEXTURE;
    }
}