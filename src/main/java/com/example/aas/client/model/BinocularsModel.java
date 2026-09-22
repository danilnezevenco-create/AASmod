package com.example.aas.client.model;

import com.example.aas.item.BinocularsItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class BinocularsModel extends GeoModel<BinocularsItem> {
    @Override
    public ResourceLocation getModelResource(BinocularsItem animatable) {
        return new ResourceLocation("aas", "geo/binoculars.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(BinocularsItem animatable) {
        return new ResourceLocation("aas", "textures/item/binoculars.png");
    }

    @Override
    public ResourceLocation getAnimationResource(BinocularsItem animatable) {
        return new ResourceLocation("aas", "animations/binoculars.animation.json"); // можно оставить пустым json
    }
}