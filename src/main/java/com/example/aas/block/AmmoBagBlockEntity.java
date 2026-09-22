// PATH: src\main\java\com\example\aas\block\AmmoBagBlockEntity.java
package com.example.aas.block;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public class AmmoBagBlockEntity extends BlockEntity {
    private UUID ownerUUID;
    // Команда владельца ("BLUE" / "RED" / "NEUTRAL"), нужна клиенту, чтобы решить,
    // показывать ли иконку сумки над блоком (видна только своей команде).
    private String ownerTeam = "NEUTRAL";

    public AmmoBagBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.AMMO_BAG_BE.get(), pos, state);
    }

    /**
     * Полная установка владельца: UUID + его команда.
     * Вызывается при постановке сумки (AmmoBagBlock#setPlacedBy).
     */
    public void setOwner(UUID uuid, String team) {
        this.ownerUUID = uuid;
        this.ownerTeam = (team != null && !team.isEmpty()) ? team.toUpperCase() : "NEUTRAL";
        setChanged();
        // Явно шлём обновление блок-энтити клиентам, чтобы иконка над сумкой
        // появилась сразу же, а не только после следующего chunk-ресинка.
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // Оставлено для обратной совместимости с местами, где вызывался старый метод.
    public void setOwnerUUID(UUID uuid) {
        setOwner(uuid, this.ownerTeam);
    }

    public UUID getOwnerUUID() {
        return this.ownerUUID;
    }

    public String getOwnerTeam() {
        return this.ownerTeam;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (ownerUUID != null) {
            tag.putUUID("OwnerUUID", ownerUUID);
        }
        tag.putString("OwnerTeam", ownerTeam);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.hasUUID("OwnerUUID")) {
            ownerUUID = tag.getUUID("OwnerUUID");
        }
        ownerTeam = tag.contains("OwnerTeam") ? tag.getString("OwnerTeam") : "NEUTRAL";
    }

    // --- Синхронизация с клиентом (нужна для рендера командной иконки) ---

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