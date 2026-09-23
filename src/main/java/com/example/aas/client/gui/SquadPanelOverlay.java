// PATH: src/main/java/com/example/aas/client/SquadPanelOverlay.java
package com.example.aas.client;

import com.example.aas.network.MapPlayerInfo;
import com.example.aas.world.AASWorldData;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Панель отряда в левом нижнем углу экрана (как на референс-скриншоте).
 *
 * Требования, которые реализует этот класс:
 * 1) Расположение: нижний левый угол экрана.
 * 2) Слой: рендерится в RenderGuiOverlayEvent.Pre для CHAT_PANEL, то есть
 *    ДО отрисовки чата -> визуально панель лежит слоем НИЖЕ чата.
 * 3) Полупрозрачная чёрная подложка без обводки.
 * 4) Показывает: круглый значок с номером отряда (та же текстура player_circle.png,
 *    что и в AASDeathScreen/SquadSelectionScreen/StatisticsScreen) + название отряда,
 *    ники игроков, их файртим (цветная полоска слева, только у файртимов, слитая
 *    в единый блок; у основного состава полоски нет), иконку кита игрока (вместо ●/◆),
 *    статус-иконку (heartbeat, статично — если в ноке; иконку смерти — если игрок
 *    мёртв; ничего — если с игроком всё в порядке), иконку микрофона (загорается
 *    только когда игрок говорит, иначе полностью не видна).
 * 5) Сворачивается/разворачивается по клавише Y (перебиндовывается в настройках
 *    управления, см. ModKeyBindings.TOGGLE_SQUAD_PANEL_KEY). Название клавиши в
 *    подсказке всегда отображается латиницей, независимо от языка клиента.
 * 6) Если панель свёрнута (список скрыт) дольше 3 секунд — она плавно полностью
 *    исчезает (включая заголовок), а не просто остаётся в виде одной строки.
 */
