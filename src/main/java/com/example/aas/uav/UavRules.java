package com.example.aas.uav;

import com.example.aas.config.AASConfig;
import com.example.aas.world.AASWorldData;
import net.minecraft.server.level.ServerPlayer;

/** Server-side permission checks, mirroring the artillery strike rules. */
public final class UavRules {
    private UavRules() {}

    public static String teamOf(ServerPlayer p) {
        return p.getTeam() != null ? p.getTeam().getName().toUpperCase() : "";
    }

    public static boolean isTeam(String team) {
        return team.equals("BLUE") || team.equals("RED");
    }

    /** Leader of the squad that is the team's CMD squad (same check as PacketConfirmArtStrike). */
    public static boolean isCommander(ServerPlayer p, AASWorldData data) {
        String team = teamOf(p);
        if (!isTeam(team)) return false;
        int squadId = p.getPersistentData().getInt("AAS_SquadID");
        boolean isSL = p.getPersistentData().getBoolean("AAS_IsSquadLeader");
        int cmdId = team.equals("BLUE") ? data.blueCMDId : data.redCMDId;
        return isSL && cmdId != -1 && squadId == cmdId;
    }

    public static boolean isDowned(ServerPlayer p) {
        return p.getPersistentData().getBoolean("AAS_IsDowned");
    }

    public static UavState get(AASWorldData d, String team) {
        return team.equals("BLUE") ? d.blueUav : d.redUav;
    }

    public static void set(AASWorldData d, String team, UavState s) {
        if (team.equals("BLUE")) d.blueUav = s; else d.redUav = s;
    }

    public static long cooldownEnd(AASWorldData d, String team) {
        return team.equals("BLUE") ? d.blueUavCooldownEnd : d.redUavCooldownEnd;
    }

    public static void setCooldownEnd(AASWorldData d, String team, long t) {
        if (team.equals("BLUE")) d.blueUavCooldownEnd = t; else d.redUavCooldownEnd = t;
    }

    public static float zoomFov(int index) {
        float[] def = {45f, 25f, 10f};
        try {
            java.util.List<? extends Number> l = AASConfig.UAV_ZOOM_FOV.get();
            if (l != null && index < l.size() && l.get(index) != null) return l.get(index).floatValue();
        } catch (Exception ignored) { }
        return def[index];
    }
}
