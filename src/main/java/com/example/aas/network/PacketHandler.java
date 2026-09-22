package com.example.aas.network;

import com.example.aas.config.AASConfig;
import com.example.aas.world.AASWorldData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PacketHandler {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("aas:main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static void register() {
        int id = 0;
        INSTANCE.registerMessage(id++, PacketSyncGameData.class, PacketSyncGameData::encode, PacketSyncGameData::decode, PacketSyncGameData::handle);
        INSTANCE.registerMessage(id++, PacketSyncPoint.class, PacketSyncPoint::encode, PacketSyncPoint::decode, PacketSyncPoint::handle);
        INSTANCE.registerMessage(id++, PacketDebugFill.class, PacketDebugFill::encode, PacketDebugFill::decode, PacketDebugFill::handle);
        INSTANCE.registerMessage(id++, PacketApplyMarker.class, PacketApplyMarker::encode, PacketApplyMarker::decode, PacketApplyMarker::handle);
        INSTANCE.registerMessage(id++, PacketRespawnRequest.class, PacketRespawnRequest::encode, PacketRespawnRequest::decode, PacketRespawnRequest::handle);
        INSTANCE.registerMessage(id++, PacketDebugSpawnRally.class, PacketDebugSpawnRally::encode, PacketDebugSpawnRally::decode, PacketDebugSpawnRally::handle);
        INSTANCE.registerMessage(id++, PacketSpawnGhost.class, PacketSpawnGhost::encode, PacketSpawnGhost::decode, PacketSpawnGhost::handle);
        INSTANCE.registerMessage(id++, PacketBuildRequest.class, PacketBuildRequest::encode, PacketBuildRequest::decode, PacketBuildRequest::handle);
        INSTANCE.registerMessage(id++, PacketToggleAim.class, PacketToggleAim::encode, PacketToggleAim::decode, PacketToggleAim::handle);
        INSTANCE.registerMessage(id++, PacketVehicleShoot.class, PacketVehicleShoot::encode, PacketVehicleShoot::decode, PacketVehicleShoot::handle);
        INSTANCE.registerMessage(id++, PacketRequestAmmo.class, PacketRequestAmmo::encode, PacketRequestAmmo::decode, PacketRequestAmmo::handle);
        INSTANCE.registerMessage(id++, PacketDropCrate.class, PacketDropCrate::encode, PacketDropCrate::decode, PacketDropCrate::handle);
        INSTANCE.registerMessage(id++, PacketUpdateSpawner.class, PacketUpdateSpawner::encode, PacketUpdateSpawner::decode, PacketUpdateSpawner::handle);
        INSTANCE.registerMessage(id++, PacketRecoil.class, PacketRecoil::encode, PacketRecoil::decode, PacketRecoil::handle);
        INSTANCE.registerMessage(id++, PacketSyncSquads.class, PacketSyncSquads::encode, PacketSyncSquads::new, PacketSyncSquads::handle);
        INSTANCE.registerMessage(id++, PacketSquadAction.class, PacketSquadAction::encode, PacketSquadAction::decode, PacketSquadAction::handle);
        INSTANCE.registerMessage(id++, PacketTeamSelect.class, PacketTeamSelect::encode, PacketTeamSelect::decode, PacketTeamSelect::handle);
        INSTANCE.registerMessage(id++, PacketSquadChat.class, PacketSquadChat::encode, PacketSquadChat::decode, PacketSquadChat::handle);
        INSTANCE.registerMessage(id++, PacketSyncMapPlayers.class, PacketSyncMapPlayers::encode, PacketSyncMapPlayers::decode, PacketSyncMapPlayers::handle);
        INSTANCE.registerMessage(id++, PacketOpenKitEditor.class, PacketOpenKitEditor::encode, PacketOpenKitEditor::decode, PacketOpenKitEditor::handle);
        INSTANCE.registerMessage(id++, PacketSaveKit.class, PacketSaveKit::encode, PacketSaveKit::decode, PacketSaveKit::handle);
        INSTANCE.registerMessage(id++, PacketRequestKitMenu.class, PacketRequestKitMenu::encode, PacketRequestKitMenu::decode, PacketRequestKitMenu::handle);
        INSTANCE.registerMessage(id++, PacketOpenPlayerKitMenu.class, PacketOpenPlayerKitMenu::encode, PacketOpenPlayerKitMenu::decode, PacketOpenPlayerKitMenu::handle);
        INSTANCE.registerMessage(id++, PacketSelectKit.class, PacketSelectKit::encode, PacketSelectKit::decode, PacketSelectKit::handle);
        INSTANCE.registerMessage(id++, PacketRequestCrateAmmo.class, PacketRequestCrateAmmo::encode, PacketRequestCrateAmmo::decode, PacketRequestCrateAmmo::handle);
        INSTANCE.registerMessage(id++, PacketRequestKitData.class, PacketRequestKitData::encode, PacketRequestKitData::decode, PacketRequestKitData::handle);
        INSTANCE.registerMessage(id++, PacketSendKitData.class, PacketSendKitData::encode, PacketSendKitData::decode, PacketSendKitData::handle);
        INSTANCE.registerMessage(id++, PacketPasteKit.class, PacketPasteKit::encode, PacketPasteKit::decode, PacketPasteKit::handle);
        INSTANCE.registerMessage(id++, PacketPasteTeam.class, PacketPasteTeam::encode, PacketPasteTeam::decode, PacketPasteTeam::handle);
        INSTANCE.registerMessage(id++, PacketDownedAction.class, PacketDownedAction::encode, PacketDownedAction::decode, PacketDownedAction::handle);
        INSTANCE.registerMessage(id++, PacketSyncDownedState.class, PacketSyncDownedState::encode, PacketSyncDownedState::decode, PacketSyncDownedState::handle);
        INSTANCE.registerMessage(id++, PacketSquadMarker.class, PacketSquadMarker::encode, PacketSquadMarker::decode, PacketSquadMarker::handle);
        INSTANCE.registerMessage(id++, PacketPlaceMapMarker.class, PacketPlaceMapMarker::encode, PacketPlaceMapMarker::decode, PacketPlaceMapMarker::handle);
        INSTANCE.registerMessage(id++, PacketRadioAction.class, PacketRadioAction::encode, PacketRadioAction::decode, PacketRadioAction::handle);
        INSTANCE.registerMessage(id++, PacketVoteAction.class, PacketVoteAction::encode, PacketVoteAction::decode, PacketVoteAction::handle);
        INSTANCE.registerMessage(id++, PacketRequestVehicleAmmo.class, PacketRequestVehicleAmmo::encode, PacketRequestVehicleAmmo::decode, PacketRequestVehicleAmmo::handle);
        INSTANCE.registerMessage(id++, PacketCaptureNotification.class, PacketCaptureNotification::encode, PacketCaptureNotification::decode, PacketCaptureNotification::handle);
        INSTANCE.registerMessage(id++, PacketPlacePing.class, PacketPlacePing::encode, PacketPlacePing::decode, PacketPlacePing::handle);
        INSTANCE.registerMessage(id++, PacketRequestCMD.class, PacketRequestCMD::encode, PacketRequestCMD::decode, PacketRequestCMD::handle);
        INSTANCE.registerMessage(id++, PacketCMDVote.class, PacketCMDVote::encode, PacketCMDVote::decode, PacketCMDVote::handle);
        INSTANCE.registerMessage(id++, PacketConfirmArtStrike.class, PacketConfirmArtStrike::encode, PacketConfirmArtStrike::decode, PacketConfirmArtStrike::handle);
        INSTANCE.registerMessage(id++, PacketSyncMyKit.class, PacketSyncMyKit::encode, PacketSyncMyKit::decode, PacketSyncMyKit::handle);
        INSTANCE.registerMessage(id++, PacketVoiceActivity.class, PacketVoiceActivity::encode, PacketVoiceActivity::decode, PacketVoiceActivity::handle);
        INSTANCE.registerMessage(id++, PacketRadioVoiceActivity.class, PacketRadioVoiceActivity::encode, PacketRadioVoiceActivity::decode, PacketRadioVoiceActivity::handle); // РќРћР’РћР•
        INSTANCE.registerMessage(id++, PacketOpenVictoryScreen.class, PacketOpenVictoryScreen::encode, PacketOpenVictoryScreen::decode, PacketOpenVictoryScreen::handle);
        INSTANCE.registerMessage(id++, PacketSyncServerConfig.class, PacketSyncServerConfig::encode, PacketSyncServerConfig::decode, PacketSyncServerConfig::handle);
        INSTANCE.registerMessage(id++, PacketOpenPointEditor.class, PacketOpenPointEditor::encode, PacketOpenPointEditor::decode, PacketOpenPointEditor::handle);
        INSTANCE.registerMessage(id++, PacketSavePoint.class, PacketSavePoint::encode, PacketSavePoint::decode, PacketSavePoint::handle);
        INSTANCE.registerMessage(id++, PacketSyncDragState.class, PacketSyncDragState::encode, PacketSyncDragState::decode, PacketSyncDragState::handle);
        INSTANCE.registerMessage(id++, PacketSyncPlayerStats.class, PacketSyncPlayerStats::encode, PacketSyncPlayerStats::decode, PacketSyncPlayerStats::handle);
        INSTANCE.registerMessage(id++, PacketReviveHold.class, PacketReviveHold::encode, PacketReviveHold::decode, PacketReviveHold::handle);
        INSTANCE.registerMessage(id++, PacketReviveProgress.class, PacketReviveProgress::encode, PacketReviveProgress::decode, PacketReviveProgress::handle);
        INSTANCE.registerMessage(id++, PacketSquadLeaderPlaytime.class, PacketSquadLeaderPlaytime::encode, PacketSquadLeaderPlaytime::decode, PacketSquadLeaderPlaytime::handle);
        INSTANCE.registerMessage(id++, PacketDeleteMarker.class, PacketDeleteMarker::encode, PacketDeleteMarker::decode, PacketDeleteMarker::handle);
    }

    private static String getFactionName(String currentFaction, boolean isBlue) {
        if (currentFaction == null || currentFaction.equals("none") || currentFaction.equals("bluefor") || currentFaction.equals("redfor")) {
            return isBlue ? AASConfig.BLUE_TEAM_CUSTOM_NAME.get() : AASConfig.RED_TEAM_CUSTOM_NAME.get();
        }
        return currentFaction.toUpperCase();
    }

    private static Map<String, String> getPlayerKitsMap() {
        Map<String, String> pKits = new HashMap<>();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                String current = p.getPersistentData().getString("AAS_CurrentKit");
                String pending = p.getPersistentData().getString("AAS_PendingKit");
                String displayKit = (!pending.isEmpty()) ? pending : current;
                pKits.put(p.getScoreboardName(), (displayKit == null || displayKit.isEmpty() || displayKit.equals("Unassigned")) ? "Unassigned" : displayKit);
            }
        }
        return pKits;
    }

    public static void sendToAllClients(int blue, int red, boolean hasBlue, boolean hasRed,
                                        boolean blueBleed, boolean redBleed, int respawnTime,
                                        boolean blueBlocked, boolean redBlocked,
                                        List<AASWorldData.HubInfo> hubs,
                                        String bFac, String rFac) {

        String bName = getFactionName(bFac, true);
        String rName = getFactionName(rFac, false);

        // РџР•Р Р•Р”Р•Р›Р«Р’РђР•Рњ РќРђ BUILDER
        PacketSyncGameData packet = PacketSyncGameData.builder()
                .tickets(blue, red)
                .rally(hasBlue, hasRed)
                .bleeding(blueBleed, redBleed)
                .respawnTime(respawnTime)
                .blocked(blueBlocked, redBlocked)
                .hubs(new ArrayList<>(hubs))
                .factions(bFac, rFac)
                .customNames(bName, rName)
                .playerKits(getPlayerKitsMap())
                .gameMode("AAS")
                .invasion("NONE", 0)
                .build();

        INSTANCE.send(PacketDistributor.ALL.noArg(), packet);
    }

    public static void sendToAllClients(AASWorldData data, boolean blueBleed, boolean redBleed, boolean bBlocked, boolean rBlocked) {
        INSTANCE.send(PacketDistributor.ALL.noArg(), createSyncPacket(data, blueBleed, redBleed, bBlocked, rBlocked));
    }

    public static void sendToAllClients(ServerLevel level, AASWorldData data) {
        INSTANCE.send(PacketDistributor.DIMENSION.with(level::dimension), createSyncPacket(data, false, false, false, false));
    }

    private static PacketSyncGameData createSyncPacket(AASWorldData data, boolean blueBleed, boolean redBleed, boolean bBlocked, boolean rBlocked) {
        boolean hasBlue = !data.blueRallies.isEmpty();
        boolean hasRed = !data.redRallies.isEmpty();

        String bName = getFactionName(data.blueFaction, true);
        String rName = getFactionName(data.redFaction, false);

        // РРЎРџРћР›Р¬Р—РЈР•Рњ BUILDER, РўРђРљ РљРђРљ РћР‘Р«Р§РќР«Р™ РљРћРќРЎРўР РЈРљРўРћР  РўР•РџР•Р Р¬ РџР РР’РђРўРќР«Р™
        return PacketSyncGameData.builder()
                .tickets(data.blueTickets, data.redTickets)
                .rally(hasBlue, hasRed)
                .bleeding(blueBleed, redBleed)
                .respawnTime(data.respawnTimer)
                .blocked(bBlocked, rBlocked)
                .hubSpawnCost(AASConfig.HUB_SPAWN_COSTS_MATERIALS.get(), AASConfig.HUB_SPAWN_MATERIAL_COST.get())
                .map(data.mapCenterX, data.mapCenterZ, data.mapSizeBlocks, data.currentMapImage)
                .vehicles(new ArrayList<>(data.markedVehicles))
                .hubs(new ArrayList<>(data.hubs))
                .stations(new ArrayList<>(data.vehicleStations))
                .factions(data.blueFaction, data.redFaction)
                .customNames(bName, rName)
                .gameStarted(data.isGameStarted)
                .capturePoints(new ArrayList<>(data.capturePoints))
                .spawns(new HashMap<>(data.blueSpawns), new HashMap<>(data.redSpawns), new HashMap<>(data.neutralSpawns))
                .playerKits(getPlayerKitsMap())
                .markers(new ArrayList<>(data.activeMarkers))
                .startVote(data.voteActive, data.voteTimer, new HashMap<>(data.votes))
                .commanderIds(data.blueCMDId, data.redCMDId)
                .blueCommanderVote(data.blueCmdVoteActive, data.blueCmdCandidateName, data.blueCmdCandidateId, data.blueCmdVoteTimer, new HashMap<>(data.blueCmdVotes))
                .redCommanderVote(data.redCmdVoteActive, data.redCmdCandidateName, data.redCmdCandidateId, data.redCmdVoteTimer, new HashMap<>(data.redCmdVotes))
                .activeStrikes(new ArrayList<>(data.activeStrikes))
                .artillery(
                        data.blueArtRequest != null ? data.blueArtRequest.pos : BlockPos.ZERO,
                        data.redArtRequest != null ? data.redArtRequest.pos : BlockPos.ZERO,
                        data.blueArtRequest != null ? data.blueArtRequest.timer : 0,
                        data.redArtRequest != null ? data.redArtRequest.timer : 0,
                        data.blueArtRequest != null ? data.blueArtRequest.requesterName : "",
                        data.redArtRequest != null ? data.redArtRequest.requesterName : ""
                )
                .ready(data.blueReady, data.redReady)
                .gameMode(data.gameMode)
                .invasion(data.invasionDefender, data.invasionPrepTicks)
                .build();
    }
}