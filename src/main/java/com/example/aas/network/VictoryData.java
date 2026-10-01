package com.example.aas.network;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Снимок итогов матча для экрана победы. Собирается на сервере в момент победы
 * и целиком отправляется клиентам (клиент хранит его в ClientData.lastVictory).
 */
public class VictoryData {

    /** Одна из трёх нижних панелей (лучший отряд / медик / логист). */
    public static class Panel {
        public boolean present = false;     // false -> «Нет лучшего ...»
        public String name = "";            // название отряда или ник игрока
        public String sub = "";             // ник лидера (только для отряда)
        public String team = "";            // "BLUE" / "RED" — по нему выбирается флаг
        public int squadNumber = 0;         // реальный номер отряда в его команде (0 = нет отряда)
        public boolean cmd = false;         // отряд командира (зелёный кружок CMD)
        public int value = 0;               // подъёмы / очки логиста / очки отряда
        public List<String> squadMembers = new ArrayList<>(); // участники отряда на момент победы

        public void encode(FriendlyByteBuf buf) {
            buf.writeBoolean(present);
            buf.writeUtf(name);
            buf.writeUtf(sub);
            buf.writeUtf(team);
            buf.writeVarInt(squadNumber);
            buf.writeBoolean(cmd);
            buf.writeInt(value);
            buf.writeVarInt(squadMembers.size());
            for (String m : squadMembers) buf.writeUtf(m);
        }

        public static Panel decode(FriendlyByteBuf buf) {
            Panel p = new Panel();
            p.present = buf.readBoolean();
            p.name = buf.readUtf();
            p.sub = buf.readUtf();
            p.team = buf.readUtf();
            p.squadNumber = buf.readVarInt();
            p.cmd = buf.readBoolean();
            p.value = buf.readInt();
            int n = buf.readVarInt();
            for (int i = 0; i < n; i++) p.squadMembers.add(buf.readUtf());
            return p;
        }
    }

    public boolean blueWon;
    public int blueTickets, redTickets;
    public String blueFaction = "none", redFaction = "none";
    public String blueName = "", redName = "";
    public Panel bestSquad = new Panel();
    public Panel bestMedic = new Panel();
    public Panel bestLogist = new Panel();

    public int winnerTickets() { return blueWon ? blueTickets : redTickets; }
    public int loserTickets() { return blueWon ? redTickets : blueTickets; }
    public String factionOf(String team) { return "RED".equalsIgnoreCase(team) ? redFaction : blueFaction; }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(blueWon);
        buf.writeInt(blueTickets);
        buf.writeInt(redTickets);
        buf.writeUtf(blueFaction);
        buf.writeUtf(redFaction);
        buf.writeUtf(blueName);
        buf.writeUtf(redName);
        bestSquad.encode(buf);
        bestMedic.encode(buf);
        bestLogist.encode(buf);
    }

    public static VictoryData decode(FriendlyByteBuf buf) {
        VictoryData d = new VictoryData();
        d.blueWon = buf.readBoolean();
        d.blueTickets = buf.readInt();
        d.redTickets = buf.readInt();
        d.blueFaction = buf.readUtf();
        d.redFaction = buf.readUtf();
        d.blueName = buf.readUtf();
        d.redName = buf.readUtf();
        d.bestSquad = Panel.decode(buf);
        d.bestMedic = Panel.decode(buf);
        d.bestLogist = Panel.decode(buf);
        return d;
    }
}
