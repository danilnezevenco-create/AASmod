// PATH: src/main/java/com/example/aas/client/ClientData.java
package com.example.aas.client;

import com.example.aas.network.MapPlayerInfo;
import com.example.aas.world.AASWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import java.util.*;
import com.example.aas.network.MapPlayerInfo;

public class ClientData {
    // 1. Основные параметры
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
    public static String gameMode = "AAS";
    public static String invasionDefender = "NONE";
    public static int invasionPrepTicks = 0;
    public static String myCurrentKit = "Unassigned";
    public static final Map<String, Long> SQUAD_SPEAKERS = new java.util.concurrent.ConcurrentHashMap<>();
    public static final Map<String, Long> RADIO_SPEAKERS = new java.util.concurrent.ConcurrentHashMap<>();
    public static long globalDeathTimestamp = 0;
    public static long deathFadeStartTime = 0;
    public static boolean deathFadePlayed = false;
    public static boolean serverAutoBalance = false;
    public static List<com.example.aas.network.PlayerStatInfo> playerStats = new ArrayList<>();
    public static List<AASWorldData.StationInfo> clientStations = new ArrayList<>();
    // 3. Командиры и РАЗДЕЛЬНОЕ голосование за CMD
    public static int blueCMDId = -1;
    public static int redCMDId = -1;
    public static float reviveProgressSelf = -1f;  // нас поднимают
    public static float reviveProgressOther = -1f; // мы поднимаем союзника

    public static boolean blueCmdVoteActive = false;
    public static String blueCmdCandidateName = "";
    public static Map<UUID, Boolean> blueCmdVotes = new HashMap<>();

    public static boolean redCmdVoteActive = false;
    public static String redCmdCandidateName = "";
    public static Map<UUID, Boolean> redCmdVotes = new HashMap<>();

    // 4. Голосование за старт и готовность
    public static boolean voteActive = false;
    public static int voteTimer = 0;
    public static Map<UUID, Boolean> votes = new HashMap<>();
    public static float voteTransition = 0f;
    public static boolean blueReady = false;
    public static boolean redReady = false;

    // 5. Карта и зум
    public static double mapScale = 4.0;
    public static int mapCenterX = 0;
    public static int mapCenterZ = 0;
    public static int mapSizeBlocks = 2048;
    public static String currentMapImage = "map1";
    public static boolean isMapOpen = false;
    public static float mapTransition = 0f;

    public static void zoomMap(double delta) {
        double zoomFactor = 1.2;
        double maxScaleLimit = Math.max(2.0, (double) mapSizeBlocks / 350.0);
        if (delta > 0) mapScale = Math.max(0.5, mapScale / zoomFactor);
        else mapScale = Math.min(maxScaleLimit, mapScale * zoomFactor);
    }

    // 6. Точки захвата и логика
    public static boolean isGameStarted = false;
    public static boolean isInsidePoint = false;
    public static String pointName = "";
    public static String pointOwner = "NEUTRAL";
    public static float pointProgress = 0.0f;
    public static boolean isLocked = false;
    public static String nextObjectiveName = "";
    public static int lockSecondsLeft = 0;
    public static boolean isContested = false;
    public static String pointCapturingTeam = "NONE";
    public static int pointCaptureRate = 0;
    public static List<AASWorldData.CapturePoint> allCapturePoints = new ArrayList<>();
    public static Map<String, MapPlayerInfo> mapPlayers = new HashMap<>();

    // 7. Снабжение и Артиллерия
    public static long lastFobResupplyTime = 0;
    public static List<AASWorldData.ActiveStrike> activeStrikes = new ArrayList<>();
    public static BlockPos blueArtPos = BlockPos.ZERO, redArtPos = BlockPos.ZERO;
    public static int blueArtTimer = 0, redArtTimer = 0;
    public static String blueArtReqName = "", redArtReqName = "";

    // 8. Спавны, Техника и Киты
    public static Map<String, BlockPos> blueSpawns = new HashMap<>();
    public static Map<String, BlockPos> redSpawns = new HashMap<>();
    public static Map<String, BlockPos> neutralSpawns = new HashMap<>();
    public static List<AASWorldData.VehicleRecord> clientVehicles = new ArrayList<>();
    public static Map<String, String> playerKits = new HashMap<>();
    public static boolean hasBlueRally = false;
    public static boolean hasRedRally = false;
    public static boolean blueRallyBlocked = false;
    public static boolean redRallyBlocked = false;
    public static boolean blueBleeding = false;
    public static boolean redBleeding = false;
    public static int RESPAWN_TIME = 10;

