package com.example.aas.block;

import com.example.aas.network.PacketHandler;
import com.example.aas.network.PacketSyncSquads;
import com.example.aas.util.TeamMessageUtil;
import com.example.aas.world.AASWorldData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
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
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import com.example.aas.events.GameLogicEvents;

public class RallyPointBlock extends BaseEntityBlock {

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, ModBlocks.RALLY_BE.get(), (lvl, pos, st, be) -> {
            if (lvl.isClientSide) {
                be.handleSoundClient();
            } else {
                be.checkExpiry(lvl, pos);
            }
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
        return RenderShape.MODEL;
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

    // === Р›РћР“РРљРђ Р РђР—Р‘РћР Рђ РЎР’РћРРњР Р РЈРќРР§РўРћР–Р•РќРРЇ Р’Р РђР“РђРњР ===
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof RallyPointBlockEntity rallyBe) {
                String playerTeam = (player.getTeam() != null) ? player.getTeam().getName().toUpperCase() : "NEUTRAL";
                String rallyTeam = "NEUTRAL";
                if (state.is(ModBlocks.BLUE_RALLY_BLOCK.get())) rallyTeam = "BLUE";
                else if (state.is(ModBlocks.RED_RALLY_BLOCK.get())) rallyTeam = "RED";

                // 1. Р•СЃР»Рё Р»РѕРјР°РµС‚ РЎРћР®Р—РќРРљ (Р Р°Р·Р±РѕСЂ)
                if (rallyTeam.equals(playerTeam)) {
                    rallyBe.wasDismantled = true;
                    rallyBe.setChanged();

                    ServerLevel serverLevel = (ServerLevel) level;
                    AASWorldData data = AASWorldData.get(serverLevel);

                    int penalty = 10;
                    if (rallyTeam.equals("BLUE")) {
                        data.blueTickets = Math.max(0, data.blueTickets - penalty);
                        broadcastMessage(serverLevel, "BLUE", "BLUE player dismantled Friendly Rally Point! (-10 Tickets)", ChatFormatting.BLUE);
                    } else if (rallyTeam.equals("RED")) {
                        data.redTickets = Math.max(0, data.redTickets - penalty);
                        broadcastMessage(serverLevel, "RED", "RED player dismantled Friendly Rally Point! (-10 Tickets)", ChatFormatting.RED);
                    }

                    int squadId = rallyBe.getSquadId();
                    if (squadId != -1) {
                        for (AASWorldData.Squad s : data.squads) {
                            if (s.id == squadId) {
                                s.nextRallyAvailableTick = -1;
                                break;
                            }
                        }
                    }

                    GameLogicEvents.checkSirenManual(serverLevel, data);
                    GameLogicEvents.checkGameOver(serverLevel, data);

                    data.setDirty();
                    PacketHandler.sendToAllClients(serverLevel, data);
                    PacketHandler.INSTANCE.send(net.minecraftforge.network.PacketDistributor.DIMENSION.with(level::dimension), new com.example.aas.network.PacketSyncSquads(data.squads));
                }
                // 2. РќРћР’РћР•: Р•СЃР»Рё Р»РѕРјР°РµС‚ Р’Р РђР“ (РќР°С‡РёСЃР»РµРЅРёРµ РѕС‡РєРѕРІ)
                else if (!rallyTeam.equals("NEUTRAL")) {
                    // Р”Р°РµРј 20 РѕС‡РєРѕРІ Р·Р° СѓРЅРёС‡С‚РѕР¶РµРЅРёРµ РІСЂР°Р¶РµСЃРєРѕРіРѕ СЂР°Р»Р»Рё-РїРѕРёРЅС‚Р°
                    com.example.aas.events.StatsHandler.addStats((net.minecraft.server.level.ServerPlayer) player, 20, 0, "Enemy Rally Destroyed");
                }
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    // === Р›РћР“РРљРђ РЈРќРР§РўРћР–Р•РќРРЇ Р РЁРўР РђР¤РћР’ ===
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);

            if (be instanceof RallyPointBlockEntity rallyBe && !level.isClientSide()) {
                ServerLevel serverLevel = (ServerLevel) level;
                AASWorldData data = AASWorldData.get(serverLevel);

                // Р•СЃР»Рё СЌС‚Рѕ РќР• РµСЃС‚РµСЃС‚РІРµРЅРЅРѕРµ РёСЃС‡РµР·РЅРѕРІРµРЅРёРµ Рё РќР• Р±С‹Р»Рѕ СЂР°Р·РѕР±СЂР°РЅРѕ СЃРІРѕРёРјРё (С‚.Рµ. СЃР»РѕРјР°Р»Рё РІСЂР°РіРё РёР»Рё РІР·РѕСЂРІР°Р»РѕСЃСЊ)
                if (!rallyBe.isDecay && !rallyBe.wasDismantled) {
                    int penalty = 20; // Р’СЂР°РіРё Р»РѕРјР°СЋС‚ Р·Р° 20

                    if (state.getBlock() == ModBlocks.BLUE_RALLY_BLOCK.get()) {
                        data.blueTickets = Math.max(0, data.blueTickets - penalty);
                        broadcastMessage(serverLevel, "BLUE", "BLUE Rally Point Destroyed! (-" + penalty + ")", ChatFormatting.BLUE);
                    }
                    else if (state.getBlock() == ModBlocks.RED_RALLY_BLOCK.get()) {
                        data.redTickets = Math.max(0, data.redTickets - penalty);
                        broadcastMessage(serverLevel, "RED", "RED Rally Point Destroyed! (-" + penalty + ")", ChatFormatting.RED);
                    }

                    GameLogicEvents.checkSirenManual(serverLevel, data);
                    GameLogicEvents.checkGameOver(serverLevel, data);
                }

                // Р’ Р»СЋР±РѕРј СЃР»СѓС‡Р°Рµ С‡РёСЃС‚РёРј РґР°РЅРЅС‹Рµ (СѓРґР°Р»СЏРµРј С‚РѕС‡РєСѓ СЃРїР°РІРЅР° РёР· СЃРїРёСЃРєР° РѕС‚СЂСЏРґР°)
                rallyBe.cleanupData(serverLevel);
                data.setDirty();
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    private void broadcastMessage(ServerLevel level, String ownerTeam, String text, ChatFormatting color) {
        TeamMessageUtil.broadcastDestructionMessage(level, ownerTeam, Component.literal(text).withStyle(color));
    }
}