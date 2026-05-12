package com.example.aas.network;

import com.example.aas.item.SupplyTruckMarkerItem;
import com.example.aas.item.VehicleMarkerItem;
import com.example.aas.world.AASWorldData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;

public class PacketApplyMarker {
    private final int targetEntityId;

    public PacketApplyMarker(int entityId) {
        this.targetEntityId = entityId;
    }

    public static void encode(PacketApplyMarker msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.targetEntityId);
    }

    public static PacketApplyMarker decode(FriendlyByteBuf buf) {
        return new PacketApplyMarker(buf.readInt());
    }

    public static void handle(PacketApplyMarker msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ServerLevel level = player.serverLevel();
            Entity target = level.getEntity(msg.targetEntityId);
            ItemStack stack = player.getMainHandItem();
            AASWorldData data = AASWorldData.get(level);

            if (target != null && player.distanceTo(target) < 10) {
                String team = "";
                String type = "";
                int penalty = 0;
                boolean isSupply = false;

                // 1. ОПРЕДЕЛЯЕМ ПАРАМЕТРЫ ИЗ ПРЕДМЕТА
                if (stack.getItem() instanceof VehicleMarkerItem markerItem) {
                    team = markerItem.getTeam();
                    type = markerItem.getType();
                    penalty = markerItem.getPenalty();
                }
                else if (stack.getItem() instanceof SupplyTruckMarkerItem supplyItem) {
                    team = supplyItem.getTeam();
                    type = supplyItem.getVehicleType();
                    penalty = supplyItem.getPenalty();
                    isSupply = true;
                }

                if (!team.isEmpty()) {
                    // 1. Серверные данные (NBT)
                    target.getPersistentData().putString("AAS_VehicleTeam", team);
                    target.getPersistentData().putString("AAS_VehicleType", type);
                    target.getPersistentData().putInt("AAS_TicketPenalty", penalty);
                    if (isSupply) {
                        target.getPersistentData().putBoolean("AAS_IsSupplyTruck", true);
                        // Даем 2 ящика сразу при клейме
                        target.getPersistentData().putInt("AAS_SupplyAmmo", com.example.aas.config.AASConfig.SUPPLY_TRUCK_CRATES.get());
                    } else {
                        // Если переклеймили в обычную технику - забираем возможность кидать ящики
                        target.getPersistentData().remove("AAS_IsSupplyTruck");
                        target.getPersistentData().remove("AAS_SupplyAmmo");
                    }
                    // 2. Глобальные данные мира (для карты)
                    // Удаляем старую запись по UUID, если она была (переклейм)
                    data.markedVehicles.removeIf(v -> v.uuid.equals(target.getUUID()));

                    BlockPos spawnerPos = null;
                    if (target.getPersistentData().contains("AAS_SpawnerPos")) {
                        spawnerPos = BlockPos.of(target.getPersistentData().getLong("AAS_SpawnerPos"));
                    }

                    // Добавляем новую
                    data.markedVehicles.add(new AASWorldData.VehicleRecord(
                            target.getUUID(),
                            team,
                            type,
                            target.getX(),
                            target.getY(),
                            target.getZ(),
                            target.getYRot(),
                            spawnerPos
                    ));

                    // 3. Синхронизация
                    PacketHandler.sendToAllClients(level, data);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}