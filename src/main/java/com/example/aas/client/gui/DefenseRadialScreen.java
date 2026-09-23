// PATH: src/main/java/com/example/aas/client/gui/DefenseRadialScreen.java
package com.example.aas.client.gui;

import com.example.aas.client.ClientPlacementHandler;
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
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;

/**
 * Р Р°РґРёР°Р»СЊРЅРѕРµ РјРµРЅСЋ РІС‹Р±РѕСЂР° РїРѕСЃС‚СЂРѕР№РєРё: HUB / РљРѕР»СЋС‡Р°СЏ РїСЂРѕРІРѕР»РѕРєР° / Р‘СѓРЅРєРµСЂ / Vehicle Station / РЎС‚РµРЅС‹.
 * Р’РёР·СѓР°Р»СЊРЅС‹Р№ СЃС‚РёР»СЊ РїРѕР»РЅРѕСЃС‚СЊСЋ РїРѕРІС‚РѕСЂСЏРµС‚ StaticGunRadialScreen (С‚РѕС‚ Р¶Рµ С‚СЂРµРє, С‚Р° Р¶Рµ РјС‘СЂС‚РІР°СЏ
 * Р·РѕРЅР° СЃ "РћС‚РјРµРЅР°", С‚РѕС‚ Р¶Рµ РїСЂРёРЅС†РёРї РїРѕРґСЃРІРµС‚РєРё РЅР°РІРµРґС‘РЅРЅРѕРіРѕ СЃРµРєС‚РѕСЂР° Рё СЃРµСЂС‹С… РЅРµРґРѕСЃС‚СѓРїРЅС‹С… РёРєРѕРЅРѕРє).
 *
 * РќРµРґРѕСЃС‚СѓРїРЅС‹Рµ РїРѕСЃС‚СЂРѕР№РєРё (РЅРµС‚ РјР°С‚РµСЂРёР°Р»РѕРІ СЂСЏРґРѕРј СЃ HUB/СЏС‰РёРєРѕРј, РёР»Рё РґР»СЏ HUB вЂ” СЃР»РёС€РєРѕРј Р±Р»РёР·РєРѕ
 * РґСЂСѓРіРѕРіРѕ СЃРІРѕРµРіРѕ HUB'Р° Р»РёР±Рѕ РїРѕ РєРѕРЅС„РёРіСѓ РЅРµ С…РІР°С‚Р°РµС‚ СЏС‰РёРєР° СЃРЅР°Р±Р¶РµРЅРёСЏ СЂСЏРґРѕРј) РѕС‚РѕР±СЂР°Р¶Р°СЋС‚СЃСЏ СЃРµСЂС‹РјРё,
 * РЅРѕ РєР»РёРє РїРѕ РЅРёРј РІСЃС‘ СЂР°РІРЅРѕ РѕР±СЂР°Р±Р°С‚С‹РІР°РµС‚СЃСЏ вЂ” С„РёРЅР°Р»СЊРЅСѓСЋ РїСЂРѕРІРµСЂРєСѓ Рё СЃРѕРѕР±С‰РµРЅРёРµ РѕР± РѕС€РёР±РєРµ РґРµР»Р°РµС‚
 * ClientPlacementHandler / СЃРµСЂРІРµСЂ (PacketRadioAction), РєР°Рє Рё СЂР°РЅСЊС€Рµ.
 */
public class DefenseRadialScreen extends Screen {

    private static final ResourceLocation ICON_HUB     = new ResourceLocation("aas", "textures/gui/build_hub.png");
    private static final ResourceLocation ICON_WIRE    = new ResourceLocation("aas", "textures/gui/build_wire.png");
    private static final ResourceLocation ICON_BUNKER  = new ResourceLocation("aas", "textures/gui/build_bunker.png");
    private static final ResourceLocation ICON_STATION = new ResourceLocation("aas", "textures/gui/build_vehicle_station.png");
    private static final ResourceLocation ICON_WALL    = new ResourceLocation("aas", "textures/gui/build_wall.png");
    private static final ResourceLocation ICON_BACK    = new ResourceLocation("aas", "textures/gui/back_button.png");

