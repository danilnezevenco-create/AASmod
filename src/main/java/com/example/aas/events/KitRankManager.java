package com.example.aas.events;

import com.example.aas.config.AASConfig;
import com.example.aas.network.PacketHandler;
import com.example.aas.network.PacketSyncKitRanks;
import com.example.aas.world.AASWorldData;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Ранги китов.
 *
 * - Время игры на ките (AAS_CurrentKit, НЕ AAS_PendingKit) копится в секундах; 600 сек (10 мин) = 1 очко.
 * - Данные привязаны к НИКУ игрока и хранятся в aas_kit_ranks.json в корне игры/сервера
 *   (рядом с папками mods, config и т.д.).
 * - Хранится не только очки, но и остаток секунд до следующего очка, чтобы время не сгорало.
 * - Накопление идёт всегда; конфиг showKitRanks управляет только показом на клиенте.
 *
 * Формат файла:
 * {
 *   "Nick": {
 *     "Medic": { "level": 3, "points": 7, "seconds": 120 }
 *   }
 * }
 */
@Mod.EventBusSubscriber(modid = "aas")
public final class KitRankManager {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final int MAX_LEVEL = 20;
    /** 10 минут игры = 1 очко. */
    public static final int SECONDS_PER_POINT = 10 * 60;

    private static final String FILE_NAME = "aas_kit_ranks.json";
    private static final int AUTOSAVE_TICKS = 20 * 60; // раз в минуту, если есть изменения

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static final class Entry {
        public int level = 1;
        public int points = 0;
        public int seconds = 0;
    }

    // ник (без учёта регистра) -> название кита -> прогресс
    private static final Map<String, Map<String, Entry>> DATA = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    // последний известный кит игрока (для сохранения при смене кита)
    private static final Map<UUID, String> LAST_KIT = new HashMap<>();

    private static boolean dirty = false;
    private static long tickCounter = 0;

    // что последний раз отправили клиентам
    private static Boolean lastSentShow = null;
    private static Map<String, Integer> lastSentRanks = null;

    // ================== ПРАВИЛА УРОВНЕЙ ==================

    /** Сколько очков нужно, чтобы с уровня {@code level} перейти на {@code level + 1}. */
    public static int costToNext(int level) {
        if (level <= 4) return 3;
        if (level <= 10) return 6;
        if (level <= 12) return 8;
        if (level <= 16) return 10;
        return 12; // 17-20
    }

    /** Уровень (1..20) по суммарному количеству очков. Все стартуют с 1 уровня. */
    public static int levelForPoints(int points) {
        int level = 1;
        int rem = Math.max(0, points);
        while (level < MAX_LEVEL) {
            int cost = costToNext(level);
            if (rem < cost) break;
            rem -= cost;
            level++;
        }
        return level;
    }

    // ================== ПУБЛИЧНЫЙ API ==================

    /** Уровень игрока на ките. Если записи ещё нет — 1. */
    public static int getLevel(String nick, String kit) {
        Entry e = getEntry(nick, kit);
        return e == null ? 1 : e.level;
    }

    public static Entry getEntry(String nick, String kit) {
        Map<String, Entry> kits = DATA.get(nick);
        return kits == null ? null : kits.get(kit);
    }

    /** Задел на будущее: начислить очки киту напрямую (не временем). */
    public static void addPoints(String nick, String kit, int points) {
        if (points <= 0 || !isRankedKit(kit)) return;
        Entry e = entry(nick, kit);
        e.points += points;
        e.level = levelForPoints(e.points);
        dirty = true;
    }

    private static boolean isRankedKit(String kit) {
        return kit != null && !kit.isEmpty() && !kit.equalsIgnoreCase("Unassigned");
    }

    private static Entry entry(String nick, String kit) {
        return DATA.computeIfAbsent(nick, k -> new TreeMap<>()).computeIfAbsent(kit, k -> new Entry());
    }

