package com.example.aas.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import java.util.List;

public class SupplyTruckMarkerItem extends Item {
    private final String team;
    private final int penalty;       // <-- НОВОЕ ПОЛЕ
    private final String vehicleType; // <-- НОВОЕ ПОЛЕ

    // Обновленный конструктор
    public SupplyTruckMarkerItem(String team, int penalty, String vehicleType) {
        super(new Properties().stacksTo(1));
        this.team = team;
        this.penalty = penalty;
        this.vehicleType = vehicleType;
    }

    public String getTeam() { return team; }
    public int getPenalty() { return penalty; }
    public String getVehicleType() { return vehicleType; }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        ChatFormatting color = team.equals("BLUE") ? ChatFormatting.BLUE : ChatFormatting.RED;
        tooltip.add(Component.literal("Team: " + team).withStyle(color));
        tooltip.add(Component.literal("Type: " + vehicleType).withStyle(ChatFormatting.GRAY));

        // Заменили на динамическое значение:
        tooltip.add(Component.literal("Max " + com.example.aas.config.AASConfig.SUPPLY_TRUCK_CRATES.get() + " Crates. Press X to drop.").withStyle(ChatFormatting.YELLOW));

        tooltip.add(Component.literal("Loss Penalty: -" + penalty + " Tickets").withStyle(ChatFormatting.RED));
        tooltip.add(Component.literal("thank exactly").withStyle(ChatFormatting.DARK_PURPLE).withStyle(ChatFormatting.ITALIC));
    }
}