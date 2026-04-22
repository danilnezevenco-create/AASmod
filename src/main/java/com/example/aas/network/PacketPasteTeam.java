package com.example.aas.network;

import com.example.aas.world.AASWorldData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;
import java.util.HashMap;
import java.util.Map;

public class PacketPasteTeam {
    private final String team;
    private final Map<String, CompoundTag> kits;

    public PacketPasteTeam(String team, Map<String, CompoundTag> kits) { this.team = team; this.kits = kits; }
    public static void encode(PacketPasteTeam msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.team);
        buf.writeInt(msg.kits.size());
        msg.kits.forEach((name, tag) -> { buf.writeUtf(name); buf.writeNbt(tag); });
    }
    public static PacketPasteTeam decode(FriendlyByteBuf buf) {
        String t = buf.readUtf();
        int size = buf.readInt();
        Map<String, CompoundTag> map = new HashMap<>();
        for(int i=0; i<size; i++) map.put(buf.readUtf(), buf.readNbt());
        return new PacketPasteTeam(t, map);
    }

    public static void handle(PacketPasteTeam msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.isCreative()) return;
            AASWorldData worldData = AASWorldData.get(player.serverLevel());

            Map<String, AASWorldData.KitInfo> target = msg.team.equals("BLUE") ? worldData.blueKits : worldData.redKits;
            msg.kits.forEach((name, tag) -> {
                if (target.containsKey(name)) {
                    target.put(name, AASWorldData.KitInfo.load(tag));
                }
            });

            worldData.setDirty();
            PacketHandler.sendToAllClients(player.serverLevel(), worldData);
        });
        ctx.get().setPacketHandled(true);
    }
}