package com.example.aas.network;

import com.example.aas.entity.AGS30Entity; // <--- ДОБАВЛЕН ВАЖНЫЙ ИМПОРТ
import com.example.aas.entity.M2BrowningEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketVehicleShoot {
    public PacketVehicleShoot() {}

    public static void encode(PacketVehicleShoot msg, FriendlyByteBuf buf) {}
    public static PacketVehicleShoot decode(FriendlyByteBuf buf) { return new PacketVehicleShoot(); }

    public static void handle(PacketVehicleShoot msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                Entity vehicle = player.getVehicle();
                if (vehicle instanceof M2BrowningEntity m2) {
                    m2.tryShoot(player);
                }
                // === ДОБАВЛЕНО ===
                else if (vehicle instanceof AGS30Entity ags) {
                    ags.tryShoot(player);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}