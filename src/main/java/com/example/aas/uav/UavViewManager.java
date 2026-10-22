package com.example.aas.uav;

import com.example.aas.entity.ReconUavEntity;
import com.example.aas.network.PacketHandler;
import com.example.aas.network.PacketUavViewState;
import com.example.aas.world.AASWorldData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server side of "looking through the UAV". The player body is NOT moved or replaced: it stays where it is,
 * stays loaded (own chunk ticket) and stays vulnerable; only the chunk/entity tracking centre is redirected to the
 * UAV (see ChunkMapUavMixin / TrackedEntityUavMixin) and the body's input is locked.
 */
public final class UavViewManager {
    private UavViewManager() {}

    public static final TicketType<ChunkPos> BODY_TICKET =
            TicketType.create("aas_uav_body", Comparator.comparingLong(ChunkPos::toLong), 40);

    private static final class Session {
        final String team;
        Vec3 viewPos;
        final Vec3 anchor;
        final int slot;
        Session(String team, Vec3 viewPos, Vec3 anchor, int slot) {
            this.team = team; this.viewPos = viewPos; this.anchor = anchor; this.slot = slot;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    public static boolean isViewing(ServerPlayer p) { return SESSIONS.containsKey(p.getUUID()); }

    /** Where the player's chunks/entities are tracked from, or null for the normal body position. Used by mixins. */
    public static Vec3 viewPos(ServerPlayer p) {
        if (SESSIONS.isEmpty()) return null;
        Session s = SESSIONS.get(p.getUUID());
        return s == null ? null : s.viewPos;
    }

    // ------------------------------------------------------------------ enter / exit
    public static boolean tryEnter(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        AASWorldData d = AASWorldData.get(level);
        String team = UavRules.teamOf(p);
        if (SESSIONS.containsKey(p.getUUID())) return false;
        if (!UavRules.isTeam(team) || !UavRules.isCommander(p, d) || !p.isAlive()) return deny(p, "not_commander");
        if (UavRules.isDowned(p)) return deny(p, "downed");
        UavState u = UavRules.get(d, team);
        Entity e = u == null ? null : level.getEntity(u.entityUuid);
        if (!(e instanceof ReconUavEntity uav) || uav.isRemoved()) return deny(p, "no_uav");

        SESSIONS.put(p.getUUID(), new Session(team, uav.position(), p.position(), p.getInventory().selected));
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> p), new PacketUavViewState(true, 0));
        keepBodyLoaded(level, p);
        level.getChunkSource().move(p); // re-evaluate tracking around the UAV
        return true;
    }

    private static boolean deny(ServerPlayer p, String key) {
        p.displayClientMessage(Component.translatable("aas.uav.msg." + key), true);
        return false;
    }

    public static void exit(ServerPlayer p, UavExitReason reason) {
        Session s = SESSIONS.remove(p.getUUID());
        if (s == null) return;
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> p), new PacketUavViewState(false, reason.ordinal()));
        if (p.isAlive() && !p.hasDisconnected()) {
            p.serverLevel().getChunkSource().move(p); // tracking back to the body
        }
    }

    public static void exitTeam(ServerLevel level, String team, UavExitReason reason) {
        MinecraftServer server = level.getServer();
        for (UUID id : new ArrayList<>(SESSIONS.keySet())) {
            Session s = SESSIONS.get(id);
            if (s == null || !s.team.equals(team)) continue;
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) { SESSIONS.remove(id); continue; }
            exit(p, reason);
        }
    }

    /** Player left the server: just forget (tracking is cleaned up by vanilla using the last section). */
    public static void forget(UUID id) { SESSIONS.remove(id); }

    public static void clearAll() { SESSIONS.clear(); }

    // ------------------------------------------------------------------ per tick
    public static void tickLevel(ServerLevel level) {
        if (SESSIONS.isEmpty()) return;
        MinecraftServer server = level.getServer();
        long now = level.getGameTime();
        AASWorldData d = AASWorldData.get(level);

        for (UUID id : new ArrayList<>(SESSIONS.keySet())) {
            Session s = SESSIONS.get(id);
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (s == null) continue;
            if (p == null) { SESSIONS.remove(id); continue; }
            if (p.serverLevel() != level) continue;

            if (!p.isAlive()) { exit(p, UavExitReason.DEAD); continue; }
            if (UavRules.isDowned(p)) { exit(p, UavExitReason.DOWNED); continue; }
            if (!UavRules.isCommander(p, d)) { exit(p, UavExitReason.NOT_CMD); continue; }

            UavState u = UavRules.get(d, s.team);
            Entity e = u == null ? null : level.getEntity(u.entityUuid);
            if (!(e instanceof ReconUavEntity) || e.isRemoved()) { exit(p, UavExitReason.LOST); continue; }
            s.viewPos = e.position();

            // --- server-side input lock: the body must not move or switch slots ---
            if (p.getInventory().selected != s.slot) {
                p.getInventory().selected = s.slot;
                p.connection.send(new ClientboundSetCarriedItemPacket(s.slot));
            }
            if (p.position().distanceToSqr(s.anchor) > 4.0) {
                p.connection.teleport(s.anchor.x, s.anchor.y, s.anchor.z, p.getYRot(), p.getXRot());
            }

            if (now % 20 == 0) keepBodyLoaded(level, p);
            if (now % 5 == 0) level.getChunkSource().move(p); // follow the UAV with chunk/entity tracking
        }
    }

    /** The body's own chunk must stay loaded + entity-ticking while the vanilla player ticket is moved to the UAV. */
    private static void keepBodyLoaded(ServerLevel level, ServerPlayer p) {
        ChunkPos cp = p.chunkPosition();
        level.getChunkSource().addRegionTicket(BODY_TICKET, cp, 2, cp);
    }
}
