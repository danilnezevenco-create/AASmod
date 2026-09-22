package com.example.aas.network;

import com.example.aas.world.AASWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.network.NetworkEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import java.util.function.Supplier;

public class PacketSavePoint {
    private final String originalName, newName, shape;
    private final int p1x, p1y, p1z, p2x, p2y, p2z;
    private final int bluePriority, redPriority, captureTime, penalty, deduct, lockMin;
    private final int gainNeut, gainCap; // Добавлено

    // Обновленный конструктор (17 аргументов)
    public PacketSavePoint(String origName, String newName, String shape, int p1x, int p1y, int p1z, int p2x, int p2y, int p2z, int bp, int rp, int time, int pen, int ded, int lock, int gainNeut, int gainCap) {
        this.originalName = origName; this.newName = newName; this.shape = shape;
        this.p1x = p1x; this.p1y = p1y; this.p1z = p1z;
        this.p2x = p2x; this.p2y = p2y; this.p2z = p2z;
        this.bluePriority = bp; this.redPriority = rp; this.captureTime = time;
        this.penalty = pen; this.deduct = ded; this.lockMin = lock;
        this.gainNeut = gainNeut; this.gainCap = gainCap;
    }

    public static void encode(PacketSavePoint msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.originalName); buf.writeUtf(msg.newName); buf.writeUtf(msg.shape);
        buf.writeInt(msg.p1x); buf.writeInt(msg.p1y); buf.writeInt(msg.p1z);
        buf.writeInt(msg.p2x); buf.writeInt(msg.p2y); buf.writeInt(msg.p2z);
        buf.writeInt(msg.bluePriority); buf.writeInt(msg.redPriority);
        buf.writeInt(msg.captureTime); buf.writeInt(msg.penalty);
        buf.writeInt(msg.deduct); buf.writeInt(msg.lockMin);
        buf.writeInt(msg.gainNeut); buf.writeInt(msg.gainCap); // Добавлено
    }

    public static PacketSavePoint decode(FriendlyByteBuf buf) {
        return new PacketSavePoint(
                buf.readUtf(), buf.readUtf(), buf.readUtf(),
                buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(),
                buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(),
                buf.readInt(), buf.readInt() // Читаем 16-й и 17-й аргументы
        );
    }

    public static void handle(PacketSavePoint msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.hasPermissions(2)) return;

            AASWorldData data = AASWorldData.get(player.serverLevel());

            for (AASWorldData.CapturePoint p : data.capturePoints) {
                if (p.name.equals(msg.originalName)) {
                    p.name = msg.newName;
                    p.shapeType = msg.shape;
                    p.bluePriority = msg.bluePriority;
                    p.redPriority = msg.redPriority;
                    p.captureTimeMinutes = msg.captureTime;
                    p.ticketPenalty = msg.penalty;
                    p.captureDeduction = msg.deduct;
                    p.lockDurationMinutes = msg.lockMin;
                    p.ticketGainNeutralize = msg.gainNeut; // Применяем новые данные
                    p.ticketGainCapture = msg.gainCap;

                    // Пересчет области AABB
                    if (msg.shape.equals("CYLINDER")) {
                        double radius = Math.sqrt(new BlockPos(msg.p1x, msg.p1y, msg.p1z).distSqr(new BlockPos(msg.p2x, msg.p1y, msg.p2z)));
                        p.area = new AABB(msg.p1x - radius, msg.p1y, msg.p1z - radius, msg.p1x + radius, msg.p2y + 1, msg.p1z + radius);
                    } else {
                        p.area = new AABB(
                                Math.min(msg.p1x, msg.p2x), Math.min(msg.p1y, msg.p2y), Math.min(msg.p1z, msg.p2z),
                                Math.max(msg.p1x, msg.p2x) + 1, Math.max(msg.p1y, msg.p2y) + 1, Math.max(msg.p1z, msg.p2z) + 1
                        );
                    }

                    data.setDirty();
                    PacketHandler.sendToAllClients(player.serverLevel(), data);
                    player.sendSystemMessage(Component.literal("Point updated!").withStyle(ChatFormatting.GREEN));
                    break;
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}