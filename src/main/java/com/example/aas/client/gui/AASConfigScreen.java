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

    private EditBox hubSpawnCostBox, hubResupplyBox, downedTimeBox;
    private EditBox hubDistBox, rallyDistBox, hubSoundRadBox, voteTimeBox;
    private EditBox hubBlockRadBox, rallyBlockRadBox, hubBuildRadBox, votePercentBox;
    private EditBox blueNameBox, redNameBox, reviveItemBox;
    private ForgeSlider diggingSpeedSlider;

    public AASConfigScreen(Screen parentScreen) {
        super(Component.literal("AAS Global Configuration"));
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        super.init();
        int cx = this.width / 2;
        int y = 20;
        int col1 = cx - 155;
        int col2 = cx + 5;

        // --- БЛОК 1: ПЕРЕКЛЮЧАТЕЛИ ---
        addToggle(col1, y, 150, "AGS Destruct", AASConfig.AGS_PROJECTILE_DESTRUCTION);
        addToggle(col2, y, 150, "Ammo Explosion", AASConfig.AMMO_STACK_DESTRUCTION);
        y += 22;
        addToggle(col1, y, 150, "Prevent Break", AASConfig.PREVENT_BLOCK_BREAKING);
        addToggle(col2, y, 150, "Allow Break Def", AASConfig.ALLOW_BREAKING_DEFENSES);
        y += 22;
        addToggle(col1, y, 150, "Prevent Drops", AASConfig.PREVENT_ALL_ITEM_DROPS);
        addToggle(col2, y, 150, "Low Tix Siren", AASConfig.LOW_TICKETS_SIREN);
        y += 22;
        addToggle(col1, y, 150, "Auto SL Radio", AASConfig.AUTO_GIVE_SL_RADIO);
        addToggle(col2, y, 150, "FOB Needs Crate", AASConfig.HUB_PLACEMENT_REQUIRES_CRATE);
        y += 22;
        addToggle(col1, y, 150, "Require Officer", AASConfig.REQUIRE_OFFICER_FOR_SL);
        addToggle(col2, y, 150, "Lock Enemy Veh", AASConfig.PREVENT_ENEMY_VEHICLE_ENTRY);
        y += 22;
        addToggle(col1, y, 150, "Spec. Driving", AASConfig.REQUIRE_SPECIALIST_TO_DRIVE);
        addToggle(col2, y, 150, "Block Veh Inv", AASConfig.PREVENT_VEHICLE_INVENTORY_ACCESS);
        y += 22;
        addToggle(col1, y, 150, "Enable Medic", AASConfig.ENABLE_KNOCKOUT);
        addToggle(col2, y, 150, "Base Healing", AASConfig.MAIN_SUPPLY_HEALING);

        // --- БЛОК 2: СЛАЙДЕР ---
        y += 25;
        diggingSpeedSlider = new ForgeSlider(cx - 155, y, 310, 20, Component.literal("Dig Speed: "), Component.literal("x"), 0.1, 5.0, AASConfig.DIGGING_SPEED_MULTIPLIER.get(), 0.1, 1, true);
        this.addRenderableWidget(diggingSpeedSlider);

        // --- БЛОК 3: ЧИСЛА ---
        y += 35;
        int x1 = cx - 170, x2 = cx - 80, x3 = cx + 10, x4 = cx + 100;

        // Ряд 1 (Оставили пустую колонку x3)
        hubSpawnCostBox = createIntBox(x1, y, AASConfig.HUB_SPAWN_MATERIAL_COST.get());
        hubResupplyBox = createIntBox(x2, y, AASConfig.HUB_RESUPPLY_COST.get());
        downedTimeBox = createIntBox(x4, y, AASConfig.MAX_DOWNED_TIME_SECONDS.get());

        y += 30;
        // Ряд 2
        hubDistBox = createIntBox(x1, y, AASConfig.MIN_HUB_DISTANCE.get());
        rallyDistBox = createIntBox(x2, y, AASConfig.MIN_RALLY_POINT_DISTANCE.get());
        hubSoundRadBox = createIntBox(x3, y, AASConfig.HUB_SOUND_RADIUS.get());
        voteTimeBox = createIntBox(x4, y, AASConfig.VOTE_AUTO_START_TIME.get());

        y += 30;
        // Ряд 3
        hubBlockRadBox = createIntBox(x1, y, AASConfig.HUB_BLOCK_RADIUS.get());
        rallyBlockRadBox = createIntBox(x2, y, AASConfig.RALLY_BLOCK_RADIUS.get());
        hubBuildRadBox = createIntBox(x3, y, AASConfig.HUB_BUILD_RADIUS.get());
        votePercentBox = createIntBox(x4, y, AASConfig.VOTE_REQUIRED_PERCENTAGE.get());

        // --- БЛОК 4: ТЕКСТ ---
        y += 35;
        blueNameBox = createStringBox(cx - 155, y, 150, AASConfig.BLUE_TEAM_CUSTOM_NAME.get());
        redNameBox = createStringBox(cx + 5, y, 150, AASConfig.RED_TEAM_CUSTOM_NAME.get());
        y += 30;
        reviveItemBox = createStringBox(cx - 155, y, 310, AASConfig.REVIVE_ITEM.get());

        // Кнопка сохранения
        this.addRenderableWidget(Button.builder(Component.literal("SAVE SETTINGS"), b -> {
            saveValues();
            this.onClose();
        }).bounds(cx - 80, this.height - 25, 160, 20).build());
    }

    private void addToggle(int x, int y, int w, String label, net.minecraftforge.common.ForgeConfigSpec.BooleanValue val) {
        this.addRenderableWidget(CycleButton.onOffBuilder(val.get())
                .create(x, y, w, 20, Component.literal(label), (b, v) -> val.set(v)));
    }

    private EditBox createIntBox(int x, int y, int val) {
        EditBox box = new EditBox(this.font, x, y, 60, 16, Component.empty());
        box.setValue(String.valueOf(val));
        box.setFilter(s -> s.matches("\\d*"));
        this.addRenderableWidget(box);
        return box;
    }

    private EditBox createStringBox(int x, int y, int w, String val) {
        EditBox box = new EditBox(this.font, x, y, w, 16, Component.empty());
        box.setValue(val);
        this.addRenderableWidget(box);
        return box;
    }

    private void saveValues() {
        try {
            AASConfig.DIGGING_SPEED_MULTIPLIER.set(diggingSpeedSlider.getValue());
            AASConfig.HUB_SPAWN_MATERIAL_COST.set(Integer.parseInt(hubSpawnCostBox.getValue()));
            AASConfig.HUB_RESUPPLY_COST.set(Integer.parseInt(hubResupplyBox.getValue()));
            AASConfig.MAX_DOWNED_TIME_SECONDS.set(Integer.parseInt(downedTimeBox.getValue()));
            AASConfig.MIN_HUB_DISTANCE.set(Integer.parseInt(hubDistBox.getValue()));
            AASConfig.MIN_RALLY_POINT_DISTANCE.set(Integer.parseInt(rallyDistBox.getValue()));
            AASConfig.HUB_SOUND_RADIUS.set(Integer.parseInt(hubSoundRadBox.getValue()));
            AASConfig.VOTE_AUTO_START_TIME.set(Integer.parseInt(voteTimeBox.getValue()));
            AASConfig.HUB_BLOCK_RADIUS.set(Integer.parseInt(hubBlockRadBox.getValue()));
            AASConfig.RALLY_BLOCK_RADIUS.set(Integer.parseInt(rallyBlockRadBox.getValue()));
            AASConfig.HUB_BUILD_RADIUS.set(Integer.parseInt(hubBuildRadBox.getValue()));
            AASConfig.VOTE_REQUIRED_PERCENTAGE.set(Integer.parseInt(votePercentBox.getValue()));
            AASConfig.BLUE_TEAM_CUSTOM_NAME.set(blueNameBox.getValue());
            AASConfig.RED_TEAM_CUSTOM_NAME.set(redNameBox.getValue());
            AASConfig.REVIVE_ITEM.set(reviveItemBox.getValue());
            AASConfig.SPEC.save();
        } catch(Exception ignored){}
    }

    @Override
    public void render(GuiGraphics gui, int mx, int my, float pt) {
        this.renderBackground(gui);
        int cx = width / 2;
        gui.drawCenteredString(font, title, cx, 8, 0xFFFF00);

        int x1 = cx - 170, x2 = cx - 80, x3 = cx + 10, x4 = cx + 100;
        int color = 0xAAAAAA;

        int ly1 = 184, ly2 = 214, ly3 = 244;
        gui.drawString(font, "FOB Mat", x1, ly1, color);
        gui.drawString(font, "Resup", x2, ly1, color);
        // x3 пустует
        gui.drawString(font, "Nok Sec", x4, ly1, color);

        gui.drawString(font, "FOB Dist", x1, ly2, color);
        gui.drawString(font, "Ral Dist", x2, ly2, color);
        gui.drawString(font, "Snd Rad", x3, ly2, color);
        gui.drawString(font, "Vote Min", x4, ly2, color);

        gui.drawString(font, "FOB Blk", x1, ly3, color);
        gui.drawString(font, "Ral Blk", x2, ly3, color);
        gui.drawString(font, "Bld Rad", x3, ly3, color);
        gui.drawString(font, "Vote %", x4, ly3, color);

        gui.drawString(font, "Blue Team Name", cx - 155, 275, 0x5555FF);
        gui.drawString(font, "Red Team Name", cx + 5, 275, 0xFF5555);
        gui.drawString(font, "Revive Item ID", cx - 155, 307, color);

        super.render(gui, mx, my, pt);
    }
}