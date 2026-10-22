package com.example.aas.uav;

import com.example.aas.config.AASConfig;
import com.example.aas.entity.ModEntities;
import com.example.aas.entity.ReconUavEntity;
import com.example.aas.network.PacketHandler;
import com.example.aas.network.PacketSyncUav;
import com.example.aas.world.AASWorldData;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.util.Comparator;

/** Server-side UAV logic: launch, cooldown, lifetime, destruction, team sync. */
public final class UavManager {
    private UavManager() {}

    public static final TicketType<ChunkPos> UAV_TICKET =
            TicketType.create("aas_uav", Comparator.comparingLong(ChunkPos::toLong), 40);

    public enum EndKind { DESTROYED, EXPIRED, LOST, MATCH_END }
    private static final Logger LOGGER = LogUtils.getLogger();
    // ------------------------------------------------------------------ launch
    public static boolean tryLaunch(ServerPlayer p, double cx, double cz, int radius) {
        ServerLevel level = p.serverLevel();
        AASWorldData d = AASWorldData.get(level);
        String team = UavRules.teamOf(p);

        if (!UavRules.isTeam(team) || !UavRules.isCommander(p, d)) return fail(p, "not_commander");
        if (!d.isGameStarted) return fail(p, "not_started");
        if (!p.isAlive()) return fail(p, "dead");
        if (UavRules.isDowned(p)) return fail(p, "downed");
        if (UavRules.get(d, team) != null) return fail(p, "already_active");

        long now = level.getGameTime();
        long cdEnd = UavRules.cooldownEnd(d, team);
        if (now < cdEnd) {
            p.displayClientMessage(Component.translatable("aas.uav.msg.cooldown", (cdEnd - now + 19) / 20), true);
            return false;
        }

        int min = AASConfig.UAV_MIN_RADIUS.get();
        int max = Math.max(min, AASConfig.UAV_MAX_RADIUS.get());
        if (!Double.isFinite(cx) || !Double.isFinite(cz) || radius < min || radius > max) return fail(p, "bad_radius");
        if (!level.getWorldBorder().isWithinBounds(cx, cz)) return fail(p, "bad_position");
        if (d.mapSizeBlocks > 0) {
            double half = d.mapSizeBlocks / 2.0;
            if (Math.abs(cx - d.mapCenterX) > half || Math.abs(cz - d.mapCenterZ) > half) return fail(p, "bad_position");
        }

        double altY = UavFlight.averageTerrain(level, cx, cz, radius) + AASConfig.UAV_ALTITUDE.get();
        double theta0 = level.random.nextDouble() * Math.PI * 2.0;
        float speed = AASConfig.UAV_SPEED.get().floatValue();
        long endTick = now + AASConfig.UAV_DURATION_SECONDS.get() * 20L;
        UavState u = new UavState(team, p.getUUID(), cx, cz, radius, altY, theta0, speed, now, endTick);

        Vec3 start = UavFlight.position(cx, cz, radius, altY, theta0, u.omega(), 0);
        ChunkPos cp = new ChunkPos(Mth.floor(start.x) >> 4, Mth.floor(start.z) >> 4);
        level.getChunk(cp.x, cp.z); // make sure the chunk exists before the entity is added
        level.getChunkSource().addRegionTicket(UAV_TICKET, cp, 2, cp);

        ReconUavEntity e = new ReconUavEntity(ModEntities.RECON_UAV.get(), level);
        e.configure(u, AASConfig.UAV_HEALTH.get());
        e.moveTo(start.x, start.y, start.z, UavFlight.yaw(theta0, u.omega(), 0), 0f);
        if (!level.addFreshEntity(e)) return fail(p, "bad_position");

        u.entityUuid = e.getUUID();
        UavRules.set(d, team, u);
        d.setDirty();
        syncTeam(level, d, team);
        p.displayClientMessage(Component.translatable("aas.uav.msg.launched"), true);
        return true;
    }

    private static boolean fail(ServerPlayer p, String key) {
        p.displayClientMessage(Component.translatable("aas.uav.msg." + key), true);
        return false;
    }

    // ------------------------------------------------------------------ tick
    public static void tickLevel(ServerLevel level) {
        AASWorldData d = AASWorldData.get(level);
        tickTeam(level, d, "BLUE");
        tickTeam(level, d, "RED");
        if (level.getGameTime() % 100 == 0) { // safety resync (team changes, late joins)
            syncTeam(level, d, "BLUE");
            syncTeam(level, d, "RED");
        }
    }

    private static ChunkPos flightChunk(UavState u, long now, int ahead) {
        Vec3 p = UavFlight.position(u.cx, u.cz, u.radius, u.altY, u.theta0, u.omega(), now - u.startTick + ahead);
        return new ChunkPos(Mth.floor(p.x) >> 4, Mth.floor(p.z) >> 4);
    }

