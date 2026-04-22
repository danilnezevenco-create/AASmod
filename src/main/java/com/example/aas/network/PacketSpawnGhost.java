package com.example.aas.network;

import com.example.aas.client.ClientHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketSpawnGhost {
    public final BlockPos pos;
    public final int blockId;

    public PacketSpawnGhost(BlockPos pos, int blockId) {
        this.pos = pos;
        this.blockId = blockId;
    }

    public static void encode(PacketSpawnGhost msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeInt(msg.blockId);
    }

    public static PacketSpawnGhost decode(FriendlyByteBuf buf) {
        return new PacketSpawnGhost(buf.readBlockPos(), buf.readInt());
    }

    public static void handle(PacketSpawnGhost msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // ВЕСЬ клиентский код убран в ClientHooks
            // Сервер этот код "не видит" и не пытается загрузить Minecraft.class
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.handleSpawnGhost(msg));
        });
        ctx.get().setPacketHandled(true);
    }
}