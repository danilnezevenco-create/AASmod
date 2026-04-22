package com.example.aas.network;

import com.example.aas.block.*;
import com.example.aas.config.AASConfig;
import com.example.aas.world.AASWorldData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

public class PacketRadioAction {
    private final int actionId;

    public PacketRadioAction(int actionId) {
        this.actionId = actionId;
    }

    public static void encode(PacketRadioAction msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.actionId);
    }

    public static PacketRadioAction decode(FriendlyByteBuf buf) {
        return new PacketRadioAction(buf.readInt());
    }

    public static void handle(PacketRadioAction msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;

            ServerLevel currentLevel = player.serverLevel();
            AASWorldData data = AASWorldData.get(currentLevel);

            String pName = player.getScoreboardName();
            AASWorldData.Squad playerSquad = null;
            boolean isLeader = false;

            for (AASWorldData.Squad s : data.squads) {
                if (s.members.contains(pName)) {
                    playerSquad = s;
                    if (s.leader.equals(pName)) isLeader = true;
                    break;
                }
            }

            if (!player.isCreative()) {
                if (player.getTeam() == null) {
                    player.sendSystemMessage(Component.literal("Access Denied: You must be in a TEAM!").withStyle(ChatFormatting.RED));
                    return;
                }
                if (playerSquad == null) {
                    player.sendSystemMessage(Component.literal("Access Denied: You must be in a SQUAD in this world!").withStyle(ChatFormatting.RED));
                    return;
                }
                if (!isLeader) {
                    player.sendSystemMessage(Component.literal("Access Denied: You must be a Squad Leader!").withStyle(ChatFormatting.RED));
                    return;
                }
            }

            ItemStack stack = player.getMainHandItem();
            long currentGameTime = currentLevel.getGameTime();

            if (msg.actionId == 0) { // Rally
                long lastUse = stack.getOrCreateTag().getLong("RallyCooldownEnd");
                if (currentGameTime < lastUse && !player.isCreative()) {
                    long timeLeft = (lastUse - currentGameTime) / 20;
                    player.sendSystemMessage(Component.literal("Rally Point Cooldown: " + timeLeft + "s").withStyle(ChatFormatting.RED));
                    return;
                }

                String team = "NEUTRAL";
                if (player.getTeam() != null) {
                    String rawTeamName = player.getTeam().getName();
                    if (rawTeamName.equalsIgnoreCase("Blue")) team = "BLUE";
                    else if (rawTeamName.equalsIgnoreCase("Red")) team = "RED";
                }
                if (team.equals("NEUTRAL") && player.isCreative()) team = "BLUE";

                if (trySpawnRally(player, currentLevel, team, playerSquad, data)) {
                    long nextAvailableTime = currentGameTime + (13 * 60 * 20); // 8 минут КД
                    stack.getOrCreateTag().putLong("RallyCooldownEnd", nextAvailableTime);
                    player.sendSystemMessage(Component.literal("Squad Rally Point Deployed!").withStyle(ChatFormatting.GREEN));
                } else {
                    stack.getOrCreateTag().putLong("RallyCooldownEnd", currentGameTime + (30 * 20)); // Штраф 30 сек
                }
            } else {
                handleConstructionLogic(msg.actionId, player, currentLevel, data);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static void handleConstructionLogic(int actionId, ServerPlayer player, ServerLevel level, AASWorldData data) {
        BlockPos targetPos = player.blockPosition();
        if (!level.getBlockState(targetPos).canBeReplaced() && actionId != 14) {
            targetPos = targetPos.relative(player.getDirection());
        }

        if (actionId == 14) { // HUB
            final String playerTeam = (player.getTeam() != null) ? player.getTeam().getName().toUpperCase() : "NEUTRAL";

            // 1. Проверка лимита ХАБов
            long hubCount = data.hubs.stream().filter(h -> h.team.equalsIgnoreCase(playerTeam)).count();
            if (hubCount >= AASConfig.MAX_HUBS_PER_TEAM.get() && !player.isCreative()) {
                player.sendSystemMessage(Component.literal("FOB Limit Reached for this world!").withStyle(ChatFormatting.RED));
                return;
            }

            // 2. Проверка дистанции до союзных ХАБов
            for (AASWorldData.HubInfo existingHub : data.hubs) {
                if (existingHub.team.equalsIgnoreCase(playerTeam) && existingHub.pos.distSqr(targetPos) < AASConfig.MIN_HUB_DISTANCE.get() * AASConfig.MIN_HUB_DISTANCE.get()) {
                    player.sendSystemMessage(Component.literal("Too close to friendly FOB!").withStyle(ChatFormatting.RED));
                    return;
                }
            }

            // 3. НОВОЕ: Проверка наличия и поглощение ящика снабжения
            if (AASConfig.HUB_PLACEMENT_REQUIRES_CRATE.get() && !player.isCreative()) {
                double crateCheckRad = 50.0;
                AABB area = new AABB(targetPos).inflate(crateCheckRad);
                java.util.List<com.example.aas.entity.SupplyCrateEntity> crates = level.getEntitiesOfClass(com.example.aas.entity.SupplyCrateEntity.class, area);

                // Ищем ближайший подходящий ящик (свой или нейтральный)
                com.example.aas.entity.SupplyCrateEntity targetCrate = crates.stream()
                        .filter(c -> c.getTeamOwner().equals("NEUTRAL") || c.getTeamOwner().equalsIgnoreCase(playerTeam))
                        .findFirst().orElse(null);

                if (targetCrate == null) {
                    player.sendSystemMessage(Component.literal("FOB placement requires a Supply Crate within 50 blocks!").withStyle(ChatFormatting.RED));
                    return;
                } else {
                    targetCrate.discard(); // Удаляем ящик
                    player.sendSystemMessage(Component.literal("Supply Crate consumed for FOB placement.").withStyle(ChatFormatting.YELLOW));
                }
            }

            // 4. Установка блока и сохранение данных
            level.setBlock(targetPos, ModBlocks.HUB_BLOCK.get().defaultBlockState(), 3);
            BlockEntity be = level.getBlockEntity(targetPos);
            if (be instanceof HubBlockEntity hubEntity) {
                hubEntity.setTeam(playerTeam);
                level.sendBlockUpdated(targetPos, level.getBlockState(targetPos), level.getBlockState(targetPos), 3);
            }

            data.hubs.add(new AASWorldData.HubInfo(targetPos, playerTeam, false, level.dimension().location().toString()));
            data.setDirty();
            PacketHandler.sendToAllClients(level, data);
            player.sendSystemMessage(Component.literal("FOB Blueprint placed!").withStyle(ChatFormatting.GREEN));
        } else if (actionId == 20)
            placeBlueprint(level, targetPos, player, ModBlocks.M2_CONSTRUCTION_BLOCK.get().defaultBlockState().setValue(M2ConstructionBlock.FACING, player.getDirection().getOpposite()), "M2");
        else if (actionId == 21)
            placeBlueprint(level, targetPos, player, ModBlocks.AGS_CONSTRUCTION_BLOCK.get().defaultBlockState().setValue(AGSConstructionBlock.FACING, player.getDirection().getOpposite()), "AGS");
        else if (actionId == 22)
            placeBlueprint(level, targetPos, player, ModBlocks.MORTAR_CONSTRUCTION_BLOCK.get().defaultBlockState().setValue(MortarConstructionBlock.FACING, player.getDirection().getOpposite()), "Mortar");
        else if (actionId == 23)
            placeBlueprint(level, targetPos, player, ModBlocks.TOW_CONSTRUCTION_BLOCK.get().defaultBlockState().setValue(TOWConstructionBlock.FACING, player.getDirection().getOpposite()), "TOW");
    }

    private static void placeBlueprint(ServerLevel level, BlockPos pos, ServerPlayer player, BlockState state, String name) {
        if (!level.getBlockState(pos).isAir() && !level.getBlockState(pos).canBeReplaced()) return;
        level.setBlock(pos, state, 3);
        BlockEntity be = level.getBlockEntity(pos);
        String pTeam = (player.getTeam() != null) ? player.getTeam().getName() : "NEUTRAL";

        if (be instanceof M2ConstructionBlockEntity m2) m2.setTeam(pTeam);
        else if (be instanceof AGSConstructionBlockEntity ags) ags.setTeam(pTeam);
        else if (be instanceof MortarConstructionBlockEntity mortar) mortar.setTeam(pTeam);
        else if (be instanceof TOWConstructionBlockEntity tow) tow.setTeam(pTeam);

        player.sendSystemMessage(Component.literal(name + " Blueprint placed.").withStyle(ChatFormatting.GREEN));
    }

    private static boolean trySpawnRally(ServerPlayer player, ServerLevel level, String team, AASWorldData.Squad squad, AASWorldData data) {
        BlockPos pos = player.blockPosition();

        // 1. ПРОВЕРКА: Близость к точкам захвата
        for (AASWorldData.CapturePoint point : data.capturePoints) {
            Vec3 center = point.area.getCenter();
            if (pos.distToCenterSqr(center.x, center.y, center.z) < AASConfig.MIN_RALLY_POINT_DISTANCE.get() * AASConfig.MIN_RALLY_POINT_DISTANCE.get()) {
                player.sendSystemMessage(Component.literal("Too close to Capture Point!").withStyle(ChatFormatting.RED));
                return false;
            }
        }

        // 2. ПРОВЕРКА: Враги в радиусе блокировки (из конфига)
        int checkRadius = AASConfig.RALLY_BLOCK_RADIUS.get();
        AABB enemyBox = new AABB(pos).inflate(checkRadius);
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, enemyBox)) {
            if (!p.isSpectator() && p.getTeam() != null && !p.getTeam().getName().equalsIgnoreCase(team)) {
                player.sendSystemMessage(Component.literal("Enemies nearby! Cannot deploy Rally.").withStyle(ChatFormatting.RED));
                return false;
            }
        }

        // 3. ПРОВЕРКА: Buddy System (Нужен 1 союзник в радиусе 5 блоков)
        AABB allyBox = new AABB(pos).inflate(5);
        int allies = 0;
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, allyBox)) {
            if (p != player && !p.isSpectator() && p.getTeam() != null && p.getTeam().getName().equalsIgnoreCase(team)) {
                allies++;
            }
        }

        // ВЫПОЛНЕНИЕ: Если союзник рядом ИЛИ игрок в креативе
        if (allies >= 1 || player.isCreative()) {

            // Удаляем старый блок раллика этого отряда, если он существует
            if (squad.rallyPos != null && level.isLoaded(squad.rallyPos)) {
                if (level.getBlockState(squad.rallyPos).getBlock() instanceof RallyPointBlock) {
                    level.removeBlock(squad.rallyPos, false);
                }
            }

            // Устанавливаем новый блок раллика
            BlockState rallyState = team.equals("BLUE") ? ModBlocks.BLUE_RALLY_BLOCK.get().defaultBlockState() : ModBlocks.RED_RALLY_BLOCK.get().defaultBlockState();
            level.setBlock(pos, rallyState, 3);

            // Привязываем ID отряда к блоку (для штрафов при уничтожении)
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof RallyPointBlockEntity rbe) {
                rbe.setSquadId(squad.id);
            }

            // СОХРАНЕНИЕ ДАННЫХ В СКВАД
            squad.rallyPos = pos;
            squad.rallyDimension = level.dimension().location().toString();

            // УСТАНОВКА ТАЙМЕРА (10 минут = 12000 тиков)
            squad.rallyExpiryTick = level.getGameTime() + 12000;

            // Сообщение об успешной установке (Теперь скобки закрыты правильно)
            player.sendSystemMessage(Component.literal("Squad Rally Point Deployed!").withStyle(ChatFormatting.GREEN));

            // Обновляем списки для глобальной карты
            if (team.equals("BLUE")) {
                data.blueRallies.add(pos);
            } else {
                data.redRallies.add(pos);
            }

            data.setDirty();

            // СИНХРОНИЗАЦИЯ: Чтобы иконка появилась на карте и в меню
            PacketHandler.sendToAllClients(level, data);
            PacketHandler.INSTANCE.send(PacketDistributor.DIMENSION.with(level::dimension), new PacketSyncSquads(data.squads));

            return true;
        } else {
            player.sendSystemMessage(Component.literal("Need 1 ally nearby!").withStyle(ChatFormatting.RED));
            return false;
        }
    }
}