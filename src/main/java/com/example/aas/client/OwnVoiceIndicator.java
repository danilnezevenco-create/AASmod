// PATH: src\main\java\com\example\aas\client\OwnVoiceIndicator.java
package com.example.aas.client;

import com.example.aas.world.AASWorldData;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Индикатор «я говорю»: [иконка войса] [название канала] (без кружка — он только в списке других игроков).
 * Свой ник в списке говорящих слева по центру НЕ показывается — вместо него эта плашка:
 *  - в игре: внизу, правее панели отряда; если панели отряда нет — у самого левого края;
 *  - в меню смерти: правее кнопки возрождения;
 *  - в SquadSelectionScreen: у самого левого края.
 * Плашка рисуется на высоком слое (z = 600), чтобы её ничего не перекрывало.
 */
public final class OwnVoiceIndicator {

    /** сколько мс после последнего пакета считаем, что игрок ещё говорит (как в списке слева) */
    private static final long ACTIVE_MS = 350;

    // --- расположение в игре (SquadPanelOverlay: MARGIN = 6, PANEL_WIDTH = 150) ---
    private static final int HUD_MARGIN = 6;
    private static final int HUD_X_WITH_PANEL = HUD_MARGIN + 150 + 6; // правее панели отряда
    private static final int HUD_X_NO_PANEL = HUD_MARGIN;             // у левого края, если панели нет
    private static final int HUD_X_CHAT = HUD_MARGIN + 270 + 6;       // правее панели чата (AASChatScreen: PANEL_W = 270)
    private static final int HUD_BOTTOM_MARGIN = 6;

    /** панель отряда после сворачивания висит HOLD (3000) + FADE (500) мс и исчезает (см. SquadPanelOverlay) */
    private static final long PANEL_GONE_AFTER_MS = 3500;


    private static final ResourceLocation RADIO_ICON =
            new ResourceLocation("aas", "textures/gui/voice_icon_radio.png");
    private static final ResourceLocation KIT_NONE =
            new ResourceLocation("aas", "textures/gui/kits/none.png");

    private static final int COLOR_LOCAL   = 0xFF55AAFF;
    private static final int COLOR_SQUAD   = 0xFF55FF55;
    private static final int COLOR_COMMAND = 0xFFFFAA00;   // лидеры отрядов в командирском чате
    private static final int COLOR_CMD     = 0xFFFFFF55;   // ЦМД (командир команды) в командирском чате — жёлтый
    private static final int COLOR_RADIO   = 0xFFFFFF00;

    // плавный сдвиг плашки в игре
    private static float hudX = -1f;
    private static long panelCollapsedSince = -1;

    private OwnVoiceIndicator() {}

    // ------------------------------------------------------------------ утилиты

