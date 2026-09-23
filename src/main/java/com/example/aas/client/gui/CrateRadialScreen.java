// PATH: src/main/java/com/example/aas/client/gui/CrateRadialScreen.java
package com.example.aas.client.gui;

import com.example.aas.client.ClientData;
import com.example.aas.config.AASConfig;
import com.example.aas.entity.SupplyCrateEntity;
import com.example.aas.network.PacketHandler;
import com.example.aas.network.PacketRequestCrateAmmo;
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
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;

/**
 * Радиальное меню Supply Crate (ящик снабжения).
 * Визуальный стиль полностью повторяет HubRadialScreen
 * (та же подложка, тот же трек, та же мёртвая зона с "Отмена",
 * тот же принцип подсветки наведённого сектора и серых недоступных/закулдауненных иконок).
 *
 * Индексы секторов (0..4) совпадают с HubRadialScreen:
 * 0=Патроны, 1=АГС-30, 2=М2, 3=Миномёт, 4=ПТРК
 */
public class CrateRadialScreen extends Screen {

    private static final ResourceLocation ICON_AMMO   = new ResourceLocation("aas", "textures/gui/hub_ammo.png");
    private static final ResourceLocation ICON_AGS    = new ResourceLocation("aas", "textures/gui/hub_ags.png");
    private static final ResourceLocation ICON_M2     = new ResourceLocation("aas", "textures/gui/hub_m2.png");
    private static final ResourceLocation ICON_MORTAR = new ResourceLocation("aas", "textures/gui/hub_mortar.png");
    private static final ResourceLocation ICON_TOW    = new ResourceLocation("aas", "textures/gui/hub_tow.png");

    private static final int SECTOR_COUNT = 5;
    private static final float SECTOR_DEG = 360f / SECTOR_COUNT;

    // ---- Геометрия (идентично HubRadialScreen) ----
    private static final int BACKDROP_RADIUS = 130;
    private static final int TRACK_OUTER = 56;
    private static final int TRACK_INNER = 50;
    private static final int DEAD_ZONE_RADIUS = 40;
    private static final int ICON_RADIUS = 95;
    private static final int ICON_SIZE = 20;
    private static final float GAP_DEG = 6f;

    // ---- Цвета (идентично HubRadialScreen) ----
    private static final int COLOR_BACKDROP   = 0x7A000000;
    private static final int COLOR_HOVER      = 0x80CCCCCC;
    private static final int COLOR_TRACK      = 0xFFFFFFFF;
    private static final int COLOR_TRACK_DIM  = 0xFFFFFFFF;
    private static final int COLOR_INNER_RING = 0xFFC2A278;
    private static final int COLOR_RIM        = 0x55FFFFFF;

    // ---- Ключи локализации ----
    private static final String KEY_AMMO      = "aas.hub.ammo";
    private static final String KEY_AGS       = "aas.hub.ags";
    private static final String KEY_M2        = "aas.hub.m2";
    private static final String KEY_MORTAR    = "aas.hub.mortar";
    private static final String KEY_TOW       = "aas.hub.tow";
    private static final String KEY_CANCEL    = "aas.radio.cancel";
    private static final String KEY_HINT      = "aas.radio.hint";
    private static final String KEY_WAIT      = "aas.radio.wait";

    // Порядок секторов: 0=Патроны, 1=АГС-30, 2=М2, 3=Миномёт, 4=ПТРК
    private static final ResourceLocation[] SECTOR_ICONS      = {ICON_AMMO, ICON_AGS, ICON_M2, ICON_MORTAR, ICON_TOW};
    private static final String[]           SECTOR_LABEL_KEYS = {KEY_AMMO, KEY_AGS, KEY_M2, KEY_MORTAR, KEY_TOW};

    private static final int SECTOR_AMMO = 0;

    private final int entityId;

    private int cdAmmo = 0;
    private int materials = 0;

