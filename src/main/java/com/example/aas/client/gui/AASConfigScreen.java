package com.example.aas.client.gui;

import com.example.aas.config.AASConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.widget.ForgeSlider;

public class AASConfigScreen extends Screen {
    private final Screen parentScreen;

    private EditBox hubSpawnCostBox, hubResupplyBox, maxHubsBox;
    private EditBox hubDistBox, rallyDistBox, hubSoundRadBox;
    private EditBox hubBlockRadBox, rallyBlockRadBox, hubBuildRadBox;
    private EditBox hubEnemyCountBox, rallyEnemyCountBox, crateBuildRadBox;

    private EditBox blueNameBox, redNameBox;
    private ForgeSlider diggingSpeedSlider;

    public AASConfigScreen(Screen parentScreen) {
        super(Component.literal("AAS Configuration"));
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        super.init();
        int cx = this.width / 2;

        int lx = cx - 155;
        int rx = cx + 5;
        int y = 22;

        // Row 1
        this.addRenderableWidget(CycleButton.onOffBuilder(AASConfig.AGS_PROJECTILE_DESTRUCTION.get())
                .create(lx, y, 150, 20, Component.literal("AGS Destruction"), (b, v) -> AASConfig.AGS_PROJECTILE_DESTRUCTION.set(v)));

        this.addRenderableWidget(CycleButton.onOffBuilder(AASConfig.AMMO_STACK_DESTRUCTION.get())
                .create(rx, y, 150, 20, Component.literal("Ammo Explosion"), (b, v) -> AASConfig.AMMO_STACK_DESTRUCTION.set(v)));

        y += 22;
        // Row 2
        this.addRenderableWidget(CycleButton.onOffBuilder(AASConfig.PREVENT_BLOCK_BREAKING.get())
                .create(lx, y, 150, 20, Component.literal("Prevent Breaking"), (b, v) -> AASConfig.PREVENT_BLOCK_BREAKING.set(v)));

        this.addRenderableWidget(CycleButton.onOffBuilder(AASConfig.PREVENT_ALL_ITEM_DROPS.get())
                .create(rx, y, 150, 20, Component.literal("Prevent Item Drops"), (b, v) -> AASConfig.PREVENT_ALL_ITEM_DROPS.set(v)));

        y += 22;
        // Row 3
        this.addRenderableWidget(CycleButton.onOffBuilder(AASConfig.HUB_SPAWN_COSTS_MATERIALS.get())
                .create(lx, y, 150, 20, Component.literal("FOB Spawn Cost"), (b, v) -> AASConfig.HUB_SPAWN_COSTS_MATERIALS.set(v)));

        this.addRenderableWidget(CycleButton.onOffBuilder(AASConfig.LOW_TICKETS_SIREN.get())
                .create(rx, y, 150, 20, Component.literal("Low Tickets Siren"), (b, v) -> AASConfig.LOW_TICKETS_SIREN.set(v)));

        y += 22;
        // Row 4
        this.addRenderableWidget(CycleButton.onOffBuilder(AASConfig.AUTO_GIVE_SL_RADIO.get())
                .create(lx, y, 150, 20, Component.literal("Auto SL Radio"), (b, v) -> AASConfig.AUTO_GIVE_SL_RADIO.set(v)));

        // НОВАЯ КНОПКА: Требовать ящик для ХАБа
        this.addRenderableWidget(CycleButton.onOffBuilder(AASConfig.HUB_PLACEMENT_REQUIRES_CRATE.get())
                .create(rx, y, 150, 20, Component.literal("FOB Needs Crate"), (b, v) -> AASConfig.HUB_PLACEMENT_REQUIRES_CRATE.set(v)));

        y += 22;
        // Row 5: Слайдер теперь ниже
        diggingSpeedSlider = new ForgeSlider(lx, y, 310, 20, Component.literal("Dig Speed: "), Component.literal("x"), 0.1, 10.0, AASConfig.DIGGING_SPEED_MULTIPLIER.get(), 0.1, 1, true);
        this.addRenderableWidget(diggingSpeedSlider);

        // --- Блок 2 (Числа) - Сдвинут вниз из-за новой строки выше ---
        y = 145;
        int c1 = cx - 190;
        int c2 = cx - 60;
        int c3 = cx + 70;

        hubSpawnCostBox = createIntBox(c1 + 85, y, AASConfig.HUB_SPAWN_MATERIAL_COST.get());
        hubResupplyBox = createIntBox(c2 + 85, y, AASConfig.HUB_RESUPPLY_COST.get());
        maxHubsBox = createIntBox(c3 + 85, y, AASConfig.MAX_HUBS_PER_TEAM.get());

        y += 22;
        hubDistBox = createIntBox(c1 + 85, y, AASConfig.MIN_HUB_DISTANCE.get());
        rallyDistBox = createIntBox(c2 + 85, y, AASConfig.MIN_RALLY_POINT_DISTANCE.get());
        hubSoundRadBox = createIntBox(c3 + 85, y, AASConfig.HUB_SOUND_RADIUS.get());

        y += 22;
        hubBlockRadBox = createIntBox(c1 + 85, y, AASConfig.HUB_BLOCK_RADIUS.get());
        rallyBlockRadBox = createIntBox(c2 + 85, y, AASConfig.RALLY_BLOCK_RADIUS.get());
        hubBuildRadBox = createIntBox(c3 + 85, y, AASConfig.HUB_BUILD_RADIUS.get());

        y += 22;
        hubEnemyCountBox = createIntBox(c1 + 85, y, AASConfig.HUB_BLOCK_ENEMY_COUNT.get());
        rallyEnemyCountBox = createIntBox(c2 + 85, y, AASConfig.RALLY_BLOCK_ENEMY_COUNT.get());
        crateBuildRadBox = createIntBox(c3 + 85, y, AASConfig.CRATE_BUILD_RADIUS.get());

        // --- Блок 3 (Имена) ---
        y = 240;
        blueNameBox = new EditBox(this.font, lx, y, 150, 20, Component.literal("Blue Name"));
        blueNameBox.setValue(AASConfig.BLUE_TEAM_CUSTOM_NAME.get());
        this.addRenderableWidget(blueNameBox);

        redNameBox = new EditBox(this.font, rx, y, 150, 20, Component.literal("Red Name"));
        redNameBox.setValue(AASConfig.RED_TEAM_CUSTOM_NAME.get());
        this.addRenderableWidget(redNameBox);

        int btnY = this.height - 25;
        this.addRenderableWidget(Button.builder(Component.literal("Save & Exit"), b -> {
            saveValues();
            this.onClose();
        }).bounds(cx - 60, btnY, 120, 20).build());
    }

