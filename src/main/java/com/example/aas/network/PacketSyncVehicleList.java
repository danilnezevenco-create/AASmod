package com.example.aas.network;

import com.example.aas.client.ClientData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server -> one client: per-type vehicle summary for the vehicle list panel.
 *
 * Why a separate packet and not an extension of PacketSyncGameData: that packet is broadcast to ALL
 * clients on every state change, while this data is per-player (enemy alive counts / timers must not
 * leak) and is only needed while the panel is open, so a dedicated request/response pair is both
 * safer and cheaper.
 *
 * Respawn time is sent as REMAINING TICKS; the client counts down locally from the moment it received
 * the packet, so no per-second packets are required.
 */
public class PacketSyncVehicleList {

    /** One row = one vehicle type (vehicleIdString) of one team. */
    public static final class Entry {
        public final String vehicleId;
        public final int category;
        public final int total;
        public final int alive;
        public final long nextRespawnTick;   // абсолютный тик игры, -1 = неизвестно/скрыто
        public final int penalty;
        public final String markerType;

        public Entry(String vehicleId, int category, int total, int alive,
                     long nextRespawnTick, int penalty, String markerType) {
            this.vehicleId = vehicleId;
            this.category = category;
            this.total = total;
            this.alive = alive;
            this.nextRespawnTick = nextRespawnTick;
            this.penalty = penalty;
            this.markerType = markerType == null ? "" : markerType;
        }
    }

    public final boolean hasTeam;
    /** true for creative/spectator viewers: both sides carry alive counts and timers. */
    public final boolean fullVisibility;
    public final String allyTeam;           // "BLUE" / "RED"
    public final List<Entry> ally;
    public final List<Entry> enemy;

    public PacketSyncVehicleList(boolean hasTeam, boolean fullVisibility, String allyTeam,
                                 List<Entry> ally, List<Entry> enemy) {
        this.hasTeam = hasTeam;
        this.fullVisibility = fullVisibility;
        this.allyTeam = allyTeam;
        this.ally = ally;
        this.enemy = enemy;
    }

    public static PacketSyncVehicleList empty() {
        return new PacketSyncVehicleList(false, false, "", new ArrayList<>(), new ArrayList<>());
    }

    public static void encode(PacketSyncVehicleList msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.hasTeam);
        buf.writeBoolean(msg.fullVisibility);
        buf.writeUtf(msg.allyTeam);
        writeList(buf, msg.ally);
        writeList(buf, msg.enemy);
    }

    public static PacketSyncVehicleList decode(FriendlyByteBuf buf) {
        boolean hasTeam = buf.readBoolean();
        boolean full = buf.readBoolean();
        String team = buf.readUtf();
        List<Entry> ally = readList(buf);
        List<Entry> enemy = readList(buf);
        return new PacketSyncVehicleList(hasTeam, full, team, ally, enemy);
    }

    private static void writeList(FriendlyByteBuf buf, List<Entry> list) {
        buf.writeVarInt(list.size());
        for (Entry e : list) {
            buf.writeUtf(e.vehicleId);
            buf.writeByte(e.category);
            buf.writeVarInt(e.total);
            buf.writeInt(e.alive);
            buf.writeLong(e.nextRespawnTick);   // было writeInt
            buf.writeVarInt(e.penalty);
            buf.writeUtf(e.markerType);
        }
    }

    private static List<Entry> readList(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<Entry> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new Entry(buf.readUtf(), buf.readByte(), buf.readVarInt(),
                    buf.readInt(), buf.readLong(), buf.readVarInt(), buf.readUtf()));
        }
        return list;
    }


    public static void handle(PacketSyncVehicleList msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientData.applyVehicleList(msg)));
        ctx.get().setPacketHandled(true);
    }
}