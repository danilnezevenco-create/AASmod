// PATH: src\main\java\com\example\aas\client\gui\KitEditorScreen.java
package com.example.aas.client.gui;

import com.example.aas.client.AASClipboard;
import com.example.aas.menu.KitEditorMenu;
import com.example.aas.network.PacketHandler;
import com.example.aas.network.PacketOpenKitEditor;
import com.example.aas.network.PacketPasteKit;
import com.example.aas.network.PacketRequestKitData;
import com.example.aas.network.PacketSaveKit;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public class KitEditorScreen extends AbstractContainerScreen<KitEditorMenu> {

    // --- Р вЂњР ВµР С•Р СР ВµРЎвЂљРЎР‚Р С‘РЎРЏ Р Р†Р ВµРЎР‚РЎвЂ¦Р Р…Р ВµР в„– Р С—Р В°Р Р…Р ВµР В»Р С‘ Р Р…Р В°РЎРѓРЎвЂљРЎР‚Р С•Р ВµР С” (Р Р†РЎРѓРЎвЂ Р Р†РЎвЂ№Р Р…Р ВµРЎРѓР ВµР Р…Р С• Р Р† Р С”Р С•Р Р…РЎРѓРЎвЂљР В°Р Р…РЎвЂљРЎвЂ№, РЎвЂЎРЎвЂљР С•Р В±РЎвЂ№ init() Р С‘ render() Р Р…Р Вµ РЎР‚Р В°РЎРѓРЎвЂ¦Р С•Р Т‘Р С‘Р В»Р С‘РЎРѓРЎРЉ) ---
    private static final int PAD = 8;

    private static final int TITLE_Y = 6;

    private static final int ROW1_Y = 20;  // Squad Ld Only   | Max/Team
    private static final int ROW2_Y = 40;  // Alt Version     | Max/Sqd
    private static final int ROW3_Y = 60;  // ALTERNATIVE     | Min/Sqd
    private static final int ROW4_Y = 82;  // Copy | Paste
    private static final int ROW_H  = 16;

    private static final int HINT1_Y = 104;
    private static final int HINT2_Y = 114;

    private static final int LEFT_COL_X = PAD;
    private static final int LEFT_COL_W = 108;

    private static final int NUM_LABEL_X = 122;
    private static final int NUM_BOX_X   = 182;
    private static final int NUM_BOX_W   = 24;
    private static final int NUM_BOX_H   = 14;

    private static final int NAME_BOX_X = 100;
    private static final int NAME_BOX_Y = TITLE_Y - 1;
    private static final int NAME_BOX_W = 108;
    private static final int NAME_BOX_H = 12;

    private EditBox nameBox; // <-- РќРћР’РћР• РїРѕР»Рµ

    private EditBox maxTeamBox;
    private EditBox maxSquadBox;
    private EditBox minPlayersBox;
    private Button hasAltBtn;
    private Button altToggleBtn;
    private Button pasteBtn;

    public KitEditorScreen(KitEditorMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 216;
        this.imageHeight = 322;
        this.inventoryLabelY = 228;
        this.titleLabelY = TITLE_Y;
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos;
        int y = topPos;

        // === РџРѕР»Рµ РІРІРѕРґР° РЅР°Р·РІР°РЅРёСЏ РєРёС‚Р° (РѕС‚РЅРѕСЃРёС‚СЃСЏ Рє С‚РµРєСѓС‰РµР№ РѕС‚РєСЂС‹С‚РѕР№ РІРµСЂСЃРёРё вЂ” standard/alt) ===
        nameBox = new EditBox(font, x + NAME_BOX_X, y + NAME_BOX_Y, NAME_BOX_W, NAME_BOX_H, Component.empty());
        nameBox.setMaxLength(32);
        nameBox.setValue(menu.displayName == null ? "" : menu.displayName);
        nameBox.setSuggestion(menu.kitName + (menu.isAlt ? " (Alt)" : ""));
        addRenderableWidget(nameBox);

        // --- РЎС‚СЂРѕРєР° 1: Squad Ld Only / Max per team ---
        Button leaderBtn = Button.builder(Component.literal(menu.isLeaderOnly ? "[X] Squad Ld Only" : "[ ] Squad Ld Only"), b -> {
            menu.isLeaderOnly = !menu.isLeaderOnly;
            b.setMessage(Component.literal(menu.isLeaderOnly ? "[X] Squad Ld Only" : "[ ] Squad Ld Only"));
        }).bounds(x + LEFT_COL_X, y + ROW1_Y, LEFT_COL_W, ROW_H).build();
        leaderBtn.active = !menu.isAlt;
        addRenderableWidget(leaderBtn);

        maxTeamBox = new EditBox(font, x + NUM_BOX_X, y + ROW1_Y + 1, NUM_BOX_W, NUM_BOX_H, Component.empty());
        maxTeamBox.setValue(String.valueOf(menu.maxPerTeam));
        maxTeamBox.setEditable(!menu.isAlt);
        addRenderableWidget(maxTeamBox);

        // --- РЎС‚СЂРѕРєР° 2: Alt Version / Max per squad ---
        hasAltBtn = Button.builder(Component.literal(menu.hasAlt ? "[X] Alt Version" : "[ ] Alt Version"), b -> {
            menu.hasAlt = !menu.hasAlt;
            b.setMessage(Component.literal(menu.hasAlt ? "[X] Alt Version" : "[ ] Alt Version"));
            altToggleBtn.active = menu.isAlt || menu.hasAlt;
        }).bounds(x + LEFT_COL_X, y + ROW2_Y, LEFT_COL_W, ROW_H).build();
        hasAltBtn.active = !menu.isAlt;
        hasAltBtn.visible = !menu.isAlt;
        addRenderableWidget(hasAltBtn);

        maxSquadBox = new EditBox(font, x + NUM_BOX_X, y + ROW2_Y + 1, NUM_BOX_W, NUM_BOX_H, Component.empty());
        maxSquadBox.setValue(String.valueOf(menu.maxPerSquad));
        maxSquadBox.setEditable(!menu.isAlt);
        addRenderableWidget(maxSquadBox);

        // --- РЎС‚СЂРѕРєР° 3: STANDARD/ALTERNATIVE / Min squad players ---
        altToggleBtn = Button.builder(Component.literal(menu.isAlt ? "STANDARD" : "ALTERNATIVE"), b -> {
            switchVariant();
        }).bounds(x + LEFT_COL_X, y + ROW3_Y, LEFT_COL_W, ROW_H).build();
        altToggleBtn.active = menu.isAlt || menu.hasAlt;
        addRenderableWidget(altToggleBtn);

        minPlayersBox = new EditBox(font, x + NUM_BOX_X, y + ROW3_Y + 1, NUM_BOX_W, NUM_BOX_H, Component.empty());
        minPlayersBox.setValue(String.valueOf(menu.minSquadPlayers));
        minPlayersBox.setEditable(!menu.isAlt);
        addRenderableWidget(minPlayersBox);

        // --- РЎС‚СЂРѕРєР° 4: Copy / Paste ---
        addRenderableWidget(Button.builder(Component.literal("Copy"), b -> {
            PacketHandler.INSTANCE.sendToServer(new PacketRequestKitData(menu.team, menu.kitName, menu.isAlt));
        }).bounds(x + LEFT_COL_X, y + ROW4_Y, 52, ROW_H).build());

        pasteBtn = Button.builder(Component.literal("Paste"), b -> {
            if (AASClipboard.kitData != null) {
                PacketHandler.INSTANCE.sendToServer(new PacketPasteKit(menu.team, menu.kitName, menu.isAlt, AASClipboard.kitData));
                PacketHandler.INSTANCE.sendToServer(new PacketOpenKitEditor(menu.team, menu.kitName, menu.isAlt));
            }
        }).bounds(x + LEFT_COL_X + 56, y + ROW4_Y, 52, ROW_H).build();
        pasteBtn.active = (AASClipboard.kitData != null);
        addRenderableWidget(pasteBtn);

        addRenderableWidget(Button.builder(Component.literal("SAVE"), b -> {
            saveKit();
        }).bounds(x + 120, y + 208, 56, 18).build());
    }

    private void switchVariant() {
        // Переключение вкладки НЕ сохраняет текущие изменения — если Save не нажат,
        // несохранённые правки (включая удалённые/добавленные предметы) отбрасываются,
        // и на сервере переоткрывается та версия кита, что там реально сохранена.
        PacketHandler.INSTANCE.sendToServer(new PacketOpenKitEditor(menu.team, menu.kitName, !menu.isAlt));
    }

    private void saveKit() {
        try { menu.maxPerTeam = Integer.parseInt(maxTeamBox.getValue()); } catch(Exception ignored){}
        try { menu.maxPerSquad = Integer.parseInt(maxSquadBox.getValue()); } catch(Exception ignored){}
        try { menu.minSquadPlayers = Integer.parseInt(minPlayersBox.getValue()); } catch(Exception ignored){}
        menu.displayName = nameBox.getValue(); // <-- РќРћР’РћР•

        PacketHandler.INSTANCE.sendToServer(new PacketSaveKit(
                menu.team, menu.kitName, menu.isAlt, menu.hasAlt, menu.isLeaderOnly,
                menu.maxPerTeam, menu.maxPerSquad, menu.minSquadPlayers,
                menu.displayName,                              // <-- РќРћР’РћР•
                menu.resupplyFlags, menu.saveNbtFlags
        ));

        this.minecraft.player.displayClientMessage(Component.literal(menu.isAlt ? "Alt Kit Saved!" : "Kit Saved!"), true);
    }

    // --- Навигация по ESC ---
    // На вкладке ALTERNATIVE: Esc НЕ закрывает всё окно целиком, а возвращает на STANDARD
    // (без сохранения, как и переключение кнопкой — см. switchVariant()).
    // На вкладке STANDARD: Esc закрывает редактор кита и возвращает на экран со списком
    // всех китов (KitListScreen), а уже там Esc закрывает всё целиком (стандартное поведение Screen).
    @Override
    public void onClose() {
        if (menu.isAlt) {
            switchVariant();
        } else {
            this.minecraft.player.closeContainer();
            this.minecraft.setScreen(new KitListScreen(menu.team));
        }
    }

    // Р вЂ™Р В°Р Р…Р С‘Р В»РЎРЉР Р…РЎвЂ№Р в„– Р В·Р В°Р С–Р С•Р В»Р С•Р Р†Р С•Р С” Р С”Р С•Р Р…РЎвЂљР ВµР в„–Р Р…Р ВµРЎР‚Р В° Р С‘ Р С—Р С•Р Т‘Р С—Р С‘РЎРѓРЎРЉ "Inventory" Р Р…Р Вµ Р Р…РЎС“Р В¶Р Р…РЎвЂ№ РІР‚вЂќ Р Р†РЎРѓРЎвЂ РЎР‚Р С‘РЎРѓРЎС“Р ВµР С РЎРѓР В°Р СР С‘ Р Р† render().
    // Р вЂР ВµР В· РЎРЊРЎвЂљР С•Р С–Р С• Р С•Р Р†Р ВµРЎР‚РЎР‚Р В°Р в„–Р Т‘Р В° Р Р†Р В°Р Р…Р С‘Р В»РЎРЉР Р…РЎвЂ№Р в„– title Р Р…Р В°Р С”Р В»Р В°Р Т‘РЎвЂ№Р Р†Р В°Р В»РЎРѓРЎРЏ Р Р…Р В° Р Р…Р В°РЎв‚¬ "[STANDARD]"/"[ALTERNATIVE]" (Р С‘Р СР ВµР Р…Р Р…Р С• Р С•РЎвЂљРЎРѓРЎР‹Р Т‘Р В° Р В±РЎвЂ№Р В» Р С•Р В±РЎР‚Р ВµР В·Р В°Р Р…Р Р…РЎвЂ№Р в„– "РЎРѓer" Р Р…Р В° РЎРѓР С”РЎР‚Р С‘Р Р…РЎв‚¬Р С•РЎвЂљР Вµ).
    @Override
    protected void renderLabels(GuiGraphics gui, int mouseX, int mouseY) {
        gui.drawString(font, this.playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
    }

    @Override
    public void render(GuiGraphics gui, int mx, int my, float pt) {
        renderBackground(gui);
        super.render(gui, mx, my, pt);
        renderTooltip(gui, mx, my);

        int x = leftPos;
        int y = topPos;
        int labelColor = 0xCCCCCC;

        // Р вЂ”Р В°Р С–Р С•Р В»Р С•Р Р†Р С•Р С”: Р С”Р В°Р С”Р С•Р в„– Р Р†Р С‘Р Т‘ РЎРѓР Р…Р В°РЎР‚РЎРЏР В¶Р ВµР Р…Р С‘РЎРЏ РЎРѓР ВµР в„–РЎвЂЎР В°РЎРѓ РЎР‚Р ВµР Т‘Р В°Р С”РЎвЂљР С‘РЎР‚РЎС“Р ВµРЎвЂљРЎРѓРЎРЏ
        gui.drawString(font, menu.isAlt ? "[ALTERNATIVE]" : "[STANDARD]", x + PAD, y + TITLE_Y, menu.isAlt ? 0xFFAA00 : 0x55FF55, false);

        // Р СџР С•Р Т‘Р С—Р С‘РЎРѓР С‘ РЎвЂЎР С‘РЎРѓР В»Р С•Р Р†РЎвЂ№РЎвЂ¦ Р С—Р С•Р В»Р ВµР в„– РІР‚вЂќ Р Р†РЎвЂ№РЎР‚Р С•Р Р†Р Р…Р ВµР Р…РЎвЂ№ Р С—Р С• Р Р†РЎвЂ№РЎРѓР С•РЎвЂљР Вµ РЎРѓР С• РЎРѓР Р†Р С•Р С‘Р СР С‘ EditBox-Р В°Р СР С‘, Р Р…Р Вµ Р В·Р В°Р В»Р ВµР В·Р В°РЎР‹РЎвЂљ Р Р…Р В° Р С”Р Р…Р С•Р С—Р С”Р С‘ РЎРѓР В»Р ВµР Р†Р В°
        gui.drawString(font, "Max/Team", x + NUM_LABEL_X, y + ROW1_Y + 4, labelColor, false);
        gui.drawString(font, "Max/Sqd",  x + NUM_LABEL_X, y + ROW2_Y + 4, labelColor, false);
        gui.drawString(font, "Min/Sqd",  x + NUM_LABEL_X, y + ROW3_Y + 4, labelColor, false);

        // Р СџР С•Р Т‘РЎРѓР С”Р В°Р В·Р С”Р В° Р С—Р С• РЎС“Р С—РЎР‚Р В°Р Р†Р В»Р ВµР Р…Р С‘РЎР‹ РІР‚вЂќ РЎвЂљР ВµР С—Р ВµРЎР‚РЎРЉ Р Р…Р С‘Р В¶Р Вµ Р Р†РЎРѓР ВµРЎвЂ¦ Р С”Р Р…Р С•Р С—Р С•Р С”, Р Р…Р С‘РЎвЂЎР ВµР С–Р С• Р Р…Р Вµ Р С—Р ВµРЎР‚Р ВµР С”РЎР‚РЎвЂ№Р Р†Р В°Р ВµРЎвЂљ
        gui.drawString(font, "MMB: Toggle Resupply", x + PAD, y + HINT1_Y, 0x80FF80, false);
        gui.drawString(font, "Shift + MMB: Save NBT", x + PAD, y + HINT2_Y, 0x80FFFF, false);

        // --- Р вЂєР С•Р С–Р С‘Р С”Р В° Р С•РЎвЂљРЎР‚Р С‘РЎРѓР С•Р Р†Р С”Р С‘ РЎР‚Р В°Р СР С•Р С” (Resupply/NBT) Р Р…Р В° РЎРѓР В»Р С•РЎвЂљР В°РЎвЂ¦ Р С”Р С‘РЎвЂљР В° (0-48) ---
        for (int i = 0; i < menu.slots.size(); i++) {
            net.minecraft.world.inventory.Slot slot = menu.slots.get(i);
            if (slot.container == menu.kitInventory) {
                int idx = slot.getContainerSlot();
                if (idx >= 0 && idx < 49) {
                    if (menu.saveNbtFlags[idx]) {
                        // Р РЋР С‘Р Р…РЎРЏРЎРЏ РЎР‚Р В°Р СР С”Р В° Р Т‘Р В»РЎРЏ NBT
                        gui.fill(leftPos + slot.x, topPos + slot.y, leftPos + slot.x + 16, topPos + slot.y + 16, 0x600000FF);
                    }
                    else if (menu.resupplyFlags[idx]) {
                        // Р вЂ“Р ВµР В»РЎвЂљР В°РЎРЏ Р С—РЎР‚Р С•Р В·РЎР‚Р В°РЎвЂЎР Р…Р В°РЎРЏ Р Т‘Р В»РЎРЏ Resupply
                        gui.fill(leftPos + slot.x, topPos + slot.y, leftPos + slot.x + 16, topPos + slot.y + 16, 0x60FFFF00);
                    }
                }
            }
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 2) { // Р РЋРЎР‚Р ВµР Т‘Р Р…РЎРЏРЎРЏ Р С”Р Р…Р С•Р С—Р С”Р В° Р СРЎвЂ№РЎв‚¬Р С‘
            net.minecraft.world.inventory.Slot slot = this.hoveredSlot;
            if (slot != null && slot.container == menu.kitInventory) {
                int idx = slot.getContainerSlot();
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
        // Р С›РЎРѓР Р…Р С•Р Р†Р Р…Р С•Р в„– РЎвЂћР С•Р Р…
        gui.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF333333);

        // --- Р СћРЎвЂР СР Р…Р В°РЎРЏ Р С—Р В°Р Р…Р ВµР В»РЎРЉ РЎРѓР В»Р ВµР Р†Р В° Р Т‘Р В»РЎРЏ EXTRA/CURIOS РЎРѓР В»Р С•РЎвЂљР С•Р Р† ---
        gui.fill(leftPos - 42, topPos + 122, leftPos - 2, topPos + 202, 0xFF222222);
        gui.renderOutline(leftPos - 42, topPos + 122, 40, 80, 0xFF000000);

        // Р С›РЎвЂљРЎР‚Р С‘РЎРѓР С•Р Р†Р С”Р В° Р Р†РЎРѓР ВµРЎвЂ¦ РЎР‚Р В°Р СР С•Р С” РЎРѓР В»Р С•РЎвЂљР С•Р Р† (Р Р†Р С”Р В»РЎР‹РЎвЂЎР В°РЎРЏ РЎвЂљР Вµ, РЎвЂЎРЎвЂљР С• Р В·Р В° Р С–РЎР‚Р В°Р Р…Р С‘РЎвЂ Р В°Р СР С‘ Р С•РЎРѓР Р…Р С•Р Р†Р Р…Р С•Р С–Р С• Р С•Р С”Р Р…Р В°)
        for (net.minecraft.world.inventory.Slot slot : menu.slots) {
            gui.fill(leftPos + slot.x - 1, topPos + slot.y - 1, leftPos + slot.x + 17, topPos + slot.y + 17, 0xFF000000);
            gui.fill(leftPos + slot.x, topPos + slot.y, leftPos + slot.x + 16, topPos + slot.y + 16, 0xFF8B8B8B);
        }
    }
}