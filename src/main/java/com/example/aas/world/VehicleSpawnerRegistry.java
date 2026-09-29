package com.example.aas.world;

import com.example.aas.block.VehicleSpawnerBlockEntity;
import com.example.aas.item.SupplyTruckMarkerItem;
import com.example.aas.item.VehicleMarkerItem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

/**
 * Persistent list of every vehicle spawner block known in a level, with a snapshot of its config.
 *
 * WHY: spawner block entities only exist (and tick) in loaded chunks, and the mod has no global
 * list of them (only markedVehicles / pendingSpawnerRespawns, which hold spawners that ALREADY
 * spawned a vehicle). The panel needs the "total" count per type, so we keep our own registry.
 *
 * Spawners register themselves in VehicleSpawnerBlockEntity#onLoad and are removed in
 * VehicleSpawnerBlock#onRemove. While a chunk is loaded the snapshot is refreshed from the live
 * block entity by VehicleListService; for unloaded chunks the last snapshot is used.
 *
 * Stored separately from AASWorldData on purpose, so AASWorldData does not have to be patched.
 */
public class VehicleSpawnerRegistry extends SavedData {

    private static final String DATA_NAME = "aas_vehicle_spawner_registry";

    /** Last known config of one spawner. */
    public record Snapshot(String team, String markerType, int penalty, String vehicleId,
                           boolean hasSpawnedOnce, long targetSpawnTick) {

        /**
         * Reads the same data the spawner uses when it spawns a vehicle:
         * team / type / penalty come from the marker item in slot 0 (see VehicleSpawnerBlockEntity#spawnVehicle).
         */
        public static Snapshot of(VehicleSpawnerBlockEntity be) {
            String team = "NEUTRAL";
            String type = "";
            int penalty = 0;
            ItemStack modifier = be.inventory.getStackInSlot(0);
            if (modifier.getItem() instanceof VehicleMarkerItem vm) {
                team = vm.getTeam();
                type = vm.getType();
                penalty = vm.getPenalty();
            } else if (modifier.getItem() instanceof SupplyTruckMarkerItem stm) {
                team = stm.getTeam();
                type = stm.getVehicleType();
                penalty = stm.getPenalty();
            }
            String id = be.vehicleIdString == null ? "" : be.vehicleIdString.trim();
            return new Snapshot(team, type, penalty, id, be.hasSpawnedOnce, be.targetSpawnTick);
        }
    }

    private final Map<BlockPos, Snapshot> spawners = new HashMap<>();

    public static VehicleSpawnerRegistry get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(VehicleSpawnerRegistry::load, VehicleSpawnerRegistry::new, DATA_NAME);
    }

    /** Adds the spawner, or refreshes its snapshot if it changed. */
    public void put(BlockPos pos, Snapshot snapshot) {
        Snapshot old = spawners.put(pos.immutable(), snapshot);
        if (!snapshot.equals(old)) setDirty();
    }

    public void unregister(BlockPos pos) {
        if (spawners.remove(pos) != null) setDirty();
    }

    /** Copy, so callers can modify the registry while iterating. */
    public Map<BlockPos, Snapshot> copy() {
        return new HashMap<>(spawners);
    }

    // ------------------------------------------------------------------ persistence
    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Map.Entry<BlockPos, Snapshot> e : spawners.entrySet()) {
            Snapshot s = e.getValue();
            CompoundTag t = new CompoundTag();
            t.putLong("Pos", e.getKey().asLong());
            t.putString("Team", s.team());
            t.putString("Type", s.markerType());
            t.putInt("Penalty", s.penalty());
            t.putString("VehicleId", s.vehicleId());
            t.putBoolean("Spawned", s.hasSpawnedOnce());
            t.putLong("Target", s.targetSpawnTick());
            list.add(t);
        }
        tag.put("Spawners", list);
        return tag;
    }

    public static VehicleSpawnerRegistry load(CompoundTag tag) {
        VehicleSpawnerRegistry reg = new VehicleSpawnerRegistry();
        ListTag list = tag.getList("Spawners", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            reg.spawners.put(BlockPos.of(t.getLong("Pos")), new Snapshot(
                    t.getString("Team"), t.getString("Type"), t.getInt("Penalty"),
                    t.getString("VehicleId"), t.getBoolean("Spawned"), t.getLong("Target")));
        }
        return reg;
    }
}