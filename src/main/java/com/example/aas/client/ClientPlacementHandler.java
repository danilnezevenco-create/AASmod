// PATH: src\main\java\com\example\aas\client\ClientPlacementHandler.java
package com.example.aas.client;

import com.example.aas.block.*;
import com.example.aas.config.AASConfig;
import com.example.aas.item.ModItems;
import com.example.aas.network.PacketBuildRequest;
import com.example.aas.network.PacketHandler;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

@Mod.EventBusSubscriber(modid = "aas", value = Dist.CLIENT)
public class ClientPlacementHandler {

    private static boolean isPlacing = false;
    private static int structureId = -1;
    private static float rotationY = 0;

    public static boolean isPlacing() {
        return isPlacing;
    }

    public static void startPlacing(int id) {
        isPlacing = true;
        structureId = id;

        if (structureId >= 20 && structureId <= 23) {
            rotationY = 90;
        } else {
            rotationY = 0;
        }
        Minecraft.getInstance().setScreen(null);
    }

    public static void stopPlacing() {
        isPlacing = false;
        structureId = -1;
    }

    @SubscribeEvent
    public static void onScreenOpen(ScreenEvent.Opening event) {
        if (isPlacing && event.getScreen() instanceof PauseScreen) {
            event.setCanceled(true);
            stopPlacing();
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (!isPlacing || event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        boolean holdingRadio = mc.player.getMainHandItem().getItem() instanceof com.example.aas.item.RallyItem
                || mc.player.getOffhandItem().getItem() instanceof com.example.aas.item.RallyItem;

        if (!holdingRadio) {
            stopPlacing();
            return;
        }

        HitResult hit = mc.hitResult;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return;

        BlockHitResult blockHit = (BlockHitResult) hit;
        BlockPos placePos = blockHit.getBlockPos().relative(blockHit.getDirection());

        boolean isValid = validatePlacement(mc, placePos);

        PoseStack pose = event.getPoseStack();
        pose.pushPose();

        Vec3 camPos = event.getCamera().getPosition();
        pose.translate(placePos.getX() - camPos.x, placePos.getY() - camPos.y, placePos.getZ() - camPos.z);

        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(rotationY));
        pose.translate(-0.5, 0, -0.5);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (isValid) {
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 0.6f);
        } else {
            RenderSystem.setShaderColor(1.0f, 0.5f, 0.5f, 0.6f);
        }

        try {
            if (structureId == 20) {
                BlockState m2State = ModBlocks.M2_CONSTRUCTION_BLOCK.get().defaultBlockState()
                        .setValue(M2ConstructionBlock.FACING, Direction.NORTH)
                        .setValue(M2ConstructionBlock.VALID, isValid);
                renderGhostBlock(mc, m2State, pose, 0, 0, 0);
            }
            else if (structureId == 21) {
                BlockState agsState = ModBlocks.AGS_CONSTRUCTION_BLOCK.get().defaultBlockState()
                        .setValue(AGSConstructionBlock.FACING, Direction.NORTH)
                        .setValue(AGSConstructionBlock.VALID, isValid);
                renderGhostBlock(mc, agsState, pose, 0, 0, 0);
            }
            else if (structureId == 22) {
                BlockState mortarState = ModBlocks.MORTAR_CONSTRUCTION_BLOCK.get().defaultBlockState()
                        .setValue(MortarConstructionBlock.FACING, Direction.NORTH)
                        .setValue(MortarConstructionBlock.VALID, isValid);
                renderGhostBlock(mc, mortarState, pose, 0, 0, 0);
            }
            else if (structureId == 23) {
                BlockState towState = ModBlocks.TOW_CONSTRUCTION_BLOCK.get().defaultBlockState()
                        .setValue(TOWConstructionBlock.FACING, Direction.NORTH)
                        .setValue(TOWConstructionBlock.VALID, isValid);
                renderGhostBlock(mc, towState, pose, 0, 0, 0);
            }
            else if (structureId == 13) {
                BlockState wireState = ModBlocks.BARBED_WIRE_BLOCK.get().defaultBlockState()
                        .setValue(BarbedWireBlock.FACING, Direction.NORTH)
                        .setValue(BarbedWireBlock.CONSTRUCTED, false)
                        .setValue(BarbedWireBlock.VALID, isValid);
                for (int x = 0; x < 3; x++) renderGhostBlock(mc, wireState, pose, x - 1, 0, 0);
            }
            else {
                BlockState wallState = ModBlocks.WALL_BLOCK.get().defaultBlockState()
                        .setValue(WallBlock.FACING, Direction.NORTH)
                        .setValue(WallBlock.CONSTRUCTED, false)
                        .setValue(WallBlock.VALID, isValid);

                BlockState slabState = ModBlocks.WALL_SLAB_BLOCK.get().defaultBlockState()
                        .setValue(WallBlock.FACING, Direction.NORTH)
                        .setValue(WallBlock.CONSTRUCTED, false)
                        .setValue(WallBlock.VALID, isValid);

                BlockState wireState = ModBlocks.BARBED_WIRE_BLOCK.get().defaultBlockState()
                        .setValue(BarbedWireBlock.FACING, Direction.NORTH)
                        .setValue(BarbedWireBlock.CONSTRUCTED, false)
                        .setValue(BarbedWireBlock.VALID, isValid);

                if (structureId == 11) { // 2x2
                    renderGhostBlock(mc, wallState, pose, 0, 0, 0);
                    renderGhostBlock(mc, wallState, pose, 1, 0, 0);
                    renderGhostBlock(mc, wallState, pose, 0, 1, 0);
                    renderGhostBlock(mc, wallState, pose, 1, 1, 0);
                } else if (structureId == 12) { // 3x3
                    for (int x = -1; x <= 1; x++) {
                        for (int y = 0; y < 3; y++) {
                            renderGhostBlock(mc, wallState, pose, x, y, 0);
                        }
                    }
                } else if (structureId == 15) { // Wire Wall (Р РЋРЎвЂљРЎС“Р С—Р ВµР Р…РЎРЉР С”Р В° РЎРѓ Р С”Р С•Р В»РЎР‹РЎвЂЎР С”Р С•Р в„–) 3x2x3
                    for (int x = -1; x <= 1; x++) {
                        // Р вЂ”Р В°Р Т‘Р Р…Р С‘Р в„– РЎР‚РЎРЏР Т‘ (РЎРѓРЎвЂљРЎС“Р С—Р ВµР Р…РЎРЉР С”Р В°, Р С–Р Т‘Р Вµ РЎРѓРЎвЂљР С•Р С‘РЎвЂљ Р С‘Р С–РЎР‚Р С•Р С”)
                        renderGhostBlock(mc, wallState, pose, x, 0, 0);
                        // Р СџР ВµРЎР‚Р ВµР Т‘Р Р…Р С‘Р в„– РЎР‚РЎРЏР Т‘ (РЎРѓРЎвЂљР ВµР Р…Р В° 2 Р В±Р В»Р С•Р С”Р В° Р Р† Р Р†РЎвЂ№РЎРѓР С•РЎвЂљРЎС“, Р Р†Р СР ВµРЎРѓРЎвЂљР С• Р С—РЎР‚Р С•Р Р†Р С•Р В»Р С•Р С”Р С‘)
                        renderGhostBlock(mc, wallState, pose, x, 0, -1);
                        renderGhostBlock(mc, wallState, pose, x, 1, -1);
                        // Р РЋР СР ВµРЎвЂ°Р ВµР Р…Р Р…Р В°РЎРЏ Р С—РЎР‚Р С•Р Р†Р С•Р В»Р С•Р С”Р В° (Р Р†Р С—Р ВµРЎР‚Р ВµР Т‘ Р С” Р Р†РЎР‚Р В°Р С–РЎС“ Р Р…Р В° РЎС“РЎР‚Р С•Р Р†Р ВµР Р…РЎРЉ Р В·Р ВµР СР В»Р С‘)
                        renderGhostBlock(mc, wireState, pose, x, 0, -2);
                    }
                } else if (structureId == 16) { // Loophole (Р С’Р СР В±РЎР‚Р В°Р В·РЎС“РЎР‚Р В°) 3x3
                    for (int x = -1; x <= 1; x++) {
                        for (int y = 0; y < 3; y++) {
                            if (x == 0 && y == 1) {
                                renderGhostBlock(mc, slabState, pose, x, y, 0); // Р СџР С•Р В»РЎС“-Р В±Р В»Р С•Р С” Р Р† РЎвЂ Р ВµР Р…РЎвЂљРЎР‚Р Вµ
                            } else {
                                renderGhostBlock(mc, wallState, pose, x, y, 0);
                            }
                        }
                    }
                    // Р вЂ™ Р СР ВµРЎвЂљР С•Р Т‘Р Вµ onRenderLevel Р С”Р В»Р В°РЎРѓРЎРѓР В° ClientPlacementHandler
                } else if (structureId == 17) { // Bunker Ghost
                    BlockState wall = ModBlocks.WALL_BLOCK.get().defaultBlockState().setValue(WallBlock.VALID, isValid);
                    BlockState slab = ModBlocks.WALL_SLAB_BLOCK.get().defaultBlockState().setValue(WallBlock.VALID, isValid);
                    BlockState net = ModBlocks.CAMO_NET_BLOCK.get().defaultBlockState();

                    // Р вЂ™РЎвЂ№РЎвЂЎР С‘РЎРѓР В»РЎРЏР ВµР С Р Р…Р В°Р С—РЎР‚Р В°Р Р†Р В»Р ВµР Р…Р С‘Р Вµ Р Р…Р В° Р С•РЎРѓР Р…Р С•Р Р†Р Вµ РЎвЂљР ВµР С”РЎС“РЎвЂ°Р ВµР С–Р С• Р С—Р С•Р Р†Р С•РЎР‚Р С•РЎвЂљР В° Р С—РЎР‚Р ВµР Р†РЎРЉРЎР‹
                    // rotationY Р СР ВµР Р…РЎРЏР ВµРЎвЂљРЎРѓРЎРЏ Р Р…Р В° 90 Р С—РЎР‚Р С‘ Р С”Р В»Р С‘Р С”Р Вµ. 0 = North, -90 = East, -180 = South, -270 = West
                    int angle = (int) rotationY % 360;
                    if (angle < 0) angle += 360;

                    Direction currentFacing = Direction.NORTH;
                    if (angle == 90 || angle == 270) currentFacing = Direction.EAST; // Р Р€Р С—РЎР‚Р С•РЎвЂ°Р ВµР Р…Р Р…Р С• Р Т‘Р В»РЎРЏ Р С—РЎР‚Р С‘Р В·РЎР‚Р В°Р С”Р В°
                    if (angle == 180) currentFacing = Direction.SOUTH;

                    for (int y = 0; y <= 2; y++) {
                        for (int x = -1; x <= 1; x++) {
                            for (int z = -1; z <= 1; z++) {
                                BlockPos ghostTarget = new BlockPos(x, y, z);

                                // Р С™РЎР‚РЎвЂ№РЎв‚¬Р В° - Р Р†РЎРѓР ВµР С–Р Т‘Р В° Р С—Р С•Р В»РЎС“Р В±Р В»Р С•Р С”
                                if (y == 2) {
                                    renderGhostBlock(mc, slab, pose, x, y, z);
                                } else {
                                    if (x == 0 && z == 0) continue;

                                    // Р вЂ™РЎвЂ¦Р С•Р Т‘ РЎвЂљР С•Р В»РЎРЉР С”Р С• Р Р…Р В° x=0, z=-1 (Р С•РЎвЂљР Р…Р С•РЎРѓР С‘РЎвЂљР ВµР В»РЎРЉР Р…Р С• Р Р†РЎР‚Р В°РЎвЂ°Р ВµР Р…Р С‘РЎРЏ pose)
                                    // Р СћР В°Р С” Р С”Р В°Р С” pose РЎС“Р В¶Р Вµ Р Р†РЎР‚Р В°РЎвЂ°Р В°Р ВµРЎвЂљРЎРѓРЎРЏ РЎвЂЎР ВµРЎР‚Р ВµР В· pose.mulPose(Axis.YP.rotationDegrees(rotationY)),
                                    // Р Р…Р В°Р С Р Т‘Р С•РЎРѓРЎвЂљР В°РЎвЂљР С•РЎвЂЎР Р…Р С• РЎР‚Р С‘РЎРѓР С•Р Р†Р В°РЎвЂљРЎРЉ Р Р†РЎвЂ¦Р С•Р Т‘ Р Р†РЎРѓР ВµР С–Р Т‘Р В° Р Р† Р С•Р Т‘Р Р…Р С•Р в„– РЎвЂљР С•РЎвЂЎР С”Р Вµ, Р С•Р Р… Р С—Р С•Р Р†Р ВµРЎР‚Р Р…Р ВµРЎвЂљРЎРѓРЎРЏ РЎРѓР В°Р С!
                                    if (x == 0 && z == -1 && y < 2) {
                                        renderGhostBlock(mc, net, pose, x, y, z);
                                    } else {
                                        renderGhostBlock(mc, wall, pose, x, y, z);
                                    }
                                }
                            }
                        }
                    }
                } else if (structureId == 18) { // Vehicle Station
                    BlockState vsState = ModBlocks.VEHICLE_STATION_BLOCK.get().defaultBlockState()
                            .setValue(VehicleStationBlock.VALID, isValid);
                    // FACING Р СњР вЂў Р РЋР СћР С’Р вЂ™Р ВР Сљ, Р ВµР С–Р С• Р С”РЎР‚РЎС“РЎвЂљР С‘РЎвЂљ pose.mulPose Р Р…Р С‘Р В¶Р Вµ
                    renderGhostBlock(mc, vsState, pose, 0, 0, 0);
                } else { // 1x1
                    renderGhostBlock(mc, wallState, pose, 0, 0, 0);
                }
            }
        } catch (Exception e) {}

        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        pose.popPose();
    }

