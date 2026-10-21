// PATH: src\main\java\com\example\aas\world\VehicleRegistryRebuilder.java
package com.example.aas.world;

import com.example.aas.block.ModBlocks;
import com.example.aas.block.VehicleSpawnerBlockEntity;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Восстанавливает VehicleSpawnerRegistry (список спавнеров техники) прямо из файлов мира
 * (region/*.mca), НЕ загружая чанки. Благодаря этому список техники (панель техники) полный
 * сразу после запуска сервера, даже если никто из игроков ещё не подгружал чанки со спавнерами.
 *
 * Запускается:
 *  - автоматически при старте сервера, если реестр измерения пуст;
 *  - командой /aas rebuildvehiclelist (принудительно, для всех измерений).
 * Чанки читаются порциями по CHUNKS_PER_TICK за тик, чтобы не вешать сервер.
 */
@Mod.EventBusSubscriber(modid = "aas", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class VehicleRegistryRebuilder {

    private static final int CHUNKS_PER_TICK = 32;
    private static final String SPAWNER_BE_ID = "aas:vehicle_spawner";
    private static final Pattern REGION_FILE = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");

    private static final class Job {
        final ServerLevel level;
        final Deque<ChunkPos> queue = new ArrayDeque<>();
        final Set<BlockPos> found = new HashSet<>();
        final CommandSourceStack source;   // может быть null (автозапуск)
        int total, scanned;

        Job(ServerLevel level, CommandSourceStack source) {
            this.level = level;
            this.source = source;
        }
    }

    private static final List<Job> JOBS = new ArrayList<>();

    private VehicleRegistryRebuilder() {}

    // ------------------------------------------------------------------ запуск

    /** Автозапуск: если реестр измерения пуст — пересобираем из мира. */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        for (ServerLevel level : server.getAllLevels()) {
            if (VehicleSpawnerRegistry.get(level).copy().isEmpty()) {
                start(level, null);
            }
        }
    }

    /** Команда /aas rebuildvehiclelist: принудительно для всех измерений. */
    public static int startFromCommand(CommandSourceStack source) {
        int started = 0;
        for (ServerLevel level : source.getServer().getAllLevels()) {
            if (start(level, source)) started++;
        }
        final int n = started;
        source.sendSuccess(() -> Component.literal("Vehicle list rebuild started for " + n
                + " dimension(s). Scanning world files in the background..."), true);
        return n;
    }

    private static boolean start(ServerLevel level, CommandSourceStack source) {
        for (Job j : JOBS) if (j.level == level) return false;   // уже идёт

        Job job = new Job(level, source);
        try {
            Path dimFolder = DimensionType.getStorageFolder(level.dimension(), level.getServer().getWorldPath(LevelResource.ROOT));
            Path regionDir = dimFolder.resolve("region");
            if (Files.isDirectory(regionDir)) {
                try (Stream<Path> files = Files.list(regionDir)) {
                    files.forEach(p -> {
                        Matcher m = REGION_FILE.matcher(p.getFileName().toString());
                        if (!m.matches()) return;
                        int rx = Integer.parseInt(m.group(1));
                        int rz = Integer.parseInt(m.group(2));
                        for (int dz = 0; dz < 32; dz++) {
                            for (int dx = 0; dx < 32; dx++) {
                                job.queue.add(new ChunkPos(rx * 32 + dx, rz * 32 + dz));
                            }
                        }
                    });
                }
            }
        } catch (IOException | RuntimeException e) {
            System.err.println("[AAS] vehicle registry rebuild: cannot list region files of "
                    + level.dimension().location() + ": " + e);
            return false;
        }
        job.total = job.queue.size();
        if (job.total == 0) return false;
        JOBS.add(job);
        System.out.println("[AAS] vehicle registry rebuild started: " + level.dimension().location()
                + " (" + job.total + " chunk slots)");
        return true;
    }

    // ------------------------------------------------------------------ работа по тикам

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || JOBS.isEmpty()) return;

        Iterator<Job> it = JOBS.iterator();
        while (it.hasNext()) {
            Job job = it.next();
            for (int i = 0; i < CHUNKS_PER_TICK && !job.queue.isEmpty(); i++) {
                scanChunk(job, job.queue.poll());
                job.scanned++;
            }
            if (job.queue.isEmpty()) {
                finish(job);
                it.remove();
            }
        }
    }

    private static void scanChunk(Job job, ChunkPos pos) {
        try {
            Optional<CompoundTag> opt = job.level.getChunkSource().chunkMap.read(pos).join();
            if (opt.isEmpty()) return;
            CompoundTag chunk = opt.get();
            if (!chunk.contains("block_entities", Tag.TAG_LIST)) return;

            ListTag list = chunk.getList("block_entities", Tag.TAG_COMPOUND);
            VehicleSpawnerRegistry registry = VehicleSpawnerRegistry.get(job.level);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag beTag = list.getCompound(i);
                if (!SPAWNER_BE_ID.equals(beTag.getString("id"))) continue;

                BlockPos bp = new BlockPos(beTag.getInt("x"), beTag.getInt("y"), beTag.getInt("z"));
                BlockState state = ModBlocks.VEHICLE_SPAWNER_BLOCK.get().defaultBlockState();
                VehicleSpawnerBlockEntity be = new VehicleSpawnerBlockEntity(bp, state);
                be.load(beTag);   // читает инвентарь, vehicleId, таймеры — так же, как при обычной загрузке чанка
                registry.put(bp, VehicleSpawnerRegistry.Snapshot.of(be));
                job.found.add(bp.immutable());
            }
        } catch (Exception e) {
            // битый/недоступный чанк пропускаем, остальные продолжаем
        }
    }

    private static void finish(Job job) {
        // Спавнеры, которых в мире (на диске) уже нет, из реестра убираем (кроме тех, что в загруженных чанках —
        // их и так обновляет VehicleListService).
        VehicleSpawnerRegistry registry = VehicleSpawnerRegistry.get(job.level);
        int removed = 0;
        for (BlockPos pos : registry.copy().keySet()) {
            if (!job.found.contains(pos) && !job.level.isLoaded(pos)) {
                registry.unregister(pos);
                removed++;
            }
        }
        String msg = "Vehicle list rebuilt for " + job.level.dimension().location() + ": "
                + job.found.size() + " spawner(s) found" + (removed > 0 ? ", " + removed + " stale removed" : "") + ".";
        System.out.println("[AAS] " + msg);
        if (job.source != null) job.source.sendSuccess(() -> Component.literal(msg), true);
    }
}