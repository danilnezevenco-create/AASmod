package com.example.aas.block;
import com.example.aas.config.AASConfig; // <--
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
public class TOWConstructionBlockEntity extends BlockEntity {
    public static final int MAX_PROGRESS = 2400;
    private int currentProgress = 0;
    private int activeDiggers = 0;
    private boolean sapperBoost = false;
    private String teamOwner = "NEUTRAL";

    public TOWConstructionBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.TOW_CONSTRUCTION_BE.get(), pos, state);
    }

    public void setTeam(String team) { this.teamOwner = team; setChanged(); }
    public String getTeam() { return teamOwner; }
    public float getPercentage() { return (float) currentProgress / MAX_PROGRESS; }

    public void addProgress() { addProgress(false); }

    public void addProgress(boolean isSapper) {
        if (currentProgress < MAX_PROGRESS) {
            this.activeDiggers++;
            if (isSapper) this.sapperBoost = true;
            setChanged();
        }
    }
    public void addCreativeProgress(int amount) {
        this.currentProgress += amount;
        if (this.currentProgress >= MAX_PROGRESS) this.currentProgress = MAX_PROGRESS;
        setChanged();
    }

    public static void tick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, TOWConstructionBlockEntity entity) {
        if (level.isClientSide) return;

        if (entity.activeDiggers > 0 || entity.currentProgress > 0) {
            if (entity.activeDiggers > 0) {
                float speed = (entity.activeDiggers >= 2) ? 2.0f : 1.0f;
                if (entity.sapperBoost) speed *= 2.0f;
                float multiplier = com.example.aas.config.AASConfig.DIGGING_SPEED_MULTIPLIER.get().floatValue();
                speed *= multiplier;
                entity.currentProgress += (int) Math.ceil(speed);
            }

            if (entity.currentProgress >= MAX_PROGRESS) {
                if (state.getBlock() instanceof TOWConstructionBlock block) {
                    // === ПЫЛЬ ДЛЯ TOW ===
                    ((ServerLevel) level).sendParticles(net.minecraft.core.particles.ParticleTypes.CAMPFIRE_COSY_SMOKE,
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                            30, 0.7, 0.4, 0.7, 0.05);
                    block.finishConstruction((ServerLevel) level, pos, state);
                }
            }

            if (level.getGameTime() % 10 == 0) {
                level.sendBlockUpdated(pos, state, state, 3);
            }
        }
        entity.activeDiggers = 0;
        entity.sapperBoost = false;
    }

    // ... (save/load) ...
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
        if (tag.contains("TeamOwner")) teamOwner = tag.getString("TeamOwner");
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