package com.example.aas.client.gui;

import com.example.aas.client.ClientData;
import com.example.aas.network.PacketHandler;
import com.example.aas.network.PacketRequestVehicleList;
import com.example.aas.network.PacketSyncVehicleList;
import com.example.aas.util.VehicleCatalog;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Vehicle list overlay: a toggle button (top right, left of the Quit button) and a two-column panel
 * (allied / enemy vehicles) drawn ON TOP of the current screen. It never opens or replaces a screen.
 *
 * Hook-up (identical for SquadSelectionScreen and AASDeathScreen, same style as MarkerPanelOverlay):
 *   1) field:                 private final VehicleListPanel vehiclePanel = new VehicleListPanel();
 *   2) end of render():       vehiclePanel.render(gui, mouseX, mouseY);
 *   3) start of mouseClicked: if (vehiclePanel.mouseClicked(mx, my, btn)) return true;
 *   4) start of mouseScrolled:if (vehiclePanel.mouseScrolled(mx, my, delta)) return true;
 *   5) start of keyPressed:   if (vehiclePanel.handleEscapePressed(keyCode)) return true;
 * Layout is computed from the current GUI-scaled window size on every call, so no init()/resize hooks are needed.
 */
public class VehicleListPanel {

    private static final Logger LOGGER = LogUtils.getLogger();

    // ------------------------------------------------------------------ textures (paths in ONE place)
    /** Button icon. Drawn as a placeholder until the file exists. */
    public static final ResourceLocation BUTTON_ICON = new ResourceLocation("aas", "textures/gui/vehicle_list_icon.png");
    /** Clock icon (respawn timer column header): assets/aas/textures/gui/icon_clock.png */
    public static final ResourceLocation ICON_CLOCK = new ResourceLocation("aas", "textures/gui/icon_clock.png");
    /** Tickets icon (same texture as the minimap tickets): assets/aas/textures/gui/minimap_tickets.png */
    public static final ResourceLocation ICON_TICKETS = new ResourceLocation("aas", "textures/gui/minimap_tickets.png");
    /** Vehicle icons are the same files the map uses: textures/gui/map_icons/<file>.png */
    public static final String MAP_ICON_DIR = "textures/gui/map_icons/";

    // marker type (lower case, as sent by the server) -> file name in map_icons/
    private static final Map<String, String> MAP_ICON_FILES = new HashMap<>();
    static {
        MAP_ICON_FILES.put("apc", "apc");
        MAP_ICON_FILES.put("tank", "tank");
        MAP_ICON_FILES.put("helicopter", "helicopter");
        MAP_ICON_FILES.put("cas helicopter", "cas_helicopter");
        MAP_ICON_FILES.put("cas fighter", "cas_fighter");
        MAP_ICON_FILES.put("combat vehicle", "combat_vehicle");
        MAP_ICON_FILES.put("infantry vehicle", "infantry_vehicle");
        MAP_ICON_FILES.put("supply truck", "supply_truck");
        MAP_ICON_FILES.put("supply helicopter", "supply_helicopter");
        MAP_ICON_FILES.put("static zu", "static_zu");
        MAP_ICON_FILES.put("mobile zu", "mobile_zu");
        MAP_ICON_FILES.put("boat", "boat");
        MAP_ICON_FILES.put("motorcycle", "motorcycle");
        MAP_ICON_FILES.put("light supply", "light_supply");
        MAP_ICON_FILES.put("atgm carrier", "atgm_carrier");
        MAP_ICON_FILES.put("heavy supply", "heavy_supply");
        MAP_ICON_FILES.put("spg", "spg");
    }

    // ------------------------------------------------------------------ layout constants
    private static final int BTN_SIZE = 20;
    private static final int BTN_Y = 5;                 // same Y as the Quit button
    private static final int QUIT_LEFT_OFFSET = 45;     // Quit button: x = screenWidth - 45 (AASDeathScreen)
    private static final int BTN_GAP = 35;              // 30-40 px gap between our button and Quit
    private static final int PANEL_TOP_GAP = 5;
    private static final double PANEL_WIDTH_FRACTION = 0.43;   // ~40-45% of the screen width
    private static final double PANEL_MAX_HEIGHT_FRACTION = 0.70;
    private static final int PANEL_MIN_WIDTH = 300;

