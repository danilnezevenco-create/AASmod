// PATH: src/main/java/com/example/aas/client/MapPlayerInfo.java
package com.example.aas.network;

public class MapPlayerInfo {
    public String name;
    public double x, z;
    public float rot;
    public int squadId;
    public boolean isLeader;
    public boolean isDowned;
    public long lastShoutTime;
    public boolean inVehicle; // Это поле должно быть здесь!
    public String team;

    public MapPlayerInfo(String name, double x, double z, float rot, int squadId, boolean isLeader, boolean isDowned, long lastShoutTime, boolean inVehicle, String team) {
        this.name = name;
        this.x = x;
        this.z = z;
        this.rot = rot;
        this.squadId = squadId;
        this.isLeader = isLeader;
        this.isDowned = isDowned;
        this.lastShoutTime = lastShoutTime;
        this.inVehicle = inVehicle;
        this.team = team;
    }
}