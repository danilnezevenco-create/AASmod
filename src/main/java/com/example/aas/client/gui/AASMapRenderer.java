// PATH: src\main\java\com\example\aas\client\gui\AASMapRenderer.java
package com.example.aas.client.gui;

import com.example.aas.client.ClientData;
import com.example.aas.network.MapPlayerInfo;
import com.example.aas.network.PacketDeleteMarker;
import com.example.aas.network.PacketHandler;
import com.example.aas.sound.ModSounds;
import com.example.aas.world.AASWorldData;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;
import java.util.Comparator;
import java.util.stream.Collectors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AASMapRenderer implements AutoCloseable {

    private static final Map<String, ResourceLocation> MAP_ICONS_CACHE = new HashMap<>();

    private ResourceLocation getCurrentMapTexture() {
        String img = ClientData.currentMapImage;
        if (img == null || img.isEmpty()) img = "map1";
        return MAP_ICONS_CACHE.computeIfAbsent(img, k -> new ResourceLocation("aas", "textures/gui/maps/" + k + ".png"));
    }

    private int mapX, mapY, mapSize;
    private double getMapScale() { return ClientData.mapScale; }
    public String selectedSpawnId = "";

    /**
     * Только для экрана смерти: проверка "можно ли сейчас заспавниться на этой точке".
     * Принимает id точки: "RALLY" или "HUB:x:y:z". На остальных экранах = null (иконки/обводка не меняются).
     */
    public java.util.function.Predicate<String> spawnAvailability = null;
    /** Во сколько раз увеличивается иконка выбранной точки спавна. */
    private static final float SELECTED_SPAWN_SCALE = 1.15f;
    /** Период одного цикла "почти прозрачная -> непрозрачная -> почти прозрачная" для обводки доступных точек спавна (мс). */
    private static final long SPAWN_PULSE_MS = 1400L;
    /** Минимальная непрозрачность обводки в нижней точке пульсации (0..1). */
    private static final float SPAWN_PULSE_MIN_ALPHA = 0.08f;
    private boolean followMode = false;
    public void setFollowMode(boolean follow) { this.followMode = follow; }
    private double mapPanX = 0;
    private double mapPanZ = 0;
    private boolean isDraggingMap = false;
    private double lastMouseX = 0;
    private double lastMouseY = 0;

    public int getMapX() { return mapX; }
    public int getMapY() { return mapY; }
    public int getMapSize() { return mapSize; }

    private static final ResourceLocation MARKER_MOVE = new ResourceLocation("aas", "textures/gui/map_icons/marker_move.png");
    private static final ResourceLocation MARKER_ATTACK = new ResourceLocation("aas", "textures/gui/map_icons/marker_attack.png");
    private static final ResourceLocation MARKER_DEFEND = new ResourceLocation("aas", "textures/gui/map_icons/marker_defend.png");
    private static final ResourceLocation MARKER_BUILD = new ResourceLocation("aas", "textures/gui/map_icons/marker_build.png");
    // Метки ДРУГИХ отрядов (видят только лидеры отрядов своей команды)
    private static final ResourceLocation MARKER_MOVE_ALLY = new ResourceLocation("aas", "textures/gui/map_icons/marker_move_ally.png");
    private static final ResourceLocation MARKER_ATTACK_ALLY = new ResourceLocation("aas", "textures/gui/map_icons/marker_attack_ally.png");
    private static final ResourceLocation MARKER_DEFEND_ALLY = new ResourceLocation("aas", "textures/gui/map_icons/marker_defend_ally.png");
    private static final ResourceLocation MARKER_BUILD_ALLY = new ResourceLocation("aas", "textures/gui/map_icons/marker_build_ally.png");
    private static final ResourceLocation PING_EYE_ALLY = new ResourceLocation("aas", "textures/gui/map_icons/ping_eye_ally.png");
    private static final int COLOR_ALLY_LINE = 0xFF279FDB; // линия от лидера другого отряда до его метки
    private static final ResourceLocation FLAG_NEUTRAL = new ResourceLocation("aas", "textures/gui/flags/neutral.png");
    private static final ResourceLocation ICON_CIRCLE = new ResourceLocation("aas", "textures/gui/map_icons/player_circle.png");
    private static final ResourceLocation ICON_PLUS = new ResourceLocation("aas", "textures/gui/map_icons/medic_plus.png");
    private static final ResourceLocation ICON_PLAYER_SELF = new ResourceLocation("aas", "textures/gui/map_icons/player_self.png");
    private static final ResourceLocation ICON_MEDIC_CIRCLE = new ResourceLocation("aas", "textures/gui/map_icons/medic_circle.png");
    private static final ResourceLocation MAP_GRID_TEXTURE = new ResourceLocation("aas", "textures/gui/map_grid.png");
    private static final ResourceLocation HUB_ICON = new ResourceLocation("aas", "textures/gui/map_icons/hub_icon.png");
    private static final ResourceLocation HUB_UNBUILT_ICON = new ResourceLocation("aas", "textures/gui/map_icons/hub_icon_unbuilt.png");
    private static final ResourceLocation RALLY_ICON = new ResourceLocation("aas", "textures/gui/map_icons/rally_icon.png");
    private static final ResourceLocation MAIN_BASE_ICON = new ResourceLocation("aas", "textures/gui/map_icons/main_base.png");
    private static final ResourceLocation ICON_OBJ_ATTACK = new ResourceLocation("aas", "textures/gui/map_icons/objective_attack.png");
    private static final ResourceLocation ICON_OBJ_DEFEND = new ResourceLocation("aas", "textures/gui/map_icons/objective_defend.png");
    private static final int COLOR_SQUAD_LINE = 0xFF00FF00; // чистый зелёный (#00FF00), как в референсе
    private static final int COLOR_BRAVO_LINE   = 0xFFAA33FF; // фиолетовый — Bravo
    private static final int COLOR_CHARLIE_LINE = 0xFF00E5CC; // бирюзовый — Charlie
    private static final ResourceLocation HUB_SELECTED_ICON = new ResourceLocation("aas", "textures/gui/map_icons/hub_icon_selected.png");
    private static final ResourceLocation RALLY_SELECTED_ICON = new ResourceLocation("aas", "textures/gui/map_icons/rally_icon_selected.png");
    private static final ResourceLocation HUB_AVAILABLE_ICON = new ResourceLocation("aas", "textures/gui/map_icons/hub_icon_available.png");
    private static final ResourceLocation RALLY_AVAILABLE_ICON = new ResourceLocation("aas", "textures/gui/map_icons/rally_icon_available.png");
    private static final ResourceLocation MAIN_SELECTED_ICON = new ResourceLocation("aas", "textures/gui/map_icons/main_base_selected.png");
    private static final ResourceLocation MAIN_AVAILABLE_ICON = new ResourceLocation("aas", "textures/gui/map_icons/main_base_available.png");
    private static final ResourceLocation MATS_ICON = new ResourceLocation("aas", "textures/gui/mats_icon.png");
    private static final ResourceLocation STATION_ICON = new ResourceLocation("aas", "textures/gui/map_icons/vehicle_station.png");
    private static final ResourceLocation RHOMBUS_CMD = new ResourceLocation("aas", "textures/gui/map_icons/squad_rhombus_cmd.png");
    private static final ResourceLocation RHOMBUS_NORMAL = new ResourceLocation("aas", "textures/gui/map_icons/squad_rhombus.png");
    private static final Map<String, ResourceLocation> VEHICLE_ICONS = new HashMap<>();
    static {
        VEHICLE_ICONS.put("APC", new ResourceLocation("aas", "textures/gui/map_icons/apc.png"));
        VEHICLE_ICONS.put("TANK", new ResourceLocation("aas", "textures/gui/map_icons/tank.png"));
        VEHICLE_ICONS.put("HELICOPTER", new ResourceLocation("aas", "textures/gui/map_icons/helicopter.png"));
        VEHICLE_ICONS.put("CAS Helicopter", new ResourceLocation("aas", "textures/gui/map_icons/cas_helicopter.png"));
        VEHICLE_ICONS.put("CAS Fighter", new ResourceLocation("aas", "textures/gui/map_icons/cas_fighter.png"));
        VEHICLE_ICONS.put("Combat Vehicle", new ResourceLocation("aas", "textures/gui/map_icons/combat_vehicle.png"));
        VEHICLE_ICONS.put("Infantry Vehicle", new ResourceLocation("aas", "textures/gui/map_icons/infantry_vehicle.png"));
        VEHICLE_ICONS.put("Supply Truck", new ResourceLocation("aas", "textures/gui/map_icons/supply_truck.png"));
        VEHICLE_ICONS.put("Supply Helicopter", new ResourceLocation("aas", "textures/gui/map_icons/supply_helicopter.png"));
        VEHICLE_ICONS.put("DEFAULT", new ResourceLocation("aas", "textures/gui/map_icons/default.png"));
        VEHICLE_ICONS.put("Static ZU", new ResourceLocation("aas", "textures/gui/map_icons/static_zu.png"));
        VEHICLE_ICONS.put("Mobile ZU", new ResourceLocation("aas", "textures/gui/map_icons/mobile_zu.png"));
        VEHICLE_ICONS.put("BOAT", new ResourceLocation("aas", "textures/gui/map_icons/boat.png"));
        VEHICLE_ICONS.put("Motorcycle", new ResourceLocation("aas", "textures/gui/map_icons/motorcycle.png"));
        VEHICLE_ICONS.put("Light Supply", new ResourceLocation("aas", "textures/gui/map_icons/light_supply.png"));
        VEHICLE_ICONS.put("Mine", new ResourceLocation("aas", "textures/gui/map_icons/skull_marker.png"));
        VEHICLE_ICONS.put("ATGM Carrier", new ResourceLocation("aas", "textures/gui/map_icons/atgm_carrier.png"));
        VEHICLE_ICONS.put("Heavy Supply", new ResourceLocation("aas", "textures/gui/map_icons/heavy_supply.png"));
        VEHICLE_ICONS.put("SPG", new ResourceLocation("aas", "textures/gui/map_icons/spg.png"));
    }
    private ResourceLocation getMarkerIcon(String type) {
        String path = type.toLowerCase()
                .replace("enemy ", "")
                .replace(" ", "_");

        return new ResourceLocation("aas", "textures/gui/map_icons/" + path + "_marker.png");
    }
    private void setFilter(ResourceLocation tex, boolean smooth) {
        Minecraft.getInstance().getTextureManager().getTexture(tex).setFilter(smooth, false);
    }
    public AASMapRenderer() {}

    public void init(int x, int y, int size) {
        this.mapX = x;
        this.mapY = y;
        this.mapSize = size;
    }
    private void drawSquadNumber(GuiGraphics gui, net.minecraft.client.gui.Font font, String text, int x, int y, int color) {
        gui.drawString(font, text, x - 1, y, 0xFF000000, false);
        gui.drawString(font, text, x + 1, y, 0xFF000000, false);
        gui.drawString(font, text, x, y - 1, 0xFF000000, false);
        gui.drawString(font, text, x, y + 1, 0xFF000000, false);
        gui.drawString(font, text, x, y, color, false);
    }
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer localPlayer = mc.player;
        if (localPlayer == null) return;

        boolean follow = followMode;
        if (follow) { mapPanX = 0; mapPanZ = 0; } else { clampView(localPlayer); }

        double currentScale = follow ? ClientData.miniMapScale : ClientData.mapScale;
        double cx = localPlayer.getX() + mapPanX;
        double cz = localPlayer.getZ() + mapPanZ;

        gui.fillGradient(mapX, mapY, mapX + mapSize, mapY + mapSize, 0xFF1A2430, 0xFF050505);

        gui.enableScissor(mapX, mapY, mapX + mapSize, mapY + mapSize);

        PoseStack pose = gui.pose();
        pose.pushPose();

        pose.translate(mapX + mapSize / 2.0, mapY + mapSize / 2.0, 0);
        float scale = 1.0f / (float) currentScale;
        pose.scale(scale, scale, 1.0f);

        int s = ClientData.mapSizeBlocks;

        float drawX = (float) (ClientData.mapCenterX - (s / 2.0) - cx);
        float drawY = (float) (ClientData.mapCenterZ - (s / 2.0) - cz);

        ResourceLocation dynamicMapTexture = getCurrentMapTexture();

        setFilter(dynamicMapTexture, true);
        RenderSystem.setShaderTexture(0, dynamicMapTexture);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        gui.blit(dynamicMapTexture, (int) drawX, (int) drawY, s, s, 0, 0, 1024, 1024, 1024, 1024);
        setFilter(dynamicMapTexture, false);

        pose.popPose();

        final int gridOffX = (int) drawX, gridOffY = (int) drawY;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        MapGrid.render(gui, mc.font, mapX, mapY, mapSize, gridOffX, gridOffY, s, currentScale);

        renderLatticeLines(gui, mc, cx, cz, currentScale);
        renderOverlays(gui, mc, cx, cz, currentScale);
        renderMainBases(gui, mc, cx, cz, currentScale);
        renderArtilleryZones(gui, cx, cz, currentScale);
        renderStructures(gui, mc, cx, cz, currentScale);
        renderVehicles(gui, mc, cx, cz, currentScale);
        renderAllPlayers(gui, mc, localPlayer, cx, cz, currentScale);
        renderSquadMarkerLogic(gui, mc, cx, cz, currentScale);
        renderTacticalMarkers(gui, mc, cx, cz, currentScale);
        renderSquadRhombusMarkers(gui, mc, cx, cz, currentScale);
        renderSquadPings(gui, cx, cz, currentScale);

        if (follow) {
            // рамка по краю карты: за ней тёмная пустота
            double half = mapSize / 2.0;
            int l = (int) (mapX + half + (ClientData.mapCenterX - s / 2.0 - cx) / currentScale);
            int r = (int) (mapX + half + (ClientData.mapCenterX + s / 2.0 - cx) / currentScale);
            int t = (int) (mapY + half + (ClientData.mapCenterZ - s / 2.0 - cz) / currentScale);
            int b = (int) (mapY + half + (ClientData.mapCenterZ + s / 2.0 - cz) / currentScale);
            int edge = 0xFFB0B8C0;
            gui.fill(l - 1, t - 1, r + 1, t, edge);
            gui.fill(l - 1, b, r + 1, b + 1, edge);
            gui.fill(l - 1, t, l, b, edge);
            gui.fill(r, t, r + 1, b, edge);
        }

        gui.disableScissor();

        if (isMouseOver(mouseX, mouseY)) {
            MapGrid.renderHover(gui, mc.font, mapX, mapY, mapSize, gridOffX, gridOffY, s, currentScale,
                    mouseX, mouseY,
                    mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        }
    }

    private void renderOverlays(GuiGraphics gui, Minecraft mc, double cx, double cz, double bpp) {
        if (ClientData.allCapturePoints == null) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        boolean blinkOn = (System.currentTimeMillis() / 400) % 2 == 0;

        for (AASWorldData.CapturePoint cp : ClientData.allCapturePoints) {
            Vec3 center = cp.area.getCenter();
            double dx = (center.x - cx) / bpp;
            double dy = (center.z - cz) / bpp;
            int pX = (int) (mapX + (mapSize / 2) + dx);
            int pY = (int) (mapY + (mapSize / 2) + dy);

            if (!isPointOnMap(pX, pY)) continue;

            String owner = cp.owner.toUpperCase();
            String capTeam = cp.capturingTeam.toUpperCase();
            float progress = cp.progress;

            String teamToRender = owner;
            float alpha = 1.0f;

            if (owner.equals("NEUTRAL")) {
                if (!capTeam.equals("NONE") && !capTeam.equals("NEUTRAL")) {
                    if (blinkOn) {
                        teamToRender = capTeam;
                        alpha = 0.1f + (progress * 0.9f);
                    } else {
                        teamToRender = "NEUTRAL";
                        alpha = 1.0f;
                    }
                } else {
                    teamToRender = "NEUTRAL";
                    alpha = 1.0f;
                }
            } else {
                if (progress < 1.0f) {
                    teamToRender = owner;
                    alpha = 0.1f + (progress * 0.9f);
                } else {
                    teamToRender = owner;
                    alpha = 1.0f;
                }
            }

            ResourceLocation flagTex = FLAG_NEUTRAL;
            int tintColor = 0xFFFFFFFF;
            boolean useTint = false;

            if (teamToRender.equals("BLUE")) {
                flagTex = getFlagTexture(ClientData.BLUE_FACTION);
                if (flagTex == null) { flagTex = FLAG_NEUTRAL; tintColor = 0xFF5555FF; useTint = true; }
            } else if (teamToRender.equals("RED")) {
                flagTex = getFlagTexture(ClientData.RED_FACTION);
                if (flagTex == null) { flagTex = FLAG_NEUTRAL; tintColor = 0xFFFF5555; useTint = true; }
            }

            float r = 1.0f, g = 1.0f, b = 1.0f;
            if (useTint) {
                r = ((tintColor >> 16) & 0xFF) / 255.0f;
                g = ((tintColor >> 8) & 0xFF) / 255.0f;
                b = (tintColor & 0xFF) / 255.0f;
            }

            RenderSystem.setShaderColor(r, g, b, alpha);
            setFilter(flagTex, true);

            gui.blit(flagTex, pX - 8, pY - 4, 16, 9, 0, 0, 64, 36, 64, 36);

            setFilter(flagTex, false);

            int textColor = 0xFFFFFF;

            gui.pose().pushPose();
            gui.pose().translate(pX, pY + 7, 101);
            float textScale = 0.6f;
            gui.pose().scale(textScale, textScale, 1.0f);

            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

            gui.drawCenteredString(mc.font, cp.name, 0, 0, textColor);
            gui.pose().popPose();
        }

        RenderSystem.enableDepthTest();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private void renderAllPlayers(GuiGraphics gui, Minecraft mc, LocalPlayer self, double cx, double cz, double bpp) {
        String myName = self.getScoreboardName();
        int myInternalSquadId = -1;

        String myTeamForNums = getPlayerTeamStrict(mc);
        boolean isBlueForNums = myTeamForNums != null && myTeamForNums.contains("BLUE");
        int teamCMDIdForNums = isBlueForNums ? ClientData.blueCMDId : ClientData.redCMDId;

        List<AASWorldData.Squad> teamSquadsForNums = ClientData.clientSquads.stream()
                .filter(s -> s.team.equalsIgnoreCase(myTeamForNums))
                .collect(Collectors.toList());
        teamSquadsForNums.sort((s1, s2) -> {
            if (s1.id == teamCMDIdForNums && teamCMDIdForNums != -1) return -1;
            if (s2.id == teamCMDIdForNums && teamCMDIdForNums != -1) return 1;
            return Integer.compare(s1.id, s2.id);
        });

        Map<Integer, Integer> idToDisplayNum = new HashMap<>();
        for (int i = 0; i < teamSquadsForNums.size(); i++) {
            var s = teamSquadsForNums.get(i);
            idToDisplayNum.put(s.id, i + 1);
            if (s.members.contains(myName)) {
                myInternalSquadId = s.id;
            }
        }

        boolean isShowNicksHeld = isShowNicknamesHeld();
        long currentTime = mc.level.getGameTime();
        boolean amIMedic = "Medic".equalsIgnoreCase(ClientData.myCurrentKit);

        Map<Integer, List<MapPlayerInfo>> vehicleGroups = new HashMap<>();

        for (MapPlayerInfo info : ClientData.mapPlayers.values()) {
            if (info.isDead) continue;
            if (info.inVehicle) {
                vehicleGroups.computeIfAbsent(info.vehicleId, k -> new ArrayList<>()).add(info);
                continue;
            }

            double dx = (info.x - cx) / bpp;
            double dy = (info.z - cz) / bpp;
            int sx = (int) (mapX + (mapSize / 2) + dx);
            int sy = (int) (mapY + (mapSize / 2) + dy);

            if (!isPointOnMap(sx, sy)) continue;

            if (!info.name.equals(myName) && ClientData.isHighlighted(info.name)) {
                drawHighlightBrackets(gui, sx, sy, 6);
            }

            if (isShowNicksHeld && !info.name.equals(myName) && !info.isDowned) {
                gui.pose().pushPose();
                gui.pose().translate(sx, sy - 8, 450);
                gui.pose().scale(0.6f, 0.6f, 1.0f);
                int nickColor = (info.squadId != -1 && info.squadId == myInternalSquadId) ? 0xFF55FF55 : 0xFFFFFFFF;
                gui.drawCenteredString(mc.font, Component.literal(info.name), 0, 0, nickColor);
                gui.pose().popPose();
            }

            if (!info.name.equals(myName)) {
                if (info.isDowned) {
                    if (amIMedic || currentTime - info.lastShoutTime < 120) {
                        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                        gui.blit(ICON_PLUS, sx - 4, sy - 4, 8, 8, 0, 0, 16, 16, 16, 16);
                    }
                } else {
                    float r = 0.2f, g = 0.6f, b = 1.0f;
                    if (info.squadId != -1 && info.squadId == myInternalSquadId) {
                        r = 0.0f; g = 1.0f; b = 0.0f;
                    }
                    RenderSystem.setShaderColor(r, g, b, 1.0f);

                    ResourceLocation iconToDraw = info.isMedic ? ICON_MEDIC_CIRCLE : ICON_CIRCLE;

                    gui.blit(iconToDraw, sx - 3, sy - 3, 6, 6, 0, 0, 16, 16, 16, 16);

                    RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                }

                Integer displayNum = idToDisplayNum.get(info.squadId);
                if (!info.isDowned && info.isLeader && info.squadId != -1 && displayNum != null) {
                    String numStr = String.valueOf(displayNum);
                    int textColor = (info.squadId == myInternalSquadId) ? 0xFF55FF55 : 0xFF5555FF;
                    gui.pose().pushPose();
                    gui.pose().translate(sx, sy, 350);
                    gui.pose().scale(0.5f, 0.5f, 1.0f);
                    int tw = mc.font.width(numStr);
                    drawSquadNumber(gui, mc.font, numStr, -(tw / 2), -4, textColor);
                    gui.pose().popPose();
                }

                if (!info.isDowned && info.fireteamRole != null && !info.fireteamRole.isEmpty()) {
                    int fireteamColor = "B".equals(info.fireteamRole) ? COLOR_BRAVO_LINE : COLOR_CHARLIE_LINE;
                    gui.pose().pushPose();
                    gui.pose().translate(sx, sy - 9, 360);
                    gui.pose().scale(0.5f, 0.5f, 1.0f);
                    int fw = mc.font.width(info.fireteamRole);
                    drawSquadNumber(gui, mc.font, info.fireteamRole, -(fw / 2), -4, fireteamColor);
                    gui.pose().popPose();
                }
            }
        }

        if (isShowNicksHeld) {
            for (List<MapPlayerInfo> group : vehicleGroups.values()) {
                if (group.isEmpty()) continue;

                group.sort(Comparator.comparingInt(p -> p.seatIndex));

                MapPlayerInfo driver = group.get(0);
                double dx = (driver.x - cx) / bpp;
                double dy = (driver.z - cz) / bpp;
                int sx = (int) (mapX + (mapSize / 2) + dx);
                int sy = (int) (mapY + (mapSize / 2) + dy);

                if (!isPointOnMap(sx, sy)) continue;

                int yOffset = sy - 10 - ((group.size() - 1) * 8);

                for (MapPlayerInfo pInfo : group) {
                    gui.pose().pushPose();
                    gui.pose().translate(sx, yOffset, 450);
                    gui.pose().scale(0.6f, 0.6f, 1.0f);

                    int nickColor = 0xFFFFFFFF;
                    if (pInfo.squadId != -1 && pInfo.squadId == myInternalSquadId) nickColor = 0xFF55FF55;
                    if (pInfo.name.equals(myName)) nickColor = 0xFFFFFF55;

                    gui.drawCenteredString(mc.font, Component.literal(pInfo.name), 0, 0, nickColor);
                    gui.pose().popPose();

                    yOffset += 8;
                }
            }
        }

        for (List<MapPlayerInfo> group : vehicleGroups.values()) {
            for (MapPlayerInfo p : group) {
                if (!ClientData.isHighlighted(p.name)) continue;
                int vx = (int) (mapX + (mapSize / 2) + (p.x - cx) / bpp);
                int vy = (int) (mapY + (mapSize / 2) + (p.z - cz) / bpp);
                if (isPointOnMap(vx, vy)) drawHighlightBrackets(gui, vx, vy, 9);
            }
        }

        renderSelf(gui, self, cx, cz, bpp, myInternalSquadId);
    }

    private void renderSelf(GuiGraphics gui, LocalPlayer self, double cx, double cz, double bpp, int mySquadId) {
        if (!self.isAlive()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        boolean inVehicle = self.getVehicle() != null;

        double myDx = (self.getX() - cx) / bpp;
        double myDy = (self.getZ() - cz) / bpp;
        int mySx = (int) (mapX + (mapSize / 2) + myDx);
        int mySy = (int) (mapY + (mapSize / 2) + myDy);

        if (isPointOnMap(mySx, mySy)) {
            if (!inVehicle) {
                gui.pose().pushPose();
                gui.pose().translate(mySx, mySy, 300);
                gui.pose().mulPose(Axis.ZP.rotationDegrees(self.getYRot() + 180f));

                if (mySquadId != -1) RenderSystem.setShaderColor(0.0f, 1.0f, 0.0f, 1.0f);
                else RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

                gui.blit(ICON_PLAYER_SELF, -5, -5, 10, 10, 0, 0, 16, 16, 16, 16);
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                gui.pose().popPose();

                if (ClientData.isHighlighted(self.getScoreboardName())) {
                    drawHighlightBrackets(gui, mySx, mySy, 8);
                }

                if (isShowNicknamesHeld()) {
                    gui.pose().pushPose();
                    gui.pose().translate(mySx, mySy - 10, 450);
                    gui.pose().scale(0.6f, 0.6f, 1.0f);
                    int myColor = (mySquadId != -1) ? 0xFF55FF55 : 0xFFFFFF55;
                    gui.drawCenteredString(mc.font, Component.literal(self.getScoreboardName()), 0, 0, myColor);
                    gui.pose().popPose();
                }
            }
        }
    }

    private boolean isPointOnMap(int x, int y) {
        return x >= mapX && x <= mapX + mapSize && y >= mapY && y <= mapY + mapSize;
    }

    private void drawHighlightBrackets(GuiGraphics gui, int cx, int cy, int half) {
        drawHighlightBrackets(gui, cx, cy, half, 0xFFFFFFFF);
    }

    // Плавно пульсирующий белый цвет (ARGB) для обводки доступных точек спавна
    private int spawnPulseColor() {
        double phase = (System.currentTimeMillis() % SPAWN_PULSE_MS) / (double) SPAWN_PULSE_MS;
        float k = (float) (0.5 - 0.5 * Math.cos(phase * Math.PI * 2.0)); // 0..1, плавно
        float a = SPAWN_PULSE_MIN_ALPHA + (1.0f - SPAWN_PULSE_MIN_ALPHA) * k;
        return (Math.round(a * 255.0f) << 24) | 0x00FFFFFF;
    }

    private void drawHighlightBrackets(GuiGraphics gui, int cx, int cy, int half, int color) {
        final int arm = 3;
        int x1 = cx - half, y1 = cy - half;
        int x2 = cx + half, y2 = cy + half;

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        gui.pose().pushPose();
        gui.pose().translate(0, 0, 340);

        gui.fill(x1, y1, x1 + arm, y1 + 1, color);
        gui.fill(x1, y1, x1 + 1, y1 + arm, color);
        gui.fill(x2 - arm, y1, x2, y1 + 1, color);
        gui.fill(x2 - 1, y1, x2, y1 + arm, color);
        gui.fill(x1, y2 - 1, x1 + arm, y2, color);
        gui.fill(x1, y2 - arm, x1 + 1, y2, color);
        gui.fill(x2 - arm, y2 - 1, x2, y2, color);
        gui.fill(x2 - 1, y2 - arm, x2, y2, color);

        gui.pose().popPose();
    }

    private ResourceLocation getFlagTexture(String faction) {
        if (faction == null || faction.equalsIgnoreCase("none")) return null;
        return new ResourceLocation("aas", "textures/gui/flags/" + faction.toLowerCase() + ".png");
    }

    public void centerOnPlayer() { mapPanX = 0; mapPanZ = 0; }
    public double getCenterX(LocalPlayer player) { return player.getX() + mapPanX; }
    public double getCenterZ(LocalPlayer player) { return player.getZ() + mapPanZ; }
    public double getBlocksPerPixel() { return followMode ? ClientData.miniMapScale : ClientData.mapScale; }
    /** Keeps the view inside the map so the background gradient is never visible. */
    private void clampView(LocalPlayer p) {
        if (mapSize <= 0) return;
        double maxBpp = MapGrid.maxBpp(ClientData.mapSizeBlocks, mapSize, 0.5);
        if (ClientData.mapScale > maxBpp) ClientData.mapScale = maxBpp;
        double view = mapSize * ClientData.mapScale;
        double cx = MapGrid.clampCenter(p.getX() + mapPanX, ClientData.mapCenterX, ClientData.mapSizeBlocks, view);
        double cz = MapGrid.clampCenter(p.getZ() + mapPanZ, ClientData.mapCenterZ, ClientData.mapSizeBlocks, view);
        mapPanX = cx - p.getX();
        mapPanZ = cz - p.getZ();
    }
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (isMouseOver(mouseX, mouseY)) {
            ClientData.zoomMap(delta);
            return true;
        }
        return false;
    }
    private void renderVehicles(GuiGraphics gui, Minecraft mc, double cx, double cz, double bpp) {
        if (ClientData.clientVehicles == null || ClientData.clientVehicles.isEmpty()) return;

        PoseStack pose = gui.pose();

        String myTeam = "NEUTRAL";
        if (mc.player.getTeam() != null) {
            String name = mc.player.getTeam().getName().toUpperCase();
            if (name.contains("BLUE")) myTeam = "BLUE";
            else if (name.contains("RED")) myTeam = "RED";
        }
        boolean isObserver = mc.player.isCreative() || mc.player.isSpectator();

        for (AASWorldData.VehicleRecord record : ClientData.clientVehicles) {
            if (!record.team.equalsIgnoreCase(myTeam) && !isObserver) continue;

            double dx = (record.x - cx) / bpp;
            double dy = (record.z - cz) / bpp;

            int screenX = (int) (mapX + (mapSize / 2) + dx);
            int screenY = (int) (mapY + (mapSize / 2) + dy);

            if (!isPointOnMap(screenX, screenY)) continue;

            ResourceLocation icon = VEHICLE_ICONS.getOrDefault(record.type, VEHICLE_ICONS.get("DEFAULT"));

            setFilter(icon, true);

            pose.pushPose();
            pose.translate(screenX, screenY, 150);
            if (!record.type.equals("Mine")) {
                pose.mulPose(Axis.ZP.rotationDegrees(record.yaw + 180f));
            }

            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.enableBlend();

            gui.blit(icon, -6, -6, 12, 12, 0, 0, 16, 16, 16, 16);
            pose.popPose();

            setFilter(icon, false);
        }
    }

    private void renderLatticeLines(GuiGraphics gui, Minecraft mc, double cx, double cz, double bpp) {
        if (ClientData.allCapturePoints == null || ClientData.allCapturePoints.isEmpty()) return;
        if (mc.level == null) return;

        String currentDim = mc.level.dimension().location().toString();

        List<AASWorldData.CapturePoint> sortedPoints = new ArrayList<>(ClientData.allCapturePoints);
        sortedPoints.sort(Comparator.comparingInt(p -> p.bluePriority));

        List<Vec3> path = new ArrayList<>();

        if (ClientData.blueSpawns.containsKey(currentDim)) {
            BlockPos bPos = ClientData.blueSpawns.get(currentDim);
            path.add(new Vec3(bPos.getX() + 0.5, bPos.getY(), bPos.getZ() + 0.5));
        }

        for (AASWorldData.CapturePoint cp : sortedPoints) {
            path.add(cp.area.getCenter());
        }

        if (ClientData.redSpawns.containsKey(currentDim)) {
            BlockPos rPos = ClientData.redSpawns.get(currentDim);
            path.add(new Vec3(rPos.getX() + 0.5, rPos.getY(), rPos.getZ() + 0.5));
        }

        int lineColor = 0x66FFFFFF;

        for (int i = 0; i < path.size() - 1; i++) {
            Vec3 p1 = path.get(i);
            Vec3 p2 = path.get(i + 1);

            int x1 = (int) (mapX + (mapSize / 2) + (p1.x - cx) / bpp);
            int y1 = (int) (mapY + (mapSize / 2) + (p1.z - cz) / bpp);
            int x2 = (int) (mapX + (mapSize / 2) + (p2.x - cx) / bpp);
            int y2 = (int) (mapY + (mapSize / 2) + (p2.z - cz) / bpp);

            drawSolidLine(gui, x1, y1, x2, y2, lineColor);
        }
    }

    // === ГЛАВНЫЕ БАЗЫ (MAIN) ===

    private void renderMainBases(GuiGraphics gui, Minecraft mc, double cx, double cz, double bpp) {
        if (mc.level == null) return;
        String currentDim = mc.level.dimension().location().toString();
        String playerTeam = getPlayerTeamStrict(mc);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        if (ClientData.blueSpawns.containsKey(currentDim)) {
            BlockPos pos = ClientData.blueSpawns.get(currentDim);
            boolean isFriendly = playerTeam.equals("BLUE");
            drawMainBaseIcon(gui, mc, pos, cx, cz, bpp, ClientData.BLUE_FACTION, 0xFF3366CC, isFriendly);
        }

        if (ClientData.redSpawns.containsKey(currentDim)) {
            BlockPos pos = ClientData.redSpawns.get(currentDim);
            boolean isFriendly = playerTeam.equals("RED");
            drawMainBaseIcon(gui, mc, pos, cx, cz, bpp, ClientData.RED_FACTION, 0xFFCC3333, isFriendly);
        }
    }

    private void drawMainBaseIcon(GuiGraphics gui, Minecraft mc, BlockPos pos, double cx, double cz, double bpp, String faction, int fallbackColor, boolean isFriendly) {
        int pX = (int) (mapX + (mapSize / 2) + (pos.getX() + 0.5 - cx) / bpp);
        int pY = (int) (mapY + (mapSize / 2) + (pos.getZ() + 0.5 - cz) / bpp);

        if (!isPointOnMap(pX, pY)) return;

        boolean mainSelected = isFriendly && "MAIN".equals(this.selectedSpawnId);
        boolean mainAvailable = isFriendly && spawnAvailability != null && spawnAvailability.test("MAIN");
        ResourceLocation icon = mainSelected ? MAIN_SELECTED_ICON : (mainAvailable ? MAIN_AVAILABLE_ICON : MAIN_BASE_ICON);

        ResourceLocation flagTex = getFlagTexture(faction);
        if (flagTex != null && !faction.equals("none")) {
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            setFilter(flagTex, true);
            gui.blit(flagTex, pX - 8, pY - 4, 16, 9, 0, 0, 64, 36, 64, 36);
            setFilter(flagTex, false);
        } else {
            gui.fill(pX - 8, pY - 4, pX + 8, pY + 5, fallbackColor);
        }

        setFilter(icon, true);
        gui.pose().pushPose();
        gui.pose().translate(pX, pY, 150);
        if (mainSelected) gui.pose().scale(SELECTED_SPAWN_SCALE, SELECTED_SPAWN_SCALE, 1.0f);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        gui.blit(icon, -6, -6, 12, 12, 0, 0, 16, 16, 16, 16);
        gui.pose().popPose();
        setFilter(icon, false);

        if (mainAvailable) {
            drawHighlightBrackets(gui, pX, pY, 9, spawnPulseColor());
        }
    }


    // === ПРЯМАЯ ТОЛСТАЯ ЛИНИЯ ===
    private void drawSolidLine(GuiGraphics gui, int x1, int y1, int x2, int y2, int color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1) return;

        float angle = (float) Math.toDegrees(Math.atan2(dy, dx));

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        gui.pose().pushPose();
        gui.pose().translate(x1, y1, 0);
        gui.pose().mulPose(Axis.ZP.rotationDegrees(angle));

        gui.fill(0, 0, (int)len, 1, color);

        gui.pose().popPose();
    }

    private void drawSmoothCircle(GuiGraphics gui, float cx, float cy, float radius, int color, float lineWidth) {
        if (radius <= 0) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.getBuilder();
        Matrix4f matrix = gui.pose().last().pose();

        float a = (float) (color >> 24 & 255) / 255.0F;
        float r = (float) (color >> 16 & 255) / 255.0F;
        float g = (float) (color >> 8 & 255) / 255.0F;
        float b = (float) (color & 255) / 255.0F;

        float half = lineWidth / 2.0f;
        float innerR = radius - half;
        float outerR = radius + half;

        int segments = 128;

        bufferbuilder.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i <= segments; i++) {
            float angle = i * ((float) Math.PI * 2F) / segments;
            float cos = Mth.cos(angle);
            float sin = Mth.sin(angle);

            float xOuter = cx + cos * outerR;
            float yOuter = cy + sin * outerR;
            float xInner = cx + cos * innerR;
            float yInner = cy + sin * innerR;

            bufferbuilder.vertex(matrix, xOuter, yOuter, 0.0F).color(r, g, b, a).endVertex();
            bufferbuilder.vertex(matrix, xInner, yInner, 0.0F).color(r, g, b, a).endVertex();
        }
        tesselator.end();

        RenderSystem.disableBlend();
    }
    /** Пунктирная окружность (радиус и толщина в пикселях GUI, цвет ARGB). Штрихи постоянной длины по дуге. */
    private void drawDashedCircle(GuiGraphics gui, float cx, float cy, float radius, int color, float lineWidth) {
        if (radius < 2f) return;

        final float dashLen = 6f;   // длина штриха, px
        final float gapLen = 4f;    // длина промежутка, px
        int dashes = Math.max(12, Math.min(400, (int) Math.round(2.0 * Math.PI * radius / (dashLen + gapLen))));
        float step = (float) (Math.PI * 2.0) / dashes;
        float dashAngle = step * dashLen / (dashLen + gapLen);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bb = tesselator.getBuilder();
        Matrix4f matrix = gui.pose().last().pose();

        float a = (float) (color >> 24 & 255) / 255.0F;
        float r = (float) (color >> 16 & 255) / 255.0F;
        float g = (float) (color >> 8 & 255) / 255.0F;
        float b = (float) (color & 255) / 255.0F;

        float half = lineWidth / 2.0f;
        float innerR = radius - half;
        float outerR = radius + half;
        final int sub = 3;   // каждый штрих режем на 3 четырёхугольника, чтобы дуга выглядела гладко

        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int i = 0; i < dashes; i++) {
            float start = i * step;
            for (int k = 0; k < sub; k++) {
                float a0 = start + dashAngle * k / sub;
                float a1 = start + dashAngle * (k + 1) / sub;
                float c0 = Mth.cos(a0), s0 = Mth.sin(a0);
                float c1 = Mth.cos(a1), s1 = Mth.sin(a1);
                bb.vertex(matrix, cx + c0 * outerR, cy + s0 * outerR, 0.0F).color(r, g, b, a).endVertex();
                bb.vertex(matrix, cx + c0 * innerR, cy + s0 * innerR, 0.0F).color(r, g, b, a).endVertex();
                bb.vertex(matrix, cx + c1 * innerR, cy + s1 * innerR, 0.0F).color(r, g, b, a).endVertex();
                bb.vertex(matrix, cx + c1 * outerR, cy + s1 * outerR, 0.0F).color(r, g, b, a).endVertex();
            }
        }
        tesselator.end();

        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    private void renderArtilleryZones(GuiGraphics gui, double cx, double cz, double bpp) {
        if (ClientData.activeStrikes == null || ClientData.activeStrikes.isEmpty()) return;

        float radius = com.example.aas.config.AASConfig.ART_STRIKE_RADIUS.get().floatValue();

        for (AASWorldData.ActiveStrike strike : ClientData.activeStrikes) {
            double dx = (strike.pos.getX() + 0.5 - cx) / bpp;
            double dy = (strike.pos.getZ() + 0.5 - cz) / bpp;
            float sx = (float) (mapX + (mapSize / 2.0) + dx);
            float sy = (float) (mapY + (mapSize / 2.0) + dy);

            drawSmoothCircle(gui, sx, sy, radius / (float)bpp, 0xFFFF0000, 1.0f);
        }
    }
    private void renderStructures(GuiGraphics gui, Minecraft mc, double cx, double cz, double bpp) {
        PoseStack pose = gui.pose();
        String myTeam = getPlayerTeamStrict(mc);
        String myName = mc.player.getScoreboardName();
        boolean isObserver = mc.player.isCreative() || mc.player.isSpectator();

        // Значения приходят с сервера (ClientData), а не из локального конфига клиента
        double minHubDist = ClientData.minHubDistance();
        double buildRad = ClientData.hubBuildRadius();
        int spawnPulse = spawnPulseColor();

        for (AASWorldData.HubInfo hub : ClientData.clientHubs) {
            if (!hub.team.equalsIgnoreCase(myTeam) && !isObserver) continue;

            double dx = (hub.pos.getX() + 0.5 - cx) / bpp;
            double dy = (hub.pos.getZ() + 0.5 - cz) / bpp;
            float sx = (float) (mapX + (mapSize / 2.0) + dx);
            float sy = (float) (mapY + (mapSize / 2.0) + dy);

            if (hub.constructed) {
                pose.pushPose();
                pose.translate(0, 0, 50);
                drawSmoothCircle(gui, sx, sy, (float)(minHubDist / bpp), 0x60FFFFFF, 1.33f);
                drawSmoothCircle(gui, sx, sy, (float)(buildRad / bpp), 0x805555FF, 1.33f);
                pose.popPose();
            }

            if (isPointOnMap((int)sx, (int)sy)) {
                ResourceLocation icon;
                boolean hubSelected = false;
                boolean hubAvailable = false;
                if (!hub.constructed) {
                    icon = HUB_UNBUILT_ICON;
                } else {
                    String hubId = "HUB:" + hub.pos.getX() + ":" + hub.pos.getY() + ":" + hub.pos.getZ();
                    hubSelected = hubId.equals(this.selectedSpawnId);
                    hubAvailable = spawnAvailability != null && spawnAvailability.test(hubId);
                    icon = hubSelected ? HUB_SELECTED_ICON : (hubAvailable ? HUB_AVAILABLE_ICON : HUB_ICON);
                }

                setFilter(icon, true);
                pose.pushPose();
                pose.translate(sx, sy, 160);
                if (hubSelected) pose.scale(SELECTED_SPAWN_SCALE, SELECTED_SPAWN_SCALE, 1.0f);
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                gui.blit(icon, -6, -6, 12, 12, 0, 0, 16, 16, 16, 16);
                pose.popPose();
                setFilter(icon, false);

                if (hubAvailable) {
                    drawHighlightBrackets(gui, Math.round(sx), Math.round(sy), 9, spawnPulse);
                }

                if (hub.constructed) {
                    String matValue = String.valueOf(hub.materials);
                    int textWidth = mc.font.width(matValue);
                    int iconSize = 8;
                    int gap = 2;
                    float totalWidth = iconSize + gap + textWidth;

                    gui.pose().pushPose();
                    gui.pose().translate(sx, sy - 15, 500);
                    gui.pose().scale(0.8f, 0.8f, 1.0f);

                    float startX = -totalWidth / 2f;

                    RenderSystem.enableBlend();
                    RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                    setFilter(MATS_ICON, true);
                    gui.blit(MATS_ICON, (int)startX, -4, 0, 0, iconSize, iconSize, iconSize, iconSize);
                    setFilter(MATS_ICON, false);

                    gui.drawString(mc.font, matValue, (int)startX + iconSize + gap, -4, 0xFFFFAA00, true);

                    gui.pose().popPose();
                }
            }
        }

        for (AASWorldData.Squad squad : ClientData.clientSquads) {
            if (squad.rallyPos == null) continue;
            if (!squad.team.equalsIgnoreCase(myTeam) && !isObserver) continue;

            double dx = (squad.rallyPos.getX() + 0.5 - cx) / bpp;
            double dy = (squad.rallyPos.getZ() + 0.5 - cz) / bpp;
            float sx = (float) (mapX + (mapSize / 2.0) + dx);
            float sy = (float) (mapY + (mapSize / 2.0) + dy);

            if (isPointOnMap((int)sx, (int)sy)) {
                boolean isMySquad = squad.members.contains(myName);
                boolean rallySelected = isMySquad && "RALLY".equals(this.selectedSpawnId);
                boolean rallyAvailable = isMySquad && spawnAvailability != null && spawnAvailability.test("RALLY");
                ResourceLocation icon = rallySelected ? RALLY_SELECTED_ICON : (rallyAvailable ? RALLY_AVAILABLE_ICON : RALLY_ICON);

                setFilter(icon, true);
                pose.pushPose();
                pose.translate(sx, sy, 170);
                if (rallySelected) pose.scale(SELECTED_SPAWN_SCALE, SELECTED_SPAWN_SCALE, 1.0f);
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                gui.blit(icon, -5, -5, 10, 10, 0, 0, 16, 16, 16, 16);
                pose.popPose();
                setFilter(icon, false);

                if (rallyAvailable) {
                    drawHighlightBrackets(gui, Math.round(sx), Math.round(sy), 8, spawnPulse);
                }
            }
        }

        for (AASWorldData.StationInfo station : ClientData.clientStations) {
            if (!station.team.equalsIgnoreCase(myTeam) && !isObserver) continue;

            double dx = (station.pos.getX() + 0.5 - cx) / bpp;
            double dy = (station.pos.getZ() + 0.5 - cz) / bpp;
            int sx = (int) (mapX + (mapSize / 2.0) + dx);
            int sy = (int) (mapY + (mapSize / 2.0) + dy);

            if (isPointOnMap(sx, sy)) {
                setFilter(STATION_ICON, true);
                gui.pose().pushPose();
                gui.pose().translate(sx, sy, 155);

                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

                gui.blit(STATION_ICON, -5, -5, 10, 10, 0, 0, 16, 16, 16, 16);
                gui.pose().popPose();
                setFilter(STATION_ICON, false);
            }
        }
    }

    // PATH: src\main\java\com\example\aas\client\gui\AASMapRenderer.java

    private void renderSquadMarkerLogic(GuiGraphics gui, Minecraft mc, double cx, double cz, double bpp) {
        if (mc.player == null) return;

        String myName = mc.player.getScoreboardName();
        AASWorldData.Squad mySquad = null;

        for (AASWorldData.Squad s : ClientData.clientSquads) {
            if (s.members.contains(myName)) { mySquad = s; break; }
        }

        if (mySquad == null) return;

        boolean isSL      = mySquad.leader.equals(myName);
        boolean isBravo   = mySquad.bravoMembers.contains(myName) || mySquad.bravoLeader.equals(myName);
        boolean isCharlie = mySquad.charlieMembers.contains(myName) || mySquad.charlieLeader.equals(myName);

        if (mySquad.marker != null && mySquad.marker.type != 6) {
            drawMapMarkerAndLine(gui, mc, cx, cz, bpp, mySquad.marker,
                    getSquadMarkerIcon(mySquad.marker.type),
                    COLOR_SQUAD_LINE,
                    true);
        }

        // Метка Bravo
        if (mySquad.bravoMarker != null && mySquad.bravoMarker.type != 6) {
            boolean canSee = isSL || isBravo;
            if (canSee) {
                boolean withDash = isSL || isBravo;
                drawMapMarkerAndLine(gui, mc, cx, cz, bpp, mySquad.bravoMarker,
                        getBravoMarkerIcon(mySquad.bravoMarker.type), COLOR_BRAVO_LINE, withDash);
            }
        }

        // Метка Charlie
        if (mySquad.charlieMarker != null && mySquad.charlieMarker.type != 6) {
            boolean canSee = isSL || isCharlie;
            if (canSee) {
                boolean withDash = isSL || isCharlie;
                drawMapMarkerAndLine(gui, mc, cx, cz, bpp, mySquad.charlieMarker,
                        getCharlieMarkerIcon(mySquad.charlieMarker.type), COLOR_CHARLIE_LINE, withDash);
            }
        }

        if (mySquad.bravoMarker != null && mySquad.bravoMarker.type != 6 && !isSL && !isBravo) {
            drawMapMarkerAndLine(gui, mc, cx, cz, bpp, mySquad.bravoMarker,
                    getBravoMarkerIcon(mySquad.bravoMarker.type), COLOR_BRAVO_LINE, false);
        }

        if (mySquad.charlieMarker != null && mySquad.charlieMarker.type != 6 && !isSL && !isCharlie) {
            drawMapMarkerAndLine(gui, mc, cx, cz, bpp, mySquad.charlieMarker,
                    getCharlieMarkerIcon(mySquad.charlieMarker.type), COLOR_CHARLIE_LINE, false);
        }

        // === МЕТКИ ДРУГИХ ОТРЯДОВ: их видят только лидеры отрядов (SL) своей команды ===
        // Линия идёт от лидера отряда-автора до его метки.
        if (isSL) {
            String myTeam = getPlayerTeamStrict(mc);
            for (AASWorldData.Squad other : ClientData.clientSquads) {
                if (other.id == mySquad.id) continue;
                if (!other.team.equalsIgnoreCase(myTeam)) continue;
                if (other.marker == null || other.marker.type == 6) continue;
                drawAllyMarkerAndLine(gui, mc, cx, cz, bpp, other.marker,
                        getAllyMarkerIcon(other.marker.type), other);
            }
        }
    }

    // Метка и линия ДРУГОГО отряда: линия от его лидера до метки, цвет #279fdb.
    // Под иконкой пишется номер отряда-автора (вместо дистанции).
    private void drawAllyMarkerAndLine(GuiGraphics gui, Minecraft mc, double cx, double cz, double bpp,
                                       AASWorldData.SquadMarker m, ResourceLocation icon, AASWorldData.Squad ownerSquad) {

        int mx = (int) (mapX + (mapSize / 2) + (m.x - cx) / bpp);
        int my = (int) (mapY + (mapSize / 2) + (m.z - cz) / bpp);

        // Лидер-автор метки: если его нет в данных карты (мёртв/не синхронизирован) - линию не рисуем
        MapPlayerInfo leaderInfo = findMapPlayerByName(ownerSquad.leader);
        if (leaderInfo != null) {
            int lx = (int) (mapX + (mapSize / 2) + (leaderInfo.x - cx) / bpp);
            int ly = (int) (mapY + (mapSize / 2) + (leaderInfo.z - cz) / bpp);
            drawSolidLine(gui, lx, ly, mx, my, COLOR_ALLY_LINE);
        }

        if (isPointOnMap(mx, my)) {
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            gui.blit(icon, mx - 6, my - 6, 0, 0, 12, 12, 12, 12);

            drawAllySquadNumberLabel(gui, mc, mx, my, ownerSquad);
        }
    }

    // Номер отряда под чужой меткой (тот же номер, что показывается на карте у лидера отряда)
    private void drawAllySquadNumberLabel(GuiGraphics gui, Minecraft mc, int mx, int my, AASWorldData.Squad ownerSquad) {
        int num = getSquadDisplayNumber(mc, ownerSquad);
        if (num <= 0) return;
        String numText = String.valueOf(num);

        gui.pose().pushPose();
        gui.pose().translate(mx, my + 8, 600);
        gui.pose().scale(0.8f, 0.8f, 1.0f);
        int textWidth = mc.font.width(numText);
        gui.drawString(mc.font, numText, -(textWidth / 2), 0, COLOR_ALLY_LINE, true);
        gui.pose().popPose();
    }

    // Порядковый номер отряда в своей команде: CMD-отряд первый, остальные по id (как в renderAllPlayers)
    private int getSquadDisplayNumber(Minecraft mc, AASWorldData.Squad target) {
        String team = target.team;
        boolean isBlue = team != null && team.toUpperCase().contains("BLUE");
        int cmdId = isBlue ? ClientData.blueCMDId : ClientData.redCMDId;

        List<AASWorldData.Squad> teamSquads = ClientData.clientSquads.stream()
                .filter(s -> s.team.equalsIgnoreCase(team))
                .collect(Collectors.toList());
        teamSquads.sort((s1, s2) -> {
            if (s1.id == cmdId && cmdId != -1) return -1;
            if (s2.id == cmdId && cmdId != -1) return 1;
            return Integer.compare(s1.id, s2.id);
        });

        for (int i = 0; i < teamSquads.size(); i++) {
            if (teamSquads.get(i).id == target.id) return i + 1;
        }
        return 0;
    }

    private MapPlayerInfo findMapPlayerByName(String name) {
        if (name == null || name.isEmpty()) return null;
        for (MapPlayerInfo info : ClientData.mapPlayers.values()) {
            if (!info.isDead && name.equals(info.name)) return info;
        }
        return null;
    }

    private ResourceLocation getAllyMarkerIcon(int type) {
        switch (type) {
            case 1: return MARKER_ATTACK_ALLY;
            case 2: return MARKER_DEFEND_ALLY;
            case 3: return MARKER_BUILD_ALLY;
            case 5: return MARKER_ATTACK_ALLY;
            default: return MARKER_MOVE_ALLY;
        }
    }

    private void drawMapMarkerAndLine(GuiGraphics gui, Minecraft mc, double cx, double cz, double bpp,
                                      AASWorldData.SquadMarker m, ResourceLocation icon, int color, boolean withDash) {

        int mx = (int) (mapX + (mapSize / 2) + (m.x - cx) / bpp);
        int my = (int) (mapY + (mapSize / 2) + (m.z - cz) / bpp);

        int px = (int) (mapX + (mapSize / 2) + (mc.player.getX() - cx) / bpp);
        int py = (int) (mapY + (mapSize / 2) + (mc.player.getZ() - cz) / bpp);

        if (withDash) {
            drawSolidLine(gui, px, py, mx, my, color);
        }

        if (isPointOnMap(mx, my)) {
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(1, 1, 1, 1);
            gui.blit(icon, mx - 6, my - 6, 0, 0, 12, 12, 12, 12);

            double distance = Math.sqrt(mc.player.distanceToSqr(m.x, mc.player.getY(), m.z));
            String distText = (int)distance + "m";

            gui.pose().pushPose();
            gui.pose().translate(mx, my + 8, 600);
            gui.pose().scale(0.8f, 0.8f, 1.0f);
            int textWidth = mc.font.width(distText);
            gui.drawString(mc.font, distText, -(textWidth / 2), 0, color, true);
            gui.pose().popPose();
        }
    }

    private int getSquadMarkerColor(int type) {
        return COLOR_SQUAD_LINE;
    }
    private ResourceLocation getSquadMarkerIcon(int type) {
        switch (type) {
            case 1: return MARKER_ATTACK;
            case 2: return MARKER_DEFEND;
            case 3: return MARKER_BUILD;
            case 5: return MARKER_ATTACK;
            default: return MARKER_MOVE;
        }
    }

    private ResourceLocation getBravoMarkerIcon(int type) {
        switch (type) {
            case 1: return new ResourceLocation("aas", "textures/gui/map_icons/marker_attack_bravo.png");
            case 2: return new ResourceLocation("aas", "textures/gui/map_icons/marker_defend_bravo.png");
            case 3: return new ResourceLocation("aas", "textures/gui/map_icons/marker_build_bravo.png");
            default: return new ResourceLocation("aas", "textures/gui/map_icons/marker_move_bravo.png");
        }
    }

    private ResourceLocation getCharlieMarkerIcon(int type) {
        switch (type) {
            case 1: return new ResourceLocation("aas", "textures/gui/map_icons/marker_attack_charlie.png");
            case 2: return new ResourceLocation("aas", "textures/gui/map_icons/marker_defend_charlie.png");
            case 3: return new ResourceLocation("aas", "textures/gui/map_icons/marker_build_charlie.png");
            default: return new ResourceLocation("aas", "textures/gui/map_icons/marker_move_charlie.png");
        }
    }

    private void drawDashedLine(GuiGraphics gui, int x1, int y1, int x2, int y2, int color) {
        int dx = x2 - x1;
        int dy = y2 - y1;
        double len = Math.sqrt(dx*dx + dy*dy);
        if (len < 5) return;

        for (int i = 0; i < len; i += 4) {
            double t = i / len;
            int lx = (int)(x1 + dx * t);
            int ly = (int)(y1 + dy * t);
            if (isPointOnMap(lx, ly)) {
                gui.fill(lx, ly, lx + 2, ly + 2, color);
            }
        }
    }
    private String getPlayerTeamStrict(Minecraft mc) {
        if (mc.player != null && mc.player.getTeam() != null) {
            String name = mc.player.getTeam().getName().toUpperCase();
            if (name.contains("BLUE")) return "BLUE";
            if (name.contains("RED")) return "RED";
            return name;
        }
        return "NEUTRAL";
    }
    private void renderSquadPings(GuiGraphics gui, double cx, double cz, double bpp) {
        Minecraft mc = Minecraft.getInstance();
        String myName = mc.player.getScoreboardName();
        AASWorldData.Squad mySquad = null;

        for (AASWorldData.Squad s : ClientData.clientSquads) {
            if (s.members.contains(myName)) {
                mySquad = s;
                break;
            }
        }

        if (mySquad != null) {
            long time = mc.level.getGameTime();

            // 1. Пинг Лидера (Зелёный) — видят все
            if (mySquad.pingPos != null && time < mySquad.pingExpiry) {
                drawPingOnMap(gui, mySquad.pingPos, new ResourceLocation("aas", "textures/gui/map_icons/ping_eye.png"), cx, cz, bpp);
            }

            // 2. Пинг Bravo (Фиолетовый) — теперь видит весь отряд
            if (mySquad.bravoPingPos != null && time < mySquad.bravoPingExpiry) {
                drawPingOnMap(gui, mySquad.bravoPingPos, new ResourceLocation("aas", "textures/gui/map_icons/ping_eye_bravo.png"), cx, cz, bpp);
            }

            // 3. Пинг Charlie (Бирюзовый) — теперь видит весь отряд
            if (mySquad.charliePingPos != null && time < mySquad.charliePingExpiry) {
                drawPingOnMap(gui, mySquad.charliePingPos, new ResourceLocation("aas", "textures/gui/map_icons/ping_eye_charlie.png"), cx, cz, bpp);
            }

            // 4. «Глаз» ДРУГИХ отрядов — видят только лидеры отрядов своей команды (без линии, как у своего)
            if (mySquad.leader.equals(myName)) {
                String myTeam = getPlayerTeamStrict(mc);
                for (AASWorldData.Squad other : ClientData.clientSquads) {
                    if (other.id == mySquad.id) continue;
                    if (!other.team.equalsIgnoreCase(myTeam)) continue;
                    if (other.pingPos != null && time < other.pingExpiry) {
                        drawAllyPingOnMap(gui, mc, other, cx, cz, bpp);
                    }
                }
            }
        }
    }

    // «Глаз» другого отряда: иконка + номер отряда под ней (без линии и без дистанции)
    private void drawAllyPingOnMap(GuiGraphics gui, Minecraft mc, AASWorldData.Squad ownerSquad, double cx, double cz, double bpp) {
        BlockPos pos = ownerSquad.pingPos;
        int px = (int) (mapX + (mapSize / 2) + (pos.getX() + 0.5 - cx) / bpp);
        int py = (int) (mapY + (mapSize / 2) + (pos.getZ() + 0.5 - cz) / bpp);

        if (isPointOnMap(px, py)) {
            RenderSystem.setShaderColor(1, 1, 1, 1);
            gui.blit(PING_EYE_ALLY, px - 6, py - 6, 0, 0, 12, 12, 12, 12);
            drawAllySquadNumberLabel(gui, mc, px, py, ownerSquad);
        }
    }

    private void drawPingOnMap(GuiGraphics gui, BlockPos pos, ResourceLocation icon, double cx, double cz, double bpp) {
        double dx = (pos.getX() + 0.5 - cx) / bpp;
        double dz = (pos.getZ() + 0.5 - cz) / bpp;
        int px = (int) (mapX + (mapSize / 2) + dx);
        int py = (int) (mapY + (mapSize / 2) + dz);

        if (isPointOnMap(px, py)) {
            RenderSystem.setShaderColor(1, 1, 1, 1);
            gui.blit(icon, px - 6, py - 6, 0, 0, 12, 12, 12, 12);
        }
    }
    private void renderTacticalMarkers(GuiGraphics gui, Minecraft mc, double cx, double cz, double bpp) {
        String myTeam = getPlayerTeamStrict(mc);
        long currentTime = mc.level.getGameTime();

        float totalLifetime = 3600.0f;

        for (AASWorldData.MapMarker m : ClientData.activeMarkers) {
            if (!m.team.equalsIgnoreCase(myTeam)) continue;

            long timeLeft = m.expiryTick - currentTime;

            if (timeLeft <= 0) continue;

            float alpha = Mth.clamp((float)timeLeft / totalLifetime, 0.0f, 1.0f);

            // Стрелка: тип "Arrow:<x>:<z>", начало = m.pos, конец записан в типе
            if (m.type.startsWith("Arrow:")) {
                String[] p = m.type.split(":");
                if (p.length == 3) {
                    try {
                        double hx = Double.parseDouble(p[1]);
                        double hz = Double.parseDouble(p[2]);
                        int tx = (int) (mapX + (mapSize / 2) + (m.pos.getX() - cx) / bpp);
                        int ty = (int) (mapY + (mapSize / 2) + (m.pos.getZ() - cz) / bpp);
                        int ex = (int) (mapX + (mapSize / 2) + (hx - cx) / bpp);
                        int ey = (int) (mapY + (mapSize / 2) + (hz - cz) / bpp);
                        drawArrow(gui, tx, ty, ex, ey, Math.max(alpha, 0.25f));
                    } catch (NumberFormatException ignored) {}
                }
                continue;
            }

            double dx = (m.pos.getX() - cx) / bpp;
            double dy = (m.pos.getZ() - cz) / bpp;
            int sx = (int) (mapX + (mapSize / 2) + dx);
            int sy = (int) (mapY + (mapSize / 2) + dy);

            // Метка хаба: золотистые пунктирные радиусы (запрет другого хаба + радиус стройки).
            // Рисуем до проверки "центр на карте", чтобы большие круги были видны, даже если иконка за краем.
            if (m.type.equals("Hub Marker")) {
                int hubAlpha = (int) (Mth.clamp(alpha, 0.25f, 1.0f) * 255f);
                int goldColor = (hubAlpha << 24) | 0xFFD700;
                gui.pose().pushPose();
                gui.pose().translate(0, 0, 50);
                drawDashedCircle(gui, sx, sy, (float) (ClientData.minHubDistance() / bpp), goldColor, 1.33f);
                drawDashedCircle(gui, sx, sy, (float) (ClientData.hubBuildRadius() / bpp), goldColor, 1.33f);
                gui.pose().popPose();
            }

            if (!isPointOnMap(sx, sy)) continue;

            ResourceLocation icon = getMarkerIcon(m.type);

            setFilter(icon, true);
            gui.pose().pushPose();
            gui.pose().translate(sx, sy, 120);

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);

            gui.blit(icon, -8, -8, 16, 16, 0, 0, 32, 32, 32, 32);

            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

            gui.pose().popPose();
            setFilter(icon, false);
        }
    }

    private void renderSquadRhombusMarkers(GuiGraphics gui, Minecraft mc, double cx, double cz, double bpp) {
        long time = mc.level.getGameTime();
        String myName = mc.player.getScoreboardName();
        String myTeam = getPlayerTeamStrict(mc);
        boolean amISquadLeader = isSquadLeaderOrFTL(mc.player);

        boolean isBlueForNums = myTeam != null && myTeam.contains("BLUE");
        int teamCMDIdForNums = isBlueForNums ? ClientData.blueCMDId : ClientData.redCMDId;

        List<AASWorldData.Squad> teamSquadsForNums = ClientData.clientSquads.stream()
                .filter(s -> s.team.equalsIgnoreCase(myTeam))
                .collect(Collectors.toList());
        teamSquadsForNums.sort((s1, s2) -> {
            if (s1.id == teamCMDIdForNums && teamCMDIdForNums != -1) return -1;
            if (s2.id == teamCMDIdForNums && teamCMDIdForNums != -1) return 1;
            return Integer.compare(s1.id, s2.id);
        });

        for (AASWorldData.Squad squad : ClientData.clientSquads) {
            if (!squad.team.equalsIgnoreCase(myTeam)) continue;
            boolean canSee = squad.members.contains(myName) || amISquadLeader;
            if (!canSee) continue;

            boolean isCMDSquad = (teamCMDIdForNums != -1 && squad.id == teamCMDIdForNums);

            drawRhombusMarkerList(gui, mc, squad.rhombusMarkers, squad, teamSquadsForNums, time, cx, cz, bpp, isCMDSquad);
            drawRhombusMarkerList(gui, mc, squad.bravoRhombusMarkers, squad, teamSquadsForNums, time, cx, cz, bpp, isCMDSquad);
            drawRhombusMarkerList(gui, mc, squad.charlieRhombusMarkers, squad, teamSquadsForNums, time, cx, cz, bpp, isCMDSquad);
        }
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    private void drawRhombusMarkerList(GuiGraphics gui, Minecraft mc, List<AASWorldData.SquadMarker> markers,
                                       AASWorldData.Squad squad, List<AASWorldData.Squad> teamSquadsForNums,
                                       long time, double cx, double cz, double bpp, boolean isCMDSquad) {
        for (AASWorldData.SquadMarker rm : markers) {
            long timeLeft = rm.expiryTick - time;
            if (timeLeft <= 0) continue;

            float alpha = Mth.clamp((float) timeLeft / 3600f, 0.1f, 1.0f);

            // стрелка отряда (тип 7) — рисуем зелёную стрелку вместо ромбика
            if (rm.type == 7) {
                drawSquadArrow(gui, mc, rm, squad, alpha, cx, cz, bpp);
                continue;
            }

            int mx = (int) (mapX + (mapSize / 2) + (rm.x - cx) / bpp);
            int my = (int) (mapY + (mapSize / 2) + (rm.z - cz) / bpp);

            if (isPointOnMap(mx, my)) {
                ResourceLocation rhombusTex = isCMDSquad ? RHOMBUS_CMD : RHOMBUS_NORMAL;

                RenderSystem.enableBlend();
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
                gui.blit(rhombusTex, mx - 8, my - 8, 0, 0, 16, 16, 16, 16);

                gui.pose().pushPose();
                gui.pose().translate(mx, my, 500);
                gui.pose().scale(0.5f, 0.5f, 1.0f);

                int displayNum = 0;
                for (int i = 0; i < teamSquadsForNums.size(); i++) {
                    if (teamSquadsForNums.get(i).id == squad.id) { displayNum = i + 1; break; }
                }

                String numStr = String.valueOf(displayNum);
                int whiteWithAlpha = ((int) (alpha * 255) << 24) | 0xFFFFFF;
                drawSquadNumber(gui, mc.font, numStr, -(mc.font.width(numStr) / 2), -4, whiteWithAlpha);

                gui.pose().popPose();
            }
        }
    }

    /** Зелёная стрелка отряда. Видят ТОЛЬКО участники этого отряда (лидеры других отрядов — нет). */
    private void drawSquadArrow(GuiGraphics gui, Minecraft mc, AASWorldData.SquadMarker rm,
                                AASWorldData.Squad squad, float alpha,
                                double cx, double cz, double bpp) {
        if (mc.player == null || !squad.members.contains(mc.player.getScoreboardName())) return;

        int sx = (int) (mapX + (mapSize / 2) + (rm.x - cx) / bpp);
        int sy = (int) (mapY + (mapSize / 2) + (rm.z - cz) / bpp);
        int ex = (int) (mapX + (mapSize / 2) + (rm.endX - cx) / bpp);
        int ey = (int) (mapY + (mapSize / 2) + (rm.endZ - cz) / bpp);

        drawArrow(gui, sx, sy, ex, ey, Math.max(alpha, 0.25f), 0x33DD33);
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    /** Красная стрелка с чёрной обводкой (командная): от (x1,y1) до (x2,y2), конус на конце. */
    public void drawArrow(GuiGraphics gui, int x1, int y1, int x2, int y2, float alpha) {
        drawArrow(gui, x1, y1, x2, y2, alpha, 0xE02020);
    }

    /** Стрелка с чёрной обводкой и заливкой цвета rgb (0xRRGGBB). */
    public void drawArrow(GuiGraphics gui, int x1, int y1, int x2, int y2, float alpha, int rgb) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 4) return;
        float angle = (float) Math.toDegrees(Math.atan2(dy, dx));
        int a = (int) (Mth.clamp(alpha, 0f, 1f) * 255f);
        int black = (a << 24);
        int fillCol = (a << 24) | (rgb & 0xFFFFFF);

        float headLen = Math.min(7f, len * 0.6f);
        float baseX = len - headLen;
        int shaftEnd = (int) (baseX + 1);
        float cy = 1f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        gui.pose().pushPose();
        gui.pose().translate(x1, y1, 0);
        gui.pose().mulPose(Axis.ZP.rotationDegrees(angle));

        gui.fill(-1, -1, shaftEnd, 3, black);
        fillTriangle(gui, len + 1.5f, cy, baseX - 0.75f, cy - 4.25f, baseX - 0.75f, cy + 4.25f, black);
        gui.fill(0, 0, shaftEnd, 2, fillCol);
        fillTriangle(gui, len, cy, baseX, cy - 3f, baseX, cy + 3f, fillCol);

        gui.pose().popPose();
    }

    private void fillTriangle(GuiGraphics gui, float x1, float y1, float x2, float y2,
                              float x3, float y3, int argb) {
        float a = (argb >> 24 & 255) / 255.0F;
        float r = (argb >> 16 & 255) / 255.0F;
        float g = (argb >> 8 & 255) / 255.0F;
        float b = (argb & 255) / 255.0F;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bb = tesselator.getBuilder();
        Matrix4f m = gui.pose().last().pose();
        bb.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        bb.vertex(m, x1, y1, 0f).color(r, g, b, a).endVertex();
        bb.vertex(m, x2, y2, 0f).color(r, g, b, a).endVertex();
        bb.vertex(m, x3, y3, 0f).color(r, g, b, a).endVertex();
        tesselator.end();
        RenderSystem.enableCull();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isMouseOver(mouseX, mouseY)) {
            if (button == 0) {
                isDraggingMap = true;
                lastMouseX = mouseX;
                lastMouseY = mouseY;
                return true;
            }
            if (button == 1) return true;
        }
        return false;
    }

    public void mouseReleased(int button) {
        if (button == 0) isDraggingMap = false;
    }

    private boolean isSquadLeaderOrFTL(LocalPlayer player) {
        if (player == null) return false;
        String pName = player.getScoreboardName();
        for (AASWorldData.Squad s : ClientData.clientSquads) {
            if (s.leader.equals(pName) || s.bravoLeader.equals(pName) || s.charlieLeader.equals(pName)) {
                return true;
            }
        }
        return false;
    }

    // ==================== УДАЛЕНИЕ МЕТОК (ПКМ ПО МЕТКЕ -> МЕНЮ УДАЛЕНИЯ) ====================

    private static final ResourceLocation MARKER_DELETE_ICON = new ResourceLocation("aas", "textures/gui/map_icons/marker_delete.png");

    /** Информация о метке, по которой кликнули на карте (для контекстного меню удаления). */
    public static class MarkerHit {
        public enum MarkerCategory {
            SQUAD_RHOMBUS,
            SQUAD_NORMAL,
            TACTICAL
        }

        public MarkerCategory category;
        public AASWorldData.Squad squad;
        public String list;
        public AASWorldData.SquadMarker squadMarker;
        public AASWorldData.MapMarker mapMarker;
        public int screenX, screenY;

        public double worldX;
        public double worldZ;

        public static MarkerHit squad(AASWorldData.Squad squad, String list, AASWorldData.SquadMarker marker, int sx, int sy, double wx, double wz) {
            MarkerHit hit = new MarkerHit();
            hit.category = "NORMAL".equals(list) ? MarkerCategory.SQUAD_NORMAL : MarkerCategory.SQUAD_RHOMBUS;
            hit.squad = squad;
            hit.list = list;
            hit.squadMarker = marker;
            hit.screenX = sx;
            hit.screenY = sy;
            hit.worldX = wx;
            hit.worldZ = wz;
            return hit;
        }

        public static MarkerHit tactical(AASWorldData.MapMarker marker, int sx, int sy, double wx, double wz) {
            MarkerHit hit = new MarkerHit();
            hit.category = MarkerCategory.TACTICAL;
            hit.mapMarker = marker;
            hit.screenX = sx;
            hit.screenY = sy;
            hit.worldX = wx;
            hit.worldZ = wz;
            return hit;
        }
    }

    /** Ищет любую доступную для удаления метку под курсором. */
    private MarkerHit findMarkerAt(double mouseX, double mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return null;
        double bpp = getBlocksPerPixel();
        double cx = getCenterX(mc.player);
        double cz = getCenterZ(mc.player);
        long time = mc.level.getGameTime();
        String myName = mc.player.getScoreboardName();
        String myTeam = getPlayerTeamStrict(mc);
        boolean amILeader = isSquadLeaderOrFTL(mc.player);

        for (AASWorldData.Squad squad : ClientData.clientSquads) {
            if (myTeam == null || !squad.team.equalsIgnoreCase(myTeam)) continue;
            boolean canSee = squad.members.contains(myName) || amILeader;
            if (!canSee) continue;

            MarkerHit hit = null;

            hit = checkMarkerListHit(squad, squad.rhombusMarkers, "SL", mouseX, mouseY, cx, cz, bpp, time);
            if (hit == null) hit = checkMarkerListHit(squad, squad.bravoRhombusMarkers, "BRAVO", mouseX, mouseY, cx, cz, bpp, time);
            if (hit == null) hit = checkMarkerListHit(squad, squad.charlieRhombusMarkers, "CHARLIE", mouseX, mouseY, cx, cz, bpp, time);

            if (hit == null && squad.marker != null && squad.marker.type != 6) {
                int mx = (int) (mapX + (mapSize / 2) + (squad.marker.x - cx) / bpp);
                int my = (int) (mapY + (mapSize / 2) + (squad.marker.z - cz) / bpp);
                if (isPointOnMap(mx, my) && Math.hypot(mouseX - mx, mouseY - my) <= 8) {
                    hit = MarkerHit.squad(squad, "SL", squad.marker, mx, my, squad.marker.x, squad.marker.z);
                }
            }

            if (hit == null && squad.bravoMarker != null && squad.bravoMarker.type != 6) {
                int mx = (int) (mapX + (mapSize / 2) + (squad.bravoMarker.x - cx) / bpp);
                int my = (int) (mapY + (mapSize / 2) + (squad.bravoMarker.z - cz) / bpp);
                if (isPointOnMap(mx, my) && Math.hypot(mouseX - mx, mouseY - my) <= 8) {
                    hit = MarkerHit.squad(squad, "BRAVO", squad.bravoMarker, mx, my, squad.bravoMarker.x, squad.bravoMarker.z);
                }
            }

            if (hit == null && squad.charlieMarker != null && squad.charlieMarker.type != 6) {
                int mx = (int) (mapX + (mapSize / 2) + (squad.charlieMarker.x - cx) / bpp);
                int my = (int) (mapY + (mapSize / 2) + (squad.charlieMarker.z - cz) / bpp);
                if (isPointOnMap(mx, my) && Math.hypot(mouseX - mx, mouseY - my) <= 8) {
                    hit = MarkerHit.squad(squad, "CHARLIE", squad.charlieMarker, mx, my, squad.charlieMarker.x, squad.charlieMarker.z);
                }
            }

            if (hit != null) return hit;
        }

        MarkerHit tacticalHit = checkTacticalMarkersHit(mouseX, mouseY, cx, cz, bpp, time);
        if (tacticalHit != null) return tacticalHit;

        return null;
    }

    /** Проверяет попадание курсора по тактическим меткам (TEAM/ENEMY). */
    private MarkerHit checkTacticalMarkersHit(double mouseX, double mouseY, double cx, double cz, double bpp, long time) {
        Minecraft mc = Minecraft.getInstance();
        String myTeam = getPlayerTeamStrict(mc);

        for (AASWorldData.MapMarker m : ClientData.activeMarkers) {
            if (!m.team.equalsIgnoreCase(myTeam)) continue;
            if (time >= m.expiryTick) continue;

            int mx = (int) (mapX + (mapSize / 2) + (m.pos.getX() - cx) / bpp);
            int my = (int) (mapY + (mapSize / 2) + (m.pos.getZ() - cz) / bpp);
            if (!isPointOnMap(mx, my)) continue;

            double dist = Math.hypot(mouseX - mx, mouseY - my);
            if (dist <= 8) {
                return MarkerHit.tactical(m, mx, my, m.pos.getX(), m.pos.getZ());
            }
        }
        return null;
    }

    private MarkerHit pendingDeleteHit = null;

    /** Открыто ли сейчас маленькое контекстное меню удаления метки. */
    public boolean isMarkerDeleteMenuOpen() {
        return pendingDeleteHit != null;
    }

    public void closeMarkerDeleteMenu() {
        pendingDeleteHit = null;
    }

    private int[] getPendingDeleteHitScreenPos() {
        if (pendingDeleteHit == null) return null;
        return new int[]{pendingDeleteHit.screenX, pendingDeleteHit.screenY};
    }

    private MarkerHit checkMarkerListHit(AASWorldData.Squad squad, List<AASWorldData.SquadMarker> list, String listName,
                                         double mouseX, double mouseY, double cx, double cz, double bpp, long time) {
        Minecraft mc = Minecraft.getInstance();
        for (AASWorldData.SquadMarker rm : list) {
            if (time >= rm.expiryTick) continue;

            // стрелку отряда видят и удаляют только члены этого отряда
            if (rm.type == 7 && (mc.player == null
                    || !squad.members.contains(mc.player.getScoreboardName()))) continue;

            int mx = (int) (mapX + (mapSize / 2) + (rm.x - cx) / bpp);
            int my = (int) (mapY + (mapSize / 2) + (rm.z - cz) / bpp);

            if (!isPointOnMap(mx, my)) continue;

            double dist = Math.hypot(mouseX - mx, mouseY - my);
            if (dist <= 8) {
                return MarkerHit.squad(squad, listName, rm, mx, my, rm.x, rm.z);
            }
        }
        return null;
    }

    /** Ищет свободную (ромбовидную) метку под курсором среди меток, видимых игроку на карте. */
    private MarkerHit findRhombusMarkerAt(double mouseX, double mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return null;

        double bpp = getBlocksPerPixel();
        double cx = getCenterX(mc.player);
        double cz = getCenterZ(mc.player);
        long time = mc.level.getGameTime();
        String myName = mc.player.getScoreboardName();
        String myTeam = getPlayerTeamStrict(mc);
        boolean amILeader = isSquadLeaderOrFTL(mc.player);

        for (AASWorldData.Squad squad : ClientData.clientSquads) {
            if (myTeam == null || !squad.team.equalsIgnoreCase(myTeam)) continue;
            boolean canSee = squad.members.contains(myName) || amILeader;
            if (!canSee) continue;

            MarkerHit hit = checkMarkerListHit(squad, squad.rhombusMarkers, "SL", mouseX, mouseY, cx, cz, bpp, time);
            if (hit == null) hit = checkMarkerListHit(squad, squad.bravoRhombusMarkers, "BRAVO", mouseX, mouseY, cx, cz, bpp, time);
            if (hit == null) hit = checkMarkerListHit(squad, squad.charlieRhombusMarkers, "CHARLIE", mouseX, mouseY, cx, cz, bpp, time);
            if (hit != null) return hit;
        }
        return null;
    }

    /**
     * Проверяет, вправе ли текущий игрок удалить эту метку.
     * - SL может удалять ЛЮБЫЕ метки (отрядные и тактические) всей своей команды.
     * - FTL может удалять ЛЮБЫЕ метки (SL, Bravo, Charlie, ромбики) ТОЛЬКО своего отряда.
     */
    private boolean canDeleteMarker(MarkerHit hit) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || hit == null) return false;

        String myName = mc.player.getScoreboardName();

        boolean isSL = false;
        boolean isFTL = false;
        AASWorldData.Squad mySquad = null;

        for (AASWorldData.Squad s : ClientData.clientSquads) {
            if (s.leader.equals(myName)) {
                isSL = true;
                mySquad = s;
                break;
            } else if (s.bravoLeader.equals(myName) || s.charlieLeader.equals(myName)) {
                isFTL = true;
                mySquad = s;
                break;
            }
        }

        if (!isSL && !isFTL) return false;

        if (hit.category == MarkerHit.MarkerCategory.TACTICAL) {
            return isSL || isFTL;
        }

        if (hit.squad == null || mySquad == null) return false;

        if (hit.squad.id == mySquad.id) {
            return true;
        } else {
            return isSL;
        }
    }

    /**
     * Вызывается при ПКМ по карте, ДО открытия меню размещения новой метки.
     * Если под курсором есть метка, которую игрок вправе удалить, открывает
     * контекстное меню удаления и возвращает true - вызывающий код НЕ должен
     * открывать TacticalMapRadialScreen в этом случае.
     * Если подходящей метки нет - возвращает false, поведение как раньше.
     */
    public boolean tryOpenMarkerDeleteMenu(double mouseX, double mouseY) {
        MarkerHit hit = findMarkerAt(mouseX, mouseY);
        if (hit != null && canDeleteMarker(hit)) {
            pendingDeleteHit = hit;
            return true;
        }
        pendingDeleteHit = null;
        return false;
    }

    /**
     * Рисует серый квадратик с иконкой удаления (окрашенной в красный) под меткой,
     * по которой кликнули правой кнопкой мыши.
     */
    public void renderMarkerDeleteMenu(GuiGraphics gui, int mouseX, int mouseY) {
        if (pendingDeleteHit == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        double bpp = getBlocksPerPixel();
        double cx = getCenterX(mc.player);
        double cz = getCenterZ(mc.player);

        double dx = (pendingDeleteHit.worldX - cx) / bpp;
        double dy = (pendingDeleteHit.worldZ - cz) / bpp;
        int currentScreenX = (int) (mapX + (mapSize / 2.0) + dx);
        int currentScreenY = (int) (mapY + (mapSize / 2.0) + dy);

        if (!isPointOnMap(currentScreenX, currentScreenY)) return;

        int size = Math.round(20 / 1.2f);
        int x = currentScreenX - size / 2;
        int y = currentScreenY + 10;

        boolean hover = mouseX >= x && mouseX <= x + size && mouseY >= y && mouseY <= y + size;

        gui.fill(x, y, x + size, y + size, hover ? 0xFF8A8A8A : 0xFF4A4A4A);

        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(0.95f, 0.25f, 0.25f, 1.0f);
        int baseIconSize = Math.round((size - 4) / 1.3f);
        int iconSize = Math.round(baseIconSize * 1.5f);
        int iconOffset = (size - iconSize) / 2;
        gui.blit(MARKER_DELETE_ICON, x + iconOffset, y + iconOffset, 0, 0, iconSize, iconSize, iconSize, iconSize);
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    /**
     * Обрабатывает клик, когда меню удаления метки открыто.
     * Возвращает true, если клик был обработан вызывающим экраном (меню в любом случае закрывается).
     */
    public boolean handleMarkerDeleteMenuClick(double mouseX, double mouseY, int button) {
        if (pendingDeleteHit == null) return false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) { pendingDeleteHit = null; return true; }

        double bpp = getBlocksPerPixel();
        double cx = getCenterX(mc.player);
        double cz = getCenterZ(mc.player);

        double dx = (pendingDeleteHit.worldX - cx) / bpp;
        double dy = (pendingDeleteHit.worldZ - cz) / bpp;
        int currentScreenX = (int) (mapX + (mapSize / 2.0) + dx);
        int currentScreenY = (int) (mapY + (mapSize / 2.0) + dy);

        if (!isPointOnMap(currentScreenX, currentScreenY)) {
            pendingDeleteHit = null;
            return true;
        }

        int size = Math.round(20 / 1.2f);
        int x = currentScreenX - size / 2;
        int y = currentScreenY + 10;
        boolean hitIcon = mouseX >= x && mouseX <= x + size && mouseY >= y && mouseY <= y + size;

        if (button == 0 && hitIcon) {
            MarkerHit hit = pendingDeleteHit;

            if (hit.category == MarkerHit.MarkerCategory.TACTICAL) {
                PacketHandler.INSTANCE.sendToServer(new PacketDeleteMarker(hit.mapMarker.pos, hit.mapMarker.type));
            } else {
                PacketHandler.INSTANCE.sendToServer(new PacketDeleteMarker(hit.squad.id, hit.list, hit.squadMarker.id));
            }

            if (mc.player != null) mc.player.playSound(ModSounds.MAP_MARKER_PLACE.get(), 1.0f, 0.8f);
            pendingDeleteHit = null;
            return true;
        }

        pendingDeleteHit = null;
        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDraggingMap && button == 0) {
            mapPanX -= (mouseX - lastMouseX) * ClientData.mapScale;
            mapPanZ -= (mouseY - lastMouseY) * ClientData.mapScale;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }
        return false;
    }
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= mapX && mouseX <= mapX + mapSize && mouseY >= mapY && mouseY <= mapY + mapSize;
    }
    private boolean isShowNicknamesHeld() {
        Minecraft mc = Minecraft.getInstance();
        long window = mc.getWindow().getWindow();
        com.mojang.blaze3d.platform.InputConstants.Key nickKey = com.example.aas.client.ModKeyBindings.SHOW_NICKNAMES_KEY.getKey();

        if (nickKey.getType() == com.mojang.blaze3d.platform.InputConstants.Type.MOUSE) {
            return org.lwjgl.glfw.GLFW.glfwGetMouseButton(window, nickKey.getValue()) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
        } else {
            return com.mojang.blaze3d.platform.InputConstants.isKeyDown(window, nickKey.getValue());
        }
    }
    @Override
    public void close() {}
}