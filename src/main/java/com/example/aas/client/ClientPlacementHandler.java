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

        boolean holdingRadio = mc.player.getMainHandItem().getItem() == ModItems.SQUAD_LEADER_RADIO.get()
                || mc.player.getOffhandItem().getItem() == ModItems.SQUAD_LEADER_RADIO.get();

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
                for (int x = 0; x < 3; x++) renderGhostBlock(mc, wireState, pose, x, 0, 0);
            }
            else {
                BlockState wallState = ModBlocks.WALL_BLOCK.get().defaultBlockState()
                        .setValue(WallBlock.FACING, Direction.NORTH)
                        .setValue(WallBlock.CONSTRUCTED, false)
                        .setValue(WallBlock.VALID, isValid);

                if (structureId == 11) { // 2x2
                    renderGhostBlock(mc, wallState, pose, 0, 0, 0);
                    renderGhostBlock(mc, wallState, pose, 1, 0, 0);
                    renderGhostBlock(mc, wallState, pose, 0, 1, 0);
                    renderGhostBlock(mc, wallState, pose, 1, 1, 0);
                } else if (structureId == 12) { // 3x3
                    for (int x = 0; x < 3; x++) {
                        for (int y = 0; y < 3; y++) {
                            renderGhostBlock(mc, wallState, pose, x, y, 0);
                        }
                    }
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

        // --- НОВОЕ: Проверка ящика для установки ХАБа (ID 14) ---
        if (structureId == 14 && AASConfig.HUB_PLACEMENT_REQUIRES_CRATE.get() && !mc.player.isCreative()) {
            double crateRad = 50.0;
            net.minecraft.world.phys.AABB area = new net.minecraft.world.phys.AABB(pos).inflate(crateRad);
            java.util.List<com.example.aas.entity.SupplyCrateEntity> nearbyCrates = mc.level.getEntitiesOfClass(com.example.aas.entity.SupplyCrateEntity.class, area);

            final String team = playerTeam;
            boolean hasValidCrate = nearbyCrates.stream().anyMatch(c ->
                    c.getTeamOwner().equals("NEUTRAL") || c.getTeamOwner().equalsIgnoreCase(team));

            if (!hasValidCrate) return false; // Если ящика нет в радиусе 50, подсвечиваем красным
        }
        // -------------------------------------------------------

        int cost = 5;
        if (structureId == 11) cost = 10;
        if (structureId == 12) cost = 15;
        if (structureId == 13) cost = 25;
        if (structureId == 20) cost = 50;
        if (structureId == 21) cost = 50;
        if (structureId == 22) cost = 200;
        if (structureId == 23) cost = 200;

        if (playerTeam.equals("NEUTRAL") && !mc.player.isCreative()) return false;

        String currentDimension = mc.level.dimension().location().toString();

        int hubRadius = AASConfig.HUB_BUILD_RADIUS.get();
        double maxDistSqHub = hubRadius * hubRadius;

        int crateRadius = AASConfig.CRATE_BUILD_RADIUS.get();
        double maxDistSqCrate = crateRadius * crateRadius;

        int totalMaterials = 0;
        boolean isInRange = false;

        // 1. Проверяем Хабы (FOB)
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

        // 2. Проверяем Ящики (Supply Crates)
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

        // ВАЖНО: Радиус соблюдаем ВСЕГДА, даже в креативе!
        if (!isInRange) return false;

        // В креативе (если в радиусе) - разрешаем строить без учета материалов
        if (mc.player.isCreative()) return true;

        // Иначе проверяем количество матов
        return totalMaterials >= cost;
    }
}