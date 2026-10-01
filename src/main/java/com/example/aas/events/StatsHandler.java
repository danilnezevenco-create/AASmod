package com.example.aas.events;

import com.example.aas.util.VehicleCatalog;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;

import java.util.HashMap;
import java.util.Map;

public class StatsHandler {

    // Ключи в player.getPersistentData()
    public static final String KEY_TP = "AAS_Stats_TeamPoints";
    public static final String KEY_SP = "AAS_Stats_SquadPoints";
    public static final String KEY_KILLS = "AAS_Stats_Kills";
    public static final String KEY_DEATHS = "AAS_Stats_Deaths";
    public static final String KEY_VEH_TOTAL = "AAS_Stats_VehKills_TOTAL";
    public static final String KEY_REVIVES = "AAS_Stats_Revives";
    private static final String KEY_VEH_PREFIX = "AAS_Stats_VehKills_";

    // Кулдауны анти-фарма: ключ -> тик последнего начисления
    private static final Map<String, Long> COOLDOWNS = new HashMap<>();

    public static String vehKey(VehicleCatalog.Category category) {
        return KEY_VEH_PREFIX + category.name();
    }

    public static void addStats(ServerPlayer player, int teamPoints, int squadPoints, String reason) {
        if (player == null) return;

        int currentTP = player.getPersistentData().getInt("AAS_Stats_TeamPoints");
        int currentSP = player.getPersistentData().getInt("AAS_Stats_SquadPoints");

        if (teamPoints > 0) player.getPersistentData().putInt("AAS_Stats_TeamPoints", currentTP + teamPoints);
        if (squadPoints > 0) player.getPersistentData().putInt("AAS_Stats_SquadPoints", currentSP + squadPoints);

        // УВЕДОМЛЕНИЕ УДАЛЕНО (теперь очки даются молча)
        /*
        player.displayClientMessage(
                Component.literal(tpStr + spStr + "(" + reason + ")").withStyle(ChatFormatting.AQUA),
                true
        );
        */
    }

    // ================= НОВОЕ: начисление по типу (значения из ScoreValues) =================

    /** Начисляет очки по типу ScoreType, значения берутся из ScoreValues. */
    public static void addScore(ServerPlayer player, ScoreType type) {
        if (player == null || type == null) return;
        applyScore(player, type.teamPoints, type.squadPoints, type);
    }

    /** Начисляет очки по типу, но с процентом от базовых значений (например, 50% вкладчикам стройки). */
    public static void addScoreScaled(ServerPlayer player, ScoreType type, int percent) {
        if (player == null || type == null) return;
        int tp = type.teamPoints * percent / 100;
        int sp = type.squadPoints * percent / 100;
        applyScore(player, tp, sp, type);
    }

    /** Начисляет произвольные очки, но с причиной/локализацией из ScoreType (техника, перевозка). */
    public static void addScoreCustom(ServerPlayer player, ScoreType type, int teamPoints, int squadPoints) {
        if (player == null || type == null) return;
        applyScore(player, teamPoints, squadPoints, type);
    }

    public static void addScoreByName(MinecraftServer server, String playerName, ScoreType type) {
        if (server == null || playerName == null || playerName.isEmpty()) return;
        ServerPlayer player = server.getPlayerList().getPlayerByName(playerName);
        if (player != null) addScore(player, type);
    }

    private static void applyScore(ServerPlayer player, int teamPoints, int squadPoints, ScoreType type) {
        if (teamPoints <= 0 && squadPoints <= 0) return;
        CompoundTag data = player.getPersistentData();
        if (teamPoints > 0) data.putInt(KEY_TP, data.getInt(KEY_TP) + teamPoints);
        if (squadPoints > 0) data.putInt(KEY_SP, data.getInt(KEY_SP) + squadPoints);
        // Уведомления о начислении отключены - очки даются молча
    }

    // ================= АНТИ-ФАРМ =================

