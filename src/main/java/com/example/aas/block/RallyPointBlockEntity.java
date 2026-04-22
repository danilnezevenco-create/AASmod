package com.example.aas.block;

import com.example.aas.network.PacketHandler;
import com.example.aas.network.PacketSyncSquads;
import com.example.aas.world.AASWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.PacketDistributor;

public class RallyPointBlockEntity extends BlockEntity {
    public boolean isDecay = false; // Флаг для предотвращения штрафа при исчезновении по времени
    private int squadId = -1;

    public RallyPointBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.RALLY_BE.get(), pos, state);
    }

    public void setSquadId(int id) { this.squadId = id; setChanged(); }
    public int getSquadId() { return squadId; }

    public void cleanupData(ServerLevel level) {
        if (squadId == -1) return;
        AASWorldData data = AASWorldData.get(level);
        boolean changed = false;
        for (AASWorldData.Squad s : data.squads) {
            if (s.id == this.squadId) {
                if (s.rallyPos != null && s.rallyPos.equals(this.worldPosition)) {
                    s.rallyPos = null;
                    s.rallyExpiryTick = -1; // Сбрасываем таймер в данных
                    changed = true;
                }
                break;
            }
        }
        if (changed) {
            data.setDirty();
            PacketHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new PacketSyncSquads(data.squads));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("SquadID", squadId);
        tag.putBoolean("IsDecay", isDecay);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        squadId = tag.getInt("SquadID");
        isDecay = tag.getBoolean("IsDecay");
    }
}