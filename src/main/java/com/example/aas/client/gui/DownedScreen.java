// FILE: DownedScreen.java
// PATH: src\main\java\com\example\aas\client\gui\DownedScreen.java
package com.example.aas.client.gui;

import com.example.aas.network.PacketDownedAction;
import com.example.aas.network.PacketHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class DownedScreen extends Screen {
    private static final ResourceLocation VIGNETTE_TEXTURE = new ResourceLocation("aas", "textures/misc/vignette.png");
    private Button callMedicButton;
    private long lastMedicCallTime = 0;
    private final long screenOpenTime; // Время, когда открылся экран нока

    public DownedScreen() {
        super(Component.literal("Incapacitated"));
        this.screenOpenTime = System.currentTimeMillis();
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int bottom = this.height - 40;

        this.callMedicButton = this.addRenderableWidget(Button.builder(Component.literal("CALL MEDIC"), b -> {
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastMedicCallTime >= 5000) {
                PacketHandler.INSTANCE.sendToServer(new PacketDownedAction(0));
                lastMedicCallTime = currentTime;
            }
        }).bounds(cx - 105, bottom, 100, 20).build());

        // Кнопка сдаться
        this.addRenderableWidget(Button.builder(Component.literal("GIVE UP"), b -> {
            PacketHandler.INSTANCE.sendToServer(new PacketDownedAction(1));
            this.onClose();
        }).bounds(cx + 5, bottom, 100, 20).build());
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void render(GuiGraphics gui, int mx, int my, float pt) {

        long remainingMedic = 5000 - (System.currentTimeMillis() - lastMedicCallTime);
        if (remainingMedic > 0) {
            callMedicButton.active = false;
            callMedicButton.setMessage(Component.literal("WAIT " + (remainingMedic / 1000 + 1) + "s"));
        } else {
            callMedicButton.active = true;
            callMedicButton.setMessage(Component.literal("CALL MEDIC"));
        }

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(0.6f, 0.0f, 0.0f, 0.9f); // Темно-красный
        gui.blit(VIGNETTE_TEXTURE, 0, 0, 0, 0, this.width, this.height, this.width, this.height);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();

        gui.drawCenteredString(this.font, "YOU ARE CRITICALLY INJURED", this.width / 2, this.height / 2 - 20, 0xFFFFFF);

        // РАСЧЕТ ОСТАВШЕГОСЯ ВРЕМЕНИ ДЛЯ BLEED OUT (3 минуты = 180 секунд)
        int maxSeconds = com.example.aas.config.AASConfig.MAX_DOWNED_TIME_SECONDS.get();
        long elapsedMillis = System.currentTimeMillis() - this.screenOpenTime;
        long remainingBleedoutSeconds = maxSeconds - (elapsedMillis / 1000);

        if (remainingBleedoutSeconds < 0) remainingBleedoutSeconds = 0;

        String timerText = "BLEEDING OUT IN: " + remainingBleedoutSeconds + "s";
        gui.drawCenteredString(this.font, timerText, this.width / 2, this.height - 15, 0xFF5555);

        super.render(gui, mx, my, pt);
    }
}