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

    // Настройки сетки
    private static final int COLS = 4;
    private static final int CELL_WIDTH = 90;  // Ширина ячейки (было 85)
    private static final int CELL_HEIGHT = 90; // Высота ячейки (было 80)
    private static final int BTN_WIDTH = 80;

    public MapMarkerGridScreen(int x, int z, String title, Map<String, ResourceLocation> markers) {
        super(Component.literal(title));
        this.worldX = x;
        this.worldZ = z;
        this.markers = markers;
    }

    @Override
    protected void init() {
        int totalWidth = COLS * CELL_WIDTH;
        int startX = (this.width - totalWidth) / 2;
        int startY = 40;

        int i = 0;
        for (String type : markers.keySet()) {
            int row = i / COLS;
            int col = i % COLS;
            int x = startX + col * CELL_WIDTH + (CELL_WIDTH - BTN_WIDTH) / 2;
            int y = startY + row * CELL_HEIGHT;

            // Кнопка расположена ниже иконки
            this.addRenderableWidget(Button.builder(Component.literal(type), b -> {
                PacketHandler.INSTANCE.sendToServer(new PacketPlaceMapMarker(worldX, worldZ, type));
                // После нажатия возвращаемся в меню отрядов
                this.minecraft.setScreen(new com.example.aas.client.gui.SquadSelectionScreen());
            }).bounds(x, y + 45, BTN_WIDTH, 20).build());

            i++;
        }
    }

    @Override
    public void render(GuiGraphics gui, int mx, int my, float pt) {
        this.renderBackground(gui);
        gui.drawCenteredString(font, title, width / 2, 15, 0xFFFFFF);

        int totalWidth = COLS * CELL_WIDTH;
        int startX = (this.width - totalWidth) / 2;
        int startY = 40;

        // ВАЖНО: Сбрасываем цвет в чисто белый, чтобы иконки не были красными
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.enableBlend();

        int i = 0;
        for (ResourceLocation icon : markers.values()) {
            int row = i / COLS;
            int col = i % COLS;

            // Центрируем иконку 32x32 внутри CELL_WIDTH
            int iconX = startX + col * CELL_WIDTH + (CELL_WIDTH - 32) / 2;
            int iconY = startY + row * CELL_HEIGHT + 5;

            // Рисуем иконку
            gui.blit(icon, iconX, iconY, 0, 0, 32, 32, 32, 32);
            i++;
        }

        // Рисуем кнопки (стандартный метод)
        super.render(gui, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}