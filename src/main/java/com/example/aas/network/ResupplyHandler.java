// PATH: src\main\java\com\example\aas\network\ResupplyHandler.java
package com.example.aas.network;

import com.example.aas.world.AASWorldData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import java.util.Map;  // <--- ДОБАВЛЕНО
import java.util.UUID; // <--- ДОБАВЛЕНО
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import com.example.aas.events.GameLogicEvents;

public class ResupplyHandler {

    /**
     * ВЫДАЧА КИТА: Очищает инвентарь игрока и расставляет вещи СТРОГО в те слоты,
     * которые были настроены в Kit Editor'е.
     */
    public static void applyKitToPlayer(ServerPlayer player, AASWorldData.KitInfo kit) {
        Inventory pInv = player.getInventory();
        pInv.clearContent();

        // Достаем сохраненные теги
        Map<Integer, CompoundTag> savedTags = GameLogicEvents.PERSISTENT_NBT_STORAGE.remove(player.getUUID());

        for (int i = 0; i < 41; i++) {
            ItemStack kitStack = kit.inventory.get(i);
            if (!kitStack.isEmpty()) {
                ItemStack itemToGive = kitStack.copy();

                // Если для этого слота включено сохранение NBT и тег существует
                if (kit.saveNbtFlags[i] && savedTags != null && savedTags.containsKey(i)) {
                    itemToGive.setTag(savedTags.get(i));
                }

                pInv.setItem(i, itemToGive);
            }
        }
        player.getPersistentData().putString("AAS_CurrentKit", kit.name);
    }

    /**
     * ПОПОЛНЕНИЕ КИТА: Вызывается, когда игрок пополняется у ящика на базе.
     * Правильно обрабатывает несколько стаков одного и того же предмета.
     */
    public static boolean resupplyPlayer(ServerPlayer player, AASWorldData.KitInfo kit, boolean isAmmoBagSource) {
        boolean gaveSomething = false;
        Inventory pInv = player.getInventory();

        // Отслеживаем, какие предметы мы уже пополнили, чтобы не считать один тип дважды
        List<ItemStack> processedItems = new ArrayList<>();

        for (int i = 0; i < 41; i++) {
            if (!kit.resupplyFlags[i]) continue; // Игнорируем предметы без зеленой рамки

            ItemStack targetStack = kit.inventory.get(i);
            if (targetStack.isEmpty()) continue;
            // === НОВАЯ ЛОГИКА: ЗАПРЕТ СУМКИ В СУМКЕ ===
            if (isAmmoBagSource && targetStack.getItem() == com.example.aas.item.ModItems.AMMO_BAG.get()) {
                continue; // Если мы пополняемся из сумки, игнорируем предмет "сумка" в ките
            }
            // Проверяем, не обрабатывали ли мы уже этот тип предмета в этом цикле
            boolean alreadyProcessed = false;
            for (ItemStack processed : processedItems) {
                if (ItemStack.isSameItemSameTags(processed, targetStack)) {
                    alreadyProcessed = true;
                    break;
                }
            }
            if (alreadyProcessed) continue;

            processedItems.add(targetStack);

            // 1. Считаем сколько ВСЕГО этого предмета требует кит (по всем слотам с галочкой)
            int totalKitNeeds = 0;
            for (int k = 0; k < 41; k++) {
                if (kit.resupplyFlags[k]) {
                    ItemStack kStack = kit.inventory.get(k);
                    if (ItemStack.isSameItemSameTags(kStack, targetStack)) {
                        totalKitNeeds += kStack.getCount();
                    }
                }
            }

            // 2. Считаем сколько ВСЕГО этого предмета уже есть у игрока во всем инвентаре
            int totalPlayerHas = 0;
            for (int j = 0; j < pInv.getContainerSize(); j++) {
                ItemStack s = pInv.getItem(j);
                if (ItemStack.isSameItemSameTags(s, targetStack)) {
                    totalPlayerHas += s.getCount();
                }
            }

            // 3. Вычисляем дефицит (сколько штук нужно докинуть)
            int deficit = totalKitNeeds - totalPlayerHas;

            if (deficit > 0) {
                gaveSomething = true;

                // 4. Распределяем недостающее количество по идеальным слотам кита
                for (int k = 0; k < 41; k++) {
                    if (deficit <= 0) break;

                    if (kit.resupplyFlags[k]) {
                        ItemStack kStack = kit.inventory.get(k);
                        if (ItemStack.isSameItemSameTags(kStack, targetStack)) {

                            ItemStack currentInSlot = pInv.getItem(k);

                            if (currentInSlot.isEmpty()) {
                                // Если слот пуст, кладем туда сколько влезает (но не больше чем требует этот слот в ките)
                                int toPut = Math.min(deficit, kStack.getCount());
                                ItemStack newStack = targetStack.copy();
                                newStack.setCount(toPut);
                                pInv.setItem(k, newStack);
                                deficit -= toPut;
                            }
                            else if (ItemStack.isSameItemSameTags(currentInSlot, targetStack)) {
                                // Если в слоте уже лежит этот предмет, добиваем его до нужного количества
                                int spaceInSlot = Math.min(kStack.getCount(), currentInSlot.getMaxStackSize()) - currentInSlot.getCount();
                                if (spaceInSlot > 0) {
                                    int toPut = Math.min(deficit, spaceInSlot);
                                    currentInSlot.grow(toPut);
                                    deficit -= toPut;
                                }
                            }
                        }
                    }
                }

                // 5. Запасной вариант: если идеальные слоты чем-то забиты, выдаем оставшееся в любой свободный слот
                while (deficit > 0) {
                    int toGive = Math.min(deficit, targetStack.getMaxStackSize());
                    ItemStack addStack = targetStack.copy();
                    addStack.setCount(toGive);
                    if (!pInv.add(addStack)) {
                        player.drop(addStack, false); // Выбрасываем, если инвентарь полон
                    }
                    deficit -= toGive;
                }
            }
        }
        return gaveSomething;
    }

