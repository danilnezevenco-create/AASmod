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
    private EditBox minPlayersBox;

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
        int labelColor = 0xCCCCCC;

        gui.drawString(font, "Max/team", x + 98, y + 17, labelColor, false);
        gui.drawString(font, "Max/Sqd",  x + 103, y + 35, labelColor, false);
        gui.drawString(font, "Min/Sqd",  x + 103, y + 53, labelColor, false);

        gui.pose().pushPose();
        gui.pose().scale(0.9f, 0.9f, 1.0f);
        int scaledX = (int)((x + 8) / 0.9f);
        int scaledY = (int)((y + 36) / 0.9f);
        gui.drawString(font, "MMB: Toggle Resupply", scaledX, scaledY, 0x80FF80, false);
        gui.drawString(font, "Shift + MMB: Save NBT", scaledX, scaledY + 10, 0x80FFFF, false);
        gui.pose().popPose();

        // --- ЛОГИКА ОТРИСОВКИ РАМОК (Обновлено до 49 слотов) ---
        for (int i = 0; i < menu.slots.size(); i++) {
            net.minecraft.world.inventory.Slot slot = menu.slots.get(i);
            if (slot.container == menu.kitInventory) {
                int idx = slot.getContainerSlot();
                // Теперь проверяем до 49 (0-48)
                if (idx >= 0 && idx < 49) {
                    if (menu.saveNbtFlags[idx]) {
                        // Синяя рамка для NBT
                        gui.fill(leftPos + slot.x, topPos + slot.y, leftPos + slot.x + 16, topPos + slot.y + 16, 0x600000FF);
                    }
                    else if (menu.resupplyFlags[idx]) {
                        // Желтая прозрачная для Resupply
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
                // Теперь разрешаем клики по индексам до 49 (Curios слоты)
                if (idx >= 0 && idx < 49) {
                    if (hasShiftDown()) {
                        menu.saveNbtFlags[idx] = !menu.saveNbtFlags[idx];
                    } else {
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
        // Основной фон
        gui.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF333333);

        // --- НОВОЕ: Темная панель слева для EXTRA/CURIOS слотов ---
        // Смещение X: -42, ширина 40 пикселей. Высота совпадает с областью слотов.
        gui.fill(leftPos - 42, topPos + 62, leftPos - 2, topPos + 142, 0xFF222222);
        gui.renderOutline(leftPos - 42, topPos + 62, 40, 80, 0xFF000000);

        // Отрисовка всех рамок слотов (включая те, что за границами основного окна)
        for (net.minecraft.world.inventory.Slot slot : menu.slots) {
            gui.fill(leftPos + slot.x - 1, topPos + slot.y - 1, leftPos + slot.x + 17, topPos + slot.y + 17, 0xFF000000);
            gui.fill(leftPos + slot.x, topPos + slot.y, leftPos + slot.x + 16, topPos + slot.y + 16, 0xFF8B8B8B);
        }
    }
}