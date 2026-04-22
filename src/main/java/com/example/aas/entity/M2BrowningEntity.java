package com.example.aas.entity;

import com.example.aas.item.M2AmmoItem;
import com.example.aas.item.ModItems;
import com.example.aas.sound.ModSounds;
import com.example.aas.entity.M2BulletEntity;
// === НОВЫЕ ИМПОРТЫ ДЛЯ ОТДАЧИ ===
import com.example.aas.network.PacketHandler;
import com.example.aas.network.PacketRecoil;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
// ================================

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public class M2BrowningEntity extends Entity implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private static final EntityDataAccessor<Float> TURRET_YAW = SynchedEntityData.defineId(M2BrowningEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TURRET_PITCH = SynchedEntityData.defineId(M2BrowningEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> HAS_MAGAZINE = SynchedEntityData.defineId(M2BrowningEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> AMMO_COUNT = SynchedEntityData.defineId(M2BrowningEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> IS_AIMING = SynchedEntityData.defineId(M2BrowningEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_FIRING = SynchedEntityData.defineId(M2BrowningEntity.class, EntityDataSerializers.BOOLEAN);

    private final boolean DEBUG_MODE = false;
    private final float SMOOTH_SPEED = 0.12f;

    private int shootCooldown = 0;
    private int firingResetTimer = 0;
    private static final int FIRE_RATE_TICKS = 2;

    public M2BrowningEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    public M2BrowningEntity(Level level, double x, double y, double z) {
        this(ModEntities.M2_BROWNING.get(), level);
        this.setPos(x, y, z);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(TURRET_YAW, 0f);
        this.entityData.define(TURRET_PITCH, 0f);
        this.entityData.define(HAS_MAGAZINE, false);
        this.entityData.define(AMMO_COUNT, 0);
        this.entityData.define(IS_AIMING, false);
        this.entityData.define(IS_FIRING, false);
    }

    public boolean isAiming() { return this.entityData.get(IS_AIMING); }
    public void setAiming(boolean aiming) { this.entityData.set(IS_AIMING, aiming); }
    public boolean hasMagazine() { return this.entityData.get(HAS_MAGAZINE); }
    public void setHasMagazine(boolean has) { this.entityData.set(HAS_MAGAZINE, has); }
    public int getAmmoCount() { return this.entityData.get(AMMO_COUNT); }
    public void setAmmoCount(int count) { this.entityData.set(AMMO_COUNT, count); }
    public void setTurretYaw(float rot) { this.entityData.set(TURRET_YAW, rot); }
    public float getTurretYaw() { return this.entityData.get(TURRET_YAW); }
    public void setTurretPitch(float rot) { this.entityData.set(TURRET_PITCH, rot); }
    public float getTurretPitch() { return this.entityData.get(TURRET_PITCH); }
    public boolean isFiring() { return this.entityData.get(IS_FIRING); }
    public void setFiring(boolean firing) { this.entityData.set(IS_FIRING, firing); }

    @Override
    public boolean isAttackable() { return true; }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.level().isClientSide || this.isRemoved()) return false;

        if (source.getEntity() instanceof Player player && player.isCreative()) {
            this.discard();
            return true;
        }

        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            this.discard();
            return true;
        }

        return false;
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide) {
            if (shootCooldown > 0) shootCooldown--;
            if (isFiring()) {
                firingResetTimer--;
                if (firingResetTimer <= 0) {
                    setFiring(false);
                }
            }
        }

        if (this.level().isClientSide && DEBUG_MODE) {
            double[] seatPos = calculateSeatPosition(this.getTurretYaw());
            this.level().addParticle(ParticleTypes.FLAME, seatPos[0], seatPos[1] + 1.6, seatPos[2], 0, 0, 0);
        }

        if (!this.level().isClientSide) {
            Entity passenger = this.getFirstPassenger();
            if (passenger instanceof Player player) {
                float playerYaw = player.getYRot();
                float playerPitch = player.getXRot();

                float targetRelativeYaw = Mth.wrapDegrees(playerYaw - this.getYRot());
                float currentTurretYaw = this.getTurretYaw();
                float smoothYaw = Mth.rotLerp(SMOOTH_SPEED, currentTurretYaw, targetRelativeYaw);
                float limitedPitch = Math.max(-45, Math.min(30, playerPitch));

                this.setTurretYaw(smoothYaw);
                this.setTurretPitch(limitedPitch);

                if (playerPitch < -45 || playerPitch > 30) {
                    player.setXRot(limitedPitch);
                }
            } else {
                if (isAiming()) setAiming(false);
                if (isFiring()) setFiring(false);
            }
        }
    }

    public void tryShoot(Player shooter) {
        if (!level().isClientSide) {
            setFiring(true);
            firingResetTimer = 5;
        }

        if (shootCooldown > 0) return;

        if (!shooter.isCreative()) {
            if (!hasMagazine()) {
                shooter.displayClientMessage(Component.literal("No Magazine!").withStyle(ChatFormatting.RED), true);
                shootCooldown = 20;
                return;
            }
            if (getAmmoCount() <= 0) {
                shooter.displayClientMessage(Component.literal("Empty!").withStyle(ChatFormatting.RED), true);
                shootCooldown = 20;
                return;
            }
        }

        if (!level().isClientSide) {
            M2BulletEntity bullet = new M2BulletEntity(level(), shooter);

            float pitch = this.getTurretPitch();
            float yaw = this.getYRot() + this.getTurretYaw();

            double pivotZ = 0.03125D;
            double barrelLength = 3D;
            double pivotHeight = 1.4D;

            double radsBase = Math.toRadians(this.getYRot());
            double pivotX = -Math.sin(radsBase) * pivotZ;
            double pivotZWorld = Math.cos(radsBase) * pivotZ;

            double yawRad = Math.toRadians(yaw);
            double pitchRad = Math.toRadians(pitch);

            double forwardX = -Math.sin(yawRad) * Math.cos(pitchRad) * barrelLength;
            double forwardZ = Math.cos(yawRad) * Math.cos(pitchRad) * barrelLength;
            double forwardY = -Math.sin(pitchRad) * barrelLength;

            double rightOffset = 0.05D;
            double rightX = -Math.cos(yawRad) * rightOffset;
            double rightZ = -Math.sin(yawRad) * rightOffset;

            bullet.setPos(
                    this.getX() + pivotX + forwardX + rightX,
                    this.getY() + pivotHeight + forwardY - 0.7D + 0.05D,
                    this.getZ() + pivotZWorld + forwardZ + rightZ
            );

            bullet.shootFromRotation(this, pitch, yaw, 0.0F, 3.0F, 0.05F);

            level().addFreshEntity(bullet);

            this.playSound(ModSounds.M2_SHOOT.get(), 3.5f, 1.0f / (this.random.nextFloat() * 0.4f + 0.8f));

            // === ДОБАВЛЕНА ОТДАЧА ===
            if (shooter instanceof ServerPlayer serverPlayer) {
                // M2 стреляет быстро, поэтому отдача за выстрел маленькая (0.5f), но частая.
                // Также добавляем небольшую тряску влево-вправо (0.2f)
                float recoilKick = 1f;
                float sideShake = (this.random.nextFloat() - 0.5f) * 0.5f;

                PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer),
                        new PacketRecoil(recoilKick, sideShake));
            }
            // ========================

            if (!shooter.isCreative()) {
                setAmmoCount(getAmmoCount() - 1);
            }

            shootCooldown = FIRE_RATE_TICKS;
        }
    }

    private double[] calculateSeatPosition(float turretRelativeYaw) {
        double modelPivotOffsetZ = 0.03125D;

        double baseRad = Math.toRadians(this.getYRot());
        double pivotX = this.getX() - Math.sin(baseRad) * modelPivotOffsetZ;
        double pivotZ = this.getZ() + Math.cos(baseRad) * modelPivotOffsetZ;

        double yOffset = isAiming() ? -0.7D : -0.55D;
        double pivotY = this.getY() + yOffset;

        double seatDistance = isAiming() ? 0.85D : 1.35D;

        float seatAngle = this.getYRot() + turretRelativeYaw + 180.0F;
        double rads = Math.toRadians(seatAngle);

        return new double[] {
                pivotX + (-Math.sin(rads) * seatDistance),
                pivotY,
                pivotZ + (Math.cos(rads) * seatDistance)
        };
    }

    @Override
    public void positionRider(Entity passenger, MoveFunction callback) {
        if (this.hasPassenger(passenger)) {
            double[] pos = calculateSeatPosition(this.getTurretYaw());
            callback.accept(passenger, pos[0], pos[1], pos[2]);
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!this.level().isClientSide && hand == InteractionHand.MAIN_HAND) {
            ItemStack stack = player.getItemInHand(hand);

            if (player.isShiftKeyDown() && stack.isEmpty() && hasMagazine()) {
                int remainingAmmo = getAmmoCount();
                ItemStack newBox = new ItemStack(ModItems.M2_AMMO.get());
                M2AmmoItem.setAmmo(newBox, remainingAmmo);
                player.setItemInHand(hand, newBox);
                setHasMagazine(false);
                setAmmoCount(0);
                this.playSound(ModSounds.M2_UNLOAD.get(), 1.0f, 1.0f);
                player.displayClientMessage(Component.literal("Unloaded (" + remainingAmmo + ")"), true);
                return InteractionResult.SUCCESS;
            }

            if (stack.getItem() instanceof M2AmmoItem) {
                if (!hasMagazine()) {
                    int ammoInBox = M2AmmoItem.getAmmo(stack);
                    setHasMagazine(true);
                    setAmmoCount(ammoInBox);
                    if (!player.isCreative()) stack.shrink(1);
                    this.playSound(ModSounds.M2_LOAD.get(), 1.0f, 1.0f);
                    player.displayClientMessage(Component.literal("Loaded: " + ammoInBox + " rounds"), true);
                    return InteractionResult.SUCCESS;
                } else {
                    player.displayClientMessage(Component.literal("Already loaded!"), true);
                    return InteractionResult.FAIL;
                }
            }

            if (!player.isShiftKeyDown() && this.getPassengers().isEmpty()) {
                float tripodYaw = this.getYRot();
                float turretYaw = this.getTurretYaw();
                player.setYRot(tripodYaw + turretYaw);
                player.setYHeadRot(tripodYaw + turretYaw);
                player.setXRot(this.getTurretPitch());
                player.startRiding(this);
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, event -> {
            if (this.isFiring() && this.getAmmoCount() > 0 && this.hasMagazine()) {
                return event.setAndContinue(RawAnimation.begin().thenLoop("animation.machinegun.fire"));
            }
            return PlayState.STOP;
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.contains("HasMagazine")) setHasMagazine(tag.getBoolean("HasMagazine"));
        if (tag.contains("AmmoCount")) setAmmoCount(tag.getInt("AmmoCount"));
        if (tag.contains("IsAiming")) setAiming(tag.getBoolean("IsAiming"));
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putBoolean("HasMagazine", hasMagazine());
        tag.putInt("AmmoCount", getAmmoCount());
        tag.putBoolean("IsAiming", isAiming());
    }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
    @Override public boolean isPickable() { return true; }
}