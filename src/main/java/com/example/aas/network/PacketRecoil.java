package com.example.aas.network;

import com.example.aas.client.RecoilHandler; // <--- Импорт хендлера
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import com.example.aas.client.ClientHooks;
import java.util.function.Supplier;

public class PacketRecoil {
    private final float pitch; // Сила вверх
    private final float yaw;   // Сила вбок

    public PacketRecoil(float pitch, float yaw) {
        this.pitch = pitch;
        this.yaw = yaw;
    }

    public static void encode(PacketRecoil msg, FriendlyByteBuf buf) {
        buf.writeFloat(msg.pitch);
        buf.writeFloat(msg.yaw);
    }

    public static PacketRecoil decode(FriendlyByteBuf buf) {
        return new PacketRecoil(buf.readFloat(), buf.readFloat());
    }

    public static void handle(PacketRecoil msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Выполняем только на клиенте
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                // Передаем управление в RecoilHandler
                // (yaw пока игнорируем для возврата, так как боковую отдачу возвращать обычно не принято)
                RecoilHandler.addRecoil(msg.pitch);

                // Если нужно, можно добавить боковую тряску без возврата:
                if (msg.yaw != 0 && net.minecraft.client.Minecraft.getInstance().player != null) {
                    net.minecraft.client.Minecraft.getInstance().player.turn(msg.yaw, 0);
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }
}