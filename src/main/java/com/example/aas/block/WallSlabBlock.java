package com.example.aas.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class WallSlabBlock extends WallBlock {

    // Форма полублока (нижняя половина)
    private static final VoxelShape SLAB_SHAPE = Block.box(0, 0, 0, 16, 8, 16);

    public WallSlabBlock() {
        super(); // Наследуем все свойства обычной стены (включая хитбоксы при постройке)
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SLAB_SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (!state.getValue(CONSTRUCTED)) {
            return Shapes.empty(); // Проходимо на этапе чертежа
        }
        return SLAB_SHAPE; // Твердый полублок после постройки
    }

}