    private EditBox createIntBox(int x, int y, int val) {
        EditBox box = new EditBox(this.font, x, y, 40, 20, Component.empty());
        box.setValue(String.valueOf(val));
        box.setFilter(s -> s.matches("\\d*"));
        this.addRenderableWidget(box);
        return box;
    }

    private void saveValues() {
        try {
            AASConfig.DIGGING_SPEED_MULTIPLIER.set(diggingSpeedSlider.getValue());
            // Булевы сохраняются сразу в кнопках, здесь только EditBox-ы
            if(!hubSpawnCostBox.getValue().isEmpty()) AASConfig.HUB_SPAWN_MATERIAL_COST.set(Integer.parseInt(hubSpawnCostBox.getValue()));
            if(!hubResupplyBox.getValue().isEmpty()) AASConfig.HUB_RESUPPLY_COST.set(Integer.parseInt(hubResupplyBox.getValue()));
            if(!maxHubsBox.getValue().isEmpty()) AASConfig.MAX_HUBS_PER_TEAM.set(Integer.parseInt(maxHubsBox.getValue()));
            if(!hubDistBox.getValue().isEmpty()) AASConfig.MIN_HUB_DISTANCE.set(Integer.parseInt(hubDistBox.getValue()));
            if(!rallyDistBox.getValue().isEmpty()) AASConfig.MIN_RALLY_POINT_DISTANCE.set(Integer.parseInt(rallyDistBox.getValue()));
            if(!hubSoundRadBox.getValue().isEmpty()) AASConfig.HUB_SOUND_RADIUS.set(Integer.parseInt(hubSoundRadBox.getValue()));
            if(!hubBlockRadBox.getValue().isEmpty()) AASConfig.HUB_BLOCK_RADIUS.set(Integer.parseInt(hubBlockRadBox.getValue()));
            if(!rallyBlockRadBox.getValue().isEmpty()) AASConfig.RALLY_BLOCK_RADIUS.set(Integer.parseInt(rallyBlockRadBox.getValue()));
            if(!hubBuildRadBox.getValue().isEmpty()) AASConfig.HUB_BUILD_RADIUS.set(Integer.parseInt(hubBuildRadBox.getValue()));
            if(!hubEnemyCountBox.getValue().isEmpty()) AASConfig.HUB_BLOCK_ENEMY_COUNT.set(Integer.parseInt(hubEnemyCountBox.getValue()));
            if(!rallyEnemyCountBox.getValue().isEmpty()) AASConfig.RALLY_BLOCK_ENEMY_COUNT.set(Integer.parseInt(rallyEnemyCountBox.getValue()));
            if(!crateBuildRadBox.getValue().isEmpty()) AASConfig.CRATE_BUILD_RADIUS.set(Integer.parseInt(crateBuildRadBox.getValue()));

            AASConfig.BLUE_TEAM_CUSTOM_NAME.set(blueNameBox.getValue());
            AASConfig.RED_TEAM_CUSTOM_NAME.set(redNameBox.getValue());

            AASConfig.SPEC.save();
        } catch(Exception ignored){}
    }

