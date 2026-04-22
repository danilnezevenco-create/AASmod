// PATH: src\main\java\com\example\aas\client\gui\KitTeamSelectScreen.java
package com.example.aas.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class KitTeamSelectScreen extends Screen {
    public KitTeamSelectScreen() { super(Component.literal("Select Team for Kits")); }

    @Override
    protected void init() {
        int cx = width / 2;
        int cy = height / 2;
        addRenderableWidget(Button.builder(Component.literal("BLUE TEAM KITS"), b -> {
            this.minecraft.setScreen(new KitListScreen("BLUE"));
        }).bounds(cx - 105, cy - 10, 100, 20).build());

        addRenderableWidget(Button.builder(Component.literal("RED TEAM KITS"), b -> {
            this.minecraft.setScreen(new KitListScreen("RED"));
        }).bounds(cx + 5, cy - 10, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics gui, int mx, int my, float pt) {
        renderBackground(gui);
        gui.drawCenteredString(font, title, width/2, 20, 0xFFFFFF);
        super.render(gui, mx, my, pt);
    }
}