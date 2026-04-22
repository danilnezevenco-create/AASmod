package com.example.aas.network;

import com.example.aas.block.ModBlocks;
import com.example.aas.world.AASWorldData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketDebugSpawnRally {
    private final String team;

    public PacketDebugSpawnRally(String team) {
        this.team = team;
    }

    public static void encode(PacketDebugSpawnRally msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.team);
    }

    public static PacketDebugSpawnRally decode(FriendlyByteBuf buf) {
        return new PacketDebugSpawnRally(buf.readUtf());
    }

    public static void handle(PacketDebugSpawnRally msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                BlockPos pos = player.blockPosition();
                AASWorldData data = AASWorldData.get(player.serverLevel().getServer().overworld());

                Scoreboard scoreboard = player.getServer().getScoreboard();
                String teamName = msg.team.equalsIgnoreCase("BLUE") ? "Blue" : "Red";
                PlayerTeam pTeam = scoreboard.getPlayerTeam(teamName);
                if (pTeam == null) pTeam = scoreboard.addPlayerTeam(teamName);
                scoreboard.addPlayerToTeam(player.getScoreboardName(), pTeam);

                if (msg.team.equals("BLUE")) {
                    player.level().setBlock(pos, ModBlocks.BLUE_RALLY_BLOCK.get().defaultBlockState(), 3);
                    data.blueRallies.add(pos);
                    player.sendSystemMessage(Component.literal("DEBUG: BLUE Rally spawned at feet & Joined Blue Team").withStyle(ChatFormatting.BLUE));
                } else {
                    player.level().setBlock(pos, ModBlocks.RED_RALLY_BLOCK.get().defaultBlockState(), 3);
                    data.redRallies.add(pos);
                    player.sendSystemMessage(Component.literal("DEBUG: RED Rally spawned at feet & Joined Red Team").withStyle(ChatFormatting.RED));
                }

                data.setDirty();

                // FIXED: Use new helper method
                PacketHandler.sendToAllClients(data, false, false, false, false);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}