// PATH: src\main\java\com\example\aas\network\PacketOpenKitEditor.java
package com.example.aas.network;

import com.example.aas.menu.KitEditorMenu;
import com.example.aas.world.AASWorldData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;
import java.util.function.Supplier;

public class PacketOpenKitEditor {
    private final String team;
    private final String kitName;
    private final boolean isAlt; // открываем редактор альтернативной версии кита

    public PacketOpenKitEditor(String team, String kitName) { this(team, kitName, false); }
    public PacketOpenKitEditor(String team, String kitName, boolean isAlt) { this.team = team; this.kitName = kitName; this.isAlt = isAlt; }

    public static void encode(PacketOpenKitEditor msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.team);
        buf.writeUtf(msg.kitName);
        buf.writeBoolean(msg.isAlt);
    }
    public static PacketOpenKitEditor decode(FriendlyByteBuf buf) {
        return new PacketOpenKitEditor(buf.readUtf(), buf.readUtf(), buf.readBoolean());
    }

    public static void handle(PacketOpenKitEditor msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.isCreative()) {
                AASWorldData data = AASWorldData.get(player.serverLevel());
                AASWorldData.KitInfo baseKit = msg.team.equals("BLUE") ? data.blueKits.get(msg.kitName) : data.redKits.get(msg.kitName);

                if (baseKit != null) {
                    AASWorldData.KitInfo kit = msg.isAlt ? baseKit.getOrCreateAlt() : baseKit;

                    NetworkHooks.openScreen(player, new net.minecraft.world.MenuProvider() {
                        @Override public Component getDisplayName() { return Component.literal((msg.isAlt ? "Edit ALT Kit: " : "Edit Kit: ") + baseKit.name); }
                        @Override public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int id, net.minecraft.world.entity.player.Inventory inv, net.minecraft.world.entity.player.Player p) {
                            SimpleContainer container = new SimpleContainer(49);
                            for(int i=0; i<49; i++) container.setItem(i, kit.inventory.get(i).copy());

                            return new KitEditorMenu(id, inv, container, msg.team, kit.name,
                                    msg.isAlt, baseKit.hasAlt,
                                    kit.isLeaderOnly, kit.maxPerTeam, kit.maxPerSquad,
                                    kit.minSquadPlayers,
                                    kit.displayName,                       // <-- НОВОЕ
                                    kit.resupplyFlags, kit.saveNbtFlags);
                        }
                    }, buf -> {
                        buf.writeUtf(msg.team);
                        buf.writeUtf(msg.kitName);
                        buf.writeBoolean(msg.isAlt);
                        buf.writeBoolean(baseKit.hasAlt);
                        buf.writeBoolean(kit.isLeaderOnly);
                        buf.writeInt(kit.maxPerTeam);
                        buf.writeInt(kit.maxPerSquad);
                        buf.writeInt(kit.minSquadPlayers);
                        buf.writeUtf(kit.displayName != null ? kit.displayName : ""); // <-- НОВОЕ, порядок = как в KitEditorMenu(FriendlyByteBuf)

                        for(boolean f : kit.resupplyFlags) buf.writeBoolean(f);
                        for(boolean f : kit.saveNbtFlags) buf.writeBoolean(f);
                    });
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
