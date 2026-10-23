package com.example.aas.client.uav;

import com.example.aas.client.ClientData;
import com.example.aas.network.PacketHandler;
import com.example.aas.network.PacketUavLaunch;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * "Choose the UAV zone" mode of the map screen: RMB sets the centre, mouse sets the radius (clamped to the
 * configured min/max), RMB confirms, Esc cancels. The screen routes RMB here BEFORE the marker menu.
 */
public class UavMapSelection {
    private enum Stage { IDLE, CENTER, RADIUS }

    private Stage stage = Stage.IDLE;
    private double centerX, centerZ;

    public boolean isActive() { return stage != Stage.IDLE; }
    public void start() { stage = Stage.CENTER; }
    public void cancel() { stage = Stage.IDLE; }

    /** @return true if the click was consumed (it was on the map while the mode is active) */
    public boolean onRightClick(double mx, double my, int mapX, int mapY, int mapSize,
                                double panX, double panZ, double bpp) {
        if (stage == Stage.IDLE) return false;
        if (mx < mapX || mx > mapX + mapSize || my < mapY || my > mapY + mapSize) return false;
        if (stage == Stage.CENTER) {
            centerX = panX + (mx - (mapX + mapSize / 2.0)) * bpp;
            centerZ = panZ + (my - (mapY + mapSize / 2.0)) * bpp;
            stage = Stage.RADIUS;
            return true;
        }
        int r = radiusAt(mx, my, mapX, mapY, mapSize, panX, panZ, bpp);
        PacketHandler.INSTANCE.sendToServer(new PacketUavLaunch(centerX, centerZ, r));
        stage = Stage.IDLE;
        return true;
    }

    private int radiusAt(double mx, double my, int mapX, int mapY, int mapSize, double panX, double panZ, double bpp) {
        double sx = mapX + mapSize / 2.0 + (centerX - panX) / bpp;
        double sy = mapY + mapSize / 2.0 + (centerZ - panZ) / bpp;
        double blocks = Math.hypot(mx - sx, my - sy) * bpp;
        int min = ClientData.uavMinRadius();
        int max = Math.max(min, ClientData.uavMaxRadius());
        return Mth.clamp((int) Math.round(blocks), min, max);
    }

    public void render(GuiGraphics gui, Font font, int mouseX, int mouseY, int mapX, int mapY, int mapSize,
                       double panX, double panZ, double bpp) {
        if (stage == Stage.IDLE) return;
        Component hint;
        if (stage == Stage.RADIUS) {
            int r = radiusAt(mouseX, mouseY, mapX, mapY, mapSize, panX, panZ, bpp);
            float sx = (float) (mapX + mapSize / 2.0 + (centerX - panX) / bpp);
            float sy = (float) (mapY + mapSize / 2.0 + (centerZ - panZ) / bpp);
            gui.enableScissor(mapX, mapY, mapX + mapSize, mapY + mapSize);
            UavZoneRenderer.drawZone(gui, sx, sy, (float) (r / bpp));
            UavZoneRenderer.drawIcon(gui, sx, sy, 16);
            gui.disableScissor();
            hint = Component.translatable("aas.uav.select.radius", r);
        } else {
            hint = Component.translatable("aas.uav.select.center");
            gui.fill(mouseX - 5, mouseY, mouseX + 6, mouseY + 1, 0xFFFFFF00);   // ярко-жёлтый
            gui.fill(mouseX, mouseY - 5, mouseX + 1, mouseY + 6, 0xFFFFFF00);
        }
        int w = font.width(hint);
        int x = mapX + mapSize / 2 - w / 2;
        int y = mapY + 6;
        gui.fill(x - 5, y - 3, x + w + 5, y + 11, 0xCC000000);
        gui.renderOutline(x - 5, y - 3, w + 10, 14, 0xFFB8860B);
        gui.drawString(font, hint, x, y, 0xFFFFE680, false);
    }
}