@Mod.EventBusSubscriber(modid = "aas", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SquadPanelOverlay {

    // === Иконки ===
    // Микрофон переиспользует уже существующую текстуру индикатора голоса отряда.
    private static final ResourceLocation ICON_MIC       = new ResourceLocation("aas", "textures/gui/voice_icon.png");
    // Реальные пути иконок статуса (те же, что используются в DownedScreen / StatisticsScreen).
    private static final ResourceLocation ICON_HEARTBEAT = new ResourceLocation("aas", "textures/gui/heartbeat.png");
    private static final ResourceLocation ICON_DEAD      = new ResourceLocation("aas", "textures/gui/stats/deaths.png");
    private static final ResourceLocation ICON_DISCONNECT = new ResourceLocation("aas", "textures/gui/disconnect.png");
    // Тот же круглый значок, что используется для номера отряда в AASDeathScreen / SquadSelectionScreen / StatisticsScreen.
    private static final ResourceLocation CIRCLE_BADGE    = new ResourceLocation("aas", "textures/gui/map_icons/player_circle.png");

    // === Цвета ===
    private static final int BG_COLOR      = 0x99000000; // полупрозрачная чёрная подложка, без обводки
    private static final int COLOR_ALPHA   = 0xFF6EDB6E; // зелёный   — основной состав/СЛ (цвет заглушки иконки кита)
    private static final int COLOR_BRAVO   = 0xFFC974E0; // фиолетовый — файртим Bravo
    private static final int COLOR_CHARLIE = 0xFF57D8D8; // бирюзовый  — файртим Charlie
    private static final int COLOR_BADGE   = 0xFF00FF00; // ярко-зелёный — кружок номера "своего" отряда (как в AASDeathScreen)
    private static final int TEXT_COLOR    = 0xFFEDEDED;
    private static final int TEXT_COLOR_ME = 0xFFFFE066;
    private static final int TITLE_COLOR   = 0xFFFFD700;
    private static final int HINT_COLOR    = 0xFFAAAAAA;

    // === Геометрия ===
    private static final int MARGIN      = 6;   // отступ от края экрана
    private static final int PANEL_WIDTH = 150;
    private static final int HEADER_H    = 16;
    private static final int ROW_H       = 14;
    private static final int STRIPE_W    = 3;
    private static final int PAD_X       = 6;
    private static final int BADGE_SIZE  = 12;  // размер кружка с номером отряда

    // === Тайминги исчезновения после сворачивания ===
    private static final long HOLD_MS = 3000; // сколько панель стоит видимой в свёрнутом виде
    private static final long FADE_MS = 500;  // длительность плавного затухания после HOLD_MS

    private static float animProgress = 1.0f; // 0 = свёрнуто (только заголовок), 1 = развёрнуто
    private static long collapsedSinceMs = -1; // -1 = сейчас развёрнуто / таймер не запущен

    // --- Обработка клавиши сворачивания/разворачивания ---
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // IN_GAME контекст клавиши сам гарантирует, что это не сработает при открытом чате/экране.
        while (ModKeyBindings.TOGGLE_SQUAD_PANEL_KEY.consumeClick()) {
            ClientData.squadPanelExpanded = !ClientData.squadPanelExpanded;
        }
    }

    // --- Рендер: Pre для чата => панель рисуется РАНЬШЕ чата => чат ложится ПОВЕРХ неё ---
    @SubscribeEvent
    public static void onRenderOverlayPre(RenderGuiOverlayEvent.Pre event) {
        if (event.getOverlay() != VanillaGuiOverlay.CHAT_PANEL.type()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        if (mc.options.hideGui) return;

        AASWorldData.Squad mySquad = findMySquad(mc.player.getScoreboardName());
        if (mySquad == null) {
            // Не в отряде — плавно "схлопываем" анимацию, чтобы не было рывка при следующем входе в отряд.
            animProgress = 1.0f;
            collapsedSinceMs = -1;
            return;
        }

        render(event.getGuiGraphics(), mc, mySquad);
    }

    private static AASWorldData.Squad findMySquad(String myName) {
        for (AASWorldData.Squad s : ClientData.clientSquads) {
            if (s.members.contains(myName)) return s;
        }
        return null;
    }
    private static int computeDisplaySquadNumber(Minecraft mc, AASWorldData.Squad mySquad) {
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
        return mySquad.id; // fallback
    }
    private static void render(GuiGraphics gui, Minecraft mc, AASWorldData.Squad squad) {
        List<String> sortedMembers = getSortedMembers(squad);

        int expandedHeight  = HEADER_H + sortedMembers.size() * ROW_H + 4;
        int collapsedHeight = HEADER_H;

        float target = ClientData.squadPanelExpanded ? 1.0f : 0.0f;
        animProgress = Mth.lerp(0.2f, animProgress, target);
        if (Math.abs(animProgress - target) < 0.01f) animProgress = target;

        int panelHeight = Math.round(Mth.lerp(animProgress, collapsedHeight, expandedHeight));

        // --- Таймер полного исчезновения: считаем время с момента, когда панель свернули ---
        long now = System.currentTimeMillis();
        if (ClientData.squadPanelExpanded) {
            collapsedSinceMs = -1;
        } else if (collapsedSinceMs < 0) {
            collapsedSinceMs = now;
        }

        float panelAlpha = 1.0f;
        if (collapsedSinceMs > 0) {
            long elapsed = now - collapsedSinceMs;
            if (elapsed > HOLD_MS) {
                float fadeProgress = Math.min(1f, (elapsed - HOLD_MS) / (float) FADE_MS);
                panelAlpha = 1.0f - fadeProgress;
            }
        }

        if (panelAlpha <= 0f) return; // прошло 3с + время затухания — панель полностью скрыта, не рисуем вообще

        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int x = MARGIN;
        int y = screenHeight - MARGIN - panelHeight;

        // 1. Полупрозрачная чёрная подложка, без обводки/рамки.
        gui.fill(x, y, x + PANEL_WIDTH, y + panelHeight, withAlpha(BG_COLOR, panelAlpha));

        // 2. Заголовок: круглый значок с номером отряда + название + подсказка по клавише сворачивания
        int badgeX = x + PAD_X;
        int badgeY = y + (HEADER_H - BADGE_SIZE) / 2;
        int displayNumber = computeDisplaySquadNumber(mc, squad);
        drawBadgeCircle(gui, mc.font, badgeX, badgeY, BADGE_SIZE, withAlpha(COLOR_BADGE, panelAlpha), String.valueOf(displayNumber), panelAlpha);

        String title = (squad.name != null && !squad.name.isEmpty()) ? squad.name : ("Squad " + displayNumber);
        int titleX = badgeX + BADGE_SIZE + 4;
        int titleY = y + (HEADER_H - mc.font.lineHeight) / 2 + 1;
        gui.drawString(mc.font, title, titleX, titleY, withAlpha(TITLE_COLOR, panelAlpha), false);

        String hint = "[" + getKeyName() + "]";
        gui.pose().pushPose();
        gui.pose().scale(0.7f, 0.7f, 1f);
        int hintW = mc.font.width(hint);
        int hintX = Math.round((x + PANEL_WIDTH - PAD_X) / 0.7f) - hintW;
        int hintY = Math.round((y + 5) / 0.7f);
        gui.drawString(mc.font, hint, hintX, hintY, withAlpha(HINT_COLOR, panelAlpha), false);
        gui.pose().popPose();

        if (panelHeight <= HEADER_H + 1) return; // свёрнуто — рисовать список не нужно

        // Разделительная полоска между заголовком и списком участников убрана намеренно.

        // 3. Список игроков, обрезанный по высоте панели (для плавного выезда при анимации)
        gui.enableScissor(x, y + HEADER_H, x + PANEL_WIDTH, y + panelHeight);

        String myName = mc.player.getScoreboardName();
        int rowY = y + HEADER_H + 2;
        for (String memberName : sortedMembers) {
            drawMemberRow(gui, mc, squad, memberName, x, rowY, myName, panelAlpha);
            rowY += ROW_H;
        }

        gui.disableScissor();
    }

    private static void drawMemberRow(GuiGraphics gui, Minecraft mc, AASWorldData.Squad squad, String name, int x, int y, String myName, float panelAlpha) {
        boolean isLeader   = name.equals(squad.leader);
        boolean isBravo    = name.equals(squad.bravoLeader) || squad.bravoMembers.contains(name);
        boolean isCharlie  = name.equals(squad.charlieLeader) || squad.charlieMembers.contains(name);

        // Цветная полоска слева — ТОЛЬКО у файртимов (Bravo/Charlie). У основного состава полоски нет.
        // Полоска рисуется на всю высоту строки (без зазора), поэтому у соседних игроков одного
        // файртима она визуально сливается в единый сплошной блок.
        if (isBravo || isCharlie) {
            int stripeColor = isBravo ? COLOR_BRAVO : COLOR_CHARLIE;
            gui.fill(x + 1, y, x + 1 + STRIPE_W, y + ROW_H, withAlpha(stripeColor, panelAlpha));
        }

        int cursorX = x + STRIPE_W + 4;

        // Иконка кита игрока (вместо прежних ●/◆). Если кит не назначен — маленькая цветная точка-заглушка.
                // Если игрок дисконектнулся — рисуем иконку дисконекта.
                String kitName = ClientData.playerKits.getOrDefault(name, "Unassigned");
        boolean hasKit = kitName != null && !kitName.isEmpty() && !kitName.equalsIgnoreCase("Unassigned");
        boolean isOnline = mc.getConnection() != null && mc.getConnection().getPlayerInfo(name) != null;

        int kitSize = 9;
        RenderSystem.enableBlend();
        if (!isOnline) {
            // Игрок дисконектнулся — рисуем иконку дисконекта (без tint)
            setAlpha(panelAlpha);
            gui.blit(ICON_DISCONNECT, cursorX, y + 2, kitSize, kitSize, 0f, 0f, 10, 10, 10, 10);
        } else if (hasKit) {
            ResourceLocation kitIcon = new ResourceLocation("aas", "textures/gui/kits/" + kitName.toLowerCase().replace(" ", "_") + ".png");
            setAlpha(panelAlpha);
            gui.blit(kitIcon, cursorX, y + 2, kitSize, kitSize, 0f, 0f, 10, 10, 10, 10);
        } else {
            int dotColor = isLeader ? COLOR_ALPHA : 0xFF777777;
            gui.fill(cursorX + 2, y + 4, cursorX + 7, y + 9, withAlpha(dotColor, panelAlpha));
        }
        cursorX += kitSize + 3;

        // имя игрока
        boolean isSelf = name.equals(myName);
        int nameColor = isSelf ? TEXT_COLOR_ME : TEXT_COLOR;
        int maxNameWidth = PANEL_WIDTH - (cursorX - x) - 26; // место под иконки статуса + mic справа
        String displayName = trimToWidth(mc.font, name, maxNameWidth);
        gui.drawString(mc.font, displayName, cursorX, y + 2, withAlpha(nameColor, panelAlpha), false);

        // === Правый блок: [статус (heartbeat/dead)] [mic] — иконки поменяны местами ===
        MapPlayerInfo info = ClientData.mapPlayers.get(name);
        boolean isSpeaking = isTalking(name);
        boolean isDowned   = info != null && info.isDowned;
        boolean isDead     = info != null && info.isDead;

        int iconY    = y + 1;
        int statusX  = x + PANEL_WIDTH - 22; // теперь статус слева от mic
        int micX     = x + PANEL_WIDTH - 11; // mic теперь справа

        RenderSystem.enableBlend();
        setAlpha(panelAlpha);

        // статус: иконка смерти, если игрок полностью погиб; статичный heartbeat, если в ноке;
        // если с игроком всё в порядке — не рисуем вообще ничего.
        if (isDead) {
            gui.blit(ICON_DEAD, statusX, iconY, 8, 8, 0f, 0f, 10, 10, 10, 10);
        } else if (isDowned) {
            gui.blit(ICON_HEARTBEAT, statusX, iconY, 8, 8, 0f, 0f, 36, 36, 36, 36);
        }

        // микрофон — виден ТОЛЬКО когда игрок реально говорит, иначе не рисуется вовсе.
        if (isSpeaking) {
            RenderSystem.setShaderColor(0.4f, 1f, 0.4f, panelAlpha);
            gui.blit(ICON_MIC, micX, iconY, 0, 0, 8, 8, 8, 8);
        }

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    private static boolean isTalking(String name) {
        long now = System.currentTimeMillis();
        Long squadTs = ClientData.SQUAD_SPEAKERS.get(name);
        if (squadTs != null && now - squadTs < 300) return true;
        Long radioTs = ClientData.RADIO_SPEAKERS.get(name);
        return radioTs != null && now - radioTs < 300;
    }

    private static String trimToWidth(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String trimmed = text;
        while (trimmed.length() > 1 && font.width(trimmed + "..") > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + "..";
    }

    /**
     * Рисует круглый значок с числом по центру — та же перекрашиваемая текстура
     * player_circle.png и та же логика, что используется в AASDeathScreen /
     * SquadSelectionScreen / StatisticsScreen для номера отряда.
     */
    private static void drawBadgeCircle(GuiGraphics gui, Font font, int x, int y, int size, int colorARGB, String label, float panelAlpha) {
        float a = ((colorARGB >>> 24) & 0xFF) / 255f;
        float r = ((colorARGB >> 16) & 0xFF) / 255f;
        float g = ((colorARGB >> 8) & 0xFF) / 255f;
        float b = (colorARGB & 0xFF) / 255f;

        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(r, g, b, a);
        gui.blit(CIRCLE_BADGE, x, y, 0, 0, size, size, size, size);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        if (label != null && !label.isEmpty()) {
            gui.drawCenteredString(font, label, x + size / 2, y + (size - 8) / 2, withAlpha(0xFFFFFFFF, panelAlpha));
        }
    }

    private static void setAlpha(float a) {
        RenderSystem.setShaderColor(1f, 1f, 1f, a);
    }

    private static int withAlpha(int argb, float factor) {
        int a = (argb >>> 24) & 0xFF;
        int newA = Mth.clamp(Math.round(a * factor), 0, 255);
        return (newA << 24) | (argb & 0x00FFFFFF);
    }

    // Та же логика сортировки (СЛ -> Alpha -> Bravo FTL -> Bravo -> Charlie FTL -> Charlie),
    // что уже используется в SquadSelectionScreen, для единообразия порядка списка.
    private static List<String> getSortedMembers(AASWorldData.Squad squad) {
        List<String> sorted = new ArrayList<>();
        if (!squad.leader.isEmpty() && squad.members.contains(squad.leader)) sorted.add(squad.leader);

        for (String m : squad.members) {
            if (!m.equals(squad.leader) && !squad.bravoMembers.contains(m) && !squad.charlieMembers.contains(m)) {
                sorted.add(m);
            }
        }

        if (!squad.bravoLeader.isEmpty() && squad.members.contains(squad.bravoLeader)) sorted.add(squad.bravoLeader);
        for (String m : squad.bravoMembers) {
            if (!m.equals(squad.bravoLeader) && squad.members.contains(m)) sorted.add(m);
        }

        if (!squad.charlieLeader.isEmpty() && squad.members.contains(squad.charlieLeader)) sorted.add(squad.charlieLeader);
        for (String m : squad.charlieMembers) {
            if (!m.equals(squad.charlieLeader) && squad.members.contains(m)) sorted.add(m);
        }

        return sorted;
    }

    /**
     * Название клавиши сворачивания панели ВСЕГДА на английском (латиница), независимо от
     * языка клиента и раскладки клавиатуры. Vanilla getTranslatedKeyMessage() может
     * возвращать локализованное имя клавиши — вместо этого коды букв/цифр (GLFW_KEY_A..Z,
     * GLFW_KEY_0..9) конвертируются напрямую в ASCII-символ, а для остальных клавиш
     * используется английское имя из GLFW.
     */
    private static String getKeyName() {
        int keyCode = ModKeyBindings.TOGGLE_SQUAD_PANEL_KEY.getKey().getValue();

        if (keyCode >= GLFW.GLFW_KEY_A && keyCode <= GLFW.GLFW_KEY_Z) {
            return String.valueOf((char) keyCode);
        }
        if (keyCode >= GLFW.GLFW_KEY_0 && keyCode <= GLFW.GLFW_KEY_9) {
            return String.valueOf((char) keyCode);
        }

        String glfwName = GLFW.glfwGetKeyName(keyCode, 0);
        if (glfwName != null && !glfwName.isEmpty()) {
            return glfwName.toUpperCase();
        }

        // Последний резерв — если у клавиши нет печатного имени (например, F-клавиши,
        // стрелки и т.д.), используем ванильное локализованное имя.
        return ModKeyBindings.TOGGLE_SQUAD_PANEL_KEY.getTranslatedKeyMessage().getString();
    }
}