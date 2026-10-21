// PATH: src/main/java/com/example/aas/network/PacketSyncKitRanks.java
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
 * и карта ник -> прогресс ранга текущего кита игрока (уровень + очки).
 */
public class PacketSyncKitRanks {
    private final boolean enabled;
    private final Map<String, KitRankProgress> ranks;

    public PacketSyncKitRanks(boolean enabled, Map<String, KitRankProgress> ranks) {
        this.enabled = enabled;
        this.ranks = ranks;
    }

    public static void encode(PacketSyncKitRanks msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.enabled);
        buf.writeVarInt(msg.ranks.size());
        for (Map.Entry<String, KitRankProgress> e : msg.ranks.entrySet()) {
            buf.writeUtf(e.getKey());
            buf.writeVarInt(e.getValue().level());
            buf.writeVarInt(e.getValue().into());
            buf.writeVarInt(e.getValue().cost());
        }
    }

    public static PacketSyncKitRanks decode(FriendlyByteBuf buf) {
        boolean enabled = buf.readBoolean();
        int size = buf.readVarInt();
        Map<String, KitRankProgress> ranks = new HashMap<>();
        for (int i = 0; i < size; i++) {
            String nick = buf.readUtf();
            int level = buf.readVarInt();
            int into = buf.readVarInt();
            int cost = buf.readVarInt();
            ranks.put(nick, new KitRankProgress(level, into, cost));
        }
        return new PacketSyncKitRanks(enabled, ranks);
    }

    public static void handle(PacketSyncKitRanks msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                    ClientData.serverShowKitRanks = msg.enabled;
                    ClientData.playerKitRankProgress = new HashMap<>(msg.ranks);

                    // старая карта с одними уровнями остаётся рабочей для KitRankIcons.levelOf()
                    Map<String, Integer> levels = new HashMap<>();
                    for (Map.Entry<String, KitRankProgress> e : msg.ranks.entrySet()) {
                        levels.put(e.getKey(), e.getValue().level());
                    }
                    ClientData.playerKitRanks = levels;
                }));
        ctx.get().setPacketHandled(true);
    }
}