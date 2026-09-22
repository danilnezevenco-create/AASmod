package com.example.aas.events;

import com.example.aas.network.*;
import com.example.aas.network.MapPlayerInfo;
import com.example.aas.util.TeamMessageUtil;
import com.example.aas.world.AASWorldData;
import com.example.aas.network.PacketCaptureNotification;
import net.minecraft.server.level.ServerLevel;
import com.example.aas.block.RallyPointBlock;
import com.example.aas.item.ModItems;
import com.example.aas.block.RallyPointBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;
import com.example.aas.config.AASConfig;
import java.util.*;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import com.example.aas.sound.ModSounds; // Р В РІР‚СњР В РЎвЂўР В Р’В±Р В Р’В°Р В Р вЂ Р РЋР Р‰Р РЋРІР‚С™Р В Р’Вµ Р РЋР РЉР РЋРІР‚С™Р РЋРЎвЂњ Р РЋР С“Р РЋРІР‚С™Р РЋР вЂљР В РЎвЂўР В РЎвЂќР РЋРЎвЂњ
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import java.util.Map;
import java.util.HashMap;
import com.example.aas.block.InvasionPrepTriggerBlock;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import com.example.aas.block.GameStartTriggerBlock;
import com.example.aas.block.ModBlocks;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.world.InteractionResult;

