// PATH: src/main/java/com/example/aas/events/DownedHandler.java
package com.example.aas.events;

import com.example.aas.config.AASConfig;
import com.example.aas.network.PacketHandler;
import com.example.aas.network.PacketReviveProgress;
import com.example.aas.network.PacketSyncDownedState;
import com.example.aas.sound.ModSounds;
import com.example.aas.network.PacketSyncDragState;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
import net.minecraftforge.eventbus.api.EventPriority;
import com.example.aas.world.AASWorldData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = "aas")
public class DownedHandler {

    // ================== DRAG / CARRY SYSTEM ==================
    // carrier UUID -> target(раненый) UUID
    private static final Map<UUID, UUID> DRAGGING = new HashMap<>();
    // carrier UUID -> yaw переносчика в момент захвата (для отслеживания суммарного разворота)
    private static final Map<UUID, Float> DRAG_START_YAW = new HashMap<>();

    private static final float DRAG_DISTANCE = 1.3f;
    private static final float TURN_SNAP_THRESHOLD = 18.0f;   // резкий рывок камеры за один тик (°)
    private static final float TURN_TOTAL_THRESHOLD = 110.0f; // суммарный разворот от начала переноски (°)
    private static final double FORWARD_BREAK_DOT = 0.35;     // порог "идёт вперёд, а не пятится"
    private static final double DRAG_MAX_DISTANCE = 4.0;      // если раненого "оторвало" дальше - бросаем

    // ================== REVIVE (HOLD KEY) SYSTEM ==================
    // reviver UUID -> активный канал подъёма
    private static final Map<UUID, ReviveChannel> REVIVE_CHANNELS = new HashMap<>();
    private static final double REVIVE_RANGE = 3.0;          // макс. дистанция до раненого, чтобы поднимать
    // На сервере допускаем чуть больший радиус, чем на клиенте: из-за лага позиции игроков на сервере
    // и у клиента расходятся, и цель на границе 3.0 блока иначе "мигает" и сбрасывает прогресс.
    private static final double REVIVE_RANGE_SERVER = REVIVE_RANGE + 0.75;

    // Звук перевязки/подъёма. Используем ванильный "шорох ткани" от кожаной брони -
    // по звучанию он ближе всего к бинтованию/перевязке, отдельный .ogg не нужен.
    private static final net.minecraft.sounds.SoundEvent REVIVE_SOUND = SoundEvents.ARMOR_EQUIP_LEATHER;

    private static class ReviveChannel {
        final UUID targetUUID;
        int progressTicks = 0;

        ReviveChannel(UUID targetUUID) {
            this.targetUUID = targetUUID;
        }
    }

