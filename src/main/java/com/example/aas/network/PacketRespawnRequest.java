// PATH: src\main\java\com\example\aas\network\PacketRespawnRequest.java
package com.example.aas.network;

import com.example.aas.block.RallyPointBlock;
import com.example.aas.world.AASWorldData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.Supplier;

public class PacketRespawnRequest {
    private final String type;

    public PacketRespawnRequest(String type) {
        this.type = type;
    }

    public static void encode(PacketRespawnRequest msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.type);
    }

    public static PacketRespawnRequest decode(FriendlyByteBuf buf) {
        return new PacketRespawnRequest(buf.readUtf());
    }

    public static void handle(PacketRespawnRequest msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ServerLevel level = player.serverLevel();
            AASWorldData data = AASWorldData.get(level);

            BlockPos targetPos = null;
            ResourceKey<Level> targetDimension = level.dimension();
            String currentDim = level.dimension().location().toString();

            boolean useCustomSpawn = false;

            String team = "NEUTRAL";
            if (player.getTeam() != null) {
                team = player.getTeam().getName().equalsIgnoreCase("Blue") ? "BLUE" : "RED";
            }
            boolean isBlue = team.equals("BLUE");

            // --- 1. ОПРЕДЕЛЕНИЕ ТОЧКИ СПАВНА ---

            if (msg.type.equals("MAIN")) {
                Map<String, BlockPos> spawns = isBlue ? data.blueSpawns : (team.equals("RED") ? data.redSpawns : data.neutralSpawns);
                if (spawns.containsKey(currentDim)) {
                    targetPos = spawns.get(currentDim);
                    useCustomSpawn = true;
                }
            }
            else if (msg.type.equals("RALLY")) {
                String pName = player.getScoreboardName();
                AASWorldData.Squad mySquad = null;
                for (AASWorldData.Squad s : data.squads) {
                    if (s.members.contains(pName)) { mySquad = s; break; }
                }

                if (mySquad != null && mySquad.rallyPos != null) {
                    String rallyDim = mySquad.rallyDimension != null ? mySquad.rallyDimension : "minecraft:overworld";
                    if (!rallyDim.equals(currentDim)) {
                        player.sendSystemMessage(Component.literal("Rally Point is in another dimension!").withStyle(ChatFormatting.RED));
                        return;
                    }
                    if (level.isLoaded(mySquad.rallyPos) && level.getBlockState(mySquad.rallyPos).getBlock() instanceof RallyPointBlock) {
                        targetPos = findRandomSafeSpawn(level, mySquad.rallyPos, 5);
                        useCustomSpawn = true;
                    } else {
                        player.sendSystemMessage(Component.literal("Rally Point destroyed!").withStyle(ChatFormatting.RED));
                        return;
                    }
                }
            }
            else if (msg.type.startsWith("HUB")) {
                String[] parts = msg.type.split(":");
                if (parts.length == 4) {
                    try {
                        BlockPos reqPos = new BlockPos(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
                        for (AASWorldData.HubInfo h : data.hubs) {
                            if (h.pos.equals(reqPos) && h.team.equalsIgnoreCase(team) && h.constructed) {
                                if (h.dimension != null && !h.dimension.equals(currentDim)) continue;
                                if (h.isBlocked) {
                                    player.sendSystemMessage(Component.literal("Spawn Failed: FOB is Overrun!").withStyle(ChatFormatting.RED));
                                    return;
                                }

                                // === ПРОВЕРКА И СНЯТИЕ МАТЕРИАЛОВ ===
                                boolean spawnCosts = com.example.aas.config.AASConfig.HUB_SPAWN_COSTS_MATERIALS.get();
                                int spawnCost = com.example.aas.config.AASConfig.HUB_SPAWN_MATERIAL_COST.get();

                                if (spawnCosts) {
                                    // ПРИНУДИТЕЛЬНО ГРУЗИМ ЧАНК ХАБА (игрок мог умереть далеко)
                                    level.getChunkSource().getChunk(reqPos.getX() >> 4, reqPos.getZ() >> 4, true);

                                    net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(reqPos);
                                    if (be instanceof com.example.aas.block.HubBlockEntity hubBe) {
                                        if (hubBe.getMaterials() < spawnCost) {
                                            player.sendSystemMessage(Component.literal("Spawn Failed: Not enough materials! (" + hubBe.getMaterials() + "/" + spawnCost + ")").withStyle(ChatFormatting.RED));
                                            return;
                                        }
                                        hubBe.consumeMaterials(spawnCost); // Снимаем материалы
                                        h.materials = hubBe.getMaterials(); // Обновляем кэш
                                        data.setDirty();
                                        PacketHandler.sendToAllClients(level, data); // Сразу синхронизируем на клиенты
                                    } else {
                                        player.sendSystemMessage(Component.literal("Spawn Failed: FOB block missing!").withStyle(ChatFormatting.RED));
                                        return;
                                    }
                                }

                                targetPos = findRandomSafeSpawn(level, reqPos, 5);
                                useCustomSpawn = true;
                                break;
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }

            // --- 2. ТЕЛЕПОРТАЦИЯ ---
            if (useCustomSpawn && targetPos != null) {
                player.setRespawnPosition(targetDimension, targetPos, 0.0f, true, false);
                player.teleportTo(level, targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, player.getYRot(), 0.0f);
            }

            // PATH: src/main/java/com/example/aas/network/PacketRespawnRequest.java

// ... внутри метода handle, после телепортации ...

// --- 3. ВЫДАЧА КИТА (ИНВЕНТАРЯ) ---
            if (data.isGameStarted) {
                // Если игра идет, выдаем вещи как обычно
                if (player.getPersistentData().contains("AAS_PendingKit")) {
                    ResupplyHandler.tryApplyPendingKit(player, data);
                } else {
                    String currentKitName = player.getPersistentData().getString("AAS_CurrentKit");
                    if (!currentKitName.isEmpty() && !currentKitName.equals("Unassigned")) {
                        String tName = player.getTeam() != null ? player.getTeam().getName().toUpperCase() : "NEUTRAL";
                        AASWorldData.KitInfo kit = tName.equals("BLUE") ? data.blueKits.get(currentKitName) : data.redKits.get(currentKitName);

                        if (kit != null) {
                            ResupplyHandler.applyKitToPlayer(player, kit);
                        }
                    }
                }
            } else {
                // ЕСЛИ ИГРА НЕ НАЧАТА:
                // Мы НЕ вызываем applyKitToPlayer.
                // Тег AAS_CurrentKit или AAS_PendingKit сохраняется в NBT игрока автоматически,
                // так что система "помнит" его выбор, но инвентарь остается пустым.
                player.sendSystemMessage(Component.literal("Кит выбран, предметы будут выданы после начала игры.")
                        .withStyle(ChatFormatting.YELLOW));
            }

            if (player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
                player.setGameMode(GameType.SURVIVAL);
            }
            else {
                String currentKitName = player.getPersistentData().getString("AAS_CurrentKit");
                if (!currentKitName.isEmpty() && !currentKitName.equals("Unassigned")) {
                    String tName = player.getTeam() != null ? player.getTeam().getName().toUpperCase() : "NEUTRAL";
                    AASWorldData.KitInfo kit = tName.equals("BLUE") ? data.blueKits.get(currentKitName) : data.redKits.get(currentKitName);

                    if (kit != null) {
                        ResupplyHandler.applyKitToPlayer(player, kit);
                    }
                }
            }

            if (player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
                player.setGameMode(GameType.SURVIVAL);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static BlockPos findRandomSafeSpawn(ServerLevel level, BlockPos center, int radius) {
        Random rand = new Random();
        for (int i = 0; i < 20; i++) {
            int dx = rand.nextInt(radius * 2 + 1) - radius;
            int dz = rand.nextInt(radius * 2 + 1) - radius;
            for (int dy = -1; dy <= 2; dy++) {
                BlockPos candidate = center.offset(dx, dy, dz);
                if (isValidSpawnSpot(level, candidate)) {
                    return candidate;
                }
            }
        }
        return center.above();
    }

    private static boolean isValidSpawnSpot(ServerLevel level, BlockPos pos) {
        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        BlockState ground = level.getBlockState(pos.below());

        boolean spaceClear = feet.getCollisionShape(level, pos).isEmpty() &&
                head.getCollisionShape(level, pos.above()).isEmpty();

        boolean groundSolid = ground.isFaceSturdy(level, pos.below(), Direction.UP);

        return spaceClear && groundSolid;
    }
}