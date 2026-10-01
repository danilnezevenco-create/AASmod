package com.example.aas.events;

import com.example.aas.util.VehicleCatalog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Отслеживание урона по вражеской технике: кто и когда бил.
 * getLastHurtByMob для техники ненадёжен, поэтому хиты собираются из нескольких источников:
 *  - LivingHurtEvent (если техника - LivingEntity),
 *  - ProjectileImpactEvent (снаряды/пули по технике, которая не LivingEntity),
 *  - ExplosionEvent.Detonate (взрывы).
 * Таран: если источник урона - другая техника, засчитывается её водитель.
 * Помеченной считается любая сущность с AAS_VehicleTeam в persistent data.
 */
@Mod.EventBusSubscriber(modid = "aas", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VehicleDamageTracker {

    private VehicleDamageTracker() {}

    // uuid техники -> (uuid игрока -> тик последнего попадания)
    private static final Map<UUID, Map<UUID, Long>> HITS = new HashMap<>();

    // ================= СБОР ХИТОВ =================

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity target = event.getEntity();
        if (target.level().isClientSide) return;
        if (!isMarkedVehicle(target)) return;
        ServerPlayer attacker = resolveAttacker(event.getSource());
        recordHit(target, attacker);
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        Projectile projectile = event.getProjectile();
        if (projectile.level().isClientSide) return;
        if (!(event.getRayTraceResult() instanceof EntityHitResult hit)) return;
        Entity target = hit.getEntity();
        if (!isMarkedVehicle(target)) return;
        Entity owner = projectile.getOwner();
        ServerPlayer attacker = asPlayer(owner);
        if (attacker == null) attacker = driverOf(owner);
        recordHit(target, attacker);
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        if (event.getLevel().isClientSide) return;
        Explosion explosion = event.getExplosion();
        Entity source = explosion.getIndirectSourceEntity();
        ServerPlayer attacker = asPlayer(source);
        if (attacker == null) attacker = driverOf(source);
        if (attacker == null) return;
        for (Entity affected : event.getAffectedEntities()) {
            if (isMarkedVehicle(affected)) recordHit(affected, attacker);
        }
    }

    private static boolean isMarkedVehicle(Entity entity) {
        return entity != null && entity.getPersistentData().contains("AAS_VehicleTeam");
    }

    /** Убийца: игрок-стрелок; если источник - техника (таран), то её водитель. */
    private static ServerPlayer resolveAttacker(DamageSource source) {
        if (source == null) return null;
        Entity direct = source.getDirectEntity();
        Entity indirect = source.getEntity();
        ServerPlayer p = asPlayer(indirect);
        if (p != null) return p;
        p = asPlayer(direct);
        if (p != null) return p;
        p = driverOf(indirect);
        if (p != null) return p;
        return driverOf(direct);
    }

    private static ServerPlayer asPlayer(Entity e) {
        return (e instanceof ServerPlayer sp) ? sp : null;
    }

    private static ServerPlayer driverOf(Entity e) {
        if (e == null) return null;
        Entity driver = e.getControllingPassenger();
        return (driver instanceof ServerPlayer sp) ? sp : null;
    }

    private static void recordHit(Entity vehicle, ServerPlayer attacker) {
        if (attacker == null || vehicle == null) return;
        if (!isEnemyOfVehicle(vehicle.getPersistentData().getString("AAS_VehicleTeam"), attacker)) return;

        long now = vehicle.level().getGameTime();
        Map<UUID, Long> perPlayer = HITS.get(vehicle.getUUID());
        if (perPlayer == null) {
            perPlayer = new HashMap<>();
            HITS.put(vehicle.getUUID(), perPlayer);
        }
        perPlayer.put(attacker.getUUID(), now);

        if (HITS.size() > 256) {
            HITS.values().removeIf(m -> {
                m.values().removeIf(t -> now < t || now - t > ScoreValues.VEHICLE_ASSIST_WINDOW_TICKS);
                return m.isEmpty();
            });
        }
    }

    private static boolean isEnemyOfVehicle(String vehicleTeam, ServerPlayer player) {
        if (vehicleTeam == null || vehicleTeam.isEmpty() || vehicleTeam.equalsIgnoreCase("NEUTRAL")) return false;
        if (player.getTeam() == null) return false;
        String pTeam = player.getTeam().getName();
        boolean playerIsBlueRed = pTeam.equalsIgnoreCase("BLUE") || pTeam.equalsIgnoreCase("RED");
        return playerIsBlueRed && !vehicleTeam.equalsIgnoreCase(pTeam);
    }

    // ================= НАЧИСЛЕНИЕ ПРИ УНИЧТОЖЕНИИ =================

    /**
     * Вызывается из GameLogicEvents.processEntityLoss при потере вражеской техники.
     * Убийца получает clamp(penalty * 15, 30, 300) TP и +1 к счётчикам техники,
     * ассисты (били за последние 30 сек, но не добили) - 40% от очков убийцы.
     */
    public static void awardForDestroyedVehicle(Entity vehicle, ServerLevel level, String vTeam, int penalty) {
        long now = level.getGameTime();
        Map<UUID, Long> hits = HITS.remove(vehicle.getUUID());

        ServerPlayer killer = null;
        long killerTick = Long.MIN_VALUE;
        List<ServerPlayer> assists = new ArrayList<>();

        if (hits != null) {
            for (Map.Entry<UUID, Long> e : hits.entrySet()) {
                long t = e.getValue();
                if (now < t || now - t > ScoreValues.VEHICLE_ASSIST_WINDOW_TICKS) continue;
                ServerPlayer p = level.getServer().getPlayerList().getPlayer(e.getKey());
                if (p == null || !isEnemyOfVehicle(vTeam, p)) continue;
                if (t > killerTick) {
                    if (killer != null) assists.add(killer);
                    killer = p;
                    killerTick = t;
                } else {
                    assists.add(p);
                }
            }
        }

        // Запасной вариант: если хитов не собрано (нестандартный источник урона)
        if (killer == null && vehicle instanceof LivingEntity living) {
            if (living.getLastHurtByMob() instanceof ServerPlayer lastAttacker && isEnemyOfVehicle(vTeam, lastAttacker)) {
                killer = lastAttacker;
            }
        }
        if (killer == null) return;

        int killerTp = Math.max(ScoreValues.VEHICLE_TP_MIN,
                Math.min(ScoreValues.VEHICLE_TP_MAX, penalty * ScoreValues.VEHICLE_TP_PER_TICKET));
        StatsHandler.addScoreCustom(killer, ScoreType.VEHICLE_DESTROYED, killerTp, 0);

        String vehicleId = null;
        ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(vehicle.getType());
        if (key != null) vehicleId = key.toString();
        String markerType = vehicle.getPersistentData().getString("AAS_VehicleType");
        VehicleCatalog.Category category = VehicleCatalog.resolve(vehicleId, markerType);
        StatsHandler.addVehicleKill(killer, category);

        int assistTp = killerTp * ScoreValues.VEHICLE_ASSIST_PERCENT / 100;
        for (ServerPlayer assist : assists) {
            if (assist == killer) continue;
            StatsHandler.addScoreCustom(assist, ScoreType.VEHICLE_ASSIST, assistTp, 0);
        }
    }
}
