package com.example.aas.network;

import com.example.aas.client.ClientData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Сервер -> клиент. Флаг "показывать ранги китов" (из конфига showKitRanks)
 * и карта ник -> уровень текущего кита игрока.
 */
public class PacketSyncKitRanks {
    private final boolean enabled;
    private final Map<String, Integer> ranks;

    public PacketSyncKitRanks(boolean enabled, Map<String, Integer> ranks) {
        this.enabled = enabled;
        this.ranks = ranks;
    }

    public static void encode(PacketSyncKitRanks msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.enabled);
        buf.writeVarInt(msg.ranks.size());
        for (Map.Entry<String, Integer> e : msg.ranks.entrySet()) {
            buf.writeUtf(e.getKey());
            buf.writeVarInt(e.getValue());
        }
    }

    public static PacketSyncKitRanks decode(FriendlyByteBuf buf) {
        boolean enabled = buf.readBoolean();
        int size = buf.readVarInt();
        Map<String, Integer> ranks = new HashMap<>();
        for (int i = 0; i < size; i++) {
            ranks.put(buf.readUtf(), buf.readVarInt());
        }
        return new PacketSyncKitRanks(enabled, ranks);
    }

    public static void handle(PacketSyncKitRanks msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    ClientData.serverShowKitRanks = msg.enabled;
                    ClientData.playerKitRanks = new HashMap<>(msg.ranks);
                }));
        ctx.get().setPacketHandled(true);
    }
}