    @Override
    public void render(GuiGraphics gui, int mx, int my, float pt) {
        this.renderBackground(gui);
        int cx = width / 2;
        gui.drawCenteredString(font, title, cx, 8, 0xFFFFFF);

        int alpha = 0x60000000;
        // Подгоняем рамки под новое положение элементов
        gui.fill(cx - 165, 17, cx + 165, 140, alpha); // Gameplay
        gui.fill(cx - 200, 142, cx + 200, 235, alpha); // Balance
        gui.fill(cx - 165, 237, cx + 165, 265, alpha); // Names

        int c1 = cx - 190;
        int c2 = cx - 60;
        int c3 = cx + 70;

        int row1 = 151; // Начало текстовых подписей второго блока
        int txtColor = 0xDDDDDD;

        gui.drawString(font, "Spawn Mats:", c1, row1, txtColor, false);
        gui.drawString(font, "Resup. Mats:", c2, row1, txtColor, false);
        gui.drawString(font, "Max FOBs:", c3, row1, txtColor, false);

        int r2 = row1 + 22;
        gui.drawString(font, "FOB Min Dist:", c1, r2, txtColor, false);
        gui.drawString(font, "Rally Min Dist:", c2, r2, txtColor, false);
        gui.drawString(font, "Sound Radius:", c3, r2, txtColor, false);

        int r3 = r2 + 22;
        gui.drawString(font, "FOB Blk Rad:", c1, r3, txtColor, false);
        gui.drawString(font, "Rally Blk Rad:", c2, r3, txtColor, false);
        gui.drawString(font, "FOB Build Rad:", c3, r3, txtColor, false);

        int r4 = r3 + 22;
        gui.drawString(font, "FOB Blk Enem:", c1, r4, txtColor, false);
        gui.drawString(font, "Rally Blk Enem:", c2, r4, txtColor, false);
        gui.drawString(font, "Crate Bld Rad:", c3, r4, txtColor, false);

        super.render(gui, mx, my, pt);
    }

    @Override
    public void onClose() { this.minecraft.setScreen(parentScreen); }
}