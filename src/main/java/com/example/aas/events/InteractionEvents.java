// FILE: InteractionEvents.java
// PATH: src\main\java\com\example\aas\events\InteractionEvents.java
package com.example.aas.events;

import com.example.aas.block.*;
import com.example.aas.item.AGSAmmoItem;
import com.example.aas.item.M2AmmoItem;
import com.example.aas.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import com.example.aas.world.AASWorldData;
import com.example.aas.block.ModBlocks;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.tags.BlockTags;
import net.minecraftforge.common.Tags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

@Mod.EventBusSubscriber(modid = "aas", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class InteractionEvents {

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (com.example.aas.config.AASConfig.PREVENT_BLOCK_BREAKING.get()) {
            Player player = event.getEntity();
            if (player.isCreative()) return;

            Level level = event.getLevel();
            boolean isStarted = false;

            // Проверка на сервере
            if (level instanceof ServerLevel serverLevel) {
                isStarted = com.example.aas.world.AASWorldData.get(serverLevel).isGameStarted;
            }
            // Проверка на клиенте (используем безопасный способ, чтобы сервер не крашнулся)
            else if (level.isClientSide) {
                isStarted = com.example.aas.client.ClientData.isGameStarted;
            }

            if (isStarted) {
                BlockState state = level.getBlockState(event.getPos());

                // Если блока НЕТ в белом списке — запрещаем по нему даже стучать
                if (!isBlockWhitelisted(state)) {
                    event.setCanceled(true);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getPlacedBlock().is(ModBlocks.GAME_START_TRIGGER.get())) {
            if (event.getLevel() instanceof ServerLevel level) {
                AASWorldData.get(level).triggerBlocks.add(event.getPos());
                AASWorldData.get(level).setDirty();
            }
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        // 1. Логика удаления триггеров (обязательная)
        if (event.getState().is(ModBlocks.GAME_START_TRIGGER.get())) {
            if (event.getLevel() instanceof ServerLevel level) {
                AASWorldData.get(level).triggerBlocks.remove(event.getPos());
                AASWorldData.get(level).setDirty();
            }
        }

        // 2. Логика защиты от ломания блоков (по конфигу)
        if (com.example.aas.config.AASConfig.PREVENT_BLOCK_BREAKING.get()) {
            Player player = event.getPlayer();
            if (!player.isCreative()) {
                if (event.getLevel() instanceof ServerLevel serverLevel) {
                    boolean isStarted = com.example.aas.world.AASWorldData.get(serverLevel).isGameStarted;

                    if (isStarted) {
                        // ПРОВЕРКА БЕЛОГО СПИСКА
                        if (!isBlockWhitelisted(event.getState())) {
                            event.setCanceled(true);
                        }
                    }
                }
            }
        }
    }

    // Вспомогательный метод для определения блоков, которые МОЖНО ломать во время игры
    private static boolean isBlockWhitelisted(BlockState state) {
        // 1. ИСКЛЮЧЕНИЕ: ТРАВА, ЦВЕТЫ И КУСТЫ
        // BlockTags.REPLACEABLE включает в себя траву, высокую траву, папоротники и т.д.
        if (state.is(BlockTags.REPLACEABLE) || state.is(BlockTags.FLOWERS) || state.is(BlockTags.CROPS)) {
            return true;
        }

        // 2. ИСКЛЮЧЕНИЕ: ГОСТ-БЛОКИ (Чертежи)
        // Проверяем блоки, у которых есть стадия строительства
        if (state.hasProperty(WallBlock.CONSTRUCTED) && !state.getValue(WallBlock.CONSTRUCTED)) {
            return true; // Недостроенная стена
        }
        if (state.hasProperty(BarbedWireBlock.CONSTRUCTED) && !state.getValue(BarbedWireBlock.CONSTRUCTED)) {
            return true; // Недостроенная колючка
        }
        if (state.hasProperty(HubBlock.CONSTRUCTED) && !state.getValue(HubBlock.CONSTRUCTED)) {
            return true; // Недостроенный ФОБ (Хаб)
        }

        // Блоки строительства техники (они всегда госты, пока не спавнят энтити)
        if (state.is(ModBlocks.M2_CONSTRUCTION_BLOCK.get()) ||
                state.is(ModBlocks.AGS_CONSTRUCTION_BLOCK.get()) ||
                state.is(ModBlocks.MORTAR_CONSTRUCTION_BLOCK.get()) ||
                state.is(ModBlocks.TOW_CONSTRUCTION_BLOCK.get())) {
            return true;
        }

        // 3. ВАШ СТАРЫЙ БЕЛЫЙ СПИСОК (Готовые постройки)
        boolean isDefense = state.is(ModBlocks.WALL_BLOCK.get()) || state.is(ModBlocks.BARBED_WIRE_BLOCK.get());
        boolean allowDefenses = com.example.aas.config.AASConfig.ALLOW_BREAKING_DEFENSES.get();

        return state.is(ModBlocks.HUB_BLOCK.get()) ||
                state.is(ModBlocks.BLUE_RALLY_BLOCK.get()) ||
                state.is(ModBlocks.RED_RALLY_BLOCK.get()) ||
                state.is(ModBlocks.AMMO_BAG_BLOCK.get()) ||
                (isDefense && allowDefenses) ||
                state.is(Tags.Blocks.GLASS) ||
                state.is(Tags.Blocks.GLASS_PANES) ||
                state.is(BlockTags.IMPERMEABLE);
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;

        Player player = event.getEntity();
        Level level = event.getLevel();
        ItemStack heldItem = event.getItemStack();
        BlockPos clickedPos = event.getPos();
        Direction face = event.getFace();

        BlockState clickedState = level.getBlockState(clickedPos);

        // === ЛОГИКА MAIN SUPPLY BLOCK (Взятие/пополнение кита при ПКМ) ===
        if (clickedState.is(ModBlocks.MAIN_SUPPLY_BLOCK.get())) {
            if (!level.isClientSide) {
                net.minecraft.server.level.ServerPlayer sPlayer = (net.minecraft.server.level.ServerPlayer) player;
                com.example.aas.world.AASWorldData data = com.example.aas.world.AASWorldData.get(sPlayer.serverLevel());

                if (!data.isGameStarted && !sPlayer.isCreative()) {
                    sPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal("Game hasn't started yet!").withStyle(net.minecraft.ChatFormatting.RED));
                    event.setCanceled(true);
                    return;
                }

                long lastMainUse = sPlayer.getPersistentData().getLong("AAS_LastMainResupply");
                long currentTime = sPlayer.level().getGameTime();

                if (!sPlayer.isCreative() && currentTime < lastMainUse + 1200) {
                    long secondsLeft = (lastMainUse + 1200 - currentTime) / 20;
                    sPlayer.displayClientMessage(net.minecraft.network.chat.Component.literal("Main Supply Cooldown: " + secondsLeft + "s")
                            .withStyle(net.minecraft.ChatFormatting.RED), true);
                    event.setCanceled(true);
                    return;
                }

                if (sPlayer.getPersistentData().contains("AAS_PendingKit")) {
                    com.example.aas.network.ResupplyHandler.tryApplyPendingKit(sPlayer, data);
                    sPlayer.getPersistentData().putLong("AAS_LastMainResupply", currentTime);
                } else {
                    String kitName = sPlayer.getPersistentData().getString("AAS_CurrentKit");
                    if (!kitName.isEmpty() && sPlayer.getTeam() != null) {
                        String t = sPlayer.getTeam().getName().toUpperCase();
                        com.example.aas.world.AASWorldData.KitInfo kit = t.equals("BLUE") ? data.blueKits.get(kitName) : data.redKits.get(kitName);
                        if (kit != null) {
                            if (com.example.aas.network.ResupplyHandler.resupplyPlayer(sPlayer, kit, false)) {
                                sPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal("Kit Resupplied!").withStyle(net.minecraft.ChatFormatting.GREEN));
                                sPlayer.level().playSound(null, sPlayer.blockPosition(), net.minecraft.sounds.SoundEvents.ITEM_PICKUP, net.minecraft.sounds.SoundSource.PLAYERS, 1f, 1f);
                                sPlayer.getPersistentData().putLong("AAS_LastMainResupply", currentTime);
                            } else {
                                sPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal("Kit is already full!").withStyle(net.minecraft.ChatFormatting.YELLOW));
                            }
                        }
                    }
                }
            }
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        if (face == null) return;
        BlockPos placePos = clickedPos.relative(face);

        if (MortarShellStackBlock.isMortarItem(heldItem)) {
            if (clickedState.getBlock() == ModBlocks.MORTAR_SHELL_STACK_BLOCK.get()) return;
            if (level.getBlockState(placePos).canBeReplaced() && !level.isOutsideBuildHeight(placePos)) {
                BlockState newState = ModBlocks.MORTAR_SHELL_STACK_BLOCK.get().defaultBlockState()
                        .setValue(MortarShellStackBlock.FACING, player.getDirection().getOpposite());
                if (newState.canSurvive(level, placePos)) {
                    if (!level.isClientSide) {
                        level.setBlock(placePos, newState, 3);
                        level.playSound(null, placePos, SoundEvents.METAL_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
                        if (!player.isCreative()) heldItem.shrink(1);
                    }
                    player.swing(event.getHand());
                    event.setCanceled(true);
                    event.setCancellationResult(InteractionResult.SUCCESS);
                }
            }
        }
        else if (TOWMissileStackBlock.isTOWItem(heldItem)) {
            if (clickedState.getBlock() == ModBlocks.TOW_MISSILE_STACK_BLOCK.get()) return;
            if (level.getBlockState(placePos).canBeReplaced() && !level.isOutsideBuildHeight(placePos)) {
                BlockState newState = ModBlocks.TOW_MISSILE_STACK_BLOCK.get().defaultBlockState()
                        .setValue(TOWMissileStackBlock.FACING, player.getDirection().getOpposite());
                if (newState.canSurvive(level, placePos)) {
                    if (!level.isClientSide) {
                        level.setBlock(placePos, newState, 3);
                        level.playSound(null, placePos, SoundEvents.METAL_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
                        if (!player.isCreative()) heldItem.shrink(1);
                    }
                    player.swing(event.getHand());
                    event.setCanceled(true);
                    event.setCancellationResult(InteractionResult.SUCCESS);
                }
            }
        }
        else if (heldItem.getItem() == ModItems.AGS_AMMO.get()) {
            if (clickedState.getBlock() == ModBlocks.AGS_AMMO_STACK_BLOCK.get()) return;
            if (level.getBlockState(placePos).canBeReplaced() && !level.isOutsideBuildHeight(placePos)) {
                BlockState newState = ModBlocks.AGS_AMMO_STACK_BLOCK.get().defaultBlockState()
                        .setValue(AGSAmmoStackBlock.FACING, player.getDirection().getOpposite());
                if (newState.canSurvive(level, placePos)) {
                    if (!level.isClientSide) {
                        level.setBlock(placePos, newState, 3);
                        BlockEntity be = level.getBlockEntity(placePos);
                        if (be instanceof AmmoStackBlockEntity ammoBe) {
                            int ammo = AGSAmmoItem.getAmmo(heldItem);
                            ammoBe.addAmmoBox(ammo);
                        }
                        level.playSound(null, placePos, SoundEvents.METAL_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
                        if (!player.isCreative()) heldItem.shrink(1);
                    }
                    player.swing(event.getHand());
                    event.setCanceled(true);
                    event.setCancellationResult(InteractionResult.SUCCESS);
                }
            }
        }
        else if (heldItem.getItem() == ModItems.M2_AMMO.get()) {
            if (clickedState.getBlock() == ModBlocks.M2_AMMO_STACK_BLOCK.get()) return;
            if (level.getBlockState(placePos).canBeReplaced() && !level.isOutsideBuildHeight(placePos)) {
                BlockState newState = ModBlocks.M2_AMMO_STACK_BLOCK.get().defaultBlockState()
                        .setValue(M2AmmoStackBlock.FACING, player.getDirection().getOpposite());
                if (newState.canSurvive(level, placePos)) {
                    if (!level.isClientSide) {
                        level.setBlock(placePos, newState, 3);
                        BlockEntity be = level.getBlockEntity(placePos);
                        if (be instanceof AmmoStackBlockEntity ammoBe) {
                            int ammo = M2AmmoItem.getAmmo(heldItem);
                            ammoBe.addAmmoBox(ammo);
                        }
                        level.playSound(null, placePos, SoundEvents.METAL_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
                        if (!player.isCreative()) heldItem.shrink(1);
                    }
                    player.swing(event.getHand());
                    event.setCanceled(true);
                    event.setCancellationResult(InteractionResult.SUCCESS);
                }
            }
        }
    }
    // Метод 1: Блокировка Shift + ПКМ снаружи
    @SubscribeEvent
    public static void onGlobalEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();
        if (!com.example.aas.config.AASConfig.PREVENT_VEHICLE_INVENTORY_ACCESS.get()) return;
        if (player.isCreative() || player.isSpectator()) return;

        if (player.isShiftKeyDown()) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);

            if (event.getLevel().isClientSide) {
                player.displayClientMessage(Component.literal("Inventory access is disabled!")
                        .withStyle(ChatFormatting.RED), true);
            }
        }
    }

    // Метод 2: Блокировка открытия контейнера на сервере (если игрок внутри)
    @SubscribeEvent
    public static void onContainerOpen(net.minecraftforge.event.entity.player.PlayerContainerEvent.Open event) {
        Player player = event.getEntity();
        if (com.example.aas.config.AASConfig.PREVENT_VEHICLE_INVENTORY_ACCESS.get() &&
                !player.isCreative() && player.getVehicle() != null) {

            event.setCanceled(true);
            player.closeContainer();
        }
    }
}