    private static final int TITLE_H = 14;
    private static final int COLHDR_H = 14;
    private static final int ROW_H = 14;
    private static final int PAD_BOTTOM = 4;
    private static final int ICON = 12;                 // vehicle icon size on screen
    private static final int HDR_ICON = 10;             // header icon size on screen
    private static final int SCROLL_W = 3;
    private static final int COL_COUNT_FULL = 30;       // "1/3"
    private static final int COL_COUNT_SIMPLE = 22;     // "3"
    private static final int COL_TIMER = 36;            // "5:00" / READY
    private static final int COL_TICKETS = 26;          // "-40"

    private static final int Z = 650;                   // above widgets (MarkerPanelOverlay uses 800 for its own popups)
    private static final long REQUEST_INTERVAL_MS = 1000L;

    /** true: a click outside the panel closes it and is NOT passed on (avoids accidental map clicks). */
    private static final boolean CONSUME_OUTSIDE_CLICK = true;

    // ------------------------------------------------------------------ colours (same palette as MarkerPanelOverlay)
    private static final int COLOR_BG = 0xEE111111;
    private static final int COLOR_BORDER = 0xFF555555;
    private static final int COLOR_TITLE_BG = 0x40FFFFFF;
    private static final int COLOR_STRIPE = 0x14FFFFFF;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    private static final int COLOR_DIM = 0xFF999999;
    private static final int COLOR_READY = 0xFF55FF55;
    private static final int COLOR_WARN = 0xFFFFCC55;
    private static final int COLOR_BAD = 0xFFFF5555;
    private static final int COLOR_TICKETS = 0xFFFF8888;

    // ------------------------------------------------------------------ state
    private boolean open = false;
    private long lastRequestMs = 0L;
    private int scrollAlly = 0;
    private int scrollEnemy = 0;

    // computed layout (see updateLayout)
    private int sw, sh, btnX, panelX, panelY, panelW, panelH, leftW, splitX, rightX, rightW, viewTop, viewH;
    private boolean noData;

    // cached rows
    private PacketSyncVehicleList cachedSource = null;
    private List<Row> allyRows = new ArrayList<>();
    private List<Row> enemyRows = new ArrayList<>();

    private Component pendingTooltip = null;

    private static final class Row {
        PacketSyncVehicleList.Entry e;
        VehicleCatalog.Category cat;
        Component name;
        String sortName;
        ResourceLocation icon;
    }

    // ================================================================== public API

    public boolean isOpen() { return open; }

    public void close() { open = false; }

    public void toggle() {
        open = !open;
        if (open) requestData();
    }

    /** Call from keyPressed(): closes only the panel (not the whole screen) on ESC. */
    public boolean handleEscapePressed(int keyCode) {
        if (keyCode != 256) return false;   // 256 = GLFW_KEY_ESCAPE
        if (open) { close(); return true; }
        return false;
    }

