package com.example.aas.network;

import com.example.aas.world.AASWorldData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public class PacketRequestKitData {
    private final String team;      // BLUE / RED (BOTH only for keyType "ALL")
    private final String kitName;   // "ALL" = whole team
    private final boolean isAlt;    // copy the alt version of one kit
    private final String keyType;   // "" = normal copy to in-game clipboard; "KIT"/"TEAM"/"ALL" = generate text key

    public PacketRequestKitData(String team, String kitName) { this(team, kitName, false, ""); }
    public PacketRequestKitData(String team, String kitName, boolean isAlt) { this(team, kitName, isAlt, ""); }
    public PacketRequestKitData(String team, String kitName, boolean isAlt, String keyType) {
        this.team = team; this.kitName = kitName; this.isAlt = isAlt; this.keyType = keyType;
    }

    public static void encode(PacketRequestKitData m, FriendlyByteBuf b) {
        b.writeUtf(m.team); b.writeUtf(m.kitName); b.writeBoolean(m.isAlt); b.writeUtf(m.keyType);
    }
    public static PacketRequestKitData decode(FriendlyByteBuf b) {
        return new PacketRequestKitData(b.readUtf(), b.readUtf(), b.readBoolean(), b.readUtf());
    }

    private static Map<String, AASWorldData.KitInfo> kitsOf(AASWorldData d, String team) {
        return team.equals("BLUE") ? d.blueKits : d.redKits;
    }

    public static void handle(PacketRequestKitData msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || !player.isCreative()) return;
            AASWorldData data = AASWorldData.get(player.serverLevel());

            if (!msg.keyType.isEmpty()) {
                // Key mode: entries are named "TEAM|KitName"
                Map<String, CompoundTag> out = new LinkedHashMap<>();
                if (msg.kitName.equals("ALL")) {
                    String[] teams = msg.team.equals("BOTH") ? new String[]{"BLUE", "RED"} : new String[]{msg.team};
                    for (String t : teams) kitsOf(data, t).forEach((n, k) -> out.put(t + "|" + n, k.save()));
                } else {
                    AASWorldData.KitInfo base = kitsOf(data, msg.team).get(msg.kitName);
                    if (base != null) {
                        if (msg.isAlt) { if (base.altKit != null) out.put(msg.team + "|" + msg.kitName, base.altKit.save()); }
                        else out.put(msg.team + "|" + msg.kitName, base.save());
                    }
                }
                PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                        new PacketSendKitData(out, msg.keyType, msg.isAlt));
                return;
            }

            // Old behaviour (in-game clipboard)
            Map<String, CompoundTag> toSend = new HashMap<>();
            if (msg.kitName.equals("ALL")) {
                kitsOf(data, msg.team).forEach((name, kit) -> toSend.put(name, kit.save()));
            } else {
                AASWorldData.KitInfo baseKit = kitsOf(data, msg.team).get(msg.kitName);
                if (baseKit != null) {
                    if (msg.isAlt) { if (baseKit.altKit != null) toSend.put(msg.kitName, baseKit.altKit.save()); }
                    else toSend.put(msg.kitName, baseKit.save());
                }
            }
            PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new PacketSendKitData(toSend));
        });
        ctx.get().setPacketHandled(true);
    }
}