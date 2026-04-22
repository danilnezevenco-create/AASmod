package com.example.aas.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

public class M2BulletEntity extends Projectile {

    private float damage = 25.0f; // Урон

    public M2BulletEntity(EntityType<? extends Projectile> type, Level level) {
        super(type, level);
    }

    public M2BulletEntity(Level level, LivingEntity shooter) {
        super(ModEntities.M2_BULLET.get(), level);
        this.setOwner(shooter);
        this.setPos(shooter.getX(), shooter.getEyeY() - 0.1, shooter.getZ());
    }

    public void shootFromRotation(Entity shooter, float pitch, float yaw, float roll, float velocity, float inaccuracy) {
        float x = -((float) Math.sin(Math.toRadians(yaw))) * (float) Math.cos(Math.toRadians(pitch));
        float y = -((float) Math.sin(Math.toRadians(pitch)));
        float z = (float) Math.cos(Math.toRadians(yaw)) * (float) Math.cos(Math.toRadians(pitch));
        this.shoot(x, y, z, velocity, inaccuracy);
    }

    @Override
    public void tick() {
        super.tick();

        // Время жизни 30 секунд (600 тиков).
        if (!this.level().isClientSide && this.tickCount > 600) {
            this.discard();
            return;
        }

        // === ФИЗИКА И СТОЛКНОВЕНИЯ ===
        Vec3 currentPos = this.position();
        Vec3 motion = this.getDeltaMovement();
        Vec3 nextPos = currentPos.add(motion);

        // 1. RayTrace (Блоки)
        HitResult hitResult = this.level().clip(new ClipContext(
                currentPos,
                nextPos,
                ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE,
                this
        ));

        if (hitResult.getType() != HitResult.Type.MISS) {
            nextPos = hitResult.getLocation();
        }

        // 2. RayTrace (Сущности)
        EntityHitResult entityHitResult = ProjectileUtil.getEntityHitResult(
                this.level(),
                this,
                currentPos,
                nextPos,
                this.getBoundingBox().expandTowards(motion).inflate(1.5D),
                this::canHitEntity
        );

        if (entityHitResult != null) {
            hitResult = entityHitResult;
        }

        // 3. Обработка попадания
        if (hitResult.getType() != HitResult.Type.MISS) {
            if (!this.isRemoved()) {
                this.onHit(hitResult);
            }
        }

        // 4. Движение
        if (!this.isRemoved()) {
            this.setPos(nextPos.x, nextPos.y, nextPos.z);

            this.updateRotation();

            // === ОТКЛЮЧЕНИЕ ЗАМЕДЛЕНИЯ ===

            // 1.0F = Скорость НЕ меняется (нет сопротивления воздуха)
            // Если поставить 0.99F, пуля будет замедляться.
            float airDrag = 1.0F;

            // 0.0D = Нет гравитации (летит прямо как лазер)
            double gravity = 0.0D;

            this.setDeltaMovement(this.getDeltaMovement().scale(airDrag).subtract(0, gravity, 0));
        }
    }

    @Override
    protected void updateRotation() {
        Vec3 motion = this.getDeltaMovement();
        double horizontalDistance = Math.sqrt(motion.x * motion.x + motion.z * motion.z);

        float newYRot = (float) (Mth.atan2(motion.x, motion.z) * (double) (180F / (float) Math.PI));
        float newXRot = (float) (Mth.atan2(motion.y, horizontalDistance) * (double) (180F / (float) Math.PI));

        this.setYRot(lerpRotation(this.yRotO, newYRot));
        this.setXRot(lerpRotation(this.xRotO, newXRot));
    }

    protected static float lerpRotation(float prevRot, float newRot) {
        while (newRot - prevRot < -180.0F) {
            prevRot -= 360.0F;
        }
        while (newRot - prevRot >= 180.0F) {
            prevRot += 360.0F;
        }
        return Mth.lerp(0.2F, prevRot, newRot);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);

        if (!this.level().isClientSide) {
            Entity target = result.getEntity();
            Entity owner = this.getOwner();
            target.hurt(this.level().damageSources().mobProjectile(this, (LivingEntity) owner), damage);
        }

        this.discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);

        if (!this.level().isClientSide) {
            ((ServerLevel) this.level()).sendParticles(ParticleTypes.POOF,
                    this.getX(), this.getY(), this.getZ(),
                    5, 0.1, 0.1, 0.1, 0.05);
        }

        this.discard();
    }

    @Override
    protected void defineSynchedData() {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}