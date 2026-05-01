// PATH: src/main/java/com/example/aas/network/PacketPlaceMapMarker.java
package com.example.aas.network;

import com.example.aas.world.AASWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class PacketPlaceMapMarker {
    private final int x;
    private final int z;
    private final String type;

    public PacketPlaceMapMarker(int x, int z, String type) {
        this.x = x;
        this.z = z;
        this.type = type;
    }

    public static void encode(PacketPlaceMapMarker msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.x);
        buf.writeInt(msg.z);
        buf.writeUtf(msg.type);
    }

    public static PacketPlaceMapMarker decode(FriendlyByteBuf buf) {
        return new PacketPlaceMapMarker(buf.readInt(), buf.readInt(), buf.readUtf());
    }

    public static void handle(PacketPlaceMapMarker msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || player.getTeam() == null) return;

            ServerLevel level = player.serverLevel();
            AASWorldData data = AASWorldData.get(level);
            String team = player.getTeam().getName().toUpperCase();

            // Логика сервера: ставим метку в данные мира
            long expiry = level.getGameTime() + 3600;
            data.activeMarkers.add(new AASWorldData.MapMarker(
                    new BlockPos(msg.x, 64, msg.z),
                    msg.type,
                    team,
                    expiry
            ));
            data.setDirty();
            PacketHandler.sendToAllClients(level, data);

            // НИКАКИХ setScreen ТУТ БЫТЬ НЕ ДОЛЖНО!
        });
        ctx.get().setPacketHandled(true);
    }
}