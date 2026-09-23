// PATH: src\main\java\com\example\aas\world\AASWorldData.java
package com.example.aas.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import com.example.aas.world.AASWorldData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AASWorldData extends SavedData {
    public List<CapturePoint> capturePoints = new ArrayList<>();
    public List<BlockPos> blueRallies = new ArrayList<>();
    public List<BlockPos> redRallies = new ArrayList<>();
    public List<HubInfo> hubs = new ArrayList<>();
    public List<Squad> squads = new ArrayList<>();
    public int blueCMDId = -1;
    public int redCMDId = -1;
    public boolean blueCmdVoteActive = false;
    public String blueCmdCandidateName = "";
    public int blueCmdCandidateId = -1;
    public int blueCmdVoteTimer = 0;
    public Map<UUID, Boolean> blueCmdVotes = new HashMap<>();
    public int maxBlueHubs = 8;
    public int maxRedHubs = 8;
    public List<StationInfo> vehicleStations = new ArrayList<>();

    public boolean redCmdVoteActive = false;
    public String redCmdCandidateName = "";
    public int redCmdCandidateId = -1;
    public int redCmdVoteTimer = 0;
    public Map<UUID, Boolean> redCmdVotes = new HashMap<>();
    public long blueArtStrikeCD = 0;
    public long redArtStrikeCD = 0;
    public String shapeType = "CUBE"; // CUBE Р С‘Р В»Р С‘ CYLINDER
    public int lockDurationMinutes = 0; // Р СњР В° РЎРѓР С”Р С•Р В»РЎРЉР С”Р С• Р СР С‘Р Р…РЎС“РЎвЂљ Р В±Р В»Р С•Р С”Р С‘РЎР‚РЎС“Р ВµРЎвЂљРЎРѓРЎРЏ РЎвЂљР С•РЎвЂЎР С”Р В°
    public long lockedUntilTick = 0; // Р вЂњР ВµР в„–Р С-РЎвЂљР С‘Р С”, Р Т‘Р С• Р С”Р С•РЎвЂљР С•РЎР‚Р С•Р С–Р С• РЎвЂљР С•РЎвЂЎР С”Р В° Р В·Р В°Р С”РЎР‚РЎвЂ№РЎвЂљР В° Р Т‘Р В»РЎРЏ Р Р†РЎР‚Р В°Р С–Р В°
    public Map<String, BlockPos> blueSpawns = new HashMap<>();
    public Map<String, BlockPos> redSpawns = new HashMap<>();
    public Map<String, BlockPos> neutralSpawns = new HashMap<>();
    public List<VehicleRecord> markedVehicles = new ArrayList<>();
    public int blueTickets = 800;
    public int redTickets = 800;
    public int respawnTimer = 10;
    public int deathTicketCost = 2;
    public String blueFaction = "none";
    public String redFaction = "none";
    public boolean isGameStarted = false;
    public String gameMode = "AAS";
    public String invasionDefender = "NONE";
    public int invasionPrepTicks = 0;
    public boolean pvpEnabled = true;
    public int countdownTicks = 0;
    public boolean countdownActive = false;
    public boolean playedBlueSiren = false;
    public boolean playedRedSiren = false;
    public Map<String, KitInfo> blueKits = new HashMap<>();
    public Map<String, KitInfo> redKits = new HashMap<>();

    public int mapCenterX = 0;
    public int mapCenterZ = 0;
    public int mapSizeBlocks = 2048;
    public String currentMapImage = "map1";

    public static final String[] KIT_NAMES = {
            "Officer", "Pilot Officer", "Mechanic Officer", "Scout", // Р СњР С•Р Р†РЎвЂ№Р Вµ Р С”Р С‘РЎвЂљРЎвЂ№ Р В·Р Т‘Р ВµРЎРѓРЎРЉ
            "LAT", "HAT", "Sapper", "Sniper", "Marksman",
            "LMG", "HMG", "Rifleman", "Medic", "Grenadier", "Assault",
            "Pilot", "Mechanic", "Drone Operator", "Anti_air"
    };

    public AASWorldData() {
        // Р ВР Р…Р С‘РЎвЂ Р С‘Р В°Р В»Р С‘Р В·Р В°РЎвЂ Р С‘РЎРЏ Р С—РЎС“РЎРѓРЎвЂљРЎвЂ№РЎвЂ¦ Р С”Р С‘РЎвЂљР С•Р Р† Р С—РЎР‚Р С‘ РЎРѓР С•Р В·Р Т‘Р В°Р Р…Р С‘Р С‘ Р СР С‘РЎР‚Р В°
        for (String name : KIT_NAMES) {
            blueKits.put(name, new KitInfo(name));
            redKits.put(name, new KitInfo(name));
        }

    }
    public List<BlockPos> triggerBlocks = new ArrayList<>();

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("BlueTickets", blueTickets);
        tag.putInt("RedTickets", redTickets);
        tag.putInt("RespawnTimer", respawnTimer);
        tag.putInt("DeathTicketCost", deathTicketCost);
        tag.putString("BlueFaction", blueFaction);
        tag.putString("RedFaction", redFaction);
        tag.putBoolean("IsGameStarted", isGameStarted);
        tag.putString("GameMode", gameMode);
        tag.putString("InvasionDefender", invasionDefender);
        tag.putInt("InvasionPrepTicks", invasionPrepTicks);
        tag.putBoolean("PvpEnabled", pvpEnabled);
        tag.putInt("CountdownTicks", countdownTicks);
        tag.putBoolean("CountdownActive", countdownActive);
        tag.putBoolean("PlayedBlueSiren", playedBlueSiren);
        tag.putBoolean("PlayedRedSiren", playedRedSiren);
        tag.putInt("MapCenterX", mapCenterX);
        tag.putInt("MapCenterZ", mapCenterZ);
        tag.putInt("MapSizeBlocks", mapSizeBlocks);
        tag.putString("CurrentMapImage", currentMapImage);
        tag.putLong("BlueArtCD", blueArtStrikeCD);
        tag.putLong("RedArtCD", redArtStrikeCD);
        tag.putInt("MaxBlueHubs", maxBlueHubs);
        tag.putInt("MaxRedHubs", maxRedHubs);
        // --- Р РЋР С›Р ТђР В Р С’Р СњР вЂўР СњР ВР вЂў Р РЋР СћР С’Р СћР Р€Р РЋР С’ Р С™Р С›Р СљР С’Р СњР вЂќР ВР В Р С›Р вЂ™ ---
        tag.putInt("BlueCMDId", blueCMDId);
        tag.putInt("RedCMDId", redCMDId);
        ListTag stationList = new ListTag();
        for (StationInfo s : vehicleStations) {
            stationList.add(s.save());
        }
        tag.put("VehicleStations", stationList);

        // --- Р РЋР С›Р ТђР В Р С’Р СњР вЂўР СњР ВР вЂў Р вЂњР С›Р вЂєР С›Р РЋР С›Р вЂ™Р С’Р СњР ВР Р‡ BLUE ---
        tag.putBoolean("BlueCmdActive", blueCmdVoteActive);
        tag.putString("BlueCmdCandName", blueCmdCandidateName);
        tag.putInt("BlueCmdCandId", blueCmdCandidateId);
        tag.putInt("BlueCmdTimer", blueCmdVoteTimer);
        CompoundTag blueVotesTag = new CompoundTag();
        blueCmdVotes.forEach((uuid, val) -> blueVotesTag.putBoolean(uuid.toString(), val));
        tag.put("BlueCmdVotesMap", blueVotesTag);

        // --- Р РЋР С›Р ТђР В Р С’Р СњР вЂўР СњР ВР вЂў Р вЂњР С›Р вЂєР С›Р РЋР С›Р вЂ™Р С’Р СњР ВР Р‡ RED ---
        tag.putBoolean("RedCmdActive", redCmdVoteActive);
        tag.putString("RedCmdCandName", redCmdCandidateName);
        tag.putInt("RedCmdCandId", redCmdCandidateId);
        tag.putInt("RedCmdTimer", redCmdVoteTimer);
        CompoundTag redVotesTag = new CompoundTag();
        redCmdVotes.forEach((uuid, val) -> redVotesTag.putBoolean(uuid.toString(), val));
        tag.put("RedCmdVotesMap", redVotesTag);
        ListTag vehicleList = new ListTag();
        for (VehicleRecord v : markedVehicles) {
            vehicleList.add(v.save());
        }
        ListTag triggerList = new ListTag();
        for (BlockPos p : triggerBlocks) triggerList.add(LongTag.valueOf(p.asLong()));
        tag.put("TriggerBlocks", triggerList);
        tag.put("MarkedVehicles", vehicleList);
        ListTag markerList = new ListTag();
        for (MapMarker m : activeMarkers) markerList.add(m.save());
        tag.put("TacticalMarkers", markerList);
        ListTag blueList = new ListTag();
        for (BlockPos pos : blueRallies) blueList.add(LongTag.valueOf(pos.asLong()));
        tag.put("BlueRallies", blueList);

        ListTag mainZoneList = new ListTag();
        for (MainProtectionZone zone : mainZones) mainZoneList.add(zone.save());
        tag.put("MainZones", mainZoneList);

        if (lobbyCenter != null) {
            CompoundTag centerTag = new CompoundTag();
            centerTag.putInt("X", lobbyCenter.getX());
            centerTag.putInt("Y", lobbyCenter.getY());
            centerTag.putInt("Z", lobbyCenter.getZ());
            tag.put("LobbyCenter", centerTag);
        }

        ListTag lobbyZoneList = new ListTag();
        for (LobbyZone zone : lobbyZones) lobbyZoneList.add(zone.save());
        tag.put("LobbyZones", lobbyZoneList);

        ListTag redList = new ListTag();
        for (BlockPos pos : redRallies) redList.add(LongTag.valueOf(pos.asLong()));
        tag.put("RedRallies", redList);

        ListTag hubList = new ListTag();
        for (HubInfo h : hubs) hubList.add(h.save());
        tag.put("HubsData", hubList);

        CompoundTag blueSpawnsTag = new CompoundTag();
        blueSpawns.forEach((dim, pos) -> blueSpawnsTag.putLong(dim, pos.asLong()));
        tag.put("BlueSpawnsMap", blueSpawnsTag);

        CompoundTag redSpawnsTag = new CompoundTag();
        redSpawns.forEach((dim, pos) -> redSpawnsTag.putLong(dim, pos.asLong()));
        tag.put("RedSpawnsMap", redSpawnsTag);

        CompoundTag neutralSpawnsTag = new CompoundTag();
        neutralSpawns.forEach((dim, pos) -> neutralSpawnsTag.putLong(dim, pos.asLong()));
        tag.put("NeutralSpawnsMap", neutralSpawnsTag);

        ListTag pointsList = new ListTag();
        for (CapturePoint point : capturePoints) pointsList.add(point.save());
        tag.put("CapturePoints", pointsList);

        ListTag squadList = new ListTag();
        for (Squad s : squads) squadList.add(s.save());
        tag.put("Squads", squadList);

        // === Р РЋР С›Р ТђР В Р С’Р СњР вЂўР СњР ВР вЂў Р С™Р ВР СћР С›Р вЂ™ ===
        CompoundTag bKitsTag = new CompoundTag();
        for (KitInfo k : blueKits.values()) bKitsTag.put(k.name, k.save());
        tag.put("BlueKits", bKitsTag);

        CompoundTag rKitsTag = new CompoundTag();
        for (KitInfo k : redKits.values()) rKitsTag.put(k.name, k.save());
        tag.put("RedKits", rKitsTag);
        if (blueArtRequest != null) {
            CompoundTag req = new CompoundTag();
            req.putString("Name", blueArtRequest.requesterName);
            req.putLong("Pos", blueArtRequest.pos.asLong());
            req.putInt("Timer", blueArtRequest.timer);
            tag.put("BlueArtReq", req);
        }
        if (redArtRequest != null) {
            CompoundTag req = new CompoundTag();
            req.putString("Name", redArtRequest.requesterName);
            req.putLong("Pos", redArtRequest.pos.asLong());
            req.putInt("Timer", redArtRequest.timer);
            tag.put("RedArtReq", req);
        }

        // Р РЋР С•РЎвЂ¦РЎР‚Р В°Р Р…Р ВµР Р…Р С‘Р Вµ Р В°Р С”РЎвЂљР С‘Р Р†Р Р…РЎвЂ№РЎвЂ¦ Р С•Р В±РЎРѓРЎвЂљРЎР‚Р ВµР В»Р С•Р Р† (РЎРѓРЎвЂљР В°Р Т‘Р С‘Р С‘ Р С‘ РЎвЂљР В°Р в„–Р СР ВµРЎР‚РЎвЂ№)
        ListTag artList = new ListTag();
        for (ActiveStrike s : activeStrikes) {
            CompoundTag sTag = new CompoundTag();
            sTag.putLong("Pos", s.pos.asLong());
            sTag.putString("Team", s.team);
            sTag.putInt("Stage", s.stage);
            sTag.putInt("Ticks", s.ticksLeft);
            artList.add(sTag);
        }
        tag.put("ActiveStrikesList", artList);

        return tag;
    }

    public static AASWorldData load(CompoundTag tag) {
        AASWorldData data = new AASWorldData();
        data.blueTickets = tag.getInt("BlueTickets");
        data.redTickets = tag.getInt("RedTickets");
        data.respawnTimer = tag.getInt("RespawnTimer");
        data.deathTicketCost = tag.getInt("DeathTicketCost");
        data.blueFaction = tag.getString("BlueFaction");
        data.redFaction = tag.getString("RedFaction");
        data.isGameStarted = tag.getBoolean("IsGameStarted");
        data.gameMode = tag.contains("GameMode") ? tag.getString("GameMode") : "AAS";
        data.invasionDefender = tag.getString("InvasionDefender");
        data.invasionPrepTicks = tag.getInt("InvasionPrepTicks");
        data.pvpEnabled = tag.contains("PvpEnabled") ? tag.getBoolean("PvpEnabled") : true;
        data.countdownTicks = tag.getInt("CountdownTicks");
        data.countdownActive = tag.getBoolean("CountdownActive");
        data.playedBlueSiren = tag.getBoolean("PlayedBlueSiren");
        data.playedRedSiren = tag.getBoolean("PlayedRedSiren");
        data.mapCenterX = tag.getInt("MapCenterX");
        data.mapCenterZ = tag.getInt("MapCenterZ");
        data.mapSizeBlocks = tag.contains("MapSizeBlocks") ? tag.getInt("MapSizeBlocks") : 2048;
        data.currentMapImage = tag.contains("CurrentMapImage") ? tag.getString("CurrentMapImage") : "map1";
        data.blueArtStrikeCD = tag.getLong("BlueArtCD");
        data.redArtStrikeCD = tag.getLong("RedArtCD");
        data.maxBlueHubs = tag.contains("MaxBlueHubs") ? tag.getInt("MaxBlueHubs") : 8;
        data.maxRedHubs = tag.contains("MaxRedHubs") ? tag.getInt("MaxRedHubs") : 8;
        data.blueCMDId = tag.getInt("BlueCMDId");
        data.redCMDId = tag.getInt("RedCMDId");

        // --- Р вЂ”Р С’Р вЂњР В Р Р€Р вЂ”Р С™Р С’ Р вЂњР С›Р вЂєР С›Р РЋР С›Р вЂ™Р С’Р СњР ВР Р‡ BLUE ---
        data.blueCmdVoteActive = tag.getBoolean("BlueCmdActive");
        data.blueCmdCandidateName = tag.getString("BlueCmdCandName");
        data.blueCmdCandidateId = tag.getInt("BlueCmdCandId");
        data.blueCmdVoteTimer = tag.getInt("BlueCmdTimer");
        if (tag.contains("BlueCmdVotesMap")) {
            CompoundTag cv = tag.getCompound("BlueCmdVotesMap");
            for (String key : cv.getAllKeys()) data.blueCmdVotes.put(UUID.fromString(key), cv.getBoolean(key));
        }

        // --- Р вЂ”Р С’Р вЂњР В Р Р€Р вЂ”Р С™Р С’ Р вЂњР С›Р вЂєР С›Р РЋР С›Р вЂ™Р С’Р СњР ВР Р‡ RED ---
        data.redCmdVoteActive = tag.getBoolean("RedCmdActive");
        data.redCmdCandidateName = tag.getString("RedCmdCandName");
        data.redCmdCandidateId = tag.getInt("RedCmdCandId");
        data.redCmdVoteTimer = tag.getInt("RedCmdTimer");
        if (tag.contains("RedCmdVotesMap")) {
            CompoundTag cv = tag.getCompound("RedCmdVotesMap");
            for (String key : cv.getAllKeys()) data.redCmdVotes.put(UUID.fromString(key), cv.getBoolean(key));
        }
        if (tag.contains("TacticalMarkers")) {
            ListTag list = tag.getList("TacticalMarkers", 10);
            for (int i = 0; i < list.size(); i++) data.activeMarkers.add(MapMarker.load(list.getCompound(i)));
        }
        if (tag.contains("TriggerBlocks")) {
            ListTag list = tag.getList("TriggerBlocks", Tag.TAG_LONG);
            for (Tag t : list) data.triggerBlocks.add(BlockPos.of(((LongTag) t).getAsLong()));
        }
        if (tag.contains("MainZones")) {
            ListTag list = tag.getList("MainZones", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) data.mainZones.add(MainProtectionZone.load(list.getCompound(i)));
        }
        if (tag.contains("LobbyCenter")) {
            CompoundTag centerTag = tag.getCompound("LobbyCenter");
            data.lobbyCenter = new BlockPos(centerTag.getInt("X"), centerTag.getInt("Y"), centerTag.getInt("Z"));
        }
        if (tag.contains("LobbyZones")) {
            ListTag list = tag.getList("LobbyZones", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) data.lobbyZones.add(LobbyZone.load(list.getCompound(i)));
        }
        if (tag.contains("MarkedVehicles")) {
            ListTag list = tag.getList("MarkedVehicles", 10);
            for (int i = 0; i < list.size(); i++) {
                data.markedVehicles.add(VehicleRecord.load(list.getCompound(i)));
            }
        }
        if (tag.contains("BlueRallies")) {
            ListTag list = tag.getList("BlueRallies", Tag.TAG_LONG);
            for (Tag t : list) data.blueRallies.add(BlockPos.of(((LongTag) t).getAsLong()));
        }
        if (tag.contains("RedRallies")) {
            ListTag list = tag.getList("RedRallies", Tag.TAG_LONG);
            for (Tag t : list) data.redRallies.add(BlockPos.of(((LongTag) t).getAsLong()));
        }

        if (tag.contains("HubsData")) {
            ListTag list = tag.getList("HubsData", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                data.hubs.add(HubInfo.load(list.getCompound(i)));
            }
        }

        if (tag.contains("BlueSpawnsMap")) {
            CompoundTag map = tag.getCompound("BlueSpawnsMap");
            for(String key : map.getAllKeys()) data.blueSpawns.put(key, BlockPos.of(map.getLong(key)));
        }
        if (tag.contains("RedSpawnsMap")) {
            CompoundTag map = tag.getCompound("RedSpawnsMap");
            for(String key : map.getAllKeys()) data.redSpawns.put(key, BlockPos.of(map.getLong(key)));
        }
        if (tag.contains("NeutralSpawnsMap")) {
            CompoundTag map = tag.getCompound("NeutralSpawnsMap");
            for(String key : map.getAllKeys()) data.neutralSpawns.put(key, BlockPos.of(map.getLong(key)));
        }
        if (tag.contains("CapturePoints")) {
            ListTag list = tag.getList("CapturePoints", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) data.capturePoints.add(CapturePoint.load(list.getCompound(i)));
        }
        if (tag.contains("Squads")) {
            ListTag list = tag.getList("Squads", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) data.squads.add(Squad.load(list.getCompound(i)));
        }

        if (tag.contains("VehicleStations")) {
            ListTag list = tag.getList("VehicleStations", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                // Р вЂќР С›Р вЂР С’Р вЂ™Р В¬ "data." Р С—Р ВµРЎР‚Р ВµР Т‘ vehicleStations
                data.vehicleStations.add(StationInfo.load(list.getCompound(i)));
            }
        }

        // === Р вЂ”Р С’Р вЂњР В Р Р€Р вЂ”Р С™Р С’ Р С™Р ВР СћР С›Р вЂ™ ===
        if (tag.contains("BlueKits")) {
            CompoundTag bk = tag.getCompound("BlueKits");
            for (String key : bk.getAllKeys()) data.blueKits.put(key, KitInfo.load(bk.getCompound(key)));
        }
        if (tag.contains("RedKits")) {
            CompoundTag rk = tag.getCompound("RedKits");
            for (String key : rk.getAllKeys()) data.redKits.put(key, KitInfo.load(rk.getCompound(key)));
        }

        if (tag.contains("BlueArtReq")) {
            CompoundTag req = tag.getCompound("BlueArtReq");
            data.blueArtRequest = new ArtStrikeRequest(req.getString("Name"), BlockPos.of(req.getLong("Pos")));
            data.blueArtRequest.timer = req.getInt("Timer");
        }
        if (tag.contains("RedArtReq")) {
            CompoundTag req = tag.getCompound("RedArtReq");
            data.redArtRequest = new ArtStrikeRequest(req.getString("Name"), BlockPos.of(req.getLong("Pos")));
            data.redArtRequest.timer = req.getInt("Timer");
        }

        if (tag.contains("ActiveStrikesList")) {
            ListTag list = tag.getList("ActiveStrikesList", 10);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag sTag = list.getCompound(i);
                ActiveStrike s = new ActiveStrike(BlockPos.of(sTag.getLong("Pos")), sTag.getString("Team"));
                s.stage = sTag.getInt("Stage");
                s.ticksLeft = sTag.getInt("Ticks");
                data.activeStrikes.add(s);
            }
        }
        if (tag.contains("VehicleStations")) {
            ListTag list = tag.getList("VehicleStations", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) data.vehicleStations.add(StationInfo.load(list.getCompound(i)));
        }
        return data;
    }

    public static class StationInfo {
        public BlockPos pos;
        public String team;
        public String dimension;

        public StationInfo(BlockPos pos, String team, String dimension) {
            this.pos = pos;
            this.team = team;
            this.dimension = dimension;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("Pos", pos.asLong());
            tag.putString("Team", team);
            tag.putString("Dim", dimension);
            return tag;
        }

        public static StationInfo load(CompoundTag tag) {
            return new StationInfo(BlockPos.of(tag.getLong("Pos")), tag.getString("Team"), tag.getString("Dim"));
        }
    }

    public static AASWorldData get(ServerLevel level) {
        String dimId = level.dimension().location().toString().replace(":", "_");
        String dataName = "aas_data_" + dimId;

        return level.getDataStorage().computeIfAbsent(AASWorldData::load, AASWorldData::new, dataName);
    }
    public static class ArtStrikeRequest {
        public String requesterName;
        public BlockPos pos;
        public int timer = 200; // 30 РЎРѓР ВµР С”РЎС“Р Р…Р Т‘ Р Р…Р В° Р С—Р С•Р Т‘РЎвЂљР Р†Р ВµРЎР‚Р В¶Р Т‘Р ВµР Р…Р С‘Р Вµ Р С”Р С•Р СР В°Р Р…Р Т‘Р С‘РЎР‚Р С•Р С
        public ArtStrikeRequest(String name, BlockPos p) { this.requesterName = name; this.pos = p; }
    }

    public ArtStrikeRequest blueArtRequest = null;
    public ArtStrikeRequest redArtRequest = null;

    // Р РЋР С—Р С‘РЎРѓР С•Р С” Р В°Р С”РЎвЂљР С‘Р Р†Р Р…РЎвЂ№РЎвЂ¦ РЎС“Р Т‘Р В°РЎР‚Р С•Р Р† (Р С”Р С•РЎвЂљР С•РЎР‚РЎвЂ№Р Вµ РЎС“Р В¶Р Вµ Р С—Р С•Р Т‘РЎвЂљР Р†Р ВµРЎР‚Р В¶Р Т‘Р ВµР Р…РЎвЂ№ Р С‘ РЎРѓРЎвЂљРЎР‚Р ВµР В»РЎРЏРЎР‹РЎвЂљ)
    public static class ActiveStrike {
        public BlockPos pos;
        public String team;
        public int stage = 0; // 0-Delay, 1-Wave1, 2-Rest, 3-Wave2, 4-Rest, 5-Wave3
        public int ticksLeft;
        public ActiveStrike(BlockPos p, String t) { this.pos = p; this.team = t; this.ticksLeft = 400; } // 20 РЎРѓР ВµР С” Р В·Р В°Р Т‘Р ВµРЎР‚Р В¶Р С”Р В°
    }
    public List<ActiveStrike> activeStrikes = new ArrayList<>();
    public static class HubInfo {
        public BlockPos pos;
        public String team;
        public boolean constructed;
        public String dimension;
        public boolean isBlocked;
        public int materials;
        public String builderName; // <--- Р СњР С›Р вЂ™Р С›Р вЂў Р СџР С›Р вЂєР вЂў: Р С‘Р СРЎРЏ РЎРѓРЎвЂљРЎР‚Р С•Р С‘РЎвЂљР ВµР В»РЎРЏ Р Т‘Р В»РЎРЏ Р С•РЎвЂЎР С”Р С•Р Р†

        // Р ВР РЋР СџР В Р С’Р вЂ™Р вЂєР вЂўР СњР С›: Р С™Р С•Р Р…РЎРѓРЎвЂљРЎР‚РЎС“Р С”РЎвЂљР С•РЎР‚ РЎвЂљР ВµР С—Р ВµРЎР‚РЎРЉ Р С—РЎР‚Р С‘Р Р…Р С‘Р СР В°Р ВµРЎвЂљ 5 Р В°РЎР‚Р С–РЎС“Р СР ВµР Р…РЎвЂљР С•Р Р† (builderName Р Т‘Р С•Р В±Р В°Р Р†Р В»Р ВµР Р… Р Р† Р С”Р С•Р Р…Р ВµРЎвЂ )
        public HubInfo(BlockPos pos, String team, boolean constructed, String dimension, String builderName) {
            this.pos = pos;
            this.team = team;
            this.constructed = constructed;
            this.dimension = dimension;
            this.isBlocked = false;
            this.materials = 0;
            this.builderName = builderName != null ? builderName : "";
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("Pos", pos.asLong());
            tag.putString("Team", team);
            tag.putBoolean("Constructed", constructed);
            tag.putString("Dimension", dimension != null ? dimension : "minecraft:overworld");
            tag.putInt("Materials", materials);
            tag.putString("Builder", builderName); // <--- Р РЋР С›Р ТђР В Р С’Р СњР Р‡Р вЂўР Сљ Р РЋР СћР В Р С›Р ВР СћР вЂўР вЂєР Р‡
            return tag;
        }

        public static HubInfo load(CompoundTag tag) {
            BlockPos p = BlockPos.of(tag.getLong("Pos"));
            String t = tag.getString("Team");
            boolean c = tag.getBoolean("Constructed");
            String d = tag.contains("Dimension") ? tag.getString("Dimension") : "minecraft:overworld";
            String b = tag.contains("Builder") ? tag.getString("Builder") : ""; // <--- Р В§Р ВР СћР С’Р вЂўР Сљ Р РЋР СћР В Р С›Р ВР СћР вЂўР вЂєР Р‡

            // Р ВР РЋР СџР В Р С’Р вЂ™Р вЂєР вЂўР СњР С›: Р СџР ВµРЎР‚Р ВµР Т‘Р В°Р ВµР С 'b' Р С—РЎРЏРЎвЂљРЎвЂ№Р С Р В°РЎР‚Р С–РЎС“Р СР ВµР Р…РЎвЂљР С•Р С, Р С•РЎв‚¬Р С‘Р В±Р С”Р В° Р С‘РЎРѓРЎвЂЎР ВµР В·Р Р…Р ВµРЎвЂљ!
            HubInfo h = new HubInfo(p, t, c, d, b);

            if (tag.contains("Materials")) h.materials = tag.getInt("Materials");
            return h;
        }
    }
    public static class SquadMarker {
        public int x, y, z; // Сохраняем реальную Y
        public int type;    // 0-Move, 1-Attack, 2-Defend, 3-Build, 6-Rhombus (свободная метка SL/FTL)
        public long expiryTick;
        public boolean isPhysical;
        public UUID id; // Уникальный ID метки — нужен, чтобы удалить конкретную метку по клику на карте

        public SquadMarker(int x, int y, int z, int type, long expiryTick, boolean isPhysical) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.type = type;
            this.expiryTick = expiryTick;
            this.isPhysical = isPhysical;
            this.id = UUID.randomUUID();
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("X", x);
            tag.putInt("Y", y); // Сохраняем Y
            tag.putInt("Z", z);
            tag.putInt("Type", type);
            tag.putLong("Expiry", expiryTick);
            tag.putBoolean("IsPhysical", isPhysical);
            tag.putUUID("Id", id);
            return tag;
        }

        public static SquadMarker load(CompoundTag tag) {
            // Исправлено: все аргументы теперь внутри скобок конструктора
            SquadMarker m = new SquadMarker(
                    tag.getInt("X"),
                    tag.getInt("Y"),
                    tag.getInt("Z"),
                    tag.getInt("Type"),
                    tag.getLong("Expiry"),
                    tag.getBoolean("IsPhysical")
            );
            // Старые сохранения могли не содержать Id — тогда оставляем сгенерированный в конструкторе
            if (tag.hasUUID("Id")) {
                m.id = tag.getUUID("Id");
            }
            return m;
        }
    }

    public static class Squad {
        public int id;
        public String name;
        public String team;
        public String leader;
        public boolean isLocked = false;
        public BlockPos rallyPos = null;
        public String dimension;
        public String rallyDimension;
        public List<String> members = new ArrayList<>();

        // --- Р В¤Р С’Р вЂўР В Р СћР ВР СљР В« ---
        public String bravoLeader = "";
        public String charlieLeader = "";
        public List<String> bravoMembers = new ArrayList<>();
        public List<String> charlieMembers = new ArrayList<>();

        public SquadMarker bravoMarker = null;
        public SquadMarker charlieMarker = null;
        public BlockPos bravoPingPos = null;
        public long bravoPingExpiry = -1;
        public BlockPos charliePingPos = null;
        public long charliePingExpiry = -1;
        // -----------------

        // Лимиты на количество свободных ("ромбовых") меток, которые можно кидать на карту.
        // При превышении лимита самая старая метка удаляется автоматически (FIFO).
        public static final int SL_MARKER_LIMIT = 10;
        public static final int FTL_MARKER_LIMIT = 5;

        public List<SquadMarker> rhombusMarkers = new ArrayList<>();       // метки, кидаемые лидером отряда (SL)
        public List<SquadMarker> bravoRhombusMarkers = new ArrayList<>();  // метки, кидаемые лидером Bravo (FTL)
        public List<SquadMarker> charlieRhombusMarkers = new ArrayList<>();// метки, кидаемые лидером Charlie (FTL)
        public SquadMarker marker = null;
        public long rallyExpiryTick = -1;
        public long slNoOfficerSince = -1;
        public long nextRallyAvailableTick = -1;
        public BlockPos pingPos = null;
        public long pingExpiry = -1;
        public boolean isRallyBlocked = false;

        public Squad(int id, String name, String team, String leader, String dimension) {
            this.id = id;
            this.name = name;
            this.team = team;
            this.leader = leader;
            this.dimension = dimension;
            this.rallyDimension = dimension;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("ID", id);
            tag.putString("Name", name);
            tag.putString("Team", team);
            tag.putString("Leader", leader);
            tag.putBoolean("IsLocked", isLocked);
            tag.putString("Dimension", dimension != null ? dimension : "minecraft:overworld");
            tag.putLong("RallyExpiry", rallyExpiryTick);
            tag.putLong("SLNoOfficerSince", slNoOfficerSince);
            tag.putLong("NextRallyAvailable", nextRallyAvailableTick);
            tag.putBoolean("RallyBlocked", isRallyBlocked);

            if (pingPos != null) {
                tag.putLong("PingPos", pingPos.asLong());
                tag.putLong("PingExpiry", pingExpiry);
            }
            if (rallyPos != null) {
                tag.putLong("RallyPos", rallyPos.asLong());
                tag.putString("RallyDim", rallyDimension != null ? rallyDimension : "minecraft:overworld");
            }

            // Р В¤Р В°Р ВµРЎР‚РЎвЂљР С‘Р СРЎвЂ№: Р РЋР С•РЎвЂ¦РЎР‚Р В°Р Р…Р ВµР Р…Р С‘Р Вµ
            tag.putString("BravoLeader", bravoLeader);
            tag.putString("CharlieLeader", charlieLeader);
            ListTag bList = new ListTag();
            for (String m : bravoMembers) bList.add(StringTag.valueOf(m));
            tag.put("BravoMembers", bList);
            ListTag cList = new ListTag();
            for (String m : charlieMembers) cList.add(StringTag.valueOf(m));
            tag.put("CharlieMembers", cList);

            if (bravoPingPos != null) {
                tag.putLong("BravoPingPos", bravoPingPos.asLong());
                tag.putLong("BravoPingExp", bravoPingExpiry);
            }
            if (charliePingPos != null) {
                tag.putLong("CharliePingPos", charliePingPos.asLong());
                tag.putLong("CharliePingExp", charliePingExpiry);
            }
            if (bravoMarker != null) tag.put("BravoMarker", bravoMarker.save());
            if (charlieMarker != null) tag.put("CharlieMarker", charlieMarker.save());

            ListTag rhombusList = new ListTag();
            for (SquadMarker rm : rhombusMarkers) rhombusList.add(rm.save());
            tag.put("RhombusList", rhombusList);

            ListTag bravoRhombusList = new ListTag();
            for (SquadMarker rm : bravoRhombusMarkers) bravoRhombusList.add(rm.save());
            tag.put("BravoRhombusList", bravoRhombusList);

            ListTag charlieRhombusList = new ListTag();
            for (SquadMarker rm : charlieRhombusMarkers) charlieRhombusList.add(rm.save());
            tag.put("CharlieRhombusList", charlieRhombusList);

            ListTag memList = new ListTag();
            for (String m : members) memList.add(StringTag.valueOf(m));
            tag.put("Members", memList);

            if (marker != null) tag.put("SquadMarker", marker.save());

            return tag;
        }

        public static Squad load(CompoundTag tag) {
            String l = tag.contains("Leader") ? tag.getString("Leader") : "";
            String dim = tag.contains("Dimension") ? tag.getString("Dimension") : "minecraft:overworld";

            Squad s = new Squad(tag.getInt("ID"), tag.getString("Name"), tag.getString("Team"), l, dim);
            s.rallyExpiryTick = tag.getLong("RallyExpiry");
            s.slNoOfficerSince = tag.getLong("SLNoOfficerSince");
            s.isRallyBlocked = tag.getBoolean("RallyBlocked");
            if (tag.contains("NextRallyAvailable")) s.nextRallyAvailableTick = tag.getLong("NextRallyAvailable");
            if (tag.contains("PingPos")) {
                s.pingPos = BlockPos.of(tag.getLong("PingPos"));
                s.pingExpiry = tag.getLong("PingExpiry");
            }
            if (tag.contains("IsLocked")) s.isLocked = tag.getBoolean("IsLocked");
            if (tag.contains("RallyPos")) {
                s.rallyPos = BlockPos.of(tag.getLong("RallyPos"));
                s.rallyDimension = tag.contains("RallyDim") ? tag.getString("RallyDim") : dim;
            }

            // Р В¤Р В°Р ВµРЎР‚РЎвЂљР С‘Р СРЎвЂ№: Р вЂ”Р В°Р С–РЎР‚РЎС“Р В·Р С”Р В°
            s.bravoLeader = tag.getString("BravoLeader");
            s.charlieLeader = tag.getString("CharlieLeader");
            if (tag.contains("BravoMembers")) {
                ListTag bList = tag.getList("BravoMembers", Tag.TAG_STRING);
                for (Tag t : bList) s.bravoMembers.add(t.getAsString());
            }
            if (tag.contains("CharlieMembers")) {
                ListTag cList = tag.getList("CharlieMembers", Tag.TAG_STRING);
                for (Tag t : cList) s.charlieMembers.add(t.getAsString());
            }
            if (tag.contains("BravoPingPos")) {
                s.bravoPingPos = BlockPos.of(tag.getLong("BravoPingPos"));
                s.bravoPingExpiry = tag.getLong("BravoPingExp");
            }
            if (tag.contains("CharliePingPos")) {
                s.charliePingPos = BlockPos.of(tag.getLong("CharliePingPos"));
                s.charliePingExpiry = tag.getLong("CharliePingExp");
            }
            if (tag.contains("BravoMarker")) s.bravoMarker = SquadMarker.load(tag.getCompound("BravoMarker"));
            if (tag.contains("CharlieMarker")) s.charlieMarker = SquadMarker.load(tag.getCompound("CharlieMarker"));

            if (tag.contains("RhombusList")) {
                ListTag list = tag.getList("RhombusList", 10);
                s.rhombusMarkers.clear();
                for (int i = 0; i < list.size(); i++) s.rhombusMarkers.add(SquadMarker.load(list.getCompound(i)));
            }
            if (tag.contains("BravoRhombusList")) {
                ListTag list = tag.getList("BravoRhombusList", 10);
                s.bravoRhombusMarkers.clear();
                for (int i = 0; i < list.size(); i++) s.bravoRhombusMarkers.add(SquadMarker.load(list.getCompound(i)));
            }
            if (tag.contains("CharlieRhombusList")) {
                ListTag list = tag.getList("CharlieRhombusList", 10);
                s.charlieRhombusMarkers.clear();
                for (int i = 0; i < list.size(); i++) s.charlieRhombusMarkers.add(SquadMarker.load(list.getCompound(i)));
            }

            if (tag.contains("Members")) {
                ListTag memList = tag.getList("Members", Tag.TAG_STRING);
                for (Tag t : memList) s.members.add(t.getAsString());
            }

            if (s.leader.isEmpty() && !s.members.isEmpty()) s.leader = s.members.get(0);
            if (!s.leader.isEmpty()) s.removeFromFireteams(s.leader);   // РќРћР’РћР•
            if (tag.contains("SquadMarker")) s.marker = SquadMarker.load(tag.getCompound("SquadMarker"));

            return s;
        }

        // Р’СЃРїРѕРјРѕРіР°С‚РµР»СЊРЅС‹Р№ РјРµС‚РѕРґ РґР»СЏ РѕС‡РёСЃС‚РєРё РёРіСЂРѕРєР° РёР· РіСЂСѓРїРї
        public void removeFromFireteams(String pName) {
            if (bravoLeader.equals(pName)) bravoLeader = "";
            if (charlieLeader.equals(pName)) charlieLeader = "";
            bravoMembers.remove(pName);
            charlieMembers.remove(pName);
        }

        // РќРћР’РћР•: РЅР°Р·РЅР°С‡РµРЅРёРµ SL РІСЃРµРіРґР° СЃРЅРёРјР°РµС‚ РёРіСЂРѕРєР° СЃ СЂРѕР»РµР№ FTL/С‡Р»РµРЅР° С„Р°Р№СЂС‚РёРјР°
        public void setLeader(String name) {
            removeFromFireteams(name);
            this.leader = name;
        }
    }
    public List<MapMarker> activeMarkers = new ArrayList<>();

    public static class MapMarker {
        public BlockPos pos;
        public String type;
        public String team;
        public long expiryTick;
        public String placedBy = ""; // НОВОЕ: имя игрока, поставившего метку

        public MapMarker(BlockPos pos, String type, String team, long expiryTick) {
            this.pos = pos;
            this.type = type;
            this.team = team;
            this.expiryTick = expiryTick;
        }

        // НОВЫЙ конструктор с placedBy
        public MapMarker(BlockPos pos, String type, String team, long expiryTick, String placedBy) {
            this.pos = pos;
            this.type = type;
            this.team = team;
            this.expiryTick = expiryTick;
            this.placedBy = placedBy;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("Pos", pos.asLong());
            tag.putString("Type", type);
            tag.putString("Team", team);
            tag.putLong("Expiry", expiryTick);
            tag.putString("PlacedBy", placedBy); // НОВОЕ
            return tag;
        }

        public static MapMarker load(CompoundTag tag) {
            MapMarker m = new MapMarker(
                    BlockPos.of(tag.getLong("Pos")),
                    tag.getString("Type"),
                    tag.getString("Team"),
                    tag.getLong("Expiry")
            );
            m.placedBy = tag.contains("PlacedBy") ? tag.getString("PlacedBy") : ""; // НОВОЕ
            return m;
        }
    }
    public static class CapturePoint {
        public String name; public AABB area; public int bluePriority; public int redPriority; public int captureTimeMinutes; public int ticketPenalty; public int captureDeduction;
        public String owner = "NEUTRAL"; public float progress = 0.0f; public String capturingTeam = "NONE";

        public String shapeType = "CUBE";
        public int lockDurationMinutes = 0;
        public long lockedUntilTick = 0;
        public int ticketGainNeutralize = 0;
        public int ticketGainCapture = 0;

        // Р СњР С›Р вЂ™Р С›Р вЂў: Р Т‘Р С•Р С—Р С•Р В»Р Р…Р С‘РЎвЂљР ВµР В»РЎРЉР Р…РЎвЂ№Р Вµ Р В·Р С•Р Р…РЎвЂ№, Р С”Р С•РЎвЂљР С•РЎР‚РЎвЂ№Р Вµ РЎРѓРЎвЂЎР С‘РЎвЂљР В°РЎР‹РЎвЂљРЎРѓРЎРЏ РЎвЂљР С•Р в„– Р В¶Р Вµ РЎРѓР В°Р СР С•Р в„– РЎвЂљР С•РЎвЂЎР С”Р С•Р в„– (РЎвЂљР С•Р В»РЎРЉР С”Р С• XYZ Р С•РЎвЂљР В»Р С‘РЎвЂЎР В°Р ВµРЎвЂљРЎРѓРЎРЏ)
        public List<AABB> linkedAreas = new ArrayList<>();

        public CapturePoint(String name, AABB area, int bp, int rp, int time, int penalty, int deduct, String shape, int lockMin, int gainNeut, int gainCap) {
            this.name = name; this.area = area; this.bluePriority = bp; this.redPriority = rp; this.captureTimeMinutes = time; this.ticketPenalty = penalty; this.captureDeduction = deduct;
            this.shapeType = shape;
            this.lockDurationMinutes = lockMin;
            this.ticketGainNeutralize = gainNeut;
            this.ticketGainCapture = gainCap;
        }

        // Р РЋРЎР‚Р В°Р Р†Р Р…Р ВµР Р…Р С‘Р Вµ РЎвЂ¦Р В°РЎР‚Р В°Р С”РЎвЂљР ВµРЎР‚Р С‘РЎРѓРЎвЂљР С‘Р С” Р вЂР вЂўР вЂ” РЎС“РЎвЂЎРЎвЂРЎвЂљР В° Р С‘Р СР ВµР Р…Р С‘ Р С‘ Р С”Р С•Р С•РЎР‚Р Т‘Р С‘Р Р…Р В°РЎвЂљ РІР‚вЂќ Р С—Р С• РЎРЊРЎвЂљР С•Р СРЎС“ Р С—РЎР‚Р С‘Р В·Р Р…Р В°Р С”РЎС“ РЎР‚Р ВµРЎв‚¬Р В°Р ВµР С, РЎРѓР В»Р С‘Р Р†Р В°РЎвЂљРЎРЉ Р В·Р С•Р Р…РЎвЂ№ Р С‘Р В»Р С‘ Р Р…Р ВµРЎвЂљ
        public boolean sameCharacteristics(int bp, int rp, int time, int penalty, int deduct, String shape, int lockMin, int gainNeut, int gainCap) {
            return this.bluePriority == bp && this.redPriority == rp && this.captureTimeMinutes == time
                    && this.ticketPenalty == penalty && this.captureDeduction == deduct
                    && this.shapeType.equalsIgnoreCase(shape) && this.lockDurationMinutes == lockMin
                    && this.ticketGainNeutralize == gainNeut && this.ticketGainCapture == gainCap;
        }

        public void addLinkedArea(AABB newArea) {
            linkedAreas.add(newArea);
        }

        // Р вЂ™РЎРѓР Вµ Р В·Р С•Р Р…РЎвЂ№ РЎРЊРЎвЂљР С•Р в„– РЎвЂљР С•РЎвЂЎР С”Р С‘ (Р С•РЎРѓР Р…Р С•Р Р†Р Р…Р В°РЎРЏ + РЎРѓР Р†РЎРЏР В·Р В°Р Р…Р Р…РЎвЂ№Р Вµ) РІР‚вЂќ Р СР С•Р В¶Р Р…Р С• Р Т‘Р С•Р В±Р В°Р Р†Р В»РЎРЏРЎвЂљРЎРЉ РЎРѓР С”Р С•Р В»РЎРЉР С”Р С• РЎС“Р С–Р С•Р Т‘Р Р…Р С•
        public List<AABB> getAllAreas() {
            List<AABB> all = new ArrayList<>(linkedAreas.size() + 1);
            all.add(area);
            all.addAll(linkedAreas);
            return all;
        }

        // Р С›Р В±РЎвЂ°Р С‘Р в„– bounding box Р Р†РЎРѓР ВµРЎвЂ¦ Р В·Р С•Р Р… РІР‚вЂќ Р Т‘Р В»РЎРЏ Р В±РЎвЂ№РЎРѓРЎвЂљРЎР‚Р С•Р С–Р С• (broad-phase) Р С—Р С•Р С‘РЎРѓР С”Р В° РЎРѓРЎС“РЎвЂ°Р Р…Р С•РЎРѓРЎвЂљР ВµР в„–
        public AABB getBoundingBox() {
            AABB box = area;
            for (AABB a : linkedAreas) box = box.minmax(a);
            return box;
        }

        public boolean intersectsAny(AABB box) {
            if (box.intersects(area)) return true;
            for (AABB a : linkedAreas) if (box.intersects(a)) return true;
            return false;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Name", name);
            tag.putDouble("minX", area.minX); tag.putDouble("minY", area.minY); tag.putDouble("minZ", area.minZ);
            tag.putDouble("maxX", area.maxX); tag.putDouble("maxY", area.maxY); tag.putDouble("maxZ", area.maxZ);
            tag.putInt("BluePriority", bluePriority); tag.putInt("RedPriority", redPriority); tag.putInt("Time", captureTimeMinutes);
            tag.putInt("Penalty", ticketPenalty); tag.putInt("Deduct", captureDeduction);
            tag.putString("Owner", owner); tag.putFloat("Progress", progress); tag.putString("CapturingTeam", capturingTeam);
            tag.putString("Shape", shapeType);
            tag.putInt("LockMin", lockDurationMinutes);
            tag.putLong("LockedUntil", lockedUntilTick);
            tag.putInt("GainNeut", ticketGainNeutralize);
            tag.putInt("GainCap", ticketGainCapture);

            // Р СњР С›Р вЂ™Р С›Р вЂў: РЎРѓР С•РЎвЂ¦РЎР‚Р В°Р Р…РЎРЏР ВµР С РЎРѓР Р†РЎРЏР В·Р В°Р Р…Р Р…РЎвЂ№Р Вµ Р В·Р С•Р Р…РЎвЂ№
            ListTag linkedList = new ListTag();
            for (AABB a : linkedAreas) {
                CompoundTag aTag = new CompoundTag();
                aTag.putDouble("minX", a.minX); aTag.putDouble("minY", a.minY); aTag.putDouble("minZ", a.minZ);
                aTag.putDouble("maxX", a.maxX); aTag.putDouble("maxY", a.maxY); aTag.putDouble("maxZ", a.maxZ);
                linkedList.add(aTag);
            }
            tag.put("LinkedAreas", linkedList);
            return tag;
        }

        public static CapturePoint load(CompoundTag tag) {
            AABB area = new AABB(tag.getDouble("minX"), tag.getDouble("minY"), tag.getDouble("minZ"), tag.getDouble("maxX"), tag.getDouble("maxY"), tag.getDouble("maxZ"));
            CapturePoint point = new CapturePoint(
                    tag.getString("Name"), area,
                    tag.getInt("BluePriority"), tag.getInt("RedPriority"),
                    tag.getInt("Time"), tag.getInt("Penalty"),
                    tag.getInt("Deduct"),
                    tag.contains("Shape") ? tag.getString("Shape") : "CUBE",
                    tag.contains("LockMin") ? tag.getInt("LockMin") : 0,
                    tag.contains("GainNeut") ? tag.getInt("GainNeut") : 0,
                    tag.contains("GainCap") ? tag.getInt("GainCap") : 0
            );
            if (tag.contains("Owner")) point.owner = tag.getString("Owner");
            if (tag.contains("Progress")) point.progress = tag.getFloat("Progress");
            if (tag.contains("CapturingTeam")) point.capturingTeam = tag.getString("CapturingTeam");
            if (tag.contains("LockedUntil")) point.lockedUntilTick = tag.getLong("LockedUntil");

            // Р СњР С›Р вЂ™Р С›Р вЂў: РЎвЂЎР С‘РЎвЂљР В°Р ВµР С РЎРѓР Р†РЎРЏР В·Р В°Р Р…Р Р…РЎвЂ№Р Вµ Р В·Р С•Р Р…РЎвЂ№
            if (tag.contains("LinkedAreas")) {
                ListTag linkedList = tag.getList("LinkedAreas", Tag.TAG_COMPOUND);
                for (int i = 0; i < linkedList.size(); i++) {
                    CompoundTag aTag = linkedList.getCompound(i);
                    point.linkedAreas.add(new AABB(
                            aTag.getDouble("minX"), aTag.getDouble("minY"), aTag.getDouble("minZ"),
                            aTag.getDouble("maxX"), aTag.getDouble("maxY"), aTag.getDouble("maxZ")
                    ));
                }
            }
            return point;
        }

        private boolean isInsideArea(net.minecraft.world.phys.Vec3 pos, AABB a) {
            if ("CYLINDER".equalsIgnoreCase(shapeType)) {
                double centerX = (a.minX + a.maxX) / 2.0;
                double centerZ = (a.minZ + a.maxZ) / 2.0;
                double radius = (a.maxX - a.minX) / 2.0;
                double dx = pos.x - centerX;
                double dz = pos.z - centerZ;
                boolean inCircle = (dx * dx + dz * dz) <= (radius * radius);
                boolean inHeight = (pos.y >= a.minY && pos.y <= a.maxY);
                return inCircle && inHeight;
            }
            return a.contains(pos);
        }

        // Р СћР ВµР С—Р ВµРЎР‚РЎРЉ Р С—РЎР‚Р С•Р Р†Р ВµРЎР‚РЎРЏР ВµРЎвЂљ Р Р†РЎвЂ¦Р С•Р В¶Р Т‘Р ВµР Р…Р С‘Р Вµ Р вЂ™Р С› Р вЂ™Р РЋР вЂў Р В·Р С•Р Р…РЎвЂ№ РЎвЂљР С•РЎвЂЎР С”Р С‘ (Р С•РЎРѓР Р…Р С•Р Р†Р Р…РЎС“РЎР‹ + РЎРѓР Р†РЎРЏР В·Р В°Р Р…Р Р…РЎвЂ№Р Вµ)
        public boolean isInside(net.minecraft.world.phys.Vec3 pos) {
            if (isInsideArea(pos, area)) return true;
            for (AABB a : linkedAreas) {
                if (isInsideArea(pos, a)) return true;
            }
            return false;
        }
    }
    public static class MainProtectionZone {
        public String team;
        public String shape;
        public AABB area;

        public MainProtectionZone(String team, String shape, AABB area) {
            this.team = team;
            this.shape = shape;
            this.area = area;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Team", team);
            tag.putString("Shape", shape);
            tag.putDouble("minX", area.minX); tag.putDouble("minY", area.minY); tag.putDouble("minZ", area.minZ);
            tag.putDouble("maxX", area.maxX); tag.putDouble("maxY", area.maxY); tag.putDouble("maxZ", area.maxZ);
            return tag;
        }

        public static MainProtectionZone load(CompoundTag tag) {
            AABB aabb = new AABB(tag.getDouble("minX"), tag.getDouble("minY"), tag.getDouble("minZ"),
                    tag.getDouble("maxX"), tag.getDouble("maxY"), tag.getDouble("maxZ"));
            return new MainProtectionZone(tag.getString("Team"), tag.getString("Shape"), aabb);
        }

        public boolean isInside(net.minecraft.world.phys.Vec3 pos) {
            if ("CYLINDER".equalsIgnoreCase(shape)) {
                double centerX = (area.minX + area.maxX) / 2.0;
                double centerZ = (area.minZ + area.maxZ) / 2.0;
                double radius = (area.maxX - area.minX) / 2.0;
                double dx = pos.x - centerX;
                double dz = pos.z - centerZ;
                return (dx * dx + dz * dz) <= (radius * radius) && pos.y >= area.minY && pos.y <= area.maxY;
            }
            return area.contains(pos);
        }
    }
    public static class LobbyZone {
        public String shape;
        public AABB area;

        public LobbyZone(String shape, AABB area) {
            this.shape = shape;
            this.area = area;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Shape", shape);
            tag.putDouble("minX", area.minX); tag.putDouble("minY", area.minY); tag.putDouble("minZ", area.minZ);
            tag.putDouble("maxX", area.maxX); tag.putDouble("maxY", area.maxY); tag.putDouble("maxZ", area.maxZ);
            return tag;
        }

        public static LobbyZone load(CompoundTag tag) {
            AABB aabb = new AABB(tag.getDouble("minX"), tag.getDouble("minY"), tag.getDouble("minZ"),
                    tag.getDouble("maxX"), tag.getDouble("maxY"), tag.getDouble("maxZ"));
            return new LobbyZone(tag.getString("Shape"), aabb);
        }

        public boolean isInside(net.minecraft.world.phys.Vec3 pos) {
            if ("CYLINDER".equalsIgnoreCase(shape)) {
                double centerX = (area.minX + area.maxX) / 2.0;
                double centerZ = (area.minZ + area.maxZ) / 2.0;
                double radius = (area.maxX - area.minX) / 2.0;
                double dx = pos.x - centerX;
                double dz = pos.z - centerZ;
                return (dx * dx + dz * dz) <= (radius * radius) && pos.y >= area.minY && pos.y <= area.maxY;
            }
            return area.contains(pos);
        }
    }

    public BlockPos lobbyCenter = null;
    public List<LobbyZone> lobbyZones = new ArrayList<>();
    public List<MainProtectionZone> mainZones = new ArrayList<>();
    // === Р СњР С›Р вЂ™Р В«Р в„ў Р вЂ™Р СњР Р€Р СћР В Р вЂўР СњР СњР ВР в„ў Р С™Р вЂєР С’Р РЋР РЋ Р вЂќР вЂєР Р‡ Р С™Р ВР СћР С›Р вЂ™ ===
    public static class KitInfo {
        public String name;
        public net.minecraft.core.NonNullList<net.minecraft.world.item.ItemStack> inventory = net.minecraft.core.NonNullList.withSize(49, net.minecraft.world.item.ItemStack.EMPTY);
        public boolean[] resupplyFlags = new boolean[49];
        public boolean[] saveNbtFlags = new boolean[49];
        public boolean isLeaderOnly = false;
        public int maxPerTeam = -1; // -1 = Р В±Р ВµРЎРѓР С”Р С•Р Р…Р ВµРЎвЂЎР Р…Р С•, 0 = Р Р†РЎвЂ№Р С”Р В»РЎР‹РЎвЂЎР ВµР Р…
        public int maxPerSquad = -1;
        public int minSquadPlayers = 0;

        // === РђР›Р¬РўР•Р РќРђРўРР’РќР«Р™ РљРРў (Р’РўРћР РђРЇ Р’Р•Р РЎРРЇ Р—РђР“Р РЈР—РљР) ===
        // Р•СЃР»Рё hasAlt == true Рё altKit != null вЂ” Сѓ РёРіСЂРѕРєРѕРІ РїРѕСЏРІР»СЏРµС‚СЃСЏ РІС‹Р±РѕСЂ STANDARD/ALTERNATIVE
        // РїСЂРё РІС‹Р±РѕСЂРµ РєР»Р°СЃСЃР°. РћРіСЂР°РЅРёС‡РµРЅРёСЏ (maxPerTeam/maxPerSquad/minSquadPlayers/isLeaderOnly)
        // РІСЃРµРіРґР° Р±РµСЂСѓС‚СЃСЏ РёР· РЎРўРђРќР”РђР РўРќРћР“Рћ (Р±Р°Р·РѕРІРѕРіРѕ) KitInfo вЂ” РѕРЅРё РѕР±С‰РёРµ РґР»СЏ РѕР±РµРёС… РІРµСЂСЃРёР№ РєР»Р°СЃСЃР°,
        // С‚Р°Рє РєР°Рє Р°Р»СЊС‚РµСЂРЅР°С‚РёРІР° СЌС‚Рѕ РїСЂРѕСЃС‚Рѕ РґСЂСѓРіРѕР№ РЅР°Р±РѕСЂ СЃРЅР°СЂСЏР¶РµРЅРёСЏ РІРЅСѓС‚СЂРё С‚РѕРіРѕ Р¶Рµ РєР»Р°СЃСЃР°.
        public boolean hasAlt = false;
        public KitInfo altKit = null;

        public String displayName = ""; // РїСѓСЃС‚Рѕ = РїРѕРєР°Р·С‹РІР°РµРј СЃС‚Р°РЅРґР°СЂС‚РЅРѕРµ kitName

        public KitInfo(String name) { this.name = name; }

        /** Р’РѕР·РІСЂР°С‰Р°РµС‚ (СЃРѕР·РґР°РІР°СЏ РїСЂРё РЅРµРѕР±С…РѕРґРёРјРѕСЃС‚Рё) Р°Р»СЊС‚РµСЂРЅР°С‚РёРІРЅС‹Р№ РєРёС‚. РўРѕР»СЊРєРѕ РґР»СЏ Р±Р°Р·РѕРІРѕРіРѕ KitInfo. */
        public KitInfo getOrCreateAlt() {
            if (altKit == null) {
                altKit = new KitInfo(this.name);
            }
            return altKit;
        }

        public CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putString("Name", name);
            t.putString("DisplayName", displayName);
            t.putBoolean("LeaderOnly", isLeaderOnly);
            t.putInt("MaxTeam", maxPerTeam);
            t.putInt("MaxSquad", maxPerSquad);
            t.putInt("MinSquadPlayers", minSquadPlayers);
            ListTag items = new ListTag();
            for (int i = 0; i < 49; i++) {
                if (!inventory.get(i).isEmpty()) {
                    CompoundTag itemTag = new CompoundTag();
                    itemTag.putByte("Slot", (byte) i);
                    itemTag.putBoolean("Resupply", resupplyFlags[i]);
                    itemTag.putBoolean("SaveNbt", saveNbtFlags[i]);
                    inventory.get(i).save(itemTag); // Р РЋР С•РЎвЂ¦РЎР‚Р В°Р Р…РЎРЏР ВµРЎвЂљ Р С—РЎР‚Р ВµР Т‘Р СР ВµРЎвЂљ Р Р†Р СР ВµРЎРѓРЎвЂљР Вµ РЎРѓ NBT
                    items.add(itemTag);
                }
            }
            t.put("Items", items);

            // === РЎРћРҐР РђРќРЇР•Рњ РђР›Р¬РўР•Р РќРђРўРР’РќРЈР® Р’Р•Р РЎРР® (РµСЃР»Рё РµСЃС‚СЊ) ===
            t.putBoolean("HasAlt", hasAlt);
            if (altKit != null) {
                t.put("AltKit", altKit.save());
            }
            return t;
        }

        public static KitInfo load(CompoundTag t) {
            KitInfo k = new KitInfo(t.getString("Name"));
            k.displayName = t.getString("DisplayName");
            k.isLeaderOnly = t.getBoolean("LeaderOnly");
            k.maxPerTeam = t.getInt("MaxTeam");
            k.maxPerSquad = t.getInt("MaxSquad");
            if (t.contains("MinSquadPlayers")) {
                k.minSquadPlayers = t.getInt("MinSquadPlayers");
            }
            ListTag items = t.getList("Items", 10);
            for (int i = 0; i < items.size(); i++) {
                CompoundTag itemTag = items.getCompound(i);
                int slot = itemTag.getByte("Slot") & 255;
                if (slot >= 0 && slot < 49) {
                    k.inventory.set(slot, net.minecraft.world.item.ItemStack.of(itemTag));
                    k.resupplyFlags[slot] = itemTag.getBoolean("Resupply");
                    k.saveNbtFlags[slot] = itemTag.getBoolean("SaveNbt");
                }
            }

            // === Р—РђР“Р РЈР–РђР•Рњ РђР›Р¬РўР•Р РќРђРўРР’РќРЈР® Р’Р•Р РЎРР® (РµСЃР»Рё РµСЃС‚СЊ) ===
            k.hasAlt = t.getBoolean("HasAlt");
            if (t.contains("AltKit")) {
                k.altKit = KitInfo.load(t.getCompound("AltKit"));
            }
            return k;
        }
    }

    /**
     * Р’РѕР·РІСЂР°С‰Р°РµС‚ РЅСѓР¶РЅС‹Р№ РІР°СЂРёР°РЅС‚ РєРёС‚Р° (СЃС‚Р°РЅРґР°СЂС‚РЅС‹Р№ РёР»Рё Р°Р»СЊС‚РµСЂРЅР°С‚РёРІРЅС‹Р№) РїРѕ РёРјРµРЅРё РєР»Р°СЃСЃР°.
     * Р•СЃР»Рё Р·Р°РїСЂРѕС€РµРЅ Р°Р»СЊС‚РµСЂРЅР°С‚РёРІРЅС‹Р№, РЅРѕ РµРіРѕ РЅРµС‚ (hasAlt=false РёР»Рё altKit=null) вЂ” РІРѕР·РІСЂР°С‰Р°РµС‚СЃСЏ СЃС‚Р°РЅРґР°СЂС‚РЅС‹Р№.
     */
    public KitInfo getKitVariant(String team, String kitName, boolean isAlt) {
        Map<String, KitInfo> source = "BLUE".equalsIgnoreCase(team) ? blueKits : redKits;
        KitInfo base = source.get(kitName);
        if (base == null) return null;
        if (isAlt && base.hasAlt && base.altKit != null) return base.altKit;
        return base;
    }

    public boolean voteActive = false;
    public int voteTimer = 0;
    public boolean blueReady = false;
    public boolean redReady = false;
    public Map<UUID, Boolean> votes = new HashMap<>();
    // Р вЂ™Р Р…РЎС“РЎвЂљРЎР‚Р С‘ AASWorldData.java
    public static class VehicleRecord {
        public UUID uuid;
        public String team;
        public String type;
        public double x, y, z;
        public float yaw;
        public BlockPos spawnerPos;

        // === РђР’РўРћ-Р’РћР—Р’Р РђРў ===
        public boolean autoReturnEnabled = false;
        public int autoReturnTimeSeconds = 60;
        public boolean autoReturnDestroy = false;
        public long emptySinceTick = -1; // -1 = РІ С‚РµС…РЅРёРєРµ РµСЃС‚СЊ РёРіСЂРѕРє / С‚Р°Р№РјРµСЂ РЅРµ Р·Р°РїСѓС‰РµРЅ

        public VehicleRecord(UUID uuid, String team, String type, double x, double y, double z, float yaw, BlockPos spawnerPos) {
            this(uuid, team, type, x, y, z, yaw, spawnerPos, false, 60, false);
        }

        public VehicleRecord(UUID uuid, String team, String type, double x, double y, double z, float yaw, BlockPos spawnerPos,
                             boolean autoReturnEnabled, int autoReturnTimeSeconds, boolean autoReturnDestroy) {
            this.uuid = uuid;
            this.team = team;
            this.type = type;
            this.x = x; this.y = y; this.z = z;
            this.yaw = yaw;
            this.spawnerPos = spawnerPos;
            this.autoReturnEnabled = autoReturnEnabled;
            this.autoReturnTimeSeconds = autoReturnTimeSeconds;
            this.autoReturnDestroy = autoReturnDestroy;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("UUID", uuid);
            tag.putString("Team", team);
            tag.putString("Type", type);
            tag.putDouble("X", x); tag.putDouble("Y", y); tag.putDouble("Z", z);
            tag.putFloat("Yaw", yaw);
            if (spawnerPos != null) tag.putLong("SpawnerPos", spawnerPos.asLong());
            tag.putBoolean("AutoReturnEnabled", autoReturnEnabled);
            tag.putInt("AutoReturnTime", autoReturnTimeSeconds);
            tag.putBoolean("AutoReturnDestroy", autoReturnDestroy);
            return tag;
        }

        public static VehicleRecord load(CompoundTag tag) {
            BlockPos sPos = tag.contains("SpawnerPos") ? BlockPos.of(tag.getLong("SpawnerPos")) : null;
            VehicleRecord v = new VehicleRecord(tag.getUUID("UUID"), tag.getString("Team"), tag.getString("Type"),
                    tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z"), tag.getFloat("Yaw"), sPos,
                    tag.getBoolean("AutoReturnEnabled"),
                    tag.contains("AutoReturnTime") ? tag.getInt("AutoReturnTime") : 60,
                    tag.getBoolean("AutoReturnDestroy"));
            return v;
        }
    }
}