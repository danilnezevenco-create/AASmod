// PATH: src\main\java\com\example\aas\client\gui\AASMapRenderer.java
package com.example.aas.client.gui;

import com.example.aas.client.ClientData;
import com.example.aas.network.MapPlayerInfo;
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

import java.util.HashMap;
import java.util.Map;

public class AASMapRenderer implements AutoCloseable {

    // ПУТЬ К КАРТИНКЕ В РЕСУРСПАКЕ / В МОДЕ
    private static final ResourceLocation STATIC_MAP_TEXTURE = new ResourceLocation("aas", "textures/gui/map.png");

    private int mapX, mapY, mapSize;
    // Текущий зум (сколько блоков в одном пикселе интерфейса)
    private double getMapScale() { return ClientData.mapScale; }

    private double mapPanX = 0;
    private double mapPanZ = 0;
    private boolean isDraggingMap = false;
    private double lastMouseX = 0;
    private double lastMouseY = 0;

    private static final ResourceLocation MARKER_MOVE = new ResourceLocation("aas", "textures/gui/map_icons/marker_move.png");
    private static final ResourceLocation MARKER_ATTACK = new ResourceLocation("aas", "textures/gui/map_icons/marker_attack.png");
    private static final ResourceLocation MARKER_DEFEND = new ResourceLocation("aas", "textures/gui/map_icons/marker_defend.png");
    private static final ResourceLocation MARKER_BUILD = new ResourceLocation("aas", "textures/gui/map_icons/marker_build.png");
    private static final ResourceLocation FLAG_NEUTRAL = new ResourceLocation("aas", "textures/gui/flags/neutral.png");
    private static final ResourceLocation ICON_CIRCLE = new ResourceLocation("aas", "textures/gui/map_icons/player_circle.png");
    private static final ResourceLocation ICON_PLUS = new ResourceLocation("aas", "textures/gui/map_icons/medic_plus.png");
    private static final ResourceLocation ICON_PLAYER_SELF = new ResourceLocation("aas", "textures/gui/map_icons/player_self.png");
    private static final ResourceLocation MAP_GRID_TEXTURE = new ResourceLocation("aas", "textures/gui/map_grid.png");
    private static final ResourceLocation HUB_ICON = new ResourceLocation("aas", "textures/gui/map_icons/hub_icon.png");
    private static final ResourceLocation RALLY_ICON = new ResourceLocation("aas", "textures/gui/map_icons/rally_icon.png");
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
    }
    private ResourceLocation getMarkerIcon(String type) {
        // Автоматически превращает "Enemy HUB" в "hub_marker.png", а "Tank" в "tank_marker.png"
        String path = type.toLowerCase()
                .replace("enemy ", "") // убираем приписку "enemy", если она есть
                .replace(" ", "_");    // пробелы в нижнее подчеркивание

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
        // Рисуем черную обводку (смещение в 4 стороны)
        gui.drawString(font, text, x - 1, y, 0xFF000000, false);
        gui.drawString(font, text, x + 1, y, 0xFF000000, false);
        gui.drawString(font, text, x, y - 1, 0xFF000000, false);
        gui.drawString(font, text, x, y + 1, 0xFF000000, false);
        // Рисуем основной текст по центру
        gui.drawString(font, text, x, y, color, false);
    }
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer localPlayer = mc.player;
        if (localPlayer == null) return;

        // 1. Координаты камеры (игрок + перетаскивание карты)
        double currentScale = ClientData.mapScale;
        double cx = localPlayer.getX() + mapPanX;
        double cz = localPlayer.getZ() + mapPanZ;

        // 2. Рамка и фон
        gui.fill(mapX - 1, mapY - 1, mapX + mapSize + 1, mapY + mapSize + 1, 0xFFFFFFFF);
        gui.fill(mapX, mapY, mapX + mapSize, mapY + mapSize, 0xFF000000);

        gui.enableScissor(mapX, mapY, mapX + mapSize, mapY + mapSize);

        PoseStack pose = gui.pose();
        pose.pushPose();

        // 3. Масштабирование и центрирование
        pose.translate(mapX + mapSize / 2.0, mapY + mapSize / 2.0, 0);
        float scale = 1.0f / (float) currentScale;
        pose.scale(scale, scale, 1.0f);

        int s = ClientData.mapSizeBlocks;

        // Расчет смещения для обеих текстур одинаковый
        float drawX = (float) (ClientData.mapCenterX - (s / 2.0) - cx);
        float drawY = (float) (ClientData.mapCenterZ - (s / 2.0) - cz);

        // --- Отрисовка КАРТЫ ---
        setFilter(STATIC_MAP_TEXTURE, true); // ВКЛЮЧАЕМ МЯГКИЕ ПИКСЕЛИ
        RenderSystem.setShaderTexture(0, STATIC_MAP_TEXTURE);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        gui.blit(STATIC_MAP_TEXTURE, (int)drawX, (int)drawY, s, s, 0, 0, 1024, 1024, 1024, 1024);
        setFilter(STATIC_MAP_TEXTURE, false);

        // --- Отрисовка СЕТКИ (Grid) ---
        // Используем те же координаты drawX и drawY, чтобы они совпали пиксель в пиксель
        setFilter(MAP_GRID_TEXTURE, true); // ВКЛЮЧАЕМ (чтобы линии не рябили)
        RenderSystem.setShaderTexture(0, MAP_GRID_TEXTURE);
        RenderSystem.enableBlend();
        gui.blit(MAP_GRID_TEXTURE, (int)drawX, (int)drawY, s, s, 0, 0, 1024, 1024, 1024, 1024);
        setFilter(MAP_GRID_TEXTURE, false); // ВЫКЛЮЧАЕМ

        pose.popPose();

        // 4. Отрисовка объектов (флаги, игроки, техника)
        renderOverlays(gui, mc, cx, cz, currentScale);
        renderStructures(gui, mc, cx, cz, currentScale);
        renderVehicles(gui, mc, cx, cz, currentScale);
        renderAllPlayers(gui, mc, localPlayer, cx, cz, currentScale);
        renderSquadMarkerLogic(gui, mc, cx, cz, currentScale);
        renderTacticalMarkers(gui, mc, cx, cz, currentScale);

        gui.disableScissor();
    }

    private void renderOverlays(GuiGraphics gui, Minecraft mc, double cx, double cz, double bpp) {
        PoseStack pose = gui.pose();

        if (ClientData.allCapturePoints != null) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            for (AASWorldData.CapturePoint cp : ClientData.allCapturePoints) {
                Vec3 center = cp.area.getCenter();
                double dx = (center.x - cx) / bpp;
                double dy = (center.z - cz) / bpp;
                int pX = (int) (mapX + (mapSize / 2) + dx);
                int pY = (int) (mapY + (mapSize / 2) + dy);

                if (!isPointOnMap(pX, pY)) continue;

                ResourceLocation flagTex = FLAG_NEUTRAL;
                int tintColor = 0xFFFFFFFF;
                boolean tintNeeded = false;

                if (cp.owner.equalsIgnoreCase("BLUE")) {
                    flagTex = getFlagTexture(ClientData.BLUE_FACTION);
                    if (flagTex == null) { flagTex = FLAG_NEUTRAL; tintColor = 0xFF5555FF; tintNeeded = true; }
                } else if (cp.owner.equalsIgnoreCase("RED")) {
                    flagTex = getFlagTexture(ClientData.RED_FACTION);
                    if (flagTex == null) { flagTex = FLAG_NEUTRAL; tintColor = 0xFFFF5555; tintNeeded = true; }
                }

                if (tintNeeded) {
                    float r = ((tintColor >> 16) & 0xFF) / 255.0f;
                    float g = ((tintColor >> 8) & 0xFF) / 255.0f;
                    float b = (tintColor & 0xFF) / 255.0f;
                    RenderSystem.setShaderColor(r, g, b, 1.0f);
                } else {
                    RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                }

                gui.blit(flagTex, pX - 8, pY - 5, 100, 0, 0, 16, 10, 16, 10);
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

                pose.pushPose();
                pose.translate(pX, pY + 6, 101);
                float textScale = 0.6f;
                pose.scale(textScale, textScale, 1.0f);
                gui.drawCenteredString(mc.font, cp.name, 0, 0, 0xFFFFFFFF);
                pose.popPose();
            }
        }
    }

    private void renderAllPlayers(GuiGraphics gui, Minecraft mc, LocalPlayer self, double cx, double cz, double bpp) {
        String myName = self.getScoreboardName();
        int mySquadId = -1;

        if (ClientData.mapPlayers.containsKey(myName)) {
            mySquadId = ClientData.mapPlayers.get(myName).squadId;
        }

        PoseStack pose = gui.pose();
        long currentTime = System.currentTimeMillis();

        String myKit = self.getPersistentData().getString("AAS_CurrentKit");
        boolean amIMedic = "Medic".equalsIgnoreCase(myKit);

        for (MapPlayerInfo info : ClientData.mapPlayers.values()) {
            // 1. Скрываем иконку игрока, если он в технике
            if (info.inVehicle) continue;

            double dx = (info.x - cx) / bpp;
            double dy = (info.z - cz) / bpp;
            int sx = (int) (mapX + (mapSize / 2) + dx);
            int sy = (int) (mapY + (mapSize / 2) + dy);

            if (!isPointOnMap(sx, sy)) continue;

            // 2. Логика НОКА (Крестик)
            if (info.isDowned) {
                // ТЕПЕРЬ: Проверка amIMedic удалена.
                // Если игрок в ноке и кричал последние 3 секунды — его видят ВСЕ союзники.
                if (currentTime - info.lastShoutTime < 3000) {
                    RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                    gui.blit(ICON_PLUS, sx - 4, sy - 4, 8, 8, 0, 0, 16, 16, 16, 16);
                }
                continue; // Не рисуем обычный кружок для тех, кто в ноке
            }

            // 3. Отрисовка КРУЖКА (для всех кроме самого себя)
            if (!info.name.equals(myName)) {
                float r = 0.2f, g = 0.6f, b = 1.0f; // Дефолт (Синий)

                // Если я спектр - раскрашиваю иконки в реальные цвета команд
                if (mc.player.isSpectator() || mc.player.isCreative()) {
                    if (info.team.equalsIgnoreCase("RED")) {
                        r = 1.0f; g = 0.2f; b = 0.2f; // Красный
                    }
                } else {
                    // Старая логика: если я в отряде - зеленый
                    if (info.squadId != -1 && info.squadId == mySquadId) {
                        r = 0.2f; g = 1.0f; b = 0.2f;
                    }
                }

                pose.pushPose();
                pose.translate(sx, sy, 200);
                RenderSystem.setShaderColor(r, g, b, 1.0f);
                // Центрируем кружок 6x6 относительно точки игрока
                gui.blit(ICON_CIRCLE, -3, -3, 6, 6, 0, 0, 16, 16, 16, 16);
                pose.popPose();
            }

            // 4. Отрисовка ЦИФРЫ (для лидеров)
            if (info.isLeader && info.squadId != -1) {
                String squadNum = String.valueOf(info.squadId);
                int textColor = (info.squadId == mySquadId) ? 0xFF55FF55 : 0xFF5555FF;

                pose.pushPose();
                pose.translate(sx, sy, 350); // Выше кружков
                pose.scale(0.5f, 0.5f, 1.0f);
                int tw = mc.font.width(squadNum);
                drawSquadNumber(gui, mc.font, squadNum, -(tw / 2), -4, textColor);
                pose.popPose();
            }
        }

        // 5. Рендер стрелки ГГ (самого себя)
        renderSelf(gui, self, cx, cz, bpp);
    }

    private void renderSelf(GuiGraphics gui, LocalPlayer self, double cx, double cz, double bpp) {
        MapPlayerInfo selfInfo = ClientData.mapPlayers.get(self.getScoreboardName());
        // Если ГГ в машине — скрываем стрелку
        if (selfInfo != null && selfInfo.inVehicle) return;

        PoseStack pose = gui.pose();
        double myDx = (self.getX() - cx) / bpp;
        double myDy = (self.getZ() - cz) / bpp;
        int mySx = (int) (mapX + (mapSize / 2) + myDx);
        int mySy = (int) (mapY + (mapSize / 2) + myDy);

        if (isPointOnMap(mySx, mySy)) {
            pose.pushPose();
            pose.translate(mySx, mySy, 300);
            pose.mulPose(Axis.ZP.rotationDegrees(self.getYRot() + 180f));
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            gui.blit(ICON_PLAYER_SELF, -5, -5, 10, 10, 0, 0, 16, 16, 16, 16);
            pose.popPose();
        }
    }

    private boolean isPointOnMap(int x, int y) {
        return x >= mapX && x <= mapX + mapSize && y >= mapY && y <= mapY + mapSize;
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
            // Приближение/отдаление (меняем кол-во блоков в 1 пикселе экрана)
            if (delta > 0) ClientData.mapScale /= 1.2; else ClientData.mapScale *= 1.2;
            ClientData.mapScale = Mth.clamp(ClientData.mapScale, 0.5, 25.0);
            return true;
        }
        return false;
    }
    private void renderVehicles(GuiGraphics gui, Minecraft mc, double cx, double cz, double bpp) {
        if (ClientData.clientVehicles == null || ClientData.clientVehicles.isEmpty()) return;

        PoseStack pose = gui.pose();

        // Определение команды игрока
        String myTeam = "NEUTRAL";
        if (mc.player.getTeam() != null) {
            String name = mc.player.getTeam().getName().toUpperCase();
            if (name.contains("BLUE")) myTeam = "BLUE";
            else if (name.contains("RED")) myTeam = "RED";
        }
        boolean isObserver = mc.player.isCreative() || mc.player.isSpectator();

        for (AASWorldData.VehicleRecord record : ClientData.clientVehicles) {
            // Фильтр: видим только свою команду (или админ)
            if (!record.team.equalsIgnoreCase(myTeam) && !isObserver) continue;

            // ВАЖНО: Используем координаты x и z прямо из record (серверные данные)
            // Это позволяет видеть технику, даже если она не прогружена у тебя лично
            double dx = (record.x - cx) / bpp;
            double dy = (record.z - cz) / bpp;

            int screenX = (int) (mapX + (mapSize / 2) + dx);
            int screenY = (int) (mapY + (mapSize / 2) + dy);

            if (!isPointOnMap(screenX, screenY)) continue;

            ResourceLocation icon = VEHICLE_ICONS.getOrDefault(record.type, VEHICLE_ICONS.get("DEFAULT"));

            setFilter(icon, true);

            pose.pushPose();
            pose.translate(screenX, screenY, 150);
            pose.mulPose(Axis.ZP.rotationDegrees(record.yaw + 180f));

            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.enableBlend();

            gui.blit(icon, -6, -6, 12, 12, 0, 0, 16, 16, 16, 16);
            pose.popPose();

            // ВЫКЛЮЧАЕМ
            setFilter(icon, false);
        }
    }
    private void renderStructures(GuiGraphics gui, Minecraft mc, double cx, double cz, double bpp) {
        PoseStack pose = gui.pose();

        // 1. СТРОГОЕ ОПРЕДЕЛЕНИЕ КОМАНДЫ (как в технике)
        String myTeam = "NEUTRAL";
        if (mc.player.getTeam() != null) {
            String name = mc.player.getTeam().getName().toUpperCase();
            if (name.contains("BLUE")) myTeam = "BLUE";
            else if (name.contains("RED")) myTeam = "RED";
        }
        boolean isObserver = mc.player.isCreative() || mc.player.isSpectator();

        // 2. ОТРИСОВКА ХАБОВ (FOB)
        for (AASWorldData.HubInfo hub : ClientData.clientHubs) {
            // Условие: Хаб построен И (принадлежит твоей команде ИЛИ ты админ)
            if (!hub.constructed) continue;
            if (!hub.team.equalsIgnoreCase(myTeam) && !isObserver) continue;

            // Позиция хаба (он всегда стоит на целых координатах блока)
            double dx = (hub.pos.getX() + 0.5 - cx) / bpp;
            double dy = (hub.pos.getZ() + 0.5 - cz) / bpp;
            int sx = (int) (mapX + (mapSize / 2) + dx);
            int sy = (int) (mapY + (mapSize / 2) + dy);

            if (!isPointOnMap(sx, sy)) continue;

            setFilter(HUB_ICON, true); // Сглаживание
            pose.pushPose();
            pose.translate(sx, sy, 160); // Слой выше флагов, но ниже игроков
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            // Рисуем иконку (размер 12x12)
            gui.blit(HUB_ICON, -6, -6, 12, 12, 0, 0, 16, 16, 16, 16);
            pose.popPose();
            setFilter(HUB_ICON, false);
        }

        // 3. ОТРИСОВКА РАЛЛИ-ПОИНТОВ
        for (AASWorldData.Squad squad : ClientData.clientSquads) {
            // Условие: У отряда есть раллик И (это твой отряд/команда ИЛИ ты админ)
            if (squad.rallyPos == null) continue;
            if (!squad.team.equalsIgnoreCase(myTeam) && !isObserver) continue;

            double dx = (squad.rallyPos.getX() + 0.5 - cx) / bpp;
            double dy = (squad.rallyPos.getZ() + 0.5 - cz) / bpp;
            int sx = (int) (mapX + (mapSize / 2) + dx);
            int sy = (int) (mapY + (mapSize / 2) + dy);

            if (!isPointOnMap(sx, sy)) continue;

            setFilter(RALLY_ICON, true); // Сглаживание
            pose.pushPose();
            pose.translate(sx, sy, 170); // Чуть выше хаба
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            // Рисуем иконку (размер 10x10)
            gui.blit(RALLY_ICON, -5, -5, 10, 10, 0, 0, 16, 16, 16, 16);
            pose.popPose();
            setFilter(RALLY_ICON, false);
        }
    }
    private void renderSquadMarkerLogic(GuiGraphics gui, Minecraft mc, double cx, double cz, double bpp) {
        if (mc.player == null) return;

        String myName = mc.player.getScoreboardName();
        AASWorldData.Squad mySquad = null;

        // 1. Ищем отряд, в котором состоит текущий игрок (независимо от того, лидер он или нет)
        for (AASWorldData.Squad s : ClientData.clientSquads) {
            if (s.members.contains(myName)) {
                mySquad = s;
                break;
            }
        }

        // 2. Если отряд найден и у него есть активная метка
        if (mySquad != null && mySquad.marker != null) {
            AASWorldData.SquadMarker m = mySquad.marker;

            // Определяем цвет и иконку в зависимости от типа (0-5)
            int color = 0xFF55FF55; // По умолчанию Зеленый (Move)
            ResourceLocation icon = MARKER_MOVE; // Используем переменную из начала класса

            switch (m.type) {
                case 1: color = 0xFFFFAA00; icon = MARKER_ATTACK; break; // Атака (Оранжевый)
                case 2: color = 0xFF5555FF; icon = MARKER_DEFEND; break; // Защита (Синий)
                case 3: color = 0xFFFF55FF; icon = MARKER_BUILD; break;  // Стройка (Розовый)
                case 4: color = 0xFFFFFFFF; icon = new ResourceLocation("aas", "textures/gui/map_icons/player_circle.png"); break; // Команда (Белый)
                case 5: color = 0xFFFF0000; icon = MARKER_ATTACK; break; // Враг (Красный)
            }

            // Позиция метки на экране (в пикселях карты)
            int mx = (int) (mapX + (mapSize / 2) + (m.x - cx) / bpp);
            int my = (int) (mapY + (mapSize / 2) + (m.z - cz) / bpp);

            // Позиция ИГРОКА на экране (у каждого игрока будет своя линия от его иконки)
            int px = (int) (mapX + (mapSize / 2) + (mc.player.getX() - cx) / bpp);
            int py = (int) (mapY + (mapSize / 2) + (mc.player.getZ() - cz) / bpp);

            // Рисуем линию только если точка назначения на карте или рядом
            drawDashedLine(gui, px, py, mx, my, color);

            // Рисуем иконку метки (размер 12x12)
            if (isPointOnMap(mx, my)) {
                RenderSystem.enableBlend();
                RenderSystem.setShaderColor(1, 1, 1, 1);
                gui.blit(icon, mx - 6, my - 6, 0, 0, 12, 12, 12, 12);

                // 3. РАСЧЕТ ДИСТАНЦИИ (Метры)
                // Считаем честное расстояние от текущего игрока до координат метки
                double distance = Math.sqrt(mc.player.distanceToSqr(m.x, mc.player.getY(), m.z));
                String distText = (int)distance + "m";

                // Отрисовка текста под иконкой
                gui.pose().pushPose();
                gui.pose().translate(mx, my + 8, 600); // Z=600 чтобы было поверх всего
                gui.pose().scale(0.8f, 0.8f, 1.0f);

                // Рисуем с черной обводкой для читаемости
                int textWidth = mc.font.width(distText);
                gui.drawString(mc.font, distText, -(textWidth / 2), 0, color, true);
                gui.pose().popPose();
            }
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
    // В файле AASMapRenderer.java

    private void renderTacticalMarkers(GuiGraphics gui, Minecraft mc, double cx, double cz, double bpp) {
        String myTeam = getPlayerTeamStrict(mc);
        long currentTime = mc.level.getGameTime();

        // Стандартное время жизни метки, которое мы задали в пакете (3600 тиков = 3 минуты)
        float totalLifetime = 3600.0f;

        for (AASWorldData.MapMarker m : ClientData.activeMarkers) {
            if (!m.team.equalsIgnoreCase(myTeam)) continue;

            // Вычисляем прозрачность
            long timeLeft = m.expiryTick - currentTime;

            // Если время вышло, пропускаем (хотя сервер должен их удалять, это для плавности)
            if (timeLeft <= 0) continue;

            // Коэффициент прозрачности: от 1.0 (новая) до 0.0 (исчезающая)
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

            // Включаем смешивание цветов (Blend) и устанавливаем прозрачность
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha); // alpha меняется со временем

            // Рендер иконки
            gui.blit(icon, -8, -8, 16, 16, 0, 0, 32, 32, 32, 32);

            // Сбрасываем цвет обратно в 1.0, чтобы не покрасить другие элементы интерфейса
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

            gui.pose().popPose();
            setFilter(icon, false);
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
    // В файле AASMapRenderer.java измените метод:
    private void handleMapRightClick(double mouseX, double mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Проверка, является ли игрок лидером
        if (!isSquadLeader(mc.player)) {
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("Only Squad Leaders can place markers!").withStyle(net.minecraft.ChatFormatting.RED), true);
            return;
        }

        double bpp = this.getBlocksPerPixel();
        double centerX = this.getCenterX(mc.player);
        double centerZ = this.getCenterZ(mc.player);

        // Расчет мировых координат из позиции мыши на карте
        int targetX = (int) (centerX + ((mouseX - (mapX + mapSize / 2.0)) * bpp));
        int targetZ = (int) (centerZ + ((mouseY - (mapY + mapSize / 2.0)) * bpp));

        // Открываем радиальное меню тактических меток
        mc.setScreen(new TacticalMapRadialScreen(targetX, targetZ));
    }

    // Добавить этот метод в любое место внутри класса AASMapRenderer (например, перед самым close()):
    private boolean isSquadLeader(LocalPlayer player) {
        if (player == null) return false;
        String pName = player.getScoreboardName();
        for (AASWorldData.Squad s : ClientData.clientSquads) {
            if (s.leader.equals(pName)) return true;
        }
        return false;
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

    @Override
    public void close() {}
}