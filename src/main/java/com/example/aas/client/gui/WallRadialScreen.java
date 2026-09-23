// PATH: src/main/java/com/example/aas/client/gui/WallRadialScreen.java
package com.example.aas.client.gui;

import com.example.aas.client.ClientPlacementHandler;
import com.example.aas.item.RallyItem;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/**
 * Радиальное меню выбора типа стены: 1x1 / 2x2 / 3x3 / Стена с колючкой / Бойница / Назад.
 * Визуальный стиль полностью повторяет DefenseRadialScreen и StaticGunRadialScreen (тот же трек,
 * та же мёртвая зона с "Отмена", тот же принцип подсветки наведённого сектора и серых недоступных иконок).
 *
 * Недоступные постройки (не хватает материалов рядом с HUB/ящиком) отображаются серыми,
 * но клик по ним всё равно обрабатывается — финальную проверку и сообщение об ошибке делает
 * ClientPlacementHandler / сервер, как и в остальных меню построек.
 */
public class WallRadialScreen extends Screen {

    private static final ResourceLocation ICON_WALL_1X1     = new ResourceLocation("aas", "textures/gui/wall_1x1.png");
    private static final ResourceLocation ICON_WALL_2X2     = new ResourceLocation("aas", "textures/gui/wall_2x2.png");
    private static final ResourceLocation ICON_WALL_3X3     = new ResourceLocation("aas", "textures/gui/wall_3x3.png");
    private static final ResourceLocation ICON_WALL_WIRE    = new ResourceLocation("aas", "textures/gui/wall_wire.png");
    private static final ResourceLocation ICON_WALL_LOOPHOLE = new ResourceLocation("aas", "textures/gui/wall_loophole.png");
    private static final ResourceLocation ICON_BACK         = new ResourceLocation("aas", "textures/gui/back_button.png");

    private static final int SECTOR_COUNT = 6;
    private static final float SECTOR_DEG = 360f / SECTOR_COUNT;

    // ---- Геометрия (тот же стиль, что и в DefenseRadialScreen/StaticGunRadialScreen) ----
    private static final int BACKDROP_RADIUS = 130;
    private static final int TRACK_OUTER = 56;
    private static final int TRACK_INNER = 50;
    private static final int DEAD_ZONE_RADIUS = 40;
    private static final int ICON_RADIUS = 95;
    private static final int ICON_SIZE = 20;
    private static final float GAP_DEG = 6f;

    // ---- Цвета ----
    private static final int COLOR_BACKDROP   = 0x7A000000;
    private static final int COLOR_HOVER      = 0x80CCCCCC;
    private static final int COLOR_TRACK      = 0xFFFFFFFF;
    private static final int COLOR_TRACK_DIM  = 0xFFFFFFFF;
    private static final int COLOR_INNER_RING = 0xFFC2A278;
    private static final int COLOR_RIM        = 0x55FFFFFF;

    // ---- Ключи локализации ----
    private static final String KEY_WALL_1X1     = "aas.wall.1x1";
    private static final String KEY_WALL_2X2     = "aas.wall.2x2";
    private static final String KEY_WALL_3X3     = "aas.wall.3x3";
    private static final String KEY_WALL_WIRE    = "aas.wall.wire";
    private static final String KEY_WALL_LOOPHOLE = "aas.wall.loophole";
    private static final String KEY_CANCEL       = "aas.radio.cancel";
    private static final String KEY_HINT         = "aas.radio.hint";
    private static final String KEY_BACK         = "aas.radio.back";

    // Порядок секторов: 0=1x1, 1=2x2, 2=3x3, 3=Стена с колючкой, 4=Бойница, 5=Назад
    private static final ResourceLocation[] SECTOR_ICONS      = {ICON_WALL_1X1, ICON_WALL_2X2, ICON_WALL_3X3, ICON_WALL_WIRE, ICON_WALL_LOOPHOLE, ICON_BACK};
    private static final String[]           SECTOR_LABEL_KEYS = {KEY_WALL_1X1, KEY_WALL_2X2, KEY_WALL_3X3, KEY_WALL_WIRE, KEY_WALL_LOOPHOLE, KEY_BACK};
    // ID построек для ClientPlacementHandler (совпадают с прежней версией меню)
    private static final int PLACEMENT_ID_WALL_1X1      = 10;
    private static final int PLACEMENT_ID_WALL_2X2      = 11;
    private static final int PLACEMENT_ID_WALL_3X3      = 12;
    private static final int PLACEMENT_ID_WALL_WIRE     = 15; // Стена с колючкой (ступенька)
    private static final int PLACEMENT_ID_WALL_LOOPHOLE = 16; // Стена с амбразурой
    // Индекс сектора "Назад" — последний в колесе, всегда доступен (без проверки материалов).
    private static final int SECTOR_BACK = 5;

    private final Screen parentScreen;
    private boolean isSwitching = false;

    public WallRadialScreen(Screen parent) {
        super(Component.literal("Walls Menu"));
        this.parentScreen = parent;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        super.init();
        triggerRadioAnim("deploy");
    }

