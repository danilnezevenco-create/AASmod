package com.example.aas.client;

import com.example.aas.block.HubBlockEntity;
import com.example.aas.block.ModBlocks;
import com.example.aas.block.VehicleStationBlockEntity;
import com.example.aas.client.gui.HubRadialScreen;
import com.example.aas.client.gui.RadioRadialScreen;
import com.example.aas.client.sound.HubLoopingSound;
import com.example.aas.client.sound.StationLoopingSound;
import com.example.aas.network.PacketSpawnGhost;
import com.example.aas.network.PacketSyncGameData;
import com.example.aas.network.PacketSyncPoint;
import com.example.aas.world.AASWorldData;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import java.util.ArrayList;
import java.util.HashMap;
import com.example.aas.block.RallyPointBlockEntity; // ДОБАВЛЕНО
import com.example.aas.client.sound.RallyLoopingSound;
import com.example.aas.client.gui.KitTeamSelectScreen;
import com.example.aas.client.gui.PlayerKitSelectScreen;
import com.example.aas.network.PacketOpenPlayerKitMenu;
import java.util.List;

public class ClientHooks {
    public static void handleRecoil(float pitch, float yaw) {
        com.example.aas.client.RecoilHandler.addRecoil(pitch);
        if (yaw != 0 && net.minecraft.client.Minecraft.getInstance().player != null) {
            net.minecraft.client.Minecraft.getInstance().player.turn(yaw, 0);
        }
    }
    // === ДЛЯ РАЛЛИКА ===
    public static Object playRallySound(RallyPointBlockEntity entity, Object currentSound) {
        Minecraft mc = Minecraft.getInstance();
        if (currentSound != null && mc.getSoundManager().isActive((net.minecraft.client.resources.sounds.SoundInstance) currentSound)) {
            return currentSound;
        }
        com.example.aas.client.sound.RallyLoopingSound newSound = new com.example.aas.client.sound.RallyLoopingSound(entity);
        mc.getSoundManager().play(newSound);
        return newSound;
    }
    public static void openPlayerKitMenu(List<PacketOpenPlayerKitMenu.KitDTO> kits) {
        if (net.minecraft.client.Minecraft.getInstance().screen instanceof PlayerKitSelectScreen screen) {
            screen.updateKits(kits); // Обновляем в реальном времени
        } else {
            net.minecraft.client.Minecraft.getInstance().setScreen(new PlayerKitSelectScreen(kits));
        }
    }
    public static void stopRallySound(Object sound) {
        if (sound instanceof RallyLoopingSound s) {
            s.stopSound();
        }
    }

    // === ДЛЯ ХАБА ===
    public static Object playHubSound(HubBlockEntity entity, Object currentSound) {
        Minecraft mc = Minecraft.getInstance();
        // Проверяем, играет ли звук на самом деле в движке игры
        if (currentSound != null && mc.getSoundManager().isActive((net.minecraft.client.resources.sounds.SoundInstance) currentSound)) {
            return currentSound;
        }
        com.example.aas.client.sound.HubLoopingSound newSound = new com.example.aas.client.sound.HubLoopingSound(entity);
        mc.getSoundManager().play(newSound);
        return newSound;
    }

    private static Object playHubSoundInternal(HubBlockEntity entity) {
        HubLoopingSound sound = new HubLoopingSound(entity);
        Minecraft.getInstance().getSoundManager().play(sound);
        return sound;
    }

    public static void stopHubSound(Object sound) {
        if (sound instanceof HubLoopingSound s) {
            s.stopSound();
        }
    }
    public static void openKitTeamSelect() {
        net.minecraft.client.Minecraft.getInstance().setScreen(new KitTeamSelectScreen());
    }
    // ... (старые методы openRadioMenu, openHubMenu и звуки без изменений) ...
    public static void openRadioMenu() {
        Minecraft.getInstance().setScreen(new RadioRadialScreen());
    }

