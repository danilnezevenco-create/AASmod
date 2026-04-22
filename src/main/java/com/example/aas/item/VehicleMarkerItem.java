package com.example.aas.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class VehicleMarkerItem extends Item {
    private final String team; // "BLUE" или "RED"
    private final String type; // Название техники
    private final int penalty; // Цена потери в тикетах

    public VehicleMarkerItem(String team, String type, int penalty) {
        super(new Item.Properties().stacksTo(1));
        this.team = team;
        this.type = type;
        this.penalty = penalty;
    }

    public String getTeam() { return team; }
    public String getType() { return type; }
    public int getPenalty() { return penalty; }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        ChatFormatting color = team.equals("BLUE") ? ChatFormatting.BLUE : ChatFormatting.RED;
        tooltip.add(Component.literal("Team: " + team).withStyle(color));
        tooltip.add(Component.literal("Type: " + type).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Loss Penalty: -" + penalty + " Tickets").withStyle(ChatFormatting.DARK_RED));
    }
}