    /** Tickets under the UAV and ahead of it along the orbit, so chunks are generated before it arrives. */
    private static void keepFlightPathLoaded(ServerLevel level, UavState u, long now) {
        ChunkPos cur = flightChunk(u, now, 0);
        level.getChunkSource().addRegionTicket(UAV_TICKET, cur, 3, cur);
        for (int ahead : new int[]{40, 80}) {
            ChunkPos cp = flightChunk(u, now, ahead);
            level.getChunkSource().addRegionTicket(UAV_TICKET, cp, 2, cp);
        }
    }

    private static void tickTeam(ServerLevel level, AASWorldData d, String team) {
        UavState u = UavRules.get(d, team);
        if (u == null) return;
        long now = level.getGameTime();
        Entity e = level.getEntity(u.entityUuid);

        if (now >= u.endTick) {
            finish(level, d, team, EndKind.EXPIRED, e instanceof ReconUavEntity r ? r : null);
            return;
        }
        if (now % 5 == 0) keepFlightPathLoaded(level, u, now);

        if (e instanceof ReconUavEntity uav && !uav.isRemoved()) {
            u.missingSince = -1;
            return;
        }
        if (now <= u.startTick + 5) return;

        // The UAV sits in a chunk that is not loaded yet: pull it in and wait instead of giving up at once.
        if (u.missingSince < 0) {
            u.missingSince = now;
            LOGGER.info("[AAS] UAV entity not found, loading its chunk (team={})", team);
        }
        ChunkPos cp = flightChunk(u, now, 0);
        level.getChunkSource().addRegionTicket(UAV_TICKET, cp, 3, cp);
        if (now % 5 == 0) level.getChunk(cp.x, cp.z);
        if (now - u.missingSince > 200) finish(level, d, team, EndKind.LOST, null); // 10 s
    }

    // ------------------------------------------------------------------ end of life
    /** Called by the entity when its health reached 0. */
    public static void onUavDestroyed(ReconUavEntity e, DamageSource src) {
        if (!(e.level() instanceof ServerLevel level)) return;
        AASWorldData d = AASWorldData.get(level);
        String team = e.getUavTeam();
        UavState u = UavRules.get(d, team);
        if (u == null || !e.getUUID().equals(u.entityUuid)) return;
        UavEffects.explode(level, e.position());
        finish(level, d, team, EndKind.DESTROYED, e);
    }

    private static void finish(ServerLevel level, AASWorldData d, String team, EndKind kind, ReconUavEntity uav) {
        UavState u = UavRules.get(d, team);
        if (u == null) return;
        UavRules.set(d, team, null);
        if (uav != null && !uav.isRemoved()) uav.discard();
        if (kind != EndKind.MATCH_END) {
            UavRules.setCooldownEnd(d, team, level.getGameTime() + AASConfig.UAV_COOLDOWN_SECONDS.get() * 20L);
        }
        d.setDirty();
        UavExitReason reason = switch (kind) {
            case DESTROYED -> UavExitReason.DESTROYED;
            case EXPIRED -> UavExitReason.EXPIRED;
            case MATCH_END -> UavExitReason.MATCH_END;
            default -> UavExitReason.LOST;
        };
        UavViewManager.exitTeam(level, team, reason);
        syncTeam(level, d, team);
    }

    // ------------------------------------------------------------------ match hooks
    public static void onMatchStart(ServerLevel level, AASWorldData d) {
        for (String team : new String[]{"BLUE", "RED"}) {
            if (UavRules.get(d, team) != null) finish(level, d, team, EndKind.MATCH_END, null);
            UavRules.setCooldownEnd(d, team, level.getGameTime() + AASConfig.UAV_COOLDOWN_SECONDS.get() * 20L);
        }
        d.setDirty();
        syncTeam(level, d, "BLUE");
        syncTeam(level, d, "RED");
    }

    public static void onMatchEnd(ServerLevel level, AASWorldData d) {
        for (String team : new String[]{"BLUE", "RED"}) {
            UavState u = UavRules.get(d, team);
            if (u != null) {
                Entity e = level.getEntity(u.entityUuid);
                finish(level, d, team, EndKind.MATCH_END, e instanceof ReconUavEntity r ? r : null);
            }
            UavRules.setCooldownEnd(d, team, 0);
        }
        d.setDirty();
        syncTeam(level, d, "BLUE");
        syncTeam(level, d, "RED");
    }

    // ------------------------------------------------------------------ sync (own team only!)
    public static void syncTeam(ServerLevel level, AASWorldData d, String team) {
        PacketSyncUav pkt = PacketSyncUav.of(UavRules.get(d, team), UavRules.cooldownEnd(d, team));
        for (ServerPlayer p : level.players()) {
            if (UavRules.teamOf(p).equals(team)) {
                PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> p), pkt);
            }
        }
    }

    public static void syncPlayer(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        AASWorldData d = AASWorldData.get(level);
        String team = UavRules.teamOf(p);
        PacketSyncUav pkt = UavRules.isTeam(team)
                ? PacketSyncUav.of(UavRules.get(d, team), UavRules.cooldownEnd(d, team))
                : PacketSyncUav.of(null, 0);
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> p), pkt);
    }
}
