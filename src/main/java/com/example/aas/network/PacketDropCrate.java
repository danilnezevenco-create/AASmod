package com.example.aas.network;
import com.example.aas.entity.SupplyCrateEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;
public class PacketDropCrate {
    public PacketDropCrate() {}

    public static void encode(PacketDropCrate msg, FriendlyByteBuf buf) {}
    public static PacketDropCrate decode(FriendlyByteBuf buf) { return new PacketDropCrate(); }

    public static void handle(PacketDropCrate msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                Entity vehicle = player.getVehicle();

                // Проверка 1: Игрок в машине и это грузовик снабжения
                if (vehicle != null && vehicle.getPersistentData().getBoolean("AAS_IsSupplyTruck")) {

                    // ПРОЛОЖЕННАЯ ПРОВЕРКА: Только первый пассажир (водитель)
                    if (vehicle.getFirstPassenger() != player) {
                        player.displayClientMessage(Component.translatable("aas.msg.only_driver_drop")
                                .withStyle(ChatFormatting.RED), true);
                        return;
                    }

                    int ammo = vehicle.getPersistentData().getInt("AAS_SupplyAmmo");
                    if (ammo > 0) {
                        // Сбрасываем счетчик
                        vehicle.getPersistentData().putInt("AAS_SupplyAmmo", ammo - 1);

                        String team = vehicle.getPersistentData().getString("AAS_VehicleTeam");
                        if (team.isEmpty()) team = "NEUTRAL";

                        // Позиция сзади
                        double yawRad = Math.toRadians(vehicle.getYRot());
                        double x = vehicle.getX() + (Math.sin(yawRad) * 2.5); // Чуть дальше, чем раньше
                        double z = vehicle.getZ() - (Math.cos(yawRad) * 2.5);
                        double y = vehicle.getY() + 1.5;

                        // Передаем UUID игрока
                        SupplyCrateEntity crate = new SupplyCrateEntity(player.level(), x, y, z, team, player.getUUID());
                        player.level().addFreshEntity(crate);

                        player.displayClientMessage(Component.translatable("aas.msg.crate_dropped").withStyle(ChatFormatting.YELLOW), true);
                    } else {
                        player.displayClientMessage(Component.translatable("aas.msg.no_supplies").withStyle(ChatFormatting.RED), true);
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}