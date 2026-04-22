package com.example.aas.menu;

import com.example.aas.block.ModBlocks;
import com.example.aas.block.VehicleSpawnerBlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.SlotItemHandler;

public class VehicleSpawnerMenu extends AbstractContainerMenu {
    public final VehicleSpawnerBlockEntity blockEntity;

    public VehicleSpawnerMenu(int id, Inventory inv, FriendlyByteBuf extraData) {
        this(id, inv, inv.player.level().getBlockEntity(extraData.readBlockPos()));
    }

    public VehicleSpawnerMenu(int id, Inventory inv, BlockEntity entity) {
        super(ModMenuTypes.VEHICLE_SPAWNER_MENU.get(), id);
        this.blockEntity = (VehicleSpawnerBlockEntity) entity;

        // Слот 0: Модификатор
        this.addSlot(new SlotItemHandler(blockEntity.inventory, 0, 15, 45));

        // Сетка 8x4 (32 слота)
        int gridStartX = 26;
        int gridStartY = 75;

        for (int row = 0; row < 4; ++row) {
            for (int col = 0; col < 8; ++col) {
                // Индекс 1 + ... (т.к. 0 занят модификатором)
                this.addSlot(new SlotItemHandler(blockEntity.inventory, 1 + col + (row * 8),
                        gridStartX + col * 18,
                        gridStartY + row * 18));
            }
        }

        // Инвентарь игрока (сдвинут вниз)
        addPlayerInventory(inv, 160);
        addPlayerHotbar(inv, 218);
    }

    private void addPlayerInventory(Inventory playerInventory, int startY) {
        for (int i = 0; i < 3; ++i) {
            for (int l = 0; l < 9; ++l) {
                this.addSlot(new Slot(playerInventory, l + i * 9 + 9, 17 + l * 18, startY + i * 18));
            }
        }
    }

    private void addPlayerHotbar(Inventory playerInventory, int startY) {
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(playerInventory, i, 17 + i * 18, startY));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player playerIn, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos()),
                player, ModBlocks.VEHICLE_SPAWNER_BLOCK.get());
    }
}