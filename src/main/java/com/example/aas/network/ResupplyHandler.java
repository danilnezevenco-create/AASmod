package com.example.aas.network;

import com.example.aas.world.AASWorldData;
import com.example.aas.events.GameLogicEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ResupplyHandler {

    /**
     * ВЫДАЧА КИТА: Очищает инвентарь и заполняет его.
     * Слоты 41-48 пытаются сначала попасть в Curios.
     */
    public static void applyKitToPlayer(ServerPlayer player, AASWorldData.KitInfo kit) {
        Inventory pInv = player.getInventory();
        pInv.clearContent();

        Map<Integer, CompoundTag> savedTags = GameLogicEvents.PERSISTENT_NBT_STORAGE.remove(player.getUUID());

        for (int i = 0; i < 49; i++) {
            ItemStack kitStack = kit.inventory.get(i);
            if (kitStack.isEmpty()) continue;

            ItemStack itemToGive = kitStack.copy();

            if (i < kit.saveNbtFlags.length && kit.saveNbtFlags[i] && savedTags != null && savedTags.containsKey(i)) {
                itemToGive.setTag(savedTags.get(i));
            }

            if (i < 41) {
                pInv.setItem(i, itemToGive);
            } else {
                boolean equipped = false;
                if (ModList.get().isLoaded("curios")) {
                    equipped = tryEquipInCurios(player, itemToGive);
                }

                if (!equipped) {
                    if (!pInv.add(itemToGive)) {
                        player.drop(itemToGive, false);
                    }
                }
            }
        }
        player.getPersistentData().putString("AAS_CurrentKit", kit.name);
    }

    /**
     * Логика автоматической экипировки в Curios (Исправлено под 1.20.1).
     */
    private static boolean tryEquipInCurios(ServerPlayer player, ItemStack stack) {
        try {
            // В 1.20.1 используем getCuriosInventory вместо getCuriosHandler
            return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).map(handler -> {
                for (var entry : handler.getCurios().entrySet()) {
                    var stacksHandler = entry.getValue().getStacks();

                    for (int i = 0; i < stacksHandler.getSlots(); i++) {
                        // stacksHandler.isStackValid проверяет, подходит ли предмет для этого типа слота
                        if (stacksHandler.getStackInSlot(i).isEmpty() && stacksHandler.isItemValid(i, stack)) {
                            stacksHandler.setStackInSlot(i, stack.copy());
                            stack.setCount(0);
                            return true;
                        }
                    }
                }
                return false;
            }).orElse(false);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * ПОПОЛНЕНИЕ КИТА: Теперь учитывает все 49 слотов.
     */
    public static boolean resupplyPlayer(ServerPlayer player, AASWorldData.KitInfo kit, boolean isAmmoBagSource) {
        boolean gaveSomething = false;
        Inventory pInv = player.getInventory();
        List<ItemStack> processedItems = new ArrayList<>();

        for (int i = 0; i < 49; i++) {
            if (!kit.resupplyFlags[i]) continue;

            ItemStack targetStack = kit.inventory.get(i);
            if (targetStack.isEmpty()) continue;

            if (isAmmoBagSource && targetStack.getItem() == com.example.aas.item.ModItems.AMMO_BAG.get()) {
                continue;
            }

            boolean alreadyProcessed = false;
            for (ItemStack processed : processedItems) {
                if (ItemStack.isSameItemSameTags(processed, targetStack)) {
                    alreadyProcessed = true;
                    break;
                }
            }
            if (alreadyProcessed) continue;
            processedItems.add(targetStack);

            int totalKitNeeds = 0;
            for (int k = 0; k < 49; k++) {
                if (kit.resupplyFlags[k]) {
                    ItemStack kStack = kit.inventory.get(k);
                    if (ItemStack.isSameItemSameTags(kStack, targetStack)) {
                        totalKitNeeds += kStack.getCount();
                    }
                }
            }

            int totalPlayerHas = 0;
            for (int j = 0; j < pInv.getContainerSize(); j++) {
                ItemStack s = pInv.getItem(j);
                if (ItemStack.isSameItemSameTags(s, targetStack)) {
                    totalPlayerHas += s.getCount();
                }
            }

            if (ModList.get().isLoaded("curios")) {
                totalPlayerHas += countInCurios(player, targetStack);
            }

            int deficit = totalKitNeeds - totalPlayerHas;
            if (deficit > 0) {
                gaveSomething = true;
                while (deficit > 0) {
                    int toGive = Math.min(deficit, targetStack.getMaxStackSize());
                    ItemStack addStack = targetStack.copy();
                    addStack.setCount(toGive);
                    if (!pInv.add(addStack)) {
                        player.drop(addStack, false);
                    }
                    deficit -= toGive;
                }
            }
        }
        return gaveSomething;
    }

    /**
     * Подсчет предметов в слотах Curios (Исправлено под 1.20.1).
     */
    private static int countInCurios(ServerPlayer player, ItemStack target) {
        return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).map(handler -> {
            int count = 0;
            for (var entry : handler.getCurios().values()) {
                var stacks = entry.getStacks();
                for (int i = 0; i < stacks.getSlots(); i++) {
                    ItemStack s = stacks.getStackInSlot(i);
                    if (ItemStack.isSameItemSameTags(s, target)) {
                        count += s.getCount();
                    }
                }
            }
            return count;
        }).orElse(0);
    }

    public static void tryApplyPendingKit(ServerPlayer player, AASWorldData data) {
        String pending = player.getPersistentData().getString("AAS_PendingKit");
        if (pending.isEmpty()) return;

        String teamName = player.getTeam() != null ? player.getTeam().getName().toUpperCase() : "";
        if (teamName.isEmpty()) return;

        AASWorldData.KitInfo kit = teamName.equals("BLUE") ? data.blueKits.get(pending) : data.redKits.get(pending);
        if (kit == null) return;

        if (pending.equals("Unassigned")) {
            applyKitToPlayer(player, kit);
            player.getPersistentData().remove("AAS_PendingKit");
            PacketHandler.sendToAllClients(player.serverLevel(), data);
            return;
        }

        int teamCount = 0;
        int squadCount = 0;
        String pName = player.getScoreboardName();
        AASWorldData.Squad mySquad = null;

        for (AASWorldData.Squad s : data.squads) {
            if (s.members.contains(pName)) {
                mySquad = s; break;
            }
        }

        for (ServerPlayer p : player.server.getPlayerList().getPlayers()) {
            if (p == player) continue;
            if (p.getTeam() != null && p.getTeam().getName().toUpperCase().equals(teamName)) {
                String otherKit = p.getPersistentData().getString("AAS_CurrentKit");
                if (otherKit.equals(pending)) {
                    teamCount++;
                    if (mySquad != null && mySquad.members.contains(p.getScoreboardName())) {
                        squadCount++;
                    }
                }
            }
        }

        boolean allowed = true;
        if (kit.maxPerTeam > 0 && teamCount >= kit.maxPerTeam) allowed = false;
        if (kit.maxPerSquad > 0 && squadCount >= kit.maxPerSquad) allowed = false;
        if (kit.minSquadPlayers > 0 && (mySquad == null || mySquad.members.size() < kit.minSquadPlayers)) allowed = false;
        if (kit.isLeaderOnly && (mySquad == null || !mySquad.leader.equals(pName))) allowed = false;

        if (allowed) {
            applyKitToPlayer(player, kit);
        } else {
            player.sendSystemMessage(Component.literal("Kit " + pending + " is full or blocked! Spawning as Unassigned.").withStyle(ChatFormatting.RED));
            AASWorldData.KitInfo unassigned = teamName.equals("BLUE") ? data.blueKits.get("Unassigned") : data.redKits.get("Unassigned");
            if (unassigned != null) applyKitToPlayer(player, unassigned);
        }

        player.getPersistentData().remove("AAS_PendingKit");
        PacketHandler.sendToAllClients(player.serverLevel(), data);
    }
}