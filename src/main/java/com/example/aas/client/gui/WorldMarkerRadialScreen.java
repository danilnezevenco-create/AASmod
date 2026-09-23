// PATH: src/main/java/com/example/aas/client/gui/WorldMarkerRadialScreen.java
package com.example.aas.client.gui;

import com.example.aas.client.ModKeyBindings;
import com.example.aas.network.PacketHandler;
import com.example.aas.network.PacketPlaceRadialMarker;
import com.example.aas.sound.ModSounds;
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
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * Круговое 3D-меню размещения меток (аналог Squad "radial marker wheel").
 *
 * Открывается при УДЕРЖАНИИ клавиши ModKeyBindings.PLACE_PING_KEY (см. ClientEvents,
 * который отвечает за длительность зажатия и решает, открывать ли этот экран или
 * выполнить быстрый тап = обычную метку-глаз).
 *
 * Пока этот Screen открыт, vanilla-код Minecraft не даёт вращать камеру мышью
 * (стандартное поведение любого GUI-экрана), а курсор свободно двигается по экрану —
 * именно это и нужно: направление взгляда (и, следовательно, точка raycast'а на
 * сервере) остаётся заморожённым на время, пока игрок выбирает сектор.
 *
 * Набор иконок подбирается под то, ОТ ЧЬЕГО ЛИЦА открыто меню:
 *   squadGroup == 0 -> иконки лидера отряда (SL)
 *   squadGroup == 1 -> иконки Bravo FTL (marker_..._bravo.png)
 *   squadGroup == 2 -> иконки Charlie FTL (marker_..._charlie.png)
 *
 * Подписи секторов и текст мёртвой зоны переведены на русский/украинский/английский
 * через стандартный механизм локализации Minecraft (I18n.get + lang-файлы мода),
 * поэтому язык подстраивается автоматически под язык игры игрока.
 */
public class WorldMarkerRadialScreen extends Screen {

    // ---- Иконки: SL ----
    private static final ResourceLocation TEX_EYE_SL    = new ResourceLocation("aas", "textures/gui/map_icons/ping_eye.png");
    private static final ResourceLocation TEX_ATTACK_SL = new ResourceLocation("aas", "textures/gui/map_icons/marker_attack.png");
    private static final ResourceLocation TEX_DEFEND_SL = new ResourceLocation("aas", "textures/gui/map_icons/marker_defend.png");
    private static final ResourceLocation TEX_BUILD_SL  = new ResourceLocation("aas", "textures/gui/map_icons/marker_build.png");
    private static final ResourceLocation TEX_MOVE_SL   = new ResourceLocation("aas", "textures/gui/map_icons/marker_move.png");

    // ---- Иконки: Bravo FTL ----
    private static final ResourceLocation TEX_EYE_BRAVO    = new ResourceLocation("aas", "textures/gui/map_icons/ping_eye_bravo.png");
    private static final ResourceLocation TEX_ATTACK_BRAVO = new ResourceLocation("aas", "textures/gui/map_icons/marker_attack_bravo.png");
    private static final ResourceLocation TEX_DEFEND_BRAVO = new ResourceLocation("aas", "textures/gui/map_icons/marker_defend_bravo.png");
    private static final ResourceLocation TEX_BUILD_BRAVO  = new ResourceLocation("aas", "textures/gui/map_icons/marker_build_bravo.png");
    private static final ResourceLocation TEX_MOVE_BRAVO   = new ResourceLocation("aas", "textures/gui/map_icons/marker_move_bravo.png");

    // ---- Иконки: Charlie FTL ----
    private static final ResourceLocation TEX_EYE_CHARLIE    = new ResourceLocation("aas", "textures/gui/map_icons/ping_eye_charlie.png");
    private static final ResourceLocation TEX_ATTACK_CHARLIE = new ResourceLocation("aas", "textures/gui/map_icons/marker_attack_charlie.png");
    private static final ResourceLocation TEX_DEFEND_CHARLIE = new ResourceLocation("aas", "textures/gui/map_icons/marker_defend_charlie.png");
    private static final ResourceLocation TEX_BUILD_CHARLIE  = new ResourceLocation("aas", "textures/gui/map_icons/marker_build_charlie.png");
    private static final ResourceLocation TEX_MOVE_CHARLIE   = new ResourceLocation("aas", "textures/gui/map_icons/marker_move_charlie.png");

