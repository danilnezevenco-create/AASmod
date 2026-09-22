package com.example.aas.network;

import net.minecraft.network.FriendlyByteBuf;

public class PlayerStatInfo {
    public String name;
    public String team;     // "BLUE" / "RED" / ""
    public int squadId;     // -1 = без отряда
    public boolean isLeader;
    public boolean isCMD;
    public int kills;       // -1 = скрыто (враг во время матча)
    public int deaths;      // -1 = скрыто
    public int ping;

    public PlayerStatInfo(String name, String team, int squadId, boolean isLeader, boolean isCMD, int kills, int deaths, int ping) {
        this.name = name;
        this.team = team;
        this.squadId = squadId;
        this.isLeader = isLeader;
        this.isCMD = isCMD;
        this.kills = kills;
        this.deaths = deaths;
        this.ping = ping;
    }

    // Урезанная версия для вражеской команды, пока матч идёт: только ник, команда и пинг
    public PlayerStatInfo stripped() {
        return new PlayerStatInfo(this.name, this.team, -1, false, false, -1, -1, this.ping);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(name);
        buf.writeUtf(team);
        buf.writeInt(squadId);
        buf.writeBoolean(isLeader);
        buf.writeBoolean(isCMD);
        buf.writeInt(kills);
        buf.writeInt(deaths);
        buf.writeInt(ping);
    }

    public static PlayerStatInfo decode(FriendlyByteBuf buf) {
        return new PlayerStatInfo(
                buf.readUtf(), buf.readUtf(), buf.readInt(),
                buf.readBoolean(), buf.readBoolean(),
                buf.readInt(), buf.readInt(), buf.readInt()
        );
    }
}