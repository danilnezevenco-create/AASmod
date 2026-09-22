// PATH: src/main/java/com/example/aas/network/PacketSyncGameData.java
package com.example.aas.network;

import com.example.aas.client.ClientHooks;
import com.example.aas.world.AASWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import java.util.*;
import java.util.function.Supplier;
import net.minecraftforge.network.NetworkEvent;

/**
 * Full game-state sync packet.
 *
 * Notes on the improvements made here vs. the previous version:
 *  - Construction goes through a {@link Builder} instead of a 46-argument
 *    constructor, so a wrong argument order can no longer silently swap
 *    two fields of the same type (e.g. blueReady/redReady, blueArtTimer/redArtTimer).
 *  - All strings and BlockPos fields are null-safety-normalized on build,
 *    since FriendlyByteBuf#writeUtf / #writeBlockPos throw NPEs on null and
 *    would otherwise crash the network thread and disconnect the player.
 *  - Every collection/map read on decode is bounded by MAX_COLLECTION_SIZE.
 *    Without a bound, a corrupted or hostile packet can make readCollection
 *    allocate an enormous list purely from a varint size prefix (OOM vector).
 *  - The four repeated (write element / read element) blocks for
 *    VehicleRecord, HubInfo, MapMarker and ActiveStrike are pulled out into
 *    named helper methods so the encode and decode side are easy to compare
 *    field-by-field, and so future field changes only need one edit.
 *  - Capture points now use writeNbt/readNbt per element directly instead of
 *    hand-rolling a "Points" ListTag wrapper CompoundTag.
 */
public class PacketSyncGameData {

    /** Upper bound for any collection/map read from the wire in this packet. */
    private static final int MAX_COLLECTION_SIZE = 4096;

    // 1. Tickets / respawn
    public final int blueTickets, redTickets;
    public final boolean hasBlueRally, hasRedRally;
    public final boolean blueBleeding, redBleeding;
    public final int respawnTime;
    public final boolean blueBlocked, redBlocked;
    public final boolean hubSpawnCosts;
    public final int hubSpawnCost;
    public final boolean blueReady, redReady;
    public final String gameMode;
    public final String invasionDefender;
    public final int invasionPrepTicks;
    public final List<AASWorldData.StationInfo> stations;

    // 2. Map
    public final int mapCenterX, mapCenterZ, mapSizeBlocks;
    public final String currentMapImage;

    // 3. Entities
    public final List<AASWorldData.VehicleRecord> markedVehicles;
    public final List<AASWorldData.HubInfo> hubs;

    // 4. Factions
    public final String blueFaction, redFaction;
    public final String blueCustomName, redCustomName;

    // 5. Game state
    public final boolean isGameStarted;
    public final List<AASWorldData.CapturePoint> capturePoints;

    // 6. Spawns / kits
    public final Map<String, BlockPos> blueSpawns, redSpawns, neutralSpawns;
    public final Map<String, String> playerKits;

    // 7. Markers / start vote
    public final List<AASWorldData.MapMarker> activeMarkers;
    public final boolean voteActive;
    public final int voteTimer;
    public final Map<UUID, Boolean> votes;

    // 8. Commanders
    public final int blueCMDId, redCMDId;

    public final boolean blueCmdVoteActive;
    public final String blueCmdCandidateName;
    public final int blueCmdCandidateId;
    public final int blueCmdVoteTimer;
    public final Map<UUID, Boolean> blueCmdVotes;

    public final boolean redCmdVoteActive;
    public final String redCmdCandidateName;
    public final int redCmdCandidateId;
    public final int redCmdVoteTimer;
    public final Map<UUID, Boolean> redCmdVotes;

    // 9. Artillery
    public final List<AASWorldData.ActiveStrike> activeStrikes;
    public final BlockPos blueArtPos, redArtPos;
    public final int blueArtTimer, redArtTimer;
    public final String blueArtReqName, redArtReqName;

