// PATH: src\main\java\com\example\aas\network\PacketRequestAmmo.java
package com.example.aas.network;

import com.example.aas.block.HubBlockEntity;
import com.example.aas.item.AGSAmmoItem;
import com.example.aas.item.M2AmmoItem;
import com.example.aas.item.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;
import com.example.aas.world.AASWorldData;

import java.util.function.Supplier;

public class PacketRequestAmmo {
    private final BlockPos pos;
    private final int type;

    public PacketRequestAmmo(BlockPos pos, int type) {
        this.pos = pos;
        this.type = type;
    }

    public static void encode(PacketRequestAmmo msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeInt(msg.type);
    }

    public static PacketRequestAmmo decode(FriendlyByteBuf buf) {
        return new PacketRequestAmmo(buf.readBlockPos(), buf.readInt());
    }

    public static void handle(PacketRequestAmmo msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (player.distanceToSqr(msg.pos.getX(), msg.pos.getY(), msg.pos.getZ()) > 64.0) return;

            BlockEntity be = player.level().getBlockEntity(msg.pos);
            if (be instanceof HubBlockEntity hub) {

                int cost = 0;
                int cooldownTime = 0;
                boolean isOnCooldown = false;

                switch (msg.type) {
                    case 0: // AMMO (KIT RESUPPLY Р В Р’ВР В РІР‚С”Р В Р’В Р В Р Р‹Р В РЎС™Р В РІР‚СћР В РЎСљР В РЎвЂ™ Р В РЎв„ўР В Р’ВР В РЎС›Р В РЎвЂ™)
                        String pendingKit = player.getPersistentData().getString("AAS_PendingKit");
                        String currentKitName = player.getPersistentData().getString("AAS_CurrentKit");
                        boolean hasPending = !pendingKit.isEmpty();
                        String targetKit = hasPending ? pendingKit : currentKitName;

                        if (targetKit.isEmpty() || targetKit.equals("Unassigned")) {
                            player.sendSystemMessage(Component.literal("No Kit equipped!").withStyle(ChatFormatting.RED));
                            return;
                        }

                        // === Р В Р Р‹Р В РІР‚СћР В Р’В Р В РІР‚в„ўР В РІР‚СћР В Р’В Р В РЎСљР В РЎвЂ™Р В Р вЂЎ Р В РЎСџР В Р’В Р В РЎвЂєР В РІР‚в„ўР В РІР‚СћР В Р’В Р В РЎв„ўР В РЎвЂ™ Р В РЎв„ўР В РІР‚Сњ ===
                        long lastFobUse = player.getPersistentData().getLong("AAS_LastFobResupply");
                        long currentTime = player.level().getGameTime();
                        if (!player.isCreative() && currentTime < lastFobUse + 1200) { // 1200 Р РЋРІР‚С™Р В РЎвЂР В РЎвЂќР В РЎвЂўР В Р вЂ  = 60 Р РЋР С“Р В Р’ВµР В РЎвЂќ
                            long secondsLeft = (lastFobUse + 1200 - currentTime) / 20;
                            player.sendSystemMessage(Component.literal("Resupply on cooldown: " + secondsLeft + "s").withStyle(ChatFormatting.RED));
                            return;
                        }

                        cost = com.example.aas.config.AASConfig.HUB_RESUPPLY_COST.get();

                        // === Р С›Р РЋР С›Р вЂР С›Р вЂў Р СџР В Р С’Р вЂ™Р ВР вЂєР С›: Р С”Р С‘РЎвЂљ "Drone Operator" Р С—Р С•Р С—Р С•Р В»Р Р…РЎРЏР ВµРЎвЂљРЎРѓРЎРЏ РЎРѓ Р ТђР С’Р вЂР С’ Р Р† 3 РЎР‚Р В°Р В·Р В° Р Т‘Р С•РЎР‚Р С•Р В¶Р Вµ ===
                        if ("Drone Operator".equalsIgnoreCase(targetKit)) {
                            double multiplier = com.example.aas.config.AASConfig.DRONE_OPERATOR_HUB_COST_MULTIPLIER.get();
                            cost = (int) Math.ceil(cost * multiplier);
                        }
                        // === Rifleman kit resupplies from HUB at 2x cost ===
                        if ("Rifleman".equalsIgnoreCase(targetKit)) {
                            double multiplier = com.example.aas.config.AASConfig.RIFLEMAN_HUB_COST_MULTIPLIER.get();
                            cost = (int) Math.ceil(cost * multiplier);
                        }
                        if ("Sapper".equalsIgnoreCase(targetKit)) {
                            double multiplier = com.example.aas.config.AASConfig.SAPPER_HUB_COST_MULTIPLIER.get();
                            cost = (int) Math.ceil(cost * multiplier);
                        }

                        if (!player.isCreative() && hub.getMaterials() < cost) {
                            player.sendSystemMessage(Component.literal("Not enough Materials! Need: " + cost).withStyle(ChatFormatting.RED));
                            return;
                        }

                        AASWorldData data = AASWorldData.get(player.serverLevel());

                        if (hasPending) {
                            // Р В Р’ВР В РЎвЂ“Р РЋР вЂљР В РЎвЂўР В РЎвЂќ Р В Р вЂ Р РЋРІР‚в„–Р В Р’В±Р РЋР вЂљР В Р’В°Р В Р’В» Р В Р вЂ¦Р В РЎвЂўР В Р вЂ Р РЋРІР‚в„–Р В РІвЂћвЂ“ Р В РЎвЂќР В РЎвЂР РЋРІР‚С™ -> Р В РІР‚в„ўР РЋРІР‚в„–Р В РўвЂР В Р’В°Р В Р’ВµР В РЎВ Р В РЎвЂ”Р В РЎвЂўР В Р’В»Р В Р вЂ¦Р В РЎвЂўР РЋР С“Р РЋРІР‚С™Р РЋР Р‰Р РЋР вЂ№ Р В Р вЂ¦Р В РЎвЂўР В Р вЂ Р В РЎвЂўР В Р’Вµ Р РЋР С“Р В Р вЂ¦Р В Р’В°Р РЋР вЂљР РЋР РЏР В Р’В¶Р В Р’ВµР В Р вЂ¦Р В РЎвЂР В Р’Вµ
                            ResupplyHandler.tryApplyPendingKit(player, data);

                            if (!player.isCreative()) {
                                hub.consumeMaterials(cost);
                                player.getPersistentData().putLong("AAS_LastFobResupply", currentTime); // Р В Р Р‹Р В РЎС›Р В РЎвЂ™Р В РІР‚в„ўР В Р’ВР В РЎС™ Р В РЎв„ўР В РІР‚Сњ
                            }
                            player.level().sendBlockUpdated(msg.pos, hub.getBlockState(), hub.getBlockState(), 3);
                            player.sendSystemMessage(Component.literal("New Kit Equipped! (-" + cost + " Mats)").withStyle(ChatFormatting.GREEN));
                        } else {
                            // Р В РЎвЂєР В Р’В±Р РЋРІР‚в„–Р РЋРІР‚РЋР В Р вЂ¦Р В РЎвЂўР В Р’Вµ Р В РЎвЂ”Р В РЎвЂўР В РЎвЂ”Р В РЎвЂўР В Р’В»Р В Р вЂ¦Р В Р’ВµР В Р вЂ¦Р В РЎвЂР В Р’Вµ
                            String t = player.getTeam() != null ? player.getTeam().getName().toUpperCase() : "NEUTRAL";
                            boolean isAltVariant = player.getPersistentData().getBoolean("AAS_CurrentKitAlt");
                            AASWorldData.KitInfo kit = data.getKitVariant(t, currentKitName, isAltVariant);

                            if (kit != null) {
                                if (ResupplyHandler.resupplyPlayer(player, kit, false)) {
                                    if (!player.isCreative()) {
                                        hub.consumeMaterials(cost);
                                        player.getPersistentData().putLong("AAS_LastFobResupply", currentTime); // Р В Р Р‹Р В РЎС›Р В РЎвЂ™Р В РІР‚в„ўР В Р’ВР В РЎС™ Р В РЎв„ўР В РІР‚Сњ
                                    }
                                    player.level().sendBlockUpdated(msg.pos, hub.getBlockState(), hub.getBlockState(), 3);
                                    player.sendSystemMessage(Component.literal("Kit Resupplied! (-" + cost + " Mats)").withStyle(ChatFormatting.GREEN));
                                } else {
                                    player.sendSystemMessage(Component.literal("Ammo already full!").withStyle(ChatFormatting.YELLOW));
                                }
                            }
                        }
                        return; // Р В РІР‚в„ўР В РЎвЂўР В Р’В·Р В Р вЂ Р РЋР вЂљР В Р’В°Р РЋРІР‚С™ Р В РўвЂР В Р’В»Р РЋР РЏ Р В РЎвЂќР В РЎвЂР РЋРІР‚С™Р В Р’В° (Р РЋРІР‚РЋР РЋРІР‚С™Р В РЎвЂўР В Р’В±Р РЋРІР‚в„– Р В Р вЂ¦Р В Р’Вµ Р В РЎвЂ”Р В РЎвЂўР РЋРІвЂљВ¬Р В Р’ВµР В Р’В» Р В Р вЂ¦Р В РЎвЂР В Р’В¶Р В Р’Вµ)
                    case 1: // AGS-30 (Р В РІР‚СњР В РЎвЂўР В Р’В±Р В Р’В°Р В Р вЂ Р В Р’В»Р РЋР РЏР В Р’ВµР В РЎВ Р РЋР РЉР РЋРІР‚С™Р РЋРЎвЂњ Р В Р’В»Р В РЎвЂўР В РЎвЂ“Р В РЎвЂР В РЎвЂќР РЋРЎвЂњ)
                        cost = 15;            // Р В Р Р‹Р РЋРІР‚С™Р В РЎвЂўР В РЎвЂР В РЎВР В РЎвЂўР РЋР С“Р РЋРІР‚С™Р РЋР Р‰ Р В РЎвЂќР В Р’В°Р В РЎвЂќ Р РЋРЎвЂњ M2
                        cooldownTime = 1200;  // Р В РЎв„ўР В РІР‚Сњ 60 Р РЋР С“Р В Р’ВµР В РЎвЂќР РЋРЎвЂњР В Р вЂ¦Р В РўвЂ (1200 Р РЋРІР‚С™Р В РЎвЂР В РЎвЂќР В РЎвЂўР В Р вЂ )
                        if (hub.cooldownAGS > 0) isOnCooldown = true; // Р В РЎСџР РЋР вЂљР В РЎвЂўР В Р вЂ Р В Р’ВµР РЋР вЂљР В РЎвЂќР В Р’В° Р В РЎв„ўР В РІР‚Сњ
                        break;

                    case 2: // M2
                        cost = 15;
                        cooldownTime = 1200;
                        if (hub.cooldownM2 > 0) isOnCooldown = true;
                        break;
                    case 3: // Mortar
                        cost = 20;
                        cooldownTime = 1200;
                        if (hub.cooldownMortar > 0) isOnCooldown = true;
                        break;
                    case 4: // TOW
                        cost = 50;
                        cooldownTime = 2400;
                        if (hub.cooldownTOW > 0) isOnCooldown = true;
                        break;
                }

                if (!player.isCreative() && isOnCooldown) {
                    player.sendSystemMessage(Component.literal("Supply on Cooldown!").withStyle(ChatFormatting.RED));
                    return;
                }

                if (!player.isCreative() && hub.getMaterials() < cost) {
                    player.sendSystemMessage(Component.literal("Not enough Construction Materials! Need: " + cost).withStyle(ChatFormatting.RED));
                    return;
                }

                boolean success = false;

                if (msg.type == 1) {
                    ItemStack stack = new ItemStack(ModItems.AGS_AMMO.get());
                    AGSAmmoItem.setAmmo(stack, AGSAmmoItem.MAX_AMMO);
                    if (player.getInventory().add(stack)) success = true;
                    else player.drop(stack, false);
                    success = true;
                }
                else if (msg.type == 2) {
                    ItemStack stack = new ItemStack(ModItems.M2_AMMO.get());
                    M2AmmoItem.setAmmo(stack, M2AmmoItem.MAX_AMMO);
                    if (player.getInventory().add(stack)) success = true;
                    else player.drop(stack, false);
                    success = true;
                }
                else if (msg.type == 3) {
                    Item mortarItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("superbwarfare", "mortar_shell"));
                    if (mortarItem == null || mortarItem == Items.AIR) mortarItem = Items.ARROW;
                    ItemStack stack = new ItemStack(mortarItem, 8);
                    if (player.getInventory().add(stack)) success = true;
                    else player.drop(stack, false);
                    success = true;
                }
                else if (msg.type == 4) {
                    Item towItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("superbwarfare", "medium_anti_ground_missile"));
                    if (towItem == null || towItem == Items.AIR) towItem = Items.SPECTRAL_ARROW;
                    ItemStack stack = new ItemStack(towItem, 2);
                    if (player.getInventory().add(stack)) success = true;
                    else player.drop(stack, false);
                    success = true;
                }

                if (success) {
                    if (!player.isCreative()) {
                        hub.consumeMaterials(cost);
                        hub.setCooldown(msg.type, cooldownTime);
                    }
                    player.sendSystemMessage(Component.literal("Resupplied! (-" + cost + " Mats)").withStyle(ChatFormatting.GREEN));
                    player.level().sendBlockUpdated(msg.pos, hub.getBlockState(), hub.getBlockState(), 3);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}