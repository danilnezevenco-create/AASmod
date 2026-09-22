// PATH: src\main\java\com\example\aas\block\BarbedWireBlockEntity.java
package com.example.aas.block;

import com.example.aas.config.AASConfig;
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

    public static final int MAX_PROGRESS = 1200;
    private int currentProgress = 0;
    private int activeDiggers = 0;
    private boolean sapperBoost = false;
    private String teamOwner = "NEUTRAL";
    private List<BlockPos> linkedWires = new ArrayList<>();
    private boolean isMultiWire = false;
    private boolean isUpdatingLinked = false;
    private int damageStage = 0;

    public BarbedWireBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.WIRE_BE.get(), pos, state);
    }

    public void setTeam(String team) { this.teamOwner = team; setChanged(); }
    public void setLinkedWires(List<BlockPos> links) { this.linkedWires = new ArrayList<>(links); this.isMultiWire = true; setChanged(); }
    public String getTeam() { return teamOwner; }
    public float getPercentage() { return (float) currentProgress / MAX_PROGRESS; }

    public void addProgress() { addProgress(false); }

    public void addProgress(boolean isSapper) {
        if (isUpdatingLinked) return;
        performAddProgress(isSapper);
        propagateToLinks(false, 0, isSapper);
    }

    public void addCreativeProgress(int amount) {
        if (isUpdatingLinked) return;
        performCreativeAdd(amount);
        propagateToLinks(true, amount, false);
    }

    private void propagateToLinks(boolean isCreative, int amount, boolean isSapper) {
        if (isMultiWire && !linkedWires.isEmpty() && level != null) {
            isUpdatingLinked = true;
            for (BlockPos linkPos : linkedWires) {
                if (linkPos.equals(this.worldPosition)) continue;
                if (level.isLoaded(linkPos)) {
                    BlockEntity be = level.getBlockEntity(linkPos);
                    if (be instanceof BarbedWireBlockEntity linkedWire) {
                        if (isCreative) linkedWire.performCreativeAdd(amount);
                        else linkedWire.performAddProgress(isSapper);
                    } else if (be instanceof WallBlockEntity linkedWall) {
                        if (isCreative) linkedWall.performCreativeAdd(amount);
                        else linkedWall.performAddProgress(isSapper);
                    }
                }
            }
            isUpdatingLinked = false;
        }
    }

    public void performAddProgress() { performAddProgress(false); }

    public void performAddProgress(boolean isSapper) {
        if (currentProgress < MAX_PROGRESS) {
            this.activeDiggers++;
            if (isSapper) this.sapperBoost = true;
            setChanged();
        }
    }

    public void performCreativeAdd(int amount) {
        this.currentProgress += amount;
        if (this.currentProgress >= MAX_PROGRESS) this.currentProgress = MAX_PROGRESS;
        setChanged();
    }

    public static void tick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, BarbedWireBlockEntity entity) {
        if (level.isClientSide) return;
        if (state.getValue(BarbedWireBlock.CONSTRUCTED)) return;

        if (state.getValue(BarbedWireBlock.BUILD_STAGE) == 0) {
            state = state.setValue(BarbedWireBlock.BUILD_STAGE, 1);
            level.setBlock(pos, state, 3);
        }

        if (entity.activeDiggers > 0 || entity.currentProgress > 0) {
            if (entity.activeDiggers > 0) {
                float speed = (entity.activeDiggers >= 3) ? 2.0f : 1.0f;
                if (entity.sapperBoost) speed *= 2.0f;
                float multiplier = com.example.aas.config.AASConfig.DIGGING_SPEED_MULTIPLIER.get().floatValue();
                speed *= multiplier;
                entity.currentProgress += (int) Math.ceil(speed);
            }

            if (entity.currentProgress >= MAX_PROGRESS) {
                entity.currentProgress = MAX_PROGRESS;
                level.setBlock(pos, state.setValue(BarbedWireBlock.CONSTRUCTED, true).setValue(BarbedWireBlock.BUILD_STAGE, 2), 3);

                if (!level.isClientSide) {
                    ((net.minecraft.server.level.ServerLevel) level).sendParticles(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE,
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                            15, 0.5, 0.3, 0.5, 0.03);
                }
            } else {
                int newStage = (entity.currentProgress >= MAX_PROGRESS / 2) ? 2 : 1;
                if (state.getValue(BarbedWireBlock.BUILD_STAGE) != newStage) {
                    state = state.setValue(BarbedWireBlock.BUILD_STAGE, newStage);
                    level.setBlock(pos, state, 3);
                }
            }

            if (level.getGameTime() % 5 == 0 || entity.currentProgress >= MAX_PROGRESS) {
                level.sendBlockUpdated(pos, state, state, 3);
            }
        }
        entity.activeDiggers = 0;
        entity.sapperBoost = false;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("BuildProgress", currentProgress);
        tag.putString("TeamOwner", teamOwner);
        tag.putBoolean("IsMultiWire", isMultiWire);
        tag.putInt("DamageStage", damageStage);
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
        if (tag.contains("DamageStage")) damageStage = tag.getInt("DamageStage");
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
    public int getProgress() { return currentProgress; }

    public void setProgress(int progress) {
        this.currentProgress = Math.max(0, Math.min(MAX_PROGRESS, progress));
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public int getDamageStage() { return damageStage; }

    public void setDamageStage(int stage) {
        this.damageStage = stage;
        setChanged();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}