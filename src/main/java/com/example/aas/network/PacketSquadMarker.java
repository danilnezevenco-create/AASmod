package com.example.aas.network;

import com.example.aas.world.AASWorldData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;

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

            boolean found = false;
            for (AASWorldData.Squad s : data.squads) {
                if (s.leader.equals(player.getScoreboardName())) {
                    s.marker = new AASWorldData.SquadMarker(msg.x, msg.z, msg.type, expiry);
                    data.setDirty();

                    // Печатаем в консоль сервера для проверки
                    System.out.println("AAS DEBUG: Marker set by " + s.leader + " at " + msg.x + ", " + msg.z + " type: " + msg.type);

                    // СИНХРОНИЗАЦИЯ: отправляем всем в этом мире обновленные данные о сквадах
                    PacketHandler.INSTANCE.send(PacketDistributor.DIMENSION.with(level::dimension), new PacketSyncSquads(data.squads));
                    found = true;
                    break;
                }
            }
            if (!found) System.out.println("AAS DEBUG: Player " + player.getScoreboardName() + " tried to set marker but isn't a SL!");
        });
        ctx.get().setPacketHandled(true);
    }
}