    private static final int SECTOR_COUNT = 5;
    private static final float SECTOR_DEG = 360f / SECTOR_COUNT;

    // ---- Геометрия ----
    private static final int BACKDROP_RADIUS = 130;   // ← было 150, уменьшили не сильно
    private static final int TRACK_OUTER      = 58;   // ← внешний край белого кольца — теперь БЛИЗКО к центру
    private static final int TRACK_INNER      = 50;   // ← внутренний край белого кольца (толщина 12px)
    private static final int DEAD_ZONE_RADIUS = 40;   // мёртвая зона / кружок "Отмена" — без изменений
    private static final int ICON_RADIUS      = 95;   // ← иконки теперь в свободном поясе МЕЖДУ кольцом и краем круга
    private static final int ICON_SIZE = 18;
    private static final float GAP_DEG = 6f;

    // ---- Цвета ----
    private static final int COLOR_BACKDROP   = 0x7A000000;
    private static final int COLOR_HOVER      = 0x80CCCCCC;
    private static final int COLOR_TRACK      = 0xFFFFFFFF;
    private static final int COLOR_TRACK_DIM  = 0xFFFFFFFF;
    private static final int COLOR_INNER_RING = 0xFFC2A278;
    private static final int COLOR_RIM        = 0x55FFFFFF; // ← сделал тише, теперь это лишь тонкий контур большого круга, а не второй трек

    // ---- Ключи локализации (см. lang-файлы aas: ru_ru.json / uk_ua.json / en_us.json) ----
    private static final String KEY_EYE    = "aas.radial.eye";
    private static final String KEY_ATTACK = "aas.radial.attack";
    private static final String KEY_BUILD  = "aas.radial.build";
    private static final String KEY_DEFEND = "aas.radial.defend";
    private static final String KEY_MOVE   = "aas.radial.move";
    private static final String KEY_CANCEL = "aas.radial.cancel"; // текст мёртвой зоны
    private static final String KEY_HINT   = "aas.radial.hint";   // подсказка снизу

    // Порядок секторов по часовой стрелке, начиная сверху (0°)
    private static final int[] SECTOR_TYPES = {
            PacketPlaceRadialMarker.TYPE_EYE,
            PacketPlaceRadialMarker.TYPE_ATTACK,
            PacketPlaceRadialMarker.TYPE_BUILD,
            PacketPlaceRadialMarker.TYPE_DEFEND,
            PacketPlaceRadialMarker.TYPE_MOVE
    };
    private static final String[] SECTOR_LABEL_KEYS = {KEY_EYE, KEY_ATTACK, KEY_BUILD, KEY_DEFEND, KEY_MOVE};

    /** 0 = SL, 1 = Bravo FTL, 2 = Charlie FTL. Определяет набор иконок. */
    private final int squadGroup;
    private final ResourceLocation[] sectorIcons;

    private boolean confirmed = false;

    public WorldMarkerRadialScreen() {
        this(0);
    }

    public WorldMarkerRadialScreen(int squadGroup) {
        super(Component.literal("Place Marker"));
        this.squadGroup = squadGroup;
        this.sectorIcons = buildIconSet(squadGroup);
    }

