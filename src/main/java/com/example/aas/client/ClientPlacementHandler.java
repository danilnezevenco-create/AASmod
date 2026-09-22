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
                } else if (structureId == 15) { // Wire Wall (Ступенька с колючкой) 3x2x3
                    for (int x = -1; x <= 1; x++) {
                        // Задний ряд (ступенька, где стоит игрок)
                        renderGhostBlock(mc, wallState, pose, x, 0, 0);
                        // Передний ряд (стена 2 блока в высоту, вместо проволоки)
                        renderGhostBlock(mc, wallState, pose, x, 0, -1);
                        renderGhostBlock(mc, wallState, pose, x, 1, -1);
                        // Смещенная проволока (вперед к врагу на уровень земли)
                        renderGhostBlock(mc, wireState, pose, x, 0, -2);
                    }
                } else if (structureId == 16) { // Loophole (Амбразура) 3x3
                    for (int x = -1; x <= 1; x++) {
                        for (int y = 0; y < 3; y++) {
                            if (x == 0 && y == 1) {
                                renderGhostBlock(mc, slabState, pose, x, y, 0); // Полу-блок в центре
                            } else {
                                renderGhostBlock(mc, wallState, pose, x, y, 0);
                            }
                        }
                    }
                    // В методе onRenderLevel класса ClientPlacementHandler
                } else if (structureId == 17) { // Bunker Ghost
                    BlockState wall = ModBlocks.WALL_BLOCK.get().defaultBlockState().setValue(WallBlock.VALID, isValid);
                    BlockState slab = ModBlocks.WALL_SLAB_BLOCK.get().defaultBlockState().setValue(WallBlock.VALID, isValid);
                    BlockState net = ModBlocks.CAMO_NET_BLOCK.get().defaultBlockState();

                    // Вычисляем направление на основе текущего поворота превью
                    // rotationY меняется на 90 при клике. 0 = North, -90 = East, -180 = South, -270 = West
                    int angle = (int) rotationY % 360;
                    if (angle < 0) angle += 360;

                    Direction currentFacing = Direction.NORTH;
                    if (angle == 90 || angle == 270) currentFacing = Direction.EAST; // Упрощенно для призрака
                    if (angle == 180) currentFacing = Direction.SOUTH;

                    for (int y = 0; y <= 2; y++) {
                        for (int x = -1; x <= 1; x++) {
                            for (int z = -1; z <= 1; z++) {
                                BlockPos ghostTarget = new BlockPos(x, y, z);

                                // Крыша - всегда полублок
                                if (y == 2) {
                                    renderGhostBlock(mc, slab, pose, x, y, z);
                                } else {
                                    if (x == 0 && z == 0) continue;

                                    // Вход только на x=0, z=-1 (относительно вращения pose)
                                    // Так как pose уже вращается через pose.mulPose(Axis.YP.rotationDegrees(rotationY)),
                                    // нам достаточно рисовать вход всегда в одной точке, он повернется сам!
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
                    // FACING НЕ СТАВИМ, его крутит pose.mulPose ниже
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

        int cost = 5; // Дефолтная цена (для 1x1 стены и если ID не указан ниже)
        if (structureId == 11) cost = 5; // 2x2 Wall
        if (structureId == 12) cost = 10; // 3x3 Wall
        if (structureId == 13) cost = 15; // Barbed Wire
        if (structureId == 15) cost = 25; // Wire Wall
        if (structureId == 16) cost = 20; // Loophole (Амбразура)
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
}