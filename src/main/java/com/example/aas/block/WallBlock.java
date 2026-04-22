package com.example.aas.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class WallBlock extends BaseEntityBlock {

    public static final BooleanProperty CONSTRUCTED = BooleanProperty.create("constructed");
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty VALID = BooleanProperty.create("valid");

    private static final VoxelShape SHAPE = Shapes.block();

    public WallBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                // ИЗМЕНЕНО: 12.0f - это предел, при котором TNT (сила 4) еще может сломать блок прямым попаданием.
                // Если поставить больше 13, обычный TNT перестанет ломать стену.
                .strength(3.0f, 20.0f)
                .requiresCorrectToolForDrops()
                .noOcclusion());

        this.registerDefaultState(this.stateDefinition.any()
                .setValue(CONSTRUCTED, false)
                .setValue(FACING, Direction.NORTH)
                .setValue(VALID, true));
    }

    // === БЕЗ @OVERRIDE ===

    public float getDestroySpeed(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(CONSTRUCTED) ? 3.0f : 0.3f;
    }

    public float getExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        // ИЗМЕНЕНО: Возвращаем 12.0f (было 9.0f, потом ошибочно 18.0f)
        return 20.0f;
    }

    // =====================

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CONSTRUCTED, FACING, VALID);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (!state.getValue(CONSTRUCTED)) {
            return Shapes.empty();
        }
        return SHAPE;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WallBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return createTickerHelper(type, ModBlocks.WALL_BE.get(), WallBlockEntity::tick);
    }
}