package com.example.aas.network;

import com.example.aas.client.ClientHooks;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketSyncPoint {
    public final boolean isInside;
    public final String name;
    public final String owner;
    public final float progress;
    public final boolean isLocked;
    public final String nextObjective;
    public final boolean isContested;
    // НОВОЕ ПОЛЕ
    public final String capturingTeam;

    public PacketSyncPoint(boolean isInside, String name, String owner, float progress, boolean locked, String nextObj, boolean contested, String capturingTeam) {
        this.isInside = isInside;
        this.name = name;
        this.owner = owner;
        this.progress = progress;
        this.isLocked = locked;
        this.nextObjective = nextObj;
        this.isContested = contested;
        this.capturingTeam = capturingTeam;
    }

    public static void encode(PacketSyncPoint msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.isInside);
        buf.writeUtf(msg.name);
        buf.writeUtf(msg.owner);
        buf.writeFloat(msg.progress);
        buf.writeBoolean(msg.isLocked);
        buf.writeUtf(msg.nextObjective);
        buf.writeBoolean(msg.isContested);
        buf.writeUtf(msg.capturingTeam); // Пишем
    }

    public static PacketSyncPoint decode(FriendlyByteBuf buf) {
        return new PacketSyncPoint(
                buf.readBoolean(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readFloat(),
                buf.readBoolean(),
                buf.readUtf(),
                buf.readBoolean(),
                buf.readUtf() // Читаем
        );
    }

    public static void handle(PacketSyncPoint msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.handleSyncPoint(msg));
        });
        ctx.get().setPacketHandled(true);
    }
}