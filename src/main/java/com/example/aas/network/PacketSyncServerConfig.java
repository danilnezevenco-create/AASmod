package com.example.aas.network;

import com.example.aas.client.ClientData;
import com.example.aas.config.AASConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

/**
 * Server -> client config values that the client must use instead of its own local config.
 * Sent on login and on every server config reload (see AASConfigSync).
 */
public class PacketSyncServerConfig {
    private final boolean autoBalance;
    private final String reviveItem;
    private final int minHubDistance;
    private final int hubBuildRadius;
    private final int crateBuildRadius;
    private final boolean hubRequiresCrate; // NEW: hubPlacementRequiresCrate

    public PacketSyncServerConfig(boolean autoBalance, String reviveItem,
                                  int minHubDistance, int hubBuildRadius, int crateBuildRadius,
                                  boolean hubRequiresCrate) {
        this.autoBalance = autoBalance;
        this.reviveItem = reviveItem;
        this.minHubDistance = minHubDistance;
        this.hubBuildRadius = hubBuildRadius;
        this.crateBuildRadius = crateBuildRadius;
        this.hubRequiresCrate = hubRequiresCrate;
    }

    /** Snapshot of the CURRENT server-side config values. Call only on the server. */
    public static PacketSyncServerConfig fromConfig() {
        return new PacketSyncServerConfig(
                AASConfig.AUTO_BALANCE_TEAMS.get(),
                AASConfig.REVIVE_ITEM.get(),
                AASConfig.MIN_HUB_DISTANCE.get(),
                AASConfig.HUB_BUILD_RADIUS.get(),
                AASConfig.CRATE_BUILD_RADIUS.get(),
                AASConfig.HUB_PLACEMENT_REQUIRES_CRATE.get());
    }

    public static void encode(PacketSyncServerConfig msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.autoBalance);
        buf.writeUtf(msg.reviveItem);
        buf.writeVarInt(msg.minHubDistance);
        buf.writeVarInt(msg.hubBuildRadius);
        buf.writeVarInt(msg.crateBuildRadius);
        buf.writeBoolean(msg.hubRequiresCrate);
    }

    public static PacketSyncServerConfig decode(FriendlyByteBuf buf) {
        return new PacketSyncServerConfig(buf.readBoolean(), buf.readUtf(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(PacketSyncServerConfig msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                ClientData.serverAutoBalance = msg.autoBalance;
                ClientData.serverReviveItem = msg.reviveItem;
                ClientData.serverMinHubDistance = msg.minHubDistance;
                ClientData.serverHubBuildRadius = msg.hubBuildRadius;
                ClientData.serverCrateBuildRadius = msg.crateBuildRadius;
                ClientData.serverHubRequiresCrate = msg.hubRequiresCrate ? 1 : 0;
            });
        });
        ctx.get().setPacketHandled(true);
    }
}