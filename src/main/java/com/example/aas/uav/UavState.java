package com.example.aas.uav;

import java.util.UUID;

/** Runtime state of one team's recon UAV (kept in AASWorldData, not saved to disk). */
public class UavState {
    public final String team;
    public final UUID owner;
    public final double cx, cz;
    public final int radius;
    public final double altY;
    public final double theta0;
    public final float speed;
    public final long startTick, endTick;
    public UUID entityUuid;

    public UavState(String team, UUID owner, double cx, double cz, int radius, double altY,
                    double theta0, float speed, long startTick, long endTick) {
        this.team = team;
        this.owner = owner;
        this.cx = cx;
        this.cz = cz;
        this.radius = radius;
        this.altY = altY;
        this.theta0 = theta0;
        this.speed = speed;
        this.startTick = startTick;
        this.endTick = endTick;
    }

    public double omega() { return UavFlight.omega(speed, radius); }
}
