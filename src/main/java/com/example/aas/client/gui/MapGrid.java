// PATH: src\main\java\com\example\aas\client\gui\MapGrid.java
package com.example.aas.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Tactical map grid (Arma/Squad style).
 *
 * - Big squares: 300x300 blocks, semi-transparent black lines.
 * - Small squares: 100x100 blocks (3x3 inside each big one), semi-transparent white lines.
 * - Letters (A, B, C ...) along the top = columns (X, west -> east).
 * - Numbers (1, 2, 3 ...) along the left = rows (Z, north -> south).
 * - Small squares are numbered like a numpad:  7 8 9 / 4 5 6 / 1 2 3.
 * - When zoomed in, each big square shows its name (e.g. "J5") in its top-left part.
 * - Hover tooltip, e.g. "J5 9", is drawn to the top-right of the cursor.
 *
 * The grid is anchored to the map image: (0,0) of the grid = top-left corner of the map.
 * Everything is derived from the current zoom (blocks per pixel) and the map size in blocks,
 * so no texture is needed.
 */
public final class MapGrid {

    public static final int BIG = 300;
    public static final int SMALL = 100;

    // ARGB colors
    private static final int COLOR_BIG    = 0x99000000; // black, ~60%
    private static final int COLOR_SMALL  = 0x55FFFFFF; // white, ~33%
    private static final int COLOR_STRIP  = 0x66000000; // background of top/left label strips
    private static final int COLOR_LABEL  = 0xFFFFFFFF;
    private static final int COLOR_NAME   = 0xCCFFFFFF;
    private static final int COLOR_TIP_TEXT = 0xFFFFFFFF; // white text
    private static final int COLOR_TIP_OUTLINE = 0xFF000000; // black outline

    // Level of detail thresholds (screen pixels)
    private static final double MIN_SMALL_PX = 8;    // below: hide the white 100m lines
    private static final double MIN_BIG_PX   = 6;    // below: hide the black 300m lines
    private static final double MIN_NAME_PX  = 90;   // below: no "J5" inside big squares
    private static final double THICK_BIG_PX = 150;  // above: black lines become 2px thick

    private static final float NAME_SCALE = 0.75f;

    private MapGrid() {}

    // ---------------------------------------------------------------- naming

    /** 0 -> A, 25 -> Z, 26 -> AA, 27 -> AB ... */
    public static String letters(int index) {
        StringBuilder sb = new StringBuilder();
        int n = index + 1;
        while (n > 0) {
            int r = (n - 1) % 26;
            sb.insert(0, (char) ('A' + r));
            n = (n - 1) / 26;
        }
        return sb.toString();
    }

    /** Numpad digit for a small square: row 0 (north) = 7 8 9, row 2 (south) = 1 2 3. */
    public static int numpad(int ix, int iy) {
        return (2 - iy) * 3 + ix + 1;
    }

    /**
     * Name of the big square, e.g. "J5".
     */
    public static String bigName(int col, int row) {
        return letters(col) + (row + 1);
    }

    /**
     * Full name of the cell for a point given in blocks relative to the map's top-left corner.
     * Returns null if the point is outside of the map. Example: "J5 9".
     */
    public static String cellName(double u, double v, int sizeBlocks) {
        if (u < 0 || v < 0 || u >= sizeBlocks || v >= sizeBlocks) return null;
        int col = (int) Math.floor(u / BIG);
        int row = (int) Math.floor(v / BIG);
        int ix = clamp((int) Math.floor((u - col * (double) BIG) / SMALL), 0, 2);
        int iy = clamp((int) Math.floor((v - row * (double) BIG) / SMALL), 0, 2);
        return bigName(col, row) + " " + numpad(ix, iy);
    }

    // ---------------------------------------------------------------- drawing