    @Override
    public void onClose() {
        if (!isSwitching) {
            triggerRadioAnim("close");
        }
        super.onClose();
    }

    private void triggerRadioAnim(String animName) {
        if (this.minecraft.player != null) {
            ItemStack stack = this.minecraft.player.getMainHandItem();
            if (stack.getItem() instanceof RallyItem radio) {
                long instanceId = stack.getOrCreateTag().getLong("GeckoLibID");
                radio.triggerAnim(this.minecraft.player, instanceId, "RadioController", animName);
            }
        }
    }

    private int getHoveredSector(int mouseX, int mouseY) {
        double dx = mouseX - width / 2.0;
        double dy = mouseY - height / 2.0;
        double dist = Math.sqrt(dx * dx + dy * dy);
        if (dist < DEAD_ZONE_RADIUS) return -1;

        double angle = Math.toDegrees(Math.atan2(dx, -dy)); // 0 = вверх, по часовой
        if (angle < 0) angle += 360;
        double shifted = (angle + SECTOR_DEG / 2.0) % 360;
        return (int) Math.floor(shifted / SECTOR_DEG) % SECTOR_COUNT;
    }

    /**
     * Может ли игрок сейчас построить стену данного типа в секторе {@code i} (используется только
     * для подсветки иконки белым/серым — окончательную проверку всё равно делает
     * ClientPlacementHandler/сервер при реальной попытке постройки).
     */
    private boolean canBuildSector(int i) {
        return switch (i) {
            case 0 -> ClientPlacementHandler.canAfford(PLACEMENT_ID_WALL_1X1);
            case 1 -> ClientPlacementHandler.canAfford(PLACEMENT_ID_WALL_2X2);
            case 2 -> ClientPlacementHandler.canAfford(PLACEMENT_ID_WALL_3X3);
            case 3 -> ClientPlacementHandler.canAfford(PLACEMENT_ID_WALL_WIRE);
            case 4 -> ClientPlacementHandler.canAfford(PLACEMENT_ID_WALL_LOOPHOLE);
            case 5 -> true; // Назад — всегда доступен
            default -> true;
        };
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        int cx = width / 2;
        int cy = height / 2;
        int hovered = getHoveredSector(mouseX, mouseY);

        // Доступность считается один раз за кадр на сектор, не пересчитываем лишний раз.
        boolean[] canBuild = new boolean[SECTOR_COUNT];
        for (int i = 0; i < SECTOR_COUNT; i++) canBuild[i] = canBuildSector(i);

        PoseStack pose = gui.pose();

        // 1. Затемнённая подложка
        fillCircle(pose, cx, cy, BACKDROP_RADIUS, COLOR_BACKDROP, 64);

        // 2. Подсветка наведённого сектора
        if (hovered >= 0) {
            float startDeg = hovered * SECTOR_DEG - SECTOR_DEG / 2f;
            float endDeg = startDeg + SECTOR_DEG;
            drawRingArc(pose, cx, cy, DEAD_ZONE_RADIUS, BACKDROP_RADIUS - 6, startDeg, endDeg, COLOR_HOVER, 16);
        }

        // 3. Белое сегментированное кольцо-трек
        for (int i = 0; i < SECTOR_COUNT; i++) {
            float startDeg = i * SECTOR_DEG - SECTOR_DEG / 2f + GAP_DEG / 2f;
            float endDeg   = i * SECTOR_DEG + SECTOR_DEG / 2f - GAP_DEG / 2f;
            int color = (i == hovered) ? COLOR_TRACK : COLOR_TRACK_DIM;
            drawRingArc(pose, cx, cy, TRACK_INNER, TRACK_OUTER, startDeg, endDeg, color, 24);
        }

        // 4. Иконки + подписи (серые, если постройку сейчас разместить не получится)
        for (int i = 0; i < SECTOR_COUNT; i++) {
            float deg = i * SECTOR_DEG;
            double rad = Math.toRadians(deg);
            int ix = cx + (int) Math.round(Math.sin(rad) * ICON_RADIUS);
            int iy = cy - (int) Math.round(Math.cos(rad) * ICON_RADIUS);

            boolean available = canBuild[i];

            RenderSystem.enableBlend();
            float alpha = i == hovered ? 1f : 0.85f;
            if (available) {
                RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
            } else {
                RenderSystem.setShaderColor(0.45f, 0.45f, 0.45f, alpha);
            }
            gui.blit(SECTOR_ICONS[i], ix - ICON_SIZE / 2, iy - ICON_SIZE / 2, ICON_SIZE, ICON_SIZE, 0f, 0f, 16, 16, 16, 16);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

            String label = I18n.get(SECTOR_LABEL_KEYS[i]);
            int labelColor;
            if (!available) labelColor = 0xFFAAAAAA;
            else labelColor = i == hovered ? 0xFFFFFFFF : 0xFFEEEEEE;
            gui.drawCenteredString(font, label, ix, iy + ICON_SIZE / 2 + 4, labelColor);
        }

        // 5. Тонкий контур большого круга
        drawRing(pose, cx, cy, BACKDROP_RADIUS - 1, BACKDROP_RADIUS + 1, COLOR_RIM, 48);

        // 6. Тонкое кольцо мёртвой зоны
        drawRing(pose, cx, cy, DEAD_ZONE_RADIUS - 1, DEAD_ZONE_RADIUS + 1, COLOR_INNER_RING, 48);

        // 7. Центральный текст
        String centerText = hovered >= 0 ? I18n.get(SECTOR_LABEL_KEYS[hovered]) : I18n.get(KEY_CANCEL);
        int centerColor = hovered >= 0 ? 0xFFFFFFFF : 0xFFCCCCCC;
        gui.drawCenteredString(font, centerText, cx, cy - 4, centerColor);

        // 8. Подсказка снизу
        gui.drawCenteredString(font, I18n.get(KEY_HINT), cx, cy + BACKDROP_RADIUS + 10, 0xFFCCCCCC);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int hovered = getHoveredSector((int) mouseX, (int) mouseY);
            handleSelect(hovered);
            return true;
        }
        if (button == 1) {
            Minecraft.getInstance().setScreen(parentScreen);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void handleSelect(int sector) {
        if (sector < 0) {
            this.onClose(); // мёртвая зона — закрыть без действия
            return;
        }

        switch (sector) {
            case 0 -> { // 1x1 Wall
                ClientPlacementHandler.startPlacing(PLACEMENT_ID_WALL_1X1);
                this.onClose();
            }
            case 1 -> { // 2x2 Wall
                ClientPlacementHandler.startPlacing(PLACEMENT_ID_WALL_2X2);
                this.onClose();
            }
            case 2 -> { // 3x3 Wall
                ClientPlacementHandler.startPlacing(PLACEMENT_ID_WALL_3X3);
                this.onClose();
            }
            case 3 -> { // Стена с колючкой
                ClientPlacementHandler.startPlacing(PLACEMENT_ID_WALL_WIRE);
                this.onClose();
            }
            case 4 -> { // Бойница
                ClientPlacementHandler.startPlacing(PLACEMENT_ID_WALL_LOOPHOLE);
                this.onClose();
            }
            case 5 -> { // Назад — на предыдущий экран
                Minecraft.getInstance().setScreen(parentScreen);
            }
        }
    }

    // ================= Утилиты отрисовки (те же, что и в DefenseRadialScreen/StaticGunRadialScreen) =================

    private static void beginColorDraw() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
    }

