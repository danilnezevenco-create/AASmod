package com.example.aas.block;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class M2ConstructionBlockEntity extends BlockEntity {

    public static final int MAX_PROGRESS = 1800; // 1.5 минуты
    private int currentProgress = 0;
    private int activeDiggers = 0;

    // === ДОБАВЛЕНО ПОЛЕ КОМАНДЫ ===
    private String teamOwner = "NEUTRAL";

    public M2ConstructionBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.M2_CONSTRUCTION_BE.get(), pos, state);
    }

    public void setTeam(String team) {
        this.teamOwner = team;
        setChanged();
    }

    public String getTeam() {
        return teamOwner;
    }

    public float getPercentage() {
        return (float) currentProgress / MAX_PROGRESS;
    }

    public void addProgress() {
        if (currentProgress < MAX_PROGRESS) {
            this.activeDiggers++;
            setChanged();
        }
    }

    public void addCreativeProgress(int amount) {
        this.currentProgress += amount;
        if (this.currentProgress >= MAX_PROGRESS) this.currentProgress = MAX_PROGRESS;
        setChanged();
    }

    public static void tick(Level level, BlockPos pos, BlockState state, M2ConstructionBlockEntity entity) {
        if (level.isClientSide) return;

        if (entity.activeDiggers > 0 || entity.currentProgress > 0) {
            if (entity.activeDiggers > 0) {
                float speed = (entity.activeDiggers >= 2) ? 2.0f : 1.0f;
                entity.currentProgress += (int) Math.ceil(speed);
            }

            if (entity.currentProgress >= MAX_PROGRESS) {
                if (state.getBlock() instanceof M2ConstructionBlock block) {
                    block.finishConstruction((ServerLevel) level, pos, state);
                }
            }

            if (level.getGameTime() % 10 == 0) {
                level.sendBlockUpdated(pos, state, state, 3);
            }
        }
        entity.activeDiggers = 0;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("BuildProgress", currentProgress);
        tag.putString("TeamOwner", teamOwner); // Сохраняем команду
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        currentProgress = tag.getInt("BuildProgress");
        if (tag.contains("TeamOwner")) {
            teamOwner = tag.getString("TeamOwner"); // Загружаем команду
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }
}