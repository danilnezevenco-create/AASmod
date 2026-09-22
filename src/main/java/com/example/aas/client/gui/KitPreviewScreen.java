package com.example.aas.client.gui;

import com.example.aas.network.PacketOpenPlayerKitMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.List;

public class KitPreviewScreen extends Screen {
    private final Screen parent;
    private final PacketOpenPlayerKitMenu.KitDTO kit;
    private boolean showingAlt;
    private Button variantToggleBtn;
    // Screen.title в этой версии Minecraft объявлен final, поэтому под свой
    // динамический заголовок (меняется при переключении STANDARD/ALTERNATIVE) заводим отдельное поле.
    private Component displayTitle;

    // === Цветовая схема кнопок в стиле "APPLY FOR CMD" (лайм-зелёный) ===
    private static final int BTN_BG = 0xCC111111;
    private static final int BTN_BORDER_IDLE = 0xFF999999;
    private static final int BTN_BORDER_HOVER = 0xFFFFFFFF;
    private static final int BTN_BORDER_DISABLED = 0xFF3A3A3A;

    public KitPreviewScreen(Screen parent, PacketOpenPlayerKitMenu.KitDTO kit) { this(parent, kit, false); }

    public KitPreviewScreen(Screen parent, PacketOpenPlayerKitMenu.KitDTO kit, boolean startOnAlt) {
        super(Component.translatable("aas.gui.kit.preview.title",
                startOnAlt ? kit.altDisplayName : kit.displayName));
        this.parent = parent;
        this.kit = kit;
        this.showingAlt = startOnAlt && kit.hasAlt;
        this.displayTitle = this.getTitle();
    }

    // Сохранена старая сигнатура для совместимости, если где-то ещё вызывается старым способом
    public KitPreviewScreen(Screen parent, String kitName, List<ItemStack> items) {
        this(parent, new PacketOpenPlayerKitMenu.KitDTO(kitName, true, "", items, false, new java.util.ArrayList<>()), false);
    }

    private List<ItemStack> currentItems() {
        return showingAlt ? kit.altItems : kit.items;
    }

    // === Кнопка в стиле "APPLY FOR CMD" (тёмная плашка + лайм-зелёная рамка) ===
    private static class AasFlatButton extends Button {
        AasFlatButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(GuiGraphics gui, int mouseX, int mouseY, float partialTicks) {
            if (!this.visible) return;

            int borderColor = this.isHovered() ? BTN_BORDER_HOVER : BTN_BORDER_IDLE;
            if (!this.active) borderColor = BTN_BORDER_DISABLED;

            gui.fill(getX(), getY(), getX() + width, getY() + height, BTN_BG);
            gui.renderOutline(getX(), getY(), width, height, borderColor);

            int textColor = this.active ? 0xFFFFFFFF : 0xFF777777;
            gui.drawCenteredString(Minecraft.getInstance().font, this.getMessage(), getX() + width / 2, getY() + (height - 8) / 2, textColor);

            if (this.active && this.isHovered()) {
                gui.fill(getX(), getY() + height - 2, getX() + 2, getY() + height, BTN_BORDER_HOVER);
            }
        }
    }

    @Override
    protected void init() {
        this.addRenderableWidget(new AasFlatButton(this.width / 2 - 90, this.height - 30, 80, 20,
                Component.translatable("aas.gui.kit.preview.back"), b -> this.minecraft.setScreen(parent)));

        if (kit.hasAlt) {
            variantToggleBtn = new AasFlatButton(this.width / 2 + 10, this.height - 30, 80, 20,
                    Component.translatable(showingAlt ? "aas.gui.kit.preview.button.standard" : "aas.gui.kit.preview.button.alternative"),
                    b -> {
                        showingAlt = !showingAlt;
                        this.displayTitle = Component.translatable("aas.gui.kit.preview.title",
                                showingAlt ? kit.altDisplayName : kit.displayName);
                        b.setMessage(Component.translatable(showingAlt ? "aas.gui.kit.preview.button.standard" : "aas.gui.kit.preview.button.alternative"));
                    });
            this.addRenderableWidget(variantToggleBtn);
        }
    }

    @Override
    public void render(GuiGraphics gui, int mx, int my, float pt) {
        if (!this.minecraft.player.isAlive()) {
            gui.fill(0, 0, this.width, this.height, 0xFF000000);
        } else {
            this.renderBackground(gui);
        }
        gui.drawCenteredString(font, this.displayTitle, this.width / 2, 20, 0xFFFFFF);
        if (kit.hasAlt) {
            Component tag = showingAlt
                    ? Component.translatable("aas.gui.kit.preview.tag.alternative").withStyle(ChatFormatting.GOLD)
                    : Component.translatable("aas.gui.kit.preview.tag.standard").withStyle(ChatFormatting.GREEN);
            gui.drawCenteredString(font, tag, this.width / 2, 32, 0xFFFFFF);
        }

        List<ItemStack> items = currentItems();
        int startX = this.width / 2 - (9 * 18) / 2;
        int startY = 50;

        // Рисуем инвентарь (как в KitEditor, но только картинки)
        // 1. Основной инвентарь (9-35)
        for (int i = 0; i < 27; i++) {
            int x = startX + (i % 9) * 18;
            int y = startY + (i / 9) * 18;
            drawSlot(gui, x, y, safeGet(items, i + 9));
        }

        // 2. Хотбар (0-8)
        for (int i = 0; i < 9; i++) {
            int x = startX + i * 18;
            int y = startY + 60;
            drawSlot(gui, x, y, safeGet(items, i));
        }

        // 3. Броня (36-39) и вторая рука (40)
        for (int i = 0; i < 5; i++) {
            int x = startX + i * 18;
            int y = startY + 85;
            drawSlot(gui, x, y, safeGet(items, i + 36));
        }

        super.render(gui, mx, my, pt);
    }

    private ItemStack safeGet(List<ItemStack> items, int idx) {
        return (idx >= 0 && idx < items.size()) ? items.get(idx) : ItemStack.EMPTY;
    }

    @Override
    public void onClose() {
        if (this.minecraft.player != null && !this.minecraft.player.isAlive()) {
            // Возвращаемся в меню выбора китов (которое само вернет в DeathScreen)
            this.minecraft.setScreen(parent);
        } else {
            super.onClose();
        }
    }
    private void drawSlot(GuiGraphics gui, int x, int y, ItemStack stack) {
        gui.fill(x, y, x + 17, y + 17, 0x50FFFFFF);
        gui.renderFakeItem(stack, x + 1, y + 1);
        gui.renderItemDecorations(font, stack, x + 1, y + 1);
    }
}