package com.example.aas.client.renderer;

import com.example.aas.client.model.BinocularsModel;
import com.example.aas.item.BinocularsItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class BinocularsRenderer extends GeoItemRenderer<BinocularsItem> {
    private static final ResourceLocation GUI_TEXTURE = new ResourceLocation("aas", "textures/item/binoculars_gui.png");

    public BinocularsRenderer() {
        super(new BinocularsModel());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        // 1. Рендерим 2D иконку в инвентаре
        if (displayContext == ItemDisplayContext.GUI) {
            render2DIcon(poseStack, bufferSource, packedOverlay);
            return;
        }

        // 2. Проверяем, смотрим ли мы от первого лица
        boolean isFirstPerson = displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND ||
                displayContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;

        if (isFirstPerson) {
            LocalPlayer player = Minecraft.getInstance().player;
            // Если игрок зажал ПКМ (использует бинокль) — отменяем рендер модели, чтобы она не закрывала экран
            if (player != null && player.isUsingItem() && player.getUseItem() == stack) {
                return;
            }
        }

        // 3. Во всех остальных случаях рисуем 3D модель (в руках, в руках у других игроков, на земле)
        super.renderByItem(stack, displayContext, poseStack, bufferSource, packedLight, packedOverlay);
    }

    private void render2DIcon(PoseStack poseStack, MultiBufferSource bufferSource, int packedOverlay) {
        poseStack.pushPose();
        VertexConsumer builder = bufferSource.getBuffer(RenderType.entityTranslucent(GUI_TEXTURE));
        Matrix4f matrix = poseStack.last().pose();
        int light = 15728880; // Full bright для интерфейса

        builder.vertex(matrix, 0, 1, 0).color(255, 255, 255, 255).uv(0, 0).overlayCoords(packedOverlay).uv2(light).normal(0, 0, 1).endVertex();
        builder.vertex(matrix, 1, 1, 0).color(255, 255, 255, 255).uv(1, 0).overlayCoords(packedOverlay).uv2(light).normal(0, 0, 1).endVertex();
        builder.vertex(matrix, 1, 0, 0).color(255, 255, 255, 255).uv(1, 1).overlayCoords(packedOverlay).uv2(light).normal(0, 0, 1).endVertex();
        builder.vertex(matrix, 0, 0, 0).color(255, 255, 255, 255).uv(0, 1).overlayCoords(packedOverlay).uv2(light).normal(0, 0, 1).endVertex();

        poseStack.popPose();
    }
}