package com.example.aas.network;

import com.example.aas.client.AASClipboard;
import com.example.aas.client.KitKeyUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public class PacketSendKitData {
    private final Map<String, CompoundTag> data;
    private final String keyType;   // "" = normal clipboard, otherwise generate a text key
    private final boolean keyIsAlt;

    public PacketSendKitData(Map<String, CompoundTag> data) { this(data, "", false); }
    public PacketSendKitData(Map<String, CompoundTag> data, String keyType, boolean keyIsAlt) {
        this.data = data; this.keyType = keyType; this.keyIsAlt = keyIsAlt;
    }

    public static void encode(PacketSendKitData msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.data.size());
        msg.data.forEach((name, tag) -> { buf.writeUtf(name); buf.writeNbt(tag); });
        buf.writeUtf(msg.keyType);
        buf.writeBoolean(msg.keyIsAlt);
    }
    public static PacketSendKitData decode(FriendlyByteBuf buf) {
        int size = buf.readInt();
        Map<String, CompoundTag> map = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) map.put(buf.readUtf(), buf.readNbt());
        return new PacketSendKitData(map, buf.readUtf(), buf.readBoolean());
    }

    public static void handle(PacketSendKitData msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (!msg.keyType.isEmpty()) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> KitKeyUtil.onKeyData(msg.keyType, msg.keyIsAlt, msg.data));
            } else if (msg.data.size() > 1) {
                AASClipboard.teamKitsData = msg.data;
            } else {
                msg.data.values().stream().findFirst().ifPresent(tag -> AASClipboard.kitData = tag);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}