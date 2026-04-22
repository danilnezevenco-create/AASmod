// PATH: src\main\java\com\example\aas\block\AmmoStackBlockEntity.java
package com.example.aas.block;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;
import java.util.List;

public class AmmoStackBlockEntity extends BlockEntity {

    private final List<Integer> ammoCounts = new ArrayList<>();

    public AmmoStackBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.AMMO_STACK_BE.get(), pos, state);
    }

    public void addAmmoBox(int amount) {
        if (ammoCounts.size() < 4) {
            ammoCounts.add(amount);
            setChanged();
            if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public int removeTopAmmoBox() {
        if (!ammoCounts.isEmpty()) {
            int amount = ammoCounts.remove(ammoCounts.size() - 1);
            setChanged();
            if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            return amount;
        }
        return 0;
    }

    public List<Integer> getAmmoCounts() {
        return new ArrayList<>(ammoCounts);
    }

    public void clear() {
        ammoCounts.clear();
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putIntArray("AmmoCounts", ammoCounts.stream().mapToInt(i -> i).toArray());
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        ammoCounts.clear();
        if (tag.contains("AmmoCounts")) {
            int[] loadedArray = tag.getIntArray("AmmoCounts");
            for (int i : loadedArray) {
                ammoCounts.add(i);
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}