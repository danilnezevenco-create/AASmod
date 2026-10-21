// PATH: src/main/java/com/example/aas/events/OfflineStats.java
package com.example.aas.events;

import com.example.aas.network.PlayerStatInfo;
import com.example.aas.world.AASWorldData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * "Замороженная" статистика игроков, которые вышли с сервера.
 *
 * - При выходе игрока (BLUE/RED) его счёт запоминается как снимок.
 * - Снимок показывается в таблице на Caps Lock (с серым значком пинга) и учитывается
 *   в итогах матча (лучший отряд / медик / логист) до тех пор, пока статистику не сбросят
 *   (старт нового матча: /aas gamestart true).
 * - Если игрок вернулся - снимок удаляется, дальше идут его живые данные.
 * - Если игрок не был онлайн в момент сброса, обнулить его счёт можно только когда он зайдёт
 *   (его данные лежат в playerdata), поэтому имя запоминается в PENDING_RESET.
 *
 * Хранится в памяти: при перезапуске сервера снимки теряются.
 */
@Mod.EventBusSubscriber(modid = "aas")
public final class OfflineStats {

    public static final class Snap {
        public String name;
        public String team;   // "BLUE" / "RED"
        public String dim;    // id измерения, в котором игрок вышел
        public int kills, deaths, tp, sp, revives, vehTotal, logi;
        public int[] vehByCat = new int[0];
    }

    // ник (нижний регистр) -> снимок
    private static final Map<String, Snap> SNAPS = new LinkedHashMap<>();
    // ники, которым нужно обнулить счёт при следующем входе
    private static final Set<String> PENDING_RESET = new HashSet<>();

    private OfflineStats() {}

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    // ================== СОБЫТИЯ ==================

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        if (p.getTeam() == null) return;
        String team = p.getTeam().getName().toUpperCase();
        if (!team.equals("BLUE") && !team.equals("RED")) return;

        var tag = p.getPersistentData();
        Snap s = new Snap();
        s.name = p.getScoreboardName();
        s.team = team;
        s.dim = p.serverLevel().dimension().location().toString();
        s.kills = tag.getInt(StatsHandler.KEY_KILLS);
        s.deaths = tag.getInt(StatsHandler.KEY_DEATHS);
        s.tp = tag.getInt(StatsHandler.KEY_TP);
        s.sp = tag.getInt(StatsHandler.KEY_SP);
        s.revives = StatsHandler.getRevives(p);
        s.vehTotal = StatsHandler.getVehKillsTotal(p);
        s.logi = StatsHandler.getLogisticsPoints(p);
        s.vehByCat = StatsHandler.getVehKillsByCategory(p);
        SNAPS.put(key(s.name), s);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        String k = key(p.getScoreboardName());
        SNAPS.remove(k); // игрок снова онлайн - дальше его живые данные
        if (PENDING_RESET.remove(k)) {
            StatsHandler.resetStats(p); // статистику сбросили, пока он был офлайн
        }
    }

    // ================== API ==================

    /** Сброс статистики (старт матча): снимки пропадают, офлайн-игрокам счёт обнулится при входе. */
    public static void resetAll() {
        PENDING_RESET.addAll(SNAPS.keySet());
        SNAPS.clear();
    }

    /** Снимки игроков, которых сейчас нет на сервере, для указанного измерения. */
    public static List<Snap> offline(MinecraftServer server, String dim) {
        List<Snap> out = new ArrayList<>();
        for (Snap s : SNAPS.values()) {
            if (!s.dim.equals(dim)) continue;
            if (server.getPlayerList().getPlayerByName(s.name) != null) continue; // онлайн (например, в другом измерении)
            out.add(s);
        }
        return out;
    }

    /** Строки для таблицы на Caps Lock. ping = -1 -> клиент рисует серый значок. */
    public static List<PlayerStatInfo> buildInfos(MinecraftServer server, String dim, AASWorldData data) {
        List<PlayerStatInfo> out = new ArrayList<>();
        for (Snap s : offline(server, dim)) {
            int squadId = -1;
            boolean isLeader = false;
            for (AASWorldData.Squad sq : data.squads) {
                if (sq.members.contains(s.name)) {
                    squadId = sq.id;
                    isLeader = sq.leader.equals(s.name);
                    break;
                }
            }
            boolean isCMD = (s.team.equals("BLUE") && squadId == data.blueCMDId && data.blueCMDId != -1)
                    || (s.team.equals("RED") && squadId == data.redCMDId && data.redCMDId != -1);

            out.add(new PlayerStatInfo(s.name, s.team, squadId, isLeader, isCMD,
                    s.kills, s.deaths, s.tp, s.sp, s.revives, s.vehTotal, s.vehByCat, -1));
        }
        return out;
    }
}