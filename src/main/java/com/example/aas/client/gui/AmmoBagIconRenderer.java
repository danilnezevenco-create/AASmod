package com.example.aas.client.renderer;

import com.example.aas.block.AmmoBagBlockEntity;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * Рисует мигающую 3D-иконку над сумкой с патронами — по тому же принципу, что и
 * 3D-пинги (ClientEvents#render3DMarker): билборд, направленный на камеру, без учёта
 * теста глубины (виден через стены). Иконка видна ТОЛЬКО игрокам той же команды,
 * что и владелец сумки, и пропадает сама, как только блок сумки удаляется из мира
 * (забрали или она закончилась).
 */
public class AmmoBagIconRenderer implements BlockEntityRenderer<AmmoBagBlockEntity> {

    private static final ResourceLocation ICON_TEXTURE =
            new ResourceLocation("aas", "textures/gui/ammo_bag_marker.png");

    public AmmoBagIconRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(AmmoBagBlockEntity be, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        String myTeam = mc.player.getTeam() != null ? mc.player.getTeam().getName().toUpperCase() : "NEUTRAL";
        String bagTeam = be.getOwnerTeam();

        // Иконку видит только своя команда. Нейтралам (нет команды) ничего не показываем.
        if (myTeam.equals("NEUTRAL") || bagTeam == null || !bagTeam.equalsIgnoreCase(myTeam)) {
            return;
        }

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.9D, 0.5D);
        poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());

        long gameTime = mc.level.getGameTime();
        float alpha = 0.45f + (float) Math.sin((gameTime + partialTick) * 0.4f) * 0.25f;

        float scale = 0.02f;
        double distSq = mc.player.distanceToSqr(
                be.getBlockPos().getX() + 0.5,
                be.getBlockPos().getY() + 0.5,
                be.getBlockPos().getZ() + 0.5);
        if (distSq > 100) {
            scale *= (float) (Math.sqrt(distSq) / 10.0);
        }
        scale = Math.min(scale, 0.2f);
        poseStack.scale(-scale, -scale, scale);

        Matrix4f matrix = poseStack.last().pose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShaderTexture(0, ICON_TEXTURE);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buf = tesselator.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        int a = (int) (alpha * 255);
        buf.vertex(matrix, -8, -8, 0).uv(0, 0).color(255, 255, 255, a).endVertex();
        buf.vertex(matrix, -8, 8, 0).uv(0, 1).color(255, 255, 255, a).endVertex();
        buf.vertex(matrix, 8, 8, 0).uv(1, 1).color(255, 255, 255, a).endVertex();
        buf.vertex(matrix, 8, -8, 0).uv(1, 0).color(255, 255, 255, a).endVertex();

        tesselator.end();

        RenderSystem.enableDepthTest();
        RenderSystem.setShaderColor(1, 1, 1, 1);

        poseStack.popPose();
    }
}