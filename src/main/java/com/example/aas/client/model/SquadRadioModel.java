package com.example.aas.client.model;

import com.example.aas.item.RallyItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

public class SquadRadioModel extends GeoModel<RallyItem> {
    @Override
    public ResourceLocation getModelResource(RallyItem animatable) {
        return new ResourceLocation("aas", "geo/squad_leader_radio.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(RallyItem animatable) {
        return new ResourceLocation("aas", "textures/item/squad_leader_radio.png");
    }

    @Override
    public ResourceLocation getAnimationResource(RallyItem animatable) {
        return new ResourceLocation("aas", "animations/squad_leader_radio.animation.json");
    }

    // === БЛОКИРОВКА АНИМАЦИИ ДЛЯ 3-ГО ЛИЦА ===
    @Override
    public void setCustomAnimations(RallyItem animatable, long instanceId, AnimationState<RallyItem> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);

        // Проверяем, в каком режиме сейчас камера
        // Если это НЕ вид от первого лица (т.е. F5 или мы смотрим на другого игрока)
        if (Minecraft.getInstance().options.getCameraType() != CameraType.FIRST_PERSON) {

            // Нам нужно найти основную кость, которую мы двигали в анимации.
            // Обычно это "root" или "body".
            // Мы принудительно сбрасываем её позицию и поворот в 0.
            CoreGeoBone root = getAnimationProcessor().getBone("root");
            if (root != null) {
                root.setPosX(0);
                root.setPosY(0);
                root.setPosZ(0);
                root.setRotX(0);
                root.setRotY(0);
                root.setRotZ(0);
            }

            // Если у тебя анимирована кость "body", добавь и её:
            CoreGeoBone body = getAnimationProcessor().getBone("body");
            if (body != null) {
                body.setPosX(0);
                body.setPosY(0);
                body.setPosZ(0);
                body.setRotX(0);
                body.setRotY(0);
                body.setRotZ(0);
            }
        }
    }
}