// PATH: src\main\java\com\example\aas\block\TOWMissileStackBlock.java
package com.example.aas.block;

import com.example.aas.config.AASConfig; // Импорт
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

public class TOWMissileStackBlock extends Block {

    public static final IntegerProperty STACK = IntegerProperty.create("stack", 1, 4);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    protected static final VoxelShape SHAPE = Block.box(1.0D, 0.0D, 1.0D, 15.0D, 5.0D, 15.0D);

    public TOWMissileStackBlock() {
        super(Properties.of().mapColor(MapColor.METAL).noCollission().strength(0.5F).noOcclusion());
        this.registerDefaultState(this.stateDefinition.any().setValue(STACK, 1).setValue(FACING, Direction.NORTH));
    }

    public static Item getCorrectItem() {
        Item modItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("superbwarfare", "medium_anti_ground_missile"));
        if (modItem != null && modItem != Items.AIR) {
            return modItem;
        }
        return Items.SPECTRAL_ARROW;
    }

    public static boolean isTOWItem(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() == getCorrectItem();
    }

    // === ЛОГИКА ВЗРЫВА ===
    private void detonate(Level level, BlockPos pos, BlockState state) {
        if (!level.isClientSide) {
            int count = state.getValue(STACK);
            float power = count * 3.0F;
            level.removeBlock(pos, false);

            // ПРОВЕРКА КОНФИГА
            boolean canDestroy = AASConfig.AMMO_STACK_DESTRUCTION.get();
            Level.ExplosionInteraction interaction = canDestroy ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE;

            level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, power, interaction);
        }
    }

    @Override
    public void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
        if (!level.isClientSide) {
            if (projectile.isOnFire() || projectile.getDeltaMovement().length() > 0.1) {
                detonate(level, hit.getBlockPos(), state);
            }
        }
    }

    @Override
    public void onBlockExploded(BlockState state, Level level, BlockPos pos, Explosion explosion) {
        if (!level.isClientSide) {
            detonate(level, pos, state);
        }
        super.onBlockExploded(state, level, pos, explosion);
    }

    // ... (Остальной код без изменений)
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Shapes.empty(); }
    @Override public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) { return Block.canSupportCenter(level, pos.below(), Direction.UP); }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
        if (!state.canSurvive(level, currentPos)) return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, direction, neighborState, level, currentPos, neighborPos);
    }

    @Nullable @Override public BlockState getStateForPlacement(BlockPlaceContext context) { return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()); }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(STACK, FACING); }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack heldItem = player.getItemInHand(hand);
        int count = state.getValue(STACK);

        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                ItemStack returnStack = new ItemStack(getCorrectItem());
                if (!player.getInventory().add(returnStack)) player.drop(returnStack, false);

                level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1.0F, 1.0F);

                if (count > 1) level.setBlock(pos, state.setValue(STACK, count - 1), 3);
                else level.removeBlock(pos, false);
            }
            return InteractionResult.SUCCESS;
        }

        if (isTOWItem(heldItem)) {
            if (count < 4) {
                if (!level.isClientSide) {
                    if (!player.isCreative()) heldItem.shrink(1);
                    level.setBlock(pos, state.setValue(STACK, count + 1), 3);
                    level.playSound(null, pos, SoundEvents.METAL_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
                }
                return InteractionResult.SUCCESS;
            } else return InteractionResult.FAIL;
        }
        return InteractionResult.PASS;
    }
}