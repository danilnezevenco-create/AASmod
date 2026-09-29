package com.example.aas.client.gui;

import com.example.aas.client.ClientData;
import com.example.aas.network.PacketHandler;
import com.example.aas.network.PacketPlaceMapMarker;
import com.example.aas.network.PacketSquadMarker;
import com.example.aas.sound.ModSounds;
import com.example.aas.world.AASWorldData;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

/**
 * Панель тактических меток — ПЛАВАЮЩИЙ ОВЕРЛЕЙ поверх карты.
 *
 * Это НЕ экран: никакого setScreen, никакого затемнения, никакого renderBackground.
 * Карта, панель отрядов и весь остальной интерфейс остаются видимыми и живыми под панелью.
 *
 * Интеграция у родителя (SquadSelectionScreen / AASDeathScreen / AASMapRenderer):
 *   1) поле:   private final MarkerPanelOverlay markerPanel = new MarkerPanelOverlay();
 *   2) вместо  setScreen(new TacticalMapRadialScreen(x, z))  ->  markerPanel.openAt(x, z, mouseX, mouseY);
 *   3) в конце своего render():                              ->  markerPanel.render(gui, mouseX, mouseY);
 *   4) ПЕРВОЙ строкой mouseClicked():                        ->  if (markerPanel.mouseClicked(mx, my, btn)) return true;
 *   5) ПЕРВОЙ строкой keyPressed():                          ->  if (markerPanel.handleEscapePressed(keyCode)) return true;
 *   6) (по желанию) в начале mouseDragged():                 ->  if (markerPanel.isOpen()) return true;
 *
 * Раскладка:            [8]
 *              [10] [7] [1] [2] [3] [4] [5] [6] [9]
 * Центр кнопки [1] в момент открытия находится ровно под курсором.
 * SL видит все 8 кнопок; у FTL слот [1] (ромб) просто пустой, позиции не сдвигаются.
 *
 * Закрытие: ESC, клик вне панели, установка метки. Клик вне панели ничего не ставит.
 */
public class MarkerPanelOverlay {

    // ============================ СТИЛЬ ============================
    // Повторяет стиль меню удаления метки и контекстных меню мода:
    // фон 0xEE111111, outline 0xFF555555, подсветка при наведении — белая рамка
    // (как у renderContextMenu / SquadButton). Если в renderMarkerDeleteMenu
    // значения отличаются — поправь ТОЛЬКО этот блок, всё собрано здесь.
    private static final int CELL = 20;              // размер квадратной кнопки
    private static final int GAP  = 2;               // зазор между кнопками
    private static final int PAD  = 3;               // отступ фона от кнопок
    private static final int ICON = 16;              // иконка внутри кнопки
    private static final int PANEL_Z = 800;          // выше всех меток на карте (у них максимум 600)

    private static final int COLOR_BG           = 0xEE111111;
    private static final int COLOR_CELL         = 0x99101010;   // почти чёрная, непрозрачность 60%
    private static final int COLOR_CELL_HOVER   = 0xB0383838;   // при наведении чуть светлее и плотнее
    private static final int COLOR_BORDER       = 0xFF555555;
    private static final int COLOR_BORDER_HOVER = 0xFFFFFFFF;

