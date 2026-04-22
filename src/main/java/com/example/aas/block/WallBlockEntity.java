package com.example.aas.block;
import com.example.aas.config.AASConfig; // <-- ИМПОРТ
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
public class WallBlockEntity extends BlockEntity {
    // ... (Поля и конструктор те же) ...
    private int currentProgress = 0;
    private int activeDiggers = 0;
    private String teamOwner = "NEUTRAL";
    private List<BlockPos> linkedWalls = new ArrayList<>();
    private boolean isMultiWall = false;
    private boolean isUpdatingLinked = false;

    public WallBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.WALL_BE.get(), pos, state);
    }

    public int getMaxProgress() {
        int size = linkedWalls.size();
        if (size > 5) return 1200;
        if (size > 0 || isMultiWall) return 600;
        return 300;
    }

    public void setTeam(String team) { this.teamOwner = team; setChanged(); }
    public void setLinkedWalls(List<BlockPos> links) { this.linkedWalls = new ArrayList<>(links); this.isMultiWall = true; setChanged(); }
    public String getTeam() { return teamOwner; }
    public float getPercentage() { return (float) currentProgress / getMaxProgress(); }

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
        if (isMultiWall && !linkedWalls.isEmpty() && level != null) {
            isUpdatingLinked = true;
            for (BlockPos linkPos : linkedWalls) {
                if (linkPos.equals(this.worldPosition)) continue;
                if (level.isLoaded(linkPos)) {
                    BlockEntity be = level.getBlockEntity(linkPos);
                    if (be instanceof WallBlockEntity linkedWall) {
                        if (isCreative) linkedWall.performCreativeAdd(amount);
                        else linkedWall.performAddProgress();
                    }
                }
            }
            isUpdatingLinked = false;
        }
    }

    private void performAddProgress() {
        if (currentProgress < getMaxProgress()) {
            this.activeDiggers++;
            setChanged();
        }
    }

    private void performCreativeAdd(int amount) {
        if (currentProgress < getMaxProgress()) {
            this.currentProgress += amount;
            if (currentProgress >= getMaxProgress()) currentProgress = getMaxProgress();
            setChanged();
        }
    }

    public static void tick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, WallBlockEntity entity) {
        if (level.isClientSide) return;
        if (state.getValue(WallBlock.CONSTRUCTED)) return;

        if (entity.activeDiggers > 0 || entity.currentProgress > 0) {
            if (entity.activeDiggers > 0) {
                float speed;
                if (entity.activeDiggers == 1) speed = 1.0f;
                else if (entity.activeDiggers == 2) speed = 1.34f;
                else if (entity.activeDiggers == 3) speed = 2.0f;
                else speed = 4.0f;

                // === ПУНКТ 4: КОНФИГ ===
                float multiplier = AASConfig.DIGGING_SPEED_MULTIPLIER.get().floatValue();
                speed *= multiplier;

                entity.currentProgress += (int) Math.ceil(speed);
            }

            int max = entity.getMaxProgress();

            if (entity.currentProgress >= max) {
                entity.currentProgress = max;
                level.setBlock(pos, state.setValue(WallBlock.CONSTRUCTED, true), 3);
            }

            if (level.getGameTime() % 5 == 0 || entity.currentProgress >= max) {
                level.sendBlockUpdated(pos, state, state, 3);
            }
        }
        entity.activeDiggers = 0;
    }

    // ... (методы сохранения/загрузки без изменений) ...
    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("BuildProgress", currentProgress);
        tag.putString("TeamOwner", teamOwner);
        tag.putBoolean("IsMultiWall", isMultiWall);
        ListTag list = new ListTag();
        for (BlockPos p : linkedWalls) list.add(LongTag.valueOf(p.asLong()));
        tag.put("LinkedWalls", list);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        currentProgress = tag.getInt("BuildProgress");
        if (tag.contains("TeamOwner")) teamOwner = tag.getString("TeamOwner");
        if (tag.contains("IsMultiWall")) isMultiWall = tag.getBoolean("IsMultiWall");
        linkedWalls.clear();
        if (tag.contains("LinkedWalls")) {
            ListTag list = tag.getList("LinkedWalls", Tag.TAG_LONG);
            for (Tag t : list) linkedWalls.add(BlockPos.of(((LongTag) t).getAsLong()));
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