    private static final int SECTOR_COUNT = 6;
    private static final float SECTOR_DEG = 360f / SECTOR_COUNT;

    // ---- Р“РµРѕРјРµС‚СЂРёСЏ (С‚РѕС‚ Р¶Рµ СЃС‚РёР»СЊ, С‡С‚Рѕ Рё РІ StaticGunRadialScreen) ----
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

    // ---- РљР»СЋС‡Рё Р»РѕРєР°Р»РёР·Р°С†РёРё ----
    private static final String KEY_HUB     = "aas.build.hub";
    private static final String KEY_WIRE    = "aas.build.wire";
    private static final String KEY_BUNKER  = "aas.build.bunker";
    private static final String KEY_STATION = "aas.build.vehiclestation";
    private static final String KEY_WALL    = "aas.build.walls";
    private static final String KEY_CANCEL  = "aas.radio.cancel";
    private static final String KEY_HINT    = "aas.radio.hint";
    private static final String KEY_BACK    = "aas.radio.back";

    // РџРѕСЂСЏРґРѕРє СЃРµРєС‚РѕСЂРѕРІ: 0=Up(HUB), 1=Wire, 2=Bunker, 3=Vehicle Station, 4=Walls(РїРѕРґРјРµРЅСЋ), 5=Back
    private static final ResourceLocation[] SECTOR_ICONS      = {ICON_HUB, ICON_WIRE, ICON_BUNKER, ICON_STATION, ICON_WALL, ICON_BACK};
    private static final String[]           SECTOR_LABEL_KEYS = {KEY_HUB, KEY_WIRE, KEY_BUNKER, KEY_STATION, KEY_WALL, KEY_BACK};
    // Индекс сектора "Назад" — последний в колесе, всегда доступен (без проверки материалов).
    private static final int SECTOR_BACK = 5;
    // ID РїРѕСЃС‚СЂРѕРµРє РґР»СЏ ClientPlacementHandler (Wire=13, Bunker=17, Vehicle Station=18, 1x1 Wall=10 РєР°Рє Р±Р°Р·Р° РґР»СЏ РїСЂРѕРІРµСЂРєРё СЃС‚РѕРёРјРѕСЃС‚Рё СЃС‚РµРЅ)
    private static final int PLACEMENT_ID_WIRE    = 13;
    private static final int PLACEMENT_ID_BUNKER  = 17;
    private static final int PLACEMENT_ID_STATION = 18;
    private static final int PLACEMENT_ID_WALL    = 10;

    private final Screen parentScreen;
    private boolean isSwitching = false;

