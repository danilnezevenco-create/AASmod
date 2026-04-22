package com.example.aas.block;

import com.example.aas.client.ClientHooks;
import com.example.aas.network.PacketHandler;
import com.example.aas.world.AASWorldData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.Nullable;
import com.example.aas.events.GameLogicEvents;

public class HubBlock extends BaseEntityBlock {

    public static final BooleanProperty CONSTRUCTED = BooleanProperty.create("constructed");

    public HubBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(3.0f, 9.0f)
                .noOcclusion());
        this.registerDefaultState(this.stateDefinition.any().setValue(CONSTRUCTED, false));
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!player.getItemInHand(hand).isEmpty()) {
            return InteractionResult.PASS;
        }

        if (state.getValue(CONSTRUCTED)) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof HubBlockEntity hub) {
                if (!player.isCreative()) {
                    String playerTeam = "NEUTRAL";
                    if (player.getTeam() != null) {
                        playerTeam = player.getTeam().getName();
                    }
                    String hubTeam = hub.getTeam();

                    if (!hubTeam.equals("NEUTRAL") && !playerTeam.equalsIgnoreCase(hubTeam)) {
                        if (level.isClientSide) {
                            player.displayClientMessage(Component.literal("Cannot access ENEMY Hub!").withStyle(ChatFormatting.RED), true);
                        }
                        return InteractionResult.FAIL;
                    }
                }

                if (level.isClientSide) {
                    DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHooks.openHubMenu(pos));
                }
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }

    // === РАЗБОР СВОИМИ (Dismantle) ===
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide && state.getValue(CONSTRUCTED)) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof HubBlockEntity hub) {
                String hubTeam = hub.getTeam();
                String playerTeam = (player.getTeam() != null) ? player.getTeam().getName() : "NEUTRAL";

                if (hubTeam.equalsIgnoreCase(playerTeam)) {
                    hub.wasDismantled = true;
                    hub.setChanged();

                    // === ИСПРАВЛЕНИЕ: БЕРЕМ ТЕКУЩИЙ МИР ===
                    ServerLevel serverLevel = (ServerLevel) level;
                    AASWorldData data = AASWorldData.get(serverLevel);
                    // =======================================

                    int penalty = 10;

                    // Сделать (с защитой):
                    if (hubTeam.equalsIgnoreCase("BLUE")) {
                        data.blueTickets = Math.max(0, data.blueTickets - penalty);
                        broadcastMessage(serverLevel, "BLUE player dismantled Friendly FOB! (-10 Tickets)", ChatFormatting.BLUE);
                    } else if (hubTeam.equalsIgnoreCase("RED")) {
                        data.redTickets = Math.max(0, data.redTickets - penalty);
                        broadcastMessage(serverLevel, "RED player dismantled Friendly FOB! (-10 Tickets)", ChatFormatting.RED);
                    }

                    data.setDirty();
                    // Синхронизируем только этот мир
                    PacketHandler.sendToAllClients(serverLevel, data);
                }
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    // === УНИЧТОЖЕНИЕ (ВЗРЫВ/ВРАГИ) ===
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof HubBlockEntity hub) {
                if (!level.isClientSide) {
                    ServerLevel serverLevel = (ServerLevel) level;
                    AASWorldData data = AASWorldData.get(serverLevel);

                    // Удаляем хаб из списка данных мира
                    data.hubs.removeIf(h -> h.pos.equals(pos));

                    if (state.getValue(CONSTRUCTED)) {
                        String hubTeam = hub.getTeam();
                        if (!hub.wasDismantled && !hubTeam.equals("NEUTRAL")) {
                            int penalty = 30;

                            // Снимаем тикеты (один раз!)
                            if (hubTeam.equalsIgnoreCase("BLUE")) {
                                data.blueTickets = Math.max(0, data.blueTickets - penalty);
                                broadcastMessage(serverLevel, "BLUE FOB Destroyed! (-30 Tickets)", ChatFormatting.BLUE);
                            } else if (hubTeam.equalsIgnoreCase("RED")) {
                                data.redTickets = Math.max(0, data.redTickets - penalty);
                                broadcastMessage(serverLevel, "RED FOB Destroyed! (-30 Tickets)", ChatFormatting.RED);
                            }
                        }
                    }

                    // Вызываем проверки сирены и конца игры
                    GameLogicEvents.checkSirenManual(serverLevel, data);
                    GameLogicEvents.checkGameOver(serverLevel, data);

                    data.setDirty();
                    // Синхронизируем данные с игроками
                    PacketHandler.sendToAllClients(serverLevel, data);
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    private void broadcastMessage(ServerLevel level, String text, ChatFormatting color) {
        level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(text).withStyle(color), false);
    }

    public float getDestroySpeed(BlockState state, BlockGetter level, BlockPos pos) { return state.getValue(CONSTRUCTED) ? 3.0f : 0.3f; }
    public float getExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) { return 9.0f; }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(CONSTRUCTED); }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public boolean propagatesSkylightDown(BlockState state, BlockGetter reader, BlockPos pos) { return !state.getValue(CONSTRUCTED); }
    @Override public float getShadeBrightness(BlockState state, BlockGetter worldIn, BlockPos pos) { return state.getValue(CONSTRUCTED) ? 0.2F : 1.0F; }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return !state.getValue(CONSTRUCTED) ? Shapes.empty() : Shapes.block(); }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Shapes.block(); }
    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new HubBlockEntity(pos, state); }
    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) { return createTickerHelper(type, ModBlocks.HUB_BE.get(), HubBlockEntity::tick); }
}