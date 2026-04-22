// PATH: src\main\java\com\example\aas\network\PacketRequestCrateAmmo.java
package com.example.aas.network;

import com.example.aas.config.AASConfig;
import com.example.aas.entity.SupplyCrateEntity;
import com.example.aas.item.AGSAmmoItem;
import com.example.aas.item.M2AmmoItem;
import com.example.aas.item.ModItems;
import com.example.aas.world.AASWorldData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.function.Supplier;

public class PacketRequestCrateAmmo {
    private final int entityId;
    private final int type;

    public PacketRequestCrateAmmo(int entityId, int type) {
        this.entityId = entityId;
        this.type = type;
    }

    public static void encode(PacketRequestCrateAmmo msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
        buf.writeInt(msg.type);
    }

    public static PacketRequestCrateAmmo decode(FriendlyByteBuf buf) {
        return new PacketRequestCrateAmmo(buf.readInt(), buf.readInt());
    }

    public static void handle(PacketRequestCrateAmmo msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            Entity target = player.level().getEntity(msg.entityId);
            if (!(target instanceof SupplyCrateEntity crate)) return;

            // Защита дистанции
            if (player.distanceToSqr(crate) > 64.0) return;

            int cost = 0;
            switch (msg.type) {
                case 0: cost = AASConfig.HUB_RESUPPLY_COST.get(); break;
                case 1: cost = 20; break; // AGS
                case 2: cost = 15; break; // M2
                case 3: cost = 20; break; // Mortar
                case 4: cost = 50; break; // TOW
            }

            // === Логика Китов (Оставляет ящик, если есть еще материалы) ===
            if (msg.type == 0) {
                String kitName = player.getPersistentData().getString("AAS_CurrentKit");
                if (kitName.isEmpty()) {
                    player.sendSystemMessage(Component.literal("No Kit equipped!").withStyle(ChatFormatting.RED));
                    return;
                }

                long lastFobUse = player.getPersistentData().getLong("AAS_LastFobResupply");
                long currentTime = player.level().getGameTime();
                if (!player.isCreative() && currentTime < lastFobUse + 1200) {
                    player.sendSystemMessage(Component.literal("Kit Resupply is on cooldown!").withStyle(ChatFormatting.RED));
                    return;
                }

                if (!player.isCreative() && crate.getMaterials() < cost) {
                    player.sendSystemMessage(Component.literal("Not enough Materials in Crate! Need: " + cost).withStyle(ChatFormatting.RED));
                    return;
                }

                AASWorldData data = AASWorldData.get(player.serverLevel());
                String t = player.getTeam() != null ? player.getTeam().getName().toUpperCase() : "NEUTRAL";
                AASWorldData.KitInfo kit = t.equals("BLUE") ? data.blueKits.get(kitName) : data.redKits.get(kitName);

                if (kit != null) {
                    if (ResupplyHandler.resupplyPlayer(player, kit, false)) {
                        if (!player.isCreative()) {
                            crate.setMaterials(crate.getMaterials() - cost);
                            player.getPersistentData().putLong("AAS_LastFobResupply", currentTime);
                        }
                        player.sendSystemMessage(Component.literal("Kit Resupplied! (-" + cost + " Mats)").withStyle(ChatFormatting.GREEN));
                    } else {
                        player.sendSystemMessage(Component.literal("Ammo already full!").withStyle(ChatFormatting.YELLOW));
                    }
                }
                return;
            }

            // === Логика Тяжелых патронов (AGS, M2 и т.д. - УНИЧТОЖАЕТ ЯЩИК) ===
            if (!player.isCreative() && crate.getMaterials() < cost) {
                player.sendSystemMessage(Component.literal("Not enough Materials in Crate! Need: " + cost).withStyle(ChatFormatting.RED));
                return;
            }

            boolean success = false;

            if (msg.type == 1) { // AGS
                ItemStack stack = new ItemStack(ModItems.AGS_AMMO.get());
                AGSAmmoItem.setAmmo(stack, AGSAmmoItem.MAX_AMMO);
                if (player.getInventory().add(stack)) success = true;
                else player.drop(stack, false);
                success = true;
            } else if (msg.type == 2) { // M2
                ItemStack stack = new ItemStack(ModItems.M2_AMMO.get());
                M2AmmoItem.setAmmo(stack, M2AmmoItem.MAX_AMMO);
                if (player.getInventory().add(stack)) success = true;
                else player.drop(stack, false);
                success = true;
            } else if (msg.type == 3) { // Mortar
                Item mortarItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("superbwarfare", "mortar_shell"));
                if (mortarItem == null || mortarItem == Items.AIR) mortarItem = Items.ARROW;
                ItemStack stack = new ItemStack(mortarItem, 8);
                if (player.getInventory().add(stack)) success = true;
                else player.drop(stack, false);
                success = true;
            } else if (msg.type == 4) { // TOW
                Item towItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("superbwarfare", "medium_anti_ground_missile"));
                if (towItem == null || towItem == Items.AIR) towItem = Items.SPECTRAL_ARROW;
                ItemStack stack = new ItemStack(towItem, 2);
                if (player.getInventory().add(stack)) success = true;
                else player.drop(stack, false);
                success = true;
            }

            if (success) {
                if (!player.isCreative()) {
                    // ИЗМЕНЕНИЕ: Обнуляем материалы (crate.setMaterials(0)),
                    // что заставит ящик мгновенно исчезнуть в его методе tick()
                    crate.setMaterials(0);
                }
                player.sendSystemMessage(Component.literal("Heavy Ammo Resupplied! Crate consumed.").withStyle(ChatFormatting.GREEN));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}