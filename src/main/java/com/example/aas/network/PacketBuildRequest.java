package com.example.aas.network;

import com.example.aas.block.*;
import com.example.aas.item.ModItems;
import com.example.aas.world.AASWorldData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.network.NetworkEvent;
import com.example.aas.config.AASConfig;
import com.example.aas.entity.SupplyCrateEntity;
import net.minecraft.world.phys.AABB;
import java.util.List;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class PacketBuildRequest {
    private final int structureId;
    private final BlockPos pos;
    private final int rotation;

    public PacketBuildRequest(int structureId, BlockPos pos, int rotation) {
        this.structureId = structureId;
        this.pos = pos;
        this.rotation = rotation;
    }

    public static void encode(PacketBuildRequest msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.structureId);
        buf.writeBlockPos(msg.pos);
        buf.writeInt(msg.rotation);
    }

    public static PacketBuildRequest decode(FriendlyByteBuf buf) {
        return new PacketBuildRequest(buf.readInt(), buf.readBlockPos(), buf.readInt());
    }

    public static void handle(PacketBuildRequest msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            ServerLevel level = player.serverLevel();

            // Проверка дистанции
            if (player.distanceToSqr(msg.pos.getX(), msg.pos.getY(), msg.pos.getZ()) > 64) {
                return;
            }

            // Проверка: держит ли игрок рацию (чтобы не спамили пакетами без предмета)
            boolean hasRadio = player.getMainHandItem().getItem() == ModItems.SQUAD_LEADER_RADIO.get() ||
                    player.getOffhandItem().getItem() == ModItems.SQUAD_LEADER_RADIO.get();
            if (!hasRadio && !player.isCreative()) return;


            // === 1. РАСЧЕТ НАПРАВЛЕНИЯ ===
            // Нормализуем угол в диапазон [0, 360)
            int r = msg.rotation % 360;
            if (r < 0) r += 360;

            Direction facing = Direction.NORTH;
            // Сопоставление визуального поворота (PoseStack Y axis) и сторон света Minecraft
            if (r == 0) facing = Direction.NORTH;
            else if (r == 270) facing = Direction.EAST;
            else if (r == 180) facing = Direction.SOUTH;
            else if (r == 90) facing = Direction.WEST;

            String team = "NEUTRAL";
            if (player.getTeam() != null) team = player.getTeam().getName();

            // Если игрок в креативе, материалы не нужны
            boolean isCreative = player.isCreative();

            // ==========================================
            // === СТРУКТУРА ID 10: СТЕНА 1x1 ===
            // ==========================================
            if (msg.structureId == 10) {
                int cost = 5;
                if (!canPlaceAt(level, msg.pos)) {
                    sendBlockedMessage(player);
                    return;
                }

                if (isCreative || hasMaterials(level, msg.pos, team, cost)) {
                    if (!isCreative) consumeMaterials(level, msg.pos, team, cost);
                    BlockState state = ModBlocks.WALL_BLOCK.get().defaultBlockState()
                            .setValue(WallBlock.FACING, facing)
                            .setValue(WallBlock.CONSTRUCTED, false)
                            .setValue(WallBlock.VALID, true);
                    level.setBlock(msg.pos, state, 3);
                    setupWallEntity(level, msg.pos, team, false, null);
                } else {
                    sendNoMaterialsMessage(player, cost);
                }
            }

            // ==========================================
            // === СТРУКТУРА ID 11: СТЕНА 2x2 ===
            // ==========================================
            else if (msg.structureId == 11) {
                int cost = 10;
                List<BlockPos> wallParts = new ArrayList<>();
                wallParts.add(msg.pos);
                wallParts.add(msg.pos.above());

                BlockPos secondColPos = getRelativePos(msg.pos, facing);
                wallParts.add(secondColPos);
                wallParts.add(secondColPos.above());

                attemptBuildMulti(player, level, wallParts, facing, team, cost);
            }

            // ==========================================
            // === СТРУКТУРА ID 12: СТЕНА 3x3 ===
            // ==========================================
            else if (msg.structureId == 12) {
                int cost = 15;
                List<BlockPos> wallParts = new ArrayList<>();
                for (int h = 0; h < 3; h++) {
                    BlockPos columnBase = getRelativePos(msg.pos, facing, h);
                    for (int v = 0; v < 3; v++) {
                        wallParts.add(columnBase.above(v));
                    }
                }
                attemptBuildMulti(player, level, wallParts, facing, team, cost);
            }

            // ==========================================
            // === СТРУКТУРА ID 13: ПРОВОЛОКА ===
            // ==========================================
            else if (msg.structureId == 13) {
                int cost = 25;
                List<BlockPos> parts = new ArrayList<>();
                for (int h = 0; h < 3; h++) {
                    parts.add(getRelativePos(msg.pos, facing, h));
                }

                boolean blocked = false;
                for (BlockPos p : parts) {
                    if (!canPlaceAt(level, p)) {
                        blocked = true;
                        break;
                    }
                }

                if (blocked) {
                    sendBlockedMessage(player);
                    return;
                }

                if (isCreative || hasMaterials(level, parts.get(0), team, cost)) {
                    if (!isCreative) consumeMaterials(level, parts.get(0), team, cost);
                    for (BlockPos p : parts) {
                        BlockState state = ModBlocks.BARBED_WIRE_BLOCK.get().defaultBlockState()
                                .setValue(BarbedWireBlock.FACING, facing)
                                .setValue(BarbedWireBlock.CONSTRUCTED, false)
                                .setValue(BarbedWireBlock.VALID, true);
                        level.setBlock(p, state, 3);
                    }
                    for (BlockPos p : parts) {
                        BlockEntity be = level.getBlockEntity(p);
                        if (be instanceof BarbedWireBlockEntity wire) {
                            wire.setTeam(team);
                            wire.setLinkedWires(parts);
                        }
                    }
                } else {
                    sendNoMaterialsMessage(player, cost);
                }
            }

            // ==========================================
            // === СТРУКТУРА ID 20: M2 BROWNING ===
            // ==========================================
            else if (msg.structureId == 20) {
                int cost = 40;
                if (!canPlaceAt(level, msg.pos)) {
                    sendBlockedMessage(player);
                    return;
                }

                if (isCreative || hasMaterials(level, msg.pos, team, cost)) {
                    if (!isCreative) consumeMaterials(level, msg.pos, team, cost);

                    BlockState m2State = ModBlocks.M2_CONSTRUCTION_BLOCK.get().defaultBlockState()
                            .setValue(M2ConstructionBlock.FACING, facing)
                            .setValue(M2ConstructionBlock.VALID, true);

                    if (level.setBlock(msg.pos, m2State, 3)) {
                        BlockEntity be = level.getBlockEntity(msg.pos);
                        if (be instanceof M2ConstructionBlockEntity m2) {
                            m2.setTeam(team);
                        }
                        player.sendSystemMessage(Component.literal("M2 Blueprint placed!").withStyle(ChatFormatting.GREEN));
                    }
                } else {
                    sendNoMaterialsMessage(player, cost);
                }
            }

            // ==========================================
            // === СТРУКТУРА ID 21: AGS-30 ===
            // ==========================================
            else if (msg.structureId == 21) {
                int cost = 100;
                if (!canPlaceAt(level, msg.pos)) {
                    sendBlockedMessage(player);
                    return;
                }

                if (isCreative || hasMaterials(level, msg.pos, team, cost)) {
                    if (!isCreative) consumeMaterials(level, msg.pos, team, cost);

                    BlockState agsState = ModBlocks.AGS_CONSTRUCTION_BLOCK.get().defaultBlockState()
                            .setValue(AGSConstructionBlock.FACING, facing)
                            .setValue(AGSConstructionBlock.VALID, true);

                    if (level.setBlock(msg.pos, agsState, 3)) {
                        BlockEntity be = level.getBlockEntity(msg.pos);
                        if (be instanceof AGSConstructionBlockEntity ags) {
                            ags.setTeam(team);
                        }
                        player.sendSystemMessage(Component.literal("AGS-30 Blueprint placed!").withStyle(ChatFormatting.GREEN));
                    }
                } else {
                    sendNoMaterialsMessage(player, cost);
                }
            }

            // ==========================================
            // === СТРУКТУРА ID 22: MORTAR ===
            // ==========================================
            else if (msg.structureId == 22) {
                int cost = 200;
                if (!canPlaceAt(level, msg.pos)) {
                    sendBlockedMessage(player);
                    return;
                }

                if (isCreative || hasMaterials(level, msg.pos, team, cost)) {
                    if (!isCreative) consumeMaterials(level, msg.pos, team, cost);

                    BlockState state = ModBlocks.MORTAR_CONSTRUCTION_BLOCK.get().defaultBlockState()
                            .setValue(MortarConstructionBlock.FACING, facing)
                            .setValue(MortarConstructionBlock.VALID, true);

                    if (level.setBlock(msg.pos, state, 3)) {
                        BlockEntity be = level.getBlockEntity(msg.pos);
                        if (be instanceof MortarConstructionBlockEntity mortar) {
                            mortar.setTeam(team);
                        }
                        player.sendSystemMessage(Component.literal("Mortar Blueprint placed!").withStyle(ChatFormatting.GREEN));
                    }
                } else {
                    sendNoMaterialsMessage(player, cost);
                }
            }

            // ==========================================
            // === СТРУКТУРА ID 23: TOW ===
            // ==========================================
            else if (msg.structureId == 23) {
                int cost = 200;
                if (!canPlaceAt(level, msg.pos)) {
                    sendBlockedMessage(player);
                    return;
                }

                if (isCreative || hasMaterials(level, msg.pos, team, cost)) {
                    if (!isCreative) consumeMaterials(level, msg.pos, team, cost);

                    BlockState state = ModBlocks.TOW_CONSTRUCTION_BLOCK.get().defaultBlockState()
                            .setValue(TOWConstructionBlock.FACING, facing)
                            .setValue(TOWConstructionBlock.VALID, true);

                    if (level.setBlock(msg.pos, state, 3)) {
                        BlockEntity be = level.getBlockEntity(msg.pos);
                        if (be instanceof TOWConstructionBlockEntity tow) {
                            tow.setTeam(team);
                        }
                        player.sendSystemMessage(Component.literal("TOW Blueprint placed!").withStyle(ChatFormatting.GREEN));
                    }
                } else {
                    sendNoMaterialsMessage(player, cost);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }

    // === ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ===

    private static boolean canPlaceAt(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).canBeReplaced();
    }

    private static void sendBlockedMessage(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("Space is blocked!").withStyle(ChatFormatting.RED));
    }

    private static void sendNoMaterialsMessage(ServerPlayer player, int cost) {
        player.sendSystemMessage(Component.literal("Need " + cost + " Materials!").withStyle(ChatFormatting.RED));
    }

    private static BlockPos getRelativePos(BlockPos start, Direction facing) {
        return getRelativePos(start, facing, 1);
    }

    private static BlockPos getRelativePos(BlockPos start, Direction facing, int offset) {
        if (facing == Direction.NORTH) return start.east(offset);
        else if (facing == Direction.WEST) return start.north(offset);
        else if (facing == Direction.SOUTH) return start.west(offset);
        else if (facing == Direction.EAST) return start.south(offset);
        return start;
    }

    private static void attemptBuildMulti(ServerPlayer player, ServerLevel level, List<BlockPos> parts, Direction facing, String team, int cost) {
        for (BlockPos p : parts) {
            if (!canPlaceAt(level, p)) {
                sendBlockedMessage(player);
                return;
            }
        }
        if (player.isCreative() || hasMaterials(level, parts.get(0), team, cost)) {
            if (!player.isCreative()) consumeMaterials(level, parts.get(0), team, cost);
            for (BlockPos p : parts) {
                BlockState state = ModBlocks.WALL_BLOCK.get().defaultBlockState()
                        .setValue(WallBlock.FACING, facing)
                        .setValue(WallBlock.CONSTRUCTED, false)
                        .setValue(WallBlock.VALID, true);
                level.setBlock(p, state, 3);
            }
            for (BlockPos p : parts) {
                setupWallEntity(level, p, team, true, parts);
            }
        } else {
            sendNoMaterialsMessage(player, cost);
        }
    }

    private static void setupWallEntity(ServerLevel level, BlockPos pos, String team, boolean multi, List<BlockPos> links) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof WallBlockEntity wall) {
            wall.setTeam(team);
            if (multi && links != null) wall.setLinkedWalls(links);
        }
    }

    // Метод для проверки суммы материалов со всех Хабов и Ящиков в радиусе
    private static boolean hasMaterials(ServerLevel level, BlockPos pos, String team, int cost) {
        int totalMaterials = 0;
        com.example.aas.world.AASWorldData data = com.example.aas.world.AASWorldData.get(level);
        String currentDim = level.dimension().location().toString();

        // 1. Считаем материалы в Хабах
        int hubRadius = com.example.aas.config.AASConfig.HUB_BUILD_RADIUS.get();
        double maxHubSq = hubRadius * hubRadius;

        for (com.example.aas.world.AASWorldData.HubInfo hubInfo : data.hubs) {
            if (hubInfo.dimension != null && !hubInfo.dimension.equals(currentDim)) continue;
            if (hubInfo.pos.distSqr(pos) <= maxHubSq) {
                if (level.isLoaded(hubInfo.pos)) {
                    net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(hubInfo.pos);
                    if (be instanceof com.example.aas.block.HubBlockEntity hub) {
                        if (hub.getTeam().equalsIgnoreCase(team) || hub.getTeam().equals("NEUTRAL")) {
                            totalMaterials += hub.getMaterials();
                        }
                    }
                }
            }
        }

        // 2. Считаем материалы в Ящиках Снабжения (Supply Crates)
        int crateRadius = com.example.aas.config.AASConfig.CRATE_BUILD_RADIUS.get();
        double maxCrateSq = crateRadius * crateRadius;
        net.minecraft.world.phys.AABB searchArea = new net.minecraft.world.phys.AABB(pos).inflate(crateRadius);
        java.util.List<com.example.aas.entity.SupplyCrateEntity> crates = level.getEntitiesOfClass(com.example.aas.entity.SupplyCrateEntity.class, searchArea);

        for (com.example.aas.entity.SupplyCrateEntity crate : crates) {
            if (crate.getTeamOwner().equalsIgnoreCase(team) || crate.getTeamOwner().equals("NEUTRAL")) {
                if (crate.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= maxCrateSq) {
                    totalMaterials += crate.getMaterials();
                }
            }
        }

        return totalMaterials >= cost;
    }

    // Метод для списывания ресурсов (сначала забирает с Ящиков, если не хватает - берет из Хабов)
    private static void consumeMaterials(ServerLevel level, BlockPos pos, String team, int cost) {
        int remainingToDeduct = cost;
        com.example.aas.world.AASWorldData data = com.example.aas.world.AASWorldData.get(level);
        String currentDim = level.dimension().location().toString();

        // 1. Сначала пытаемся списать материалы с Ящиков Снабжения
        int crateRadius = com.example.aas.config.AASConfig.CRATE_BUILD_RADIUS.get();
        double maxCrateSq = crateRadius * crateRadius;
        net.minecraft.world.phys.AABB searchArea = new net.minecraft.world.phys.AABB(pos).inflate(crateRadius);
        java.util.List<com.example.aas.entity.SupplyCrateEntity> crates = level.getEntitiesOfClass(com.example.aas.entity.SupplyCrateEntity.class, searchArea);

        for (com.example.aas.entity.SupplyCrateEntity crate : crates) {
            if (remainingToDeduct <= 0) break; // Если списали всё, что нужно - выходим

            if (crate.getTeamOwner().equalsIgnoreCase(team) || crate.getTeamOwner().equals("NEUTRAL")) {
                if (crate.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= maxCrateSq) {
                    int cMats = crate.getMaterials();
                    int take = Math.min(cMats, remainingToDeduct);

                    crate.setMaterials(cMats - take);
                    remainingToDeduct -= take;
                }
            }
        }

        // 2. Если в ящиках не хватило, остаток списываем с Хабов
        if (remainingToDeduct > 0) {
            int hubRadius = com.example.aas.config.AASConfig.HUB_BUILD_RADIUS.get();
            double maxHubSq = hubRadius * hubRadius;

            for (com.example.aas.world.AASWorldData.HubInfo hubInfo : data.hubs) {
                if (remainingToDeduct <= 0) break;

                if (hubInfo.dimension != null && !hubInfo.dimension.equals(currentDim)) continue;
                if (hubInfo.pos.distSqr(pos) <= maxHubSq) {
                    if (level.isLoaded(hubInfo.pos)) {
                        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(hubInfo.pos);
                        if (be instanceof com.example.aas.block.HubBlockEntity hub) {
                            if (hub.getTeam().equalsIgnoreCase(team) || hub.getTeam().equals("NEUTRAL")) {
                                int hMats = hub.getMaterials();
                                int take = Math.min(hMats, remainingToDeduct);

                                hub.consumeMaterials(take);
                                remainingToDeduct -= take;
                            }
                        }
                    }
                }
            }
        }
    }
}