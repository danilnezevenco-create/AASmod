package com.example.aas.block;

import com.example.aas.client.ClientHooks;
import com.example.aas.network.PacketHandler;
import com.example.aas.util.TeamMessageUtil;
import com.example.aas.world.AASWorldData;
import net.minecraft.ChatFormatting;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
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
import net.minecraft.world.level.material.FluidState;
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
    public static final IntegerProperty BUILD_STAGE = IntegerProperty.create("build_stage", 0, 2);

    public HubBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(3.0f, 9.0f)
                .noOcclusion());
        this.registerDefaultState(this.stateDefinition.any().setValue(CONSTRUCTED, false).setValue(BUILD_STAGE, 0));
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

    // === РќРћР’РћР•: РћРўРљРђРў РҐРђР‘Рђ РџР Р Р›РћРњРљР• Р’РњР•РЎРўРћ РњР“РќРћР’Р•РќРќРћР“Рћ РЈРќРР§РўРћР–Р•РќРРЇ ===
    // Р¦РµР»С‹Р№ С…Р°Р± (100%) -> СЃР»РѕРј -> РѕС‚РєР°С‚ РґРѕ "С‡РµСЂС‚РµР¶Р°" 70% -> СЃР»РѕРј -> РѕС‚РєР°С‚ РґРѕ 30% ->
    // СЃР»РѕРј -> С…Р°Р± СѓРЅРёС‡С‚РѕР¶Р°РµС‚СЃСЏ РїРѕ-РЅР°СЃС‚РѕСЏС‰РµРјСѓ.
    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof HubBlockEntity hub) {
                boolean constructed = state.getValue(CONSTRUCTED);
                int stage = hub.getDamageStage();

                // Откат в 3 удара: достроенное -> 70% -> 30% -> удаление.
                // Ни разу не достроенный чертёж (stage == 0) откатов не имеет: сразу удаляется.
                if (constructed || stage == 1) {
                    int newStage = constructed ? 1 : 2;
                    int targetPercent = (newStage == 1) ? 70 : 30;
                    int targetProgress = Math.round(HubBlockEntity.MAX_PROGRESS * (targetPercent / 100f));

                    hub.setProgress(targetProgress);
                    hub.setDamageStage(newStage);

                    int newBuildStage = (targetPercent >= 50) ? 2 : 1;
                    BlockState newState = state.setValue(CONSTRUCTED, false).setValue(BUILD_STAGE, newBuildStage);
                    level.setBlock(pos, newState, 3);

                    // Р—РІСѓРє/С‡Р°СЃС‚РёС†С‹ РїРѕР»РѕРјРєРё Р±Р»РѕРєР° Р±РµР· СЂРµР°Р»СЊРЅРѕРіРѕ СѓРґР°Р»РµРЅРёСЏ
                    level.levelEvent(null, 2001, pos, Block.getId(state));

                    // === РќРћР’РћР•: С…Р°Р± Р±РѕР»СЊС€Рµ РЅРµ "РґРѕСЃС‚СЂРѕРµРЅ" РґР»СЏ РёРіСЂС‹ ===
                    // РЎРЅРёРјР°РµРј constructed=true РІ РѕР±С‰РёС… РґР°РЅРЅС‹С… РјРёСЂР°, С‡С‚РѕР±С‹:
                    // - РїСЂРѕРїР°Р»Рё РєСЂСѓРіРё/Р·РѕРЅР° СЃРїР°РІРЅР° РЅР° СЌС‚РѕРј С…Р°Р±Рµ;
                    // - РёРєРѕРЅРєР° РЅР° РєР°СЂС‚Рµ СЃРјРµРЅРёР»Р°СЃСЊ РЅР° РЅРµРґРѕСЃС‚СЂРѕРµРЅРЅСѓСЋ;
                    // - РёРіСЂРѕРєРё Р±РѕР»СЊС€Рµ РЅРµ РјРѕРіР»Рё РЅР° РЅС‘Рј Р·Р°СЃРїР°РІРЅРёС‚СЊСЃСЏ,
                    // РїРѕРєР° РѕРЅ СЃРЅРѕРІР° РЅРµ Р±СѓРґРµС‚ РїРѕР»РЅРѕСЃС‚СЊСЋ РѕС‚СЃС‚СЂРѕРµРЅ.
                    ServerLevel serverLevel = (ServerLevel) level;
                    AASWorldData data = AASWorldData.get(serverLevel);
                    for (AASWorldData.HubInfo h : data.hubs) {
                        if (h.pos.equals(pos)) {
                            h.constructed = false;
                            data.setDirty();
                            PacketHandler.sendToAllClients(serverLevel, data);
                            break;
                        }
                    }

                    return false; // Р±Р»РѕРє РЅРµ СѓРґР°Р»СЏРµС‚СЃСЏ, С‚РѕР»СЊРєРѕ РѕС‚РєР°С‚С‹РІР°РµС‚СЃСЏ РЅР° СЃС‚Р°РґРёСЋ РЅР°Р·Р°Рґ
                }
            }
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    // === Р РђР—Р‘РћР  РЎР’РћРРњР (Dismantle) / Р РђР—Р РЈРЁР•РќРР• Р’Р РђР“РћРњ ===
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            // РЈС‡РёС‚С‹РІР°РµРј РЅРµ С‚РѕР»СЊРєРѕ С‚РµРєСѓС‰РёР№ CONSTRUCTED, РЅРѕ Рё С‚Рѕ, С‡С‚Рѕ С…Р°Р± РєРѕРіРґР°-С‚Рѕ Р±С‹Р»
            // РїРѕР»РЅРѕСЃС‚СЊСЋ РїРѕСЃС‚СЂРѕРµРЅ (РјРѕРі Р±С‹С‚СЊ СѓР¶Рµ РѕС‚РєР°С‡РµРЅ РґРѕ "С‡РµСЂС‚РµР¶Р°" РѕС‚РґРµР»СЊРЅС‹РјРё СЃР»РѕРјР°РјРё).
            if (be instanceof HubBlockEntity hub && (state.getValue(CONSTRUCTED) || hub.wasEverConstructed())) {
                String hubTeam = hub.getTeam();
                String playerTeam = (player.getTeam() != null) ? player.getTeam().getName() : "NEUTRAL";

                // 1. Р•СЃР»Рё Р»РѕРјР°РµС‚ РЎРћР®Р—РќРРљ (Р Р°Р·Р±РѕСЂ)
                if (hubTeam.equalsIgnoreCase(playerTeam)) {
                    hub.wasDismantled = true;
                    hub.setChanged();

                    ServerLevel serverLevel = (ServerLevel) level;
                    AASWorldData data = AASWorldData.get(serverLevel);

                    int penalty = 10;
                    if (hubTeam.equalsIgnoreCase("BLUE")) {
                        data.blueTickets = Math.max(0, data.blueTickets - penalty);
                        broadcastMessage(serverLevel, "BLUE", "BLUE player dismantled Friendly HUB! (-10 Tickets)", ChatFormatting.BLUE);
                    } else if (hubTeam.equalsIgnoreCase("RED")) {
                        data.redTickets = Math.max(0, data.redTickets - penalty);
                        broadcastMessage(serverLevel, "RED", "RED player dismantled Friendly HUB! (-10 Tickets)", ChatFormatting.RED);
                    }

                    GameLogicEvents.checkSirenManual(serverLevel, data);
                    GameLogicEvents.checkGameOver(serverLevel, data);

                    data.setDirty();
                    PacketHandler.sendToAllClients(serverLevel, data);
                }
                // 2. РќРћР’РћР•: Р•СЃР»Рё Р»РѕРјР°РµС‚ Р’Р РђР“ (РќР°С‡РёСЃР»РµРЅРёРµ РѕС‡РєРѕРІ)
                else if (!hubTeam.equals("NEUTRAL")) {
                    // Р”Р°РµРј 30 РѕС‡РєРѕРІ РёРіСЂРѕРєСѓ РІ Team Points Р·Р° СѓРЅРёС‡С‚РѕР¶РµРЅРёРµ РІСЂР°Р¶РµСЃРєРѕРіРѕ РҐРђР‘Р°
                    com.example.aas.events.StatsHandler.addStats((net.minecraft.server.level.ServerPlayer) player, 30, 0, "Enemy HUB Destroyed");
                }
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    // === РЈРќРР§РўРћР–Р•РќРР• (Р’Р—Р Р«Р’/Р’Р РђР“Р) ===
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof HubBlockEntity hub) {
                if (!level.isClientSide) {
                    ServerLevel serverLevel = (ServerLevel) level;
                    AASWorldData data = AASWorldData.get(serverLevel);

                    // РЈРґР°Р»СЏРµРј С…Р°Р± РёР· СЃРїРёСЃРєР° РґР°РЅРЅС‹С… РјРёСЂР°
                    data.hubs.removeIf(h -> h.pos.equals(pos));

                    // РЈС‡РёС‚С‹РІР°РµРј РЅРµ С‚РѕР»СЊРєРѕ С‚РµРєСѓС‰РёР№ CONSTRUCTED, РЅРѕ Рё everConstructed вЂ”
                    // С…Р°Р± РјРѕРі Р±С‹С‚СЊ СѓР¶Рµ РѕС‚РєР°С‡РµРЅ РґРѕ "С‡РµСЂС‚РµР¶Р°" РїСЂРё С„РёРЅР°Р»СЊРЅРѕРј СЃР»РѕРјРµ.
                    if (state.getValue(CONSTRUCTED) || hub.wasEverConstructed()) {
                        String hubTeam = hub.getTeam();
                        if (!hub.wasDismantled && !hubTeam.equals("NEUTRAL")) {
                            int penalty = 30;

                            // РЎРЅРёРјР°РµРј С‚РёРєРµС‚С‹ (РѕРґРёРЅ СЂР°Р·!)
                            if (hubTeam.equalsIgnoreCase("BLUE")) {
                                data.blueTickets = Math.max(0, data.blueTickets - penalty);
                                broadcastMessage(serverLevel, "BLUE", "BLUE HUB Destroyed! (-30 Tickets)", ChatFormatting.BLUE);
                            } else if (hubTeam.equalsIgnoreCase("RED")) {
                                data.redTickets = Math.max(0, data.redTickets - penalty);
                                broadcastMessage(serverLevel, "RED", "RED HUB Destroyed! (-30 Tickets)", ChatFormatting.RED);
                            }
                        }
                    }

                    // Р’С‹Р·С‹РІР°РµРј РїСЂРѕРІРµСЂРєРё СЃРёСЂРµРЅС‹ Рё РєРѕРЅС†Р° РёРіСЂС‹
                    GameLogicEvents.checkSirenManual(serverLevel, data);
                    GameLogicEvents.checkGameOver(serverLevel, data);

                    data.setDirty();
                    // РЎРёРЅС…СЂРѕРЅРёР·РёСЂСѓРµРј РґР°РЅРЅС‹Рµ СЃ РёРіСЂРѕРєР°РјРё
                    PacketHandler.sendToAllClients(serverLevel, data);
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    private void broadcastMessage(ServerLevel level, String ownerTeam, String text, ChatFormatting color) {
        TeamMessageUtil.broadcastDestructionMessage(level, ownerTeam, Component.literal(text).withStyle(color));
    }

    // === РќРћР’РћР•: С…Р°Р± РєСЂРµРїРєРёР№ РЅРµ С‚РѕР»СЊРєРѕ РєРѕРіРґР° РїРѕР»РЅРѕСЃС‚СЊСЋ РїРѕСЃС‚СЂРѕРµРЅ, РЅРѕ Рё РІ СЃРѕСЃС‚РѕСЏРЅРёРё
    // "С‡РµСЂС‚РµР¶Р°" РїРѕСЃР»Рµ РѕС‚РєР°С‚Р° (damageStage > 0). Р›РѕРјР°РµС‚СЃСЏ РјРµРґР»РµРЅРЅРѕ, РїРѕРєР° РЅРµ Р±СѓРґРµС‚
    // РѕРєРѕРЅС‡Р°С‚РµР»СЊРЅРѕ СѓРЅРёС‡С‚РѕР¶РµРЅ. РћР±С‹С‡РЅС‹Р№ (РµС‰С‘ РЅРё СЂР°Р·Сѓ РЅРµ РїРѕСЃС‚СЂРѕРµРЅРЅС‹Р№) С‡РµСЂС‚С‘Р¶ РЅР° СЃС‚Р°РґРёРё
    // СЃС‚СЂРѕР№РєРё РїРѕ-РїСЂРµР¶РЅРµРјСѓ Р»РѕРјР°РµС‚СЃСЏ Р»РµРіРєРѕ, РєР°Рє Рё СЂР°РЅСЊС€Рµ.
    public float getDestroySpeed(BlockState state, BlockGetter level, BlockPos pos) {
        if (state.getValue(CONSTRUCTED)) return 3.0f;

        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof HubBlockEntity hub && hub.getDamageStage() > 0) {
            return 3.0f;
        }
        return 0.3f;
    }

    public float getExplosionResistance(BlockState state, BlockGetter level, BlockPos pos, Explosion explosion) { return 9.0f; }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CONSTRUCTED, BUILD_STAGE);
    }

    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public boolean propagatesSkylightDown(BlockState state, BlockGetter reader, BlockPos pos) { return !state.getValue(CONSTRUCTED); }
    @Override public float getShadeBrightness(BlockState state, BlockGetter worldIn, BlockPos pos) { return state.getValue(CONSTRUCTED) ? 0.2F : 1.0F; }
    @Override public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return !state.getValue(CONSTRUCTED) ? Shapes.empty() : Shapes.block(); }
    @Override public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return Shapes.block(); }
    @Nullable @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new HubBlockEntity(pos, state); }
    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) { return createTickerHelper(type, ModBlocks.HUB_BE.get(), HubBlockEntity::tick); }
}