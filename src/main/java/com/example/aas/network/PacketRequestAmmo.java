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
                    case 0: // AMMO (KIT RESUPPLY)
                        String kitName = player.getPersistentData().getString("AAS_CurrentKit");
                        if (kitName.isEmpty()) {
                            player.sendSystemMessage(Component.literal("No Kit equipped!").withStyle(ChatFormatting.RED));
                            return;
                        }

                        // === СЕРВЕРНАЯ ПРОВЕРКА КД ===
                        long lastFobUse = player.getPersistentData().getLong("AAS_LastFobResupply");
                        long currentTime = player.level().getGameTime();
                        if (!player.isCreative() && currentTime < lastFobUse + 1200) { // 1200 тиков = 60 сек
                            player.sendSystemMessage(Component.literal("Kit Resupply is on cooldown!").withStyle(ChatFormatting.RED));
                            return;
                        }

                        cost = com.example.aas.config.AASConfig.HUB_RESUPPLY_COST.get();

                        if (!player.isCreative() && hub.getMaterials() < cost) {
                            player.sendSystemMessage(Component.literal("Not enough Materials! Need: " + cost).withStyle(ChatFormatting.RED));
                            return;
                        }

                        AASWorldData data = AASWorldData.get(player.serverLevel());
                        String t = player.getTeam() != null ? player.getTeam().getName().toUpperCase() : "NEUTRAL";
                        AASWorldData.KitInfo kit = t.equals("BLUE") ? data.blueKits.get(kitName) : data.redKits.get(kitName);

                        if (kit != null) {
                            if (ResupplyHandler.resupplyPlayer(player, kit, false)) {
                                if (!player.isCreative()) {
                                    hub.consumeMaterials(cost);
                                    player.getPersistentData().putLong("AAS_LastFobResupply", currentTime); // СТАВИМ КД
                                }
                                player.level().sendBlockUpdated(msg.pos, hub.getBlockState(), hub.getBlockState(), 3);
                                player.sendSystemMessage(Component.literal("Kit Resupplied! (-" + cost + " Mats)").withStyle(ChatFormatting.GREEN));
                            } else {
                                player.sendSystemMessage(Component.literal("Ammo already full!").withStyle(ChatFormatting.YELLOW));
                            }
                        }
                        return; // Возврат для кита
                    case 1: // AGS-30 (Добавляем эту логику)
                        cost = 15;            // Стоимость как у M2
                        cooldownTime = 1200;  // КД 60 секунд (1200 тиков)
                        if (hub.cooldownAGS > 0) isOnCooldown = true; // Проверка КД
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