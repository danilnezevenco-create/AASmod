package com.example.aas.network;

import com.example.aas.client.ClientData;
import com.example.aas.config.AASConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class PacketSyncServerConfig {
    private final boolean autoBalance;
    private final String reviveItem;

    public PacketSyncServerConfig(boolean autoBalance, String reviveItem) {
        this.autoBalance = autoBalance;
        this.reviveItem = reviveItem;
    }

    public static void encode(PacketSyncServerConfig msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.autoBalance);
        buf.writeUtf(msg.reviveItem);
    }

    public static PacketSyncServerConfig decode(FriendlyByteBuf buf) {
        return new PacketSyncServerConfig(buf.readBoolean(), buf.readUtf());
    }

    public static void handle(PacketSyncServerConfig msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                ClientData.serverAutoBalance = msg.autoBalance;
                ClientData.serverReviveItem = msg.reviveItem;
            });
        });
        ctx.get().setPacketHandled(true);
    }
}