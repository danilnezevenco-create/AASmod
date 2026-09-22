package com.example.aas.client.gui;

import com.example.aas.network.PacketHandler;
import com.example.aas.network.PacketOpenPointEditor;
import com.example.aas.network.PacketSavePoint;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class PointEditorScreen extends Screen {
    private final PacketOpenPointEditor data;

    private EditBox nameBox;
    private String currentShape;
    private EditBox p1x, p1y, p1z;
    private EditBox p2x, p2y, p2z;

    private EditBox bluePrioBox, redPrioBox, capTimeBox;
    private EditBox penaltyBox, deductBox, lockBox;
    private EditBox gainNeutBox, gainCapBox; // <-- НОВЫЕ ПОЛЯ

    public PointEditorScreen(PacketOpenPointEditor data) {
        super(Component.literal("Edit Point: " + data.originalName));
        this.data = data;
        this.currentShape = data.shape;
    }

    @Override
    protected void init() {
        super.init();
        int cx = this.width / 2;
        int cy = this.height / 2;
        int startY = cy - 90;

        // Имя и Форма
        nameBox = new EditBox(this.font, cx - 80, startY, 110, 16, Component.empty());
        nameBox.setValue(data.originalName);
        this.addRenderableWidget(nameBox);

        this.addRenderableWidget(Button.builder(Component.literal(currentShape), b -> {
            currentShape = currentShape.equals("CUBE") ? "CYLINDER" : "CUBE";
            b.setMessage(Component.literal(currentShape));
        }).bounds(cx + 80, startY - 2, 60, 20).build());

        // Координаты P1
        p1x = createCoordBox(cx - 50, startY + 30, data.p1x);
        p1y = createCoordBox(cx, startY + 30, data.p1y);
        p1z = createCoordBox(cx + 50, startY + 30, data.p1z);

        // Координаты P2
        p2x = createCoordBox(cx - 50, startY + 60, data.p2x);
        p2y = createCoordBox(cx, startY + 60, data.p2y);
        p2z = createCoordBox(cx + 50, startY + 60, data.p2z);

        // Нижние настройки
        int bottomY = startY + 100;
        bluePrioBox = createCoordBox(cx - 100, bottomY, data.bluePriority);
        redPrioBox  = createCoordBox(cx - 40,  bottomY, data.redPriority);
        capTimeBox  = createCoordBox(cx + 20,  bottomY, data.captureTime);
        lockBox     = createCoordBox(cx + 80,  bottomY, data.lockMin);

        penaltyBox = createCoordBox(cx - 60, bottomY + 35, data.penalty);
        deductBox  = createCoordBox(cx + 20, bottomY + 35, data.deduct);

        // --- НОВЫЕ ТЕКСТОВЫЕ ПОЛЯ ---
        gainNeutBox = createCoordBox(cx - 60, bottomY + 60, data.gainNeut);
        gainCapBox  = createCoordBox(cx + 20, bottomY + 60, data.gainCap);

        // Кнопка сохранения (Сдвинута немного ниже, на +90)
        this.addRenderableWidget(Button.builder(Component.literal("SAVE"), b -> {
            saveAndClose();
        }).bounds(cx - 50, bottomY + 90, 100, 20).build());
    }

    private EditBox createCoordBox(int x, int y, int value) {
        EditBox box = new EditBox(this.font, x, y, 40, 16, Component.empty());
        box.setValue(String.valueOf(value));
        box.setFilter(s -> s.matches("-?\\d*")); // Разрешаем минус и цифры
        this.addRenderableWidget(box);
        return box;
    }

    private void saveAndClose() {
        try {
            String newName = nameBox.getValue().trim();
            if (newName.isEmpty()) return;

            int x1 = Integer.parseInt(p1x.getValue().isEmpty() ? "0" : p1x.getValue());
            int y1 = Integer.parseInt(p1y.getValue().isEmpty() ? "0" : p1y.getValue());
            int z1 = Integer.parseInt(p1z.getValue().isEmpty() ? "0" : p1z.getValue());
            int x2 = Integer.parseInt(p2x.getValue().isEmpty() ? "0" : p2x.getValue());
            int y2 = Integer.parseInt(p2y.getValue().isEmpty() ? "0" : p2y.getValue());
            int z2 = Integer.parseInt(p2z.getValue().isEmpty() ? "0" : p2z.getValue());

            int bp = Integer.parseInt(bluePrioBox.getValue().isEmpty() ? "0" : bluePrioBox.getValue());
            int rp = Integer.parseInt(redPrioBox.getValue().isEmpty() ? "0" : redPrioBox.getValue());
            int time = Integer.parseInt(capTimeBox.getValue().isEmpty() ? "1" : capTimeBox.getValue());
            int pen = Integer.parseInt(penaltyBox.getValue().isEmpty() ? "0" : penaltyBox.getValue());
            int ded = Integer.parseInt(deductBox.getValue().isEmpty() ? "0" : deductBox.getValue());
            int lock = Integer.parseInt(lockBox.getValue().isEmpty() ? "0" : lockBox.getValue());

            // Считываем значения для новых переменных
            int gNeut = Integer.parseInt(gainNeutBox.getValue().isEmpty() ? "0" : gainNeutBox.getValue());
            int gCap = Integer.parseInt(gainCapBox.getValue().isEmpty() ? "0" : gainCapBox.getValue());

            // Отправляем все параметры, включая новые
            PacketHandler.INSTANCE.sendToServer(new PacketSavePoint(data.originalName, newName, currentShape, x1, y1, z1, x2, y2, z2, bp, rp, time, pen, ded, lock, gNeut, gCap));
            this.onClose();
        } catch (Exception e) {}
    }

    @Override
    public void render(GuiGraphics gui, int mx, int my, float pt) {
        this.renderBackground(gui);
        int cx = this.width / 2;
        int cy = this.height / 2;
        int startY = cy - 90;

        // Немного увеличили высоту черного окна (+25 пикселей)
        gui.fill(cx - 160, cy - 110, cx + 160, cy + 135, 0xEE000000);
        gui.renderOutline(cx - 160, cy - 110, 320, 245, 0xFFFFFFFF);

        gui.drawCenteredString(font, "EDIT POINT", cx, cy - 105, 0xFFFFAA00);

        gui.drawString(font, "Name:", cx - 120, startY + 4, 0xAAAAAA);
        gui.drawString(font, "Shape:", cx + 40, startY + 4, 0xAAAAAA);

        gui.drawString(font, "Pos 1 (X Y Z):", cx - 135, startY + 34, 0xAAAAAA);
        gui.drawString(font, "Pos 2 (X Y Z):", cx - 135, startY + 64, 0xAAAAAA);

        int bottomY = startY + 90;
        gui.drawString(font, "BluePrio", cx - 100, bottomY, 0x5555FF);
        gui.drawString(font, "RedPrio", cx - 40, bottomY, 0xFF5555);
        gui.drawString(font, "Time (m)", cx + 20, bottomY, 0xAAAAAA);
        gui.drawString(font, "Lock (m)", cx + 80, bottomY, 0xAAAAAA);

        gui.drawString(font, "Tix Penalty", cx - 60, bottomY + 25, 0xAAAAAA);
        gui.drawString(font, "Cap Deduct", cx + 20, bottomY + 25, 0xAAAAAA);

        // --- НОВЫЕ ТЕКСТОВЫЕ ПОДПИСИ ---
        gui.drawString(font, "Gain Neut", cx - 60, bottomY + 50, 0x55FF55);
        gui.drawString(font, "Gain Cap", cx + 20, bottomY + 50, 0x55FF55);

        super.render(gui, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}