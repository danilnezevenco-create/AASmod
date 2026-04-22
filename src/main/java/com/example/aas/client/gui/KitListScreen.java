// PATH: src\main\java\com\example\aas\client\gui\KitListScreen.java
package com.example.aas.client.gui;

import com.example.aas.client.AASClipboard;
import com.example.aas.network.*;
import com.example.aas.world.AASWorldData;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class KitListScreen extends Screen {
    private final String team;
    private static final int COLUMNS = 4;
    private static final int ROW_HEIGHT = 60;
    private static final int BUTTON_WIDTH = 90;

    public KitListScreen(String team) {
        super(Component.literal(team + " Kits"));
        this.team = team;
    }

    @Override
    protected void init() {
        // Рассчитываем центр для сетки кнопок
        int startX = (width - 440) / 2;
        int startY = 40;

        // --- Кнопки для всей команды сверху ---
        addRenderableWidget(Button.builder(Component.literal("COPY ALL"), b -> {
            PacketHandler.INSTANCE.sendToServer(new PacketRequestKitData(team, "ALL"));
        }).bounds(startX, 10, 80, 20).build());

        Button pasteAllBtn = Button.builder(Component.literal("PASTE ALL"), b -> {
            if (AASClipboard.teamKitsData != null)
                PacketHandler.INSTANCE.sendToServer(new PacketPasteTeam(team, AASClipboard.teamKitsData));
        }).bounds(startX + 85, 10, 80, 20).build();

        // Кнопка активна только если в буфере есть данные команды
        pasteAllBtn.active = (AASClipboard.teamKitsData != null);
        addRenderableWidget(pasteAllBtn);

        // --- Сетка китов ---
        for (int i = 0; i < AASWorldData.KIT_NAMES.length; i++) {
            String kitName = AASWorldData.KIT_NAMES[i];
            int row = i / COLUMNS;
            int col = i % COLUMNS;
            int x = startX + col * 110;
            int y = startY + row * ROW_HEIGHT + 26;

            // Кнопка открытия редактора
            addRenderableWidget(Button.builder(Component.literal(kitName), b -> {
                PacketHandler.INSTANCE.sendToServer(new PacketOpenKitEditor(team, kitName));
            }).bounds(x, y, 70, 20).build());

            // Кнопка Копировать (C)
            addRenderableWidget(Button.builder(Component.literal("C"), b -> {
                PacketHandler.INSTANCE.sendToServer(new PacketRequestKitData(team, kitName));
            }).bounds(x + 72, y, 15, 20).build());

            // Кнопка Вставить (P)
            Button pBtn = Button.builder(Component.literal("P"), b -> {
                if (AASClipboard.kitData != null)
                    PacketHandler.INSTANCE.sendToServer(new PacketPasteKit(team, kitName, AASClipboard.kitData));
            }).bounds(x + 89, y, 15, 20).build();

            // Кнопка активна только если в буфере есть данные одного кита
            pBtn.active = (AASClipboard.kitData != null);
            addRenderableWidget(pBtn);
        }
    }

    @Override
    public void render(GuiGraphics gui, int mx, int my, float pt) {
        renderBackground(gui);
        gui.drawCenteredString(font, title, width/2, 10, 0xFFFFFF);

        int startX = (width - 440) / 2;
        int startY = 40;

        for (int i = 0; i < AASWorldData.KIT_NAMES.length; i++) {
            String kitName = AASWorldData.KIT_NAMES[i];
            int row = i / COLUMNS;
            int col = i % COLUMNS;

            String iconPath = kitName.toLowerCase().replace(" ", "_").replace("-", "_");
            ResourceLocation iconLoc = new ResourceLocation("aas", "textures/gui/kits/" + iconPath + ".png");

            // Центрируем иконку над группой кнопок кита
            int iconX = startX + col * 110 + (70 / 2) - 12;
            int iconY = startY + row * ROW_HEIGHT;

            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            gui.blit(iconLoc, iconX, iconY, 0, 0, 24, 24, 24, 24);
        }

        super.render(gui, mx, my, pt);

        // Подсказки при наведении на маленькие кнопки
        if (mx > (width - 440) / 2) {
            // Можно добавить рендер тултипов "Copy Kit" / "Paste Kit", если нужно
        }
    }
}