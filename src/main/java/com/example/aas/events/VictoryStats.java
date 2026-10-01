package com.example.aas.events;

import com.example.aas.network.VictoryData;
import com.example.aas.world.AASWorldData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;

/**
 * Подсчёт итогов матча для экрана победы. Учитываются только игроки, которые
 * находятся в сети в момент победы. Реальные очки игроков не изменяются.
 */
public final class VictoryStats {

    private VictoryStats() {}

    /** Множитель отрядных очков — ТОЛЬКО для сравнения отрядов между собой. */
    public static final int SQUAD_POINTS_WEIGHT = 3;

    private record Entry(ServerPlayer player, String team, int squadId,
                         int tp, int sp, int revives, int logi) {}

    public static VictoryData build(ServerLevel level, AASWorldData data, boolean blueWon,
                                    String blueName, String redName) {
        VictoryData out = new VictoryData();
        out.blueWon = blueWon;
        out.blueTickets = data.blueTickets;
        out.redTickets = data.redTickets;
        out.blueFaction = data.blueFaction == null ? "none" : data.blueFaction;
        out.redFaction = data.redFaction == null ? "none" : data.redFaction;
        out.blueName = blueName;
        out.redName = redName;

        String dim = level.dimension().location().toString();

        // 1. Реальные номера отрядов: ровно как в SquadSelectionScreen / StatisticsScreen —
        //    внутри команды и измерения, CMD-отряд первым, остальные по возрастанию id, нумерация с 1.
        Map<Integer, Integer> numberById = new HashMap<>();
        Map<Integer, AASWorldData.Squad> squadById = new HashMap<>();
        for (String team : new String[]{"BLUE", "RED"}) {
            int cmdId = team.equals("BLUE") ? data.blueCMDId : data.redCMDId;
            List<AASWorldData.Squad> list = new ArrayList<>();
            for (AASWorldData.Squad s : data.squads) {
                if (s.team != null && s.team.equalsIgnoreCase(team) && s.dimension != null && s.dimension.equals(dim)) {
                    list.add(s);
                }
            }
            list.sort((a, b) -> {
                if (a.id == cmdId && cmdId != -1) return -1;
                if (b.id == cmdId && cmdId != -1) return 1;
                return Integer.compare(a.id, b.id);
            });
            for (int i = 0; i < list.size(); i++) {
                // id отрядов уникален глобально (см. PacketSquadAction), поэтому ключ — просто id
                numberById.put(list.get(i).id, i + 1);
                squadById.put(list.get(i).id, list.get(i));
            }
        }

        // 2. Игроки в сети с командой BLUE/RED (спектаторы не учитываются)
        List<Entry> entries = new ArrayList<>();
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || p.getTeam() == null) continue;
            String team = p.getTeam().getName().toUpperCase();
            if (!team.equals("BLUE") && !team.equals("RED")) continue;

            String pName = p.getScoreboardName();
            int squadId = -1;
            for (AASWorldData.Squad s : data.squads) {
                if (s.members.contains(pName) && numberById.containsKey(s.id)
                        && s.team != null && s.team.equalsIgnoreCase(team)) {
                    squadId = s.id;
                    break;
                }
            }
            var tag = p.getPersistentData();
            entries.add(new Entry(p, team, squadId,
                    tag.getInt(StatsHandler.KEY_TP), tag.getInt(StatsHandler.KEY_SP),
                    StatsHandler.getRevives(p), StatsHandler.getLogisticsPoints(p)));
        }

        // 3. Лучший отряд: сумма (TP + SP*3) онлайн-участников.
        //    Ничья: больше онлайн-участников, затем меньший номер отряда.
        int bestId = -1;
        long bestScore = 0;
        int bestOnline = 0;
        Map<Integer, long[]> perSquad = new HashMap<>(); // id -> {score, onlineCount}
        for (Entry e : entries) {
            if (e.squadId == -1) continue;
            long[] acc = perSquad.computeIfAbsent(e.squadId, k -> new long[2]);
            acc[0] += (long) e.tp + (long) e.sp * SQUAD_POINTS_WEIGHT;
            acc[1]++;
        }
        for (Map.Entry<Integer, long[]> me : perSquad.entrySet()) {
            int id = me.getKey();
            long score = me.getValue()[0];
            int online = (int) me.getValue()[1];
            if (score <= 0) continue;
            boolean better = bestId == -1
                    || score > bestScore
                    || (score == bestScore && online > bestOnline)
                    || (score == bestScore && online == bestOnline && numberById.get(id) < numberById.get(bestId));
            if (better) { bestId = id; bestScore = score; bestOnline = online; }
        }
        if (bestId != -1) {
            AASWorldData.Squad s = squadById.get(bestId);
            fillSquadInfo(out.bestSquad, s, numberById.get(bestId), data);
            out.bestSquad.present = true;
            out.bestSquad.name = s.name;
            out.bestSquad.sub = s.leader == null ? "" : s.leader;
            out.bestSquad.team = s.team.toUpperCase();
            out.bestSquad.value = (int) Math.min(Integer.MAX_VALUE, bestScore);
        }

        // 4. Лучший медик: больше всего подъёмов. Ничья: больше TP+SP, затем ник по алфавиту.
        Entry medic = null;
        for (Entry e : entries) {
            if (e.revives <= 0) continue;
            if (medic == null || e.revives > medic.revives
                    || (e.revives == medic.revives && (long) e.tp + e.sp > (long) medic.tp + medic.sp)
                    || (e.revives == medic.revives && (long) e.tp + e.sp == (long) medic.tp + medic.sp
                        && e.player.getScoreboardName().compareToIgnoreCase(medic.player.getScoreboardName()) < 0)) {
                medic = e;
            }
        }
        if (medic != null) fillPlayerPanel(out.bestMedic, medic, medic.revives, numberById, squadById, data);

        // 5. Лучший логист: только очки за постройку и пополнение хабов. Ничья как у медика.
        Entry logist = null;
        for (Entry e : entries) {
            if (e.logi <= 0) continue;
            if (logist == null || e.logi > logist.logi
                    || (e.logi == logist.logi && (long) e.tp + e.sp > (long) logist.tp + logist.sp)
                    || (e.logi == logist.logi && (long) e.tp + e.sp == (long) logist.tp + logist.sp
                        && e.player.getScoreboardName().compareToIgnoreCase(logist.player.getScoreboardName()) < 0)) {
                logist = e;
            }
        }
        if (logist != null) fillPlayerPanel(out.bestLogist, logist, logist.logi, numberById, squadById, data);

        return out;
    }

    private static void fillPlayerPanel(VictoryData.Panel panel, Entry e, int value,
                                        Map<Integer, Integer> numberById,
                                        Map<Integer, AASWorldData.Squad> squadById, AASWorldData data) {
        panel.present = true;
        panel.name = e.player.getScoreboardName();
        panel.team = e.team;
        panel.value = value;
        if (e.squadId != -1) {
            fillSquadInfo(panel, squadById.get(e.squadId), numberById.get(e.squadId), data);
        }
    }

    private static void fillSquadInfo(VictoryData.Panel panel, AASWorldData.Squad s, int number, AASWorldData data) {
        panel.squadNumber = number;
        panel.squadMembers = new ArrayList<>(s.members);
        int cmdId = s.team.equalsIgnoreCase("BLUE") ? data.blueCMDId : data.redCMDId;
        panel.cmd = (cmdId != -1 && s.id == cmdId);
    }
}
