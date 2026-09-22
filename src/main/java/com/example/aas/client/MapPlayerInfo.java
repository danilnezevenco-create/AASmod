// PATH: src/main/java/com/example/aas/network/MapPlayerInfo.java
package com.example.aas.network;
import java.util.UUID;

public class MapPlayerInfo {
    public String name;
    public UUID uuid;
    public double x, z;
    public float rot;
    public int squadId;
    public boolean isLeader;
    public boolean isDowned;
    public boolean isDead;
    public long lastShoutTime;
    public boolean inVehicle;
    public int vehicleId;
    public int seatIndex;
    public String team;

    // НОВОЕ: снаряжение медика (+ внутри кружка)
    public boolean isMedic;
    // НОВОЕ: роль в фаертиме — "" (нет), "B" (глава Bravo), "C" (глава Charlie)
    public String fireteamRole;

    public MapPlayerInfo(String name, UUID uuid, double x, double z, float rot, int squadId, boolean isLeader,
                         boolean isDowned, boolean isDead, long lastShoutTime, boolean inVehicle, int vehicleId,
                         int seatIndex, String team, boolean isMedic, String fireteamRole) {
        this.name = name;
        this.uuid = uuid;
        this.x = x;
        this.z = z;
        this.rot = rot;
        this.squadId = squadId;
        this.isLeader = isLeader;
        this.isDowned = isDowned;
        this.isDead = isDead;
        this.lastShoutTime = lastShoutTime;
        this.inVehicle = inVehicle;
        this.vehicleId = vehicleId;
        this.seatIndex = seatIndex;
        this.team = team;
        this.isMedic = isMedic;
        this.fireteamRole = fireteamRole == null ? "" : fireteamRole;
    }
}