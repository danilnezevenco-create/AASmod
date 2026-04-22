// PATH: src\main\java\com\example\aas\block\M2AmmoStackBlock.java
package com.example.aas.block;

import com.example.aas.config.AASConfig;
import com.example.aas.item.M2AmmoItem;
import com.example.aas.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
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
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class M2AmmoStackBlock extends BaseEntityBlock {

    public static final IntegerProperty MAGS = IntegerProperty.create("mags", 1, 4);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    protected static final VoxelShape SHAPE = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 6.0D, 14.0D);

    public M2AmmoStackBlock() {
        super(Properties.of().mapColor(MapColor.METAL).noCollission().strength(0.5F).noOcclusion());
        this.registerDefaultState(this.stateDefinition.any().setValue(MAGS, 1).setValue(FACING, Direction.NORTH));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AmmoStackBlockEntity(pos, state);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof AmmoStackBlockEntity ammoBe) {
                List<Integer> counts = ammoBe.getAmmoCounts();
                for (int ammo : counts) {
                    ItemStack stack = new ItemStack(ModItems.M2_AMMO.get());
                    M2AmmoItem.setAmmo(stack, ammo);
                    Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    private void detonate(Level level, BlockPos pos, BlockState state) {
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof AmmoStackBlockEntity ammoBe) {
                ammoBe.clear();
            }
            int count = state.getValue(MAGS);
            float power = count * 1F;
            level.removeBlock(pos, false);

            // КОНФИГ
            boolean canDestroy = AASConfig.AMMO_STACK_DESTRUCTION.get();
            Level.ExplosionInteraction interaction = canDestroy ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE;

            level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, power, interaction);
        }
    }

    @Override
    public void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
        if (!level.isClientSide && (projectile.isOnFire() || projectile.getDeltaMovement().length() > 0.5)) {
            detonate(level, hit.getBlockPos(), state);
        }
    }

    @Override
    public void onBlockExploded(BlockState state, Level level, BlockPos pos, Explosion explosion) {
        if (!level.isClientSide) {
            detonate(level, pos, state);
        }
        super.onBlockExploded(state, level, pos, explosion);
    }

    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Shapes.empty(); }
    @Override public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) { return Block.canSupportCenter(level, pos.below(), Direction.UP); }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
        if (!state.canSurvive(level, currentPos)) return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, direction, neighborState, level, currentPos, neighborPos);
    }

    @Nullable @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof AmmoStackBlockEntity ammoBe) {
                int ammo = M2AmmoItem.getAmmo(stack);
                ammoBe.addAmmoBox(ammo);
            }
        }
    }

    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(MAGS, FACING); }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack heldItem = player.getItemInHand(hand);
        int count = state.getValue(MAGS);

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof AmmoStackBlockEntity ammoBe)) return InteractionResult.FAIL;

        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                int ammoInside = ammoBe.removeTopAmmoBox();

                ItemStack returnStack = new ItemStack(ModItems.M2_AMMO.get());
                M2AmmoItem.setAmmo(returnStack, ammoInside);

                if (!player.getInventory().add(returnStack)) player.drop(returnStack, false);
                level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1.0F, 1.0F);

                if (count > 1) level.setBlock(pos, state.setValue(MAGS, count - 1), 3);
                else level.removeBlock(pos, false);
            }
            return InteractionResult.SUCCESS;
        }

        if (heldItem.getItem() == ModItems.M2_AMMO.get()) {
            if (count < 4) {
                if (!level.isClientSide) {
                    int ammoInHand = M2AmmoItem.getAmmo(heldItem);
                    ammoBe.addAmmoBox(ammoInHand);

                    if (!player.isCreative()) heldItem.shrink(1);
                    level.setBlock(pos, state.setValue(MAGS, count + 1), 3);
                    level.playSound(null, pos, SoundEvents.METAL_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
                }
                return InteractionResult.SUCCESS;
            } else return InteractionResult.FAIL;
        }
        return InteractionResult.PASS;
    }
}