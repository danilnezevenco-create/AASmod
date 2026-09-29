package com.example.aas.client.gui;

import com.example.aas.client.AASClipboard;
import com.example.aas.client.KitKeyUtil;
import com.example.aas.network.*;
import com.example.aas.world.AASWorldData;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class KitListScreen extends Screen {
    private final String team;
    private static final int COLUMNS = 4;
    private static final int ROW_HEIGHT = 60;

    public KitListScreen(String team) {
        super(Component.literal(team + " Kits"));
        this.team = team;
    }

    @Override
    protected void init() {
        int startX = (width - 440) / 2;
        int startY = 40;

        // --- Whole-team buttons (in-game clipboard) ---
        addRenderableWidget(Button.builder(Component.literal("COPY ALL"), b ->
                PacketHandler.INSTANCE.sendToServer(new PacketRequestKitData(team, "ALL"))).bounds(startX, 10, 80, 20).build());

        Button pasteAllBtn = Button.builder(Component.literal("PASTE ALL"), b -> {
            if (AASClipboard.teamKitsData != null)
                PacketHandler.INSTANCE.sendToServer(new PacketPasteTeam(team, AASClipboard.teamKitsData));
        }).bounds(startX + 85, 10, 80, 20).build();
        pasteAllBtn.active = (AASClipboard.teamKitsData != null);
        addRenderableWidget(pasteAllBtn);

        // --- Key for this team: generate (copy to system clipboard) / paste ---
        addRenderableWidget(Button.builder(Component.literal("COPY KEY"), b ->
                KitKeyUtil.requestCopy("TEAM", team, "ALL", false)).bounds(startX + 180, 10, 80, 20).build());

        addRenderableWidget(Button.builder(Component.literal("PASTE KEY"), b ->
                KitKeyUtil.importForTeam(team)).bounds(startX + 265, 10, 80, 20).build());

        // --- Kit grid ---
        for (int i = 0; i < AASWorldData.KIT_NAMES.length; i++) {
            String kitName = AASWorldData.KIT_NAMES[i];
            int row = i / COLUMNS;
            int col = i % COLUMNS;
            int x = startX + col * 110;
            int y = startY + row * ROW_HEIGHT + 26;

            addRenderableWidget(Button.builder(Component.literal(kitName), b ->
                    PacketHandler.INSTANCE.sendToServer(new PacketOpenKitEditor(team, kitName))).bounds(x, y, 70, 20).build());

            addRenderableWidget(Button.builder(Component.literal("C"), b ->
                    PacketHandler.INSTANCE.sendToServer(new PacketRequestKitData(team, kitName))).bounds(x + 72, y, 15, 20).build());

            Button pBtn = Button.builder(Component.literal("P"), b -> {
                if (AASClipboard.kitData != null)
                    PacketHandler.INSTANCE.sendToServer(new PacketPasteKit(team, kitName, AASClipboard.kitData));
            }).bounds(x + 89, y, 15, 20).build();
            pBtn.active = (AASClipboard.kitData != null);
            addRenderableWidget(pBtn);
        }
    }

    @Override
    public void render(GuiGraphics gui, int mx, int my, float pt) {
        renderBackground(gui);
        gui.drawCenteredString(font, title, width / 2, 1, 0xFFFFFF);

        int startX = (width - 440) / 2;
        int startY = 40;

        for (int i = 0; i < AASWorldData.KIT_NAMES.length; i++) {
            String kitName = AASWorldData.KIT_NAMES[i];
            int row = i / COLUMNS;
            int col = i % COLUMNS;
            String iconPath = kitName.toLowerCase().replace(" ", "_").replace("-", "_");
            ResourceLocation iconLoc = new ResourceLocation("aas", "textures/gui/kits/" + iconPath + ".png");
            int iconX = startX + col * 110 + (70 / 2) - 12;
            int iconY = startY + row * ROW_HEIGHT;
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            gui.blit(iconLoc, iconX, iconY, 0, 0, 24, 24, 24, 24);
        }
        super.render(gui, mx, my, pt);
    }
}