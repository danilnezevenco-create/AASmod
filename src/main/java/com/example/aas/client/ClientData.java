package com.example.aas.client;

import com.example.aas.network.MapPlayerInfo;
import com.example.aas.world.AASWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import java.util.*;
import com.example.aas.network.MapPlayerInfo;

public class ClientData {
    // ... существующие поля ...
    public static int BLUE_TICKETS = 800;
    public static int RED_TICKETS = 800;
    public static List<AASWorldData.Squad> clientSquads = new ArrayList<>();
    public static List<AASWorldData.HubInfo> clientHubs = new ArrayList<>();
    public static String BLUE_FACTION = "none";
    public static String RED_FACTION = "none";
    public static final Set<Integer> DOWNED_PLAYERS = new HashSet<>();
    public static List<AASWorldData.MapMarker> activeMarkers = new ArrayList<>();
    public static boolean serverHubSpawnCosts = false;
    public static int serverHubSpawnCostAmount = 0;
    public static String customBlueName = "BLUEFOR";
    public static String customRedName = "REDFOR";
    public static final Map<UUID, Long> SQUAD_SPEAKERS = new HashMap<>();
    public static final Map<UUID, Long> COMMAND_SPEAKERS = new HashMap<>();
    public static void updateSpeaker(UUID id, int type) {
        if (type == 1) SQUAD_SPEAKERS.put(id, System.currentTimeMillis());
        else if (type == 2) COMMAND_SPEAKERS.put(id, System.currentTimeMillis());
    }
    public static int mapMinX = -1000;
    public static int mapMinZ = -1000;
    public static int mapMaxX = 1000;
    public static int mapMaxZ = 1000;
    public static int mapCenterX = 0;
    public static int mapCenterZ = 0;
    public static int mapSizeBlocks = 2048;
    public static Map<String, BlockPos> blueSpawns = new HashMap<>();
    public static Map<String, BlockPos> redSpawns = new HashMap<>();
    public static Map<String, BlockPos> neutralSpawns = new HashMap<>();
    public static List<AASWorldData.VehicleRecord> clientVehicles = new ArrayList<>();
    public static Map<String, String> playerKits = new HashMap<>();
    public static boolean isMapOpen = false;
    public static float mapTransition = 0f; // Для плавной анимации (0 - закрыта, 1 - открыта)
    public static boolean hasBlueRally = false;
    public static boolean hasRedRally = false;
    public static boolean blueRallyBlocked = false;
    public static boolean redRallyBlocked = false;
    public static boolean blueBleeding = false;
    public static boolean redBleeding = false;
    public static int RESPAWN_TIME = 10;
    public static boolean isGameStarted = false;
    public static boolean isInsidePoint = false;
    public static String pointName = "";
    public static String pointOwner = "NEUTRAL";
    public static float pointProgress = 0.0f;
    public static boolean isLocked = false;
    public static String nextObjectiveName = "";
    public static boolean isContested = false;
    public static String pointCapturingTeam = "NONE";
    public static List<AASWorldData.CapturePoint> allCapturePoints = new ArrayList<>();
    public static List<Component> menuChatHistory = new ArrayList<>();
    public static Map<String, MapPlayerInfo> mapPlayers = new HashMap<>();
    public static long lastFobResupplyTime = 0;
    public static void addChatMessage(Component msg) {
        menuChatHistory.add(0, msg);
        if (menuChatHistory.size() > 50) menuChatHistory.remove(menuChatHistory.size() - 1);
    }
}