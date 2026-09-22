package com.example.aas.mixin;

import com.example.aas.entity.AGS30Entity;
import com.example.aas.entity.M2BrowningEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.OptionInstance;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MouseHandler.class)
public class MouseSensitivityMixin {

    // Используем OptionInstance<?> чтобы принимать любые типы (и Double, и Boolean)
    @Redirect(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"))
    private Object aas$modifySensitivity(OptionInstance<?> instance) {
        Object value = instance.get();
        Minecraft mc = Minecraft.getInstance();

        if (instance == mc.options.sensitivity()) {
            Double sensitivity = (Double) value;

            if (mc.player != null) {
                // Замедление для бинокля
                if (mc.player.isUsingItem() && mc.player.getUseItem().getItem() == com.example.aas.item.ModItems.BINOCULARS.get()) {
                    return sensitivity * 0.12; // Замедляем очень сильно (в ~8 раз)
                }

                // Твоя существующая логика для техники
                Entity vehicle = mc.player.getVehicle();
                if (vehicle instanceof AGS30Entity ags && ags.isAiming()) return sensitivity * 0.25;
                if (vehicle instanceof M2BrowningEntity m2 && m2.isAiming()) return sensitivity * 0.5;
            }
            return sensitivity;
        }
        return value;
    }
}