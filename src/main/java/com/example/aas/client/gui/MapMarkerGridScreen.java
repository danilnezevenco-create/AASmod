package com.example.aas.client.gui;

import com.example.aas.network.PacketHandler;
import com.example.aas.network.PacketPlaceMapMarker;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.Map;

public class MapMarkerGridScreen extends Screen {
    private final int worldX, worldZ;
    private final Map<String, ResourceLocation> markers;

    public MapMarkerGridScreen(int x, int z, String title, Map<String, ResourceLocation> markers) {
        super(Component.literal(title));
        this.worldX = x;
        this.worldZ = z;
        this.markers = markers;
    }

    @Override
    protected void init() {
        int startX = (width - 340) / 2;
        int startY = 50;
        int i = 0;
        for (String type : markers.keySet()) {
            int row = i / 4;
            int col = i % 4;
            int x = startX + col * 85;
            int y = startY + row * 80;

            // Кнопка под иконкой
            this.addRenderableWidget(Button.builder(Component.literal(type), b -> {
                PacketHandler.INSTANCE.sendToServer(new PacketPlaceMapMarker(worldX, worldZ, type));
                this.minecraft.setScreen(new com.example.aas.client.gui.SquadSelectionScreen());
            }).bounds(x, y + 35, 80, 20).build());
            i++;
        }
    }

    @Override
    public void render(GuiGraphics gui, int mx, int my, float pt) {
        this.renderBackground(gui);
        gui.drawCenteredString(font, title, width / 2, 15, 0xFFFFFF);

        int i = 0;
        int startX = (width - 340) / 2;
        int startY = 50;
        for (ResourceLocation icon : markers.values()) {
            int row = i / 4;
            int col = i % 4;
            int x = startX + col * 85 + 24; // Центрирование 32px иконки в 80px слоте
            int y = startY + row * 80;

            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            // Рендер иконки 32x32
            gui.blit(icon, x, y, 0, 0, 32, 32, 32, 32);
            i++;
        }
        super.render(gui, mx, my, pt);
    }
}