    // 9. Чат
    public static List<Component> menuChatHistory = new ArrayList<>();
    public static String serverReviveItem = "minecraft:paper"; // дефолт на всякий случай

    public static void addChatMessage(Component msg) {
        menuChatHistory.add(0, msg);
        if (menuChatHistory.size() > 50) menuChatHistory.remove(menuChatHistory.size() - 1);
    }

    // По умолчанию считаем "не подключен", пока Simple Voice Chat явно не подтвердит обратное
    public static boolean voicechatConnected = false;

    // 10. Уведомления (Capture Notifications)
    public static class CaptureNotification {
        public String pointName;
        public String team;
        public boolean isNeutralized;
        public long startTime;
        public long duration = 5000;

        public CaptureNotification(String name, String team, boolean neutralized) {
            this.pointName = name;
            this.team = team;
            this.isNeutralized = neutralized;
            this.startTime = System.currentTimeMillis();
        }
    }

    public static List<CaptureNotification> captureNotifications = new java.util.concurrent.CopyOnWriteArrayList<>();

    // 11. НОВОЕ: панель отряда (левый нижний угол, см. SquadPanelOverlay)
    // true = развёрнута полностью, false = свёрнута (виден только заголовок). Переключается клавишей Y.
    public static boolean squadPanelExpanded = true;

    // 12. НОВОЕ: плашка с часами сквадного (правый верхний угол)
    public static String squadLeaderPlaytimeName = "";
    public static long squadLeaderPlaytimeHours = -1;
    public static boolean showSquadLeaderPlaytime = false;
    public static long squadLeaderPlaytimeShownAt = 0L;

    // 13. НОВОЕ: выделение игроков на карте (чисто клиентское, сервер не участвует)
    // ник -> момент (System.currentTimeMillis()), когда выделение само сбросится
    public static final Map<String, Long> HIGHLIGHTED_PLAYERS = new java.util.concurrent.ConcurrentHashMap<>();

    // ТЕСТ: 3 секунды. Для 5 минут поставь 300_000L (5 * 60 * 1000 мс)
    public static final long HIGHLIGHT_DURATION_MS = 300_000L;

    public static boolean isHighlighted(String name) {
        if (name == null) return false;
        Long until = HIGHLIGHTED_PLAYERS.get(name);
        if (until == null) return false;
        if (System.currentTimeMillis() >= until) {
            HIGHLIGHTED_PLAYERS.remove(name, until); // время вышло - сбрасываем
            return false;
        }
        return true;
    }

    // ЛКМ по нику в списке отрядов: если выделен - снять, если нет - выделить на HIGHLIGHT_DURATION_MS
    public static void toggleHighlight(String name) {
        if (name == null || name.isEmpty()) return;
        if (isHighlighted(name)) HIGHLIGHTED_PLAYERS.remove(name);
        else HIGHLIGHTED_PLAYERS.put(name, System.currentTimeMillis() + HIGHLIGHT_DURATION_MS);
    }
    // 14. НОВОЕ: перевод пунктов контекстного меню отряда (ПКМ по нику / по отряду).
    // В коде пункты остаются английскими (по ним работает логика), переводится только показ.
    public static Component squadMenuOption(String opt) {
        String key;
        switch (opt) {
            case "Promote to SL":    key = "aas.gui.squad.menu.promote_sl"; break;
            case "Set FTL Bravo":    key = "aas.gui.squad.menu.set_ftl_bravo"; break;
            case "Set FTL Charlie":  key = "aas.gui.squad.menu.set_ftl_charlie"; break;
            case "Pass FTL Bravo":   key = "aas.gui.squad.menu.pass_ftl_bravo"; break;
            case "Pass FTL Charlie": key = "aas.gui.squad.menu.pass_ftl_charlie"; break;
            case "Add to Bravo":     key = "aas.gui.squad.menu.add_bravo"; break;
            case "Add to Charlie":   key = "aas.gui.squad.menu.add_charlie"; break;
            case "Remove from FT":   key = "aas.gui.squad.menu.remove_ft"; break;
            case "Kick from Squad":  key = "aas.gui.squad.menu.kick"; break;
            case "Disband Squad":    key = "aas.gui.squad.menu.disband"; break;
            default: return Component.literal(opt);
        }
        return Component.translatable(key);
    }

    // Ширина меню под самый длинный пункт (минимум 100, как было раньше)
    public static int squadMenuWidth(net.minecraft.client.gui.Font font, List<String> options) {
        int w = 100;
        for (String o : options) w = Math.max(w, font.width(squadMenuOption(o)) + 8);
        return w;
    }
}

