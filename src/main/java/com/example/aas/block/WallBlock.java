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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.FluidState;

public class WallBlock extends BaseEntityBlock {

    public static final BooleanProperty CONSTRUCTED = BooleanProperty.create("constructed");
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty VALID = BooleanProperty.create("valid");
    public static final IntegerProperty BUILD_STAGE = IntegerProperty.create("build_stage", 0, 2);

    private static final VoxelShape SHAPE = Shapes.block();

    public WallBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(3.0f, 20.0f)
                .requiresCorrectToolForDrops()
                .noOcclusion());

        this.registerDefaultState(this.stateDefinition.any()
                .setValue(CONSTRUCTED, false)
                .setValue(BUILD_STAGE, 0)
                .setValue(FACING, Direction.NORTH)
                .setValue(VALID, true));
    }

    // Убран @Override, так как в некоторых маппингах 1.20.1 Forge метод
    // считается "внедренным" (patched), а не стандартным
    public float getDestroySpeed(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(CONSTRUCTED) ? 3.0f : 0.3f;
    }

    // Убран @Override по той же причине
    public float getExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        return 20.0f;
    }
    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof WallBlockEntity wall) {
                boolean constructed = state.getValue(CONSTRUCTED);
                int stage = wall.getDamageStage();

                // Откат в 3 удара: достроенное -> 70% -> 30% -> удаление.
                // Ни разу не достроенный чертёж (stage == 0) откатов не имеет: сразу удаляется.
                if (constructed || stage == 1) {
                    int newStage = constructed ? 1 : 2;
                    int targetPercent = (newStage == 1) ? 70 : 30;
                    int max = wall.getMaxProgress();
                    int targetProgress = Math.round(max * (targetPercent / 100f));

                    wall.setProgress(targetProgress);
                    wall.setDamageStage(newStage);

                    int newBuildStage = (targetPercent >= 50) ? 2 : 1;
                    BlockState newState = state.setValue(CONSTRUCTED, false).setValue(BUILD_STAGE, newBuildStage);
                    level.setBlock(pos, newState, 3);

                    // Звук/частицы поломки без реального удаления блока
                    level.levelEvent(null, 2001, pos, Block.getId(state));

                    return false; // блок не удаляется, только откатывается на стадию назад
                }
            }
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CONSTRUCTED, BUILD_STAGE, FACING, VALID);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(CONSTRUCTED, false)
                .setValue(BUILD_STAGE, 0);
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