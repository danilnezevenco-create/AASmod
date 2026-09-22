package com.example.aas.network;

import com.example.aas.client.ClientData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

// Пакет для синхронизации текущего кита игрока на его же клиент
// (getPersistentData() на сервере не синхронизируется на клиент автоматически)
public class PacketSyncMyKit {
    private final String kitName;

    public PacketSyncMyKit(String kitName) {
        this.kitName = kitName;
    }

    public static void encode(PacketSyncMyKit msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.kitName == null ? "" : msg.kitName);
    }

    public static PacketSyncMyKit decode(FriendlyByteBuf buf) {
        return new PacketSyncMyKit(buf.readUtf());
    }

    public static void handle(PacketSyncMyKit msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientData.myCurrentKit = msg.kitName);
        });
        ctx.get().setPacketHandled(true);
    }
}