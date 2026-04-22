// PATH: src\main\java\com\example\aas\entity\AGS30GrenadeEntity.java
package com.example.aas.entity;

import com.example.aas.config.AASConfig; // Импорт конфига
import com.example.aas.item.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;

public class AGS30GrenadeEntity extends Projectile implements ItemSupplier {

    private double originX;
    private double originY;
    private double originZ;

    public AGS30GrenadeEntity(EntityType<? extends Projectile> type, Level level) {
        super(type, level);
    }

    public AGS30GrenadeEntity(Level level, LivingEntity shooter) {
        super(ModEntities.AGS_30_GRENADE.get(), level);
        this.setOwner(shooter);
        this.setPos(shooter.getX(), shooter.getEyeY(), shooter.getZ());

        this.originX = this.getX();
        this.originY = this.getY();
        this.originZ = this.getZ();
    }

    @Override
    public ItemStack getItem() {
        return new ItemStack(ModItems.AGS_PROJECTILE_ITEM.get());
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

        if (this.tickCount == 1 && originX == 0 && originY == 0 && originZ == 0) {
            this.originX = this.getX();
            this.originY = this.getY();
            this.originZ = this.getZ();
        }

        if (this.tickCount > 600) {
            this.discard();
            return;
        }

        Vec3 currentPos = this.position();
        Vec3 motion = this.getDeltaMovement();
        Vec3 nextPos = currentPos.add(motion);

        HitResult hitResult = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hitResult.getType() != HitResult.Type.MISS) {
            this.onHit(hitResult);
        }

        if (!this.isRemoved()) {
            this.setPos(nextPos.x, nextPos.y, nextPos.z);
            float drag = 0.99F;
            double gravity = 0.010D;
            this.setDeltaMovement(this.getDeltaMovement().scale(drag).subtract(0, gravity, 0));

            if (this.level().isClientSide) {
                this.level().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY(), this.getZ(), 0, 0, 0);
            }
        }
    }

    private void explode() {
        if (!this.level().isClientSide) {
            double distanceSq = this.distanceToSqr(originX, originY, originZ);
            if (distanceSq < 160) { // Не взрывается слишком близко к стрелку (безопасная зона)
                this.discard();
                return;
            }

            // ПРОВЕРКА КОНФИГА
            boolean canDestroy = AASConfig.AGS_PROJECTILE_DESTRUCTION.get();
            Level.ExplosionInteraction interaction = canDestroy ? Level.ExplosionInteraction.BLOCK : Level.ExplosionInteraction.NONE;

            this.level().explode(this, this.getX(), this.getY(), this.getZ(), 3F, interaction);
            this.discard();
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        result.getEntity().hurt(this.level().damageSources().thrown(this, this.getOwner()), 5f);
        explode();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        explode();
    }

    @Override protected void defineSynchedData() {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putDouble("OriginX", this.originX);
        tag.putDouble("OriginY", this.originY);
        tag.putDouble("OriginZ", this.originZ);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.originX = tag.getDouble("OriginX");
        this.originY = tag.getDouble("OriginY");
        this.originZ = tag.getDouble("OriginZ");
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}