    public static void openHubMenu(BlockPos pos) {
        Minecraft.getInstance().setScreen(new HubRadialScreen(pos));
    }
    // Внутри класса ClientHooks:
    public static void openCrateMenu(int entityId) {
        net.minecraft.client.Minecraft.getInstance().setScreen(new com.example.aas.client.gui.CrateRadialScreen(entityId));
    }
    // В файле src/main/java/com/example/aas/client/ClientHooks.java

    public static void tryOpenRadioMenu(net.minecraft.world.entity.player.Player player) {
        if (player.isCreative()) {
            openRadioMenu();
            return;
        }

        if (player.getTeam() == null) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("You must join a TEAM (Blue/Red) first!").withStyle(net.minecraft.ChatFormatting.RED), true);
            return;
        }

        String playerName = player.getScoreboardName();
        boolean isInSquad = false;
        boolean isLeader = false;

        // Здесь безопасно используем ClientData, так как мы в клиентском классе
        for (com.example.aas.world.AASWorldData.Squad s : ClientData.clientSquads) {
            if (s.members.contains(playerName)) {
                isInSquad = true;
                if (s.leader.equals(playerName)) {
                    isLeader = true;
                }
                break;
            }
        }

        if (!isInSquad) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("You must join a SQUAD first! Press 'K'.").withStyle(net.minecraft.ChatFormatting.RED), true);
            return;
        }

        if (!isLeader) {
            player.displayClientMessage(net.minecraft.network.chat.Component.literal("You must be a Squad Leader to use this!").withStyle(net.minecraft.ChatFormatting.RED), true);
            return;
        }

        // Если всё ок - открываем меню
        openRadioMenu();
    }

    public static void handleSpawnGhost(PacketSpawnGhost msg) {
        Level level = Minecraft.getInstance().level;
        if (level != null) {
            Display.BlockDisplay ghost = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, level);
            if (msg.blockId == 14) {
                CompoundTag tag = new CompoundTag();
                tag.put("block_state", NbtUtils.writeBlockState(ModBlocks.HUB_BLOCK.get().defaultBlockState()));
                ghost.load(tag);
            }
            ghost.setPos(msg.pos.getX(), msg.pos.getY(), msg.pos.getZ());
            level.addFreshEntity(ghost);
        }
    }
    public static void handleDownedState(int entityId, boolean isDowned, boolean died) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) return;

        if (isDowned) {
            com.example.aas.client.ClientData.DOWNED_PLAYERS.add(entityId);
            if (mc.player != null && mc.player.getId() == entityId) {
                mc.player.getPersistentData().putBoolean("AAS_IsDowned", true);

                // ФИКС: Устанавливаем время только если оно еще не установлено
                if (com.example.aas.client.ClientData.globalDeathTimestamp == 0) {
                    com.example.aas.client.ClientData.globalDeathTimestamp = System.currentTimeMillis();
                }
                mc.setScreen(new com.example.aas.client.gui.DownedScreen());
            }
        } else {
            com.example.aas.client.ClientData.DOWNED_PLAYERS.remove(entityId);

            if (mc.player != null && mc.player.getId() == entityId) {
                mc.player.getPersistentData().putBoolean("AAS_IsDowned", false);

                if (mc.screen instanceof com.example.aas.client.gui.DownedScreen) {
                    mc.setScreen(null);
                }

                // Сбрасываем таймер ТОЛЬКО при настоящем подъёме медиком.
                // Если это Give Up/авто-бидаут (died = true) — не трогаем,
                // время в ноке должно донестись до экрана смерти.
                if (!died) { // Если реально подняли, а не убили окончательно
                    com.example.aas.client.ClientData.globalDeathTimestamp = 0;
                }
            }
        }
    }

    public static void handleDragState(boolean isDragging) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.getPersistentData().putBoolean("AAS_IsDraggingAlly", isDragging);
    }

    public static void handleSyncGameData(PacketSyncGameData msg) {
        java.util.Map<String, String> oldKits = new java.util.HashMap<>(ClientData.playerKits);
        ClientData.BLUE_TICKETS = msg.blueTickets;
        ClientData.RED_TICKETS = msg.redTickets;
        ClientData.hasBlueRally = msg.hasBlueRally;
        ClientData.hasRedRally = msg.hasRedRally;
        ClientData.blueBleeding = msg.blueBleeding;
        ClientData.redBleeding = msg.redBleeding;
        ClientData.RESPAWN_TIME = msg.respawnTime;
        ClientData.blueRallyBlocked = msg.blueBlocked;
        ClientData.redRallyBlocked = msg.redBlocked;
        ClientData.clientHubs = new ArrayList<>(msg.hubs);
        ClientData.clientStations = new ArrayList<>(msg.stations);
        ClientData.BLUE_FACTION = msg.blueFaction;
        ClientData.RED_FACTION = msg.redFaction;
        ClientData.gameMode = msg.gameMode;
        ClientData.invasionDefender = msg.invasionDefender;
        ClientData.invasionPrepTicks = msg.invasionPrepTicks;
        ClientData.isGameStarted = msg.isGameStarted;
        ClientData.allCapturePoints = new ArrayList<>(msg.capturePoints);
        ClientData.customBlueName = msg.blueCustomName;
        ClientData.customRedName = msg.redCustomName;
        ClientData.activeMarkers = new ArrayList<>(msg.activeMarkers);
        ClientData.serverHubSpawnCosts = msg.hubSpawnCosts;
        ClientData.serverHubSpawnCostAmount = msg.hubSpawnCost;
        ClientData.mapCenterX = msg.mapCenterX;
        ClientData.mapCenterZ = msg.mapCenterZ;
        ClientData.mapSizeBlocks = msg.mapSizeBlocks;
        ClientData.currentMapImage = msg.currentMapImage;


        // Статус готовности (голосование старта)
        ClientData.blueReady = msg.blueReady;
        ClientData.redReady = msg.redReady;

        ClientData.clientVehicles = new ArrayList<>(msg.markedVehicles);
        ClientData.blueSpawns = new HashMap<>(msg.blueSpawns);
        ClientData.redSpawns = new HashMap<>(msg.redSpawns);
        ClientData.neutralSpawns = new HashMap<>(msg.neutralSpawns);
        ClientData.playerKits = new HashMap<>(msg.playerKits);

        ClientData.voteActive = msg.voteActive;
        ClientData.voteTimer = msg.voteTimer;
        ClientData.votes = new HashMap<>(msg.votes);

        // --- СИНХРОНИЗАЦИЯ КОМАНДИРОВ (РАЗДЕЛЬНАЯ) ---
        ClientData.blueCMDId = msg.blueCMDId;
        ClientData.redCMDId = msg.redCMDId;

        // Синхронизация Blue
        ClientData.blueCmdVoteActive = msg.blueCmdVoteActive;
        ClientData.blueCmdCandidateName = msg.blueCmdCandidateName;
        ClientData.blueCmdVotes = new HashMap<>(msg.blueCmdVotes);

        // Синхронизация Red
        ClientData.redCmdVoteActive = msg.redCmdVoteActive;
        ClientData.redCmdCandidateName = msg.redCmdCandidateName;
        ClientData.redCmdVotes = new HashMap<>(msg.redCmdVotes);

        // Артиллерия и прочее
        ClientData.activeStrikes = new ArrayList<>(msg.activeStrikes);
        ClientData.blueArtPos = msg.blueArtPos;
        ClientData.redArtPos = msg.redArtPos;
        ClientData.blueArtTimer = msg.blueArtTimer;
        ClientData.redArtTimer = msg.redArtTimer;
        ClientData.blueArtReqName = msg.blueArtReqName;
        ClientData.redArtReqName = msg.redArtReqName;
        if (!oldKits.equals(ClientData.playerKits)) {
            if (net.minecraft.client.Minecraft.getInstance().screen instanceof PlayerKitSelectScreen) {
                com.example.aas.network.PacketHandler.INSTANCE.sendToServer(new com.example.aas.network.PacketRequestKitMenu());
            }
        }
    }

    public static void handleSyncPoint(PacketSyncPoint msg) {
        ClientData.isInsidePoint = msg.isInside;

        // Если пакет пустой (сервер шлет его, чтобы скрыть плашку HUD у тех, кто вне точки)
        if (msg.name.isEmpty()) {
            ClientData.pointName = "";
            return;
        }

        // Если игрок физически стоит на точке - обновляем данные для центрального HUD
        if (msg.isInside) {
            ClientData.pointName = msg.name;
            ClientData.pointOwner = msg.owner;
            ClientData.pointProgress = msg.progress;
            ClientData.isLocked = msg.isLocked;
            ClientData.nextObjectiveName = msg.nextObjective;
            ClientData.lockSecondsLeft = msg.lockSecondsLeft;
            ClientData.isContested = msg.isContested;
            ClientData.pointCapturingTeam = msg.capturingTeam;
            ClientData.pointCaptureRate = msg.captureRate;
        }

        // === ГЛОБАЛЬНОЕ ОБНОВЛЕНИЕ КАРТЫ ===
        // Обновляем статус точки в кэше карты (работает для всех игроков на сервере!)
        if (ClientData.allCapturePoints != null) {
            for (AASWorldData.CapturePoint cp : ClientData.allCapturePoints) {
                if (cp.name.equals(msg.name)) {
                    cp.owner = msg.owner;
                    cp.progress = msg.progress;
                    cp.capturingTeam = msg.capturingTeam;
                    break;
                }
            }
        }
    }
    public static void openPointEditor(com.example.aas.network.PacketOpenPointEditor msg) {
        net.minecraft.client.Minecraft.getInstance().setScreen(new com.example.aas.client.gui.PointEditorScreen(msg));
    }
    public static void openVictoryScreen(String winnerName, String winnerFaction, String subText, boolean isBlueWinner) {
        net.minecraft.client.Minecraft.getInstance().setScreen(
                new com.example.aas.client.gui.VictoryScreen(winnerName, winnerFaction, subText, isBlueWinner)
        );
    }
    // === ДЛЯ СТАНЦИИ ===
    public static Object playStationSound(VehicleStationBlockEntity entity, Object currentSound) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        System.out.println("[AAS DEBUG] playStationSound called, currentSound=" + currentSound);

        if (currentSound != null && mc.getSoundManager().isActive((net.minecraft.client.resources.sounds.SoundInstance) currentSound)) {
            System.out.println("[AAS DEBUG] sound already active, skip");
            return currentSound;
        }

        com.example.aas.client.sound.StationLoopingSound newSound = new com.example.aas.client.sound.StationLoopingSound(entity);
        System.out.println("[AAS DEBUG] creating new StationLoopingSound and calling play()");
        mc.getSoundManager().play(newSound);
        return newSound;
    }

    public static void stopStationSound(Object sound) {
        // БЫЛО: if (sound instanceof StationLoopingSound s) s.stop();
        // СТАЛО:
        if (sound instanceof StationLoopingSound s) s.stopSound();
    }
    public static void handleRadioVoiceActivity(String playerName) {
        boolean isNewSpeaker = !ClientData.RADIO_SPEAKERS.containsKey(playerName);

        // Обновляем таймер говорящего по рации
        ClientData.RADIO_SPEAKERS.put(playerName, System.currentTimeMillis());

        // Если игрок только начал говорить - проигрываем пип (код выполняется строго на клиенте)
        if (isNewSpeaker) {
            net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(com.example.aas.sound.ModSounds.RADIO_BEEP.get(), 1.0F)
            );
        }
    }
    public static void handleReviveProgress(float progress, boolean isBeingRevived) {
        if (isBeingRevived) {
            com.example.aas.client.ClientData.reviveProgressSelf = progress;
        } else {
            com.example.aas.client.ClientData.reviveProgressOther = progress;
        }
    }
}