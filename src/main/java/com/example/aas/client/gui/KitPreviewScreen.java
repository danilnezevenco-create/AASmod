package com.example.aas.client.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.List;

public class KitPreviewScreen extends Screen {
    private final Screen parent;
    private final String kitName;
    private final List<ItemStack> items;

    public KitPreviewScreen(Screen parent, String kitName, List<ItemStack> items) {
        super(Component.literal("Preview: " + kitName));
        this.parent = parent;
        this.kitName = kitName;
        this.items = items;
    }

    @Override
    protected void init() {
        this.addRenderableWidget(Button.builder(Component.literal("BACK"), b -> this.minecraft.setScreen(parent))
                .bounds(this.width / 2 - 40, this.height - 30, 80, 20).build());
    }

    @Override
    public void render(GuiGraphics gui, int mx, int my, float pt) {
        this.renderBackground(gui);
        gui.drawCenteredString(font, this.title, this.width / 2, 20, 0xFFFFFF);

        int startX = this.width / 2 - (9 * 18) / 2;
        int startY = 50;

        // Рисуем инвентарь (как в KitEditor, но только картинки)
        // 1. Основной инвентарь (9-35)
        for (int i = 0; i < 27; i++) {
            int x = startX + (i % 9) * 18;
            int y = startY + (i / 9) * 18;
            drawSlot(gui, x, y, items.get(i + 9));
        }

        // 2. Хотбар (0-8)
        for (int i = 0; i < 9; i++) {
            int x = startX + i * 18;
            int y = startY + 60;
            drawSlot(gui, x, y, items.get(i));
        }

        // 3. Броня (36-39) и вторая рука (40)
        for (int i = 0; i < 5; i++) {
            int x = startX + i * 18;
            int y = startY + 85;
            drawSlot(gui, x, y, items.get(i + 36));
        }

        super.render(gui, mx, my, pt);
    }

    private void drawSlot(GuiGraphics gui, int x, int y, ItemStack stack) {
        gui.fill(x, y, x + 17, y + 17, 0x50FFFFFF);
        gui.renderFakeItem(stack, x + 1, y + 1);
        gui.renderItemDecorations(font, stack, x + 1, y + 1);
    }
}