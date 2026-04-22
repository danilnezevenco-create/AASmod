package com.example.aas.item;

import com.example.aas.client.ClientData;
import com.example.aas.client.ClientHooks;
import com.example.aas.world.AASWorldData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

public class RallyItem extends Item {

    public RallyItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {

                // === 0. CREATIVE BYPASS ===
                if (player.isCreative()) {
                    ClientHooks.openRadioMenu();
                    return;
                }

                // === 1. ПРОВЕРКА КОМАНДЫ (TEAM) ===
                if (player.getTeam() == null) {
                    player.displayClientMessage(Component.literal("You must join a TEAM (Blue/Red) first!").withStyle(ChatFormatting.RED), true);
                    return;
                }

                // === 2. ПРОВЕРКА ОТРЯДА (SQUAD) ===
                String playerName = player.getScoreboardName();
                boolean isInSquad = false;
                boolean isLeader = false;

                for (AASWorldData.Squad s : ClientData.clientSquads) {
                    if (s.members.contains(playerName)) {
                        isInSquad = true;
                        if (s.leader.equals(playerName)) {
                            isLeader = true;
                        }
                        break;
                    }
                }

                if (!isInSquad) {
                    player.displayClientMessage(Component.literal("You must join a SQUAD first! Press 'K'.").withStyle(ChatFormatting.RED), true);
                    return;
                }

                // === 3. ПРОВЕРКА ЛИДЕРА ===
                if (!isLeader) {
                    player.displayClientMessage(Component.literal("You must be a Squad Leader to use this!").withStyle(ChatFormatting.RED), true);
                    return;
                }

                // Все проверки пройдены
                ClientHooks.openRadioMenu();
            });
        }
        return InteractionResultHolder.pass(player.getItemInHand(hand));
    }
}