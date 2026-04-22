package com.example.aas.client.model;
import com.example.aas.entity.AGS30Entity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
public class AGS30Model extends GeoModel<AGS30Entity> {
    private static final ResourceLocation WITH_MAG = new ResourceLocation("aas", "geo/ags_30.geo.json");
    private static final ResourceLocation NO_MAG = new ResourceLocation("aas", "geo/ags_30_no_mag.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation("aas", "textures/entity/ags_30.png");
    private static final ResourceLocation ANIMATION = new ResourceLocation("aas", "animations/ags_30.animation.json");

    @Override
    public ResourceLocation getModelResource(AGS30Entity animatable) {
        return animatable.hasMagazine() ? WITH_MAG : NO_MAG;
    }

    @Override
    public ResourceLocation getTextureResource(AGS30Entity animatable) { return TEXTURE; }

    @Override
    public ResourceLocation getAnimationResource(AGS30Entity animatable) { return ANIMATION; }

    @Override
    public void setCustomAnimations(AGS30Entity animatable, long instanceId, AnimationState<AGS30Entity> animationState) {
        CoreGeoBone turret = getAnimationProcessor().getBone("turret_pivot"); // Имя кости башни
        CoreGeoBone gun = getAnimationProcessor().getBone("gun_body");       // Имя кости пушки

        if (turret != null) {
            // Поворот башни влево/вправо
            turret.setRotY((float) Math.toRadians(-animatable.getTurretYaw()));
        }
        if (gun != null) {
            // ИСПРАВЛЕНИЕ: Убран знак минуса перед animatable.getTurretPitch()
            // Теперь: Игрок смотрит вверх (-45) -> Модель получает (-45) и поворачивается вверх
            gun.setRotX((float) Math.toRadians(animatable.getTurretPitch()));
        }
    }
}