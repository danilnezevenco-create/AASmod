package com.example.aas.entity;

import com.example.aas.uav.UavFlight;
import com.example.aas.uav.UavManager;
import com.example.aas.uav.UavState;
import net.minecraft.core.NonNullList;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Recon UAV. A LivingEntity (so that every projectile/explosion of the mod and of other mods can hurt it),
 * flies strictly along a circle, takes damage from everybody (no team immunity), never drops anything.
 */
public class ReconUavEntity extends LivingEntity implements GeoEntity {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private String uavTeam = "";
    private double cx, cz, radius, altY, theta0, omega;
    private long startTick;
    private boolean configured = false;

    public ReconUavEntity(EntityType<? extends ReconUavEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return LivingEntity.createLivingAttributes().add(Attributes.MAX_HEALTH, 100.0D);
    }

    /** Server only: copy flight parameters and apply the configured health. */
    public void configure(UavState s, int health) {
        this.uavTeam = s.team;
        this.cx = s.cx;
        this.cz = s.cz;
        this.radius = s.radius;
        this.altY = s.altY;
        this.theta0 = s.theta0;
        this.omega = s.omega();
        this.startTick = s.startTick;
        this.configured = true;
        this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        this.setHealth((float) health);
    }

    public String getUavTeam() { return uavTeam; }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || !configured) return;
        double t = level().getGameTime() - startTick;
        Vec3 p = UavFlight.position(cx, cz, radius, altY, theta0, omega, t);
        float yaw = UavFlight.yaw(theta0, omega, t);
        this.setDeltaMovement(Vec3.ZERO);
        this.setPos(p.x, p.y, p.z);
        this.setYRot(yaw);
        this.setXRot(0f);
        this.yHeadRot = yaw;
        this.yBodyRot = yaw;
        this.invulnerableTime = 0; // no i-frames: fast-firing weapons must all register
    }

    // ---------- no physics ----------
    @Override public void travel(Vec3 travelVector) { }
    @Override public void knockback(double strength, double x, double z) { }
    @Override protected void pushEntities() { }
    @Override public void push(Entity entity) { }
    @Override public boolean isPushable() { return false; }
    @Override public boolean canBeCollidedWith() { return false; }
    @Override public boolean isPickable() { return true; }
    @Override public boolean shouldShowName() { return false; }
    @Override public boolean isAffectedByPotions() { return false; }

    /** Visible from far away. */
    @Override
    public boolean shouldRenderAtSqrDistance(double distSqr) {
        double r = 2048.0;
        return distSqr < r * r;
    }

    /** Only entity-caused, explosion, projectile and /kill damage counts (no wall/fall/drown/fire). */
    @Override
    public boolean isInvulnerableTo(DamageSource s) {
        if (super.isInvulnerableTo(s)) return true;
        boolean allowed = s.getEntity() != null || s.getDirectEntity() != null
                || s.is(DamageTypeTags.IS_EXPLOSION) || s.is(DamageTypeTags.IS_PROJECTILE)
                || s.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
        return !allowed;
    }

    @Override
    public void die(DamageSource source) {
        if (level().isClientSide || this.dead) return;
        if (ForgeHooks.onLivingDeath(this, source)) return;
        this.dead = true;
        UavManager.onUavDestroyed(this, source);
        this.discard();
    }

    @Override protected SoundEvent getHurtSound(DamageSource s) { return SoundEvents.IRON_GOLEM_HURT; }
    @Override protected SoundEvent getDeathSound() { return null; }

    // ---------- equipment boilerplate ----------
    private final NonNullList<ItemStack> none = NonNullList.withSize(4, ItemStack.EMPTY);
    @Override public Iterable<ItemStack> getArmorSlots() { return none; }
    @Override public ItemStack getItemBySlot(EquipmentSlot slot) { return ItemStack.EMPTY; }
    @Override public void setItemSlot(EquipmentSlot slot, ItemStack stack) { }
    @Override public HumanoidArm getMainArm() { return HumanoidArm.RIGHT; }

    // ---------- Geckolib (stub: model/animations come from the customer) ----------
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 0, state -> PlayState.STOP));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}
