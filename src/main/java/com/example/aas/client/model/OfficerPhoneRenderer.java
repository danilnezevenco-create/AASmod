package com.example.aas.client.renderer;

import com.example.aas.client.model.OfficerPhoneModel;
import com.example.aas.item.OfficerPhoneItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import software.bernie.geckolib.renderer.GeoItemRenderer;

public class OfficerPhoneRenderer extends GeoItemRenderer<OfficerPhoneItem> {
    private static final ResourceLocation GUI_TEXTURE = new ResourceLocation("aas", "textures/item/officer_phone_gui.png");

    public OfficerPhoneRenderer() {
        super(new OfficerPhoneModel());
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (displayContext == ItemDisplayContext.GUI) {
            render2DIcon(poseStack, bufferSource, packedOverlay);
        } else {
            super.renderByItem(stack, displayContext, poseStack, bufferSource, packedLight, packedOverlay);
        }
    }

    private void render2DIcon(PoseStack poseStack, MultiBufferSource bufferSource, int packedOverlay) {
        poseStack.pushPose();
        VertexConsumer builder = bufferSource.getBuffer(RenderType.entityTranslucent(GUI_TEXTURE));
        Matrix4f matrix = poseStack.last().pose();

        float min = 0.0f;
        float max = 1.0f;
        float z = 0.0f;
        int light = 15728880;

        builder.vertex(matrix, min, max, z).color(255, 255, 255, 255).uv(0, 0).overlayCoords(packedOverlay).uv2(light).normal(0, 0, 1).endVertex();
        builder.vertex(matrix, max, max, z).color(255, 255, 255, 255).uv(1, 0).overlayCoords(packedOverlay).uv2(light).normal(0, 0, 1).endVertex();
        builder.vertex(matrix, max, min, z).color(255, 255, 255, 255).uv(1, 1).overlayCoords(packedOverlay).uv2(light).normal(0, 0, 1).endVertex();
        builder.vertex(matrix, min, min, z).color(255, 255, 255, 255).uv(0, 1).overlayCoords(packedOverlay).uv2(light).normal(0, 0, 1).endVertex();

        poseStack.popPose();
    }
}