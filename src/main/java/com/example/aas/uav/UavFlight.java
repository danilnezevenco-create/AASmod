package com.example.aas.uav;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/** Flight path maths shared by server (entity) and client (camera anchor, map icon). */
public final class UavFlight {
    private UavFlight() {}

    /** Angular speed in rad/tick for a given linear speed (blocks/tick) and orbit radius. */
    public static double omega(double speed, double radius) {
        return speed / Math.max(1.0, radius);
    }

    public static Vec3 position(double cx, double cz, double r, double y, double theta0, double omega, double ticks) {
        double t = theta0 + omega * ticks;
        return new Vec3(cx + r * Math.cos(t), y, cz + r * Math.sin(t));
    }

    /** Minecraft yaw (degrees) of the tangent direction of travel. */
    public static float yaw(double theta0, double omega, double ticks) {
        double t = theta0 + omega * ticks;
        double dx = -Math.sin(t);
        double dz = Math.cos(t);
        return (float) (Mth.atan2(-dx, dz) * (180.0 / Math.PI));
    }

    /**
     * Average terrain height over the centre + 24 points on the circle, LOADED chunks only.
     * Fallback when nothing is loaded: sea level.
     */
    public static double averageTerrain(ServerLevel level, double cx, double cz, double r) {
        double sum = 0;
        int n = 0;
        int samples = 24;
        for (int i = -1; i < samples; i++) {
            double x = cx;
            double z = cz;
            if (i >= 0) {
                double a = i * (Math.PI * 2.0) / samples;
                x += r * Math.cos(a);
                z += r * Math.sin(a);
            }
            int bx = Mth.floor(x);
            int bz = Mth.floor(z);
            LevelChunk chunk = level.getChunkSource().getChunkNow(bx >> 4, bz >> 4);
            if (chunk != null) {
                sum += chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
                n++;
            }
        }
        return n > 0 ? sum / n : level.getSeaLevel();
    }
}
