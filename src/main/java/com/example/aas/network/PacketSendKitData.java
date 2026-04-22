package com.example.aas.network;

import com.example.aas.client.AASClipboard;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;
import java.util.HashMap;
import java.util.Map;

public class PacketSendKitData {
    private final Map<String, CompoundTag> data;
    public PacketSendKitData(Map<String, CompoundTag> data) { this.data = data; }

    public static void encode(PacketSendKitData msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.data.size());
        msg.data.forEach((name, tag) -> { buf.writeUtf(name); buf.writeNbt(tag); });
    }
    public static PacketSendKitData decode(FriendlyByteBuf buf) {
        int size = buf.readInt();
        Map<String, CompoundTag> map = new HashMap<>();
        for(int i=0; i<size; i++) map.put(buf.readUtf(), buf.readNbt());
        return new PacketSendKitData(map);
    }
    public static void handle(PacketSendKitData msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (msg.data.size() > 1) {
                AASClipboard.teamKitsData = msg.data;
            } else {
                msg.data.values().stream().findFirst().ifPresent(tag -> AASClipboard.kitData = tag);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}