    /**
     * Draws the grid. Call it in screen space, inside the map scissor, right after the map texture.
     *
     * @param mapX, mapY, mapSize map panel on screen
     * @param offX, offY          world offset (in blocks) of the map's top-left corner relative to the
     *                            map center - the same (int)drawX / (int)drawY the map texture is blitted at
     * @param sizeBlocks          map size in blocks
     * @param bpp                 blocks per screen pixel (ClientData.mapScale)
     */
    public static void render(GuiGraphics gui, Font font,
                              int mapX, int mapY, int mapSize,
                              int offX, int offY, int sizeBlocks, double bpp) {
        if (sizeBlocks <= 0 || bpp <= 0) return;

        final double ppb = 1.0 / bpp;                       // pixels per block
        final double half = mapSize / 2.0;
        final double ox = mapX + half + offX * ppb;         // screen x of the grid origin
        final double oy = mapY + half + offY * ppb;
        final double ex = ox + sizeBlocks * ppb;            // screen x of the right map edge
        final double ey = oy + sizeBlocks * ppb;
        final int mapRight = mapX + mapSize;
        final int mapBottom = mapY + mapSize;

        // part of the map image that is actually visible inside the panel
        final int vx0 = (int) Math.max(mapX, Math.floor(ox));
        final int vy0 = (int) Math.max(mapY, Math.floor(oy));
        final int vx1 = (int) Math.min(mapRight, Math.ceil(ex));
        final int vy1 = (int) Math.min(mapBottom, Math.ceil(ey));
        if (vx1 <= vx0 || vy1 <= vy0) return;

        final double smallPx = SMALL * ppb;
        final double bigPx = BIG * ppb;
        final int smallCount = sizeBlocks / SMALL;          // number of full 100m steps

        // ---- small (white) lines: every 100m that is not a 300m line
        if (smallPx >= MIN_SMALL_PX) {
            int i0 = Math.max(1, (int) Math.floor((vx0 - ox) / smallPx));
            int i1 = Math.min(smallCount, (int) Math.ceil((vx1 - ox) / smallPx));
            for (int i = i0; i <= i1; i++) {
                if ((i * SMALL) % BIG == 0) continue;
                int x = (int) Math.round(ox + i * smallPx);
                if (x < vx0 || x >= vx1) continue;
                gui.fill(x, vy0, x + 1, vy1, COLOR_SMALL);
            }
            int j0 = Math.max(1, (int) Math.floor((vy0 - oy) / smallPx));
            int j1 = Math.min(smallCount, (int) Math.ceil((vy1 - oy) / smallPx));
            for (int j = j0; j <= j1; j++) {
                if ((j * SMALL) % BIG == 0) continue;
                int y = (int) Math.round(oy + j * smallPx);
                if (y < vy0 || y >= vy1) continue;
                gui.fill(vx0, y, vx1, y + 1, COLOR_SMALL);
            }
        }

        // ---- big (black) lines: every 300m + map borders
        final int cols = ceilDiv(sizeBlocks, BIG);
        if (bigPx >= MIN_BIG_PX) {
            int w = bigPx >= THICK_BIG_PX ? 2 : 1;
            for (int c = 0; c <= cols; c++) {
                double bx = (c == cols) ? ex : ox + c * bigPx;
                int x = (int) Math.round(bx) - (c == cols ? w : 0);
                if (x + w <= vx0 || x >= vx1) continue;
                gui.fill(Math.max(x, vx0), vy0, Math.min(x + w, vx1), vy1, COLOR_BIG);
            }
            for (int r = 0; r <= cols; r++) {
                double by = (r == cols) ? ey : oy + r * bigPx;
                int y = (int) Math.round(by) - (r == cols ? w : 0);
                if (y + w <= vy0 || y >= vy1) continue;
                gui.fill(vx0, Math.max(y, vy0), vx1, Math.min(y + w, vy1), COLOR_BIG);
            }
        }

        // ---- edge labels: letters on top, numbers on the left
        final int rows = cols; // map is square
        final int topH = font.lineHeight + 3;
        final int leftW = font.width(String.valueOf(rows)) + 5;

        gui.fill(mapX, mapY, mapRight, mapY + topH, COLOR_STRIP);
        gui.fill(mapX, mapY + topH, mapX + leftW, mapBottom, COLOR_STRIP);

        for (int c = 0; c < cols; c++) {
            double x0 = ox + c * bigPx;
            double x1 = Math.min(ox + (c + 1) * bigPx, ex);
            String s = letters(c);
            double a = Math.max(x0, mapX + leftW);
            double b = Math.min(x1, mapRight);
            if (b - a < font.width(s) + 4) continue;
            int tx = (int) Math.round((a + b) / 2.0 - font.width(s) / 2.0);
            gui.drawString(font, s, tx, mapY + 2, COLOR_LABEL, true);
        }
        for (int r = 0; r < rows; r++) {
            double y0 = oy + r * bigPx;
            double y1 = Math.min(oy + (r + 1) * bigPx, ey);
            double a = Math.max(y0, mapY + topH);
            double b = Math.min(y1, mapBottom);
            if (b - a < font.lineHeight + 2) continue;
            String s = String.valueOf(r + 1);
            int ty = (int) Math.round((a + b) / 2.0 - font.lineHeight / 2.0);
            gui.drawString(font, s, mapX + 3, ty, COLOR_LABEL, true);
        }

        // ---- big square names ("J5") in the top-left part when zoomed in enough
        if (bigPx >= MIN_NAME_PX) {
            int c0 = Math.max(0, (int) Math.floor((vx0 - ox) / bigPx));
            int c1 = Math.min(cols - 1, (int) Math.floor((vx1 - 1 - ox) / bigPx));
            int r0 = Math.max(0, (int) Math.floor((vy0 - oy) / bigPx));
            int r1 = Math.min(rows - 1, (int) Math.floor((vy1 - 1 - oy) / bigPx));
            for (int r = r0; r <= r1; r++) {
                for (int c = c0; c <= c1; c++) {
                    String name = bigName(c, r);
                    double x0 = ox + c * bigPx;
                    double y0 = oy + r * bigPx;
                    double x1 = Math.min(x0 + bigPx, ex);
                    double y1 = Math.min(y0 + bigPx, ey);
                    double lx = Math.max(x0, mapX + leftW) + 3;
                    double ly = Math.max(y0, mapY + topH) + 3;
                    double tw = font.width(name) * NAME_SCALE;
                    double th = font.lineHeight * NAME_SCALE;
                    if (lx + tw > x1 - 1 || ly + th > y1 - 1) continue; // square is mostly off-screen
                    gui.pose().pushPose();
                    gui.pose().translate(lx, ly, 0);
                    gui.pose().scale(NAME_SCALE, NAME_SCALE, 1.0f);
                    gui.drawString(font, name, 0, 0, COLOR_NAME, true);
                    gui.pose().popPose();
                }
            }
        }
    }

