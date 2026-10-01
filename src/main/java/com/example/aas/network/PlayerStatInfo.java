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
    public int tp;          // командные очки (Team Points), -1 = скрыто
    public int sp;          // отрядные очки (Squad Points), -1 = скрыто
    public int revives;     // сколько игроков поднял, -1 = скрыто
    public int vehKillsTotal;       // сколько техники уничтожено всего, -1 = скрыто
    public int[] vehKillsByCategory; // по VehicleCatalog.Category.values(), пустой массив = нет данных
    public int ping;

    public PlayerStatInfo(String name, String team, int squadId, boolean isLeader, boolean isCMD,
                          int kills, int deaths, int tp, int sp, int revives, int vehKillsTotal, int[] vehKillsByCategory, int ping) {
        this.name = name;
        this.team = team;
        this.squadId = squadId;
        this.isLeader = isLeader;
        this.isCMD = isCMD;
        this.kills = kills;
        this.deaths = deaths;
        this.tp = tp;
        this.sp = sp;
        this.revives = revives;
        this.vehKillsTotal = vehKillsTotal;
        this.vehKillsByCategory = (vehKillsByCategory != null) ? vehKillsByCategory : new int[0];
        this.ping = ping;
    }

    // Урезанная версия для вражеской команды, пока матч идёт: только ник, команда и пинг
    public PlayerStatInfo stripped() {
        return new PlayerStatInfo(this.name, this.team, -1, false, false, -1, -1, -1, -1, -1, -1, new int[0], this.ping);
    }

    // Все 5 колонок скрыты, если сервер прислал -1 хотя бы в одном из значений
    public boolean isHidden() {
        return kills < 0 || deaths < 0 || tp < 0 || sp < 0 || revives < 0 || vehKillsTotal < 0;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(name);
        buf.writeUtf(team);
        buf.writeInt(squadId);
        buf.writeBoolean(isLeader);
        buf.writeBoolean(isCMD);
        buf.writeInt(kills);
        buf.writeInt(deaths);
        buf.writeInt(tp);
        buf.writeInt(sp);
        buf.writeInt(revives);
        buf.writeInt(vehKillsTotal);
        buf.writeVarInt(vehKillsByCategory.length);
        for (int v : vehKillsByCategory) buf.writeInt(v);
        buf.writeInt(ping);
    }

    public static PlayerStatInfo decode(FriendlyByteBuf buf) {
        String name = buf.readUtf();
        String team = buf.readUtf();
        int squadId = buf.readInt();
        boolean isLeader = buf.readBoolean();
        boolean isCMD = buf.readBoolean();
        int kills = buf.readInt();
        int deaths = buf.readInt();
        int tp = buf.readInt();
        int sp = buf.readInt();
        int revives = buf.readInt();
        int vehKillsTotal = buf.readInt();
        int catCount = buf.readVarInt();
        int[] byCat = new int[catCount];
        for (int i = 0; i < catCount; i++) byCat[i] = buf.readInt();
        int ping = buf.readInt();
        return new PlayerStatInfo(name, team, squadId, isLeader, isCMD, kills, deaths, tp, sp, revives, vehKillsTotal, byCat, ping);
    }
}