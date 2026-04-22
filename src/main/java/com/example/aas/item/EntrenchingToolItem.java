package com.example.aas.item;

import com.example.aas.block.*; // Импортирует все блоки и TileEntity
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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

public class EntrenchingToolItem extends Item {

    public EntrenchingToolItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        HitResult hit = player.pick(4.5D, 0.0F, false);

        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) hit;
            BlockPos pos = blockHit.getBlockPos();
            BlockState state = level.getBlockState(pos);

            // 1. Проверяем, что это один из наших блоков
            if (state.is(ModBlocks.HUB_BLOCK.get()) ||
                    state.is(ModBlocks.WALL_BLOCK.get()) ||
                    state.is(ModBlocks.BARBED_WIRE_BLOCK.get()) ||
                    state.is(ModBlocks.M2_CONSTRUCTION_BLOCK.get()) ||
                    state.is(ModBlocks.AGS_CONSTRUCTION_BLOCK.get()) ||
                    state.is(ModBlocks.MORTAR_CONSTRUCTION_BLOCK.get()) ||
                    state.is(ModBlocks.TOW_CONSTRUCTION_BLOCK.get())) {

                // 2. ПРОВЕРКА: Если уже построено - копать нельзя
                if (isConstructed(state)) {
                    if (level.isClientSide) {
                        player.displayClientMessage(Component.literal("Structure already built!"), true);
                    }
                    return InteractionResultHolder.fail(player.getItemInHand(hand));
                }

                // 3. Получаем Entity и начинаем действие
                BlockEntity be = level.getBlockEntity(pos);

                // --- HUB ---
                if (be instanceof HubBlockEntity hub) {
                    if (canDigHub(player, hub)) {
                        player.startUsingItem(hand);
                        return InteractionResultHolder.consume(player.getItemInHand(hand));
                    } else {
                        sendEnemyMessage(level, player);
                    }
                }
                // --- WALL ---
                else if (be instanceof WallBlockEntity wall) {
                    if (canDigWall(player, wall)) {
                        player.startUsingItem(hand);
                        return InteractionResultHolder.consume(player.getItemInHand(hand));
                    } else {
                        sendEnemyMessage(level, player);
                    }
                }
                // --- BARBED WIRE ---
                else if (be instanceof BarbedWireBlockEntity wire) {
                    if (canDigWire(player, wire)) {
                        player.startUsingItem(hand);
                        return InteractionResultHolder.consume(player.getItemInHand(hand));
                    } else {
                        sendEnemyMessage(level, player);
                    }
                }
                // --- STATIC WEAPONS (M2, AGS, Mortar, TOW) ---
                // Для техники тоже стоит добавить проверку, но пока разрешаем всем (или по логике ниже)
                else if (be instanceof M2ConstructionBlockEntity ||
                        be instanceof AGSConstructionBlockEntity ||
                        be instanceof MortarConstructionBlockEntity ||
                        be instanceof TOWConstructionBlockEntity) {

                    player.startUsingItem(hand);
                    return InteractionResultHolder.consume(player.getItemInHand(hand));
                }
            }
        }
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int count) {
        if (entity instanceof Player player) {
            HitResult hit = player.pick(4.5D, 0.0F, false);

            if (hit.getType() == HitResult.Type.BLOCK) {
                BlockHitResult blockHit = (BlockHitResult) hit;
                BlockPos pos = blockHit.getBlockPos();
                BlockState state = level.getBlockState(pos);

                // 1. Если блок достроился в процессе копания - останавливаемся
                if (isConstructed(state)) {
                    player.stopUsingItem();
                    return;
                }

                BlockEntity be = level.getBlockEntity(pos);
                boolean isValidTarget = false;

                // --- HUB LOGIC ---
                if (be instanceof HubBlockEntity hub) {
                    if (canDigHub(player, hub)) {
                        isValidTarget = true;
                        if (!level.isClientSide) {
                            hub.addProgress();
                            if (player.isCreative()) hub.addCreativeProgress(40);
                        }
                    }
                }
                // --- WALL LOGIC ---
                else if (be instanceof WallBlockEntity wall) {
                    if (canDigWall(player, wall)) {
                        isValidTarget = true;
                        if (!level.isClientSide) {
                            wall.addProgress();
                            if (player.isCreative()) wall.addCreativeProgress(40);
                        }
                    }
                }
                // --- WIRE LOGIC ---
                else if (be instanceof BarbedWireBlockEntity wire) {
                    if (canDigWire(player, wire)) {
                        isValidTarget = true;
                        if (!level.isClientSide) {
                            wire.addProgress();
                            if (player.isCreative()) wire.addCreativeProgress(40);
                        }
                    }
                }
                // --- M2 BROWNING LOGIC ---
                else if (be instanceof M2ConstructionBlockEntity m2) {
                    // Здесь тоже можно добавить проверку команды, если нужно
                    isValidTarget = true;
                    if (!level.isClientSide) {
                        m2.addProgress();
                        if (player.isCreative()) m2.addCreativeProgress(100);
                    }
                }
                // --- AGS-30 LOGIC ---
                else if (be instanceof AGSConstructionBlockEntity ags) {
                    isValidTarget = true;
                    if (!level.isClientSide) {
                        ags.addProgress();
                        if (player.isCreative()) ags.addCreativeProgress(100);
                    }
                }
                // --- MORTAR LOGIC ---
                else if (be instanceof MortarConstructionBlockEntity mortar) {
                    isValidTarget = true;
                    if (!level.isClientSide) {
                        mortar.addProgress();
                        if (player.isCreative()) mortar.addCreativeProgress(100);
                    }
                }
                // --- TOW LOGIC ---
                else if (be instanceof TOWConstructionBlockEntity tow) {
                    isValidTarget = true;
                    if (!level.isClientSide) {
                        tow.addProgress();
                        if (player.isCreative()) tow.addCreativeProgress(100);
                    }
                }

                if (!isValidTarget) player.stopUsingItem();
            } else {
                // Если игрок отвел взгляд от блока
                player.stopUsingItem();
            }
        }
    }

    // === ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ===

    private boolean isConstructed(BlockState state) {
        if (state.hasProperty(WallBlock.CONSTRUCTED)) {
            return state.getValue(WallBlock.CONSTRUCTED);
        }
        if (state.hasProperty(HubBlock.CONSTRUCTED)) {
            return state.getValue(HubBlock.CONSTRUCTED);
        }
        if (state.hasProperty(BarbedWireBlock.CONSTRUCTED)) {
            return state.getValue(BarbedWireBlock.CONSTRUCTED);
        }
        return false;
    }

    // === ИСПРАВЛЕННЫЕ МЕТОДЫ ПРОВЕРКИ КОМАНДЫ ===

    private boolean canDigHub(Player player, HubBlockEntity hub) {
        if (player.isCreative()) return true; // Креатив может копать все
        if (player.getTeam() == null) return false; // Без команды нельзя в выживании

        String structureTeam = hub.getTeam();
        // Если структура нейтральная, разрешаем копать (или запрещаем, по вашему желанию)
        if (structureTeam.equals("NEUTRAL")) return true;

        return player.getTeam().getName().equalsIgnoreCase(structureTeam);
    }

    private boolean canDigWall(Player player, WallBlockEntity wall) {
        if (player.isCreative()) return true;
        if (player.getTeam() == null) return false;

        String structureTeam = wall.getTeam();
        if (structureTeam.equals("NEUTRAL")) return true;

        return player.getTeam().getName().equalsIgnoreCase(structureTeam);
    }

    private boolean canDigWire(Player player, BarbedWireBlockEntity wire) {
        if (player.isCreative()) return true;
        if (player.getTeam() == null) return false;

        String structureTeam = wire.getTeam();
        if (structureTeam.equals("NEUTRAL")) return true;

        return player.getTeam().getName().equalsIgnoreCase(structureTeam);
    }

    private void sendEnemyMessage(Level level, Player player) {
        if (level.isClientSide) {
            player.displayClientMessage(Component.literal("Cannot build enemy structures!"), true);
        }
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }
}