package com.example.aas.entity;

import com.example.aas.item.AGSAmmoItem;
import com.example.aas.item.ModItems;
import com.example.aas.network.PacketHandler;
import com.example.aas.network.PacketRecoil;
import com.example.aas.sound.ModSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraftforge.network.PacketDistributor;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public class AGS30Entity extends Entity implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private static final EntityDataAccessor<Float> TURRET_YAW = SynchedEntityData.defineId(AGS30Entity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TURRET_PITCH = SynchedEntityData.defineId(AGS30Entity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> HAS_MAGAZINE = SynchedEntityData.defineId(AGS30Entity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> AMMO_COUNT = SynchedEntityData.defineId(AGS30Entity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> IS_AIMING = SynchedEntityData.defineId(AGS30Entity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_FIRING = SynchedEntityData.defineId(AGS30Entity.class, EntityDataSerializers.BOOLEAN);

    private int shootCooldown = 0;
    private int firingResetTimer = 0;
    private static final int FIRE_RATE_TICKS = 2; // Скорострельность

    public AGS30Entity(EntityType<?> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    public AGS30Entity(Level level, double x, double y, double z) {
        this(ModEntities.AGS_30.get(), level);
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
    public boolean isAiming() { return this.entityData.get(IS_AIMING); }
    public void setAiming(boolean aiming) { this.entityData.set(IS_AIMING, aiming); }

    @Override
    public boolean isAttackable() { return true; }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Если это клиент или энтити уже удалена - ничего не делаем
        if (this.level().isClientSide || this.isRemoved()) return false;

        // 1. Игрок в Креативе может ломать (иначе вы не сможете их убрать)
        if (source.getEntity() instanceof Player player && player.isCreative()) {
            this.discard(); // Удаляем энтити
            return true;
        }

        // 2. Взрыв (ТНТ, Крипер, Граната AGS)
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            this.discard(); // Ломается от взрыва
            // Если нужно, чтобы выпадал предмет, добавьте:
            // this.spawnAtLocation(ModItems.AGS_CONSTRUCTION_BLOCK.get());
            return true;
        }

        // 3. ЛЮБОЙ ДРУГОЙ УРОН (Пули, удары мечом, стрелы) -> ИГНОРИРУЕМ
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide) {
            if (shootCooldown > 0) shootCooldown--;
            if (isFiring()) {
                firingResetTimer--;
                if (firingResetTimer <= 0) setFiring(false);
            }

            Entity passenger = this.getFirstPassenger();
            if (passenger instanceof Player player) {
                float playerYaw = player.getYRot();
                float playerPitch = player.getXRot();

                // Плавный поворот башни
                float targetRelativeYaw = Mth.wrapDegrees(playerYaw - this.getYRot());
                float currentTurretYaw = this.getTurretYaw();
                float smoothYaw = Mth.rotLerp(0.12f, currentTurretYaw, targetRelativeYaw);
                // Ограничение углов вертикальной наводки (-20 вниз, 45 вверх)
                float limitedPitch = Math.max(-20, Math.min(45, playerPitch));

                this.setTurretYaw(smoothYaw);
                this.setTurretPitch(limitedPitch);

                // Ограничиваем камеру игрока, чтобы он не крутил головой сквозь модель
                if (playerPitch < -20 || playerPitch > 45) {
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
            // Анимация стрельбы
            if ((hasMagazine() && getAmmoCount() > 0) || shooter.isCreative()) {
                setFiring(true);
                firingResetTimer = 10;
            } else {
                setFiring(false);
            }
        }

        if (shootCooldown > 0) return;

        // Проверка патронов
        if (!shooter.isCreative()) {
            if (!hasMagazine()) {
                shooter.displayClientMessage(Component.literal("No Ammo Box!").withStyle(ChatFormatting.RED), true);
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
            AGS30GrenadeEntity grenade = new AGS30GrenadeEntity(level(), shooter);

            float pitch = this.getTurretPitch();
            float yaw = this.getYRot() + this.getTurretYaw();

            // Точка спавна снаряда (ствол)
            double pivotZ = 0.1875D;
            double barrelLength = 1.4D;

            double radsBase = Math.toRadians(this.getYRot());
            double pivotX = -Math.sin(radsBase) * pivotZ;
            double pivotZWorld = Math.cos(radsBase) * pivotZ;

            double yawRad = Math.toRadians(yaw);
            double pitchRad = Math.toRadians(pitch);

            double forwardX = -Math.sin(yawRad) * Math.cos(pitchRad) * barrelLength;
            double forwardZ = Math.cos(yawRad) * Math.cos(pitchRad) * barrelLength;
            double forwardY = -Math.sin(pitchRad) * barrelLength;

            double leftOffset = 0.0D;
            double leftX = Math.cos(yawRad) * leftOffset;
            double leftZ = Math.sin(yawRad) * leftOffset;

            grenade.setPos(
                    this.getX() + pivotX + forwardX + leftX,
                    this.getY() + 0.35D + forwardY,
                    this.getZ() + pivotZWorld + forwardZ + leftZ
            );

            // === ИЗМЕНЕНИЕ СКОРОСТИ ===
            // Скорость установлена на 6.0F (быстрая настильная траектория)
            grenade.shootFromRotation(this, pitch, yaw, 0.0F, 4.0F, 0.0F);
            // ==========================

            level().addFreshEntity(grenade);

            this.playSound(ModSounds.AGS_SHOOT.get(), 4.0f, 1.0f / (this.random.nextFloat() * 0.4f + 0.8f));

            // === ОТДАЧА (ТРЯСКА ЭКРАНА) ===
            if (shooter instanceof ServerPlayer serverPlayer) {
                // Сила рывка вверх (3.0) и случайное дрожание в стороны
                float recoilKick = 4.0f;
                float sideShake = (this.random.nextFloat() - 1f) * 1f;

                PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new PacketRecoil(recoilKick, sideShake));
            }
            // ==============================

            if (!shooter.isCreative()) {
                setAmmoCount(getAmmoCount() - 1);
            }
            shootCooldown = FIRE_RATE_TICKS;
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (!this.level().isClientSide && hand == InteractionHand.MAIN_HAND) {
            ItemStack stack = player.getItemInHand(hand);

            // Зарядка коробкой
            if (stack.getItem() instanceof AGSAmmoItem) {
                if (!hasMagazine()) {
                    int ammo = AGSAmmoItem.getAmmo(stack);
                    setHasMagazine(true);
                    setAmmoCount(ammo);
                    if (!player.isCreative()) stack.shrink(1);
                    this.playSound(ModSounds.M2_LOAD.get(), 1.0f, 1.0f);
                    player.displayClientMessage(Component.literal("AGS Loaded: " + ammo + " rounds"), true);
                    return InteractionResult.SUCCESS;
                } else {
                    player.displayClientMessage(Component.literal("Already loaded!"), true);
                    return InteractionResult.FAIL;
                }
            }

            // Разрядка (Shift + Пустая рука)
            if (player.isShiftKeyDown() && stack.isEmpty() && hasMagazine()) {
                int remaining = getAmmoCount();
                ItemStack newBox = new ItemStack(ModItems.AGS_AMMO.get());
                AGSAmmoItem.setAmmo(newBox, remaining);
                player.setItemInHand(hand, newBox);
                setHasMagazine(false);
                setAmmoCount(0);
                this.playSound(ModSounds.M2_UNLOAD.get(), 1.0f, 1.0f);
                player.displayClientMessage(Component.literal("AGS Unloaded (" + remaining + ")"), true);
                return InteractionResult.SUCCESS;
            }

            // Посадка в технику
            if (!player.isShiftKeyDown() && this.getPassengers().isEmpty()) {
                float gunFacingYaw = this.getYRot() + this.getTurretYaw();
                float gunFacingPitch = this.getTurretPitch();
                player.setYRot(gunFacingYaw);
                player.setYHeadRot(gunFacingYaw);
                player.setXRot(gunFacingPitch);
                player.startRiding(this);
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    public void positionRider(Entity passenger, MoveFunction callback) {
        if (this.hasPassenger(passenger)) {
            double[] pos = calculateSeatPosition(this.getTurretYaw());
            callback.accept(passenger, pos[0], pos[1], pos[2]);
        }
    }

    private double[] calculateSeatPosition(float turretRelativeYaw) {
        double modelPivotOffsetZ = 0.1875D;

        double baseRad = Math.toRadians(this.getYRot());
        double pivotX = this.getX() - Math.sin(baseRad) * modelPivotOffsetZ;
        double pivotZ = this.getZ() + Math.cos(baseRad) * modelPivotOffsetZ;

        // Позиция игрока:
        // Если целится: -0.55D (чуть выше, чтобы видеть прицел)
        // Если не целится: -0.4D
        double pivotY = this.getY() + (isAiming() ? -0.60D : -0.35D);

        double seatDistance = 1.1D;

        float seatAngle = this.getYRot() + turretRelativeYaw + 180.0F;
        double rads = Math.toRadians(seatAngle);

        return new double[] {
                pivotX + (-Math.sin(rads) * seatDistance),
                pivotY,
                pivotZ + (Math.cos(rads) * seatDistance)
        };
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, event -> {
            if (this.isFiring() && (this.getAmmoCount() > 0 || !this.hasMagazine())) {
                if (this.getAmmoCount() > 0 && this.hasMagazine()) {
                    return event.setAndContinue(RawAnimation.begin().thenLoop("animation.ags.fire"));
                }
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