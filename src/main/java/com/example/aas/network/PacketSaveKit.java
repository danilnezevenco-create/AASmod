// PATH: src\main\java\com\example\aas\network\PacketSaveKit.java
package com.example.aas.network;

import com.example.aas.menu.KitEditorMenu;
import com.example.aas.world.AASWorldData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class PacketSaveKit {
    private final String team;
    private final String kitName;
    private final boolean isAlt;
    private final boolean hasAlt;
    private final boolean isLeader;
    private final int maxTeam;
    private final int maxSquad;
    private final int minSquadPlayers;
    private final String displayName;           // <-- НОВОЕ
    private final boolean[] resupplyFlags;
    private final boolean[] nbtFlags;

    public PacketSaveKit(String team, String kitName, boolean isAlt, boolean hasAlt, boolean isLeader,
                         int maxTeam, int maxSquad, int minSquadPlayers,
                         String displayName,                              // <-- НОВОЕ
                         boolean[] resupplyFlags, boolean[] nbtFlags) {
        this.team = team;
        this.kitName = kitName;
        this.isAlt = isAlt;
        this.hasAlt = hasAlt;
        this.isLeader = isLeader;
        this.maxTeam = maxTeam;
        this.maxSquad = maxSquad;
        this.minSquadPlayers = minSquadPlayers;
        this.displayName = displayName != null ? displayName : "";        // <-- НОВОЕ
        this.resupplyFlags = resupplyFlags;
        this.nbtFlags = nbtFlags;
    }

    public static void encode(PacketSaveKit msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.team);
        buf.writeUtf(msg.kitName);
        buf.writeBoolean(msg.isAlt);
        buf.writeBoolean(msg.hasAlt);
        buf.writeBoolean(msg.isLeader);
        buf.writeInt(msg.maxTeam);
        buf.writeInt(msg.maxSquad);
        buf.writeInt(msg.minSquadPlayers);
        buf.writeUtf(msg.displayName);           // <-- НОВОЕ

        for(int i=0; i<49; i++) buf.writeBoolean(msg.resupplyFlags[i]);
        for(int i=0; i<49; i++) buf.writeBoolean(msg.nbtFlags[i]);
    }

    public static PacketSaveKit decode(FriendlyByteBuf buf) {
        String t = buf.readUtf();
        String k = buf.readUtf();
        boolean isAlt = buf.readBoolean();
        boolean hasAlt = buf.readBoolean();
        boolean l = buf.readBoolean();
        int mt = buf.readInt();
        int ms = buf.readInt();
        int minP = buf.readInt();
        String displayName = buf.readUtf();      // <-- НОВОЕ

        boolean[] f1 = new boolean[49];
        for(int i=0; i<49; i++) f1[i] = buf.readBoolean();
        boolean[] f2 = new boolean[49];
        for(int i=0; i<49; i++) f2[i] = buf.readBoolean();

        return new PacketSaveKit(t, k, isAlt, hasAlt, l, mt, ms, minP, displayName, f1, f2);
    }

    public static void handle(PacketSaveKit msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.isCreative() && player.containerMenu instanceof KitEditorMenu menu) {
                AASWorldData data = AASWorldData.get(player.serverLevel());
                AASWorldData.KitInfo baseKit = msg.team.equals("BLUE") ? data.blueKits.get(msg.kitName) : data.redKits.get(msg.kitName);

                if (baseKit != null) {
                    AASWorldData.KitInfo target = msg.isAlt ? baseKit.getOrCreateAlt() : baseKit;

                    if (!msg.isAlt) {
                        baseKit.isLeaderOnly = msg.isLeader;
                        baseKit.maxPerTeam = msg.maxTeam;
                        baseKit.maxPerSquad = msg.maxSquad;
                        baseKit.minSquadPlayers = msg.minSquadPlayers;
                        baseKit.hasAlt = msg.hasAlt;
                    }

                    target.displayName = msg.displayName;   // <-- НОВОЕ: своё имя для той версии, что редактировали
                    target.resupplyFlags = msg.resupplyFlags;
                    target.saveNbtFlags = msg.nbtFlags;

                    for(int i=0; i<49; i++) {
                        target.inventory.set(i, menu.kitInventory.getItem(i).copy());
                    }

                    data.setDirty();
                    PacketHandler.sendToAllClients(player.serverLevel(), data);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