    private static void renderGhostBlock(Minecraft mc, BlockState state, PoseStack pose, int xOff, int yOff, int zOff) {
        pose.pushPose();
        pose.translate(xOff, yOff, zOff);
        mc.getBlockRenderer().renderSingleBlock(state, pose, mc.renderBuffers().bufferSource(),
                15728880, OverlayTexture.NO_OVERLAY);
        mc.renderBuffers().bufferSource().endBatch(ItemBlockRenderTypes.getChunkRenderType(state));
        pose.popPose();
    }

    @SubscribeEvent
    public static void onMouseInput(InputEvent.MouseButton.Pre event) {
        if (!isPlacing) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;

        if (event.getAction() == GLFW.GLFW_PRESS) {
            if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                event.setCanceled(true);
                if (structureId >= 20 && structureId <= 23) {
                    return;
                }
                rotationY -= 90;
                if (rotationY <= -360) rotationY = 0;
            }
            else if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                HitResult hit = mc.hitResult;
                if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
                    BlockHitResult blockHit = (BlockHitResult) hit;
                    BlockPos placePos = blockHit.getBlockPos().relative(blockHit.getDirection());

                    if (validatePlacement(mc, placePos)) {
                        PacketHandler.INSTANCE.sendToServer(new PacketBuildRequest(structureId, placePos, (int) rotationY));
                        stopPlacing();
                    } else {
                        mc.player.displayClientMessage(Component.literal("Not enough materials or out of range!").withStyle(net.minecraft.ChatFormatting.RED), true);
                    }
                }
                event.setCanceled(true);
            }
        }
    }

    private static boolean validatePlacement(Minecraft mc, BlockPos pos) {
        if (mc.player == null) return false;
        String playerTeam = "NEUTRAL";
        if (mc.player.getTeam() != null) playerTeam = mc.player.getTeam().getName();

        if (structureId == 14 && AASConfig.HUB_PLACEMENT_REQUIRES_CRATE.get() && !mc.player.isCreative()) {
            double crateRad = 50.0;
            net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(pos).inflate(crateRad);
            java.util.List<com.example.aas.entity.SupplyCrateEntity> nearbyCrates = mc.level.getEntitiesOfClass(com.example.aas.entity.SupplyCrateEntity.class, area);

            final String team = playerTeam;
            boolean hasValidCrate = nearbyCrates.stream().anyMatch(c ->
                    c.getTeamOwner().equals("NEUTRAL") || c.getTeamOwner().equalsIgnoreCase(team));

            if (!hasValidCrate) return false;
        }

        int cost = 5; // Р вЂќР ВµРЎвЂћР С•Р В»РЎвЂљР Р…Р В°РЎРЏ РЎвЂ Р ВµР Р…Р В° (Р Т‘Р В»РЎРЏ 1x1 РЎРѓРЎвЂљР ВµР Р…РЎвЂ№ Р С‘ Р ВµРЎРѓР В»Р С‘ ID Р Р…Р Вµ РЎС“Р С”Р В°Р В·Р В°Р Р… Р Р…Р С‘Р В¶Р Вµ)
        if (structureId == 11) cost = 5; // 2x2 Wall
        if (structureId == 12) cost = 10; // 3x3 Wall
        if (structureId == 13) cost = 15; // Barbed Wire
        if (structureId == 15) cost = 25; // Wire Wall
        if (structureId == 16) cost = 20; // Loophole (Р С’Р СР В±РЎР‚Р В°Р В·РЎС“РЎР‚Р В°)
        if (structureId == 17) cost = 50; // Bunker
        if (structureId == 18) cost = 100; // Vehicle Station
        if (structureId == 20) cost = 100; // M2
        if (structureId == 21) cost = 100; // AGS
        if (structureId == 22) cost = 300; // Mortar
        if (structureId == 23) cost = 200; // TOW

        if (playerTeam.equals("NEUTRAL") && !mc.player.isCreative()) return false;

        String currentDimension = mc.level.dimension().location().toString();

        int hubRadius = AASConfig.HUB_BUILD_RADIUS.get();
        double maxDistSqHub = hubRadius * hubRadius;

        int crateRadius = AASConfig.CRATE_BUILD_RADIUS.get();
        double maxDistSqCrate = crateRadius * crateRadius;

        int totalMaterials = 0;
        boolean isInRange = false;

        if (ClientData.clientHubs != null) {
            for (com.example.aas.world.AASWorldData.HubInfo hubInfo : ClientData.clientHubs) {
                if (hubInfo.dimension != null && !hubInfo.dimension.equals(currentDimension)) {
                    continue;
                }
                BlockPos hubPos = hubInfo.pos;
                if (hubPos.distSqr(pos) <= maxDistSqHub) {
                    if (mc.level.isLoaded(hubPos)) {
                        BlockEntity be = mc.level.getBlockEntity(hubPos);
                        BlockState hubState = mc.level.getBlockState(hubPos);
                        if (be instanceof HubBlockEntity hub) {
                            if (hub.getTeam().equalsIgnoreCase(playerTeam) || hub.getTeam().equals("NEUTRAL")) {
                                if (hubState.hasProperty(HubBlock.CONSTRUCTED) && !hubState.getValue(HubBlock.CONSTRUCTED)) continue;
                                isInRange = true;
                                totalMaterials += hub.getMaterials();
                            }
                        }
                    }
                }
            }
        }

        net.minecraft.world.phys.AABB searchArea = new net.minecraft.world.phys.AABB(pos).inflate(crateRadius);
        java.util.List<com.example.aas.entity.SupplyCrateEntity> crates = mc.level.getEntitiesOfClass(com.example.aas.entity.SupplyCrateEntity.class, searchArea);

        for (com.example.aas.entity.SupplyCrateEntity crate : crates) {
            if (crate.getTeamOwner().equalsIgnoreCase(playerTeam) || crate.getTeamOwner().equals("NEUTRAL")) {
                if (crate.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= maxDistSqCrate) {
                    isInRange = true;
                    totalMaterials += crate.getMaterials();
                }
            }
        }

        if (!isInRange) return false;
        if (mc.player.isCreative()) return true;

        return totalMaterials >= cost;
    }

    // ================= Р”РѕСЃС‚СѓРїРЅРѕСЃС‚СЊ РїРѕСЃС‚СЂРѕР№РєРё РґР»СЏ СЂР°РґРёР°Р»СЊРЅРѕРіРѕ РјРµРЅСЋ (Р±РµР· РЅР°РІРµРґРµРЅРёСЏ РЅР° Р±Р»РѕРє) =================

    /**
     * РЎС‚РѕРёРјРѕСЃС‚СЊ РїРѕСЃС‚СЂРѕР№РєРё РїРѕ structureId. РўР° Р¶Рµ С‚Р°Р±Р»РёС†Р°, С‡С‚Рѕ Рё РІ validatePlacement,
     * РІС‹РЅРµСЃРµРЅР° РѕС‚РґРµР»СЊРЅРѕ, С‡С‚РѕР±С‹ РµС‘ РјРѕР¶РЅРѕ Р±С‹Р»Рѕ РїРµСЂРµРёСЃРїРѕР»СЊР·РѕРІР°С‚СЊ РёР· СЂР°РґРёР°Р»СЊРЅРѕРіРѕ РјРµРЅСЋ.
     */
    public static int getCost(int structureId) {
        int cost = 5; // РџРѕ СѓРјРѕР»С‡Р°РЅРёСЋ (1x1 СЃС‚РµРЅР°)
        if (structureId == 11) cost = 5;   // 2x2 Wall
        if (structureId == 12) cost = 10;  // 3x3 Wall
        if (structureId == 13) cost = 15;  // Barbed Wire
        if (structureId == 15) cost = 25;  // Wire Wall
        if (structureId == 16) cost = 20;  // Loophole
        if (structureId == 17) cost = 50;  // Bunker
        if (structureId == 18) cost = 100; // Vehicle Station
        if (structureId == 20) cost = 100; // M2
        if (structureId == 21) cost = 100; // AGS
        if (structureId == 22) cost = 300; // Mortar
        if (structureId == 23) cost = 200; // TOW
        return cost;
    }

    /**
     * РЎСѓРјРјР° РјР°С‚РµСЂРёР°Р»РѕРІ РёР· РІСЃРµС… HUB/СЏС‰РёРєРѕРІ СЃРЅР°Р±Р¶РµРЅРёСЏ СЃРІРѕРµР№ РєРѕРјР°РЅРґС‹, РЅР°С…РѕРґСЏС‰РёС…СЃСЏ РІ СЂР°РґРёСѓСЃРµ
     * РїРѕСЃС‚СЂРѕР№РєРё РћРў РўР•РљРЈР©Р•Р™ РџРћР—РР¦РР РР“Р РћРљРђ (Р° РЅРµ РѕС‚ С‚РѕС‡РєРё РЅР°РІРµРґРµРЅРёСЏ РєСѓСЂСЃРѕСЂР°, РєР°Рє РІ validatePlacement вЂ”
     * СЌС‚РѕС‚ РјРµС‚РѕРґ РЅСѓР¶РµРЅ РґР»СЏ СЂР°РґРёР°Р»СЊРЅРѕРіРѕ РјРµРЅСЋ, РєРѕС‚РѕСЂРѕРµ РѕС‚РєСЂС‹РІР°РµС‚СЃСЏ Р”Рћ С‚РѕРіРѕ, РєР°Рє РёРіСЂРѕРє РЅР°С‡Р°Р» С†РµР»РёС‚СЊСЃСЏ).
     * Р’РѕР·РІСЂР°С‰Р°РµС‚ -1, РµСЃР»Рё РёРіСЂРѕРє РІРѕРѕР±С‰Рµ РЅРµ РІ СЂР°РґРёСѓСЃРµ РґРµР№СЃС‚РІРёСЏ РЅРё РѕРґРЅРѕРіРѕ РёСЃС‚РѕС‡РЅРёРєР° РјР°С‚РµСЂРёР°Р»РѕРІ.
     */
    public static int getAvailableMaterials() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return -1;

        String playerTeam = "NEUTRAL";
        if (mc.player.getTeam() != null) playerTeam = mc.player.getTeam().getName();
        if (playerTeam.equals("NEUTRAL") && !mc.player.isCreative()) return -1;

        BlockPos pos = mc.player.blockPosition();
        String currentDimension = mc.level.dimension().location().toString();

        int hubRadius = AASConfig.HUB_BUILD_RADIUS.get();
        double maxDistSqHub = (double) hubRadius * hubRadius;
        int crateRadius = AASConfig.CRATE_BUILD_RADIUS.get();
        double maxDistSqCrate = (double) crateRadius * crateRadius;

        int totalMaterials = 0;
        boolean isInRange = false;

        if (ClientData.clientHubs != null) {
            for (com.example.aas.world.AASWorldData.HubInfo hubInfo : ClientData.clientHubs) {
                if (hubInfo.dimension != null && !hubInfo.dimension.equals(currentDimension)) {
                    continue;
                }
                BlockPos hubPos = hubInfo.pos;
                if (hubPos.distSqr(pos) <= maxDistSqHub) {
                    if (mc.level.isLoaded(hubPos)) {
                        BlockEntity be = mc.level.getBlockEntity(hubPos);
                        BlockState hubState = mc.level.getBlockState(hubPos);
                        if (be instanceof HubBlockEntity hub) {
                            if (hub.getTeam().equalsIgnoreCase(playerTeam) || hub.getTeam().equals("NEUTRAL")) {
                                if (hubState.hasProperty(HubBlock.CONSTRUCTED) && !hubState.getValue(HubBlock.CONSTRUCTED)) continue;
                                isInRange = true;
                                totalMaterials += hub.getMaterials();
                            }
                        }
                    }
                }
            }
        }

        net.minecraft.world.phys.AABB searchArea = new net.minecraft.world.phys.AABB(pos).inflate(crateRadius);
        java.util.List<com.example.aas.entity.SupplyCrateEntity> crates =
                mc.level.getEntitiesOfClass(com.example.aas.entity.SupplyCrateEntity.class, searchArea);

        for (com.example.aas.entity.SupplyCrateEntity crate : crates) {
            if (crate.getTeamOwner().equalsIgnoreCase(playerTeam) || crate.getTeamOwner().equals("NEUTRAL")) {
                if (crate.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= maxDistSqCrate) {
                    isInRange = true;
                    totalMaterials += crate.getMaterials();
                }
            }
        }

        return isInRange ? totalMaterials : -1;
    }

    /**
     * РњРѕР¶РµС‚ Р»Рё РёРіСЂРѕРє РџР РЇРњРћ РЎР•Р™Р§РђРЎ (РїРѕ СЃРІРѕРµР№ С‚РµРєСѓС‰РµР№ РїРѕР·РёС†РёРё, РґРѕ РЅР°РІРµРґРµРЅРёСЏ РЅР° Р±Р»РѕРє)
     * РїРѕР·РІРѕР»РёС‚СЊ СЃРµР±Рµ РїРѕСЃС‚СЂРѕР№РєСѓ structureId вЂ” СЃС‚РѕРёС‚ Р»Рё РѕРЅ РІ СЂР°РґРёСѓСЃРµ HUB/СЏС‰РёРєР° Рё С…РІР°С‚Р°РµС‚ Р»Рё РјР°С‚РµСЂРёР°Р»РѕРІ.
     * РСЃРїРѕР»СЊР·СѓРµС‚СЃСЏ СЂР°РґРёР°Р»СЊРЅС‹Рј РјРµРЅСЋ РґР»СЏ РїРѕРґСЃРІРµС‚РєРё РёРєРѕРЅРѕРє (Р±РµР»Р°СЏ/СЃРµСЂР°СЏ).
     */
    public static boolean canAfford(int structureId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null && mc.player.isCreative()) return true;
        int available = getAvailableMaterials();
        return available >= 0 && available >= getCost(structureId);
    }

    /**
     * РњРѕР¶РµС‚ Р»Рё РёРіСЂРѕРє РїРѕСЃС‚Р°РІРёС‚СЊ HUB СЃРѕ СЃРІРѕРµР№ С‚РµРєСѓС‰РµР№ РїРѕР·РёС†РёРё (HUB СЃС‚Р°РІРёС‚СЃСЏ РјРіРЅРѕРІРµРЅРЅРѕ,
     * Р±РµР· РЅР°РІРµРґРµРЅРёСЏ РЅР° Р±Р»РѕРє, СЃРј. PacketRadioAction actionId == 14). РСЃРїРѕР»СЊР·СѓРµС‚СЃСЏ РЅРѕРІС‹Рј
     * СЌРєСЂР°РЅРѕРј РїРѕСЃС‚СЂРѕР№РєРё (DefenseRadialScreen), С‡С‚РѕР±С‹ Р·Р°СЂР°РЅРµРµ РїРѕРєР°Р·Р°С‚СЊ РёРєРѕРЅРєСѓ HUB СЃРµСЂРѕР№,
     * РµСЃР»Рё СЃРµСЂРІРµСЂ РІСЃС‘ СЂР°РІРЅРѕ РѕС‚РєР°Р¶РµС‚. Р›РѕРіРёРєР° С‚РѕС‡РЅРѕ РїРѕРІС‚РѕСЂСЏРµС‚ РїСЂРѕРІРµСЂРєРё РЅР° СЃРµСЂРІРµСЂРµ:
     *   1) РґРёСЃС‚Р°РЅС†РёСЏ РґРѕ СЃРІРѕРµРіРѕ Р¶Рµ HUB'Р° РЅРµ РјРµРЅСЊС€Рµ AASConfig.MIN_HUB_DISTANCE (РґРµР№СЃС‚РІСѓРµС‚ РґР°Р¶Рµ РІ РєСЂРёР°С‚РёРІРµ);
     *   2) РµСЃР»Рё РІРєР»СЋС‡С‘РЅ AASConfig.HUB_PLACEMENT_REQUIRES_CRATE вЂ” СЂСЏРґРѕРј (50 Р±Р»РѕРєРѕРІ) РґРѕР»Р¶РµРЅ
     *      Р±С‹С‚СЊ СЃРІРѕР№/РЅРµР№С‚СЂР°Р»СЊРЅС‹Р№ СЏС‰РёРє СЃРЅР°Р±Р¶РµРЅРёСЏ (РЅРµ РїСЂРѕРІРµСЂСЏРµС‚СЃСЏ РІ РєСЂРёР°С‚РёРІРµ).
     * РџСЂРѕРІРµСЂРєР° Р»РёРјРёС‚Р° РєРѕР»РёС‡РµСЃС‚РІР° HUB'РѕРІ Р·РґРµСЃСЊ РќР• РґРµР»Р°РµС‚СЃСЏ вЂ” РєР»РёРµРЅС‚ РЅРµ Р·РЅР°РµС‚ maxBlueHubs/maxRedHubs.
     */
    public static boolean canBuildHub() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;

        String playerTeam = (mc.player.getTeam() != null)
                ? mc.player.getTeam().getName().toUpperCase()
                : "NEUTRAL";

        BlockPos pos = mc.player.blockPosition();
        String currentDimension = mc.level.dimension().location().toString();

        // 1. Р”РёСЃС‚Р°РЅС†РёСЏ РґРѕ СЃРІРѕРµРіРѕ HUB'Р° вЂ” РґРµР№СЃС‚РІСѓРµС‚ РІСЃРµРіРґР°, РґР°Р¶Рµ РІ РєСЂРёР°С‚РёРІРµ (РєР°Рє РЅР° СЃРµСЂРІРµСЂРµ).
        int minDist = AASConfig.MIN_HUB_DISTANCE.get();
        double minDistSq = (double) minDist * minDist;
        if (ClientData.clientHubs != null) {
            for (com.example.aas.world.AASWorldData.HubInfo hubInfo : ClientData.clientHubs) {
                if (hubInfo.dimension != null && !hubInfo.dimension.equals(currentDimension)) continue;
                if (hubInfo.team != null && hubInfo.team.equalsIgnoreCase(playerTeam)
                        && hubInfo.pos.distSqr(pos) < minDistSq) {
                    return false;
                }
            }
        }

        // 2. РЇС‰РёРє СЃРЅР°Р±Р¶РµРЅРёСЏ СЂСЏРґРѕРј, РµСЃР»Рё С‚СЂРµР±СѓРµС‚СЃСЏ РїРѕ РєРѕРЅС„РёРіСѓ вЂ” РІ РєСЂРёР°С‚РёРІРµ РЅРµ РїСЂРѕРІРµСЂСЏРµС‚СЃСЏ.
        if (AASConfig.HUB_PLACEMENT_REQUIRES_CRATE.get() && !mc.player.isCreative()) {
            double crateCheckRad = 50.0;
            net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(pos).inflate(crateCheckRad);
            java.util.List<com.example.aas.entity.SupplyCrateEntity> crates =
                    mc.level.getEntitiesOfClass(com.example.aas.entity.SupplyCrateEntity.class, area);

            final String team = playerTeam;
            boolean hasValidCrate = crates.stream().anyMatch(c ->
                    c.getTeamOwner().equals("NEUTRAL") || c.getTeamOwner().equalsIgnoreCase(team));

            if (!hasValidCrate) return false;
        }

        return true;
    }
}
