package com.example.aas.client.model;

import com.example.aas.entity.M2BrowningEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

public class M2BrowningModel extends GeoModel<M2BrowningEntity> {

    private static final ResourceLocation WITH_MAG = new ResourceLocation("aas", "geo/m2_browning.geo.json");
    private static final ResourceLocation NO_MAG = new ResourceLocation("aas", "geo/m2_browning_no_magazin.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation("aas", "textures/entity/m2_browning.png");
    private static final ResourceLocation ANIMATION = new ResourceLocation("aas", "animations/m2_browning.animation.json");

    @Override
    public ResourceLocation getModelResource(M2BrowningEntity animatable) {
        return animatable.hasMagazine() ? WITH_MAG : NO_MAG;
    }

    @Override
    public ResourceLocation getTextureResource(M2BrowningEntity animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(M2BrowningEntity animatable) {
        return ANIMATION;
    }

    @Override
    public void setCustomAnimations(M2BrowningEntity animatable, long instanceId, AnimationState<M2BrowningEntity> animationState) {
        CoreGeoBone turret = getAnimationProcessor().getBone("turret_pivot");
        CoreGeoBone gun = getAnimationProcessor().getBone("gun_body");

        if (turret != null) {
            // Вращение башни (влево-вправо)
            turret.setRotY((float) Math.toRadians(-animatable.getTurretYaw()));
        }

        if (gun != null) {
            // === ИСПРАВЛЕНИЕ ВЕРТИКАЛЬНОГО ПОВОРОТА ===
            // 1. Используем ось Z (как вы просили ранее).
            // 2. Добавляем МИНУС (-animatable...), чтобы инвертировать направление.
            // Теперь Взгляд Вверх -> Ствол Вверх.
            gun.setRotZ((float) Math.toRadians(-animatable.getTurretPitch()));
        }
    }
}