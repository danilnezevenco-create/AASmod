// PATH: src/main/java/com/example/aas/client/gui/StaticGunRadialScreen.java
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
 * Р Р°РґРёР°Р»СЊРЅРѕРµ РјРµРЅСЋ РІС‹Р±РѕСЂР° Р±РѕРµРІРѕРіРѕ СЃРѕРѕСЂСѓР¶РµРЅРёСЏ: TOW / M2 Browning / Mortar / AGS-30.
 * Р’РёР·СѓР°Р»СЊРЅС‹Р№ СЃС‚РёР»СЊ РїРѕР»РЅРѕСЃС‚СЊСЋ РїРѕРІС‚РѕСЂСЏРµС‚ RadioRadialScreen (С‚РѕС‚ Р¶Рµ С‚СЂРµРє, С‚Р° Р¶Рµ РјС‘СЂС‚РІР°СЏ
 * Р·РѕРЅР° СЃ "РћС‚РјРµРЅР°", С‚РѕС‚ Р¶Рµ РїСЂРёРЅС†РёРї РїРѕРґСЃРІРµС‚РєРё РЅР°РІРµРґС‘РЅРЅРѕРіРѕ СЃРµРєС‚РѕСЂР°).
 * РќР°Р·РІР°РЅРёСЏ РѕСЂСѓР¶РёСЏ РЅРµ Р»РѕРєР°Р»РёР·СѓСЋС‚СЃСЏ вЂ” РІС‹РІРѕРґСЏС‚СЃСЏ РЅР° Р°РЅРіР»РёР№СЃРєРѕРј.
 */
public class StaticGunRadialScreen extends Screen {

    private static final ResourceLocation ICON_TOW    = new ResourceLocation("aas", "textures/gui/static_gun_tow.png");
    private static final ResourceLocation ICON_M2      = new ResourceLocation("aas", "textures/gui/static_gun_m2.png");
    private static final ResourceLocation ICON_MORTAR = new ResourceLocation("aas", "textures/gui/static_gun_mortar.png");
    private static final ResourceLocation ICON_AGS    = new ResourceLocation("aas", "textures/gui/static_gun_ags.png");
    private static final ResourceLocation ICON_BACK   = new ResourceLocation("aas", "textures/gui/back_button.png");

    private static final int SECTOR_COUNT = 5;
    private static final float SECTOR_DEG = 360f / SECTOR_COUNT;

    // ---- Р“РµРѕРјРµС‚СЂРёСЏ (С‚РѕС‚ Р¶Рµ СЃС‚РёР»СЊ, С‡С‚Рѕ Рё РІ RadioRadialScreen) ----
    private static final int BACKDROP_RADIUS = 130;
    private static final int TRACK_OUTER = 56;
    private static final int TRACK_INNER = 50;
    private static final int DEAD_ZONE_RADIUS = 40;
    private static final int ICON_RADIUS = 95;
    private static final int ICON_SIZE = 20;
    private static final float GAP_DEG = 6f;

    // ---- Р¦РІРµС‚Р° ----
    private static final int COLOR_BACKDROP   = 0x7A000000;
    private static final int COLOR_HOVER      = 0x80CCCCCC;
    private static final int COLOR_TRACK      = 0xFFFFFFFF;
    private static final int COLOR_TRACK_DIM  = 0xFFFFFFFF;
    private static final int COLOR_INNER_RING = 0xFFC2A278;
    private static final int COLOR_RIM        = 0x55FFFFFF;

    // ---- РљР»СЋС‡Рё Р»РѕРєР°Р»РёР·Р°С†РёРё С‚РѕР»СЊРєРѕ РґР»СЏ СЃР»СѓР¶РµР±РЅС‹С… РЅР°РґРїРёСЃРµР№ ----
    private static final String KEY_CANCEL = "aas.radio.cancel";
    private static final String KEY_HINT   = "aas.radio.hint";
    private static final String KEY_BACK   = "aas.radio.back";

    // РџРѕСЂСЏРґРѕРє СЃРµРєС‚РѕСЂРѕРІ СЃРѕРІРїР°РґР°РµС‚ СЃРѕ СЃС‚Р°СЂРѕР№ РІРµСЂСЃРёРµР№ РјРµРЅСЋ: 0=Up(TOW), 1=Right(M2), 2=Down(Mortar), 3=Left(AGS), 4=Back
    private static final ResourceLocation[] SECTOR_ICONS  = {ICON_TOW, ICON_M2, ICON_MORTAR, ICON_AGS, ICON_BACK};
    // Названия оружия принципиально не локализуются (см. комментарий класса), поэтому для сектора
    // "Назад" подпись достаётся через I18n отдельно (см. render/handleSelect) — здесь просто заглушка.
    private static final String[]           SECTOR_LABELS = {"TOW", "M2 BROWNING", "MORTAR", "AGS-30", null};
    private static final int[]              SECTOR_PLACEMENT_IDS = {23, 20, 22, 21, -1}; // TOW, M2, Mortar, AGS, Back(не используется)
    private static final int SECTOR_BACK = 4;

