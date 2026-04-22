// FILE: DownedHandler.java
// PATH: src\main\java\com\example\aas\network\DownedHandler.java
package com.example.aas.events;

import com.example.aas.config.AASConfig;
import com.example.aas.network.PacketHandler;
import com.example.aas.network.PacketSyncDownedState;
import com.example.aas.sound.ModSounds;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

@Mod.EventBusSubscriber(modid = "aas")
public class DownedHandler {

    @SubscribeEvent
    public static void onPlayerHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!AASConfig.ENABLE_KNOCKOUT.get()) return;

        if (event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        if (player.getPersistentData().getBoolean("AAS_GivingUp")) return;
        if (player.getPersistentData().getBoolean("AAS_IsDowned")) {
            if (event.getSource().is(DamageTypeTags.IS_EXPLOSION)) return;
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
                player.getPersistentData().putString("AAS_KnockedBy", attackingPlayer.getScoreboardName());
            } else {
                player.getPersistentData().remove("AAS_KnockedBy");
            }

            enterDownedState(player);
        }
    }

    public static void enterDownedState(ServerPlayer player) {
        player.getPersistentData().putBoolean("AAS_IsDowned", true);

        // НОВОЕ: Запоминаем тик, когда игрок упал в нокаут
        player.getPersistentData().putLong("AAS_DownedTick", player.level().getGameTime());

        player.setHealth(6.0f); // Даем немного ХП в ноке
        player.setPose(Pose.SWIMMING);

        // ИЗДАЕМ ЗВУК ПАДЕНИЯ (слышат все вокруг)
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSounds.PLAYER_DOWNED.get(), net.minecraft.sounds.SoundSource.PLAYERS, 1.5F, 1.0F);

        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new PacketSyncDownedState(player.getId(), true));
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) return;

        if (event.player.getPersistentData().getBoolean("AAS_IsDowned")) {
            ServerPlayer player = (ServerPlayer) event.player;
            player.setPose(Pose.SWIMMING); // Поддерживаем позу лежа

            // Полная остановка движения
            player.setDeltaMovement(0, player.getDeltaMovement().y, 0);

            // НОВОЕ: Автоматический Give Up через 3 минуты (3 * 60 * 20 = 3600 тиков)
            long downedTick = player.getPersistentData().getLong("AAS_DownedTick");
            if (player.level().getGameTime() - downedTick >= 3600) {
                forceGiveUp(player);
            }
        }
    }

    // НОВОЕ: Вынесенный метод для убийства игрока, чтобы вызывать его и по кнопке, и по таймеру
    public static void forceGiveUp(ServerPlayer player) {
        String victimName = player.getScoreboardName();
        String killerName = player.getPersistentData().getString("AAS_KnockedBy");

        // ПРОВЕРКА: Учитываем ванильное правило отображения смертей
        boolean showMessages = player.serverLevel().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_SHOWDEATHMESSAGES);

        if (showMessages) {
            net.minecraft.network.chat.MutableComponent logMsg;

            if (killerName != null && !killerName.isEmpty()) {
                logMsg = Component.literal(victimName)
                        .withStyle(ChatFormatting.WHITE)
                        .append(Component.literal(" was finished by ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal(killerName).withStyle(ChatFormatting.GOLD));
            } else {
                logMsg = Component.literal(victimName)
                        .withStyle(ChatFormatting.WHITE)
                        .append(Component.literal(" has bled out").withStyle(ChatFormatting.GRAY));
            }
            // Рассылаем, только если правило включено
            player.server.getPlayerList().broadcastSystemMessage(logMsg, false);
        }

        // Статистику убийце начисляем в любом случае (даже если чат молчит)
        if (killerName != null && !killerName.isEmpty()) {
            ServerPlayer killer = player.server.getPlayerList().getPlayerByName(killerName);
            if (killer != null) {
                killer.awardStat(net.minecraft.stats.Stats.PLAYER_KILLS);
            }
        }

        // Очистка и смерть
        player.getPersistentData().remove("AAS_KnockedBy");
        player.getPersistentData().putBoolean("AAS_GivingUp", true);
        player.getPersistentData().putBoolean("AAS_IsDowned", false);

        player.kill();
    }

    @SubscribeEvent
    public static void onReviveInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof ServerPlayer target)) return;
        ServerPlayer reviver = (ServerPlayer) event.getEntity();

        if (target.getPersistentData().getBoolean("AAS_IsDowned")) {
            if (reviver.getTeam() == null || target.getTeam() == null ||
                    !reviver.getTeam().isAlliedTo(target.getTeam())) {

                if (!reviver.level().isClientSide) {
                    reviver.displayClientMessage(Component.literal("You cannot revive an ENEMY!")
                            .withStyle(ChatFormatting.RED), true);
                }
                event.setCanceled(true);
                return;
            }

            ItemStack held = reviver.getItemInHand(event.getHand());
            String reviveItemName = AASConfig.REVIVE_ITEM.get();
            Item reviveItem = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(new ResourceLocation(reviveItemName));

            if (held.getItem() == reviveItem) {
                revivePlayer(target);
                if (!reviver.isCreative()) held.shrink(1);

                reviver.displayClientMessage(Component.literal("Teammate revived!")
                        .withStyle(ChatFormatting.GREEN), true);
            }
        }
    }

    public static void revivePlayer(ServerPlayer player) {
        player.getPersistentData().putBoolean("AAS_IsDowned", false);
        player.getPersistentData().putLong("AAS_LastReviveTime", player.level().getGameTime());
        player.setHealth(2.0f); // 3 сердца
        player.setPose(net.minecraft.world.entity.Pose.STANDING);

        PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new PacketSyncDownedState(player.getId(), false));
    }
}