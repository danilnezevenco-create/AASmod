package com.example.aas.util;

import com.example.aas.block.VehicleSpawnerBlockEntity;
import com.example.aas.network.PacketSyncVehicleList;
import com.example.aas.world.AASWorldData;
import com.example.aas.world.VehicleSpawnerRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.scores.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Server side: builds the per-type vehicle summary for one player.
 *
 * The per-level summary is cached for CACHE_TICKS (1 second), so even if many players poll
 * at once the spawner registry is walked at most once per second per level.
 */
public final class VehicleListService {

    private VehicleListService() {}

    private static final long CACHE_TICKS = 20L;

    /** Aggregated data for one vehicle type (vehicleIdString) of one team. */
    private static final class Agg {
        VehicleCatalog.Category category;
        int total;
        int alive;
        long nextRespawnTarget = -1;   // абсолютный тик, -1 = неизвестно/нет
        int penalty;
        String markerType = "";
    }

    private static final class Cached {
        long tick = Long.MIN_VALUE;
        Map<String, Map<String, Agg>> byTeam = new HashMap<>();
    }

    private static final Map<ResourceKey<Level>, Cached> CACHE = new HashMap<>();

    // ---------------------------------------------------------------- public API

    /** Builds the packet content for this player. Must be called on the server thread. */
    public static PacketSyncVehicleList buildFor(ServerPlayer player) {
        boolean privileged = player.isCreative() || player.isSpectator();
        String own = teamOf(player);

        // A player without a team (and not creative/spectator) gets an empty list -> "No data".
        if (own == null && !privileged) return PacketSyncVehicleList.empty();

        // "Allied" side: the player's own team. Creative/spectator players without a team are shown
        // BLUE as the allied side. Privileged players get full data (alive + timers) for BOTH sides.
        String ally = own != null ? own : "BLUE";
        String enemy = ally.equals("BLUE") ? "RED" : "BLUE";

        Map<String, Map<String, Agg>> summary = summaryFor(player.serverLevel());
        List<PacketSyncVehicleList.Entry> allyList = toEntries(summary.get(ally), true);
        // Enemy side: alive counts and respawn timers are NOT put on the wire (no intel leak),
        // unless the viewer is creative/spectator.
        List<PacketSyncVehicleList.Entry> enemyList = toEntries(summary.get(enemy), privileged);
        return new PacketSyncVehicleList(true, privileged, ally, allyList, enemyList);
    }

    // ---------------------------------------------------------------- internals

    /** "BLUE" / "RED" or null. Team names are compared case-insensitively, like the rest of the mod. */
    private static String teamOf(ServerPlayer player) {
        Team t = player.getTeam();
        if (t == null) return null;
        String n = t.getName();
        if (n.equalsIgnoreCase("Blue")) return "BLUE";
        if (n.equalsIgnoreCase("Red")) return "RED";
        return null;
    }

    private static List<PacketSyncVehicleList.Entry> toEntries(Map<String, Agg> map, boolean full) {
        List<PacketSyncVehicleList.Entry> out = new ArrayList<>();
        if (map == null) return out;
        for (Map.Entry<String, Agg> e : map.entrySet()) {
            Agg a = e.getValue();
            out.add(new PacketSyncVehicleList.Entry(
                    e.getKey(), a.category.ordinal(), a.total,
                    full ? a.alive : -1,
                    full ? a.nextRespawnTarget : -1L,
                    a.penalty,
                    a.markerType));
        }
        return out;
    }

    private static Map<String, Map<String, Agg>> summaryFor(ServerLevel level) {
        Cached c = CACHE.computeIfAbsent(level.dimension(), k -> new Cached());
        long now = level.getGameTime();
        if (c.tick == Long.MIN_VALUE || now - c.tick >= CACHE_TICKS || now < c.tick) {
            c.byTeam = compute(level, now);
            c.tick = now;
        }
        return c.byTeam;
    }

    private static Map<String, Map<String, Agg>> compute(ServerLevel level, long now) {
        AASWorldData data = AASWorldData.get(level);
        VehicleSpawnerRegistry registry = VehicleSpawnerRegistry.get(level);

        // A spawner is "alive" if its vehicle record exists (global list, independent of chunk loading).
        Set<BlockPos> aliveSpawners = new HashSet<>();
        for (AASWorldData.VehicleRecord v : data.markedVehicles) {
            if (v.spawnerPos != null) aliveSpawners.add(v.spawnerPos);
        }

        Map<String, Map<String, Agg>> result = new HashMap<>();
        result.put("BLUE", new HashMap<>());
        result.put("RED", new HashMap<>());

        for (Map.Entry<BlockPos, VehicleSpawnerRegistry.Snapshot> en : registry.copy().entrySet()) {
            BlockPos pos = en.getKey();
            VehicleSpawnerRegistry.Snapshot s = en.getValue();

            // Loaded chunk: refresh the snapshot from the live block entity.
            if (level.isLoaded(pos)) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof VehicleSpawnerBlockEntity spawner) {
                    s = VehicleSpawnerRegistry.Snapshot.of(spawner);
                    registry.put(pos, s);
                } else {
                    registry.unregister(pos);   // block is gone
                    continue;
                }
            }

            Map<String, Agg> teamMap = result.get(s.team());
            if (teamMap == null) continue;                    // NEUTRAL / no marker: not shown
            if (s.vehicleId().isEmpty()) continue;            // spawner without a vehicle id

            final String markerType = s.markerType();
            Agg a = teamMap.computeIfAbsent(s.vehicleId(), k -> {
                Agg n = new Agg();
                n.category = VehicleCatalog.resolve(k, markerType);
                n.markerType = markerType == null ? "" : markerType;   // <-- ДОБАВИТЬ
                return n;
            });
            a.total++;
            a.penalty = Math.max(a.penalty, s.penalty());

            if (aliveSpawners.contains(pos)) {
                a.alive++;
                continue;
            }

            // Waiting for (re)spawn. Global deadline first (set the instant the vehicle dies, works even
            // in unloaded chunks), then the spawner's own targetSpawnTick (initial spawn countdown).
            Long pending = data.pendingSpawnerRespawns.get(pos);
            long target = pending != null ? pending : s.targetSpawnTick();
            if (target > 0) {
                a.nextRespawnTarget = a.nextRespawnTarget < 0 ? target : Math.min(a.nextRespawnTarget, target);
            }
        }
        return result;
    }
}