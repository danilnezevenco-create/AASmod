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

import java.util.Map;

public class ResupplyHandler {

    /**
     * ВЫДАЧА КИТА: очищает инвентарь и заполняет его.
     * Слоты 41-48 пытаются сначала попасть в Curios.
     */
    public static void applyKitToPlayer(ServerPlayer player, AASWorldData.KitInfo kit) { applyKitToPlayer(player, kit, false); }

    public static void applyKitToPlayer(ServerPlayer player, AASWorldData.KitInfo kit, boolean isAlt) {
        Inventory pInv = player.getInventory();
        pInv.clearContent();
        clearCurios(player);

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
        player.getPersistentData().putBoolean("AAS_CurrentKitAlt", isAlt);
        com.example.aas.network.PacketHandler.INSTANCE.send(
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player),
                new com.example.aas.network.PacketSyncMyKit(kit.name));
    }

    /** Очистка всех слотов Curios */
    public static void clearCurios(ServerPlayer player) {
        if (ModList.get().isLoaded("curios")) {
            try {
                top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).ifPresent(handler -> {
                    for (var entry : handler.getCurios().values()) {
                        var stacks = entry.getStacks();
                        for (int i = 0; i < stacks.getSlots(); i++) {
                            stacks.setStackInSlot(i, ItemStack.EMPTY);
                        }
                    }
                });
            } catch (Exception ignored) {}
        }
    }

    /** Автоматическая экипировка в Curios (1.20.1). */
    private static boolean tryEquipInCurios(ServerPlayer player, ItemStack stack) {
        try {
            return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).map(handler -> {
                for (var entry : handler.getCurios().entrySet()) {
                    var stacksHandler = entry.getValue().getStacks();

                    for (int i = 0; i < stacksHandler.getSlots(); i++) {
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
     * ПОПОЛНЕНИЕ КИТА. Вся логика вынесена в KitResupplier
     * (сравнение предметов без изменчивого NBT, суммарный подсчёт, лимит по слотам кита).
     */
    public static boolean resupplyPlayer(ServerPlayer player, AASWorldData.KitInfo kit, boolean isAmmoBagSource) {
        return KitResupplier.resupply(player, kit, isAmmoBagSource);
    }

    public static void tryApplyPendingKit(ServerPlayer player, AASWorldData data) {
        // --- 1. ОПРЕДЕЛЯЕМ ЦЕЛЕВОЙ КИТ ---
        String pending = player.getPersistentData().getString("AAS_PendingKit");
        String current = player.getPersistentData().getString("AAS_CurrentKit");
        boolean pendingAlt = player.getPersistentData().getBoolean("AAS_PendingKitAlt");
        boolean currentAlt = player.getPersistentData().getBoolean("AAS_CurrentKitAlt");

        // Если есть очередь (Pending) — берём её, если пуста — берём текущий кит
        String targetKitName = !pending.isEmpty() ? pending : current;
        boolean targetIsAlt = !pending.isEmpty() ? pendingAlt : currentAlt;

        // Если кита нет или это "Unassigned", просто очищаем очередь и выходим
        if (targetKitName.isEmpty() || targetKitName.equals("Unassigned")) {
            player.getPersistentData().remove("AAS_PendingKit");
            return;
        }

        // --- ПРОВЕРКА: СТАРТ ИГРЫ ---
        // Если раунд ещё не начат, не выдаём предметы, просто оставляем Pending.
        if (!data.isGameStarted && !player.isCreative()) {
            player.displayClientMessage(Component.translatable("aas.msg.kit_reserved", targetKitName).withStyle(ChatFormatting.YELLOW), true);
            return;
        }

        // --- 2. ПРОВЕРКА ПОДГОТОВКИ В INVASION ---
        if (data.gameMode.equalsIgnoreCase("INVASION") && data.invasionPrepTicks > 0) {
            String pTeam = player.getTeam() != null ? player.getTeam().getName().toUpperCase() : "NEUTRAL";
            if (!pTeam.equals(data.invasionDefender) && !player.isCreative()) {
                player.displayClientMessage(Component.translatable("aas.msg.invasion_prep").withStyle(ChatFormatting.RED), true);
                return;
            }
        }

        String teamName = player.getTeam() != null ? player.getTeam().getName().toUpperCase() : "";
        if (teamName.isEmpty()) return;

        AASWorldData.KitInfo kit = data.getKitVariant(teamName, targetKitName, targetIsAlt);
        if (kit == null) return;

        // --- 3. ПРОВЕРКА ЛИМИТОВ (КРОМЕ САМОГО ИГРОКА) ---
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
            if (p == player) continue; // Не считаем самого себя
            if (p.getTeam() != null && p.getTeam().getName().toUpperCase().equals(teamName)) {
                String otherCurrent = p.getPersistentData().getString("AAS_CurrentKit");
                String otherPending = p.getPersistentData().getString("AAS_PendingKit");
                // Если другой игрок уже использует или забронировал этот кит
                if (otherCurrent.equals(targetKitName) || otherPending.equals(targetKitName)) {
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

        // --- 4. ПРИМЕНЕНИЕ КИТА ---
        if (allowed) {
            // applyKitToPlayer сам очистит инвентарь перед выдачей
            applyKitToPlayer(player, kit, targetIsAlt);
        } else {
            // Если за время смерти лимиты на кит забились другими игроками
            player.sendSystemMessage(Component.translatable("aas.msg.kit_blocked", targetKitName).withStyle(ChatFormatting.RED));
            AASWorldData.KitInfo unassigned = teamName.equals("BLUE") ? data.blueKits.get("Unassigned") : data.redKits.get("Unassigned");
            if (unassigned != null) applyKitToPlayer(player, unassigned, false);
        }

        // Очищаем Pending (бронь), так как мы её обработали
        player.getPersistentData().remove("AAS_PendingKit");
        player.getPersistentData().remove("AAS_PendingKitAlt");
        // Синхронизируем иконки китов на карте
        PacketHandler.sendToAllClients(player.serverLevel(), data);
    }
}