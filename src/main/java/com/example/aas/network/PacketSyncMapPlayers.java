// PATH: src/main/java/com/example/aas/network/PacketSyncMapPlayers.java
package com.example.aas.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class PacketSyncMapPlayers {
    private final List<com.example.aas.network.MapPlayerInfo> players;

    public PacketSyncMapPlayers(List<com.example.aas.network.MapPlayerInfo> players) {
        this.players = players;
    }

    public List<com.example.aas.network.MapPlayerInfo> getPlayers() {
        return this.players;
    }

    public static void encode(PacketSyncMapPlayers msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.players.size());
        for (com.example.aas.network.MapPlayerInfo p : msg.players) {
            buf.writeUtf(p.name);
            buf.writeUUID(p.uuid);
            buf.writeDouble(p.x);
            buf.writeDouble(p.z);
            buf.writeFloat(p.rot);
            buf.writeInt(p.squadId);
            buf.writeBoolean(p.isLeader);
            buf.writeBoolean(p.isDowned);
            buf.writeBoolean(p.isDead);
            buf.writeLong(p.lastShoutTime);
            buf.writeBoolean(p.inVehicle);
            buf.writeInt(p.vehicleId);
            buf.writeInt(p.seatIndex);
            buf.writeUtf(p.team);
            buf.writeBoolean(p.isMedic);      // НОВОЕ
            buf.writeUtf(p.fireteamRole);     // НОВОЕ
        }
    }

    public static PacketSyncMapPlayers decode(FriendlyByteBuf buf) {
        int size = buf.readInt();
        List<com.example.aas.network.MapPlayerInfo> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(new com.example.aas.network.MapPlayerInfo(
                    buf.readUtf(), buf.readUUID(), buf.readDouble(), buf.readDouble(),
                    buf.readFloat(), buf.readInt(), buf.readBoolean(),
                    buf.readBoolean(), buf.readBoolean(),
                    buf.readLong(), buf.readBoolean(),
                    buf.readInt(), buf.readInt(),
                    buf.readUtf(),
                    buf.readBoolean(), buf.readUtf() // НОВОЕ: isMedic, fireteamRole
            ));
        }
        return new PacketSyncMapPlayers(list);
    }

    public static void handle(PacketSyncMapPlayers msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleSyncMap(msg));
        });
        ctx.get().setPacketHandled(true);
    }
}