    private PacketSyncGameData(Builder b) {
        this.blueTickets = b.blueTickets;
        this.redTickets = b.redTickets;
        this.hasBlueRally = b.hasBlueRally;
        this.hasRedRally = b.hasRedRally;
        this.blueBleeding = b.blueBleeding;
        this.redBleeding = b.redBleeding;
        this.respawnTime = b.respawnTime;
        this.blueBlocked = b.blueBlocked;
        this.redBlocked = b.redBlocked;
        this.hubSpawnCosts = b.hubSpawnCosts;
        this.hubSpawnCost = b.hubSpawnCost;
        this.blueReady = b.blueReady;
        this.redReady = b.redReady;
        this.gameMode = orEmpty(b.gameMode);
        this.invasionDefender = orEmpty(b.invasionDefender);
        this.invasionPrepTicks = b.invasionPrepTicks;
        this.stations = b.stations != null ? b.stations : List.of();

        this.mapCenterX = b.mapCenterX;
        this.mapCenterZ = b.mapCenterZ;
        this.mapSizeBlocks = b.mapSizeBlocks;
        this.currentMapImage = orEmpty(b.currentMapImage);

        this.markedVehicles = b.markedVehicles != null ? b.markedVehicles : List.of();
        this.hubs = b.hubs != null ? b.hubs : List.of();

        this.blueFaction = orEmpty(b.blueFaction);
        this.redFaction = orEmpty(b.redFaction);
        this.blueCustomName = orEmpty(b.blueCustomName);
        this.redCustomName = orEmpty(b.redCustomName);

        this.isGameStarted = b.isGameStarted;
        this.capturePoints = b.capturePoints != null ? b.capturePoints : List.of();

        this.blueSpawns = b.blueSpawns != null ? b.blueSpawns : Map.of();
        this.redSpawns = b.redSpawns != null ? b.redSpawns : Map.of();
        this.neutralSpawns = b.neutralSpawns != null ? b.neutralSpawns : Map.of();
        this.playerKits = b.playerKits != null ? b.playerKits : Map.of();

        this.activeMarkers = b.activeMarkers != null ? b.activeMarkers : List.of();
        this.voteActive = b.voteActive;
        this.voteTimer = b.voteTimer;
        this.votes = b.votes != null ? b.votes : Map.of();

        this.blueCMDId = b.blueCMDId;
        this.redCMDId = b.redCMDId;

        this.blueCmdVoteActive = b.blueCmdVoteActive;
        this.blueCmdCandidateName = orEmpty(b.blueCmdCandidateName);
        this.blueCmdCandidateId = b.blueCmdCandidateId;
        this.blueCmdVoteTimer = b.blueCmdVoteTimer;
        this.blueCmdVotes = b.blueCmdVotes != null ? b.blueCmdVotes : Map.of();

        this.redCmdVoteActive = b.redCmdVoteActive;
        this.redCmdCandidateName = orEmpty(b.redCmdCandidateName);
        this.redCmdCandidateId = b.redCmdCandidateId;
        this.redCmdVoteTimer = b.redCmdVoteTimer;
        this.redCmdVotes = b.redCmdVotes != null ? b.redCmdVotes : Map.of();

        this.activeStrikes = b.activeStrikes != null ? b.activeStrikes : List.of();
        this.blueArtPos = b.blueArtPos != null ? b.blueArtPos : BlockPos.ZERO;
        this.redArtPos = b.redArtPos != null ? b.redArtPos : BlockPos.ZERO;
        this.blueArtTimer = b.blueArtTimer;
        this.redArtTimer = b.redArtTimer;
        this.blueArtReqName = orEmpty(b.blueArtReqName);
        this.redArtReqName = orEmpty(b.redArtReqName);
    }

    private static String orEmpty(String s) {
        return s != null ? s : "";
    }

    // ------------------------------------------------------------------
    // Encode
    // ------------------------------------------------------------------

    public static void encode(PacketSyncGameData msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.blueTickets);
        buf.writeInt(msg.redTickets);
        buf.writeBoolean(msg.hasBlueRally);
        buf.writeBoolean(msg.hasRedRally);
        buf.writeBoolean(msg.blueBleeding);
        buf.writeBoolean(msg.redBleeding);
        buf.writeInt(msg.respawnTime);
        buf.writeBoolean(msg.blueBlocked);
        buf.writeBoolean(msg.redBlocked);
        buf.writeBoolean(msg.hubSpawnCosts);
        buf.writeInt(msg.hubSpawnCost);
        buf.writeCollection(msg.stations, PacketSyncGameData::writeStation);

        buf.writeInt(msg.mapCenterX);
        buf.writeInt(msg.mapCenterZ);
        buf.writeInt(msg.mapSizeBlocks);
        buf.writeUtf(msg.currentMapImage);