    private final Screen parentScreen;
    private boolean isSwitching = false;

    public StaticGunRadialScreen(Screen parent) {
        super(Component.literal("Static Guns"));
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

        double angle = Math.toDegrees(Math.atan2(dx, -dy)); // 0 = РІРІРµСЂС…, РїРѕ С‡Р°СЃРѕРІРѕР№
        if (angle < 0) angle += 360;
        double shifted = (angle + SECTOR_DEG / 2.0) % 360;
        return (int) Math.floor(shifted / SECTOR_DEG) % SECTOR_COUNT;
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        int cx = width / 2;
        int cy = height / 2;
        int hovered = getHoveredSector(mouseX, mouseY);

        // Р”РѕСЃС‚СѓРїРЅРѕСЃС‚СЊ РјР°С‚РµСЂРёР°Р»РѕРІ РїСЂРѕРІРµСЂСЏРµС‚СЃСЏ РѕС‚ РўР•РљРЈР©Р•Р™ РїРѕР·РёС†РёРё РёРіСЂРѕРєР° РѕРґРёРЅ СЂР°Р· Р·Р° РєР°РґСЂ
        // (Р° РЅРµ 4 СЂР°Р·Р° РЅР° РёРєРѕРЅРєСѓ) вЂ” СЌРєРѕРЅРѕРјРёРј РЅР° РїРµСЂРµР±РѕСЂРµ HUB/СЏС‰РёРєРѕРІ.
        boolean creative = this.minecraft.player != null && this.minecraft.player.isCreative();
        int availableMaterials = ClientPlacementHandler.getAvailableMaterials();

        PoseStack pose = gui.pose();

        // 1. Р—Р°С‚РµРјРЅС‘РЅРЅР°СЏ РїРѕРґР»РѕР¶РєР°
        fillCircle(pose, cx, cy, BACKDROP_RADIUS, COLOR_BACKDROP, 64);

        // 2. РџРѕРґСЃРІРµС‚РєР° РЅР°РІРµРґС‘РЅРЅРѕРіРѕ СЃРµРєС‚РѕСЂР°
        if (hovered >= 0) {
            float startDeg = hovered * SECTOR_DEG - SECTOR_DEG / 2f;
            float endDeg = startDeg + SECTOR_DEG;
            drawRingArc(pose, cx, cy, DEAD_ZONE_RADIUS, BACKDROP_RADIUS - 6, startDeg, endDeg, COLOR_HOVER, 16);
        }

        // 3. Р‘РµР»РѕРµ СЃРµРіРјРµРЅС‚РёСЂРѕРІР°РЅРЅРѕРµ РєРѕР»СЊС†Рѕ-С‚СЂРµРє
        for (int i = 0; i < SECTOR_COUNT; i++) {
            float startDeg = i * SECTOR_DEG - SECTOR_DEG / 2f + GAP_DEG / 2f;
            float endDeg   = i * SECTOR_DEG + SECTOR_DEG / 2f - GAP_DEG / 2f;
            int color = (i == hovered) ? COLOR_TRACK : COLOR_TRACK_DIM;
            drawRingArc(pose, cx, cy, TRACK_INNER, TRACK_OUTER, startDeg, endDeg, color, 24);
        }

        // 4. РРєРѕРЅРєРё + РїРѕРґРїРёСЃРё (Р±РµР· РїРµСЂРµРІРѕРґР°, РІСЃРµРіРґР° РЅР° Р°РЅРіР»РёР№СЃРєРѕРј)
        for (int i = 0; i < SECTOR_COUNT; i++) {
            float deg = i * SECTOR_DEG;
            double rad = Math.toRadians(deg);
            int ix = cx + (int) Math.round(Math.sin(rad) * ICON_RADIUS);
            int iy = cy - (int) Math.round(Math.cos(rad) * ICON_RADIUS);

            // Р‘РµР»Р°СЏ вЂ” РјРѕР¶РЅРѕ РїРѕСЃС‚СЂРѕРёС‚СЊ (РІ СЂР°РґРёСѓСЃРµ HUB/СЏС‰РёРєР° Рё С…РІР°С‚Р°РµС‚ РјР°С‚РµСЂРёР°Р»РѕРІ).
            // РЎРµСЂР°СЏ вЂ” РЅРµР»СЊР·СЏ (РЅРµ С…РІР°С‚Р°РµС‚ РјР°С‚РµСЂРёР°Р»РѕРІ РР›Р РёРіСЂРѕРє РІРЅРµ СЂР°РґРёСѓСЃР°) вЂ” РЅРѕ РєР»РёРєР°РµС‚СЃСЏ РІСЃС‘ СЂР°РІРЅРѕ,
            // РѕРєРѕРЅС‡Р°С‚РµР»СЊРЅСѓСЋ РїСЂРѕРІРµСЂРєСѓ Рё СЃРѕРѕР±С‰РµРЅРёРµ "Not enough materials" РІСЃС‘ СЂР°РІРЅРѕ РґРµР»Р°РµС‚ ClientPlacementHandler.
            boolean canBuild = (i == SECTOR_BACK) || creative
                    || (availableMaterials >= 0 && availableMaterials >= ClientPlacementHandler.getCost(SECTOR_PLACEMENT_IDS[i]));

            RenderSystem.enableBlend();
            float alpha = i == hovered ? 1f : 0.85f;
            if (canBuild) {
                RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
            } else {
                RenderSystem.setShaderColor(0.45f, 0.45f, 0.45f, alpha);
            }
            gui.blit(SECTOR_ICONS[i], ix - ICON_SIZE / 2, iy - ICON_SIZE / 2, ICON_SIZE, ICON_SIZE, 0f, 0f, 16, 16, 16, 16);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

            String label = (i == SECTOR_BACK) ? I18n.get(KEY_BACK) : SECTOR_LABELS[i];
            int labelColor;
            if (!canBuild) labelColor = 0xFFAAAAAA;
            else labelColor = i == hovered ? 0xFFFFFFFF : 0xFFEEEEEE;
            gui.drawCenteredString(font, label, ix, iy + ICON_SIZE / 2 + 4, labelColor);
        }

        // 5. РўРѕРЅРєРёР№ РєРѕРЅС‚СѓСЂ Р±РѕР»СЊС€РѕРіРѕ РєСЂСѓРіР°
        drawRing(pose, cx, cy, BACKDROP_RADIUS - 1, BACKDROP_RADIUS + 1, COLOR_RIM, 48);

        // 6. РўРѕРЅРєРѕРµ РєРѕР»СЊС†Рѕ РјС‘СЂС‚РІРѕР№ Р·РѕРЅС‹
        drawRing(pose, cx, cy, DEAD_ZONE_RADIUS - 1, DEAD_ZONE_RADIUS + 1, COLOR_INNER_RING, 48);

        // 7. Р¦РµРЅС‚СЂР°Р»СЊРЅС‹Р№ С‚РµРєСЃС‚ (РЅР°Р·РІР°РЅРёРµ РѕСЂСѓР¶РёСЏ вЂ” РЅР° Р°РЅРіР»РёР№СЃРєРѕРј, "Cancel" вЂ” Р»РѕРєР°Р»РёР·СѓРµС‚СЃСЏ)
        String centerText = hovered < 0 ? I18n.get(KEY_CANCEL)
                : (hovered == SECTOR_BACK ? I18n.get(KEY_BACK) : SECTOR_LABELS[hovered]);
        int centerColor = hovered >= 0 ? 0xFFFFFFFF : 0xFFCCCCCC;
        gui.drawCenteredString(font, centerText, cx, cy - 4, centerColor);

        // 8. РџРѕРґСЃРєР°Р·РєР° СЃРЅРёР·Сѓ
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
            this.onClose(); // РјС‘СЂС‚РІР°СЏ Р·РѕРЅР° вЂ” Р·Р°РєСЂС‹С‚СЊ Р±РµР· РґРµР№СЃС‚РІРёСЏ
            return;
        }
        if (sector == SECTOR_BACK) { // Back — назад на предыдущий экран
            Minecraft.getInstance().setScreen(parentScreen);
            return;
        }
        ClientPlacementHandler.startPlacing(SECTOR_PLACEMENT_IDS[sector]);
        this.onClose();
    }

    // ================= РЈС‚РёР»РёС‚С‹ РѕС‚СЂРёСЃРѕРІРєРё (С‚Рµ Р¶Рµ, С‡С‚Рѕ Рё РІ RadioRadialScreen) =================

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