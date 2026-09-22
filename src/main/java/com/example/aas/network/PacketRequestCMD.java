package com.example.aas.network;

import com.example.aas.world.AASWorldData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class PacketRequestCMD {
    public PacketRequestCMD() {}
    public static void encode(PacketRequestCMD msg, FriendlyByteBuf buf) {}
    public static PacketRequestCMD decode(FriendlyByteBuf buf) { return new PacketRequestCMD(); }

    public static void handle(PacketRequestCMD msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || player.getTeam() == null) return;

            AASWorldData data = AASWorldData.get(player.serverLevel());
            String pName = player.getScoreboardName();
            String team = player.getTeam().getName().toUpperCase();
            boolean isBlue = team.equals("BLUE");

            // 1. Проверяем, идет ли уже голосование В ЭТОЙ команде
            boolean alreadyVoting = isBlue ? data.blueCmdVoteActive : data.redCmdVoteActive;
            if (alreadyVoting) return;

            // 2. Проверяем, есть ли уже командир В ЭТОЙ команде
            int currentCMD = isBlue ? data.blueCMDId : data.redCMDId;
            if (currentCMD != -1) return;

            // 3. Только лидеры отрядов могут подавать заявку
            if (player.getPersistentData().getBoolean("AAS_IsSquadLeader")) {
                if (isBlue) {
                    data.blueCmdVoteActive = true;
                    data.blueCmdCandidateName = pName;
                    data.blueCmdCandidateId = player.getPersistentData().getInt("AAS_SquadID");
                    data.blueCmdVoteTimer = 600; // 30 секунд
                    data.blueCmdVotes.clear();
                } else {
                    data.redCmdVoteActive = true;
                    data.redCmdCandidateName = pName;
                    data.redCmdCandidateId = player.getPersistentData().getInt("AAS_SquadID");
                    data.redCmdVoteTimer = 600;
                    data.redCmdVotes.clear();
                }

                data.setDirty();
                // Рассылаем всем, так как данные команд разделены внутри пакета синхронизации
                PacketHandler.sendToAllClients(player.serverLevel(), data);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}