package com.example.aas.events;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;

public class StatsHandler {

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
    public static void addStatsByName(MinecraftServer server, String playerName, int teamPoints, int squadPoints, String reason) {
        if (playerName == null || playerName.isEmpty()) return;
        ServerPlayer player = server.getPlayerList().getPlayerByName(playerName);
        if (player != null) {
            addStats(player, teamPoints, squadPoints, reason);
        }
    }
}