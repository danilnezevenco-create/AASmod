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
    private final boolean isAlt; // вставить в стандартную или альтернативную версию
    private final CompoundTag tag;

    public PacketPasteKit(String team, String kitName, CompoundTag tag) { this(team, kitName, false, tag); }
    public PacketPasteKit(String team, String kitName, boolean isAlt, CompoundTag tag) { this.team = team; this.kitName = kitName; this.isAlt = isAlt; this.tag = tag; }

    public static void encode(PacketPasteKit msg, FriendlyByteBuf buf) { buf.writeUtf(msg.team); buf.writeUtf(msg.kitName); buf.writeBoolean(msg.isAlt); buf.writeNbt(msg.tag); }
    public static PacketPasteKit decode(FriendlyByteBuf buf) { return new PacketPasteKit(buf.readUtf(), buf.readUtf(), buf.readBoolean(), buf.readNbt()); }

    public static void handle(PacketPasteKit msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.isCreative()) return;
            AASWorldData worldData = AASWorldData.get(player.serverLevel());

            if (msg.isAlt) {
                // Вставляем только в альтернативную версию, не трогая стандартный кит
                AASWorldData.KitInfo baseKit = msg.team.equals("BLUE") ? worldData.blueKits.get(msg.kitName) : worldData.redKits.get(msg.kitName);
                if (baseKit == null) return;

                AASWorldData.KitInfo newAlt = AASWorldData.KitInfo.load(msg.tag);
                newAlt.name = msg.kitName; // Сохраняем имя целевого слота
                newAlt.hasAlt = false;
                newAlt.altKit = null; // у альтернативы не бывает своей альтернативы
                baseKit.altKit = newAlt;
                baseKit.hasAlt = true; // вставили данные — автоматически включаем альтернативную версию
            } else {
                AASWorldData.KitInfo newKit = AASWorldData.KitInfo.load(msg.tag);
                newKit.name = msg.kitName; // Сохраняем имя целевого слота

                if (msg.team.equals("BLUE")) worldData.blueKits.put(msg.kitName, newKit);
                else worldData.redKits.put(msg.kitName, newKit);
            }

            worldData.setDirty();
            PacketHandler.sendToAllClients(player.serverLevel(), worldData);
        });
        ctx.get().setPacketHandled(true);
    }
}
