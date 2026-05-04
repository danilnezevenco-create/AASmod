package com.example.aas.client.model;

import com.example.aas.item.EntrenchingToolItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import net.minecraft.util.Mth;

public class EntrenchingToolModel extends GeoModel<EntrenchingToolItem> {
    @Override public ResourceLocation getModelResource(EntrenchingToolItem animatable) { return new ResourceLocation("aas", "geo/entrenching_tool.geo.json"); }
    @Override public ResourceLocation getTextureResource(EntrenchingToolItem animatable) { return new ResourceLocation("aas", "textures/item/entrenching_tool.png"); }
    @Override public ResourceLocation getAnimationResource(EntrenchingToolItem animatable) { return new ResourceLocation("aas", "animations/entrenching_tool.animation.json"); }

}