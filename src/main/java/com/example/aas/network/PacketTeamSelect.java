// PATH: src\main\java\com\example\aas\network\PacketTeamSelect.java
package com.example.aas.network;

import com.example.aas.world.AASWorldData;
import net.minecraft.ChatFormatting;
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

                // 1. Выход из текущего отряда (если он был)
                // Этот метод (после обновления в PacketSquadAction) очистит теги отряда
                PacketSquadAction.leaveCurrentSquad(player, data);

                // 2. ПРИНУДИТЕЛЬНЫЙ СБРОС КИТА И ИНВЕНТАРЯ ПРИ СМЕНЕ КОМАНДЫ
                // Сбрасываем текущий кит на "не назначен"
                player.getPersistentData().putString("AAS_CurrentKit", "Unassigned");
                player.getPersistentData().remove("AAS_PendingKit");

                // Полностью очищаем инвентарь (чтобы не переносить оружие другой стороны)
                player.getInventory().clearContent();
                // Синхронизируем изменения инвентаря с клиентом
                player.inventoryMenu.broadcastChanges();

                data.setDirty();

                // Синхронизируем состояние отрядов для всех игроков в этом мире
                PacketHandler.INSTANCE.send(
                        PacketDistributor.DIMENSION.with(player.level()::dimension),
                        new PacketSyncSquads(data.squads)
                );

                // 3. Добавление в Scoreboard (глобально для сервера)
                Scoreboard scoreboard = player.getServer().getScoreboard();
                String internalTeamName = msg.teamName.equalsIgnoreCase("BLUE") ? "Blue" : "Red";
                ChatFormatting color = msg.teamName.equalsIgnoreCase("BLUE") ? ChatFormatting.BLUE : ChatFormatting.RED;

                PlayerTeam team = scoreboard.getPlayerTeam(internalTeamName);
                if (team == null) {
                    team = scoreboard.addPlayerTeam(internalTeamName);
                    team.setColor(color);
                    team.setSeeFriendlyInvisibles(true);
                }

                // Добавляем игрока в команду Scoreboard
                scoreboard.addPlayerToTeam(player.getScoreboardName(), team);

                // Уведомление игрока
                player.sendSystemMessage(Component.literal("You joined the " + internalTeamName + " team! Kit and inventory reset.")
                        .withStyle(color));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}