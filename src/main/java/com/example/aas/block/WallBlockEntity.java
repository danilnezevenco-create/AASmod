package com.example.aas.block;

import com.example.aas.config.AASConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

public class WallBlockEntity extends BlockEntity {

    private int currentProgress = 0;
    private int activeDiggers = 0;
    private boolean sapperBoost = false;
    private String teamOwner = "NEUTRAL";
    private List<BlockPos> linkedWalls = new ArrayList<>();
    private boolean isMultiWall = false;
    private boolean isUpdatingLinked = false;
    private int damageStage = 0;

    // Логика трансформации для бункера
    private String transformTo = "";
    private int transformDir = 2; // По умолчанию Север (2)

    public WallBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.WALL_BE.get(), pos, state);
    }

    public int getMaxProgress() {
        int size = linkedWalls.size();
        if (size > 15) return 2400; // Прогресс для бункера
        if (size > 5) return 1200;  // Прогресс для стен 3х3
        if (size > 0 || isMultiWall) return 600; // Стены 2х2
        return 300; // Одиночная стена
    }

    public void setTeam(String team) { this.teamOwner = team; setChanged(); }
    public void setLinkedWalls(List<BlockPos> links) { this.linkedWalls = new ArrayList<>(links); this.isMultiWall = true; setChanged(); }
    public String getTeam() { return teamOwner; }
    public float getPercentage() { return (float) currentProgress / getMaxProgress(); }

    public void setTransformTo(String blockId, int directionIndex) {
        this.transformTo = blockId;
        this.transformDir = directionIndex;
        setChanged();
    }

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
        if (isMultiWall && !linkedWalls.isEmpty() && level != null) {
            isUpdatingLinked = true;
            for (BlockPos linkPos : linkedWalls) {
                if (linkPos.equals(this.worldPosition)) continue;
                if (level.isLoaded(linkPos)) {
                    BlockEntity be = level.getBlockEntity(linkPos);
                    if (be instanceof WallBlockEntity linkedWall) {
                        if (isCreative) linkedWall.performCreativeAdd(amount);
                        else linkedWall.performAddProgress(isSapper);
                    } else if (be instanceof BarbedWireBlockEntity linkedWire) {
                        if (isCreative) linkedWire.performCreativeAdd(amount);
                        else linkedWire.performAddProgress(isSapper);
                    }
                }
            }
            isUpdatingLinked = false;
        }
    }

    public void performAddProgress() { performAddProgress(false); }

    public void performAddProgress(boolean isSapper) {
        if (currentProgress < getMaxProgress()) {
            this.activeDiggers++;
            if (isSapper) this.sapperBoost = true;
            setChanged();
        }
    }

    public void performCreativeAdd(int amount) {
        int max = getMaxProgress();
        if (currentProgress < max) {
            this.currentProgress = Math.min(max, this.currentProgress + amount);
            setChanged();
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, WallBlockEntity entity) {
        if (level.isClientSide) return;
        if (state.getValue(WallBlock.CONSTRUCTED)) return;

        // Переход из состояния "голограмма" в состояние "чертеж в работе"
        if (state.getValue(WallBlock.BUILD_STAGE) == 0) {
            level.setBlock(pos, state.setValue(WallBlock.BUILD_STAGE, 1), 3);
        }

        if (entity.activeDiggers > 0 || entity.currentProgress > 0) {
            if (entity.activeDiggers > 0) {
                float speed = (entity.activeDiggers == 1) ? 1.0f :
                        (entity.activeDiggers == 2) ? 1.34f :
                                (entity.activeDiggers == 3) ? 2.0f : 4.0f;
                if (entity.sapperBoost) speed *= 2.0f;

                float multiplier = AASConfig.DIGGING_SPEED_MULTIPLIER.get().floatValue();
                entity.currentProgress += (int) Math.ceil(speed * multiplier);
            }

            int max = entity.getMaxProgress();

            if (entity.currentProgress >= max) {
                entity.currentProgress = max;

                // ЛОГИКА ЗАВЕРШЕНИЯ / ТРАНСФОРМАЦИИ
                if (!entity.transformTo.isEmpty()) {
                    Block target = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(entity.transformTo));
                    if (target != null) {
                        BlockState finalState = target.defaultBlockState();
                        // Используем HORIZONTAL_FACING и from2DDataValue
                        if (finalState.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                            finalState = finalState.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.from2DDataValue(entity.transformDir));
                        }
                        level.setBlock(pos, finalState, 3);
                    }
                } else {
                    level.setBlock(pos, state.setValue(WallBlock.CONSTRUCTED, true).setValue(WallBlock.BUILD_STAGE, 2), 3);
                }

                // Эффекты дыма
                ((net.minecraft.server.level.ServerLevel) level).sendParticles(
                        net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        15, 0.5, 0.3, 0.5, 0.03);
            } else {
                // Обновление этапа визуализации
                int newStage = (entity.currentProgress >= max / 2) ? 2 : 1;
                if (state.getValue(WallBlock.BUILD_STAGE) != newStage) {
                    level.setBlock(pos, state.setValue(WallBlock.BUILD_STAGE, newStage), 3);
                }
            }

            if (level.getGameTime() % 5 == 0 || entity.currentProgress >= max) {
                level.sendBlockUpdated(pos, state, state, 3);
            }
        }
        entity.activeDiggers = 0;
        entity.sapperBoost = false;
    }

    public int getProgress() { return currentProgress; }

    public void setProgress(int progress) {
        int max = getMaxProgress();
        this.currentProgress = Math.max(0, Math.min(max, progress));
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public int getDamageStage() { return damageStage; }

    public void setDamageStage(int stage) {
        this.damageStage = stage;
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("BuildProgress", currentProgress);
        tag.putString("TeamOwner", teamOwner);
        tag.putBoolean("IsMultiWall", isMultiWall);
        tag.putString("TransformTo", transformTo);
        tag.putInt("TransformDir", transformDir);
        tag.putInt("DamageStage", damageStage);

        ListTag list = new ListTag();
        for (BlockPos p : linkedWalls) list.add(LongTag.valueOf(p.asLong()));
        tag.put("LinkedWalls", list);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        currentProgress = tag.getInt("BuildProgress");
        teamOwner = tag.getString("TeamOwner");
        isMultiWall = tag.getBoolean("IsMultiWall");
        transformTo = tag.getString("TransformTo");
        transformDir = tag.getInt("TransformDir");
        if (tag.contains("DamageStage")) damageStage = tag.getInt("DamageStage");

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