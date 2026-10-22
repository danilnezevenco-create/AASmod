package com.example.aas.uav;

import com.example.aas.entity.ReconUavEntity;
import com.example.aas.network.PacketHandler;
import com.example.aas.network.PacketUavViewState;
import com.example.aas.world.AASWorldData;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.chunk.LevelChunk;
import org.slf4j.Logger;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /** Keeps the chunks around the UAV loaded server side (the player's own ticket stays on the body). */
    public static final TicketType<ChunkPos> VIEW_TICKET =
            TicketType.create("aas_uav_view", Comparator.comparingLong(ChunkPos::toLong), 40);

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Set by ChunkMapUavMixin the first time it redirects a viewer. */
    public static volatile boolean mixinActive = false;

    /** Sends chunks around a centre chunk straight to one client. */
    private static final class Sender {
        int cx = Integer.MIN_VALUE, cz = Integer.MIN_VALUE;
        final Set<Long> sent = new HashSet<>();

        /** @return true when every loaded chunk inside the radius has been sent */
        boolean step(ServerLevel level, ServerPlayer p, int ccx, int ccz, int r, int budget, int syncLoads) {
            if (ccx != cx || ccz != cz) {
                cx = ccx; cz = ccz;
                p.connection.send(new ClientboundSetChunkCacheCenterPacket(ccx, ccz));
                sent.removeIf(k -> Math.abs(ChunkPos.getX(k) - ccx) > r || Math.abs(ChunkPos.getZ(k) - ccz) > r);
            }
            boolean missing = false;
            for (int d = 0; d <= r; d++) {
                for (int dx = -d; dx <= d; dx++) {
                    for (int dz = -d; dz <= d; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != d) continue;
                        long key = ChunkPos.asLong(ccx + dx, ccz + dz);
                        if (sent.contains(key)) continue;
                        if (budget <= 0) return false;
                        LevelChunk c = level.getChunkSource().getChunkNow(ccx + dx, ccz + dz);
                        if (c == null && syncLoads > 0) { syncLoads--; c = level.getChunk(ccx + dx, ccz + dz); } // load/generate now
                        if (c == null) { missing = true; continue; }
                        p.connection.send(new ClientboundLevelChunkWithLightPacket(c, level.getLightEngine(), null, null));
                        sent.add(key);
                        budget--;
                    }
                }
            }
            return !missing;
        }
    }

    private static final class Restore {
        final Sender sender = new Sender();
        int ticks = 0;
    }

    private static final Map<UUID, Restore> RESTORE = new HashMap<>();

    private static int fallbackRadius(ServerLevel level) {
        return Math.max(2, Math.min(level.getServer().getPlayerList().getViewDistance(), 16));
    }

    private static final class Session {
        final Sender sender = new Sender();
        long enteredAt = -1;
        final String team;
        Vec3 viewPos;
        final Vec3 anchor;
        final int slot;
        Session(String team, Vec3 viewPos, Vec3 anchor, int slot) {
            this.team = team; this.viewPos = viewPos; this.anchor = anchor; this.slot = slot;
        }
    }
    private static final class Guard {
        final Vec3 anchor; final long until;
        Guard(Vec3 a, long u) { anchor = a; until = u; }
    }
    private static final Map<UUID, Guard> GUARD = new HashMap<>();

    private static void tickGuard(ServerLevel level, MinecraftServer server, long now) {
        if (GUARD.isEmpty()) return;
        for (UUID id : new ArrayList<>(GUARD.keySet())) {
            Guard g = GUARD.get(id);
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (g == null || p == null || !p.isAlive() || now > g.until) { GUARD.remove(id); continue; }
            if (p.serverLevel() != level) continue;
            if (p.getY() < g.anchor.y - 1.0) {
                LOGGER.warn("[AAS] body dropped to y={} after UAV exit, put back at y={}", p.getY(), g.anchor.y);
                p.connection.teleport(g.anchor.x, g.anchor.y, g.anchor.z, p.getYRot(), p.getXRot());
                p.fallDistance = 0f;
                p.setDeltaMovement(Vec3.ZERO);
            }
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

        RESTORE.remove(p.getUUID());
        SESSIONS.put(p.getUUID(), new Session(team, uav.position(), p.position(), p.getInventory().selected));
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> p), new PacketUavViewState(true, 0));
        keepBodyLoaded(level, p);
        keepViewLoaded(level, uav.position());
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
            LOGGER.info("[AAS] UAV exit: reason={} bodyY={} anchorY={} onGround={} bodyChunkLoaded={}",
                    reason, p.getY(), s.anchor.y, p.onGround(),
                    p.serverLevel().getChunkSource().getChunkNow(p.chunkPosition().x, p.chunkPosition().z) != null);
            GUARD.put(p.getUUID(), new Guard(s.anchor, p.serverLevel().getGameTime() + 200)); // 10 s
            if (p.position().distanceToSqr(s.anchor) > 0.01) {
                p.connection.teleport(s.anchor.x, s.anchor.y, s.anchor.z, p.getYRot(), p.getXRot());
            }
            p.fallDistance = 0f;
            p.setDeltaMovement(Vec3.ZERO);
            p.serverLevel().getChunkSource().move(p); // tracking back to the body
            RESTORE.put(p.getUUID(), new Restore()); // resend the body's surroundings
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
    public static void forget(UUID id) { SESSIONS.remove(id); GUARD.remove(id); RESTORE.remove(id); }

    public static void clearAll() { SESSIONS.clear(); RESTORE.clear(); GUARD.clear(); }

    // ------------------------------------------------------------------ per tick
    public static void tickLevel(ServerLevel level) {
        if (SESSIONS.isEmpty() && RESTORE.isEmpty() && GUARD.isEmpty()) return;
        MinecraftServer server = level.getServer();
        long now = level.getGameTime();
        tickGuard(level, server, now);   // was never called: nothing caught the body falling after exit
        tickRestore(level, server, now);
        if (SESSIONS.isEmpty()) return;
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

            if (s.enteredAt < 0) s.enteredAt = now;
            int ccx = SectionPos.blockToSectionCoord(Mth.floor(s.viewPos.x));
            int ccz = SectionPos.blockToSectionCoord(Mth.floor(s.viewPos.z));
            if (now % 2 == 0) s.sender.step(level, p, ccx, ccz, fallbackRadius(level), 16, 1); // always, on top of the mixin
            long age = now - s.enteredAt;
            if (age == 60 || age == 200) logView(level, p, s, ccx, ccz);

            // --- server-side input lock: the body must not move or switch slots ---
            if (p.getInventory().selected != s.slot) {
                p.getInventory().selected = s.slot;
                p.connection.send(new ClientboundSetCarriedItemPacket(s.slot));
            }
            if (p.position().distanceToSqr(s.anchor) > 4.0) {
                p.connection.teleport(s.anchor.x, s.anchor.y, s.anchor.z, p.getYRot(), p.getXRot());
            }

            if (now % 20 == 0) keepBodyLoaded(level, p);
            if (now % 10 == 0) keepViewLoaded(level, s.viewPos);
            if (now % 5 == 0) level.getChunkSource().move(p); // follow the UAV with chunk/entity tracking
        }
    }

    /** Diagnostics: how many chunks around the UAV are loaded on the server / sent by the sender. */
    private static void logView(ServerLevel level, ServerPlayer p, Session s, int ccx, int ccz) {
        int r = fallbackRadius(level), loaded = 0, total = 0;
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            total++;
            if (level.getChunkSource().getChunkNow(ccx + dx, ccz + dz) != null) loaded++;
        }
        LOGGER.info("[AAS] UAV view: mixinActive={} uavChunk=({},{}) bodyChunk=({},{}) r={} loadedOnServer={}/{} sentByUs={} uavY={}",
                mixinActive, ccx, ccz, p.chunkPosition().x, p.chunkPosition().z, r, loaded, total, s.sender.sent.size(), (int) s.viewPos.y);
    }

    private static void tickRestore(ServerLevel level, MinecraftServer server, long now) {
        if (RESTORE.isEmpty() || now % 2 != 0) return;
        for (UUID id : new ArrayList<>(RESTORE.keySet())) {
            Restore r = RESTORE.get(id);
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (r == null || p == null || p.serverLevel() != level) continue;
            boolean done = r.sender.step(level, p, p.chunkPosition().x, p.chunkPosition().z, fallbackRadius(level), 24, 1);
            if (done || ++r.ticks > 200) RESTORE.remove(id);
        }
    }

    private static void keepViewLoaded(ServerLevel level, Vec3 pos) {
        ChunkPos cp = new ChunkPos(BlockPos.containing(pos));
        level.getChunkSource().addRegionTicket(VIEW_TICKET, cp, 3, cp); // small: only keeps the UAV's own surroundings alive
    }

    /** The body's own chunk must stay loaded + entity-ticking while the vanilla player ticket is moved to the UAV. */
    private static void keepBodyLoaded(ServerLevel level, ServerPlayer p) {
        ChunkPos cp = p.chunkPosition();
        level.getChunkSource().addRegionTicket(BODY_TICKET, cp, 2, cp);
    }
}