package com.example.aas.network;

import com.example.aas.uav.UavViewManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> server: a marker / ping thrown while looking through the UAV.
 * The server raycasts from the UAV position along the camera direction (yaw/pitch sent by the client),
 * then uses the same squad-marker logic as PacketPlaceRadialMarker. Ignored when the player is not viewing.
 * type: PacketPlaceRadialMarker.TYPE_* (TYPE_EYE = quick ping, TYPE_MOVE = quick move marker).
 */
public class PacketUavMarker {
    private final int type;
    private final float yaw, pitch;

    public PacketUavMarker(int type, float yaw, float pitch) {
        this.type = type; this.yaw = yaw; this.pitch = pitch;
    }

    public static void encode(PacketUavMarker m, FriendlyByteBuf b) { b.writeVarInt(m.type); b.writeFloat(m.yaw); b.writeFloat(m.pitch); }

    public static PacketUavMarker decode(FriendlyByteBuf b) { return new PacketUavMarker(b.readVarInt(), b.readFloat(), b.readFloat()); }

    public static void handle(PacketUavMarker m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer p = ctx.get().getSender();
            if (p == null) return;
            Vec3 eye = UavViewManager.viewPos(p);          // null unless the player is looking through the UAV
            if (eye == null) return;
            if (!Float.isFinite(m.yaw) || !Float.isFinite(m.pitch)) return;
            Vec3 look = Vec3.directionFromRotation(Mth.clamp(m.pitch, -90f, 90f), m.yaw);
            PacketPlaceRadialMarker.place(p, m.type, eye, look, PacketPlaceRadialMarker.UAV_REACH);
        });
        ctx.get().setPacketHandled(true);
    }
}