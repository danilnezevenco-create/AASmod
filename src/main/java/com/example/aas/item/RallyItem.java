package com.example.aas.item;

import com.example.aas.sound.ModSounds;
import com.example.aas.client.ClientHooks;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Consumer;

public class RallyItem extends Item implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public RallyItem() {
        super(new Properties().stacksTo(1));
        // Регистрация для синхронизации анимаций между сервером и клиентом
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    // Метод для присвоения уникального ID предмету (нужно для GeckoLib)
    private long getOrAssignID(ItemStack stack, Level level) {
        if (!stack.getOrCreateTag().contains("GeckoLibID")) {
            stack.getOrCreateTag().putLong("GeckoLibID", level.getRandom().nextLong());
        }
        return stack.getOrCreateTag().getLong("GeckoLibID");
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // Обязательно вызываем получение ID при клике
        getOrAssignID(stack, level);

        if (!level.isClientSide) {
            // Звук на сервере
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    ModSounds.RADIO_OPEN.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        } else {
            // Логика открытия меню (защищена через ClientHooks)
            com.example.aas.client.ClientHooks.tryOpenRadioMenu(player);
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity) {
        // Отключаем стандартный взмах рукой Minecraft
        return true;
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        // Используем прослойку ClientItemExtensions, чтобы сервер не крашился при загрузке рендерера
        consumer.accept(com.example.aas.client.ClientItemExtensions.RALLY_RADIO);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "RadioController", 5, event -> PlayState.CONTINUE)
                .triggerableAnim("deploy", RawAnimation.begin().thenPlayAndHold("animation.radio.deploy"))
                .triggerableAnim("close", RawAnimation.begin().thenPlay("animation.radio.close"))
        );
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}