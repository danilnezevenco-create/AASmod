package com.example.aas.block;
import com.example.aas.config.AASConfig; // <--
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;
import java.util.List;
public class BarbedWireBlockEntity extends BlockEntity {

    public static final int MAX_PROGRESS = 1200; // 1 минута
    private int currentProgress = 0;
    private int activeDiggers = 0;
    private String teamOwner = "NEUTRAL";
    private List<BlockPos> linkedWires = new ArrayList<>();
    private boolean isMultiWire = false;
    private boolean isUpdatingLinked = false;

    public BarbedWireBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.WIRE_BE.get(), pos, state);
    }

    // ... (Методы синхронизации, team, linkedWires - без изменений) ...
    public void setTeam(String team) { this.teamOwner = team; setChanged(); }
    public void setLinkedWires(List<BlockPos> links) { this.linkedWires = new ArrayList<>(links); this.isMultiWire = true; setChanged(); }
    public String getTeam() { return teamOwner; }
    public float getPercentage() { return (float) currentProgress / MAX_PROGRESS; }

    public void addProgress() {
        if (isUpdatingLinked) return;
        performAddProgress();
        propagateToLinks(false, 0);
    }
    public void addCreativeProgress(int amount) {
        if (isUpdatingLinked) return;
        performCreativeAdd(amount);
        propagateToLinks(true, amount);
    }
    private void propagateToLinks(boolean isCreative, int amount) {
        if (isMultiWire && !linkedWires.isEmpty() && level != null) {
            isUpdatingLinked = true;
            for (BlockPos linkPos : linkedWires) {
                if (linkPos.equals(this.worldPosition)) continue;
                if (level.isLoaded(linkPos)) {
                    BlockEntity be = level.getBlockEntity(linkPos);
                    if (be instanceof BarbedWireBlockEntity linkedWire) {
                        if (isCreative) linkedWire.performCreativeAdd(amount);
                        else linkedWire.performAddProgress();
                    }
                }
            }
            isUpdatingLinked = false;
        }
    }
    private void performAddProgress() {
        if (currentProgress < MAX_PROGRESS) {
            this.activeDiggers++;
            setChanged();
        }
    }
    private void performCreativeAdd(int amount) {
        this.currentProgress += amount;
        if (this.currentProgress >= MAX_PROGRESS) this.currentProgress = MAX_PROGRESS;
        setChanged();
    }

    public static void tick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, BarbedWireBlockEntity entity) {
        if (level.isClientSide) return;
        if (state.getValue(BarbedWireBlock.CONSTRUCTED)) return;

        if (entity.activeDiggers > 0 || entity.currentProgress > 0) {
            if (entity.activeDiggers > 0) {
                float speed = (entity.activeDiggers >= 3) ? 2.0f : 1.0f;

                // === ПУНКТ 4: КОНФИГ ===
                float multiplier = AASConfig.DIGGING_SPEED_MULTIPLIER.get().floatValue();
                speed *= multiplier;

                entity.currentProgress += (int) Math.ceil(speed);
            }

            if (entity.currentProgress >= MAX_PROGRESS) {
                entity.currentProgress = MAX_PROGRESS;
                level.setBlock(pos, state.setValue(BarbedWireBlock.CONSTRUCTED, true), 3);
            }

            if (level.getGameTime() % 5 == 0 || entity.currentProgress >= MAX_PROGRESS) {
                level.sendBlockUpdated(pos, state, state, 3);
            }
        }
        entity.activeDiggers = 0;
    }

    // ... (save/load) ...
    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("BuildProgress", currentProgress);
        tag.putString("TeamOwner", teamOwner);
        tag.putBoolean("IsMultiWire", isMultiWire);
        ListTag list = new ListTag();
        for (BlockPos p : linkedWires) list.add(LongTag.valueOf(p.asLong()));
        tag.put("LinkedWires", list);
    }
    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        currentProgress = tag.getInt("BuildProgress");
        if (tag.contains("TeamOwner")) teamOwner = tag.getString("TeamOwner");
        if (tag.contains("IsMultiWire")) isMultiWire = tag.getBoolean("IsMultiWire");
        linkedWires.clear();
        if (tag.contains("LinkedWires")) {
            ListTag list = tag.getList("LinkedWires", Tag.TAG_LONG);
            for (Tag t : list) linkedWires.add(BlockPos.of(((LongTag) t).getAsLong()));
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