    // ========================== ТЕКСТУРЫ ===========================
    // Иконка ромба В МЕНЮ — новая текстура (путь и размер см. в списке текстур).
    // На карте по-прежнему рисуются squad_rhombus.png / squad_rhombus_cmd.png — их не трогаем.
    private static final ResourceLocation ICON_RHOMBUS_MENU =
            new ResourceLocation("aas", "textures/gui/map_icons/rhombus_menu.png");
    private static final ResourceLocation ICON_ARROW_MENU =
            new ResourceLocation("aas", "textures/gui/map_icons/arrow_menu.png");
    private static final int ARROW_ACTION = 100;   // служебный тип: кнопка «стрелка»
    private static final ResourceLocation ICON_SQUAD_ARROW_MENU =
            new ResourceLocation("aas", "textures/gui/map_icons/arrow_squad_menu.png");
    private static final int SQUAD_ARROW_ACTION = 101;   // кнопка «стрелка отряда»
    // Метки отряда (существующие текстуры)
    private static final ResourceLocation ICON_MOVE   =
            new ResourceLocation("aas", "textures/gui/map_icons/marker_move.png");
    private static final ResourceLocation ICON_ATTACK =
            new ResourceLocation("aas", "textures/gui/map_icons/marker_attack.png");
    private static final ResourceLocation ICON_DEFEND =
            new ResourceLocation("aas", "textures/gui/map_icons/marker_defend.png");
    private static final ResourceLocation ICON_BUILD  =
            new ResourceLocation("aas", "textures/gui/map_icons/marker_build.png");
    // Метки фаертимов: 0=Move, 1=Attack, 2=Defend, 3=Build
    private static final ResourceLocation[] BRAVO_ICONS = {
            new ResourceLocation("aas", "textures/gui/map_icons/marker_move_bravo.png"),
            new ResourceLocation("aas", "textures/gui/map_icons/marker_attack_bravo.png"),
            new ResourceLocation("aas", "textures/gui/map_icons/marker_defend_bravo.png"),
            new ResourceLocation("aas", "textures/gui/map_icons/marker_build_bravo.png")
    };
    private static final ResourceLocation[] CHARLIE_ICONS = {
            new ResourceLocation("aas", "textures/gui/map_icons/marker_move_charlie.png"),
            new ResourceLocation("aas", "textures/gui/map_icons/marker_attack_charlie.png"),
            new ResourceLocation("aas", "textures/gui/map_icons/marker_defend_charlie.png"),
            new ResourceLocation("aas", "textures/gui/map_icons/marker_build_charlie.png")
    };

    // Кэш проверки "есть ли PNG", чтобы отсутствующие файлы не спамили
    // "Failed to load texture" в лог каждый кадр.
    private static final Map<ResourceLocation, Boolean> TEXTURE_EXISTS = new HashMap<>();
    private static final Map<ResourceLocation, int[]> TEXTURE_SIZE = new HashMap<>();
    /**
     * Строит путь к иконке метки по той же схеме, что и AASMapRenderer.getMarkerIcon:
     * "Enemy HUB" -> hub_marker.png, "Machine Gun" -> machine_gun_marker.png,
     * "Anti-Air" -> anti-air_marker.png (дефис сохраняется, заменяются только пробелы).
     */
    private static ResourceLocation markerIcon(String type) {
        String path = type.toLowerCase()
                .replace("enemy ", "")
                .replace(" ", "_");
        return new ResourceLocation("aas", "textures/gui/map_icons/" + path + "_marker.png");
    }

    // ============================ МОДЕЛЬ ============================
    private static class Entry {
        final int slot;               // позиция на панели 1..8; у подпунктов -1
        final String nameKey;         // lang-ключ названия (подсказка при наведении)
        final ResourceLocation icon;
        final String stub;            // короткая подпись цветной заглушки, пока PNG не нарисован
        final int stubColor;
        final String mapType;         // != null -> PacketPlaceMapMarker(x, z, mapType)
        final int squadType;          // >= 0    -> PacketSquadMarker(x, z, squadType)
        final Entry[] sub;            // подменю или null

        Entry(int slot, String nameKey, ResourceLocation icon, String stub, int stubColor,
              String mapType, int squadType, Entry... sub) {
            this.slot = slot;
            this.nameKey = nameKey;
            this.icon = icon;
            this.stub = stub;
            this.stubColor = stubColor;
            this.mapType = mapType;
            this.squadType = squadType;
            this.sub = (sub == null || sub.length == 0) ? null : sub;
        }
    }

    // Цвета заглушек по категориям (используются, только пока PNG не нарисованы)
    private static final int C_INF = 0xFF55FF55;   // пехота
    private static final int C_VEH = 0xFF6FB7E8;   // техника
    private static final int C_AIR = 0xFFCC77FF;   // авиация
    private static final int C_STR = 0xFFFF6B55;   // строения
    private static final int C_SQD = 0xFFFFD755;   // метки отряда / ромб
    private static final int C_SUP = 0xFF3399FF;   // снабжение

    private static Entry mapMarker(int slot, String key, String type, int color, String stub, Entry... sub) {
        return new Entry(slot, key, markerIcon(type), stub, color, type, -1, sub);
    }

    private static Entry mapSub(String key, String type, int color, String stub) {
        return new Entry(-1, key, markerIcon(type), stub, color, type, -1);
    }

    private static Entry squadMarker(int slot, String key, ResourceLocation icon, int type, int color, String stub, Entry... sub) {
        return new Entry(slot, key, icon, stub, color, null, type, sub);
    }

    private static Entry squadSub(String key, ResourceLocation icon, int type, int color, String stub) {
        return new Entry(-1, key, icon, stub, color, null, type);
    }

