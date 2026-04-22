package com.example.aas.block;

import com.example.aas.item.ModItems;
import com.example.aas.network.ResupplyHandler;
import com.example.aas.world.AASWorldData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class AmmoBagBlock extends Block {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    // Размер сумки (немного меньше полного блока)
    protected static final VoxelShape SHAPE = Block.box(3.0D, 0.0D, 3.0D, 13.0D, 7.0D, 13.0D);

    public AmmoBagBlock() {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.WOOL).strength(0.5F).noOcclusion());
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty(); // Можно проходить насквозь
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return Block.canSupportCenter(level, pos.below(), Direction.UP);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
        if (!state.canSurvive(level, currentPos)) return Blocks.AIR.defaultBlockState();
        return super.updateShape(state, direction, neighborState, level, currentPos, neighborPos);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;

        // Shift + ПКМ = забрать сумку в инвентарь (как ящики AGS/M2)
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                ItemStack returnStack = new ItemStack(ModItems.AMMO_BAG.get());
                if (!player.getInventory().add(returnStack)) player.drop(returnStack, false);
                level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1.0F, 1.0F);
                level.removeBlock(pos, false);
            }
            return InteractionResult.SUCCESS;
        }

        // Обычный ПКМ = Пополнение Кита
        if (!level.isClientSide) {
            String kitName = player.getPersistentData().getString("AAS_CurrentKit");
            if (kitName.isEmpty() || kitName.equals("Unassigned")) {
                player.sendSystemMessage(Component.literal("No kit assigned!").withStyle(ChatFormatting.RED));
                return InteractionResult.SUCCESS;
            }

            AASWorldData data = AASWorldData.get((net.minecraft.server.level.ServerLevel) level);
            String t = player.getTeam() != null ? player.getTeam().getName().toUpperCase() : "NEUTRAL";
            AASWorldData.KitInfo kit = t.equals("BLUE") ? data.blueKits.get(kitName) : data.redKits.get(kitName);

            if (kit != null) {
                if (ResupplyHandler.resupplyPlayer((ServerPlayer) player, kit, true)) { // <-- ТУТ true
                    player.sendSystemMessage(Component.literal("Kit Resupplied! (Ammo Bags not refilled)").withStyle(ChatFormatting.GREEN));
                    level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 1f, 1f);
                    level.removeBlock(pos, false); // Уничтожаем сумку после пополнения
                } else {
                    player.sendSystemMessage(Component.literal("Ammo already full!").withStyle(ChatFormatting.YELLOW));
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}