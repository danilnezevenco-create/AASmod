// PATH: src\main\java\com\example\aas\entity\SupplyCrateEntity.java
package com.example.aas.entity;

import com.example.aas.block.HubBlockEntity;
import com.example.aas.block.VehicleSpawnerBlockEntity;
import com.example.aas.world.AASWorldData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.UUID;

public class SupplyCrateEntity extends Entity {

    private static final EntityDataAccessor<Integer> MATERIALS = SynchedEntityData.defineId(SupplyCrateEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> TEAM_OWNER = SynchedEntityData.defineId(SupplyCrateEntity.class, EntityDataSerializers.STRING);

    private boolean hasResupplied = false;
    private UUID ownerId = null;
    private UUID currentTargetUUID = null;

    public SupplyCrateEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    public SupplyCrateEntity(Level level, double x, double y, double z, String team, UUID ownerId) {
        this(ModEntities.SUPPLY_CRATE.get(), level);
        this.setPos(x, y, z);
        this.setTeamOwner(team);
        // Заменяем 50 на конфиг:
        this.setMaterials(com.example.aas.config.AASConfig.SUPPLY_CRATE_MATERIALS.get());
        this.ownerId = ownerId;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(MATERIALS, 50);
        this.entityData.define(TEAM_OWNER, "NEUTRAL");
    }

    public int getMaterials() { return this.entityData.get(MATERIALS); }
    public void setMaterials(int amount) { this.entityData.set(MATERIALS, amount); }
    public String getTeamOwner() { return this.entityData.get(TEAM_OWNER); }
    public void setTeamOwner(String team) { this.entityData.set(TEAM_OWNER, team); }

    @Override
    public boolean isAttackable() { return true; }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide || this.isRemoved()) return false;
        if (source.is(DamageTypeTags.IS_EXPLOSION) || source.getEntity() instanceof Player) {
            destroyCrate();
            return true;
        }
        return false;
    }

    public void destroyCrate() {
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, this.getX(), this.getY() + 0.5, this.getZ(), 5, 0.2, 0.2, 0.2, 0.05);
        }
        this.discard();
    }

    // === ОТКРЫТИЕ МЕНЮ ЯЩИКА ПО ПКМ ===
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;

        // Открываем меню только на клиенте
        if (this.level().isClientSide) {
            // Проверка команды на клиенте для красоты (чтобы меню даже не открывалось)
            String pTeam = player.getTeam() != null ? player.getTeam().getName() : "NEUTRAL";
            if (!this.getTeamOwner().equals("NEUTRAL") && !this.getTeamOwner().equalsIgnoreCase(pTeam)) {
                player.displayClientMessage(Component.literal("Cannot open enemy crate!").withStyle(ChatFormatting.RED), true);
                return InteractionResult.SUCCESS;
            }

            // Вызов хука для открытия окна
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> com.example.aas.client.ClientHooks.openCrateMenu(this.getId()));
        }

        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    @Override
    public void tick() {
        super.tick();

        // Проверка: если 0 материалов - удаляем ящик
        if (!this.level().isClientSide && this.getMaterials() <= 0) {
            this.destroyCrate();
            return;
        }

        if (!this.isNoGravity()) {
            this.setDeltaMovement(this.getDeltaMovement().add(0, -0.04, 0));
        }
        this.move(MoverType.SELF, this.getDeltaMovement());

        float friction = 0.98F;
        if (this.onGround()) friction = 0.7F;
        this.setDeltaMovement(this.getDeltaMovement().scale(friction));

        if (this.level().isClientSide) return;

        if (!hasResupplied && this.tickCount > 20 && this.tickCount % 20 == 0 && this.onGround()) {
            supplyNearbyHub();

            if (!this.isRemoved()) {
                supplyNearbyVehicle();
            }
        }

        if (this.tickCount > 6000) {
            this.discard();
        }
    }

    private void supplyNearbyVehicle() {
        ServerLevel level = (ServerLevel) this.level();

        if (this.currentTargetUUID != null) {
            Entity target = level.getEntity(this.currentTargetUUID);

            if (target == null || !target.isAlive() || target.distanceTo(this) > 15.0) {
                if (target != null) target.getPersistentData().putInt("AAS_CrateTimer", 0);
                this.currentTargetUUID = null;
                return;
            }
            processVehicleLogic(target);
            return;
        }

        AABB searchArea = this.getBoundingBox().inflate(10.0);
        List<Entity> vehicles = level.getEntities(this, searchArea, e -> e.isAlive() && e.getPersistentData().contains("AAS_VehicleTeam"));

        // === ПРИОРИТЕТ Машины с игроком ===
        vehicles.sort((e1, e2) -> {
            boolean p1 = !e1.getPassengers().isEmpty();
            boolean p2 = !e2.getPassengers().isEmpty();
            if (p1 && !p2) return -1;
            if (!p1 && p2) return 1;
            return 0;
        });

        for (Entity vehicle : vehicles) {
            // Проверка команды
            String vTeam = vehicle.getPersistentData().getString("AAS_VehicleTeam");
            if (!this.getTeamOwner().equals("NEUTRAL") && !vTeam.isEmpty() && !vTeam.equalsIgnoreCase(this.getTeamOwner())) {
                continue;
            }

            boolean needsService = false;
            if (vehicle.getPersistentData().contains("AAS_SpawnerPos")) {
                if (vehicle instanceof LivingEntity living && living.getHealth() < living.getMaxHealth()) needsService = true;
                if (!vehicle.getPersistentData().getBoolean("AAS_IsSupplyTruck")) needsService = true;
            }

            if (needsService) {
                this.currentTargetUUID = vehicle.getUUID();
                vehicle.getPersistentData().putInt("AAS_CrateTimer", 0);
                sendMessageToPassengers(vehicle, "Connecting to Supply Crate...", ChatFormatting.YELLOW);
                return;
            }
        }
    }

    private void processVehicleLogic(Entity vehicle) {
        int timer = vehicle.getPersistentData().getInt("AAS_CrateTimer");
        timer++;
        int timeToWait = 30;

        if (timer >= timeToWait) {
            boolean actionDone = false;

            if (vehicle.getPersistentData().contains("AAS_SpawnerPos")) {
                if (vehicle instanceof LivingEntity living) living.setHealth(living.getMaxHealth());

                long spawnerPosLong = vehicle.getPersistentData().getLong("AAS_SpawnerPos");
                BlockPos spawnerPos = BlockPos.of(spawnerPosLong);
                if (level().isLoaded(spawnerPos)) {
                    BlockEntity be = level().getBlockEntity(spawnerPos);
                    if (be instanceof VehicleSpawnerBlockEntity spawner) {
                        vehicle.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(vehInv -> {
                            if (vehInv instanceof IItemHandlerModifiable modifiable) {
                                for (int i = 0; i < vehInv.getSlots(); i++) modifiable.setStackInSlot(i, ItemStack.EMPTY);
                            }
                            int slotsToCopy = 32;
                            for (int i = 0; i < slotsToCopy; i++) {
                                if (i >= vehInv.getSlots()) break;
                                ItemStack sourceStack = spawner.inventory.getStackInSlot(i + 1);
                                if (!sourceStack.isEmpty()) insertItem(vehInv, i, sourceStack.copy());
                            }
                            Item batteryItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("superbwarfare", "large_battery"));
                            if (batteryItem != null) {
                                for (int i = 0; i < vehInv.getSlots(); i++) {
                                    if (vehInv.getStackInSlot(i).isEmpty()) {
                                        insertItem(vehInv, i, new ItemStack(batteryItem));
                                        break;
                                    }
                                }
                            }
                        });
                    }
                }
                vehicle.getPersistentData().putLong("AAS_NextSupplyTime", level().getGameTime() + 6000);
                sendMessageToPassengers(vehicle, "Vehicle Repaired & Rearmed by Crate!", ChatFormatting.GREEN);
                actionDone = true;
            }

            if (actionDone) {
                if (this.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER, vehicle.getX(), vehicle.getY() + 1.5, vehicle.getZ(), 20, 1.0, 1.0, 1.0, 0.1);
                }
                vehicle.getPersistentData().putInt("AAS_CrateTimer", 0);
                this.hasResupplied = true;
                this.destroyCrate();
            } else {
                vehicle.getPersistentData().putInt("AAS_CrateTimer", 0);
                this.currentTargetUUID = null;
            }

        } else {
            vehicle.getPersistentData().putInt("AAS_CrateTimer", timer);
            sendMessageToPassengers(vehicle, "Resupplying: " + (timeToWait - timer) + "s", ChatFormatting.AQUA);
        }
    }

    private static void insertItem(IItemHandler handler, int slot, ItemStack stack) {
        if (handler instanceof IItemHandlerModifiable modifiable) modifiable.setStackInSlot(slot, stack);
        else handler.insertItem(slot, stack, false);
    }

    private static void sendMessageToPassengers(Entity vehicle, String msg, ChatFormatting color) {
        for (Entity passenger : vehicle.getPassengers()) {
            if (passenger instanceof Player player) player.displayClientMessage(Component.literal(msg).withStyle(color), true);
        }
    }

    private void supplyNearbyHub() {
        if (this.getTeamOwner().equals("NEUTRAL")) return;

        ServerLevel currentLevel = (ServerLevel) this.level();
        AASWorldData data = AASWorldData.get(currentLevel);
        BlockPos myPos = this.blockPosition();
        double searchRadiusSq = 7500.0;
        String currentDim = currentLevel.dimension().location().toString();

        for (AASWorldData.HubInfo hubInfo : data.hubs) {
            if (hubInfo.dimension != null && !hubInfo.dimension.equals(currentDim)) continue;

            if (!hubInfo.constructed) continue;
            BlockPos hubPos = hubInfo.pos;

            if (hubPos.distSqr(myPos) <= searchRadiusSq) {
                if (currentLevel.isLoaded(hubPos)) {
                    BlockEntity be = currentLevel.getBlockEntity(hubPos);

                    if (be instanceof HubBlockEntity hub) {
                        String hubTeam = hub.getTeam();

                        if (hubTeam.equalsIgnoreCase(this.getTeamOwner()) || hubTeam.equals("NEUTRAL")) {
                            if (hubTeam.equals("NEUTRAL")) {
                                hub.setTeam(this.getTeamOwner());
                                hubInfo.team = this.getTeamOwner();
                                data.setDirty();
                            }

                            // Передаем в хаб все материалы, которые есть в ящике
                            hub.addMaterials(this.getMaterials());
                            this.hasResupplied = true;

                            if (this.ownerId != null) {
                                Player player = currentLevel.getPlayerByUUID(this.ownerId);
                                if (player != null) {
                                    ChatFormatting color = this.getTeamOwner().equals("BLUE") ? ChatFormatting.BLUE : ChatFormatting.RED;
                                    player.displayClientMessage(Component.literal("FOB Resupplied! (+" + this.getMaterials() + " Mats)").withStyle(color), true);
                                }
                            }

                            currentLevel.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                                    hubPos.getX() + 0.5, hubPos.getY() + 1.5, hubPos.getZ() + 0.5,
                                    20, 0.5, 0.5, 0.5, 0.1);

                            this.destroyCrate();
                            return;
                        }
                    }
                }
            }
        }
    }

    @Override public boolean canBeCollidedWith() { return false; }
    @Override public boolean isPickable() { return true; }
    @Override public boolean isPushable() { return false; }

    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        this.setTeamOwner(tag.getString("Team"));
        this.setMaterials(tag.getInt("Materials"));
        this.hasResupplied = tag.getBoolean("HasResupplied");
        if (tag.hasUUID("Owner")) this.ownerId = tag.getUUID("Owner");
        if (tag.hasUUID("Target")) this.currentTargetUUID = tag.getUUID("Target");
    }

    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putString("Team", this.getTeamOwner());
        tag.putInt("Materials", this.getMaterials());
        tag.putBoolean("HasResupplied", hasResupplied);
        if (this.ownerId != null) tag.putUUID("Owner", this.ownerId);
        if (this.currentTargetUUID != null) tag.putUUID("Target", this.currentTargetUUID);
    }

    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}