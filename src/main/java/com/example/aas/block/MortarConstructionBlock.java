package com.example.aas.block;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
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
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
public class MortarConstructionBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = DirectionProperty.create("facing", Direction.Plane.HORIZONTAL);
    public static final BooleanProperty VALID = BooleanProperty.create("valid");
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);

    public MortarConstructionBlock() {
        super(Properties.of().strength(1.0f).noOcclusion());
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(VALID, true));
    }

    // ... (Методы форм и рендера без изменений) ...
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Shapes.empty(); }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING, VALID); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new MortarConstructionBlockEntity(pos, state); }
    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) { return createTickerHelper(type, ModBlocks.MORTAR_CONSTRUCTION_BE.get(), MortarConstructionBlockEntity::tick); }

    // === ПУНКТ 3: ИСПРАВЛЕННЫЙ МЕТОД СПАВНА ===
    public void finishConstruction(ServerLevel level, BlockPos pos, BlockState state) {
        double x = pos.getX() + 0.5;
        double y = pos.getY();
        double z = pos.getZ() + 0.5;

        Direction facing = state.getValue(FACING);
        float yaw = 0;
        switch (facing) {
            case NORTH: yaw = 180f; break;
            case SOUTH: yaw = 0f;   break;
            case WEST:  yaw = 90f;  break;
            case EAST:  yaw = -90f; break;
        }

        // Вместо команды /summon используем Java API
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("superbwarfare", "mortar"));

        if (type != null) {
            Entity entity = type.create(level);
            if (entity != null) {
                entity.setPos(x, y, z);
                entity.setYRot(yaw);
                // Если у сущности есть отдельный метод для поворота турели, его можно вызвать здесь через рефлексию или каст,
                // но setYRot обычно достаточно для начальной установки.

                level.addFreshEntity(entity);
            } else {
                System.err.println("AAS Mod: Failed to create entity 'superbwarfare:mortar'");
            }
        } else {
            System.err.println("AAS Mod: EntityType 'superbwarfare:mortar' not found!");
        }

        level.removeBlock(pos, false);
    }
}