    // ================== ЖИЗНЕННЫЙ ЦИКЛ ==================

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        load();
        LAST_KIT.clear();
        lastSentShow = null;
        lastSentRanks = null;
        tickCounter = 0;
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        save();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;
        boolean show = AASConfig.SHOW_KIT_RANKS.get();
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                new PacketSyncKitRanks(show, show ? buildRanks(server) : new HashMap<>()));
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        LAST_KIT.remove(player.getUUID());
        if (dirty) save();
    }

    // ================== ТИК ==================

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        tickCounter++;
        if (tickCounter % 20 != 0) return; // раз в секунду

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        AASWorldData data = AASWorldData.get(server.overworld());
        boolean flushNeeded = false;

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            // Только реально выданный кит. AAS_PendingKit (бронь) не учитывается.
            String kit = p.getPersistentData().getString("AAS_CurrentKit");

            String prev = LAST_KIT.put(p.getUUID(), kit);
            if (prev != null && !prev.equals(kit)) flushNeeded = true;

            if (!isRankedKit(kit)) continue;
            if (!data.isGameStarted) continue;
            if (!p.isAlive() || p.isSpectator()) continue;

            addSecond(p.getGameProfile().getName(), kit);
        }

        syncToClientsIfChanged(server);

        if (flushNeeded || (tickCounter % AUTOSAVE_TICKS == 0)) {
            if (dirty) save();
        }
    }

    private static void addSecond(String nick, String kit) {
        Entry e = entry(nick, kit);
        e.seconds++;
        if (e.seconds >= SECONDS_PER_POINT) {
            e.seconds -= SECONDS_PER_POINT;
            e.points++;
            e.level = levelForPoints(e.points);
        }
        dirty = true;
    }

    // ================== СИНХРОНИЗАЦИЯ С КЛИЕНТАМИ ==================

    /** ник -> уровень на текущем ките, только для игроков онлайн с назначенным китом. */
    private static Map<String, Integer> buildRanks(MinecraftServer server) {
        Map<String, Integer> ranks = new HashMap<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            String kit = p.getPersistentData().getString("AAS_CurrentKit");
            if (!isRankedKit(kit)) continue;
            String nick = p.getGameProfile().getName();
            ranks.put(nick, getLevel(nick, kit));
        }
        return ranks;
    }

    private static void syncToClientsIfChanged(MinecraftServer server) {
        boolean show = AASConfig.SHOW_KIT_RANKS.get();
        Map<String, Integer> ranks = show ? buildRanks(server) : new HashMap<>();

        if (lastSentShow != null && lastSentShow == show && ranks.equals(lastSentRanks)) return;

        lastSentShow = show;
        lastSentRanks = ranks;
        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new PacketSyncKitRanks(show, ranks));
    }

    // ================== ФАЙЛ ==================

    private static Path file() {
        return FMLPaths.GAMEDIR.get().resolve(FILE_NAME);
    }

    private static void load() {
        DATA.clear();
        dirty = false;

        Path file = file();
        if (!Files.exists(file)) return;

        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Type type = new TypeToken<Map<String, Map<String, Entry>>>() {}.getType();
            Map<String, Map<String, Entry>> loaded = GSON.fromJson(r, type);
            if (loaded == null) return;

            for (Map.Entry<String, Map<String, Entry>> nickEntry : loaded.entrySet()) {
                if (nickEntry.getValue() == null) continue;
                Map<String, Entry> kits = new TreeMap<>();
                for (Map.Entry<String, Entry> kitEntry : nickEntry.getValue().entrySet()) {
                    Entry e = kitEntry.getValue();
                    if (e == null) continue;
                    if (e.points < 0) e.points = 0;
                    if (e.seconds < 0) e.seconds = 0;
                    // если в файле руками выставили много секунд — переводим в очки
                    if (e.seconds >= SECONDS_PER_POINT) {
                        e.points += e.seconds / SECONDS_PER_POINT;
                        e.seconds = e.seconds % SECONDS_PER_POINT;
                    }
                    e.level = levelForPoints(e.points); // источник истины — очки
                    kits.put(kitEntry.getKey(), e);
                }
                DATA.put(nickEntry.getKey(), kits);
            }
            LOGGER.info("[AAS] Kit ranks loaded: {} players", DATA.size());
        } catch (JsonParseException | IOException ex) {
            // Не затираем битый файл молча: откладываем в сторону и стартуем с пустого.
            LOGGER.error("[AAS] Failed to read " + FILE_NAME + ", moving it aside", ex);
            try {
                Path bad = file.resolveSibling(FILE_NAME + ".broken-" + System.currentTimeMillis());
                Files.move(file, bad, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
            }
            DATA.clear();
        }
    }

    public static void save() {
        Path file = file();
        Path tmp = file.resolveSibling(FILE_NAME + ".tmp");
        try {
            try (Writer w = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
                GSON.toJson(DATA, w);
            }
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            dirty = false;
        } catch (IOException ex) {
            LOGGER.error("[AAS] Failed to save " + FILE_NAME, ex);
        }
    }

    private KitRankManager() {
    }
}