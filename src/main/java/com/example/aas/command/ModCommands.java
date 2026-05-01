// PATH: src\main\java\com\example\aas\command\ModCommands.java
package com.example.aas.command;

import com.example.aas.events.GameLogicEvents;
import com.example.aas.network.PacketHandler;
import com.example.aas.network.PacketSquadAction;
import com.example.aas.world.AASWorldData;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ModCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("aas")
                .requires(s -> s.hasPermission(2))

                // --- УПРАВЛЕНИЕ ИГРОЙ ---
                .then(Commands.literal("gamestart")
                        .then(Commands.argument("active", BoolArgumentType.bool())
                                .executes(ctx -> setGameStart(ctx.getSource(), BoolArgumentType.getBool(ctx, "active")))))

                .then(Commands.literal("deathtimer")
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(0))
                                .executes(ctx -> setRespawnTime(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "seconds")))))

                .then(Commands.literal("deathtickets")
                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                .executes(ctx -> setDeathTickets(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "amount")))))
                // === ВСТАВИТЬ В МЕТОД register ===
                .then(Commands.literal("clearsquad")
                        .then(Commands.argument("team", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("blue", "red"), builder))
                                .executes(ctx -> clearSquads(ctx.getSource(), StringArgumentType.getString(ctx, "team")))
                        )
                )
                // --- ТИКЕТЫ ---
                .then(Commands.literal("teamtickets")
                        .then(Commands.argument("team", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("blue", "red"), builder))
                                .then(Commands.argument("amount", IntegerArgumentType.integer())
                                        .executes(ctx -> setTickets(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "team"),
                                                IntegerArgumentType.getInteger(ctx, "amount")
                                        ))
                                )
                        )
                )

                .then(Commands.literal("teamjoin")
                        .then(Commands.argument("team", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("blue", "red"), builder))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> joinTeam(ctx.getSource(), StringArgumentType.getString(ctx, "team"), EntityArgument.getPlayer(ctx, "player"))))))

                .then(Commands.literal("addpoint")
                        .then(Commands.argument("shape", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("cube", "cylinder"), builder))
                                .then(Commands.argument("pos1", BlockPosArgument.blockPos())
                                        .then(Commands.argument("pos2", BlockPosArgument.blockPos())
                                                .then(Commands.argument("name", StringArgumentType.string())
                                                        .then(Commands.argument("bluePriority", IntegerArgumentType.integer(0, 999))
                                                                .then(Commands.argument("redPriority", IntegerArgumentType.integer(0, 999))
                                                                        .then(Commands.argument("timeMin", IntegerArgumentType.integer(1))
                                                                                .then(Commands.argument("penalty", IntegerArgumentType.integer(0))
                                                                                        .then(Commands.argument("captureDeduct", IntegerArgumentType.integer(0))
                                                                                                .then(Commands.argument("lockMinutes", IntegerArgumentType.integer(0))
                                                                                                        .executes(ctx -> addPoint(ctx.getSource(),
                                                                                                                StringArgumentType.getString(ctx, "shape"),
                                                                                                                BlockPosArgument.getLoadedBlockPos(ctx, "pos1"),
                                                                                                                BlockPosArgument.getLoadedBlockPos(ctx, "pos2"),
                                                                                                                StringArgumentType.getString(ctx, "name"),
                                                                                                                IntegerArgumentType.getInteger(ctx, "bluePriority"),
                                                                                                                IntegerArgumentType.getInteger(ctx, "redPriority"),
                                                                                                                IntegerArgumentType.getInteger(ctx, "timeMin"),
                                                                                                                IntegerArgumentType.getInteger(ctx, "penalty"),
                                                                                                                IntegerArgumentType.getInteger(ctx, "captureDeduct"),
                                                                                                                IntegerArgumentType.getInteger(ctx, "lockMinutes")
                                                                                                        ))
                                                                                                )
                                                                                        )
                                                                                )
                                                                        )
                                                                )
                                                        )
                                                )
                                        )
                                )
                        )
                )

                // === УДАЛЕНИЕ ТОЧКИ (Только текущий мир) ===
                .then(Commands.literal("removepoint")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .suggests((ctx, builder) -> suggestLocalPoints(ctx, builder)) // Локальные подсказки
                                .executes(ctx -> removePoint(ctx.getSource(), StringArgumentType.getString(ctx, "name")))
                        )
                )

                // === СБРОС ТОЧКИ (Только текущий мир) ===
                .then(Commands.literal("pointclear")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .suggests((ctx, builder) -> suggestLocalPoints(ctx, builder)) // Локальные подсказки
                                .executes(ctx -> clearSpecificPoint(ctx.getSource(), StringArgumentType.getString(ctx, "name")))
                        )
                )

                // --- СПАВНЫ ---
                .then(Commands.literal("teamspawn")
                        .then(Commands.argument("team", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("blue", "red", "none"), builder))
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(ctx -> setTeamSpawn(ctx.getSource(), StringArgumentType.getString(ctx, "team"), BlockPosArgument.getLoadedBlockPos(ctx, "pos")))
                                )
                        )
                )
                .then(Commands.literal("map")
                        .then(Commands.literal("setcenter")
                                // Добавляем аргументы для X и Z
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .executes(ctx -> {
                                                    // Получаем значения из команды
                                                    int x = IntegerArgumentType.getInteger(ctx, "x");
                                                    int z = IntegerArgumentType.getInteger(ctx, "z");

                                                    ServerLevel level = ctx.getSource().getLevel();
                                                    AASWorldData data = AASWorldData.get(level);

                                                    data.mapCenterX = x;
                                                    data.mapCenterZ = z;
                                                    data.setDirty();

                                                    // Синхронизируем данные со всеми игроками
                                                    PacketHandler.sendToAllClients(level, data);

                                                    ctx.getSource().sendSuccess(() -> Component.literal("Map center manually set to X: " + x + ", Z: " + z), true);
                                                    return 1;
                                                })
                                        )
                                )
                        )
                        .then(Commands.literal("setsize")
                                .then(Commands.argument("blocks", IntegerArgumentType.integer(128))
                                        .executes(ctx -> {
                                            int size = IntegerArgumentType.getInteger(ctx, "blocks");
                                            AASWorldData data = AASWorldData.get(ctx.getSource().getLevel());
                                            data.mapSizeBlocks = size;
                                            data.setDirty();
                                            PacketHandler.sendToAllClients(ctx.getSource().getLevel(), data);
                                            ctx.getSource().sendSuccess(() -> Component.literal("Map world size set to " + size + " blocks."), true);
                                            return 1;
                                        })))
                )

                // --- ФРАКЦИИ ---
                .then(Commands.literal("fraction")
                        .then(Commands.argument("team", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("blue", "red"), builder))
                                .then(Commands.argument("faction", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of(
                                                "ukraine", "russia", "usa", "nato", "bluefor", "redfor", "clear"
                                        ), builder))
                                        .executes(ctx -> setFaction(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "team"),
                                                StringArgumentType.getString(ctx, "faction")
                                        ))
                                )
                        )
                )
        );
    }

    // === ВСПОМОГАТЕЛЬНЫЙ МЕТОД: ПОДСКАЗКИ ТОЛЬКО ДЛЯ ТЕКУЩЕГО МИРА ===
    private static CompletableFuture<Suggestions> suggestLocalPoints(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        // Берем уровень, в котором находится игрок/командный блок
        ServerLevel level = ctx.getSource().getLevel();
        AASWorldData data = AASWorldData.get(level);

        List<String> pointNames = new ArrayList<>();
        for (AASWorldData.CapturePoint p : data.capturePoints) {
            pointNames.add(p.name);
        }
        return SharedSuggestionProvider.suggest(pointNames, builder);
    }

    // === ЛОГИКА КОМАНД ===

    private static int setGameStart(CommandSourceStack source, boolean active) {
        ServerLevel level = source.getLevel();
        AASWorldData data = AASWorldData.get(level);

        if (active) {
            data.playedBlueSiren = false;
            data.playedRedSiren = false;
            GameLogicEvents.startGameCountdown(level);
            source.sendSuccess(() -> Component.literal("Countdown started in this world!").withStyle(ChatFormatting.GREEN), true);
        } else {
            data.isGameStarted = false;
            GameLogicEvents.cancelCountdown(level);
            data.setDirty();
            syncDataToAll(level, data);
            source.sendSuccess(() -> Component.literal("Game Stopped in this world!").withStyle(ChatFormatting.RED), true);
        }
        return 1;
    }

    private static int setRespawnTime(CommandSourceStack source, int seconds) {
        ServerLevel level = source.getLevel();
        AASWorldData data = AASWorldData.get(level);
        data.respawnTimer = seconds;
        data.setDirty();
        syncDataToAll(level, data);
        source.sendSuccess(() -> Component.literal("Respawn timer set to " + seconds + "s for current world").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int setFaction(CommandSourceStack source, String team, String faction) {
        ServerLevel level = source.getLevel();
        AASWorldData data = AASWorldData.get(level);
        String cleanFaction = faction.toLowerCase();
        String valueToSave = cleanFaction.equals("clear") ? "none" : cleanFaction;
        if (team.equalsIgnoreCase("blue")) data.blueFaction = valueToSave;
        else if (team.equalsIgnoreCase("red")) data.redFaction = valueToSave;
        data.setDirty(); syncDataToAll(level, data); return 1;
    }

    private static int setTickets(CommandSourceStack source, String team, int amount) {
        ServerLevel level = source.getLevel();
        AASWorldData data = AASWorldData.get(level);
        if (team.equalsIgnoreCase("blue")) {
            data.blueTickets = amount;
            if (amount > 50) data.playedBlueSiren = false; // Сбрасываем флаг, чтобы сирена могла пропеть снова
        } else if (team.equalsIgnoreCase("red")) {
            data.redTickets = amount;
            if (amount > 50) data.playedRedSiren = false; // Сбрасываем флаг
        }
        data.setDirty(); syncDataToAll(level, data);
        source.sendSuccess(() -> Component.literal(team.toUpperCase() + " tickets set to " + amount).withStyle(ChatFormatting.GOLD), true); return 1;
    }

    private static int setDeathTickets(CommandSourceStack source, int amount) {
        ServerLevel level = source.getLevel();
        AASWorldData data = AASWorldData.get(level);
        data.deathTicketCost = amount; data.setDirty(); syncDataToAll(level, data);
        source.sendSuccess(() -> Component.literal("Death cost set to " + amount).withStyle(ChatFormatting.GOLD), true); return 1;
    }

    private static int joinTeam(CommandSourceStack source, String teamName, ServerPlayer player) {
        ServerLevel level = source.getLevel();
        AASWorldData data = AASWorldData.get(level);
        PacketSquadAction.leaveCurrentSquad(player, data);
        data.setDirty();
        syncDataToAll(level, data);
        PacketHandler.INSTANCE.send(net.minecraftforge.network.PacketDistributor.ALL.noArg(), new com.example.aas.network.PacketSyncSquads(data.squads));
        Scoreboard scoreboard = source.getServer().getScoreboard();
        String internalTeamName = teamName.equalsIgnoreCase("blue") ? "Blue" : "Red";
        ChatFormatting color = teamName.equalsIgnoreCase("blue") ? ChatFormatting.BLUE : ChatFormatting.RED;
        PlayerTeam team = scoreboard.getPlayerTeam(internalTeamName);
        if (team == null) team = scoreboard.addPlayerTeam(internalTeamName);
        team.setColor(color);
        scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
        source.sendSuccess(() -> Component.literal("Player joined " + internalTeamName).withStyle(color), true);
        return 1;
    }

    private static int addPoint(CommandSourceStack source, String shape, BlockPos pos1, BlockPos pos2, String name, int bp, int rp, int time, int penalty, int captureDeduct, int lockMinutes) {
        ServerLevel level = source.getLevel();
        AASWorldData data = AASWorldData.get(level);

        AABB area;
        if (shape.equalsIgnoreCase("cylinder")) {
            // Для цилиндра: pos1 - центр основания, pos2 определяет радиус и высоту
            double radius = Math.sqrt(pos1.distSqr(new BlockPos(pos2.getX(), pos1.getY(), pos2.getZ())));
            double minY = Math.min(pos1.getY(), pos2.getY());
            double maxY = Math.max(pos1.getY(), pos2.getY()) + 1;
            // Мы сохраняем в AABB границы цилиндра для оптимизации поиска сущностей
            area = new AABB(pos1.getX() - radius, minY, pos1.getZ() - radius, pos1.getX() + radius, maxY, pos1.getZ() + radius);
        } else {
            double minX = Math.min(pos1.getX(), pos2.getX());
            double minY = Math.min(pos1.getY(), pos2.getY());
            double minZ = Math.min(pos1.getZ(), pos2.getZ());
            double maxX = Math.max(pos1.getX(), pos2.getX()) + 1;
            double maxY = Math.max(pos1.getY(), pos2.getY()) + 1;
            double maxZ = Math.max(pos1.getZ(), pos2.getZ()) + 1;
            area = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        }

        data.capturePoints.add(new AASWorldData.CapturePoint(name, area, bp, rp, time, penalty, captureDeduct, shape.toUpperCase(), lockMinutes));
        data.setDirty();
        source.sendSuccess(() -> Component.literal("Point '" + name + "' (" + shape + ") added! Lock: " + lockMinutes + " min."), true);
        PacketHandler.sendToAllClients(level, data);
        return 1;
    }

    // === УДАЛЕНИЕ ТОЧКИ (Ищет ТОЛЬКО в текущем мире) ===
    private static int removePoint(CommandSourceStack source, String name) {
        ServerLevel level = source.getLevel();
        AASWorldData data = AASWorldData.get(level);

        boolean removed = data.capturePoints.removeIf(p -> p.name.equals(name));

        if (removed) {
            data.setDirty();
            syncDataToAll(level, data);
            source.sendSuccess(() -> Component.literal("Point '" + name + "' removed from " + level.dimension().location()).withStyle(ChatFormatting.RED), true);
            return 1;
        }

        source.sendFailure(Component.literal("Point '" + name + "' not found in THIS world!"));
        return 0;
    }

    // === СБРОС КОНКРЕТНОЙ ТОЧКИ (Ищет ТОЛЬКО в текущем мире) ===
    private static int clearSpecificPoint(CommandSourceStack source, String name) {
        ServerLevel level = source.getLevel();
        AASWorldData data = AASWorldData.get(level);

        for (AASWorldData.CapturePoint point : data.capturePoints) {
            if (point.name.equals(name)) {
                // Сброс параметров
                point.owner = "NEUTRAL";
                point.progress = 0.0f;
                point.capturingTeam = "NONE";

                data.setDirty();
                syncDataToAll(level, data);

                source.sendSuccess(() -> Component.literal("Point '" + name + "' reset to NEUTRAL in this world!").withStyle(ChatFormatting.YELLOW), true);
                return 1;
            }
        }

        source.sendFailure(Component.literal("Point '" + name + "' not found in THIS world!"));
        return 0;
    }

    private static int setTeamSpawn(CommandSourceStack source, String teamName, BlockPos pos) {
        ServerLevel level = source.getLevel();
        AASWorldData data = AASWorldData.get(level);
        String currentDim = level.dimension().location().toString();
        if (teamName.equalsIgnoreCase("blue")) data.blueSpawns.put(currentDim, pos);
        else if (teamName.equalsIgnoreCase("red")) data.redSpawns.put(currentDim, pos);
        else if (teamName.equalsIgnoreCase("none")) data.neutralSpawns.put(currentDim, pos);
        data.setDirty(); syncDataToAll(level, data);
        source.sendSuccess(() -> Component.literal("Spawn set for this dimension.").withStyle(ChatFormatting.GREEN), true); return 1;
    }

    private static void syncDataToAll(ServerLevel level, AASWorldData data) {
        PacketHandler.sendToAllClients(level, data);
    }
    // === МЕТОД ДЛЯ ОЧИСТКИ ОТРЯДОВ (Clear Squads) ===
    private static int clearSquads(CommandSourceStack source, String teamName) {
        ServerLevel level = source.getLevel();
        AASWorldData data = AASWorldData.get(level);
        String targetTeam = teamName.toUpperCase(); // "BLUE" или "RED"

        // 1. Находим и удаляем теги у игроков, которые сейчас в этих отрядах
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            if (player.getPersistentData().contains("AAS_SquadID")) {
                int pSquadId = player.getPersistentData().getInt("AAS_SquadID");

                // Проверяем, принадлежит ли этот ID отряду целевой команды
                boolean belongsToTeam = data.squads.stream()
                        .anyMatch(s -> s.id == pSquadId && s.team.equalsIgnoreCase(targetTeam));

                if (belongsToTeam) {
                    player.getPersistentData().remove("AAS_SquadID");
                    player.getPersistentData().remove("AAS_IsSquadLeader");
                }
            }
        }

        // 2. Удаляем сами отряды из списка данных мира
        boolean removed = data.squads.removeIf(squad -> squad.team.equalsIgnoreCase(targetTeam));

        if (removed) {
            data.setDirty();

            // 3. Синхронизируем изменения клиентам (чтобы обновилось GUI)
            PacketHandler.INSTANCE.send(
                    net.minecraftforge.network.PacketDistributor.DIMENSION.with(level::dimension),
                    new com.example.aas.network.PacketSyncSquads(data.squads)
            );

            source.sendSuccess(() -> Component.literal("Cleared all " + targetTeam + " squads.").withStyle(ChatFormatting.GREEN), true);
        } else {
            source.sendFailure(Component.literal("No squads found for team " + targetTeam));
        }
        return 1;
    }
}