// PATH: src\main\java\com\example\aas\network\PacketSquadAction.java
package com.example.aas.network;

import com.example.aas.config.AASConfig;
import com.example.aas.item.ModItems;
import com.example.aas.world.AASWorldData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import com.example.aas.util.SquadPlaytimeHelper;
import java.util.ArrayList;

import java.util.function.Supplier;

public class PacketSquadAction {
    private final int action;
    private final int squadId;
    private final String stringData;

    public PacketSquadAction(int action, int squadId, String stringData) {
        this.action = action;
        this.squadId = squadId;
        this.stringData = stringData;
    }

    public static void encode(PacketSquadAction msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.action);
        buf.writeInt(msg.squadId);
        buf.writeUtf(msg.stringData);
    }

    public static PacketSquadAction decode(FriendlyByteBuf buf) {
        return new PacketSquadAction(buf.readInt(), buf.readInt(), buf.readUtf());
    }

    private static final String[] NATO_ALPHABET = {
            "Alpha", "Bravo", "Charlie", "Delta", "Echo", "Foxtrot", "Golf", "Hotel", "India",
            "Juliett", "Kilo", "Lima", "Mike", "November", "Oscar", "Papa", "Quebec", "Romeo",
            "Sierra", "Tango", "Uniform", "Victor", "Whiskey", "X-ray", "Yankee", "Zulu"
    };

    public static void handle(PacketSquadAction msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            AASWorldData data = AASWorldData.get(player.serverLevel());

            String pName = player.getScoreboardName();
            String pTeam = (player.getTeam() != null) ? player.getTeam().getName() : "NEUTRAL";

            if (pTeam.equals("NEUTRAL") && !player.isCreative()) return;

            // === 0. CREATE SQUAD ===
            if (msg.action == 0) {
                leaveCurrentSquad(player, data);

                String rawName = msg.stringData.trim();
                if (rawName.length() > 12) rawName = rawName.substring(0, 12);
                String finalName = rawName.isEmpty() ? getAvailableSquadName(data, pTeam) : rawName;

                int newId = 0;
                for (int i = 1; i < 1000; i++) {
                    boolean idTaken = false;
                    for(AASWorldData.Squad s : data.squads) { if(s.id == i) { idTaken = true; break; } }
                    if(!idTaken) { newId = i; break; }
                }

                String currentDim = player.level().dimension().location().toString();

                AASWorldData.Squad newSquad = new AASWorldData.Squad(newId, finalName, pTeam, pName, currentDim);
                newSquad.members.add(pName);
                data.squads.add(newSquad);
                com.example.aas.voicechat.VoicechatCompat.createAndJoinGroup(player, newId, finalName);
                updatePlayerTags(player, newId, true);

                if (AASConfig.AUTO_GIVE_SL_RADIO.get()) {
                    giveRadio(player);
                }

                player.sendSystemMessage(Component.translatable("aas.msg.squad_created", finalName).withStyle(ChatFormatting.GOLD));
            }
            // === 1. JOIN ===
            else if (msg.action == 1) {
                for (AASWorldData.Squad s : data.squads) {
                    if (s.id == msg.squadId && s.team.equalsIgnoreCase(pTeam)) {
                        if (s.isLocked && !player.isCreative()) {
                            player.sendSystemMessage(Component.literal("Squad is LOCKED!").withStyle(ChatFormatting.RED));
                            return;
                        }
                        if (s.members.size() >= 9) {
                            player.sendSystemMessage(Component.literal("Squad is full!").withStyle(ChatFormatting.RED));
                            return;
                        }
                        leaveCurrentSquad(player, data);

                        s.members.add(pName);
                        com.example.aas.voicechat.VoicechatCompat.joinGroup(player, s.id);
                        updatePlayerTags(player, s.id, false);
                        player.sendSystemMessage(Component.translatable("aas.msg.joined_squad", s.name).withStyle(ChatFormatting.GREEN));
                        long hours = SquadPlaytimeHelper.getPlaytimeHoursByName(player.server, s.leader);
                        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                                new PacketSquadLeaderPlaytime(s.leader, hours, true));
                        break;
                    }
                }
            }
            // === 2. LEAVE ===
            else if (msg.action == 2) {
                leaveCurrentSquad(player, data);
                player.sendSystemMessage(Component.translatable("aas.msg.left_squad").withStyle(ChatFormatting.YELLOW));
            }
            // === 3. KICK ===
            else if (msg.action == 3) {
                AASWorldData.Squad targetSquad = null;
                for(AASWorldData.Squad s : data.squads) { if(s.id == msg.squadId) { targetSquad = s; break; } }

                if (targetSquad != null) {
                    String targetName = msg.stringData;
                    ServerPlayer targetEntity = player.server.getPlayerList().getPlayerByName(targetName);

                    boolean isLeaderKicking = targetSquad.leader.equals(pName) && !targetName.equals(pName);
                    boolean isKickingOfflineLeader = targetName.equals(targetSquad.leader) && targetEntity == null && targetSquad.members.contains(pName);

                    if (isLeaderKicking || isKickingOfflineLeader) {
                        if (targetSquad.members.remove(targetName)) {
                            if (targetName.equals(targetSquad.leader) && !targetSquad.members.isEmpty()) {
                                targetSquad.removeFromFireteams(targetSquad.members.get(0));
                                targetSquad.leader = targetSquad.members.get(0);

                                ServerPlayer newLeader = player.server.getPlayerList().getPlayerByName(targetSquad.leader);
                                if (newLeader != null) {
                                    updatePlayerTags(newLeader, targetSquad.id, true);
                                    if (AASConfig.AUTO_GIVE_SL_RADIO.get()) giveRadio(newLeader);
                                    newLeader.sendSystemMessage(Component.literal("The previous leader was offline and removed. You are the new Leader!").withStyle(ChatFormatting.GOLD));
                                }
                            }

                            if (targetEntity != null) {
                                removePlayerTags(targetEntity);
                                removeRadio(targetEntity);
                                com.example.aas.voicechat.VoicechatCompat.leaveGroup(targetEntity);
                                targetEntity.displayClientMessage(Component.literal("You were kicked!").withStyle(ChatFormatting.RED), true);
                                PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> targetEntity),
                                        new PacketSquadLeaderPlaytime("", -1, false));
                            }

                            player.server.getPlayerList().broadcastSystemMessage(
                                    Component.literal("Offline leader " + targetName + " was removed from squad " + targetSquad.name).withStyle(ChatFormatting.YELLOW), false
                            );
                        }
                    }
                }
            }
            // === 4. PROMOTE ===
            else if (msg.action == 4) {
                AASWorldData.Squad mySquad = getPlayerSquad(pName, data);
                if (mySquad != null && mySquad.leader.equals(pName)) {
                    String targetName = msg.stringData;
                    if (mySquad.members.contains(targetName)) {

                        removeRadio(player);
                        mySquad.removeFromFireteams(targetName);
                        mySquad.leader = targetName;
                        long hoursForMembers = SquadPlaytimeHelper.getPlaytimeHoursByName(player.server, targetName);
                        for (String memberName : mySquad.members) {
                            if (memberName.equals(targetName)) continue; // сам новый лидер плашку не видит
                            ServerPlayer m = player.server.getPlayerList().getPlayerByName(memberName);
                            if (m != null) {
                                PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> m),
                                        new PacketSquadLeaderPlaytime(targetName, hoursForMembers, true));
                            }
                        }
                        updatePlayerTags(player, mySquad.id, false);

                        ServerPlayer target = player.server.getPlayerList().getPlayerByName(targetName);
                        if (target != null) {
                            updatePlayerTags(target, mySquad.id, true);

                            if (AASConfig.AUTO_GIVE_SL_RADIO.get()) {
                                giveRadio(target);
                            }
                            target.displayClientMessage(Component.translatable("aas.msg.promoted_sl").withStyle(ChatFormatting.GOLD), true);
                        }
                        // ПРИМЕЧАНИЕ: если targetName сейчас офлайн, его AAS_SquadID/AAS_IsSquadLeader
                        // не обновятся здесь (некому). mySquad.leader в мировых данных уже верный,
                        // но персистентные теги игрока рассинхронизируются до его следующего входа.
                        // Нужно досинхронизировать их в обработчике логина игрока (PlayerLoggedInEvent),
                        // иначе после захода этот игрок временно не будет проходить проверку amICommander/SL.

                        player.sendSystemMessage(Component.literal("Promoted " + targetName).withStyle(ChatFormatting.GOLD));
                    }
                }
            }
            // === 5. LOCK ===
            else if (msg.action == 5) {
                AASWorldData.Squad mySquad = getPlayerSquad(pName, data);
                if (mySquad != null && mySquad.leader.equals(pName)) {
                    mySquad.isLocked = !mySquad.isLocked;
                    String status = mySquad.isLocked ? "LOCKED" : "UNLOCKED";
                    ChatFormatting color = mySquad.isLocked ? ChatFormatting.RED : ChatFormatting.GREEN;
                    player.sendSystemMessage(Component.translatable(mySquad.isLocked ? "aas.msg.squad_locked" : "aas.msg.squad_unlocked").withStyle(color));
                }
            }
            // === 6. ASSIGN FTL BRAVO ===
            else if (msg.action == 6) {
                AASWorldData.Squad mySquad = getPlayerSquad(pName, data);
                if (mySquad != null && (mySquad.leader.equals(pName) || mySquad.bravoLeader.equals(pName))) {
                    String target = msg.stringData;
                    if (mySquad.members.contains(target) && !mySquad.leader.equals(target) && !mySquad.charlieLeader.equals(target)) {
                        mySquad.removeFromFireteams(target);
                        mySquad.bravoLeader = target;
                        if (!mySquad.bravoMembers.contains(target)) mySquad.bravoMembers.add(target);
                        player.sendSystemMessage(Component.translatable("aas.msg.ftl_assigned", target, "Bravo").withStyle(ChatFormatting.GOLD));
                    }
                }
            }
            // === 7. ASSIGN FTL CHARLIE ===
            else if (msg.action == 7) {
                AASWorldData.Squad mySquad = getPlayerSquad(pName, data);
                if (mySquad != null && (mySquad.leader.equals(pName) || mySquad.charlieLeader.equals(pName))) {
                    String target = msg.stringData;
                    if (mySquad.members.contains(target) && !mySquad.leader.equals(target) && !mySquad.bravoLeader.equals(target)) {
                        mySquad.removeFromFireteams(target);
                        mySquad.charlieLeader = target;
                        if (!mySquad.charlieMembers.contains(target)) mySquad.charlieMembers.add(target);
                        player.sendSystemMessage(Component.translatable("aas.msg.ftl_assigned", target, "Charlie").withStyle(ChatFormatting.GOLD));
                    }
                }
            }
            // === 8. ADD TO BRAVO ===
            else if (msg.action == 8) {
                AASWorldData.Squad mySquad = getPlayerSquad(pName, data);
                if (mySquad != null && (mySquad.leader.equals(pName) || mySquad.bravoLeader.equals(pName))) {
                    String target = msg.stringData;
                    if (mySquad.members.contains(target) && !mySquad.leader.equals(target)) {
                        mySquad.removeFromFireteams(target);
                        mySquad.bravoMembers.add(target);
                    }
                }
            }
            // === 9. ADD TO CHARLIE ===
            else if (msg.action == 9) {
                AASWorldData.Squad mySquad = getPlayerSquad(pName, data);
                if (mySquad != null && (mySquad.leader.equals(pName) || mySquad.charlieLeader.equals(pName))) {
                    String target = msg.stringData;
                    if (mySquad.members.contains(target) && !mySquad.leader.equals(target)) {
                        mySquad.removeFromFireteams(target);
                        mySquad.charlieMembers.add(target);
                    }
                }
            }
            // === 10. REMOVE FROM FIRETEAM ===
            else if (msg.action == 10) {
                AASWorldData.Squad mySquad = getPlayerSquad(pName, data);
                if (mySquad != null) {
                    String target = msg.stringData;
                    boolean canRemove = mySquad.leader.equals(pName) ||
                            (mySquad.bravoLeader.equals(pName) && mySquad.bravoMembers.contains(target)) ||
                            (mySquad.charlieLeader.equals(pName) && mySquad.charlieMembers.contains(target));

                    if (canRemove) {
                        mySquad.removeFromFireteams(target);
                    }
                }
            }
            else if (msg.action == 11) { // DISBAND SQUAD (CMD Only)
                AASWorldData.Squad targetSquad = null;
                for(AASWorldData.Squad s : data.squads) { if(s.id == msg.squadId) { targetSquad = s; break; } }

                if (targetSquad != null) {
                    boolean isBlue = targetSquad.team.equalsIgnoreCase("Blue");
                    int teamCmdId = isBlue ? data.blueCMDId : data.redCMDId;

                    if (player.getPersistentData().getInt("AAS_SquadID") == teamCmdId && player.getPersistentData().getBoolean("AAS_IsSquadLeader")) {

                        Component disbandMsg = Component.translatable("aas.msg.cmd_disbanded_squad", targetSquad.name).withStyle(ChatFormatting.RED);
                        for (ServerPlayer teamPlayer : player.server.getPlayerList().getPlayers()) {
                            if (teamPlayer.getTeam() != null && teamPlayer.getTeam().getName().equalsIgnoreCase(targetSquad.team)) {
                                teamPlayer.sendSystemMessage(disbandMsg);
                            }
                        }

                        for (String memberName : new ArrayList<>(targetSquad.members)) {
                            ServerPlayer m = player.server.getPlayerList().getPlayerByName(memberName);
                            if (m != null) {
                                m.getPersistentData().putString("AAS_CurrentKit", "Unassigned");
                                com.example.aas.network.PacketHandler.INSTANCE.send(
                                        net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> m),
                                        new com.example.aas.network.PacketSyncMyKit("Unassigned"));
                                m.getPersistentData().remove("AAS_PendingKit");
                                m.getPersistentData().remove("AAS_SquadID");
                                m.getPersistentData().remove("AAS_IsSquadLeader");
                                removeRadio(m);
                                m.getInventory().clearContent();
                                com.example.aas.network.ResupplyHandler.clearCurios(m);
                                m.inventoryMenu.broadcastChanges();
                                m.displayClientMessage(Component.translatable("aas.msg.squad_disbanded_cmd").withStyle(ChatFormatting.RED), true);
                                PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> m),
                                        new PacketSquadLeaderPlaytime("", -1, false));
                                com.example.aas.voicechat.VoicechatCompat.leaveGroup(m);
                            }
                        }

                        data.squads.remove(targetSquad);
                        if (msg.squadId == teamCmdId) {
                            if (isBlue) data.blueCMDId = -1; else data.redCMDId = -1;
                        }

                        data.setDirty();
                        PacketHandler.sendToAllClients(player.serverLevel(), data);
                    }
                }
            }
            data.setDirty();
            PacketHandler.INSTANCE.send(PacketDistributor.DIMENSION.with(player.level()::dimension), new PacketSyncSquads(data.squads));
        });
        ctx.get().setPacketHandled(true);
    }

    public static String getAvailableSquadName(AASWorldData data, String teamName) {
        for (String natoName : NATO_ALPHABET) {
            boolean taken = false;
            for (AASWorldData.Squad s : data.squads) {
                if (s.team.equalsIgnoreCase(teamName) && s.name.equalsIgnoreCase(natoName)) {
                    taken = true; break;
                }
            }
            if (!taken) return natoName;
        }
        return "Squad " + (data.squads.size() + 1);
    }

    public static AASWorldData.Squad getPlayerSquad(String playerName, AASWorldData data) {
        for (AASWorldData.Squad s : data.squads) {
            if (s.members.contains(playerName)) return s;
        }
        return null;
    }

    public static void leaveCurrentSquad(ServerPlayer player, AASWorldData data) {
        String pName = player.getScoreboardName();
        com.example.aas.voicechat.VoicechatCompat.leaveGroup(player);

        for (AASWorldData.Squad s : data.squads) {
            if (s.members.contains(pName)) {
                s.members.remove(pName);
                s.removeFromFireteams(pName);

                String pTeam = (player.getTeam() != null) ? player.getTeam().getName().toUpperCase() : "NEUTRAL";
                boolean voteCancelled = false;

                if (pTeam.equals("BLUE") && data.blueCmdVoteActive && data.blueCmdCandidateName.equals(pName)) {
                    data.blueCmdVoteActive = false;
                    data.blueCmdVotes.clear();
                    voteCancelled = true;
                } else if (pTeam.equals("RED") && data.redCmdVoteActive && data.redCmdCandidateName.equals(pName)) {
                    data.redCmdVoteActive = false;
                    data.redCmdVotes.clear();
                    voteCancelled = true;
                }

                if (voteCancelled) {
                    Component cancelMsg = Component.literal("CMD Application cancelled: " + pName + " left.")
                            .withStyle(ChatFormatting.RED);
                    player.server.getPlayerList().broadcastSystemMessage(cancelMsg, false);
                }

                if (s.leader.equals(pName)) {
                    removeRadio(player);

                    if (!s.members.isEmpty()) {
                        s.removeFromFireteams(s.members.get(0));
                        s.leader = s.members.get(0);
                        ServerPlayer newLeader = player.server.getPlayerList().getPlayerByName(s.leader);
                        if (newLeader != null) {
                            updatePlayerTags(newLeader, s.id, true);
                            if (com.example.aas.config.AASConfig.AUTO_GIVE_SL_RADIO.get()) giveRadio(newLeader);
                        }
                    } else {
                        if (s.id == data.blueCMDId) data.blueCMDId = -1;
                        if (s.id == data.redCMDId) data.redCMDId = -1;
                    }
                }
                break;
            }
        }
        data.squads.removeIf(s -> s.members.isEmpty());

        player.getPersistentData().putString("AAS_CurrentKit", "Unassigned");
        com.example.aas.network.PacketHandler.INSTANCE.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                new com.example.aas.network.PacketSyncMyKit("Unassigned"));
        player.getPersistentData().putString("AAS_PendingKit", "");

        player.getInventory().clearContent();
        com.example.aas.network.ResupplyHandler.clearCurios(player);
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.broadcastChanges();

        removePlayerTags(player);
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                new PacketSquadLeaderPlaytime("", -1, false));
        if (!data.isGameStarted) {
            player.displayClientMessage(Component.translatable("aas.msg.left_squad_pregame").withStyle(ChatFormatting.YELLOW), true);
        } else {
            player.displayClientMessage(Component.translatable("aas.msg.left_squad_reset").withStyle(ChatFormatting.YELLOW), true);
        }

        data.setDirty();
        PacketHandler.sendToAllClients(player.serverLevel(), data);
    }

    public static void giveRadio(ServerPlayer player) {
        if (player == null) return;
        boolean hasRadio = false;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof com.example.aas.item.RallyItem) {
                hasRadio = true;
                break;
            }
        }
        if (!hasRadio && player.getOffhandItem().getItem() instanceof com.example.aas.item.RallyItem) hasRadio = true;

        if (!hasRadio) {
            ItemStack radioStack = new ItemStack(ModItems.SQUAD_LEADER_RADIO.get());
            if (!player.getInventory().add(radioStack)) {
                player.drop(radioStack, false);
            }
        }
    }

    public static void removeRadio(ServerPlayer player) {
        if (player == null) return;

        if (!com.example.aas.config.AASConfig.AUTO_GIVE_SL_RADIO.get()) {
            return;
        }

        player.getInventory().clearOrCountMatchingItems(
                p -> p.getItem() instanceof com.example.aas.item.RallyItem,
                -1,
                player.inventoryMenu.getCraftSlots());
        player.inventoryMenu.broadcastChanges();
    }

    public static void updatePlayerTags(ServerPlayer p, int squadId, boolean isLeader) {
        p.getPersistentData().putInt("AAS_SquadID", squadId);
        p.getPersistentData().putBoolean("AAS_IsSquadLeader", isLeader);
    }
    public static void removePlayerTags(ServerPlayer p) {
        p.getPersistentData().remove("AAS_SquadID");
        p.getPersistentData().remove("AAS_IsSquadLeader");
    }
}