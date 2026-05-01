package com.example.aas.client.renderer;

import com.example.aas.client.model.SquadRadioModel;
import com.example.aas.item.RallyItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class SquadRadioRenderer extends GeoItemRenderer<RallyItem> {
    // Путь к вашей 2D иконке
    private static final ResourceLocation GUI_TEXTURE = new ResourceLocation("aas", "textures/item/squad_leader_radio_gui.png");

    public SquadRadioRenderer() {
        super(new SquadRadioModel());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        // Если это GUI (инвентарь, хотбар)
        if (displayContext == ItemDisplayContext.GUI) {
            render2DIcon(poseStack, bufferSource, packedOverlay);
        } else {
            // Во всех остальных случаях (в руках, на земле, в рамке) рисуем 3D GeckoLib модель
            super.renderByItem(stack, displayContext, poseStack, bufferSource, packedLight, packedOverlay);
        }
    }

    private void render2DIcon(PoseStack poseStack, MultiBufferSource bufferSource, int packedOverlay) {
        poseStack.pushPose();

        // Подключаем текстуру
        VertexConsumer builder = bufferSource.getBuffer(RenderType.entityTranslucent(GUI_TEXTURE));
        Matrix4f matrix = poseStack.last().pose();

        // Координаты квадрата (от 0 до 1 по X и Y)
        float min = 0.0f;
        float max = 1.0f;
        float z = 0.0f; // Глубина
        int light = 15728880; // Максимальная яркость для GUI

        // Отрисовка 4 вершин (плоская картинка)
        // Координаты UV настроены так, чтобы картинка не была перевернутой
        builder.vertex(matrix, min, max, z).color(255, 255, 255, 255).uv(0, 0).overlayCoords(packedOverlay).uv2(light).normal(0, 0, 1).endVertex();
        builder.vertex(matrix, max, max, z).color(255, 255, 255, 255).uv(1, 0).overlayCoords(packedOverlay).uv2(light).normal(0, 0, 1).endVertex();
        builder.vertex(matrix, max, min, z).color(255, 255, 255, 255).uv(1, 1).overlayCoords(packedOverlay).uv2(light).normal(0, 0, 1).endVertex();
        builder.vertex(matrix, min, min, z).color(255, 255, 255, 255).uv(0, 1).overlayCoords(packedOverlay).uv2(light).normal(0, 0, 1).endVertex();

        poseStack.popPose();
    }
}