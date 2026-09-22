package com.example.aas.block;

import com.example.aas.network.PacketHandler;
import com.example.aas.world.AASWorldData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class VehicleStationBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty CONSTRUCTED = BooleanProperty.create("constructed");
    public static final IntegerProperty BUILD_STAGE = IntegerProperty.create("build_stage", 0, 2);
    public static final BooleanProperty VALID = BooleanProperty.create("valid");

    public VehicleStationBlock() {
        super(Properties.of().strength(3.0f, 20.0f).noOcclusion());
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(CONSTRUCTED, false)
                .setValue(BUILD_STAGE, 0)
                .setValue(FACING, Direction.NORTH)
                .setValue(VALID, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CONSTRUCTED, BUILD_STAGE, FACING, VALID);
    }

    @Override
    public RenderShape getRenderShape(BlockState s) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block(); // Полный блок для отрисовки
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        // Если не построено — коллизии нет (можно проходить сквозь чертеж)
        return state.getValue(CONSTRUCTED) ? Shapes.block() : Shapes.empty();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos p, BlockState s) {
        return new VehicleStationBlockEntity(p, s);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level l, BlockState s, BlockEntityType<T> t) {
        return createTickerHelper(t, ModBlocks.VEHICLE_STATION_BE.get(), VehicleStationBlockEntity::tick);
    }
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            AASWorldData data = AASWorldData.get((ServerLevel) level);
            data.vehicleStations.removeIf(s -> s.pos.equals(pos));
            data.setDirty();
            PacketHandler.sendToAllClients((ServerLevel)level, data);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}