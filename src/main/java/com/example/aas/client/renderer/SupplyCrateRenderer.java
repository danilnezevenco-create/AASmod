package com.example.aas.client.renderer;

import com.example.aas.block.ModBlocks;
import com.example.aas.entity.SupplyCrateEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

public class SupplyCrateRenderer extends EntityRenderer<SupplyCrateEntity> {

    public SupplyCrateRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.5f;
    }

    @Override
    public void render(SupplyCrateEntity entity, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();

        // 1. Поворачиваем ящик, чтобы он смотрел куда надо
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - entityYaw));

        // 2. Смещение.
        // Блоки рисуются от угла (0,0,0). Сущности от центра низа (0.5, 0, 0.5).
        // Сдвигаем на -0.5 по X и Z, чтобы ящик стоял ровно по центру.
        poseStack.translate(-0.5D, 0.0D, -0.5D);

        // 3. Берем состояние блока MAIN_SUPPLY (оно само подтянет твои JSON и текстуры)
        // Используем наш новый визуальный блок
        BlockState blockState = ModBlocks.SUPPLY_CRATE_VISUAL.get().defaultBlockState();

        // 4. Рендерим это состояние как блок
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        dispatcher.renderSingleBlock(blockState, poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY);

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(SupplyCrateEntity entity) {
        // Указываем атлас блоков, так как мы рендерим блок
        return new ResourceLocation("minecraft", "textures/atlas/blocks.png");
    }
}