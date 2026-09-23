// PATH: src/main/java/com/example/aas/client/gui/RadioRadialScreen.java
package com.example.aas.client.gui;

import com.example.aas.item.RallyItem;
import com.example.aas.network.PacketHandler;
import com.example.aas.network.PacketRadioAction;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/**
 * Радиальное меню рации: Rally Point / Defenses / Static Gun.
 * Визуальный стиль полностью повторяет WorldMarkerRadialScreen (тот же трек,
 * та же мёртвая зона с "Отмена", тот же принцип подсветки наведённого сектора).
 */
public class RadioRadialScreen extends Screen {

    private static final ResourceLocation ICON_RALLY      = new ResourceLocation("aas", "textures/gui/radio_rally.png");
    private static final ResourceLocation ICON_DEFEND     = new ResourceLocation("aas", "textures/gui/radio_defend.png");
    private static final ResourceLocation ICON_STATIC_GUN = new ResourceLocation("aas", "textures/gui/radio_static_gun.png");

    private static final int SECTOR_COUNT = 3;
    private static final float SECTOR_DEG = 360f / SECTOR_COUNT;

    // ---- Геометрия (тот же стиль, что и в WorldMarkerRadialScreen) ----
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
    private static final int COLOR_DISABLED   = 0xFFFF5555; // Rally на кулдауне

    // ---- Ключи локализации ----
    private static final String KEY_RALLY  = "aas.radio.rally";
    private static final String KEY_DEFEND = "aas.radio.defenses";
    private static final String KEY_STATIC = "aas.radio.staticgun";
    private static final String KEY_CANCEL = "aas.radio.cancel";
    private static final String KEY_HINT   = "aas.radio.hint";
    private static final String KEY_WAIT   = "aas.radio.wait"; // формат "%s" под кол-во секунд

    private static final ResourceLocation[] SECTOR_ICONS = {ICON_RALLY, ICON_DEFEND, ICON_STATIC_GUN};
    private static final String[] SECTOR_LABEL_KEYS = {KEY_RALLY, KEY_DEFEND, KEY_STATIC};

    private boolean isSwitching = false; // переходим в подменю (Defenses/Static Gun) — не проигрывать анимацию "close"

    private boolean rallyOnCooldown = false;
    private long rallySecondsLeft = 0;

    public RadioRadialScreen() {
        super(Component.literal("Radio Menu"));
    }

    @Override
    protected void init() {
        super.init();
        triggerRadioAnim("deploy"); // Достать рацию
    }

    @Override
    public void onClose() {
        if (!isSwitching) {
            triggerRadioAnim("close"); // Убрать рацию, только если закрываем совсем
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

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void updateRallyCooldown() {
        rallyOnCooldown = false;
        rallySecondsLeft = 0;

        Player player = Minecraft.getInstance().player;
        if (player == null) return;

        String pName = player.getScoreboardName();
        com.example.aas.world.AASWorldData.Squad mySquad = null;
        for (com.example.aas.world.AASWorldData.Squad s : com.example.aas.client.ClientData.clientSquads) {
            if (s.members.contains(pName)) { mySquad = s; break; }
        }

        if (mySquad != null) {
            long cooldownEnd = mySquad.nextRallyAvailableTick;
            long gameTime = player.level().getGameTime();
            if (gameTime < cooldownEnd && !player.isCreative()) {
                rallyOnCooldown = true;
                rallySecondsLeft = (cooldownEnd - gameTime) / 20;
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

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        updateRallyCooldown();

        int cx = width / 2;
        int cy = height / 2;
        int hovered = getHoveredSector(mouseX, mouseY);
        if (hovered == 0 && rallyOnCooldown) hovered = -1; // недоступный сектор не подсвечиваем как выбираемый

        PoseStack pose = gui.pose();

        // 1. Затемнённая подложка
        fillCircle(pose, cx, cy, BACKDROP_RADIUS, COLOR_BACKDROP, 64);

        // 2. Подсветка наведённого сектора (серый клин от края "Отмена" до края круга)
        if (hovered >= 0) {
            float startDeg = hovered * SECTOR_DEG - SECTOR_DEG / 2f;
            float endDeg = startDeg + SECTOR_DEG;
            drawRingArc(pose, cx, cy, DEAD_ZONE_RADIUS, BACKDROP_RADIUS - 6, startDeg, endDeg, COLOR_HOVER, 16);
        }

        // 3. Белое сегментированное кольцо-трек
        for (int i = 0; i < SECTOR_COUNT; i++) {
            float startDeg = i * SECTOR_DEG - SECTOR_DEG / 2f + GAP_DEG / 2f;
            float endDeg   = i * SECTOR_DEG + SECTOR_DEG / 2f - GAP_DEG / 2f;
            int color = (i == 0 && rallyOnCooldown) ? COLOR_DISABLED : ((i == hovered) ? COLOR_TRACK : COLOR_TRACK_DIM);
            drawRingArc(pose, cx, cy, TRACK_INNER, TRACK_OUTER, startDeg, endDeg, color, 24);
        }

        // 4. Иконки + подписи
        for (int i = 0; i < SECTOR_COUNT; i++) {
            float deg = i * SECTOR_DEG;
            double rad = Math.toRadians(deg);
            int ix = cx + (int) Math.round(Math.sin(rad) * ICON_RADIUS);
            int iy = cy - (int) Math.round(Math.cos(rad) * ICON_RADIUS);

            boolean disabled = (i == 0 && rallyOnCooldown);

            RenderSystem.enableBlend();
            if (disabled) {
                RenderSystem.setShaderColor(1f, 0.45f, 0.45f, 1f);
            } else {
                RenderSystem.setShaderColor(1f, 1f, 1f, i == hovered ? 1f : 0.85f);
            }
            gui.blit(SECTOR_ICONS[i], ix - ICON_SIZE / 2, iy - ICON_SIZE / 2, ICON_SIZE, ICON_SIZE, 0f, 0f, 16, 16, 16, 16);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

            String label;
            int labelColor;
            if (disabled) {
                label = I18n.get(KEY_WAIT, rallySecondsLeft);
                labelColor = COLOR_DISABLED;
            } else {
                label = I18n.get(SECTOR_LABEL_KEYS[i]);
                labelColor = i == hovered ? 0xFFFFFFFF : 0xFFEEEEEE;
            }
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

        if (sector == 0) { // Rally
            if (rallyOnCooldown) return; // недоступно, ничего не делаем
            PacketHandler.INSTANCE.sendToServer(new PacketRadioAction(0));
            this.onClose();
        } else if (sector == 1) { // Defenses — подменю
            this.isSwitching = true;
            Minecraft.getInstance().setScreen(new DefenseRadialScreen(this));
        } else if (sector == 2) { // Static Gun — подменю
            this.isSwitching = true;
            Minecraft.getInstance().setScreen(new StaticGunRadialScreen(this));
        }
    }

    // ================= Утилиты отрисовки (те же, что в WorldMarkerRadialScreen) =================

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