    /**
     * Вся раскладка. Поле slot задаёт позицию:
     *   [7] [1] [2] [3] [4] [5] [6] — один ряд, [8] — над [1].
     * Строковые типы — ровно те, что уже используются в activeMarkers
     * (рендер на карте, PacketDeleteMarker и hit-test продолжат работать).
     */
    private static final Entry[] PANEL = {
            // [7] МЕТКИ ОТРЯДА — PacketSquadMarker, по умолчанию Move (0)
            squadMarker(7, "aas.gui.map_marker.squad_move", ICON_MOVE, 0, C_SQD, "MV",
                    squadSub("aas.gui.map_marker.squad_attack", ICON_ATTACK, 1, C_SQD, "AK"),
                    squadSub("aas.gui.map_marker.squad_defend", ICON_DEFEND, 2, C_SQD, "DF"),
                    squadSub("aas.gui.map_marker.squad_build",  ICON_BUILD,  3, C_SQD, "BD")),
            // [1] РОМБ — PacketSquadMarker type 6, без подменю, только для SL
            squadMarker(1, "aas.gui.map_marker.rhombus", ICON_RHOMBUS_MENU, 6, C_SQD, "RH"),
            // [2] ПЕХОТА — по умолчанию Infantry
            mapMarker(2, "aas.gui.map_marker.infantry", "Infantry", C_INF, "IN",
                    mapSub("aas.gui.map_marker.sniper",      "Sniper",      C_INF, "SN"),
                    mapSub("aas.gui.map_marker.hat",         "HAT",         C_INF, "HT"),
                    mapSub("aas.gui.map_marker.machine_gun", "Machine Gun", C_INF, "MG"),
                    mapSub("aas.gui.map_marker.anti_air",    "Anti-Air",    C_INF, "AA")),
            // [3] ЛЁГКАЯ ТЕХНИКА — по умолчанию Combat Vehicle
            mapMarker(3, "aas.gui.map_marker.light_vehicle", "Combat Vehicle", C_VEH, "CV",
                    mapSub("aas.gui.map_marker.supply_truck",    "Supply Truck",    C_VEH, "ST"),
                    mapSub("aas.gui.map_marker.infantry_vehicle","Infantry Vehicle",C_VEH, "IV"),
                    mapSub("aas.gui.map_marker.atgm_carrier",    "ATGM Carrier",    C_VEH, "AT"),
                    mapSub("aas.gui.map_marker.motorcycle",      "Motorcycle",      C_VEH, "MC"),
                    mapSub("aas.gui.map_marker.boat",            "Boat",            C_VEH, "BT")),
            // [4] ТЯЖЁЛАЯ ТЕХНИКА — по умолчанию APC
            mapMarker(4, "aas.gui.map_marker.heavy_vehicle", "APC", C_VEH, "AP",
                    mapSub("aas.gui.map_marker.tank",         "Tank",         C_VEH, "TK"),
                    mapSub("aas.gui.map_marker.heavy_supply", "Heavy Supply", C_VEH, "HS"),
                    mapSub("aas.gui.map_marker.mobile_zu",    "Mobile ZU",    C_VEH, "ZU"),
                    mapSub("aas.gui.map_marker.spg",          "SPG",          C_VEH, "SP")),
            // [5] АВИАЦИЯ — по умолчанию Helicopter
            mapMarker(5, "aas.gui.map_marker.aircraft", "Helicopter", C_AIR, "HL",
                    mapSub("aas.gui.map_marker.cas_heli",    "CAS Heli",    C_AIR, "CH"),
                    mapSub("aas.gui.map_marker.cas_fighter", "CAS Fighter", C_AIR, "CF")),
            // [6] СТРОЕНИЯ — по умолчанию Enemy HUB
            mapMarker(6, "aas.gui.map_marker.structures", "Enemy HUB", C_STR, "HB",
                    mapSub("aas.gui.map_marker.enemy_rally", "Enemy Rally", C_STR, "RL"),
                    mapSub("aas.gui.map_marker.mortar",      "Mortar",      C_STR, "MR"),
                    mapSub("aas.gui.map_marker.tow",         "TOW",         C_STR, "TW")),
            // [8] МЕТКИ КОМАНДЫ — Supply Request, без подменю
            mapMarker(8, "aas.gui.map_marker.supply_request", "Supply Request", C_SUP, "SR"),
            // [9] СТРЕЛКА — только SL
            squadMarker(9, "aas.gui.map_marker.arrow", ICON_ARROW_MENU, ARROW_ACTION, C_STR, "AR"),
            // [10] СТРЕЛКА ОТРЯДА — только SL, слева от [7]
            squadMarker(10, "aas.gui.map_marker.arrow_squad", ICON_SQUAD_ARROW_MENU,
                    SQUAD_ARROW_ACTION, 0xFF33DD33, "AS")
    };