    private static void fillCircle(PoseStack pose, float cx, float cy, float radius, int argb, int segments) {
        beginColorDraw();
        Matrix4f m = pose.last().pose();
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        float a = ((argb >>> 24) & 0xFF) / 255f, r = ((argb >> 16) & 0xFF) / 255f, g = ((argb >> 8) & 0xFF) / 255f, b = (argb & 0xFF) / 255f;
        buf.vertex(m, cx, cy, 0).color(r, g, b, a).endVertex();
        for (int i = 0; i <= segments; i++) {
            double ang = 2 * Math.PI * i / segments;
            float x = cx + (float) (Math.sin(ang) * radius);
            float y = cy - (float) (Math.cos(ang) * radius);
            buf.vertex(m, x, y, 0).color(r, g, b, a).endVertex();
        }
        tess.end();
    }

    private static void drawRing(PoseStack pose, float cx, float cy, float innerR, float outerR, int argb, int segments) {
        beginColorDraw();
        Matrix4f m = pose.last().pose();
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        float a = ((argb >>> 24) & 0xFF) / 255f, r = ((argb >> 16) & 0xFF) / 255f, g = ((argb >> 8) & 0xFF) / 255f, b = (argb & 0xFF) / 255f;
        for (int i = 0; i <= segments; i++) {
            double ang = 2 * Math.PI * i / segments;
            float sx = (float) Math.sin(ang), sy = (float) -Math.cos(ang);
            buf.vertex(m, cx + sx * outerR, cy + sy * outerR, 0).color(r, g, b, a).endVertex();
            buf.vertex(m, cx + sx * innerR, cy + sy * innerR, 0).color(r, g, b, a).endVertex();
        }
        tess.end();
    }

    private static void drawRingArc(PoseStack pose, float cx, float cy, float innerR, float outerR,
                                    float startDeg, float endDeg, int argb, int segments) {
        beginColorDraw();
        Matrix4f m = pose.last().pose();
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        float a = ((argb >>> 24) & 0xFF) / 255f, r = ((argb >> 16) & 0xFF) / 255f, g = ((argb >> 8) & 0xFF) / 255f, b = (argb & 0xFF) / 255f;
        for (int i = 0; i <= segments; i++) {
            double ang = Math.toRadians(startDeg + (endDeg - startDeg) * i / segments);
            float sx = (float) Math.sin(ang), sy = (float) -Math.cos(ang);
            buf.vertex(m, cx + sx * outerR, cy + sy * outerR, 0).color(r, g, b, a).endVertex();
            buf.vertex(m, cx + sx * innerR, cy + sy * innerR, 0).color(r, g, b, a).endVertex();
        }
        tess.end();
    }
}