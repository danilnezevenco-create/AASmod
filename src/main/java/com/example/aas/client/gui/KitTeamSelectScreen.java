package com.example.aas.client.gui;

import com.example.aas.client.KitKeyUtil;
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

        // --- Key buttons at the very top (away from the team buttons, so no accidental clicks) ---
        addRenderableWidget(Button.builder(Component.literal("COPY KEY (BOTH TEAMS)"), b ->
                KitKeyUtil.requestCopy("ALL", "BOTH", "ALL", false)).bounds(cx - 155, 4, 150, 20).build());

        addRenderableWidget(Button.builder(Component.literal("PASTE KEY (CLIPBOARD)"), b ->
                KitKeyUtil.importAllTeams()).bounds(cx + 5, 4, 150, 20).build());

        // --- Team buttons ---
        addRenderableWidget(Button.builder(Component.literal("BLUE TEAM KITS"), b ->
                this.minecraft.setScreen(new KitListScreen("BLUE"))).bounds(cx - 105, cy - 10, 100, 20).build());

        addRenderableWidget(Button.builder(Component.literal("RED TEAM KITS"), b ->
                this.minecraft.setScreen(new KitListScreen("RED"))).bounds(cx + 5, cy - 10, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics gui, int mx, int my, float pt) {
        renderBackground(gui);
        gui.drawCenteredString(font, "Key = all kits as one text. Copy here, paste in another world.", width / 2, 28, 0xAAAAAA);
        gui.drawCenteredString(font, title, width / 2, 46, 0xFFFFFF);
        super.render(gui, mx, my, pt);
    }
}