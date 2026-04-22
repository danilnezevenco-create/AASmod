// PATH: src\main\java\com\example\aas\network\PacketSquadAction.java
package com.example.aas.network;

import com.example.aas.config.AASConfig; // <--- ДОБАВЛЕН ИМПОРТ
import com.example.aas.item.ModItems;
import com.example.aas.world.AASWorldData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

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

                updatePlayerTags(player, newId, true);

                // ПРОВЕРКА КОНФИГА: ВЫДАЕМ РАЦИЮ ЛИДЕРУ ТОЛЬКО ЕСЛИ ВКЛЮЧЕНО
                if (AASConfig.AUTO_GIVE_SL_RADIO.get()) {
                    giveRadio(player);
                }

                player.sendSystemMessage(Component.literal("Squad created: " + finalName).withStyle(ChatFormatting.GOLD));
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
                        updatePlayerTags(player, s.id, false);
                        player.sendSystemMessage(Component.literal("Joined squad: " + s.name).withStyle(ChatFormatting.GREEN));
                        break;
                    }
                }
            }
            // === 2. LEAVE ===
            else if (msg.action == 2) {
                leaveCurrentSquad(player, data);
                player.sendSystemMessage(Component.literal("You left the squad.").withStyle(ChatFormatting.YELLOW));
            }
            // === 3. KICK ===
            else if (msg.action == 3) {
                AASWorldData.Squad mySquad = getPlayerSquad(pName, data);
                if (mySquad != null && mySquad.leader.equals(pName)) {
                    String targetName = msg.stringData;
                    if (!targetName.equals(pName) && mySquad.members.remove(targetName)) {
                        ServerPlayer target = player.server.getPlayerList().getPlayerByName(targetName);
                        if (target != null) {
                            removePlayerTags(target);
                            target.displayClientMessage(Component.literal("You were kicked!").withStyle(ChatFormatting.RED), true);
                        }
                        player.sendSystemMessage(Component.literal("Kicked " + targetName).withStyle(ChatFormatting.RED));
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

                        mySquad.leader = targetName;
                        updatePlayerTags(player, mySquad.id, false);

                        ServerPlayer target = player.server.getPlayerList().getPlayerByName(targetName);
                        if (target != null) {
                            updatePlayerTags(target, mySquad.id, true);

                            // ПРОВЕРКА КОНФИГА: ВЫДАЕМ РАЦИЮ НОВОМУ ЛИДЕРУ ТОЛЬКО ЕСЛИ ВКЛЮЧЕНО
                            if (AASConfig.AUTO_GIVE_SL_RADIO.get()) {
                                giveRadio(target);
                            }

                            target.displayClientMessage(Component.literal("You have been promoted to Squad Leader!").withStyle(ChatFormatting.GOLD), true);
                        }

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
                    player.sendSystemMessage(Component.literal("Squad is now " + status).withStyle(color));
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
        data.squads.forEach(s -> {
            if (s.members.remove(pName)) {
                if (s.leader.equals(pName)) {
                    removeRadio(player);
                    if (!s.members.isEmpty()) {
                        s.leader = s.members.get(0);
                        ServerPlayer newLeader = player.server.getPlayerList().getPlayerByName(s.leader);
                        if (newLeader != null) {
                            updatePlayerTags(newLeader, s.id, true);

                            // ПРОВЕРКА КОНФИГА ПРИ ВЫХОДЕ ИЗ СКВАДА
                            if (AASConfig.AUTO_GIVE_SL_RADIO.get()) {
                                giveRadio(newLeader);
                            }

                            newLeader.sendSystemMessage(Component.literal("You are now the Squad Leader!").withStyle(ChatFormatting.GOLD));
                        }
                    }
                }
            }
        });
        data.squads.removeIf(s -> s.members.isEmpty());
        removePlayerTags(player);
    }

    public static void giveRadio(ServerPlayer player) {
        if (player == null) return;
        boolean hasRadio = false;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() == ModItems.SQUAD_LEADER_RADIO.get()) {
                hasRadio = true;
                break;
            }
        }
        if (!hasRadio && player.getOffhandItem().getItem() == ModItems.SQUAD_LEADER_RADIO.get()) hasRadio = true;

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
            return; // Если конфиг выключен, ничего не забираем!
        }

        player.getInventory().clearOrCountMatchingItems(
                p -> p.getItem() == ModItems.SQUAD_LEADER_RADIO.get(),
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