        buf.writeCollection(msg.markedVehicles, PacketSyncGameData::writeVehicle);
        buf.writeCollection(msg.hubs, PacketSyncGameData::writeHub);

        buf.writeUtf(msg.blueFaction);
        buf.writeUtf(msg.redFaction);
        buf.writeUtf(msg.blueCustomName);
        buf.writeUtf(msg.redCustomName);
        buf.writeBoolean(msg.isGameStarted);

        buf.writeCollection(msg.capturePoints, (b, cp) -> b.writeNbt(cp.save()));

        buf.writeMap(msg.blueSpawns, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeBlockPos);
        buf.writeMap(msg.redSpawns, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeBlockPos);
        buf.writeMap(msg.neutralSpawns, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeBlockPos);
        buf.writeMap(msg.playerKits, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeUtf);

        buf.writeCollection(msg.activeMarkers, PacketSyncGameData::writeMarker);

        buf.writeBoolean(msg.voteActive);
        buf.writeInt(msg.voteTimer);
        buf.writeMap(msg.votes, FriendlyByteBuf::writeUUID, FriendlyByteBuf::writeBoolean);

        buf.writeInt(msg.blueCMDId);
        buf.writeInt(msg.redCMDId);

        buf.writeBoolean(msg.blueCmdVoteActive);
        buf.writeUtf(msg.blueCmdCandidateName);
        buf.writeInt(msg.blueCmdCandidateId);
        buf.writeInt(msg.blueCmdVoteTimer);
        buf.writeMap(msg.blueCmdVotes, FriendlyByteBuf::writeUUID, FriendlyByteBuf::writeBoolean);

        buf.writeBoolean(msg.redCmdVoteActive);
        buf.writeUtf(msg.redCmdCandidateName);
        buf.writeInt(msg.redCmdCandidateId);
        buf.writeInt(msg.redCmdVoteTimer);
        buf.writeMap(msg.redCmdVotes, FriendlyByteBuf::writeUUID, FriendlyByteBuf::writeBoolean);

        buf.writeCollection(msg.activeStrikes, PacketSyncGameData::writeStrike);