    // =========================== СОСТОЯНИЕ ==========================
    private boolean open = false;
    private boolean isSL = false;
    private int variant = 0;
    private int targetX, targetZ;
    private int panelX, panelY, panelW, panelH;
    private int hoverSlot = -1;      // наведённая главная кнопка
    private int openSubSlot = -1;    // кнопка, у которой раскрыто подменю
    private int hoverItem = -1;      // наведённый пункт подменю

    private AASMapRenderer map;      // карта, за которой едет панель (может быть null)
    private int offX, offY;          // смещение панели от точки клика на экране
    private boolean arrowPending = false;
    private boolean arrowSquad = false;      // NEW: true = стрелка отряда (зелёная)
    private int arrowStartX, arrowStartZ;

    public boolean isOpen() {
        return open;
    }

    public void close() {
        open = false;
        openSubSlot = -1;
        hoverSlot = -1;
        hoverItem = -1;
    }

    /**
     * Для keyPressed родителя: глотает ESC, пока панель открыта.
     * Использование: if (markerPanel.handleEscapePressed(keyCode)) return true;
     */
    public boolean handleEscapePressed(int keyCode) {
        if (keyCode != 256) return false;      // 256 = GLFW_KEY_ESCAPE
        if (open) { close(); return true; }
        if (arrowPending) { arrowPending = false; return true; }
        return false;
    }

    // =========================== ОТКРЫТИЕ ===========================

    /**
     * Открыть панель у курсора: центр кнопки [1] оказывается ровно под курсором.
     * Если панель вместе с самым длинным подменю не влезает — сдвигается внутрь экрана.
     * Вызывается ВМЕСТО setScreen(new TacticalMapRadialScreen(...)).
     */
    /** Старый вариант (панель не едет за картой). Оставлен для совместимости. */
    public void openAt(int worldX, int worldZ, double mouseX, double mouseY) {
        openAt(worldX, worldZ, mouseX, mouseY, null);
    }

    /**
     * Открыть панель у курсора: центр кнопки [1] оказывается ровно под курсором.
     * Если передан mapRenderer — панель привязывается к точке на карте и едет за ней.
     */
    public void openAt(int worldX, int worldZ, double mouseX, double mouseY, AASMapRenderer mapRenderer) {
        String role = getClientRole();
        if ("NONE".equals(role)) return;

        this.isSL = "SL".equals(role);
        this.variant = getClientVariant();
        this.targetX = worldX;
        this.targetZ = worldZ;
        this.map = mapRenderer;

        Minecraft mc = Minecraft.getInstance();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        panelW = PAD * 2 + 9 * CELL + 8 * GAP;
        panelH = PAD * 2 + 2 * CELL + GAP;

        int maxSub = 0;
        for (Entry e : PANEL) {
            if (e.sub != null) maxSub = Math.max(maxSub, e.sub.length);
        }
        int longestSubH = PAD * 2 + maxSub * CELL + (maxSub - 1) * GAP;
        int totalH = panelH + GAP + longestSubH;

        int wantX = (int) mouseX - (PAD + 2 * (CELL + GAP) + CELL / 2);
        int wantY = (int) mouseY - (PAD + (CELL + GAP) + CELL / 2);

        panelX = Mth.clamp(wantX, 0, Math.max(0, screenW - panelW));
        int yMax = screenH - totalH;
        if (yMax < 0) yMax = Math.max(0, screenH - panelH);
        panelY = Mth.clamp(wantY, 0, yMax);

        // Запоминаем, на сколько панель сдвинута от точки клика, чтобы потом
        // держать это смещение, пока карта двигается
        offX = panelX - (int) mouseX;
        offY = panelY - (int) mouseY;

        open = true;
        openSubSlot = -1;
        hoverSlot = -1;
        hoverItem = -1;
    }

