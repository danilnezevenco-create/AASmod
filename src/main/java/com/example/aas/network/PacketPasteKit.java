package com.example.aas.network;

import com.example.aas.world.AASWorldData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class PacketPasteKit {
    private final String team;
    private final String kitName;
    private final boolean isAlt;      // paste into alt version instead of the standard one
    private final CompoundTag tag;
    private final boolean broadcast;  // false = skip client sync (used for batch paste from a key, last packet syncs)

    public PacketPasteKit(String team, String kitName, CompoundTag tag) { this(team, kitName, false, tag, true); }
    public PacketPasteKit(String team, String kitName, boolean isAlt, CompoundTag tag) { this(team, kitName, isAlt, tag, true); }
    public PacketPasteKit(String team, String kitName, boolean isAlt, CompoundTag tag, boolean broadcast) {
        this.team = team; this.kitName = kitName; this.isAlt = isAlt; this.tag = tag; this.broadcast = broadcast;
    }

    public static void encode(PacketPasteKit m, FriendlyByteBuf b) {
        b.writeUtf(m.team); b.writeUtf(m.kitName); b.writeBoolean(m.isAlt); b.writeNbt(m.tag); b.writeBoolean(m.broadcast);
    }
    public static PacketPasteKit decode(FriendlyByteBuf b) {
        return new PacketPasteKit(b.readUtf(), b.readUtf(), b.readBoolean(), b.readNbt(), b.readBoolean());
    }

    public static void handle(PacketPasteKit msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.isCreative() || msg.tag == null) return;
            AASWorldData worldData = AASWorldData.get(player.serverLevel());

            if (msg.isAlt) {
                AASWorldData.KitInfo baseKit = msg.team.equals("BLUE") ? worldData.blueKits.get(msg.kitName) : worldData.redKits.get(msg.kitName);
                if (baseKit == null) return;
                AASWorldData.KitInfo newAlt = AASWorldData.KitInfo.load(msg.tag);
                newAlt.name = msg.kitName;
                newAlt.hasAlt = false;
                newAlt.altKit = null;
                baseKit.altKit = newAlt;
                baseKit.hasAlt = true;
            } else {
                AASWorldData.KitInfo newKit = AASWorldData.KitInfo.load(msg.tag);
                newKit.name = msg.kitName;
                if (msg.team.equals("BLUE")) worldData.blueKits.put(msg.kitName, newKit);
                else worldData.redKits.put(msg.kitName, newKit);
            }

            worldData.setDirty();
            if (msg.broadcast) PacketHandler.sendToAllClients(player.serverLevel(), worldData);
        });
        ctx.get().setPacketHandled(true);
    }
}