    /**
     * ПОПЫТКА ВЫДАЧИ "ОЖИДАЮЩЕГО" КИТА:
     * Проверяет все лимиты в реальном времени перед фактической выдачей кита игроку.
     */
    public static void tryApplyPendingKit(ServerPlayer player, AASWorldData data) {
        String pending = player.getPersistentData().getString("AAS_PendingKit");
        if (pending.isEmpty()) return;

        String teamName = player.getTeam() != null ? player.getTeam().getName().toUpperCase() : "";
        if (teamName.isEmpty()) return;

        AASWorldData.KitInfo kit = teamName.equals("BLUE") ? data.blueKits.get(pending) : data.redKits.get(pending);
        if (kit == null) return;

        // Если это пустой кит, отдаем без проверок лимитов
        if (pending.equals("Unassigned")) {
            applyKitToPlayer(player, kit);
            player.getPersistentData().remove("AAS_PendingKit");
            com.example.aas.network.PacketHandler.sendToAllClients(player.serverLevel(), data);
            return;
        }

        // СЧИТАЕМ ЛИМИТЫ НА ДАННЫЙ МОМЕНТ
        int teamCount = 0;
        int squadCount = 0;
        String pName = player.getScoreboardName();
        AASWorldData.Squad mySquad = null;

        // Ищем отряд игрока
        for (AASWorldData.Squad s : data.squads) {
            if (s.members.contains(pName)) {
                mySquad = s; break;
            }
        }

        // Проверяем инвентари (киты) других игроков в команде
        for (ServerPlayer p : player.server.getPlayerList().getPlayers()) {
            if (p == player) continue; // Себя не считаем

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

        // ПРОВЕРЯЕМ УСЛОВИЯ (лимиты на команду, отряд, минимум игроков, лидерство)
        boolean allowed = true;
        if (kit.maxPerTeam > 0 && teamCount >= kit.maxPerTeam) allowed = false;
        if (kit.maxPerSquad > 0 && squadCount >= kit.maxPerSquad) allowed = false;
        if (kit.minSquadPlayers > 0 && (mySquad == null || mySquad.members.size() < kit.minSquadPlayers)) allowed = false;
        if (kit.isLeaderOnly && (mySquad == null || !mySquad.leader.equals(pName))) allowed = false;

        if (allowed) {
            // Все лимиты соблюдены, выдаем кит
            applyKitToPlayer(player, kit);
        } else {
            // Лимит превышен (или кто-то взял его быстрее), выдаем пустой кит
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Kit " + pending + " is full or blocked! Spawning as Unassigned.").withStyle(net.minecraft.ChatFormatting.RED));
            AASWorldData.KitInfo unassigned = teamName.equals("BLUE") ? data.blueKits.get("Unassigned") : data.redKits.get("Unassigned");
            if (unassigned != null) {
                applyKitToPlayer(player, unassigned);
            }
        }

        // Очищаем статус "ожидающего" кита в NBT
        player.getPersistentData().remove("AAS_PendingKit");

        // Синхронизируем, чтобы у всех моментально обновилась иконка над головой/в меню
        com.example.aas.network.PacketHandler.sendToAllClients(player.serverLevel(), data);
    }
}