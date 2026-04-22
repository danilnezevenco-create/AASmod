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
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class BarbedWireBlock extends BaseEntityBlock {

    public static final BooleanProperty CONSTRUCTED = BooleanProperty.create("constructed");
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty VALID = BooleanProperty.create("valid");

    private static final VoxelShape SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 15.0D, 15.0D);

    public BarbedWireBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(3.0f, 9.0f)
                .noOcclusion());
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(CONSTRUCTED, false)
                .setValue(FACING, Direction.NORTH)
                .setValue(VALID, true));
    }

    // === ЛОГИКА УРОНА И ЗАМЕДЛЕНИЯ ===
    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (state.getValue(CONSTRUCTED)) {
            // 1. Замедление (как паутина)
            entity.makeStuckInBlock(state, new Vec3(0.25D, 0.05D, 0.25D));

            // 2. Урон 0.5 HP в секунду
            // Проверяем, что это живое существо и делаем урон раз в секунду (20 тиков)
            if (!level.isClientSide && entity instanceof LivingEntity) {
                // Используем hashCode позиции, чтобы урон не "стакался" мгновенно от разных блоков,
                // но срабатывал ритмично. Или просто проверяем время мира.
                if (level.getGameTime() % 20 == 0) {
                    // Источник урона "cactus" подходит для колючки, или "generic"
                    entity.hurt(level.damageSources().cactus(), 3F);
                }
            }
        }
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty(); // Можно проходить сквозь
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
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CONSTRUCTED, FACING, VALID);
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