package com.example.aas.block;
import com.example.aas.entity.AGS30Entity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
public class AGSConstructionBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = DirectionProperty.create("facing", Direction.Plane.HORIZONTAL);
    public static final BooleanProperty VALID = BooleanProperty.create("valid");
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);

    public AGSConstructionBlock() {
        super(Properties.of().strength(1.0f).noOcclusion());
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(VALID, true));
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, VALID);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AGSConstructionBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlocks.AGS_CONSTRUCTION_BE.get(), AGSConstructionBlockEntity::tick);
    }

    // === ЛОГИКА СПАВНА ГОТОВОГО АГС ===
    public void finishConstruction(ServerLevel level, BlockPos pos, BlockState state) {
        AGS30Entity gun = new AGS30Entity(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);

        // Получаем направление, куда смотрел чертеж (призрак)
        Direction facing = state.getValue(FACING);

        // Переводим Direction в угол поворота (yaw) с поворотом на 90 градусов ВПРАВО
        float yaw = 0;
        switch (facing) {
            case NORTH: yaw = -90f; break; // Север (180) -> Право -> Восток (-90)
            case SOUTH: yaw = 90f;  break; // Юг (0) -> Право -> Запад (90)
            case WEST:  yaw = 180f; break; // Запад (90) -> Право -> Север (180)
            case EAST:  yaw = 0f;   break; // Восток (-90) -> Право -> Юг (0)
        }

        // 1. Поворачиваем треногу (базу)
        gun.setYRot(yaw);

        // 2. Сбрасываем поворот башни в 0 (относительно базы)
        gun.setTurretYaw(0f);

        gun.setHasMagazine(false); // Спавним пустым

        level.addFreshEntity(gun);
        level.removeBlock(pos, false);
    }
}