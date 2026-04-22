// PATH: src/main/java/com/example/aas/client/gui/MapMarkerSelectionScreen.java
package com.example.aas.client.gui;

import com.example.aas.network.PacketHandler;
import com.example.aas.network.PacketPlaceMapMarker;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashMap;
import java.util.Map;

public class MapMarkerSelectionScreen extends Screen {
    private final BlockPos targetPos;
    private static final Map<String, ResourceLocation> MARKERS = new LinkedHashMap<>();

    static {
        // Метки врагов
        MARKERS.put("Infantry", new ResourceLocation("aas", "textures/gui/map_icons/infantry_marker.png"));
        MARKERS.put("Sniper", new ResourceLocation("aas", "textures/gui/map_icons/sniper_marker.png"));
        MARKERS.put("HAT", new ResourceLocation("aas", "textures/gui/map_icons/hat_marker.png"));
        MARKERS.put("APC", new ResourceLocation("aas", "textures/gui/map_icons/apc_marker.png"));
        MARKERS.put("Tank", new ResourceLocation("aas", "textures/gui/map_icons/tank_marker.png"));
        MARKERS.put("Enemy HUB", new ResourceLocation("aas", "textures/gui/map_icons/hub_marker.png"));
        MARKERS.put("Enemy Rally", new ResourceLocation("aas", "textures/gui/map_icons/rally_marker.png"));
        // Метки союзников
        MARKERS.put("Supply Request", new ResourceLocation("aas", "textures/gui/map_icons/supply_request_marker.png"));
    }

    public MapMarkerSelectionScreen(BlockPos pos) {
        super(Component.literal("Select Marker"));
        this.targetPos = pos;
    }

    @Override
    protected void init() {
        int startX = (width - 340) / 2;
        int startY = 40; // Немного поднял
        int i = 0;
        for (String type : MARKERS.keySet()) {
            int row = i / 4;
            int col = i % 4;
            int x = startX + col * 85;
            int y = startY + row * 80; // Увеличил шаг для 32-пиксельных иконок

            this.addRenderableWidget(Button.builder(Component.literal(type), b -> {
                // ИСПРАВЛЕНО: Передаем координаты X и Z отдельно
                PacketHandler.INSTANCE.sendToServer(new PacketPlaceMapMarker(targetPos.getX(), targetPos.getZ(), type));
                this.onClose();
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
        int startY = 40;
        for (ResourceLocation icon : MARKERS.values()) {
            int row = i / 4;
            int col = i % 4;

            // Расчет позиции для иконки 32x32 (центрирование над кнопкой 80px)
            int iconX = startX + col * 85 + 24;
            int iconY = startY + row * 80;

            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            // ПУНКТ 2: Рендер иконки 32x32
            gui.blit(icon, iconX, iconY, 0, 0, 32, 32, 32, 32);
            i++;
        }
        super.render(gui, mx, my, pt);
    }
}