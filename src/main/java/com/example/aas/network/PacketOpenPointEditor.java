package com.example.aas.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketOpenPointEditor {
    public final String originalName, shape;
    public final int p1x, p1y, p1z, p2x, p2y, p2z;
    // ИСПРАВЛЕНО: точки с запятой заменены на запятые
    public final int bluePriority, redPriority, captureTime, penalty, deduct, lockMin, gainNeut, gainCap;

    // ИСПРАВЛЕНО: добавлены int gainNeut, int gainCap в конструктор
    public PacketOpenPointEditor(String name, String shape, int p1x, int p1y, int p1z, int p2x, int p2y, int p2z, int bluePrio, int redPrio, int time, int pen, int ded, int lock, int gainNeut, int gainCap) {
        this.originalName = name; this.shape = shape;
        this.p1x = p1x; this.p1y = p1y; this.p1z = p1z;
        this.p2x = p2x; this.p2y = p2y; this.p2z = p2z;
        this.bluePriority = bluePrio; this.redPriority = redPrio;
        this.captureTime = time; this.penalty = pen; this.deduct = ded; this.lockMin = lock;
        this.gainNeut = gainNeut; this.gainCap = gainCap; // ИСПРАВЛЕНО: присваиваем значения
    }

    public static void encode(PacketOpenPointEditor msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.originalName); buf.writeUtf(msg.shape);
        buf.writeInt(msg.p1x); buf.writeInt(msg.p1y); buf.writeInt(msg.p1z);
        buf.writeInt(msg.p2x); buf.writeInt(msg.p2y); buf.writeInt(msg.p2z);
        buf.writeInt(msg.bluePriority); buf.writeInt(msg.redPriority);
        buf.writeInt(msg.captureTime); buf.writeInt(msg.penalty);
        buf.writeInt(msg.deduct); buf.writeInt(msg.lockMin);
        buf.writeInt(msg.gainNeut); buf.writeInt(msg.gainCap);
    }

    public static PacketOpenPointEditor decode(FriendlyByteBuf buf) {
        return new PacketOpenPointEditor(
                buf.readUtf(), buf.readUtf(),
                buf.readInt(), buf.readInt(), buf.readInt(),
                buf.readInt(), buf.readInt(), buf.readInt(),
                buf.readInt(), buf.readInt(), buf.readInt(),
                buf.readInt(), buf.readInt(), buf.readInt(),
                buf.readInt(), buf.readInt()
        );
    }

    public static void handle(PacketOpenPointEditor msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.example.aas.client.ClientHooks.openPointEditor(msg)));
        ctx.get().setPacketHandled(true);
    }
}