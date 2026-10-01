package com.example.aas.events;

import com.example.aas.network.PacketHandler;
import com.example.aas.network.PacketTeamKillNotification;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.scores.Team;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/** Серверная логика: определяет тимкилл/тимнок и рассылает уведомления убийце и жертве. */
public final class TeamKillNotifier {

    /** Настоящая смерть игрока. */
    public static void onPlayerDeath(ServerPlayer victim, DamageSource source, boolean wasGivingUp) {
        // Игрок сам нажал "Give Up" в нокауте — это не убийство.
        if (wasGivingUp) return;

        ServerPlayer found = null;
        if (source != null && source.getEntity() instanceof ServerPlayer direct) {
            found = direct;
        } else if (victim.getLastHurtByMob() instanceof ServerPlayer lastAttacker) {
            found = lastAttacker;
        }

        notifyIfAllied(victim, found, false);
    }

    /** Игрока уложили в нок (вызывается из DownedHandler.onPlayerHurt). */
    public static void onPlayerKnocked(ServerPlayer victim, @Nullable ServerPlayer attacker) {
        notifyIfAllied(victim, attacker, true);
    }

    private static void notifyIfAllied(ServerPlayer victim, @Nullable ServerPlayer killer, boolean knocked) {
        if (killer == null || killer == victim) return;

        Team killerTeam = killer.getTeam();
        Team victimTeam = victim.getTeam();
        if (killerTeam == null || victimTeam == null || !killerTeam.isAlliedTo(victimTeam)) return;

        String killerName = killer.getGameProfile().getName();
        String victimName = victim.getGameProfile().getName();

        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> killer),
                new PacketTeamKillNotification(victimName, true, knocked));
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> victim),
                new PacketTeamKillNotification(killerName, false, knocked));
    }

    private TeamKillNotifier() {
    }
}