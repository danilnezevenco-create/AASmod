package com.example.aas.config;

import com.example.aas.entity.SupplyCrateEntity;
import com.example.aas.network.PacketHandler;
import com.example.aas.network.PacketSyncServerConfig;
import com.example.aas.world.AASWorldData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

/** Pushes server config to clients on reload + server-side build range validation. */
@Mod.EventBusSubscriber(modid = "aas", bus = Mod.EventBusSubscriber.Bus.MOD)
public class AASConfigSync {

    /** Fires when config file changes on disk (or is saved from the config screen). */
    @SubscribeEvent
    public static void onReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() != AASConfig.SPEC) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return; // pure client: nothing to broadcast
        // the watcher fires on another thread -> hop to the server thread
        server.execute(() ->
                PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), PacketSyncServerConfig.fromConfig()));
    }

    /**
     * Server-side check: is `pos` within hubBuildRadius of an own/neutral constructed HUB
     * or within crateBuildRadius of an own/neutral Supply Crate? Creative bypasses.
     * Call from the build handler (PacketBuildRequest) BEFORE spending materials / placing.
     */
    public static boolean isInBuildRange(ServerPlayer player, BlockPos pos) {
        if (player.isCreative()) return true;

        String team = player.getTeam() != null ? player.getTeam().getName().toUpperCase() : "NEUTRAL";
        String dim = player.level().dimension().location().toString();

        double hubR = AASConfig.HUB_BUILD_RADIUS.get();
        double hubRSq = hubR * hubR;
        AASWorldData data = AASWorldData.get(player.serverLevel());
        for (AASWorldData.HubInfo hub : data.hubs) {
            if (!hub.constructed) continue;
            if (hub.dimension != null && !hub.dimension.equals(dim)) continue;
            if (!hub.team.equalsIgnoreCase(team) && !hub.team.equalsIgnoreCase("NEUTRAL")) continue;
            if (hub.pos.distSqr(pos) <= hubRSq) return true;
        }

        double crateR = AASConfig.CRATE_BUILD_RADIUS.get();
        for (SupplyCrateEntity crate : player.level().getEntitiesOfClass(
                SupplyCrateEntity.class, new AABB(pos).inflate(crateR))) {
            String owner = crate.getTeamOwner();
            if (!owner.equalsIgnoreCase(team) && !owner.equals("NEUTRAL")) continue;
            if (crate.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= crateR * crateR)
                return true;
        }
        return false;
    }
}