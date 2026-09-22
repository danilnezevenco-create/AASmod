package com.example.aas.block;

import com.example.aas.item.SupplyTruckMarkerItem;
import com.example.aas.item.VehicleMarkerItem;
import com.example.aas.menu.VehicleSpawnerMenu;
import com.example.aas.world.AASWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
import com.example.aas.network.PacketHandler;
import java.util.UUID;

public class VehicleSpawnerBlockEntity extends BlockEntity implements MenuProvider {

    public final ItemStackHandler inventory = new ItemStackHandler(33) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };

    public float vehicleYaw = 0;
    public int respawnTimeSettings = 60;
    public int initialTimeSettings = 60;
    public String vehicleIdString = "";

    public long targetSpawnTick = 0;
    public boolean hasSpawnedOnce = false;
    private UUID lastVehicleUUID = null;
    private int loadTimer = 60;

    // === АВТО-ВОЗВРАТ ТЕХНИКИ ===
    // Если техника пуста (нет игроков) дольше autoReturnTimeSettings секунд и находится
    // за пределами мейн-зоны своей команды — она телепортируется обратно на спавн,
    // либо (если autoReturnDestroy == true) уничтожается. Сама проверка выполняется
    // глобально в GameLogicEvents#handleVehicleAutoReturn, а не здесь, т.к. этот тик
    // блок-энтити не выполняется в непрогруженных чанках.
    public boolean autoReturnEnabled = false;
    public int autoReturnTimeSettings = 60;
    public boolean autoReturnDestroy = false;

    // Один раз на первом тике после появления блока в мире сканируем ландшафт
    // вокруг и подбираем камуфляж. После этого флаг сохраняется в NBT и повторного
    // сканирования больше никогда не будет (даже после выгрузки/загрузки чанка).
    private boolean camoScanned = false;

    public VehicleSpawnerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.VEHICLE_SPAWNER_BE.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        this.loadTimer = 60;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, VehicleSpawnerBlockEntity be) {
        if (level.isClientSide) return;

        // === КАМУФЛЯЖ: единоразовое сканирование блоков вокруг ===
        if (!be.camoScanned) {
            be.camoScanned = true;
            VehicleSpawnerBlock.CamoType camo = VehicleSpawnerBlock.computeCamoType(level, pos);
            if (state.getValue(VehicleSpawnerBlock.CAMO) != camo) {
                level.setBlock(pos, state.setValue(VehicleSpawnerBlock.CAMO, camo), 3);
            }
            be.setChanged();
        }

        if (be.loadTimer > 0) {
            be.loadTimer--;
            return;
        }

        AASWorldData data = AASWorldData.get((ServerLevel) level);

        // Если игра не начата — сбрасываем всё
        if (!data.isGameStarted) {
            if (be.hasSpawnedOnce || be.targetSpawnTick != 0 || be.lastVehicleUUID != null) {
                be.hasSpawnedOnce = false;
                be.targetSpawnTick = 0;
                be.lastVehicleUUID = null;
                be.setChanged();
                be.syncToClient();
            }
            return;
        }

        long currentTick = level.getGameTime();
        // --- ДОБАВИТЬ ЭТОТ БЛОК ---
        String spawnerTeam = "NEUTRAL";
        ItemStack modifier = be.inventory.getStackInSlot(0);
        if (modifier.getItem() instanceof VehicleMarkerItem vm) spawnerTeam = vm.getTeam();
        if (modifier.getItem() instanceof SupplyTruckMarkerItem stm) spawnerTeam = stm.getTeam();

        // Замораживаем спавнер (таймер не начнет отсчет), пока идет подготовка в Invasion
        if (data.gameMode.equalsIgnoreCase("INVASION") && data.invasionPrepTicks > 0) {
            if (!spawnerTeam.equals(data.invasionDefender) && !spawnerTeam.equals("NEUTRAL")) {
                return; // Прерываем тик! Таймер инициализируется только когда закончится prepTicks
            }
        }
        // 1. Проверяем, жива ли техника этого спавнера в мире
        boolean vehicleExistsGlobally = data.markedVehicles.stream()
                .anyMatch(v -> v.spawnerPos != null && v.spawnerPos.equals(pos));

        if (vehicleExistsGlobally) {
            // Техника жива — таймер должен быть сброшен в 0 (не отображаться)
            if (be.targetSpawnTick != 0) {
                be.targetSpawnTick = 0;
                be.setChanged();
                be.syncToClient();
            }
            return;
        }

        // 2. Если техники нет, но UUID еще остался — значит её только что уничтожили
        if (be.lastVehicleUUID != null) {
            be.lastVehicleUUID = null;
            int delay = be.respawnTimeSettings * 20; // Переводим секунды в тики
            be.targetSpawnTick = currentTick + delay;
            be.setChanged();
            be.syncToClient();
        }

        // 3. Инициализация самого первого спавна
        if (be.targetSpawnTick == 0 && !be.hasSpawnedOnce) {
            int delay = be.initialTimeSettings * 20;
            be.targetSpawnTick = currentTick + delay;
            be.setChanged();
            be.syncToClient();
        }
        // 4. САМ СПАВН ТЕХНИКИ (Этого куска у вас сейчас нет)
        if (be.targetSpawnTick != 0 && currentTick >= be.targetSpawnTick) {
            be.spawnVehicle();
            be.targetSpawnTick = 0; // Сбрасываем после спавна
            be.setChanged();
            be.syncToClient();
        }
    }

    // PATH: src\main\java\com\example\aas\block\VehicleSpawnerBlockEntity.java

    private void spawnVehicle() {
        if (level == null || level.isClientSide) return;
        if (vehicleIdString == null || vehicleIdString.trim().isEmpty()) return;

        ResourceLocation resLoc = ResourceLocation.tryParse(vehicleIdString);
        if (resLoc == null) return;

        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(resLoc);
        if (type == null) return;

        Entity entity = type.create(level);
        if (entity != null) {
            entity.setPos(worldPosition.getX() + 0.5, worldPosition.getY() + 2.5, worldPosition.getZ() + 0.5);
            entity.getPersistentData().putLong("AAS_SpawnerPos", worldPosition.asLong());
            entity.setYRot(this.vehicleYaw);
            entity.setYHeadRot(this.vehicleYaw);

            String vTeam = "NEUTRAL";
            String vType = "DEFAULT";
            int penalty = 0;
            int maxMats = 0;

            ItemStack modifierStack = inventory.getStackInSlot(0);
            if (!modifierStack.isEmpty()) {
                if (modifierStack.getItem() instanceof VehicleMarkerItem marker) {
                    vTeam = marker.getTeam();
                    vType = marker.getType();
                    penalty = marker.getPenalty();
                    maxMats = marker.getMaxMats();
                }
                else if (modifierStack.getItem() instanceof SupplyTruckMarkerItem supply) {
                    vTeam = supply.getTeam();
                    vType = supply.getVehicleType();
                    penalty = supply.getPenalty();
                    maxMats = supply.getMaxMats();

                    // ГАРАНТИРУЕМ, что тег ставится сразу при спавне
                    entity.getPersistentData().putBoolean("AAS_IsSupplyTruck", true);
                    // Берем максимальное число из конфига
                    int maxCrates = com.example.aas.config.AASConfig.SUPPLY_TRUCK_CRATES.get();
                    entity.getPersistentData().putInt("AAS_SupplyAmmo", maxCrates);
                }
            }

            // === ВАЖНО: ВСЕГДА ВЕШАЕМ ТЕГ КОМАНДЫ (Даже если это NEUTRAL) ===
            entity.getPersistentData().putString("AAS_VehicleTeam", vTeam);
            entity.getPersistentData().putString("AAS_VehicleType", vType);
            entity.getPersistentData().putInt("AAS_TicketPenalty", penalty);

            if (maxMats > 0) {
                entity.getPersistentData().putInt("AAS_VehicleMaxMats", maxMats);
                entity.getPersistentData().putInt("AAS_VehicleMats", maxMats);
            }

            net.minecraft.nbt.ListTag loadoutTag = new net.minecraft.nbt.ListTag();
            for (int i = 0; i < 32; i++) {
                ItemStack contentStack = inventory.getStackInSlot(i + 1);
                if (!contentStack.isEmpty()) {
                    net.minecraft.nbt.CompoundTag itemTag = new net.minecraft.nbt.CompoundTag();
                    itemTag.putByte("Slot", (byte) i);
                    contentStack.save(itemTag);
                    loadoutTag.add(itemTag);
                }
            }
            entity.getPersistentData().put("AAS_InitialLoadout", loadoutTag);

            AASWorldData worldData = AASWorldData.get((ServerLevel) level);
            worldData.markedVehicles.removeIf(v -> v.spawnerPos != null && v.spawnerPos.equals(this.worldPosition));

            // === Передаём настройки авто-возврата в запись техники ===
            worldData.markedVehicles.add(new AASWorldData.VehicleRecord(
                    entity.getUUID(),
                    vTeam,
                    vType,
                    entity.getX(),
                    entity.getY(),
                    entity.getZ(),
                    entity.getYRot(),
                    this.worldPosition,
                    this.autoReturnEnabled,
                    this.autoReturnTimeSettings,
                    this.autoReturnDestroy
            ));
            worldData.setDirty();
            PacketHandler.sendToAllClients((ServerLevel)level, worldData);

            entity.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
                for (int i = 0; i < 32; i++) {
                    ItemStack contentStack = inventory.getStackInSlot(i + 1);
                    if (!contentStack.isEmpty()) {
                        if (handler instanceof net.minecraftforge.items.IItemHandlerModifiable modifiable) {
                            modifiable.setStackInSlot(i, contentStack.copy());
                        }
                    }
                }
            });

            if (entity instanceof LivingEntity living) {
                living.setHealth(living.getMaxHealth());
            }

            // === 5 СЕКУНД ИММУНИТЕТА К МЕЙН-ЗОНЕ ===
            entity.getPersistentData().putLong("AAS_SpawnGraceTick", level.getGameTime());

            level.addFreshEntity(entity);
            this.lastVehicleUUID = entity.getUUID();
            this.hasSpawnedOnce = true;
            this.setChanged();
        }
    }
    private void insertItemIntoSlot(IItemHandler handler, int slot, ItemStack stack) {
        if (slot < handler.getSlots()) {
            if (handler instanceof net.minecraftforge.items.IItemHandlerModifiable modifiable) {
                modifiable.setStackInSlot(slot, stack);
            } else {
                handler.insertItem(slot, stack, false);
            }
        }
    }

    public void syncToClient() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Inventory")) inventory.deserializeNBT(tag.getCompound("Inventory"));
        if (inventory.getSlots() < 33) inventory.setSize(33);
        respawnTimeSettings = tag.getInt("RespawnTime");
        initialTimeSettings = tag.getInt("InitialTime");
        targetSpawnTick = tag.getLong("TargetSpawnTick");
        hasSpawnedOnce = tag.getBoolean("HasSpawnedOnce");
        vehicleIdString = tag.getString("VehicleID");
        vehicleYaw = tag.getFloat("VehicleYaw");
        camoScanned = tag.getBoolean("CamoScanned");
        if (tag.hasUUID("LastVehicle")) lastVehicleUUID = tag.getUUID("LastVehicle");

        // === АВТО-ВОЗВРАТ ===
        autoReturnEnabled = tag.getBoolean("AutoReturnEnabled");
        autoReturnTimeSettings = tag.contains("AutoReturnTime") ? tag.getInt("AutoReturnTime") : 60;
        autoReturnDestroy = tag.getBoolean("AutoReturnDestroy");
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Inventory", inventory.serializeNBT());
        tag.putInt("RespawnTime", respawnTimeSettings);
        tag.putInt("InitialTime", initialTimeSettings);
        tag.putLong("TargetSpawnTick", targetSpawnTick);
        tag.putBoolean("HasSpawnedOnce", hasSpawnedOnce);
        tag.putString("VehicleID", vehicleIdString);
        tag.putFloat("VehicleYaw", vehicleYaw);
        tag.putBoolean("CamoScanned", camoScanned);
        if (lastVehicleUUID != null) tag.putUUID("LastVehicle", lastVehicleUUID);

        // === АВТО-ВОЗВРАТ ===
        tag.putBoolean("AutoReturnEnabled", autoReturnEnabled);
        tag.putInt("AutoReturnTime", autoReturnTimeSettings);
        tag.putBoolean("AutoReturnDestroy", autoReturnDestroy);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public Component getDisplayName() {
        return Component.literal("Vehicle Spawner Config");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInv, Player player) {
        return new VehicleSpawnerMenu(id, playerInv, this);
    }
}