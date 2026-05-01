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

    @Override
    public void setCustomAnimations(EntrenchingToolItem animatable, long instanceId, AnimationState<EntrenchingToolItem> animationState) {
        super.setCustomAnimations(animatable, instanceId, animationState);
        CoreGeoBone body = getAnimationProcessor().getBone("body");
        if (body == null) return;

        if (animationState.getData(DataTickets.ENTITY) instanceof LivingEntity living) {
            // Проверяем использование предмета
            if (living.isUsingItem() && living.getUseItem().getItem() instanceof EntrenchingToolItem) {

                // Длительность 1 сек = 20 тиков. Получаем текущий прогресс 0.0 - 1.0
                float ticks = (living.getTicksUsingItem() + animationState.getPartialTick());
                float t = (ticks % 20f) / 20f;

                float rx = 0, rz = 0;
                float px = 0, py = 0, pz = 0;

                // ФАЗА 1: 0.0 -> 0.25 (Замах назад и вниз)
                if (t < 0.25f) {
                    float p = t / 0.25f;
                    rz = p * -50f;
                    py = p * -10f;
                }
                // ФАЗА 2: 0.25 -> 0.5 (УДАР И ПЕРЕВОРОТ)
                else if (t < 0.50f) {
                    float p = (t - 0.25f) / 0.25f;
                    rx = p * 70f; // Переворот по X
                    rz = -50f;
                    px = p * -1.5f;
                    py = -10f + (p * 3.75f); // Подъем от -10 до -6.25
                    pz = p * 6f; // Выпад вперед
                }
                // ФАЗА 3: 0.5 -> 0.75 (ВОЗВРАТ С УСКОРЕНИЕМ - easeIn)
                else if (t < 0.75f) {
                    float p = (t - 0.50f) / 0.25f;
                    float easedCubic = p * p * p; // Твой easeInCubic
                    float easedQuart = easedCubic * p; // Твой easeInQuart

                    rx = Mth.lerp(easedCubic, 70f, 0f);
                    rz = Mth.lerp(easedCubic, -50f, 25f);

                    px = Mth.lerp(easedQuart, -1.5f, 2.25f);
                    py = Mth.lerp(easedQuart, -6.25f, 0.75f);
                    pz = Mth.lerp(easedQuart, 6f, 0f);
                }
                // ФАЗА 4: 0.75 -> 1.0 (Успокоение)
                else {
                    float p = (t - 0.75f) / 0.25f;
                    rz = Mth.lerp(p, 25f, 0f);
                    px = Mth.lerp(p, 2.25f, 0f);
                    py = Mth.lerp(p, 0.75f, 0f);
                }

                // ПРИМЕНЕНИЕ (Важно: вращение в радианах, позиции в единицах Blockbench)
                body.setRotX((float) Math.toRadians(rx));
                body.setRotZ((float) Math.toRadians(rz));

                body.setPosX(px);
                body.setPosY(py);
                body.setPosZ(pz);

            } else {
                // Если не копаем - плавно сбрасываем в 0
                body.setRotX(0); body.setRotZ(0);
                body.setPosX(0); body.setPosY(0); body.setPosZ(0);
            }
        }
    }
}