    /**
     * Tooltip with the cell under the cursor ("J5 9"), drawn to the top-right of the cursor.
     * Call it after the map scissor is disabled. Does nothing if the cursor is outside of the map image.
     */
    public static void renderHover(GuiGraphics gui, Font font,
                                   int mapX, int mapY, int mapSize,
                                   int offX, int offY, int sizeBlocks, double bpp,
                                   int mouseX, int mouseY, int screenW, int screenH) {
        if (sizeBlocks <= 0 || bpp <= 0) return;
        if (mouseX < mapX || mouseX > mapX + mapSize || mouseY < mapY || mouseY > mapY + mapSize) return;

        double half = mapSize / 2.0;
        double u = (mouseX - mapX - half) * bpp - offX;
        double v = (mouseY - mapY - half) * bpp - offY;
        String text = cellName(u, v, sizeBlocks);
        if (text == null) return;

        // plain text, no background: white digits with a black outline, close to the cursor
        int w = font.width(text) + 2;
        int h = font.lineHeight;
        int x = mouseX + 5;
        int y = mouseY - h - 2;
        if (x + w > screenW - 2) x = mouseX - w - 5;   // no room on the right -> put it on the left
        if (y < 2) y = mouseY + 8;                     // no room above -> put it below
        if (y + h > screenH - 2) y = screenH - 2 - h;
        if (x < 2) x = 2;

        gui.pose().pushPose();
        gui.pose().translate(0, 0, 500);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;
                gui.drawString(font, text, x + dx, y + dy, COLOR_TIP_OUTLINE, false);
            }
        }
        gui.drawString(font, text, x, y, COLOR_TIP_TEXT, false);
        gui.pose().popPose();
    }

    // ---------------------------------------------------------------- view limits

    /**
     * Clamps the view center along one axis so the visible area never goes past the map edges
     * (so the background gradient is never visible).
     *
     * @param center       desired view center (world blocks)
     * @param mapCenter    center of the map image (world blocks)
     * @param mapBlocks    map size in blocks
     * @param viewBlocks   size of the visible area in blocks (panel pixels * blocks-per-pixel)
     * @return the clamped center; if the view is larger than the map, the map center
     */
    public static double clampCenter(double center, double mapCenter, double mapBlocks, double viewBlocks) {
        double half = viewBlocks / 2.0;
        double min = mapCenter - mapBlocks / 2.0 + half;
        double max = mapCenter + mapBlocks / 2.0 - half;
        if (min > max) return mapCenter;
        return Math.max(min, Math.min(max, center));
    }

    /**
     * Largest blocks-per-pixel value at which the whole panel is still covered by the map
     * (zooming out further would show the background). Never below minBpp.
     */
    public static double maxBpp(int sizeBlocks, int panelPx, double minBpp) {
        if (panelPx <= 0) return Double.MAX_VALUE;
        return Math.max(minBpp, sizeBlocks / (double) panelPx);
    }

    // ---------------------------------------------------------------- utils

    private static int ceilDiv(int a, int b) { return (a + b - 1) / b; }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
}