    public DefenseRadialScreen(Screen parent) {
        super(Component.literal("Defense Menu"));
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

    /**
     * РњРѕР¶РµС‚ Р»Рё РёРіСЂРѕРє СЃРµР№С‡Р°СЃ РїРѕСЃС‚СЂРѕРёС‚СЊ РїРѕСЃС‚СЂРѕР№РєСѓ РІ СЃРµРєС‚РѕСЂРµ {@code i} (РёСЃРїРѕР»СЊР·СѓРµС‚СЃСЏ С‚РѕР»СЊРєРѕ
     * РґР»СЏ РїРѕРґСЃРІРµС‚РєРё РёРєРѕРЅРєРё Р±РµР»С‹Рј/СЃРµСЂС‹Рј вЂ” РѕРєРѕРЅС‡Р°С‚РµР»СЊРЅСѓСЋ РїСЂРѕРІРµСЂРєСѓ РІСЃС‘ СЂР°РІРЅРѕ РґРµР»Р°РµС‚
     * ClientPlacementHandler/СЃРµСЂРІРµСЂ РїСЂРё СЂРµР°Р»СЊРЅРѕР№ РїРѕРїС‹С‚РєРµ РїРѕСЃС‚СЂРѕР№РєРё).
     */
    private boolean canBuildSector(int i) {
        return switch (i) {
            case 0 -> ClientPlacementHandler.canBuildHub();               // HUB
            case 1 -> ClientPlacementHandler.canAfford(PLACEMENT_ID_WIRE);    // Barbed Wire
            case 2 -> ClientPlacementHandler.canAfford(PLACEMENT_ID_BUNKER);  // Bunker
            case 3 -> ClientPlacementHandler.canAfford(PLACEMENT_ID_STATION); // Vehicle Station
            case 4 -> ClientPlacementHandler.canAfford(PLACEMENT_ID_WALL);    // Walls (СЃР°РјС‹Р№ РґРµС€С‘РІС‹Р№ РІР°СЂРёР°РЅС‚ 1x1)
            case 5 -> true; // Back — всегда доступен
            default -> true;
        };
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        int cx = width / 2;
        int cy = height / 2;
        int hovered = getHoveredSector(mouseX, mouseY);

        // Р”РѕСЃС‚СѓРїРЅРѕСЃС‚СЊ СЃС‡РёС‚Р°РµС‚СЃСЏ РѕРґРёРЅ СЂР°Р· Р·Р° РєР°РґСЂ РЅР° СЃРµРєС‚РѕСЂ, РЅРµ РїРµСЂРµСЃС‡РёС‚С‹РІР°РµРј Р»РёС€РЅРёР№ СЂР°Р·.
        boolean[] canBuild = new boolean[SECTOR_COUNT];
        for (int i = 0; i < SECTOR_COUNT; i++) canBuild[i] = canBuildSector(i);

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

        // 4. РРєРѕРЅРєРё + РїРѕРґРїРёСЃРё (СЃРµСЂС‹Рµ, РµСЃР»Рё РїРѕСЃС‚СЂРѕР№РєСѓ СЃРµР№С‡Р°СЃ СЂР°Р·РјРµСЃС‚РёС‚СЊ РЅРµ РїРѕР»СѓС‡РёС‚СЃСЏ)
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

        // 5. РўРѕРЅРєРёР№ РєРѕРЅС‚СѓСЂ Р±РѕР»СЊС€РѕРіРѕ РєСЂСѓРіР°
        drawRing(pose, cx, cy, BACKDROP_RADIUS - 1, BACKDROP_RADIUS + 1, COLOR_RIM, 48);

        // 6. РўРѕРЅРєРѕРµ РєРѕР»СЊС†Рѕ РјС‘СЂС‚РІРѕР№ Р·РѕРЅС‹
        drawRing(pose, cx, cy, DEAD_ZONE_RADIUS - 1, DEAD_ZONE_RADIUS + 1, COLOR_INNER_RING, 48);

        // 7. Р¦РµРЅС‚СЂР°Р»СЊРЅС‹Р№ С‚РµРєСЃС‚
        String centerText = hovered >= 0 ? I18n.get(SECTOR_LABEL_KEYS[hovered]) : I18n.get(KEY_CANCEL);
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

        switch (sector) {
            case 0 -> { // HUB вЂ” СЃС‚Р°РІРёС‚СЃСЏ РјРіРЅРѕРІРµРЅРЅРѕ РЅР° РїРѕР·РёС†РёРё РёРіСЂРѕРєР°, Р±РµР· РїСЂРёС†РµР»РёРІР°РЅРёСЏ
                PacketHandler.INSTANCE.sendToServer(new PacketRadioAction(14));
                this.onClose();
            }
            case 1 -> { // Barbed Wire
                ClientPlacementHandler.startPlacing(PLACEMENT_ID_WIRE);
                this.onClose();
            }
            case 2 -> { // Bunker
                ClientPlacementHandler.startPlacing(PLACEMENT_ID_BUNKER);
                this.onClose();
            }
            case 3 -> { // Vehicle Station
                ClientPlacementHandler.startPlacing(PLACEMENT_ID_STATION);
                this.onClose();
            }
            case 4 -> { // Walls вЂ” РїРѕРґРјРµРЅСЋ
                this.isSwitching = true;
                Minecraft.getInstance().setScreen(new WallRadialScreen(this));
            }
            case 5 -> { // Back — назад на предыдущий экран
                Minecraft.getInstance().setScreen(parentScreen);
            }
        }
    }

    // ================= РЈС‚РёР»РёС‚С‹ РѕС‚СЂРёСЃРѕРІРєРё (С‚Рµ Р¶Рµ, С‡С‚Рѕ Рё РІ StaticGunRadialScreen) =================

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