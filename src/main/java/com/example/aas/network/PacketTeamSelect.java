package com.example.aas.network;

import com.example.aas.config.AASConfig;
import com.example.aas.world.AASWorldData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public class PacketTeamSelect {
    private final String teamName;

    public PacketTeamSelect(String teamName) {
        this.teamName = teamName;
    }

    public static void encode(PacketTeamSelect msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.teamName);
    }

    public static PacketTeamSelect decode(FriendlyByteBuf buf) {
        return new PacketTeamSelect(buf.readUtf());
    }

    public static void handle(PacketTeamSelect msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                AASWorldData data = AASWorldData.get(player.serverLevel());

                // Получаем красивые названия фракций
                String blueName = getFactionDisplayName(data.blueFaction, true);
                String redName = getFactionDisplayName(data.redFaction, false);

                // === НОВАЯ ЛОГИКА АВТОБАЛАНСА ===
                if (AASConfig.AUTO_BALANCE_TEAMS.get() && !player.isCreative()) {
                    int blueCount = 0;
                    int redCount = 0;

                    // Считаем игроков (исключая самого отправителя, чтобы он не посчитал себя)
                    for (ServerPlayer p : player.server.getPlayerList().getPlayers()) {
                        if (p == player) continue;
                        if (p.getTeam() != null) {
                            if (p.getTeam().getName().equalsIgnoreCase("Blue")) blueCount++;
                            else if (p.getTeam().getName().equalsIgnoreCase("Red")) redCount++;
                        }
                    }

                    if (msg.teamName.equalsIgnoreCase("BLUE") && blueCount > redCount) {
                        player.sendSystemMessage(Component.translatable("aas.msg.unbalanced_teams", blueName, redName)
                                .withStyle(ChatFormatting.RED));
                        return; // Прерываем
                    }
                    else if (msg.teamName.equalsIgnoreCase("RED") && redCount > blueCount) {
                        player.sendSystemMessage(Component.literal("Teams are unbalanced! You cannot join " + redName + ". Please join " + blueName + ".")
                                .withStyle(ChatFormatting.RED));
                        return; // Прерываем
                    }
                }
                // ================================

                // 1. Выход из текущего отряда (если он был)
                PacketSquadAction.leaveCurrentSquad(player, data);

                // 2. ПРИНУДИТЕЛЬНЫЙ СБРОС КИТА И ИНВЕНТАРЯ ПРИ СМЕНЕ КОМАНДЫ
                player.getPersistentData().putString("AAS_CurrentKit", "Unassigned");
                com.example.aas.network.PacketHandler.INSTANCE.send(
                        net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                        new com.example.aas.network.PacketSyncMyKit("Unassigned"));
                player.getPersistentData().remove("AAS_PendingKit");
                player.getInventory().clearContent();
                com.example.aas.network.ResupplyHandler.clearCurios(player);
                player.inventoryMenu.broadcastChanges();

                data.setDirty();
                PacketHandler.INSTANCE.send(PacketDistributor.DIMENSION.with(player.level()::dimension), new PacketSyncSquads(data.squads));

                // 3. Добавление в Scoreboard
                Scoreboard scoreboard = player.getServer().getScoreboard();
                String internalTeamName = msg.teamName.equalsIgnoreCase("BLUE") ? "Blue" : "Red";
                ChatFormatting color = msg.teamName.equalsIgnoreCase("BLUE") ? ChatFormatting.BLUE : ChatFormatting.RED;

                PlayerTeam team = scoreboard.getPlayerTeam(internalTeamName);
                if (team == null) {
                    team = scoreboard.addPlayerTeam(internalTeamName);
                    team.setColor(color);
                    team.setSeeFriendlyInvisibles(true);
                }

                scoreboard.addPlayerToTeam(player.getScoreboardName(), team);

                if (data.isGameStarted) {
                    String currentDim = player.level().dimension().location().toString();
                    BlockPos mainSpawn = msg.teamName.equalsIgnoreCase("BLUE") ? data.blueSpawns.get(currentDim) : data.redSpawns.get(currentDim);

                    if (mainSpawn != null) {
                        player.teleportTo(mainSpawn.getX() + 0.5, mainSpawn.getY(), mainSpawn.getZ() + 0.5);
                        player.sendSystemMessage(Component.literal("Match in progress! Teleporting to Main Base...")
                                .withStyle(ChatFormatting.YELLOW));
                    } else {
                        player.sendSystemMessage(Component.literal("Warning: Main Base spawn point is not set for this team!")
                                .withStyle(ChatFormatting.RED));
                    }
                }

                // Уведомление с правильным названием фракции!
                String joinedFactionName = msg.teamName.equalsIgnoreCase("BLUE") ? blueName : redName;
                player.sendSystemMessage(Component.translatable("aas.msg.joined_team", joinedFactionName)
                        .withStyle(color));
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static String getFactionDisplayName(String faction, boolean isBlue) {
        if (faction == null || faction.equals("none") || faction.equals("bluefor") || faction.equals("redfor")) {
            return isBlue ? AASConfig.BLUE_TEAM_CUSTOM_NAME.get() : AASConfig.RED_TEAM_CUSTOM_NAME.get();
        }
        return faction.toUpperCase().replace("_", " ");
    }
}