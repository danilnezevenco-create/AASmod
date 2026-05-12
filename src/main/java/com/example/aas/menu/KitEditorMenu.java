package com.example.aas.menu;

import com.mojang.datafixers.util.Pair;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class KitEditorMenu extends AbstractContainerMenu {
    // 1. Увеличиваем константу до 49 (41 стандартный + 8 дополнительных)
    public static final int MAX_KIT_SLOTS = 49;

    public final Container kitInventory;
    public final String team;
    public final String kitName;
    public boolean isLeaderOnly;
    public int maxPerTeam;
    public int maxPerSquad;
    public int minSquadPlayers;
    public final boolean[] resupplyFlags;
    public final boolean[] saveNbtFlags;

    private static final ResourceLocation[] ARMOR_SLOT_TEXTURES = new ResourceLocation[]{
            InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS,
            InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS,
            InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE,
            InventoryMenu.EMPTY_ARMOR_SLOT_HELMET
    };

    // Чтение пакета клиентом
    public KitEditorMenu(int id, Inventory playerInv, FriendlyByteBuf data) {
        // Здесь везде меняем 41 на 49
        this(id, playerInv, new SimpleContainer(MAX_KIT_SLOTS),
                data.readUtf(),
                data.readUtf(),
                data.readBoolean(),
                data.readInt(),
                data.readInt(),
                data.readInt(),
                new boolean[MAX_KIT_SLOTS],
                new boolean[MAX_KIT_SLOTS]);

        for (int i = 0; i < MAX_KIT_SLOTS; i++) {
            this.resupplyFlags[i] = data.readBoolean();
        }
        for (int i = 0; i < MAX_KIT_SLOTS; i++) {
            this.saveNbtFlags[i] = data.readBoolean();
        }
    }

    public KitEditorMenu(int id, Inventory playerInv, Container kitInv, String t, String k,
                         boolean l, int mt, int ms, int minPlayers,
                         boolean[] flags, boolean[] nbtFlags) {
        super(ModMenuTypes.KIT_EDITOR_MENU.get(), id);
        this.kitInventory = kitInv;
        this.team = t;
        this.kitName = k;
        this.isLeaderOnly = l;
        this.maxPerTeam = mt;
        this.maxPerSquad = ms;
        this.minSquadPlayers = minPlayers;
        this.resupplyFlags = flags;
        this.saveNbtFlags = nbtFlags;

        // --- СТАНДАРТНЫЕ СЛОТЫ КИТА (0 - 40) ---

        // 1. Основная сетка (9 - 35)
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(kitInv, 9 + col + row * 9, 8 + col * 18, 66 + row * 18));
            }
        }

        // 2. Хотбар (0 - 8)
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(kitInv, col, 8 + col * 18, 124));
        }

        // 3. Броня (36 - 39)
        for (int i = 0; i < 4; ++i) {
            final int armorIndex = i;
            int slotIndex = 36 + i;
            this.addSlot(new Slot(kitInv, slotIndex, 8 + i * 18, 150) {
                @Override
                public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
                    return Pair.of(InventoryMenu.BLOCK_ATLAS, ARMOR_SLOT_TEXTURES[armorIndex]);
                }
            });
        }

        // 4. Вторая рука (40)
        this.addSlot(new Slot(kitInv, 40, 84, 150) {
            @Override
            public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
                return Pair.of(InventoryMenu.BLOCK_ATLAS, InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD);
            }
        });

        // --- 5. НОВЫЕ "CURSE" СЛОТЫ (41 - 48) ---
        // Размещаем их в два столбика слева от основного инвентаря
        // Координата X: -36 и -18 (относительно левого края основного окна)
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 2; col++) {
                int slotIndex = 41 + (row * 2 + col);
                // X = -36 для первого столбца, -18 для второго. Y начинается с 66.
                this.addSlot(new Slot(kitInv, slotIndex, -36 + col * 18, 66 + row * 18));
            }
        }

        // --- СЛОТЫ ИНВЕНТАРЯ ИГРОКА ---
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                this.addSlot(new Slot(playerInv, j + i * 9 + 9, 8 + j * 18, 180 + i * 18));
            }
        }
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(playerInv, i, 8 + i * 18, 238));
        }
    }

    @Override
    public boolean stillValid(Player p) {
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player p, int index) {
        return ItemStack.EMPTY;
    }
}