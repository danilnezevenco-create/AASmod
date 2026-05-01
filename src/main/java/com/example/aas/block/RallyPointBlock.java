package com.example.aas.block;

import com.example.aas.network.PacketHandler;
import com.example.aas.world.AASWorldData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;
import com.example.aas.events.GameLogicEvents;

public class RallyPointBlock extends BaseEntityBlock {

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlocks.RALLY_BE.get(), (lvl, pos, st, be) -> {
            if (lvl.isClientSide) be.handleSoundClient();
        });
    }

    public static final VoxelShape SHAPE = Shapes.block();

    public RallyPointBlock() {
        super(BlockBehaviour.Properties.of()
                .strength(1.0f)
                .noOcclusion()
        );
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL; // <--- ИЗМЕНЕНО
    }
    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RallyPointBlockEntity(pos, state);
    }


    // === ЛОГИКА УНИЧТОЖЕНИЯ И ШТРАФОВ ===
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);

            if (be instanceof RallyPointBlockEntity rallyBe && !level.isClientSide()) {
                ServerLevel serverLevel = (ServerLevel) level;
                AASWorldData data = AASWorldData.get(serverLevel);

                // Если это НЕ естественное исчезновение (значит, сломали враги или игрок)
                if (!rallyBe.isDecay) {
                    int penalty = 20; // ШТРАФ объявляем здесь

                    if (state.getBlock() == ModBlocks.BLUE_RALLY_BLOCK.get()) {
                        data.blueTickets = Math.max(0, data.blueTickets - penalty);
                        broadcastMessage(serverLevel, "BLUE Rally Point Destroyed! (-" + penalty + ")", ChatFormatting.BLUE);
                    }
                    else if (state.getBlock() == ModBlocks.RED_RALLY_BLOCK.get()) {
                        data.redTickets = Math.max(0, data.redTickets - penalty);
                        broadcastMessage(serverLevel, "RED Rally Point Destroyed! (-" + penalty + ")", ChatFormatting.RED);
                    }

                    // Вызываем проверки СРАЗУ после того, как отняли тикеты
                    GameLogicEvents.checkSirenManual(serverLevel, data);
                    GameLogicEvents.checkGameOver(serverLevel, data);
                }

                // В любом случае чистим данные (удаляем точку спавна из списка)
                rallyBe.cleanupData(serverLevel);
                data.setDirty();
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    private void broadcastMessage(ServerLevel level, String text, ChatFormatting color) {
        level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(text).withStyle(color), false);
    }
}