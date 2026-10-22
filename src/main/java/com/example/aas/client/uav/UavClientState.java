package com.example.aas.client.uav;

import com.example.aas.client.ClientData;
import com.example.aas.network.PacketSyncUav;
import com.example.aas.uav.UavFlight;
import com.example.aas.world.AASWorldData;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

/** Client copy of the own team's UAV (the server never sends it to the enemy team). */
public final class UavClientState {
    private UavClientState() {}

    public static boolean active;
    public static double cx, cz;
    public static int radius;
    public static float altY, theta0, speed;
    public static long startTick, endTick, cooldownEnd;

    public static void apply(PacketSyncUav m) {
        active = m.active;
        cx = m.cx; cz = m.cz; radius = m.radius;
        altY = m.altY; theta0 = m.theta0; speed = m.speed;
        startTick = m.startTick; endTick = m.endTick; cooldownEnd = m.cooldownEnd;
        if (!active && UavViewClient.isViewing()) UavViewClient.exitSilently();
    }

    public static void clear() {
        active = false;
        cooldownEnd = 0;
    }

    public static long cooldownTicksLeft(Minecraft mc) {
        if (mc.level == null) return 0;
        return Math.max(0, cooldownEnd - mc.level.getGameTime());
    }

    /** Analytic UAV position at (gameTime + partial): the same formula the server entity uses. */
    public static Vec3 position(double gameTime) {
        double omega = UavFlight.omega(speed, radius);
        return UavFlight.position(cx, cz, radius, altY, theta0, omega, gameTime - startTick);
    }

    /** Leader of the CMD squad, determined from the synced squad list (persistent data is not synced to clients). */
    public static boolean isCmdLeader(Minecraft mc) {
        if (mc.player == null) return false;
        String name = mc.player.getScoreboardName();
        String team = mc.player.getTeam() != null ? mc.player.getTeam().getName().toUpperCase() : "";
        if (!team.equals("BLUE") && !team.equals("RED")) return false;
        int cmdId = team.equals("BLUE") ? ClientData.blueCMDId : ClientData.redCMDId;
        if (cmdId == -1) return false;
        for (AASWorldData.Squad s : ClientData.clientSquads) {
            if (s.id == cmdId && s.leader.equals(name)) return true;
        }
        return false;
    }

    public static boolean isDowned(Minecraft mc) {
        return mc.player != null && mc.player.getPersistentData().getBoolean("AAS_IsDowned");
    }
}
