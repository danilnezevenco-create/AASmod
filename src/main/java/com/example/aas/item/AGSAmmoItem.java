// PATH: src\main\java\com\example\aas\item\AGSAmmoItem.java
package com.example.aas.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import java.util.List;

public class AGSAmmoItem extends Item {
    public static final int MAX_AMMO = 30;

    public AGSAmmoItem() {
        super(new Properties().stacksTo(1));
    }

    public static int getAmmo(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        // Если тега нет, это новая коробка -> полная
        if (tag == null || !tag.contains("Ammo")) {
            return MAX_AMMO;
        }
        // Если тег есть, возвращаем как есть
        return tag.getInt("Ammo");
    }

    public static void setAmmo(ItemStack stack, int amount) {
        if (amount > MAX_AMMO) amount = MAX_AMMO;
        if (amount < 0) amount = 0;
        stack.getOrCreateTag().putInt("Ammo", amount);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) { return true; }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * ((float) getAmmo(stack) / MAX_AMMO));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return net.minecraft.util.Mth.hsvToRgb(Math.max(0.0F, (float) getAmmo(stack) / MAX_AMMO) / 3.0F, 1.0F, 1.0F);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Grenades: " + getAmmo(stack) + " / " + MAX_AMMO));
    }
}