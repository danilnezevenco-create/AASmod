package com.example.aas.network;

import com.example.aas.entity.AGS30Entity;
import com.example.aas.entity.M2BrowningEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketToggleAim {
    public PacketToggleAim() {}

    public static void encode(PacketToggleAim msg, FriendlyByteBuf buf) {}

    public static PacketToggleAim decode(FriendlyByteBuf buf) {
        return new PacketToggleAim();
    }

    public static void handle(PacketToggleAim msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                Entity vehicle = player.getVehicle();

                // M2 Browning
                if (vehicle instanceof M2BrowningEntity m2) {
                    m2.setAiming(!m2.isAiming());
                }

                // === ИЗМЕНЕНИЕ 8: Обработка AGS-30 ===
                else if (vehicle instanceof AGS30Entity ags) {
                    ags.setAiming(!ags.isAiming());
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}