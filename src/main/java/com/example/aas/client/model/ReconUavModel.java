package com.example.aas.client.model;

import com.example.aas.entity.ReconUavEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** Placeholder model: the final geo/texture/animation files are replaced by the customer, paths stay the same. */
public class ReconUavModel extends GeoModel<ReconUavEntity> {
    private static final ResourceLocation MODEL = new ResourceLocation("aas", "geo/recon_uav.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation("aas", "textures/entity/recon_uav.png");
    private static final ResourceLocation ANIMATION = new ResourceLocation("aas", "animations/recon_uav.animation.json");

    @Override public ResourceLocation getModelResource(ReconUavEntity a) { return MODEL; }
    @Override public ResourceLocation getTextureResource(ReconUavEntity a) { return TEXTURE; }
    @Override public ResourceLocation getAnimationResource(ReconUavEntity a) { return ANIMATION; }
}