    /**
     * Пересчитывает позицию панели по текущему положению карты.
     * Возвращает false, если точка клика ушла за пределы карты (тогда панель закрывается).
     */
    private boolean followMap() {
        if (map == null) return true;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;

        double bpp = map.getBlocksPerPixel();
        double cx = map.getCenterX(mc.player);
        double cz = map.getCenterZ(mc.player);
        int ax = (int) (map.getMapX() + map.getMapSize() / 2.0 + (targetX - cx) / bpp);
        int ay = (int) (map.getMapY() + map.getMapSize() / 2.0 + (targetZ - cz) / bpp);

        if (ax < map.getMapX() || ax > map.getMapX() + map.getMapSize()
                || ay < map.getMapY() || ay > map.getMapY() + map.getMapSize()) {
            return false;
        }
        panelX = ax + offX;
        panelY = ay + offY;
        return true;
    }

    /** Курсор над основной панелью или над открытым подменю? */
    private boolean onPanelArea(int mx, int my) {
        if (mx >= panelX && mx < panelX + panelW && my >= panelY && my < panelY + panelH) return true;
        if (openSubSlot >= 0) return inSubArea(bySlot(openSubSlot), mx, my);
        return false;
    }

    // ============================ ВВОД ==============================

    /**
     * Отдавать ПЕРЕД собственными обработчиками родителя.
     * Возвращает true, если клик поглощён (в т.ч. клик вне панели — он её закрывает).
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Ждём конец стрелки
        if (arrowPending) {
            if (button == 1 && map != null && map.isMouseOver(mouseX, mouseY)) {
                finishArrow(mouseX, mouseY);
                return true;
            }
            if (button == 1) {          // ПКМ мимо карты — отмена
                arrowPending = false;
                return false;
            }
        }
        if (!open) return false;
        if (!followMap()) {
            close();
            return false;
        }
        int mx = (int) mouseX, my = (int) mouseY;
        boolean onPanel = onPanelArea(mx, my);

        if (button != 0) {
            // ПКМ/СКМ: просто закрываем панель, независимо от того, где был клик —
            // по панели или мимо неё. Больше НЕ открываем новую панель в точке клика.
            close();
            return true; // <-- было: return onPanel;
        }

        if (button != 0) {
            // ПКМ/СКМ: закрываем панель. Если ПКМ был по карте (не по панели),
            // возвращаем false, чтобы родитель сразу открыл новую панель на новом месте.
            close();
            return onPanel;
        }

        // 1) пункты подменю
        if (openSubSlot >= 0) {
            Entry parent = bySlot(openSubSlot);
            int idx = subItemAt(parent, mx, my);
            if (idx >= 0) {
                place(parent.sub[idx]);
                return true;
            }
            if (inSubArea(parent, mx, my)) return true;
        }

        // 2) главные кнопки: клик ставит метку по умолчанию
        for (Entry e : PANEL) {
            if ((e.slot == 1 || e.slot == 9 || e.slot == 10) && !isSL) continue;
            if (inCell(e, mx, my)) {
                place(e);
                return true;
            }
        }

        // 3) зазоры внутри панели ничего не делают
        if (onPanel) return true;

        // 4) ЛКМ по карте вне панели: отдаём клик карте (перетаскивание), панель остаётся
        if (map != null && map.isMouseOver(mouseX, mouseY)) return false;

        // 5) ЛКМ в любом другом месте (боковая панель и т.п.): закрываем
        close();
        return true;
    }

    // =========================== ОТРИСОВКА ==========================

    /** Рисовать в самом КОНЦЕ render() родителя, поверх всего. */
    public void render(GuiGraphics gui, int mouseX, int mouseY) {
        if (arrowPending) renderArrowPreview(gui, mouseX, mouseY);
        if (!open) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !followMap()) {
            close();
            return;
        }
        Font font = mc.font;

        updateHover(mouseX, mouseY);
        RenderSystem.enableBlend();

        gui.pose().pushPose();
        gui.pose().translate(0, 0, PANEL_Z);   // поднимаем всю панель выше меток

        // --- основные кнопки (общей подложки нет) ---
        for (Entry e : PANEL) {
            if ((e.slot == 1 || e.slot == 9 || e.slot == 10) && !isSL) continue;
            int x = cellX(e.slot), y = cellY(e.slot);
            boolean hovered = (e.slot == hoverSlot) || (e.slot == openSubSlot);
            gui.fill(x, y, x + CELL, y + CELL, hovered ? COLOR_CELL_HOVER : COLOR_CELL);
            gui.renderOutline(x, y, CELL, CELL, hovered ? COLOR_BORDER_HOVER : COLOR_BORDER);
            drawIcon(gui, font, e, x + (CELL - ICON) / 2, y + (CELL - ICON) / 2);
            if (e.sub != null) drawSubBadge(gui, x, y);
        }

