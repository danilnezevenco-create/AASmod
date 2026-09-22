package com.example.aas.block;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import java.util.List;

public class MainSupplyBlockEntity extends BlockEntity {

    private int checkTimer = 0;

    public MainSupplyBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.MAIN_SUPPLY_BE.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, MainSupplyBlockEntity entity) {
        if (level.isClientSide) return;

        entity.checkTimer++;
        if (entity.checkTimer < 20) return; // Проверка раз в секунду
        entity.checkTimer = 0;
        // === НОВОЕ: ЛЕЧЕНИЕ ИГРОКОВ ===
        if (com.example.aas.config.AASConfig.MAIN_SUPPLY_HEALING.get()) {
            int healRad = com.example.aas.config.AASConfig.MAIN_SUPPLY_HEAL_RADIUS.get();
            AABB healArea = new AABB(pos).inflate(healRad);
            List<Player> players = level.getEntitiesOfClass(Player.class, healArea);
            for (Player p : players) {
                if (p.isAlive() && !p.isSpectator()) {
                    // Выдаем регенерацию на 3 секунды (60 тиков) каждую секунду
                    p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 60, 0, false, false, true));
                }
            }
        }
        // ==============================

        AABB searchArea = new AABB(pos).inflate(15);
        List<Entity> nearbyEntities = level.getEntitiesOfClass(Entity.class, searchArea);

        // === НОВОЕ: СОРТИРОВКА ПО ПРИОРИТЕТУ ===
        // Сначала те, у кого есть пассажиры-игроки, затем остальные
        nearbyEntities.sort((e1, e2) -> {
            boolean p1HasPlayer = e1.getPassengers().stream().anyMatch(p -> p instanceof Player);
            boolean p2HasPlayer = e2.getPassengers().stream().anyMatch(p -> p instanceof Player);
            if (p1HasPlayer && !p2HasPlayer) return -1;
            if (!p1HasPlayer && p2HasPlayer) return 1;
            return 0;
        });

        long currentTime = level.getGameTime();

        for (Entity vehicle : nearbyEntities) {

            // Строка статуса для Action Bar (чтобы объединить таймеры)
            StringBuilder statusMessage = new StringBuilder();
            boolean showActionBar = false;

            // Сброс таймеров при долгом отсутствии (если не обновлялись более 2 сек, значит выезжали)
            long lastSeenTime = vehicle.getPersistentData().getLong("AAS_LastSupplyTime");
            if (vehicle.getPersistentData().contains("AAS_VehicleMaxMats")) {
                int maxMats = vehicle.getPersistentData().getInt("AAS_VehicleMaxMats");
                vehicle.getPersistentData().putInt("AAS_VehicleMats", maxMats);
            }
            if (currentTime - lastSeenTime > 200) { // > 20 секунд отсутствия
                vehicle.getPersistentData().putInt("AAS_RepairTimer", 0);
                vehicle.getPersistentData().putInt("AAS_TruckReloadTimer", 0);
            }
            vehicle.getPersistentData().putLong("AAS_LastSupplyTime", currentTime);


            // === ЛОГИКА 1: SUPPLY TRUCK (ПОПОЛНЕНИЕ ЯЩИКОВ) ===
            // ЭТА ЛОГИКА ТЕПЕРЬ НЕ ЗАВИСИТ ОТ КУЛДАУНА
            if (vehicle.getPersistentData().getBoolean("AAS_IsSupplyTruck")) {
                int currentAmmo = vehicle.getPersistentData().getInt("AAS_SupplyAmmo");
                int maxCrates = com.example.aas.config.AASConfig.SUPPLY_TRUCK_CRATES.get();

                if (currentAmmo < maxCrates) {
                    int supplyTimer = vehicle.getPersistentData().getInt("AAS_TruckReloadTimer");
                    supplyTimer++;

                    if (supplyTimer >= 15) { // 15 секунд загрузки
                        vehicle.getPersistentData().putInt("AAS_SupplyAmmo", currentAmmo + 1);
                        vehicle.getPersistentData().putInt("AAS_TruckReloadTimer", 0);

                        // Уведомление в ЧАТ (чтобы было выше и не перекрывалось)
                        sendChatMessageToPassengers(vehicle, Component.translatable("aas.msg.crate_loaded", (currentAmmo + 1), maxCrates).getString(), ChatFormatting.GOLD);
                        spawnEffects(level, vehicle);
                    } else {
                        vehicle.getPersistentData().putInt("AAS_TruckReloadTimer", supplyTimer);
                        // Добавляем в общий статус бар
                        statusMessage.append(ChatFormatting.YELLOW).append(Component.translatable("aas.msg.loading_crate", 15 - supplyTimer).getString()).append(" ");
                        showActionBar = true;
                    }
                } else {
                    // Если фулл ящиков, сбрасываем таймер
                    vehicle.getPersistentData().putInt("AAS_TruckReloadTimer", 0);
                }
            }


            // === ЛОГИКА 2: РЕМОНТ И ПЕРЕЗАРЯДКА (REARM) ===
            // ЭТА ЛОГИКА ИМЕЕТ КУЛДАУН 5 МИНУТ
            if (vehicle.getPersistentData().contains("AAS_SpawnerPos")) {

                long nextSupplyTime = vehicle.getPersistentData().getLong("AAS_NextSupplyTime");

                if (currentTime < nextSupplyTime) {
                    // ЕСЛИ КУЛДАУН АКТИВЕН
                    long secondsLeft = (nextSupplyTime - currentTime) / 20;
                    vehicle.getPersistentData().putInt("AAS_RepairTimer", 0); // Сбрасываем прогресс ремонта, если КД

                    // Добавляем инфо о КД в статус бар
                    statusMessage.append(ChatFormatting.RED).append(Component.translatable("aas.msg.rearm_cooldown", secondsLeft).getString());
                    showActionBar = true;
                }
                else {
                    // ЕСЛИ КУЛДАУНА НЕТ -> НАЧИНАЕМ РЕМОНТ
                    int repairTimer = vehicle.getPersistentData().getInt("AAS_RepairTimer");
                    repairTimer++; // +1 секунда

                    if (repairTimer >= 30) { // 30 секунд процесс
                        // 1. Лечим
                        if (vehicle instanceof LivingEntity living) {
                            living.setHealth(living.getMaxHealth());
                        }

                        // 2. Перезаряжаем
                        java.util.concurrent.atomic.AtomicBoolean rearmSuccess = new java.util.concurrent.atomic.AtomicBoolean(false);

                        vehicle.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(vehInv -> {
                            // НАДЕЖНАЯ ОЧИСТКА ИНВЕНТАРЯ
                            if (vehInv instanceof IItemHandlerModifiable modifiable) {
                                for (int i = 0; i < vehInv.getSlots(); i++) {
                                    modifiable.setStackInSlot(i, ItemStack.EMPTY);
                                }
                            } else {
                                for (int i = 0; i < vehInv.getSlots(); i++) vehInv.extractItem(i, 64, false);
                            }

                            // НОВЫЙ МЕТОД: Грузим из NBT машины (работает даже если чанк выгружен)
                            if (vehicle.getPersistentData().contains("AAS_InitialLoadout")) {
                                net.minecraft.nbt.ListTag loadoutTag = vehicle.getPersistentData().getList("AAS_InitialLoadout", 10);
                                for (int i = 0; i < loadoutTag.size(); i++) {
                                    net.minecraft.nbt.CompoundTag itemTag = loadoutTag.getCompound(i);
                                    int slot = itemTag.getByte("Slot") & 255;
                                    if (slot < vehInv.getSlots()) {
                                        insertItem(vehInv, slot, ItemStack.of(itemTag));
                                    }
                                }
                                rearmSuccess.set(true);
                            }
                            // СТАРЫЙ МЕТОД: Если машина была заспавнена до фикса (Резерв)
                            else {
                                long spawnerPosLong = vehicle.getPersistentData().getLong("AAS_SpawnerPos");
                                BlockPos spawnerPos = BlockPos.of(spawnerPosLong);

                                if (level.isLoaded(spawnerPos)) {
                                    BlockEntity be = level.getBlockEntity(spawnerPos);
                                    if (be instanceof VehicleSpawnerBlockEntity spawner) {
                                        for (int i = 0; i < 32; i++) {
                                            if (i >= vehInv.getSlots()) break;
                                            ItemStack sourceStack = spawner.inventory.getStackInSlot(i + 1);
                                            if (!sourceStack.isEmpty()) {
                                                insertItem(vehInv, i, sourceStack.copy());
                                            }
                                        }
                                        rearmSuccess.set(true);
                                    }
                                }
                            }

                            // Батарейка
                            if (rearmSuccess.get()) {
                                Item batteryItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("superbwarfare", "large_battery"));
                                if (batteryItem != null) {
                                    for (int i = 0; i < vehInv.getSlots(); i++) {
                                        if (vehInv.getStackInSlot(i).isEmpty()) {
                                            insertItem(vehInv, i, new ItemStack(batteryItem));
                                            break;
                                        }
                                    }
                                }
                            }
                        });

                        vehicle.getPersistentData().putInt("AAS_RepairTimer", 0);

                        // ЕСЛИ ПЕРЕЗАРЯДКА РЕАЛЬНО СЛУЧИЛАСЬ
                        if (rearmSuccess.get()) {
                            // Исправлено: 600 тиков = 30 секунд
                            vehicle.getPersistentData().putLong("AAS_NextSupplyTime", currentTime + 600);
                            sendChatMessageToPassengers(vehicle, Component.translatable("aas.msg.vehicle_rearmed").getString(), ChatFormatting.GREEN);
                            spawnEffects(level, vehicle);
                        } else {
                            sendChatMessageToPassengers(vehicle, Component.translatable("aas.msg.rearm_error_chunk").getString(), ChatFormatting.RED);
                        }
                    } else {
                        vehicle.getPersistentData().putInt("AAS_RepairTimer", repairTimer);
                        statusMessage.append(ChatFormatting.AQUA).append(Component.translatable("aas.msg.rearming", 30 - repairTimer).getString());
                        showActionBar = true;
                    }
                }
            }

            // ОТПРАВЛЯЕМ ОБЪЕДИНЕННЫЙ СТАТУС В ACTION BAR (над хотбаром)
            // Это предотвращает мигание текста
            if (showActionBar && statusMessage.length() > 0) {
                for (Entity passenger : vehicle.getPassengers()) {
                    if (passenger instanceof Player player) {
                        player.displayClientMessage(Component.literal(statusMessage.toString()), true);
                    }
                }
            }
        }
    }

    private static void insertItem(IItemHandler handler, int slot, ItemStack stack) {
        if (handler instanceof IItemHandlerModifiable modifiable) {
            modifiable.setStackInSlot(slot, stack);
        } else {
            handler.insertItem(slot, stack, false);
        }
    }

    // Сообщение в Action Bar (исчезает, обновляется)
    private static void sendActionBarToPassengers(Entity vehicle, String msg, ChatFormatting color) {
        for (Entity passenger : vehicle.getPassengers()) {
            if (passenger instanceof Player player) {
                player.displayClientMessage(Component.literal(msg).withStyle(color), true);
            }
        }
    }

    // Сообщение в ЧАТ (сохраняется в истории, находится выше Action Bar)
    private static void sendChatMessageToPassengers(Entity vehicle, String msg, ChatFormatting color) {
        for (Entity passenger : vehicle.getPassengers()) {
            if (passenger instanceof Player player) {
                // false = System Chat Message
                player.displayClientMessage(Component.literal(msg).withStyle(color), false);
            }
        }
    }

    private static void spawnEffects(Level level, Entity vehicle) {
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                    vehicle.getX(), vehicle.getY() + 1.5, vehicle.getZ(),
                    10, 1.0, 1.0, 1.0, 0.1);
        }
    }
}