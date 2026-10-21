// PATH: src/main/java/com/example/aas/client/gui/AASChatScreen.java
package com.example.aas.client.gui;

import com.example.aas.client.ClientData;
import com.example.aas.network.PacketHandler;
import com.example.aas.network.PacketSquadChat;
import com.example.aas.world.AASWorldData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;

/**
 * Панель чата в левом нижнем углу (в одном стиле с SquadSelectionScreen / AASDeathScreen).
 *
 * Сверху вниз: заголовок -> история сообщений -> [кнопка режима] [строка ввода].
 * Режимы: TEAM (по умолчанию) -> SQUAD -> ALL -> TEAM ... (кнопка или клавиша TAB).
 * Сообщения уходят через PacketSquadChat (0 = ALL, 1 = TEAM, 2 = SQUAD), команды (/...) - как обычно.
 */
public class AASChatScreen extends Screen {

    // === Режимы (совпадают с PacketSquadChat) ===
    private static final int MODE_ALL   = 0;
    private static final int MODE_TEAM  = 1;
    private static final int MODE_SQUAD = 2;

    // === Цвета (те же, что в остальных экранах мода) ===
    private static final int COLOR_ALL   = 0xFFFF55FF;
    private static final int COLOR_TEAM  = 0xFF3399FF;
    private static final int COLOR_SQUAD = 0xFF55FF55;
    private static final int TITLE_COLOR = 0xFFFFD700;
    private static final int HINT_COLOR  = 0xFFAAAAAA;

    // === Геометрия ===
    private static final int MARGIN        = 6;
    private static final int PANEL_W       = 270;
    private static final int HEADER_H      = 16;
    private static final int PAD           = 6;
    private static final int GAP           = 4;
    private static final int LINE_H        = 10;
    private static final int VISIBLE_LINES = 10;
    private static final int INPUT_H       = 18;
    private static final int MODE_BTN_W    = 56;
    private static final int STRIPE_W      = 3;
    private static final long SLIDE_MS     = 180;

    // История отправленных сообщений (стрелки вверх/вниз), живёт между открытиями чата
    private static final List<String> SENT_HISTORY = new ArrayList<>();

    private EditBox input;
    private int chatMode = MODE_TEAM; // по умолчанию TEAM
    private int scroll = 0;
    private int maxScroll = 0;
    private int historyIndex = 0;
    private String draft = "";
    private final long openedAt = System.currentTimeMillis();

    // Рассчитанная раскладка
    private int panelX, panelY, panelH;
    private int histTop, histBottom;
    private int inputX, inputY, inputW;

    public AASChatScreen() {
        super(Component.literal("Chat"));
        this.historyIndex = SENT_HISTORY.size();
    }

    @Override
    protected void init() {
        String prev = input != null ? input.getValue() : "";

        int bottom = this.height - MARGIN;
        panelX = MARGIN;
        inputY = bottom - PAD - INPUT_H;
        histBottom = inputY - GAP;
        histTop = histBottom - (VISIBLE_LINES * LINE_H + 4);
        panelY = histTop - HEADER_H;
        panelH = bottom - panelY;

        int btnX = panelX + PAD;
        this.addRenderableWidget(new ModeButton(btnX, inputY, MODE_BTN_W, INPUT_H, this::cycleMode, () -> chatMode));

        inputX = btnX + MODE_BTN_W + GAP;
        inputW = PANEL_W - PAD - (MODE_BTN_W + GAP) - PAD;

        // Рамку рисуем сами (в стиле SquadButton), поэтому у EditBox она выключена
        input = new EditBox(this.font, inputX + 5, inputY + (INPUT_H - 8) / 2, inputW - 10, 10, Component.literal("Chat"));
        input.setBordered(false);
        input.setMaxLength(256);
        input.setValue(prev);
        this.addRenderableWidget(input);
        this.setInitialFocus(input);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ режимы

    private static int modeColor(int mode) {
        switch (mode) {
            case MODE_ALL:   return COLOR_ALL;
            case MODE_SQUAD: return COLOR_SQUAD;
            default:         return COLOR_TEAM;
        }
    }

    private static Component modeLabel(int mode) {
        switch (mode) {
            case MODE_ALL:   return Component.translatable("aas.gui.chat.all");
            case MODE_SQUAD: return Component.translatable("aas.gui.chat.squad");
            default:         return Component.translatable("aas.gui.chat.team");
        }
    }

    private static boolean isInSquad() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        String me = mc.player.getScoreboardName();
        for (AASWorldData.Squad s : ClientData.clientSquads) {
            if (s.members.contains(me)) return true;
        }
        return false;
    }