    /**
     * true - действие разрешено (и кулдаун запущен), false - ещё на кулдауне.
     * key должен включать тип действия и участников, например "revive:<reviver>:<target>".
     */
    public static boolean tryCooldown(String key, long nowTick, long cooldownTicks) {
        Long last = COOLDOWNS.get(key);
        if (last != null && nowTick >= last && nowTick - last < cooldownTicks) return false;
        COOLDOWNS.put(key, nowTick);
        if (COOLDOWNS.size() > 512) {
            COOLDOWNS.values().removeIf(t -> nowTick < t || nowTick - t > ScoreValues.MAX_COOLDOWN_TICKS);
        }
        return true;
    }

    // ================= K/D и техника =================

    public static void addKill(ServerPlayer killer) {
        if (killer == null) return;
        int current = killer.getPersistentData().getInt("AAS_Stats_Kills");
        killer.getPersistentData().putInt("AAS_Stats_Kills", current + 1);
    }

    public static void addDeath(ServerPlayer victim) {
        if (victim == null) return;
        int current = victim.getPersistentData().getInt("AAS_Stats_Deaths");
        victim.getPersistentData().putInt("AAS_Stats_Deaths", current + 1);
    }

    /** +1 к счётчику поднятых игроков. */
    public static void addRevive(ServerPlayer reviver) {
        if (reviver == null) return;
        CompoundTag data = reviver.getPersistentData();
        data.putInt(KEY_REVIVES, data.getInt(KEY_REVIVES) + 1);
    }

    public static int getRevives(ServerPlayer player) {
        return player.getPersistentData().getInt(KEY_REVIVES);
    }

    /** +1 к счётчикам уничтоженной техники убийцы: по категории и общий. */
    public static void addVehicleKill(ServerPlayer killer, VehicleCatalog.Category category) {
        if (killer == null) return;
        if (category == null) category = VehicleCatalog.Category.OTHER;
        CompoundTag data = killer.getPersistentData();
        String key = vehKey(category);
        data.putInt(key, data.getInt(key) + 1);
        data.putInt(KEY_VEH_TOTAL, data.getInt(KEY_VEH_TOTAL) + 1);
    }

    /** Счётчики по категориям в порядке VehicleCatalog.Category.values(). */
    public static int[] getVehKillsByCategory(ServerPlayer player) {
        VehicleCatalog.Category[] all = VehicleCatalog.Category.values();
        int[] result = new int[all.length];
        CompoundTag data = player.getPersistentData();
        for (int i = 0; i < all.length; i++) result[i] = data.getInt(vehKey(all[i]));
        return result;
    }

    public static int getVehKillsTotal(ServerPlayer player) {
        return player.getPersistentData().getInt(KEY_VEH_TOTAL);
    }

    /** Полный сброс статистики игрока (старт матча). */
    public static void resetStats(ServerPlayer player) {
        CompoundTag data = player.getPersistentData();
        data.putInt(KEY_TP, 0);
        data.putInt(KEY_SP, 0);
        data.putInt(KEY_KILLS, 0);
        data.putInt(KEY_DEATHS, 0);
        data.putInt(KEY_VEH_TOTAL, 0);
        data.putInt(KEY_REVIVES, 0);
        for (VehicleCatalog.Category c : VehicleCatalog.Category.values()) data.putInt(vehKey(c), 0);
    }

    /** Копирование статистики при клоне игрока (только те ключи, которые есть у старого игрока). */
    public static void copyStats(CompoundTag oldData, CompoundTag newData) {
        copyInt(oldData, newData, KEY_TP);
        copyInt(oldData, newData, KEY_SP);
        copyInt(oldData, newData, KEY_KILLS);
        copyInt(oldData, newData, KEY_DEATHS);
        copyInt(oldData, newData, KEY_VEH_TOTAL);
        copyInt(oldData, newData, KEY_REVIVES);
        for (VehicleCatalog.Category c : VehicleCatalog.Category.values()) copyInt(oldData, newData, vehKey(c));
    }

    private static void copyInt(CompoundTag from, CompoundTag to, String key) {
        if (from.contains(key)) to.putInt(key, from.getInt(key));
    }

    public static void addStatsByName(MinecraftServer server, String playerName, int teamPoints, int squadPoints, String reason) {
        if (playerName == null || playerName.isEmpty()) return;
        ServerPlayer player = server.getPlayerList().getPlayerByName(playerName);
        if (player != null) {
            addStats(player, teamPoints, squadPoints, reason);
        }
    }
}