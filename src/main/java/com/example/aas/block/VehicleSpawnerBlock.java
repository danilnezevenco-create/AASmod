package com.example.aas.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

public class VehicleSpawnerBlock extends BaseEntityBlock {

    // Камуфляж: тип текстуры блока в зависимости от соседних блоков.
    // Сама установка значения происходит один раз в VehicleSpawnerBlockEntity#tick
    // (на самом первом тике после появления блока в мире) — так это работает
    // независимо от того, каким способом блок оказался в мире (обычная установка,
    // структура, команда /setblock и т.д.), в отличие от setPlacedBy.
    public static final EnumProperty<CamoType> CAMO = EnumProperty.create("camo", CamoType.class);

    public enum CamoType implements StringRepresentable {
        NONE, SAND, DIRT, GRASS, SNOW, STONE;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public VehicleSpawnerBlock() {
        super(Properties.copy(Blocks.DROPPER).strength(-1.0F, 3600000.0F)); // Бедрок прочность
        this.registerDefaultState(this.stateDefinition.any().setValue(CAMO, CamoType.NONE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CAMO);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VehicleSpawnerBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) {
            if (player.isCreative()) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof VehicleSpawnerBlockEntity spawner) {
                    NetworkHooks.openScreen((ServerPlayer) player, spawner, pos);
                }
            } else {
                player.displayClientMessage(Component.translatable("aas.msg.creative_only"), true);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return createTickerHelper(type, ModBlocks.VEHICLE_SPAWNER_BE.get(), VehicleSpawnerBlockEntity::tick);
    }

    /**
     * Сканирует блоки вокруг спавнера и определяет, какой камуфляж подходит
     * лучше всего исходя из того, каких блоков вокруг больше всего.
     * Возвращает NONE, если вокруг недостаточно однородного ландшафта.
     */
    public static CamoType computeCamoType(LevelAccessor level, BlockPos pos) {
        final int radiusHorizontal = 3;
        final int radiusVertical = 1;
        final int minMatchesRequired = 6; // порог, чтобы один случайный блок не дёргал текстуру

        Map<CamoType, Integer> counts = new EnumMap<>(CamoType.class);

        for (int dx = -radiusHorizontal; dx <= radiusHorizontal; dx++) {
            for (int dz = -radiusHorizontal; dz <= radiusHorizontal; dz++) {
                for (int dy = -radiusVertical; dy <= radiusVertical; dy++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    BlockPos checkPos = pos.offset(dx, dy, dz);
                    CamoType type = classify(level.getBlockState(checkPos).getBlock());
                    if (type != CamoType.NONE) counts.merge(type, 1, Integer::sum);
                }
            }
        }

        CamoType best = CamoType.NONE;
        int bestCount = 0;
        for (Map.Entry<CamoType, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > bestCount) {
                bestCount = entry.getValue();
                best = entry.getKey();
            }
        }
        return bestCount < minMatchesRequired ? CamoType.NONE : best;
    }

    private static CamoType classify(Block block) {
        if (block == Blocks.SAND || block == Blocks.RED_SAND || block == Blocks.SANDSTONE
                || block == Blocks.RED_SANDSTONE || block == Blocks.SMOOTH_SANDSTONE
                || block == Blocks.CUT_SANDSTONE || block == Blocks.CHISELED_SANDSTONE) return CamoType.SAND;

        if (block == Blocks.DIRT || block == Blocks.COARSE_DIRT || block == Blocks.PODZOL
                || block == Blocks.ROOTED_DIRT || block == Blocks.MUD || block == Blocks.DIRT_PATH
                || block == Blocks.FARMLAND) return CamoType.DIRT;

        if (block == Blocks.GRASS_BLOCK || block == Blocks.MOSS_BLOCK || block == Blocks.GRASS
                || block == Blocks.FERN || block == Blocks.TALL_GRASS || block == Blocks.LARGE_FERN) return CamoType.GRASS;

        if (block == Blocks.SNOW || block == Blocks.SNOW_BLOCK || block == Blocks.POWDER_SNOW
                || block == Blocks.ICE || block == Blocks.PACKED_ICE || block == Blocks.BLUE_ICE) return CamoType.SNOW;

        if (block == Blocks.STONE || block == Blocks.COBBLESTONE || block == Blocks.GRAVEL
                || block == Blocks.ANDESITE || block == Blocks.DIORITE || block == Blocks.GRANITE
                || block == Blocks.DEEPSLATE || block == Blocks.TUFF || block == Blocks.CALCITE) return CamoType.STONE;

        return CamoType.NONE;
    }
}