        // --- подменю (подложки нет) ---
        if (openSubSlot >= 0) {
            Entry parent = bySlot(openSubSlot);
            int sx = subX(parent), sy = subY(parent);
            for (int i = 0; i < parent.sub.length; i++) {
                Entry it = parent.sub[i];
                int ix = sx + PAD;
                int iy = sy + PAD + i * (CELL + GAP);
                boolean hovered = (i == hoverItem);
                gui.fill(ix, iy, ix + CELL, iy + CELL, hovered ? COLOR_CELL_HOVER : COLOR_CELL);
                gui.renderOutline(ix, iy, CELL, CELL, hovered ? COLOR_BORDER_HOVER : COLOR_BORDER);
                drawIcon(gui, font, it, ix + (CELL - ICON) / 2, iy + (CELL - ICON) / 2);
            }
        }

        Component tip = tooltip();
        if (tip != null) gui.renderTooltip(font, tip, mouseX, mouseY);

        gui.pose().popPose();   // возвращаем высоту обратно
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    // ======================= СЛУЖЕБНАЯ ГЕОМЕТРИЯ =====================

    private static int colOf(int slot) {
        switch (slot) {
            case 10: return 0;
            case 7:  return 1;
            case 1:  return 2;
            case 2:  return 3;
            case 3:  return 4;
            case 4:  return 5;
            case 5:  return 6;
            case 6:  return 7;
            case 9:  return 8;
            default: return 2;   // слот [8] — над [1]
        }
    }

    private int cellX(int slot) {
        return panelX + PAD + colOf(slot) * (CELL + GAP);
    }

    private int cellY(int slot) {
        return slot == 8 ? panelY + PAD : panelY + PAD + CELL + GAP;
    }

    private boolean inCell(Entry e, int mx, int my) {
        int x = cellX(e.slot), y = cellY(e.slot);
        return mx >= x && mx < x + CELL && my >= y && my < y + CELL;
    }

    private int subX(Entry parent) { return cellX(parent.slot) - PAD; }
    private int subY(Entry parent) { return cellY(parent.slot) + CELL + GAP; }
    private int subW()             { return CELL + PAD * 2; }
    private int subH(Entry parent) {
        int n = parent.sub.length;
        return PAD * 2 + n * CELL + (n - 1) * GAP;
    }

    /**
     * Хит-зона подменю БЕЗ мёртвой зоны: начинается прямо от нижнего края
     * главной кнопки (зазор GAP и нижняя кромка фона входят в зону).
     */
    private boolean inSubArea(Entry parent, int mx, int my) {
        int x = subX(parent);
        int top = cellY(parent.slot) + CELL;          // нижний край кнопки
        int bottom = subY(parent) + subH(parent);
        return mx >= x && mx < x + subW() && my >= top && my < bottom;
    }

    private int subItemAt(Entry parent, int mx, int my) {
        int x = subX(parent) + PAD;
        for (int i = 0; i < parent.sub.length; i++) {
            int y = subY(parent) + PAD + i * (CELL + GAP);
            if (mx >= x && mx < x + CELL && my >= y && my < y + CELL) return i;
        }
        return -1;
    }

    // =========================== HOVER-ЛОГИКА ========================

    /**
     * Подменю раскрывается при наведении на свою кнопку и закрывается,
     * когда курсор ушёл И с кнопки, И с подменю.
     */
    private void updateHover(int mx, int my) {
        hoverSlot = -1;
        hoverItem = -1;

        for (Entry e : PANEL) {
            if ((e.slot == 1 || e.slot == 9 || e.slot == 10) && !isSL) continue;
            if (inCell(e, mx, my)) {
                hoverSlot = e.slot;
                break;
            }
        }

        if (openSubSlot >= 0) {
            Entry parent = bySlot(openSubSlot);
            if (hoverSlot == openSubSlot) {
                // курсор всё ещё на «своей» кнопке — подменю остаётся
            } else if (inSubArea(parent, mx, my)) {
                hoverItem = subItemAt(parent, mx, my);
            } else {
                openSubSlot = -1;   // ушёл и с кнопки, и с подменю
            }
        }

        if (hoverSlot >= 0) {
            Entry hovered = bySlot(hoverSlot);
            if (hovered.sub != null) {
                if (openSubSlot != hoverSlot) hoverItem = -1;
                openSubSlot = hoverSlot;
            }
        }
    }

