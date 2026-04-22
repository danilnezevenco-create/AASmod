package com.example.aas.client;

import net.minecraft.client.Minecraft;

public class RecoilHandler {

    // Сколько градусов нам еще нужно вернуть вниз
    private static float pendingRecovery = 0.0f;

    // Вызывается, когда приходит пакет с сервера (выстрел)
    public static void addRecoil(float pitch) {
        if (pitch <= 0) return;

        // 1. Сразу дергаем камеру ВВЕРХ (отдача)
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.turn(0, -pitch); // Минус pitch = взгляд вверх
        }

        // 2. Запоминаем, сколько нужно вернуть обратно
        pendingRecovery += pitch;
    }

    // Вызывается каждый тик игры (для плавного возврата)
    public static void clientTick() {
        if (pendingRecovery > 0.01f) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            // СКОРОСТЬ ВОЗВРАТА
            // Умножаем на 0.2f -> каждый тик возвращаем 20% от оставшегося пути.
            // Это создает эффект плавного торможения в конце.
            float recoveryStep = pendingRecovery * 0.2f;

            // Минимальный шаг, чтобы не зависнуть на микро-числах
            if (recoveryStep < 0.05f) recoveryStep = 0.05f;
            if (recoveryStep > pendingRecovery) recoveryStep = pendingRecovery;

            // Двигаем камеру ВНИЗ (возврат)
            mc.player.turn(0, recoveryStep);

            // Уменьшаем остаток
            pendingRecovery -= recoveryStep;
        } else {
            pendingRecovery = 0.0f;
        }
    }
}