// PATH: src/main/java/com/example/aas/network/PacketSquadMarker.java
package com.example.aas.network;

import com.example.aas.world.AASWorldData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import java.util.function.Supplier;

public class PacketSquadMarker {
    private final int x, z, type;

    public PacketSquadMarker(int x, int z, int type) {
        this.x = x; this.z = z; this.type = type;
    }

    public static void encode(PacketSquadMarker msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.x); buf.writeInt(msg.z); buf.writeInt(msg.type);
    }

    public static PacketSquadMarker decode(FriendlyByteBuf buf) {
        return new PacketSquadMarker(buf.readInt(), buf.readInt(), buf.readInt());
    }

    public static void handle(PacketSquadMarker msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ServerLevel level = player.serverLevel();
            AASWorldData data = AASWorldData.get(level);
            long expiry = level.getGameTime() + 6000;

            for (AASWorldData.Squad s : data.squads) {
                if (s.leader.equals(player.getScoreboardName())) {
                    s.marker = new AASWorldData.SquadMarker(msg.x, msg.z, msg.type, expiry);
                    data.setDirty();

                    // Синхронизация данных
                    PacketHandler.INSTANCE.send(PacketDistributor.DIMENSION.with(level::dimension),
                            new PacketSyncSquads(data.squads));
                    break;
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}