package com.example.aas.network;

import com.example.aas.world.AASWorldData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class PacketRequestKitMenu {
    public PacketRequestKitMenu() {}
    public static void encode(PacketRequestKitMenu msg, FriendlyByteBuf buf) {}
    public static PacketRequestKitMenu decode(FriendlyByteBuf buf) { return new PacketRequestKitMenu(); }

    public static void handle(PacketRequestKitMenu msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null || player.getTeam() == null) return;

            AASWorldData data = AASWorldData.get(player.serverLevel());
            String teamName = player.getTeam().getName().toUpperCase();
            String pName = player.getScoreboardName();

            AASWorldData.Squad mySquad = null;
            for (AASWorldData.Squad s : data.squads) {
                if (s.members.contains(pName)) { mySquad = s; break; }
            }

            List<PacketOpenPlayerKitMenu.KitDTO> dtoList = new ArrayList<>();

            for (String kitName : AASWorldData.KIT_NAMES) {
                AASWorldData.KitInfo kit = teamName.equals("BLUE") ? data.blueKits.get(kitName) : data.redKits.get(kitName);
                if (kit == null) continue;

                if (kit.maxPerTeam == 0 && !kitName.equals("Unassigned")) continue;

                int tCount = 0;
                int sCount = 0;

                for (ServerPlayer p : player.server.getPlayerList().getPlayers()) {
                    if (p == player) continue;
                    if (p.getTeam() != null && p.getTeam().getName().toUpperCase().equals(teamName)) {
                        String cKit = p.getPersistentData().getString("AAS_CurrentKit");
                        String pKit = p.getPersistentData().getString("AAS_PendingKit");

                        // Если хотя бы один из тегов совпадает с проверяемым китом - значит место занято
                        if (cKit.equals(kitName) || pKit.equals(kitName)) {
                            tCount++; // Считаем для всей команды
                            if (mySquad != null && mySquad.members.contains(p.getScoreboardName())) {
                                sCount++; // Считаем внутри отряда
                            }
                        }
                    }
                }

                boolean available = true;
                String reason = "";

                if (kit.maxPerTeam > 0 && tCount >= kit.maxPerTeam) { available = false; reason = "Team Full (" + tCount + "/" + kit.maxPerTeam + ")"; }
                else if (kit.maxPerSquad > 0 && sCount >= kit.maxPerSquad) { available = false; reason = "Squad Full (" + sCount + "/" + kit.maxPerSquad + ")"; }
                else if (kit.minSquadPlayers > 0 && (mySquad == null || mySquad.members.size() < kit.minSquadPlayers)) { available = false; reason = "Need " + kit.minSquadPlayers + " players in Squad"; }
                else if (kit.isLeaderOnly && (mySquad == null || !mySquad.leader.equals(pName))) { available = false; reason = "Squad Leader Only"; }

                // Создаем список предметов для предпросмотра
                List<ItemStack> kitPreviewItems = new ArrayList<>(kit.inventory);
                dtoList.add(new PacketOpenPlayerKitMenu.KitDTO(kitName, available, reason, kitPreviewItems));
            }
            PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new PacketOpenPlayerKitMenu(dtoList));
        });
        ctx.get().setPacketHandled(true);
    }
}