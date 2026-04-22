package com.example.aas.client.renderer;

import com.example.aas.block.RallyPointBlockEntity;
import com.example.aas.client.model.ModelRallyPoint;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public class RallyPointRenderer implements BlockEntityRenderer<RallyPointBlockEntity> {
    private final ModelRallyPoint model;
    // Путь к текстуре
    private static final ResourceLocation TEXTURE = new ResourceLocation("aas", "textures/entity/rally_point.png");

    public RallyPointRenderer(BlockEntityRendererProvider.Context context) {
        // Создаем модель
        this.model = new ModelRallyPoint(ModelRallyPoint.createBodyLayer().bakeRoot());
    }

    @Override
    public void render(RallyPointBlockEntity entity, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        poseStack.pushPose();

        // Центрируем модель (0.5, 1.5, 0.5) и переворачиваем, так как в Blockbench Y идет вверх, а в Minecraft вниз
        poseStack.translate(0.5D, 1.5D, 0.5D);
        poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(180.0F));

        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        this.model.renderToBuffer(poseStack, vertexConsumer, packedLight, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);

        poseStack.popPose();
    }
}