    @SubscribeEvent
    public static void onPlayerHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!AASConfig.ENABLE_KNOCKOUT.get()) return;

        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        if (player.getPersistentData().getBoolean("AAS_GivingUp")) return;
        if (player.getPersistentData().getBoolean("AAS_IsDowned")) {
            // Если игрока в ноке взорвали - позволяем ему умереть окончательно
            if (event.getSource().is(DamageTypeTags.IS_EXPLOSION)) return;

            // Любой другой урон (пули) в ноке игнорируем, чтобы не "добивали" без Give Up
            event.setCanceled(true);
            return;
        }

        if (event.getAmount() >= player.getHealth()) {
            if (event.getSource().is(DamageTypeTags.IS_EXPLOSION)) return;

            long lastRevive = player.getPersistentData().getLong("AAS_LastReviveTime");
            if (lastRevive > 0 && (player.level().getGameTime() - lastRevive) < (AASConfig.REVIVE_COOLDOWN_SECONDS.get() * 20)) return;

            event.setCanceled(true);

            // ЗАПОМИНАЕМ КТО НОКНУЛ
            Entity attacker = event.getSource().getEntity();
            if (attacker instanceof Player attackingPlayer) {
                // Если убил напрямую игрок (пуля, меч)
                player.getPersistentData().putString("AAS_KnockedBy", attackingPlayer.getScoreboardName());
            } else if (player.getLastHurtByMob() instanceof Player lastAttacker) {
                // Ванильная механика: если добил огонь, падение, колючая проволока,
                // но незадолго до этого игрока бил другой игрок!
                player.getPersistentData().putString("AAS_KnockedBy", lastAttacker.getScoreboardName());
            } else {
                // Стираем убийцу, только если это была чистая смерть от окружения
                player.getPersistentData().remove("AAS_KnockedBy");
            }

            enterDownedState(player);
        }
    }

    public static void enterDownedState(ServerPlayer player) {

        if (player.isPassenger()) {
            player.stopRiding();
        }

        // Если этого игрока кто-то уже пытался поднимать - канал больше не актуален
        clearReviveChannelsIfTarget(player);

        player.getPersistentData().putBoolean("AAS_IsDowned", true);

        // Запоминаем тик, когда игрок упал в нокаут
        player.getPersistentData().putLong("AAS_DownedTick", player.level().getGameTime());

        player.setHealth(6.0f); // Даём немного ХП в ноке
        player.setPose(Pose.SWIMMING);

        // ИЗДАЕМ ЗВУК ПАДЕНИЯ (слышат все вокруг)
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.PLAYER_DOWNED.get(), net.minecraft.sounds.SoundSource.PLAYERS, 1.5F, 1.0F);

        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new PacketSyncDownedState(player.getId(), true));
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        if (!(event.player instanceof ServerPlayer player)) return;

        // РЕСИНК НОКА ПОСЛЕ ПЕРЕЗАХОДА (см. onLogin) — досчитываем отложенный
        // таймер и, когда он истёк, наконец шлём клиенту реальное состояние.
        int resyncDelay = player.getPersistentData().getInt("AAS_DownedResyncDelay");
        if (resyncDelay > 0) {
            resyncDelay--;
            if (resyncDelay <= 0) {
                player.getPersistentData().remove("AAS_DownedResyncDelay");
                if (player.getPersistentData().getBoolean("AAS_IsDowned")) {
                    player.setPose(Pose.SWIMMING);
                    PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),
                            new PacketSyncDownedState(player.getId(), true));
                }
            } else {
                player.getPersistentData().putInt("AAS_DownedResyncDelay", resyncDelay);
            }
        }

        if (player.getPersistentData().getBoolean("AAS_IsDowned")) {
            player.setPose(Pose.SWIMMING); // Поддерживаем позу лёжа

            // Полная остановка движения
            player.setDeltaMovement(0, player.getDeltaMovement().y, 0);

            // Автоматический Give Up через 3 минуты (3 * 60 * 20 = 3600 тиков)
            long downedTick = player.getPersistentData().getLong("AAS_DownedTick");

            if (player.level().getGameTime() - downedTick >= 3600) {
                forceGiveUp(player);
            }
        } else if (player.isAlive()) {
            // НОВОЕ: если раненый (недавно поднятый) игрок полностью восстановил здоровье -
            // снимаем "таймер мгновенной смерти" (AAS_LastReviveTime). После этого следующий
            // смертельный удар снова уложит его в нок, а не убьёт мгновенно.
            if (player.getHealth() >= player.getMaxHealth()
                    && player.getPersistentData().getLong("AAS_LastReviveTime") != 0) {
                player.getPersistentData().putLong("AAS_LastReviveTime", 0L);
            }
        }

        // Тик активного канала подъёма (если этот игрок сейчас кого-то поднимает)
        tickReviveChannel(player);
    }

    // Вынесенный метод для убийства игрока, чтобы вызывать его и по кнопке, и по таймеру
    public static void forceGiveUp(ServerPlayer player) {
        clearDragIfTarget(player);
        stopDragging(player, null);
        clearReviveChannelsIfTarget(player);

        String victimName = player.getScoreboardName();
        String killerName = player.getPersistentData().getString("AAS_KnockedBy");
        ServerLevel level = player.serverLevel();

        boolean showMessages = level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_SHOWDEATHMESSAGES);

        if (showMessages) {
            net.minecraft.network.chat.MutableComponent logMsg;
            if (killerName != null && !killerName.isEmpty()) {
                logMsg = Component.literal(victimName).withStyle(ChatFormatting.WHITE)
                        .append(Component.literal(" was finished by ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(killerName).withStyle(ChatFormatting.GOLD));
            } else {
                logMsg = Component.literal(victimName).withStyle(ChatFormatting.WHITE)
                        .append(Component.literal(" has bled out").withStyle(ChatFormatting.GRAY));
            }
            player.server.getPlayerList().broadcastSystemMessage(logMsg, false);
        }

        if (killerName != null && !killerName.isEmpty()) {
            ServerPlayer killer = player.server.getPlayerList().getPlayerByName(killerName);
            if (killer != null) {
                // Устанавливаем убийцу как "последнего ударившего", чтобы ваниль сама выдала килл
                player.setLastHurtByPlayer(killer);

                // FIX: getLastHurtByMob() тут не обновится (урон, вырубивший игрока, был отменён
                // event.setCanceled в onPlayerHurt), поэтому кастомную статистику (AAS_Stats_Kills)
                // начисляем здесь напрямую, пока убийца точно известен.
                if (killer != player && killer.getTeam() != player.getTeam()) {
                    com.example.aas.events.StatsHandler.addStats(killer, 2, 0, "Enemy Killed");
                    com.example.aas.events.StatsHandler.addKill(killer);
                }
            }
        }

        player.getPersistentData().remove("AAS_KnockedBy");
        player.getPersistentData().putBoolean("AAS_GivingUp", true);
        player.getPersistentData().putBoolean("AAS_IsDowned", false);

        PacketHandler.INSTANCE.send(net.minecraftforge.network.PacketDistributor.ALL.noArg(),
                new PacketSyncDownedState(player.getId(), false, true));

        player.kill();
    }

    @SubscribeEvent
    public static void onReviveInteract(PlayerInteractEvent.EntityInteract event) {
        // ВАЖНО: событие стреляет дважды за один клик (MAIN_HAND и OFF_HAND).
        // Без этой проверки tryCarryPlayer вызывается 2 раза подряд и сразу же "отпускает" союзника.
        if (event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND) return;

        if (!(event.getTarget() instanceof ServerPlayer target)) return;
        ServerPlayer reviver = (ServerPlayer) event.getEntity();

        if (target.getPersistentData().getBoolean("AAS_IsDowned")) {
            // Проверка на команду
            if (reviver.getTeam() == null || target.getTeam() == null ||
                    !reviver.getTeam().isAlliedTo(target.getTeam())) {
                if (!reviver.level().isClientSide) {
                    reviver.displayClientMessage(Component.translatable("aas.msg.revive_enemy_error").withStyle(ChatFormatting.RED), true);
                }
                event.setCanceled(true);
                return;
            }

            // Shift + ПКМ = взять/отпустить раненого (перетаскивание)
            if (reviver.isShiftKeyDown()) {
                event.setCanceled(true);
                tryCarryPlayer(reviver, target);
                return;
            }

            // Обычный клик подъём больше не запускает - теперь нужно подойти и ЗАЖАТЬ клавишу
            // подъёма (REVIVE_KEY, по умолчанию F). Логика самого подъёма живёт в
            // setReviveHold()/tickReviveChannel() и управляется пакетом PacketReviveHold.
            event.setCanceled(true);
        }
    }

    /**
     * Вызывается на сервере из PacketReviveHold. Клиент шлёт этот пакет ТОЛЬКО при смене состояния:
     *  - holding=true  = START (нажали клавишу и рядом есть цель; может повторяться раз в 0.5 с,
     *                    пока сервер не подтвердил подъём полоской прогресса);
     *  - holding=false = CANCEL (отпустили клавишу / потеряли цель).
     * Невалидная цель/дистанция/предмет снимает канал. Валидная цель создаёт канал (если его ещё нет);
     * повторный START на ту же цель прогресс не трогает. Сам таймер подъёма считает сервер
     * в tickReviveChannel() - клиенту время не доверяется.
     */
    public static void setReviveHold(ServerPlayer reviver, boolean holding, int targetEntityId) {
        if (!holding) {
            removeReviveChannel(reviver);
            return;
        }

        if (reviver.getPersistentData().getBoolean("AAS_IsDowned")) {
            removeReviveChannel(reviver);
            return;
        }

        // Нельзя одновременно тащить раненого и поднимать другого
        if (DRAGGING.containsKey(reviver.getUUID())) {
            removeReviveChannel(reviver);
            return;
        }

        Entity targetEntity = reviver.level().getEntity(targetEntityId);
        if (!(targetEntity instanceof ServerPlayer target) || !target.isAlive()
                || !target.getPersistentData().getBoolean("AAS_IsDowned")) {
            removeReviveChannel(reviver);
            return;
        }

        if (reviver.getTeam() == null || target.getTeam() == null || !reviver.getTeam().isAlliedTo(target.getTeam())) {
            removeReviveChannel(reviver);
            return;
        }

        if (reviver.distanceTo(target) > REVIVE_RANGE_SERVER) {
            removeReviveChannel(reviver);
            return;
        }

        String reviveItemName = AASConfig.REVIVE_ITEM.get();
        Item reviveItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation(reviveItemName));
        if (reviveItem == null || (reviver.getMainHandItem().getItem() != reviveItem && reviver.getOffhandItem().getItem() != reviveItem)) {
            removeReviveChannel(reviver);
            return;
        }

        ReviveChannel channel = REVIVE_CHANNELS.get(reviver.getUUID());
        if (channel != null && channel.targetUUID.equals(target.getUUID())) {
            // Канал на эту цель уже идёт (повторный START от клиента) - ничего не трогаем,
            // набранный прогресс сохраняется.
            return;
        }
        // Другая цель (или канала не было): гасим старую полоску и начинаем с нуля
        removeReviveChannel(reviver);
        REVIVE_CHANNELS.put(reviver.getUUID(), new ReviveChannel(target.getUUID()));
    }

    /**
     * Ежетиковая обработка активного канала подъёма для данного игрока (если он сейчас поднимает).
     * Вызывается из onPlayerTick.
     */
    private static void tickReviveChannel(ServerPlayer reviver) {
        ReviveChannel channel = REVIVE_CHANNELS.get(reviver.getUUID());
        if (channel == null) return;

        // Канал живёт, пока сервер сам видит, что условия выполняются (цель в ноке, дистанция,
        // предмет в руке). Таймаута по "пингам" клиента больше нет: канал снимают только
        // явный CANCEL (holding=false), выход игрока, смерть или нарушение условий ниже.
        ServerPlayer target = reviver.server.getPlayerList().getPlayer(channel.targetUUID);
        if (!reviver.isAlive() || target == null || !target.isAlive() || target.level() != reviver.level()
                || !target.getPersistentData().getBoolean("AAS_IsDowned")
                || reviver.getPersistentData().getBoolean("AAS_IsDowned")
                || reviver.distanceTo(target) > REVIVE_RANGE_SERVER) {
            removeReviveChannel(reviver);
            return;
        }

        String reviveItemName = AASConfig.REVIVE_ITEM.get();
        Item reviveItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation(reviveItemName));
        if (reviveItem == null || (reviver.getMainHandItem().getItem() != reviveItem && reviver.getOffhandItem().getItem() != reviveItem)) {
            removeReviveChannel(reviver);
            return;
        }

        channel.progressTicks++;

        boolean isMedic = "Medic".equalsIgnoreCase(reviver.getPersistentData().getString("AAS_CurrentKit"));
        int requiredTicks = (isMedic
                ? AASConfig.MEDIC_REVIVE_HOLD_SECONDS.get()
                : AASConfig.REVIVE_HOLD_SECONDS.get()) * 20;

        // Прогресс теперь идёт полоской в HUD, а не текстом с процентами в actionbar
        float progressFraction = Math.min(1.0f, (float) channel.progressTicks / requiredTicks);
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> reviver), new PacketReviveProgress(progressFraction, false));
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> target), new PacketReviveProgress(progressFraction, true));

        // Звук перевязки/подъёма на сервере - слышат все игроки поблизости, повторяется раз в секунду,
        // пока идёт подъём (как со звуком копки лопатой при постройке)
        if (channel.progressTicks % 20 == 1) {
            target.level().playSound(null, target.getX(), target.getY(), target.getZ(),
                    REVIVE_SOUND, SoundSource.PLAYERS, 1.0F, 1.0F);
        }

        if (channel.progressTicks >= requiredTicks) {
            REVIVE_CHANNELS.remove(reviver.getUUID());
            // Полоски прогресса гасим явно, т.к. removeReviveChannel сюда не годится -
            // канал уже удалён и finishRevive сам переведёт target в состояние "не в ноке"
            PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> reviver), new PacketReviveProgress(-1f, false));
            PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> target), new PacketReviveProgress(-1f, true));
            finishRevive(reviver, target);
        }
    }

    // Если поднимающий погиб - канал подъёма больше не актуален
    @SubscribeEvent
    public static void onReviverDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            removeReviveChannel(sp);
        }
    }

    private static void finishRevive(ServerPlayer reviver, ServerPlayer target) {
        revivePlayer(target);

        if (!reviver.isCreative()) {
            String reviveItemName = AASConfig.REVIVE_ITEM.get();
            Item reviveItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation(reviveItemName));
            if (reviver.getMainHandItem().getItem() == reviveItem) {
                reviver.getMainHandItem().shrink(1);
            } else if (reviver.getOffhandItem().getItem() == reviveItem) {
                reviver.getOffhandItem().shrink(1);
            }
        }
        reviver.displayClientMessage(Component.translatable("aas.msg.revive_success").withStyle(ChatFormatting.GREEN), true);

        // --- Очки за поднятие ---
        int s1 = reviver.getPersistentData().getInt("AAS_SquadID");
        int s2 = target.getPersistentData().getInt("AAS_SquadID");

        if (s1 != 0 && s1 == s2) {
            // В одном отряде - даем и командные и отрядные очки
            com.example.aas.events.StatsHandler.addStats(reviver, 10, 15, "Squad Revive");
        } else {
            // Разные отряды - только командные
            com.example.aas.events.StatsHandler.addStats(reviver, 10, 0, "Revived Teammate");
        }
    }

    public static void revivePlayer(ServerPlayer player) {
        clearDragIfTarget(player); // если раненого тащили - отпускаем при подъеме
        clearReviveChannelsIfTarget(player); // если его поднимал кто-то ещё параллельно - снимаем канал

        player.getPersistentData().putBoolean("AAS_IsDowned", false);
        // Сбрасываем время начала нокаута, чтобы при следующей смерти таймер начался с нуля
        player.getPersistentData().remove("AAS_DownedTick");
        player.getPersistentData().remove("AAS_GivingUp");

        // Запоминаем время оживления (нужно для защиты от мгновенного повторного нока)
        player.getPersistentData().putLong("AAS_LastReviveTime", player.level().getGameTime());

        player.setHealth(2.0f); // 1 сердечко после подъема
        player.setPose(Pose.STANDING);

        // Синхронизация с клиентом (пакет заставит клиент сбросить свой ClientData.globalDeathTimestamp)
        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new PacketSyncDownedState(player.getId(), false));
    }


    // ================== DRAG / CARRY: ЛОГИКА ==================

    /**
     * Shift+ПКМ по раненому: если раненого ещё никто не тащит - берём.
     * Если это уже наша "ноша" - отпускаем.
     */
    public static void tryCarryPlayer(ServerPlayer carrier, ServerPlayer target) {
        if (carrier.getPersistentData().getBoolean("AAS_IsDowned")) return;

        UUID currentTarget = DRAGGING.get(carrier.getUUID());
        if (currentTarget != null && currentTarget.equals(target.getUUID())) {
            stopDragging(carrier, Component.translatable("aas.msg.drag_stop"));
            return;
        }

        if (DRAGGING.containsKey(carrier.getUUID())) return;
        if (DRAGGING.containsValue(target.getUUID())) return;

        // Нельзя одновременно поднимать (держать F) и тащить
        removeReviveChannel(carrier);

        DRAGGING.put(carrier.getUUID(), target.getUUID());
        DRAG_START_YAW.put(carrier.getUUID(), carrier.getYRot());

        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> carrier), new PacketSyncDragState(true));

        carrier.displayClientMessage(Component.translatable("aas.msg.carry_start").withStyle(ChatFormatting.YELLOW), true);
        target.displayClientMessage(Component.translatable("aas.msg.being_carried").withStyle(ChatFormatting.YELLOW), true);
    }

    public static void stopDragging(ServerPlayer carrier, @Nullable Component reason) {
        UUID removed = DRAGGING.remove(carrier.getUUID());
        DRAG_START_YAW.remove(carrier.getUUID());
        if (removed != null) {
            PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> carrier), new PacketSyncDragState(false));
            if (reason != null) {
                carrier.displayClientMessage(reason.copy().withStyle(ChatFormatting.RED), true);
            }
        }
    }

    /**
     * Если данный игрок был чьей-то "ношей" (его тащили) - убираем эту связь.
     * Вызывается при подъеме, добивании, дисконнекте раненого.
     */
    private static void clearDragIfTarget(ServerPlayer target) {
        DRAGGING.entrySet().removeIf(e -> e.getValue().equals(target.getUUID()));
        DRAG_START_YAW.keySet().retainAll(DRAGGING.keySet());
    }

    /**
     * Снимает канал подъёма для конкретного поднимающего (reviver) и явно гасит
     * полоску прогресса на клиенте: и у самого reviver-а, и у его цели (если она известна).
     */
    private static void removeReviveChannel(ServerPlayer reviver) {
        ReviveChannel channel = REVIVE_CHANNELS.remove(reviver.getUUID());
        if (channel == null) return;

        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> reviver), new PacketReviveProgress(-1f, false));

        ServerPlayer target = reviver.server.getPlayerList().getPlayer(channel.targetUUID);
        if (target != null) {
            PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> target), new PacketReviveProgress(-1f, true));
        }
    }

    /**
     * Если данный игрок был чьей-то целью подъёма - снимаем все каналы, нацеленные на него,
     * и гасим полоску прогресса у того, кто поднимал (и у самой цели).
     * Вызывается при подъёме, добивании (Give Up), дисконнекте раненого.
     */
    private static void clearReviveChannelsIfTarget(ServerPlayer target) {
        REVIVE_CHANNELS.entrySet().removeIf(e -> {
            if (!e.getValue().targetUUID.equals(target.getUUID())) return false;

            ServerPlayer reviver = target.server.getPlayerList().getPlayer(e.getKey());
            if (reviver != null) {
                PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> reviver), new PacketReviveProgress(-1f, false));
            }
            PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> target), new PacketReviveProgress(-1f, true));
            return true;
        });
    }

    @SubscribeEvent
    public static void onDragTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;
        if (!(event.player instanceof ServerPlayer carrier)) return;

        UUID targetId = DRAGGING.get(carrier.getUUID());
        if (targetId == null) return;

        ServerPlayer target = carrier.server.getPlayerList().getPlayer(targetId);

        if (target == null || !target.isAlive() || target.level() != carrier.level()
                || !target.getPersistentData().getBoolean("AAS_IsDowned")
                || carrier.getPersistentData().getBoolean("AAS_IsDowned")
                || carrier.distanceTo(target) > DRAG_MAX_DISTANCE) {
            stopDragging(carrier, null);
            return;
        }

        if (!carrier.isShiftKeyDown()) {
            stopDragging(carrier, Component.translatable("aas.msg.drag_stop"));
            return;
        }

        float yawDelta = Math.abs(Mth.wrapDegrees(carrier.getYRot() - carrier.yRotO));
        float startYaw = DRAG_START_YAW.getOrDefault(carrier.getUUID(), carrier.getYRot());
        float totalTurn = Math.abs(Mth.wrapDegrees(carrier.getYRot() - startYaw));

        if (yawDelta > TURN_SNAP_THRESHOLD || totalTurn > TURN_TOTAL_THRESHOLD) {
            stopDragging(carrier, Component.translatable("aas.msg.drag_lost"));
            return;
        }

        // Кладём раненого прямо перед собой, лицом друг к другу
        Vec3 forward = Vec3.directionFromRotation(0, carrier.getYRot());
        Vec3 desiredPos = carrier.position().add(forward.scale(DRAG_DISTANCE));
        float targetYaw = carrier.getYRot() + 180f;

        target.connection.teleport(desiredPos.x, carrier.getY(), desiredPos.z, targetYaw, target.getXRot());
        target.setYHeadRot(targetYaw);
        target.setDeltaMovement(0, target.getDeltaMovement().y, 0);
        target.fallDistance = 0;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // === ЗАЩИТА ОТ "ФАНТОМНОЙ" ТЕХНИКИ (ванильный RootVehicle) ===
        Entity vehicle = player.getVehicle();
        if (vehicle != null) {
            AASWorldData data = AASWorldData.get(player.serverLevel());
            boolean isKnownVehicle = data.markedVehicles.stream()
                    .anyMatch(v -> v.uuid.equals(vehicle.getUUID()));

            player.stopRiding();

            if (!isKnownVehicle) {
                vehicle.discard();
            }
        }

        // ВОССТАНОВЛЕНИЕ НОКА ПОСЛЕ ПЕРЕЗАХОДА (ваш прежний код):
        if (player.getPersistentData().getBoolean("AAS_IsDowned")) {
            player.getPersistentData().putInt("AAS_DownedResyncDelay", 20);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            stopDragging(sp, null);
            clearDragIfTarget(sp);
            REVIVE_CHANNELS.remove(sp.getUUID());
            clearReviveChannelsIfTarget(sp);

            if (sp.getVehicle() != null) {
                sp.stopRiding();
            }

            PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),
                    new PacketSyncDownedState(sp.getId(), false, true));
        }
    }
}