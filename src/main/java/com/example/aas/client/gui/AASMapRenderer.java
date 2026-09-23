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
import java.util.Comparator; // Р”РѕР±Р°РІСЊС‚Рµ СЌС‚Сѓ СЃС‚СЂРѕРєСѓ
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
        // Р—Р°РіСЂСѓР¶Р°РµРј РёР· РїР°РїРєРё "maps/"
        return MAP_ICONS_CACHE.computeIfAbsent(img, k -> new ResourceLocation("aas", "textures/gui/maps/" + k + ".png"));
    }

    private int mapX, mapY, mapSize;
    // РўРµРєСѓС‰РёР№ Р·СѓРј (СЃРєРѕР»СЊРєРѕ Р±Р»РѕРєРѕРІ РІ РѕРґРЅРѕРј РїРёРєСЃРµР»Рµ РёРЅС‚РµСЂС„РµР№СЃР°)
    private double getMapScale() { return ClientData.mapScale; }
    public String selectedSpawnId = "";
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
    private static final ResourceLocation FLAG_NEUTRAL = new ResourceLocation("aas", "textures/gui/flags/neutral.png");
    private static final ResourceLocation ICON_CIRCLE = new ResourceLocation("aas", "textures/gui/map_icons/player_circle.png");
    private static final ResourceLocation ICON_PLUS = new ResourceLocation("aas", "textures/gui/map_icons/medic_plus.png");
    private static final ResourceLocation ICON_PLAYER_SELF = new ResourceLocation("aas", "textures/gui/map_icons/player_self.png");
    private static final ResourceLocation ICON_MEDIC_CIRCLE = new ResourceLocation("aas", "textures/gui/map_icons/medic_circle.png");
    private static final ResourceLocation MAP_GRID_TEXTURE = new ResourceLocation("aas", "textures/gui/map_grid.png");
    private static final ResourceLocation HUB_ICON = new ResourceLocation("aas", "textures/gui/map_icons/hub_icon.png");
    private static final ResourceLocation HUB_UNBUILT_ICON = new ResourceLocation("aas", "textures/gui/map_icons/hub_icon_unbuilt.png"); // РќРћР’РђРЇ РРљРћРќРљРђ
    private static final ResourceLocation RALLY_ICON = new ResourceLocation("aas", "textures/gui/map_icons/rally_icon.png");
    private static final ResourceLocation MAIN_BASE_ICON = new ResourceLocation("aas", "textures/gui/map_icons/main_base.png");
    private static final ResourceLocation ICON_OBJ_ATTACK = new ResourceLocation("aas", "textures/gui/map_icons/objective_attack.png");
    private static final ResourceLocation ICON_OBJ_DEFEND = new ResourceLocation("aas", "textures/gui/map_icons/objective_defend.png");
    private static final int COLOR_SQUAD_LINE = 0xFF00FF00; // чистый зелёный (#00FF00), как в референсе
    private static final int COLOR_BRAVO_LINE   = 0xFFAA33FF; // фиолетовый — Bravo
    private static final int COLOR_CHARLIE_LINE = 0xFF00E5CC; // бирюзовый — Charlie
    private static final ResourceLocation HUB_SELECTED_ICON = new ResourceLocation("aas", "textures/gui/map_icons/hub_icon_selected.png");
    private static final ResourceLocation RALLY_SELECTED_ICON = new ResourceLocation("aas", "textures/gui/map_icons/rally_icon_selected.png");
    private static final ResourceLocation MAIN_SELECTED_ICON = new ResourceLocation("aas", "textures/gui/map_icons/main_base_selected.png");
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
        // РђРІС‚РѕРјР°С‚РёС‡РµСЃРєРё РїСЂРµРІСЂР°С‰Р°РµС‚ "Enemy HUB" РІ "hub_marker.png", Р° "Tank" РІ "tank_marker.png"
        String path = type.toLowerCase()
                .replace("enemy ", "") // СѓР±РёСЂР°РµРј РїСЂРёРїРёСЃРєСѓ "enemy", РµСЃР»Рё РѕРЅР° РµСЃС‚СЊ
                .replace(" ", "_");    // РїСЂРѕР±РµР»С‹ РІ РЅРёР¶РЅРµРµ РїРѕРґС‡РµСЂРєРёРІР°РЅРёРµ

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
        // Р РёСЃСѓРµРј С‡РµСЂРЅСѓСЋ РѕР±РІРѕРґРєСѓ (СЃРјРµС‰РµРЅРёРµ РІ 4 СЃС‚РѕСЂРѕРЅС‹)
        gui.drawString(font, text, x - 1, y, 0xFF000000, false);
        gui.drawString(font, text, x + 1, y, 0xFF000000, false);
        gui.drawString(font, text, x, y - 1, 0xFF000000, false);
        gui.drawString(font, text, x, y + 1, 0xFF000000, false);
        // Р РёСЃСѓРµРј РѕСЃРЅРѕРІРЅРѕР№ С‚РµРєСЃС‚ РїРѕ С†РµРЅС‚СЂСѓ
        gui.drawString(font, text, x, y, color, false);
    }
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer localPlayer = mc.player;
        if (localPlayer == null) return;

        // 1. РљРѕРѕСЂРґРёРЅР°С‚С‹ РєР°РјРµСЂС‹ (РёРіСЂРѕРє + РїРµСЂРµС‚Р°СЃРєРёРІР°РЅРёРµ РєР°СЂС‚С‹)
        double currentScale = ClientData.mapScale;
        double cx = localPlayer.getX() + mapPanX;
        double cz = localPlayer.getZ() + mapPanZ;

        // 2. Р Р°РјРєР° Рё С„РѕРЅ
        gui.fillGradient(mapX, mapY, mapX + mapSize, mapY + mapSize, 0xFF1A2430, 0xFF050505);

        gui.enableScissor(mapX, mapY, mapX + mapSize, mapY + mapSize);

        PoseStack pose = gui.pose();
        pose.pushPose();

        // 3. РњР°СЃС€С‚Р°Р±РёСЂРѕРІР°РЅРёРµ Рё С†РµРЅС‚СЂРёСЂРѕРІР°РЅРёРµ
        pose.translate(mapX + mapSize / 2.0, mapY + mapSize / 2.0, 0);
        float scale = 1.0f / (float) currentScale;
        pose.scale(scale, scale, 1.0f);

        int s = ClientData.mapSizeBlocks;

        // Р Р°СЃС‡РµС‚ СЃРјРµС‰РµРЅРёСЏ РґР»СЏ РѕР±РµРёС… С‚РµРєСЃС‚СѓСЂ (РєР°СЂС‚Р° Рё СЃРµС‚РєР°) РѕРґРёРЅР°РєРѕРІС‹Р№
        float drawX = (float) (ClientData.mapCenterX - (s / 2.0) - cx);
        float drawY = (float) (ClientData.mapCenterZ - (s / 2.0) - cz);

        // --- РћС‚СЂРёСЃРѕРІРєР° РљРђР РўР« ---
        ResourceLocation dynamicMapTexture = getCurrentMapTexture();

        setFilter(dynamicMapTexture, true); // Р’РљР›Р®Р§РђР•Рњ РњРЇР“РљРР• РџРРљРЎР•Р›Р
        RenderSystem.setShaderTexture(0, dynamicMapTexture);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        gui.blit(dynamicMapTexture, (int)drawX, (int)drawY, s, s, 0, 0, 1024, 1024, 1024, 1024);
        setFilter(dynamicMapTexture, false);

        // --- РћС‚СЂРёСЃРѕРІРєР° РЎР•РўРљР (Grid) ---
        setFilter(MAP_GRID_TEXTURE, true);
        RenderSystem.setShaderTexture(0, MAP_GRID_TEXTURE);
        RenderSystem.enableBlend();
        gui.blit(MAP_GRID_TEXTURE, (int)drawX, (int)drawY, s, s, 0, 0, 1024, 1024, 1024, 1024);
        setFilter(MAP_GRID_TEXTURE, false);

        pose.popPose();

        // 4. РћС‚СЂРёСЃРѕРІРєР° РѕР±СЉРµРєС‚РѕРІ
        renderLatticeLines(gui, mc, cx, cz, currentScale); // Р›РёРЅРёРё РјРµР¶РґСѓ С‚РѕС‡РєР°РјРё (Р›Р°С‚С‚РёСЃ)
        renderOverlays(gui, mc, cx, cz, currentScale);     // РўРѕС‡РєРё Р·Р°С…РІР°С‚Р°
        renderMainBases(gui, mc, cx, cz, currentScale);    // РњРµР№РЅС‹ РєРѕРјР°РЅРґ
        renderArtilleryZones(gui, cx, cz, currentScale);
        renderStructures(gui, mc, cx, cz, currentScale);
        renderVehicles(gui, mc, cx, cz, currentScale);
        renderAllPlayers(gui, mc, localPlayer, cx, cz, currentScale);
        renderSquadMarkerLogic(gui, mc, cx, cz, currentScale);
        renderTacticalMarkers(gui, mc, cx, cz, currentScale);
        renderSquadRhombusMarkers(gui, mc, cx, cz, currentScale);
        renderSquadPings(gui, cx, cz, currentScale);

        gui.disableScissor();
    }

    private void renderOverlays(GuiGraphics gui, Minecraft mc, double cx, double cz, double bpp) {
        if (ClientData.allCapturePoints == null) return;

        // Р’РєР»СЋС‡Р°РµРј СЃРјРµС€РёРІР°РЅРёРµ Рё РѕС‚РєР»СЋС‡Р°РµРј С‚РµСЃС‚ РіР»СѓР±РёРЅС‹, С‡С‚РѕР±С‹ РёРєРѕРЅРєРё РЅРµ РјРµСЂС†Р°Р»Рё РїСЂРё РЅР°Р»РѕР¶РµРЅРёРё
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

            // --- Р›РћР“РРљРђ Р¦Р’Р•РўРђ Р РџР РћР—Р РђР§РќРћРЎРўР ---
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

            // --- Р’Р«Р‘РћР  РўР•РљРЎРўРЈР Р« ---
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

            // --- Р Р•РќР”Р•Р  РРљРћРќРљР ---
            float r = 1.0f, g = 1.0f, b = 1.0f;
            if (useTint) {
                r = ((tintColor >> 16) & 0xFF) / 255.0f;
                g = ((tintColor >> 8) & 0xFF) / 255.0f;
                b = (tintColor & 0xFF) / 255.0f;
            }

            // РЈСЃС‚Р°РЅР°РІР»РёРІР°РµРј С†РІРµС‚ Рё Р°Р»СЊС„Сѓ РґР»СЏ С€РµР№РґРµСЂР°
            RenderSystem.setShaderColor(r, g, b, alpha);
            setFilter(flagTex, true);

            // Р РµРЅРґРµСЂРёРј С„Р»Р°Рі
            gui.blit(flagTex, pX - 8, pY - 4, 16, 9, 0, 0, 64, 36, 64, 36);

            setFilter(flagTex, false);

            // --- Р Р•РќР”Р•Р  РўР•РљРЎРўРђ ---
            // Р Р°СЃСЃС‡РёС‚С‹РІР°РµРј С†РІРµС‚ С‚РµРєСЃС‚Р°
            int textColor = 0xFFFFFF;

            gui.pose().pushPose();
            // РЎРґРІРёРіР°РµРј РїРѕ Z (101), С‡С‚РѕР±С‹ С‚РµРєСЃС‚ Р±С‹Р» РїРѕРІРµСЂС… РІСЃРµРіРѕ
            gui.pose().translate(pX, pY + 7, 101);
            float textScale = 0.6f;
            gui.pose().scale(textScale, textScale, 1.0f);

            // РЎР±СЂР°СЃС‹РІР°РµРј С†РІРµС‚ С€РµР№РґРµСЂР° РїРµСЂРµРґ С‚РµРєСЃС‚РѕРј, С‡С‚РѕР±С‹ RenderSystem.setShaderColor РЅРµ РєСЂР°СЃРёР» С€СЂРёС„С‚
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

            gui.drawCenteredString(mc.font, cp.name, 0, 0, textColor);
            gui.pose().popPose();
        }

        // Р’РѕР·РІСЂР°С‰Р°РµРј РЅР°СЃС‚СЂРѕР№РєРё СЂРµРЅРґРµСЂР° РІ СЃС‚Р°РЅРґР°СЂС‚РЅРѕРµ СЃРѕСЃС‚РѕСЏРЅРёРµ
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

        // --- 1. Р вЂњР  Р Р€Р СџР СџР ВР  Р Р€Р вЂўР Сљ Р ВР вЂњР  Р С›Р С™Р С›Р вЂ™ Р вЂ™ Р СћР вЂўР ТђР СњР ВР С™Р вЂў ---
        Map<Integer, List<MapPlayerInfo>> vehicleGroups = new HashMap<>();

        for (MapPlayerInfo info : ClientData.mapPlayers.values()) {
            if (info.isDead) continue;
            if (info.inVehicle) {
                vehicleGroups.computeIfAbsent(info.vehicleId, k -> new ArrayList<>()).add(info);
                continue; // Р СџРЎР‚Р С•Р С—РЎС“РЎРѓР С”Р В°Р ВµР С Р С—Р ВµРЎв‚¬РЎС“РЎР‹ Р С•РЎвЂљРЎР‚Р С‘РЎРѓР С•Р Р†Р С”РЎС“
            }

            // --- 2. Р С›Р СћР  Р ВР РЋР С›Р вЂ™Р С™Р С’ Р СџР вЂўР РЃР вЂўР ТђР С›Р вЂќР С›Р вЂ™ ---
            double dx = (info.x - cx) / bpp;
            double dy = (info.z - cz) / bpp;
            int sx = (int) (mapX + (mapSize / 2) + dx);
            int sy = (int) (mapY + (mapSize / 2) + dy);

            if (!isPointOnMap(sx, sy)) continue;

            // Р’С‹РґРµР»РµРЅРЅС‹Р№ РёРіСЂРѕРє: Р±РµР»С‹Рµ СѓРіРѕР»РєРё РІРѕРєСЂСѓРі РёРєРѕРЅРєРё (СЃРµР±СЏ СЂРёСЃСѓРµС‚ renderSelf)
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
                    // РћРїСЂРµРґРµР»СЏРµРј С†РІРµС‚ (Р·РµР»РµРЅС‹Р№ РґР»СЏ СЃРІРѕРµРіРѕ РѕС‚СЂСЏРґР°, СЃРёРЅРёР№ РґР»СЏ РѕСЃС‚Р°Р»СЊРЅС‹С…)
                    float r = 0.2f, g = 0.6f, b = 1.0f;
                    if (info.squadId != -1 && info.squadId == myInternalSquadId) {
                        r = 0.0f; g = 1.0f; b = 0.0f;
                    }
                    RenderSystem.setShaderColor(r, g, b, 1.0f);

                    // Р’С‹Р±РёСЂР°РµРј РєР°РєСѓСЋ РєР°СЂС‚РёРЅРєСѓ СЂРёСЃРѕРІР°С‚СЊ: РјРµРґРёРєР° РёР»Рё РѕР±С‹С‡РЅСѓСЋ
                    ResourceLocation iconToDraw = info.isMedic ? ICON_MEDIC_CIRCLE : ICON_CIRCLE;

                    // Р РёСЃСѓРµРј РІС‹Р±СЂР°РЅРЅСѓСЋ РёРєРѕРЅРєСѓ (РёРіСЂР° РїРѕРєСЂР°СЃРёС‚ РµС‘ РІ РЅСѓР¶РЅС‹Р№ С†РІРµС‚ РёР·-Р·Р° setShaderColor РІС‹С€Рµ)
                    gui.blit(iconToDraw, sx - 3, sy - 3, 6, 6, 0, 0, 16, 16, 16, 16);

                    // РЎР±СЂР°СЃС‹РІР°РµРј С†РІРµС‚ РѕР±СЂР°С‚РЅРѕ РЅР° Р±РµР»С‹Р№
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

                // РќРћР’РћР•: РЅР°Рґ РєСЂСѓР¶РєРѕРј вЂ” Р±СѓРєРІР° B/C РґР»СЏ РіР»Р°РІС‹ С„Р°РµСЂС‚РёРјР°, СЃРѕРѕС‚РІРµС‚СЃС‚РІСѓСЋС‰РёРј С†РІРµС‚РѕРј
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

        // --- 3. Р С›Р СћР  Р ВР РЋР С›Р вЂ™Р С™Р С’ Р РЋР СџР ВР РЋР С™Р С›Р вЂ™ Р вЂ™ Р СћР вЂўР ТђР СњР ВР С™Р вЂў ---
        if (isShowNicksHeld) {
            for (List<MapPlayerInfo> group : vehicleGroups.values()) {
                if (group.isEmpty()) continue;

                // Р РЋР С•РЎР‚РЎвЂљР С‘РЎР‚РЎС“Р ВµР С: 0 Р СР ВµРЎРѓРЎвЂљР С• (Р Р†Р С•Р Т‘Р С‘РЎвЂљР ВµР В»РЎРЉ) РЎРѓР Р†Р ВµРЎР‚РЎвЂ¦РЎС“, Р С•РЎРѓРЎвЂљР В°Р В»РЎРЉР Р…РЎвЂ№Р Вµ Р Р…Р С‘Р В¶Р Вµ
                group.sort(Comparator.comparingInt(p -> p.seatIndex));

                // Р С™Р С•Р С•РЎР‚Р Т‘Р С‘Р Р…Р В°РЎвЂљРЎвЂ№ Р В±Р ВµРЎР‚Р ВµР С РЎС“ Р Р†Р С•Р Т‘Р С‘РЎвЂљР ВµР В»РЎРЏ
                MapPlayerInfo driver = group.get(0);
                double dx = (driver.x - cx) / bpp;
                double dy = (driver.z - cz) / bpp;
                int sx = (int) (mapX + (mapSize / 2) + dx);
                int sy = (int) (mapY + (mapSize / 2) + dy);

                if (!isPointOnMap(sx, sy)) continue;

                // Р СџР С•Р Т‘Р Р…Р С‘Р СР В°Р ВµР С Р Р…Р В°РЎвЂЎР В°Р В»РЎРЉР Р…РЎС“РЎР‹ РЎвЂљР С•РЎвЂЎР С”РЎС“ РЎРѓР С—Р С‘РЎРѓР С”Р В° Р Р† Р В·Р В°Р Р†Р С‘РЎРѓР С‘Р СР С•РЎРѓРЎвЂљР С‘ Р С•РЎвЂљ Р С”Р С•Р В»Р С‘РЎвЂЎР ВµРЎРѓРЎвЂљР Р†Р В° Р С‘Р С–РЎР‚Р С•Р С”Р С•Р Р†, РЎвЂЎРЎвЂљР С•Р В±РЎвЂ№ РЎРѓР С—Р С‘РЎРѓР С•Р С” РЎвЂ Р ВµР Р…РЎвЂљРЎР‚Р С‘РЎР‚Р С•Р Р†Р В°Р В»РЎРѓРЎРЏ Р Р…Р В°Р Т‘ Р СР В°РЎв‚¬Р С‘Р Р…Р С•Р в„–
                int yOffset = sy - 10 - ((group.size() - 1) * 8);

                for (MapPlayerInfo pInfo : group) {
                    gui.pose().pushPose();
                    gui.pose().translate(sx, yOffset, 450);
                    gui.pose().scale(0.6f, 0.6f, 1.0f);

                    int nickColor = 0xFFFFFFFF; // Р вЂР ВµР В»РЎвЂ№Р в„–
                    if (pInfo.squadId != -1 && pInfo.squadId == myInternalSquadId) nickColor = 0xFF55FF55; // Р вЂ”Р ВµР В»Р ВµР Р…РЎвЂ№Р в„– Р Т‘Р В»РЎРЏ РЎРѓР С”Р Р†Р В°Р Т‘Р В°
                    if (pInfo.name.equals(myName)) nickColor = 0xFFFFFF55; // Р вЂ“Р ВµР В»РЎвЂљРЎвЂ№Р в„– Р Т‘Р В»РЎРЏ РЎРѓР ВµР В±РЎРЏ

                    gui.drawCenteredString(mc.font, Component.literal(pInfo.name), 0, 0, nickColor);
                    gui.pose().popPose();

                    yOffset += 8; // Р РЋР С—РЎС“РЎРѓР С”Р В°Р ВµР СРЎРѓРЎРЏ Р Р…Р В° РЎРѓРЎвЂљРЎР‚Р С•РЎвЂЎР С”РЎС“ Р Р…Р С‘Р В¶Р Вµ Р Т‘Р В»РЎРЏ РЎРѓР В»Р ВµР Т‘РЎС“РЎР‹РЎвЂ°Р ВµР С–Р С• Р С—Р В°РЎРѓРЎРѓР В°Р В¶Р С‘РЎР‚Р В°
                }
            }
        }

        // Р’С‹РґРµР»РµРЅРЅС‹Рµ РёРіСЂРѕРєРё РІ С‚РµС…РЅРёРєРµ: СЂР°РјРєР° РІРѕРєСЂСѓРі РёРєРѕРЅРєРё С‚РµС…РЅРёРєРё
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
            // Р РёСЃСѓРµРј СЃРµР±СЏ СЃС‚СЂРµР»РѕС‡РєРѕР№ РўРћР›Р¬РљРћ РµСЃР»Рё РјС‹ РїРµС€РєРѕРј
            if (!inVehicle) {
                gui.pose().pushPose();
                gui.pose().translate(mySx, mySy, 300);
                gui.pose().mulPose(Axis.ZP.rotationDegrees(self.getYRot() + 180f));

                if (mySquadId != -1) RenderSystem.setShaderColor(0.0f, 1.0f, 0.0f, 1.0f);
                else RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

                gui.blit(ICON_PLAYER_SELF, -5, -5, 10, 10, 0, 0, 16, 16, 16, 16);
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                gui.pose().popPose();

                // Р•СЃР»Рё РІС‹РґРµР»РёР»Рё СЃР°РјРѕРіРѕ СЃРµР±СЏ РІ СЃРїРёСЃРєРµ РѕС‚СЂСЏРґРѕРІ
                if (ClientData.isHighlighted(self.getScoreboardName())) {
                    drawHighlightBrackets(gui, mySx, mySy, 8);
                }

                // РРјСЏ РґР»СЏ СЃРµР±СЏ РїРµС€РєРѕРј СЂРёСЃСѓРµС‚СЃСЏ С‚СѓС‚ (РґР»СЏ С‚РµС…РЅРёРєРё РѕРЅРѕ СЂРёСЃСѓРµС‚СЃСЏ РІ С†РёРєР»Рµ РІС‹С€Рµ)
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

    // Р‘РµР»Р°СЏ СЂР°РјРєР°-В«СѓРіРѕР»РєРёВ» РІРѕРєСЂСѓРі РёРєРѕРЅРєРё РІС‹РґРµР»РµРЅРЅРѕРіРѕ РёРіСЂРѕРєР°.
    // cx, cy - С†РµРЅС‚СЂ РёРєРѕРЅРєРё, half - РїРѕР»РѕРІРёРЅР° СЃС‚РѕСЂРѕРЅС‹ СЂР°РјРєРё (РІ РїРёРєСЃРµР»СЏС… GUI)
    private void drawHighlightBrackets(GuiGraphics gui, int cx, int cy, int half) {
        final int arm = 3;              // РґР»РёРЅР° РєР°Р¶РґРѕРіРѕ СѓРіРѕР»РєР°
        final int color = 0xFFFFFFFF;   // Р±РµР»С‹Р№
        int x1 = cx - half, y1 = cy - half;
        int x2 = cx + half, y2 = cy + half;

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        gui.pose().pushPose();
        gui.pose().translate(0, 0, 340); // РїРѕРІРµСЂС… РёРєРѕРЅРѕРє РёРіСЂРѕРєРѕРІ/С‚РµС…РЅРёРєРё

        // Р»РµРІС‹Р№ РІРµСЂС…РЅРёР№
        gui.fill(x1, y1, x1 + arm, y1 + 1, color);
        gui.fill(x1, y1, x1 + 1, y1 + arm, color);
        // РїСЂР°РІС‹Р№ РІРµСЂС…РЅРёР№
        gui.fill(x2 - arm, y1, x2, y1 + 1, color);
        gui.fill(x2 - 1, y1, x2, y1 + arm, color);
        // Р»РµРІС‹Р№ РЅРёР¶РЅРёР№
        gui.fill(x1, y2 - 1, x1 + arm, y2, color);
        gui.fill(x1, y2 - arm, x1 + 1, y2, color);
        // РїСЂР°РІС‹Р№ РЅРёР¶РЅРёР№
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
    public double getBlocksPerPixel() { return ClientData.mapScale; }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (isMouseOver(mouseX, mouseY)) {
            // Р’С‹Р·С‹РІР°РµРј Р±РµР·РѕРїР°СЃРЅС‹Р№ Р·СѓРј РёР· ClientData
            ClientData.zoomMap(delta);
            return true;
        }
        return false;
    }
    private void renderVehicles(GuiGraphics gui, Minecraft mc, double cx, double cz, double bpp) {
        if (ClientData.clientVehicles == null || ClientData.clientVehicles.isEmpty()) return;

        PoseStack pose = gui.pose();

        // РћРїСЂРµРґРµР»РµРЅРёРµ РєРѕРјР°РЅРґС‹ РёРіСЂРѕРєР°
        String myTeam = "NEUTRAL";
        if (mc.player.getTeam() != null) {
            String name = mc.player.getTeam().getName().toUpperCase();
            if (name.contains("BLUE")) myTeam = "BLUE";
            else if (name.contains("RED")) myTeam = "RED";
        }
        boolean isObserver = mc.player.isCreative() || mc.player.isSpectator();

        for (AASWorldData.VehicleRecord record : ClientData.clientVehicles) {
            // Р¤РёР»СЊС‚СЂ: РІРёРґРёРј С‚РѕР»СЊРєРѕ СЃРІРѕСЋ РєРѕРјР°РЅРґСѓ (РёР»Рё Р°РґРјРёРЅ)
            if (!record.team.equalsIgnoreCase(myTeam) && !isObserver) continue;

            // Р’РђР–РќРћ: РСЃРїРѕР»СЊР·СѓРµРј РєРѕРѕСЂРґРёРЅР°С‚С‹ x Рё z РїСЂСЏРјРѕ РёР· record (СЃРµСЂРІРµСЂРЅС‹Рµ РґР°РЅРЅС‹Рµ)
            // Р­С‚Рѕ РїРѕР·РІРѕР»СЏРµС‚ РІРёРґРµС‚СЊ С‚РµС…РЅРёРєСѓ, РґР°Р¶Рµ РµСЃР»Рё РѕРЅР° РЅРµ РїСЂРѕРіСЂСѓР¶РµРЅР° Сѓ С‚РµР±СЏ Р»РёС‡РЅРѕ
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

            // Р’Р«РљР›Р®Р§РђР•Рњ
            setFilter(icon, false);
        }
    }

    private void renderLatticeLines(GuiGraphics gui, Minecraft mc, double cx, double cz, double bpp) {
        if (ClientData.allCapturePoints == null || ClientData.allCapturePoints.isEmpty()) return;
        if (mc.level == null) return;

        String currentDim = mc.level.dimension().location().toString();

        // 1. РЎРѕСЂС‚РёСЂСѓРµРј С‚РѕС‡РєРё Р·Р°С…РІР°С‚Р° РѕС‚ РЎРёРЅРµР№ Р±Р°Р·С‹ Рє РљСЂР°СЃРЅРѕР№ (РїРѕ bluePriority РїРѕ РІРѕР·СЂР°СЃС‚Р°РЅРёСЋ)
        List<AASWorldData.CapturePoint> sortedPoints = new ArrayList<>(ClientData.allCapturePoints);
        sortedPoints.sort(Comparator.comparingInt(p -> p.bluePriority));

        // 2. РЎС‚СЂРѕРёРј РµРґРёРЅС‹Р№ РїСѓС‚СЊ РёР· РєРѕРѕСЂРґРёРЅР°С‚: РЎРёРЅРёР№ РњРµР№РЅ -> РўРѕС‡РєРё -> РљСЂР°СЃРЅС‹Р№ РњРµР№РЅ
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

        // 3. РћС‚СЂРёСЃРѕРІС‹РІР°РµРј РѕС‚СЂРµР·РєРё РјРµР¶РґСѓ РІСЃРµРјРё С‚РѕС‡РєР°РјРё РїСѓС‚Рё
        int lineColor = 0x66FFFFFF; // Р‘РµР»С‹Р№, РїРѕР»СѓРїСЂРѕР·СЂР°С‡РЅС‹Р№ (alpha ~ 40%)

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

    // === Р“Р›РђР’РќР«Р• Р‘РђР—Р« (MAIN) ===
    // Р’ С„Р°Р№Р»Рµ AASMapRenderer.java

    private void renderMainBases(GuiGraphics gui, Minecraft mc, double cx, double cz, double bpp) {
        if (mc.level == null) return;
        String currentDim = mc.level.dimension().location().toString();
        String playerTeam = getPlayerTeamStrict(mc); // Р’РµСЂРЅРµС‚ "BLUE", "RED" РёР»Рё "NEUTRAL"

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // РћС‚СЂРёСЃРѕРІРєР° РЎРёРЅРµРіРѕ РјРµР№РЅР°
        if (ClientData.blueSpawns.containsKey(currentDim)) {
            BlockPos pos = ClientData.blueSpawns.get(currentDim);
            // РџСЂРѕРІРµСЂСЏРµРј: РµСЃР»Рё РёРіСЂРѕРє РІ РЎРёРЅРµР№ РєРѕРјР°РЅРґРµ, С‚Рѕ РґР»СЏ РЅРµРіРѕ СЌС‚РѕС‚ РјРµР№РЅ - "СЃРІРѕР№"
            boolean isFriendly = playerTeam.equals("BLUE");
            drawMainBaseIcon(gui, mc, pos, cx, cz, bpp, ClientData.BLUE_FACTION, 0xFF3366CC, isFriendly);
        }

        // РћС‚СЂРёСЃРѕРІРєР° РљСЂР°СЃРЅРѕРіРѕ РјРµР№РЅР°
        if (ClientData.redSpawns.containsKey(currentDim)) {
            BlockPos pos = ClientData.redSpawns.get(currentDim);
            // РџСЂРѕРІРµСЂСЏРµРј: РµСЃР»Рё РёРіСЂРѕРє РІ РљСЂР°СЃРЅРѕР№ РєРѕРјР°РЅРґРµ, С‚Рѕ РґР»СЏ РЅРµРіРѕ СЌС‚РѕС‚ РјРµР№РЅ - "СЃРІРѕР№"
            boolean isFriendly = playerTeam.equals("RED");
            drawMainBaseIcon(gui, mc, pos, cx, cz, bpp, ClientData.RED_FACTION, 0xFFCC3333, isFriendly);
        }
    }

    private void drawMainBaseIcon(GuiGraphics gui, Minecraft mc, BlockPos pos, double cx, double cz, double bpp, String faction, int fallbackColor, boolean isFriendly) {
        int pX = (int) (mapX + (mapSize / 2) + (pos.getX() + 0.5 - cx) / bpp);
        int pY = (int) (mapY + (mapSize / 2) + (pos.getZ() + 0.5 - cz) / bpp);

        if (!isPointOnMap(pX, pY)) return;

        // РўРµРїРµСЂСЊ Р»РѕРіРёРєР° РІС‹Р±РѕСЂР° РёРєРѕРЅРєРё:
        // Р•СЃР»Рё СЌС‚Рѕ Р±Р°Р·Р° РЅР°С€РµР№ РєРѕРјР°РЅРґС‹ Р РІ РјРµРЅСЋ РІС‹Р±СЂР°РЅРѕ "MAIN" -> СЂРёСЃСѓРµРј РІС‹РґРµР»РµРЅРЅСѓСЋ РёРєРѕРЅРєСѓ
        ResourceLocation icon = (isFriendly && "MAIN".equals(this.selectedSpawnId)) ? MAIN_SELECTED_ICON : MAIN_BASE_ICON;

        // Р РµРЅРґРµСЂ С„Р»Р°РіР° РїРѕРґ РёРєРѕРЅРєРѕР№
        ResourceLocation flagTex = getFlagTexture(faction);
        if (flagTex != null && !faction.equals("none")) {
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            setFilter(flagTex, true);
            gui.blit(flagTex, pX - 8, pY - 4, 16, 9, 0, 0, 64, 36, 64, 36);
            setFilter(flagTex, false);
        } else {
            gui.fill(pX - 8, pY - 4, pX + 8, pY + 5, fallbackColor);
        }

        // Р РµРЅРґРµСЂ СЃР°РјРѕР№ РёРєРѕРЅРєРё РґРѕРјРёРєР°
        setFilter(icon, true);
        gui.pose().pushPose();
        gui.pose().translate(pX, pY, 150);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        gui.blit(icon, -6, -6, 12, 12, 0, 0, 16, 16, 16, 16);
        gui.pose().popPose();
        setFilter(icon, false);
    }


    // === РџР РЇРњРђРЇ РўРћР›РЎРўРђРЇ Р›РРќРРЇ ===
    private void drawSolidLine(GuiGraphics gui, int x1, int y1, int x2, int y2, int color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1) return;

        // Р’С‹С‡РёСЃР»СЏРµРј СѓРіРѕР» РЅР°РєР»РѕРЅР° Р»РёРЅРёРё
        float angle = (float) Math.toDegrees(Math.atan2(dy, dx));

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        gui.pose().pushPose();
        gui.pose().translate(x1, y1, 0); // РЎС‚Р°РІРёРј СЃС‚Р°СЂС‚ РІ (x1, y1)
        gui.pose().mulPose(Axis.ZP.rotationDegrees(angle)); // РџРѕРІРѕСЂР°С‡РёРІР°РµРј С‚СѓРґР°, РіРґРµ РЅР°С…РѕРґРёС‚СЃСЏ (x2, y2)

        // Р РёСЃСѓРµРј Р»РёРЅРёСЋ РЅСѓР¶РЅРѕР№ РґР»РёРЅС‹ (С‚РѕР»С‰РёРЅР° 3 РїРёРєСЃРµР»СЏ: РѕС‚ -1 РґРѕ +1 РїРѕ Y)
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

        // Р РёСЃСѓРµРј РєРѕР»СЊС†Рѕ С‚СЂРµСѓРіРѕР»СЊРЅРѕР№ Р»РµРЅС‚РѕР№ РјРµР¶РґСѓ innerR Рё outerR
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
    private void renderArtilleryZones(GuiGraphics gui, double cx, double cz, double bpp) {
        // РџСЂРѕРІРµСЂСЏРµРј, РµСЃС‚СЊ Р»Рё Р°РєС‚РёРІРЅС‹Рµ РѕР±СЃС‚СЂРµР»С‹ РІ РґР°РЅРЅС‹С… РєР»РёРµРЅС‚Р°
        if (ClientData.activeStrikes == null || ClientData.activeStrikes.isEmpty()) return;

        float radius = com.example.aas.config.AASConfig.ART_STRIKE_RADIUS.get().floatValue();

        for (AASWorldData.ActiveStrike strike : ClientData.activeStrikes) {
            // Р Р°СЃСЃС‡РёС‚С‹РІР°РµРј СЌРєСЂР°РЅРЅС‹Рµ РєРѕРѕСЂРґРёРЅР°С‚С‹ С†РµРЅС‚СЂР° РѕР±СЃС‚СЂРµР»Р°
            double dx = (strike.pos.getX() + 0.5 - cx) / bpp;
            double dy = (strike.pos.getZ() + 0.5 - cz) / bpp;
            float sx = (float) (mapX + (mapSize / 2.0) + dx);
            float sy = (float) (mapY + (mapSize / 2.0) + dy);

            // Р РёСЃСѓРµРј РєСЂР°СЃРЅС‹Р№ РєСЂСѓРі.
            // 0xFFFF0000 - СЃРїР»РѕС€РЅРѕР№ РєСЂР°СЃРЅС‹Р№, РёР»Рё 0x80FF0000 РґР»СЏ РїРѕР»СѓРїСЂРѕР·СЂР°С‡РЅРѕРіРѕ
            drawSmoothCircle(gui, sx, sy, radius / (float)bpp, 0xFFFF0000, 1.0f);

            // РћРїС†РёРѕРЅР°Р»СЊРЅРѕ: РјРѕР¶РЅРѕ РЅР°СЂРёСЃРѕРІР°С‚СЊ РµС‰Рµ РѕРґРёРЅ Р·Р°РєСЂР°С€РµРЅРЅС‹Р№ РєСЂСѓРі РІРЅСѓС‚СЂРё РґР»СЏ Р·Р°РјРµС‚РЅРѕСЃС‚Рё
            // gui.fill((int)sx-2, (int)sy-2, (int)sx+2, (int)sy+2, 0xFFFF0000);
        }
    }
    private void renderStructures(GuiGraphics gui, Minecraft mc, double cx, double cz, double bpp) {
        PoseStack pose = gui.pose();
        String myTeam = getPlayerTeamStrict(mc);
        String myName = mc.player.getScoreboardName();
        boolean isObserver = mc.player.isCreative() || mc.player.isSpectator();

        double minHubDist = com.example.aas.config.AASConfig.MIN_HUB_DISTANCE.get();
        double buildRad = com.example.aas.config.AASConfig.HUB_BUILD_RADIUS.get();

        // 1. Р РµРЅРґРµСЂ РҐРђР‘РѕРІ
        for (AASWorldData.HubInfo hub : ClientData.clientHubs) {
            if (!hub.team.equalsIgnoreCase(myTeam) && !isObserver) continue;

            double dx = (hub.pos.getX() + 0.5 - cx) / bpp;
            double dy = (hub.pos.getZ() + 0.5 - cz) / bpp;
            float sx = (float) (mapX + (mapSize / 2.0) + dx);
            float sy = (float) (mapY + (mapSize / 2.0) + dy);

            // РљСЂСѓРіРё СЂР°РґРёСѓСЃР° СЂРёСЃСѓРµРј С‚РѕР»СЊРєРѕ РґР»СЏ РґРѕСЃС‚СЂРѕРµРЅРЅРѕРіРѕ С…Р°Р±Р°
            if (hub.constructed) {
                pose.pushPose();
                pose.translate(0, 0, 50);
                drawSmoothCircle(gui, sx, sy, (float)(minHubDist / bpp), 0x60FFFFFF, 1.33f);
                drawSmoothCircle(gui, sx, sy, (float)(buildRad / bpp), 0x805555FF, 1.33f);
                pose.popPose();
            }

            if (isPointOnMap((int)sx, (int)sy)) {
                ResourceLocation icon;
                if (!hub.constructed) {
                    icon = HUB_UNBUILT_ICON;
                } else {
                    String hubId = "HUB:" + hub.pos.getX() + ":" + hub.pos.getY() + ":" + hub.pos.getZ();
                    icon = hubId.equals(this.selectedSpawnId) ? HUB_SELECTED_ICON : HUB_ICON;
                }

                setFilter(icon, true);
                pose.pushPose();
                pose.translate(sx, sy, 160);
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                gui.blit(icon, -6, -6, 12, 12, 0, 0, 16, 16, 16, 16);
                pose.popPose();
                setFilter(icon, false);

                // === Р›РћР“РРљРђ РћРўР РРЎРћР’РљР РњРђРўР•Р РРђР›РћР’ вЂ” С‚РѕР»СЊРєРѕ РґР»СЏ РґРѕСЃС‚СЂРѕРµРЅРЅРѕРіРѕ С…Р°Р±Р° ===
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

        // 2. Р РµРЅРґРµСЂ Р Р°Р»Р»Рё-РїРѕРёРЅС‚РѕРІ
        for (AASWorldData.Squad squad : ClientData.clientSquads) {
            if (squad.rallyPos == null) continue;
            if (!squad.team.equalsIgnoreCase(myTeam) && !isObserver) continue;

            double dx = (squad.rallyPos.getX() + 0.5 - cx) / bpp;
            double dy = (squad.rallyPos.getZ() + 0.5 - cz) / bpp;
            float sx = (float) (mapX + (mapSize / 2.0) + dx);
            float sy = (float) (mapY + (mapSize / 2.0) + dy);

            if (isPointOnMap((int)sx, (int)sy)) {
                boolean isMySquad = squad.members.contains(myName);
                ResourceLocation icon = (isMySquad && "RALLY".equals(this.selectedSpawnId)) ? RALLY_SELECTED_ICON : RALLY_ICON;

                setFilter(icon, true);
                pose.pushPose();
                pose.translate(sx, sy, 170);
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                gui.blit(icon, -5, -5, 10, 10, 0, 0, 16, 16, 16, 16);
                pose.popPose();
                setFilter(icon, false);
            }
        }

        // Р’РЅСѓС‚СЂРё РјРµС‚РѕРґР° renderStructures РІ СЃР°РјРѕРј РєРѕРЅС†Рµ
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

                // РЈР±СЂР°Р»Рё РїСЂРѕРІРµСЂРєСѓ РєРѕРјР°РЅРґС‹ Рё РїРѕРєСЂР°СЃРєСѓ, РїСЂРѕСЃС‚Рѕ СЃС‚Р°РІРёРј Р±РµР»С‹Р№ С†РІРµС‚
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

        // РњРµС‚РєР° Р»РёРґРµСЂР° вЂ” РІРёРґСЏС‚ РІСЃРµ, РІСЃРµРіРґР° СЃ РїСѓРЅРєС‚РёСЂРѕРј
        if (mySquad.marker != null && mySquad.marker.type != 6) {
            drawMapMarkerAndLine(gui, mc, cx, cz, bpp, mySquad.marker,
                    getSquadMarkerIcon(mySquad.marker.type),
                    COLOR_SQUAD_LINE, // ← было getSquadMarkerColor(mySquad.marker.type)
                    true);
        }

        // Метка Bravo
        if (mySquad.bravoMarker != null && mySquad.bravoMarker.type != 6) {
            boolean canSee = isSL || isBravo;
            if (canSee) {
                boolean withDash = isSL || isBravo; // свои — с пунктиром
                drawMapMarkerAndLine(gui, mc, cx, cz, bpp, mySquad.bravoMarker,
                        getBravoMarkerIcon(mySquad.bravoMarker.type), COLOR_BRAVO_LINE, withDash);
            }
        }

        // Метка Charlie
        if (mySquad.charlieMarker != null && mySquad.charlieMarker.type != 6) {
            boolean canSee = isSL || isCharlie;
            if (canSee) {
                boolean withDash = isSL || isCharlie; // свои — с пунктиром
                drawMapMarkerAndLine(gui, mc, cx, cz, bpp, mySquad.charlieMarker,
                        getCharlieMarkerIcon(mySquad.charlieMarker.type), COLOR_CHARLIE_LINE, withDash);
            }
        }

        // === Р§РЈР–РР• Р¤РђР™Р РўРРњР« (Bravo/Charlie РєРѕС‚РѕСЂС‹Рµ РЅРµ С‚РІРѕРё) ===
        // Bravo РІРёРґРЅРѕ Charlie Рё РЅР°РѕР±РѕСЂРѕС‚ вЂ” Р±РµР· РїСѓРЅРєС‚РёСЂР°
        if (mySquad.bravoMarker != null && mySquad.bravoMarker.type != 6 && !isSL && !isBravo) {
            // isCharlie РёР»Рё РїСЂРѕСЃС‚Рѕ РЅРµ Bravo Рё РЅРµ SL вЂ” РІРёРґРёС‚ РјРµС‚РєСѓ Р±РµР· РїСѓРЅРєС‚РёСЂР°
            drawMapMarkerAndLine(gui, mc, cx, cz, bpp, mySquad.bravoMarker,
                    getBravoMarkerIcon(mySquad.bravoMarker.type), COLOR_BRAVO_LINE, false);
        }

        if (mySquad.charlieMarker != null && mySquad.charlieMarker.type != 6 && !isSL && !isCharlie) {
            drawMapMarkerAndLine(gui, mc, cx, cz, bpp, mySquad.charlieMarker,
                    getCharlieMarkerIcon(mySquad.charlieMarker.type), COLOR_CHARLIE_LINE, false);
        }
    }

    // Р’СЃРїРѕРјРѕРіР°С‚РµР»СЊРЅС‹Р№ РјРµС‚РѕРґ РґР»СЏ РѕС‚СЂРёСЃРѕРІРєРё Р»СЋР±РѕР№ РјРµС‚РєРё РѕС‚СЂСЏРґР° Рё Р»РёРЅРёРё Рє РЅРµР№
    private void drawMapMarkerAndLine(GuiGraphics gui, Minecraft mc, double cx, double cz, double bpp,
                                      AASWorldData.SquadMarker m, ResourceLocation icon, int color, boolean withDash) {

        int mx = (int) (mapX + (mapSize / 2) + (m.x - cx) / bpp);
        int my = (int) (mapY + (mapSize / 2) + (m.z - cz) / bpp);

        int px = (int) (mapX + (mapSize / 2) + (mc.player.getX() - cx) / bpp);
        int py = (int) (mapY + (mapSize / 2) + (mc.player.getZ() - cz) / bpp);

        if (withDash) {
            drawSolidLine(gui, px, py, mx, my, color);   // ← было drawDashedLine(gui, px, py, mx, my, color);
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
        return COLOR_SQUAD_LINE; // теперь всегда салатовый, независимо от типа метки (move/attack/build/...)
    }
    private ResourceLocation getSquadMarkerIcon(int type) {
        switch (type) {
            case 1: return MARKER_ATTACK;
            case 2: return MARKER_DEFEND;
            case 3: return MARKER_BUILD;
            case 5: return MARKER_ATTACK; // РРєРѕРЅРєР° РґР»СЏ РѕР±РЅР°СЂСѓР¶РµРЅРЅРѕРіРѕ РІСЂР°РіР° (РєСЂР°СЃРЅР°СЏ)
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
    // Р’ С„Р°Р№Р»Рµ AASMapRenderer.java
    // Р’ AASMapRenderer.java РґРѕР±Р°РІСЊС‚Рµ РІ РєРѕРЅРµС† РјРµС‚РѕРґРѕРІ СЂРµРЅРґРµСЂРёРЅРіР° (РЅР°РїСЂРёРјРµСЂ, РІ render РёР»Рё РѕС‚РґРµР»СЊРЅС‹Р№ РјРµС‚РѕРґ)
    // Р”РѕР±Р°РІРёС‚СЊ РІ AASMapRenderer.java
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

            // 1. РџРёРЅРі Р›РёРґРµСЂР° (Р—РµР»С‘РЅС‹Р№) вЂ” РІРёРґСЏС‚ РІСЃРµ
            if (mySquad.pingPos != null && time < mySquad.pingExpiry) {
                drawPingOnMap(gui, mySquad.pingPos, new ResourceLocation("aas", "textures/gui/map_icons/ping_eye.png"), cx, cz, bpp);
            }

            // 2. РџРёРЅРі Bravo (Р¤РёРѕР»РµС‚РѕРІС‹Р№) вЂ” С‚РµРїРµСЂСЊ РІРёРґРёС‚ РІРµСЃСЊ РѕС‚СЂСЏРґ
            if (mySquad.bravoPingPos != null && time < mySquad.bravoPingExpiry) {
                drawPingOnMap(gui, mySquad.bravoPingPos, new ResourceLocation("aas", "textures/gui/map_icons/ping_eye_bravo.png"), cx, cz, bpp);
            }

            // 3. РџРёРЅРі Charlie (Р‘РёСЂСЋР·РѕРІС‹Р№) вЂ” С‚РµРїРµСЂСЊ РІРёРґРёС‚ РІРµСЃСЊ РѕС‚СЂСЏРґ
            if (mySquad.charliePingPos != null && time < mySquad.charliePingExpiry) {
                drawPingOnMap(gui, mySquad.charliePingPos, new ResourceLocation("aas", "textures/gui/map_icons/ping_eye_charlie.png"), cx, cz, bpp);
            }
        }
    }

    // Р’СЃРїРѕРјРѕРіР°С‚РµР»СЊРЅС‹Р№ РјРµС‚РѕРґ РґР»СЏ С‡РёСЃС‚РѕС‚С‹ РєРѕРґР°
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

        // РЎС‚Р°РЅРґР°СЂС‚РЅРѕРµ РІСЂРµРјСЏ Р¶РёР·РЅРё РјРµС‚РєРё, РєРѕС‚РѕСЂРѕРµ РјС‹ Р·Р°РґР°Р»Рё РІ РїР°РєРµС‚Рµ (3600 С‚РёРєРѕРІ = 3 РјРёРЅСѓС‚С‹)
        float totalLifetime = 3600.0f;

        for (AASWorldData.MapMarker m : ClientData.activeMarkers) {
            if (!m.team.equalsIgnoreCase(myTeam)) continue;

            // Р’С‹С‡РёСЃР»СЏРµРј РїСЂРѕР·СЂР°С‡РЅРѕСЃС‚СЊ
            long timeLeft = m.expiryTick - currentTime;

            // Р•СЃР»Рё РІСЂРµРјСЏ РІС‹С€Р»Рѕ, РїСЂРѕРїСѓСЃРєР°РµРј (С…РѕС‚СЏ СЃРµСЂРІРµСЂ РґРѕР»Р¶РµРЅ РёС… СѓРґР°Р»СЏС‚СЊ, СЌС‚Рѕ РґР»СЏ РїР»Р°РІРЅРѕСЃС‚Рё)
            if (timeLeft <= 0) continue;

            // РљРѕСЌС„С„РёС†РёРµРЅС‚ РїСЂРѕР·СЂР°С‡РЅРѕСЃС‚Рё: РѕС‚ 1.0 (РЅРѕРІР°СЏ) РґРѕ 0.0 (РёСЃС‡РµР·Р°СЋС‰Р°СЏ)
            float alpha = Mth.clamp((float)timeLeft / totalLifetime, 0.0f, 1.0f);

            double dx = (m.pos.getX() - cx) / bpp;
            double dy = (m.pos.getZ() - cz) / bpp;
            int sx = (int) (mapX + (mapSize / 2) + dx);
            int sy = (int) (mapY + (mapSize / 2) + dy);

            if (!isPointOnMap(sx, sy)) continue;

            ResourceLocation icon = getMarkerIcon(m.type);

            setFilter(icon, true);
            gui.pose().pushPose();
            gui.pose().translate(sx, sy, 120);

            // Р’РєР»СЋС‡Р°РµРј СЃРјРµС€РёРІР°РЅРёРµ С†РІРµС‚РѕРІ (Blend) Рё СѓСЃС‚Р°РЅР°РІР»РёРІР°РµРј РїСЂРѕР·СЂР°С‡РЅРѕСЃС‚СЊ
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha); // alpha РјРµРЅСЏРµС‚СЃСЏ СЃРѕ РІСЂРµРјРµРЅРµРј

            // Р РµРЅРґРµСЂ РёРєРѕРЅРєРё
            gui.blit(icon, -8, -8, 16, 16, 0, 0, 32, 32, 32, 32);

            // РЎР±СЂР°СЃС‹РІР°РµРј С†РІРµС‚ РѕР±СЂР°С‚РЅРѕ РІ 1.0, С‡С‚РѕР±С‹ РЅРµ РїРѕРєСЂР°СЃРёС‚СЊ РґСЂСѓРіРёРµ СЌР»РµРјРµРЅС‚С‹ РёРЅС‚РµСЂС„РµР№СЃР°
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

            // Определяем, является ли этот отряд CMD (командирским)
            boolean isCMDSquad = (teamCMDIdForNums != -1 && squad.id == teamCMDIdForNums);

            // --- Метки SL + метки фаертима Bravo + метки фаертима Charlie ---
            drawRhombusMarkerList(gui, mc, squad.rhombusMarkers, squad, teamSquadsForNums, time, cx, cz, bpp, isCMDSquad);
            drawRhombusMarkerList(gui, mc, squad.bravoRhombusMarkers, squad, teamSquadsForNums, time, cx, cz, bpp, isCMDSquad);
            drawRhombusMarkerList(gui, mc, squad.charlieRhombusMarkers, squad, teamSquadsForNums, time, cx, cz, bpp, isCMDSquad);
        }
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    // Отрисовка одного списка свободных меток отряда (общая для SL / Bravo / Charlie).
    // Параметр isCMDSquad определяет, какую текстуру использовать.
    private void drawRhombusMarkerList(GuiGraphics gui, Minecraft mc, List<AASWorldData.SquadMarker> markers,
                                       AASWorldData.Squad squad, List<AASWorldData.Squad> teamSquadsForNums,
                                       long time, double cx, double cz, double bpp, boolean isCMDSquad) {
        for (AASWorldData.SquadMarker rm : markers) {
            long timeLeft = rm.expiryTick - time;
            if (timeLeft <= 0) continue;

            float alpha = Mth.clamp((float) timeLeft / 3600f, 0.1f, 1.0f);

            int mx = (int) (mapX + (mapSize / 2) + (rm.x - cx) / bpp);
            int my = (int) (mapY + (mapSize / 2) + (rm.z - cz) / bpp);

            if (isPointOnMap(mx, my)) {
                // Выбираем текстуру: CMD использует свою, обычные — стандартную
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
    // Р’ С„Р°Р№Р»Рµ AASMapRenderer.java РёР·РјРµРЅРёС‚Рµ РјРµС‚РѕРґ:
    private void handleMapRightClick(double mouseX, double mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // РџСЂРѕРІРµСЂРєР°, СЏРІР»СЏРµС‚СЃСЏ Р»Рё РёРіСЂРѕРє Р»РёРґРµСЂРѕРј
        if (!isSquadLeaderOrFTL(mc.player)) {
            mc.player.displayClientMessage(Component.translatable("aas.msg.sl_ftl_only").withStyle(net.minecraft.ChatFormatting.RED), true);
            return;
        }

        double bpp = this.getBlocksPerPixel();
        double centerX = this.getCenterX(mc.player);
        double centerZ = this.getCenterZ(mc.player);

        // Р Р°СЃС‡РµС‚ РјРёСЂРѕРІС‹С… РєРѕРѕСЂРґРёРЅР°С‚ РёР· РїРѕР·РёС†РёРё РјС‹С€Рё РЅР° РєР°СЂС‚Рµ
        int targetX = (int) (centerX + ((mouseX - (mapX + mapSize / 2.0)) * bpp));
        int targetZ = (int) (centerZ + ((mouseY - (mapY + mapSize / 2.0)) * bpp));

        // РћС‚РєСЂС‹РІР°РµРј СЂР°РґРёР°Р»СЊРЅРѕРµ РјРµРЅСЋ С‚Р°РєС‚РёС‡РµСЃРєРёС… РјРµС‚РѕРє
        mc.setScreen(new TacticalMapRadialScreen(targetX, targetZ));
    }

    // Р”РѕР±Р°РІРёС‚СЊ СЌС‚РѕС‚ РјРµС‚РѕРґ РІ Р»СЋР±РѕРµ РјРµСЃС‚Рѕ РІРЅСѓС‚СЂРё РєР»Р°СЃСЃР° AASMapRenderer (РЅР°РїСЂРёРјРµСЂ, РїРµСЂРµРґ СЃР°РјС‹Рј close()):
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

        // НОВЫЕ ПОЛЯ: мировые координаты метки
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
        // Мы уже сохранили screenX и screenY при обнаружении попадания,
        // поэтому просто возвращаем их.
        return new int[]{pendingDeleteHit.screenX, pendingDeleteHit.screenY};
    }

    private MarkerHit checkMarkerListHit(AASWorldData.Squad squad, List<AASWorldData.SquadMarker> list, String listName,
                                         double mouseX, double mouseY, double cx, double cz, double bpp, long time) {
        for (AASWorldData.SquadMarker rm : list) {
            if (time >= rm.expiryTick) continue;

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
     * Новая логика:
     * - SL может удалять ЛЮБЫЕ метки (отрядные и тактические) всей своей команды.
     * - FTL может удалять ЛЮБЫЕ метки (SL, Bravo, Charlie, ромбики) ТОЛЬКО своего отряда.
     */
    private boolean canDeleteMarker(MarkerHit hit) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || hit == null) return false;

        String myName = mc.player.getScoreboardName();

        // 1. Определяем роль игрока и его отряд (ищем строго по нику, чтобы избежать проблем с названиями команд)
        boolean isSL = false;
        boolean isFTL = false;
        AASWorldData.Squad mySquad = null;

        for (AASWorldData.Squad s : ClientData.clientSquads) {
            if (s.leader.equals(myName)) {
                isSL = true;
                mySquad = s;
                break; // Мы нашли свой отряд как SL
            } else if (s.bravoLeader.equals(myName) || s.charlieLeader.equals(myName)) {
                isFTL = true;
                mySquad = s;
                break; // Мы нашли свой отряд как FTL
            }
        }

        // Если игрок не является лидером (ни SL, ни FTL), он не может удалять метки
        if (!isSL && !isFTL) return false;

        // 2. Логика для ТАКТИЧЕСКИХ меток (TEAM, ENEMY, SUPPLY и т.д.)
        if (hit.category == MarkerHit.MarkerCategory.TACTICAL) {
            // Теперь FTL тоже может удалять тактические метки своей команды
            return isSL || isFTL;
        }

        // 3. Логика для меток ОТРЯДА (SQUAD)
        if (hit.squad == null || mySquad == null) return false;

        // Проверяем, принадлежит ли метка нашему отряду
        if (hit.squad.id == mySquad.id) {
            // Если метка нашего отряда, удалять могут и SL, и FTL
            // (это покрывает метки SL, Bravo и Charlie, а также ромбики внутри этого отряда)
            return true;
        } else {
            // Если метка ЧУЖОГО отряда, удалять может ТОЛЬКО SL
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
        MarkerHit hit = findMarkerAt(mouseX, mouseY); // Было: findRhombusMarkerAt
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
     * Текстуру "aas:textures/gui/map_icons/marker_delete.png" нужно добавить отдельно -
     * это простая монохромная иконка (крестик/корзина), она будет окрашена в красный цвет кодом ниже.
     */
    public void renderMarkerDeleteMenu(GuiGraphics gui, int mouseX, int mouseY) {
        if (pendingDeleteHit == null) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // ПЕРЕСЧЁТ ПОЗИЦИИ КАЖДЫЙ КАДР
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

        // СВЕТЛЕЕ ПРИ НАВЕДЕНИИ: было 0xFF6A6A6A, стало 0xFF8A8A8A
        gui.fill(x, y, x + size, y + size, hover ? 0xFF8A8A8A : 0xFF4A4A4A);

        // Иконка удаления, окрашенная в красный (увеличена в 1.5 раза)
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

        // ПЕРЕСЧЁТ ПОЗИЦИИ
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