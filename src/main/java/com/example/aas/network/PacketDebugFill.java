package com.example.aas.network;

import com.example.aas.world.AASWorldData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketDebugFill {
    // Пакет пустой, нам важен сам факт нажатия
    public PacketDebugFill() {}

    public static void encode(PacketDebugFill msg, FriendlyByteBuf buf) {}

    public static PacketDebugFill decode(FriendlyByteBuf buf) {
        return new PacketDebugFill();
    }

    public static void handle(PacketDebugFill msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                ServerLevel level = player.serverLevel();
                AASWorldData data = AASWorldData.get(level);

                // Ищем, на какой точке стоит игрок
                for (AASWorldData.CapturePoint point : data.capturePoints) {
                    if (point.area.contains(player.position())) {
                        // Добавляем 25% (одна полоска)
                        point.progress += 0.25f;
                        if (point.progress > 1.0f) point.progress = 1.0f;

                        // Если точка была нейтральной, можно временно присвоить её тому, кто нажал (для теста)
                        // Но лучше оставить нейтральной, просто заполнить полоску

                        data.setDirty();
                        break; // Нашли точку, выходим
                    }
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}