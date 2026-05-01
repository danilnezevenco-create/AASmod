package com.example.aas.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

// УДАЛИТЕ ВСЕ ИМПОРТЫ .client.* ОТСЮДА!

public class PacketSyncMapPlayers {
    private final List<MapPlayerInfo> players;

    public PacketSyncMapPlayers(List<MapPlayerInfo> players) {
        this.players = players;
    }

    public List<MapPlayerInfo> getPlayers() {
        return this.players;
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
            buf.writeBoolean(p.inVehicle);
        }
    }

    public static PacketSyncMapPlayers decode(FriendlyByteBuf buf) {
        int size = buf.readInt();
        List<MapPlayerInfo> list = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            list.add(new MapPlayerInfo(
                    buf.readUtf(), buf.readDouble(), buf.readDouble(),
                    buf.readFloat(), buf.readInt(), buf.readBoolean(),
                    buf.readBoolean(), buf.readLong(), buf.readBoolean()
            ));
        }
        return new PacketSyncMapPlayers(list);
    }

    public static void handle(PacketSyncMapPlayers msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Используем DistExecutor, но вызываем метод через интерфейс или ClientHooks
            // чтобы не "пачкать" импорты пакета
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientPacketHandler.handleSyncMap(msg));
        });
        ctx.get().setPacketHandled(true);
    }
}