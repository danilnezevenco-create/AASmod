package com.example.aas.network;

import com.example.aas.util.VehicleListService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Client -> server: "send me the vehicle summary".
 * Sent when the panel is opened and then about once per second while it stays open,
 * so nothing is sent (and no server work is done) while the panel is closed.
 */
public class PacketRequestVehicleList {

    /** Minimum gap between two answers to the same player (ticks). Protects against request spam. */
    private static final long MIN_GAP_TICKS = 15L;
    private static final Map<UUID, Long> LAST_ANSWER = new ConcurrentHashMap<>();

    public PacketRequestVehicleList() {}

    public static void encode(PacketRequestVehicleList msg, FriendlyByteBuf buf) {}

    public static PacketRequestVehicleList decode(FriendlyByteBuf buf) { return new PacketRequestVehicleList(); }

    public static void handle(PacketRequestVehicleList msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            long now = player.serverLevel().getGameTime();
            Long last = LAST_ANSWER.get(player.getUUID());
            if (last != null && now >= last && now - last < MIN_GAP_TICKS) return;
            LAST_ANSWER.put(player.getUUID(), now);

            PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), VehicleListService.buildFor(player));
        });
        ctx.get().setPacketHandled(true);
    }
}