    private static ResourceLocation[] buildIconSet(int squadGroup) {
        if (squadGroup == 1) {
            return new ResourceLocation[]{TEX_EYE_BRAVO, TEX_ATTACK_BRAVO, TEX_BUILD_BRAVO, TEX_DEFEND_BRAVO, TEX_MOVE_BRAVO};
        } else if (squadGroup == 2) {
            return new ResourceLocation[]{TEX_EYE_CHARLIE, TEX_ATTACK_CHARLIE, TEX_BUILD_CHARLIE, TEX_DEFEND_CHARLIE, TEX_MOVE_CHARLIE};
        }
        return new ResourceLocation[]{TEX_EYE_SL, TEX_ATTACK_SL, TEX_BUILD_SL, TEX_DEFEND_SL, TEX_MOVE_SL};
    }

    @Override
    protected void init() {
        super.init();
        // Не захватываем фокус на виджетах — их здесь нет, управление курсором свободное.
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

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        int cx = width / 2;
        int cy = height / 2;
        int hovered = getHoveredSector(mouseX, mouseY);

        PoseStack pose = gui.pose();

        // 1. Затемнённая подложка на весь круг меню
        fillCircle(pose, cx, cy, BACKDROP_RADIUS, COLOR_BACKDROP, 64);

        // 2. Подсветка наведённого сектора: серый клин, растущий от края "Отмена" почти до края круга
        if (hovered >= 0) {
            float startDeg = hovered * SECTOR_DEG - SECTOR_DEG / 2f;
            float endDeg = startDeg + SECTOR_DEG;
            drawRingArc(pose, cx, cy, DEAD_ZONE_RADIUS, BACKDROP_RADIUS - 6, startDeg, endDeg, COLOR_HOVER, 16);
        }

        // 3. Белое сегментированное кольцо-трек — теперь ВНУТРИ, сразу за кружком "Отмена"
        for (int i = 0; i < SECTOR_COUNT; i++) {
            float startDeg = i * SECTOR_DEG - SECTOR_DEG / 2f + GAP_DEG / 2f;
            float endDeg   = i * SECTOR_DEG + SECTOR_DEG / 2f - GAP_DEG / 2f;
            int color = (i == hovered) ? COLOR_TRACK : COLOR_TRACK_DIM;
            drawRingArc(pose, cx, cy, TRACK_INNER, TRACK_OUTER, startDeg, endDeg, color, 24);
        }

        // 4. Иконки + подписи — по центру своего сектора, в поясе между треком и краем круга
        for (int i = 0; i < SECTOR_COUNT; i++) {
            float deg = i * SECTOR_DEG;
            double rad = Math.toRadians(deg);
            int ix = cx + (int) Math.round(Math.sin(rad) * ICON_RADIUS);
            int iy = cy - (int) Math.round(Math.cos(rad) * ICON_RADIUS);

            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            gui.blit(sectorIcons[i], ix - ICON_SIZE / 2, iy - ICON_SIZE / 2, ICON_SIZE, ICON_SIZE, 0f, 0f, 16, 16, 16, 16);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

            String label = I18n.get(SECTOR_LABEL_KEYS[i]);
            int labelColor = i == hovered ? 0xFFFFFFFF : 0xFFEEEEEE;
            gui.drawCenteredString(font, label, ix, iy + ICON_SIZE / 2 + 2, labelColor);
        }

        // 5. Тонкий контур большого круга (не трек, просто обводка)
        drawRing(pose, cx, cy, BACKDROP_RADIUS - 1, BACKDROP_RADIUS + 1, COLOR_RIM, 48);

        // 6. Тонкое кольцо мёртвой зоны в центре
        drawRing(pose, cx, cy, DEAD_ZONE_RADIUS - 1, DEAD_ZONE_RADIUS + 1, COLOR_INNER_RING, 48);

        // 7. Центральный текст
        String centerText = hovered >= 0 ? I18n.get(SECTOR_LABEL_KEYS[hovered]) : I18n.get(KEY_CANCEL);
        int centerColor = hovered >= 0 ? 0xFFFFFFFF : 0xFFCCCCCC;
        gui.drawCenteredString(font, centerText, cx, cy - 4, centerColor);

        // 8. Подсказка снизу
        String hint = I18n.get(KEY_HINT);
        gui.drawCenteredString(font, hint, cx, cy + BACKDROP_RADIUS + 10, 0xFFCCCCCC);
    }

