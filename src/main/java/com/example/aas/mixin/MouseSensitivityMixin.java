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
        // 1. Сначала получаем значение как Object, чтобы не вызвать краш на Boolean настройках
        Object value = instance.get();

        Minecraft mc = Minecraft.getInstance();

        // 2. Проверяем, что текущая опция — это ИМЕННО чувствительность
        if (instance == mc.options.sensitivity()) {
            // Теперь безопасно превращаем Object в Double
            Double sensitivity = (Double) value;

            if (mc.player != null) {
                Entity vehicle = mc.player.getVehicle();

                // Логика для AGS-30
                if (vehicle instanceof AGS30Entity ags && ags.isAiming()) {
                    return sensitivity * 0.25; // Сильное замедление для прицела
                }

                // Логика для M2 Browning (добавил на всякий случай)
                if (vehicle instanceof M2BrowningEntity m2 && m2.isAiming()) {
                    return sensitivity * 0.5; // Среднее замедление
                }
            }

            return sensitivity;
        }

        // 3. Если это другая настройка (например, Invert Mouse), просто возвращаем её как есть
        return value;
    }
}