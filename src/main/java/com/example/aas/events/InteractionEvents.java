// FILE: InteractionEvents.java
// PATH: src\main\java\com\example\aas\events\InteractionEvents.java
package com.example.aas.events;

import com.example.aas.block.*;
import com.example.aas.block.WallBlock;
import com.example.aas.config.AASConfig;
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
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.tags.BlockTags;
import net.minecraftforge.common.Tags;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraft.world.level.block.EnchantmentTableBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.BeaconBlock;

import java.util.Set;

@Mod.EventBusSubscriber(modid = "aas", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class InteractionEvents {

    // РЎРїРёСЃРѕРє СЂР°Р·СЂРµС€РµРЅРЅС‹С… СЂР°Р·РІР»РµРєР°С‚РµР»СЊРЅС‹С… СЃСѓС‰РЅРѕСЃС‚РµР№ (РіРґРµ РЅСѓР¶РЅРѕ РѕС‚РєСЂС‹РІР°С‚СЊ РёРЅРІРµРЅС‚Р°СЂСЊ С‚РµС…РЅРёРєРё)
    private static final Set<String> ALLOWED_INVENTORY_ENTITIES = Set.of(
            "superbwarfare:mortar",
            "superbwarfare:drone",
            "wrbdrones:fpv_drone",
            "wrbdrones:mavic_drone_no_drop",
            "wrbdrones:mavic_drone_with_drop",
            "aas:supply_crate",
            "vvp:mi8_mtv3",
            "vvp:mi8",
            "vvp:mi8_amtsh",
            "aas:ags_30",
            "aas:m2_browning"
    );

    /**
     * Р“Р»РѕР±Р°Р»СЊРЅР°СЏ Р±Р»РѕРєРёСЂРѕРІРєР° Shift + РџРљРњ РїРѕ СЃСѓС‰РЅРѕСЃС‚Рё (С‚РµС…РЅРёРєР°, РіР»СѓС€РёРј РґРѕСЃС‚СѓРї Рє РёРЅРІРµРЅС‚Р°СЂСЋ СЃРєРІРѕР·СЊ С‚РµС…Р±Р°Р·Сѓ)
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onGlobalEntityInteract(PlayerInteractEvent.EntityInteract event) {
        // РћР±СЂР°Р±Р°С‚С‹РІР°РµРј С‚РѕР»СЊРєРѕ РіР»Р°РІРЅСѓСЋ СЂСѓРєСѓ, С‡С‚РѕР±С‹ РЅРµ СЃСЂР°Р±Р°С‚С‹РІР°Р»Рѕ РґРІР°Р¶РґС‹ (РіР»Р°РІРЅР°СЏ СЂСѓРєР° + РѕС„С„С…РµРЅРґ)
        if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;

        Player player = event.getEntity();
        if (player.isCreative() || player.isSpectator()) return;

        if (player.isShiftKeyDown()) {
            Entity target = event.getTarget();

            // РќРћР’РћР•: РёРіСЂРѕРєРѕРІ РЅРµ СЃС‡РёС‚Р°РµРј "С‚РµС…РЅРёРєРѕР№" - РїСЂРѕРїСѓСЃРєР°РµРј РїРѕР»РЅРѕСЃС‚СЊСЋ,
            // С‡С‚РѕР±С‹ СЂР°Р±РѕС‚Р°Р»Рё Revive (РџРљРњ РїСЂРµРґРјРµС‚РѕРј) Рё Carry/Drag (Shift+РџРљРњ) РёР· DownedHandler
            if (target instanceof Player) {
                return;
            }

            ResourceLocation entityKey = ForgeRegistries.ENTITY_TYPES.getKey(target.getType());

            // Р•СЃР»Рё СЌС‚Рѕ РІ СЃРїРёСЃРєРµ РёСЃРєР»СЋС‡РµРЅРёР№ (РјРѕСЂС‚РёСЂР°/РґСЂРѕРЅ) - СЂР°Р·СЂРµС€Р°РµРј РІР°РЅРёР»СЊРЅРѕРµ РІР·Р°РёРјРѕРґРµР№СЃС‚РІРёРµ
            if (entityKey != null && ALLOWED_INVENTORY_ENTITIES.contains(entityKey.toString())) {
                return;
            }

            // === Р”РћРџРћР›РќРРўР•Р›Р¬РќРћР• РџРћРџРћР›РќР•РќРР• РљРРўРђ (С‚РѕР»СЊРєРѕ РЅР° С‚РµС…РЅРёРєРµ) ===
            if (!event.getLevel().isClientSide) {
                if (target.getPersistentData().contains("AAS_VehicleTeam")) {
                    String vType = target.getPersistentData().getString("AAS_VehicleType");
                    String vTeam = target.getPersistentData().getString("AAS_VehicleTeam");
                    String pTeam = player.getTeam() != null ? player.getTeam().getName() : "";

                    // РџСЂРѕРІРµСЂСЏРµРј: РєРѕРјР°РЅРґР° СЃРѕРІРїР°РґР°РµС‚ Рё СЌС‚Рѕ РЅРµ Static ZU
                    if (!vType.equalsIgnoreCase("Static ZU") && vTeam.equalsIgnoreCase(pTeam)) {
                        ServerPlayer sPlayer = (ServerPlayer) player;

                        int currentMats = target.getPersistentData().getInt("AAS_VehicleMats");

                        // 1. РћРїСЂРµРґРµР»СЏРµРј РєР°РєРѕР№ РєРёС‚ РёРіСЂРѕРєР° (РЅСѓР¶РЅРѕ СЂР°РЅСЊС€Рµ, С‡С‚РѕР±С‹ Р·РЅР°С‚СЊ СЃС‚РѕРёРјРѕСЃС‚СЊ РїРѕРїРѕР»РЅРµРЅРёСЏ)
                        String pendingKit = sPlayer.getPersistentData().getString("AAS_PendingKit");
                        String currentKit = sPlayer.getPersistentData().getString("AAS_CurrentKit");
                        boolean hasPending = !pendingKit.isEmpty();
                        String targetKitName = hasPending ? pendingKit : currentKit;

                        if (targetKitName == null || targetKitName.isEmpty() || targetKitName.equals("Unassigned")) {
                            sPlayer.displayClientMessage(Component.translatable("aas.msg.no_kit").withStyle(ChatFormatting.RED), true);
                            event.setCanceled(true);
                            event.setCancellationResult(InteractionResult.SUCCESS);
                            return;
                        }

                        // 2. РЎС‚РѕРёРјРѕСЃС‚СЊ: РґР»СЏ "Drone Operator" РїРѕРїРѕР»РЅРµРЅРёСЏ СЃРІРѕР№ droneOperatorCrateResupplyCost, Сѓ РѕСЃС‚Р°Р»СЊРЅС‹С… - РѕР±С‹С‡РЅР°СЏ hubResupplyCost
                        int cost;
                        if ("Drone Operator".equalsIgnoreCase(targetKitName)) {
                            cost = AASConfig.DRONE_OPERATOR_VEHICLE_RESUPPLY_COST.get();
                        } else if ("Sapper".equalsIgnoreCase(targetKitName)) {
                            cost = AASConfig.SAPPER_VEHICLE_RESUPPLY_COST.get();
                        } else if ("Rifleman".equalsIgnoreCase(targetKitName)) {
                            cost = AASConfig.RIFLEMAN_CRATE_RESUPPLY_COST.get();
                        } else {
                            cost = AASConfig.HUB_RESUPPLY_COST.get();
                        }

                        // 3. РҐРІР°С‚Р°РµС‚ Р»Рё РјР°С‚РµСЂРёР°Р»РѕРІ РІ С‚РµС…РЅРёРєРµ?
                        if (currentMats < cost) {
                            sPlayer.displayClientMessage(Component.translatable("aas.msg.not_enough_vehicle_mats", currentMats).withStyle(ChatFormatting.RED), true);
                            event.setCanceled(true);
                            event.setCancellationResult(InteractionResult.SUCCESS);
                            return;
                        }

                        // 4. Р’С‹РїРѕР»РЅСЏРµРј СЃРјРµРЅСѓ РєРёС‚Р° РёР»Рё РїРѕРїРѕР»РЅРµРЅРёРµ
                        AASWorldData data = AASWorldData.get(sPlayer.serverLevel());

                        if (hasPending) {
                            // РРіСЂРѕРє РІС‹Р±СЂР°Р» РЅРѕРІС‹Р№ РєРёС‚ -> РІС‹РґР°РµРј РїРѕР»РЅРѕСЃС‚СЊСЋ СЃРЅР°СЂСЏР¶РµРЅРёРµ
                            com.example.aas.network.ResupplyHandler.tryApplyPendingKit(sPlayer, data);

                            if (!sPlayer.isCreative()) {
                                target.getPersistentData().putInt("AAS_VehicleMats", currentMats - cost);
                            }
                            sPlayer.displayClientMessage(Component.translatable("aas.msg.new_kit_mats", (currentMats - cost)).withStyle(ChatFormatting.GREEN), true);
                            sPlayer.level().playSound(null, sPlayer.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 1f, 1f);
                        } else {
                            // РЈ РёРіСЂРѕРєР° РЅРµС‚ РѕР¶РёРґР°СЋС‰РµРіРѕ РєРёС‚Р° РІ РѕР¶РёРґР°РЅРёРё -> РїСЂРѕСЃС‚Рѕ РїРѕРїРѕР»РЅСЏРµРј С‚РµРєСѓС‰РёР№
                            boolean isAltVariant = sPlayer.getPersistentData().getBoolean("AAS_CurrentKitAlt");
                            AASWorldData.KitInfo kit = data.getKitVariant(pTeam, currentKit, isAltVariant);
                            if (kit != null) {
                                if (com.example.aas.network.ResupplyHandler.resupplyPlayer(sPlayer, kit, false)) {
                                    if (!sPlayer.isCreative()) {
                                        target.getPersistentData().putInt("AAS_VehicleMats", currentMats - cost);
                                    }
                                    sPlayer.displayClientMessage(Component.translatable("aas.msg.kit_resupplied_mats", (currentMats - cost)).withStyle(ChatFormatting.GREEN), true);
                                    sPlayer.level().playSound(null, sPlayer.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 1f, 1f);
                                } else {
                                    sPlayer.displayClientMessage(Component.translatable("aas.msg.kit_full_mats", currentMats).withStyle(ChatFormatting.YELLOW), true);
                                }
                            }
                        }

                        event.setCanceled(true);
                        event.setCancellationResult(InteractionResult.SUCCESS);
                        return;
                    }
                }
            }

            // Р•СЃР»Рё С‚РµС…РЅРёРєР° С‡СѓР¶Р°СЏ, Р»РёР±Рѕ РІР·Р°РёРјРѕРґРµР№СЃС‚РІРёРµ РїСѓСЃС‚РѕРµ - РїСЂРѕСЃС‚Рѕ Р±Р»РѕРєРёСЂСѓРµРј РёРЅРІРµРЅС‚Р°СЂСЊ РІР·Р°РёРјРѕРґРµР№СЃС‚РІРёСЏ
            if (AASConfig.PREVENT_VEHICLE_INVENTORY_ACCESS.get()) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
                if (event.getLevel().isClientSide) {
                    player.displayClientMessage(Component.translatable("aas.msg.inventory_disabled").withStyle(ChatFormatting.RED), true);
                }
            }
        }
    }
    // Р‘Р»РѕРєРё, РІР·Р°РёРјРѕРґРµР№СЃС‚РІРёРµ СЃ РєРѕС‚РѕСЂС‹РјРё Р·Р°РїСЂРµС‰РµРЅРѕ РєРѕРЅС„РёРіРѕРј restrictInteractionsToDoorsAndGates.
// AbstractChestBlock РїРѕРєСЂС‹РІР°РµС‚ СЃСЂР°Р·Сѓ РѕР±С‹С‡РЅС‹Р№ СЃСѓРЅРґСѓРє, СЃСѓРЅРґСѓРє-Р»РѕРІСѓС€РєСѓ Р СЌРЅРґРµСЂ-СЃСѓРЅРґСѓРє
// (EnderChestBlock С‚РѕР¶Рµ РѕС‚ РЅРµРіРѕ РЅР°СЃР»РµРґСѓРµС‚СЃСЏ), РѕС‚РґРµР»СЊРЅРѕ РµРіРѕ РґРѕР±Р°РІР»СЏС‚СЊ РЅРµ РЅСѓР¶РЅРѕ.
// AbstractFurnaceBlock РїРѕРєСЂС‹РІР°РµС‚ РїРµС‡СЊ, РґРѕРјРµРЅРЅСѓСЋ РїРµС‡СЊ Рё РєРѕРїС‚РёР»СЊРЅСЋ.
// DispenserBlock РїРѕРєСЂС‹РІР°РµС‚ Рё РґРёСЃРїРµРЅСЃРµСЂ, Рё РІС‹Р±СЂР°СЃС‹РІР°С‚РµР»СЊ (DropperBlock РµРіРѕ РЅР°СЃР»РµРґСѓРµС‚).
// AnvilBlock РїРѕРєСЂС‹РІР°РµС‚ С†РµР»СѓСЋ/С‚СЂРµСЃРЅСѓС‚СѓСЋ/РїРѕРІСЂРµР¶РґС‘РЅРЅСѓСЋ РЅР°РєРѕРІР°Р»СЊРЅСЋ (СЌС‚Рѕ РѕРґРёРЅ РєР»Р°СЃСЃ РЅР° РІСЃРµ СЃС‚Р°РґРёРё).
    private static final Set<Class<?>> RESTRICTED_INTERACTION_BLOCK_CLASSES = Set.of(
            AbstractChestBlock.class,
            BarrelBlock.class,
            AbstractFurnaceBlock.class,
            TrapDoorBlock.class,
            AnvilBlock.class,
            ShulkerBoxBlock.class,
            HopperBlock.class,
            DispenserBlock.class,
            BrewingStandBlock.class,
            EnchantmentTableBlock.class,
            GrindstoneBlock.class,
            SmithingTableBlock.class,
            LecternBlock.class,
            BeaconBlock.class
    );

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRestrictedInteraction(PlayerInteractEvent.RightClickBlock event) {
        if (!AASConfig.RESTRICT_INTERACTIONS_TO_DOORS_GATES.get()) return;

        Player player = event.getEntity();

        // РўРѕР»СЊРєРѕ survival/adventure вЂ” РєСЂРµР°С‚РёРІ Рё СЃРїРµРєС‚Р°С‚РѕСЂ РЅРµ С‚СЂРѕРіР°РµРј
        if (player.isCreative() || player.isSpectator()) return;

        Block block = event.getLevel().getBlockState(event.getPos()).getBlock();

        // Р—Р°РїСЂРµС‰Р°РµРј РўРћР›Р¬РљРћ С…СЂР°РЅРёР»РёС‰Р°/Р»СѓС‚-Р±Р»РѕРєРё РёР· С‡С‘СЂРЅРѕРіРѕ СЃРїРёСЃРєР° РІС‹С€Рµ.
        // Р’СЃС‘ РѕСЃС‚Р°Р»СЊРЅРѕРµ (РґРІРµСЂРё, РєР°Р»РёС‚РєРё, РєРЅРѕРїРєРё, СЂС‹С‡Р°РіРё, РІРµСЂСЃС‚Р°Рє Рё С‚.Рґ.) С‚РµРїРµСЂСЊ СЂР°Р·СЂРµС€РµРЅРѕ.
        boolean isRestricted = RESTRICTED_INTERACTION_BLOCK_CLASSES.stream()
                .anyMatch(clazz -> clazz.isInstance(block));

        if (!isRestricted) return;

        event.setCanceled(true);
        event.setUseBlock(net.minecraftforge.eventbus.api.Event.Result.DENY);
        event.setUseItem(net.minecraftforge.eventbus.api.Event.Result.DENY);
    }
    /**
     * Р‘Р»РѕРєРёСЂРѕРІРєР° РєР»Р°РІРёС€Рё E (РёРЅРІРµРЅС‚Р°СЂСЊ), РєРѕРіРґР° РёРіСЂРѕРє СЃРёРґРёС‚ РІРЅСѓС‚СЂРё С‚РµС…РЅРёРєРё
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onContainerOpen(PlayerContainerEvent.Open event) {
        Player player = event.getEntity();
        if (AASConfig.PREVENT_VEHICLE_INVENTORY_ACCESS.get() && !player.isCreative() && player.getVehicle() != null) {

            Entity vehicle = player.getVehicle();
            ResourceLocation vehicleKey = ForgeRegistries.ENTITY_TYPES.getKey(vehicle.getType());

            // Р•СЃР»Рё РёРіСЂРѕРє РІ СЂР°Р·СЂРµС€РµРЅРЅРѕР№ С‚РµС…РЅРёРєРµ - РЅРёС‡РµРіРѕ РЅРµ РґРµР»Р°РµРј
            if (vehicleKey != null && ALLOWED_INVENTORY_ENTITIES.contains(vehicleKey.toString())) {
                return;
            }

            // РРЅР°С‡Рµ РїСЂРёРЅСѓРґРёС‚РµР»СЊРЅРѕ Р·Р°РєСЂС‹РІР°РµРј РёРЅРІРµРЅС‚Р°СЂСЊ
            event.setCanceled(true);
            if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.server.execute(serverPlayer::closeContainer);
            } else {
                player.closeContainer();
            }
        }
    }


    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (AASConfig.PREVENT_BLOCK_BREAKING.get()) {
            Player player = event.getEntity();
            if (player.isCreative()) return;

            Level level = event.getLevel();
            boolean isStarted = false;

            // РџСЂРѕРІРµСЂРєР° РЅР° СЃРµСЂРІРµСЂРµ
            if (level instanceof ServerLevel serverLevel) {
                isStarted = AASWorldData.get(serverLevel).isGameStarted;
            }
            // РџСЂРѕРІРµСЂРєР° РЅР° РєР»РёРµРЅС‚Рµ (РёСЃРїРѕР»СЊР·СѓРµРј Р±РµР·РѕРїР°СЃРЅС‹Р№ СЃРїРёСЃРѕРє, С‡С‚РѕР±С‹ СЃРµСЂРІРµСЂ РЅРµ РєСЂР°С€РЅСѓР»СЃСЏ)
            else if (level.isClientSide) {
                isStarted = com.example.aas.client.ClientData.isGameStarted;
            }

            if (!isStarted) {
                // Р”Рћ РЎРўРђР РўРђ РР“Р Р« РќР•Р›Р¬Р—РЇ Р›РћРњРђРўР¬ РќРР§Р•Р“Рћ РљР РћРњР• Р’РђРќРР›Р›Р«
                event.setCanceled(true);
            } else {
                // РџРћРЎР›Р• РЎРўРђР РўРђ РР“Р Р« РџР РћР’Р•Р РЇР•Рњ Р‘Р•Р›Р«Р™ РЎРџРРЎРћРљ Р‘Р›РћРљРћР’
                BlockState state = level.getBlockState(event.getPos());
                if (!isBlockWhitelisted(state)) {
                    event.setCanceled(true);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getPlacedBlock().is(ModBlocks.GAME_START_TRIGGER.get()) || event.getPlacedBlock().is(ModBlocks.INVASION_PREP_TRIGGER.get())) {
            if (event.getLevel() instanceof ServerLevel level) {
                AASWorldData.get(level).triggerBlocks.add(event.getPos());
                AASWorldData.get(level).setDirty();
            }
        }
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        // 1. Р›РѕРіРёРєР° СѓРґР°Р»РµРЅРёСЏ С‚СЂРёРіРіРµСЂРѕРІ (РѕР±СЏР·Р°С‚РµР»СЊРЅР°СЏ)
        if (event.getState().is(ModBlocks.GAME_START_TRIGGER.get()) || event.getState().is(ModBlocks.INVASION_PREP_TRIGGER.get())) {
            if (event.getLevel() instanceof ServerLevel level) {
                AASWorldData.get(level).triggerBlocks.remove(event.getPos());
                AASWorldData.get(level).setDirty();
            }
        }

        // 2. Р›РѕРіРёРєР° Р·Р°С‰РёС‚С‹ РѕС‚ Р»РѕРјР°РЅРёСЏ Р±Р»РѕРєРѕРІ (РїРѕ РєРѕРЅС„РёРіСѓ)
        if (AASConfig.PREVENT_BLOCK_BREAKING.get()) {
            Player player = event.getPlayer();
            if (!player.isCreative()) {
                if (event.getLevel() instanceof ServerLevel serverLevel) {
                    boolean isStarted = AASWorldData.get(serverLevel).isGameStarted;

                    if (!isStarted) {
                        // Р”Рћ РЎРўРђР РўРђ РР“Р Р« РќР•Р›Р¬Р—РЇ Р›РћРњРђРўР¬ РќРР§Р•Р“Рћ
                        event.setCanceled(true);
                    } else {
                        // РџРћРЎР›Р• РЎРўРђР РўРђ РџР РћР’Р•Р РЇР•Рњ Р‘Р•Р›Р«Р™ РЎРџРРЎРћРљ
                        if (!isBlockWhitelisted(event.getState())) {
                            event.setCanceled(true);
                        }
                    }
                }
            }
        }
    }

    // Р’СЃРїРѕРјРѕРіР°С‚РµР»СЊРЅС‹Р№ РјРµС‚РѕРґ РґР»СЏ РѕРїСЂРµРґРµР»РµРЅРёСЏ Р±Р»РѕРєРѕРІ, РєРѕС‚РѕСЂС‹Рµ РјРѕР¶РЅРѕ Р»РѕРјР°С‚СЊ РІ РёРіСЂРµ
    private static boolean isBlockWhitelisted(BlockState state) {
        // 1. Исключаем: цветы, стебли культур
        if (state.is(BlockTags.REPLACEABLE) || state.is(BlockTags.FLOWERS) || state.is(BlockTags.CROPS) || state.is(BlockTags.LEAVES)) {
            return true;
        }

        // Доп. исключение: листва (ванильный класс) и опавшая листва из другого мода (по ID, без прямой зависимости)
        if (state.getBlock() instanceof net.minecraft.world.level.block.LeavesBlock) {
            return true;
        }
        net.minecraft.resources.ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (blockId != null && blockId.toString().equals("squadmcscreen:squad_leaf_litter")) {
            return true;
        }

        // 2. РСЃРєР»СЋС‡Р°РµРј: РЅРµРґРѕСЃС‚СЂРѕРµРЅРЅС‹Рµ Р±Р»РѕРєРё (Р§РµСЂС‚РµР¶Рё)
        // РџСЂРѕРІРµСЂСЏРµРј Р±Р»РѕРєРё, С‚Сѓ РєРѕС‚РѕСЂС‹С… РµСЃС‚СЊ СЃРІРѕР№СЃС‚РІРѕ СЃС‚СЂРѕРёС‚РµР»СЊСЃС‚РІР°
        if (state.hasProperty(WallBlock.CONSTRUCTED) && !state.getValue(WallBlock.CONSTRUCTED)) {
            return true; // РќРµРґРѕСЃС‚СЂРѕРµРЅРЅР°СЏ СЃС‚РµРЅР°
        }
        if (state.hasProperty(BarbedWireBlock.CONSTRUCTED) && !state.getValue(BarbedWireBlock.CONSTRUCTED)) {
            return true; // РќРµРґРѕСЃС‚СЂРѕРµРЅРЅР°СЏ РєРѕР»СЋС‡РєР°
        }
        if (state.hasProperty(HubBlock.CONSTRUCTED) && !state.getValue(HubBlock.CONSTRUCTED)) {
            return true; // РќРµРґРѕСЃС‚СЂРѕРµРЅРЅС‹Р№ РҐРђР‘ (РўР°Р±)
        }

        // Р‘Р»РѕРєРё СЃС‚СЂРѕРёС‚РµР»СЊСЃС‚РІР° С‚РµС…РЅРёРєРё (РѕРЅРё РІСЃРµРіРґР° РіРѕС‚РѕРІС‹, РїРѕРєР° РЅРµ Р·Р°РІРµСЂС€РµРЅ РїСЂРёР·С‹РІ)
        if (state.is(ModBlocks.M2_CONSTRUCTION_BLOCK.get()) ||
                state.is(ModBlocks.AGS_CONSTRUCTION_BLOCK.get()) ||
                state.is(ModBlocks.MORTAR_CONSTRUCTION_BLOCK.get()) ||
                state.is(ModBlocks.TOW_CONSTRUCTION_BLOCK.get())) {
            return true;
        }

        // 3. РРіСЂРѕРІС‹Рµ С†РµР»РµРІС‹Рµ Р±Р»РѕРєРё (РўРѕС‡РєРё РїРѕСЃС‚СЂРѕР№РєРё)
        boolean isDefense = state.is(ModBlocks.WALL_BLOCK.get()) || state.is(ModBlocks.BARBED_WIRE_BLOCK.get());
        boolean allowDefenses = AASConfig.ALLOW_BREAKING_DEFENSES.get();

        return state.is(ModBlocks.HUB_BLOCK.get()) ||
                state.is(ModBlocks.BLUE_RALLY_BLOCK.get()) ||
                state.is(ModBlocks.RED_RALLY_BLOCK.get()) ||
                state.is(ModBlocks.AMMO_BAG_BLOCK.get()) ||
                state.is(ModBlocks.VEHICLE_STATION_BLOCK.get()) ||
                (isDefense && allowDefenses) ||
                state.is(Tags.Blocks.GLASS) ||
                state.is(Tags.Blocks.GLASS_PANES) ||
                state.is(BlockTags.IMPERMEABLE) ||
                state.is(net.minecraft.world.level.block.Blocks.STONE_BUTTON);
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

        // === РљР›РРљРќРЈР› MAIN SUPPLY BLOCK (Р’Р·СЏС‚РёРµ/РїРѕРїРѕР»РЅРµРЅРёРµ РєРёС‚Р° РїСЂРё РџРљРњ) ===
        if (clickedState.is(ModBlocks.MAIN_SUPPLY_BLOCK.get())) {
            if (!level.isClientSide) {
                ServerPlayer sPlayer = (ServerPlayer) player;
                AASWorldData data = AASWorldData.get(sPlayer.serverLevel());

                if (!data.isGameStarted && !sPlayer.isCreative()) {
                    sPlayer.sendSystemMessage(Component.literal("Game hasn't started yet!").withStyle(ChatFormatting.RED));
                    event.setCanceled(true);
                    return;
                }

                long lastMainUse = sPlayer.getPersistentData().getLong("AAS_LastMainResupply");
                long currentTime = sPlayer.level().getGameTime();

                if (!sPlayer.isCreative() && currentTime < lastMainUse + 1200) {
                    long secondsLeft = (lastMainUse + 1200 - currentTime) / 20;
                    sPlayer.displayClientMessage(Component.literal("Main Supply Cooldown: " + secondsLeft + "s")
                            .withStyle(ChatFormatting.RED), true);
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
                        boolean isAltVariant2 = sPlayer.getPersistentData().getBoolean("AAS_CurrentKitAlt");
                        AASWorldData.KitInfo kit = data.getKitVariant(t, kitName, isAltVariant2);
                        if (kit != null) {
                            if (com.example.aas.network.ResupplyHandler.resupplyPlayer(sPlayer, kit, false)) {
                                sPlayer.sendSystemMessage(Component.literal("Kit Resupplied!").withStyle(ChatFormatting.GREEN));
                                sPlayer.level().playSound(null, sPlayer.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 1f, 1f);
                                sPlayer.getPersistentData().putLong("AAS_LastMainResupply", currentTime);
                            } else {
                                sPlayer.sendSystemMessage(Component.translatable("aas.msg.kit_full").withStyle(ChatFormatting.YELLOW));
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

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onGlobalVehicleInteract(PlayerInteractEvent.EntityInteract event) {
        Player player = event.getEntity();

        // 1. РџСЂРѕРІРµСЂСЏРµРј РєРѕРЅС„РёРі
        if (!AASConfig.PREVENT_ENEMY_VEHICLE_ENTRY.get()) return;

        // 2. РРіРЅРѕСЂРёСЂСѓРµРј РєСЂРµР°С‚РёРІ Рё СЃРїРµРєС‚Р°С‚РѕСЂР°
        if (player.isCreative() || player.isSpectator()) return;

        Entity target = event.getTarget();

        // 3. РџСЂРѕРІРµСЂСЏРµРј, РµСЃС‚СЊ Р»Рё Сѓ СЃСѓС‰РЅРѕСЃС‚Рё РєРѕРјР°РЅРґР° (С‚РµС…РЅРёРєР° СЃС‚Р°РІРёС‚ С‚РµРі С‡РµСЂРµР· Spawner РёР»Рё РІСЂСѓС‡РЅСѓСЋ)
        if (target.getPersistentData().contains("AAS_VehicleTeam")) {
            String vTeam = target.getPersistentData().getString("AAS_VehicleTeam");

            // Р•СЃР»Рё С‚РµС…РЅРёРєР° РЅРµ РЅРµР№С‚СЂР°Р»СЊРЅР°СЏ
            if (!vTeam.isEmpty() && !vTeam.equalsIgnoreCase("NEUTRAL")) {
                String pTeam = (player.getTeam() != null) ? player.getTeam().getName().toUpperCase() : "NEUTRAL";

                // 4. Р•СЃР»Рё РєРѕРјР°РЅРґР° С‚РµС…РЅРёРєРё РЅРµ СЃРѕРІРїР°РґР°РµС‚ СЃ РєРѕРјР°РЅРґРѕР№ РёРіСЂРѕРєР° - Р±Р»РѕРєРёСЂСѓРµРј РІР·Р°РёРјРѕРґРµР№СЃС‚РІРёРµ
                if (!vTeam.equalsIgnoreCase(pTeam)) {
                    // РћС‚РјРµРЅСЏРµРј РІР·Р°РёРјРѕРґРµР№СЃС‚РІРёРµ (РїРѕСЃР°РґРєСѓ)
                    event.setCanceled(true);
                    event.setCancellationResult(InteractionResult.FAIL);

                    // Р’С‹РІРѕРґРёРј СЃРѕРѕР±С‰РµРЅРёРµ С‚РѕР»СЊРєРѕ РЅР° СЃС‚РѕСЂРѕРЅРµ РєР»РёРµРЅС‚Р°
                    if (event.getLevel().isClientSide) {
                        player.displayClientMessage(Component.literal("Access Denied: Enemy Vehicle!").withStyle(ChatFormatting.RED), true);
                    }
                }
            }
        }
    }
    // === Блокировка рации walkietalkie:netherite_walkietalkie в survival/adventure ===
    private static boolean isBlockedWalkieTalkie(ItemStack stack, Player player) {
        if (player.isCreative() || player.isSpectator()) return false;
        if (stack == null || stack.isEmpty()) return false;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null
                && id.getNamespace().equals("walkietalkie")
                && id.getPath().equals("netherite_walkietalkie");
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onWalkieTalkieRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (isBlockedWalkieTalkie(event.getItemStack(), event.getEntity())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onWalkieTalkieRightClickBlockGuard(PlayerInteractEvent.RightClickBlock event) {
        if (isBlockedWalkieTalkie(event.getItemStack(), event.getEntity())) {
            event.setUseItem(net.minecraftforge.eventbus.api.Event.Result.DENY);
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }
}