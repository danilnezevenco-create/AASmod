package com.example.aas.network;

import com.example.aas.world.AASWorldData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import java.util.function.Supplier;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;

public class PacketRequestKitData {
    private final String team;
    private final String kitName; // Если "ALL", копируем всю команду

    public PacketRequestKitData(String team, String kitName) { this.team = team; this.kitName = kitName; }
    public static void encode(PacketRequestKitData msg, FriendlyByteBuf buf) { buf.writeUtf(msg.team); buf.writeUtf(msg.kitName); }
    public static PacketRequestKitData decode(FriendlyByteBuf buf) { return new PacketRequestKitData(buf.readUtf(), buf.readUtf()); }

    public static void handle(PacketRequestKitData msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.isCreative()) return;
            AASWorldData data = AASWorldData.get(player.serverLevel());

            Map<String, CompoundTag> toSend = new HashMap<>();
            if (msg.kitName.equals("ALL")) {
                Map<String, AASWorldData.KitInfo> source = msg.team.equals("BLUE") ? data.blueKits : data.redKits;
                source.forEach((name, kit) -> toSend.put(name, kit.save()));
            } else {
                AASWorldData.KitInfo kit = msg.team.equals("BLUE") ? data.blueKits.get(msg.kitName) : data.redKits.get(msg.kitName);
                if (kit != null) toSend.put(msg.kitName, kit.save());
            }
            PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new PacketSendKitData(toSend));
        });
        ctx.get().setPacketHandled(true);
    }
}