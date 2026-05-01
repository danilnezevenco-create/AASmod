package com.example.aas.item;

import com.example.aas.block.*;
import com.example.aas.client.renderer.EntrenchingToolRenderer;
import com.example.aas.sound.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.*;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;

import java.util.function.Consumer;

public class EntrenchingToolItem extends Item implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public EntrenchingToolItem() {
        super(new Properties().stacksTo(1));
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    private long getOrAssignID(ItemStack stack, Level level) {
        if (!stack.getOrCreateTag().contains("GeckoLibID")) {
            stack.getOrCreateTag().putLong("GeckoLibID", level.getRandom().nextLong());
        }
        return stack.getOrCreateTag().getLong("GeckoLibID");
    }

    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity) {
        return true;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        HitResult hit = player.pick(4.5D, 0.0F, false);

        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockState state = level.getBlockState(((BlockHitResult) hit).getBlockPos());

            if (isAASConstruction(state) && !isConstructed(state)) {
                if (!level.isClientSide) {
                    long id = getOrAssignID(itemstack, level);
                    this.triggerAnim(player, id, "ShovelController", "dig");
                }
                player.startUsingItem(hand);
                return InteractionResultHolder.consume(itemstack);
            }
        }
        return InteractionResultHolder.pass(itemstack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int count) {
        if (entity instanceof Player player && !level.isClientSide) {
            HitResult hit = player.pick(4.5D, 0.0F, false);
            if (hit instanceof BlockHitResult blockHit) {
                BlockPos pos = blockHit.getBlockPos();
                BlockState state = level.getBlockState(pos);
                BlockEntity be = level.getBlockEntity(pos);

                if (isAASConstruction(state)) {
                    // 1. НЕЛЬЗЯ КОПАТЬ УЖЕ ПОСТРОЕННОЕ
                    if (isConstructed(state)) {
                        player.displayClientMessage(Component.literal("Structure is already finished!").withStyle(ChatFormatting.YELLOW), true);
                        stopDigging(player, stack);
                        return;
                    }

                    // 2. НЕЛЬЗЯ КОПАТЬ ВРАЖЕСКОЕ
                    String playerTeam = player.getTeam() != null ? player.getTeam().getName() : "NEUTRAL";
                    String structureTeam = getStructureTeam(be);

                    if (!structureTeam.equals("NEUTRAL") && !structureTeam.equalsIgnoreCase(playerTeam) && !player.isCreative()) {
                        player.displayClientMessage(Component.literal("Cannot build ENEMY structures!").withStyle(ChatFormatting.RED), true);
                        stopDigging(player, stack);
                        return;
                    }

                    // Эффекты
                    int elapsed = getUseDuration(stack) - count;
                    if (elapsed % 20 == 10) {
                        level.playSound(null, player.getX(), player.getY(), player.getZ(), ModSounds.SHOVEL_DIG.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
                        ((ServerLevel)level).sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, 12, 0.2, 0.2, 0.2, 0.1);
                    }

                    // 4. В КРЕАТИВЕ Х10 СКОРОСТЬ
                    if (player.isCreative()) {
                        addCreativeProgressToBE(be, 10);
                    } else {
                        addProgressToBE(be, player);
                    }

                } else {
                    stopDigging(player, stack);
                }
            } else {
                stopDigging(player, stack);
            }
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        stopDigging(entity, stack);
    }

    private void stopDigging(LivingEntity entity, ItemStack stack) {
        if (entity instanceof Player player && !player.level().isClientSide) {
            long id = getOrAssignID(stack, player.level());
            this.triggerAnim(player, id, "ShovelController", "stop");
            player.stopUsingItem();
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "ShovelController", 5, event -> PlayState.CONTINUE)
                .triggerableAnim("dig", RawAnimation.begin().thenLoop("animation.shovel.dig"))
                .triggerableAnim("stop", RawAnimation.begin().thenPlay("animation.nothing"))
        );
    }

    private boolean isAASConstruction(BlockState state) {
        return state.is(ModBlocks.HUB_BLOCK.get()) || state.is(ModBlocks.WALL_BLOCK.get()) ||
                state.is(ModBlocks.BARBED_WIRE_BLOCK.get()) || state.is(ModBlocks.M2_CONSTRUCTION_BLOCK.get()) ||
                state.is(ModBlocks.AGS_CONSTRUCTION_BLOCK.get()) || state.is(ModBlocks.MORTAR_CONSTRUCTION_BLOCK.get()) ||
                state.is(ModBlocks.TOW_CONSTRUCTION_BLOCK.get());
    }

    private String getStructureTeam(BlockEntity be) {
        if (be instanceof HubBlockEntity h) return h.getTeam();
        if (be instanceof WallBlockEntity w) return w.getTeam();
        if (be instanceof BarbedWireBlockEntity b) return b.getTeam();
        if (be instanceof AGSConstructionBlockEntity a) return a.getTeam();
        if (be instanceof M2ConstructionBlockEntity m) return m.getTeam();
        if (be instanceof MortarConstructionBlockEntity mo) return mo.getTeam();
        if (be instanceof TOWConstructionBlockEntity t) return t.getTeam();
        return "NEUTRAL";
    }

    private void addCreativeProgressToBE(BlockEntity be, int multiplier) {
        if (be instanceof HubBlockEntity b) b.addCreativeProgress(multiplier);
        else if (be instanceof WallBlockEntity b) b.addCreativeProgress(multiplier);
        else if (be instanceof BarbedWireBlockEntity b) b.addCreativeProgress(multiplier);
        else if (be instanceof AGSConstructionBlockEntity b) b.addCreativeProgress(multiplier);
        else if (be instanceof M2ConstructionBlockEntity b) b.addCreativeProgress(multiplier);
        else if (be instanceof MortarConstructionBlockEntity b) b.addCreativeProgress(multiplier);
        else if (be instanceof TOWConstructionBlockEntity b) b.addCreativeProgress(multiplier);
    }

    private boolean isConstructed(BlockState state) {
        if (state.hasProperty(WallBlock.CONSTRUCTED)) return state.getValue(WallBlock.CONSTRUCTED);
        if (state.hasProperty(HubBlock.CONSTRUCTED)) return state.getValue(HubBlock.CONSTRUCTED);
        if (state.hasProperty(BarbedWireBlock.CONSTRUCTED)) return state.getValue(BarbedWireBlock.CONSTRUCTED);
        return false;
    }

    private void addProgressToBE(BlockEntity be, Player player) {
        if (be instanceof HubBlockEntity b) b.addProgress();
        else if (be instanceof WallBlockEntity b) b.addProgress();
        else if (be instanceof BarbedWireBlockEntity b) b.addProgress();
        else if (be instanceof AGSConstructionBlockEntity b) b.addProgress();
        else if (be instanceof M2ConstructionBlockEntity b) b.addProgress();
        else if (be instanceof MortarConstructionBlockEntity b) b.addProgress();
        else if (be instanceof TOWConstructionBlockEntity b) b.addProgress();
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(com.example.aas.client.ClientItemExtensions.ENTRENCHING_TOOL);
    }
}