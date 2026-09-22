package com.example.aas.client.model;

import com.example.aas.item.OfficerPhoneItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

public class OfficerPhoneModel extends GeoModel<OfficerPhoneItem> {
    @Override
    public ResourceLocation getModelResource(OfficerPhoneItem animatable) {
        return new ResourceLocation("aas", "geo/officer_phone.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(OfficerPhoneItem animatable) {
        return new ResourceLocation("aas", "textures/item/officer_phone.png");
    }

    @Override
    public ResourceLocation getAnimationResource(OfficerPhoneItem animatable) {
        // Используем оригинальную анимацию рации
        return new ResourceLocation("aas", "animations/squad_leader_radio.animation.json");
    }

    @Override
    public void setCustomAnimations(OfficerPhoneItem animatable, long instanceId, AnimationState<OfficerPhoneItem> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);

        // Блокировка анимации от 3-го лица (как в оригинальной рации)
        if (Minecraft.getInstance().options.getCameraType() != CameraType.FIRST_PERSON) {
            CoreGeoBone root = getAnimationProcessor().getBone("root");
            if (root != null) {
                root.setPosX(0); root.setPosY(0); root.setPosZ(0);
                root.setRotX(0); root.setRotY(0); root.setRotZ(0);
            }

            CoreGeoBone body = getAnimationProcessor().getBone("body");
            if (body != null) {
                body.setPosX(0); body.setPosY(0); body.setPosZ(0);
                body.setRotX(0); body.setRotY(0); body.setRotZ(0);
            }
        }
    }
}