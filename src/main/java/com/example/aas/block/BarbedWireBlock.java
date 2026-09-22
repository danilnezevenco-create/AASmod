// PATH: src\main\java\com\example\aas\block\BarbedWireBlock.java
package com.example.aas.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
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
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.material.FluidState;

public class BarbedWireBlock extends BaseEntityBlock {

    public static final BooleanProperty CONSTRUCTED = BooleanProperty.create("constructed");
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty VALID = BooleanProperty.create("valid");
    public static final IntegerProperty BUILD_STAGE = IntegerProperty.create("build_stage", 0, 2);

    private static final VoxelShape SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 15.0D, 15.0D);

    public BarbedWireBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(3.0f, 9.0f)
                .noOcclusion());
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(CONSTRUCTED, false)
                .setValue(BUILD_STAGE, 0)
                .setValue(FACING, Direction.NORTH)
                .setValue(VALID, true));
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (state.getValue(CONSTRUCTED)) {
            entity.makeStuckInBlock(state, new Vec3(0.25D, 0.05D, 0.25D));
            if (!level.isClientSide && entity instanceof LivingEntity) {
                if (level.getGameTime() % 20 == 0) {
                    entity.hurt(level.damageSources().cactus(), 3F);
                }
            }
        }
    }
    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof BarbedWireBlockEntity wire) {
                boolean constructed = state.getValue(CONSTRUCTED);
                int stage = wire.getDamageStage();

                // Откат в 3 удара: достроенное -> 70% -> 30% -> удаление.
                // Ни разу не достроенный чертёж (stage == 0) откатов не имеет: сразу удаляется.
                if (constructed || stage == 1) {
                    int newStage = constructed ? 1 : 2;
                    int targetPercent = (newStage == 1) ? 70 : 30;
                    int targetProgress = Math.round(BarbedWireBlockEntity.MAX_PROGRESS * (targetPercent / 100f));

                    wire.setProgress(targetProgress);
                    wire.setDamageStage(newStage);

                    int newBuildStage = (targetPercent >= 50) ? 2 : 1;
                    BlockState newState = state.setValue(CONSTRUCTED, false).setValue(BUILD_STAGE, newBuildStage);
                    level.setBlock(pos, newState, 3);

                    level.levelEvent(null, 2001, pos, Block.getId(state));

                    return false;
                }
            }
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }
    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    public float getDestroySpeed(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(CONSTRUCTED) ? 3.0f : 0.3f;
    }

    public float getExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) {
        return 9.0f;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter reader, BlockPos pos) {
        return true;
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter worldIn, BlockPos pos) {
        return 1.0F;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CONSTRUCTED, BUILD_STAGE, FACING, VALID);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BarbedWireBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return createTickerHelper(type, ModBlocks.WIRE_BE.get(), BarbedWireBlockEntity::tick);
    }
}