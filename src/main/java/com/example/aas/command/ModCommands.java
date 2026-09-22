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
import com.example.aas.config.AASConfig;

public class ModCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("aas")
                .requires(s -> s.hasPermission(2))

                // --- УПРАВЛЕНИЕ ИГРОЙ ---
                .then(Commands.literal("gamestart")
                        .then(Commands.argument("active", BoolArgumentType.bool())
                                .executes(ctx -> setGameStart(ctx.getSource(), BoolArgumentType.getBool(ctx, "active")))))
                .then(Commands.literal("gamemode")
                        .then(Commands.argument("mode", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("aas", "invasion"), builder))
                                .executes(ctx -> setGameMode(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "mode")
                                ))
                        )
                )
                .then(Commands.literal("invasiondefend")
                        .then(Commands.argument("team", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("blue", "red"), builder))
                                .executes(ctx -> setInvasionDefender(ctx.getSource(), StringArgumentType.getString(ctx, "team")))
                        )
                )
                .then(Commands.literal("deathtimer")
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(0))
                                .executes(ctx -> setRespawnTime(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "seconds")))))

                .then(Commands.literal("deathtickets")
                        .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                .executes(ctx -> setDeathTickets(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "amount")))))
                // === ЛИМИТ ХАБОВ ===
                .then(Commands.literal("maxhub")
                        .then(Commands.argument("team", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("blue", "red"), builder))
                                .then(Commands.argument("amount", IntegerArgumentType.integer(0))
                                        .executes(ctx -> setMaxHubs(ctx.getSource(), StringArgumentType.getString(ctx, "team"), IntegerArgumentType.getInteger(ctx, "amount")))
                                )
                        )
                )
                // === PVP TOGGLE ===
                .then(Commands.literal("pvp")
                        .then(Commands.literal("on")
                                .executes(ctx -> setPvPState(ctx.getSource(), true))
                        )
                        .then(Commands.literal("off")
                                .executes(ctx -> setPvPState(ctx.getSource(), false))
                        )
                )
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
                .then(Commands.literal("mainzone")
                        .then(Commands.argument("team", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("blue", "red"), builder))
                                .then(Commands.argument("shape", StringArgumentType.word())
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("cube", "cylinder"), builder))
                                        .then(Commands.argument("pos1", BlockPosArgument.blockPos())
                                                .then(Commands.argument("pos2", BlockPosArgument.blockPos())
                                                        .executes(ctx -> addMainZone(ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "team"),
                                                                StringArgumentType.getString(ctx, "shape"),
                                                                // ИЗМЕНЕНО ТУТ: getSpawnablePos вместо getLoadedBlockPos
                                                                BlockPosArgument.getSpawnablePos(ctx, "pos1"),
                                                                BlockPosArgument.getSpawnablePos(ctx, "pos2")
                                                        ))
                                                )
                                        )
                                )
                        )
                )
                .then(Commands.literal("addcenterlobbyzone")
                        .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                .executes(ctx -> addCenterLobbyZone(ctx.getSource(), BlockPosArgument.getSpawnablePos(ctx, "pos")))
                        )
                )
                .then(Commands.literal("addlobbyzone")
                        .then(Commands.argument("shape", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("cube", "cylinder"), builder))
                                .then(Commands.argument("pos1", BlockPosArgument.blockPos())
                                        .then(Commands.argument("pos2", BlockPosArgument.blockPos())
                                                .executes(ctx -> addLobbyZone(ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "shape"),
                                                        BlockPosArgument.getSpawnablePos(ctx, "pos1"),
                                                        BlockPosArgument.getSpawnablePos(ctx, "pos2")
                                                ))
                                        )
                                )
                        )
                )
                .then(Commands.literal("removelobbyzone")
                        .executes(ctx -> removeLobbyZone(ctx.getSource()))
                )
                .then(Commands.literal("removemainzone")
                        .then(Commands.argument("team", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("blue", "red"), builder))
                                .executes(ctx -> removeMainZone(ctx.getSource(), StringArgumentType.getString(ctx, "team")))
                        )
                )
                .then(Commands.literal("removemainzone")
                        .then(Commands.argument("team", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("blue", "red"), builder))
                                .executes(ctx -> removeMainZone(ctx.getSource(), StringArgumentType.getString(ctx, "team")))
                        )
                )
                .then(Commands.literal("teamjoin")
                        .then(Commands.argument("team", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("blue", "red"), builder))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> joinTeam(ctx.getSource(), StringArgumentType.getString(ctx, "team"), EntityArgument.getPlayer(ctx, "player"))))))

                .then(Commands.literal("editpoint")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .suggests((ctx, builder) -> suggestLocalPoints(ctx, builder)) // Автоподстановка
                                .executes(ctx -> openEditPointGui(ctx.getSource(), StringArgumentType.getString(ctx, "name")))
                        )
                )

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
                                                                                                        .then(Commands.argument("gainNeut", IntegerArgumentType.integer(0))
                                                                                                                .then(Commands.argument("gainCap", IntegerArgumentType.integer(0))
                                                                                                                        .executes(ctx -> addPoint(ctx.getSource(),
                                                                                                                                StringArgumentType.getString(ctx, "shape"),
                                                                                                                                BlockPosArgument.getSpawnablePos(ctx, "pos1"),
                                                                                                                                BlockPosArgument.getSpawnablePos(ctx, "pos2"),
                                                                                                                                StringArgumentType.getString(ctx, "name"),
                                                                                                                                IntegerArgumentType.getInteger(ctx, "bluePriority"),
                                                                                                                                IntegerArgumentType.getInteger(ctx, "redPriority"),
                                                                                                                                IntegerArgumentType.getInteger(ctx, "timeMin"),
                                                                                                                                IntegerArgumentType.getInteger(ctx, "penalty"),
                                                                                                                                IntegerArgumentType.getInteger(ctx, "captureDeduct"),
                                                                                                                                IntegerArgumentType.getInteger(ctx, "lockMinutes"),
                                                                                                                                IntegerArgumentType.getInteger(ctx, "gainNeut"),
                                                                                                                                IntegerArgumentType.getInteger(ctx, "gainCap")
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
                        )
                )
                .then(Commands.literal("warn")
                        .then(Commands.argument("target", EntityArgument.player()) // Авто-подсказка онлайна
                                .then(Commands.argument("message", StringArgumentType.greedyString()) // Читает весь остальной текст
                                        .executes(ctx -> issueWarning(
                                                ctx.getSource(),
                                                EntityArgument.getPlayer(ctx, "target"),
                                                StringArgumentType.getString(ctx, "message")
                                        ))
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
                // === СИСТЕМА ОЧКОВ ===
                .then(Commands.literal("stats")
                        // Просмотр своей статы (уже было)
                        .executes(ctx -> showStats(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        // Просмотр топа
                        .then(Commands.literal("top")
                                .then(Commands.literal("all")
                                        .executes(ctx -> showTopStats(ctx.getSource(), "ALL")))
                                .then(Commands.literal("team")
                                        .then(Commands.argument("teamName", StringArgumentType.word())
                                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("blue", "red"), builder))
                                                .executes(ctx -> showTopStats(ctx.getSource(), StringArgumentType.getString(ctx, "teamName").toUpperCase()))
                                        )
                                )
                        )
                        // Просмотр статы конкретного игрока (уже было)
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> showStats(ctx.getSource(), EntityArgument.getPlayer(ctx, "target")))
                        )
                )
                // === СБРОС ТОЧКИ (Только текущий мир) ===
                .then(Commands.literal("pointclear")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .suggests((ctx, builder) -> suggestLocalPoints(ctx, builder)) // Локальные подсказки
                                .executes(ctx -> clearSpecificPoint(ctx.getSource(), StringArgumentType.getString(ctx, "name")))
                        )
                )

                // === ПРИНУДИТЕЛЬНЫЙ ЗАХВАТ ТОЧКИ ===
                .then(Commands.literal("pointcapture")
                        .then(Commands.argument("team", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("blue", "red"), builder))
                                .then(Commands.argument("name", StringArgumentType.greedyString())
                                        .suggests((ctx, builder) -> suggestLocalPoints(ctx, builder))
                                        .executes(ctx -> forceCapturePoint(ctx.getSource(), StringArgumentType.getString(ctx, "team"), StringArgumentType.getString(ctx, "name")))
                                )
                        )
                )

                // --- СПАВНЫ ---
                .then(Commands.literal("teamspawn")
                        .then(Commands.argument("team", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(List.of("blue", "red", "none"), builder))
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        // ИЗМЕНЕНО ТУТ: getSpawnablePos
                                        .executes(ctx -> setTeamSpawn(ctx.getSource(), StringArgumentType.getString(ctx, "team"), BlockPosArgument.getSpawnablePos(ctx, "pos")))
                                )
                        )
                )
                .then(Commands.literal("map")
                        .then(Commands.literal("setimage")
                                .then(Commands.argument("imagename", StringArgumentType.word())
                                        // Подсказки от map1 до map12
                                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                                List.of("map1", "map2", "map3", "map4", "map5", "map6", "map7", "map8", "map9", "map10", "map11", "map12"), builder))
                                        .executes(ctx -> {
                                            String imgName = StringArgumentType.getString(ctx, "imagename");
                                            ServerLevel level = ctx.getSource().getLevel();
                                            AASWorldData data = AASWorldData.get(level);

                                            data.currentMapImage = imgName;
                                            data.setDirty();
                                            PacketHandler.sendToAllClients(level, data);

                                            ctx.getSource().sendSuccess(() -> Component.literal("Map image set to: " + imgName + ".png"), true);
                                            return 1;
                                        })
                                )
                        )
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
                                                "ukraine", "russia", "usa", "nato", "bluefor", "redfor", "insurgency", "pmc", "germany", "militia", "clear"
                                        ), builder))
                                        .executes(ctx -> setFaction(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "team"),
                                                StringArgumentType.getString(ctx, "faction")
                                        ))
                                )
                        )
                )
                .then(Commands.literal("votestart")
                        .then(Commands.argument("active", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    ServerLevel level = ctx.getSource().getLevel();
                                    AASWorldData data = AASWorldData.get(level);
                                    boolean active = BoolArgumentType.getBool(ctx, "active");

                                    data.voteActive = active;

                                    if (active) {
                                        // Инициализация при старте голосования
                                        data.voteTimer = AASConfig.VOTE_AUTO_START_TIME.get() * 60;
                                        data.votes.clear();

                                        // Сбрасываем флаги, чтобы сообщения о готовности команд могли сработать снова
                                        data.blueReady = false;
                                        data.redReady = false;
                                    }

                                    data.setDirty();
                                    PacketHandler.sendToAllClients(level, data);

                                    String status = active ? "started" : "stopped";
                                    ctx.getSource().sendSuccess(() -> Component.literal("Voting process " + status), true);

                                    return 1;
                                })))
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
            for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
                p.getPersistentData().putInt("AAS_Stats_TeamPoints", 0);
                p.getPersistentData().putInt("AAS_Stats_SquadPoints", 0);
                p.getPersistentData().putInt("AAS_Stats_Kills", 0);   // НОВОЕ
                p.getPersistentData().putInt("AAS_Stats_Deaths", 0);  // НОВОЕ
            }
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

    private static int addPoint(CommandSourceStack source, String shape, BlockPos pos1, BlockPos pos2, String name, int bp, int rp, int time, int penalty, int captureDeduct, int lockMinutes, int gainNeut, int gainCap) {
        ServerLevel level = source.getLevel();
        AASWorldData data = AASWorldData.get(level);

        AABB area;
        if (shape.equalsIgnoreCase("cylinder")) {
            double radius = Math.sqrt(pos1.distSqr(new BlockPos(pos2.getX(), pos1.getY(), pos2.getZ())));
            double minY = Math.min(pos1.getY(), pos2.getY());
            double maxY = Math.max(pos1.getY(), pos2.getY()) + 1;
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

        String upperShape = shape.toUpperCase();

        // НОВОЕ: если точка с таким именем уже есть и характеристики совпадают —
        // не создаём новую точку, а добавляем эту зону как ещё одну зону существующей точки.
        // Так можно делать сколько угодно раз подряд.
        for (AASWorldData.CapturePoint existing : data.capturePoints) {
            if (existing.name.equalsIgnoreCase(name)) {
                if (existing.sameCharacteristics(bp, rp, time, penalty, captureDeduct, upperShape, lockMinutes, gainNeut, gainCap)) {
                    existing.addLinkedArea(area);
                    data.setDirty();
                    int totalZones = existing.getAllAreas().size();
                    source.sendSuccess(() -> Component.literal("Zone added to point '" + name + "'! It now has " + totalZones + " linked zone(s).").withStyle(ChatFormatting.GREEN), true);
                    PacketHandler.sendToAllClients(level, data);
                    return 1;
                } else {
                    source.sendFailure(Component.literal("Point '" + name + "' already exists with different settings. Use identical settings to link a new zone to it, or choose another name."));
                    return 0;
                }
            }
        }

        data.capturePoints.add(new AASWorldData.CapturePoint(name, area, bp, rp, time, penalty, captureDeduct, upperShape, lockMinutes, gainNeut, gainCap));
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

    // === ПРИНУДИТЕЛЬНЫЙ ЗАХВАТ КОНКРЕТНОЙ ТОЧКИ ===
    private static int forceCapturePoint(CommandSourceStack source, String teamInput, String name) {
        ServerLevel level = source.getLevel();
        AASWorldData data = AASWorldData.get(level);
        String targetTeam = teamInput.toUpperCase();

        if (!targetTeam.equals("BLUE") && !targetTeam.equals("RED")) {
            source.sendFailure(Component.literal("Invalid team! Please use 'blue' or 'red'."));
            return 0;
        }

        for (AASWorldData.CapturePoint point : data.capturePoints) {
            if (point.name.equals(name)) {
                point.owner = targetTeam;
                point.progress = 1.0f; // 100% прогресса
                point.capturingTeam = "NONE";

                data.setDirty();
                syncDataToAll(level, data); // Обновляем у всех игроков

                ChatFormatting color = targetTeam.equals("BLUE") ? ChatFormatting.BLUE : ChatFormatting.RED;

                // Сообщение админу
                source.sendSuccess(() -> Component.literal("Point '" + name + "' forcefully captured by " + targetTeam + "!").withStyle(color), true);

                // Уведомление в глобальный чат о захвате
                level.getServer().getPlayerList().broadcastSystemMessage(
                        Component.literal("[ADMIN] Point " + name + " forcefully captured by " + targetTeam).withStyle(color), false
                );

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
    private static int issueWarning(CommandSourceStack source, ServerPlayer target, String message) {
        // 1. Настройка таймингов: 10 тиков (0.5с) появление, 140 тиков (7с) на экране, 20 тиков (1с) затухание
        target.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket(10, 140, 20));

        // 2. Сначала отправляем подзаголовок (Сам текст сообщения - желтым цветом)
        target.connection.send(new net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket(
                Component.literal(message).withStyle(ChatFormatting.YELLOW)
        ));

        // 3. Отправляем главный заголовок (Красный, жирный)
        target.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(
                Component.literal("!WARNING!").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
        ));

        // 4. Проигрываем неприятный звук удара наковальни (чтобы игрок точно обратил внимание)
        target.playNotifySound(net.minecraft.sounds.SoundEvents.ANVIL_LAND, net.minecraft.sounds.SoundSource.MASTER, 1.0f, 0.8f);

        // 5. Дублируем сообщение в чат игрока, чтобы оно осталось в истории (если текст длинный и он не успел дочитать)
        target.sendSystemMessage(Component.literal("[ADMIN WARN] " + message).withStyle(ChatFormatting.RED, ChatFormatting.BOLD));

        // 6. Подтверждаем админу, что предупреждение отправлено
        source.sendSuccess(() -> Component.literal("Successfully warned " + target.getScoreboardName() + "!")
                .withStyle(ChatFormatting.GREEN), true);

        return 1;
    }
    private static void syncDataToAll(ServerLevel level, AASWorldData data) {
        PacketHandler.sendToAllClients(level, data);
    }
    private static int addMainZone(CommandSourceStack source, String team, String shape, BlockPos pos1, BlockPos pos2) {
        ServerLevel level = source.getLevel();
        AASWorldData data = AASWorldData.get(level);

        AABB area;
        if (shape.equalsIgnoreCase("cylinder")) {
            double radius = Math.sqrt(pos1.distSqr(new BlockPos(pos2.getX(), pos1.getY(), pos2.getZ())));
            double minY = Math.min(pos1.getY(), pos2.getY());
            double maxY = Math.max(pos1.getY(), pos2.getY()) + 1;
            area = new AABB(pos1.getX() - radius, minY, pos1.getZ() - radius, pos1.getX() + radius, maxY, pos1.getZ() + radius);
        } else {
            area = new AABB(Math.min(pos1.getX(), pos2.getX()), Math.min(pos1.getY(), pos2.getY()), Math.min(pos1.getZ(), pos2.getZ()),
                    Math.max(pos1.getX(), pos2.getX()) + 1, Math.max(pos1.getY(), pos2.getY()) + 1, Math.max(pos1.getZ(), pos2.getZ()) + 1);
        }

        // Удаляем старую зону этой команды (если была)
        data.mainZones.removeIf(z -> z.team.equalsIgnoreCase(team));

        data.mainZones.add(new AASWorldData.MainProtectionZone(team.toUpperCase(), shape.toUpperCase(), area));
        data.setDirty();

        source.sendSuccess(() -> Component.literal(team.toUpperCase() + " Main Protection Zone successfully added!").withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int removeMainZone(CommandSourceStack source, String team) {
        ServerLevel level = source.getLevel();
        AASWorldData data = AASWorldData.get(level);

        // Пытаемся удалить зону команды
        boolean removed = data.mainZones.removeIf(z -> z.team.equalsIgnoreCase(team));

        if (removed) {
            data.setDirty(); // Сохраняем мир
            source.sendSuccess(() -> Component.literal(team.toUpperCase() + " Main Protection Zone removed!").withStyle(ChatFormatting.GREEN), true);
        } else {
            source.sendFailure(Component.literal("No protection zone found for team: " + team.toUpperCase()));
        }
        return 1;
    }
    // === МЕТОД ДЛЯ ОЧИСТКИ ОТРЯДОВ (Clear Squads) ===
    private static int clearSquads(CommandSourceStack source, String teamName) {
        ServerLevel level = source.getLevel();
        AASWorldData data = AASWorldData.get(level);
        String targetTeam = teamName.toUpperCase();

        // 1. Сбрасываем теги у игроков
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            if (player.getPersistentData().contains("AAS_SquadID")) {
                boolean belongsToTeam = data.squads.stream()
                        .anyMatch(s -> s.id == player.getPersistentData().getInt("AAS_SquadID") && s.team.equalsIgnoreCase(targetTeam));

                if (belongsToTeam) {
                    player.getPersistentData().remove("AAS_SquadID");
                    player.getPersistentData().remove("AAS_IsSquadLeader");
                }
            }
        }

        // 2. Удаляем отряды
        data.squads.removeIf(squad -> squad.team.equalsIgnoreCase(targetTeam));

        // === 3. СБРАСЫВАЕМ CMD (Добавь эти строки) ===
        if (targetTeam.equals("BLUE")) {
            data.blueCMDId = -1;
        } else if (targetTeam.equals("RED")) {
            data.redCMDId = -1;
        }
        // ============================================

        data.setDirty();
        PacketHandler.sendToAllClients(level, data);
        // НОВОЕ: обновляем список отрядов у ВСЕХ игроков
        PacketHandler.INSTANCE.send(net.minecraftforge.network.PacketDistributor.ALL.noArg(),
                new com.example.aas.network.PacketSyncSquads(data.squads));

        source.sendSuccess(() -> Component.literal("Cleared squads and CMD for " + targetTeam).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
    private static int openEditPointGui(CommandSourceStack source, String name) {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;

        ServerLevel level = source.getLevel();
        AASWorldData data = AASWorldData.get(level);

        for (AASWorldData.CapturePoint p : data.capturePoints) {
            if (p.name.equals(name)) {

                // Восстанавливаем оригинальные координаты pos1 и pos2 из готового хитбокса AABB
                int p1x, p1y, p1z, p2x, p2y, p2z;
                if (p.shapeType.equals("CYLINDER")) {
                    p1x = (int) ((p.area.minX + p.area.maxX) / 2);
                    p1y = (int) p.area.minY;
                    p1z = (int) ((p.area.minZ + p.area.maxZ) / 2);
                    p2x = (int) p.area.maxX;
                    p2y = (int) p.area.maxY - 1;
                    p2z = p1z;
                } else {
                    p1x = (int) p.area.minX;
                    p1y = (int) p.area.minY;
                    p1z = (int) p.area.minZ;
                    p2x = (int) p.area.maxX - 1;
                    p2y = (int) p.area.maxY - 1;
                    p2z = (int) p.area.maxZ - 1;
                }

                PacketHandler.INSTANCE.send(
                        net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                        new com.example.aas.network.PacketOpenPointEditor(
                                p.name, p.shapeType,
                                p1x, p1y, p1z, p2x, p2y, p2z,
                                p.bluePriority, p.redPriority,
                                p.captureTimeMinutes, p.ticketPenalty,
                                p.captureDeduction, p.lockDurationMinutes,
                                p.ticketGainNeutralize, p.ticketGainCapture
                        )
                );
                return 1;
            }
        }
        source.sendFailure(Component.literal("Point '" + name + "' not found!"));
        return 0;
    }
    private static int setMaxHubs(CommandSourceStack source, String team, int amount) {
        ServerLevel level = source.getLevel();
        AASWorldData data = AASWorldData.get(level);

        if (team.equalsIgnoreCase("blue")) {
            data.maxBlueHubs = amount;
        } else if (team.equalsIgnoreCase("red")) {
            data.maxRedHubs = amount;
        } else {
            source.sendFailure(Component.literal("Invalid team! Please use 'blue' or 'red'."));
            return 0;
        }

        data.setDirty();
        // ВАЖНО: Синхронизировать с клиентом не обязательно, так как установка Хаба проверяется только на сервере
        source.sendSuccess(() -> Component.literal("Max FOBs for " + team.toUpperCase() + " set to " + amount).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
    private static int setPvPState(CommandSourceStack source, boolean state) {
        ServerLevel level = source.getLevel();
        AASWorldData data = AASWorldData.get(level);

        data.pvpEnabled = state;
        data.setDirty(); // Сохраняем изменения в мир

        String status = state ? "ENABLED" : "DISABLED";
        ChatFormatting color = state ? ChatFormatting.GREEN : ChatFormatting.YELLOW;
        Component msg = Component.literal("[AAS] PvP between players is now " + status + " in this world.")
                .withStyle(color);

        // Отправляем сообщение ТОЛЬКО модераторам (уровень прав 2+)
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            if (player.hasPermissions(2)) {
                player.sendSystemMessage(msg);
            }
        }

        // Если команду ввела консоль сервера, нужно отправить ответ и ей
        if (!(source.getEntity() instanceof ServerPlayer)) {
            source.sendSuccess(() -> msg, true);
        }

        return 1;
    }
    // === СМЕНА ИГРОВОГО РЕЖИМА ===
    private static int setGameMode(CommandSourceStack source, String mode) {
        ServerLevel level = source.getLevel();
        AASWorldData data = AASWorldData.get(level);

        String newMode = mode.toUpperCase();

        if (!newMode.equals("AAS") && !newMode.equals("INVASION")) {
            source.sendFailure(Component.literal("Invalid game mode! Use 'aas' or 'invasion'."));
            return 0;
        }

        data.gameMode = newMode;
        data.setDirty();
        syncDataToAll(level, data); // Синхронизируем изменения с игроками

        source.sendSuccess(() -> Component.literal("Game Mode successfully set to: " + newMode).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }
    private static int setInvasionDefender(CommandSourceStack source, String teamInput) {
        ServerLevel level = source.getLevel();
        AASWorldData data = AASWorldData.get(level);
        String team = teamInput.toUpperCase();

        if (!team.equals("BLUE") && !team.equals("RED")) {
            source.sendFailure(Component.literal("Use 'blue' or 'red'!"));
            return 0;
        }

        data.invasionDefender = team;
        data.setDirty();

        // Синхронизируем со всеми
        PacketHandler.sendToAllClients(level, data);

        String attacker = team.equals("BLUE") ? "RED" : "BLUE";
        source.sendSuccess(() -> Component.literal("Invasion Setup: ")
                .append(Component.literal(team).withStyle(team.equals("BLUE") ? ChatFormatting.BLUE : ChatFormatting.RED))
                .append(" is DEFENDING, ")
                .append(Component.literal(attacker).withStyle(attacker.equals("BLUE") ? ChatFormatting.BLUE : ChatFormatting.RED))
                .append(" is ATTACKING."), true);

        return 1;
    }
    private static int showStats(CommandSourceStack source, ServerPlayer target) {
        int tp = target.getPersistentData().getInt("AAS_Stats_TeamPoints");
        int sp = target.getPersistentData().getInt("AAS_Stats_SquadPoints");
        int total = tp + sp;

        source.sendSuccess(() -> Component.literal("=== STATS: " + target.getScoreboardName() + " ===").withStyle(ChatFormatting.GOLD), false);
        source.sendSuccess(() -> Component.literal("Team Points (TP): ").withStyle(ChatFormatting.AQUA).append(Component.literal(String.valueOf(tp)).withStyle(ChatFormatting.WHITE)), false);
        source.sendSuccess(() -> Component.literal("Squad Points (SP): ").withStyle(ChatFormatting.GREEN).append(Component.literal(String.valueOf(sp)).withStyle(ChatFormatting.WHITE)), false);
        source.sendSuccess(() -> Component.literal("Total Score: ").withStyle(ChatFormatting.YELLOW).append(Component.literal(String.valueOf(total)).withStyle(ChatFormatting.WHITE)), false);

        return 1;
    }
    private static int showTopStats(CommandSourceStack source, String scope) {
        // Получаем всех игроков на сервере
        List<ServerPlayer> players = source.getServer().getPlayerList().getPlayers();

        // Вспомогательный класс для сортировки
        class PlayerScore {
            final String name;
            final int totalPoints;
            final String team;

            PlayerScore(ServerPlayer p) {
                this.name = p.getScoreboardName();
                int tp = p.getPersistentData().getInt("AAS_Stats_TeamPoints");
                int sp = p.getPersistentData().getInt("AAS_Stats_SquadPoints");
                this.totalPoints = tp + sp;
                this.team = (p.getTeam() != null) ? p.getTeam().getName().toUpperCase() : "NEUTRAL";
            }
        }

        // Собираем список подходящих игроков
        List<PlayerScore> scores = new ArrayList<>();
        for (ServerPlayer p : players) {
            PlayerScore ps = new PlayerScore(p);

            // Фильтр 1: Очков больше 0
            if (ps.totalPoints <= 0) continue;

            // Фильтр 2: По команде (если не ALL)
            if (!scope.equals("ALL")) {
                if (!ps.team.contains(scope)) continue; // Проверяем вхождение строки (BLUE/RED)
            }

            scores.add(ps);
        }

        // Сортировка от большего к меньшему
        scores.sort((a, b) -> Integer.compare(b.totalPoints, a.totalPoints));

        // Вывод заголовка
        String header = scope.equals("ALL") ? "--- GLOBAL TOP PLAYERS ---" : "--- TOP PLAYERS: " + scope + " ---";
        source.sendSuccess(() -> Component.literal(header).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD), false);

        if (scores.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No players with points found in this category.").withStyle(ChatFormatting.GRAY), false);
            return 1;
        }

        // Вывод списка
        for (int i = 0; i < scores.size(); i++) {
            PlayerScore ps = scores.get(i);
            int rank = i + 1;

            ChatFormatting teamColor = ps.team.contains("BLUE") ? ChatFormatting.BLUE :
                    (ps.team.contains("RED") ? ChatFormatting.RED : ChatFormatting.GRAY);

            // Формат: "1. NickName - 150 pts"
            source.sendSuccess(() -> Component.literal(rank + ". ")
                    .withStyle(ChatFormatting.YELLOW)
                    .append(Component.literal(ps.name).withStyle(teamColor))
                    .append(Component.literal(" - " + ps.totalPoints + " pts").withStyle(ChatFormatting.WHITE)), false);
        }

        return 1;
    }
    private static int addCenterLobbyZone(CommandSourceStack source, BlockPos pos) {
        ServerLevel level = source.getLevel();
        AASWorldData data = AASWorldData.get(level);

        data.lobbyCenter = pos;
        data.setDirty();

        source.sendSuccess(() -> Component.literal("Lobby teleport point set to " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ())
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int addLobbyZone(CommandSourceStack source, String shape, BlockPos pos1, BlockPos pos2) {
        ServerLevel level = source.getLevel();
        AASWorldData data = AASWorldData.get(level);

        AABB area;
        if (shape.equalsIgnoreCase("cylinder")) {
            double radius = Math.sqrt(pos1.distSqr(new BlockPos(pos2.getX(), pos1.getY(), pos2.getZ())));
            double minY = Math.min(pos1.getY(), pos2.getY());
            double maxY = Math.max(pos1.getY(), pos2.getY()) + 1;
            area = new AABB(pos1.getX() - radius, minY, pos1.getZ() - radius, pos1.getX() + radius, maxY, pos1.getZ() + radius);
        } else {
            area = new AABB(Math.min(pos1.getX(), pos2.getX()), Math.min(pos1.getY(), pos2.getY()), Math.min(pos1.getZ(), pos2.getZ()),
                    Math.max(pos1.getX(), pos2.getX()) + 1, Math.max(pos1.getY(), pos2.getY()) + 1, Math.max(pos1.getZ(), pos2.getZ()) + 1);
        }

        data.lobbyZones.add(new AASWorldData.LobbyZone(shape.toUpperCase(), area));
        data.setDirty();

        source.sendSuccess(() -> Component.literal("Lobby zone added! (" + data.lobbyZones.size() + " total)")
                .withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int removeLobbyZone(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        AASWorldData data = AASWorldData.get(level);

        boolean removed = !data.lobbyZones.isEmpty();
        data.lobbyZones.clear();

        if (removed) {
            data.setDirty();
            source.sendSuccess(() -> Component.literal("Lobby zone(s) removed!").withStyle(ChatFormatting.GREEN), true);
        } else {
            source.sendFailure(Component.literal("No lobby zone found."));
        }
        return 1;
    }
}