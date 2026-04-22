package com.example.aas.network;

import com.example.aas.client.ClientData;
import com.example.aas.client.MapPlayerInfo;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class PacketSyncMapPlayers {
    private final List<MapPlayerInfo> players;

    public PacketSyncMapPlayers(List<MapPlayerInfo> players) {
        this.players = players;
    }

    public static void encode(PacketSyncMapPlayers msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.players.size());
        for (MapPlayerInfo p : msg.players) {
            buf.writeUtf(p.name);
            buf.writeDouble(p.x);
            buf.writeDouble(p.z);
            buf.writeFloat(p.rot);
            buf.writeInt(p.squadId);
            buf.writeBoolean(p.isLeader);
            buf.writeBoolean(p.isDowned);
            buf.writeLong(p.lastShoutTime);
            buf.writeBoolean(p.inVehicle); // ДОБАВИТЬ ЭТУ СТРОКУ
        }
    }

    public static PacketSyncMapPlayers decode(FriendlyByteBuf buf) {
        int size = buf.readInt();
        List<MapPlayerInfo> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(new MapPlayerInfo(
                    buf.readUtf(),      // name
                    buf.readDouble(),   // x
                    buf.readDouble(),   // z
                    buf.readFloat(),    // rot
                    buf.readInt(),      // squadId
                    buf.readBoolean(),  // isLeader
                    buf.readBoolean(),  // isDowned
                    buf.readLong(),     // lastShoutTime
                    buf.readBoolean()   // inVehicle (ДОБАВИТЬ ЭТО ЧТЕНИЕ)
            ));
        }
        return new PacketSyncMapPlayers(list);
    }

    public static void handle(PacketSyncMapPlayers msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                // Обновляем данные на клиенте
                ClientData.mapPlayers.clear();
                for (MapPlayerInfo info : msg.players) {
                    ClientData.mapPlayers.put(info.name, info);
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }
}