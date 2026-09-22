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
    // Р РЎРЎРЎС‚Р°РІРёР» РєРѕРЅСЃС‚Р°РЅС‚Сѓ РґРѕ 49 (41 СЃС‚Р°РЅРґР°СЂС‚РЅС‹Р№ + 8 РґРѕРїРѕР»РЅРёС‚РµР»СЊРЅС‹С…)
    public static final int MAX_KIT_SLOTS = 49;

    // Р’С‹СЃРѕС‚Р° РІРµСЂС…РЅРµР№ РїР°РЅРµР»Рё СЃ РЅР°СЃС‚СЂРѕР№РєР°РјРё (РґРѕР»Р¶РЅР° СЃРѕРІРїР°РґР°С‚СЊ СЃ РіРµРѕРјРµС‚СЂРёРµР№ РІ KitEditorScreen)
    private static final int SLOT_Y_OFFSET = 60;

    public final Container kitInventory;
    public final String team;
    public final String kitName;
    public String displayName;
    public boolean isLeaderOnly;
    public int maxPerTeam;
    public int maxPerSquad;
    public int minSquadPlayers;
    public final boolean[] resupplyFlags;
    public final boolean[] saveNbtFlags;

    // === РђР›Р¬РўР•Р РќРђРўРР’РќР«Р™ РљРРў ===
    public final boolean isAlt;
    public boolean hasAlt;

    private static final ResourceLocation[] ARMOR_SLOT_TEXTURES = new ResourceLocation[]{
            InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS,
            InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS,
            InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE,
            InventoryMenu.EMPTY_ARMOR_SLOT_HELMET
    };

    public KitEditorMenu(int id, Inventory playerInv, FriendlyByteBuf data) {
        this(id, playerInv, new SimpleContainer(MAX_KIT_SLOTS),
                data.readUtf(),
                data.readUtf(),
                data.readBoolean(), // isAlt
                data.readBoolean(), // hasAlt
                data.readBoolean(), // isLeaderOnly
                data.readInt(),
                data.readInt(),
                data.readInt(),
                data.readUtf(),
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
                         boolean isAlt, boolean hasAlt,
                         boolean l, int mt, int ms, int minPlayers,
                         String displayName,
                         boolean[] flags, boolean[] nbtFlags) {
        super(ModMenuTypes.KIT_EDITOR_MENU.get(), id);
        this.kitInventory = kitInv;
        this.team = t;
        this.kitName = k;
        this.isAlt = isAlt;
        this.hasAlt = hasAlt;
        this.isLeaderOnly = l;
        this.maxPerTeam = mt;
        this.maxPerSquad = ms;
        this.minSquadPlayers = minPlayers;
        this.displayName = displayName != null ? displayName : "";
        this.resupplyFlags = flags;
        this.saveNbtFlags = nbtFlags;

        // --- РЎР›РћРўР« РљРРўРђ (0 - 40) ---

        // 1. РћСЃРЅРѕРІРЅР°СЏ СЃРµС‚РєР° (9 - 35)
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(kitInv, 9 + col + row * 9, 8 + col * 18, SLOT_Y_OFFSET + 66 + row * 18));
            }
        }

        // 2. РҐРѕС‚Р±Р°СЂ (0 - 8)
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(kitInv, col, 8 + col * 18, SLOT_Y_OFFSET + 124));
        }

        // 3. Р‘СЂРѕРЅСЏ (36 - 39)
        for (int i = 0; i < 4; ++i) {
            final int armorIndex = i;
            int slotIndex = 36 + i;
            this.addSlot(new Slot(kitInv, slotIndex, 8 + i * 18, SLOT_Y_OFFSET + 150) {
                @Override
                public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
                    return Pair.of(InventoryMenu.BLOCK_ATLAS, ARMOR_SLOT_TEXTURES[armorIndex]);
                }
            });
        }

        // 4. Р’С‚РѕСЂР°СЏ СЂСѓРєР° (40)
        this.addSlot(new Slot(kitInv, 40, 84, SLOT_Y_OFFSET + 150) {
            @Override
            public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
                return Pair.of(InventoryMenu.BLOCK_ATLAS, InventoryMenu.EMPTY_ARMOR_SLOT_SHIELD);
            }
        });

        // --- 5. РќРћР’Р«Р• "CURSE" РЎР›РћРўР« (41 - 48) ---
        // РЎР»РµРІР° РѕС‚ РѕСЃРЅРѕРІРЅРѕРіРѕ РёРЅРІРµРЅС‚Р°СЂСЏ. РљРѕРѕСЂРґРёРЅР°С‚Р° X: -36 Рё -18 (РѕС‚РЅРѕСЃРёС‚РµР»СЊРЅРѕ Р»РµРІРѕРіРѕ РєСЂР°СЏ РѕСЃРЅРѕРІРЅРѕРіРѕ РѕРєРЅР°)
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 2; col++) {
                int slotIndex = 41 + (row * 2 + col);
                this.addSlot(new Slot(kitInv, slotIndex, -36 + col * 18, SLOT_Y_OFFSET + 66 + row * 18));
            }
        }

        // --- РЎР›РћРўР« РРќР’Р•РќРўРђР РЇ РР“Р РћРљРђ ---
        for (int i = 0; i < 3; ++i) {
            for (int j = 0; j < 9; ++j) {
                this.addSlot(new Slot(playerInv, j + i * 9 + 9, 8 + j * 18, SLOT_Y_OFFSET + 180 + i * 18));
            }
        }
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(playerInv, i, 8 + i * 18, SLOT_Y_OFFSET + 238));
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