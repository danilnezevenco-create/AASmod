// PATH: src\main\java\com\example\aas\entity\AGS30GrenadeEntity.java
package com.example.aas.entity;

import com.example.aas.config.AASConfig;
import com.example.aas.item.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel; // ДОБАВЛЕНО
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB; // ДОБАВЛЕНО
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
            ServerLevel serverLevel = (ServerLevel) this.level();
            double distanceSq = this.distanceToSqr(originX, originY, originZ);

            // Безопасная зона для стрелка (чтобы не подорваться на вылете)
            if (distanceSq < 160) {
                this.discard();
                return;
            }

            // 1. ПУСКАЕМ 12 ОСКОЛОЧНЫХ ЛУЧЕЙ
            spawnShrapnel(serverLevel);

            // 2. СТАНДАРТНЫЙ ВЗРЫВ (для визуальных эффектов и разрушения блоков)
            boolean canDestroy = AASConfig.AGS_PROJECTILE_DESTRUCTION.get();
            Level.ExplosionInteraction interaction = canDestroy ? Level.ExplosionInteraction.BLOCK : Level.ExplosionInteraction.NONE;

            // Мощность взрыва можно чуть уменьшить (например, до 1.5), так как основной урон теперь от лучей
            this.level().explode(this, this.getX(), this.getY(), this.getZ(), 2.0F, interaction);

            this.discard();
        }
    }

    private void spawnShrapnel(ServerLevel level) {
        Vec3 center = this.position().add(0, 0.2, 0); // Центр взрыва
        int count = 24; // Количество лучей

        for (int i = 0; i < count; i++) {
            // Алгоритм распределения точек на сфере (Fibonacci Sphere)
            // Это создаст равномерный разлет во всех направлениях (вверх, вниз, в бока)
            double y = 1.0 - (i / (double) (count - 1)) * 2.0; // от 1 до -1
            double radiusAtY = Math.sqrt(1.0 - y * y); // радиус круга на этой высоте

            double goldenAngle = Math.PI * (3.0 - Math.sqrt(5.0)); // "Золотой угол"
            double theta = goldenAngle * i;

            double x = Math.cos(theta) * radiusAtY;
            double z = Math.sin(theta) * radiusAtY;

            // Направление луча в 3D
            Vec3 direction = new Vec3(x, y, z).normalize();
            Vec3 endPos = center.add(direction.scale(3.5)); // Длина 2 блока

            // 1. Проверка блоков (препятствий)
            BlockHitResult blockHit = level.clip(new net.minecraft.world.level.ClipContext(
                    center, endPos, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, this));

            Vec3 finalTargetPos = (blockHit.getType() == HitResult.Type.MISS) ? endPos : blockHit.getLocation();


            // 2. Проверка попадания в хитбокс персонажа
            EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(
                    level, this, center, finalTargetPos,
                    new AABB(center, finalTargetPos).inflate(0.2), // Толщина луча
                    e -> e instanceof LivingEntity && !e.isSpectator());

            if (entityHit != null && entityHit.getEntity() instanceof LivingEntity victim) {
                // Наносим 20 урона
                victim.hurt(level.damageSources().explosion(this, this.getOwner()), 20.0F);
                level.sendParticles(ParticleTypes.FLASH, entityHit.getLocation().x, entityHit.getLocation().y, entityHit.getLocation().z, 1, 0, 0, 0, 0);
            }
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