@Mod.EventBusSubscriber(modid = "aas", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class GameLogicEvents {

    public static final Map<UUID, String> pendingRespawnLocations = new HashMap<>();
    public static final Map<UUID, String> pendingTeams = new HashMap<>();
    public static final Map<UUID, Map<Integer, CompoundTag>> PERSISTENT_NBT_STORAGE = new HashMap<>();
    private static final Map<String, Boolean> lastBlueBlockedMap = new HashMap<>();
    private static final Map<String, Boolean> lastRedBlockedMap = new HashMap<>();

    public static void startGameCountdown(ServerLevel level) {
        AASWorldData data = AASWorldData.get(level);
        data.countdownTicks = 100;
        data.countdownActive = true;
        data.setDirty();
    }

    public static void cancelCountdown(ServerLevel level) {
        AASWorldData data = AASWorldData.get(level);
        data.countdownActive = false;
        data.countdownTicks = 0;
        data.setDirty();
    }

    // Р В РЎСџР РЋРЎвЂњР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР В РІвЂћвЂ“ Р В РЎВР В Р’ВµР РЋРІР‚С™Р В РЎвЂўР В РўвЂ Р В РўвЂР В Р’В»Р РЋР РЏ Р РЋР С“Р В РЎвЂўР В Р вЂ Р В РЎВР В Р’ВµР РЋР С“Р РЋРІР‚С™Р В РЎвЂР В РЎВР В РЎвЂўР РЋР С“Р РЋРІР‚С™Р В РЎвЂ
    public static void cancelCountdown() {}

    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        if (event.getPlayer() == null || event.getPlayer().level().isClientSide) return;

        ServerPlayer player = (ServerPlayer) event.getPlayer();
        if (player.isCreative()) return;

        AASWorldData data = AASWorldData.get(player.serverLevel());

        // Р В Р’В Р В Р’В°Р В Р’В±Р В РЎвЂўР РЋРІР‚С™Р В Р’В°Р В Р’ВµР РЋРІР‚С™ Р РЋРІР‚С™Р В РЎвЂўР В Р’В»Р РЋР Р‰Р В РЎвЂќР В РЎвЂў Р В Р’ВµР РЋР С“Р В Р’В»Р В РЎвЂ Р В РЎвЂР В РЎвЂ“Р РЋР вЂљР В Р’В° Р В Р’В·Р В Р’В°Р В РЎвЂ”Р РЋРЎвЂњР РЋРІР‚В°Р В Р’ВµР В Р вЂ¦Р В Р’В°
        if (data.isGameStarted) {

            // Р В РЎСџР РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р’ВµР РЋР вЂљР РЋР РЏР В Р’ВµР В РЎВ, Р РЋР РЏР В Р вЂ Р В Р’В»Р РЋР РЏР В Р’ВµР РЋРІР‚С™Р РЋР С“Р РЋР РЏ Р В Р’В»Р В РЎвЂ Р В Р вЂ Р РЋРІР‚в„–Р В Р’В±Р РЋР вЂљР В Р’В°Р РЋР С“Р РЋРІР‚в„–Р В Р вЂ Р В Р’В°Р В Р’ВµР В РЎВР РЋРІР‚в„–Р В РІвЂћвЂ“ Р В РЎвЂ”Р РЋР вЂљР В Р’ВµР В РўвЂР В РЎВР В Р’ВµР РЋРІР‚С™ fpv-Р В РўвЂР РЋР вЂљР В РЎвЂўР В Р вЂ¦Р В РЎвЂўР В РЎВ Р Р†Р вЂљРІР‚Сњ Р В Р’ВµР В РЎВР РЋРЎвЂњ Р В Р вЂ Р РЋР С“Р В Р’ВµР В РЎвЂ“Р В РўвЂР В Р’В° Р В РЎВР В РЎвЂўР В Р’В¶Р В Р вЂ¦Р В РЎвЂў
            ItemStack tossedStack = event.getEntity().getItem();
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(tossedStack.getItem());
            boolean isFpvDrone = itemId != null && itemId.toString().equals("fpvdrone:drone");

            if (isFpvDrone) {
                return; // Р РЋР вЂљР В Р’В°Р В Р’В·Р РЋР вЂљР В Р’ВµР РЋРІвЂљВ¬Р В Р’В°Р В Р’ВµР В РЎВ Р В Р вЂ Р РЋРІР‚в„–Р В Р’В±Р РЋР вЂљР В РЎвЂўР РЋР С“, Р В Р вЂ¦Р В РЎвЂР В РЎвЂќР В Р’В°Р В РЎвЂќР В РЎвЂР РЋРІР‚В¦ Р В РЎвЂўР В РЎвЂ“Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В РЎвЂР РЋРІР‚РЋР В Р’ВµР В Р вЂ¦Р В РЎвЂР В РІвЂћвЂ“
            }

            boolean preventAll = false;
            try {
                preventAll = AASConfig.PREVENT_ALL_ITEM_DROPS.get();
            } catch (Exception ignored) {}

            if (preventAll) {
                event.setCanceled(true);
                // Р В РІР‚в„ўР В Р’В°Р В Р’В¶Р В Р вЂ¦Р В РЎвЂў: Р В Р вЂ Р В РЎвЂўР В Р’В·Р В Р вЂ Р РЋР вЂљР В Р’В°Р РЋРІР‚В°Р В Р’В°Р В Р’ВµР В РЎВ Р В РЎвЂ”Р РЋР вЂљР В Р’ВµР В РўвЂР В РЎВР В Р’ВµР РЋРІР‚С™ Р В Р вЂ  Р В РЎвЂР В Р вЂ¦Р В Р вЂ Р В Р’ВµР В Р вЂ¦Р РЋРІР‚С™Р В Р’В°Р РЋР вЂљР РЋР Р‰, Р РЋРІР‚РЋР РЋРІР‚С™Р В РЎвЂўР В Р’В±Р РЋРІР‚в„– Р В РЎвЂўР В Р вЂ¦ Р В Р вЂ¦Р В Р’Вµ Р В РЎвЂ”Р РЋР вЂљР В РЎвЂўР В РЎвЂ”Р В Р’В°Р В Р’В»
                player.getInventory().add(tossedStack);
                player.displayClientMessage(Component.literal("Item dropping is DISABLED during the game!")
                        .withStyle(ChatFormatting.RED), true);
            }
            else if (isHeavyItem(tossedStack.getItem())) {
                // Р В РІР‚СћР РЋР С“Р В Р’В»Р В РЎвЂ Р В РЎвЂўР В Р’В±Р РЋРІР‚В°Р В Р’В°Р РЋР РЏ Р В Р’В±Р В Р’В»Р В РЎвЂўР В РЎвЂќР В РЎвЂР РЋР вЂљР В РЎвЂўР В Р вЂ Р В РЎвЂќР В Р’В° Р В Р вЂ Р РЋРІР‚в„–Р В РЎвЂќР В Р’В»Р РЋР вЂ№Р РЋРІР‚РЋР В Р’ВµР В Р вЂ¦Р В Р’В°, Р В РЎвЂ”Р РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р’ВµР РЋР вЂљР РЋР РЏР В Р’ВµР В РЎВ Р РЋРІР‚С™Р В РЎвЂўР В Р’В»Р РЋР Р‰Р В РЎвЂќР В РЎвЂў Р РЋРІР‚С™Р РЋР РЏР В Р’В¶Р РЋРІР‚ВР В Р’В»Р РЋРІР‚в„–Р В Р’Вµ Р В РЎвЂ”Р РЋР вЂљР В Р’ВµР В РўвЂР В РЎВР В Р’ВµР РЋРІР‚С™Р РЋРІР‚в„–
                event.setCanceled(true);
                player.getInventory().add(tossedStack);
                player.displayClientMessage(Component.literal("Cannot drop heavy ammo during combat!")
                        .withStyle(ChatFormatting.RED), true);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTickEffects(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        if (event.player.tickCount % 10 == 0) {
            ServerPlayer player = (ServerPlayer) event.player;
            AASWorldData data = AASWorldData.get(player.serverLevel());
            if (data.isGameStarted && !player.isCreative()) {
                boolean hasHeavyItem = false;
                for (ItemStack stack : player.getInventory().items) {
                    if (!stack.isEmpty() && isHeavyItem(stack.getItem())) { hasHeavyItem = true; break; }
                }
                if (!hasHeavyItem) {
                    for (ItemStack stack : player.getInventory().offhand) {
                        if (!stack.isEmpty() && isHeavyItem(stack.getItem())) { hasHeavyItem = true; break; }
                    }
                }
                if (hasHeavyItem) player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 2, false, false, true));
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTickDriveCheck(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;

        if (!AASConfig.REQUIRE_SPECIALIST_TO_DRIVE.get()) return;

        ServerPlayer player = (ServerPlayer) event.player;
        Entity vehicle = player.getVehicle();

        // Р В РІР‚СћР РЋР С“Р В Р’В»Р В РЎвЂ Р В РЎвЂР В РЎвЂ“Р РЋР вЂљР В РЎвЂўР В РЎвЂќ Р В Р вЂ Р РЋРІР‚в„–Р РЋРІвЂљВ¬Р В Р’ВµР В Р’В» Р В РЎвЂР В Р’В· Р РЋРІР‚С™Р В Р’ВµР РЋРІР‚В¦Р В Р вЂ¦Р В РЎвЂР В РЎвЂќР В РЎвЂ Р Р†Р вЂљРІР‚Сњ Р РЋРІР‚РЋР В РЎвЂР РЋР С“Р РЋРІР‚С™Р В РЎвЂР В РЎВ Р В Р вЂ Р РЋР С“Р РЋРІР‚В
        if (vehicle == null) {
            player.removeEffect(MobEffects.BLINDNESS);
            player.getPersistentData().remove("AAS_DriveKickTimer");
            player.getPersistentData().remove("AAS_BoardingGrace");
            player.getPersistentData().remove("AAS_LastVehicleId");
            return;
        }

        // --- GRACE PERIOD ---
        // Р В РІР‚вЂќР В Р’В°Р В РЎвЂ”Р В РЎвЂўР В РЎВР В РЎвЂР В Р вЂ¦Р В Р’В°Р В Р’ВµР В РЎВ ID Р РЋРІР‚С™Р В Р’ВµР РЋРІР‚В¦Р В Р вЂ¦Р В РЎвЂР В РЎвЂќР В РЎвЂ. Р В РІР‚СћР РЋР С“Р В Р’В»Р В РЎвЂ Р РЋРІР‚С™Р В Р’ВµР РЋРІР‚В¦Р В Р вЂ¦Р В РЎвЂР В РЎвЂќР В Р’В° Р В Р вЂ¦Р В РЎвЂўР В Р вЂ Р В Р’В°Р РЋР РЏ Р Р†Р вЂљРІР‚Сњ Р В РўвЂР В Р’В°Р РЋРІР‚ВР В РЎВ Р В Р вЂ Р РЋР вЂљР В Р’ВµР В РЎВР РЋР РЏ Р В Р вЂ¦Р В Р’В° Р В РЎвЂ”Р В Р’ВµР РЋР вЂљР В Р’ВµР РЋР С“Р В Р’В°Р В РўвЂР В РЎвЂќР РЋРЎвЂњ
        int lastVehicleId = player.getPersistentData().getInt("AAS_LastVehicleId");
        if (lastVehicleId != vehicle.getId()) {
            // Р В Р’ВР В РЎвЂ“Р РЋР вЂљР В РЎвЂўР В РЎвЂќ Р РЋР С“Р В Р’ВµР В Р’В» Р В Р вЂ  Р В Р вЂ¦Р В РЎвЂўР В Р вЂ Р РЋРЎвЂњР РЋР вЂ№ (Р В РЎвЂР В Р’В»Р В РЎвЂ Р В РўвЂР РЋР вЂљР РЋРЎвЂњР В РЎвЂ“Р РЋРЎвЂњР РЋР вЂ№) Р РЋРІР‚С™Р В Р’ВµР РЋРІР‚В¦Р В Р вЂ¦Р В РЎвЂР В РЎвЂќР РЋРЎвЂњ Р Р†Р вЂљРІР‚Сњ Р РЋР С“Р В Р’В±Р РЋР вЂљР В Р’В°Р РЋР С“Р РЋРІР‚в„–Р В Р вЂ Р В Р’В°Р В Р’ВµР В РЎВ Р В Р вЂ Р РЋР С“Р РЋРІР‚В Р В РЎвЂ Р В Р’В¶Р В РўвЂР РЋРІР‚ВР В РЎВ
            player.getPersistentData().putInt("AAS_LastVehicleId", vehicle.getId());
            player.getPersistentData().putInt("AAS_BoardingGrace", 0);
            player.getPersistentData().remove("AAS_DriveKickTimer");
            return; // Р В РІР‚СњР В Р’В°Р РЋРІР‚ВР В РЎВ 1 Р РЋРІР‚С™Р В РЎвЂР В РЎвЂќ Р В Р вЂ¦Р В Р’В° "Р РЋРЎвЂњР РЋР С“Р РЋРІР‚С™Р РЋР вЂљР В РЎвЂўР В РІвЂћвЂ“Р РЋР С“Р РЋРІР‚С™Р В Р вЂ Р В РЎвЂў"
        }

        int boardingGrace = player.getPersistentData().getInt("AAS_BoardingGrace");
        if (boardingGrace < 80) { // 4 Р РЋР С“Р В Р’ВµР В РЎвЂќР РЋРЎвЂњР В Р вЂ¦Р В РўвЂР РЋРІР‚в„– Р Р†Р вЂљРІР‚Сњ Р В Р вЂ Р РЋР вЂљР В Р’ВµР В РЎВР РЋР РЏ Р В Р’В·Р В Р’В°Р В Р вЂ¦Р РЋР РЏР РЋРІР‚С™Р РЋР Р‰ Р В Р вЂ¦Р РЋРЎвЂњР В Р’В¶Р В Р вЂ¦Р В РЎвЂўР В Р’Вµ Р В РЎВР В Р’ВµР РЋР С“Р РЋРІР‚С™Р В РЎвЂў
            player.getPersistentData().putInt("AAS_BoardingGrace", boardingGrace + 1);
            return; // Р В РЎСџР РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р’ВµР РЋР вЂљР В РЎвЂќР РЋРЎвЂњ Р В Р вЂ¦Р В Р’Вµ Р В РўвЂР В Р’ВµР В Р’В»Р В Р’В°Р В Р’ВµР В РЎВ, Р В РЎвЂ”Р В РЎвЂўР В РЎвЂќР В Р’В° Р В Р вЂ¦Р В Р’Вµ Р В РЎвЂР РЋР С“Р РЋРІР‚С™Р РЋРІР‚ВР В РЎвЂќ grace period
        }
        // --- Р В РЎв„ўР В РЎвЂєР В РЎСљР В РІР‚СћР В Р’В¦ GRACE PERIOD ---

        int seatIndex = vehicle.getPassengers().indexOf(player);

        // Р В РЎСџР В Р’В°Р РЋР С“Р РЋР С“Р В Р’В°Р В Р’В¶Р В РЎвЂР РЋР вЂљР В РЎвЂўР В Р вЂ  (Р В Р вЂ¦Р В Р’Вµ Р В Р вЂ Р В РЎвЂўР В РўвЂР В РЎвЂР РЋРІР‚С™Р В Р’ВµР В Р’В»Р В Р’ВµР В РІвЂћвЂ“) Р В Р вЂ¦Р В Р’Вµ Р РЋРІР‚С™Р РЋР вЂљР В РЎвЂўР В РЎвЂ“Р В Р’В°Р В Р’ВµР В РЎВ Р В Р вЂ¦Р В РЎвЂР В РЎвЂќР В РЎвЂўР В РЎвЂ“Р В РўвЂР В Р’В°
        if (seatIndex > 0) {
            player.removeEffect(MobEffects.BLINDNESS);
            player.getPersistentData().remove("AAS_DriveKickTimer");
            return;
        }

        // Р В РЎСљР В РЎвЂР В Р’В¶Р В Р’Вµ Р Р†Р вЂљРІР‚Сњ Р РЋРІР‚С™Р В РЎвЂўР В Р’В»Р РЋР Р‰Р В РЎвЂќР В РЎвЂў Р В Р вЂ Р В РЎвЂўР В РўвЂР В РЎвЂР РЋРІР‚С™Р В Р’ВµР В Р’В»Р РЋР Р‰ (seatIndex == 0)
        if (player.isCreative() || player.isSpectator()) return;

        String vType = vehicle.getPersistentData().getString("AAS_VehicleType");
        String pKit = player.getPersistentData().getString("AAS_CurrentKit");

        boolean isAuthorized = true;
        String requiredSpecialist = "";

        if (vType.equalsIgnoreCase("HELICOPTER") || vType.contains("CAS") || vType.contains("Supply Helicopter")) {
            if (!pKit.equals("Pilot") && !pKit.equals("Pilot Officer")) {
                isAuthorized = false;
                requiredSpecialist = "PILOT";
            }
        } else if (vType.equalsIgnoreCase("TANK") || vType.equalsIgnoreCase("APC") ||
                vType.equalsIgnoreCase("Mobile ZU") || vType.equalsIgnoreCase("SPG") ||
                vType.equalsIgnoreCase("Heavy Supply")) {
            if (!pKit.equals("Mechanic") && !pKit.equals("Mechanic Officer")) {
                isAuthorized = false;
                requiredSpecialist = "MECHANIC";
            }
        }

        if (!isAuthorized) {
            int timer = player.getPersistentData().getInt("AAS_DriveKickTimer");
            timer++;

            // Р В Р Р‹Р В Р’В»Р В Р’ВµР В РЎвЂ”Р В РЎвЂўР РЋРІР‚С™Р В Р’В° Р В РўвЂР В Р’ВµР РЋР вЂљР В Р’В¶Р В РЎвЂР РЋРІР‚С™Р РЋР С“Р РЋР РЏ Р В Р вЂ Р В Р’ВµР РЋР С“Р РЋР Р‰ Р В РЎвЂ”Р В Р’ВµР РЋР вЂљР В РЎвЂР В РЎвЂўР В РўвЂ, Р В РЎвЂ”Р В РЎвЂўР В РЎвЂќР В Р’В° Р В РЎвЂР В РЎвЂ“Р РЋР вЂљР В РЎвЂўР В РЎвЂќ Р В Р’В±Р В Р’ВµР В Р’В· Р В Р вЂ¦Р РЋРЎвЂњР В Р’В¶Р В Р вЂ¦Р В РЎвЂўР В РЎвЂ“Р В РЎвЂў Р В РЎвЂќР В РЎвЂР РЋРІР‚С™Р В Р’В° Р РЋР С“Р В РЎвЂР В РўвЂР В РЎвЂР РЋРІР‚С™ Р В Р’В·Р В Р’В° Р РЋР вЂљР РЋРЎвЂњР В Р’В»Р РЋРІР‚ВР В РЎВ.
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 40, 0, false, false, false));

            if (timer >= 100) {
                player.stopRiding();
                player.removeEffect(MobEffects.BLINDNESS);
                player.getPersistentData().remove("AAS_DriveKickTimer");
                player.getPersistentData().remove("AAS_BoardingGrace");
                player.getPersistentData().remove("AAS_LastVehicleId");
                player.displayClientMessage(Component.literal("EJECTED: You are not a qualified driver!")
                        .withStyle(ChatFormatting.RED), true);
            } else {
                player.getPersistentData().putInt("AAS_DriveKickTimer", timer);
                if (timer % 20 == 0) {
                    // Р В РЎСџР РЋР вЂљР В Р’В°Р В Р вЂ Р В РЎвЂР В Р’В»Р РЋР Р‰Р В Р вЂ¦Р РЋРІР‚в„–Р В РІвЂћвЂ“ Р В Р вЂ Р РЋРІР‚в„–Р В Р’В·Р В РЎвЂўР В Р вЂ : Р РЋР С“Р В РЎвЂўР В Р’В·Р В РўвЂР В Р’В°Р В Р’ВµР В РЎВ Р В РЎвЂ”Р В Р’ВµР РЋР вЂљР В Р’ВµР В Р вЂ Р В РЎвЂўР В РўвЂР В РЎвЂР В РЎВР РЋРІР‚в„–Р В РІвЂћвЂ“ Р В РЎвЂќР В РЎвЂўР В РЎВР В РЎвЂ”Р В РЎвЂўР В Р вЂ¦Р В Р’ВµР В Р вЂ¦Р РЋРІР‚С™, Р В РЎвЂќР РЋР вЂљР В Р’В°Р РЋР С“Р В РЎвЂР В РЎВ Р В Р’ВµР В РЎвЂ“Р В РЎвЂў Р В РЎвЂ Р В РЎвЂўР РЋРІР‚С™Р В РЎвЂ”Р РЋР вЂљР В Р’В°Р В Р вЂ Р В Р’В»Р РЋР РЏР В Р’ВµР В РЎВ Р В Р вЂ  Action Bar (true)
                    player.displayClientMessage(Component.translatable("aas.msg.ejection_warning", requiredSpecialist, (5 - (timer / 20))).withStyle(ChatFormatting.YELLOW), true);

                    player.playNotifySound(net.minecraft.sounds.SoundEvents.NOTE_BLOCK_BASS.value(),
                            net.minecraft.sounds.SoundSource.PLAYERS, 1.0f, 0.5f);
                }
            }
        } else {
            player.removeEffect(MobEffects.BLINDNESS);
            player.getPersistentData().remove("AAS_DriveKickTimer");
        }
    }

    @SubscribeEvent
    public static void onPlayerItemLogic(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        if (event.player.level().getGameTime() % 20 != 0) return;
        Player player = event.player;
        ItemStack mainHandStack = player.getMainHandItem();
        ItemStack offHandStack = player.getOffhandItem();
        Item monitorItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("superbwarfare", "monitor"));
        Item walkieItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("walkietalkie", "netherite_walkietalkie"));
        if (monitorItem == null || walkieItem == null) return;
        boolean hasMonitor = mainHandStack.getItem() == monitorItem;
        if (hasMonitor) {
            if (offHandStack.getItem() != walkieItem && !offHandStack.isEmpty()) {
                moveItemToInventory(player, offHandStack);
                player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            }
            if (player.getOffhandItem().isEmpty()) {
                int walkieSlot = findItemInInventory(player.getInventory(), walkieItem);
                if (walkieSlot != -1) {
                    ItemStack walkieStack = player.getInventory().getItem(walkieSlot);
                    player.setItemInHand(InteractionHand.OFF_HAND, walkieStack);
                    player.getInventory().setItem(walkieSlot, ItemStack.EMPTY);
                }
            }
        } else {
            if (!offHandStack.isEmpty() && offHandStack.getItem() == walkieItem) {
                moveItemToInventory(player, offHandStack);
                player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            }
        }
    }

    @SubscribeEvent
    public static void onWalkieTalkieUseAttempt(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return; // Р В»Р С•Р С–Р С‘Р С”РЎС“ Р Т‘Р ВµРЎР‚Р В¶Р С‘Р С Р Р…Р В° РЎРѓР ВµРЎР‚Р Р†Р ВµРЎР‚Р Вµ

        GameType gameMode = null;
        if (player instanceof ServerPlayer serverPlayer) {
            gameMode = serverPlayer.gameMode.getGameModeForPlayer();
        }
        if (gameMode != GameType.SURVIVAL && gameMode != GameType.ADVENTURE) return;

        ItemStack stack = event.getItemStack();
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id != null
                && id.getNamespace().equals("walkietalkie")
                && id.getPath().equals("netherite_walkietalkie")) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    private static boolean isHeavyItem(Item item) {
        if (item == ModItems.AGS_AMMO.get()) return true;
        if (item == ModItems.M2_AMMO.get()) return true;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        if (id != null) {
            String path = id.getPath();
            if (path.equals("mortar_shell")) return true;
            if (path.equals("medium_anti_ground_missile")) return true;
        }
        return false;
    }

    private static int findItemInInventory(Inventory inventory, Item item) {
        for (int i = 0; i < inventory.items.size(); i++) { if (inventory.items.get(i).getItem() == item) return i; }
        return -1;
    }

    private static void moveItemToInventory(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        int globalTick = server.getTickCount();

        // === 1. Р В РІР‚СљР В РІР‚С”Р В РЎвЂєР В РІР‚ВР В РЎвЂ™Р В РІР‚С”Р В Р’В¬Р В РЎСљР В РЎвЂ™Р В Р вЂЎ Р В Р’В Р В РЎвЂ™Р В Р Р‹Р В Р Р‹Р В Р’В«Р В РІР‚С”Р В РЎв„ўР В РЎвЂ™ Р В РЎСџР В РЎвЂєР В РІР‚вЂќР В Р’ВР В Р’В¦Р В Р’ВР В РІвЂћСћ (Р В Р’В Р В Р’В°Р В Р’В±Р В РЎвЂўР РЋРІР‚С™Р В Р’В°Р В Р’ВµР РЋРІР‚С™ Р В РўвЂР В Р’В»Р РЋР РЏ Р В Р’В¶Р В РЎвЂР В Р вЂ Р РЋРІР‚в„–Р РЋРІР‚В¦ Р В РЎвЂ Р В РЎВР В Р’ВµР РЋР вЂљР РЋРІР‚С™Р В Р вЂ Р РЋРІР‚в„–Р РЋРІР‚В¦) ===
        if (globalTick % 2 == 0) {
            // Р В Р Р‹Р В Р вЂ¦Р В Р’В°Р РЋРІР‚РЋР В Р’В°Р В Р’В»Р В Р’В° Р В РЎвЂќР РЋР РЉР РЋРІвЂљВ¬Р В РЎвЂР РЋР вЂљР РЋРЎвЂњР В Р’ВµР В РЎВ Р В РўвЂР В Р’В°Р В Р вЂ¦Р В Р вЂ¦Р РЋРІР‚в„–Р В Р’Вµ Р В РўвЂР В Р’В»Р РЋР РЏ Р В РЎвЂќР В Р’В°Р В Р’В¶Р В РўвЂР В РЎвЂўР В РЎвЂ“Р В РЎвЂў Р В РЎвЂР В Р’В·Р В РЎВР В Р’ВµР РЋР вЂљР В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР РЏ
            Map<ServerLevel, List<MapPlayerInfo>> dimensionDataCache = new HashMap<>();
            for (ServerLevel level : server.getAllLevels()) {
                AASWorldData data = AASWorldData.get(level);
                // Р В РЎСљР В РЎвЂєР В РІР‚в„ўР В РЎвЂєР В РІР‚Сћ: Р В РІР‚ВР В Р’ВµР РЋР вЂљР В Р’ВµР В РЎВ Р В Р вЂ Р РЋР С“Р В Р’ВµР РЋРІР‚В¦ Р В РЎвЂР В РЎвЂ“Р РЋР вЂљР В РЎвЂўР В РЎвЂќР В РЎвЂўР В Р вЂ  Р РЋР С“ Р РЋР С“Р В Р’ВµР РЋР вЂљР В Р вЂ Р В Р’ВµР РЋР вЂљР В Р’В° Р В РЎвЂ Р РЋРІР‚С›Р В РЎвЂР В Р’В»Р РЋР Р‰Р РЋРІР‚С™Р РЋР вЂљР РЋРЎвЂњР В Р’ВµР В РЎВ Р В РЎвЂ”Р В РЎвЂў Р В РЎвЂР В Р’В·Р В РЎВР В Р’ВµР РЋР вЂљР В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР вЂ№, Р РЋРІР‚РЋР РЋРІР‚С™Р В РЎвЂўР В Р’В±Р РЋРІР‚в„– Р В Р вЂ¦Р В Р’Вµ Р РЋРІР‚С™Р В Р’ВµР РЋР вЂљР РЋР РЏР РЋРІР‚С™Р РЋР Р‰ "Р В РЎВР В Р’ВµР РЋР вЂљР РЋРІР‚С™Р В Р вЂ Р РЋРІР‚в„–Р РЋРІР‚В¦"
                List<ServerPlayer> dimPlayers = new ArrayList<>();
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    if (p.serverLevel() == level) {
                        dimPlayers.add(p);
                    }
                }
                dimensionDataCache.put(level, buildPlayerInfo(dimPlayers, data));
            }

            // Р В Р’В Р В Р’В°Р РЋР С“Р РЋР С“Р РЋРІР‚в„–Р В Р’В»Р В Р’В°Р В Р’ВµР В РЎВ Р В РЎвЂ”Р В Р’В°Р В РЎвЂќР В Р’ВµР РЋРІР‚С™Р РЋРІР‚в„– Р В Р вЂ Р РЋР С“Р В Р’ВµР В РЎВ Р В РЎвЂР В РЎвЂ“Р РЋР вЂљР В РЎвЂўР В РЎвЂќР В Р’В°Р В РЎВ Р В Р вЂ  Р РЋР С“Р В РЎвЂ”Р В РЎвЂР РЋР С“Р В РЎвЂќР В Р’Вµ Р РЋР С“Р В Р’ВµР РЋР вЂљР В Р вЂ Р В Р’ВµР РЋР вЂљР В Р’В° (Р В Р вЂ Р В РЎвЂќР В Р’В»Р РЋР вЂ№Р РЋРІР‚РЋР В Р’В°Р РЋР РЏ Р РЋР РЉР В РЎвЂќР РЋР вЂљР В Р’В°Р В Р вЂ¦ Р РЋР С“Р В РЎВР В Р’ВµР РЋР вЂљР РЋРІР‚С™Р В РЎвЂ)
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                ServerLevel playerLevel = p.serverLevel();
                List<MapPlayerInfo> playersInMyDim = dimensionDataCache.get(playerLevel);

                if (playersInMyDim == null || playersInMyDim.isEmpty()) continue;

                if (p.isSpectator() || p.isCreative()) {
                    PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> p), new PacketSyncMapPlayers(playersInMyDim));
                } else if (p.getTeam() != null) {
                    String myTeamName = p.getTeam().getName();
                    List<MapPlayerInfo> teamOnly = playersInMyDim.stream()
                            .filter(info -> info.team.equalsIgnoreCase(myTeamName))
                            .toList();
                    PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> p), new PacketSyncMapPlayers(teamOnly));
                }
            }
        }
        // === 1.5. Р В Р’В Р В РЎвЂ™Р В Р Р‹Р В Р Р‹Р В Р’В«Р В РІР‚С”Р В РЎв„ўР В РЎвЂ™ Р В Р Р‹Р В РЎС›Р В РЎвЂ™Р В РЎС›Р В Р’ВР В Р Р‹Р В РЎС›Р В Р’ВР В РЎв„ўР В Р’В (Р В Р’В­Р В РЎв„ўР В Р’В Р В РЎвЂ™Р В РЎСљ Р В Р Р‹Р В РЎС›Р В РЎвЂ™Р В РЎС›Р В Р’ВР В Р Р‹Р В РЎС›Р В Р’ВР В РЎв„ўР В Р’В, Caps Lock) Р Р†Р вЂљРІР‚Сњ Р РЋР вЂљР В Р’В°Р В Р’В· Р В Р вЂ  Р РЋР С“Р В Р’ВµР В РЎвЂќР РЋРЎвЂњР В Р вЂ¦Р В РўвЂР РЋРЎвЂњ ===
        if (globalTick % 20 == 0) {
            Map<ServerLevel, List<PlayerStatInfo>> statsCache = new HashMap<>();
            for (ServerLevel level : server.getAllLevels()) {
                AASWorldData data = AASWorldData.get(level);
                // Р В РЎСљР В РЎвЂєР В РІР‚в„ўР В РЎвЂєР В РІР‚Сћ: Р В РІР‚ВР В Р’ВµР РЋР вЂљР В Р’ВµР В РЎВ Р В Р вЂ Р РЋР С“Р В Р’ВµР РЋРІР‚В¦ Р В РЎвЂР В РЎвЂ“Р РЋР вЂљР В РЎвЂўР В РЎвЂќР В РЎвЂўР В Р вЂ  Р РЋР С“ Р РЋР С“Р В Р’ВµР РЋР вЂљР В Р вЂ Р В Р’ВµР РЋР вЂљР В Р’В° Р В РЎвЂ Р РЋРІР‚С›Р В РЎвЂР В Р’В»Р РЋР Р‰Р РЋРІР‚С™Р РЋР вЂљР РЋРЎвЂњР В Р’ВµР В РЎВ Р В РЎвЂ”Р В РЎвЂў Р В РЎвЂР В Р’В·Р В РЎВР В Р’ВµР РЋР вЂљР В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР вЂ№, Р РЋРІР‚РЋР РЋРІР‚С™Р В РЎвЂўР В Р’В±Р РЋРІР‚в„– Р В Р вЂ¦Р В Р’Вµ Р РЋРІР‚С™Р В Р’ВµР РЋР вЂљР РЋР РЏР РЋРІР‚С™Р РЋР Р‰ "Р В РЎВР В Р’ВµР РЋР вЂљР РЋРІР‚С™Р В Р вЂ Р РЋРІР‚в„–Р РЋРІР‚В¦"
                List<ServerPlayer> dimPlayers = new ArrayList<>();
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    if (p.serverLevel() == level) {
                        dimPlayers.add(p);
                    }
                }
                statsCache.put(level, buildStatsInfo(dimPlayers, data));
            }

            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                ServerLevel pLevel = p.serverLevel();
                List<PlayerStatInfo> full = statsCache.get(pLevel);
                if (full == null) continue;

                AASWorldData pData = AASWorldData.get(pLevel);
                boolean isAdminObserver = p.hasPermissions(2) && (p.isSpectator() || p.isCreative());

                String myTeam = (p.getTeam() != null) ? p.getTeam().getName().toUpperCase() : "";
                List<PlayerStatInfo> toSend = new ArrayList<>();

                for (PlayerStatInfo info : full) {
                    boolean isAlly = !myTeam.isEmpty() && info.team.equalsIgnoreCase(myTeam);

                    if (isAdminObserver || isAlly) {
                        toSend.add(info); // Р В РЎвЂ™Р В РўвЂР В РЎВР В РЎвЂР В Р вЂ¦Р РЋРІР‚в„– Р В РЎвЂ Р РЋР С“Р В РЎвЂўР РЋР вЂ№Р В Р’В·Р В Р вЂ¦Р В РЎвЂР В РЎвЂќР В РЎвЂ Р В Р вЂ Р В РЎвЂР В РўвЂР РЋР РЏР РЋРІР‚С™ Р В Р вЂ Р РЋР С“Р РЋРІР‚В (Р В РЎвЂўР РЋРІР‚С™Р РЋР вЂљР РЋР РЏР В РўвЂР РЋРІР‚в„–, Р РЋР С“Р РЋРІР‚С™Р В Р’В°Р РЋРІР‚С™Р РЋРЎвЂњ)
                    } else {
                        // Р В РІР‚СњР В Р’В»Р РЋР РЏ Р В Р вЂ Р РЋР вЂљР В Р’В°Р В РЎвЂ“Р В РЎвЂўР В Р вЂ 
                        if (!pData.isGameStarted) {
                            // Р В РІР‚СњР В РЎвЂў Р РЋР С“Р РЋРІР‚С™Р В Р’В°Р РЋР вЂљР РЋРІР‚С™Р В Р’В° Р В РЎвЂР В РЎвЂ“Р РЋР вЂљР РЋРІР‚в„–: Р В РЎвЂўР РЋРІР‚С™Р В РЎвЂ”Р РЋР вЂљР В Р’В°Р В Р вЂ Р В Р’В»Р РЋР РЏР В Р’ВµР В РЎВ K/D, Р В Р вЂ¦Р В РЎвЂў Р РЋР С“Р В РЎвЂќР РЋР вЂљР РЋРІР‚в„–Р В Р вЂ Р В Р’В°Р В Р’ВµР В РЎВ Р В РЎвЂўР РЋРІР‚С™Р РЋР вЂљР РЋР РЏР В РўвЂР РЋРІР‚в„– (-1)
                            toSend.add(new PlayerStatInfo(
                                    info.name, info.team, -1, false, false, info.kills, info.deaths, info.ping
                            ));
                        } else {
                            // Р В РІР‚в„ўР В РЎвЂў Р В Р вЂ Р РЋР вЂљР В Р’ВµР В РЎВР РЋР РЏ Р В РЎвЂР В РЎвЂ“Р РЋР вЂљР РЋРІР‚в„–: Р РЋР С“Р В РЎвЂќР РЋР вЂљР РЋРІР‚в„–Р В Р вЂ Р В Р’В°Р В Р’ВµР В РЎВ Р В РЎвЂўР РЋРІР‚С™Р РЋР вЂљР РЋР РЏР В РўвЂР РЋРІР‚в„– Р В РЎвЂ K/D (K/D Р В Р’В·Р В Р’В°Р В РЎВР В Р’ВµР В Р вЂ¦Р РЋР РЏР В Р’ВµР РЋРІР‚С™Р РЋР С“Р РЋР РЏ Р В Р вЂ¦Р В Р’В° -1 Р В Р вЂ  stripped)
                            toSend.add(info.stripped());
                        }
                    }
                }
                PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> p), new PacketSyncPlayerStats(toSend));
            }
        }
        // === 2. Р В РІР‚С”Р В РЎвЂєР В РІР‚СљР В Р’ВР В РЎв„ўР В РЎвЂ™ Р В РІР‚в„ўР В РЎСљР В Р в‚¬Р В РЎС›Р В Р’В Р В Р’В Р В Р’ВР В РІР‚вЂќР В РЎС™Р В РІР‚СћР В Р’В Р В РІР‚СћР В РЎСљР В Р’ВР В РІвЂћСћ (Р В РІР‚вЂќР В Р’В°Р РЋРІР‚В¦Р В Р вЂ Р В Р’В°Р РЋРІР‚С™Р РЋРІР‚в„–, Р В РЎС›Р В Р’ВµР РЋРІР‚В¦Р В Р вЂ¦Р В РЎвЂР В РЎвЂќР В Р’В°, Р В РЎС›Р В РЎвЂР В РЎвЂќР В Р’ВµР РЋРІР‚С™Р РЋРІР‚в„–) ===
        for (ServerLevel level : server.getAllLevels()) {
            AASWorldData data = AASWorldData.get(level);

            handleMainProtectionZones(level, data);
            handleMainProtectionZones(level, data);
            handleLobbyZone(level, data);
            if (globalTick % 400 == 0) {
                handleVehicleAutoReturn(level, data);
            }
            boolean blueIsBleeding = false;
            boolean redIsBleeding = false;

            // Р В РЎв„ўР В РЎвЂўР В РўвЂ Р В РЎвЂ“Р В РЎвЂўР В Р’В»Р В РЎвЂўР РЋР С“Р В РЎвЂўР В Р вЂ Р В Р’В°Р В Р вЂ¦Р В РЎвЂР РЋР РЏ Р В Р’В·Р В Р’В° Р В РЎвЂќР В РЎвЂўР В РЎВР В Р’В°Р В Р вЂ¦Р В РўвЂР В РЎвЂР РЋР вЂљР В Р’В°
            if (data.blueCmdVoteActive) {
                data.blueCmdVoteTimer--;
                checkCmdVoteStatus(level, server, data, "BLUE");
            }
            if (data.redCmdVoteActive) {
                data.redCmdVoteTimer--;
                checkCmdVoteStatus(level, server, data, "RED");
            }

            // Р В РЎв„ўР В РЎвЂўР В РўвЂ Р В РЎвЂ”Р РЋР вЂљР В Р’ВµР В РўвЂР В РЎВР В Р’В°Р РЋРІР‚С™Р РЋРІР‚РЋР В Р’ВµР В Р вЂ Р В РЎвЂўР В РЎвЂ“Р В РЎвЂў Р В РЎвЂ“Р В РЎвЂўР В Р’В»Р В РЎвЂўР РЋР С“Р В РЎвЂўР В Р вЂ Р В Р’В°Р В Р вЂ¦Р В РЎвЂР РЋР РЏ
            if (data.voteActive && !data.isGameStarted) {
                if (globalTick % 20 == 0 && data.voteTimer > 0) {
                    data.voteTimer--;
                    data.setDirty();
                    boolean bReady = isTeamReady(level, "Blue", data);
                    boolean rReady = isTeamReady(level, "Red", data);
                    if (bReady != data.blueReady || rReady != data.redReady || data.voteTimer % 5 == 0 || data.voteTimer < 5) {
                        data.blueReady = bReady;
                        data.redReady = rReady;
                        PacketHandler.sendToAllClients(level, data);
                    }
                }
                if ((data.blueReady && data.redReady) || (data.voteTimer <= 0)) {
                    data.voteActive = false;
                    data.setDirty();
                    startGameCountdown(level);
                    PacketHandler.sendToAllClients(level, data);
                }
            }

            // --- Р В РЎвЂєР В РІР‚ВР В РЎСљР В РЎвЂєР В РІР‚в„ўР В РІР‚С”Р В РІР‚СћР В РЎСљР В Р’ВР В РІР‚Сћ Р В РЎС›Р В РІР‚СћР В РўС’Р В РЎСљР В Р’ВР В РЎв„ўР В Р’В Р В РЎСљР В РЎвЂ™ Р В РЎв„ўР В РЎвЂ™Р В Р’В Р В РЎС›Р В РІР‚Сћ ---
            if (globalTick % 5 == 0) {
                boolean needsSync = false;
                for (AASWorldData.VehicleRecord record : data.markedVehicles) {
                    Entity vEntity = level.getEntity(record.uuid);
                    if (vEntity != null && vEntity.isAlive()) {
                        float currentYaw = vEntity.getYRot();
                        if (record.x != vEntity.getX() || record.yaw != currentYaw) {
                            record.x = vEntity.getX();
                            record.y = vEntity.getY();
                            record.z = vEntity.getZ();
                            record.yaw = currentYaw;
                            needsSync = true;
                        }
                    }
                }
                if (needsSync) {
                    data.setDirty();
                    PacketHandler.sendToAllClients(level, data);
                }
            }

            // Р В РЎвЂєР РЋРІР‚РЋР В РЎвЂР РЋР С“Р РЋРІР‚С™Р В РЎвЂќР В Р’В° Р В РЎВР В Р’ВµР РЋР вЂљР РЋРІР‚С™Р В Р вЂ Р РЋРІР‚в„–Р РЋРІР‚В¦ Р В РЎВР В Р’В°Р РЋР вЂљР В РЎвЂќР В Р’ВµР РЋР вЂљР В РЎвЂўР В Р вЂ  Р РЋРІР‚С™Р В Р’ВµР РЋРІР‚В¦Р В Р вЂ¦Р В РЎвЂР В РЎвЂќР В РЎвЂ
            if (globalTick % 40 == 0) {
                boolean changed = false;
                Iterator<AASWorldData.VehicleRecord> it = data.markedVehicles.iterator();
                while (it.hasNext()) {
                    AASWorldData.VehicleRecord record = it.next();
                    Entity vEntity = level.getEntity(record.uuid);
                    if (vEntity != null && !vEntity.isAlive()) {
                        it.remove();
                        changed = true;
                    }
                }
                if (changed) {
                    data.setDirty();
                    PacketHandler.sendToAllClients(level, data);
                }
            }

            // Р В Р в‚¬Р В РўвЂР В Р’В°Р В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂР В Р’Вµ Р РЋР С“Р РЋРІР‚С™Р В Р’В°Р РЋР вЂљР РЋРІР‚в„–Р РЋРІР‚В¦ Р РЋРІР‚С™Р В Р’В°Р В РЎвЂќР РЋРІР‚С™Р В РЎвЂР РЋРІР‚РЋР В Р’ВµР РЋР С“Р В РЎвЂќР В РЎвЂР РЋРІР‚В¦ Р В РЎВР В Р’В°Р РЋР вЂљР В РЎвЂќР В Р’ВµР РЋР вЂљР В РЎвЂўР В Р вЂ 
            if (globalTick % 200 == 0) {
                long time = level.getGameTime();
                if (data.activeMarkers.removeIf(m -> time >= m.expiryTick)) {
                    data.setDirty();
                    PacketHandler.sendToAllClients(level, data);
                }
            }

            // Р В РІР‚С”Р В РЎвЂўР В РЎвЂ“Р В РЎвЂР В РЎвЂќР В Р’В° Р В Р’В±Р В Р’В»Р В РЎвЂўР В РЎвЂќР В РЎвЂР РЋР вЂљР В РЎвЂўР В Р вЂ Р В РЎвЂќР В РЎвЂ Р РЋР вЂљР В Р’В°Р В Р’В»Р В Р’В»Р В РЎвЂ-Р В РЎвЂ”Р В РЎвЂўР В РЎвЂР В Р вЂ¦Р РЋРІР‚С™Р В РЎвЂўР В Р вЂ  Р В Р вЂ Р РЋР вЂљР В Р’В°Р В РЎвЂ“Р В Р’В°Р В РЎВР В РЎвЂ
            if (globalTick % 20 == 0) {
                int rallyRadius = AASConfig.RALLY_BLOCK_RADIUS.get();
                int rallyEnemiesRequired = AASConfig.RALLY_BLOCK_ENEMY_COUNT.get();
                for (AASWorldData.Squad squad : data.squads) {
                    if (squad.rallyPos != null) {
                        int enemies = getEnemyCount(level, squad.rallyPos, squad.team, rallyRadius);
                        boolean currentlyBlocked = (enemies >= rallyEnemiesRequired);
                        if (squad.isRallyBlocked != currentlyBlocked) {
                            squad.isRallyBlocked = currentlyBlocked;
                            data.setDirty();
                            PacketHandler.INSTANCE.send(PacketDistributor.DIMENSION.with(level::dimension), new PacketSyncSquads(data.squads));
                        }
                    }
                }
            }
            // Р В РЎСџР РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р’ВµР РЋР вЂљР В РЎвЂќР В Р’В° Р В РЎвЂќР В РЎвЂР РЋРІР‚С™Р В Р’В° Р В Р’В»Р В РЎвЂР В РўвЂР В Р’ВµР РЋР вЂљР В Р’В° Р В РЎвЂўР РЋРІР‚С™Р РЋР вЂљР РЋР РЏР В РўвЂР В Р’В° (Р В Р’В Р В Р’В°Р В Р’В· Р В Р вЂ  20 Р РЋР С“Р В Р’ВµР В РЎвЂќР РЋРЎвЂњР В Р вЂ¦Р В РўвЂ)
            if (globalTick % 400 == 0 && AASConfig.REQUIRE_OFFICER_FOR_SL.get()) {
                // Р В Р’ВР РЋР С“Р В РЎвЂ”Р В РЎвЂўР В Р’В»Р РЋР Р‰Р В Р’В·Р РЋРЎвЂњР В Р’ВµР В РЎВ Р В РЎвЂР РЋРІР‚С™Р В Р’ВµР РЋР вЂљР В Р’В°Р РЋРІР‚С™Р В РЎвЂўР РЋР вЂљ Р В РЎвЂР В Р’В»Р В РЎвЂ Р В РЎвЂќР В РЎвЂўР В РЎвЂ”Р В РЎвЂР РЋР вЂ№ Р РЋР С“Р В РЎвЂ”Р В РЎвЂР РЋР С“Р В РЎвЂќР В Р’В°, Р РЋРІР‚С™Р В Р’В°Р В РЎвЂќ Р В РЎвЂќР В Р’В°Р В РЎвЂќ Р В Р’В±Р РЋРЎвЂњР В РўвЂР В Р’ВµР В РЎВ Р РЋРЎвЂњР В РўвЂР В Р’В°Р В Р’В»Р РЋР РЏР РЋРІР‚С™Р РЋР Р‰ Р РЋР РЉР В Р’В»Р В Р’ВµР В РЎВР В Р’ВµР В Р вЂ¦Р РЋРІР‚С™Р РЋРІР‚в„– Р В РЎвЂР В Р’В· data.squads
                for (AASWorldData.Squad squad : new ArrayList<>(data.squads)) {
                    ServerPlayer leader = server.getPlayerList().getPlayerByName(squad.leader);

                    if (leader != null) {
                        if (leader.isCreative()) {
                            squad.slNoOfficerSince = -1;
                            continue;
                        }

                        String current = leader.getPersistentData().getString("AAS_CurrentKit");
                        String pending = leader.getPersistentData().getString("AAS_PendingKit");
                        String kitName = !pending.isEmpty() ? pending : current;

                        boolean hasCommandKit = false;
                        if (!kitName.isEmpty() && !kitName.equals("Unassigned")) {
                            String team = squad.team.toUpperCase();
                            AASWorldData.KitInfo kitInfo = team.equals("BLUE") ? data.blueKits.get(kitName) : data.redKits.get(kitName);
                            if (kitInfo != null && kitInfo.isLeaderOnly) {
                                hasCommandKit = true;
                            }
                        }

                        if (!hasCommandKit) {
                            if (squad.slNoOfficerSince == -1) squad.slNoOfficerSince = level.getGameTime();

                            long remaining = 2400 - (level.getGameTime() - squad.slNoOfficerSince);

                            if (remaining <= 0) {
                                // 1. Р В РЎвЂєР В РЎвЂ”Р В РЎвЂўР В Р вЂ Р В Р’ВµР РЋРІР‚В°Р В Р’В°Р В Р’ВµР В РЎВ Р В РЎВР В РЎвЂР РЋР вЂљ
                                broadcastTeamMessage(level, squad.team, Component.translatable("aas.msg.squad_disbanded_no_officer", squad.name).getString(), ChatFormatting.RED);

                                // 2. Р В РЎСџР РЋР вЂљР В РЎвЂўР РЋРІР‚В¦Р В РЎвЂўР В РўвЂР В РЎвЂР В РЎВ Р В РЎвЂ”Р В РЎвЂў Р В Р вЂ Р РЋР С“Р В Р’ВµР В РЎВ Р РЋРЎвЂњР РЋРІР‚РЋР В Р’В°Р РЋР С“Р РЋРІР‚С™Р В Р вЂ¦Р В РЎвЂР В РЎвЂќР В Р’В°Р В РЎВ (Р В РўвЂР В Р’В°Р В Р’В¶Р В Р’Вµ Р В РЎвЂўР РЋРІР‚С›Р РЋРІР‚С›Р В Р’В»Р В Р’В°Р В РІвЂћвЂ“Р В Р вЂ¦) Р В РЎвЂ Р РЋРІР‚РЋР В РЎвЂР РЋР С“Р РЋРІР‚С™Р В РЎвЂР В РЎВ Р В РЎвЂР РЋРІР‚В¦ Р В РўвЂР В Р’В°Р В Р вЂ¦Р В Р вЂ¦Р РЋРІР‚в„–Р В Р’Вµ
                                for (String memberName : new ArrayList<>(squad.members)) {
                                    ServerPlayer m = server.getPlayerList().getPlayerByName(memberName);
                                    if (m != null) {
                                        // Р В Р Р‹Р В Р’В±Р РЋР вЂљР В Р’В°Р РЋР С“Р РЋРІР‚в„–Р В Р вЂ Р В Р’В°Р В Р’ВµР В РЎВ Р В РЎвЂќР В РЎвЂР РЋРІР‚С™
                                        m.getPersistentData().putString("AAS_CurrentKit", "Unassigned");
                                        m.getPersistentData().remove("AAS_PendingKit");

                                        // Р В Р в‚¬Р В РўвЂР В Р’В°Р В Р’В»Р РЋР РЏР В Р’ВµР В РЎВ Р В РЎвЂ”Р РЋР вЂљР В РЎвЂР В Р вЂ Р РЋР РЏР В Р’В·Р В РЎвЂќР РЋРЎвЂњ Р В РЎвЂќ Р В РЎвЂўР РЋРІР‚С™Р РЋР вЂљР РЋР РЏР В РўвЂР РЋРЎвЂњ
                                        m.getPersistentData().remove("AAS_SquadID");
                                        m.getPersistentData().remove("AAS_IsSquadLeader");

                                        // Р В РІР‚вЂќР В Р’В°Р В Р’В±Р В РЎвЂР РЋР вЂљР В Р’В°Р В Р’ВµР В РЎВ Р РЋР вЂљР В Р’В°Р РЋРІР‚В Р В РЎвЂР РЋР вЂ№
                                        PacketSquadAction.removeRadio(m);

                                        // Р В РЎвЂєР РЋРІР‚РЋР В РЎвЂР РЋРІР‚В°Р В Р’В°Р В Р’ВµР В РЎВ Р В РЎвЂР В Р вЂ¦Р В Р вЂ Р В Р’ВµР В Р вЂ¦Р РЋРІР‚С™Р В Р’В°Р РЋР вЂљР РЋР Р‰ (Р РЋРІР‚С™Р В Р’В°Р В РЎвЂќ Р В РЎвЂќР В Р’В°Р В РЎвЂќ Р В Р’В±Р В Р’ВµР В Р’В· Р В РЎвЂўР РЋРІР‚С™Р РЋР вЂљР РЋР РЏР В РўвЂР В Р’В° Р В РЎвЂќР В РЎвЂР РЋРІР‚С™ Р В Р вЂ¦Р В Р’Вµ Р В РЎвЂ”Р В РЎвЂўР В Р’В»Р В РЎвЂўР В Р’В¶Р В Р’ВµР В Р вЂ¦)
                                        m.getInventory().clearContent();
                                        ResupplyHandler.clearCurios(m);
                                        m.inventoryMenu.broadcastChanges();
                                        m.containerMenu.broadcastChanges();

                                        m.displayClientMessage(Component.translatable("aas.msg.squad_disbanded_member").withStyle(ChatFormatting.RED), true);
                                    }
                                }

                                // 3. Р В Р в‚¬Р В РўвЂР В Р’В°Р В Р’В»Р РЋР РЏР В Р’ВµР В РЎВ Р РЋР С“Р В Р’В°Р В РЎВ Р В РЎвЂўР РЋРІР‚С™Р РЋР вЂљР РЋР РЏР В РўвЂ Р В РЎвЂР В Р’В· Р В РўвЂР В Р’В°Р В Р вЂ¦Р В Р вЂ¦Р РЋРІР‚в„–Р РЋРІР‚В¦ Р РЋР С“Р В Р’ВµР РЋР вЂљР В Р вЂ Р В Р’ВµР РЋР вЂљР В Р’В°
                                data.squads.remove(squad);
                                data.setDirty();

                                // 4. Р В Р Р‹Р В РЎвЂР В Р вЂ¦Р РЋРІР‚В¦Р РЋР вЂљР В РЎвЂўР В Р вЂ¦Р В РЎвЂР В Р’В·Р В РЎвЂР РЋР вЂљР РЋРЎвЂњР В Р’ВµР В РЎВ Р РЋР С“Р В РЎвЂ”Р В РЎвЂР РЋР С“Р В РЎвЂўР В РЎвЂќ Р В РЎвЂўР РЋРІР‚С™Р РЋР вЂљР РЋР РЏР В РўвЂР В РЎвЂўР В Р вЂ  Р РЋР С“ Р В РЎвЂќР В Р’В»Р В РЎвЂР В Р’ВµР В Р вЂ¦Р РЋРІР‚С™Р В Р’В°Р В РЎВР В РЎвЂ, Р РЋРІР‚РЋР РЋРІР‚С™Р В РЎвЂўР В Р’В±Р РЋРІР‚в„– Р В РЎВР В Р’ВµР В Р вЂ¦Р РЋР вЂ№ "K" Р В РЎвЂўР В Р’В±Р В Р вЂ¦Р В РЎвЂўР В Р вЂ Р В РЎвЂР В Р’В»Р В РЎвЂўР РЋР С“Р РЋР Р‰
                                PacketHandler.INSTANCE.send(PacketDistributor.DIMENSION.with(level::dimension),
                                        new PacketSyncSquads(data.squads));

                            } else {
                                leader.displayClientMessage(Component.translatable("aas.msg.squad_leader_kit_warning", (remaining / 20)), true);
                            }
                        } else {
                            squad.slNoOfficerSince = -1;
                        }
                    }
                }
            }
            // Р В РІР‚в„ўР В Р вЂ¦Р РЋРЎвЂњР РЋРІР‚С™Р РЋР вЂљР В РЎвЂ Р РЋРІР‚В Р В РЎвЂР В РЎвЂќР В Р’В»Р В Р’В° for (ServerLevel level : server.getAllLevels()) Р В Р вЂ  GameLogicEvents.java

            long now = level.getGameTime();
            boolean squadsChanged = false;

            for (AASWorldData.Squad squad : data.squads) {
                if (squad.rhombusMarkers.removeIf(rm -> now >= rm.expiryTick)) {
                    squadsChanged = true;
                }
                if (squad.bravoRhombusMarkers.removeIf(rm -> now >= rm.expiryTick)) {
                    squadsChanged = true;
                }
                if (squad.charlieRhombusMarkers.removeIf(rm -> now >= rm.expiryTick)) {
                    squadsChanged = true;
                }

                if (squad.rallyPos != null && squad.rallyExpiryTick != -1 && now >= squad.rallyExpiryTick) {

                    // Р В РІР‚СћР РЋР С“Р В Р’В»Р В РЎвЂ Р РЋРІР‚РЋР В Р’В°Р В Р вЂ¦Р В РЎвЂќ Р В Р’В·Р В Р’В°Р В РЎвЂ“Р РЋР вЂљР РЋРЎвЂњР В Р’В¶Р В Р’ВµР В Р вЂ¦, Р В РЎвЂ”Р В РЎвЂўР В РЎВР В Р’ВµР РЋРІР‚РЋР В Р’В°Р В Р’ВµР В РЎВ Р РЋР вЂљР В Р’В°Р В Р’В»Р В Р’В»Р В РЎвЂР В РЎвЂќ Р В РЎвЂќР В Р’В°Р В РЎвЂќ "Р В РЎвЂР РЋР С“Р РЋРІР‚С™Р В Р’ВµР В РЎвЂќР РЋРІвЂљВ¬Р В РЎвЂР В РІвЂћвЂ“", Р РЋРІР‚РЋР РЋРІР‚С™Р В РЎвЂўР В Р’В±Р РЋРІР‚в„– Р В Р вЂ¦Р В Р’Вµ Р В Р’В±Р РЋРІР‚в„–Р В Р’В»Р В РЎвЂў Р РЋРІвЂљВ¬Р РЋРІР‚С™Р РЋР вЂљР В Р’В°Р РЋРІР‚С›Р В Р’В° -20 Р РЋРІР‚С™Р В РЎвЂР В РЎвЂќР В Р’ВµР РЋРІР‚С™Р В РЎвЂўР В Р вЂ 
                    if (level.isLoaded(squad.rallyPos)) {
                        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(squad.rallyPos);
                        if (be instanceof RallyPointBlockEntity rbe) {
                            rbe.isDecay = true;
                        }
                        level.removeBlock(squad.rallyPos, false);
                    } else {
                        // Р В РІР‚СћР РЋР С“Р В Р’В»Р В РЎвЂ Р РЋРІР‚РЋР В Р’В°Р В Р вЂ¦Р В РЎвЂќ Р В РЎСљР В РІР‚Сћ Р В Р’В·Р В Р’В°Р В РЎвЂ“Р РЋР вЂљР РЋРЎвЂњР В Р’В¶Р В Р’ВµР В Р вЂ¦, Р В Р’В±Р В Р’В»Р В РЎвЂўР В РЎвЂќ Р РЋРЎвЂњР В РўвЂР В Р’В°Р В Р’В»Р В РЎвЂР РЋРІР‚С™Р РЋР С“Р РЋР РЏ Р РЋР С“Р В Р’В°Р В РЎВ, Р В РЎвЂќР В РЎвЂўР В РЎвЂ“Р В РўвЂР В Р’В° Р В РЎвЂР В РЎвЂ“Р РЋР вЂљР В РЎвЂўР В РЎвЂќ Р В РЎвЂ”Р В РЎвЂўР В РўвЂР В РЎвЂўР В РІвЂћвЂ“Р В РўвЂР В Р’ВµР РЋРІР‚С™,
                        // Р В Р вЂ¦Р В РЎвЂў Р В РЎвЂР В Р’В· Р В РўвЂР В Р’В°Р В Р вЂ¦Р В Р вЂ¦Р РЋРІР‚в„–Р РЋРІР‚В¦ Р В РЎвЂќР В Р’В°Р РЋР вЂљР РЋРІР‚С™Р РЋРІР‚в„– Р В РЎвЂР В РЎвЂќР В РЎвЂўР В Р вЂ¦Р В РЎвЂќР В Р’В° Р В РўвЂР В РЎвЂўР В Р’В»Р В Р’В¶Р В Р вЂ¦Р В Р’В° Р В РЎвЂ”Р РЋР вЂљР В РЎвЂўР В РЎвЂ”Р В Р’В°Р РЋР С“Р РЋРІР‚С™Р РЋР Р‰ Р В РЎвЂ”Р РЋР вЂљР РЋР РЏР В РЎВР В РЎвЂў Р РЋР С“Р В Р’ВµР В РІвЂћвЂ“Р РЋРІР‚РЋР В Р’В°Р РЋР С“:
                        data.blueRallies.remove(squad.rallyPos);
                        data.redRallies.remove(squad.rallyPos);
                        squad.rallyPos = null;
                        squad.rallyExpiryTick = -1;
                        squadsChanged = true;
                    }
                }
            }

            if (squadsChanged) {
                data.setDirty();
                PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new PacketSyncSquads(data.squads));
            }
            // 2. Р В РЎСџР РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р’ВµР РЋР вЂљР В РЎвЂќР В Р’В° Р В Р Р‹Р В РЎв„ўР В РІР‚в„ўР В РЎвЂ™Р В РІР‚СњР В РЎСљР В Р’В«Р В РўС’ Р В РЎВР В Р’ВµР РЋРІР‚С™Р В РЎвЂўР В РЎвЂќ (Move/Attack Р В РЎвЂ Р РЋРІР‚С™.Р В РўвЂ.) - Р РЋР вЂљР В Р’В°Р В Р’В· Р В Р вЂ  10 Р РЋР С“Р В Р’ВµР В РЎвЂќР РЋРЎвЂњР В Р вЂ¦Р В РўвЂ
            // Р В РІР‚С”Р В РЎвЂР РЋРІвЂљВ¬Р В Р вЂ¦Р РЋР РЏР РЋР РЏ Р РЋР С“Р РЋРІР‚С™Р РЋР вЂљР В РЎвЂўР В РЎвЂќР В Р’В° AASWorldData data = ... Р В Р в‚¬Р В РІР‚СњР В РЎвЂ™Р В РІР‚С”Р В РІР‚СћР В РЎСљР В РЎвЂ™, Р РЋРІР‚С™Р В Р’В°Р В РЎвЂќ Р В РЎвЂќР В Р’В°Р В РЎвЂќ data Р РЋРЎвЂњР В Р’В¶Р В Р’Вµ Р В Р’ВµР РЋР С“Р РЋРІР‚С™Р РЋР Р‰ Р В Р вЂ Р РЋРІР‚в„–Р РЋРІвЂљВ¬Р В Р’Вµ
            if (globalTick % 200 == 0) {
                long currentTick = level.getGameTime();
                boolean anyMarkerRemoved = false;
                for (AASWorldData.Squad squad : data.squads) {
                    if (squad.marker != null && currentTick >= squad.marker.expiryTick) {
                        squad.marker = null; // Р В Р в‚¬Р В РўвЂР В Р’В°Р В Р’В»Р РЋР РЏР В Р’ВµР В РЎВ Р В РЎВР В Р’ВµР РЋРІР‚С™Р В РЎвЂќР РЋРЎвЂњ
                        anyMarkerRemoved = true;
                    }
                }

                if (anyMarkerRemoved) {
                    data.setDirty();
                    // Р В Р’В Р В Р’В°Р РЋР С“Р РЋР С“Р РЋРІР‚в„–Р В Р’В»Р В Р’В°Р В Р’ВµР В РЎВ Р В РЎвЂўР В Р’В±Р В Р вЂ¦Р В РЎвЂўР В Р вЂ Р В Р’В»Р В Р’ВµР В Р вЂ¦Р В Р вЂ¦Р РЋРІР‚в„–Р В Р’Вµ Р В РўвЂР В Р’В°Р В Р вЂ¦Р В Р вЂ¦Р РЋРІР‚в„–Р В Р’Вµ Р В РЎвЂў Р РЋР С“Р В РЎвЂќР В Р вЂ Р В Р’В°Р В РўвЂР В Р’В°Р РЋРІР‚В¦ (Р РЋРІР‚РЋР РЋРІР‚С™Р В РЎвЂўР В Р’В±Р РЋРІР‚в„– Р В РЎвЂ”Р РЋР вЂљР В РЎвЂўР В РЎвЂ”Р В Р’В°Р В Р’В»Р В РЎвЂ Р В Р’В»Р В РЎвЂР В Р вЂ¦Р В РЎвЂР В РЎвЂ)
                    PacketHandler.INSTANCE.send(PacketDistributor.DIMENSION.with(level::dimension),
                            new PacketSyncSquads(data.squads));
                }
            }
            // === 2. Р В РІР‚С”Р В РЎвЂєР В РІР‚СљР В Р’ВР В РЎв„ўР В РЎвЂ™ Р В РЎвЂєР В РЎС›Р В Р Р‹Р В Р’В§Р В РІР‚СћР В РЎС›Р В РЎвЂ™ Р В РІР‚СњР В РЎвЂє Р В Р Р‹Р В РЎС›Р В РЎвЂ™Р В Р’В Р В РЎС›Р В РЎвЂ™ ===
            if (data.countdownActive) { // <--- Р В РЎвЂєР В РІР‚ВР В Р вЂЎР В РІР‚вЂќР В РЎвЂ™Р В РЎС›Р В РІР‚СћР В РІР‚С”Р В Р’В¬Р В РЎСљР В РЎвЂ™Р В Р вЂЎ Р В РЎСџР В Р’В Р В РЎвЂєР В РІР‚в„ўР В РІР‚СћР В Р’В Р В РЎв„ўР В РЎвЂ™
                if (data.countdownTicks > 0) {

                    // Р В Р Р‹Р В Р вЂ¦Р В Р’В°Р РЋРІР‚РЋР В Р’В°Р В Р’В»Р В Р’В° Р В РЎвЂ”Р РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р’ВµР РЋР вЂљР РЋР РЏР В Р’ВµР В РЎВ Р В РЎвЂ Р В РЎвЂ”Р В РЎвЂўР В РЎвЂќР В Р’В°Р В Р’В·Р РЋРІР‚в„–Р В Р вЂ Р В Р’В°Р В Р’ВµР В РЎВ Р РЋРІР‚В Р В РЎвЂР РЋРІР‚С›Р РЋР вЂљР РЋРЎвЂњ, Р В Р’В° Р В РЎС›Р В РЎвЂєР В РІР‚С”Р В Р’В¬Р В РЎв„ўР В РЎвЂє Р В РЎСџР В РЎвЂєР В РЎС›Р В РЎвЂєР В РЎС™ Р РЋРЎвЂњР В РЎВР В Р’ВµР В Р вЂ¦Р РЋР Р‰Р РЋРІвЂљВ¬Р В Р’В°Р В Р’ВµР В РЎВ
                    if (data.countdownTicks % 20 == 0) {
                        int seconds = data.countdownTicks / 20;
                        sendTitleToLevel(level, String.valueOf(seconds), ChatFormatting.YELLOW);
                        level.playSound(null, new BlockPos(0, 100, 0), net.minecraft.sounds.SoundEvents.NOTE_BLOCK_HAT.value(), net.minecraft.sounds.SoundSource.MASTER, 1f, 1f);
                    }

                    data.countdownTicks--;
                    data.setDirty();

                } else {
                    // Р В Р’В­Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚С™ Р В Р’В±Р В Р’В»Р В РЎвЂўР В РЎвЂќ Р РЋР С“Р РЋР вЂљР В Р’В°Р В Р’В±Р В РЎвЂўР РЋРІР‚С™Р В Р’В°Р В Р’ВµР РЋРІР‚С™ Р В РЎвЂєР В РІР‚СњР В Р’ВР В РЎСљ Р В Р’В Р В РЎвЂ™Р В РІР‚вЂќ, Р В РЎвЂќР В РЎвЂўР В РЎвЂ“Р В РўвЂР В Р’В° Р РЋРІР‚С™Р В РЎвЂР В РЎвЂќР В РЎвЂ Р РЋР С“Р РЋРІР‚С™Р В Р’В°Р В Р вЂ¦Р РЋРЎвЂњР РЋРІР‚С™ 0
                    data.countdownActive = false;
                    sendTitleToLevel(level, "GO!", ChatFormatting.GREEN);
                    data.isGameStarted = true;
                    if (data.gameMode.equalsIgnoreCase("INVASION")) {
                        // Р В Р в‚¬Р РЋР С“Р РЋРІР‚С™Р В Р’В°Р В Р вЂ¦Р В Р’В°Р В Р вЂ Р В Р’В»Р В РЎвЂР В Р вЂ Р В Р’В°Р В Р’ВµР В РЎВ Р РЋРІР‚С™Р В Р’В°Р В РІвЂћвЂ“Р В РЎВР В Р’ВµР РЋР вЂљ Р В РЎвЂ”Р В РЎвЂўР В РўвЂР В РЎвЂ“Р В РЎвЂўР РЋРІР‚С™Р В РЎвЂўР В Р вЂ Р В РЎвЂќР В РЎвЂ (Р В РЎВР В РЎвЂР В Р вЂ¦Р РЋРЎвЂњР РЋРІР‚С™Р РЋРІР‚в„– * 60 Р РЋР С“Р В Р’ВµР В РЎвЂќ * 20 Р РЋРІР‚С™Р В РЎвЂР В РЎвЂќР В РЎвЂўР В Р вЂ )
                        data.invasionPrepTicks = AASConfig.INVASION_PREP_TIME_MINUTES.get() * 1200;
                    }
                    data.setDirty();
                    sendSyncPacket(level, data);
                    // === Р В РІР‚в„ўР В Р Р‹Р В РЎС›Р В РЎвЂ™Р В РІР‚в„ўР В Р’ВР В РЎС›Р В Р’В¬ Р В Р Р‹Р В Р’В®Р В РІР‚СњР В РЎвЂ™: Р В РІР‚в„ўР РЋРІР‚в„–Р В РўвЂР В Р’В°Р В Р’ВµР В РЎВ Р В РЎвЂќР В РЎвЂР РЋРІР‚С™Р РЋРІР‚в„– Р РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р вЂ¦Р В РЎвЂў 1 Р РЋР вЂљР В Р’В°Р В Р’В· Р В РЎвЂ”Р РЋР вЂљР В РЎвЂ Р РЋР С“Р РЋРІР‚С™Р В Р’В°Р РЋР вЂљР РЋРІР‚С™Р В Р’Вµ Р В РЎвЂР В РЎвЂ“Р РЋР вЂљР РЋРІР‚в„–! ===
                    // === Р В РЎСљР В РЎвЂєР В РІР‚в„ўР В РЎвЂєР В РІР‚Сћ: Р В РІР‚в„ўР В Р’В«Р В РІР‚СњР В РЎвЂ™Р В РІР‚СћР В РЎС™ Р В РІР‚в„ўР В РІР‚СћР В Р’В©Р В Р’В Р В РІР‚в„ўР В Р Р‹Р В РІР‚СћР В РЎС™ Р В Р’ВР В РІР‚СљР В Р’В Р В РЎвЂєР В РЎв„ўР В РЎвЂ™Р В РЎС™ Р В РЎСџР В Р’В Р В Р’В Р В Р Р‹Р В РЎС›Р В РЎвЂ™Р В Р’В Р В РЎС›Р В РІР‚Сћ ===
                    for (ServerPlayer p : level.players()) {
                        String pending = p.getPersistentData().getString("AAS_PendingKit");
                        String current = p.getPersistentData().getString("AAS_CurrentKit");

                        String kitToApply = !pending.isEmpty() ? pending : current;

                        String pTeam = p.getTeam() != null ? p.getTeam().getName().toUpperCase() : "NEUTRAL";
                        boolean isAttacker = data.gameMode.equalsIgnoreCase("INVASION") && !pTeam.equals(data.invasionDefender);

                        if (isAttacker) {
                            p.displayClientMessage(Component.literal("Gear will be issued after preparation!").withStyle(ChatFormatting.YELLOW), true);
                            continue;
                        }

                        // Р В РІР‚СњР В РЎвЂўР В Р’В±Р В Р’В°Р В Р вЂ Р РЋР Р‰Р РЋРІР‚С™Р В Р’Вµ Р В Р вЂ Р РЋРІР‚в„–Р В Р’В·Р В РЎвЂўР В Р вЂ  Р В Р вЂ Р РЋРІР‚в„–Р В РўвЂР В Р’В°Р РЋРІР‚РЋР В РЎвЂ Р В Р’В·Р В РўвЂР В Р’ВµР РЋР С“Р РЋР Р‰, Р В Р’ВµР РЋР С“Р В Р’В»Р В РЎвЂ Р В Р’ВµР В РЎвЂ“Р В РЎвЂў Р В Р вЂ¦Р В Р’ВµР РЋРІР‚С™
                        ResupplyHandler.tryApplyPendingKit(p, data);
                    }


                    for (BlockPos p : data.triggerBlocks) {
                        if (level.isLoaded(p)) {
                            BlockState st = level.getBlockState(p);
                            if (st.is(ModBlocks.GAME_START_TRIGGER.get())) {
                                level.setBlock(p, st.setValue(GameStartTriggerBlock.POWERED, true), 3);
                                level.scheduleTick(p, ModBlocks.GAME_START_TRIGGER.get(), 20);
                            }
                        }
                    }
                    sendSyncPacket(level, data);
                }
            }
            if (data.isGameStarted && data.invasionPrepTicks > 0) {
                data.invasionPrepTicks--;
                if (data.invasionPrepTicks % 20 == 0) {
                    data.setDirty();
                    PacketHandler.sendToAllClients(level, data);
                }
                if (data.invasionPrepTicks == 0) {
                    data.setDirty();
                    PacketHandler.sendToAllClients(level, data);
                    broadcastMessage(level, Component.translatable("aas.msg.invasion_prep_end").getString(), ChatFormatting.RED);

                    for (ServerPlayer p : level.players()) {
                        String pTeam = p.getTeam() != null ? p.getTeam().getName().toUpperCase() : "NEUTRAL";
                        if (!pTeam.equals(data.invasionDefender) && !pTeam.equals("NEUTRAL")) {
                            ResupplyHandler.tryApplyPendingKit(p, data);
                            p.displayClientMessage(Component.literal("Preparation ended. Gear issued!").withStyle(ChatFormatting.GREEN), false);
                        }
                    }
                    // === Р В РЎСљР В РЎвЂєР В РІР‚в„ўР В Р’В«Р В РІвЂћСћ Р В РІР‚ВР В РІР‚С”Р В РЎвЂєР В РЎв„ў: Р В Р Р‹Р В Р’В Р В РЎвЂ™Р В РІР‚ВР В РЎвЂ™Р В РЎС›Р В Р’В«Р В РІР‚в„ўР В РЎвЂ™Р В РЎСљР В Р’ВР В РІР‚Сћ INVASION PREP Р В РЎС›Р В Р’В Р В Р’ВР В РІР‚СљР В РІР‚СљР В РІР‚СћР В Р’В Р В РЎвЂ™ ===
                    for (BlockPos p : data.triggerBlocks) {
                        if (level.isLoaded(p)) {
                            BlockState st = level.getBlockState(p);
                            if (st.is(ModBlocks.INVASION_PREP_TRIGGER.get())) {
                                level.setBlock(p, st.setValue(InvasionPrepTriggerBlock.POWERED, true), 3);
                                level.scheduleTick(p, ModBlocks.INVASION_PREP_TRIGGER.get(), 20); // 20 Р РЋРІР‚С™Р В РЎвЂР В РЎвЂќР В РЎвЂўР В Р вЂ  = 1 Р РЋР С“Р В Р’ВµР В РЎвЂќ Р РЋР С“Р В РЎвЂР В РЎвЂ“Р В Р вЂ¦Р В Р’В°Р В Р’В»Р В Р’В°
                            }
                        }
                    }
                }
            } // <--- Р В РІР‚в„ўР В РЎвЂєР В РЎС› Р В Р’В­Р В РЎС›Р В РЎвЂ™ Р В Р Р‹Р В РЎв„ўР В РЎвЂєР В РІР‚ВР В РЎв„ўР В РЎвЂ™ Р В РІР‚ВР В Р’В«Р В РІР‚С”Р В РЎвЂ™ Р В РЎСџР В Р’В Р В РЎвЂєР В РЎСџР В Р в‚¬Р В Р’В©Р В РІР‚СћР В РЎСљР В РЎвЂ™ (Р В Р’В·Р В Р’В°Р В РЎвЂќР РЋР вЂљР РЋРІР‚в„–Р В Р вЂ Р В Р’В°Р В Р’ВµР РЋРІР‚С™ Р В РЎвЂ”Р РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р’ВµР РЋР вЂљР В РЎвЂќР РЋРЎвЂњ invasionPrepTicks > 0)
            // Р В РІР‚в„ўР В Р вЂ¦Р РЋРЎвЂњР РЋРІР‚С™Р РЋР вЂљР В РЎвЂ onServerTick, Р РЋРІР‚С™Р В Р’В°Р В РЎВ Р В РЎвЂ“Р В РўвЂР В Р’Вµ Р В РЎвЂР В РўвЂР В Р’ВµР РЋРІР‚С™ Р В РЎвЂўР РЋР С“Р В Р вЂ¦Р В РЎвЂўР В Р вЂ Р В Р вЂ¦Р В Р’В°Р РЋР РЏ Р В Р’В»Р В РЎвЂўР В РЎвЂ“Р В РЎвЂР В РЎвЂќР В Р’В° Р В РЎвЂР В РЎвЂ“Р РЋР вЂљР РЋРІР‚в„–
            if (data.isGameStarted && AASConfig.LOW_TICKETS_SIREN.get()) {
                // Р В РЎСџР РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р’ВµР РЋР вЂљР В РЎвЂќР В Р’В° Р В РўвЂР В Р’В»Р РЋР РЏ Р В Р Р‹Р В РЎвЂР В Р вЂ¦Р В РЎвЂР РЋРІР‚В¦
                if (data.blueTickets <= 50 && data.blueTickets > 0 && !data.playedBlueSiren) {
                    playSirenForTeam(level, "Blue");
                    data.playedBlueSiren = true;
                    data.setDirty();
                }
                // Р В РЎСџР РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р’ВµР РЋР вЂљР В РЎвЂќР В Р’В° Р В РўвЂР В Р’В»Р РЋР РЏ Р В РЎв„ўР РЋР вЂљР В Р’В°Р РЋР С“Р В Р вЂ¦Р РЋРІР‚в„–Р РЋРІР‚В¦
                if (data.redTickets <= 50 && data.redTickets > 0 && !data.playedRedSiren) {
                    playSirenForTeam(level, "Red");
                    data.playedRedSiren = true;
                    data.setDirty();
                }
            }
            // === 3. Р В РІР‚в„ўР В Р’В«Р В Р’В§Р В Р’ВР В Р Р‹Р В РІР‚С”Р В РІР‚СћР В РЎСљР В Р’ВР В РІР‚Сћ TICKET BLITZ (BLEED) ===
            boolean gameEnded = (data.blueTickets <= 0 || data.redTickets <= 0);

            if (data.isGameStarted && !gameEnded && !data.capturePoints.isEmpty()) {
                int totalPoints = data.capturePoints.size();
                long blueOwned = data.capturePoints.stream().filter(p -> p.owner.equalsIgnoreCase("BLUE")).count();
                long redOwned = data.capturePoints.stream().filter(p -> p.owner.equalsIgnoreCase("RED")).count();

                // Р В РІР‚С”Р В РЎвЂєР В РІР‚СљР В Р’ВР В РЎв„ўР В РЎвЂ™: Р В РЎв„ўР В РЎвЂўР В РЎВР В Р’В°Р В Р вЂ¦Р В РўвЂР В Р’В° Р РЋРІР‚С™Р В Р’ВµР РЋР вЂљР РЋР РЏР В Р’ВµР РЋРІР‚С™ Р РЋРІР‚С™Р В РЎвЂР В РЎвЂќР В Р’ВµР РЋРІР‚С™Р РЋРІР‚в„–, Р В Р’ВµР РЋР С“Р В Р’В»Р В РЎвЂ Р РЋРЎвЂњ Р В Р вЂ¦Р В Р’ВµР РЋРІР‚В 0 Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В Р’ВµР В РЎвЂќ, Р В РЎвЂ™ Р РЋРЎвЂњ Р В Р вЂ Р РЋР вЂљР В Р’В°Р В РЎвЂ“Р В Р’В° Р В Р’В·Р В Р’В°Р РЋРІР‚В¦Р В Р вЂ Р В Р’В°Р РЋРІР‚РЋР В Р’ВµР В Р вЂ¦Р В РЎвЂў (Р В РІР‚в„ўР РЋР С“Р В Р’ВµР В РЎвЂ“Р В РЎвЂў - 1) Р В РЎвЂР В Р’В»Р В РЎвЂ Р В Р’В±Р В РЎвЂўР В Р’В»Р РЋР Р‰Р РЋРІвЂљВ¬Р В Р’Вµ.
                if (data.gameMode.equalsIgnoreCase("INVASION")) {
                    // Р В РІР‚в„ў Р В Р’ВР В Р вЂ¦Р В Р вЂ Р В Р’В°Р В Р’В·Р В РЎвЂР В РЎвЂ Р В Р вЂ¦Р В Р’ВµР РЋРІР‚С™ Р В РЎвЂќР В Р’В»Р В Р’В°Р РЋР С“Р РЋР С“Р В РЎвЂР РЋРІР‚РЋР В Р’ВµР РЋР С“Р В РЎвЂќР В РЎвЂўР В РЎвЂ“Р В РЎвЂў Р В Р’В±Р В Р’В»Р В РЎвЂР РЋРІР‚С™Р РЋРІР‚В Р В Р’В° (Р В РЎвЂќР РЋР вЂљР В РЎвЂўР В Р вЂ Р В РЎвЂўР РЋРІР‚С™Р В Р’ВµР РЋРІР‚РЋР В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР РЏ Р РЋРІР‚С™Р В РЎвЂР В РЎвЂќР В Р’ВµР РЋРІР‚С™Р В РЎвЂўР В Р вЂ )
                    blueIsBleeding = false;
                    redIsBleeding = false;

                    // Р В РЎвЂєР В РЎвЂ”Р РЋР вЂљР В Р’ВµР В РўвЂР В Р’ВµР В Р’В»Р РЋР РЏР В Р’ВµР В РЎВ Р В РЎвЂќР В РЎвЂўР В РЎВР В Р’В°Р В Р вЂ¦Р В РўвЂР РЋРЎвЂњ Р В Р’В°Р РЋРІР‚С™Р В Р’В°Р В РЎвЂќР В РЎвЂ
                    String attacker = data.invasionDefender.equalsIgnoreCase("BLUE") ? "RED" : "BLUE";
                    long attackerOwned = (attacker.equals("BLUE")) ? blueOwned : redOwned;

                    // Р В РІР‚СћР РЋР С“Р В Р’В»Р В РЎвЂ Р В Р’В°Р РЋРІР‚С™Р В Р’В°Р В РЎвЂќР В Р’В° Р В Р’В·Р В Р’В°Р РЋРІР‚В¦Р В Р вЂ Р В Р’В°Р РЋРІР‚С™Р В РЎвЂР В Р’В»Р В Р’В° Р В РІР‚в„ўР В Р Р‹Р В РІР‚Сћ Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В РЎвЂќР В РЎвЂ
                    if (attackerOwned >= totalPoints && totalPoints > 0) {
                        if (data.invasionDefender.equalsIgnoreCase("BLUE")) {
                            data.blueTickets = 0; // Р В РІР‚вЂќР В Р’В°Р РЋРІР‚В°Р В РЎвЂР РЋРІР‚С™Р В Р’В° Р В РЎвЂ”Р РЋР вЂљР В РЎвЂўР В РЎвЂР В РЎвЂ“Р РЋР вЂљР В Р’В°Р В Р’В»Р В Р’В°
                        } else {
                            data.redTickets = 0;
                        }
                        checkGameOver(level, data);
                    }
                } else {
                    // Р В Р Р‹Р РЋРІР‚С™Р В Р’В°Р РЋР вЂљР В Р’В°Р РЋР РЏ Р В Р’В»Р В РЎвЂўР В РЎвЂ“Р В РЎвЂР В РЎвЂќР В Р’В° Р В Р’В±Р В Р’В»Р В РЎвЂР РЋРІР‚С™Р РЋРІР‚В Р В Р’В° Р РЋРІР‚С™Р В РЎвЂўР В Р’В»Р РЋР Р‰Р В РЎвЂќР В РЎвЂў Р В РўвЂР В Р’В»Р РЋР РЏ Р РЋР вЂљР В Р’ВµР В Р’В¶Р В РЎвЂР В РЎВР В Р’В° AAS
                    blueIsBleeding = (blueOwned == 0) && (redOwned >= totalPoints - 1) && (totalPoints > 0);
                    redIsBleeding = (redOwned == 0) && (blueOwned >= totalPoints - 1) && (totalPoints > 0);
                }

                // Р В РЎвЂєР РЋРІР‚С™Р В Р вЂ¦Р В РЎвЂР В РЎВР В Р’В°Р В Р’ВµР В РЎВ Р РЋРІР‚С™Р В РЎвЂР В РЎвЂќР В Р’ВµР РЋРІР‚С™Р РЋРІР‚в„– Р В РЎвЂќР В Р’В°Р В Р’В¶Р В РўвЂР РЋРІР‚в„–Р В Р’Вµ 40 Р РЋРІР‚С™Р В РЎвЂР В РЎвЂќР В РЎвЂўР В Р вЂ  (2 Р РЋР С“Р В Р’ВµР В РЎвЂќР РЋРЎвЂњР В Р вЂ¦Р В РўвЂР РЋРІР‚в„–)
                if (globalTick % 40 == 0) {
                    boolean changed = false;
                    if (blueIsBleeding) {
                        data.blueTickets -= 1;
                        changed = true;
                    }
                    if (redIsBleeding) {
                        data.redTickets -= 1;
                        changed = true;
                    }

                    if (changed) {
                        checkGameOver(level, data);
                        data.setDirty();
                    }
                }
            }

            // === 4. Р В РІР‚в„ўР В РЎвЂ™Р В РІР‚С”Р В Р’ВР В РІР‚СњР В РЎвЂ™Р В Р’В¦Р В Р’ВР В Р вЂЎ Р В Р’В Р В Р Р‹Р В Р’ВР В РЎСљР В РўС’Р В Р’В Р В РЎвЂєР В РЎСљР В Р’ВР В РІР‚вЂќР В РЎвЂ™Р В Р’В¦Р В Р’ВР В Р вЂЎ ===
            // Р В РЎСџР В Р’ВµР РЋР вЂљР В Р’ВµР В РўвЂР В Р’В°Р В Р’ВµР В РЎВ Р РЋР С“Р РЋРІР‚С™Р В Р’В°Р РЋРІР‚С™Р РЋРЎвЂњР РЋР С“ Р В Р’В±Р В Р’В»Р В РЎвЂР РЋРІР‚С™Р РЋРІР‚В Р В Р’В° (blueIsBleeding/redIsBleeding) Р В Р вЂ  Р В РЎВР В Р’ВµР РЋРІР‚С™Р В РЎвЂўР В РўвЂ Р РЋР С“Р В РЎвЂР В Р вЂ¦Р РЋРІР‚В¦Р РЋР вЂљР В РЎвЂўР В Р вЂ¦Р В РЎвЂР В Р’В·Р В Р’В°Р РЋРІР‚В Р В РЎвЂР В РЎвЂ,
            // Р РЋРІР‚РЋР РЋРІР‚С™Р В РЎвЂўР В Р’В±Р РЋРІР‚в„– Р В РЎвЂќР В Р’В»Р В РЎвЂР В Р’ВµР В Р вЂ¦Р РЋРІР‚С™ Р РЋРЎвЂњР В Р вЂ Р В РЎвЂР В РўвЂР В Р’ВµР В Р’В» Р В РЎВР В РЎвЂР В РЎвЂ“Р В Р’В°Р В Р вЂ¦Р В РЎвЂР В Р’Вµ Р РЋРІР‚С™Р В РЎвЂР В РЎвЂќР В Р’ВµР РЋРІР‚С™Р В РЎвЂўР В Р вЂ .
            if (globalTick % 20 == 0) {
                validateAndSync(level, false, blueIsBleeding, redIsBleeding);
            }

            // === Р В РЎвЂєР В Р’В§Р В РЎв„ўР В Р’В Р В РІР‚вЂќР В РЎвЂ™ Р В РІР‚в„ўР В РЎвЂєР В РІР‚вЂњР В РІР‚СњР В РІР‚СћР В РЎСљР В Р’ВР В РІР‚Сћ Р В РЎС›Р В РІР‚СћР В РўС’Р В РЎСљР В Р’ВР В РЎв„ўР В Р’В ===
            if (globalTick % 20 == 0) {
                for (ServerPlayer p : level.players()) {
                    if (p.getVehicle() != null && p.getVehicle().getControllingPassenger() == p) {
                        // Р В Р Р‹Р РЋРІР‚РЋР В РЎвЂР РЋРІР‚С™Р В Р’В°Р В Р’ВµР В РЎВ Р В РЎвЂ”Р В Р’В°Р РЋР С“Р РЋР С“Р В Р’В°Р В Р’В¶Р В РЎвЂР РЋР вЂљР В РЎвЂўР В Р вЂ -Р В Р’В»Р РЋР вЂ№Р В РўвЂР В Р’ВµР В РІвЂћвЂ“, Р В РЎвЂќР РЋР вЂљР В РЎвЂўР В РЎВР В Р’Вµ Р В Р вЂ Р В РЎвЂўР В РўвЂР В РЎвЂР РЋРІР‚С™Р В Р’ВµР В Р’В»Р РЋР РЏ
                        long passCount = p.getVehicle().getPassengers().stream().filter(e -> e instanceof ServerPlayer).count() - 1;
                        if (passCount > 0) {
                            int driveTimer = p.getPersistentData().getInt("AAS_DriveStatsTimer") + 1;
                            if (driveTimer >= 60) { // Р В РЎСџР РЋР вЂљР В РЎвЂўР РЋРІвЂљВ¬Р В Р’В»Р В Р’В° 1 Р В РЎВР В РЎвЂР В Р вЂ¦Р РЋРЎвЂњР РЋРІР‚С™Р В Р’В° Р В Р вЂ Р В РЎвЂўР В Р’В¶Р В РўвЂР В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР РЏ
                                StatsHandler.addStats(p, (int)passCount * 5, 0, "Transporting Team");
                                p.getPersistentData().putInt("AAS_DriveStatsTimer", 0);
                            } else {
                                p.getPersistentData().putInt("AAS_DriveStatsTimer", driveTimer);
                            }
                        } else {
                            p.getPersistentData().remove("AAS_DriveStatsTimer");
                        }
                    } else {
                        p.getPersistentData().remove("AAS_DriveStatsTimer");
                    }
                }
            }

            if (data.countdownActive && data.countdownTicks == 1) {
                long cdTicks = AASConfig.ART_STRIKE_COOLDOWN_MINUTES.get() * 60L * 20L;
                data.blueArtStrikeCD = level.getGameTime() + cdTicks;
                data.redArtStrikeCD = level.getGameTime() + cdTicks;
            }

            // 2. Р В РЎС›Р В РЎвЂР В РЎвЂќР В Р’В°Р В Р’ВµР В РЎВ Р В Р’В·Р В Р’В°Р В РЎвЂ”Р РЋР вЂљР В РЎвЂўР РЋР С“Р РЋРІР‚в„– CMD (PgUp/PgDn)
            if (data.blueArtRequest != null) {
                data.blueArtRequest.timer--;
                if (data.blueArtRequest.timer <= 0) {
                    data.blueArtRequest = null;
                    data.setDirty();
                    PacketHandler.sendToAllClients(level, data); // Р В Р Р‹Р В Р’ВР В РЎСљР В РўС’Р В Р’В Р В РЎвЂєР В РЎСљР В Р’ВР В РІР‚вЂќР В РЎвЂ™Р В Р’В¦Р В Р’ВР В Р вЂЎ
                }
            }
            if (data.redArtRequest != null) {
                data.redArtRequest.timer--;
                if (data.redArtRequest.timer <= 0) {
                    data.redArtRequest = null;
                    data.setDirty();
                    PacketHandler.sendToAllClients(level, data); // Р В Р Р‹Р В Р’ВР В РЎСљР В РўС’Р В Р’В Р В РЎвЂєР В РЎСљР В Р’ВР В РІР‚вЂќР В РЎвЂ™Р В Р’В¦Р В Р’ВР В Р вЂЎ
                }
            }
        }
    }
    // Р В РЎС™Р В Р’ВµР РЋРІР‚С™Р В РЎвЂўР В РўвЂ Р РЋР С“Р В РЎвЂ”Р В Р’В°Р В Р вЂ Р В Р вЂ¦Р В Р’В° Р РЋР С“Р В Р вЂ¦Р В Р’В°Р РЋР вЂљР РЋР РЏР В РўвЂР В Р’В° (Р В РЎвЂР РЋР С“Р В РЎвЂ”Р РЋР вЂљР В Р’В°Р В Р вЂ Р В Р’В»Р В Р’ВµР В Р вЂ¦: Р РЋРІР‚С™Р В Р’ВµР В РЎвЂ”Р В Р’ВµР РЋР вЂљР РЋР Р‰ Р В Р’В±Р В Р’ВµР В Р’В· Р В РЎвЂќР В РЎвЂўР В РЎВР В Р’В°Р В Р вЂ¦Р В РўвЂ, Р В Р вЂ¦Р В Р’В°Р В РЎвЂ”Р РЋР вЂљР РЋР РЏР В РЎВР РЋРЎвЂњР РЋР вЂ№ Р РЋРІР‚РЋР В Р’ВµР РЋР вЂљР В Р’ВµР В Р’В· API)
    private static void spawnArtShell(ServerLevel level, BlockPos pos) {
        int rad = AASConfig.ART_STRIKE_RADIUS.get();
        double x = pos.getX() + (level.random.nextDouble() * rad * 2) - rad;
        double z = pos.getZ() + (level.random.nextDouble() * rad * 2) - rad;

        // Р В РІР‚в„ўР РЋРІР‚в„–Р РЋР С“Р В РЎвЂўР РЋРІР‚С™Р В Р’В° Р РЋР С“Р В РЎвЂ”Р В Р’В°Р В Р вЂ Р В Р вЂ¦Р В Р’В° - Р В Р вЂ Р В Р’ВµР РЋР вЂљР РЋРІР‚В¦Р В Р вЂ¦Р РЋР РЏР РЋР РЏ Р В РЎвЂ“Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В РЎвЂР РЋРІР‚В Р В Р’В° Р В РЎВР В РЎвЂР РЋР вЂљР В Р’В°
        double y = level.getMaxBuildHeight() - 2;

        // Р В Р’ВР РЋРІР‚В°Р В Р’ВµР В РЎВ Р РЋРІР‚С™Р В РЎвЂР В РЎвЂ” Р РЋР С“Р В Р вЂ¦Р В Р’В°Р РЋР вЂљР РЋР РЏР В РўвЂР В Р’В° Р В Р вЂ  Р РЋР вЂљР В Р’ВµР В Р’ВµР РЋР С“Р РЋРІР‚С™Р РЋР вЂљР В Р’Вµ
        net.minecraft.world.entity.EntityType<?> shellType = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("superbwarfare", "mortar_shell"));

        if (shellType != null) {
            Entity shell = shellType.create(level);
            if (shell != null) {
                shell.setPos(x, y, z);
                // Р В Р Р‹Р В РЎвЂќР В РЎвЂўР РЋР вЂљР В РЎвЂўР РЋР С“Р РЋРІР‚С™Р РЋР Р‰ Р В РЎвЂ”Р В Р’В°Р В РўвЂР В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР РЏ (3.5 Р В Р’В±Р В Р’В»Р В РЎвЂўР В РЎвЂќР В Р’В° Р В Р’В·Р В Р’В° Р РЋРІР‚С™Р В РЎвЂР В РЎвЂќ)
                shell.setDeltaMovement(0, 0, 0);
                level.addFreshEntity(shell);
            }
        }
    }
    private static boolean isTeamReady(ServerLevel level, String teamName, AASWorldData data) {
        List<ServerPlayer> teamPlayers = level.players().stream()
                .filter(p -> p.getTeam() != null && p.getTeam().getName().equalsIgnoreCase(teamName))
                .toList();

        if (teamPlayers.isEmpty()) return true; // Р В РІР‚СћР РЋР С“Р В Р’В»Р В РЎвЂ Р В Р вЂ  Р В РЎвЂќР В РЎвЂўР В РЎВР В Р’В°Р В Р вЂ¦Р В РўвЂР В Р’Вµ Р В Р вЂ¦Р В РЎвЂР В РЎвЂќР В РЎвЂўР В РЎвЂ“Р В РЎвЂў, Р В РЎвЂўР В Р вЂ¦Р В Р’В° Р В Р вЂ¦Р В Р’Вµ Р В РЎВР В Р’ВµР РЋРІвЂљВ¬Р В Р’В°Р В Р’ВµР РЋРІР‚С™ Р РЋР С“Р РЋРІР‚С™Р В Р’В°Р РЋР вЂљР РЋРІР‚С™Р РЋРЎвЂњ

        long yesVotes = teamPlayers.stream()
                .filter(p -> data.votes.getOrDefault(p.getUUID(), false))
                .count();

        float percent = (float) yesVotes / teamPlayers.size() * 100f;
        boolean isReady = percent >= AASConfig.VOTE_REQUIRED_PERCENTAGE.get();

        return isReady;
    }

    // === Р В РІР‚в„ўР В Р Р‹Р В РЎСџР В РЎвЂєР В РЎС™Р В РЎвЂєР В РІР‚СљР В РЎвЂ™Р В РЎС›Р В РІР‚СћР В РІР‚С”Р В Р’В¬Р В РЎСљР В Р’В«Р В РІвЂћСћ Р В РЎС™Р В РІР‚СћР В РЎС›Р В РЎвЂєР В РІР‚Сњ Р В РІР‚СњР В РІР‚С”Р В Р вЂЎ Р В Р Р‹Р В РІР‚ВР В РЎвЂєР В Р’В Р В РЎвЂ™ Р В РІР‚СњР В РЎвЂ™Р В РЎСљР В РЎСљР В Р’В«Р В РўС’ Р В РЎвЂєР В РІР‚В Р В Р’ВР В РІР‚СљР В Р’В Р В РЎвЂєР В РЎв„ўР В РЎвЂ™Р В РўС’ ===
    private static List<MapPlayerInfo> buildPlayerInfo(List<ServerPlayer> players, AASWorldData data) {
        List<MapPlayerInfo> infoList = new ArrayList<>();

        for (ServerPlayer p : players) {
            String pName = p.getScoreboardName();
            String pTeam = (p.getTeam() != null) ? p.getTeam().getName().toUpperCase() : "NEUTRAL";

            int squadId = p.getPersistentData().getInt("AAS_SquadID");
            if (squadId == 0) squadId = -1;

            boolean isLeader = p.getPersistentData().getBoolean("AAS_IsSquadLeader");

            // Р В РЎСљР В РЎвЂєР В РІР‚в„ўР В РЎвЂєР В РІР‚Сћ: Р РЋР вЂљР В РЎвЂўР В Р’В»Р РЋР Р‰ Р В Р вЂ Р В РЎвЂў Р РЋРІР‚С›Р В Р’В°Р В Р’ВµР РЋР вЂљР РЋРІР‚С™Р В РЎвЂР В РЎВР В Р’Вµ
            String fireteamRole = "";

            for (AASWorldData.Squad s : data.squads) {
                if (s.members.contains(pName)) {
                    squadId = s.id;
                    if (s.leader.equals(pName)) isLeader = true;
                    if (s.bravoLeader.equals(pName)) fireteamRole = "B";
                    else if (s.charlieLeader.equals(pName)) fireteamRole = "C";
                    break;
                }
            }

            boolean inVehicle = p.getVehicle() != null;
            int vId = -1;
            int seatIdx = -1;

            if (inVehicle) {
                Entity vehicle = p.getVehicle();
                vId = vehicle.getId();
                seatIdx = vehicle.getPassengers().indexOf(p);
            }

            boolean downed = p.getPersistentData().getBoolean("AAS_IsDowned");
            boolean dead = !p.isAlive();
            long shout = p.getPersistentData().getLong("AAS_LastMedicShoutTimeMS");

            // Р В РЎСљР В РЎвЂєР В РІР‚в„ўР В РЎвЂєР В РІР‚Сћ: Р РЋР С“Р В Р вЂ¦Р В Р’В°Р РЋР вЂљР РЋР РЏР В Р’В¶Р В Р’ВµР В Р вЂ¦Р В РЎвЂР В Р’Вµ Р В РЎВР В Р’ВµР В РўвЂР В РЎвЂР В РЎвЂќР В Р’В°
            String pKit = p.getPersistentData().getString("AAS_CurrentKit");
            boolean isMedic = "Medic".equalsIgnoreCase(pKit);

            infoList.add(new MapPlayerInfo(
                    pName, p.getUUID(), p.getX(), p.getZ(), p.getYRot(),
                    squadId, isLeader, downed, dead, shout,
                    inVehicle, vId, seatIdx,
                    pTeam, isMedic, fireteamRole
            ));
        }
        return infoList;
    }
    private static List<PlayerStatInfo> buildStatsInfo(List<ServerPlayer> players, AASWorldData data) {
        List<PlayerStatInfo> list = new ArrayList<>();

        for (ServerPlayer p : players) {
            String pName = p.getScoreboardName();
            String pTeam = (p.getTeam() != null) ? p.getTeam().getName().toUpperCase() : "";

            int squadId = -1;
            boolean isLeader = false;
            for (AASWorldData.Squad s : data.squads) {
                if (s.members.contains(pName)) {
                    squadId = s.id;
                    isLeader = s.leader.equals(pName);
                    break;
                }
            }

            boolean isCMD = (pTeam.equals("BLUE") && squadId == data.blueCMDId && data.blueCMDId != -1) ||
                    (pTeam.equals("RED") && squadId == data.redCMDId && data.redCMDId != -1);

            int kills = p.getPersistentData().getInt("AAS_Stats_Kills");
            int deaths = p.getPersistentData().getInt("AAS_Stats_Deaths");
            int ping = p.latency;

            list.add(new PlayerStatInfo(pName, pTeam, squadId, isLeader, isCMD, kills, deaths, ping));
        }
        return list;
    }
    // Р В РІР‚в„ў Р РЋРІР‚С›Р В Р’В°Р В РІвЂћвЂ“Р В Р’В»Р В Р’Вµ GameLogicEvents.java Р В Р’В·Р В Р’В°Р В РЎВР В Р’ВµР В Р вЂ¦Р РЋР РЏР В Р’ВµР В РЎВ Р В РЎВР В Р’ВµР РЋРІР‚С™Р В РЎвЂўР В РўвЂ onWorldTick Р В РЎвЂ”Р В РЎвЂўР В Р’В»Р В Р вЂ¦Р В РЎвЂўР РЋР С“Р РЋРІР‚С™Р РЋР Р‰Р РЋР вЂ№
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;

        Entity entity = event.getEntity();
        ResourceLocation rl = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());

        // Р В РЎСџР РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р’ВµР РЋР вЂљР РЋР РЏР В Р’ВµР В РЎВ, Р РЋР РЏР В Р вЂ Р В Р’В»Р РЋР РЏР В Р’ВµР РЋРІР‚С™Р РЋР С“Р РЋР РЏ Р В Р’В»Р В РЎвЂ Р РЋР РЉР В Р вЂ¦Р РЋРІР‚С™Р В РЎвЂР РЋРІР‚С™Р В РЎвЂ Р В РЎВР В РЎвЂР В Р вЂ¦Р В РЎвЂўР В РІвЂћвЂ“ Р В РЎвЂР В Р’В»Р В РЎвЂ Р В РЎвЂќР В Р’В»Р В Р’ВµР В РІвЂћвЂ“Р В РЎВР В РЎвЂўР РЋР вЂљР В РЎвЂўР В РЎВ Р В РЎвЂР В Р’В· Р В РЎВР В РЎвЂўР В РўвЂР В Р’В° SuperbWarfare
        if (rl != null && (rl.toString().equals("superbwarfare:tm_62") || rl.toString().equals("superbwarfare:claymore"))) {
            ServerLevel serverLevel = (ServerLevel) event.getLevel();
            AASWorldData data = AASWorldData.get(serverLevel);

            // Р В РІР‚СћР РЋР С“Р В Р’В»Р В РЎвЂ Р РЋР РЉР РЋРІР‚С™Р В Р’В° Р В РЎВР В РЎвЂР В Р вЂ¦Р В Р’В° Р РЋРЎвЂњР В Р’В¶Р В Р’Вµ Р В Р’ВµР РЋР С“Р РЋРІР‚С™Р РЋР Р‰ Р В Р вЂ  Р РЋР С“Р В РЎвЂ”Р В РЎвЂР РЋР С“Р В РЎвЂќР В Р’Вµ - Р В Р вЂ¦Р В Р’Вµ Р В РўвЂР В РЎвЂўР В Р’В±Р В Р’В°Р В Р вЂ Р В Р’В»Р РЋР РЏР В Р’ВµР В РЎВ Р В РЎвЂ”Р В РЎвЂўР В Р вЂ Р РЋРІР‚С™Р В РЎвЂўР РЋР вЂљР В Р вЂ¦Р В РЎвЂў
            if (data.markedVehicles.stream().anyMatch(v -> v.uuid.equals(entity.getUUID()))) {
                return;
            }

            String team = "NEUTRAL";

            // Р В РЎСџР РЋРІР‚в„–Р РЋРІР‚С™Р В Р’В°Р В Р’ВµР В РЎВР РЋР С“Р РЋР РЏ Р В Р вЂ Р В Р’В·Р РЋР РЏР РЋРІР‚С™Р РЋР Р‰ Р В РЎвЂќР В РЎвЂўР В РЎВР В Р’В°Р В Р вЂ¦Р В РўвЂР РЋРЎвЂњ Р В РЎвЂР В Р’В· Р РЋР С“Р В РЎвЂўР РЋРІР‚В¦Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В Р’ВµР В Р вЂ¦Р В Р вЂ¦Р РЋРІР‚в„–Р РЋРІР‚В¦ NBT (Р В Р’ВµР РЋР С“Р В Р’В»Р В РЎвЂ Р РЋР РЉР РЋРІР‚С™Р В РЎвЂў Р В РЎвЂ”Р В Р’ВµР РЋР вЂљР В Р’ВµР В Р’В·Р В Р’В°Р В РЎвЂ“Р РЋР вЂљР РЋРЎвЂњР В Р’В·Р В РЎвЂќР В Р’В° Р РЋРІР‚РЋР В Р’В°Р В Р вЂ¦Р В РЎвЂќР В Р’В°)
            if (entity.getPersistentData().contains("AAS_VehicleTeam")) {
                team = entity.getPersistentData().getString("AAS_VehicleTeam");
            } else {
                // Р В Р’ВР РЋРІР‚В°Р В Р’ВµР В РЎВ Р В Р вЂ Р В Р’В»Р В Р’В°Р В РўвЂР В Р’ВµР В Р’В»Р РЋР Р‰Р РЋРІР‚В Р В Р’В° Р В РЎвЂ”Р РЋР вЂљР В РЎвЂ Р В РЎвЂ”Р В Р’ВµР РЋР вЂљР В Р вЂ Р В РЎвЂР РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂўР В РЎВ Р РЋР С“Р В РЎвЂ”Р В Р’В°Р В Р вЂ Р В Р вЂ¦Р В Р’Вµ
                Player owner = null;

                // Р В РЎСџР РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р’ВµР РЋР вЂљР РЋР РЏР В Р’ВµР В РЎВ, Р В Р’ВµР РЋР С“Р В Р’В»Р В РЎвЂ Р РЋР РЉР В Р вЂ¦Р РЋРІР‚С™Р В РЎвЂР РЋРІР‚С™Р В РЎвЂ - Projectile
                if (entity instanceof net.minecraft.world.entity.projectile.Projectile proj) {
                    if (proj.getOwner() instanceof Player p) owner = p;
                }
                // Р В РЎСџР РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р’ВµР РЋР вЂљР РЋР РЏР В Р’ВµР В РЎВ, Р В Р’ВµР РЋР С“Р В Р’В»Р В РЎвЂ Р РЋР РЉР В Р вЂ¦Р РЋРІР‚С™Р В РЎвЂР РЋРІР‚С™Р В РЎвЂ Р РЋР вЂљР В Р’ВµР В Р’В°Р В Р’В»Р В РЎвЂР В Р’В·Р РЋРЎвЂњР В Р’ВµР РЋРІР‚С™ OwnableEntity
                if (owner == null && entity instanceof net.minecraft.world.entity.OwnableEntity ownable) {
                    if (ownable.getOwner() instanceof Player p) owner = p;
                }
                // Р В РІР‚СћР РЋР С“Р В Р’В»Р В РЎвЂ API Р В РЎВР В РЎвЂўР В РўвЂР В Р’В° Р В Р вЂ¦Р В Р’Вµ Р В РўвЂР В Р’В°Р В Р’В»Р В РЎвЂў Р В Р вЂ Р В Р’В»Р В Р’В°Р В РўвЂР В Р’ВµР В Р’В»Р РЋР Р‰Р РЋРІР‚В Р В Р’В°, Р В Р’В±Р В Р’ВµР РЋР вЂљР В Р’ВµР В РЎВ Р В Р’В±Р В Р’В»Р В РЎвЂР В Р’В¶Р В Р’В°Р В РІвЂћвЂ“Р РЋРІвЂљВ¬Р В Р’ВµР В РЎвЂ“Р В РЎвЂў Р В РЎвЂР В РЎвЂ“Р РЋР вЂљР В РЎвЂўР В РЎвЂќР В Р’В° Р В Р вЂ  Р РЋР вЂљР В Р’В°Р В РўвЂР В РЎвЂР РЋРЎвЂњР РЋР С“Р В Р’Вµ 10 Р В Р’В±Р В Р’В»Р В РЎвЂўР В РЎвЂќР В РЎвЂўР В Р вЂ  Р В РЎвЂќР В Р’В°Р В РЎвЂќ Р РЋРІР‚С™Р В РЎвЂўР В РЎвЂ“Р В РЎвЂў, Р В РЎвЂќР РЋРІР‚С™Р В РЎвЂў Р В Р’В±Р РЋР вЂљР В РЎвЂўР РЋР С“Р В РЎвЂР В Р’В»/Р В РЎвЂ”Р В РЎвЂўР РЋР С“Р РЋРІР‚С™Р В Р’В°Р В Р вЂ Р В РЎвЂР В Р’В»
                if (owner == null) {
                    owner = serverLevel.getNearestPlayer(entity, 10.0);
                }

                // Р В РІР‚вЂќР В Р’В°Р В РЎвЂ”Р В РЎвЂР РЋР С“Р РЋРІР‚в„–Р В Р вЂ Р В Р’В°Р В Р’ВµР В РЎВ Р В РЎвЂќР В РЎвЂўР В РЎВР В Р’В°Р В Р вЂ¦Р В РўвЂР РЋРЎвЂњ
                if (owner != null && owner.getTeam() != null) {
                    team = owner.getTeam().getName().toUpperCase();
                    entity.getPersistentData().putString("AAS_VehicleTeam", team);
                    entity.getPersistentData().putString("AAS_VehicleType", "Mine");
                }
            }

            // Р В РІР‚СњР В РЎвЂўР В Р’В±Р В Р’В°Р В Р вЂ Р В Р’В»Р РЋР РЏР В Р’ВµР В РЎВ Р В РЎВР В Р’В°Р РЋР вЂљР В РЎвЂќР В Р’ВµР РЋР вЂљ Р В РўвЂР В Р’В»Р РЋР РЏ Р РЋР С“Р В РЎвЂўР РЋР вЂ№Р В Р’В·Р В Р вЂ¦Р В РЎвЂР В РЎвЂќР В РЎвЂўР В Р вЂ 
            if (!team.equals("NEUTRAL")) {
                data.markedVehicles.add(new AASWorldData.VehicleRecord(
                        entity.getUUID(), team, "Mine", entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), null
                ));
                data.setDirty();

                // Р В РЎвЂєР РЋРІР‚С™Р В РЎвЂ”Р РЋР вЂљР В Р’В°Р В Р вЂ Р В Р’В»Р РЋР РЏР В Р’ВµР В РЎВ Р В РЎвЂ”Р В Р’В°Р В РЎвЂќР В Р’ВµР РЋРІР‚С™ Р РЋР С“Р В РЎвЂР В Р вЂ¦Р РЋРІР‚В¦Р РЋР вЂљР В РЎвЂўР В Р вЂ¦Р В РЎвЂР В Р’В·Р В Р’В°Р РЋРІР‚В Р В РЎвЂР В РЎвЂ Р В РЎвЂќР В Р’В»Р В РЎвЂР В Р’ВµР В Р вЂ¦Р РЋРІР‚С™Р В Р’В°Р В РЎВ
                PacketHandler.sendToAllClients(serverLevel, data);
            }
        }
    }
    @SubscribeEvent
    public static void onWorldTick(TickEvent.LevelTickEvent event) {
        if (event.level.isClientSide || event.phase != TickEvent.Phase.END) return;
        ServerLevel level = (ServerLevel) event.level;
        AASWorldData data = AASWorldData.get(level);
        Set<UUID> playersInPreciseZones = new HashSet<>();

        for (AASWorldData.CapturePoint point : data.capturePoints) {
            List<ServerPlayer> playersInBox = level.getEntitiesOfClass(ServerPlayer.class, point.getBoundingBox());
            int blueOnPointLiving = 0;
            int redOnPointLiving = 0;

            for (ServerPlayer p : playersInBox) {
                if (!p.isAlive() || p.isSpectator()) continue;
                if (p.getPersistentData().getBoolean("AAS_IsDowned")) continue;
                if (point.isInside(p.position())) {
                    if (p.getTeam() != null) {
                        if (p.getTeam().getName().equalsIgnoreCase("Blue")) blueOnPointLiving++;
                        else if (p.getTeam().getName().equalsIgnoreCase("Red")) redOnPointLiving++;
                    }
                }
            }

            String dominantTeam = "NONE";
            int alliesOnPoint = 0;
            boolean isContested = false;
            boolean isTimeLocked = level.getGameTime() < point.lockedUntilTick;

            if (blueOnPointLiving > 0 && redOnPointLiving > 0) {
                if (blueOnPointLiving > redOnPointLiving) {
                    dominantTeam = "BLUE";
                    alliesOnPoint = blueOnPointLiving;
                } else if (redOnPointLiving > blueOnPointLiving) {
                    dominantTeam = "RED";
                    alliesOnPoint = redOnPointLiving;
                } else {
                    // Р В РІР‚СћР РЋР С“Р В Р’В»Р В РЎвЂ Р В Р’В»Р РЋР вЂ№Р В РўвЂР В Р’ВµР В РІвЂћвЂ“ Р В РЎвЂ”Р В РЎвЂўР РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р вЂ¦Р РЋРЎвЂњ - Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В РЎвЂќР В Р’В° Р В РЎвЂўР РЋР С“Р В РЎвЂ”Р В РЎвЂўР РЋР вЂљР В Р’ВµР В Р вЂ¦Р В Р’В° (Contested)
                    isContested = true;
                }
            } else if (blueOnPointLiving > 0) {
                dominantTeam = "BLUE";
                alliesOnPoint = blueOnPointLiving;
            } else if (redOnPointLiving > 0) {
                dominantTeam = "RED";
                alliesOnPoint = redOnPointLiving;
            }

            float multiplier = 1.0f;
            if (alliesOnPoint > 1) multiplier += (alliesOnPoint - 1) * 0.5f;
            if (multiplier > 4.0f) multiplier = 4.0f;

            // --- Р В РІР‚С”Р В РЎвЂєР В РІР‚СљР В Р’ВР В РЎв„ўР В РЎвЂ™ Р В Р Р‹Р В РЎС›Р В Р’В Р В РІР‚СћР В РІР‚С”Р В РЎвЂєР В РЎв„ў Р В Р’В Р В Р’В¦Р В РІР‚в„ўР В РІР‚СћР В РЎС›Р В РЎвЂ™ ---
            int currentRate = 0;
            String teamToSync = "NONE";

            if (!dominantTeam.equals("NONE") && !isContested && !isTimeLocked) {
                boolean isOwner = point.owner.equals(dominantTeam);
                boolean isFullyCaptured = (isOwner && point.progress >= 1.0f);

                // Р В РЎСџР В Р’В Р В РЎвЂєР В РІР‚в„ўР В РІР‚СћР В Р’В Р В РЎв„ўР В РЎвЂ™: Р В Р вЂЎР В Р вЂ Р В Р’В»Р РЋР РЏР В Р’ВµР РЋРІР‚С™Р РЋР С“Р РЋР РЏ Р В Р’В»Р В РЎвЂ Р В РўвЂР В РЎвЂўР В РЎВР В РЎвЂР В Р вЂ¦Р В РЎвЂР РЋР вЂљР РЋРЎвЂњР РЋР вЂ№Р РЋРІР‚В°Р В Р’В°Р РЋР РЏ Р В РЎвЂќР В РЎвЂўР В РЎВР В Р’В°Р В Р вЂ¦Р В РўвЂР В Р’В° Р В Р’В·Р В Р’В°Р РЋРІР‚В°Р В РЎвЂР РЋРІР‚С™Р В Р вЂ¦Р В РЎвЂР В РЎвЂќР В РЎвЂўР В РЎВ Р В Р вЂ  Р РЋР вЂљР В Р’ВµР В Р’В¶Р В РЎвЂР В РЎВР В Р’Вµ Invasion
                boolean isInvDefenderBlocking = data.gameMode.equalsIgnoreCase("INVASION") && dominantTeam.equals(data.invasionDefender);

                // Р В РІР‚СћР РЋР С“Р В Р’В»Р В РЎвЂ Р РЋР РЉР РЋРІР‚С™Р В РЎвЂў Р В РЎСљР В РІР‚Сћ Р В РЎвЂ”Р В РЎвЂўР В Р’В»Р В Р вЂ¦Р В Р’В°Р РЋР РЏ Р В Р’В±Р В Р’В°Р В Р’В·Р В Р’В° Р В РЎвЂ Р РЋР РЉР РЋРІР‚С™Р В РЎвЂў Р В РЎСљР В РІР‚Сћ Р В Р’В·Р В Р’В°Р РЋРІР‚В°Р В РЎвЂР РЋРІР‚С™Р В Р вЂ¦Р В РЎвЂР В РЎвЂќ Р В Р’ВР В Р вЂ¦Р В Р вЂ Р В Р’В°Р В Р’В·Р В РЎвЂР В РЎвЂ Р Р†Р вЂљРІР‚Сњ Р РЋР вЂљР В Р’В°Р В Р’В·Р РЋР вЂљР В Р’ВµР РЋРІвЂљВ¬Р В Р’В°Р В Р’ВµР В РЎВ Р РЋР вЂљР В Р’В°Р РЋР С“Р РЋРІР‚РЋР В Р’ВµР РЋРІР‚С™ Р РЋР С“Р РЋРІР‚С™Р РЋР вЂљР В Р’ВµР В Р’В»Р В РЎвЂўР РЋРІР‚РЋР В Р’ВµР В РЎвЂќ
                if (!isFullyCaptured && !isInvDefenderBlocking) {
                    if (canCapture(point, dominantTeam, data)) {
                        teamToSync = dominantTeam;

                        boolean isNeutralizing = (!point.owner.equals("NEUTRAL") && !point.owner.equals(dominantTeam))
                                || (point.owner.equals("NEUTRAL") && !point.capturingTeam.equals("NONE") && !point.capturingTeam.equals(dominantTeam));

                        currentRate = Math.round(multiplier);
                        if (isNeutralizing) currentRate = -currentRate;
                    }
                }
            }

            for (ServerPlayer p : playersInBox) {
                if (point.isInside(p.position())) {
                    playersInPreciseZones.add(p.getUUID());
                    boolean lockedUI = false;
                    String nextObjectiveForPlayer = "";
                    int lockSecondsLeft = 0;

                    if (p.getTeam() != null) {
                        String teamKey = p.getTeam().getName().equalsIgnoreCase("Blue") ? "BLUE" : "RED";
                        if (isTimeLocked) {
                            lockedUI = true;
                            lockSecondsLeft = (int) Math.max(0, (point.lockedUntilTick - level.getGameTime()) / 20);
                        } else if (!canCapture(point, teamKey, data)) {
                            lockedUI = true;
                            nextObjectiveForPlayer = findRequiredPointName(point, teamKey, data);
                        }
                    }

                    // РћС‚РїСЂР°РІР»СЏРµС‚ РїР°РєРµС‚ РёРіСЂРѕРєСѓ РЅР° С‚РѕС‡РєРµ (С‚РµРїРµСЂСЊ teamToSync С‚РѕР¶Рµ РїСѓСЃС‚, РµСЃР»Рё С‚РѕС‡РєР° Р·Р°Р±Р»РѕРєРёСЂРѕРІР°РЅР°)
                    PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> p),
                            new PacketSyncPoint(true, point.name, point.owner, point.progress, lockedUI, nextObjectiveForPlayer, isContested, teamToSync, currentRate, lockSecondsLeft));
                }
            }

            // --- Р В РІР‚С”Р В РЎвЂєР В РІР‚СљР В Р’ВР В РЎв„ўР В РЎвЂ™ Р В РІР‚вЂќР В РЎвЂ™Р В РўС’Р В РІР‚в„ўР В РЎвЂ™Р В РЎС›Р В РЎвЂ™ Р В Р’В Р В РІР‚в„ўР В РЎвЂєР В Р Р‹Р В Р Р‹Р В РЎС›Р В РЎвЂ™Р В РЎСљР В РЎвЂєР В РІР‚в„ўР В РІР‚С”Р В РІР‚СћР В РЎСљР В Р’ВР В Р вЂЎ (Decay) ---
            boolean isBeingActivelyCaptured = false;

            if (!dominantTeam.equals("NONE") && !isContested && !isTimeLocked) {
                if (canCapture(point, dominantTeam, data)) {

                    // Р В РЎСљР В РЎвЂєР В РІР‚в„ўР В РЎвЂєР В РІР‚Сћ: Р В РІР‚СћР РЋР С“Р В Р’В»Р В РЎвЂ Р В Р вЂ  Р В Р’ВР В Р вЂ¦Р В Р вЂ Р В Р’В°Р В Р’В·Р В РЎвЂР В РЎвЂ Р В Р’В·Р В Р’В°Р РЋРІР‚В°Р В РЎвЂР РЋРІР‚С™Р В Р’В° Р В РЎвЂР В РЎВР В Р’ВµР В Р’ВµР РЋРІР‚С™ Р В Р’В±Р В РЎвЂўР В Р’В»Р РЋР Р‰Р РЋРІвЂљВ¬Р В РЎвЂР В Р вЂ¦Р РЋР С“Р РЋРІР‚С™Р В Р вЂ Р В РЎвЂў Р Р†Р вЂљРІР‚Сњ Р В РЎВР РЋРІР‚в„– Р В РЎвЂ”Р РЋР вЂљР В РЎвЂўР РЋР С“Р РЋРІР‚С™Р В РЎвЂў Р В РЎСљР В РІР‚Сћ Р РЋР С“Р РЋРІР‚С™Р В Р’В°Р В Р вЂ Р В РЎвЂР В РЎВ Р РЋРІР‚С›Р В Р’В»Р В Р’В°Р В РЎвЂ“ Р В Р’В·Р В Р’В°Р РЋРІР‚В¦Р В Р вЂ Р В Р’В°Р РЋРІР‚С™Р В Р’В°
                    if (data.gameMode.equalsIgnoreCase("INVASION") && dominantTeam.equals(data.invasionDefender)) {
                        isBeingActivelyCaptured = false; // Р В РЎС›Р В РЎвЂўР РЋРІР‚РЋР В РЎвЂќР В Р’В° Р В РЎвЂ”Р РЋР вЂљР В РЎвЂўР РЋР С“Р РЋРІР‚С™Р В РЎвЂў Р РЋР С“Р РЋРІР‚С™Р В РЎвЂўР В РЎвЂР РЋРІР‚С™ Р В Р вЂ¦Р В Р’В° Р В РЎВР В Р’ВµР РЋР С“Р РЋРІР‚С™Р В Р’Вµ (Р В Р’В·Р В Р’В°Р В Р’В±Р В Р’В»Р В РЎвЂўР В РЎвЂќР В РЎвЂР РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р’В°Р В Р вЂ¦Р В Р’В°)
                    } else {
                        isBeingActivelyCaptured = true;
                        float baseSpeed = 1.0f / (point.captureTimeMinutes * 60 * 20);
                        String oldOwner = point.owner;

                        handleTeamInfluence(point, dominantTeam, multiplier, baseSpeed, data, level);

                        if (!point.owner.equals(oldOwner) && !point.owner.equals("NEUTRAL")) {
                            if (point.lockDurationMinutes > 0) {
                                point.lockedUntilTick = level.getGameTime() + (point.lockDurationMinutes * 60L * 20L);
                                data.setDirty();
                            }
                        }
                    }
                }
            }

            // Р В РІР‚СћР РЋР С“Р В Р’В»Р В РЎвЂ Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В РЎвЂќР В Р’В° Р В Р вЂ¦Р В Р’Вµ Р В Р’В·Р В Р’В°Р В Р’В±Р В Р’В»Р В РЎвЂўР В РЎвЂќР В РЎвЂР РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р’В°Р В Р вЂ¦Р В Р’В° Р В РЎвЂ”Р В РЎвЂў Р В Р вЂ Р РЋР вЂљР В Р’ВµР В РЎВР В Р’ВµР В Р вЂ¦Р В РЎвЂ, Р В Р вЂ¦Р В Р’Вµ Р В РЎвЂўР РЋР С“Р В РЎвЂ”Р В Р’В°Р РЋР вЂљР В РЎвЂР В Р вЂ Р В Р’В°Р В Р’ВµР РЋРІР‚С™Р РЋР С“Р РЋР РЏ (Contested) Р В РЎвЂ Р В Р’В°Р В РЎвЂќР РЋРІР‚С™Р В РЎвЂР В Р вЂ Р В Р вЂ¦Р В РЎвЂў Р В Р вЂ¦Р В Р’Вµ Р В Р’В·Р В Р’В°Р РЋРІР‚В¦Р В Р вЂ Р В Р’В°Р РЋРІР‚С™Р РЋРІР‚в„–Р В Р вЂ Р В Р’В°Р В Р’ВµР РЋРІР‚С™Р РЋР С“Р РЋР РЏ
            // (Р В Р вЂ¦Р В Р’В°Р В РЎвЂ”Р РЋР вЂљР В РЎвЂР В РЎВР В Р’ВµР РЋР вЂљ: Р В Р вЂ¦Р В РЎвЂР В РЎвЂќР В РЎвЂўР В РЎвЂ“Р В РЎвЂў Р В Р вЂ¦Р В Р’ВµР РЋРІР‚С™, Р В РЎвЂР В Р’В»Р В РЎвЂ Р РЋР С“Р РЋРІР‚С™Р В РЎвЂўР РЋР РЏР РЋРІР‚С™ Р В Р вЂ Р РЋР вЂљР В Р’В°Р В РЎвЂ“Р В РЎвЂ, Р РЋРЎвЂњ Р В РЎвЂќР В РЎвЂўР РЋРІР‚С™Р В РЎвЂўР РЋР вЂљР РЋРІР‚в„–Р РЋРІР‚В¦ Р В Р вЂ¦Р В Р’ВµР РЋРІР‚С™ Р РЋР С“Р В Р вЂ Р РЋР РЏР В Р’В·Р В РЎвЂ Р РЋР С“ Р В РЎвЂ”Р РЋР вЂљР В Р’ВµР В РўвЂР РЋРІР‚в„–Р В РўвЂР РЋРЎвЂњР РЋРІР‚В°Р В Р’ВµР В РІвЂћвЂ“ Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В РЎвЂќР В РЎвЂўР В РІвЂћвЂ“)
            // Р В Р’ВР РЋРІР‚В°Р В Р’ВµР В РЎВ Р РЋР РЉР РЋРІР‚С™Р В РЎвЂўР РЋРІР‚С™ Р В Р’В±Р В Р’В»Р В РЎвЂўР В РЎвЂќ:
            if (!isBeingActivelyCaptured && !isContested && !isTimeLocked) {
                float baseSpeed = 1.0f / (point.captureTimeMinutes * 60 * 20);

                // Р В Р’ВР В РІР‚вЂќР В РЎС™Р В РІР‚СћР В РЎСљР В РІР‚СћР В РЎСљР В РЎСљР В РЎвЂєР В РІР‚Сћ Р В Р в‚¬Р В Р Р‹Р В РІР‚С”Р В РЎвЂєР В РІР‚в„ўР В Р’ВР В РІР‚Сћ: Р В РІР‚СњР В РЎвЂўР В Р’В±Р В Р’В°Р В Р вЂ Р В Р’В»Р РЋР РЏР В Р’ВµР В РЎВ Р В РЎвЂ”Р РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р’ВµР РЋР вЂљР В РЎвЂќР РЋРЎвЂњ Р В Р вЂ¦Р В Р’В° Р РЋР вЂљР В Р’ВµР В Р’В¶Р В РЎвЂР В РЎВ Р В РЎвЂР В РЎвЂ“Р РЋР вЂљР РЋРІР‚в„–
                if (point.owner.equals("NEUTRAL") && point.progress > 0) {

                    // Р В РІР‚СћР РЋР С“Р В Р’В»Р В РЎвЂ Р РЋР вЂљР В Р’ВµР В Р’В¶Р В РЎвЂР В РЎВ Р В РЎСљР В РІР‚Сћ Invasion Р Р†Р вЂљРІР‚Сњ Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В РЎвЂќР В Р’В° Р В РЎвЂўР РЋРІР‚С™Р В РЎвЂќР В Р’В°Р РЋРІР‚С™Р РЋРІР‚в„–Р В Р вЂ Р В Р’В°Р В Р’ВµР РЋРІР‚С™Р РЋР С“Р РЋР РЏ Р В Р вЂ¦Р В Р’В°Р В Р’В·Р В Р’В°Р В РўвЂ (Р РЋР С“Р РЋРІР‚С™Р В Р’В°Р РЋР вЂљР В РЎвЂўР В Р’Вµ Р В РЎвЂ”Р В РЎвЂўР В Р вЂ Р В Р’ВµР В РўвЂР В Р’ВµР В Р вЂ¦Р В РЎвЂР В Р’Вµ)
                    if (!data.gameMode.equalsIgnoreCase("INVASION")) {
                        point.progress -= (baseSpeed / 2.0f);
                        if (point.progress <= 0) {
                            point.progress = 0;
                            point.capturingTeam = "NONE";
                        }
                    }
                    // Р В РІР‚СћР РЋР С“Р В Р’В»Р В РЎвЂ Р РЋР вЂљР В Р’ВµР В Р’В¶Р В РЎвЂР В РЎВ Invasion Р Р†Р вЂљРІР‚Сњ Р В Р вЂ¦Р В РЎвЂР РЋРІР‚РЋР В Р’ВµР В РЎвЂ“Р В РЎвЂў Р В Р вЂ¦Р В Р’Вµ Р В РўвЂР В Р’ВµР В Р’В»Р В Р’В°Р В Р’ВµР В РЎВ (Р В РЎвЂ”Р РЋР вЂљР В РЎвЂўР В РЎвЂ“Р РЋР вЂљР В Р’ВµР РЋР С“Р РЋР С“ Р В РЎвЂ”Р РЋР вЂљР В РЎвЂўР РЋР С“Р РЋРІР‚С™Р В РЎвЂў Р РЋР С“Р РЋРІР‚С™Р В РЎвЂўР В РЎвЂР РЋРІР‚С™ Р В Р вЂ¦Р В Р’В° Р В РЎВР В Р’ВµР РЋР С“Р РЋРІР‚С™Р В Р’Вµ)
                }

                // Р В РІР‚в„ўР В РЎвЂўР РЋР С“Р РЋР С“Р РЋРІР‚С™Р В Р’В°Р В Р вЂ¦Р В РЎвЂўР В Р вЂ Р В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂР В Р’Вµ Р В Р’В·Р В Р’В°Р РЋРІР‚В¦Р В Р вЂ Р В Р’В°Р РЋРІР‚РЋР В Р’ВµР В Р вЂ¦Р В Р вЂ¦Р В РЎвЂўР В РІвЂћвЂ“ Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В РЎвЂќР В РЎвЂ (Р В Р’В±Р В Р’В°Р В Р’В·Р В Р’В° Р РЋР С“Р В Р вЂ Р В РЎвЂўР В РЎвЂР РЋРІР‚В¦ Р В РўвЂР В РЎвЂўР В Р’В»Р В Р’В¶Р В Р вЂ¦Р В Р’В° Р РЋРІР‚РЋР В РЎвЂР В Р вЂ¦Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰Р РЋР С“Р РЋР РЏ Р В Р вЂ Р РЋР С“Р В Р’ВµР В РЎвЂ“Р В РўвЂР В Р’В°)
                else if (!point.owner.equals("NEUTRAL") && point.progress < 1.0f) {
                    point.progress += (baseSpeed / 2.0f);
                    if (point.progress >= 1.0f) {
                        point.progress = 1.0f;
                        point.capturingTeam = "NONE";
                    }
                }
            }

            // === Р В РЎвЂєР В РЎСџР В РЎС›Р В Р’ВР В РЎС™Р В Р’ВР В РІР‚вЂќР В Р’ВР В Р’В Р В РЎвЂєР В РІР‚в„ўР В РЎвЂ™Р В РЎСљР В РЎСљР В РЎвЂ™Р В Р вЂЎ Р В Р’В Р В РЎвЂ™Р В Р Р‹Р В Р Р‹Р В Р’В«Р В РІР‚С”Р В РЎв„ўР В РЎвЂ™ Р В РІР‚СњР В РІР‚С”Р В Р вЂЎ Р В РЎв„ўР В РЎвЂ™Р В Р’В Р В РЎС›Р В Р’В« ===
            if (level.getGameTime() % 10 == 0) {
                boolean isPointActive = (point.progress > 0.0f && point.progress < 1.0f) || !point.capturingTeam.equals("NONE");

                if (isPointActive) {
                    for (ServerPlayer player : level.players()) {
                        if (!playersInPreciseZones.contains(player.getUUID())) {
                            PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                                    new PacketSyncPoint(false, point.name, point.owner, point.progress, false, "", isContested, point.capturingTeam, 0, 0));
                        }
                    }
                }
            }
        }

        for (ServerPlayer player : level.players()) {
            if (!playersInPreciseZones.contains(player.getUUID())) {
                PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                        new PacketSyncPoint(false, "", "", 0, false, "", false, "NONE", 0, 0));
            }
        }
    }

    private static void handleMainProtectionZones(ServerLevel level, AASWorldData data) {
        if (data.mainZones.isEmpty()) return;

        // 1. Р В РЎСџР В РІР‚СћР В Р’В Р В РІР‚в„ўР В Р’В«Р В РІвЂћСћ Р В Р’В¦Р В Р’ВР В РЎв„ўР В РІР‚С”: Р В РЎвЂєР В РЎвЂ“Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В РЎвЂР РЋРІР‚РЋР В Р’ВµР В Р вЂ¦Р В РЎвЂР В Р’Вµ Р В Р вЂ Р РЋРІР‚в„–Р РЋРІР‚В¦Р В РЎвЂўР В РўвЂР В Р’В° Р РЋР С“Р В РЎвЂў Р РЋР С“Р В Р вЂ Р В РЎвЂўР В Р’ВµР В РІвЂћвЂ“ Р В Р’В±Р В Р’В°Р В Р’В·Р РЋРІР‚в„– (Р В РЎС›Р В Р’ВµР В Р’В»Р В Р’ВµР В РЎвЂ”Р В РЎвЂўР РЋР вЂљР РЋРІР‚С™Р В Р’В°Р РЋРІР‚В Р В РЎвЂР РЋР РЏ Р В Р вЂ¦Р В Р’В°Р В Р’В·Р В Р’В°Р В РўвЂ)
        for (ServerPlayer player : level.players()) {
            if (player.isCreative() || player.isSpectator()) continue;
            String pTeam = player.getTeam() != null ? player.getTeam().getName().toUpperCase() : "NEUTRAL";
            if (pTeam.equals("NEUTRAL")) continue;

            for (AASWorldData.MainProtectionZone zone : data.mainZones) {
                if (zone.team.equalsIgnoreCase(pTeam)) {
                    boolean isOutside = !zone.isInside(player.position());
                    boolean shouldRestrict = false;
                    String restrictMessageKey = "aas.msg.main_zone_wait_start";

                    if (!data.isGameStarted) {
                        // Р В РІР‚СњР В РЎвЂў Р В РЎвЂР В РЎвЂ“Р РЋР вЂљР РЋРІР‚в„– - Р В РЎвЂўР В РЎвЂ“Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В РЎвЂР РЋРІР‚РЋР В Р’ВµР В Р вЂ¦Р РЋРІР‚в„– Р В РІР‚в„ўР В Р Р‹Р В РІР‚Сћ
                        shouldRestrict = isOutside;
                        restrictMessageKey = "aas.msg.main_zone_wait_start";
                    } else if (data.gameMode.equalsIgnoreCase("INVASION") && data.invasionPrepTicks > 0) {
                        // Р В РІР‚в„ўР В РЎвЂў Р В Р вЂ Р РЋР вЂљР В Р’ВµР В РЎВР РЋР РЏ Р В РЎвЂ”Р В РЎвЂўР В РўвЂР В РЎвЂ“Р В РЎвЂўР РЋРІР‚С™Р В РЎвЂўР В Р вЂ Р В РЎвЂќР В РЎвЂ Р В Р вЂ  Р В Р’ВР В Р вЂ¦Р В Р вЂ Р В Р’В°Р В Р’В·Р В РЎвЂР В РЎвЂ: Р РЋРІР‚С™Р В РЎвЂўР В Р’В»Р РЋР Р‰Р В РЎвЂќР В РЎвЂў Р В РЎвЂ™Р В РЎС›Р В РЎвЂ™Р В РЎв„ўР В РЎвЂ™ Р В РЎвЂўР В РЎвЂ“Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В РЎвЂР РЋРІР‚РЋР В Р’ВµР В Р вЂ¦Р В Р’В°
                        if (!pTeam.equals(data.invasionDefender)) {
                            shouldRestrict = isOutside;
                            restrictMessageKey = "aas.msg.invasion_prep_wait";
                        }
                    }

                    if (shouldRestrict) {
                        String dim = level.dimension().location().toString();
                        BlockPos spawn = pTeam.equals("BLUE") ? data.blueSpawns.get(dim) : data.redSpawns.get(dim);
                        if (spawn != null) {
                            player.teleportTo(spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5);
                            player.displayClientMessage(Component.translatable(restrictMessageKey).withStyle(ChatFormatting.YELLOW), true);
                        }
                    }
                    break;
                }
            }
        }

        // 2. Р В РІР‚в„ўР В РЎС›Р В РЎвЂєР В Р’В Р В РЎвЂєР В РІвЂћСћ Р В РІР‚ВР В РІР‚С”Р В РЎвЂєР В РЎв„ў: Р В РІР‚вЂќР В Р’В°Р РЋРІР‚В°Р В РЎвЂР РЋРІР‚С™Р В Р’В° Р В Р’В±Р В Р’В°Р В Р’В· Р В РЎвЂўР РЋРІР‚С™ Р В РІР‚в„ўР В Р’В Р В РЎвЂ™Р В РІР‚СљР В РЎвЂєР В РІР‚в„ў (Р В Р в‚¬Р В Р’В±Р В РЎвЂР В РІвЂћвЂ“Р РЋР С“Р РЋРІР‚С™Р В Р вЂ Р В РЎвЂў Р В Р’В·Р В Р’В°Р РЋРІвЂљВ¬Р В Р’ВµР В РўвЂР РЋРІвЂљВ¬Р В РЎвЂР РЋРІР‚В¦ Р В Р вЂ¦Р В Р’В° Р РЋРІР‚РЋР РЋРЎвЂњР В Р’В¶Р В РЎвЂўР В РІвЂћвЂ“ Р В РЎВР В Р’ВµР В РІвЂћвЂ“Р В Р вЂ¦)
        // Р В Р’В Р В Р’В°Р В Р вЂ¦Р РЋР Р‰Р РЋРІвЂљВ¬Р В Р’Вµ Р РЋРІР‚С™Р РЋРЎвЂњР РЋРІР‚С™ Р РЋР С“Р РЋРІР‚С™Р В РЎвЂўР РЋР РЏР В Р’В» Р В РЎвЂўР РЋРІвЂљВ¬Р В РЎвЂР В Р’В±Р В РЎвЂўР РЋРІР‚РЋР В Р вЂ¦Р РЋРІР‚в„–Р В РІвЂћвЂ“ else, Р РЋРІР‚С™Р В Р’ВµР В РЎвЂ”Р В Р’ВµР РЋР вЂљР РЋР Р‰ Р В РЎвЂ”Р РЋР вЂљР В Р’В°Р В Р вЂ Р В РЎвЂР В Р’В»Р РЋР Р‰Р В Р вЂ¦Р РЋРІР‚в„–Р В РІвЂћвЂ“ if
        if (data.isGameStarted) {
            for (AASWorldData.MainProtectionZone zone : data.mainZones) {
                List<Entity> entitiesInZone = level.getEntitiesOfClass(Entity.class, zone.area);

                for (Entity entity : entitiesInZone) {
                    if (!zone.isInside(entity.position())) continue;

                    // 5 Р РЋР С“Р В Р’ВµР В РЎвЂќР РЋРЎвЂњР В Р вЂ¦Р В РўвЂ Р В РЎвЂР В РЎВР В РЎВР РЋРЎвЂњР В Р вЂ¦Р В РЎвЂР РЋРІР‚С™Р В Р’ВµР РЋРІР‚С™Р В Р’В° Р В РўвЂР В Р’В»Р РЋР РЏ Р В Р вЂ¦Р В РЎвЂўР В Р вЂ Р В РЎвЂўР В РІвЂћвЂ“ Р РЋРІР‚С™Р В Р’ВµР РЋРІР‚В¦Р В Р вЂ¦Р В РЎвЂР В РЎвЂќР В РЎвЂ
                    if (entity.getPersistentData().contains("AAS_SpawnGraceTick")) {
                        long graceTick = entity.getPersistentData().getLong("AAS_SpawnGraceTick");
                        if (level.getGameTime() - graceTick < 100) continue;
                    }

                    // Р В РІР‚С”Р В РЎвЂўР В РЎвЂ“Р В РЎвЂР В РЎвЂќР В Р’В° Р В РўвЂР В Р’В»Р РЋР РЏ Р В РЎвЂР В РЎвЂ“Р РЋР вЂљР В РЎвЂўР В РЎвЂќР В РЎвЂўР В Р вЂ -Р В Р вЂ Р РЋР вЂљР В Р’В°Р В РЎвЂ“Р В РЎвЂўР В Р вЂ 
                    if (entity instanceof ServerPlayer player) {
                        if (player.isCreative() || player.isSpectator()) continue;
                        String pTeam = player.getTeam() != null ? player.getTeam().getName().toUpperCase() : "NEUTRAL";

                        if (!pTeam.equalsIgnoreCase(zone.team)) {
                            int ticks = player.getPersistentData().getInt("AAS_MainZoneTimer");
                            ticks++;
                            if (ticks >= 200) {
                                player.getPersistentData().remove("AAS_MainZoneTimer");
                                player.kill();
                            } else {
                                player.getPersistentData().putInt("AAS_MainZoneTimer", ticks);
                                if (ticks % 20 == 0) {
                                    int secLeft = 10 - (ticks / 20);
                                    player.connection.send(new ClientboundSetTitlesAnimationPacket(0, 30, 0));
                                    player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("aas.msg.main_zone_die", secLeft).withStyle(ChatFormatting.RED)));
                                    player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("aas.msg.enemy_main_title").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)));
                                }
                            }
                        } else {
                            player.getPersistentData().remove("AAS_MainZoneTimer");
                        }
                    }
                    // Р В РІР‚С”Р В РЎвЂўР В РЎвЂ“Р В РЎвЂР В РЎвЂќР В Р’В° Р В РўвЂР В Р’В»Р РЋР РЏ Р В Р вЂ Р РЋР вЂљР В Р’В°Р В Р’В¶Р В Р’ВµР РЋР С“Р В РЎвЂќР В РЎвЂР РЋРІР‚В¦ Р В РЎВР В Р’В°Р РЋРІвЂљВ¬Р В РЎвЂР В Р вЂ¦
                    else if (entity.getPersistentData().contains("AAS_VehicleTeam")) {
                        String vTeam = entity.getPersistentData().getString("AAS_VehicleTeam");
                        if (vTeam.equalsIgnoreCase("NEUTRAL") || vTeam.isEmpty()) {
                            entity.discard();
                        } else if (vTeam.equalsIgnoreCase(zone.team)) {
                            entity.getPersistentData().remove("AAS_MainZoneTimer");
                        } else {
                            int ticks = entity.getPersistentData().getInt("AAS_MainZoneTimer");
                            ticks++;
                            if (ticks >= 200) {
                                if (entity instanceof LivingEntity le) le.kill();
                                else entity.discard();
                            } else {
                                entity.getPersistentData().putInt("AAS_MainZoneTimer", ticks);
                                if (ticks % 20 == 0) {
                                    int secLeft = 10 - (ticks / 20);
                                    for (Entity passenger : entity.getPassengers()) {
                                        if (passenger instanceof ServerPlayer p) {
                                            p.displayClientMessage(Component.translatable("aas.msg.main_zone_veh_die", secLeft).withStyle(ChatFormatting.RED, ChatFormatting.BOLD), true);
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else {
                        if (entity instanceof net.minecraftforge.entity.PartEntity) continue;
                        if (entity instanceof net.minecraft.world.entity.Display) continue;
                        if (entity instanceof net.minecraft.world.entity.item.ItemEntity) continue;
                        entity.discard();
                    }
                }
            }

            // Р В РЎвЂєР РЋРІР‚РЋР В РЎвЂР РЋР С“Р РЋРІР‚С™Р В РЎвЂќР В Р’В° Р РЋРІР‚С™Р В Р’В°Р В РІвЂћвЂ“Р В РЎВР В Р’ВµР РЋР вЂљР В РЎвЂўР В Р вЂ  Р РЋРІР‚С™Р В Р’ВµР РЋРІР‚В¦, Р В РЎвЂќР РЋРІР‚С™Р В РЎвЂў Р В Р вЂ Р РЋРІР‚в„–Р В Р’ВµР РЋРІР‚В¦Р В Р’В°Р В Р’В»
            for (ServerPlayer player : level.players()) {
                if (player.getPersistentData().contains("AAS_MainZoneTimer")) {
                    boolean inEnemyZone = false;
                    String pTeam = player.getTeam() != null ? player.getTeam().getName().toUpperCase() : "NEUTRAL";
                    for (AASWorldData.MainProtectionZone zone : data.mainZones) {
                        if (!zone.team.equalsIgnoreCase(pTeam) && zone.isInside(player.position())) {
                            inEnemyZone = true; break;
                        }
                    }
                    if (!inEnemyZone) player.getPersistentData().remove("AAS_MainZoneTimer");
                }
            }
            // (Р В РЎвЂєР РЋРІР‚РЋР В РЎвЂР РЋР С“Р РЋРІР‚С™Р В РЎвЂќР В Р’В° Р РЋРІР‚С™Р В Р’В°Р В РІвЂћвЂ“Р В РЎВР В Р’ВµР РЋР вЂљР В РЎвЂўР В Р вЂ  Р В РЎВР В Р’В°Р РЋРІвЂљВ¬Р В РЎвЂР В Р вЂ¦ Р В Р’В°Р В Р вЂ¦Р В Р’В°Р В Р’В»Р В РЎвЂўР В РЎвЂ“Р В РЎвЂР РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂў...)
            for (AASWorldData.VehicleRecord record : data.markedVehicles) {
                Entity vehicle = level.getEntity(record.uuid);
                if (vehicle != null && vehicle.getPersistentData().contains("AAS_MainZoneTimer")) {
                    boolean inEnemyZone = false;
                    String vTeam = vehicle.getPersistentData().getString("AAS_VehicleTeam");
                    for (AASWorldData.MainProtectionZone zone : data.mainZones) {
                        if (!zone.team.equalsIgnoreCase(vTeam) && zone.isInside(vehicle.position())) {
                            inEnemyZone = true; break;
                        }
                    }
                    if (!inEnemyZone) vehicle.getPersistentData().remove("AAS_MainZoneTimer");
                }
            }
        }
    }
    private static void handleVehicleAutoReturn(ServerLevel level, AASWorldData data) {
        if (data.markedVehicles.isEmpty()) return;
        long currentTick = level.getGameTime();
        boolean needsSync = false;

        // Р Р°Р±РѕС‚Р°РµРј СЃРѕ СЃРЅРёРјРєРѕРј СЃРїРёСЃРєР°, С‚.Рє. vEntity.discard() РЅРёР¶Рµ СЃРёРЅС…СЂРѕРЅРЅРѕ
        // С‚СЂРёРіРіРµСЂРёС‚ EntityLeaveLevelEvent -> onEntityRemove, РєРѕС‚РѕСЂС‹Р№ СЃР°Рј
        // С‡РёСЃС‚РёС‚ data.markedVehicles.removeIf(...) вЂ” РёР·РјРµРЅРµРЅРёРµ РѕСЂРёРіРёРЅР°Р»СЊРЅРѕРіРѕ
        // СЃРїРёСЃРєР° РїСЂСЏРјРѕ РІРѕ РІСЂРµРјСЏ РµРіРѕ РѕР±С…РѕРґР° РІС‹Р·С‹РІР°Р»Рѕ ConcurrentModificationException.
        for (AASWorldData.VehicleRecord record : new ArrayList<>(data.markedVehicles)) {
            if (!record.autoReturnEnabled) continue;

            long requiredTicks = (long) record.autoReturnTimeSeconds * 20L;
            Entity vEntity = level.getEntity(record.uuid);

            if (vEntity != null && vEntity.isAlive()) {
                if (hasPlayerOccupant(vEntity)) {
                    if (record.emptySinceTick != -1) { record.emptySinceTick = -1; needsSync = true; }
                    continue;
                }
                if (record.emptySinceTick == -1) {
                    record.emptySinceTick = currentTick;
                    needsSync = true;
                    continue;
                }
            } else {
                if (record.emptySinceTick == -1) {
                    record.emptySinceTick = currentTick;
                    needsSync = true;
                    continue;
                }
            }

            long elapsed = currentTick - record.emptySinceTick;
            if (elapsed < requiredTicks) continue;

            if (vEntity == null || !vEntity.isAlive()) {
                int cx = ((int) Math.floor(record.x)) >> 4;
                int cz = ((int) Math.floor(record.z)) >> 4;
                level.getChunk(cx, cz);
                vEntity = level.getEntity(record.uuid);
                if (vEntity == null || !vEntity.isAlive()) continue;
                if (hasPlayerOccupant(vEntity)) {
                    record.emptySinceTick = -1;
                    needsSync = true;
                    continue;
                }
            }

            boolean insideOwnMain = false;
            for (AASWorldData.MainProtectionZone zone : data.mainZones) {
                if (zone.team.equalsIgnoreCase(record.team) && zone.isInside(vEntity.position())) {
                    insideOwnMain = true;
                    break;
                }
            }
            if (insideOwnMain) {
                record.emptySinceTick = -1;
                needsSync = true;
                continue;
            }

            if (record.autoReturnDestroy) {
                vEntity.discard(); // Р±РµР·РѕРїР°СЃРЅРѕ: С‚РµРїРµСЂСЊ РјС‹ РёС‚РµСЂРёСЂСѓРµРјСЃСЏ РїРѕ РєРѕРїРёРё СЃРїРёСЃРєР°
            } else if (record.spawnerPos != null) {
                vEntity.ejectPassengers();
                vEntity.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
                double tx = record.spawnerPos.getX() + 0.5;
                double ty = record.spawnerPos.getY() + 2.5;
                double tz = record.spawnerPos.getZ() + 0.5;
                vEntity.teleportTo(tx, ty, tz);
                vEntity.getPersistentData().putLong("AAS_SpawnGraceTick", level.getGameTime());
                record.x = tx; record.y = ty; record.z = tz;
            }

            record.emptySinceTick = -1;
            needsSync = true;
        }

        if (needsSync) {
            data.setDirty();
            PacketHandler.sendToAllClients(level, data);
        }
    }

    // Р РµРєСѓСЂСЃРёРІРЅРѕ РїСЂРѕРІРµСЂСЏРµС‚, РµСЃС‚СЊ Р»Рё РёРіСЂРѕРє СЃСЂРµРґРё РїР°СЃСЃР°Р¶РёСЂРѕРІ (РІ С‚.С‡. РІ СЃРёРґРµРЅСЊСЏС…-Р·Р°РіР»СѓС€РєР°С…)
    private static boolean hasPlayerOccupant(Entity entity) {
        for (Entity passenger : entity.getPassengers()) {
            if (passenger instanceof ServerPlayer) return true;
            if (hasPlayerOccupant(passenger)) return true;
        }
        return false;
    }
    private static void handleLobbyZone(ServerLevel level, AASWorldData data) {
        if (data.lobbyZones.isEmpty() || data.lobbyCenter == null) return;

        BlockPos center = data.lobbyCenter;

        for (ServerPlayer player : level.players()) {
            if (player.isCreative() || player.isSpectator()) continue;

            boolean insideAnyZone = false;
            for (AASWorldData.LobbyZone zone : data.lobbyZones) {
                if (zone.isInside(player.position())) {
                    insideAnyZone = true;
                    break;
                }
            }

            if (!insideAnyZone) {
                player.teleportTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
                player.displayClientMessage(Component.translatable("aas.msg.lobby_zone_return").withStyle(ChatFormatting.YELLOW), true);
            }
        }
    }
    @SubscribeEvent
    public static void onPlayerChangeDimension(net.minecraftforge.event.entity.EntityTravelToDimensionEvent event) {
        // Р В РЎСџР РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р’ВµР РЋР вЂљР РЋР РЏР В Р’ВµР В РЎВ, Р РЋРІР‚РЋР РЋРІР‚С™Р В РЎвЂў Р В РЎвЂ”Р В Р’ВµР РЋР вЂљР В Р’ВµР В РЎВР В Р’ВµР РЋРІР‚В°Р В Р’В°Р В Р’ВµР РЋРІР‚С™Р РЋР С“Р РЋР РЏ Р В РЎвЂР В РЎВР В Р’ВµР В Р вЂ¦Р В Р вЂ¦Р В РЎвЂў Р В РЎвЂР В РЎвЂ“Р РЋР вЂљР В РЎвЂўР В РЎвЂќ Р В РЎвЂ Р РЋР РЉР РЋРІР‚С™Р В РЎвЂў Р В РЎвЂ”Р РЋР вЂљР В РЎвЂўР В РЎвЂР РЋР С“Р РЋРІР‚В¦Р В РЎвЂўР В РўвЂР В РЎвЂР РЋРІР‚С™ Р В Р вЂ¦Р В Р’В° Р РЋР С“Р В Р’ВµР РЋР вЂљР В Р вЂ Р В Р’ВµР РЋР вЂљР В Р’Вµ
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerLevel level = player.serverLevel();
            AASWorldData data = AASWorldData.get(level);

            // Р В Р’ВР РЋР С“Р В РЎвЂ”Р В РЎвЂўР В Р’В»Р РЋР Р‰Р В Р’В·Р РЋРЎвЂњР В Р’ВµР В РЎВ Р РЋРЎвЂњР В Р’В¶Р В Р’Вµ Р В РЎвЂ“Р В РЎвЂўР РЋРІР‚С™Р В РЎвЂўР В Р вЂ Р РЋРЎвЂњР РЋР вЂ№ Р В Р’В»Р В РЎвЂўР В РЎвЂ“Р В РЎвЂР В РЎвЂќР РЋРЎвЂњ Р В Р вЂ Р РЋРІР‚в„–Р РЋРІР‚В¦Р В РЎвЂўР В РўвЂР В Р’В° Р В РЎвЂР В Р’В· Р В РЎвЂўР РЋРІР‚С™Р РЋР вЂљР РЋР РЏР В РўвЂР В Р’В°
            // Р В РЎвЂєР В Р вЂ¦Р В Р’В° Р РЋРЎвЂњР В РўвЂР В Р’В°Р В Р’В»Р В РЎвЂР РЋРІР‚С™ Р В РЎвЂР В РЎвЂ“Р РЋР вЂљР В РЎвЂўР В РЎвЂќР В Р’В° Р В РЎвЂР В Р’В· Р РЋР С“Р В РЎвЂ”Р В РЎвЂР РЋР С“Р В РЎвЂќР В Р’В° Р В РЎвЂўР РЋРІР‚С™Р РЋР вЂљР РЋР РЏР В РўвЂР В Р’В°, Р РЋР С“Р В Р’В±Р РЋР вЂљР В РЎвЂўР РЋР С“Р В РЎвЂР РЋРІР‚С™ Р В Р’ВµР В РЎвЂ“Р В РЎвЂў Р РЋРІР‚С™Р В Р’ВµР В РЎвЂ“Р В РЎвЂ Р В РЎвЂ Р В РЎвЂќР В РЎвЂР РЋРІР‚С™
            PacketSquadAction.leaveCurrentSquad(player, data);

            // Р В Р Р‹Р В РЎвЂР В Р вЂ¦Р РЋРІР‚В¦Р РЋР вЂљР В РЎвЂўР В Р вЂ¦Р В РЎвЂР В Р’В·Р В РЎвЂР РЋР вЂљР РЋРЎвЂњР В Р’ВµР В РЎВ Р В РЎвЂР В Р’В·Р В РЎВР В Р’ВµР В Р вЂ¦Р В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР РЏ Р РЋР С“Р В РЎвЂў Р В Р вЂ Р РЋР С“Р В Р’ВµР В РЎВР В РЎвЂ Р В РЎвЂР В РЎвЂ“Р РЋР вЂљР В РЎвЂўР В РЎвЂќР В Р’В°Р В РЎВР В РЎвЂ, Р РЋРІР‚РЋР РЋРІР‚С™Р В РЎвЂўР В Р’В±Р РЋРІР‚в„– Р В РЎвЂўР В Р’В±Р В Р вЂ¦Р В РЎвЂўР В Р вЂ Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰ GUI
            PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),
                    new PacketSyncSquads(data.squads));

            // Р В РЎвЂєР РЋРІР‚С™Р В РЎвЂ”Р РЋР вЂљР В Р’В°Р В Р вЂ Р В Р’В»Р РЋР РЏР В Р’ВµР В РЎВ Р РЋРЎвЂњР В Р вЂ Р В Р’ВµР В РўвЂР В РЎвЂўР В РЎВР В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂР В Р’Вµ Р В РЎвЂР В РЎвЂ“Р РЋР вЂљР В РЎвЂўР В РЎвЂќР РЋРЎвЂњ
            player.sendSystemMessage(Component.translatable("aas.msg.squad_left_world")
                    .withStyle(ChatFormatting.YELLOW));
        }
    }
    private static String findRequiredPointName(AASWorldData.CapturePoint currentPoint, String team, AASWorldData data) {
        int currentPriority = team.equals("BLUE") ? currentPoint.bluePriority : currentPoint.redPriority;
        int requiredPriority = currentPriority - 1;

        if (requiredPriority < 1) return "";

        for (AASWorldData.CapturePoint p : data.capturePoints) {
            int pPriority = team.equals("BLUE") ? p.bluePriority : p.redPriority;
            if (pPriority == requiredPriority) {
                return p.name;
            }
        }
        return "Unknown";
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!event.getEntity().level().isClientSide) {
            ServerPlayer player = (ServerPlayer) event.getEntity();
            PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                    new PacketSyncServerConfig(AASConfig.AUTO_BALANCE_TEAMS.get(), AASConfig.REVIVE_ITEM.get()));
            ServerLevel level = player.serverLevel();
            AASWorldData data = AASWorldData.get(level);
            sendSyncPacket(level, data);
            PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new PacketSyncSquads(data.squads));
            PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new PacketSyncDownedState(player.getId(), false));

            // РќРѕРІРѕРјСѓ РёРіСЂРѕРєСѓ СЃРѕРѕР±С‰Р°РµРј, РєС‚Рѕ СѓР¶Рµ Р»РµР¶РёС‚ РІ РЅРѕРєР°СѓС‚Рµ
            for (ServerPlayer other : player.server.getPlayerList().getPlayers()) {
                if (other != player && other.getPersistentData().getBoolean("AAS_IsDowned")) {
                    PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                            new PacketSyncDownedState(other.getId(), true));
                }
            }

            if (!data.isGameStarted && player.hasPermissions(2)) {
                player.sendSystemMessage(Component.literal("AAS Game is paused. /aas gamestart true to start.").withStyle(ChatFormatting.YELLOW));
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!event.getEntity().level().isClientSide) {
            ServerPlayer newPlayer = (ServerPlayer) event.getEntity();
            ServerLevel level = newPlayer.serverLevel();
            AASWorldData data = AASWorldData.get(level);
            // Р В РІР‚в„ўР РЋРІР‚в„–Р В РўвЂР В Р’В°Р В Р’ВµР В РЎВ Р В РЎвЂќР В РЎвЂР РЋРІР‚С™, Р В РЎвЂќР В РЎвЂўР РЋРІР‚С™Р В РЎвЂўР РЋР вЂљР РЋРІР‚в„–Р В РІвЂћвЂ“ Р В Р’В±Р РЋРІР‚в„–Р В Р’В» Р В Р вЂ¦Р В Р’В° Р В РЎвЂР В РЎвЂ“Р РЋР вЂљР В РЎвЂўР В РЎвЂќР В Р’Вµ Р В РЎвЂР В Р’В»Р В РЎвЂ Р РЋР С“Р РЋРІР‚С™Р В РЎвЂўР В РЎвЂР РЋРІР‚С™ Р В Р вЂ  Р В РЎвЂўР РЋРІР‚РЋР В Р’ВµР РЋР вЂљР В Р’ВµР В РўвЂР В РЎвЂ (Pending)
            ResupplyHandler.tryApplyPendingKit(newPlayer, data);
            if (newPlayer.gameMode.getGameModeForPlayer() != GameType.CREATIVE) newPlayer.setGameMode(GameType.SURVIVAL);
            newPlayer.connection.send(new ClientboundPlayerAbilitiesPacket(newPlayer.getAbilities()));
            pendingRespawnLocations.remove(newPlayer.getUUID());
            pendingTeams.remove(newPlayer.getUUID());
        }
    }

    @SubscribeEvent
    public static void onEntityDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) return;

        if (event.getEntity() instanceof ServerPlayer player) {
            processEntityLoss(player);

            player.getPersistentData().putBoolean("AAS_IsDowned", false);
            StatsHandler.addDeath(player);

            boolean wasGivingUp = player.getPersistentData().getBoolean("AAS_GivingUp");

            // FIX: Р В Р’ВµР РЋР С“Р В Р’В»Р В РЎвЂ Р РЋР РЉР РЋРІР‚С™Р В РЎвЂў "Р В РўвЂР В РЎвЂўР В Р’В±Р В РЎвЂР РЋРІР‚С™Р В РЎвЂР В Р’Вµ" Р В РЎвЂ”Р В РЎвЂўР РЋР С“Р В Р’В»Р В Р’Вµ Р В Р вЂ¦Р В РЎвЂўР В РЎвЂќР В Р’В° Р Р†Р вЂљРІР‚Сњ Р В РЎвЂќР В РЎвЂР В Р’В»Р В Р’В» Р РЋРЎвЂњР В Р’В¶Р В Р’Вµ Р В Р вЂ¦Р В Р’В°Р РЋРІР‚РЋР В РЎвЂР РЋР С“Р В Р’В»Р В Р’ВµР В Р вЂ¦ Р В Р вЂ  DownedHandler.forceGiveUp().
            // Р В РІР‚вЂќР В РўвЂР В Р’ВµР РЋР С“Р РЋР Р‰ Р В Р вЂ¦Р В Р’В°Р РЋРІР‚РЋР В РЎвЂР РЋР С“Р В Р’В»Р РЋР РЏР В Р’ВµР В РЎВ Р В РЎвЂќР В РЎвЂР В Р’В»Р В Р’В» Р РЋРІР‚С™Р В РЎвЂўР В Р’В»Р РЋР Р‰Р В РЎвЂќР В РЎвЂў Р В РўвЂР В Р’В»Р РЋР РЏ "Р В РЎвЂ”Р РЋР вЂљР РЋР РЏР В РЎВР РЋРІР‚в„–Р РЋРІР‚В¦" Р РЋР С“Р В РЎВР В Р’ВµР РЋР вЂљР РЋРІР‚С™Р В Р’ВµР В РІвЂћвЂ“ (Р В Р вЂ¦Р В Р’В°Р В РЎвЂ”Р РЋР вЂљР В РЎвЂР В РЎВР В Р’ВµР РЋР вЂљ, Р В Р вЂ Р В Р’В·Р РЋР вЂљР РЋРІР‚в„–Р В Р вЂ  Р В Р вЂ Р В РЎвЂў Р В Р вЂ Р РЋР вЂљР В Р’ВµР В РЎВР РЋР РЏ Р В Р вЂ¦Р В РЎвЂўР В РЎвЂќР В Р’В°),
            // Р РЋРІР‚РЋР РЋРІР‚С™Р В РЎвЂўР В Р’В±Р РЋРІР‚в„– Р В Р вЂ¦Р В Р’Вµ Р В Р’В·Р В Р’В°Р РЋР С“Р РЋРІР‚РЋР В РЎвЂР РЋРІР‚С™Р В Р’В°Р РЋРІР‚С™Р РЋР Р‰ Р В РЎвЂўР В РўвЂР В РЎвЂР В Р вЂ¦ Р В РЎвЂќР В РЎвЂР В Р’В»Р В Р’В» Р В РўвЂР В Р вЂ Р В Р’В°Р В Р’В¶Р В РўвЂР РЋРІР‚в„–.
            if (!wasGivingUp && player.getLastHurtByMob() instanceof ServerPlayer killer && killer != player) {
                if (killer.getTeam() != player.getTeam()) {
                    StatsHandler.addStats(killer, 2, 0, "Enemy Killed");
                    StatsHandler.addKill(killer);
                }
            }

            if (wasGivingUp) {
                return; // Р В РІР‚в„ўР РЋРІР‚в„–Р РЋРІР‚В¦Р В РЎвЂўР В РўвЂР В РЎвЂР В РЎВ, Р РЋРІР‚РЋР РЋРІР‚С™Р В РЎвЂўР В Р’В±Р РЋРІР‚в„– Р В Р вЂ¦Р В Р’Вµ Р РЋР С“Р В Р вЂ¦Р В РЎвЂР В РЎВР В Р’В°Р РЋРІР‚С™Р РЋР Р‰ Р РЋРІР‚С™Р В РЎвЂР В РЎвЂќР В Р’ВµР РЋРІР‚С™Р РЋРІР‚в„– Р В Р вЂ Р РЋРІР‚С™Р В РЎвЂўР РЋР вЂљР В РЎвЂўР В РІвЂћвЂ“ Р РЋР вЂљР В Р’В°Р В Р’В·
            }
        }
    }
    private static void saveKitNbtBeforeDeath(ServerPlayer player) {
        AASWorldData data = AASWorldData.get(player.serverLevel());
        String kitName = player.getPersistentData().getString("AAS_CurrentKit");
        String team = (player.getTeam() != null) ? player.getTeam().getName().toUpperCase() : "";

        if (team.isEmpty() || kitName.isEmpty() || kitName.equals("Unassigned")) return;

        boolean isAltVariant = player.getPersistentData().getBoolean("AAS_CurrentKitAlt");
        AASWorldData.KitInfo kit = data.getKitVariant(team, kitName, isAltVariant);
        if (kit == null) return;

        Map<Integer, CompoundTag> savedTags = new HashMap<>();

        for (int i = 0; i < 41; i++) {
            // Р В РЎСџР РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р’ВµР РЋР вЂљР РЋР РЏР В Р’ВµР В РЎВ, Р РЋР С“Р РЋРІР‚С™Р В РЎвЂўР В РЎвЂР РЋРІР‚С™ Р В Р’В»Р В РЎвЂ Р РЋРІР‚С›Р В Р’В»Р В Р’В°Р В РЎвЂ“ Р РЋР С“Р В РЎвЂўР РЋРІР‚В¦Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР РЏ NBT Р В РўвЂР В Р’В»Р РЋР РЏ Р РЋР РЉР РЋРІР‚С™Р В РЎвЂўР В РЎвЂ“Р В РЎвЂў Р РЋР С“Р В Р’В»Р В РЎвЂўР РЋРІР‚С™Р В Р’В°
            if (i < kit.saveNbtFlags.length && kit.saveNbtFlags[i]) {
                ItemStack item = player.getInventory().getItem(i);
                if (!item.isEmpty() && item.hasTag()) {
                    // Р В Р Р‹Р В РЎвЂўР РЋРІР‚В¦Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р РЋР РЏР В Р’ВµР В РЎВ Р В РЎвЂќР В РЎвЂўР В РЎвЂ”Р В РЎвЂР РЋР вЂ№ NBT (Р В Р’В·Р В Р’В°Р РЋРІР‚РЋР В Р’В°Р РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р’В°Р В Р вЂ¦Р В РЎвЂР РЋР РЏ, Р В РЎвЂ”Р В Р’В°Р РЋРІР‚С™Р РЋР вЂљР В РЎвЂўР В Р вЂ¦Р РЋРІР‚в„– Р В РЎвЂ Р РЋРІР‚С™.Р В РўвЂ.)
                    savedTags.put(i, item.getTag().copy());
                }
            }
        }

        if (!savedTags.isEmpty()) {
            PERSISTENT_NBT_STORAGE.put(player.getUUID(), savedTags);
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        ServerPlayer oldPlayer = (ServerPlayer) event.getOriginal();
        ServerPlayer newPlayer = (ServerPlayer) event.getEntity();
        // Р В РЎвЂєР РЋРІР‚РЋР В РЎвЂР РЋРІР‚В°Р В Р’В°Р В Р’ВµР В РЎВ Р В Р вЂ Р РЋР С“Р В Р’Вµ Р РЋРІР‚С›Р В Р’В»Р В Р’В°Р В РЎвЂ“Р В РЎвЂ Р В Р вЂ¦Р В РЎвЂўР В РЎвЂќР В Р’В° Р В РЎвЂ”Р РЋР вЂљР В РЎвЂ Р В РЎвЂ”Р В Р’ВµР РЋР вЂљР В Р’ВµР РЋР вЂљР В РЎвЂўР В Р’В¶Р В РўвЂР В Р’ВµР В Р вЂ¦Р В РЎвЂР В РЎвЂ
        newPlayer.getPersistentData().putBoolean("AAS_IsDowned", false);
        newPlayer.getPersistentData().remove("AAS_GivingUp");
        newPlayer.getPersistentData().remove("AAS_DownedYaw");
        newPlayer.getPersistentData().remove("AAS_DownedPitch");
        newPlayer.getPersistentData().putLong("AAS_LastReviveTime", 0);

        // Р В Р Р‹Р В РЎвЂР В Р вЂ¦Р РЋРІР‚В¦Р РЋР вЂљР В РЎвЂўР В Р вЂ¦Р В РЎвЂР В Р’В·Р В РЎвЂР РЋР вЂљР РЋРЎвЂњР В Р’ВµР В РЎВ Р РЋР С“Р В РЎвЂўР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР РЋР РЏР В Р вЂ¦Р В РЎвЂР В Р’Вµ "Р В Р вЂ¦Р В Р’Вµ Р В Р вЂ  Р В Р вЂ¦Р В РЎвЂўР В РЎвЂќР В Р’Вµ" Р РЋР С“Р В РЎвЂў Р В Р вЂ Р РЋР С“Р В Р’ВµР В РЎВР В РЎвЂ
        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new PacketSyncDownedState(newPlayer.getId(), false));
        // Р В РЎСџР В Р’ВµР РЋР вЂљР В Р’ВµР В Р вЂ¦Р В РЎвЂўР РЋР С“Р В РЎвЂР В РЎВ Р В РўвЂР В Р’В°Р В Р вЂ¦Р В Р вЂ¦Р РЋРІР‚в„–Р В Р’Вµ Р В РЎвЂў NBT Р В РЎвЂР В Р’В· Р РЋРІР‚В¦Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В РЎвЂР В Р’В»Р В РЎвЂР РЋРІР‚В°Р В Р’В° Р В Р вЂ¦Р В Р’В° Р В Р вЂ¦Р В РЎвЂўР В Р вЂ Р РЋРІР‚в„–Р В РІвЂћвЂ“ UUID (Р В Р’ВµР РЋР С“Р В Р’В»Р В РЎвЂ UUID Р В Р вЂ Р В РўвЂР РЋР вЂљР РЋРЎвЂњР В РЎвЂ“ Р В РЎВР В Р’ВµР В Р вЂ¦Р РЋР РЏР В Р’ВµР РЋРІР‚С™Р РЋР С“Р РЋР РЏ, Р В Р вЂ¦Р В РЎвЂў Р В РЎвЂўР В Р’В±Р РЋРІР‚в„–Р РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂў Р В РЎвЂўР В Р вЂ¦ Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚С™ Р В Р’В¶Р В Р’Вµ)
        // Р В РЎСљР В РЎвЂў Р РЋРІР‚С™Р В Р’В°Р В РЎвЂќ Р В РЎвЂќР В Р’В°Р В РЎвЂќ Р В РЎВР РЋРІР‚в„– Р В РЎвЂР РЋР С“Р В РЎвЂ”Р В РЎвЂўР В Р’В»Р РЋР Р‰Р В Р’В·Р РЋРЎвЂњР В Р’ВµР В РЎВ Р РЋР С“Р РЋРІР‚С™Р В Р’В°Р РЋРІР‚С™Р В РЎвЂР РЋРІР‚РЋР В Р’ВµР РЋР С“Р В РЎвЂќР В РЎвЂР В РІвЂћвЂ“ Map Р В РЎвЂ UUID, Р В РўвЂР В Р’В°Р В Р вЂ¦Р В Р вЂ¦Р РЋРІР‚в„–Р В Р’Вµ Р РЋР С“Р В РЎвЂўР РЋРІР‚В¦Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р РЋР РЏР РЋРІР‚С™Р РЋР С“Р РЋР РЏ Р В Р вЂ  PERSISTENT_NBT_STORAGE Р В Р’В°Р В Р вЂ Р РЋРІР‚С™Р В РЎвЂўР В РЎВР В Р’В°Р РЋРІР‚С™Р В РЎвЂР РЋРІР‚РЋР В Р’ВµР РЋР С“Р В РЎвЂќР В РЎвЂ.

        // 1. Р В РЎв„ўР В РЎвЂєР В РЎСџР В Р’ВР В Р’В Р В РЎвЂєР В РІР‚в„ўР В РЎвЂ™Р В РЎСљР В Р’ВР В РІР‚Сћ Р В РЎСџР В РІР‚СћР В Р’В Р В Р Р‹Р В Р’ВР В Р Р‹Р В РЎС›Р В РІР‚СћР В РЎСљР В РЎС›Р В РЎСљР В Р’В«Р В РўС’ Р В РІР‚СњР В РЎвЂ™Р В РЎСљР В РЎСљР В Р’В«Р В РўС’ (Р В РІР‚в„ўР В Р’В°Р РЋРІвЂљВ¬ Р РЋР С“Р РЋРЎвЂњР РЋРІР‚В°Р В Р’ВµР РЋР С“Р РЋРІР‚С™Р В Р вЂ Р РЋРЎвЂњР РЋР вЂ№Р РЋРІР‚В°Р В РЎвЂР В РІвЂћвЂ“ Р В РЎвЂќР В РЎвЂўР В РўвЂ)
        CompoundTag oldData = oldPlayer.getPersistentData();
        CompoundTag newData = newPlayer.getPersistentData();

        if (oldData.contains("AAS_Stats_TeamPoints")) newData.putInt("AAS_Stats_TeamPoints", oldData.getInt("AAS_Stats_TeamPoints"));
        if (oldData.contains("AAS_Stats_SquadPoints")) newData.putInt("AAS_Stats_SquadPoints", oldData.getInt("AAS_Stats_SquadPoints"));
        if (oldData.contains("AAS_Stats_Kills")) newData.putInt("AAS_Stats_Kills", oldData.getInt("AAS_Stats_Kills"));
        if (oldData.contains("AAS_Stats_Deaths")) newData.putInt("AAS_Stats_Deaths", oldData.getInt("AAS_Stats_Deaths"));

        if (oldData.contains("AAS_SquadID")) newData.putInt("AAS_SquadID", oldData.getInt("AAS_SquadID"));
        if (oldData.contains("AAS_IsSquadLeader")) newData.putBoolean("AAS_IsSquadLeader", oldData.getBoolean("AAS_IsSquadLeader"));
        if (oldData.contains("AAS_CurrentKit")) newData.putString("AAS_CurrentKit", oldData.getString("AAS_CurrentKit"));
        if (oldData.contains("AAS_CurrentKitAlt")) newData.putBoolean("AAS_CurrentKitAlt", oldData.getBoolean("AAS_CurrentKitAlt"));
        if (oldData.contains("AAS_PendingKit")) newData.putString("AAS_PendingKit", oldData.getString("AAS_PendingKit"));
        if (oldData.contains("AAS_PendingKitAlt")) newData.putBoolean("AAS_PendingKitAlt", oldData.getBoolean("AAS_PendingKitAlt"));
        if (oldData.contains("AAS_LastFobResupply")) newData.putLong("AAS_LastFobResupply", oldData.getLong("AAS_LastFobResupply"));
        if (oldData.contains("AAS_LastMainResupply")) newData.putLong("AAS_LastMainResupply", oldData.getLong("AAS_LastMainResupply"));

        // 2. Р В РІР‚в„ўР В РЎвЂєР В Р Р‹Р В Р Р‹Р В РЎС›Р В РЎвЂ™Р В РЎСљР В РЎвЂєР В РІР‚в„ўР В РІР‚С”Р В РІР‚СћР В РЎСљР В Р’ВР В РІР‚Сћ Р В РЎв„ўР В РЎвЂєР В РЎС™Р В РЎвЂ™Р В РЎСљР В РІР‚СњР В Р’В«
        if (event.isWasDeath()) {
            String teamName = (oldPlayer.getTeam() != null) ? oldPlayer.getTeam().getName() : null;
            if (teamName != null) {
                Scoreboard scoreboard = newPlayer.getScoreboard();
                PlayerTeam pTeam = scoreboard.getPlayerTeam(teamName);
                if (pTeam != null) scoreboard.addPlayerToTeam(newPlayer.getScoreboardName(), pTeam);
            }
        }
    }

    @SubscribeEvent
    public static void onEntityRemove(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide) return;
        Entity entity = event.getEntity();
        if (entity instanceof LivingEntity) return;
        Entity.RemovalReason reason = entity.getRemovalReason();
        if (reason != null && (reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED)) {
            processEntityLoss(entity);
        }
    }

    private static void processEntityLoss(Entity entity) {
        if (entity.level() == null || entity.level().getServer() == null) return;
        ServerLevel level = (ServerLevel) entity.level();
        AASWorldData data = AASWorldData.get(level);

        // === 1. Р В РЎС™Р В РІР‚СљР В РЎСљР В РЎвЂєР В РІР‚в„ўР В РІР‚СћР В РЎСљР В РЎСљР В РЎвЂєР В РІР‚Сћ Р В Р в‚¬Р В РІР‚СњР В РЎвЂ™Р В РІР‚С”Р В РІР‚СћР В РЎСљР В Р’ВР В РІР‚Сћ Р В Р’ВР В РЎв„ўР В РЎвЂєР В РЎСљР В РЎв„ўР В Р’В Р В Р Р‹ Р В РЎв„ўР В РЎвЂ™Р В Р’В Р В РЎС›Р В Р’В« ===
        // Р В РІР‚СћР РЋР С“Р В Р’В»Р В РЎвЂ UUID Р РЋР РЉР РЋРІР‚С™Р В РЎвЂўР В РІвЂћвЂ“ Р РЋР С“Р РЋРЎвЂњР РЋРІР‚В°Р В Р вЂ¦Р В РЎвЂўР РЋР С“Р РЋРІР‚С™Р В РЎвЂ Р В Р’ВµР РЋР С“Р РЋРІР‚С™Р РЋР Р‰ Р В Р вЂ  Р РЋР С“Р В РЎвЂ”Р В РЎвЂР РЋР С“Р В РЎвЂќР В Р’Вµ Р В РЎвЂ”Р В РЎвЂўР В РЎВР В Р’ВµР РЋРІР‚РЋР В Р’ВµР В Р вЂ¦Р В Р вЂ¦Р РЋРІР‚в„–Р РЋРІР‚В¦ Р Р†Р вЂљРІР‚Сњ Р РЋРЎвЂњР В РўвЂР В Р’В°Р В Р’В»Р РЋР РЏР В Р’ВµР В РЎВ Р В Р’В·Р В Р’В°Р В РЎвЂ”Р В РЎвЂР РЋР С“Р РЋР Р‰
        boolean markerRemoved = data.markedVehicles.removeIf(v -> v.uuid.equals(entity.getUUID()));

        // Р В РІР‚СћР РЋР С“Р В Р’В»Р В РЎвЂ Р В Р’В±Р В РЎвЂР В Р’В»Р В Р’ВµР РЋРІР‚С™Р В РЎвЂўР В Р вЂ  Р РЋРЎвЂњР В Р’В¶Р В Р’Вµ 0, Р В РЎВР РЋРІР‚в„– Р В Р вЂ Р РЋР С“Р РЋРІР‚В Р РЋР вЂљР В Р’В°Р В Р вЂ Р В Р вЂ¦Р В РЎвЂў Р В РўвЂР В РЎвЂўР В Р’В»Р В Р’В¶Р В Р вЂ¦Р РЋРІР‚в„– Р РЋР С“Р В РЎвЂР В Р вЂ¦Р РЋРІР‚В¦Р РЋР вЂљР В РЎвЂўР В Р вЂ¦Р В РЎвЂР В Р’В·Р В РЎвЂР РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р’В°Р РЋРІР‚С™Р РЋР Р‰ Р РЋРЎвЂњР В РўвЂР В Р’В°Р В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂР В Р’Вµ Р В РЎВР В Р’В°Р РЋР вЂљР В РЎвЂќР В Р’ВµР РЋР вЂљР В Р’В°
        if (data.blueTickets <= 0 || data.redTickets <= 0) {
            if (markerRemoved) {
                data.setDirty();
                sendSyncPacket(level, data);
            }
            return;
        }

        boolean ticketsChanged = false;

        // === 2. Р В РІР‚С”Р В РЎвЂєР В РІР‚СљР В Р’ВР В РЎв„ўР В РЎвЂ™ Р В Р Р‹Р В РЎС™Р В РІР‚СћР В Р’В Р В РЎС›Р В Р’В Р В Р’ВР В РІР‚СљР В Р’В Р В РЎвЂєР В РЎв„ўР В РЎвЂ™ (Р В Р РѓР В РЎС›Р В Р’В Р В РЎвЂ™Р В Р’В¤Р В Р’В«) ===
        if (entity instanceof ServerPlayer player) {
            if (player.getTeam() != null) {
                String teamName = player.getTeam().getName();
                int cost = data.deathTicketCost;
                if (teamName.equalsIgnoreCase("Blue")) {
                    data.blueTickets = Math.max(0, data.blueTickets - cost);
                    ticketsChanged = true;
                }
                else if (teamName.equalsIgnoreCase("Red")) {
                    data.redTickets = Math.max(0, data.redTickets - cost);
                    ticketsChanged = true;
                }
            }
        }

        // === 3. Р В РІР‚С”Р В РЎвЂєР В РІР‚СљР В Р’ВР В РЎв„ўР В РЎвЂ™ Р В РЎСџР В РЎвЂєР В РЎС›Р В РІР‚СћР В Р’В Р В Р’В Р В РЎС›Р В РІР‚СћР В РўС’Р В РЎСљР В Р’ВР В РЎв„ўР В Р’В (Р В Р РѓР В РЎС›Р В Р’В Р В РЎвЂ™Р В Р’В¤Р В Р’В«) ===
        if (entity.getPersistentData().contains("AAS_TicketPenalty")) {
            int penalty = entity.getPersistentData().getInt("AAS_TicketPenalty");
            String vTeam = entity.getPersistentData().getString("AAS_VehicleTeam");
            String vType = entity.getPersistentData().getString("AAS_VehicleType");

            if (penalty > 0) {
                if (vTeam.equalsIgnoreCase("BLUE")) {
                    data.blueTickets = Math.max(0, data.blueTickets - penalty);
                    broadcastVehicleLossMessage(level, "BLUE", "BLUE lost " + vType + " (-" + penalty + ")", ChatFormatting.BLUE);
                    ticketsChanged = true;
                } else if (vTeam.equalsIgnoreCase("RED")) {
                    data.redTickets = Math.max(0, data.redTickets - penalty);
                    broadcastVehicleLossMessage(level, "RED", "RED lost " + vType + " (-" + penalty + ")", ChatFormatting.RED);
                    ticketsChanged = true;
                }

                // Р В РЎСљР В Р’В°Р РЋРІР‚РЋР В РЎвЂР РЋР С“Р В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂР В Р’Вµ Р В РЎвЂўР РЋРІР‚РЋР В РЎвЂќР В РЎвЂўР В Р вЂ  Р РЋРЎвЂњР В Р’В±Р В РЎвЂР В РІвЂћвЂ“Р РЋРІР‚В Р В Р’Вµ
                Entity lastAttacker = null;
                if (entity instanceof LivingEntity living) {
                    lastAttacker = living.getLastHurtByMob();
                }

                if (lastAttacker instanceof ServerPlayer sKiller) {
                    String killerTeam = sKiller.getTeam() != null ? sKiller.getTeam().getName().toUpperCase() : "NEUTRAL";
                    if (!vTeam.equalsIgnoreCase(killerTeam)) {
                        StatsHandler.addStats(sKiller, penalty, 0, "Enemy Vehicle Destroyed");
                    }
                }
            }
            entity.getPersistentData().remove("AAS_TicketPenalty");
        }

        // === 4. Р В Р Р‹Р В Р’ВР В РЎСљР В РўС’Р В Р’В Р В РЎвЂєР В РЎСљР В Р’ВР В РІР‚вЂќР В РЎвЂ™Р В Р’В¦Р В Р’ВР В Р вЂЎ ===
        // Р В РІР‚СћР РЋР С“Р В Р’В»Р В РЎвЂ Р В РЎвЂР В Р’В·Р В РЎВР В Р’ВµР В Р вЂ¦Р В РЎвЂР В Р’В»Р В РЎвЂР РЋР С“Р РЋР Р‰ Р РЋРІР‚С™Р В РЎвЂР В РЎвЂќР В Р’ВµР РЋРІР‚С™Р РЋРІР‚в„– Р В Р’ВР В РІР‚С”Р В Р’В Р В Р’В±Р РЋРІР‚в„–Р В Р’В» Р РЋРЎвЂњР В РўвЂР В Р’В°Р В Р’В»Р В Р’ВµР В Р вЂ¦ Р В РЎВР В Р’В°Р РЋР вЂљР В РЎвЂќР В Р’ВµР РЋР вЂљ Р РЋРІР‚С™Р В Р’ВµР РЋРІР‚В¦Р В Р вЂ¦Р В РЎвЂР В РЎвЂќР В РЎвЂ
        if (ticketsChanged || markerRemoved) {
            if (ticketsChanged) checkGameOver(level, data);
            data.setDirty();
            sendSyncPacket(level, data); // Р В РЎвЂєР РЋРІР‚С™Р В РЎвЂ”Р РЋР вЂљР В Р’В°Р В Р вЂ Р В Р’В»Р РЋР РЏР В Р’ВµР В РЎВ Р В РЎвЂўР В Р’В±Р В Р вЂ¦Р В РЎвЂўР В Р вЂ Р В Р’В»Р В Р’ВµР В Р вЂ¦Р В Р вЂ¦Р РЋРІР‚в„–Р В Р’Вµ Р В РўвЂР В Р’В°Р В Р вЂ¦Р В Р вЂ¦Р РЋРІР‚в„–Р В Р’Вµ Р В Р вЂ Р РЋР С“Р В Р’ВµР В РЎВ Р В РЎвЂР В РЎвЂ“Р РЋР вЂљР В РЎвЂўР В РЎвЂќР В Р’В°Р В РЎВ
        }
    }

    private static void handleTeamInfluence(AASWorldData.CapturePoint point, String attackingTeam, float multiplier, float baseSpeed, AASWorldData data, ServerLevel level) {
        float speedBoosted = baseSpeed * multiplier;

        if (point.owner.equals("NEUTRAL")) {
            // --- Р В РІР‚С”Р В РЎвЂєР В РІР‚СљР В Р’ВР В РЎв„ўР В РЎвЂ™ Р В РІР‚вЂќР В РЎвЂ™Р В РўС’Р В РІР‚в„ўР В РЎвЂ™Р В РЎС›Р В РЎвЂ™ Р В РЎСљР В РІР‚СћР В РІвЂћСћР В РЎС›Р В Р’В Р В РЎвЂ™Р В РІР‚С”Р В Р’В¬Р В РЎСљР В РЎвЂєР В РІвЂћСћ Р В РЎС›Р В РЎвЂєР В Р’В§Р В РЎв„ўР В Р’В ---
            if (point.capturingTeam.equals("NONE") || point.capturingTeam.equals(attackingTeam)) {
                point.capturingTeam = attackingTeam;
                point.progress += speedBoosted;

                if (point.progress >= 1.0f) {
                    point.progress = 1.0f;
                    point.owner = attackingTeam;

                    List<ServerPlayer> cappers = level.getEntitiesOfClass(ServerPlayer.class, point.getBoundingBox());
                    for (ServerPlayer p : cappers) {
                        if (p.isAlive() && !p.isSpectator() && p.getTeam() != null && p.getTeam().getName().equalsIgnoreCase(attackingTeam)) {
                            StatsHandler.addStats(p, 50, 10, "Point Captured");
                        }
                    }

                    // Р В РІР‚СљР РЋР вЂљР В Р’В°Р РЋРІР‚С›Р В РЎвЂР РЋРІР‚РЋР В Р’ВµР РЋР С“Р В РЎвЂќР В РЎвЂўР В Р’Вµ Р РЋРЎвЂњР В Р вЂ Р В Р’ВµР В РўвЂР В РЎвЂўР В РЎВР В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂР В Р’Вµ Р В РЎвЂў Р В Р’В·Р В Р’В°Р РЋРІР‚В¦Р В Р вЂ Р В Р’В°Р РЋРІР‚С™Р В Р’Вµ
                    PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),
                            new PacketCaptureNotification(point.name, attackingTeam, false));

                    // Р В РЎСљР В Р’В°Р РЋРІР‚РЋР В РЎвЂР РЋР С“Р В Р’В»Р РЋР РЏР В Р’ВµР В РЎВ Р РЋРІР‚С™Р В РЎвЂР В РЎвЂќР В Р’ВµР РЋРІР‚С™Р РЋРІР‚в„– Р В Р’В°Р РЋРІР‚С™Р В Р’В°Р В РЎвЂќР РЋРЎвЂњР РЋР вЂ№Р РЋРІР‚В°Р В РЎвЂР В РЎВ (Р В Р’ВµР РЋР С“Р В Р’В»Р В РЎвЂ Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р В Р вЂ¦Р В РЎвЂў Р В Р вЂ  Р В РЎвЂќР В РЎвЂўР В Р вЂ¦Р РЋРІР‚С›Р В РЎвЂР В РЎвЂ“Р В Р’Вµ Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В РЎвЂќР В РЎвЂ)
                    if (point.ticketGainCapture > 0) {
                        if (attackingTeam.equals("BLUE")) data.blueTickets += point.ticketGainCapture;
                        else data.redTickets += point.ticketGainCapture;
                    }

                    // Р В Р РѓР РЋРІР‚С™Р РЋР вЂљР В Р’В°Р РЋРІР‚С› Р В Р вЂ Р РЋР вЂљР В Р’В°Р В РЎвЂ“Р РЋРЎвЂњ Р В Р’В·Р В Р’В° Р В РЎвЂ”Р В РЎвЂўР РЋРІР‚С™Р В Р’ВµР РЋР вЂљР РЋР вЂ№ Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В РЎвЂќР В РЎвЂ (Р РЋРЎвЂњР В Р вЂ¦Р В РЎвЂР В Р вЂ Р В Р’ВµР РЋР вЂљР РЋР С“Р В Р’В°Р В Р’В»Р РЋР Р‰Р В Р вЂ¦Р В РЎвЂў Р В РўвЂР В Р’В»Р РЋР РЏ Р В Р вЂ Р РЋР С“Р В Р’ВµР РЋРІР‚В¦ Р РЋР вЂљР В Р’ВµР В Р’В¶Р В РЎвЂР В РЎВР В РЎвЂўР В Р вЂ )
                    if (point.captureDeduction > 0) {
                        if (attackingTeam.equals("BLUE")) data.redTickets -= point.captureDeduction;
                        else data.blueTickets -= point.captureDeduction;
                    }

                    checkGameOver(level, data);
                    data.setDirty();
                    sendSyncPacket(level, data);
                }
            } else {
                // Р В Р Р‹Р В Р’В±Р В РЎвЂР РЋРІР‚С™Р В РЎвЂР В Р’Вµ Р В РЎвЂ”Р РЋР вЂљР В РЎвЂўР В РЎвЂ“Р РЋР вЂљР В Р’ВµР РЋР С“Р РЋР С“Р В Р’В° Р В РўвЂР РЋР вЂљР РЋРЎвЂњР В РЎвЂ“Р В РЎвЂўР В РІвЂћвЂ“ Р В РЎвЂќР В РЎвЂўР В РЎВР В Р’В°Р В Р вЂ¦Р В РўвЂР РЋРІР‚в„–
                point.progress -= speedBoosted;
                if (point.progress <= 0.0f) {
                    point.progress = 0.0f;
                    point.capturingTeam = "NONE";
                }
            }
        } else if (point.owner.equals(attackingTeam)) {
            // Р В Р Р‹Р В Р вЂ Р В РЎвЂўР РЋР РЏ Р В РЎвЂќР В РЎвЂўР В РЎВР В Р’В°Р В Р вЂ¦Р В РўвЂР В Р’В° "Р В РўвЂР В РЎвЂўР В Р’В»Р В Р’ВµР РЋРІР‚РЋР В РЎвЂР В Р вЂ Р В Р’В°Р В Р’ВµР РЋРІР‚С™" Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В РЎвЂќР РЋРЎвЂњ Р В РўвЂР В РЎвЂў 100%
            if (point.progress < 1.0f) {
                point.progress += speedBoosted;
                if (point.progress > 1.0f) point.progress = 1.0f;
            }
        } else {
            // --- Р В РІР‚С”Р В РЎвЂєР В РІР‚СљР В Р’ВР В РЎв„ўР В РЎвЂ™ Р В РЎСљР В РІР‚СћР В РІвЂћСћР В РЎС›Р В Р’В Р В РЎвЂ™Р В РІР‚С”Р В Р’ВР В РІР‚вЂќР В РЎвЂ™Р В Р’В¦Р В Р’ВР В Р’В Р В РІР‚в„ўР В Р’В Р В РЎвЂ™Р В РІР‚вЂњР В РІР‚СћР В Р Р‹Р В РЎв„ўР В РЎвЂєР В РІвЂћСћ Р В РЎС›Р В РЎвЂєР В Р’В§Р В РЎв„ўР В Р’В ---
            point.progress -= speedBoosted;
            if (point.progress <= 0.0f) {
                String oldOwner = point.owner;

                // Р В Р РѓР РЋРІР‚С™Р РЋР вЂљР В Р’В°Р РЋРІР‚С› Р В Р’В·Р В Р’В° Р В РЎвЂ”Р В РЎвЂўР РЋРІР‚С™Р В Р’ВµР РЋР вЂљР РЋР вЂ№ Р В Р вЂ¦Р В Р’ВµР В РІвЂћвЂ“Р РЋРІР‚С™Р РЋР вЂљР В Р’В°Р В Р’В»Р В РЎвЂР РЋРІР‚С™Р В Р’ВµР РЋРІР‚С™Р В Р’В° (Р РЋР С“Р РЋРІР‚С™Р В Р’В°Р РЋР вЂљР РЋРІР‚в„–Р В РІвЂћвЂ“ Р В Р вЂ Р В Р’В»Р В Р’В°Р В РўвЂР В Р’ВµР В Р’В»Р В Р’ВµР РЋРІР‚В  Р РЋРІР‚С™Р В Р’ВµР РЋР вЂљР РЋР РЏР В Р’ВµР РЋРІР‚С™ Р РЋРІР‚С™Р В РЎвЂР В РЎвЂќР В Р’ВµР РЋРІР‚С™Р РЋРІР‚в„–)
                if (oldOwner.equals("BLUE")) data.blueTickets -= point.ticketPenalty;
                else if (oldOwner.equals("RED")) data.redTickets -= point.ticketPenalty;

                // Р В РІР‚ВР В РЎвЂўР В Р вЂ¦Р РЋРЎвЂњР РЋР С“ Р В Р’В°Р РЋРІР‚С™Р В Р’В°Р В РЎвЂќР РЋРЎвЂњР РЋР вЂ№Р РЋРІР‚В°Р В РЎвЂР В РЎВ Р В Р’В·Р В Р’В° Р В Р вЂ¦Р В Р’ВµР В РІвЂћвЂ“Р РЋРІР‚С™Р РЋР вЂљР В Р’В°Р В Р’В»Р В РЎвЂР В Р’В·Р В Р’В°Р РЋРІР‚В Р В РЎвЂР РЋР вЂ№ (Р В Р’ВµР РЋР С“Р В Р’В»Р В РЎвЂ Р В Р’В·Р В Р’В°Р В РўвЂР В Р’В°Р В Р вЂ¦Р В РЎвЂў)
                if (point.ticketGainNeutralize > 0) {
                    if (attackingTeam.equals("BLUE")) data.blueTickets += point.ticketGainNeutralize;
                    else data.redTickets += point.ticketGainNeutralize;
                }

                checkGameOver(level, data);

                point.owner = "NEUTRAL";
                point.progress = 0.0f;
                point.capturingTeam = "NONE";

                // Р В РІР‚СљР РЋР вЂљР В Р’В°Р РЋРІР‚С›Р В РЎвЂР РЋРІР‚РЋР В Р’ВµР РЋР С“Р В РЎвЂќР В РЎвЂўР В Р’Вµ Р РЋРЎвЂњР В Р вЂ Р В Р’ВµР В РўвЂР В РЎвЂўР В РЎВР В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂР В Р’Вµ Р В РЎвЂў Р В Р вЂ¦Р В Р’ВµР В РІвЂћвЂ“Р РЋРІР‚С™Р РЋР вЂљР В Р’В°Р В Р’В»Р В РЎвЂР В Р’В·Р В Р’В°Р РЋРІР‚В Р В РЎвЂР В РЎвЂ
                PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),
                        new PacketCaptureNotification(point.name, attackingTeam, true));

                data.setDirty();
                sendSyncPacket(level, data);
            }
        }
    }
    @SubscribeEvent
    public static void onItemPickup(net.minecraftforge.event.entity.player.EntityItemPickupEvent event) {
        if (event.getEntity().level().isClientSide) return;

        Player player = event.getEntity();
        if (player.isCreative()) return;

        // РќРћР’РћР•: Р±РµР· РєРёС‚Р° Rifleman СЃСѓРјРєСѓ СЃ Р·РµРјР»Рё РїРѕРґРѕР±СЂР°С‚СЊ РЅРµР»СЊР·СЏ
        if (event.getItem().getItem().getItem() == ModItems.AMMO_BAG.get()
                && !com.example.aas.block.AmmoBagBlock.canPickupByKit(player)) {
            event.setCanceled(true);
            if (player.tickCount % 20 == 0) {
                player.displayClientMessage(Component.translatable("aas.msg.rifleman_only_bag").withStyle(ChatFormatting.RED), true);
            }
            return;
        }

        // Р В РІР‚СћР РЋР С“Р В Р’В»Р В РЎвЂ Р РЋР РЉР РЋРІР‚С™Р В РЎвЂў Р РЋР С“Р РЋРЎвЂњР В РЎВР В РЎвЂќР В Р’В° Р РЋР С“ Р В РЎвЂ”Р В Р’В°Р РЋРІР‚С™Р РЋР вЂљР В РЎвЂўР В Р вЂ¦Р В Р’В°Р В РЎВР В РЎвЂ Р В РЎвЂ Р В Р вЂ Р В РЎвЂќР В Р’В»Р РЋР вЂ№Р РЋРІР‚РЋР В Р’ВµР В Р вЂ¦ Р В Р’В»Р В РЎвЂР В РЎВР В РЎвЂР РЋРІР‚С™ Р В Р вЂ  Р В РЎвЂќР В РЎвЂўР В Р вЂ¦Р РЋРІР‚С›Р В РЎвЂР В РЎвЂ“Р В Р’Вµ
        if (AASConfig.ONE_AMMO_BAG_PER_PLAYER.get()) {
            if (event.getItem().getItem().getItem() == ModItems.AMMO_BAG.get()) {
                // Р В РЎСџР РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р’ВµР РЋР вЂљР РЋР РЏР В Р’ВµР В РЎВ Р В РЎвЂР В Р вЂ¦Р В Р вЂ Р В Р’ВµР В Р вЂ¦Р РЋРІР‚С™Р В Р’В°Р РЋР вЂљР РЋР Р‰ Р В РЎвЂР В РЎвЂ“Р РЋР вЂљР В РЎвЂўР В РЎвЂќР В Р’В°
                boolean hasBag = false;
                for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                    if (player.getInventory().getItem(i).getItem() == ModItems.AMMO_BAG.get()) {
                        hasBag = true;
                        break;
                    }
                }

                // Р В РІР‚СћР РЋР С“Р В Р’В»Р В РЎвЂ Р РЋР С“Р РЋРЎвЂњР В РЎВР В РЎвЂќР В Р’В° Р РЋРЎвЂњР В Р’В¶Р В Р’Вµ Р В Р’ВµР РЋР С“Р РЋРІР‚С™Р РЋР Р‰, Р В Р’В·Р В Р’В°Р В РЎвЂ”Р РЋР вЂљР В Р’ВµР РЋРІР‚В°Р В Р’В°Р В Р’ВµР В РЎВ Р В РЎвЂ”Р В РЎвЂўР В РўвЂР В Р’В±Р В РЎвЂР РЋР вЂљР В Р’В°Р РЋРІР‚С™Р РЋР Р‰ Р В РЎвЂ”Р РЋР вЂљР В Р’ВµР В РўвЂР В РЎВР В Р’ВµР РЋРІР‚С™ Р РЋР С“ Р В Р’В·Р В Р’ВµР В РЎВР В Р’В»Р В РЎвЂ
                if (hasBag) {
                    event.setCanceled(true);
                    // Р В РІР‚в„ўР РЋРІР‚в„–Р В РўвЂР В Р’В°Р В Р’ВµР В РЎВ Р В РЎвЂ”Р РЋР вЂљР В Р’ВµР В РўвЂР РЋРЎвЂњР В РЎвЂ”Р РЋР вЂљР В Р’ВµР В Р’В¶Р В РўвЂР В Р’ВµР В Р вЂ¦Р В РЎвЂР В Р’Вµ (Р В Р вЂ¦Р В Р’Вµ Р РЋР С“Р В РЎвЂ”Р В Р’В°Р В РЎВР В РЎвЂР В РЎВ Р РЋР С“Р В Р’В»Р В РЎвЂР РЋРІвЂљВ¬Р В РЎвЂќР В РЎвЂўР В РЎВ Р РЋРІР‚РЋР В Р’В°Р РЋР С“Р РЋРІР‚С™Р В РЎвЂў)
                    if (player.tickCount % 20 == 0) {
                        player.displayClientMessage(Component.translatable("aas.msg.one_bag_limit").withStyle(ChatFormatting.RED), true);
                    }
                }
            }
        }
    }
    // === Р В РЎвЂєР В РІР‚ВР В РЎСљР В РЎвЂєР В РІР‚в„ўР В РІР‚С”Р В РІР‚СћР В РЎСљР В РЎСљР В Р’В«Р В РІвЂћСћ Р В РЎС™Р В РІР‚СћР В РЎС›Р В РЎвЂєР В РІР‚Сњ: Р В РЎСџР РЋР вЂљР В РЎвЂР В Р вЂ¦Р В РЎвЂР В РЎВР В Р’В°Р В Р’ВµР РЋРІР‚С™ Р РЋР С“Р РЋРІР‚С™Р В Р’В°Р РЋРІР‚С™Р РЋРЎвЂњР РЋР С“ Р В РІР‚ВР В Р’В»Р В РЎвЂР РЋРІР‚С™Р РЋРІР‚В Р В Р’В° ===
    private static void validateAndSync(ServerLevel level, boolean forceSend, boolean blueBleed, boolean redBleed) {
        AASWorldData data = AASWorldData.get(level);
        boolean changed = false;
        String dimKey = level.dimension().location().toString();

        // 1. Р В РІР‚в„ўР В Р’В°Р В Р’В»Р В РЎвЂР В РўвЂР В Р’В°Р РЋРІР‚В Р В РЎвЂР РЋР РЏ Р РЋР вЂљР В Р’В°Р В Р’В»Р В Р’В»Р В РЎвЂР В РЎвЂќР В РЎвЂўР В Р вЂ  (Р РЋРЎвЂњР В РўвЂР В Р’В°Р В Р’В»Р РЋР РЏР В Р’ВµР В РЎВ Р В РЎвЂР В Р’В· Р РЋР С“Р В РЎвЂ”Р В РЎвЂР РЋР С“Р В РЎвЂќР В Р’В° Р В РЎвЂќР В РЎвЂўР В РЎвЂўР РЋР вЂљР В РўвЂР В РЎвЂР В Р вЂ¦Р В Р’В°Р РЋРІР‚С™, Р В Р’ВµР РЋР С“Р В Р’В»Р В РЎвЂ Р В Р’В±Р В Р’В»Р В РЎвЂўР В РЎвЂќ Р РЋРІР‚С›Р В РЎвЂР В Р’В·Р В РЎвЂР РЋРІР‚РЋР В Р’ВµР РЋР С“Р В РЎвЂќР В РЎвЂ Р РЋР С“Р В Р’В»Р В РЎвЂўР В РЎВР В Р’В°Р В Р вЂ¦)
        changed |= validateRallies(level, data.blueRallies);
        changed |= validateRallies(level, data.redRallies);

        // 2. Р В РЎСџР РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р’ВµР РЋР вЂљР В РЎвЂќР В Р’В° Р В РўС’Р В Р’В°Р В Р’В±Р В РЎвЂўР В Р вЂ  (Р В Р’В±Р В Р’В»Р В РЎвЂўР В РЎвЂќР В РЎвЂР РЋР вЂљР В РЎвЂўР В Р вЂ Р В РЎвЂќР В Р’В° Р В Р вЂ Р РЋР вЂљР В Р’В°Р В РЎвЂ“Р В Р’В°Р В РЎВР В РЎвЂ Р В РЎвЂ Р В РЎвЂўР В Р’В±Р В Р вЂ¦Р В РЎвЂўР В Р вЂ Р В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂР В Р’Вµ Р В РЎВР В Р’В°Р РЋРІР‚С™Р В Р’ВµР РЋР вЂљР В РЎвЂР В Р’В°Р В Р’В»Р В РЎвЂўР В Р вЂ )
        int hubRadius = AASConfig.HUB_BLOCK_RADIUS.get();
        int hubEnemiesRequired = AASConfig.HUB_BLOCK_ENEMY_COUNT.get();

        for (AASWorldData.HubInfo hub : data.hubs) {
            if (hub.constructed && hub.dimension != null && hub.dimension.equals(dimKey)) {
                if (level.isLoaded(hub.pos)) {
                    boolean nowBlocked = getEnemyCount(level, hub.pos, hub.team, hubRadius) >= hubEnemiesRequired;
                    if (hub.isBlocked != nowBlocked) {
                        hub.isBlocked = nowBlocked;
                        changed = true;
                    }

                    net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(hub.pos);
                    if (be instanceof com.example.aas.block.HubBlockEntity hubBe) {
                        if (hub.materials != hubBe.getMaterials()) {
                            hub.materials = hubBe.getMaterials();
                            changed = true;
                        }
                    }
                }
            }
        }

        // 3. Р В РЎСџР РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р’ВµР РЋР вЂљР В РЎвЂќР В Р’В° Р В Р’В Р В Р’В°Р В Р’В»Р В Р’В»Р В РЎвЂР В РЎвЂќР В РЎвЂўР В Р вЂ  (Р В Р’ВР В Р вЂ¦Р В РўвЂР В РЎвЂР В Р вЂ Р В РЎвЂР В РўвЂР РЋРЎвЂњР В Р’В°Р В Р’В»Р РЋР Р‰Р В Р вЂ¦Р В РЎвЂў Р В РўвЂР В Р’В»Р РЋР РЏ Р В РЎвЂќР В Р’В°Р В Р’В¶Р В РўвЂР В РЎвЂўР В РЎвЂ“Р В РЎвЂў Р В РЎвЂєР В РЎС›Р В Р’В Р В Р вЂЎР В РІР‚СњР В РЎвЂ™)
        int rallyRadius = AASConfig.RALLY_BLOCK_RADIUS.get();
        int rallyEnemiesRequired = AASConfig.RALLY_BLOCK_ENEMY_COUNT.get();

        boolean anySquadStatusChanged = false;
        boolean teamBlueBlocked = false; // Р В РІР‚СњР В Р’В»Р РЋР РЏ Р РЋР С“Р В РЎвЂўР В Р вЂ Р В РЎВР В Р’ВµР РЋР С“Р РЋРІР‚С™Р В РЎвЂР В РЎВР В РЎвЂўР РЋР С“Р РЋРІР‚С™Р В РЎвЂ Р РЋР С“ Р В РЎвЂўР В Р’В±Р РЋРІР‚В°Р В РЎвЂР В РЎВ Р В РЎвЂ”Р В Р’В°Р В РЎвЂќР В Р’ВµР РЋРІР‚С™Р В РЎвЂўР В РЎВ
        boolean teamRedBlocked = false;

        for (AASWorldData.Squad squad : data.squads) {
            if (squad.rallyPos != null && squad.rallyDimension.equals(dimKey)) {
                // Р В Р Р‹Р РЋРІР‚РЋР В РЎвЂР РЋРІР‚С™Р В Р’В°Р В Р’ВµР В РЎВ Р В Р вЂ Р РЋР вЂљР В Р’В°Р В РЎвЂ“Р В РЎвЂўР В Р вЂ  Р В Р вЂ Р В РЎвЂўР В РЎвЂќР РЋР вЂљР РЋРЎвЂњР В РЎвЂ“ Р РЋР вЂљР В Р’В°Р В Р’В»Р В Р’В»Р В РЎвЂР В РЎвЂќР В Р’В° Р РЋР РЉР РЋРІР‚С™Р В РЎвЂўР В РЎвЂ“Р В РЎвЂў Р В РЎвЂќР В РЎвЂўР В Р вЂ¦Р В РЎвЂќР РЋР вЂљР В Р’ВµР РЋРІР‚С™Р В Р вЂ¦Р В РЎвЂўР В РЎвЂ“Р В РЎвЂў Р В РЎвЂўР РЋРІР‚С™Р РЋР вЂљР РЋР РЏР В РўвЂР В Р’В°
                int enemies = getEnemyCount(level, squad.rallyPos, squad.team, rallyRadius);
                boolean currentlyBlocked = (enemies >= rallyEnemiesRequired);

                // Р В РІР‚СћР РЋР С“Р В Р’В»Р В РЎвЂ Р РЋР С“Р РЋРІР‚С™Р В Р’В°Р РЋРІР‚С™Р РЋРЎвЂњР РЋР С“ Р В РЎвЂР В Р’В·Р В РЎВР В Р’ВµР В Р вЂ¦Р В РЎвЂР В Р’В»Р РЋР С“Р РЋР РЏ - Р В РЎвЂ”Р В РЎвЂўР В РЎВР В Р’ВµР РЋРІР‚РЋР В Р’В°Р В Р’ВµР В РЎВ Р В РўвЂР В Р’В»Р РЋР РЏ Р РЋР С“Р В РЎвЂР В Р вЂ¦Р РЋРІР‚В¦Р РЋР вЂљР В РЎвЂўР В Р вЂ¦Р В РЎвЂР В Р’В·Р В Р’В°Р РЋРІР‚В Р В РЎвЂР В РЎвЂ
                if (squad.isRallyBlocked != currentlyBlocked) {
                    squad.isRallyBlocked = currentlyBlocked;
                    anySquadStatusChanged = true;
                }

                // Р В Р Р‹Р В РЎвЂўР В Р’В±Р В РЎвЂР РЋР вЂљР В Р’В°Р В Р’ВµР В РЎВ Р В РЎвЂўР В Р’В±Р РЋРІР‚В°Р РЋРЎвЂњР РЋР вЂ№ Р В РЎвЂР В Р вЂ¦Р РЋРІР‚С›Р В РЎвЂўР РЋР вЂљР В РЎВР В Р’В°Р РЋРІР‚В Р В РЎвЂР РЋР вЂ№ Р В РўвЂР В Р’В»Р РЋР РЏ Р В РЎвЂР В РЎвЂќР В РЎвЂўР В Р вЂ¦Р В РЎвЂќР В РЎвЂ Р В Р вЂ¦Р В Р’В° Р В РЎвЂќР В РЎвЂўР В РЎВР В РЎвЂ”Р В Р’В°Р РЋР С“Р В Р’Вµ/Р В РЎвЂќР В Р’В°Р РЋР вЂљР РЋРІР‚С™Р В Р’Вµ (Р В Р’ВµР РЋР С“Р В Р’В»Р В РЎвЂ Р В Р вЂ¦Р РЋРЎвЂњР В Р’В¶Р В Р вЂ¦Р В РЎвЂў)
                if (currentlyBlocked) {
                    if (squad.team.equalsIgnoreCase("Blue")) teamBlueBlocked = true;
                    else teamRedBlocked = true;
                }
            }
        }

        // Р В РЎСџР РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р’ВµР РЋР вЂљР В РЎвЂќР В Р’В° Р В РЎвЂќР РЋР РЉР РЋРІвЂљВ¬Р В Р’В° Р В РўвЂР В Р’В»Р РЋР РЏ Р В РЎвЂўР РЋРІР‚С™Р В РЎвЂ”Р РЋР вЂљР В Р’В°Р В Р вЂ Р В РЎвЂќР В РЎвЂ Р В РЎвЂ”Р В Р’В°Р В РЎвЂќР В Р’ВµР РЋРІР‚С™Р В Р’В°
        Boolean cachedBlue = lastBlueBlockedMap.getOrDefault(dimKey, false);
        Boolean cachedRed = lastRedBlockedMap.getOrDefault(dimKey, false);

        // 4. Р В Р Р‹Р В Р’ВР В РЎСљР В РўС’Р В Р’В Р В РЎвЂєР В РЎСљР В Р’ВР В РІР‚вЂќР В РЎвЂ™Р В Р’В¦Р В Р’ВР В Р вЂЎ
        if (forceSend || changed || anySquadStatusChanged || teamBlueBlocked != cachedBlue || teamRedBlocked != cachedRed || blueBleed || redBleed) {

            // Р В РЎвЂєР РЋРІР‚С™Р В РЎвЂ”Р РЋР вЂљР В Р’В°Р В Р вЂ Р В Р’В»Р РЋР РЏР В Р’ВµР В РЎВ Р В РЎвЂўР В Р’В±Р РЋРІР‚В°Р В РЎвЂР В Р’Вµ Р В РўвЂР В Р’В°Р В Р вЂ¦Р В Р вЂ¦Р РЋРІР‚в„–Р В Р’Вµ (Р РЋРІР‚С™Р В РЎвЂР В РЎвЂќР В Р’ВµР РЋРІР‚С™Р РЋРІР‚в„–, Р РЋРІР‚В¦Р В Р’В°Р В Р’В±Р РЋРІР‚в„– Р В РЎвЂ Р РЋРІР‚С™.Р В РўвЂ.)
            sendSyncPacket(level, data, blueBleed, redBleed, teamBlueBlocked, teamRedBlocked);

            // Р В РІР‚СћР РЋР С“Р В Р’В»Р В РЎвЂ Р В РЎвЂР В Р’В·Р В РЎВР В Р’ВµР В Р вЂ¦Р В РЎвЂР В Р’В»Р РЋР С“Р РЋР РЏ Р РЋР С“Р РЋРІР‚С™Р В Р’В°Р РЋРІР‚С™Р РЋРЎвЂњР РЋР С“ Р В Р’В±Р В Р’В»Р В РЎвЂўР В РЎвЂќР В РЎвЂР РЋР вЂљР В РЎвЂўР В Р вЂ Р В РЎвЂќР В РЎвЂ Р РЋРІР‚В¦Р В РЎвЂўР РЋРІР‚С™Р РЋР Р‰ Р В РЎвЂўР В РўвЂР В Р вЂ¦Р В РЎвЂўР В РЎвЂ“Р В РЎвЂў Р В РЎвЂўР РЋРІР‚С™Р РЋР вЂљР РЋР РЏР В РўвЂР В Р’В° - Р РЋРІвЂљВ¬Р В Р’В»Р В Р’ВµР В РЎВ Р В РЎвЂўР В Р’В±Р В Р вЂ¦Р В РЎвЂўР В Р вЂ Р В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂР В Р’Вµ Р В РЎвЂўР РЋРІР‚С™Р РЋР вЂљР РЋР РЏР В РўвЂР В РЎвЂўР В Р вЂ 
            if (anySquadStatusChanged) {
                PacketHandler.INSTANCE.send(PacketDistributor.DIMENSION.with(level::dimension),
                        new PacketSyncSquads(data.squads));
            }

            lastBlueBlockedMap.put(dimKey, teamBlueBlocked);
            lastRedBlockedMap.put(dimKey, teamRedBlocked);
        }

        if (changed || anySquadStatusChanged) data.setDirty();
    }

    // === Р В РЎС™Р В РІР‚СћР В РЎС›Р В РЎвЂєР В РІР‚Сњ Р В РІР‚СњР В РІР‚С”Р В Р вЂЎ Р В Р Р‹Р В РЎвЂєР В РІР‚в„ўР В РЎС™Р В РІР‚СћР В Р Р‹Р В РЎС›Р В Р’ВР В РЎС™Р В РЎвЂєР В Р Р‹Р В РЎС›Р В Р’В ===
    // Р В РЎСљР РЋРЎвЂњР В Р’В¶Р В Р’ВµР В Р вЂ¦, Р РЋРІР‚РЋР РЋРІР‚С™Р В РЎвЂўР В Р’В±Р РЋРІР‚в„– Р В Р вЂ¦Р В Р’Вµ Р В Р’В±Р РЋРІР‚в„–Р В Р’В»Р В РЎвЂў Р В РЎвЂўР РЋРІвЂљВ¬Р В РЎвЂР В Р’В±Р В РЎвЂўР В РЎвЂќ Р В Р вЂ  onEntityDeath Р В РЎвЂ Р В РўвЂР РЋР вЂљР РЋРЎвЂњР В РЎвЂ“Р В РЎвЂР РЋРІР‚В¦ Р В РЎВР В Р’ВµР РЋР С“Р РЋРІР‚С™Р В Р’В°Р РЋРІР‚В¦, Р В РЎвЂ“Р В РўвЂР В Р’Вµ Р В РЎВР РЋРІР‚в„– Р В Р вЂ¦Р В Р’Вµ Р В Р’В·Р В Р вЂ¦Р В Р’В°Р В Р’ВµР В РЎВ Р РЋР С“Р РЋРІР‚С™Р В Р’В°Р РЋРІР‚С™Р РЋРЎвЂњР РЋР С“ Р В Р’В±Р В Р’В»Р В РЎвЂР РЋРІР‚С™Р РЋРІР‚В Р В Р’В°.
    // Р В РЎСџР В РЎвЂў Р РЋРЎвЂњР В РЎВР В РЎвЂўР В Р’В»Р РЋРІР‚РЋР В Р’В°Р В Р вЂ¦Р В РЎвЂР РЋР вЂ№ Р В РЎвЂ”Р В Р’ВµР РЋР вЂљР В Р’ВµР В РўвЂР В Р’В°Р В Р’ВµР В РЎВ false (Р В Р вЂ¦Р В Р’ВµР РЋРІР‚С™ Р В Р’В±Р В Р’В»Р В РЎвЂР РЋРІР‚С™Р РЋРІР‚В Р В Р’В°).
    private static void validateAndSync(ServerLevel level, boolean forceSend) {
        validateAndSync(level, forceSend, false, false);
    }

    private static boolean validateRallies(ServerLevel level, List<BlockPos> rallies) {
        return rallies.removeIf(pos -> {
            if (level.isLoaded(pos)) {
                return !(level.getBlockState(pos).getBlock() instanceof RallyPointBlock);
            }
            return false;
        });
    }

    private static boolean isEnemyNearby(ServerLevel level, BlockPos pos, String allyTeamName, int radius, int minCount) {
        return getEnemyCount(level, pos, allyTeamName, radius) >= minCount;
    }

    private static int getEnemyCount(ServerLevel level, BlockPos pos, String allyTeamName, int radius) {
        // Р В Р’ВР РЋР С“Р В РЎвЂ”Р В РЎвЂўР В Р’В»Р РЋР Р‰Р В Р’В·Р РЋРЎвЂњР В Р’ВµР В РЎВ Р В РЎвЂ”Р В Р’ВµР РЋР вЂљР В Р’ВµР В РўвЂР В Р’В°Р В Р вЂ¦Р В Р вЂ¦Р РЋРІР‚в„–Р В РІвЂћвЂ“ Р РЋР вЂљР В Р’В°Р В РўвЂР В РЎвЂР РЋРЎвЂњР РЋР С“
        AABB checkArea = new AABB(pos).inflate(radius);
        List<ServerPlayer> enemies = level.getEntitiesOfClass(ServerPlayer.class, checkArea);
        int count = 0;
        for (ServerPlayer p : enemies) {
            if (p.isSpectator()) continue;
            if (p.getTeam() == null || !p.getTeam().getName().equalsIgnoreCase(allyTeamName)) count++;
        }
        return count;
    }

    public static int getEnemyCount(ServerLevel level, BlockPos pos, String allyTeamName) {
        AABB checkArea = new AABB(pos).inflate(40);
        List<ServerPlayer> enemies = level.getEntitiesOfClass(ServerPlayer.class, checkArea);
        int count = 0;
        for (ServerPlayer p : enemies) {
            if (p.isSpectator()) continue;
            if (p.getTeam() == null || !p.getTeam().getName().equalsIgnoreCase(allyTeamName)) count++;
        }
        return count;
    }

    public static void checkGameOver(ServerLevel level, AASWorldData data) {
        if (data.blueTickets <= 0) {
            data.blueTickets = 0;
            executeVictory(level, data, false); // Р В РЎв„ўР РЋР вЂљР В Р’В°Р РЋР С“Р В Р вЂ¦Р РЋРІР‚в„–Р В Р’Вµ Р В Р вЂ Р РЋРІР‚в„–Р В РЎвЂР В РЎвЂ“Р РЋР вЂљР В Р’В°Р В Р’В»Р В РЎвЂ
        } else if (data.redTickets <= 0) {
            data.redTickets = 0;
            executeVictory(level, data, true); // Р В Р Р‹Р В РЎвЂР В Р вЂ¦Р В РЎвЂР В Р’Вµ Р В Р вЂ Р РЋРІР‚в„–Р В РЎвЂР В РЎвЂ“Р РЋР вЂљР В Р’В°Р В Р’В»Р В РЎвЂ
        }
    }

    private static void executeVictory(ServerLevel level, AASWorldData data, boolean blueWon) {
        data.isGameStarted = false;
        data.setDirty();

        // 1. Р В РЎвЂєР В РЎвЂ”Р РЋР вЂљР В Р’ВµР В РўвЂР В Р’ВµР В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂР В Р’Вµ Р В РЎвЂ”Р В РЎвЂўР В Р’В±Р В Р’ВµР В РўвЂР В РЎвЂР РЋРІР‚С™Р В Р’ВµР В Р’В»Р РЋР РЏ
        String winnerName;
        String winnerFaction;
        if (blueWon) {
            winnerFaction = data.blueFaction;
            winnerName = (winnerFaction == null || winnerFaction.equals("none") || winnerFaction.equals("bluefor"))
                    ? AASConfig.BLUE_TEAM_CUSTOM_NAME.get() : formatFactionName(winnerFaction);
            if (winnerName.isEmpty()) winnerName = "BLUE TEAM";
        } else {
            winnerFaction = data.redFaction;
            winnerName = (winnerFaction == null || winnerFaction.equals("none") || winnerFaction.equals("redfor"))
                    ? AASConfig.RED_TEAM_CUSTOM_NAME.get() : formatFactionName(winnerFaction);
            if (winnerName.isEmpty()) winnerName = "RED TEAM";
        }

        String subText = (blueWon ? data.blueTickets : data.redTickets) + " tickets remaining";

        // 2. Р В РЎвЂєР РЋРІР‚С™Р В РЎвЂ”Р РЋР вЂљР В Р’В°Р В Р вЂ Р В РЎвЂќР В Р’В° Р В Р вЂ¦Р В РЎвЂўР В Р вЂ Р В РЎвЂўР В РЎвЂ“Р В РЎвЂў Р В РЎвЂќР РЋР вЂљР В Р’В°Р РЋР С“Р В РЎвЂР В Р вЂ Р В РЎвЂўР В РЎвЂ“Р В РЎвЂў Р РЋР РЉР В РЎвЂќР РЋР вЂљР В Р’В°Р В Р вЂ¦Р В Р’В° Р В Р вЂ Р РЋР С“Р В Р’ВµР В РЎВ
        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),
                new PacketOpenVictoryScreen(winnerName, winnerFaction, subText, blueWon));

        // 3. Р В РІР‚в„ўР РЋРІР‚в„–Р В РЎвЂќР В Р’В»Р РЋР вЂ№Р РЋРІР‚РЋР В Р’ВµР В Р вЂ¦Р В РЎвЂР В Р’Вµ Friendly Fire Р В РўвЂР В Р’В»Р РЋР РЏ Р В РЎвЂќР В РЎвЂўР В РЎВР В Р’В°Р В Р вЂ¦Р В РўвЂ (Р В Р вЂ¦Р В РЎвЂР В РЎвЂќР РЋРІР‚С™Р В РЎвЂў Р В Р вЂ¦Р В Р’Вµ Р РЋР С“Р В РЎВР В РЎвЂўР В Р’В¶Р В Р’ВµР РЋРІР‚С™ Р РЋРЎвЂњР В Р’В±Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰ Р РЋР С“Р В Р вЂ Р В РЎвЂўР В РЎвЂР РЋРІР‚В¦)
        Scoreboard scoreboard = level.getScoreboard();
        PlayerTeam blueTeam = scoreboard.getPlayerTeam("Blue");
        PlayerTeam redTeam = scoreboard.getPlayerTeam("Red");
        if (blueTeam != null) blueTeam.setAllowFriendlyFire(false);
        if (redTeam != null) redTeam.setAllowFriendlyFire(false);

        // 4. Р В РЎвЂєР РЋРІР‚РЋР В РЎвЂР РЋР С“Р РЋРІР‚С™Р В РЎвЂќР В Р’В° Р В РЎвЂР В Р вЂ¦Р В Р вЂ Р В Р’ВµР В Р вЂ¦Р РЋРІР‚С™Р В Р’В°Р РЋР вЂљР В Р’ВµР В РІвЂћвЂ“, Р В Р вЂ Р В РЎвЂўР РЋР С“Р В РЎвЂќР РЋР вЂљР В Р’ВµР РЋРІвЂљВ¬Р В Р’ВµР В Р вЂ¦Р В РЎвЂР В Р’Вµ Р В Р вЂ¦Р В РЎвЂўР В РЎвЂќР В Р вЂ¦Р РЋРЎвЂњР РЋРІР‚С™Р РЋРІР‚в„–Р РЋРІР‚В¦ Р В РЎвЂ Р РЋРІР‚С™Р В Р’ВµР В Р’В»Р В Р’ВµР В РЎвЂ”Р В РЎвЂўР РЋР вЂљР РЋРІР‚С™ Р В Р вЂ Р РЋР С“Р В Р’ВµР РЋРІР‚В¦ Р В Р вЂ¦Р В Р’В° Р В РЎС™Р В Р’ВµР В РІвЂћвЂ“Р В Р вЂ¦ Р В РІР‚ВР В Р’В°Р В Р’В·Р РЋРЎвЂњ
        String currentDim = level.dimension().location().toString();

        for (ServerPlayer player : level.players()) {
            if (player.isCreative() || player.isSpectator()) continue;

            // Р В РІР‚СћР РЋР С“Р В Р’В»Р В РЎвЂ Р В РЎвЂР В РЎвЂ“Р РЋР вЂљР В РЎвЂўР В РЎвЂќ Р В Р’В±Р РЋРІР‚в„–Р В Р’В» Р В Р вЂ  Р В Р вЂ¦Р В РЎвЂўР В РЎвЂќР В Р’Вµ - Р В РЎвЂ”Р В РЎвЂўР В РўвЂР В Р вЂ¦Р В РЎвЂР В РЎВР В Р’В°Р В Р’ВµР В РЎВ Р В Р’ВµР В РЎвЂ“Р В РЎвЂў
            if (player.getPersistentData().getBoolean("AAS_IsDowned")) {
                com.example.aas.events.DownedHandler.revivePlayer(player);
            }

            // Р В РІР‚вЂќР В Р’В°Р В Р’В±Р В РЎвЂР РЋР вЂљР В Р’В°Р В Р’ВµР В РЎВ Р В РЎв„ўР В РЎвЂР РЋРІР‚С™ (Р В РЎвЂР В Р вЂ¦Р В Р вЂ Р В Р’ВµР В Р вЂ¦Р РЋРІР‚С™Р В Р’В°Р РЋР вЂљР РЋР Р‰)
            player.setHealth(player.getMaxHealth());
            player.getPersistentData().putString("AAS_CurrentKit", "Unassigned");
            PacketHandler.INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new PacketSyncMyKit("Unassigned"));
            player.getPersistentData().remove("AAS_PendingKit");
            player.getInventory().clearContent();
            ResupplyHandler.clearCurios(player);
            player.inventoryMenu.broadcastChanges();
            player.containerMenu.broadcastChanges();

            // Р В РЎС›Р В Р’ВµР В Р’В»Р В Р’ВµР В РЎвЂ”Р В РЎвЂўР РЋР вЂљР РЋРІР‚С™Р В Р’В°Р РЋРІР‚В Р В РЎвЂР РЋР РЏ Р В Р вЂ¦Р В Р’В° Р В РЎС™Р В Р’ВµР В РІвЂћвЂ“Р В Р вЂ¦
            String pTeam = player.getTeam() != null ? player.getTeam().getName().toUpperCase() : "NEUTRAL";
            BlockPos spawnPos = null;

            if (pTeam.equals("BLUE")) spawnPos = data.blueSpawns.get(currentDim);
            else if (pTeam.equals("RED")) spawnPos = data.redSpawns.get(currentDim);

            if (spawnPos != null) {
                // Р В РІР‚СћР РЋР С“Р В Р’В»Р В РЎвЂ Р В РЎвЂР В РЎвЂ“Р РЋР вЂљР В РЎвЂўР В РЎвЂќ Р В Р’В±Р РЋРІР‚в„–Р В Р’В» Р В РЎВР В Р’ВµР РЋР вЂљР РЋРІР‚С™Р В Р вЂ  (Р В Р вЂ¦Р В Р’В° Р РЋР РЉР В РЎвЂќР РЋР вЂљР В Р’В°Р В Р вЂ¦Р В Р’Вµ Р РЋР С“Р В РЎВР В Р’ВµР РЋР вЂљР РЋРІР‚С™Р В РЎвЂ), Р РЋРЎвЂњР РЋР С“Р РЋРІР‚С™Р В Р’В°Р В Р вЂ¦Р В Р’В°Р В Р вЂ Р В Р’В»Р В РЎвЂР В Р вЂ Р В Р’В°Р В Р’ВµР В РЎВ Р В Р’ВµР В РЎВР РЋРЎвЂњ Р РЋРІР‚С™Р В РЎвЂўР РЋРІР‚РЋР В РЎвЂќР РЋРЎвЂњ Р В Р вЂ Р В РЎвЂўР В Р’В·Р РЋР вЂљР В РЎвЂўР В Р’В¶Р В РўвЂР В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР РЏ Р РЋР С“Р РЋР вЂ№Р В РўвЂР В Р’В°
                player.setRespawnPosition(level.dimension(), spawnPos, 0.0f, true, false);

                // Р В РІР‚СћР РЋР С“Р В Р’В»Р В РЎвЂ Р В Р’В¶Р В РЎвЂР В Р вЂ  - Р В РЎвЂ”Р РЋР вЂљР В РЎвЂўР РЋР С“Р РЋРІР‚С™Р В РЎвЂў Р РЋРІР‚С™Р В Р’ВµР В Р’В»Р В Р’ВµР В РЎвЂ”Р В РЎвЂўР РЋР вЂљР РЋРІР‚С™Р В РЎвЂР РЋР вЂљР РЋРЎвЂњР В Р’ВµР В РЎВ
                if (player.isAlive()) {
                    player.teleportTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);
                }
            }
        }
    }

    private static String formatFactionName(String faction) {
        return faction.replace("_", " ").toUpperCase();
    }

    private static void sendTitleToLevel(ServerLevel level, String text, ChatFormatting color) {
        Component title = Component.literal(text).withStyle(color).withStyle(ChatFormatting.BOLD);
        for (ServerPlayer player : level.players()) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(0, 20, 10));
            player.connection.send(new ClientboundSetTitleTextPacket(title));
        }
    }

    private static void sendTitleToAll(MinecraftServer server, String text, ChatFormatting color) {
        Component title = Component.literal(text).withStyle(color).withStyle(ChatFormatting.BOLD);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(0, 20, 10));
            player.connection.send(new ClientboundSetTitleTextPacket(title));
        }
    }

    private static void broadcastMessage(ServerLevel level, String text, ChatFormatting color) {
        level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(text).withStyle(color), false);
    }

    // Used for vehicle-loss messages: respects AASConfig.SEPARATE_TEAM_DESTRUCTION_MESSAGES,
    // keeping "X lost vehicle" messages within team X (plus players without a Blue/Red team) when enabled.
    private static void broadcastVehicleLossMessage(ServerLevel level, String ownerTeam, String text, ChatFormatting color) {
        TeamMessageUtil.broadcastDestructionMessage(level, ownerTeam, Component.literal(text).withStyle(color));
    }

    private static void sendSyncPacket(ServerLevel level, AASWorldData data) {
        sendSyncPacket(level, data, false, false);
    }

    private static void sendSyncPacket(ServerLevel level, AASWorldData data, boolean blueBleed, boolean redBleed) {
        String dimKey = level.dimension().location().toString();
        boolean bBlocked = lastBlueBlockedMap.getOrDefault(dimKey, false);
        boolean rBlocked = lastRedBlockedMap.getOrDefault(dimKey, false);
        sendSyncPacket(level, data, blueBleed, redBleed, bBlocked, rBlocked);
    }

    private static void sendSyncPacket(ServerLevel level, AASWorldData data, boolean blueBleed, boolean redBleed, boolean bBlocked, boolean rBlocked) {
        PacketSyncGameData packet = createSyncPacket(data, blueBleed, redBleed, bBlocked, rBlocked);
        PacketHandler.INSTANCE.send(PacketDistributor.DIMENSION.with(level::dimension), packet);
    }

    private static PacketSyncGameData createSyncPacket(AASWorldData data, boolean blueBleed, boolean redBleed, boolean bBlocked, boolean rBlocked) {
        boolean hasBlue = !data.blueRallies.isEmpty();
        boolean hasRed = !data.redRallies.isEmpty();

        String bName = data.blueFaction.equals("none") ? AASConfig.BLUE_TEAM_CUSTOM_NAME.get() : data.blueFaction.toUpperCase();
        String rName = data.redFaction.equals("none") ? AASConfig.RED_TEAM_CUSTOM_NAME.get() : data.redFaction.toUpperCase();

        Map<String, String> pKits = new HashMap<>();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                String current = p.getPersistentData().getString("AAS_CurrentKit");
                String pending = p.getPersistentData().getString("AAS_PendingKit");
                String displayKit = !pending.isEmpty() ? pending : current;
                pKits.put(p.getScoreboardName(), displayKit.isEmpty() ? "Unassigned" : displayKit);
            }
        }

        // Р В Р’ВР В Р Р‹Р В РЎСџР В РЎвЂєР В РІР‚С”Р В Р’В¬Р В РІР‚вЂќР В Р в‚¬Р В РІР‚СћР В РЎС™ BUILDER Р В РІР‚в„ўР В РЎС™Р В РІР‚СћР В Р Р‹Р В РЎС›Р В РЎвЂє NEW
        return PacketSyncGameData.builder()
                .tickets(data.blueTickets, data.redTickets)
                .rally(hasBlue, hasRed)
                .bleeding(blueBleed, redBleed)
                .respawnTime(data.respawnTimer)
                .blocked(bBlocked, rBlocked)
                .hubSpawnCost(AASConfig.HUB_SPAWN_COSTS_MATERIALS.get(), AASConfig.HUB_SPAWN_MATERIAL_COST.get())
                .map(data.mapCenterX, data.mapCenterZ, data.mapSizeBlocks, data.currentMapImage)
                .vehicles(new ArrayList<>(data.markedVehicles))
                .hubs(new ArrayList<>(data.hubs))
                .factions(data.blueFaction, data.redFaction)
                .customNames(bName, rName)
                .gameStarted(data.isGameStarted)
                .capturePoints(new ArrayList<>(data.capturePoints))
                .spawns(new HashMap<>(data.blueSpawns), new HashMap<>(data.redSpawns), new HashMap<>(data.neutralSpawns))
                .playerKits(pKits)
                .markers(new ArrayList<>(data.activeMarkers))
                .startVote(data.voteActive, data.voteTimer, new HashMap<>(data.votes))
                .commanderIds(data.blueCMDId, data.redCMDId)
                .blueCommanderVote(data.blueCmdVoteActive, data.blueCmdCandidateName, data.blueCmdCandidateId, data.blueCmdVoteTimer, new HashMap<>(data.blueCmdVotes))
                .redCommanderVote(data.redCmdVoteActive, data.redCmdCandidateName, data.redCmdCandidateId, data.redCmdVoteTimer, new HashMap<>(data.redCmdVotes))
                .activeStrikes(new ArrayList<>(data.activeStrikes))
                .artillery(
                        data.blueArtRequest != null ? data.blueArtRequest.pos : BlockPos.ZERO,
                        data.redArtRequest != null ? data.redArtRequest.pos : BlockPos.ZERO,
                        data.blueArtRequest != null ? data.blueArtRequest.timer : 0,
                        data.redArtRequest != null ? data.redArtRequest.timer : 0,
                        data.blueArtRequest != null ? data.blueArtRequest.requesterName : "",
                        data.redArtRequest != null ? data.redArtRequest.requesterName : ""
                )
                .ready(data.blueReady, data.redReady)
                .gameMode(data.gameMode)
                .invasion(data.invasionDefender, data.invasionPrepTicks)
                .build();
    }

    private static boolean canCapture(AASWorldData.CapturePoint target, String team, AASWorldData data) {
        int currentPriority = team.equals("BLUE") ? target.bluePriority : target.redPriority;
        if (currentPriority <= 1) return true;
        int requiredPriority = currentPriority - 1;
        for (AASWorldData.CapturePoint p : data.capturePoints) {
            int pPriority = team.equals("BLUE") ? p.bluePriority : p.redPriority;
            if (pPriority == requiredPriority && p.owner.equals(team)) return true;
        }
        return false;
    }
    // === Р В РІР‚в„ўР В Р Р‹Р В РЎС›Р В РЎвЂ™Р В РІР‚в„ўР В Р’ВР В РЎС›Р В Р’В¬ Р В РІР‚в„ў Р В РЎв„ўР В РІР‚С”Р В РЎвЂ™Р В Р Р‹Р В Р Р‹ GameLogicEvents ===

    public static void leaveCurrentSquad(ServerPlayer player, AASWorldData data) {
        String pName = player.getScoreboardName();

        // 1. Р В Р в‚¬Р В РўвЂР В Р’В°Р В Р’В»Р РЋР РЏР В Р’ВµР В РЎВ Р В РЎвЂР В РЎвЂ“Р РЋР вЂљР В РЎвЂўР В РЎвЂќР В Р’В° Р В РЎвЂР В Р’В· Р В Р вЂ Р РЋР С“Р В Р’ВµР РЋРІР‚В¦ Р В РЎвЂўР РЋРІР‚С™Р РЋР вЂљР РЋР РЏР В РўвЂР В РЎвЂўР В Р вЂ , Р В РЎвЂ“Р В РўвЂР В Р’Вµ Р В РЎвЂўР В Р вЂ¦ Р В РЎВР В РЎвЂўР В Р’В¶Р В Р’ВµР РЋРІР‚С™ Р РЋРІР‚РЋР В РЎвЂР РЋР С“Р В Р’В»Р В РЎвЂР РЋРІР‚С™Р РЋР Р‰Р РЋР С“Р РЋР РЏ
        for (AASWorldData.Squad s : data.squads) {
            if (s.members.contains(pName)) {
                s.members.remove(pName);
                if (s.leader.equals(pName)) {
                    // Р В РІР‚СћР РЋР С“Р В Р’В»Р В РЎвЂ Р В Р’В±Р РЋРІР‚в„–Р В Р’В» Р В Р’В»Р В РЎвЂР В РўвЂР В Р’ВµР РЋР вЂљР В РЎвЂўР В РЎВ Р Р†Р вЂљРІР‚Сњ Р РЋРЎвЂњР В РўвЂР В Р’В°Р В Р’В»Р РЋР РЏР В Р’ВµР В РЎВ Р РЋР вЂљР В Р’В°Р РЋРІР‚В Р В РЎвЂР РЋР вЂ№
                    PacketSquadAction.removeRadio(player);
                    if (!s.members.isEmpty()) {
                        s.removeFromFireteams(s.members.get(0));
                        s.leader = s.members.get(0);
                        ServerPlayer newLeader = player.server.getPlayerList().getPlayerByName(s.leader);
                        if (newLeader != null) {
                            PacketSquadAction.updatePlayerTags(newLeader, s.id, true);
                            if (AASConfig.AUTO_GIVE_SL_RADIO.get()) PacketSquadAction.giveRadio(newLeader);
                        }
                    }
                }
            }
        }
        data.squads.removeIf(s -> s.members.isEmpty());
        data.setDirty();
        PacketHandler.sendToAllClients(player.serverLevel(), data);
        // 2. Р В РЎСџР В РЎвЂєР В РІР‚С”Р В РЎСљР В РЎвЂ™Р В Р вЂЎ Р В РЎвЂєР В Р’В§Р В Р’ВР В Р Р‹Р В РЎС›Р В РЎв„ўР В РЎвЂ™ Р В РЎв„ўР В Р’ВР В РЎС›Р В РЎвЂ™ (Р В РЎС›Р В Р’ВµР В РЎвЂ“Р В РЎвЂ Р В РЎвЂ Р В Р’ВР В Р вЂ¦Р В Р вЂ Р В Р’ВµР В Р вЂ¦Р РЋРІР‚С™Р В Р’В°Р РЋР вЂљР РЋР Р‰)
        // Р В Р Р‹Р В Р’В±Р РЋР вЂљР В Р’В°Р РЋР С“Р РЋРІР‚в„–Р В Р вЂ Р В Р’В°Р В Р’ВµР В РЎВ Р РЋРІР‚С™Р В Р’ВµР В РЎвЂ“Р В РЎвЂ Р В Р вЂ  Р РЋР С“Р В РЎвЂўР РЋР С“Р РЋРІР‚С™Р В РЎвЂўР РЋР РЏР В Р вЂ¦Р В РЎвЂР В Р’Вµ "Р В РЎвЂ”Р В РЎвЂў Р РЋРЎвЂњР В РЎВР В РЎвЂўР В Р’В»Р РЋРІР‚РЋР В Р’В°Р В Р вЂ¦Р В РЎвЂР РЋР вЂ№"
        player.getPersistentData().putString("AAS_CurrentKit", "Unassigned");
        PacketHandler.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                new PacketSyncMyKit("Unassigned"));
        player.getPersistentData().putString("AAS_PendingKit", ""); // Р В РЎвЂєР РЋРІР‚РЋР В РЎвЂР РЋРІР‚В°Р В Р’В°Р В Р’ВµР В РЎВ Р В Р’В±Р РЋР вЂљР В РЎвЂўР В Р вЂ¦Р РЋР Р‰

        // Р В РЎвЂєР РЋРІР‚РЋР В РЎвЂР РЋРІР‚В°Р В Р’В°Р В Р’ВµР В РЎВ Р В РЎвЂР В Р вЂ¦Р В Р вЂ Р В Р’ВµР В Р вЂ¦Р РЋРІР‚С™Р В Р’В°Р РЋР вЂљР РЋР Р‰ Р В РЎвЂР В РЎвЂ“Р РЋР вЂљР В РЎвЂўР В РЎвЂќР В Р’В° Р В РЎвЂ”Р В РЎвЂўР В Р’В»Р В Р вЂ¦Р В РЎвЂўР РЋР С“Р РЋРІР‚С™Р РЋР Р‰Р РЋР вЂ№
        player.getInventory().clearContent();
        ResupplyHandler.clearCurios(player);

        // Р В Р Р‹Р В РЎвЂР В Р вЂ¦Р РЋРІР‚В¦Р РЋР вЂљР В РЎвЂўР В Р вЂ¦Р В РЎвЂР В Р’В·Р В РЎвЂР РЋР вЂљР РЋРЎвЂњР В Р’ВµР В РЎВ Р В РЎвЂР В Р вЂ¦Р В Р вЂ Р В Р’ВµР В Р вЂ¦Р РЋРІР‚С™Р В Р’В°Р РЋР вЂљР РЋР Р‰ (Р РЋРІР‚РЋР РЋРІР‚С™Р В РЎвЂўР В Р’В±Р РЋРІР‚в„– Р В Р вЂ Р В Р’ВµР РЋРІР‚В°Р В РЎвЂ Р В РЎвЂР РЋР С“Р РЋРІР‚РЋР В Р’ВµР В Р’В·Р В Р’В»Р В РЎвЂ Р РЋРЎвЂњ Р В РЎвЂР В РЎвЂ“Р РЋР вЂљР В РЎвЂўР В РЎвЂќР В Р’В°)
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.broadcastChanges();

        // Р В Р в‚¬Р В Р’В±Р В РЎвЂР РЋР вЂљР В Р’В°Р В Р’ВµР В РЎВ Р РЋРІР‚С™Р В Р’ВµР В РЎвЂ“Р В РЎвЂ Р В Р’В»Р В РЎвЂР В РўвЂР В Р’ВµР РЋР вЂљР В Р’В°/Р В РЎвЂўР РЋРІР‚С™Р РЋР вЂљР РЋР РЏР В РўвЂР В Р’В°
        PacketSquadAction.removePlayerTags(player);

        // 3. Р В Р в‚¬Р В Р вЂ Р В Р’ВµР В РўвЂР В РЎвЂўР В РЎВР В Р’В»Р В Р’ВµР В Р вЂ¦Р В РЎвЂР В Р’Вµ
        player.displayClientMessage(Component.literal("Р вЂ™Р’В§eSquad left. Kit and reservations cleared."), true);

        // 4. Р В Р Р‹Р В Р’ВР В РЎСљР В РўС’Р В Р’В Р В РЎвЂєР В РЎСљР В Р’ВР В РІР‚вЂќР В РЎвЂ™Р В Р’В¦Р В Р’ВР В Р вЂЎ Р В РІР‚СњР В РЎвЂ™Р В РЎСљР В РЎСљР В Р’В«Р В РўС’ Р В РЎС™Р В Р’ВР В Р’В Р В РЎвЂ™
        // Р В РЎС™Р РЋРІР‚в„– Р В РЎвЂ”Р В РЎвЂўР В РЎВР В Р’ВµР РЋРІР‚РЋР В Р’В°Р В Р’ВµР В РЎВ Р В РўвЂР В Р’В°Р В Р вЂ¦Р В Р вЂ¦Р РЋРІР‚в„–Р В Р’Вµ Р В РЎвЂќР В Р’В°Р В РЎвЂќ "Р В РЎвЂ“Р РЋР вЂљР РЋР РЏР В Р’В·Р В Р вЂ¦Р РЋРІР‚в„–Р В Р’Вµ" Р В РЎвЂ Р В РЎСџР В Р’В Р В Р’ВР В РЎСљР В Р в‚¬Р В РІР‚СњР В Р’ВР В РЎС›Р В РІР‚СћР В РІР‚С”Р В Р’В¬Р В РЎСљР В РЎвЂє Р РЋРІвЂљВ¬Р В Р’В»Р В Р’ВµР В РЎВ Р В РЎвЂ”Р В Р’В°Р В РЎвЂќР В Р’ВµР РЋРІР‚С™ Р В Р вЂ Р РЋР С“Р В Р’ВµР В РЎВ
        data.setDirty();
        PacketHandler.sendToAllClients(player.serverLevel(), data);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!event.getEntity().level().isClientSide) {
            ServerPlayer player = (ServerPlayer) event.getEntity();
            ServerLevel level = player.serverLevel();
            AASWorldData data = AASWorldData.get(level);

            // === Р В Р Р‹Р В РЎвЂєР В РўС’Р В Р’В Р В РЎвЂ™Р В РЎСљР В РІР‚СћР В РЎСљР В Р’ВР В РІР‚Сћ Р В РЎСџР В Р’В Р В РЎвЂєР В РІР‚СљР В Р’В Р В РІР‚СћР В Р Р‹Р В Р Р‹Р В РЎвЂ™ Р В РЎСџР В Р’В Р В Р’В Р В РІР‚в„ўР В Р’В«Р В РІР‚С”Р В РІР‚СћР В РЎС›Р В РІР‚Сћ Р В РІР‚в„ў Р В РІР‚ВР В РЎвЂєР В Р’В® ===
            if (data.isGameStarted) {
                return; // Р В РЎСљР В РЎвЂР РЋРІР‚РЋР В Р’ВµР В РЎвЂ“Р В РЎвЂў Р В Р вЂ¦Р В Р’Вµ Р В РЎвЂўР РЋРІР‚РЋР В РЎвЂР РЋРІР‚В°Р В Р’В°Р В Р’ВµР В РЎВ, Р В Р’ВµР РЋР С“Р В Р’В»Р В РЎвЂ Р В РЎвЂР В РЎвЂ“Р РЋР вЂљР В Р’В° Р В РЎвЂР В РўвЂР В Р’ВµР РЋРІР‚С™!
            }

            // === Р В РЎвЂєР В Р’В§Р В Р’ВР В Р Р‹Р В РЎС›Р В РЎв„ўР В РЎвЂ™ Р В РІР‚СњР В РЎвЂє Р В Р Р‹Р В РЎС›Р В РЎвЂ™Р В Р’В Р В РЎС›Р В РЎвЂ™ Р В Р’ВР В РІР‚СљР В Р’В Р В Р’В« ===
            // 1. Р В Р в‚¬Р В РўвЂР В Р’В°Р В Р’В»Р РЋР РЏР В Р’ВµР В РЎВ Р В РЎвЂР В Р’В· Р В РЎвЂўР РЋРІР‚С™Р РЋР вЂљР РЋР РЏР В РўвЂР В Р’В° Р В РЎвЂ Р РЋРІР‚РЋР В РЎвЂР РЋР С“Р РЋРІР‚С™Р В РЎвЂР В РЎВ Р РЋРІР‚С™Р В Р’ВµР В РЎвЂ“Р В РЎвЂ SL (Р В РЎвЂ”Р В Р’ВµР РЋР вЂљР В Р’ВµР В РўвЂР В Р’В°Р В Р’ВµР В РЎВ Р В Р’В»Р В РЎвЂР В РўвЂР В Р’ВµР РЋР вЂљР В Р’В° Р В РўвЂР РЋР вЂљР РЋРЎвЂњР В РЎвЂ“Р В РЎвЂўР В РЎВР РЋРЎвЂњ, Р В Р’ВµР РЋР С“Р В Р’В»Р В РЎвЂ Р В Р вЂ¦Р РЋРЎвЂњР В Р’В¶Р В Р вЂ¦Р В РЎвЂў)
            PacketSquadAction.leaveCurrentSquad(player, data);

            // 2. Р В РЎСџР В РЎвЂєР В РІР‚С”Р В РЎСљР В Р’В«Р В РІвЂћСћ Р В Р Р‹Р В РІР‚ВР В Р’В Р В РЎвЂєР В Р Р‹ Р В РЎв„ўР В Р’ВР В РЎС›Р В РЎвЂ™ Р В РЎСџР В Р’В Р В Р’В Р В РІР‚в„ўР В Р’В«Р В РўС’Р В РЎвЂєР В РІР‚СњР В РІР‚Сћ
            player.getPersistentData().putString("AAS_CurrentKit", "Unassigned");
            PacketHandler.INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    new PacketSyncMyKit("Unassigned"));
            player.getPersistentData().remove("AAS_PendingKit");

            // 3. Р В РЎвЂєР РЋРІР‚РЋР В РЎвЂР РЋР С“Р РЋРІР‚С™Р В РЎвЂќР В Р’В° Р В РЎвЂР В Р вЂ¦Р В Р вЂ Р В Р’ВµР В Р вЂ¦Р РЋРІР‚С™Р В Р’В°Р РЋР вЂљР РЋР РЏ, Р РЋРІР‚РЋР РЋРІР‚С™Р В РЎвЂўР В Р’В±Р РЋРІР‚в„– Р В Р вЂ¦Р В Р’Вµ Р В РўвЂР РЋР вЂ№Р В РЎвЂ”Р В Р’В°Р В Р’В»Р В РЎвЂР РЋР С“Р РЋР Р‰ Р В Р вЂ Р В Р’ВµР РЋРІР‚В°Р В РЎвЂ
            player.getInventory().clearContent();
            ResupplyHandler.clearCurios(player);
            player.inventoryMenu.broadcastChanges();

            data.setDirty();

            // Р В Р Р‹Р В РЎвЂР В Р вЂ¦Р РЋРІР‚В¦Р РЋР вЂљР В РЎвЂўР В Р вЂ¦Р В РЎвЂР В Р’В·Р В РЎвЂР РЋР вЂљР РЋРЎвЂњР В Р’ВµР В РЎВ Р РЋР С“Р В РЎвЂ”Р В РЎвЂР РЋР С“Р В РЎвЂўР В РЎвЂќ Р В РЎвЂўР РЋРІР‚С™Р РЋР вЂљР РЋР РЏР В РўвЂР В РЎвЂўР В Р вЂ  Р В РўвЂР В Р’В»Р РЋР РЏ Р В РЎвЂўР РЋР С“Р РЋРІР‚С™Р В Р’В°Р В Р’В»Р РЋР Р‰Р В Р вЂ¦Р РЋРІР‚в„–Р РЋРІР‚В¦ Р В РЎвЂР В РЎвЂ“Р РЋР вЂљР В РЎвЂўР В РЎвЂќР В РЎвЂўР В Р вЂ 
            PacketHandler.INSTANCE.send(PacketDistributor.DIMENSION.with(level::dimension), new PacketSyncSquads(data.squads));
        }
    }
    private static void playSirenForTeam(ServerLevel level, String teamName) {
        for (ServerPlayer player : level.players()) {
            if (player.getTeam() != null && player.getTeam().getName().equalsIgnoreCase(teamName)) {
                // Р В Р’В¤Р В Р’ВР В РЎв„ўР В Р Р‹: Р В РЎвЂєР РЋРІР‚С™Р В РЎвЂ”Р РЋР вЂљР В Р’В°Р В Р вЂ Р В Р’В»Р РЋР РЏР В Р’ВµР В РЎВ Р В Р’В·Р В Р вЂ Р РЋРЎвЂњР В РЎвЂќ Р В Р вЂ¦Р В Р’В°Р В РЎвЂ”Р РЋР вЂљР РЋР РЏР В РЎВР РЋРЎвЂњР РЋР вЂ№ Р В РЎвЂќР В Р’В»Р В РЎвЂР В Р’ВµР В Р вЂ¦Р РЋРІР‚С™Р РЋРЎвЂњ Р В РЎвЂР В РЎвЂ“Р РЋР вЂљР В РЎвЂўР В РЎвЂќР В Р’В°
                player.playNotifySound(ModSounds.SIREN_ALARM.get(), net.minecraft.sounds.SoundSource.MASTER, 1.0F, 1.0F);
            }
        }
    }
    // 2. Р В РІР‚СњР В РЎвЂўР В Р’В±Р В Р’В°Р В Р вЂ Р В Р’В»Р РЋР РЏР В Р’ВµР В РЎВ Р В РЎВР В Р’ВµР РЋРІР‚С™Р В РЎвЂўР В РўвЂ Р В РўвЂР В Р’В»Р РЋР РЏ Р В РЎВР В РЎвЂ“Р В Р вЂ¦Р В РЎвЂўР В Р вЂ Р В Р’ВµР В Р вЂ¦Р В Р вЂ¦Р В РЎвЂўР В РІвЂћвЂ“ Р В РЎвЂ”Р РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р’ВµР РЋР вЂљР В РЎвЂќР В РЎвЂ Р РЋР С“Р В РЎвЂР РЋР вЂљР В Р’ВµР В Р вЂ¦Р РЋРІР‚в„– (Р В Р вЂ Р РЋРІР‚в„–Р В Р’В·Р РЋРІР‚в„–Р В Р вЂ Р В Р’В°Р РЋРІР‚С™Р РЋР Р‰ Р В РЎвЂ”Р РЋР вЂљР В РЎвЂ Р В РЎвЂР В Р’В·Р В РЎВР В Р’ВµР В Р вЂ¦Р В Р’ВµР В Р вЂ¦Р В РЎвЂР В РЎвЂ Р РЋРІР‚С™Р В РЎвЂР В РЎвЂќР В Р’ВµР РЋРІР‚С™Р В РЎвЂўР В Р вЂ )
    public static void checkSirenManual(ServerLevel level, AASWorldData data) {
        if (!data.isGameStarted || !AASConfig.LOW_TICKETS_SIREN.get()) return;

        if (data.blueTickets <= 50 && data.blueTickets >= 0 && !data.playedBlueSiren) {
            playSirenForTeam(level, "Blue");
            data.playedBlueSiren = true;
            data.setDirty();
        }
        if (data.redTickets <= 50 && data.redTickets >= 0 && !data.playedRedSiren) {
            playSirenForTeam(level, "Red");
            data.playedRedSiren = true;
            data.setDirty();
        }
    }
    @SubscribeEvent
    public static void enforceMortarShellLimit(TickEvent.PlayerTickEvent event) {
        // Р В РІР‚в„ўР РЋРІР‚в„–Р В РЎвЂ”Р В РЎвЂўР В Р’В»Р В Р вЂ¦Р РЋР РЏР В Р’ВµР В РЎВ Р РЋРІР‚С™Р В РЎвЂўР В Р’В»Р РЋР Р‰Р В РЎвЂќР В РЎвЂў Р В Р вЂ¦Р В Р’В° Р РЋР С“Р В Р’ВµР РЋР вЂљР В Р вЂ Р В Р’ВµР РЋР вЂљР В Р’Вµ Р В РЎвЂ Р В Р вЂ  Р В РЎвЂќР В РЎвЂўР В Р вЂ¦Р РЋРІР‚В Р В Р’Вµ Р РЋРІР‚С™Р В РЎвЂР В РЎвЂќР В Р’В°
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;

        // Р В РЎСџР РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р’ВµР РЋР вЂљР РЋР РЏР В Р’ВµР В РЎВ Р РЋР вЂљР В Р’В°Р В Р’В· Р В Р вЂ  10 Р РЋРІР‚С™Р В РЎвЂР В РЎвЂќР В РЎвЂўР В Р вЂ  (Р В РЎвЂ”Р В РЎвЂўР В Р’В»Р РЋР С“Р В Р’ВµР В РЎвЂќР РЋРЎвЂњР В Р вЂ¦Р В РўвЂР РЋРІР‚в„–) Р В РўвЂР В Р’В»Р РЋР РЏ Р В РЎвЂўР В РЎвЂ”Р РЋРІР‚С™Р В РЎвЂР В РЎВР В РЎвЂР В Р’В·Р В Р’В°Р РЋРІР‚В Р В РЎвЂР В РЎвЂ
        if (event.player.tickCount % 10 != 0) return;

        Player player = event.player;

        // Р В РІР‚в„ў Р В РЎвЂќР РЋР вЂљР В Р’ВµР В Р’В°Р РЋРІР‚С™Р В РЎвЂР В Р вЂ Р В Р’Вµ Р В РЎвЂ Р РЋР вЂљР В Р’ВµР В Р’В¶Р В РЎвЂР В РЎВР В Р’Вµ Р В Р вЂ¦Р В Р’В°Р В Р’В±Р В Р’В»Р РЋР вЂ№Р В РўвЂР В Р’В°Р РЋРІР‚С™Р В Р’ВµР В Р’В»Р РЋР РЏ Р В РЎвЂўР В РЎвЂ“Р РЋР вЂљР В Р’В°Р В Р вЂ¦Р В РЎвЂР РЋРІР‚РЋР В Р’ВµР В Р вЂ¦Р В РЎвЂР В РІвЂћвЂ“ Р В Р вЂ¦Р В Р’ВµР РЋРІР‚С™
        if (player.isCreative() || player.isSpectator()) return;

        // Р В Р’ВР РЋРІР‚В°Р В Р’ВµР В РЎВ Р В РЎВР В РЎвЂўР РЋР вЂљР РЋРІР‚С™Р В РЎвЂР РЋР вЂљР В Р вЂ¦Р РЋРІР‚в„–Р В РІвЂћвЂ“ Р РЋР С“Р В Р вЂ¦Р В Р’В°Р РЋР вЂљР РЋР РЏР В РўвЂ. Р В РІР‚СћР РЋР С“Р В Р’В»Р В РЎвЂ Р В РЎВР В РЎвЂўР В РўвЂР В Р’В° Р В Р вЂ¦Р В Р’ВµР РЋРІР‚С™ Р Р†Р вЂљРІР‚Сњ Р В РЎвЂР РЋР С“Р В РЎвЂ”Р В РЎвЂўР В Р’В»Р РЋР Р‰Р В Р’В·Р РЋРЎвЂњР В Р’ВµР В РЎВ Р РЋР С“Р РЋРІР‚С™Р РЋР вЂљР В Р’ВµР В Р’В»Р РЋРІР‚в„– (Р В РЎвЂўР РЋРІР‚С™Р В Р’В»Р В Р’В°Р В РўвЂР В РЎвЂќР В Р’В°)
        Item mortarItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("superbwarfare", "mortar_shell"));
        if (mortarItem == null || mortarItem == net.minecraft.world.item.Items.AIR) {
            mortarItem = net.minecraft.world.item.Items.ARROW;
        }

        // Р В Р Р‹Р РЋРІР‚РЋР В РЎвЂР РЋРІР‚С™Р В Р’В°Р В Р’ВµР В РЎВ Р В РЎвЂўР В Р’В±Р РЋРІР‚В°Р В Р’ВµР В Р’Вµ Р В РЎвЂќР В РЎвЂўР В Р’В»Р В РЎвЂР РЋРІР‚РЋР В Р’ВµР РЋР С“Р РЋРІР‚С™Р В Р вЂ Р В РЎвЂў Р РЋР С“Р В Р вЂ¦Р В Р’В°Р РЋР вЂљР РЋР РЏР В РўвЂР В РЎвЂўР В Р вЂ  Р В Р вЂ  Р В РЎвЂР В Р вЂ¦Р В Р вЂ Р В Р’ВµР В Р вЂ¦Р РЋРІР‚С™Р В Р’В°Р РЋР вЂљР В Р’Вµ
        int totalCount = 0;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.getItem() == mortarItem) {
                totalCount += stack.getCount();
            }
        }

        // Р В РІР‚СћР РЋР С“Р В Р’В»Р В РЎвЂ Р В Р’В±Р В РЎвЂўР В Р’В»Р РЋР Р‰Р РЋРІвЂљВ¬Р В Р’Вµ 8 Р Р†Р вЂљРІР‚Сњ Р В Р’В·Р В Р’В°Р В Р’В±Р В РЎвЂР РЋР вЂљР В Р’В°Р В Р’ВµР В РЎВ Р В РЎвЂ Р В Р вЂ Р РЋРІР‚в„–Р В Р’В±Р РЋР вЂљР В Р’В°Р РЋР С“Р РЋРІР‚в„–Р В Р вЂ Р В Р’В°Р В Р’ВµР В РЎВ Р В РЎвЂР В Р’В·Р В Р’В»Р В РЎвЂР РЋРІвЂљВ¬Р В Р’ВµР В РЎвЂќ
        if (totalCount > 8) {
            int toRemove = totalCount - 8;
            int removedSoFar = 0;

            for (int i = 0; i < inv.getContainerSize(); i++) {
                if (removedSoFar >= toRemove) break;

                ItemStack stack = inv.getItem(i);
                if (stack.getItem() == mortarItem) {
                    int shrinkAmount = Math.min(stack.getCount(), toRemove - removedSoFar);
                    stack.shrink(shrinkAmount);
                    removedSoFar += shrinkAmount;

                    // Р В РІР‚в„ўР РЋРІР‚в„–Р В Р’В±Р РЋР вЂљР В Р’В°Р РЋР С“Р РЋРІР‚в„–Р В Р вЂ Р В Р’В°Р В Р’ВµР В РЎВ Р В Р’В»Р В РЎвЂР РЋРІвЂљВ¬Р В Р вЂ¦Р В Р’ВµР В Р’Вµ Р В Р вЂ¦Р В Р’В° Р В Р’В·Р В Р’ВµР В РЎВР В Р’В»Р РЋР вЂ№ Р В РЎвЂ”Р В Р’ВµР РЋР вЂљР В Р’ВµР В РўвЂ Р В РЎвЂР В РЎвЂ“Р РЋР вЂљР В РЎвЂўР В РЎвЂќР В РЎвЂўР В РЎВ
                    ItemStack dropped = new ItemStack(mortarItem, shrinkAmount);
                    player.drop(dropped, false, true);
                }
            }

            // Р В Р в‚¬Р В Р вЂ Р В Р’ВµР В РўвЂР В РЎвЂўР В РЎВР В Р’В»Р РЋР РЏР В Р’ВµР В РЎВ Р В РЎвЂР В РЎвЂ“Р РЋР вЂљР В РЎвЂўР В РЎвЂќР В Р’В°
            player.displayClientMessage(Component.literal("You can only carry up to 8 Mortar Shells!").withStyle(ChatFormatting.RED), true);
        }
    }
    private static void checkCmdVoteStatus(ServerLevel level, MinecraftServer server, AASWorldData data, String team) {
        boolean isBlue = team.equals("BLUE");

        // Р В РІР‚в„ўР РЋРІР‚в„–Р В Р’В±Р В РЎвЂР РЋР вЂљР В Р’В°Р В Р’ВµР В РЎВ Р В Р вЂ¦Р РЋРЎвЂњР В Р’В¶Р В Р вЂ¦Р РЋРІР‚в„–Р В Р’Вµ Р В РЎвЂ”Р В РЎвЂўР В Р’В»Р РЋР РЏ Р В Р вЂ  Р В Р’В·Р В Р’В°Р В Р вЂ Р В РЎвЂР РЋР С“Р В РЎвЂР В РЎВР В РЎвЂўР РЋР С“Р РЋРІР‚С™Р В РЎвЂ Р В РЎвЂўР РЋРІР‚С™ Р В РЎвЂќР В РЎвЂўР В РЎВР В Р’В°Р В Р вЂ¦Р В РўвЂР РЋРІР‚в„–
        boolean active = isBlue ? data.blueCmdVoteActive : data.redCmdVoteActive;
        String candidateName = isBlue ? data.blueCmdCandidateName : data.redCmdCandidateName;
        int candidateId = isBlue ? data.blueCmdCandidateId : data.redCmdCandidateId;
        int timer = isBlue ? data.blueCmdVoteTimer : data.redCmdVoteTimer;
        Map<UUID, Boolean> votesMap = isBlue ? data.blueCmdVotes : data.redCmdVotes;

        if (!active) return;

        // 1. Р В Р Р‹Р РЋРІР‚РЋР В РЎвЂР РЋРІР‚С™Р В Р’В°Р В Р’ВµР В РЎВ Р В Р’В¶Р В РЎвЂР В Р вЂ Р РЋРІР‚в„–Р РЋРІР‚В¦ SL Р РЋР РЉР РЋРІР‚С™Р В РЎвЂўР В РІвЂћвЂ“ Р В РЎвЂќР В РЎвЂўР В РЎВР В Р’В°Р В Р вЂ¦Р В РўвЂР РЋРІР‚в„– (Р В РЎвЂќР РЋР вЂљР В РЎвЂўР В РЎВР В Р’Вµ Р В РЎвЂќР В Р’В°Р В Р вЂ¦Р В РўвЂР В РЎвЂР В РўвЂР В Р’В°Р РЋРІР‚С™Р В Р’В°)
        List<ServerPlayer> otherSLs = level.players().stream()
                .filter(p -> p.getTeam() != null && p.getTeam().getName().equalsIgnoreCase(team))
                .filter(p -> p.getPersistentData().getBoolean("AAS_IsSquadLeader"))
                .filter(p -> !p.getScoreboardName().equals(candidateName))
                .toList();

        int totalVoters = otherSLs.size();
        int needed = (totalVoters > 0) ? (int) Math.ceil(totalVoters / 2.0) : 1;

        // 2. Р В Р Р‹Р РЋРІР‚РЋР В РЎвЂР РЋРІР‚С™Р В Р’В°Р В Р’ВµР В РЎВ Р В РЎвЂ“Р В РЎвЂўР В Р’В»Р В РЎвЂўР РЋР С“Р В Р’В°
        long yes = votesMap.entrySet().stream()
                .filter(e -> server.getPlayerList().getPlayer(e.getKey()) != null)
                .filter(Map.Entry::getValue).count();

        long no = votesMap.entrySet().stream()
                .filter(e -> server.getPlayerList().getPlayer(e.getKey()) != null)
                .filter(e -> !e.getValue()).count();

        boolean win = (totalVoters > 0 && yes >= needed);
        boolean fail = (totalVoters > 0 && no >= totalVoters) || (timer <= 0);

        if (win || fail) {
            if (win) {
                if (isBlue) data.blueCMDId = candidateId;
                else data.redCMDId = candidateId;
                broadcastTeamMessage(level, team, team + " COMMANDER ASSIGNED: " + candidateName, ChatFormatting.GREEN);
            } else {
                broadcastTeamMessage(level, team, team + " CMD Application rejected or timed out.", ChatFormatting.RED);
            }

            // Р В РЎвЂєР РЋРІР‚РЋР В РЎвЂР РЋР С“Р РЋРІР‚С™Р В РЎвЂќР В Р’В°
            if (isBlue) {
                data.blueCmdVoteActive = false;
                data.blueCmdVotes.clear();
            } else {
                data.redCmdVoteActive = false;
                data.redCmdVotes.clear();
            }
            data.setDirty();
            PacketHandler.sendToAllClients(level, data);
        }
    }
    private static void broadcastTeamMessage(ServerLevel level, String team, String text, ChatFormatting color) {
        Component message = Component.literal(text).withStyle(color);
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.getTeam() != null && player.getTeam().getName().equalsIgnoreCase(team)) {
                player.sendSystemMessage(message);
            }
        }
    }
    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        // Р В РІР‚в„ўР РЋРІР‚в„–Р В РЎвЂ”Р В РЎвЂўР В Р’В»Р В Р вЂ¦Р РЋР РЏР В Р’ВµР В РЎВ Р В Р’В»Р В РЎвЂўР В РЎвЂ“Р В РЎвЂР В РЎвЂќР РЋРЎвЂњ Р РЋРІР‚С™Р В РЎвЂўР В Р’В»Р РЋР Р‰Р В РЎвЂќР В РЎвЂў Р В Р вЂ¦Р В Р’В° Р РЋР С“Р В Р’ВµР РЋР вЂљР В Р вЂ Р В Р’ВµР РЋР вЂљР В Р’Вµ
        if (event.getEntity().level().isClientSide) return;

        Entity target = event.getEntity();
        Entity attacker = event.getSource().getEntity();

        // Р В РЎСџР РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р’ВµР РЋР вЂљР РЋР РЏР В Р’ВµР В РЎВ, Р РЋРІР‚РЋР РЋРІР‚С™Р В РЎвЂў Р В РЎвЂўР В Р’В±Р В Р’В° Р РЋРЎвЂњР РЋРІР‚РЋР В Р’В°Р РЋР С“Р РЋРІР‚С™Р В Р вЂ¦Р В РЎвЂР В РЎвЂќР В Р’В° - Р В РЎвЂР В РЎвЂ“Р РЋР вЂљР В РЎвЂўР В РЎвЂќР В РЎвЂ, Р В РЎвЂ Р РЋР РЉР РЋРІР‚С™Р В РЎвЂў Р В Р вЂ¦Р В Р’Вµ Р РЋРЎвЂњР РЋР вЂљР В РЎвЂўР В Р вЂ¦ Р РЋР С“Р В Р’В°Р В РЎВР В РЎвЂўР В РЎВР РЋРЎвЂњ Р РЋР С“Р В Р’ВµР В Р’В±Р В Р’Вµ (Р В РЎвЂўР РЋРІР‚С™ Р В РЎвЂ”Р В Р’В°Р В РўвЂР В Р’ВµР В Р вЂ¦Р В РЎвЂР РЋР РЏ, Р В РЎвЂўР В РЎвЂ“Р В Р вЂ¦Р РЋР РЏ Р В РЎвЂ Р РЋРІР‚С™Р В РўвЂ)
        if (target instanceof Player && attacker instanceof Player && target != attacker) {
            ServerLevel serverLevel = (ServerLevel) target.level();
            AASWorldData data = AASWorldData.get(serverLevel);

            // Р В РІР‚СћР РЋР С“Р В Р’В»Р В РЎвЂ PvP Р В РЎвЂўР РЋРІР‚С™Р В РЎвЂќР В Р’В»Р РЋР вЂ№Р РЋРІР‚РЋР В Р’ВµР В Р вЂ¦Р В РЎвЂў Р В Р вЂ  Р РЋР РЉР РЋРІР‚С™Р В РЎвЂўР В РЎВ Р В РЎВР В РЎвЂР РЋР вЂљР В Р’Вµ - Р В РЎвЂўР РЋРІР‚С™Р В РЎВР В Р’ВµР В Р вЂ¦Р РЋР РЏР В Р’ВµР В РЎВ Р РЋРЎвЂњР РЋР вЂљР В РЎвЂўР В Р вЂ¦
            if (!data.pvpEnabled) {
                event.setCanceled(true);
            }
        }
    }

}