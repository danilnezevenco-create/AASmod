package com.example.aas.client.gui;

import com.example.aas.network.PacketHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.LinkedHashMap;
import java.util.Map;

public class TacticalMapRadialScreen extends Screen {
    private final int wx, wz;
    // Используем стандартную текстуру сектора на 120 градусов
    private static final ResourceLocation SECTOR_TEXTURE = new ResourceLocation("aas", "textures/gui/radial_sector.png");

    public TacticalMapRadialScreen(int x, int z) {
        super(Component.literal("Tactical Menu"));
        this.wx = x; this.wz = z;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gui);

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        double dx = mouseX - centerX;
        double dy = mouseY - centerY;
        double distance = Math.sqrt(dx * dx + dy * dy);

        double angle = Math.toDegrees(Math.atan2(dy, dx)) + 90;
        if (angle < 0) angle += 360;

        int selected = -1;
        if (distance > 10) {
            if (angle > 300 || angle <= 60) selected = 0;      // TEAM (Верх)
            else if (angle > 60 && angle <= 180) selected = 1; // ENEMY (Право-низ)
            else if (angle > 180 && angle <= 300) selected = 2; // SQUAD (Лево-низ)
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        PoseStack pose = gui.pose();
        int size = 95;

        for (int i = 0; i < 3; i++) {
            pose.pushPose();
            pose.translate(centerX, centerY, 0);
            pose.mulPose(Axis.ZP.rotationDegrees(i * 120));

            boolean isSelected = (i == selected);
            float scale = isSelected ? 1.15f : 1.0f;
            pose.scale(scale, scale, 1.0f);
            pose.translate(-size / 2.0f, -size, 0);

            // Стиль как в RadioRadialScreen
            if (isSelected) {
                RenderSystem.setShaderColor(0.4f, 1.0f, 0.4f, 1.0f); // Зеленый при наведении
            } else {
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f); // Обычный белый
            }

            gui.blit(SECTOR_TEXTURE, 0, 0, 0, 0, size, size, size, size);
            pose.popPose();
        }
        RenderSystem.setShaderColor(1, 1, 1, 1);

        // Отрисовка подписей
        drawLabel(gui, "TEAM", centerX, centerY - 70, selected == 0);
        drawLabel(gui, "ENEMY", centerX + 60, centerY + 30, selected == 1);
        drawLabel(gui, "SQUAD", centerX - 60, centerY + 30, selected == 2);
    }

    private void drawLabel(GuiGraphics gui, String text, int x, int y, boolean selected) {
        int color = selected ? 0xFF00FF00 : 0xFFFFFFFF;
        gui.drawCenteredString(this.font, text, x, y - 4, color);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn == 0) {
            int centerX = this.width / 2;
            int centerY = this.height / 2;
            double dx = mx - centerX;
            double dy = my - centerY;
            double dist = Math.sqrt(dx * dx + dy * dy);
            if (dist < 10) return false;

            double angle = Math.toDegrees(Math.atan2(dy, dx)) + 90;
            if (angle < 0) angle += 360;

            int action = -1;
            if (angle > 300 || angle <= 60) action = 0;
            else if (angle > 60 && angle <= 180) action = 1;
            else if (angle > 180 && angle <= 300) action = 2;

            if (action == 0) {
                openGrid("Team Markers", getTeamMarkers());
            } else if (action == 1) {
                openGrid("Enemy Markers", getEnemyMarkers());
            } else if (action == 2) {
                this.minecraft.setScreen(new SquadMarkerRadialScreen(wx, wz));
            }
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    private void openGrid(String title, Map<String, ResourceLocation> markers) {
        this.minecraft.setScreen(new MapMarkerGridScreen(wx, wz, title, markers));
    }

    private Map<String, ResourceLocation> getTeamMarkers() {
        Map<String, ResourceLocation> m = new LinkedHashMap<>();
        m.put("Supply Request", new ResourceLocation("aas", "textures/gui/map_icons/supply_request_marker.png"));
        return m;
    }

    private Map<String, ResourceLocation> getEnemyMarkers() {
        Map<String, ResourceLocation> m = new LinkedHashMap<>();

        // Статичные и пехота
        m.put("Infantry", new ResourceLocation("aas", "textures/gui/map_icons/infantry_marker.png"));
        m.put("Sniper", new ResourceLocation("aas", "textures/gui/map_icons/sniper_marker.png"));
        m.put("HAT", new ResourceLocation("aas", "textures/gui/map_icons/hat_marker.png"));
        m.put("Mortar", new ResourceLocation("aas", "textures/gui/map_icons/mortar_marker.png"));
        m.put("TOW", new ResourceLocation("aas", "textures/gui/map_icons/tow_marker.png"));
        m.put("Enemy HUB", new ResourceLocation("aas", "textures/gui/map_icons/hub_marker.png"));
        m.put("Enemy Rally", new ResourceLocation("aas", "textures/gui/map_icons/rally_marker.png"));
        m.put("Combat Vehicle", new ResourceLocation("aas", "textures/gui/map_icons/combat_vehicle_marker.png"));
        m.put("Infantry Vehicle", new ResourceLocation("aas", "textures/gui/map_icons/infantry_vehicle_marker.png"));
        // Техника (теперь тоже с припиской _marker)
        m.put("APC", new ResourceLocation("aas", "textures/gui/map_icons/apc_marker.png"));
        m.put("Tank", new ResourceLocation("aas", "textures/gui/map_icons/tank_marker.png"));
        m.put("Helicopter", new ResourceLocation("aas", "textures/gui/map_icons/helicopter_marker.png"));
        m.put("CAS Heli", new ResourceLocation("aas", "textures/gui/map_icons/cas_helicopter_marker.png"));
        m.put("CAS Fighter", new ResourceLocation("aas", "textures/gui/map_icons/cas_fighter_marker.png"));
        m.put("Mobile ZU", new ResourceLocation("aas", "textures/gui/map_icons/mobile_zu_marker.png"));
        m.put("Supply Truck", new ResourceLocation("aas", "textures/gui/map_icons/supply_truck_marker.png"));

        return m;
    }
}