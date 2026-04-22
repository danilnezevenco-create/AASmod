// PATH: src\main\java\com\example\aas\menu\KitEditorMenu.java
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
    public final Container kitInventory;
    public final String team;
    public final String kitName;
    public boolean isLeaderOnly;
    public int maxPerTeam;
    public int maxPerSquad;
    public int minSquadPlayers;
    public final boolean[] resupplyFlags;
    public final boolean[] saveNbtFlags; // Флаги для сохранения NBT после смерти

    private static final ResourceLocation[] ARMOR_SLOT_TEXTURES = new ResourceLocation[]{
            InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS,
            InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS,
            InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE,
            InventoryMenu.EMPTY_ARMOR_SLOT_HELMET
    };

    // Чтение пакета клиентом (вызывается через PacketOpenKitEditor)
    public KitEditorMenu(int id, Inventory playerInv, FriendlyByteBuf data) {
        this(id, playerInv, new SimpleContainer(41),
                data.readUtf(),         // team
                data.readUtf(),         // kitName
                data.readBoolean(),     // isLeaderOnly
                data.readInt(),         // maxPerTeam
                data.readInt(),         // maxPerSquad
                data.readInt(),         // minSquadPlayers
                new boolean[41],        // временный массив для resupply
                new boolean[41]);       // временный массив для saveNbt

        // Читаем флаги ресаплая
        for (int i = 0; i < 41; i++) {
            this.resupplyFlags[i] = data.readBoolean();
        }
        // Читаем флаги сохранения NBT
        for (int i = 0; i < 41; i++) {
            this.saveNbtFlags[i] = data.readBoolean();
        }
    }

    // Основной конструктор (Серверный + используется клиентским)
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

        // --- СЛОТЫ КИТА (Слоты 0 - 40) ---

        // 1. Основная сетка инвентаря кита (слоты 9 - 35)
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(kitInv, 9 + col + row * 9, 8 + col * 18, 66 + row * 18));
            }
        }

        // 2. Хотбар кита (слоты 0 - 8)
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(kitInv, col, 8 + col * 18, 124));
        }

        // 3. Броня кита (слоты 36 - 39)
        // 36: Boots, 37: Legs, 38: Chest, 39: Helmet
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

        // 4. Вторая рука кита (слот 40)
        this.addSlot(new Slot(kitInv, 40, 84, 150) {
            @Override
            public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
                return Pair.of(InventoryMenu.BLOCK_ATLAS, InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD);
            }
        });

        // --- СЛОТЫ ИГРОКА (Стандартные) ---

        // Основной инвентарь игрока
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                this.addSlot(new Slot(playerInv, j + i * 9 + 9, 8 + j * 18, 180 + i * 18));
            }
        }

        // Хотбар игрока
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
        // Отключено в редакторе, чтобы игроки не путались при настройке флагов
        return ItemStack.EMPTY;
    }
}