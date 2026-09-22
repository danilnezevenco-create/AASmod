package com.example.aas.block;

import com.example.aas.config.AASConfig;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class AmmoBagBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty USES = IntegerProperty.create("uses", 1, 4);

    // РљРёС‚С‹, РєРѕС‚РѕСЂС‹Рµ С‚СЂР°С‚СЏС‚ СЃСѓРјРєСѓ РїРѕР»РЅРѕСЃС‚СЊСЋ Р·Р° РѕРґРЅРѕ РёСЃРїРѕР»СЊР·РѕРІР°РЅРёРµ, РЅРµР·Р°РІРёСЃРёРјРѕ РѕС‚ С‚РѕРіРѕ,
    // СЃРєРѕР»СЊРєРѕ Р·Р°СЂСЏРґРѕРІ (USES) РІ РЅРµР№ РµС‰С‘ РѕСЃС‚Р°РІР°Р»РѕСЃСЊ.
    private static final String[] INSTANT_CONSUME_KITS = {"Drone Operator", "Sapper"};

    protected static final VoxelShape SHAPE = Block.box(3.0D, 0.0D, 3.0D, 13.0D, 7.0D, 13.0D);

    public AmmoBagBlock() {
        super(Properties.of()
                .mapColor(MapColor.WOOL)
                .sound(net.minecraft.world.level.block.SoundType.WOOL)
                .strength(0.5F)
                .noOcclusion());
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(USES, 4));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL; // РћР±СЏР·Р°С‚РµР»СЊРЅРѕ РґР»СЏ BaseEntityBlock, РёРЅР°С‡Рµ СЃСѓРјРєР° СЃС‚Р°РЅРµС‚ РЅРµРІРёРґРёРјРѕР№
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AmmoBagBlockEntity(pos, state);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
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
        ItemStack stack = context.getItemInHand();
        int usesLeft = 4 - stack.getDamageValue();
        if (usesLeft < 1) usesLeft = 1;
        if (usesLeft > 4) usesLeft = 4;

        return this.defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite())
                .setValue(USES, usesLeft);
    }

    // РЎРѕС…СЂР°РЅСЏРµРј РІР»Р°РґРµР»СЊС†Р° Рё РµРіРѕ РєРѕРјР°РЅРґСѓ РїСЂРё СѓСЃС‚Р°РЅРѕРІРєРµ Р±Р»РѕРєР°
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && placer instanceof Player player) {
            if (level.getBlockEntity(pos) instanceof AmmoBagBlockEntity be) {
                String team = player.getTeam() != null ? player.getTeam().getName().toUpperCase() : "NEUTRAL";
                be.setOwner(player.getUUID(), team);
            }
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, USES);
    }

    private static boolean isInstantConsumeKit(String kitName) {
        if (kitName == null) return false;
        for (String k : INSTANT_CONSUME_KITS) {
            if (k.equalsIgnoreCase(kitName)) return true;
        }
        return false;
    }
    /** true, если игроку разрешено подбирать сумку с патронами по правилу "только Rifleman". */
    public static boolean canPickupByKit(Player player) {
        if (!AASConfig.REQUIRE_RIFLEMAN_FOR_AMMO_BAG.get()) return true;
        if (player.isCreative()) return true;
        String kit = player.getPersistentData().getString("AAS_CurrentKit");
        return "Rifleman".equalsIgnoreCase(kit);
    }
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;

        // Shift + РџРљРњ = Р—РђР‘Р РђРўР¬ РЎРЈРњРљРЈ
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                BlockEntity baseBE = level.getBlockEntity(pos);
                if (baseBE instanceof AmmoBagBlockEntity be) {
                    // РџСЂРѕРІРµСЂРєР° РІР»Р°РґРµР»СЊС†Р°
                    if (be.getOwnerUUID() != null && !be.getOwnerUUID().equals(player.getUUID()) && !player.isCreative()) {
                        player.displayClientMessage(Component.translatable("aas.msg.own_bag_only").withStyle(ChatFormatting.RED), true);
                        return InteractionResult.FAIL;
                    }
                    // НОВОЕ: только Rifleman может забрать сумку
                    if (!canPickupByKit(player)) {
                        player.displayClientMessage(Component.translatable("aas.msg.rifleman_only_bag").withStyle(ChatFormatting.RED), true);
                        return InteractionResult.FAIL;
                    }
                    // РџСЂРѕРІРµСЂРєР° Р»РёРјРёС‚Р° (1 СЃСѓРјРєР° РІ РёРЅРІРµРЅС‚Р°СЂРµ)
                    if (AASConfig.ONE_AMMO_BAG_PER_PLAYER.get() && !player.isCreative()) {
                        boolean hasBag = false;
                        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                            if (player.getInventory().getItem(i).getItem() == ModItems.AMMO_BAG.get()) {
                                hasBag = true;
                                break;
                            }
                        }
                        if (hasBag) {
                            player.displayClientMessage(Component.translatable("aas.msg.one_bag_limit").withStyle(ChatFormatting.RED), true);
                            return InteractionResult.FAIL;
                        }
                    }

                    int uses = state.getValue(USES);
                    ItemStack returnStack = new ItemStack(ModItems.AMMO_BAG.get());
                    returnStack.setDamageValue(4 - uses);

                    if (!player.getInventory().add(returnStack)) player.drop(returnStack, false);
                    level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 1.0F, 1.0F);
                    level.removeBlock(pos, false);
                }
            }
            return InteractionResult.SUCCESS;
        }

        // РћР±С‹С‡РЅС‹Р№ РџРљРњ = РџРѕРїРѕР»РЅРµРЅРёРµ РєРёС‚Р°
        if (!level.isClientSide) {
            String kitName = player.getPersistentData().getString("AAS_CurrentKit");
            if (kitName.isEmpty() || kitName.equals("Unassigned")) {
                player.sendSystemMessage(Component.translatable("aas.msg.no_kit").withStyle(ChatFormatting.RED));
                return InteractionResult.SUCCESS;
            }

            AASWorldData data = AASWorldData.get((net.minecraft.server.level.ServerLevel) level);
            String t = player.getTeam() != null ? player.getTeam().getName().toUpperCase() : "NEUTRAL";
            boolean isAltVariant = player.getPersistentData().getBoolean("AAS_CurrentKitAlt");
            AASWorldData.KitInfo kit = data.getKitVariant(t, kitName, isAltVariant);

            if (kit != null) {
                if (ResupplyHandler.resupplyPlayer((ServerPlayer) player, kit, true)) {
                    player.sendSystemMessage(Component.translatable("aas.msg.kit_resupplied_bag").withStyle(ChatFormatting.GREEN));
                    level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 1f, 1f);

                    // РљРёС‚С‹ "Drone Operator" Рё "Sapper" С‚СЂР°С‚СЏС‚ СЃСѓРјРєСѓ СЃСЂР°Р·Сѓ, Р·Р° РѕРґРЅРѕ РїРѕРїРѕР»РЅРµРЅРёРµ,
                    // РЅРµР·Р°РІРёСЃРёРјРѕ РѕС‚ С‚РѕРіРѕ, СЃРєРѕР»СЊРєРѕ Р·Р°СЂСЏРґРѕРІ (USES) РІ РЅРµР№ РµС‰С‘ Р±С‹Р»Рѕ.
                    boolean instantConsume = isInstantConsumeKit(kitName);
                    int usesLeft = state.getValue(USES);

                    if (instantConsume || usesLeft <= 1) {
                        // Р­С‚Рѕ Р±С‹Р» РїРѕСЃР»РµРґРЅРёР№ Р·Р°СЂСЏРґ (РёР»Рё РєРёС‚ С‚СЂРµР±СѓРµС‚ РјРіРЅРѕРІРµРЅРЅРѕРіРѕ СЂР°СЃС…РѕРґР°) вЂ” СЃСѓРјРєР° РёСЃС‡РµР·Р°РµС‚
                        level.removeBlock(pos, false);
                    } else {
                        // РўСЂР°С‚РёРј РѕРґРёРЅ Р·Р°СЂСЏРґ, СЃСѓРјРєР° РѕСЃС‚Р°С‘С‚СЃСЏ Р»РµР¶Р°С‚СЊ СЃ РѕСЃС‚Р°РІС€РёРјРёСЃСЏ Р·Р°СЂСЏРґР°РјРё
                        level.setBlock(pos, state.setValue(USES, usesLeft - 1), 3);
                    }
                } else {
                    player.sendSystemMessage(Component.translatable("aas.msg.kit_full").withStyle(ChatFormatting.YELLOW));
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}