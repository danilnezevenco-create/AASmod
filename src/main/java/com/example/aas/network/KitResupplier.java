package com.example.aas.network;

import com.example.aas.item.AGSAmmoItem;
import com.example.aas.item.M2AmmoItem;
import com.example.aas.item.ModItems;
import com.example.aas.world.AASWorldData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Единая система пополнения кита (хаб, ammo bag, main supply, ящики, техника).
 *
 * Правила:
 *  1. "Тот же предмет" = тот же Item + одинаковый NBT БЕЗ изменчивых ключей
 *     (Damage, GeckoLibID, Ammo). Для слотов с флагом SaveNbt сравниваем только по Item.
 *  2. Предметы одного вида из кита считаются ВМЕСТЕ: нужно N штук суммарно, есть M -> докладываем N-M.
 *  3. Докладываем сначала в "свои" слоты кита, не превышая количество этого слота;
 *     остаток - в главный инвентарь (0..35), никогда не в броню/оффхенд.
 *  4. Магазины с зарядом (AGS/M2) не дублируются - у существующих просто восстанавливается заряд.
 */
public final class KitResupplier {
    private KitResupplier() {}

    /** Ключи, которые меняются сами и не определяют "какой это предмет".
     *  Если используешь оружие из других модов - добавь сюда ключи его счётчика патронов. */
    private static final Set<String> VOLATILE_KEYS = Set.of("Damage", "GeckoLibID", "Ammo");

    private static final int SLOTS = 41;       // 0..35 инвентарь, 36..39 броня, 40 оффхенд
    private static final int MAIN_SLOTS = 36;

    public static boolean resupply(ServerPlayer player, AASWorldData.KitInfo kit, boolean skipAmmoBag) {
        Inventory inv = player.getInventory();

        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < SLOTS; i++) {
            if (i >= kit.resupplyFlags.length || !kit.resupplyFlags[i]) continue;
            ItemStack kitStack = kit.inventory.get(i);
            if (kitStack.isEmpty()) continue;
            if (skipAmmoBag && kitStack.getItem() == ModItems.AMMO_BAG.get()) continue;
            slots.add(i);
        }

