package com.example.aas.events;

import com.example.aas.world.AASWorldData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * "Актуальный триггер": пока игрок жив, не в спектаторе и находится в зоне активной точки,
 * которая реально оспаривается или удерживается, он получает POINT_PRESENCE_TP каждые 30 сек присутствия.
 * Анти-афк: игрок должен двигаться, крутить камеру или что-то делать хотя бы раз в 15 сек.
 */
public final class PointPresenceTracker {

    private PointPresenceTracker() {}

    private static final class State {
        long presenceTicks = 0;
        long lastSeenTick = 0;
        long lastActiveTick = 0;
        double x, y, z;
        float yaw, pitch;
        boolean hasSample = false;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    public static void tick(ServerLevel level, AASWorldData.CapturePoint point, List<ServerPlayer> playersInBox,
                            String dominantTeam, boolean isContested, boolean isBeingActivelyCaptured, boolean isTimeLocked,
                            boolean blueCanCapture, boolean redCanCapture) {
        if (isTimeLocked || playersInBox.isEmpty()) return;
        long now = level.getGameTime();

        for (ServerPlayer p : playersInBox) {
            if (!p.isAlive() || p.isSpectator()) continue;
            if (p.getPersistentData().getBoolean("AAS_IsDowned")) continue;
            if (!point.isInside(p.position())) continue;
            if (p.getTeam() == null) continue;

            String teamKey;
            if (p.getTeam().getName().equalsIgnoreCase("Blue")) teamKey = "BLUE";
            else if (p.getTeam().getName().equalsIgnoreCase("Red")) teamKey = "RED";
            else continue;

            State st = STATES.get(p.getUUID());
            if (st == null) {
                st = new State();
                st.lastActiveTick = now;
                STATES.put(p.getUUID(), st);
            }
            if (now - st.lastSeenTick > 40L) st.presenceTicks = 0; // вышел из зоны - счётчик начинается заново
            st.lastSeenTick = now;

            updateActivity(st, p, now);
            boolean afk = now - st.lastActiveTick > ScoreValues.POINT_AFK_TIMEOUT_TICKS;

            // Точка "актуальна" для игрока, если: идёт спор, он атакует/нейтрализует точку,
            // или он защищает свою точку, на которую враг реально может напасть (фронтовая точка)
            boolean enemyCanCapture = teamKey.equals("BLUE") ? redCanCapture : blueCanCapture;
            boolean active = isContested
                    || (teamKey.equals(dominantTeam) && isBeingActivelyCaptured)
                    || (point.owner.equals(teamKey) && enemyCanCapture);

            if (!active || afk) continue;

            st.presenceTicks++;
            if (st.presenceTicks >= ScoreValues.POINT_PRESENCE_INTERVAL_TICKS) {
                st.presenceTicks = 0;
                StatsHandler.addScore(p, ScoreType.POINT_PRESENCE);
            }
        }

        if (STATES.size() > 256) {
            STATES.values().removeIf(s -> now < s.lastSeenTick || now - s.lastSeenTick > 1200L);
        }
    }

    private static void updateActivity(State st, ServerPlayer p, long now) {
        double x = p.getX(), y = p.getY(), z = p.getZ();
        float yaw = p.getYRot(), pitch = p.getXRot();
        if (!st.hasSample) {
            st.hasSample = true;
            st.lastActiveTick = now;
        } else {
            double dx = x - st.x, dy = y - st.y, dz = z - st.z;
            boolean moved = dx * dx + dy * dy + dz * dz > 0.0004;
            boolean looked = Math.abs(yaw - st.yaw) + Math.abs(pitch - st.pitch) > 0.5f;
            if (moved || looked || p.swinging || p.isUsingItem()) st.lastActiveTick = now;
        }
        st.x = x; st.y = y; st.z = z;
        st.yaw = yaw; st.pitch = pitch;
    }
}
