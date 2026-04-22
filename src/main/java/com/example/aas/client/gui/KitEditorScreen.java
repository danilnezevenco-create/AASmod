// PATH: src\main\java\com\example\aas\client\gui\KitEditorScreen.java
package com.example.aas.client.gui;

import com.example.aas.menu.KitEditorMenu;
import com.example.aas.network.PacketHandler;
import com.example.aas.network.PacketSaveKit;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class KitEditorScreen extends AbstractContainerScreen<KitEditorMenu> {
    private EditBox maxTeamBox;
    private EditBox maxSquadBox;
    private EditBox minPlayersBox; // <--- НОВОЕ ПОЛЕ

    public KitEditorScreen(KitEditorMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 262;
        this.inventoryLabelY = 168;
        this.titleLabelY = 4;
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos;
        int y = topPos;

        // Компактное размещение, чтобы уместить 3 поля
        addRenderableWidget(Button.builder(Component.literal(menu.isLeaderOnly ? "[X] Squad Ld Only" : "[ ] Squad Ld Only"), b -> {
            menu.isLeaderOnly = !menu.isLeaderOnly;
            b.setMessage(Component.literal(menu.isLeaderOnly ? "[X] Squad Ld Only" : "[ ] Squad Ld Only"));
        }).bounds(x + 8, y + 14, 80, 18).build());

        maxTeamBox = new EditBox(font, x + 144, y + 14, 24, 14, Component.empty());
        maxTeamBox.setValue(String.valueOf(menu.maxPerTeam));
        addRenderableWidget(maxTeamBox);

        maxSquadBox = new EditBox(font, x + 144, y + 32, 24, 14, Component.empty());
        maxSquadBox.setValue(String.valueOf(menu.maxPerSquad));
        addRenderableWidget(maxSquadBox);

        // Поле "Минимум игроков в отряде"
        minPlayersBox = new EditBox(font, x + 144, y + 50, 24, 14, Component.empty());
        minPlayersBox.setValue(String.valueOf(menu.minSquadPlayers));
        addRenderableWidget(minPlayersBox);

        addRenderableWidget(Button.builder(Component.literal("SAVE"), b -> {
            saveKit();
        }).bounds(x + 120, y + 148, 48, 20).build());
    }

    private void saveKit() {
        try { menu.maxPerTeam = Integer.parseInt(maxTeamBox.getValue()); } catch(Exception ignored){}
        try { menu.maxPerSquad = Integer.parseInt(maxSquadBox.getValue()); } catch(Exception ignored){}
        try { menu.minSquadPlayers = Integer.parseInt(minPlayersBox.getValue()); } catch(Exception ignored){}

        // ПЕРЕДАЕМ 8 АРГУМЕНТОВ (добавлен menu.saveNbtFlags в конце)
        PacketHandler.INSTANCE.sendToServer(new PacketSaveKit(
                menu.team, menu.kitName, menu.isLeaderOnly,
                menu.maxPerTeam, menu.maxPerSquad, menu.minSquadPlayers,
                menu.resupplyFlags, menu.saveNbtFlags
        ));

        this.minecraft.player.displayClientMessage(Component.literal("Kit Saved!"), true);
    }

    @Override
    public void render(GuiGraphics gui, int mx, int my, float pt) {
        renderBackground(gui);
        super.render(gui, mx, my, pt);
        renderTooltip(gui, mx, my);

        int x = leftPos;
        int y = topPos;
        int labelColor = 0xCCCCCC; // Светло-серый для подписей
        int hintColor = 0xAAAAAA;  // Серый для инструкций

        // --- 1. ПОДПИСИ К ПОЛЯМ (Слева от коробок) ---
        // Рисуем справа налево, чтобы выровнять по правому краю перед полями
        gui.drawString(font, "Max/team", x + 98, y + 17, labelColor, false);
        gui.drawString(font, "Max/Sqd",  x + 103, y + 35, labelColor, false);
        gui.drawString(font, "Min/Sqd",  x + 103, y + 53, labelColor, false);

        // --- 2. ИНСТРУКЦИИ ПО УПРАВЛЕНИЮ (Под кнопкой) ---
        // Смещение y + 36, так как кнопка заканчивается на y + 32
        gui.pose().pushPose();
        gui.pose().scale(0.9f, 0.9f, 1.0f); // Немного уменьшим текст, чтобы влезло

        // Пересчитываем координаты из-за скейла (x/0.9, y/0.9)
        int scaledX = (int)((x + 8) / 0.9f);
        int scaledY = (int)((y + 36) / 0.9f);

        gui.drawString(font, "MMB: Toggle Resupply", scaledX, scaledY, 0x80FF80, false); // Светло-зеленый
        gui.drawString(font, "Shift + MMB: Save NBT", scaledX, scaledY + 10, 0x80FFFF, false); // Бирюзовый

        gui.pose().popPose();

        // --- 3. ЛОГИКА ОТРИСОВКИ РАМОК (Твой существующий код) ---
        for (int i = 0; i < menu.slots.size(); i++) {
            net.minecraft.world.inventory.Slot slot = menu.slots.get(i);
            if (slot.container == menu.kitInventory) {
                int idx = slot.getContainerSlot();
                if (idx >= 0 && idx < 41) {
                    if (menu.saveNbtFlags[idx]) {
                        // Зеленая рамка для NBT
                        gui.fill(leftPos + slot.x, topPos + slot.y, leftPos + slot.x + 16, topPos + slot.y + 16, 0x600000FF);
                    }
                    else if (menu.resupplyFlags[idx]) {
                        // Желтая/Зеленая прозрачная для Resupply
                        gui.fill(leftPos + slot.x, topPos + slot.y, leftPos + slot.x + 16, topPos + slot.y + 16, 0x60FFFF00);
                    }
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 2) { // Средняя кнопка мыши
            net.minecraft.world.inventory.Slot slot = this.hoveredSlot;
            if (slot != null && slot.container == menu.kitInventory) {
                int idx = slot.getContainerSlot();
                if (idx >= 0 && idx < 41) {
                    if (hasShiftDown()) {
                        // Shift + СКМ = Переключить сохранение NBT
                        menu.saveNbtFlags[idx] = !menu.saveNbtFlags[idx];
                    } else {
                        // Просто СКМ = Переключить ресаплай
                        menu.resupplyFlags[idx] = !menu.resupplyFlags[idx];
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    protected void renderBg(GuiGraphics gui, float pt, int mx, int my) {
        gui.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF333333);

        for (net.minecraft.world.inventory.Slot slot : menu.slots) {
            gui.fill(leftPos + slot.x - 1, topPos + slot.y - 1, leftPos + slot.x + 17, topPos + slot.y + 17, 0xFF000000);
            gui.fill(leftPos + slot.x, topPos + slot.y, leftPos + slot.x + 16, topPos + slot.y + 16, 0xFF8B8B8B);
        }
    }
}