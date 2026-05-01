package com.example.aas.client;

import com.example.aas.block.HubBlockEntity;
import com.example.aas.block.ModBlocks;
import com.example.aas.client.gui.HubRadialScreen;
import com.example.aas.client.gui.RadioRadialScreen;
import com.example.aas.client.sound.HubLoopingSound;
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
    public static Object playRallySound(RallyPointBlockEntity entity, Object currentSound) {
        if (currentSound == null) {
            RallyLoopingSound sound = new RallyLoopingSound(entity);
            Minecraft.getInstance().getSoundManager().play(sound);
            return sound;
        }
        return currentSound;
    }

    public static void stopRallySound(Object sound) {
        if (sound instanceof RallyLoopingSound s) {
            s.stopSound();
        }
    }

    // === БЕЗОПАСНЫЙ ЗАПУСК ЗВУКА ХАБА ===
    public static Object playHubSound(HubBlockEntity entity, Object currentSound) {
        if (currentSound != null) {
            HubLoopingSound sound = (HubLoopingSound) currentSound;
            if (sound.isStopped()) return playHubSoundInternal(entity);
            return currentSound;
        }
        return playHubSoundInternal(entity);
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
    // Добавь этот метод внутрь класса ClientHooks
    public static void openPlayerKitMenu(List<PacketOpenPlayerKitMenu.KitDTO> kits) {
        net.minecraft.client.Minecraft.getInstance().setScreen(new PlayerKitSelectScreen(kits));
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
    public static void handleDownedState(int entityId, boolean isDowned) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) return;

        if (isDowned) {
            com.example.aas.client.ClientData.DOWNED_PLAYERS.add(entityId);
            // Если упали МЫ — открываем экран нока
            if (mc.player != null && mc.player.getId() == entityId) {
                mc.setScreen(new com.example.aas.client.gui.DownedScreen());
            }
        } else {
            com.example.aas.client.ClientData.DOWNED_PLAYERS.remove(entityId);
            // Если подняли НАС — закрываем экран
            if (mc.player != null && mc.player.getId() == entityId) {
                if (mc.screen instanceof com.example.aas.client.gui.DownedScreen) {
                    mc.setScreen(null);
                }
            }
        }
    }
    // PATH: src/main/java/com/example/aas/client/ClientHooks.java
    public static void handleSyncGameData(PacketSyncGameData msg) {
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
        ClientData.BLUE_FACTION = msg.blueFaction;
        ClientData.RED_FACTION = msg.redFaction;
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

        // ПУНКТ 2: ТЕХНИКА
        ClientData.clientVehicles = new ArrayList<>(msg.markedVehicles);

        ClientData.blueSpawns = new HashMap<>(msg.blueSpawns);
        ClientData.redSpawns = new HashMap<>(msg.redSpawns);
        ClientData.neutralSpawns = new HashMap<>(msg.neutralSpawns);
        ClientData.playerKits = new HashMap<>(msg.playerKits);
    }

    public static void handleSyncPoint(PacketSyncPoint msg) {
        ClientData.isInsidePoint = msg.isInside;
        ClientData.pointName = msg.name;
        ClientData.pointOwner = msg.owner;
        ClientData.pointProgress = msg.progress;
        ClientData.isLocked = msg.isLocked;
        ClientData.nextObjectiveName = msg.nextObjective;
        ClientData.isContested = msg.isContested;
        ClientData.pointCapturingTeam = msg.capturingTeam;
    }
}