    private void confirmSector(int hoveredIndex) {
        if (confirmed) return;
        if (hoveredIndex < 0 || hoveredIndex >= SECTOR_COUNT) {
            // Это была нейтральная зона в центре — просто закрываем без установки метки.
            closeScreen();
            return;
        }
        confirmed = true;
        int type = SECTOR_TYPES[hoveredIndex];
        PacketHandler.INSTANCE.sendToServer(new PacketPlaceRadialMarker(type));
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.MAP_MARKER_PLACE.get(), 1.0F));
        }
        closeScreen();
    }

    private void closeScreen() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(null);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int hovered = getHoveredSector((int) mouseX, (int) mouseY);
            confirmSector(hovered);
            return true;
        }
        if (button == 1) {
            // ПКМ — отмена без установки метки
            confirmed = true;
            closeScreen();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (ModKeyBindings.PLACE_PING_KEY.matchesMouse(button)) {
            int hovered = getHoveredSector((int) mouseX, (int) mouseY);
            confirmSector(hovered);
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (ModKeyBindings.PLACE_PING_KEY.matches(keyCode, scanCode)) {
            double mx = this.minecraft != null ? this.minecraft.mouseHandler.xpos() * width / this.minecraft.getWindow().getScreenWidth() : width / 2.0;
            double my = this.minecraft != null ? this.minecraft.mouseHandler.ypos() * height / this.minecraft.getWindow().getScreenHeight() : height / 2.0;
            int hovered = getHoveredSector((int) mx, (int) my);
            confirmSector(hovered);
            return true;
        }
        return super.keyReleased(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ================= Утилиты отрисовки (Tesselator, без текстуры) =================

    private static void beginColorDraw() {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();   // ← ДОБАВИТЬ: иначе fillCircle/fillWedge могут не рисоваться
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

    private static void fillWedge(PoseStack pose, float cx, float cy, float radius, float startDeg, float endDeg, int argb, int segments) {
        beginColorDraw();
        Matrix4f m = pose.last().pose();
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        float a = ((argb >>> 24) & 0xFF) / 255f, r = ((argb >> 16) & 0xFF) / 255f, g = ((argb >> 8) & 0xFF) / 255f, b = (argb & 0xFF) / 255f;
        buf.vertex(m, cx, cy, 0).color(r, g, b, a).endVertex();
        for (int i = 0; i <= segments; i++) {
            double ang = Math.toRadians(startDeg + (endDeg - startDeg) * i / segments);
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

    /** Кольцевая дуга (сегмент трека), ограниченная диапазоном углов [startDeg; endDeg]. */
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

    private static void drawSpoke(PoseStack pose, float cx, float cy, float innerR, float outerR, float deg, int argb) {
        beginColorDraw();
        Matrix4f m = pose.last().pose();
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.getBuilder();
        buf.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        float a = ((argb >>> 24) & 0xFF) / 255f, r = ((argb >> 16) & 0xFF) / 255f, g = ((argb >> 8) & 0xFF) / 255f, b = (argb & 0xFF) / 255f;
        double ang = Math.toRadians(deg);
        float sx = (float) Math.sin(ang), sy = (float) -Math.cos(ang);
        float px = -sy, py = sx;
        float halfW = 0.5f;
        buf.vertex(m, cx + sx * innerR + px * halfW, cy + sy * innerR + py * halfW, 0).color(r, g, b, a).endVertex();
        buf.vertex(m, cx + sx * innerR - px * halfW, cy + sy * innerR - py * halfW, 0).color(r, g, b, a).endVertex();
        buf.vertex(m, cx + sx * outerR + px * halfW, cy + sy * outerR + py * halfW, 0).color(r, g, b, a).endVertex();
        buf.vertex(m, cx + sx * outerR - px * halfW, cy + sy * outerR - py * halfW, 0).color(r, g, b, a).endVertex();
        tess.end();
    }
}