    /** ник локального игрока (так же, как ключ в *_SPEAKERS) */
    public static String selfName() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? null : mc.player.getScoreboardName();
    }

    public static boolean isSelf(String name) {
        String self = selfName();
        return self != null && self.equals(name);
    }

    /** сколько записей в карте принадлежит ДРУГИМ игрокам (для сдвига списка) */
    public static int countOthers(Map<String, Long> speakers) {
        String self = selfName();
        int n = speakers.size();
        if (self != null && speakers.containsKey(self)) n--;
        return n;
    }

    /** Иконка кита; если кита нет (null / пусто / Unassigned) — иконка none. */
    public static ResourceLocation kitIcon(String kitName) {
        if (kitName == null || kitName.isEmpty() || kitName.equalsIgnoreCase("Unassigned")) return KIT_NONE;
        return new ResourceLocation("aas", "textures/gui/kits/" + kitName.toLowerCase().replace(" ", "_") + ".png");
    }

    // ------------------------------------------------------------------ ЦМД и звук

    /** Игрок — ЦМД (лидер командирского отряда своей команды)? Союзники всегда из моей команды. */
    public static boolean isCommander(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || name == null) return false;
        boolean isBlue = mc.player.getTeam() != null && mc.player.getTeam().getName().toUpperCase().contains("BLUE");
        int cmdId = isBlue ? ClientData.blueCMDId : ClientData.redCMDId;
        if (cmdId == -1) return false;
        for (AASWorldData.Squad s : ClientData.clientSquads) {
            if (s.id == cmdId) return name.equals(s.leader);
        }
        return false;
    }

    /** Цвет игрока в командирском чате: ЦМД — жёлтый, остальные лидеры — золотой. */
    public static int commandColor(String name) {
        return isCommander(name) ? COLOR_CMD : COLOR_COMMAND;
    }

    /**
     * Вызывается при пакете активности командирского чата: «пип» рации, когда кто-то НАЧИНАЕТ говорить
     * (как у рации, см. ClientHooks.handleRadioVoiceActivity). Пищит и когда начинаешь говорить сам.
     *
     * @param prevTs метка времени предыдущей активности этого игрока (null, если не было)
     */
    public static void onCommandActivity(String speaker, Long prevTs, long now) {
        boolean isNew = prevTs == null || now - prevTs > 400;
        if (!isNew) return;
        Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(com.example.aas.sound.ModSounds.RADIO_BEEP.get(), 1.0F));
    }

    // ------------------------------------------------------------------ каналы

    private static final int NONE = 0, LOCAL = 1, SQUAD = 2, COMMAND = 3, RADIO = 4;

    private static int activeChannel(String self) {
        long now = System.currentTimeMillis();
        int best = NONE;
        long bestTs = 0;
        long t;
        if ((t = ts(ClientData.LOCAL_SPEAKERS, self)) > bestTs && now - t < ACTIVE_MS)   { best = LOCAL;   bestTs = t; }
        if ((t = ts(ClientData.SQUAD_SPEAKERS, self)) > bestTs && now - t < ACTIVE_MS)   { best = SQUAD;   bestTs = t; }
        if ((t = ts(ClientData.COMMAND_SPEAKERS, self)) > bestTs && now - t < ACTIVE_MS) { best = COMMAND; bestTs = t; }
        if ((t = ts(ClientData.RADIO_SPEAKERS, self)) > bestTs && now - t < ACTIVE_MS)   { best = RADIO;   bestTs = t; }
        return best;
    }

    private static long ts(Map<String, Long> map, String name) {
        Long v = map.get(name);
        return v == null ? 0L : v;
    }

    /** true, если я прямо сейчас говорю в каком-либо канале */
    public static boolean isSpeaking() {
        String self = selfName();
        return self != null && activeChannel(self) != NONE;
    }

    // ------------------------------------------------------------------ отряд

    private static AASWorldData.Squad findMySquad(String myName) {
        for (AASWorldData.Squad s : ClientData.clientSquads) {
            if (s.members.contains(myName)) return s;
        }
        return null;
    }

    /** тот же номер отряда, что в заголовке SquadPanelOverlay */
    private static int displaySquadNumber(Minecraft mc, AASWorldData.Squad mySquad) {
        String myTeam = mc.player.getTeam() != null ? mc.player.getTeam().getName().toUpperCase() : "NEUTRAL";
        String myDim = mc.level.dimension().location().toString();
        boolean isBlue = myTeam.contains("BLUE");
        int teamCMDId = isBlue ? ClientData.blueCMDId : ClientData.redCMDId;

        List<AASWorldData.Squad> teamSquads = new ArrayList<>();
        for (AASWorldData.Squad s : ClientData.clientSquads) {
            if (s.team.equalsIgnoreCase(myTeam) && s.dimension != null && s.dimension.equals(myDim)) {
                teamSquads.add(s);
            }
        }
        teamSquads.sort((s1, s2) -> {
            if (s1.id == teamCMDId && teamCMDId != -1) return -1;
            if (s2.id == teamCMDId && teamCMDId != -1) return 1;
            return Integer.compare(s1.id, s2.id);
        });
        for (int i = 0; i < teamSquads.size(); i++) {
            if (teamSquads.get(i).id == mySquad.id) return i + 1;
        }
        return mySquad.id;
    }

    /**
     * Номер отряда игрока (как в заголовке панели отряда) или null, если игрок не в отряде.
     * Нужен для кружка в списке говорящих (локальный и командирский каналы).
     */
    public static String squadNumberOf(String playerName) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || playerName == null) return null;
        AASWorldData.Squad sq = findMySquad(playerName);
        return sq == null ? null : String.valueOf(displaySquadNumber(mc, sq));
    }

    // ------------------------------------------------------------------ отрисовка

    /** HUD в игре: внизу; правее панели отряда, а если панели нет — у левого края */
    public static void renderHud(GuiGraphics gui, Minecraft mc, int screenWidth, int screenHeight) {
        if (mc.player == null || mc.level == null) return;
        // под этими экранами индикатор рисуют они сами
        if (mc.screen instanceof net.minecraft.client.gui.screens.DeathScreen
                || mc.screen instanceof com.example.aas.client.gui.SquadSelectionScreen) return;

        long now = System.currentTimeMillis();
        if (ClientData.squadPanelExpanded) panelCollapsedSince = -1;
        else if (panelCollapsedSince < 0) panelCollapsedSince = now;

        boolean panelVisible = findMySquad(mc.player.getScoreboardName()) != null
                && !(panelCollapsedSince > 0 && now - panelCollapsedSince > PANEL_GONE_AFTER_MS);

        // пока открыт AASChatScreen, панель чата стоит на месте панели отряда — уходим правее неё
        boolean chatOpen = mc.screen instanceof com.example.aas.client.gui.AASChatScreen;
        float target = chatOpen ? HUD_X_CHAT : (panelVisible ? HUD_X_WITH_PANEL : HUD_X_NO_PANEL);
        hudX = (hudX < 0) ? target : Mth.lerp(0.25f, hudX, target);
        if (Math.abs(hudX - target) < 0.5f) hudX = target;

        render(gui, mc.font, Math.round(hudX), screenHeight - HUD_BOTTOM_MARGIN - 10);
    }

    private static final int ROW_ACTIVE_MS = 500;
    private static final int LIST_BADGE_SIZE = 10;

    /**
     * Список ДРУГИХ говорящих игроков (рация, командирский, отряд, локальный) — для экранов-меню.
     * Только рисует (мыши не обрабатывает), на высоком слое z=600: лежит поверх меню и не мешает кликам.
     * (x, y) — левый верхний угол первой строки.
     */
    public static void renderOthers(GuiGraphics gui, Font font, int x, int y) {
        long now = System.currentTimeMillis();
        List<Object[]> rows = new ArrayList<>();   // {имя, цвет, префикс, кружок?, рация?}
        collect(rows, ClientData.RADIO_SPEAKERS, now, COLOR_RADIO, "", false, true, false);
        collect(rows, ClientData.COMMAND_SPEAKERS, now, COLOR_COMMAND, "\u2605 ", true, false, true);
        collect(rows, ClientData.SQUAD_SPEAKERS, now, COLOR_SQUAD, "", false, false, false);
        collect(rows, ClientData.LOCAL_SPEAKERS, now, COLOR_LOCAL, "", true, false, false);
        if (rows.isEmpty()) return;

        boolean anyBadge = false;
        for (Object[] r : rows) {
            if ((Boolean) r[3] && squadNumberOf((String) r[0]) != null) { anyBadge = true; break; }
        }
        int badgeW = anyBadge ? LIST_BADGE_SIZE + 3 : 0;
        final int KIT_SIZE = 9, KIT_X = 3, VOICE_X = KIT_X + KIT_SIZE + 2, NAME_X = VOICE_X + 8 + 3;

        gui.pose().pushPose();
        gui.pose().translate(0, 0, 600);
        for (Object[] r : rows) {
            String name = (String) r[0];
            int color = (Integer) r[1];
            String label = (String) r[2] + name;
            boolean radio = (Boolean) r[4];

            RenderSystem.enableBlend();
            if (radio) {
                gui.fill(x, y - 2, x + font.width(label) + 19, y + 10, 0x80000000);
                RenderSystem.setShaderColor(((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f, 1f);
                gui.blit(RADIO_ICON, x + 3, y, 0, 0, 8, 8, 8, 8);
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                gui.drawString(font, label, x + 14, y, color, false);
            } else {
                gui.fill(x, y - 2, x + badgeW + NAME_X + font.width(label) + 6, y + 10, 0x80000000);

                if (badgeW > 0 && (Boolean) r[3]) {
                    String num = squadNumberOf(name);
                    if (num != null) BadgeCircle.draw(gui, font, x + 2, y - 1, LIST_BADGE_SIZE, color, num);
                }

                // кит: если кита нет — место остаётся пустым (ники ровно)
                String kitName = ClientData.playerKits.getOrDefault(name, "Unassigned");
                if (kitName != null && !kitName.isEmpty() && !kitName.equalsIgnoreCase("Unassigned")) {
                    ResourceLocation kitIcon = new ResourceLocation("aas",
                            "textures/gui/kits/" + kitName.toLowerCase().replace(" ", "_") + ".png");
                    KitIconStyle.apply(color, 1.0f);
                    gui.blit(kitIcon, x + badgeW + KIT_X, y, KIT_SIZE, KIT_SIZE, 0f, 0f, 10, 10, 10, 10);
                    KitIconStyle.reset();
                }

                RenderSystem.setShaderColor(((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f, 1f);
                gui.blit(VoiceIcons.forPlayer(name), x + badgeW + VOICE_X, y, 0, 0, 8, 8, 8, 8);
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

                gui.drawString(font, label, x + badgeW + NAME_X, y, color, false);
            }
            y += 14;
        }
        gui.pose().popPose();
    }

    private static void collect(List<Object[]> rows, Map<String, Long> speakers, long now, int color,
                                String prefix, boolean badge, boolean radio, boolean command) {
        for (var e : new ArrayList<>(speakers.entrySet())) {
            if (isSelf(e.getKey()) || now - e.getValue() >= ROW_ACTIVE_MS) continue;
            rows.add(new Object[]{e.getKey(), command ? commandColor(e.getKey()) : color, prefix, badge, radio});
        }
    }

    public static boolean render(GuiGraphics gui, Font font, int x, int y) {
        return render(gui, font, x, y, Integer.MAX_VALUE);
    }

    /**
     * Рисует плашку. (x, y) — левый верхний угол текста/иконки; плашка занимает по высоте y-2 .. y+10.
     * Если плашка шире maxWidth — она пропорционально уменьшается.
     *
     * @return true, если что-то было нарисовано
     */
    public static boolean render(GuiGraphics gui, Font font, int x, int y, int maxWidth) {
        Minecraft mc = Minecraft.getInstance();
        String self = selfName();
        if (self == null || mc.level == null) return false;
        int ch = activeChannel(self);
        if (ch == NONE) return false;

        int color;
        String key;
        ResourceLocation icon;
        switch (ch) {
            case COMMAND -> { color = commandColor(self); key = "aas.voice.channel.command"; icon = VoiceIcons.forPlayer(self); }
            case SQUAD   -> { color = COLOR_SQUAD;   key = "aas.voice.channel.squad";   icon = VoiceIcons.forPlayer(self); }
            case RADIO   -> { color = COLOR_RADIO;   key = "aas.voice.channel.radio";   icon = RADIO_ICON; }
            default      -> { color = COLOR_LOCAL;   key = "aas.voice.channel.local";   icon = VoiceIcons.forPlayer(self); }
        }

        Component label = Component.translatable(key);
        int iconX = 3;
        int textX = iconX + 8 + 3;
        int width = textX + font.width(label) + 6;

        float scale = width > maxWidth ? Math.max(0.5f, maxWidth / (float) width) : 1f;

        gui.pose().pushPose();
        gui.pose().translate(x, y, 600);          // высокий слой: ничего не перекрывает
        if (scale != 1f) gui.pose().scale(scale, scale, 1f);

        gui.fill(0, -2, width, 10, 0x80000000);

        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f, 1f);
        gui.blit(icon, iconX, 0, 0, 0, 8, 8, 8, 8);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        gui.drawString(font, label, textX, 0, color, false);

        gui.pose().popPose();
        return true;
    }
}