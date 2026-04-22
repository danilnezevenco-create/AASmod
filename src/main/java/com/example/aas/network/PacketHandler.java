package com.example.aas.network;

import com.example.aas.config.AASConfig;
import com.example.aas.world.AASWorldData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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
                String displayKit = !pending.isEmpty() ? pending : current;
                pKits.put(p.getScoreboardName(), displayKit.isEmpty() ? "Unassigned" : displayKit);
            }
        }
        return pKits;
    }

    // Исправленный старый метод (добавлены 0 для координат карты)
    public static void sendToAllClients(int blue, int red, boolean hasBlue, boolean hasRed,
                                        boolean blueBleed, boolean redBleed, int respawnTime,
                                        boolean blueBlocked, boolean redBlocked,
                                        List<AASWorldData.HubInfo> hubs,
                                        String bFac, String rFac) {

        String bName = getFactionName(bFac, true);
        String rName = getFactionName(rFac, false);

        INSTANCE.send(PacketDistributor.ALL.noArg(),
                new PacketSyncGameData(blue, red, hasBlue, hasRed, blueBleed, redBleed,
                        respawnTime, blueBlocked, redBlocked, hubs,
                        bFac, rFac, bName, rName, false,
                        0, 0, 2048, // ТРИ параметра карты вместо ЧЕТЫРЕХ (ЦентрX, ЦентрZ, Размер)
                        new ArrayList<>(), new HashMap<>(), new HashMap<>(), new HashMap<>(),
                        getPlayerKitsMap(),
                        new ArrayList<>(), // 24-й: Список техники
                        new ArrayList<>(), // 25-й аргумент: Пустой список техники
                        com.example.aas.config.AASConfig.HUB_SPAWN_COSTS_MATERIALS.get(),
                        com.example.aas.config.AASConfig.HUB_SPAWN_MATERIAL_COST.get()
                ));
    }

    // Исправленная глобальная отправка
    public static void sendToAllClients(AASWorldData data, boolean blueBleed, boolean redBleed, boolean bBlocked, boolean rBlocked) {
        INSTANCE.send(PacketDistributor.ALL.noArg(), createSyncPacket(data, blueBleed, redBleed, bBlocked, rBlocked));
    }

    // Исправленная отправка для уровня
    public static void sendToAllClients(ServerLevel level, AASWorldData data) {
        INSTANCE.send(PacketDistributor.DIMENSION.with(level::dimension), createSyncPacket(data, false, false, false, false));
    }

    // Единый метод создания пакета (уже был правильным)
    private static PacketSyncGameData createSyncPacket(AASWorldData data, boolean blueBleed, boolean redBleed, boolean bBlocked, boolean rBlocked) {
        boolean hasBlue = !data.blueRallies.isEmpty();
        boolean hasRed = !data.redRallies.isEmpty();

        String bName = getFactionName(data.blueFaction, true);
        String rName = getFactionName(data.redFaction, false);

        return new PacketSyncGameData(
                data.blueTickets, data.redTickets, hasBlue, hasRed, blueBleed, redBleed,
                data.respawnTimer, bBlocked, rBlocked, data.hubs,
                data.blueFaction, data.redFaction, bName, rName,
                data.isGameStarted,
                // --- ВОТ ЭТИ ТРИ СТРОКИ ВМЕСТО ЧЕТЫРЕХ СТАРЫХ ---
                data.mapCenterX,
                data.mapCenterZ,
                data.mapSizeBlocks,
                // -----------------------------------------------
                data.capturePoints,
                data.blueSpawns, data.redSpawns, data.neutralSpawns,
                getPlayerKitsMap(),
                data.markedVehicles, // Список техники (последний аргумент)
                data.activeMarkers,
                AASConfig.HUB_SPAWN_COSTS_MATERIALS.get(), // Передаем настройку СЕРВЕРА
                AASConfig.HUB_SPAWN_MATERIAL_COST.get()
        );
    }
}