package com.example.aas.network;

import com.example.aas.block.BarbedWireBlock;
import com.example.aas.block.ModBlocks;
import com.example.aas.block.WallBlock;
import com.example.aas.block.WallBlockEntity;
import com.example.aas.block.BarbedWireBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public class StructureBuilder {

    public static void build(ServerPlayer player, ServerLevel level, int structureId, BlockPos origin, int rotationY) {
        String team = player.getTeam() != null ? player.getTeam().getName() : "NEUTRAL";
        Direction facing = Direction.fromYRot(rotationY).getOpposite(); // Направление стены

        // Определяем вектора "вправо" и "назад" в зависимости от того, куда смотрит игрок
        Direction right = facing.getCounterClockWise();
        Direction back = facing.getOpposite();

        List<BlockPos> wallPositions = new ArrayList<>();
        List<BlockPos> wirePositions = new ArrayList<>();

        if (structureId == 10) { // 1x1
            placeWall(level, origin, facing, team, false, wallPositions);
        } else if (structureId == 11) { // 2x2
            placeWall(level, getOffset(origin, right, back, 0, 0, 0), facing, team, false, wallPositions);
            placeWall(level, getOffset(origin, right, back, 1, 0, 0), facing, team, false, wallPositions);
            placeWall(level, getOffset(origin, right, back, 0, 1, 0), facing, team, false, wallPositions);
            placeWall(level, getOffset(origin, right, back, 1, 1, 0), facing, team, false, wallPositions);
        } else if (structureId == 12) { // 3x3
            for (int x = -1; x <= 1; x++) {
                for (int y = 0; y < 3; y++) {
                    placeWall(level, getOffset(origin, right, back, x, y, 0), facing, team, false, wallPositions);
                }
            }
        } else if (structureId == 13) { // 1x3 Wire
            for (int x = -1; x <= 1; x++) {
                placeWire(level, getOffset(origin, right, back, x, 0, 0), facing, team, wirePositions);
            }
        } else if (structureId == 15) { // Ступенька с колючкой (Wire Wall)
            for (int x = -1; x <= 1; x++) {
                // Передний ряд (ближе к врагу, -Z от игрока)
                BlockPos frontBase = getOffset(origin, right, back, x, 0, -1);
                placeWall(level, frontBase, facing, team, false, wallPositions);
                placeWire(level, frontBase.above(), facing, team, wirePositions);

                // Задний ряд (ступенька, где стоит игрок)
                placeWall(level, getOffset(origin, right, back, x, 0, 0), facing, team, false, wallPositions);
            }
        } else if (structureId == 16) { // Амбразура (Loophole)
            for (int x = -1; x <= 1; x++) {
                for (int y = 0; y < 3; y++) {
                    BlockPos pos = getOffset(origin, right, back, x, y, 0);
                    if (x == 0 && y == 1) {
                        placeWall(level, pos, facing, team, true, wallPositions); // Слаб в центре
                    } else {
                        placeWall(level, pos, facing, team, false, wallPositions);
                    }
                }
            }
        }

        // Связываем сущности для равномерной раскопки
        for (BlockPos pos : wallPositions) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof WallBlockEntity wall) wall.setLinkedWalls(wallPositions);
        }
        for (BlockPos pos : wirePositions) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof BarbedWireBlockEntity wire) wire.setLinkedWires(wirePositions);
        }
    }

    private static BlockPos getOffset(BlockPos origin, Direction right, Direction back, int x, int y, int z) {
        return origin.offset(
                x * right.getStepX() + z * back.getStepX(),
                y,
                x * right.getStepZ() + z * back.getStepZ()
        );
    }

    private static void placeWall(ServerLevel level, BlockPos pos, Direction facing, String team, boolean isSlab, List<BlockPos> list) {
        if (!level.getBlockState(pos).canBeReplaced() && !level.getBlockState(pos).isAir()) return;
        BlockState state = isSlab ? ModBlocks.WALL_SLAB_BLOCK.get().defaultBlockState() : ModBlocks.WALL_BLOCK.get().defaultBlockState();
        level.setBlock(pos, state.setValue(WallBlock.FACING, facing).setValue(WallBlock.CONSTRUCTED, false), 3);
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof WallBlockEntity wallBe) {
            wallBe.setTeam(team);
            list.add(pos);
        }
    }

    private static void placeWire(ServerLevel level, BlockPos pos, Direction facing, String team, List<BlockPos> list) {
        if (!level.getBlockState(pos).canBeReplaced() && !level.getBlockState(pos).isAir()) return;
        level.setBlock(pos, ModBlocks.BARBED_WIRE_BLOCK.get().defaultBlockState().setValue(BarbedWireBlock.FACING, facing).setValue(BarbedWireBlock.CONSTRUCTED, false), 3);
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof BarbedWireBlockEntity wireBe) {
            wireBe.setTeam(team);
            list.add(pos);
        }
    }
}