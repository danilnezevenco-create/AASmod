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

    public long spawnTimestamp = 0;
    public boolean hasSpawnedOnce = false;
    private UUID lastVehicleUUID = null;
    private int loadTimer = 60;

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

        if (be.loadTimer > 0) {
            be.loadTimer--;
            return;
        }

        AASWorldData data = AASWorldData.get((ServerLevel) level);

        if (!data.isGameStarted) {
            if (be.hasSpawnedOnce || be.spawnTimestamp != 0 || be.lastVehicleUUID != null) {
                be.hasSpawnedOnce = false;
                be.spawnTimestamp = 0;
                be.lastVehicleUUID = null;
                be.setChanged();
                be.syncToClient();
            }
            return;
        }

        long currentTime = System.currentTimeMillis();

        // === ГЛОБАЛЬНАЯ ПРОВЕРКА (Защита от дубликатов при выгрузке чанков) ===
        // Ищем в "бухгалтерии" мира запись о технике, которая привязана именно к этому спавнеру
        boolean vehicleExistsGlobally = data.markedVehicles.stream()
                .anyMatch(v -> v.spawnerPos != null && v.spawnerPos.equals(pos));

        if (vehicleExistsGlobally) {
            // Техника где-то жива (даже если чанк с ней выгружен).
            // Останавливаем таймер и ничего не спавним.
            if (be.spawnTimestamp != 0) {
                be.spawnTimestamp = 0;
                be.setChanged();
                be.syncToClient();
            }
            return;
        }

        // Если мы здесь, значит в глобальном списке техники этого спавнера НЕТ (она уничтожена)
        if (be.lastVehicleUUID != null) {
            // Техника была, но исчезла из списка -> запускаем таймер респавна
            be.lastVehicleUUID = null;
            be.spawnTimestamp = currentTime + (be.respawnTimeSettings * 1000L);
            be.setChanged();
            be.syncToClient();
        }

        // Логика работы таймера
        if (be.spawnTimestamp == 0) {
            // Инициализируем таймер в первый раз (или после уничтожения)
            int delaySeconds = be.hasSpawnedOnce ? be.respawnTimeSettings : be.initialTimeSettings;
            be.spawnTimestamp = currentTime + (delaySeconds * 1000L);
            be.setChanged();
            be.syncToClient();
        }

        // Проверка завершения отсчета
        if (currentTime >= be.spawnTimestamp) {
            be.spawnVehicle();
            be.spawnTimestamp = 0;
            be.setChanged();
            be.syncToClient();
        }
    }

    private void spawnVehicle() {
        if (level == null || level.isClientSide) return;
        if (vehicleIdString == null || vehicleIdString.trim().isEmpty()) return;

        ResourceLocation resLoc = ResourceLocation.tryParse(vehicleIdString);
        if (resLoc == null) return;

        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(resLoc);
        if (type == null) return;

        Entity entity = type.create(level);
        if (entity != null) {
            entity.setPos(worldPosition.getX() + 0.5, worldPosition.getY() + 1.5, worldPosition.getZ() + 0.5);
            entity.getPersistentData().putLong("AAS_SpawnerPos", worldPosition.asLong());
            entity.setYRot(this.vehicleYaw);
            entity.setYHeadRot(this.vehicleYaw);

            String vTeam = "NEUTRAL";
            String vType = "DEFAULT";
            int penalty = 0;

            ItemStack modifierStack = inventory.getStackInSlot(0);
            if (!modifierStack.isEmpty()) {
                if (modifierStack.getItem() instanceof VehicleMarkerItem marker) {
                    vTeam = marker.getTeam();
                    vType = marker.getType();
                    penalty = marker.getPenalty();
                    entity.getPersistentData().putString("AAS_VehicleTeam", vTeam);
                    entity.getPersistentData().putString("AAS_VehicleType", vType);
                    entity.getPersistentData().putInt("AAS_TicketPenalty", penalty);
                }
                else if (modifierStack.getItem() instanceof SupplyTruckMarkerItem supply) {
                    vTeam = supply.getTeam();
                    vType = supply.getVehicleType();
                    penalty = supply.getPenalty();
                    entity.getPersistentData().putBoolean("AAS_IsSupplyTruck", true);
                    entity.getPersistentData().putInt("AAS_SupplyAmmo", 2);
                    entity.getPersistentData().putString("AAS_VehicleTeam", vTeam);
                    entity.getPersistentData().putString("AAS_VehicleType", vType);
                    entity.getPersistentData().putInt("AAS_TicketPenalty", penalty);
                }
            }

            // === РЕГИСТРАЦИЯ ТЕХНИКИ С ПРИВЯЗКОЙ К ПОЗИЦИИ БЛОКА ===
            AASWorldData worldData = AASWorldData.get((ServerLevel) level);

            // На всякий случай чистим старые записи этого спавнера
            worldData.markedVehicles.removeIf(v -> v.spawnerPos != null && v.spawnerPos.equals(this.worldPosition));

            worldData.markedVehicles.add(new AASWorldData.VehicleRecord(
                    entity.getUUID(),
                    vTeam,
                    vType,
                    entity.getX(),
                    entity.getY(),
                    entity.getZ(),
                    entity.getYRot(),
                    this.worldPosition // Передаем BlockPos спавнера
            ));
            worldData.setDirty();
            PacketHandler.sendToAllClients((ServerLevel)level, worldData);

            // Заполнение инвентаря
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
        spawnTimestamp = tag.getLong("SpawnTimestamp");
        hasSpawnedOnce = tag.getBoolean("HasSpawnedOnce");
        vehicleIdString = tag.getString("VehicleID");
        vehicleYaw = tag.getFloat("VehicleYaw");
        if (tag.hasUUID("LastVehicle")) lastVehicleUUID = tag.getUUID("LastVehicle");
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Inventory", inventory.serializeNBT());
        tag.putInt("RespawnTime", respawnTimeSettings);
        tag.putInt("InitialTime", initialTimeSettings);
        tag.putLong("SpawnTimestamp", spawnTimestamp);
        tag.putBoolean("HasSpawnedOnce", hasSpawnedOnce);
        tag.putString("VehicleID", vehicleIdString);
        tag.putFloat("VehicleYaw", vehicleYaw);
        if (lastVehicleUUID != null) tag.putUUID("LastVehicle", lastVehicleUUID);
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