    /** TEAM -> SQUAD -> ALL -> TEAM. SQUAD пропускается, если игрок не в отряде. */
    private void cycleMode() {
        int next = chatMode;
        for (int i = 0; i < 3; i++) {
            next = (next + 1) % 3;
            if (next == MODE_SQUAD && !isInSquad()) continue;
            break;
        }
        chatMode = next;
    }

    // ------------------------------------------------------------------ отправка

    private void send() {
        String msg = input.getValue().trim();
        if (!msg.isEmpty()) {
            if (SENT_HISTORY.isEmpty() || !SENT_HISTORY.get(SENT_HISTORY.size() - 1).equals(msg)) {
                SENT_HISTORY.add(msg);
                if (SENT_HISTORY.size() > 50) SENT_HISTORY.remove(0);
            }
            if (msg.startsWith("/")) {
                if (this.minecraft != null && this.minecraft.player != null) {
                    this.minecraft.player.connection.sendCommand(msg.substring(1));
                }
            } else {
                PacketHandler.INSTANCE.sendToServer(new PacketSquadChat(msg, chatMode));
            }
        }
        this.onClose();
    }

    // ------------------------------------------------------------------ ввод

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_TAB) {
            cycleMode();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            send();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP) {
            if (historyIndex > 0) {
                if (historyIndex == SENT_HISTORY.size()) draft = input.getValue();
                historyIndex--;
                input.setValue(SENT_HISTORY.get(historyIndex));
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            if (historyIndex < SENT_HISTORY.size() - 1) {
                historyIndex++;
                input.setValue(SENT_HISTORY.get(historyIndex));
            } else if (historyIndex == SENT_HISTORY.size() - 1) {
                historyIndex = SENT_HISTORY.size();
                input.setValue(draft);
            }
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP) {
            scroll = Math.min(maxScroll, scroll + VISIBLE_LINES - 1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            scroll = Math.max(0, scroll - (VISIBLE_LINES - 1));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (delta > 0) scroll = Math.min(maxScroll, scroll + 3);
        else if (delta < 0) scroll = Math.max(0, scroll - 3);
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        // Строка ввода тонкая - даём фокус по клику в любое место её рамки
        int ox = -slideOffset();
        if (mx >= inputX + ox && mx < inputX + inputW + ox && my >= inputY && my < inputY + INPUT_H) {
            this.setFocused(input);
        }
        return super.mouseClicked(mx, my, button);
    }

    // ------------------------------------------------------------------ рендер

    private int slideOffset() {
        float p = Mth.clamp((System.currentTimeMillis() - openedAt) / (float) SLIDE_MS, 0f, 1f);
        float e = 1f - (1f - p) * (1f - p) * (1f - p);
        return -Math.round((1f - e) * (PANEL_W + MARGIN));
    }

    private List<FormattedCharSequence> buildLines() {
        int w = PANEL_W - 2 * PAD - 8;
        List<FormattedCharSequence> out = new ArrayList<>();
        List<Component> hist = new ArrayList<>(ClientData.menuChatHistory); // [0] = самое новое
        for (int i = hist.size() - 1; i >= 0; i--) {
            out.addAll(this.font.split(hist.get(i), w));
        }
        return out;
    }

    @Override
    public void render(GuiGraphics gui, int mx, int my, float pt) {
        int modeColor = modeColor(chatMode);

        gui.pose().pushPose();
        gui.pose().translate(slideOffset(), 0, 0);

        // 1. Фон панели + подсветка заголовка + цветная полоса режима слева
        gui.fill(panelX, panelY, panelX + PANEL_W, panelY + panelH, 0xAA000000);
        gui.fill(panelX, panelY, panelX + PANEL_W, panelY + HEADER_H, 0x22FFFFFF);
        gui.fill(panelX, panelY, panelX + STRIPE_W, panelY + panelH, modeColor);

        // 2. Заголовок + подсказка
        gui.drawString(this.font, Component.translatable("aas.gui.chat.title"), panelX + STRIPE_W + PAD, panelY + (HEADER_H - 8) / 2 + 1, TITLE_COLOR, false);

        String hint = Component.translatable("aas.gui.chat.hint").getString();
        gui.pose().pushPose();
        gui.pose().scale(0.7f, 0.7f, 1f);
        int hintW = this.font.width(hint);
        int hintX = Math.round((panelX + PANEL_W - PAD) / 0.7f) - hintW;
        int hintY = Math.round((panelY + 5) / 0.7f);
        gui.drawString(this.font, hint, hintX, hintY, HINT_COLOR, false);
        gui.pose().popPose();

        // 3. История сообщений (последние внизу)
        int hx1 = panelX + PAD;
        int hx2 = panelX + PANEL_W - PAD;
        gui.fill(hx1, histTop, hx2, histBottom, 0x70000000);

        List<FormattedCharSequence> lines = buildLines();
        int total = lines.size();
        maxScroll = Math.max(0, total - VISIBLE_LINES);
        scroll = Mth.clamp(scroll, 0, maxScroll);

        int start = Math.max(0, total - VISIBLE_LINES - scroll);
        int end = Math.min(total, start + VISIBLE_LINES);
        int count = end - start;
        int y = histBottom - 2 - count * LINE_H;
        for (int i = start; i < end; i++) {
            gui.drawString(this.font, lines.get(i), hx1 + 4, y, 0xFFFFFFFF, true);
            y += LINE_H;
        }

        // Полоса прокрутки
        if (total > VISIBLE_LINES) {
            int trackTop = histTop + 2;
            int trackH = histBottom - histTop - 4;
            int thumbH = Math.max(8, trackH * VISIBLE_LINES / total);
            float frac = maxScroll == 0 ? 0f : start / (float) maxScroll;
            int thumbY = trackTop + Math.round((trackH - thumbH) * frac);
            int sx = hx2 - 4;
            gui.fill(sx, trackTop, sx + 2, trackTop + trackH, 0x33FFFFFF);
            gui.fill(sx, thumbY, sx + 2, thumbY + thumbH, (0xCC << 24) | (modeColor & 0x00FFFFFF));
        }

        // 4. Строка ввода (рамка в стиле SquadButton, при фокусе - цвета режима)
        gui.fill(inputX, inputY, inputX + inputW, inputY + INPUT_H, 0xCC111111);
        gui.renderOutline(inputX, inputY, inputW, INPUT_H, input.isFocused() ? modeColor : 0xFF999999);
        if (input.getValue().isEmpty()) {
            gui.drawString(this.font, Component.translatable("aas.gui.chat.placeholder"), inputX + 5, inputY + (INPUT_H - 8) / 2, 0xFF777777, false);
        }

        // 5. Виджеты (кнопка режима + EditBox)
        super.render(gui, mx, my, pt);

        gui.pose().popPose();
    }

    // ------------------------------------------------------------------ кнопка режима (стиль SquadButton)

    private static class ModeButton extends Button {
        private final IntSupplier mode;

        ModeButton(int x, int y, int w, int h, Runnable onClick, IntSupplier mode) {
            super(x, y, w, h, Component.empty(), b -> onClick.run(), DEFAULT_NARRATION);
            this.mode = mode;
        }

        @Override
        protected void renderWidget(GuiGraphics gui, int mouseX, int mouseY, float partialTicks) {
            if (!this.visible) return;

            int borderColor = this.isHovered() ? 0xFFFFFFFF : 0xFF999999;
            gui.fill(getX(), getY(), getX() + width, getY() + height, 0xCC111111);
            gui.renderOutline(getX(), getY(), width, height, borderColor);

            int m = mode.getAsInt();
            gui.drawCenteredString(Minecraft.getInstance().font, modeLabel(m),
                    getX() + width / 2, getY() + (height - 8) / 2, modeColor(m));

            if (this.isHovered()) {
                gui.fill(getX(), getY() + height - 2, getX() + 2, getY() + height, 0xFFFFFFFF);
            }
        }
    }
}