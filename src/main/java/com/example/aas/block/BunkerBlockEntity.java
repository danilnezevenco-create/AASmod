package com.example.aas.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.particles.ParticleTypes;

public class BunkerBlockEntity extends BlockEntity {
    public static final int MAX_PROGRESS = 2400;
    private int currentProgress = 0;
    private int activeDiggers = 0;
    private String teamOwner = "NEUTRAL";

    public BunkerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.BUNKER_BE.get(), pos, state);
    }

    public void addProgress() { this.activeDiggers++; setChanged(); }
    public void addCreativeProgress(int amount) { this.currentProgress = Math.min(MAX_PROGRESS, this.currentProgress + amount); setChanged(); }
    public float getPercentage() { return (float) currentProgress / MAX_PROGRESS; }
    public String getTeam() { return teamOwner; }
    public void setTeam(String team) { this.teamOwner = team; setChanged(); }

    public static void tick(Level level, BlockPos pos, BlockState state, BunkerBlockEntity entity) {
        if (level.isClientSide) return;

        if (entity.activeDiggers > 0) {
            float multiplier = com.example.aas.config.AASConfig.DIGGING_SPEED_MULTIPLIER.get().floatValue();
            entity.currentProgress += (int) Math.ceil(entity.activeDiggers * multiplier);

            if (entity.currentProgress >= MAX_PROGRESS) {
                entity.finishConstruction((ServerLevel) level, pos, state);
            }
            setChanged(level, pos, state);
        }
        entity.activeDiggers = 0;
    }

    // Найти метод finishConstruction и заменить блок установки сетки (NET)
    private void finishConstruction(ServerLevel level, BlockPos pos, BlockState state) {
        Direction bunkerFacing = state.getValue(BunkerBlock.FACING);
        level.removeBlock(pos, false);

        BlockState wall = ModBlocks.WALL_BLOCK.get().defaultBlockState().setValue(WallBlock.CONSTRUCTED, true);
        BlockState slab = ModBlocks.WALL_SLAB_BLOCK.get().defaultBlockState().setValue(WallBlock.CONSTRUCTED, true);
        BlockState netBase = ModBlocks.CAMO_NET_BLOCK.get().defaultBlockState();

        for (int y = 0; y <= 2; y++) {
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    BlockPos target = pos.offset(x, y, z);

                    if (y == 2) {
                        level.setBlock(target, slab, 3);
                    } else {
                        if (x == 0 && z == 0) {
                            level.removeBlock(target, false);
                            continue;
                        }

                        boolean isEntrance = false;
                        if (bunkerFacing == Direction.NORTH && x == 0 && z == -1) isEntrance = true;
                        else if (bunkerFacing == Direction.SOUTH && x == 0 && z == 1) isEntrance = true;
                        else if (bunkerFacing == Direction.WEST && x == -1 && z == 0) isEntrance = true;
                        else if (bunkerFacing == Direction.EAST && x == 1 && z == 0) isEntrance = true;

                        if (isEntrance) {
                            // netFacing всегда = bunkerFacing, чтобы северная (лицевая) сторона
                            // модели сетки всегда смотрела наружу бункера, независимо от направления входа
                            level.setBlock(target, netBase.setValue(HorizontalDirectionalBlock.FACING, bunkerFacing), 3);
                        } else {
                            level.setBlock(target, wall, 3);
                        }
                    }
                }
            }
        }
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE,
                pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 40, 0.7, 0.5, 0.7, 0.05);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("BuildProgress", currentProgress);
        tag.putString("TeamOwner", teamOwner);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        currentProgress = tag.getInt("BuildProgress");
        teamOwner = tag.getString("TeamOwner");
    }
}