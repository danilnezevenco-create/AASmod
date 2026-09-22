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

    // PATH: src/main/java/com/example/aas/network/PacketRequestKitMenu.java

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

            // Проверяем, является ли игрок лидером своего отряда
            boolean amILeader = (mySquad != null && mySquad.leader.equals(pName));

            List<PacketOpenPlayerKitMenu.KitDTO> dtoList = new ArrayList<>();

            for (String kitName : AASWorldData.KIT_NAMES) {
                AASWorldData.KitInfo kit = teamName.equals("BLUE") ? data.blueKits.get(kitName) : data.redKits.get(kitName);
                if (kit == null) continue;

                // --- НОВАЯ ЛОГИКА ФИЛЬТРАЦИИ ---
                // Если кит только для лидеров, а игрок НЕ лидер — полностью скрываем его (пропускаем итерацию)
                if (kit.isLeaderOnly && !amILeader && !player.isCreative()) {
                    continue;
                }
                // -------------------------------

                if (kit.maxPerTeam == 0 && !kitName.equals("Unassigned")) continue;

                int tCount = 0;
                int sCount = 0;

                for (ServerPlayer p : player.server.getPlayerList().getPlayers()) {
                    if (p == player) continue;
                    if (p.getTeam() != null && p.getTeam().getName().toUpperCase().equals(teamName)) {
                        String cKit = p.getPersistentData().getString("AAS_CurrentKit");
                        String pKit = p.getPersistentData().getString("AAS_PendingKit");
                        if (cKit.equals(kitName) || pKit.equals(kitName)) {
                            tCount++;
                            if (mySquad != null && mySquad.members.contains(p.getScoreboardName())) {
                                sCount++;
                            }
                        }
                    }
                }

                boolean available = true;
                String reason = "";

                // Проверки лимитов (тепеть тут не нужно проверять на лидера, так как мы скрыли кит выше)
                if (kit.maxPerTeam > 0 && tCount >= kit.maxPerTeam) {
                    available = false;
                    reason = "Team Full (" + tCount + "/" + kit.maxPerTeam + ")";
                }
                else if (kit.maxPerSquad > 0 && sCount >= kit.maxPerSquad) {
                    available = false;
                    reason = "Squad Full (" + sCount + "/" + kit.maxPerSquad + ")";
                }
                else if (kit.minSquadPlayers > 0 && (mySquad == null || mySquad.members.size() < kit.minSquadPlayers)) {
                    available = false;
                    reason = "Need " + kit.minSquadPlayers + " players in Squad";
                }

                List<ItemStack> kitPreviewItems = new ArrayList<>(kit.inventory);

                // === АЛЬТЕРНАТИВНАЯ ВЕРСИЯ КИТА (если включена админом) ===
                // Ограничения (Team/Squad/Leader) общие для класса — доступность у альт. версии та же,
                // что и у стандартной, так как это просто другой набор снаряжения.
                boolean hasAlt = kit.hasAlt && kit.altKit != null;
                List<ItemStack> altPreviewItems = hasAlt ? new ArrayList<>(kit.altKit.inventory) : new ArrayList<>();

                // === НОВОЕ: подставляем кастомные названия, если заданы ===
                String stdDisplay = (kit.displayName != null && !kit.displayName.isEmpty()) ? kit.displayName : kitName;
                String altDisplay = hasAlt
                        ? ((kit.altKit.displayName != null && !kit.altKit.displayName.isEmpty()) ? kit.altKit.displayName : kitName)
                        : kitName;

                dtoList.add(new PacketOpenPlayerKitMenu.KitDTO(kitName, available, reason, kitPreviewItems, hasAlt, altPreviewItems,
                        kit.isLeaderOnly, stdDisplay, altDisplay));
            }
            PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new PacketOpenPlayerKitMenu(dtoList));
        });
        ctx.get().setPacketHandled(true);
    }
}