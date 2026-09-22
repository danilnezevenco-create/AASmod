package com.example.aas.network;

import com.example.aas.config.AASConfig;
import com.example.aas.world.AASWorldData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketRequestVehicleAmmo {
    private final int entityId;
    private final int type;

    public PacketRequestVehicleAmmo(int entityId, int type) {
        this.entityId = entityId;
        this.type = type;
    }

    public static void encode(PacketRequestVehicleAmmo msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
        buf.writeInt(msg.type);
    }

    public static PacketRequestVehicleAmmo decode(FriendlyByteBuf buf) {
        return new PacketRequestVehicleAmmo(buf.readInt(), buf.readInt());
    }

    public static void handle(PacketRequestVehicleAmmo msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            Entity vehicle = player.level().getEntity(msg.entityId);
            if (vehicle == null || player.distanceToSqr(vehicle) > 64.0) return;

            String kitName = player.getPersistentData().getString("AAS_CurrentKit");
            if (kitName.isEmpty() || kitName.equals("Unassigned")) {
                player.displayClientMessage(Component.literal("No Kit equipped!").withStyle(ChatFormatting.RED), true);
                return;
            }

            // Р§РёС‚Р°РµРј С‚РµРєСѓС‰РёРµ РјР°С‚РµСЂРёР°Р»С‹ С‚РµС…РЅРёРєРё
            int currentMats = vehicle.getPersistentData().getInt("AAS_VehicleMats");
            int cost = AASConfig.HUB_RESUPPLY_COST.get();

            // === РћРЎРћР‘РћР• РџР РђР’РР›Рћ: РєРёС‚С‹ "Drone Operator" Рё "Sapper" РїРѕРїРѕР»РЅСЏСЋС‚СЃСЏ СЃ РјР°С€РёРЅС‹ РїРѕ С„РёРєСЃРёСЂРѕРІР°РЅРЅРѕР№ С†РµРЅРµ ===
            boolean isDroneOperator = "Drone Operator".equalsIgnoreCase(kitName);
            boolean isSapper = "Sapper".equalsIgnoreCase(kitName);
            if (isDroneOperator) {
                cost = AASConfig.DRONE_OPERATOR_VEHICLE_RESUPPLY_COST.get();
            } else if (isSapper) {
                cost = AASConfig.SAPPER_VEHICLE_RESUPPLY_COST.get();
            }

            if (!player.isCreative() && currentMats < cost) {
                player.displayClientMessage(Component.literal("Not enough Materials in Vehicle! (" + currentMats + ")").withStyle(ChatFormatting.RED), true);
                return;
            }

            AASWorldData data = AASWorldData.get(player.serverLevel());
            String t = player.getTeam() != null ? player.getTeam().getName().toUpperCase() : "NEUTRAL";
            boolean isAltVariant = player.getPersistentData().getBoolean("AAS_CurrentKitAlt");
            AASWorldData.KitInfo kit = data.getKitVariant(t, kitName, isAltVariant);

            if (kit != null) {
                if (ResupplyHandler.resupplyPlayer(player, kit, false)) {
                    int newMats = currentMats;
                    if (!player.isCreative()) {
                        newMats = currentMats - cost;
                        // Р–Р•Р›Р•Р—РћР‘Р•РўРћРќРќРћР• РЎРџРРЎРђРќРР•
                        vehicle.getPersistentData().putInt("AAS_VehicleMats", newMats);
                    }
                    // РџРёС€РµРј РЅР°Рґ С…РѕС‚Р±Р°СЂРѕРј Р·РµР»РµРЅС‹Рј
                    player.displayClientMessage(Component.literal("Kit Resupplied! Vehicle Mats: " + newMats).withStyle(ChatFormatting.GREEN), true);
                    player.level().playSound(null, player.blockPosition(), net.minecraft.sounds.SoundEvents.ITEM_PICKUP, net.minecraft.sounds.SoundSource.PLAYERS, 1f, 1f);
                } else {
                    // РџРёС€РµРј РЅР°Рґ С…РѕС‚Р±Р°СЂРѕРј Р¶РµР»С‚С‹Рј
                    player.displayClientMessage(Component.translatable("aas.msg.kit_full_mats", currentMats).withStyle(ChatFormatting.YELLOW), true);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}