    private Component tooltip() {
        if (openSubSlot >= 0 && hoverItem >= 0) {
            return Component.translatable(bySlot(openSubSlot).sub[hoverItem].nameKey);
        }
        if (hoverSlot >= 0) {
            return Component.translatable(bySlot(hoverSlot).nameKey);
        }
        return null;
    }

    // =========================== ДЕЙСТВИЯ ============================

    private void place(Entry e) {
        if (e.squadType == ARROW_ACTION || e.squadType == SQUAD_ARROW_ACTION) {
            arrowStartX = targetX;
            arrowStartZ = targetZ;
            arrowSquad = (e.squadType == SQUAD_ARROW_ACTION);
            arrowPending = true;
            close();
            return;
        }
        if (e.mapType != null) {
            // Кнопки 2–6 и 8: те же строковые ключи, что уже лежат в activeMarkers
            PacketHandler.INSTANCE.sendToServer(new PacketPlaceMapMarker(targetX, targetZ, e.mapType));
        } else {
            // Ромб (6) и метки отряда (0..3): существующие числовые типы
            PacketHandler.INSTANCE.sendToServer(new PacketSquadMarker(targetX, targetZ, e.squadType));
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.playSound(ModSounds.MAP_MARKER_PLACE.get(), 1.0f, 1.0f);
        }
        close();    // после установки игрок остаётся на том же экране
    }

    // ============================ ИКОНКИ =============================