    public boolean mouseClicked(double mx, double my, int btn) {
        updateLayout();
        if (btn == 0 && inRect(mx, my, btnX, BTN_Y, BTN_SIZE, BTN_SIZE)) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            toggle();
            return true;
        }
        if (!open) return false;
        if (inRect(mx, my, panelX, panelY, panelW, panelH)) return true;   // click inside: swallow
        close();                                                           // click outside: close
        return CONSUME_OUTSIDE_CLICK;
    }

    public boolean mouseScrolled(double mx, double my, double delta) {
        if (!open) return false;
        updateLayout();
        if (!inRect(mx, my, panelX, panelY, panelW, panelH)) return false;
        int step = (int) Math.round(delta * ROW_H * 2);
        if (mx < splitX) scrollAlly = clampScroll(scrollAlly - step, allyRows.size());
        else scrollEnemy = clampScroll(scrollEnemy - step, enemyRows.size());
        return true;
    }

    /** Draws the button (always) and the panel (when open). Call at the very end of the screen's render(). */
    public void render(GuiGraphics gui, int mx, int my) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Font font = mc.font;
        refreshRows();
        updateLayout();
        if (open) {
            long now = System.currentTimeMillis();
            if (now - lastRequestMs >= REQUEST_INTERVAL_MS) requestData();
        }
        pendingTooltip = null;

        RenderSystem.enableBlend();
        gui.pose().pushPose();
        gui.pose().translate(0, 0, Z);
        renderButton(gui, mx, my);
        if (open) renderPanel(gui, font, mx, my);
        if (pendingTooltip != null) gui.renderTooltip(font, pendingTooltip, mx, my);
        gui.pose().popPose();
    }

    /** Optional: call after a resource reload if you swap textures while the game runs. */
    public static void clearTextureCache() { TEX_CACHE.clear(); }

    // ================================================================== layout

    private void updateLayout() {
        Minecraft mc = Minecraft.getInstance();
        sw = mc.getWindow().getGuiScaledWidth();
        sh = mc.getWindow().getGuiScaledHeight();
        btnX = sw - QUIT_LEFT_OFFSET - BTN_GAP - BTN_SIZE;

        PacketSyncVehicleList src = ClientData.vehicleList;
        noData = src == null || !src.hasTeam;

        panelW = Math.min(sw - 10, Math.max(PANEL_MIN_WIDTH, (int) (sw * PANEL_WIDTH_FRACTION)));
        panelX = sw - 5 - panelW;
        panelY = BTN_Y + BTN_SIZE + PANEL_TOP_GAP;
        leftW = (panelW - 1) / 2;
        splitX = panelX + leftW;
        rightX = splitX + 1;
        rightW = panelW - 1 - leftW;

        int rows = noData ? 2 : Math.max(1, Math.max(allyRows.size(), enemyRows.size()));
        int natural = TITLE_H + (noData ? 0 : COLHDR_H) + rows * ROW_H + PAD_BOTTOM;
        int maxH = Math.min((int) (sh * PANEL_MAX_HEIGHT_FRACTION), sh - panelY - 5);
        panelH = Math.max(TITLE_H + ROW_H, Math.min(natural, maxH));
        viewTop = panelY + TITLE_H + (noData ? 0 : COLHDR_H);
        viewH = panelY + panelH - PAD_BOTTOM - viewTop;
    }

    private int clampScroll(int value, int rowCount) {
        int max = Math.max(0, rowCount * ROW_H - viewH);
        return Mth.clamp(value, 0, max);
    }

    private static boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ================================================================== networking

    private void requestData() {
        lastRequestMs = System.currentTimeMillis();
        PacketHandler.INSTANCE.sendToServer(new PacketRequestVehicleList());
    }

    // ================================================================== data -> rows

    private void refreshRows() {
        PacketSyncVehicleList src = ClientData.vehicleList;
        if (src == cachedSource) return;
        cachedSource = src;
        allyRows = src == null ? new ArrayList<>() : buildRows(src.ally);
        enemyRows = src == null ? new ArrayList<>() : buildRows(src.enemy);
        scrollAlly = clampScroll(scrollAlly, allyRows.size());
        scrollEnemy = clampScroll(scrollEnemy, enemyRows.size());
    }

    private static List<Row> buildRows(List<PacketSyncVehicleList.Entry> entries) {
        List<Row> rows = new ArrayList<>();
        for (PacketSyncVehicleList.Entry e : entries) {
            Row r = new Row();
            r.e = e;
            r.cat = VehicleCatalog.Category.byId(e.category);
            r.name = vehicleName(e.vehicleId);
            r.sortName = r.name.getString().toLowerCase(Locale.ROOT);
            r.icon = mapIcon(e.markerType, r.cat);
            rows.add(r);
        }
        // Order: by category (enum declaration order), then by displayed name.
        rows.sort((a, b) -> {
            int c = Integer.compare(a.cat.ordinal(), b.cat.ordinal());
            return c != 0 ? c : a.sortName.compareTo(b.sortName);
        });
        return rows;
    }

    /**
     * Same icon the vehicle has on the map (textures/gui/map_icons/*.png), chosen by the marker type.
     * If the marker type is unknown, falls back to an icon picked by category.
     */
    private static ResourceLocation mapIcon(String markerType, VehicleCatalog.Category cat) {
        String file = markerType == null ? null : MAP_ICON_FILES.get(markerType.trim().toLowerCase(Locale.ROOT));
        if (file == null) {
            file = switch (cat) {
                case LOGISTICS -> "supply_truck";
                case TRANSPORT -> "infantry_vehicle";
                case APC -> "apc";
                case IFV -> "combat_vehicle";
                case TANK -> "tank";
                case SPG -> "spg";
                case AIR_DEFENSE -> "mobile_zu";
                case HELICOPTER -> "helicopter";
                case AIRCRAFT -> "cas_fighter";
                default -> "default";
            };
        }
        return new ResourceLocation("aas", MAP_ICON_DIR + file + ".png");
    }

    private static final Set<String> WARNED_IDS = new HashSet<>();

    /**
     * The name comes from the vehicle entity type's own lang key (EntityType#getDescriptionId, e.g.
     * "entity.<modid>.<name>") - the mod's own lang files contain no per-vehicle names.
     * If the key is missing, the id WITHOUT the namespace is shown ("squadmc:puma" -> "puma")
     * and a warning is logged once.
     */
    private static Component vehicleName(String vehicleId) {
        ResourceLocation rl = ResourceLocation.tryParse(vehicleId);
        if (rl != null && ForgeRegistries.ENTITY_TYPES.containsKey(rl)) {
            String key = ForgeRegistries.ENTITY_TYPES.getValue(rl).getDescriptionId();
            if (I18n.exists(key)) return Component.translatable(key);
            warnOnce(vehicleId, "no lang key '" + key + "'");
        } else {
            warnOnce(vehicleId, "entity type is not registered");
        }
        return Component.literal(shortId(vehicleId));
    }

    /** "squadmc:puma" -> "puma"; an id without a namespace is returned unchanged. */
    private static String shortId(String id) {
        if (id == null) return "";
        int i = id.indexOf(':');
        return i >= 0 ? id.substring(i + 1) : id;
    }

    private static void warnOnce(String id, String why) {
        if (WARNED_IDS.add(id)) LOGGER.warn("[AAS] Vehicle list: no translated name for '{}' ({})", id, why);
    }

    // ================================================================== rendering

    private void renderButton(GuiGraphics gui, int mx, int my) {
        boolean hover = inRect(mx, my, btnX, BTN_Y, BTN_SIZE, BTN_SIZE);
        gui.fill(btnX, BTN_Y, btnX + BTN_SIZE, BTN_Y + BTN_SIZE, hover ? 0xFF3A3A3A : 0xFF000000);   // black, lighter on hover
        gui.renderOutline(btnX, BTN_Y, BTN_SIZE, BTN_SIZE, open ? 0xFFFFFFFF : (hover ? 0xFF999999 : COLOR_BORDER));

        int[] info = texInfo(BUTTON_ICON);
        if (info != null) {
            gui.blit(BUTTON_ICON, btnX + 2, BTN_Y + 2, 16, 16, 0f, 0f, info[0], info[1], info[0], info[1]);
        } else {
            // Placeholder "list" glyph until vehicle_list_icon.png exists.
            for (int i = 0; i < 3; i++) {
                int ly = BTN_Y + 5 + i * 4;
                gui.fill(btnX + 4, ly, btnX + 6, ly + 2, 0xFFCCCCCC);
                gui.fill(btnX + 8, ly, btnX + 16, ly + 2, 0xFFCCCCCC);
            }
        }
        if (hover && !open) pendingTooltip = Component.translatable("aas.gui.vehicles.button");
    }

    private void renderPanel(GuiGraphics gui, Font font, int mx, int my) {
        gui.fill(panelX, panelY, panelX + panelW, panelY + panelH, COLOR_BG);
        gui.renderOutline(panelX, panelY, panelW, panelH, COLOR_BORDER);

        PacketSyncVehicleList src = ClientData.vehicleList;

        if (noData) {
            gui.fill(panelX + 1, panelY + 1, panelX + panelW - 1, panelY + TITLE_H, COLOR_TITLE_BG);
            gui.drawCenteredString(font, Component.translatable("aas.gui.vehicles.button"), panelX + panelW / 2, panelY + 3, COLOR_TEXT);
            gui.drawCenteredString(font, Component.translatable("aas.gui.vehicles.no_data"), panelX + panelW / 2, viewTop + 6, COLOR_DIM);
            return;
        }

        // Titles (allied title is tinted with the allied team colour)
        gui.fill(panelX + 1, panelY + 1, panelX + panelW - 1, panelY + TITLE_H, COLOR_TITLE_BG);
        int allyColor = "RED".equals(src.allyTeam) ? 0xFFFF6666 : 0xFF66AAFF;
        int enemyColor = "RED".equals(src.allyTeam) ? 0xFF66AAFF : 0xFFFF6666;
        gui.drawCenteredString(font, Component.translatable("aas.gui.vehicles.title.allied"), panelX + leftW / 2, panelY + 3, allyColor);
        gui.drawCenteredString(font, Component.translatable("aas.gui.vehicles.title.enemy"), rightX + rightW / 2, panelY + 3, enemyColor);

        // Vertical divider between the two halves
        gui.fill(splitX, panelY + 1, splitX + 1, panelY + panelH - 1, COLOR_BORDER);

        // Allied half always shows everything. Enemy half shows only total + tickets,
        // unless the viewer is creative/spectator (fullVisibility).
        renderHalf(gui, font, mx, my, panelX, leftW, allyRows, true, true);
        renderHalf(gui, font, mx, my, rightX, rightW, enemyRows, src.fullVisibility, false);
    }

    private void renderHalf(GuiGraphics gui, Font font, int mx, int my, int x, int w,
                            List<Row> rows, boolean full, boolean isAlly) {
        // ---- column geometry (right to left; scrollbar sits at the far right)
        int colsRight = x + w - 2 - SCROLL_W - 2;
        int ticketsR = colsRight, ticketsL = ticketsR - COL_TICKETS;
        int timerL = 0, timerR = 0, countL, countR;
        if (full) {
            timerR = ticketsL; timerL = timerR - COL_TIMER;
            countR = timerL;   countL = countR - COL_COUNT_FULL;
        } else {
            countR = ticketsL; countL = countR - COL_COUNT_SIMPLE;
        }
        int nameL = x + 3 + ICON + 3;
        int nameR = countL - 3;

        // ---- column header row (stays in place while the list scrolls)
        int hy = panelY + TITLE_H;
        gui.fill(x + 1, hy, x + w - 1, hy + COLHDR_H, 0x30000000);
        gui.fill(x + 1, hy + COLHDR_H - 1, x + w - 1, hy + COLHDR_H, COLOR_BORDER);
        int hIconY = hy + (COLHDR_H - HDR_ICON) / 2;

        // count header: "#" (a symbol, not a language string)
        gui.drawCenteredString(font, "#", (countL + countR) / 2, hy + 3, COLOR_DIM);
        if (inRect(mx, my, countL, hy, countR - countL, COLHDR_H)) {
            pendingTooltip = Component.translatable(full ? "aas.gui.vehicles.tooltip.count" : "aas.gui.vehicles.tooltip.total");
        }
        if (full) {
            drawTexOrStub(gui, ICON_CLOCK, (timerL + timerR) / 2 - HDR_ICON / 2, hIconY, HDR_ICON);
            if (inRect(mx, my, timerL, hy, timerR - timerL, COLHDR_H)) {
                pendingTooltip = Component.translatable("aas.gui.vehicles.tooltip.respawn");
            }
        }
        drawTexOrStub(gui, ICON_TICKETS, (ticketsL + ticketsR) / 2 - HDR_ICON / 2, hIconY, HDR_ICON);
        if (inRect(mx, my, ticketsL, hy, ticketsR - ticketsL, COLHDR_H)) {
            pendingTooltip = Component.translatable("aas.gui.vehicles.tooltip.tickets");
        }

        // ---- scrolling list area
        int scroll = clampScroll(isAlly ? scrollAlly : scrollEnemy, rows.size());
        if (isAlly) scrollAlly = scroll; else scrollEnemy = scroll;
        int contentH = rows.size() * ROW_H;

        if (rows.isEmpty()) {
            gui.drawCenteredString(font, Component.translatable("aas.gui.vehicles.no_data"), x + w / 2, viewTop + 3, COLOR_DIM);
            return;
        }

        Minecraft mcNow = Minecraft.getInstance();
        long gameTime = mcNow.level != null ? mcNow.level.getGameTime() : 0L;

        gui.enableScissor(x + 1, viewTop, x + w - 1, viewTop + viewH);
        for (int i = 0; i < rows.size(); i++) {
            int ry = viewTop + i * ROW_H - scroll;
            if (ry + ROW_H < viewTop || ry > viewTop + viewH) continue;
            Row r = rows.get(i);
            PacketSyncVehicleList.Entry e = r.e;

            if ((i & 1) == 1) gui.fill(x + 1, ry, x + w - 1, ry + ROW_H, COLOR_STRIPE);

            // 1) vehicle icon = same icon as on the map (placeholder square if the PNG is missing)
            int iconY = ry + (ROW_H - ICON) / 2;
            drawTexOrStub(gui, r.icon, x + 3, iconY, ICON);
            if (inRect(mx, my, x + 3, iconY, ICON, ICON) && my >= viewTop && my < viewTop + viewH) {
                pendingTooltip = Component.translatable(r.cat.langKey);
            }

            int ty = ry + (ROW_H - font.lineHeight) / 2 + 1;

            // 2) name (from the vehicle's own lang key, or the id without namespace), trimmed to the column
            gui.drawString(font, fit(font, r.name.getString(), nameR - nameL), nameL, ty, COLOR_TEXT, false);

            // 4) count: alive/total (full) or total only (enemy)
            if (full) {
                int alive = Math.max(0, e.alive);
                int cc = alive == 0 ? COLOR_BAD : (alive < e.total ? COLOR_WARN : COLOR_TEXT);
                drawCentered(gui, font, alive + "/" + e.total, countL, countR, ty, cc);
            } else {
                drawCentered(gui, font, String.valueOf(e.total), countL, countR, ty, COLOR_TEXT);
            }

            // 5) time to the nearest respawn (full only): READY if every unit is alive
            if (full) {
                if (e.alive >= e.total) {
                    drawCentered(gui, font, Component.translatable("aas.hud.ready").getString(), timerL, timerR, ty, COLOR_READY);
                } else if (e.nextRespawnTick < 0) {
                    drawCentered(gui, font, "--:--", timerL, timerR, ty, COLOR_DIM);
                } else {
                    long remTicks = Math.max(0L, e.nextRespawnTick - gameTime);
                    long sec = (remTicks + 19L) / 20L;   // округление вверх: 0:00 только когда время реально вышло
                    String t = (sec / 60) + ":" + (sec % 60 < 10 ? "0" : "") + (sec % 60);
                    drawCentered(gui, font, t, timerL, timerR, ty, COLOR_TEXT);
                }
            }

            // 6) tickets lost when this vehicle is destroyed
            drawCentered(gui, font, e.penalty > 0 ? "-" + e.penalty : "0", ticketsL, ticketsR, ty,
                    e.penalty > 0 ? COLOR_TICKETS : COLOR_DIM);
        }
        gui.disableScissor();

        // ---- thin scrollbar
        if (contentH > viewH) {
            int trackX = x + w - 2 - SCROLL_W;
            gui.fill(trackX, viewTop, trackX + SCROLL_W, viewTop + viewH, 0x40FFFFFF);
            int thumbH = Math.max(8, viewH * viewH / contentH);
            int maxScroll = contentH - viewH;
            int thumbY = viewTop + (int) ((long) (viewH - thumbH) * scroll / maxScroll);
            gui.fill(trackX, thumbY, trackX + SCROLL_W, thumbY + thumbH, 0xFFAAAAAA);
        }
    }

    // ================================================================== small helpers

    private static void drawCentered(GuiGraphics gui, Font font, String s, int left, int right, int y, int color) {
        gui.drawString(font, s, (left + right) / 2 - font.width(s) / 2, y, color, false);
    }

    private static String fit(Font font, String s, int maxW) {
        if (maxW <= 0) return "";
        if (font.width(s) <= maxW) return s;
        String dots = "...";
        return font.plainSubstrByWidth(s, Math.max(0, maxW - font.width(dots))) + dots;
    }

    /** Draws the texture scaled to size x size, or a grey placeholder square if the PNG does not exist. */
    private static void drawTexOrStub(GuiGraphics gui, ResourceLocation tex, int x, int y, int size) {
        int[] info = texInfo(tex);
        if (info != null) {
            gui.blit(tex, x, y, size, size, 0f, 0f, info[0], info[1], info[0], info[1]);
        } else {
            gui.fill(x, y, x + size, y + size, 0xFF3A3A3A);
            gui.renderOutline(x, y, size, size, 0xFF888888);
        }
    }

    // Texture existence + size, cached (same idea as MarkerPanelOverlay: check the resource manager,
    // so no "Failed to load texture" spam in the log while PNGs are still missing).
    private static final Map<ResourceLocation, int[]> TEX_CACHE = new HashMap<>();
    private static final int[] MISSING = new int[0];

    private static int[] texInfo(ResourceLocation tex) {
        int[] cached = TEX_CACHE.get(tex);
        if (cached != null) return cached == MISSING ? null : cached;
        int[] info = MISSING;
        var res = Minecraft.getInstance().getResourceManager().getResource(tex);
        if (res.isPresent()) {
            info = new int[]{16, 16};
            try (InputStream in = res.get().open(); NativeImage img = NativeImage.read(in)) {
                info = new int[]{img.getWidth(), img.getHeight()};
            } catch (IOException ignored) {
                // keep 16x16 fallback
            }
        }
        TEX_CACHE.put(tex, info);
        return info == MISSING ? null : info;
    }
}