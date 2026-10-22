package com.example.aas.uav;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Far-visible explosion, far-audible sound and a lingering smoke column for a destroyed UAV. */
public final class UavEffects {
    private UavEffects() {}

    private static final double RANGE = 1200.0;
    private static final int SMOKE_TICKS = 160; // 8 s

    private static final class Smoke {
        final ServerLevel level; final double x, y, z; int left = SMOKE_TICKS;
        Smoke(ServerLevel l, double x, double y, double z) { this.level = l; this.x = x; this.y = y; this.z = z; }
    }

    private static final List<Smoke> SMOKES = new ArrayList<>();

    public static void explode(ServerLevel level, Vec3 pos) {
        // volume > 1 widens the audible range (16 * volume blocks)
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.GENERIC_EXPLODE, SoundSource.AMBIENT, 64.0f, 0.6f);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.GENERIC_EXPLODE, SoundSource.AMBIENT, 48.0f, 0.9f);

        for (int i = 0; i < 6; i++) {
            double ox = (level.random.nextDouble() - 0.5) * 12, oy = (level.random.nextDouble() - 0.5) * 6, oz = (level.random.nextDouble() - 0.5) * 12;
            toAll(level, ParticleTypes.EXPLOSION_EMITTER, pos.x + ox, pos.y + oy, pos.z + oz, 1, 0, 0, 0, 0);
        }
        toAll(level, ParticleTypes.FLASH, pos.x, pos.y, pos.z, 3, 2, 2, 2, 0);
        toAll(level, ParticleTypes.FLAME, pos.x, pos.y, pos.z, 80, 5, 3, 5, 0.18);
        toAll(level, ParticleTypes.LAVA, pos.x, pos.y, pos.z, 30, 4, 2, 4, 0);
        toAll(level, ParticleTypes.LARGE_SMOKE, pos.x, pos.y, pos.z, 100, 6, 4, 6, 0.1);
        toAll(level, ParticleTypes.CLOUD, pos.x, pos.y, pos.z, 50, 5, 3, 5, 0.2);

        SMOKES.add(new Smoke(level, pos.x, pos.y, pos.z));
    }

    /** Called every level tick. */
    public static void tick(ServerLevel level) {
        if (SMOKES.isEmpty()) return;
        for (Iterator<Smoke> it = SMOKES.iterator(); it.hasNext(); ) {
            Smoke s = it.next();
            if (s.level != level) continue;
            if (--s.left <= 0) { it.remove(); continue; }
            if (s.left % 2 != 0) continue;
            for (int i = 0; i < 4; i++) {
                double ox = (level.random.nextDouble() - 0.5) * 2.5, oz = (level.random.nextDouble() - 0.5) * 2.5;
                // count == 0: the "spread" is the velocity direction, times speed -> rising column
                toAll(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, s.x + ox, s.y + level.random.nextDouble() * 4, s.z + oz, 0, 0, 1, 0, 0.12);
            }
            toAll(level, ParticleTypes.LARGE_SMOKE, s.x, s.y, s.z, 6, 1.5, 1.0, 1.5, 0.02);
        }
    }

    public static void clear() { SMOKES.clear(); }

    private static <T extends ParticleOptions> void toAll(ServerLevel level, T particle, double x, double y, double z,
                                                          int count, double dx, double dy, double dz, double speed) {
        double rangeSq = RANGE * RANGE;
        for (ServerPlayer p : level.players()) {
            if (!near(p.getX(), p.getZ(), x, z, rangeSq)) {
                Vec3 v = UavViewManager.viewPos(p); // a viewer's camera sits at the UAV, not at the body
                if (v == null || !near(v.x, v.z, x, z, rangeSq)) continue;
            }
            level.sendParticles(p, particle, true, x, y, z, count, dx, dy, dz, speed);
        }
    }

    private static boolean near(double ax, double az, double bx, double bz, double rangeSq) {
        double dx = ax - bx, dz = az - bz;
        return dx * dx + dz * dz <= rangeSq;
    }
}
