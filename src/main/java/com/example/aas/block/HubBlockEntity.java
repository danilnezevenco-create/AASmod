package com.example.aas.block;
import com.example.aas.client.ClientHooks;
import com.example.aas.config.AASConfig; // <-- ИМПОРТ КОНФИГА
import com.example.aas.network.PacketHandler;
import com.example.aas.world.AASWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
public class HubBlockEntity extends BlockEntity {
    // ... (Поля класса те же) ...
    public static final int MAX_PROGRESS = 2400;
    private int currentProgress = 0;
    private int activeDiggers = 0;
    private String teamOwner = "NEUTRAL";
    private int constructionMaterials = 200;
    public int cooldownAGS = 0;
    public int cooldownM2 = 0;
    public int cooldownMortar = 0;
    public int cooldownTOW = 0;
    public boolean wasDismantled = false;
    private Object clientSoundRef = null;

    public HubBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.HUB_BE.get(), pos, state);
    }

// ... (setTeam, getTeam, setCooldown, addProgress и т.д. без изменений) ...

    public void addProgress() { if (currentProgress < MAX_PROGRESS) this.activeDiggers++; }
    public void addCreativeProgress(int amount) {
        if (currentProgress < MAX_PROGRESS) {
            this.currentProgress += amount;
            if (this.currentProgress >= MAX_PROGRESS) this.currentProgress = MAX_PROGRESS;
        }
    }
    public void setTeam(String team) { this.teamOwner = team; setChanged(); if(level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3); }
    public String getTeam() { return teamOwner; }
    public void setCooldown(int type, int ticks) {
        if (type == 1) this.cooldownAGS = ticks;
        if (type == 2) this.cooldownM2 = ticks;
        if (type == 3) this.cooldownMortar = ticks;
        if (type == 4) this.cooldownTOW = ticks;
        setChanged();
    }
    public int getMaterials() { return constructionMaterials; }
    public void consumeMaterials(int amount) {
        this.constructionMaterials = Math.max(0, this.constructionMaterials - amount);
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    public void addMaterials(int amount) {
        this.constructionMaterials += amount;
        if(this.constructionMaterials > 3000) this.constructionMaterials = 3000;
        setChanged();
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public static void tick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, HubBlockEntity entity) {
        // ... (Логика кулдаунов и звуков без изменений) ...
        boolean needsSync = false;
        if (entity.cooldownAGS > 0) { entity.cooldownAGS--; if(entity.cooldownAGS == 0) needsSync = true; }
        if (entity.cooldownM2 > 0) { entity.cooldownM2--; if(entity.cooldownM2 == 0) needsSync = true; }
        if (entity.cooldownMortar > 0) { entity.cooldownMortar--; if(entity.cooldownMortar == 0) needsSync = true; }
        if (entity.cooldownTOW > 0) { entity.cooldownTOW--; if(entity.cooldownTOW == 0) needsSync = true; }

        if (!level.isClientSide && needsSync) {
            level.sendBlockUpdated(pos, state, state, 3);
        }

        if (level.isClientSide) {
            if (state.getValue(HubBlock.CONSTRUCTED)) {
                entity.handleSoundClient();
            }
            return;
        }

        if (state.getValue(HubBlock.CONSTRUCTED)) return;

        if (entity.activeDiggers > 0 || entity.currentProgress > 0) {
            if (entity.activeDiggers > 0) {
                float speed = (entity.activeDiggers == 1) ? 1.0f : (entity.activeDiggers == 2) ? 1.34f : (entity.activeDiggers == 3) ? 2.0f : 4.0f;

                // === ПУНКТ 4: ПРИМЕНЕНИЕ КОНФИГА ===
                float multiplier = AASConfig.DIGGING_SPEED_MULTIPLIER.get().floatValue();
                speed *= multiplier;

                entity.currentProgress += (int) Math.ceil(speed);
            }

            if (entity.currentProgress >= MAX_PROGRESS) {
                entity.currentProgress = MAX_PROGRESS;
                level.setBlock(pos, state.setValue(HubBlock.CONSTRUCTED, true), 3);

                if (!level.isClientSide) {
                    AASWorldData data = AASWorldData.get((ServerLevel) level);
                    for (AASWorldData.HubInfo h : data.hubs) {
                        if (h.pos.equals(pos)) {
                            h.constructed = true;
                            data.setDirty();
                            PacketHandler.sendToAllClients((ServerLevel)level, data); // Используем правильный метод синхронизации
                            break;
                        }
                    }
                }
            }

            if (level.getGameTime() % 5 == 0 || entity.currentProgress >= MAX_PROGRESS) {
                level.sendBlockUpdated(pos, state, state, 3);
            }
        }
        entity.activeDiggers = 0;
    }

    // ... (Остальные методы: saveAdditional, load, getUpdateTag и т.д. без изменений) ...


    private void handleSoundClient() {
        net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> {
            this.clientSoundRef = com.example.aas.client.ClientHooks.playHubSound(this, this.clientSoundRef);
        });
    }

    @Override
    public void setRemoved() {
        if (this.level != null && this.level.isClientSide) {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> {
                com.example.aas.client.ClientHooks.stopHubSound(this.clientSoundRef);
            });
        }
        super.setRemoved();
    }

    public float getPercentage() { return (float) currentProgress / MAX_PROGRESS; }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt("BuildProgress", currentProgress);
        tag.putString("TeamOwner", teamOwner);
        tag.putInt("Materials", constructionMaterials);
        tag.putInt("CooldownAGS", cooldownAGS);
        tag.putInt("CooldownM2", cooldownM2);
        tag.putInt("CooldownMortar", cooldownMortar);
        tag.putInt("CooldownTOW", cooldownTOW);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        currentProgress = tag.getInt("BuildProgress");
        if (tag.contains("TeamOwner")) teamOwner = tag.getString("TeamOwner");
        if (tag.contains("Materials")) constructionMaterials = tag.getInt("Materials");
        if (tag.contains("CooldownAGS")) cooldownAGS = tag.getInt("CooldownAGS");
        if (tag.contains("CooldownM2")) cooldownM2 = tag.getInt("CooldownM2");
        if (tag.contains("CooldownMortar")) cooldownMortar = tag.getInt("CooldownMortar");
        if (tag.contains("CooldownTOW")) cooldownTOW = tag.getInt("CooldownTOW");
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