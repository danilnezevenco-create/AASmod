package com.example.aas.client.gui;

import com.example.aas.network.PacketHandler;
import com.example.aas.network.PacketOpenPlayerKitMenu;
import com.example.aas.network.PacketSelectKit;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

public class PlayerKitSelectScreen extends Screen {
    private final List<PacketOpenPlayerKitMenu.KitDTO> kits;
    private static final int COLUMNS = 4;
    private static final int BUTTON_WIDTH = 90;
    private static final int ROW_HEIGHT = 60; // Увеличено, чтобы влезла иконка
    private static final Component EYE_ICON = Component.literal("👁");

    public PlayerKitSelectScreen(List<PacketOpenPlayerKitMenu.KitDTO> kits) {
        super(Component.literal("Select Class"));
        this.kits = kits;
    }

    @Override
    protected void init() {
        int startX = (width - 400) / 2;
        int startY = 40;
        int i = 0;

        for (PacketOpenPlayerKitMenu.KitDTO kit : kits) {
            int row = i / COLUMNS;
            int col = i % COLUMNS;
            int x = startX + col * 100;
            int y = startY + row * ROW_HEIGHT + 26;

            // Основная кнопка выбора
            Button btn = Button.builder(Component.literal(kit.name), b -> {
                PacketHandler.INSTANCE.sendToServer(new PacketSelectKit(kit.name));
                this.onClose();
            }).bounds(x, y, BUTTON_WIDTH - 20, 20).build(); // Сузил кнопку на 20 пикселей

            btn.active = kit.available;
            this.addRenderableWidget(btn);

            // КНОПКА ГЛАЗИК
            this.addRenderableWidget(Button.builder(EYE_ICON, b -> {
                this.minecraft.setScreen(new KitPreviewScreen(this, kit.name, kit.items));
            }).bounds(x + BUTTON_WIDTH - 18, y, 20, 20).build());

            i++;
        }
    }

    @Override
    public void render(GuiGraphics gui, int mx, int my, float pt) {
        renderBackground(gui);
        gui.drawCenteredString(font, title, width / 2, 10, 0xFFFFFF);

        int startX = (width - 400) / 2;
        int startY = 40;

        // Отрисовка иконок
        for (int i = 0; i < kits.size(); i++) {
            PacketOpenPlayerKitMenu.KitDTO kit = kits.get(i);
            int row = i / COLUMNS;
            int col = i % COLUMNS;

            String iconName = kit.name.toLowerCase().replace(" ", "_").replace("-", "_");
            ResourceLocation iconLoc = new ResourceLocation("aas", "textures/gui/kits/" + iconName + ".png");

            int iconX = startX + col * 100 + (BUTTON_WIDTH / 2) - 12;
            int iconY = startY + row * ROW_HEIGHT;

            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, kit.available ? 1.0f : 0.4f);
            gui.blit(iconLoc, iconX, iconY, 0, 0, 24, 24, 24, 24);
        }
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        super.render(gui, mx, my, pt);

        // Тултип причины блокировки
        for (net.minecraft.client.gui.components.Renderable widget : this.renderables) {
            if (widget instanceof Button btn && btn.isHovered() && !btn.active) {
                for (PacketOpenPlayerKitMenu.KitDTO kit : kits) {
                    if (btn.getMessage().getString().equals(kit.name)) {
                        gui.renderTooltip(font, Component.literal(kit.reason), mx, my);
                        break;
                    }
                }
            }
        }
    }
}