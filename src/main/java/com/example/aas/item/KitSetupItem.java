package com.example.aas.item;

import com.example.aas.client.ClientHooks;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

public class KitSetupItem extends Item {
    public KitSetupItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // На сервере ничего не делаем, только на клиенте
        if (level.isClientSide && player.isCreative()) {
            // Используем DistExecutor, чтобы вызвать метод, который находится в клиентском классе
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientHooks::openKitTeamSelect);
        }
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }
}