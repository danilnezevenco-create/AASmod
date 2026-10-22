package com.example.aas.network;

import com.example.aas.client.uav.UavClientState;
import com.example.aas.uav.UavState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> own team only: active UAV zone + cooldown end. Never sent to the enemy team. */
public class PacketSyncUav {
    public final boolean active;
    public final double cx, cz;
    public final int radius;
    public final float altY, theta0, speed;
    public final long startTick, endTick, cooldownEnd;

    public PacketSyncUav(boolean active, double cx, double cz, int radius, float altY, float theta0, float speed,
                         long startTick, long endTick, long cooldownEnd) {
        this.active = active; this.cx = cx; this.cz = cz; this.radius = radius;
        this.altY = altY; this.theta0 = theta0; this.speed = speed;
        this.startTick = startTick; this.endTick = endTick; this.cooldownEnd = cooldownEnd;
    }

    public static PacketSyncUav of(UavState u, long cooldownEnd) {
        if (u == null) return new PacketSyncUav(false, 0, 0, 0, 0, 0, 0, 0, 0, cooldownEnd);
        return new PacketSyncUav(true, u.cx, u.cz, u.radius, (float) u.altY, (float) u.theta0, u.speed,
                u.startTick, u.endTick, cooldownEnd);
    }

    public static void encode(PacketSyncUav m, FriendlyByteBuf b) {
        b.writeBoolean(m.active);
        b.writeDouble(m.cx); b.writeDouble(m.cz); b.writeVarInt(m.radius);
        b.writeFloat(m.altY); b.writeFloat(m.theta0); b.writeFloat(m.speed);
        b.writeLong(m.startTick); b.writeLong(m.endTick); b.writeLong(m.cooldownEnd);
    }

    public static PacketSyncUav decode(FriendlyByteBuf b) {
        return new PacketSyncUav(b.readBoolean(), b.readDouble(), b.readDouble(), b.readVarInt(),
                b.readFloat(), b.readFloat(), b.readFloat(), b.readLong(), b.readLong(), b.readLong());
    }

    public static void handle(PacketSyncUav m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> UavClientState.apply(m)));
        ctx.get().setPacketHandled(true);
    }
}
