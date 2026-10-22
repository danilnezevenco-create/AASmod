package com.example.aas.client.renderer;

import com.example.aas.client.model.ReconUavModel;
import com.example.aas.client.uav.UavViewClient;
import com.example.aas.entity.ReconUavEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class ReconUavRenderer extends GeoEntityRenderer<ReconUavEntity> {
    public ReconUavRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new ReconUavModel());
        this.shadowRadius = 0f;
    }

    /** No name plate above the UAV (the entity also returns an empty display name as a second guard). */
    protected void renderNameTag(ReconUavEntity e, net.minecraft.network.chat.Component name,
                                 com.mojang.blaze3d.vertex.PoseStack pose,
                                 net.minecraft.client.renderer.MultiBufferSource buffers, int light) { }

    /** The camera sits inside the UAV while looking through it: do not draw our own model around the lens. */
    @Override
    public boolean shouldRender(ReconUavEntity e, Frustum frustum, double camX, double camY, double camZ) {
        if (UavViewClient.isViewing() && e.distanceToSqr(camX, camY, camZ) < 64.0) return false;
        return super.shouldRender(e, frustum, camX, camY, camZ);
    }
}
