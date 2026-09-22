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

            // Вспомогательный метод для фолбэка на мейн спавн команды
            // Используется если хаб/ралли не нашли безопасное место
            Map<String, BlockPos> mainSpawns = isBlue ? data.blueSpawns
                    : (team.equals("RED") ? data.redSpawns : data.neutralSpawns);

            // --- 1. ОПРЕДЕЛЕНИЕ ТОЧКИ СПАВНА ---

            if (msg.type.equals("MAIN")) {
                if (mainSpawns.containsKey(currentDim)) {
                    targetPos = mainSpawns.get(currentDim);
                    useCustomSpawn = true;
                }
            }
            else if (msg.type.equals("RALLY")) {
                String pName = player.getScoreboardName();
                AASWorldData.Squad mySquad = getPlayerSquad(pName, data);

                if (mySquad != null && mySquad.rallyPos != null) {
                    if (mySquad.isRallyBlocked && !player.isCreative()) {
                        player.sendSystemMessage(Component.translatable("aas.msg.spawn_overrun_rally").withStyle(ChatFormatting.RED));
                        return;
                    }

                    String rallyDim = mySquad.rallyDimension != null ? mySquad.rallyDimension : "minecraft:overworld";
                    if (!rallyDim.equals(currentDim)) {
                        player.sendSystemMessage(Component.literal("Rally Point is in another dimension!").withStyle(ChatFormatting.RED));
                        return;
                    }

                    if (level.isLoaded(mySquad.rallyPos) && level.getBlockState(mySquad.rallyPos).getBlock() instanceof RallyPointBlock) {
                        targetPos = findRandomSafeSpawn(level, mySquad.rallyPos, 10);
                        useCustomSpawn = true;

                        // ФИКС: если вокруг раллика нет места — спавним на мейне
                        if (targetPos == null) {
                            targetPos = mainSpawns.get(currentDim);
                            player.sendSystemMessage(Component.literal("Rally spawn blocked! Redirecting to Main Base.").withStyle(ChatFormatting.YELLOW));
                        }
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

                                // === ЖЁСТКАЯ ПРОВЕРКА ВРАГОВ ДЛЯ ХАБА ===
                                int hubRadius = com.example.aas.config.AASConfig.HUB_BLOCK_RADIUS.get();
                                int hubEnemiesReq = com.example.aas.config.AASConfig.HUB_BLOCK_ENEMY_COUNT.get();
                                int enemyCount = com.example.aas.events.GameLogicEvents.getEnemyCount(level, reqPos, team);

                                if ((h.isBlocked || enemyCount >= hubEnemiesReq) && !player.isCreative()) {
                                    player.sendSystemMessage(Component.translatable("aas.msg.spawn_overrun_fob").withStyle(ChatFormatting.RED));
                                    return;
                                }

                                // === ПРОВЕРКА И СНЯТИЕ МАТЕРИАЛОВ ===
                                boolean spawnCosts = com.example.aas.config.AASConfig.HUB_SPAWN_COSTS_MATERIALS.get();
                                int spawnCost = com.example.aas.config.AASConfig.HUB_SPAWN_MATERIAL_COST.get();

                                if (spawnCosts) {
                                    level.getChunkSource().getChunk(reqPos.getX() >> 4, reqPos.getZ() >> 4, true);

                                    net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(reqPos);
                                    if (be instanceof com.example.aas.block.HubBlockEntity hubBe) {
                                        if (hubBe.getMaterials() < spawnCost) {
                                            player.sendSystemMessage(Component.literal("Spawn Failed: Not enough materials! (" + hubBe.getMaterials() + "/" + spawnCost + ")").withStyle(ChatFormatting.RED));
                                            return;
                                        }
                                        hubBe.consumeMaterials(spawnCost);
                                        h.materials = hubBe.getMaterials();
                                        data.setDirty();
                                        PacketHandler.sendToAllClients(level, data);
                                    } else {
                                        player.sendSystemMessage(Component.literal("Spawn Failed: FOB block missing!").withStyle(ChatFormatting.RED));
                                        return;
                                    }
                                }

                                targetPos = findRandomSafeSpawn(level, reqPos, 10);
                                useCustomSpawn = true;

                                // ФИКС: если вокруг хаба нет места — спавним на мейне
                                if (targetPos == null) {
                                    targetPos = mainSpawns.get(currentDim);
                                    player.sendSystemMessage(Component.literal("FOB spawn blocked! Redirecting to Main Base.").withStyle(ChatFormatting.YELLOW));
                                }

                                break;
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }

            // --- 2. ТЕЛЕПОРТАЦИЯ И ОЧКИ ---
            if (useCustomSpawn && targetPos != null) {
                player.setRespawnPosition(targetDimension, targetPos, 0.0f, true, false);
                player.teleportTo(level, targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, player.getYRot(), 0.0f);

                com.example.aas.network.ResupplyHandler.tryApplyPendingKit(player, data);

                if (player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
                    player.setGameMode(GameType.SURVIVAL);
                }

                // --- НОВОЕ: Выдача очков за спавн ---
                if (msg.type.equals("RALLY")) {
                    AASWorldData.Squad s = getPlayerSquad(player.getScoreboardName(), data);
                    if (s != null && !s.leader.equals(player.getScoreboardName())) {
                        com.example.aas.events.StatsHandler.addStatsByName(player.server, s.leader, 0, 5, "Squad spawned on Rally");
                    }
                } else if (msg.type.startsWith("HUB")) {
                    String[] parts = msg.type.split(":");
                    if (parts.length == 4) {
                        try {
                            BlockPos reqPos = new BlockPos(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
                            for (AASWorldData.HubInfo h : data.hubs) {
                                if (h.pos.equals(reqPos)) {
                                    if (!h.builderName.equals(player.getScoreboardName())) {
                                        com.example.aas.events.StatsHandler.addStatsByName(player.server, h.builderName, 5, 0, "Player spawned on FOB");
                                    }
                                    break;
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                } else {
                    player.sendSystemMessage(Component.literal("Кит забронирован. Предметы будут выданы после начала игры.")
                            .withStyle(ChatFormatting.YELLOW));
                }
            } else {
                player.sendSystemMessage(Component.translatable("aas.msg.spawn_unavailable").withStyle(ChatFormatting.RED));
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static AASWorldData.Squad getPlayerSquad(String playerName, AASWorldData data) {
        for (AASWorldData.Squad s : data.squads) {
            if (s.members.contains(playerName)) return s;
        }
        return null;
    }

    // ФИКС: теперь возвращает null если не нашла безопасное место,
    // вместо fallback на heightmap который мог быть в воздухе или под землёй
    private static BlockPos findRandomSafeSpawn(ServerLevel level, BlockPos center, int radius) {
        java.util.Random rand = new java.util.Random();

        for (int i = 0; i < 100; i++) {
            int dx = rand.nextInt(radius * 2 + 1) - radius;
            int dz = rand.nextInt(radius * 2 + 1) - radius;

            // Проверяем высоту от -3 до +3 относительно самого спавнера (раллика/хаба)
            // Идем снизу вверх, чтобы приоритетно спавнить на полу, а не левитировать
            for (int dy = -3; dy <= 3; dy++) {
                BlockPos candidate = center.offset(dx, dy, dz);
                if (isValidSpawnSpot(level, candidate)) {
                    return candidate;
                }
            }
        }

        return null; // Фолбэк на главную базу обрабатывается в методе handle()
    }

    private static boolean isValidSpawnSpot(ServerLevel level, BlockPos pos) {
        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        BlockState ground = level.getBlockState(pos.below());

        // 1. Место для ног и головы игрока должно быть свободно от твердых коллизий
        boolean spaceClear = feet.getCollisionShape(level, pos).isEmpty() &&
                head.getCollisionShape(level, pos.above()).isEmpty();

        // 2. ФИКС ПОЛУБЛОКОВ: Блок под ногами не обязан быть "полным" (isFaceSturdy),
        // достаточно того, чтобы у него просто БЫЛА физическая коллизия (он не пустой).
        boolean groundSolid = !ground.getCollisionShape(level, pos.below()).isEmpty();

        // 3. Защита от опасных зон и ИСКЛЮЧЕНИЯ ПО БЛОКАМ
        boolean notDangerous = !feet.is(net.minecraft.world.level.block.Blocks.LAVA) &&
                !feet.is(net.minecraft.world.level.block.Blocks.FIRE) &&
                !ground.is(net.minecraft.world.level.block.Blocks.LAVA) &&
                !feet.is(net.minecraft.world.level.block.Blocks.WATER) &&  // не спавним ногами в воде
                !head.is(net.minecraft.world.level.block.Blocks.WATER) &&  // и чтобы голова не была в воде
                !feet.is(net.minecraft.tags.BlockTags.LEAVES) &&           // Исключаем спавн в листве (ноги)
                !head.is(net.minecraft.tags.BlockTags.LEAVES) &&           // Исключаем спавн в листве (голова)
                !feet.is(net.minecraft.world.level.block.Blocks.COCOA) &&  // Исключаем какао-бобы
                !head.is(net.minecraft.world.level.block.Blocks.COCOA);

        return spaceClear && groundSolid && notDangerous;
    }
}