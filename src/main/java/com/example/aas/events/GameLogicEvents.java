package com.example.aas.events;

import com.example.aas.network.*;
import com.example.aas.world.AASWorldData;
import net.minecraft.server.level.ServerLevel;
import com.example.aas.block.RallyPointBlock;
import com.example.aas.item.ModItems;
import com.example.aas.block.RallyPointBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;
import com.example.aas.config.AASConfig;
import java.util.*;
import com.example.aas.sound.ModSounds; // Добавьте эту строку
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

@Mod.EventBusSubscriber(modid = "aas", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class GameLogicEvents {

    public static final Map<UUID, String> pendingRespawnLocations = new HashMap<>();
    public static final Map<UUID, String> pendingTeams = new HashMap<>();
    public static final Map<UUID, Map<Integer, CompoundTag>> PERSISTENT_NBT_STORAGE = new HashMap<>();
    private static final Map<String, Boolean> lastBlueBlockedMap = new HashMap<>();
    private static final Map<String, Boolean> lastRedBlockedMap = new HashMap<>();

    public static void startGameCountdown(ServerLevel level) {
        AASWorldData data = AASWorldData.get(level);
        data.countdownTicks = 100;
        data.countdownActive = true;
        data.setDirty();
    }

    public static void cancelCountdown(ServerLevel level) {
        AASWorldData data = AASWorldData.get(level);
        data.countdownActive = false;
        data.countdownTicks = 0;
        data.setDirty();
    }

    // Пустой метод для совместимости
    public static void cancelCountdown() {}

    @SubscribeEvent
    public static void onItemToss(ItemTossEvent event) {
        if (event.getPlayer() == null || event.getPlayer().level().isClientSide) return;

        ServerPlayer player = (ServerPlayer) event.getPlayer();
        if (player.isCreative()) return;

        AASWorldData data = AASWorldData.get(player.serverLevel());

        // Работает только если игра запущена
        if (data.isGameStarted) {
            boolean preventAll = false;
            try {
                preventAll = AASConfig.PREVENT_ALL_ITEM_DROPS.get();
            } catch (Exception ignored) {}

            if (preventAll) {
                event.setCanceled(true);
                // Важно: возвращаем предмет в инвентарь, чтобы он не пропал
                player.getInventory().add(event.getEntity().getItem());
                player.displayClientMessage(Component.literal("Item dropping is DISABLED during the game!")
                        .withStyle(ChatFormatting.RED), true);
            }
            else if (isHeavyItem(event.getEntity().getItem().getItem())) {
                // Если общая блокировка выключена, проверяем только тяжелые предметы
                event.setCanceled(true);
                player.getInventory().add(event.getEntity().getItem());
                player.displayClientMessage(Component.literal("Cannot drop heavy ammo during combat!")
                        .withStyle(ChatFormatting.RED), true);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerTickEffects(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        if (event.player.tickCount % 10 == 0) {
            ServerPlayer player = (ServerPlayer) event.player;
            AASWorldData data = AASWorldData.get(player.serverLevel());
            if (data.isGameStarted && !player.isCreative()) {
                boolean hasHeavyItem = false;
                for (ItemStack stack : player.getInventory().items) {
                    if (!stack.isEmpty() && isHeavyItem(stack.getItem())) { hasHeavyItem = true; break; }
                }
                if (!hasHeavyItem) {
                    for (ItemStack stack : player.getInventory().offhand) {
                        if (!stack.isEmpty() && isHeavyItem(stack.getItem())) { hasHeavyItem = true; break; }
                    }
                }
                if (hasHeavyItem) player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 2, false, false, true));
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerItemLogic(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        if (event.player.level().getGameTime() % 20 != 0) return;
        Player player = event.player;
        ItemStack mainHandStack = player.getMainHandItem();
        ItemStack offHandStack = player.getOffhandItem();
        Item monitorItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("superbwarfare", "monitor"));
        Item walkieItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("walkietalkie", "netherite_walkietalkie"));
        if (monitorItem == null || walkieItem == null) return;
        boolean hasMonitor = mainHandStack.getItem() == monitorItem;
        if (hasMonitor) {
            if (offHandStack.getItem() != walkieItem && !offHandStack.isEmpty()) {
                moveItemToInventory(player, offHandStack);
                player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            }
            if (player.getOffhandItem().isEmpty()) {
                int walkieSlot = findItemInInventory(player.getInventory(), walkieItem);
                if (walkieSlot != -1) {
                    ItemStack walkieStack = player.getInventory().getItem(walkieSlot);
                    player.setItemInHand(InteractionHand.OFF_HAND, walkieStack);
                    player.getInventory().setItem(walkieSlot, ItemStack.EMPTY);
                }
            }
        } else {
            if (!offHandStack.isEmpty() && offHandStack.getItem() == walkieItem) {
                moveItemToInventory(player, offHandStack);
                player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            }
        }
    }

    private static boolean isHeavyItem(Item item) {
        if (item == ModItems.AGS_AMMO.get()) return true;
        if (item == ModItems.M2_AMMO.get()) return true;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        if (id != null) {
            String path = id.getPath();
            if (path.equals("mortar_shell")) return true;
            if (path.equals("medium_anti_ground_missile")) return true;
        }
        return false;
    }

    private static int findItemInInventory(Inventory inventory, Item item) {
        for (int i = 0; i < inventory.items.size(); i++) { if (inventory.items.get(i).getItem() == item) return i; }
        return -1;
    }

    private static void moveItemToInventory(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        int globalTick = server.getTickCount();

        for (ServerLevel level : server.getAllLevels()) {
            AASWorldData data = AASWorldData.get(level);

            // Инициализируем переменные Блитца для текущего уровня
            // Они должны быть здесь, чтобы их видел и блок расчета, и блок отправки пакета
            boolean blueIsBleeding = false;
            boolean redIsBleeding = false;

            // === 1. РАССЫЛКА ПОЗИЦИЙ ИГРОКОВ (Оптимизация: каждые 2 тика) ===
            if (globalTick % 2 == 0) {
                List<ServerPlayer> bluePlayers = new ArrayList<>();
                List<ServerPlayer> redPlayers = new ArrayList<>();

                for (ServerPlayer p : level.players()) {
                    if (p.isSpectator()) continue;
                    if (p.getTeam() != null) {
                        if (p.getTeam().getName().equalsIgnoreCase("Blue")) bluePlayers.add(p);
                        else if (p.getTeam().getName().equalsIgnoreCase("Red")) redPlayers.add(p);
                    }
                }

                if (!bluePlayers.isEmpty()) {
                    List<MapPlayerInfo> blueInfo = buildPlayerInfo(bluePlayers, data);
                    PacketSyncMapPlayers packet = new PacketSyncMapPlayers(blueInfo);
                    for (ServerPlayer bp : bluePlayers) {
                        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> bp), packet);
                    }
                }

                if (!redPlayers.isEmpty()) {
                    List<MapPlayerInfo> redInfo = buildPlayerInfo(redPlayers, data);
                    PacketSyncMapPlayers packet = new PacketSyncMapPlayers(redInfo);
                    for (ServerPlayer rp : redPlayers) {
                        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> rp), packet);
                    }
                }
                // PATH: src/main/java/com/example/aas/events/GameLogicEvents.java

                if (globalTick % 5 == 0) { // Обновляем чаще (каждые 0.25 сек) для плавности поворота
                    boolean needsSync = false;
                    for (AASWorldData.VehicleRecord record : data.markedVehicles) {
                        Entity vEntity = level.getEntity(record.uuid);
                        if (vEntity != null && vEntity.isAlive()) {
                            // БеремgetYRot() — это основной поворот корпуса
                            float currentYaw = vEntity.getYRot();

                            // Если позиция или поворот изменились — помечаем для синхронизации
                            if (record.x != vEntity.getX() || record.yaw != currentYaw) {
                                record.x = vEntity.getX();
                                record.y = vEntity.getY();
                                record.z = vEntity.getZ();
                                record.yaw = currentYaw;
                                needsSync = true;
                            }
                        }
                    }
                    if (needsSync) {
                        data.setDirty();
                        // Принудительно шлем пакет, чтобы поворот обновился у всех сразу
                        PacketHandler.sendToAllClients(level, data);
                    }
                }
                if (globalTick % 40 == 0) { // Раз в 2 секунды проверяем "мертвые души"
                    boolean changed = false;
                    // Используем Iterator, чтобы безопасно удалять элементы во время цикла
                    Iterator<AASWorldData.VehicleRecord> it = data.markedVehicles.iterator();
                    while (it.hasNext()) {
                        AASWorldData.VehicleRecord record = it.next();
                        Entity vEntity = level.getEntity(record.uuid);

                        // Если сущность прогружена, но она МЕРТВА — удаляем её маркер
                        if (vEntity != null && !vEntity.isAlive()) {
                            it.remove();
                            changed = true;
                        }
                    }

                    if (changed) {
                        data.setDirty();
                        PacketHandler.sendToAllClients(level, data);
                    }
                }
            }
            if (globalTick % 200 == 0) {
                long time = level.getGameTime();
                boolean removed = data.activeMarkers.removeIf(m -> time >= m.expiryTick);
                if (removed) {
                    data.setDirty();
                    PacketHandler.sendToAllClients(level, data);
                }
            }
            // Внутри цикла for (ServerLevel level : server.getAllLevels()) в GameLogicEvents.java

            long now = level.getGameTime();
            boolean squadsChanged = false;

            for (AASWorldData.Squad squad : data.squads) {
                if (squad.rallyPos != null && squad.rallyExpiryTick != -1 && now >= squad.rallyExpiryTick) {

                    // Если чанк загружен, помечаем раллик как "истекший", чтобы не было штрафа -20 тикетов
                    if (level.isLoaded(squad.rallyPos)) {
                        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(squad.rallyPos);
                        if (be instanceof RallyPointBlockEntity rbe) {
                            rbe.isDecay = true;
                        }
                        level.removeBlock(squad.rallyPos, false);
                    } else {
                        // Если чанк НЕ загружен, блок удалится сам, когда игрок подойдет,
                        // но из данных карты иконка должна пропасть прямо сейчас:
                        data.blueRallies.remove(squad.rallyPos);
                        data.redRallies.remove(squad.rallyPos);
                        squad.rallyPos = null;
                        squad.rallyExpiryTick = -1;
                        squadsChanged = true;
                    }
                }
            }

            if (squadsChanged) {
                data.setDirty();
                PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new PacketSyncSquads(data.squads));
            }
            // 2. Проверка СКВАДНЫХ меток (Move/Attack и т.д.) - раз в 10 секунд
            // Лишняя строка AASWorldData data = ... УДАЛЕНА, так как data уже есть выше
            if (globalTick % 200 == 0) {
                long currentTick = level.getGameTime();
                boolean anyMarkerRemoved = false;
                for (AASWorldData.Squad squad : data.squads) {
                    if (squad.marker != null && currentTick >= squad.marker.expiryTick) {
                        squad.marker = null; // Удаляем метку
                        anyMarkerRemoved = true;
                    }
                }

                if (anyMarkerRemoved) {
                    data.setDirty();
                    // Рассылаем обновленные данные о сквадах (чтобы пропали линии)
                    PacketHandler.INSTANCE.send(PacketDistributor.DIMENSION.with(level::dimension),
                            new PacketSyncSquads(data.squads));
                }
            }
            // === 2. ЛОГИКА ОТСЧЕТА ДО СТАРТА ===
            if (data.countdownActive) {
                if (data.countdownTicks > 0) {
                    if (globalTick % 20 == 0) {
                        int seconds = data.countdownTicks / 20;
                        sendTitleToLevel(level, String.valueOf(seconds), ChatFormatting.YELLOW);
                        level.playSound(null, new BlockPos(0, 100, 0), net.minecraft.sounds.SoundEvents.NOTE_BLOCK_HAT.value(), net.minecraft.sounds.SoundSource.MASTER, 1f, 1f);
                    }
                    data.countdownTicks--;
                    data.setDirty();
                } else {
                    data.countdownActive = false;
                    sendTitleToLevel(level, "GO!", ChatFormatting.GREEN);
                    data.isGameStarted = true;
                    data.setDirty();
                    sendSyncPacket(level, data);
                    // === ВСТАВИТЬ СЮДА: Выдаем киты ровно 1 раз при старте игры! ===
                    // === НОВОЕ: ВЫДАЕМ ВЕЩИ ВСЕМ ИГРОКАМ ПРИ СТАРТЕ ===
                    for (ServerPlayer p : level.players()) {
                        String pending = p.getPersistentData().getString("AAS_PendingKit");
                        String current = p.getPersistentData().getString("AAS_CurrentKit");

                        // Если у игрока был выбран кит (в ожидании или уже текущий)
                        String kitToApply = !pending.isEmpty() ? pending : current;

                        if (kitToApply != null && !kitToApply.isEmpty() && !kitToApply.equals("Unassigned")) {
                            com.example.aas.network.ResupplyHandler.tryApplyPendingKit(p, data);
                        }
                    }

                    sendSyncPacket(level, data);
                }
            }
            // Внутри onServerTick, там где идет основная логика игры
            if (data.isGameStarted && AASConfig.LOW_TICKETS_SIREN.get()) {
                // Проверка для Синих
                if (data.blueTickets <= 50 && data.blueTickets > 0 && !data.playedBlueSiren) {
                    playSirenForTeam(level, "Blue");
                    data.playedBlueSiren = true;
                    data.setDirty();
                }
                // Проверка для Красных
                if (data.redTickets <= 50 && data.redTickets > 0 && !data.playedRedSiren) {
                    playSirenForTeam(level, "Red");
                    data.playedRedSiren = true;
                    data.setDirty();
                }
            }
            // === 3. ВЫЧИСЛЕНИЕ TICKET BLITZ (BLEED) ===
            boolean gameEnded = (data.blueTickets <= 0 || data.redTickets <= 0);

            if (data.isGameStarted && !gameEnded && !data.capturePoints.isEmpty()) {
                int totalPoints = data.capturePoints.size();
                long blueOwned = data.capturePoints.stream().filter(p -> p.owner.equalsIgnoreCase("BLUE")).count();
                long redOwned = data.capturePoints.stream().filter(p -> p.owner.equalsIgnoreCase("RED")).count();

                // ЛОГИКА: Команда теряет тикеты, если у неё 0 точек, А у врага захвачено (Всего - 1) или больше.
                blueIsBleeding = (blueOwned == 0) && (redOwned >= totalPoints - 1) && (totalPoints > 0);
                redIsBleeding = (redOwned == 0) && (blueOwned >= totalPoints - 1) && (totalPoints > 0);

                // Отнимаем тикеты каждые 40 тиков (2 секунды)
                if (globalTick % 40 == 0) {
                    boolean changed = false;
                    if (blueIsBleeding) {
                        data.blueTickets -= 1;
                        changed = true;
                    }
                    if (redIsBleeding) {
                        data.redTickets -= 1;
                        changed = true;
                    }

                    if (changed) {
                        checkGameOver(level, data);
                        data.setDirty();
                    }
                }
            }

            // === 4. ВАЛИДАЦИЯ И СИНХРОНИЗАЦИЯ ===
            // Передаем статус блитца (blueIsBleeding/redIsBleeding) в метод синхронизации,
            // чтобы клиент увидел мигание тикетов.
            if (globalTick % 20 == 0) {
                validateAndSync(level, false, blueIsBleeding, redIsBleeding);
            }
        }
    }

    // === ВСПОМОГАТЕЛЬНЫЙ МЕТОД ДЛЯ СБОРА ДАННЫХ ОБ ИГРОКАХ ===
    private static List<MapPlayerInfo> buildPlayerInfo(List<ServerPlayer> players, AASWorldData data) {
        List<MapPlayerInfo> infoList = new ArrayList<>();

        for (ServerPlayer p : players) {
            String pName = p.getScoreboardName();
            int squadId = -1;
            boolean isLeader = false;

            // 1. Ищем отряд
            for (AASWorldData.Squad s : data.squads) {
                if (s.members.contains(pName)) {
                    squadId = s.id;
                    if (s.leader.equals(pName)) isLeader = true;
                    break;
                }
            }

            // 2. Сначала собираем ВСЕ данные (важен порядок!)
            boolean inVehicle = p.getVehicle() != null;
            boolean downed = p.getPersistentData().getBoolean("AAS_IsDowned");
            long shout = p.getPersistentData().getLong("AAS_LastMedicShoutTimeMS");

            // 3. Добавляем в список ОДИН раз со всеми 9 аргументами
            infoList.add(new MapPlayerInfo(
                    pName,
                    p.getX(),
                    p.getZ(),
                    p.getYRot(),
                    squadId,
                    isLeader,
                    downed,
                    shout,
                    inVehicle
            ));
        }
        return infoList;
    }

    // В файле GameLogicEvents.java заменяем метод onWorldTick полностью

    @SubscribeEvent
    public static void onWorldTick(TickEvent.LevelTickEvent event) {
        if (event.level.isClientSide || event.phase != TickEvent.Phase.END) return;
        ServerLevel level = (ServerLevel) event.level;
        AASWorldData data = AASWorldData.get(level);

        Set<UUID> playersInPreciseZones = new HashSet<>();

        for (AASWorldData.CapturePoint point : data.capturePoints) {
            // Берем всех в квадрате (AABB) для начала
            List<ServerPlayer> playersInBox = level.getEntitiesOfClass(ServerPlayer.class, point.area);

            int blueOnPointLiving = 0;
            int redOnPointLiving = 0;
            boolean isTimeLocked = level.getGameTime() < point.lockedUntilTick;

            for (ServerPlayer p : playersInBox) {
                if (!p.isAlive() || p.isSpectator()) continue;

                // ФИКС УГЛОВ: Проверка реально внутри цилиндра или куба
                if (point.isInside(p.position())) {
                    playersInPreciseZones.add(p.getUUID());

                    boolean lockedUI = false;
                    String nextObjectiveForPlayer = "";

                    if (p.getTeam() != null) {
                        String teamName = p.getTeam().getName();
                        String teamKey = teamName.equalsIgnoreCase("Blue") ? "BLUE" : "RED";

                        if (!teamKey.isEmpty()) {
                            if (isTimeLocked) {
                                lockedUI = true;
                                long totalSeconds = (point.lockedUntilTick - level.getGameTime()) / 20;
                                nextObjectiveForPlayer = String.format("LOCKED: %dм %dс", totalSeconds / 60, totalSeconds % 60);
                            } else if (!canCapture(point, teamKey, data)) {
                                lockedUI = true;
                                nextObjectiveForPlayer = findRequiredPointName(point, teamKey, data);
                            }
                        }
                    }

                    // Шлем данные только тем, кто внутри формы
                    PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> p),
                            new PacketSyncPoint(true, point.name, point.owner, point.progress, lockedUI, nextObjectiveForPlayer, false, point.capturingTeam));

                    if (p.getTeam() != null) {
                        if (p.getTeam().getName().equalsIgnoreCase("Blue")) blueOnPointLiving++;
                        else if (p.getTeam().getName().equalsIgnoreCase("Red")) redOnPointLiving++;
                    }
                }
            }

            // ТВОЯ ПОЛНАЯ ЛОГИКА ЗАХВАТА
            String dominantTeam = "NONE";
            int alliesOnPoint = 0;
            boolean isContested = false;

            if (blueOnPointLiving > 0 && redOnPointLiving > 0) {
                if (blueOnPointLiving >= redOnPointLiving * 2) { dominantTeam = "BLUE"; alliesOnPoint = blueOnPointLiving; }
                else if (redOnPointLiving >= blueOnPointLiving * 2) { dominantTeam = "RED"; alliesOnPoint = redOnPointLiving; }
                else { isContested = true; }
            } else if (blueOnPointLiving > 0) {
                dominantTeam = "BLUE"; alliesOnPoint = blueOnPointLiving;
            } else if (redOnPointLiving > 0) {
                dominantTeam = "RED"; alliesOnPoint = redOnPointLiving;
            }

            if (!dominantTeam.equals("NONE") && !isContested && !isTimeLocked) {
                if (canCapture(point, dominantTeam, data)) {
                    float baseSpeed = 1.0f / (point.captureTimeMinutes * 60 * 20);
                    float multiplier = 1.0f;
                    if (alliesOnPoint > 1) multiplier += (alliesOnPoint - 1) * 0.5f;
                    if (multiplier > 4.0f) multiplier = 4.0f;

                    String oldOwner = point.owner;
                    handleTeamInfluence(point, dominantTeam, multiplier, baseSpeed, data, level);

                    if (!point.owner.equals(oldOwner) && !point.owner.equals("NEUTRAL")) {
                        if (point.lockDurationMinutes > 0) {
                            point.lockedUntilTick = level.getGameTime() + (point.lockDurationMinutes * 60L * 20L);
                            data.setDirty();
                        }
                    }
                }
            } else if (blueOnPointLiving == 0 && redOnPointLiving == 0 && !isTimeLocked) {
                if (point.owner.equals("NEUTRAL") && point.progress > 0) {
                    float baseSpeed = 1.0f / (point.captureTimeMinutes * 60 * 20);
                    point.progress -= (baseSpeed / 2.0f);
                    if (point.progress <= 0) { point.progress = 0; point.capturingTeam = "NONE"; }
                }
            }
        }

        // Очистка UI для тех, кто вышел из формы (каждый тик для точности цилиндра)
        for (ServerPlayer player : level.players()) {
            if (!playersInPreciseZones.contains(player.getUUID())) {
                PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                        new PacketSyncPoint(false, "", "", 0, false, "", false, "NONE"));
            }
        }
    }
    @SubscribeEvent
    public static void onPlayerChangeDimension(net.minecraftforge.event.entity.EntityTravelToDimensionEvent event) {
        // Проверяем, что перемещается именно игрок и это происходит на сервере
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerLevel level = player.serverLevel();
            AASWorldData data = AASWorldData.get(level);

            // Используем уже готовую логику выхода из отряда
            // Она удалит игрока из списка отряда, сбросит его теги и кит
            PacketSquadAction.leaveCurrentSquad(player, data);

            // Синхронизируем изменения со всеми игроками, чтобы обновить GUI
            PacketHandler.INSTANCE.send(net.minecraftforge.network.PacketDistributor.ALL.noArg(),
                    new PacketSyncSquads(data.squads));

            // Отправляем уведомление игроку
            player.sendSystemMessage(Component.literal("You were removed from the squad because you changed worlds.")
                    .withStyle(ChatFormatting.YELLOW));
        }
    }
    private static String findRequiredPointName(AASWorldData.CapturePoint currentPoint, String team, AASWorldData data) {
        int currentPriority = team.equals("BLUE") ? currentPoint.bluePriority : currentPoint.redPriority;
        int requiredPriority = currentPriority - 1;

        if (requiredPriority < 1) return "";

        for (AASWorldData.CapturePoint p : data.capturePoints) {
            int pPriority = team.equals("BLUE") ? p.bluePriority : p.redPriority;
            if (pPriority == requiredPriority) {
                return p.name;
            }
        }
        return "Unknown";
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!event.getEntity().level().isClientSide) {
            ServerPlayer player = (ServerPlayer) event.getEntity();
            ServerLevel level = player.serverLevel();
            AASWorldData data = AASWorldData.get(level);
            sendSyncPacket(level, data);
            PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new PacketSyncSquads(data.squads));
            if (!data.isGameStarted && player.hasPermissions(2)) {
                player.sendSystemMessage(Component.literal("AAS Game is paused. /aas gamestart true to start.").withStyle(ChatFormatting.YELLOW));
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!event.getEntity().level().isClientSide) {
            ServerPlayer newPlayer = (ServerPlayer) event.getEntity();
            if (newPlayer.gameMode.getGameModeForPlayer() != GameType.CREATIVE) newPlayer.setGameMode(GameType.SURVIVAL);
            newPlayer.connection.send(new ClientboundPlayerAbilitiesPacket(newPlayer.getAbilities()));
            pendingRespawnLocations.remove(newPlayer.getUUID());
            pendingTeams.remove(newPlayer.getUUID());
        }
    }

    @SubscribeEvent
    public static void onEntityDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) return;

        if (event.getEntity() instanceof ServerPlayer player) {
            // Эта функция вызовется только когда игрок УМЕР (взрыв или нажатие Give Up)
            processEntityLoss(player);

            // Сбрасываем тег нока при смерти
            player.getPersistentData().putBoolean("AAS_IsDowned", false);
            if (player.getPersistentData().getBoolean("AAS_GivingUp")) {
                return; // Выходим, чтобы не снимать тикеты второй раз
            }
        }
    }
    private static void saveKitNbtBeforeDeath(ServerPlayer player) {
        AASWorldData data = AASWorldData.get(player.serverLevel());
        String kitName = player.getPersistentData().getString("AAS_CurrentKit");
        String team = (player.getTeam() != null) ? player.getTeam().getName().toUpperCase() : "";

        if (team.isEmpty() || kitName.isEmpty() || kitName.equals("Unassigned")) return;

        AASWorldData.KitInfo kit = team.equals("BLUE") ? data.blueKits.get(kitName) : data.redKits.get(kitName);
        if (kit == null) return;

        Map<Integer, CompoundTag> savedTags = new HashMap<>();

        for (int i = 0; i < 41; i++) {
            // Проверяем, стоит ли флаг сохранения NBT для этого слота
            if (i < kit.saveNbtFlags.length && kit.saveNbtFlags[i]) {
                ItemStack item = player.getInventory().getItem(i);
                if (!item.isEmpty() && item.hasTag()) {
                    // Сохраняем копию NBT (зачарования, патроны и т.д.)
                    savedTags.put(i, item.getTag().copy());
                }
            }
        }

        if (!savedTags.isEmpty()) {
            PERSISTENT_NBT_STORAGE.put(player.getUUID(), savedTags);
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        ServerPlayer oldPlayer = (ServerPlayer) event.getOriginal();
        ServerPlayer newPlayer = (ServerPlayer) event.getEntity();
        // Очищаем все флаги нока при перерождении
        newPlayer.getPersistentData().putBoolean("AAS_IsDowned", false);
        newPlayer.getPersistentData().remove("AAS_GivingUp");
        newPlayer.getPersistentData().remove("AAS_DownedYaw");
        newPlayer.getPersistentData().remove("AAS_DownedPitch");
        newPlayer.getPersistentData().putLong("AAS_LastReviveTime", 0);

        // Синхронизируем состояние "не в ноке" со всеми
        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new PacketSyncDownedState(newPlayer.getId(), false));
        // Переносим данные о NBT из хранилища на новый UUID (если UUID вдруг меняется, но обычно он тот же)
        // Но так как мы используем статический Map и UUID, данные сохранятся в PERSISTENT_NBT_STORAGE автоматически.

        // 1. КОПИРОВАНИЕ ПЕРСИСТЕНТНЫХ ДАННЫХ (Ваш существующий код)
        net.minecraft.nbt.CompoundTag oldData = oldPlayer.getPersistentData();
        net.minecraft.nbt.CompoundTag newData = newPlayer.getPersistentData();

        if (oldData.contains("AAS_SquadID")) newData.putInt("AAS_SquadID", oldData.getInt("AAS_SquadID"));
        if (oldData.contains("AAS_IsSquadLeader")) newData.putBoolean("AAS_IsSquadLeader", oldData.getBoolean("AAS_IsSquadLeader"));
        if (oldData.contains("AAS_CurrentKit")) newData.putString("AAS_CurrentKit", oldData.getString("AAS_CurrentKit"));
        if (oldData.contains("AAS_PendingKit")) newData.putString("AAS_PendingKit", oldData.getString("AAS_PendingKit"));
        if (oldData.contains("AAS_LastFobResupply")) newData.putLong("AAS_LastFobResupply", oldData.getLong("AAS_LastFobResupply"));
        if (oldData.contains("AAS_LastMainResupply")) newData.putLong("AAS_LastMainResupply", oldData.getLong("AAS_LastMainResupply"));

        // 2. ВОССТАНОВЛЕНИЕ КОМАНДЫ
        if (event.isWasDeath()) {
            String teamName = (oldPlayer.getTeam() != null) ? oldPlayer.getTeam().getName() : null;
            if (teamName != null) {
                Scoreboard scoreboard = newPlayer.getScoreboard();
                PlayerTeam pTeam = scoreboard.getPlayerTeam(teamName);
                if (pTeam != null) scoreboard.addPlayerToTeam(newPlayer.getScoreboardName(), pTeam);
            }
        }
    }

    @SubscribeEvent
    public static void onEntityRemove(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide) return;
        Entity entity = event.getEntity();
        if (entity instanceof LivingEntity) return;
        Entity.RemovalReason reason = entity.getRemovalReason();
        if (reason != null && (reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED)) {
            processEntityLoss(entity);
        }
    }

    private static void processEntityLoss(Entity entity) {
        if (entity.level() == null || entity.level().getServer() == null) return;
        ServerLevel level = (ServerLevel) entity.level();
        AASWorldData data = AASWorldData.get(level);

        // === 1. МГНОВЕННОЕ УДАЛЕНИЕ ИКОНКИ С КАРТЫ ===
        // Если UUID этой сущности есть в списке помеченных — удаляем запись
        boolean markerRemoved = data.markedVehicles.removeIf(v -> v.uuid.equals(entity.getUUID()));

        // Если билетов уже 0, мы всё равно должны синхронизировать удаление маркера
        if (data.blueTickets <= 0 || data.redTickets <= 0) {
            if (markerRemoved) {
                data.setDirty();
                sendSyncPacket(level, data);
            }
            return;
        }

        boolean ticketsChanged = false;

        // === 2. ЛОГИКА СМЕРТИ ИГРОКА (ШТРАФЫ) ===
        if (entity instanceof ServerPlayer player) {
            if (player.getTeam() != null) {
                String teamName = player.getTeam().getName();
                int cost = data.deathTicketCost;
                if (teamName.equalsIgnoreCase("Blue")) {
                    data.blueTickets = Math.max(0, data.blueTickets - cost);
                    ticketsChanged = true;
                }
                else if (teamName.equalsIgnoreCase("Red")) {
                    data.redTickets = Math.max(0, data.redTickets - cost);
                    ticketsChanged = true;
                }
            }
        }

        // === 3. ЛОГИКА ПОТЕРИ ТЕХНИКИ (ШТРАФЫ) ===
        if (entity.getPersistentData().contains("AAS_TicketPenalty")) {
            int penalty = entity.getPersistentData().getInt("AAS_TicketPenalty");
            String vTeam = entity.getPersistentData().getString("AAS_VehicleTeam");
            String vType = entity.getPersistentData().getString("AAS_VehicleType");
            entity.getPersistentData().remove("AAS_TicketPenalty");

            if (penalty > 0) {
                if (vTeam.equalsIgnoreCase("BLUE")) {
                    data.blueTickets = Math.max(0, data.blueTickets - penalty);
                    ticketsChanged = true;
                    broadcastMessage(level, "BLUE lost " + vType + " (-" + penalty + ")", ChatFormatting.BLUE);
                } else if (vTeam.equalsIgnoreCase("RED")) {
                    data.redTickets = Math.max(0, data.redTickets - penalty);
                    ticketsChanged = true;
                    broadcastMessage(level, "RED lost " + vType + " (-" + penalty + ")", ChatFormatting.RED);
                }
            }
        }

        // === 4. СИНХРОНИЗАЦИЯ ===
        // Если изменились тикеты ИЛИ был удален маркер техники
        if (ticketsChanged || markerRemoved) {
            if (ticketsChanged) checkGameOver(level, data);
            data.setDirty();
            sendSyncPacket(level, data); // Отправляем обновленные данные всем игрокам
        }
    }

    private static void handleTeamInfluence(AASWorldData.CapturePoint point, String attackingTeam, float multiplier, float baseSpeed, AASWorldData data, ServerLevel level) {
        float speedBoosted = baseSpeed * multiplier;

        if (point.owner.equals("NEUTRAL")) {
            if (point.capturingTeam.equals("NONE") || point.capturingTeam.equals(attackingTeam)) {
                point.capturingTeam = attackingTeam;
                point.progress += speedBoosted;

                if (point.progress >= 1.0f) {
                    point.progress = 1.0f;
                    point.owner = attackingTeam;

                    String displayName = attackingTeam;
                    ChatFormatting color = ChatFormatting.WHITE;

                    // === ЛОГИКА ИМЕН КОМАНД ИЗ КОНФИГА ===
                    if (attackingTeam.equals("BLUE")) {
                        color = ChatFormatting.BLUE;
                        String cfgName = com.example.aas.config.AASConfig.BLUE_TEAM_CUSTOM_NAME.get();
                        // Если выбрана конкретная фракция (не "none" и не "bluefor"), используем её имя
                        if (data.blueFaction != null && !data.blueFaction.equals("none") && !data.blueFaction.equals("bluefor")) {
                            displayName = formatFactionName(data.blueFaction);
                        } else {
                            // Иначе используем имя из конфига (по умолчанию "BLUEFOR", но можно сменить на "Спецназ" и т.д.)
                            displayName = cfgName;
                        }
                    } else if (attackingTeam.equals("RED")) {
                        color = ChatFormatting.RED;
                        String cfgName = com.example.aas.config.AASConfig.RED_TEAM_CUSTOM_NAME.get();
                        // Если выбрана конкретная фракция (не "none" и не "redfor"), используем её имя
                        if (data.redFaction != null && !data.redFaction.equals("none") && !data.redFaction.equals("redfor")) {
                            displayName = formatFactionName(data.redFaction);
                        } else {
                            // Иначе используем имя из конфига
                            displayName = cfgName;
                        }
                    }
                    // ======================================

                    sendTitleToLevel(level, displayName + " Captured " + point.name, color);

                    if (point.captureDeduction > 0) {
                        if (attackingTeam.equals("BLUE")) {
                            data.redTickets -= point.captureDeduction;
                            broadcastMessage(level, "RED lost " + point.captureDeduction + " tickets!", ChatFormatting.RED);
                        } else {
                            data.blueTickets -= point.captureDeduction;
                            broadcastMessage(level, "BLUE lost " + point.captureDeduction + " tickets!", ChatFormatting.BLUE);
                        }
                        checkGameOver(level, data);
                        sendSyncPacket(level, data);
                    }
                }
            } else {
                point.progress -= speedBoosted;
                if (point.progress <= 0.0f) {
                    point.progress = 0.0f;
                    point.capturingTeam = "NONE";
                }
            }
        } else if (point.owner.equals(attackingTeam)) {
            if (point.progress < 1.0f) {
                point.progress += speedBoosted;
                if (point.progress > 1.0f) point.progress = 1.0f;
            }
        } else {
            point.progress -= speedBoosted;
            if (point.progress <= 0.0f) {
                if (point.owner.equals("BLUE")) data.blueTickets -= point.ticketPenalty;
                else data.redTickets -= point.ticketPenalty;

                checkGameOver(level, data);
                sendSyncPacket(level, data);
                broadcastMessage(level, point.name + " neutralized!", ChatFormatting.GRAY);

                point.owner = "NEUTRAL";
                point.progress = 0.0f;
                point.capturingTeam = "NONE";
            }
        }
    }

    // === ОБНОВЛЕННЫЙ МЕТОД: Принимает статус Блитца ===
    private static void validateAndSync(ServerLevel level, boolean forceSend, boolean blueBleed, boolean redBleed) {
        AASWorldData data = AASWorldData.get(level);
        boolean changed = false;
        String dimKey = level.dimension().location().toString();

        // 1. Валидация ралликов (удаляем, если блок сломан)
        changed |= validateRallies(level, data.blueRallies);
        changed |= validateRallies(level, data.redRallies);

        // 2. Проверка Хабов (блокировка и материалы)
        int hubRadius = AASConfig.HUB_BLOCK_RADIUS.get();
        int hubEnemiesRequired = AASConfig.HUB_BLOCK_ENEMY_COUNT.get();

        boolean anyHubChanged = false;
        for (AASWorldData.HubInfo hub : data.hubs) {
            if (hub.constructed && hub.dimension != null && hub.dimension.equals(dimKey)) {
                if (level.isLoaded(hub.pos)) {
                    boolean nowBlocked = getEnemyCount(level, hub.pos, hub.team, hubRadius) >= hubEnemiesRequired;
                    if (hub.isBlocked != nowBlocked) {
                        hub.isBlocked = nowBlocked;
                        anyHubChanged = true;
                        changed = true;
                    }

                    // НОВОЕ: Обновляем материалы для клиента
                    net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(hub.pos);
                    if (be instanceof com.example.aas.block.HubBlockEntity hubBe) {
                        if (hub.materials != hubBe.getMaterials()) {
                            hub.materials = hubBe.getMaterials();
                            anyHubChanged = true;
                            changed = true;
                        }
                    }
                }
            }
        }

        // 3. Проверка Ралликов (блокировка)
        // === ПРИМЕНЕНИЕ КОНФИГА: Радиус и Кол-во врагов для РАЛИКА ===
        int rallyRadius = AASConfig.RALLY_BLOCK_RADIUS.get();
        int rallyEnemiesRequired = AASConfig.RALLY_BLOCK_ENEMY_COUNT.get();

        boolean hasBlue = !data.blueRallies.isEmpty();
        boolean hasRed = !data.redRallies.isEmpty();
        boolean blueBlocked = false;
        // Передаем параметры в isEnemyNearby
        if (hasBlue) blueBlocked = isEnemyNearby(level, data.blueRallies.get(data.blueRallies.size() - 1), "Blue", rallyRadius, rallyEnemiesRequired);

        boolean redBlocked = false;
        if (hasRed) redBlocked = isEnemyNearby(level, data.redRallies.get(data.redRallies.size() - 1), "Red", rallyRadius, rallyEnemiesRequired);

        Boolean cachedBlue = lastBlueBlockedMap.getOrDefault(dimKey, false);
        Boolean cachedRed = lastRedBlockedMap.getOrDefault(dimKey, false);

        if (forceSend || anyHubChanged || blueBlocked != cachedBlue || redBlocked != cachedRed || changed || blueBleed || redBleed) {
            sendSyncPacket(level, data, blueBleed, redBleed, blueBlocked, redBlocked);
            lastBlueBlockedMap.put(dimKey, blueBlocked);
            lastRedBlockedMap.put(dimKey, redBlocked);
        }

        if (changed) data.setDirty();
    }

    // === МЕТОД ДЛЯ СОВМЕСТИМОСТИ ===
    // Нужен, чтобы не было ошибок в onEntityDeath и других местах, где мы не знаем статус блитца.
    // По умолчанию передаем false (нет блитца).
    private static void validateAndSync(ServerLevel level, boolean forceSend) {
        validateAndSync(level, forceSend, false, false);
    }

    private static boolean validateRallies(ServerLevel level, List<BlockPos> rallies) {
        return rallies.removeIf(pos -> {
            if (level.isLoaded(pos)) {
                return !(level.getBlockState(pos).getBlock() instanceof RallyPointBlock);
            }
            return false;
        });
    }

    private static boolean isEnemyNearby(ServerLevel level, BlockPos pos, String allyTeamName, int radius, int minCount) {
        return getEnemyCount(level, pos, allyTeamName, radius) >= minCount;
    }

    private static int getEnemyCount(ServerLevel level, BlockPos pos, String allyTeamName, int radius) {
        // Используем переданный радиус
        AABB checkArea = new AABB(pos).inflate(radius);
        List<ServerPlayer> enemies = level.getEntitiesOfClass(ServerPlayer.class, checkArea);
        int count = 0;
        for (ServerPlayer p : enemies) {
            if (p.isSpectator()) continue;
            if (p.getTeam() == null || !p.getTeam().getName().equalsIgnoreCase(allyTeamName)) count++;
        }
        return count;
    }

    private static int getEnemyCount(ServerLevel level, BlockPos pos, String allyTeamName) {
        AABB checkArea = new AABB(pos).inflate(40);
        List<ServerPlayer> enemies = level.getEntitiesOfClass(ServerPlayer.class, checkArea);
        int count = 0;
        for (ServerPlayer p : enemies) {
            if (p.isSpectator()) continue;
            if (p.getTeam() == null || !p.getTeam().getName().equalsIgnoreCase(allyTeamName)) count++;
        }
        return count;
    }

    public static void checkGameOver(ServerLevel level, AASWorldData data) {
        if (data.blueTickets <= 0) {
            data.blueTickets = 0;

            String winnerName;
            if (data.redFaction == null || data.redFaction.equals("none") || data.redFaction.equals("redfor")) {
                winnerName = AASConfig.RED_TEAM_CUSTOM_NAME.get();
            } else {
                winnerName = formatFactionName(data.redFaction);
            }
            if (winnerName.isEmpty()) winnerName = "RED TEAM";

            // Текст подзаголовка для Красных
            String subText = data.redTickets + " tickets";
            sendVictoryMessage(level, winnerName + " WINS!", subText, ChatFormatting.RED);

            data.isGameStarted = false;
            data.setDirty();

        } else if (data.redTickets <= 0) {
            data.redTickets = 0;

            String winnerName;
            if (data.blueFaction == null || data.blueFaction.equals("none") || data.blueFaction.equals("bluefor")) {
                winnerName = AASConfig.BLUE_TEAM_CUSTOM_NAME.get();
            } else {
                winnerName = formatFactionName(data.blueFaction);
            }
            if (winnerName.isEmpty()) winnerName = "BLUE TEAM";

            // Текст подзаголовка для Синих
            String subText = data.blueTickets + " tickets";
            sendVictoryMessage(level, winnerName + " WINS!", subText, ChatFormatting.BLUE);

            data.isGameStarted = false;
            data.setDirty();
        }
    }

    private static void sendVictoryMessage(ServerLevel level, String mainText, String subText, ChatFormatting color) {
        Component title = Component.literal(mainText).withStyle(color).withStyle(ChatFormatting.BOLD);
        Component subtitle = Component.literal(subText).withStyle(ChatFormatting.GRAY); // Маленький текст будет серым

        for (ServerPlayer player : level.players()) {
            // Устанавливаем время показа (20 тиков появление, 100 тиков показ, 20 тиков исчезновение)
            player.connection.send(new ClientboundSetTitlesAnimationPacket(20, 100, 20));
            // Сначала отправляем подзаголовок (обязательно перед заголовком)
            player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
            // Затем основной заголовок
            player.connection.send(new ClientboundSetTitleTextPacket(title));
        }
    }

    private static String formatFactionName(String faction) {
        return faction.replace("_", " ").toUpperCase();
    }

    private static void sendTitleToLevel(ServerLevel level, String text, ChatFormatting color) {
        Component title = Component.literal(text).withStyle(color).withStyle(ChatFormatting.BOLD);
        for (ServerPlayer player : level.players()) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(0, 20, 10));
            player.connection.send(new ClientboundSetTitleTextPacket(title));
        }
    }

    private static void sendTitleToAll(MinecraftServer server, String text, ChatFormatting color) {
        Component title = Component.literal(text).withStyle(color).withStyle(ChatFormatting.BOLD);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.connection.send(new ClientboundSetTitlesAnimationPacket(0, 20, 10));
            player.connection.send(new ClientboundSetTitleTextPacket(title));
        }
    }

    private static void broadcastMessage(ServerLevel level, String text, ChatFormatting color) {
        level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(text).withStyle(color), false);
    }

    private static void sendSyncPacket(ServerLevel level, AASWorldData data) {
        sendSyncPacket(level, data, false, false);
    }

    private static void sendSyncPacket(ServerLevel level, AASWorldData data, boolean blueBleed, boolean redBleed) {
        String dimKey = level.dimension().location().toString();
        boolean bBlocked = lastBlueBlockedMap.getOrDefault(dimKey, false);
        boolean rBlocked = lastRedBlockedMap.getOrDefault(dimKey, false);
        sendSyncPacket(level, data, blueBleed, redBleed, bBlocked, rBlocked);
    }

    private static void sendSyncPacket(ServerLevel level, AASWorldData data, boolean blueBleed, boolean redBleed, boolean bBlocked, boolean rBlocked) {
        PacketSyncGameData packet = createSyncPacket(data, blueBleed, redBleed, bBlocked, rBlocked);
        PacketHandler.INSTANCE.send(PacketDistributor.DIMENSION.with(level::dimension), packet);
    }

    private static PacketSyncGameData createSyncPacket(AASWorldData data, boolean blueBleed, boolean redBleed, boolean bBlocked, boolean rBlocked) {
        boolean hasBlue = !data.blueRallies.isEmpty();
        boolean hasRed = !data.redRallies.isEmpty();

        // Читаем из конфига СЕРВЕРА
        String bName = com.example.aas.config.AASConfig.BLUE_TEAM_CUSTOM_NAME.get();
        String rName = com.example.aas.config.AASConfig.RED_TEAM_CUSTOM_NAME.get();

        // Собираем киты игроков
        java.util.Map<String, String> pKits = new java.util.HashMap<>();
        net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            for (net.minecraft.server.level.ServerPlayer p : server.getPlayerList().getPlayers()) {
                String current = p.getPersistentData().getString("AAS_CurrentKit");
                String pending = p.getPersistentData().getString("AAS_PendingKit");
                String displayKit = !pending.isEmpty() ? pending : current;
                pKits.put(p.getScoreboardName(), displayKit.isEmpty() ? "Unassigned" : displayKit);
            }
        }

        // ПЕРЕДАЕМ ВСЕ 24 АРГУМЕНТА В ПРАВИЛЬНОМ ПОРЯДКЕ
        return new PacketSyncGameData(
                data.blueTickets,
                data.redTickets,
                hasBlue,
                hasRed,
                blueBleed,
                redBleed,
                data.respawnTimer,
                bBlocked,
                rBlocked,
                data.hubs,
                data.blueFaction,
                data.redFaction,
                bName,
                rName,
                data.isGameStarted,// 19-й
                data.mapCenterX,
                data.mapCenterZ,
                data.mapSizeBlocks,
                data.capturePoints, // 20-й
                data.blueSpawns,
                data.redSpawns,
                data.neutralSpawns,
                pKits,
                data.markedVehicles,
                data.activeMarkers,
                com.example.aas.config.AASConfig.HUB_SPAWN_COSTS_MATERIALS.get(),
                com.example.aas.config.AASConfig.HUB_SPAWN_MATERIAL_COST.get()
        );
    }

    private static boolean canCapture(AASWorldData.CapturePoint target, String team, AASWorldData data) {
        int currentPriority = team.equals("BLUE") ? target.bluePriority : target.redPriority;
        if (currentPriority <= 1) return true;
        int requiredPriority = currentPriority - 1;
        for (AASWorldData.CapturePoint p : data.capturePoints) {
            int pPriority = team.equals("BLUE") ? p.bluePriority : p.redPriority;
            if (pPriority == requiredPriority && p.owner.equals(team)) return true;
        }
        return false;
    }
    // === ВСТАВИТЬ В КЛАСС GameLogicEvents ===

    public static void leaveCurrentSquad(ServerPlayer player, AASWorldData data) {
        String pName = player.getScoreboardName();

        data.squads.forEach(s -> {
            if (s.members.contains(pName)) {
                s.members.remove(pName);

                if (s.leader.equals(pName)) {
                    PacketSquadAction.removeRadio(player);

                    if (!s.members.isEmpty()) {
                        s.leader = s.members.get(0);
                        ServerPlayer newLeader = player.server.getPlayerList().getPlayerByName(s.leader);
                        if (newLeader != null) {
                            PacketSquadAction.updatePlayerTags(newLeader, s.id, true);

                            // === ПРОВЕРКА КОНФИГА: ВЫДАЕМ РАЦИЮ ТОЛЬКО ЕСЛИ ВКЛЮЧЕНО ===
                            if (AASConfig.AUTO_GIVE_SL_RADIO.get()) {
                                PacketSquadAction.giveRadio(newLeader);
                            }

                            newLeader.sendSystemMessage(Component.literal("The leader left. You are now the Squad Leader!").withStyle(ChatFormatting.GOLD));
                        }
                    }
                }
            }
        });

        data.squads.removeIf(s -> s.members.isEmpty());

        player.getPersistentData().putString("AAS_CurrentKit", "Unassigned");
        player.getPersistentData().remove("AAS_PendingKit");
        player.getInventory().clearContent();
        player.inventoryMenu.broadcastChanges();

        PacketSquadAction.removePlayerTags(player);

        player.displayClientMessage(Component.literal("You left the squad. Kit reset.").withStyle(ChatFormatting.YELLOW), true);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!event.getEntity().level().isClientSide) {
            ServerPlayer player = (ServerPlayer) event.getEntity();
            ServerLevel level = player.serverLevel();
            AASWorldData data = AASWorldData.get(level);

            // === СОХРАНЕНИЕ ПРОГРЕССА ПРИ ВЫЛЕТЕ В БОЮ ===
            if (data.isGameStarted) {
                return; // Ничего не очищаем, если игра идет!
            }

            // === ОЧИСТКА ДО СТАРТА ИГРЫ ===
            // 1. Удаляем из отряда и чистим теги SL (передаем лидера другому, если нужно)
            PacketSquadAction.leaveCurrentSquad(player, data);

            // 2. ПОЛНЫЙ СБРОС КИТА ПРИ ВЫХОДЕ
            player.getPersistentData().putString("AAS_CurrentKit", "Unassigned");
            player.getPersistentData().remove("AAS_PendingKit");

            // 3. Очистка инвентаря, чтобы не дюпались вещи
            player.getInventory().clearContent();
            player.inventoryMenu.broadcastChanges();

            data.setDirty();

            // Синхронизируем список отрядов для остальных игроков
            PacketHandler.INSTANCE.send(PacketDistributor.DIMENSION.with(level::dimension), new PacketSyncSquads(data.squads));
        }
    }
    private static void playSirenForTeam(ServerLevel level, String teamName) {
        for (ServerPlayer player : level.players()) {
            if (player.getTeam() != null && player.getTeam().getName().equalsIgnoreCase(teamName)) {
                // ФИКС: Отправляем звук напрямую клиенту игрока
                player.playNotifySound(ModSounds.SIREN_ALARM.get(), net.minecraft.sounds.SoundSource.MASTER, 1.0F, 1.0F);
            }
        }
    }
    // 2. Добавляем метод для мгновенной проверки сирены (вызывать при изменении тикетов)
    public static void checkSirenManual(ServerLevel level, AASWorldData data) {
        if (!data.isGameStarted || !AASConfig.LOW_TICKETS_SIREN.get()) return;

        if (data.blueTickets <= 50 && data.blueTickets >= 0 && !data.playedBlueSiren) {
            playSirenForTeam(level, "Blue");
            data.playedBlueSiren = true;
            data.setDirty();
        }
        if (data.redTickets <= 50 && data.redTickets >= 0 && !data.playedRedSiren) {
            playSirenForTeam(level, "Red");
            data.playedRedSiren = true;
            data.setDirty();
        }
    }
    @SubscribeEvent
    public static void enforceMortarShellLimit(TickEvent.PlayerTickEvent event) {
        // Выполняем только на сервере и в конце тика
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;

        // Проверяем раз в 10 тиков (полсекунды) для оптимизации
        if (event.player.tickCount % 10 != 0) return;

        Player player = event.player;

        // В креативе и режиме наблюдателя ограничений нет
        if (player.isCreative() || player.isSpectator()) return;

        // Ищем мортирный снаряд. Если мода нет — используем стрелы (отладка)
        Item mortarItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("superbwarfare", "mortar_shell"));
        if (mortarItem == null || mortarItem == net.minecraft.world.item.Items.AIR) {
            mortarItem = net.minecraft.world.item.Items.ARROW;
        }

        // Считаем общее количество снарядов в инвентаре
        int totalCount = 0;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.getItem() == mortarItem) {
                totalCount += stack.getCount();
            }
        }

        // Если больше 8 — забираем и выбрасываем излишек
        if (totalCount > 8) {
            int toRemove = totalCount - 8;
            int removedSoFar = 0;

            for (int i = 0; i < inv.getContainerSize(); i++) {
                if (removedSoFar >= toRemove) break;

                ItemStack stack = inv.getItem(i);
                if (stack.getItem() == mortarItem) {
                    int shrinkAmount = Math.min(stack.getCount(), toRemove - removedSoFar);
                    stack.shrink(shrinkAmount);
                    removedSoFar += shrinkAmount;

                    // Выбрасываем лишнее на землю перед игроком
                    ItemStack dropped = new ItemStack(mortarItem, shrinkAmount);
                    player.drop(dropped, false, true);
                }
            }

            // Уведомляем игрока
            player.displayClientMessage(Component.literal("You can only carry up to 8 Mortar Shells!").withStyle(ChatFormatting.RED), true);
        }
    }
}