        boolean changed = false;
        boolean[] done = new boolean[SLOTS];
        for (int i : slots) {
            if (done[i]) continue;
            ItemStack sample = kit.inventory.get(i);

            List<Integer> group = new ArrayList<>();
            boolean loose = isLoose(kit, i);
            for (int j : slots) {
                if (done[j]) continue;
                if (matches(kit.inventory.get(j), sample, isLoose(kit, i) || isLoose(kit, j))) {
                    group.add(j);
                    done[j] = true;
                    loose |= isLoose(kit, j);
                }
            }
            if (resupplyGroup(inv, kit, group, sample, loose)) changed = true;
        }
        return changed;
    }

    private static boolean resupplyGroup(Inventory inv, AASWorldData.KitInfo kit,
                                         List<Integer> group, ItemStack sample, boolean loose) {
        boolean changed = false;
        boolean charge = isCharge(sample);
        int maxStack = Math.max(1, sample.getMaxStackSize());

        int need = 0;
        for (int s : group) need += Math.min(kit.inventory.get(s).getCount(), maxStack);

        int have = 0;
        List<Integer> owned = new ArrayList<>();
        for (int j = 0; j < SLOTS; j++) {
            ItemStack s = inv.getItem(j);
            if (matches(s, sample, loose)) {
                have += s.getCount();
                owned.add(j);
            }
        }

        // A. Заряд: восстанавливаем самые пустые магазины, но не больше чем положено киту
        if (charge) {
            owned.sort(Comparator.comparingInt(j -> getCharge(inv.getItem(j))));
            int left = need;
            for (int j : owned) {
                if (left <= 0) break;
                ItemStack s = inv.getItem(j);
                left -= s.getCount();
                if (getCharge(s) < getMaxCharge(s)) {
                    setFullCharge(s);
                    changed = true;
                }
            }
        }

        // B. Недостающее количество
        int missing = need - have;
        if (missing <= 0) return changed;

        // B1. В свои слоты кита, не больше количества этого слота
        for (int s : group) {
            if (missing <= 0) break;
            ItemStack kitStack = kit.inventory.get(s);
            int cap = Math.min(kitStack.getCount(), maxStack);
            ItemStack cur = inv.getItem(s);

            if (cur.isEmpty()) {
                int put = Math.min(missing, cap);
                ItemStack give = kitStack.copy();
                give.setCount(put);
                if (charge) setFullCharge(give);
                inv.setItem(s, give);
                missing -= put;
                changed = true;
            } else if (matches(cur, sample, loose)) {
                int add = Math.min(missing, cap - cur.getCount());
                if (add > 0) {
                    cur.grow(add);
                    missing -= add;
                    changed = true;
                }
            }
            // в слоте чужой предмет - пропускаем, остаток уйдёт в B2
        }

        // B2. Остаток (свои слоты заняты чужим): сначала доливаем уже имеющиеся стаки...
        for (int j = 0; j < MAIN_SLOTS && missing > 0; j++) {
            if (group.contains(j)) continue;
            ItemStack cur = inv.getItem(j);
            if (matches(cur, sample, loose) && cur.getCount() < maxStack) {
                int add = Math.min(missing, maxStack - cur.getCount());
                cur.grow(add);
                missing -= add;
                changed = true;
            }
        }
        // ...потом пустые слоты: сначала не зарезервированные китом, затем любые
        for (int pass = 0; pass < 2 && missing > 0; pass++) {
            for (int j = 0; j < MAIN_SLOTS && missing > 0; j++) {
                if (group.contains(j) || !inv.getItem(j).isEmpty()) continue;
                if (pass == 0 && !kit.inventory.get(j).isEmpty()) continue;
                int put = Math.min(missing, maxStack);
                ItemStack give = sample.copy();
                give.setCount(put);
                if (charge) setFullCharge(give);
                inv.setItem(j, give);
                missing -= put;
                changed = true;
            }
        }
        return changed;
    }

    // ---------- сравнение ----------

    private static boolean isLoose(AASWorldData.KitInfo kit, int slot) {
        return slot < kit.saveNbtFlags.length && kit.saveNbtFlags[slot];
    }

    static boolean matches(ItemStack stack, ItemStack sample, boolean loose) {
        if (stack.isEmpty() || sample.isEmpty()) return false;
        if (!ItemStack.isSameItem(stack, sample)) return false;
        return loose || Objects.equals(identityTag(stack), identityTag(sample));
    }

    /** NBT без изменчивых ключей; пустой NBT и его отсутствие считаются одинаковыми (null). */
    private static CompoundTag identityTag(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) return null;
        CompoundTag copy = tag.copy();
        for (String key : VOLATILE_KEYS) copy.remove(key);
        return copy.isEmpty() ? null : copy;
    }

    // ---------- магазины с зарядом ----------

    private static boolean isCharge(ItemStack s) {
        return s.getItem() instanceof AGSAmmoItem || s.getItem() instanceof M2AmmoItem;
    }

    private static int getCharge(ItemStack s) {
        if (s.getItem() instanceof AGSAmmoItem) return AGSAmmoItem.getAmmo(s);
        if (s.getItem() instanceof M2AmmoItem) return M2AmmoItem.getAmmo(s);
        return 0;
    }

    private static int getMaxCharge(ItemStack s) {
        if (s.getItem() instanceof AGSAmmoItem) return AGSAmmoItem.MAX_AMMO;
        if (s.getItem() instanceof M2AmmoItem) return M2AmmoItem.MAX_AMMO;
        return 0;
    }

    private static void setFullCharge(ItemStack s) {
        if (s.getItem() instanceof AGSAmmoItem) AGSAmmoItem.setAmmo(s, AGSAmmoItem.MAX_AMMO);
        else if (s.getItem() instanceof M2AmmoItem) M2AmmoItem.setAmmo(s, M2AmmoItem.MAX_AMMO);
    }
}