    private void drawIcon(GuiGraphics gui, Font font, Entry e, int x, int y) {
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.enableBlend();
        ResourceLocation icon = iconFor(e);
        if (textureExists(icon)) {
            int[] size = textureSize(icon);
            int tw = size[0];
            int th = size[1];
            gui.pose().pushPose();
            gui.pose().translate(x, y, 0);
            gui.pose().scale(ICON / (float) tw, ICON / (float) th, 1.0f);
            gui.blit(icon, 0, 0, 0, 0, tw, th, tw, th);
            gui.pose().popPose();
        } else {
            gui.fill(x, y, x + ICON, y + ICON, (e.stubColor & 0x00FFFFFF) | 0x44000000);
            gui.renderOutline(x, y, ICON, ICON, (e.stubColor & 0x00FFFFFF) | 0x99000000);
            gui.drawCenteredString(font, e.stub, x + ICON / 2, y + (ICON - font.lineHeight) / 2, e.stubColor);
        }
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    /** Маленький треугольник в углу кнопки — признак наличия подменю. */
    private void drawSubBadge(GuiGraphics gui, int x, int y) {
        int bx = x + CELL - 7, by = y + CELL - 7;
        gui.fill(bx, by, bx + 5, by + 1, 0xFF999999);
        gui.fill(bx + 1, by + 1, bx + 4, by + 2, 0xFF999999);
        gui.fill(bx + 2, by + 2, bx + 3, by + 3, 0xFF999999);
    }

    /** Есть ли PNG в ресурсах мода (проверяем файл напрямую, а не через TextureManager). */
    private static boolean textureExists(ResourceLocation tex) {
        Boolean cached = TEXTURE_EXISTS.get(tex);
        if (cached != null) return cached;
        boolean exists = Minecraft.getInstance().getResourceManager().getResource(tex).isPresent();
        TEXTURE_EXISTS.put(tex, exists);
        return exists;
    }

    /** Реальный размер PNG в пикселях (читаем один раз и запоминаем). */
    private static int[] textureSize(ResourceLocation tex) {
        int[] cached = TEXTURE_SIZE.get(tex);
        if (cached != null) return cached;
        int[] size = new int[]{16, 16};
        try (java.io.InputStream in = Minecraft.getInstance().getResourceManager().open(tex);
             NativeImage img = NativeImage.read(in)) {
            size[0] = img.getWidth();
            size[1] = img.getHeight();
        } catch (Exception ignored) {
            // оставляем 16x16
        }
        TEXTURE_SIZE.put(tex, size);
        return size;
    }

    // ============================= РОЛИ ==============================

    /**
     * Клиентское определение роли — точное зеркало серверного getPlayerRole(...)
     * из PacketPlaceMapMarker: "SL" / "FTL" / "NONE" (данные отрядов в ClientData).
     */
    private static String getClientRole() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return "NONE";
        String name = mc.player.getScoreboardName();
        for (AASWorldData.Squad s : ClientData.clientSquads) {
            if (s.members.contains(name)) {
                if (s.leader.equals(name)) return "SL";
                if (s.bravoLeader.equals(name) || s.charlieLeader.equals(name)) return "FTL";
                return "NONE";
            }
        }
        return "NONE";
    }
    /** Какой набор иконок использовать: 0 = SL, 1 = Bravo FTL, 2 = Charlie FTL. */
    private static int getClientVariant() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        String name = mc.player.getScoreboardName();
        for (AASWorldData.Squad s : ClientData.clientSquads) {
            if (s.members.contains(name)) {
                if (s.bravoLeader.equals(name)) return 1;
                if (s.charlieLeader.equals(name)) return 2;
                return 0;
            }
        }
        return 0;
    }

    /** Иконка кнопки с учётом фаертима (только для меток движения/атаки/обороны/строить). */
    private ResourceLocation iconFor(Entry e) {
        if (e.mapType == null && e.squadType >= 0 && e.squadType <= 3 && variant != 0) {
            return (variant == 1 ? BRAVO_ICONS : CHARLIE_ICONS)[e.squadType];
        }
        return e.icon;
    }
    // ============================ СТРЕЛКА ============================

    /** Второй ПКМ по карте: ставим конец стрелки. */
    private void finishArrow(double mouseX, double mouseY) {
        Minecraft mc = Minecraft.getInstance();
        arrowPending = false;
        if (mc.player == null || map == null) return;
        int hx = (int) (map.getCenterX(mc.player)
                + (mouseX - (map.getMapX() + map.getMapSize() / 2.0)) * map.getBlocksPerPixel());
        int hz = (int) (map.getCenterZ(mc.player)
                + (mouseY - (map.getMapY() + map.getMapSize() / 2.0)) * map.getBlocksPerPixel());
        if (hx == arrowStartX && hz == arrowStartZ) return;
        if (arrowSquad) {
            // стрелка отряда: SquadMarker type 7, видят только участники отряда
            PacketHandler.INSTANCE.sendToServer(
                    new PacketSquadMarker(arrowStartX, arrowStartZ, 7, hx, hz));
        } else {
            PacketHandler.INSTANCE.sendToServer(
                    new PacketPlaceMapMarker(arrowStartX, arrowStartZ, "Arrow:" + hx + ":" + hz));
        }
        mc.player.playSound(ModSounds.MAP_MARKER_PLACE.get(), 1.0f, 1.0f);
    }

    /** Пока ждём конец: точка начала + стрелка за курсором + подсказка. */
    private void renderArrowPreview(GuiGraphics gui, int mouseX, int mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || map == null) { arrowPending = false; return; }
        double bpp = map.getBlocksPerPixel();
        int sx = (int) (map.getMapX() + map.getMapSize() / 2.0 + (arrowStartX - map.getCenterX(mc.player)) / bpp);
        int sy = (int) (map.getMapY() + map.getMapSize() / 2.0 + (arrowStartZ - map.getCenterZ(mc.player)) / bpp);

        // Цвет зависит от типа стрелки: отряд = зелёная, командная = красная
        int dotColor = arrowSquad ? 0xFF33DD33 : 0xFFE02020;   // ARGB для gui.fill
        int arrowRgb = arrowSquad ? 0x33DD33   : 0xE02020;     // RGB для drawArrow

        gui.pose().pushPose();
        gui.pose().translate(0, 0, 800);
        gui.enableScissor(map.getMapX(), map.getMapY(),
                map.getMapX() + map.getMapSize(), map.getMapY() + map.getMapSize());
        gui.fill(sx - 3, sy - 3, sx + 3, sy + 3, 0xFF000000);
        gui.fill(sx - 2, sy - 2, sx + 2, sy + 2, dotColor);
        if (map.isMouseOver(mouseX, mouseY)) map.drawArrow(gui, sx, sy, mouseX, mouseY, 0.7f, arrowRgb);
        gui.disableScissor();
        gui.drawString(mc.font, Component.translatable("aas.gui.map_marker.arrow_hint"),
                mouseX + 10, mouseY + 12, 0xFFFFFFFF, true);
        gui.pose().popPose();
    }
    private static Entry bySlot(int slot) {
        for (Entry e : PANEL) {
            if (e.slot == slot) return e;
        }
        return null;
    }
}