    public CrateRadialScreen(int entityId) {
        super(Component.literal("Crate Supply"));
        this.entityId = entityId;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    private void updateData() {
        // === Вычисление клиентского кулдауна пополнения патронов ===
        long elapsed = System.currentTimeMillis() - ClientData.lastFobResupplyTime;
        if (elapsed < 60000 && !Minecraft.getInstance().player.isCreative()) {
            cdAmmo = (int) ((60000 - elapsed) / 50); // Перевод в тики (для визуала)
        } else {
            cdAmmo = 0;
        }

        if (Minecraft.getInstance().level != null) {
            Entity entity = Minecraft.getInstance().level.getEntity(entityId);
            if (entity instanceof SupplyCrateEntity crate) {
                this.materials = crate.getMaterials();
            } else {
                // Если ящик пропал, закрываем меню
                this.onClose();
            }
        }
    }

    private int getCost(int i) {
        return switch (i) {
            case 0 -> AASConfig.HUB_RESUPPLY_COST.get();
            case 1 -> 20;
            case 2 -> 15;
            case 3 -> 20;
            case 4 -> 50;
            default -> 0;
        };
    }

    private int getCooldown(int i) {
        return switch (i) {
            case 0 -> cdAmmo;
            default -> 0; // У ящика только патроны имеют кулдаун
        };
    }

    /**
     * Доступен ли сектор {@code i} прямо сейчас.
     */
    private boolean isAvailable(int i) {
        if (getCooldown(i) > 0) return false;
        if (Minecraft.getInstance().player != null && Minecraft.getInstance().player.isCreative()) return true;
        return materials >= getCost(i);
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
        updateData();

        int cx = width / 2;
        int cy = height / 2;
        int hovered = getHoveredSector(mouseX, mouseY);

        // Доступность считается один раз за кадр на сектор
        boolean[] available = new boolean[SECTOR_COUNT];
        for (int i = 0; i < SECTOR_COUNT; i++) available[i] = isAvailable(i);

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

        // 4. Иконки + подписи (серые, если недоступно; красные с таймером — если на КД)
        for (int i = 0; i < SECTOR_COUNT; i++) {
            float deg = i * SECTOR_DEG;
            double rad = Math.toRadians(deg);
            int ix = cx + (int) Math.round(Math.sin(rad) * ICON_RADIUS);
            int iy = cy - (int) Math.round(Math.cos(rad) * ICON_RADIUS);

            RenderSystem.enableBlend();
            float alpha = i == hovered ? 1f : 0.85f;
            if (available[i]) {
                RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
            } else {
                RenderSystem.setShaderColor(0.45f, 0.45f, 0.45f, alpha);
            }
            gui.blit(SECTOR_ICONS[i], ix - ICON_SIZE / 2, iy - ICON_SIZE / 2, ICON_SIZE, ICON_SIZE, 0f, 0f, 16, 16, 16, 16);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

            int cd = getCooldown(i);
            String label = cd > 0
                    ? String.format(I18n.get(KEY_WAIT), cd / 20)
                    : I18n.get(SECTOR_LABEL_KEYS[i]) + " (" + getCost(i) + ")";
            int labelColor;
            if (cd > 0) labelColor = 0xFFFF5555;
            else if (!available[i]) labelColor = 0xFFAAAAAA;
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

        // 9. Подсказка снизу
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
            this.onClose();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void handleSelect(int sector) {
        if (sector < 0) {
            this.onClose(); // мёртвая зона — закрыть без действия
            return;
        }

        // === ЛОГИКА КУЛДАУНА (патроны выставляют клиентский КД сразу) ===
        if (sector == SECTOR_AMMO) {
            if (cdAmmo > 0) return; // игнорируем клик, если ещё на КД

            if (materials >= AASConfig.HUB_RESUPPLY_COST.get() || Minecraft.getInstance().player.isCreative()) {
                ClientData.lastFobResupplyTime = System.currentTimeMillis();
            }
        }

        PacketHandler.INSTANCE.sendToServer(new PacketRequestCrateAmmo(entityId, sector));
        this.onClose();
    }

    // ================= Утилиты отрисовки (идентично HubRadialScreen) =================

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