// PATH: src/main/java/com/example/aas/util/TeamMessageUtil.java
package com.example.aas.util;

import com.example.aas.config.AASConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.Team;

/**
 * Utility for broadcasting HUB / Rally Point / Vehicle destruction messages,
 * optionally keeping them separate per team (see AASConfig.SEPARATE_TEAM_DESTRUCTION_MESSAGES).
 */
public class TeamMessageUtil {

    /**
     * Sends a destruction-related chat message either to all players (default behaviour),
     * or - if separateTeamDestructionMessages is enabled in the config - only to:
     *  - players on the "ownerTeam" (BLUE or RED, whichever team lost the HUB/Rally/Vehicle)
     *  - players who are NOT currently on the BLUE or RED team (no team, spectators, other scoreboard teams)
     * The opposing team (BLUE or RED, whichever one is NOT ownerTeam) will not receive the message.
     *
     * @param level     the server level to broadcast in
     * @param ownerTeam "BLUE" or "RED" - the team that owns/lost the destroyed object
     * @param message   the chat message to send
     */
    public static void broadcastDestructionMessage(ServerLevel level, String ownerTeam, Component message) {
        if (!AASConfig.SEPARATE_TEAM_DESTRUCTION_MESSAGES.get()) {
            level.getServer().getPlayerList().broadcastSystemMessage(message, false);
            return;
        }

        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            Team pt = player.getTeam();
            String teamName = (pt != null) ? pt.getName() : null;
            boolean isBlueOrRed = teamName != null
                    && (teamName.equalsIgnoreCase("Blue") || teamName.equalsIgnoreCase("Red"));

            // Players without a team (or on some other scoreboard team) always see both teams' messages.
            // Players on BLUE/RED only see the message if it's about their own team's loss.
            if (!isBlueOrRed || teamName.equalsIgnoreCase(ownerTeam)) {
                player.sendSystemMessage(message);
            }
        }
    }
}