        buf.writeBlockPos(msg.blueArtPos);
        buf.writeBlockPos(msg.redArtPos);
        buf.writeInt(msg.blueArtTimer);
        buf.writeInt(msg.redArtTimer);
        buf.writeUtf(msg.blueArtReqName);
        buf.writeUtf(msg.redArtReqName);
        buf.writeBoolean(msg.blueReady);
        buf.writeBoolean(msg.redReady);
        buf.writeUtf(msg.gameMode);
        buf.writeUtf(msg.invasionDefender);
        buf.writeInt(msg.invasionPrepTicks);
    }

    private static void writeVehicle(FriendlyByteBuf b, AASWorldData.VehicleRecord v) {
        b.writeUUID(v.uuid);
        b.writeUtf(v.team);
        b.writeUtf(v.type);
        b.writeDouble(v.x);
        b.writeDouble(v.y);
        b.writeDouble(v.z);
        b.writeFloat(v.yaw);
    }

    private static void writeHub(FriendlyByteBuf b, AASWorldData.HubInfo h) {
        b.writeBlockPos(h.pos);
        b.writeUtf(h.team);
        b.writeBoolean(h.constructed);
        b.writeUtf(h.dimension);
        b.writeBoolean(h.isBlocked);
        b.writeInt(h.materials);
        b.writeUtf(h.builderName);
    }

    private static void writeMarker(FriendlyByteBuf b, AASWorldData.MapMarker m) {
        b.writeBlockPos(m.pos);
        b.writeUtf(m.type);
        b.writeUtf(m.team);
        b.writeLong(m.expiryTick);
    }

    private static void writeStrike(FriendlyByteBuf b, AASWorldData.ActiveStrike s) {
        b.writeBlockPos(s.pos);
        b.writeUtf(s.team);
        b.writeInt(s.stage);
        b.writeInt(s.ticksLeft);
    }

    // ------------------------------------------------------------------
    // Decode
    // ------------------------------------------------------------------

    public static PacketSyncGameData decode(FriendlyByteBuf buf) {
        Builder b = new Builder();

        b.blueTickets = buf.readInt();
        b.redTickets = buf.readInt();
        b.hasBlueRally = buf.readBoolean();
        b.hasRedRally = buf.readBoolean();
        b.blueBleeding = buf.readBoolean();
        b.redBleeding = buf.readBoolean();
        b.respawnTime = buf.readInt();
        b.blueBlocked = buf.readBoolean();
        b.redBlocked = buf.readBoolean();
        b.hubSpawnCosts = buf.readBoolean();
        b.hubSpawnCost = buf.readInt();
        b.stations = readBoundedList(buf, PacketSyncGameData::readStation);

        b.mapCenterX = buf.readInt();
        b.mapCenterZ = buf.readInt();
        b.mapSizeBlocks = buf.readInt();
        b.currentMapImage = buf.readUtf();

        b.markedVehicles = readBoundedList(buf, PacketSyncGameData::readVehicle);
        b.hubs = readBoundedList(buf, PacketSyncGameData::readHub);

        b.blueFaction = buf.readUtf();
        b.redFaction = buf.readUtf();
        b.blueCustomName = buf.readUtf();
        b.redCustomName = buf.readUtf();
        b.isGameStarted = buf.readBoolean();

        b.capturePoints = readBoundedList(buf, buf2 -> {
            CompoundTag tag = buf2.readNbt();
            return tag != null ? AASWorldData.CapturePoint.load(tag) : null;
        });
        b.capturePoints.removeIf(Objects::isNull);

        b.blueSpawns = readBoundedMap(buf, FriendlyByteBuf::readUtf, FriendlyByteBuf::readBlockPos);
        b.redSpawns = readBoundedMap(buf, FriendlyByteBuf::readUtf, FriendlyByteBuf::readBlockPos);
        b.neutralSpawns = readBoundedMap(buf, FriendlyByteBuf::readUtf, FriendlyByteBuf::readBlockPos);
        b.playerKits = readBoundedMap(buf, FriendlyByteBuf::readUtf, FriendlyByteBuf::readUtf);

        b.activeMarkers = readBoundedList(buf, PacketSyncGameData::readMarker);

        b.voteActive = buf.readBoolean();
        b.voteTimer = buf.readInt();
        b.votes = readBoundedMap(buf, FriendlyByteBuf::readUUID, FriendlyByteBuf::readBoolean);

        b.blueCMDId = buf.readInt();
        b.redCMDId = buf.readInt();

        b.blueCmdVoteActive = buf.readBoolean();
        b.blueCmdCandidateName = buf.readUtf();
        b.blueCmdCandidateId = buf.readInt();
        b.blueCmdVoteTimer = buf.readInt();
        b.blueCmdVotes = readBoundedMap(buf, FriendlyByteBuf::readUUID, FriendlyByteBuf::readBoolean);

        b.redCmdVoteActive = buf.readBoolean();
        b.redCmdCandidateName = buf.readUtf();
        b.redCmdCandidateId = buf.readInt();
        b.redCmdVoteTimer = buf.readInt();
        b.redCmdVotes = readBoundedMap(buf, FriendlyByteBuf::readUUID, FriendlyByteBuf::readBoolean);

        b.activeStrikes = readBoundedList(buf, PacketSyncGameData::readStrike);

        b.blueArtPos = buf.readBlockPos();
        b.redArtPos = buf.readBlockPos();
        b.blueArtTimer = buf.readInt();
        b.redArtTimer = buf.readInt();
        b.blueArtReqName = buf.readUtf();
        b.redArtReqName = buf.readUtf();

        b.blueReady = buf.readBoolean();
        b.redReady = buf.readBoolean();
        b.gameMode = buf.readUtf();
        b.invasionDefender = buf.readUtf();
        b.invasionPrepTicks = buf.readInt();

        return b.build();
    }

    private static AASWorldData.VehicleRecord readVehicle(FriendlyByteBuf b) {
        return new AASWorldData.VehicleRecord(
                b.readUUID(), b.readUtf(), b.readUtf(),
                b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat(), null
        );
    }

    private static AASWorldData.HubInfo readHub(FriendlyByteBuf b) {
        // Читаем СТРОГО в том порядке, в котором они были записаны в writeHub!
        BlockPos pos = b.readBlockPos();
        String team = b.readUtf();
        boolean constructed = b.readBoolean();
        String dimension = b.readUtf();
        boolean isBlocked = b.readBoolean();
        int materials = b.readInt();
        String builderName = b.readUtf();

        // Теперь, когда всё прочитано правильно, создаем объект
        AASWorldData.HubInfo h = new AASWorldData.HubInfo(pos, team, constructed, dimension, builderName);
        h.isBlocked = isBlocked;
        h.materials = materials;
        return h;
    }

    private static AASWorldData.MapMarker readMarker(FriendlyByteBuf b) {
        return new AASWorldData.MapMarker(b.readBlockPos(), b.readUtf(), b.readUtf(), b.readLong());
    }

    private static AASWorldData.ActiveStrike readStrike(FriendlyByteBuf b) {
        AASWorldData.ActiveStrike s = new AASWorldData.ActiveStrike(b.readBlockPos(), b.readUtf());
        s.stage = b.readInt();
        s.ticksLeft = b.readInt();
        return s;
    }

    /**
     * Reads a varint-prefixed list while rejecting sizes above
     * {@link #MAX_COLLECTION_SIZE}, so a corrupted/hostile packet can't force
     * an oversized allocation before any element is even read.
     */
    private static <T> ArrayList<T> readBoundedList(FriendlyByteBuf buf, java.util.function.Function<FriendlyByteBuf, T> reader) {
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_COLLECTION_SIZE) {
            throw new IllegalArgumentException("PacketSyncGameData: refusing to read list of size " + size);
        }
        ArrayList<T> result = new ArrayList<>(Math.min(size, 256));
        for (int i = 0; i < size; i++) {
            result.add(reader.apply(buf));
        }
        return result;
    }

    private static <K, V> Map<K, V> readBoundedMap(FriendlyByteBuf buf, java.util.function.Function<FriendlyByteBuf, K> keyReader, java.util.function.Function<FriendlyByteBuf, V> valReader) {
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_COLLECTION_SIZE) {
            throw new IllegalArgumentException("PacketSyncGameData: refusing to read map of size " + size);
        }
        Map<K, V> result = new HashMap<>(Math.min(size, 256));
        for (int i = 0; i < size; i++) {
            result.put(keyReader.apply(buf), valReader.apply(buf));
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Handle
    // ------------------------------------------------------------------

    public static void handle(PacketSyncGameData msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.handleSyncGameData(msg)));
        context.setPacketHandled(true);
    }

    // ------------------------------------------------------------------
    // Builder
    // ------------------------------------------------------------------

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int blueTickets, redTickets;
        private boolean hasBlueRally, hasRedRally;
        private boolean blueBleeding, redBleeding;
        private int respawnTime;
        private boolean blueBlocked, redBlocked;
        private boolean hubSpawnCosts;
        private int hubSpawnCost;
        private boolean blueReady, redReady;
        private String gameMode;
        private String invasionDefender;
        private int invasionPrepTicks;

        private int mapCenterX, mapCenterZ, mapSizeBlocks;
        private String currentMapImage;

        private List<AASWorldData.VehicleRecord> markedVehicles;
        private List<AASWorldData.HubInfo> hubs;

        private String blueFaction, redFaction;
        private String blueCustomName, redCustomName;

        private boolean isGameStarted;
        private List<AASWorldData.CapturePoint> capturePoints;

        private Map<String, BlockPos> blueSpawns, redSpawns, neutralSpawns;
        private Map<String, String> playerKits;

        private List<AASWorldData.MapMarker> activeMarkers;
        private boolean voteActive;
        private int voteTimer;
        private Map<UUID, Boolean> votes;

        private int blueCMDId, redCMDId;

        private boolean blueCmdVoteActive;
        private String blueCmdCandidateName;
        private int blueCmdCandidateId;
        private int blueCmdVoteTimer;
        private Map<UUID, Boolean> blueCmdVotes;

        private boolean redCmdVoteActive;
        private String redCmdCandidateName;
        private int redCmdCandidateId;
        private int redCmdVoteTimer;
        private Map<UUID, Boolean> redCmdVotes;

        private List<AASWorldData.ActiveStrike> activeStrikes;
        private BlockPos blueArtPos, redArtPos;
        private int blueArtTimer, redArtTimer;
        private String blueArtReqName, redArtReqName;

        public Builder tickets(int blue, int red) { this.blueTickets = blue; this.redTickets = red; return this; }
        public Builder rally(boolean blue, boolean red) { this.hasBlueRally = blue; this.hasRedRally = red; return this; }
        public Builder bleeding(boolean blue, boolean red) { this.blueBleeding = blue; this.redBleeding = red; return this; }
        public Builder respawnTime(int ticks) { this.respawnTime = ticks; return this; }
        public Builder blocked(boolean blue, boolean red) { this.blueBlocked = blue; this.redBlocked = red; return this; }
        public Builder hubSpawnCost(boolean enabled, int cost) { this.hubSpawnCosts = enabled; this.hubSpawnCost = cost; return this; }
        public Builder ready(boolean blue, boolean red) { this.blueReady = blue; this.redReady = red; return this; }
        public Builder gameMode(String mode) { this.gameMode = mode; return this; }
        public Builder invasion(String defenderTeam, int prepTicks) { this.invasionDefender = defenderTeam; this.invasionPrepTicks = prepTicks; return this; }

        public Builder map(int centerX, int centerZ, int sizeBlocks, String imageId) {
            this.mapCenterX = centerX; this.mapCenterZ = centerZ; this.mapSizeBlocks = sizeBlocks; this.currentMapImage = imageId;
            return this;
        }

        public Builder vehicles(List<AASWorldData.VehicleRecord> vehicles) { this.markedVehicles = vehicles; return this; }
        public Builder hubs(List<AASWorldData.HubInfo> hubs) { this.hubs = hubs; return this; }

        public Builder factions(String blue, String red) { this.blueFaction = blue; this.redFaction = red; return this; }
        public Builder customNames(String blue, String red) { this.blueCustomName = blue; this.redCustomName = red; return this; }

        public Builder gameStarted(boolean started) { this.isGameStarted = started; return this; }
        public Builder capturePoints(List<AASWorldData.CapturePoint> points) { this.capturePoints = points; return this; }

        public Builder spawns(Map<String, BlockPos> blue, Map<String, BlockPos> red, Map<String, BlockPos> neutral) {
            this.blueSpawns = blue; this.redSpawns = red; this.neutralSpawns = neutral; return this;
        }
        public Builder playerKits(Map<String, String> kits) { this.playerKits = kits; return this; }

        public Builder markers(List<AASWorldData.MapMarker> markers) { this.activeMarkers = markers; return this; }
        public Builder startVote(boolean active, int timer, Map<UUID, Boolean> votes) {
            this.voteActive = active; this.voteTimer = timer; this.votes = votes; return this;
        }

        public Builder commanderIds(int blueId, int redId) { this.blueCMDId = blueId; this.redCMDId = redId; return this; }

        public Builder blueCommanderVote(boolean active, String candidateName, int candidateId, int timer, Map<UUID, Boolean> votes) {
            this.blueCmdVoteActive = active; this.blueCmdCandidateName = candidateName;
            this.blueCmdCandidateId = candidateId; this.blueCmdVoteTimer = timer; this.blueCmdVotes = votes;
            return this;
        }

        public Builder redCommanderVote(boolean active, String candidateName, int candidateId, int timer, Map<UUID, Boolean> votes) {
            this.redCmdVoteActive = active; this.redCmdCandidateName = candidateName;
            this.redCmdCandidateId = candidateId; this.redCmdVoteTimer = timer; this.redCmdVotes = votes;
            return this;
        }

        public Builder activeStrikes(List<AASWorldData.ActiveStrike> strikes) { this.activeStrikes = strikes; return this; }

        public Builder artillery(BlockPos bluePos, BlockPos redPos, int blueTimer, int redTimer, String blueReqName, String redReqName) {
            this.blueArtPos = bluePos; this.redArtPos = redPos;
            this.blueArtTimer = blueTimer; this.redArtTimer = redTimer;
            this.blueArtReqName = blueReqName; this.redArtReqName = redReqName;
            return this;
        }

        public PacketSyncGameData build() {
            return new PacketSyncGameData(this);
        }

        private List<AASWorldData.StationInfo> stations;

        public Builder stations(List<AASWorldData.StationInfo> s) {
            this.stations = s;
            return this;
        }
    }
    private static void writeStation(FriendlyByteBuf b, AASWorldData.StationInfo s) {
        b.writeBlockPos(s.pos);
        b.writeUtf(s.team);
        b.writeUtf(s.dimension);
    }

    private static AASWorldData.StationInfo readStation(FriendlyByteBuf b) {
        return new AASWorldData.StationInfo(b.readBlockPos(), b.readUtf(), b.readUtf());
    }
}