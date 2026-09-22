package com.example.aas.client;

import com.example.aas.client.gui.SquadSelectionScreen;
import com.example.aas.client.gui.TeamSelectionScreen;
import com.example.aas.entity.AGS30Entity;
import com.example.aas.entity.M2BrowningEntity;
import com.example.aas.sound.ModSounds;
import com.example.aas.item.EntrenchingToolItem;
import com.example.aas.item.ModItems;
import net.minecraft.world.level.GameType;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;
import com.example.aas.item.SupplyTruckMarkerItem;
import com.example.aas.item.VehicleMarkerItem;
import com.example.aas.network.*;
import com.example.aas.client.gui.DownedScreen;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.ChatFormatting;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.InputEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import com.example.aas.network.PacketHandler;
import com.example.aas.network.PacketVehicleShoot;
import com.example.aas.client.RecoilHandler;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraft.client.Minecraft;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import com.example.aas.config.AASConfig;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.entity.player.Player;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.Matrix4f;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.resources.language.I18n;

@Mod.EventBusSubscriber(modid = "aas", value = Dist.CLIENT)
public class ClientEvents {
    private static final ResourceLocation PING_TEXTURE = new ResourceLocation("aas", "textures/gui/map_icons/ping_eye.png");
    private static final ResourceLocation MOVE_TEXTURE = new ResourceLocation("aas", "textures/gui/map_icons/marker_move.png");
    private static final ResourceLocation MOVE_TEX_BRAVO = new ResourceLocation("aas", "textures/gui/map_icons/marker_move_bravo.png");
    private static final ResourceLocation PING_TEX_BRAVO = new ResourceLocation("aas", "textures/gui/map_icons/ping_eye_bravo.png");

    private static final ResourceLocation MOVE_TEX_CHARLIE = new ResourceLocation("aas", "textures/gui/map_icons/marker_move_charlie.png");
    private static final ResourceLocation PING_TEX_CHARLIE = new ResourceLocation("aas", "textures/gui/map_icons/ping_eye_charlie.png");
    private static boolean revivingHeld = false;
    private static int reviveTargetId = -1;        // кому в последний раз послали START
    private static int reviveResendCooldown = 0;   // тики до повторного START, если сервер не подтвердил
    private static final int REVIVE_RESEND_TICKS = 10;
    private static final double REVIVE_RANGE = 3.0;
    // Р¦РµР»СЊ, РЅР° РєРѕС‚РѕСЂСѓСЋ РёРіСЂРѕРє СЃРµР№С‡Р°СЃ РЅР°РІРѕРґРёС‚СЃСЏ Рё РјРѕР¶РµС‚ РїРѕРґРЅСЏС‚СЊ (РґР»СЏ РІРёР·СѓР°Р»СЊРЅРѕР№ РїРѕРґСЃРєР°Р·РєРё "F")
    private static Entity currentRevivePromptTarget = null;
    // РњРёРЅРёРјР°Р»СЊРЅС‹Р№ РєРѕСЃРёРЅСѓСЃ СѓРіР»Р° РјРµР¶РґСѓ РЅР°РїСЂР°РІР»РµРЅРёРµРј РІР·РіР»СЏРґР° Рё РЅР°РїСЂР°РІР»РµРЅРёРµРј РЅР° С†РµР»СЊ,
    // С‡С‚РѕР±С‹ СЃС‡РёС‚Р°С‚СЊ, С‡С‚Рѕ РёРіСЂРѕРє "РЅР°РІРµР» РїСЂРёС†РµР» РЅР° СЂР°РЅРµРЅРѕРіРѕ" (~28В°)
    private static final double REVIVE_AIM_DOT_THRESHOLD = 0.88;
    // РџРѕР»СЏ РґР»СЏ РѕС‚СЃР»РµР¶РёРІР°РЅРёСЏ СЃРѕСЃС‚РѕСЏРЅРёСЏ РєР»Р°РІРёС€ СЂР°С†РёРё
    // ==== Подсказки для новичков (команда -> отряд -> снаряжение) ====
    private static boolean teamHintShown = false;
    private static boolean squadHintShown = false;
    private static boolean kitHintShown = false;
    private static boolean prevHasSquad = false;   // для отслеживания момента выхода/кика из отряда
    private static boolean hintsInitialized = false; // чтобы не словить ложное срабатывание на самом первом тике
    private static int hintCheckDelay = 0;

    @SubscribeEvent
    public static void onChatReceived(ClientChatReceivedEvent event) {
        ClientData.addChatMessage(event.getMessage());
    }
    @SubscribeEvent
    public static void onLoggingIn(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingIn event) {
        teamHintShown = false;
        squadHintShown = false;
        kitHintShown = false;
        hintsInitialized = false;
        hintCheckDelay = 40;
        ClientData.voicechatConnected = false;
        ClientData.clientSquads.clear();
        ClientData.HIGHLIGHTED_PLAYERS.clear();
        ClientData.squadPanelExpanded = true;
        ClientData.DOWNED_PLAYERS.clear();
    }

    // РћР‘РЄР•Р”РРќР•РќРќР«Р™ РњР•РўРћР” РўРРљРђ
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Р¤РРљРЎ: РўР°Р№РјРµСЂ СЃР±СЂР°СЃС‹РІР°РµС‚СЃСЏ РўРћР›Р¬РљРћ РµСЃР»Рё РёРіСЂРѕРє Р¶РёРІ, РЅРµ РІ РЅРѕРєР°СѓС‚Рµ Р Сѓ РЅРµРіРѕ РЅРµС‚ РѕС‚РєСЂС‹С‚РѕРіРѕ СЌРєСЂР°РЅР° СЃРјРµСЂС‚Рё
        if (mc.player.isAlive() && !mc.player.getPersistentData().getBoolean("AAS_IsDowned") && !(mc.screen instanceof AASDeathScreen)) {
            if (ClientData.globalDeathTimestamp != 0) {
                // Р”РѕРїРѕР»РЅРёС‚РµР»СЊРЅР°СЏ РїСЂРѕРІРµСЂРєР°: РЅРµ СЃР±СЂР°СЃС‹РІР°РµРј, РµСЃР»Рё РјС‹ РїСЂРѕСЃС‚Рѕ РїРµСЂРµС…РѕРґРёРј РјРµР¶РґСѓ РјРµРЅСЋС€РєР°РјРё AAS
                if (!(mc.screen instanceof com.example.aas.client.gui.PlayerKitSelectScreen) &&
                        !(mc.screen instanceof com.example.aas.client.gui.MapMarkerGridScreen)) {
                    ClientData.globalDeathTimestamp = 0;
                    ClientData.deathFadePlayed = false;
                }
            }
        }

        if (ClientData.voteActive && ClientData.voteTimer > 0) {
            // РљР°Р¶РґС‹Рµ 20 С‚РёРєРѕРІ (1 СЃРµРєСѓРЅРґР°) СѓРјРµРЅСЊС€Р°РµРј РІРёР·СѓР°Р»СЊРЅС‹Р№ С‚Р°Р№РјРµСЂ
            if (mc.level.getGameTime() % 20 == 0) {
                ClientData.voteTimer--;
            }
        }

        // 1. РћС‚РґР°С‡Р° (Recoil)
        RecoilHandler.clientTick();


        // 3. Р›РћР“РРљРђ РЎРўР Р•Р›Р¬Р‘Р« РР— РўР•РҐРќРРљР
        Entity vehicle = mc.player.getVehicle();
        if (vehicle instanceof M2BrowningEntity || vehicle instanceof AGS30Entity) {
            long windowId = mc.getWindow().getWindow();
            boolean isLeftBtnDown = GLFW.glfwGetMouseButton(windowId, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

            if (isLeftBtnDown && mc.screen == null) {
                PacketHandler.INSTANCE.sendToServer(new PacketVehicleShoot());
                while (mc.options.keyAttack.consumeClick()) { }
            }
        }
        // Р”РѕР±Р°РІР»СЏРµРј РїСЂРѕРІРµСЂРєСѓ mc.player.isAlive()
        if (mc.player.getPersistentData().getBoolean("AAS_IsDowned") && mc.player.isAlive()) {
            mc.player.setDeltaMovement(0, mc.player.getDeltaMovement().y, 0);

            if (mc.screen == null) {
                mc.setScreen(new DownedScreen());
            }
        } else {
            if (mc.player.getPersistentData().contains("AAS_DownedYaw")) {
                mc.player.getPersistentData().remove("AAS_DownedYaw");
                mc.player.getPersistentData().remove("AAS_DownedPitch");
            }
        }
        handleReviveHold(mc);

        // РћР±РЅРѕРІР»СЏРµРј С†РµР»СЊ РґР»СЏ РІРёР·СѓР°Р»СЊРЅРѕР№ РїРѕРґСЃРєР°Р·РєРё "Р·Р°Р¶РјРёС‚Рµ F" (РЅР°РІРѕРґРєР° + РЅСѓР¶РЅС‹Р№ РїСЂРµРґРјРµС‚ РІ СЂСѓРєРµ)
        currentRevivePromptTarget = findRevivePromptTarget(mc);
        // ==== Подсказки для новичков ====
        if (hintCheckDelay > 0) {
            hintCheckDelay--;
        } else {
            checkOnboardingHints(mc);
        }
    }



    @SubscribeEvent
    public static void onMovementInput(net.minecraftforge.client.event.MovementInputUpdateEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (mc.player.getPersistentData().getBoolean("AAS_IsDraggingAlly")) {
            if (event.getInput().forwardImpulse > 0) {
                event.getInput().forwardImpulse = 0f;
            }
            // РџСЂРё Р¶РµР»Р°РЅРёРё РјРѕР¶РЅРѕ РѕРіСЂР°РЅРёС‡РёС‚СЊ Рё СЂС‹РІРєРё РІ СЃС‚РѕСЂРѕРЅС‹:
            // event.getInput().leftImpulse *= 0.5f;
        }
    }

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        // Р•СЃР»Рё РєР°СЂС‚Р° РѕС‚РєСЂС‹С‚Р° (РЅР° TAB)
        if (ClientData.isMapOpen && mc.screen == null) {
            double delta = event.getScrollDelta();
            if (delta != 0) {
                // Р’С‹Р·С‹РІР°РµРј С‚РѕС‚ Р¶Рµ СЃР°РјС‹Р№ Р±РµР·РѕРїР°СЃРЅС‹Р№ Р·СѓРј
                ClientData.zoomMap(delta);
                event.setCanceled(true);
            }
        }
    }
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // РњР°СЂРєРµСЂ "Р·Р°Р¶РјРёС‚Рµ F" СЂРёСЃСѓРµРј РЅРµР·Р°РІРёСЃРёРјРѕ РѕС‚ РЅР°Р»РёС‡РёСЏ РѕС‚СЂСЏРґР° (РїРѕСЌС‚РѕРјСѓ РґРѕ РІС‹С…РѕРґР° РЅРёР¶Рµ)
        if (currentRevivePromptTarget != null && currentRevivePromptTarget.isAlive()) {
            renderRevivePrompt(event.getPoseStack(), event.getCamera().getPosition(), currentRevivePromptTarget, mc, event.getPartialTick());
        }

        String myName = mc.player.getScoreboardName();
        com.example.aas.world.AASWorldData.Squad mySquad = null;

        for (com.example.aas.world.AASWorldData.Squad s : com.example.aas.client.ClientData.clientSquads) {
            if (s.members.contains(myName)) {
                mySquad = s;
                break;
            }
        }

        if (mySquad == null) return;

        // РћРїСЂРµРґРµР»СЏРµРј СЂРѕР»СЊ РёРіСЂРѕРєР°
        boolean isSL = mySquad.leader.equals(myName);
        boolean isBravo = mySquad.bravoMembers.contains(myName) || mySquad.bravoLeader.equals(myName);
        boolean isCharlie = mySquad.charlieMembers.contains(myName) || mySquad.charlieLeader.equals(myName);

        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getCamera().getPosition();
        long gameTime = mc.level.getGameTime();
        float blinkAlpha = 0.45f + (float)Math.sin(gameTime * 0.4f) * 0.25f;

        // --- 1. РџРРќР“Р (Р“Р›РђР—Рђ) ---
        // РџРёРЅРі Р»РёРґРµСЂР° РІРёРґСЏС‚ Р’РЎР• РІ РѕС‚СЂСЏРґРµ
        if (mySquad.pingPos != null && gameTime < mySquad.pingExpiry) {
            render3DMarker(poseStack, cameraPos, mySquad.pingPos, PING_TEXTURE, mc, blinkAlpha, 0.5);
        }
        // РџРёРЅРіРё Bravo/Charlie С‚РµРїРµСЂСЊ РІРёРґРёС‚ РІРµСЃСЊ РѕС‚СЂСЏРґ, Р° РЅРµ С‚РѕР»СЊРєРѕ РЎР› Рё СѓС‡Р°СЃС‚РЅРёРєРѕРІ СЃРѕРѕС‚РІРµС‚СЃС‚РІСѓСЋС‰РµРіРѕ С„Р°РµСЂС‚РёРјР°
        if (mySquad.bravoPingPos != null && gameTime < mySquad.bravoPingExpiry) {
            render3DMarker(poseStack, cameraPos, mySquad.bravoPingPos, PING_TEX_BRAVO, mc, blinkAlpha, 0.5);
        }
        if (mySquad.charliePingPos != null && gameTime < mySquad.charliePingExpiry) {
            render3DMarker(poseStack, cameraPos, mySquad.charliePingPos, PING_TEX_CHARLIE, mc, blinkAlpha, 0.5);
        }

        // --- 2. РњР•РўРљР Р”Р’РР–Р•РќРРЇ (MOVE - РўРРџ 0) ---
        // РњС‹ СЂРёСЃСѓРµРј С„РёР·РёС‡РµСЃРєСѓСЋ РјРµС‚РєСѓ РІ РјРёСЂРµ РўРћР›Р¬РљРћ РµСЃР»Рё РµС‘ С‚РёРї == 0 (Move)

        // РњРµС‚РєР° Р»РёРґРµСЂР° (РІРёРґСЏС‚ РІСЃРµ)
        if (mySquad.marker != null && mySquad.marker.type == 0 && gameTime < mySquad.marker.expiryTick) {
            BlockPos mPos = new BlockPos(mySquad.marker.x, mySquad.marker.y, mySquad.marker.z);
            render3DMarker(poseStack, cameraPos, mPos, MOVE_TEXTURE, mc, 1.0f, 0.5);
        }

        // РњРµС‚РєР° Bravo (SL + Bravo)
        if (mySquad.bravoMarker != null && mySquad.bravoMarker.type == 0 && gameTime < mySquad.bravoMarker.expiryTick) {
            BlockPos mPos = new BlockPos(mySquad.bravoMarker.x, mySquad.bravoMarker.y, mySquad.bravoMarker.z);
            render3DMarker(poseStack, cameraPos, mPos, MOVE_TEX_BRAVO, mc, 1.0f, 0.5);
        }

        // РњРµС‚РєР° Charlie (SL + Charlie)
        if (mySquad.charlieMarker != null && mySquad.charlieMarker.type == 0 && gameTime < mySquad.charlieMarker.expiryTick) {
            BlockPos mPos = new BlockPos(mySquad.charlieMarker.x, mySquad.charlieMarker.y, mySquad.charlieMarker.z);
            render3DMarker(poseStack, cameraPos, mPos, MOVE_TEX_CHARLIE, mc, 1.0f, 0.5);
        }
    }
    @SubscribeEvent
    public static void onRenderOverlayPre(RenderGuiOverlayEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Р•СЃР»Рё РїСЂРёС†РµР» (Crosshair) РіРѕС‚РѕРІРёС‚СЃСЏ Рє СЂРµРЅРґРµСЂСѓ
        if (event.getOverlay() == net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.CROSSHAIR.type()) {
            boolean usingBinoculars = mc.player.isUsingItem() && mc.player.getUseItem().getItem() == ModItems.BINOCULARS.get();
            Entity vehicle = mc.player.getVehicle();
            boolean aimingInVehicle = (vehicle instanceof AGS30Entity ags && ags.isAiming()) ||
                    (vehicle instanceof M2BrowningEntity m2 && m2.isAiming());

            if (usingBinoculars || aimingInVehicle) {
                event.setCanceled(true);
            }
        }

        // РќРћР’РћР•: СЃРєСЂС‹РІР°РµРј РІР°РЅРёР»СЊРЅС‹Р№ СЃРїРёСЃРѕРє РёРіСЂРѕРєРѕРІ (TAB) РїРѕРєР° РјР°С‚С‡ РёРґС‘С‚,
        // С‚.Рє. TAB Р·Р°РЅСЏС‚ РїРѕРґ РѕС‚РєСЂС‹С‚РёРµ РєР°СЂС‚С‹
        if (event.getOverlay() == net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.PLAYER_LIST.type()) {
            boolean isCreativeOrSpectator = mc.player.isCreative() || mc.player.isSpectator();

            if (ClientData.isGameStarted && !isCreativeOrSpectator) {
                event.setCanceled(true);
            }
        }
    }
    private static boolean tryPlacePingLogic(Minecraft mc) {
        String myName = mc.player.getScoreboardName();
        boolean canPing = false;

        // РџСЂРѕРІРµСЂСЏРµРј СЂРѕР»СЊ: С‚РѕР»СЊРєРѕ SL РёР»Рё FTL РјРѕРіСѓС‚ СЃС‚Р°РІРёС‚СЊ РјРµС‚РєСѓ
        for (com.example.aas.world.AASWorldData.Squad s : com.example.aas.client.ClientData.clientSquads) {
            if (s.leader.equals(myName) || s.bravoLeader.equals(myName) || s.charlieLeader.equals(myName)) {
                canPing = true;
                break;
            }
        }

        if (canPing) {
            boolean isShift = net.minecraft.client.gui.screens.Screen.hasShiftDown();
            PacketHandler.INSTANCE.sendToServer(new PacketPlacePing(isShift));
            mc.player.playSound(ModSounds.MAP_MARKER_PLACE.get(), 1.0f, 1.0f);
            return true; // РјРµС‚РєСѓ СЂРµР°Р»СЊРЅРѕ РїРѕСЃС‚Р°РІРёР»Рё
        }
        return false; // РЅРµ Р±С‹Р»Рѕ РїСЂР°РІ вЂ” pick-block РЅРµ С‚СЂРѕРіР°РµРј
    }
    /**
     * РЈРЅРёРІРµСЂСЃР°Р»СЊРЅС‹Р№ РІСЃРїРѕРјРѕРіР°С‚РµР»СЊРЅС‹Р№ РјРµС‚РѕРґ РґР»СЏ РѕС‚СЂРёСЃРѕРІРєРё 3D РјР°СЂРєРµСЂР°
     */
    private static void render3DMarker(PoseStack poseStack, Vec3 cameraPos, BlockPos pos, ResourceLocation texture, Minecraft mc, float alpha, double heightOffset) {
        poseStack.pushPose();

        double renderX = pos.getX() + 0.5 - cameraPos.x;
        double renderY = pos.getY() + heightOffset - cameraPos.y;
        double renderZ = pos.getZ() + 0.5 - cameraPos.z;
        poseStack.translate(renderX, renderY, renderZ);

        poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());

        float scale = 0.025f;
        double distSq = mc.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        if (distSq > 100) {
            scale *= (float)(Math.sqrt(distSq) / 10.0);
        }
        scale = Math.min(scale, 0.25f);
        poseStack.scale(-scale, -scale, scale);

        Matrix4f matrix = poseStack.last().pose();

        // Р РµРЅРґРµСЂРёРј С‡РµСЂРµР· Tesselator РЅР°РїСЂСЏРјСѓСЋ вЂ” disableDepthTest СЂР°Р±РѕС‚Р°РµС‚ Р·РґРµСЃСЊ
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();  // С‚РµРїРµСЂСЊ СЂРµР°Р»СЊРЅРѕ СЂР°Р±РѕС‚Р°РµС‚
        com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, texture);
        com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexColorShader);

        com.mojang.blaze3d.vertex.Tesselator tesselator = com.mojang.blaze3d.vertex.Tesselator.getInstance();
        com.mojang.blaze3d.vertex.BufferBuilder buf = tesselator.getBuilder();
        buf.begin(com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR);

        int a = (int)(alpha * 255);
        buf.vertex(matrix, -8, -8, 0).uv(0, 0).color(255, 255, 255, a).endVertex();
        buf.vertex(matrix, -8,  8, 0).uv(0, 1).color(255, 255, 255, a).endVertex();
        buf.vertex(matrix,  8,  8, 0).uv(1, 1).color(255, 255, 255, a).endVertex();
        buf.vertex(matrix,  8, -8, 0).uv(1, 0).color(255, 255, 255, a).endVertex();

        tesselator.end();

        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1, 1, 1, 1);

        poseStack.popPose();
    }

    @SubscribeEvent
    public static void onMouseInput(InputEvent.MouseButton.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        Entity vehicle = mc.player.getVehicle();

        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (vehicle instanceof M2BrowningEntity || vehicle instanceof AGS30Entity) {
                event.setCanceled(true);
            }
        }

        if (ModKeyBindings.PLACE_PING_KEY.matchesMouse(event.getButton()) && event.getAction() == GLFW.GLFW_PRESS) {
            // РњРµС‚РєР° СЂР°Р±РѕС‚Р°РµС‚ РўРћР›Р¬РљРћ РІ С‡РёСЃС‚РѕР№ РёРіСЂРµ: РЅРµ РІ С‡Р°С‚Рµ, РЅРµ РІ РёРЅРІРµРЅС‚Р°СЂРµ, РЅРµ РІ РјРµРЅСЋ вЂ”
            // С‡С‚РѕР±С‹ РЅРёРєРѕРіРґР° РЅРµ РєРѕРЅС„Р»РёРєС‚РѕРІР°С‚СЊ СЃ Pick Block (С‚Р° Р¶Рµ СЃСЂРµРґРЅСЏСЏ РєРЅРѕРїРєР° РјС‹С€Рё)
            if (mc.screen == null && tryPlacePingLogic(mc)) {
                event.setCanceled(true);
            }
        }
        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && event.getAction() == GLFW.GLFW_PRESS) {
            if (vehicle instanceof M2BrowningEntity || vehicle instanceof AGS30Entity) {
                PacketHandler.INSTANCE.sendToServer(new PacketToggleAim());
                event.setCanceled(true);
                return;
            }
        }

        if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE && event.getAction() == GLFW.GLFW_PRESS) {
            ItemStack stack = mc.player.getMainHandItem();
            if (stack.getItem() instanceof VehicleMarkerItem || stack.getItem() instanceof SupplyTruckMarkerItem) {
                HitResult result = mc.hitResult;
                if (result != null && result.getType() == HitResult.Type.ENTITY) {
                    EntityHitResult entityResult = (EntityHitResult) result;
                    Entity target = entityResult.getEntity();
                    PacketHandler.INSTANCE.sendToServer(new PacketApplyMarker(target.getId()));
                }
            }
        }
    }

    @SubscribeEvent
    public static void onComputeFov(ComputeFovModifierEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            // Р—СѓРј РґР»СЏ Р±РёРЅРѕРєР»СЏ
            ItemStack activeStack = mc.player.getUseItem();
            if (activeStack.getItem() == ModItems.BINOCULARS.get()) {
                event.setNewFovModifier(event.getFovModifier() * (1.0f / 8.0f));
            }

            // Р’Р°С€Р° СЃСѓС‰РµСЃС‚РІСѓСЋС‰Р°СЏ Р»РѕРіРёРєР° РґР»СЏ С‚РµС…РЅРёРєРё...
            Entity vehicle = mc.player.getVehicle();
            if (vehicle instanceof M2BrowningEntity m2 && m2.isAiming()) {
                event.setNewFovModifier(event.getFovModifier() * (1.0f / 1.5f));
            } else if (vehicle instanceof AGS30Entity ags && ags.isAiming()) {
                event.setNewFovModifier(event.getFovModifier() * (1.0f / 3f));
            }
        }
    }

    private static final ResourceLocation BINOCULAR_SHADER = new ResourceLocation("aas", "shaders/post/binoculars.json");
    private static boolean wasUsingBinoculars = false;

    @SubscribeEvent
    public static void onClientTickShader(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        boolean isUsingBinoculars = mc.player.isUsingItem() && mc.player.getUseItem().getItem() == ModItems.BINOCULARS.get();

        if (isUsingBinoculars && !wasUsingBinoculars) {
            System.out.println("DEBUG: Loading Binocular Shader"); // РўР•РЎРў
            mc.tell(() -> mc.gameRenderer.loadEffect(BINOCULAR_SHADER));
            wasUsingBinoculars = true;
        } else if (!isUsingBinoculars && wasUsingBinoculars) {
            System.out.println("DEBUG: Shutting down Shader"); // РўР•РЎРў
            mc.tell(() -> mc.gameRenderer.shutdownEffect());
            wasUsingBinoculars = false;
        }
    }


    // Р’ С„Р°Р№Р»Рµ ClientEvents.java РЅР°Р№РґРёС‚Рµ РјРµС‚РѕРґ onOpenGui
    @SubscribeEvent
    public static void onOpenGui(ScreenEvent.Opening event) {
        if (event.getScreen() instanceof DeathScreen && !(event.getScreen() instanceof AASDeathScreen)) {
            // Р•СЃР»Рё РёРіСЂРѕРє СѓРјРµСЂ, РЅРѕ С‚Р°Р№РјРµСЂ СѓР¶Рµ Р·Р°РїСѓС‰РµРЅ (Р±С‹Р» РІ РЅРѕРєР°СѓС‚Рµ), РќР• РџР•Р Р•Р—РђРџРРЎР«Р’РђР•Рњ
            if (ClientData.globalDeathTimestamp == 0) {
                ClientData.globalDeathTimestamp = System.currentTimeMillis();
            }

            ClientData.deathFadeStartTime = System.currentTimeMillis();
            ClientData.deathFadePlayed = true;

            Component cause = null;
            if (Minecraft.getInstance().player != null) {
                cause = Minecraft.getInstance().player.getCombatTracker().getDeathMessage();
            }
            event.setNewScreen(new AASDeathScreen(cause, false));
        }
    }

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // --- ФИКС: F одновременно триггерит ревайв и ванильный Swap Offhand ---
        // Это событие приходит СРАЗУ после KeyMapping.click(), но ДО того как
        // Minecraft.handleKeybinds() (вызывается каждый кадр, а не каждый тик)
        // успеет вызвать keySwapOffhand.consumeClick() и отправить свап на сервер.
        // Поэтому съедаем клик здесь, а не в TickEvent — там уже может быть поздно,
        // т.к. тик идёт реже кадров и ванильный свап успевает проскочить раньше.
        if (mc.options.keySwapOffhand.matches(event.getKey(), event.getScanCode())
                && mc.screen == null
                && mc.player.isAlive()
                && !mc.player.getPersistentData().getBoolean("AAS_IsDowned")
                && findNearestDownedAlly(mc) != null) {
            while (mc.options.keySwapOffhand.consumeClick()) { }
        }

        if (event.getKey() == GLFW.GLFW_KEY_PAGE_UP && event.getAction() == GLFW.GLFW_PRESS) {
            PacketHandler.INSTANCE.sendToServer(new PacketConfirmArtStrike(true));
        }
        if (event.getKey() == GLFW.GLFW_KEY_PAGE_DOWN && event.getAction() == GLFW.GLFW_PRESS) {
            PacketHandler.INSTANCE.sendToServer(new PacketConfirmArtStrike(false));
        }
        // --- Р›РћР“РРљРђ Р“РћР›РћРЎРћР’РђРќРРЇ Р—Рђ CMD (F7/F8) ---
        String myTeam = mc.player.getTeam() != null ? mc.player.getTeam().getName().toUpperCase() : "NEUTRAL";
        boolean isBlue = myTeam.equals("BLUE");

        // РџСЂРѕРІРµСЂСЏРµРј СЃС‚Р°С‚СѓСЃ РіРѕР»РѕСЃРѕРІР°РЅРёСЏ РёРјРµРЅРЅРѕ РґР»СЏ РЎР’РћР•Р™ РєРѕРјР°РЅРґС‹
        boolean myTeamCmdActive = isBlue ? ClientData.blueCmdVoteActive : ClientData.redCmdVoteActive;
        String myCandidateName = isBlue ? ClientData.blueCmdCandidateName : ClientData.redCmdCandidateName;

        if (myTeamCmdActive && mc.screen == null) {
            // РљР°РЅРґРёРґР°С‚ РЅРµ РјРѕР¶РµС‚ РіРѕР»РѕСЃРѕРІР°С‚СЊ Р·Р° СЃР°РјРѕРіРѕ СЃРµР±СЏ
            boolean isCandidate = mc.player.getScoreboardName().equals(myCandidateName);

            if (!isCandidate) {
                if (event.getKey() == GLFW.GLFW_KEY_F7 && event.getAction() == GLFW.GLFW_PRESS) {
                    PacketHandler.INSTANCE.sendToServer(new PacketCMDVote(true));
                    mc.player.displayClientMessage(Component.literal("Voted: YES").withStyle(ChatFormatting.GREEN), true);
                    return;
                } else if (event.getKey() == GLFW.GLFW_KEY_F8 && event.getAction() == GLFW.GLFW_PRESS) {
                    PacketHandler.INSTANCE.sendToServer(new PacketCMDVote(false));
                    mc.player.displayClientMessage(Component.literal("Voted: NO").withStyle(ChatFormatting.RED), true);
                    return;
                }
            }
        }
        if (ModKeyBindings.PLACE_PING_KEY.matches(event.getKey(), event.getScanCode()) && event.getAction() == GLFW.GLFW_PRESS) {
            if (mc.screen == null) {
                tryPlacePingLogic(mc);
            }
            return;
        }
        // 1. Р›РћР“РРљРђ Р“РћР›РћРЎРћР’РђРќРРЇ (F1/F2)
        if (ClientData.voteActive && mc.screen == null && event.getAction() == GLFW.GLFW_PRESS) {
            if (event.getKey() == GLFW.GLFW_KEY_F9) {
                PacketHandler.INSTANCE.sendToServer(new PacketVoteAction(true));
                // event.setCanceled(true); // РЈР”РђР›Р•РќРћ, С‚Р°Рє РєР°Рє РІС‹Р·С‹РІР°РµС‚ РєСЂР°С€
                return;
            } else if (event.getKey() == GLFW.GLFW_KEY_F10) {
                PacketHandler.INSTANCE.sendToServer(new PacketVoteAction(false));
                // event.setCanceled(true); // РЈР”РђР›Р•РќРћ, С‚Р°Рє РєР°Рє РІС‹Р·С‹РІР°РµС‚ РєСЂР°С€
                return;
            }
        }

        // 2. РЎР‘Р РћРЎ РЇР©РРљРђ (X)
        if (event.getAction() == GLFW.GLFW_PRESS && ModKeyBindings.DROP_SUPPLY_KEY.matches(event.getKey(), event.getScanCode())) {
            if (mc.player.getVehicle() != null) {
                PacketHandler.INSTANCE.sendToServer(new PacketDropCrate());
            }
        }

        // 3. РњР•РќР® РћРўР РЇР”РћР’ (K)
        if (event.getAction() == GLFW.GLFW_PRESS && ModKeyBindings.OPEN_SQUAD_MENU_KEY.matches(event.getKey(), event.getScanCode())) {
            // Р РђР—Р Р•РЁРђР•Рњ РѕС‚РєСЂС‹С‚РёРµ, РµСЃР»Рё СЌРєСЂР°РЅ РїСѓСЃС‚РѕР№ РР›Р РµСЃР»Рё РѕС‚РєСЂС‹С‚ СЌРєСЂР°РЅ РЅРѕРєР°
            if (mc.screen == null || mc.screen instanceof com.example.aas.client.gui.DownedScreen) {
                String teamName = (mc.player.getTeam() != null) ? mc.player.getTeam().getName() : "";
                boolean isValidTeam = teamName.equalsIgnoreCase("Blue") || teamName.equalsIgnoreCase("Red");

                if (!isValidTeam) {
                    mc.setScreen(new TeamSelectionScreen());
                } else {
                    mc.setScreen(new SquadSelectionScreen());
                }
            }
        }

        // 4. РљРђР РўРђ (TAB)
        if (ModKeyBindings.SHOW_MAP_KEY.matches(event.getKey(), event.getScanCode())) {
            if (event.getAction() == GLFW.GLFW_PRESS) {
                ClientData.isMapOpen = true;
            }
            else if (event.getAction() == GLFW.GLFW_RELEASE) {
                ClientData.isMapOpen = false;
            }
        }
        if (event.getKey() == GLFW.GLFW_KEY_F9 && event.getAction() == GLFW.GLFW_PRESS) {
            if (mc.player.isCreative() && mc.screen == null) {
                PacketHandler.INSTANCE.sendToServer(new PacketDebugFill());
                return;
            }
        }
        // 5. РЎРўРђРўРРЎРўРРљРђ (Caps Lock, РїРµСЂРµР±РёРЅРґРёС‚СЃСЏ РІ РЅР°СЃС‚СЂРѕР№РєР°С… СѓРїСЂР°РІР»РµРЅРёСЏ)
        if (event.getAction() == GLFW.GLFW_PRESS && ModKeyBindings.OPEN_STATS_KEY.matches(event.getKey(), event.getScanCode())) {
            if (mc.screen == null) {
                mc.setScreen(new com.example.aas.client.gui.StatisticsScreen());
            } else if (mc.screen instanceof com.example.aas.client.gui.StatisticsScreen) {
                mc.setScreen(null); // РїРѕРІС‚РѕСЂРЅРѕРµ РЅР°Р¶Р°С‚РёРµ Р·Р°РєСЂС‹РІР°РµС‚ СЌРєСЂР°РЅ
            }
        }
    }

    @SubscribeEvent
    public static void onGuiOpen(net.minecraftforge.client.event.ScreenEvent.Opening event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (com.example.aas.config.AASConfig.PREVENT_VEHICLE_INVENTORY_ACCESS.get() &&
                !mc.player.isCreative() && mc.player.getVehicle() != null) {

            if (event.getScreen() instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen ||
                    event.getScreen() instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen) {

                event.setCanceled(true);
                mc.player.displayClientMessage(Component.literal("Inventory is disabled while inside a vehicle!")
                        .withStyle(ChatFormatting.RED), true);
            }
        }
    }
    private static float downedCameraProgress = 0f;
    private static float initialPitchOnFall = 0f; // Р¤РёРєСЃРёСЂСѓРµРј РІР·РіР»СЏРґ РІ РјРѕРјРµРЅС‚ СѓРґР°СЂР°
    private static boolean wasDownedLastFrame = false;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        boolean isDowned = mc.player.getPersistentData().getBoolean("AAS_IsDowned") && mc.player.isAlive();

        // 1. Р›РѕРіРёРєР° РІС…РѕРґР° РІ СЃРѕСЃС‚РѕСЏРЅРёРµ (С„РёРєСЃР°С†РёСЏ РЅР°С‡Р°Р»СЊРЅРѕР№ С‚РѕС‡РєРё)
        if (isDowned) {
            if (!wasDownedLastFrame) {
                initialPitchOnFall = event.getPitch(); // Р—Р°РїРѕРјРёРЅР°РµРј РєСѓРґР° СЃРјРѕС‚СЂРµР»Рё
                wasDownedLastFrame = true;
            }
            if (downedCameraProgress < 1.0f) downedCameraProgress += 0.07f; // РЎРєРѕСЂРѕСЃС‚СЊ "РїР°РґРµРЅРёСЏ"
        } else {
            wasDownedLastFrame = false;
            if (downedCameraProgress > 0.0f) downedCameraProgress -= 0.05f;
        }

        if (downedCameraProgress > 0) {
            float p = downedCameraProgress;

            // 2. Р­Р¤Р¤Р•РљРў "Р РЈРҐРќРЈР’РЁР•Р“Рћ РўР•Р›Рђ"
            // РЎРЅР°С‡Р°Р»Р° (РїРµСЂРІС‹Рµ 30% РїР°РґРµРЅРёСЏ) РїРµСЂСЃРѕРЅР°Р¶ РЅРµРјРЅРѕРіРѕ "РєР»СЋРµС‚" РІРЅРёР· (stumble)
            float stumble = 0;
            if (p < 0.4f) {
                stumble = Mth.sin(p * (float)Math.PI * 2.5f) * 12f;
            }

            // РћСЃРЅРѕРІРЅРѕР№ РїРµСЂРµС…РѕРґ Рє -60 РіСЂР°РґСѓСЃР°Рј
            float targetPitch = -60.0f;
            float smoothPitch = Mth.lerp(p, initialPitchOnFall, targetPitch) + stumble;
            event.setPitch(smoothPitch);

            // 3. РќРђРљР›РћРќ Р“РћР›РћР’Р« (Roll)
            // РџСЂРё РїР°РґРµРЅРёРё РіРѕР»РѕРІР° Р·Р°РІР°Р»РёРІР°РµС‚СЃСЏ РЅР° Р±РѕРє РЅР° 35 РіСЂР°РґСѓСЃРѕРІ
            event.setRoll(p * 35f);

            // 4. Р”РРќРђРњРР§Р•РЎРљРђРЇ РўР РЇРЎРљРђ
            // Р’ РјРѕРјРµРЅС‚ "РїСЂРёР·РµРјР»РµРЅРёСЏ" (РєРѕРіРґР° p > 0.8) РґРѕР±Р°РІР»СЏРµРј Р»РµРіРєРёР№ СѓРґР°СЂ
            if (isDowned && p > 0.8f) {
                float force = (p - 0.8f) * 10f;
                event.setYaw(event.getYaw() + (mc.level.random.nextFloat() - 0.5f) * force);
                event.setPitch(event.getPitch() + (mc.level.random.nextFloat() - 0.5f) * force);
            }

            // 5. РРњРРўРђР¦РРЇ Р”Р«РҐРђРќРРЇ (РєРѕРіРґР° СѓР¶Рµ Р»РµР¶РёС‚)
            if (p >= 1.0f && isDowned) {
                float breathing = Mth.sin(mc.level.getGameTime() * 0.06f) * 1.5f;
                event.setPitch(targetPitch + breathing);
            }
        }

    }
    private static void handleReviveHold(Minecraft mc) {
        if (mc.level == null || mc.player == null) return;

        // Проверяем, зажата ли клавиша подъёма (по умолчанию F)
        boolean keyDown = ModKeyBindings.REVIVE_KEY.isDown();

        // Условия: кнопка нажата, нет открытых меню, игрок жив и сам не в нокауте
        boolean canTryRevive = keyDown && mc.screen == null
                && mc.player.isAlive()
                && !mc.player.getPersistentData().getBoolean("AAS_IsDowned");

        Entity target = canTryRevive ? findNearestDownedAlly(mc) : null;

        if (target != null) {
            // Гасим ванильный F (свап рук), чтобы не мешал
            while (mc.options.keySwapOffhand.consumeClick()) { }

            boolean newTarget = !revivingHeld || target.getId() != reviveTargetId;
            // Сервер подтверждает, что подъём идёт, присылая полоску прогресса (>= 0)
            boolean serverConfirmed = ClientData.reviveProgressOther >= 0f;

            // START шлём ОДИН раз при начале / смене цели. Если сервер не подтвердил
            // (например, отклонил из-за расхождения позиций) - повторяем раз в REVIVE_RESEND_TICKS.
            // Повторный START для той же цели на сервере безопасен: прогресс не сбрасывается.
            if (newTarget || (!serverConfirmed && --reviveResendCooldown <= 0)) {
                PacketHandler.INSTANCE.sendToServer(new PacketReviveHold(true, target.getId()));
                reviveResendCooldown = REVIVE_RESEND_TICKS;
            }
            revivingHeld = true;
            reviveTargetId = target.getId();
        } else if (revivingHeld) {
            // Клавишу отпустили / цель потеряна - CANCEL, один раз
            revivingHeld = false;
            reviveTargetId = -1;
            PacketHandler.INSTANCE.sendToServer(new PacketReviveHold(false, -1));
        }
    }

    private static Entity findNearestDownedAlly(Minecraft mc) {
        Entity best = null;
        double bestDistSq = REVIVE_RANGE * REVIVE_RANGE;

        for (Entity e : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(REVIVE_RANGE))) {
            if (!(e instanceof net.minecraft.world.entity.player.Player p) || p == mc.player) continue;
            // Р’РђР–РќРћ: persistentData("AAS_IsDowned") РЅР° РєР»РёРµРЅС‚Рµ РїСЂРѕСЃС‚Р°РІР»СЏРµС‚СЃСЏ РўРћР›Р¬РљРћ РґР»СЏ
            // Р›РћРљРђР›Р¬РќРћР“Рћ РёРіСЂРѕРєР° (СЃРј. ClientHooks.handleDownedState). Р”Р»СЏ Р’РЎР•РҐ РѕСЃС‚Р°Р»СЊРЅС‹С…
            // РёРіСЂРѕРєРѕРІ СЃС‚Р°С‚СѓСЃ "РІ РЅРѕРєРµ" СЃРёРЅС…СЂРѕРЅРёР·РёСЂСѓРµС‚СЃСЏ С‚РѕР»СЊРєРѕ С‡РµСЂРµР· ClientData.DOWNED_PLAYERS,
            // РїРѕСЌС‚РѕРјСѓ РїСЂРѕРІРµСЂРєР° РЅР° persistentData Р·РґРµСЃСЊ РІСЃРµРіРґР° РІРѕР·РІСЂР°С‰Р°Р»Р° false РґР»СЏ СЃРѕСЋР·РЅРёРєРѕРІ
            // Рё F РЅРёРєРѕРіРґР° РЅРµ РЅР°С…РѕРґРёР» С†РµР»СЊ РґР»СЏ РїРѕРґСЉС‘РјР°.
            if (!ClientData.DOWNED_PLAYERS.contains(p.getId())) continue;

            double d = mc.player.distanceToSqr(p);
            if (d < bestDistSq) {
                bestDistSq = d;
                best = p;
            }
        }
        return best;
    }

    /**
     * РС‰РµС‚ СЂР°РЅРµРЅРѕРіРѕ СЃРѕСЋР·РЅРёРєР°, РЅР° РєРѕС‚РѕСЂРѕРіРѕ РёРіСЂРѕРє СЃРµР№С‡Р°СЃ РЅР°РІС‘Р» РІР·РіР»СЏРґ, РґРµСЂР¶Р° РІ СЂСѓРєРµ
     * РїСЂР°РІРёР»СЊРЅС‹Р№ РїСЂРµРґРјРµС‚ РґР»СЏ РїРѕРґСЉС‘РјР° (AASConfig.REVIVE_ITEM). РСЃРїРѕР»СЊР·СѓРµС‚СЃСЏ РўРћР›Р¬РљРћ РґР»СЏ
     * РІРёР·СѓР°Р»СЊРЅРѕР№ РїРѕРґСЃРєР°Р·РєРё вЂ” СЃР°Рј РїРѕРґСЉС‘Рј РїРѕ-РїСЂРµР¶РЅРµРјСѓ СЂР°Р±РѕС‚Р°РµС‚ С‡РµСЂРµР· findNearestDownedAlly
     * (РїРѕ Р±Р»РёР¶Р°Р№С€РµРјСѓ РІ СЂР°РґРёСѓСЃРµ), С‡С‚РѕР±С‹ РЅРµ Р»РѕРјР°С‚СЊ РІРѕР·РјРѕР¶РЅРѕСЃС‚СЊ РїРѕРґРЅСЏС‚СЊ РёРіСЂРѕРєР°,
     * Р»РµР¶Р°С‰РµРіРѕ РїРѕС‡С‚Рё РІРїР»РѕС‚РЅСѓСЋ (Сѓ РЅРёС… РѕС‡РµРЅСЊ РЅРёР·РєРёР№ С…РёС‚Р±РѕРєСЃ РІ РїРѕР·Рµ SWIMMING, Рё СѓР·РєРёР№
     * СЂРµР№РєР°СЃС‚ РїРѕ РЅРµРјСѓ С‡Р°СЃС‚Рѕ РїСЂРѕРјР°С…РёРІР°РµС‚СЃСЏ).
     */
    private static Entity findRevivePromptTarget(Minecraft mc) {
        if (mc.player == null || mc.level == null) return null;
        if (mc.screen != null) return null;
        if (!mc.player.isAlive() || mc.player.getPersistentData().getBoolean("AAS_IsDowned")) return null;

        String reviveItemName = ClientData.serverReviveItem; // было: AASConfig.REVIVE_ITEM.get()
        net.minecraft.world.item.Item reviveItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation(reviveItemName));
        if (reviveItem == null || (mc.player.getMainHandItem().getItem() != reviveItem && mc.player.getOffhandItem().getItem() != reviveItem)) return null;

        Vec3 eye = mc.player.getEyePosition(1.0f);
        Vec3 look = mc.player.getViewVector(1.0f).normalize();

        Entity best = null;
        double bestDot = REVIVE_AIM_DOT_THRESHOLD;

        for (Entity e : mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(REVIVE_RANGE))) {
            if (!(e instanceof Player p) || p == mc.player) continue;
            // РЎРј. РєРѕРјРјРµРЅС‚Р°СЂРёР№ РІ findNearestDownedAlly вЂ” РґР»СЏ С‡СѓР¶РёС… РёРіСЂРѕРєРѕРІ РЅСѓР¶РЅРѕ РїСЂРѕРІРµСЂСЏС‚СЊ
            // РёРјРµРЅРЅРѕ ClientData.DOWNED_PLAYERS, Р° РЅРµ РёС… persistentData.
            if (!ClientData.DOWNED_PLAYERS.contains(p.getId())) continue;
            if (mc.player.distanceTo(p) > REVIVE_RANGE) continue;

            Vec3 toTarget = p.position().add(0, p.getBbHeight() * 0.5 + 0.15, 0).subtract(eye).normalize();
            double dot = look.dot(toTarget);
            if (dot > bestDot) {
                bestDot = dot;
                best = p;
            }
        }
        return best;
    }

    /**
     * РРЅРѕС‡РЅС‹Р№ 3D-РјР°СЂРєРµСЂ РЅР°Рґ СЂР°РЅРµРЅС‹Рј: С‡С‘СЂРЅС‹Р№ РїРѕР»СѓРїСЂРѕР·СЂР°С‡РЅС‹Р№ РєРІР°РґСЂР°С‚ СЃ Р±РµР»РѕР№ РѕР±РІРѕРґРєРѕР№,
     * Р±СѓРєРІР° "F" РІ С†РµРЅС‚СЂРµ Рё РїРѕРґРїРёСЃСЊ СЃРїСЂР°РІР°.
     */
    private static void renderRevivePrompt(PoseStack poseStack, Vec3 cameraPos, Entity target, Minecraft mc, float partialTick) {
        poseStack.pushPose();

        double tx = Mth.lerp(partialTick, target.xOld, target.getX());
        double ty = Mth.lerp(partialTick, target.yOld, target.getY());
        double tz = Mth.lerp(partialTick, target.zOld, target.getZ());

        double renderX = tx - cameraPos.x;
        double renderY = ty + target.getBbHeight() + 0.05 - cameraPos.y; // было + 0.55
        double renderZ = tz - cameraPos.z;
        poseStack.translate(renderX, renderY, renderZ);

        poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());

        // Р Р°Р·РјРµСЂ РјР°СЂРєРµСЂР° СѓРјРµРЅСЊС€РµРЅ РІ 4 СЂР°Р·Р° РїРѕ СЃСЂР°РІРЅРµРЅРёСЋ СЃ РёСЃС…РѕРґРЅС‹Рј (0.025f -> 0.00625f)
        float scale = 0.00625f;
        double dist = mc.player.distanceTo(target);
        scale *= (float) Math.max(1.0, dist / 2.5);
        scale = Math.min(scale, 0.0125f);
        poseStack.scale(-scale, -scale, scale);

        Matrix4f matrix = poseStack.last().pose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buf = tesselator.getBuilder();

        float half = 13f;

        // 1. Р§С‘СЂРЅС‹Р№ РїРѕР»СѓРїСЂРѕР·СЂР°С‡РЅС‹Р№ С„РѕРЅ-РєРІР°РґСЂР°С‚
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        quad(buf, matrix, -half, -half, half, half, 0, 0, 0, 140);
        tesselator.end();

        // 2. Р‘РµР»Р°СЏ РѕР±РІРѕРґРєР° (4 С‚РѕРЅРєРёС… РїРѕР»РѕСЃРєРё РїРѕ РїРµСЂРёРјРµС‚СЂСѓ)
        float t = 1.2f;
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        quad(buf, matrix, -half, -half, half, -half + t, 255, 255, 255, 230);      // РІРµСЂС…
        quad(buf, matrix, -half, half - t, half, half, 255, 255, 255, 230);        // РЅРёР·
        quad(buf, matrix, -half, -half, -half + t, half, 255, 255, 255, 230);      // Р»РµРІР°СЏ
        quad(buf, matrix, half - t, -half, half, half, 255, 255, 255, 230);        // РїСЂР°РІР°СЏ
        tesselator.end();

        RenderSystem.setShaderColor(1, 1, 1, 1);

        // 3. Р‘СѓРєРІР° РІ С†РµРЅС‚СЂРµ РєРІР°РґСЂР°С‚Р° вЂ” Р”РРќРђРњРР§Р•РЎРљРђРЇ: Р±РµСЂС‘С‚СЃСЏ РёР· С‚РµРєСѓС‰РµРіРѕ Р±РёРЅРґР° REVIVE_KEY,
        // РЅРѕ РІСЃРµРіРґР° РѕС‚РѕР±СЂР°Р¶Р°РµС‚СЃСЏ Р°РЅРіР»РёР№СЃРєРёРј СЃРёРјРІРѕР»РѕРј (РЅРµ Р·Р°РІРёСЃРёС‚ РѕС‚ СЏР·С‹РєР° РёРіСЂС‹/СЂР°СЃРєР»Р°РґРєРё)
        Font font = mc.font;
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();

        String letter = getReviveKeyLabel();
        float lw = font.width(letter);
        // было: font.drawInBatch(letter, -lw / 2f, -4f, 0xFFFFFFFF, false, matrix, buffer, Font.DisplayMode.NORMAL, 0, 15728880);
        font.drawInBatch(letter, -lw / 2f, -4f, 0xFFFFFFFF, false,
                matrix, buffer, Font.DisplayMode.NORMAL, 0, 15728880); // оставляем как есть — это уже чистый белый

// подпись рядом — убираем теневую подложку 0x55000000, чтобы не давала серый оттенок
        poseStack.pushPose();
        poseStack.translate(half + 6f, -4f, 0f);
        Matrix4f textMatrix = poseStack.last().pose();
        font.drawInBatch(I18n.get("aas.hud.revive_prompt"), 0, 0, 0xFFFFFFFF, false,
                textMatrix, buffer, Font.DisplayMode.NORMAL, 0, 15728880); // false вместо true, 0 вместо 0x55000000
        poseStack.popPose();

        buffer.endBatch();

        RenderSystem.enableDepthTest();
        poseStack.popPose();
    }

    /**
     * Р’РѕР·РІСЂР°С‰Р°РµС‚ РѕС‚РѕР±СЂР°Р¶Р°РµРјРѕРµ РёРјСЏ С‚РµРєСѓС‰РµР№ РєР»Р°РІРёС€Рё РїРѕРґСЉС‘РјР° (ModKeyBindings.REVIVE_KEY).
     * Р”Р»СЏ Р±СѓРєРІ Рё С†РёС„СЂ (A-Z, 0-9) РІСЃРµРіРґР° РІРѕР·РІСЂР°С‰Р°РµС‚ Р»Р°С‚РёРЅСЃРєРёР№ СЃРёРјРІРѕР» РЅР°РїСЂСЏРјСѓСЋ РёР·
     * GLFW-РєРѕРґР° РєР»Р°РІРёС€Рё (РѕРЅ СЂР°РІРµРЅ ASCII-РєРѕРґСѓ), С‡С‚РѕР±С‹ Р±СѓРєРІР° РЅРµ РїРµСЂРµРІРѕРґРёР»Р°СЃСЊ/РЅРµ
     * Р·Р°РІРёСЃРµР»Р° РѕС‚ СЏР·С‹РєР° РёРіСЂС‹. Р”Р»СЏ РѕСЃС‚Р°Р»СЊРЅС‹С… РєР»Р°РІРёС€ (Shift, РїСЂРѕР±РµР», РєРЅРѕРїРєРё РјС‹С€Рё Рё С‚.Рґ.)
     * РёСЃРїРѕР»СЊР·СѓРµС‚СЃСЏ РІР°РЅРёР»СЊРЅРѕРµ РѕС‚РѕР±СЂР°Р¶Р°РµРјРѕРµ РёРјСЏ РєР»Р°РІРёС€Рё.
     */
    private static String getReviveKeyLabel() {
        InputConstants.Key key = ModKeyBindings.REVIVE_KEY.getKey();

        if (key.getType() == InputConstants.Type.KEYSYM) {
            int v = key.getValue();
            if (v >= org.lwjgl.glfw.GLFW.GLFW_KEY_A && v <= org.lwjgl.glfw.GLFW.GLFW_KEY_Z) {
                return String.valueOf((char) v); // GLFW_KEY_A..Z СЃРѕРІРїР°РґР°СЋС‚ СЃ ASCII 'A'..'Z'
            }
            if (v >= org.lwjgl.glfw.GLFW.GLFW_KEY_0 && v <= org.lwjgl.glfw.GLFW.GLFW_KEY_9) {
                return String.valueOf((char) v); // GLFW_KEY_0..9 СЃРѕРІРїР°РґР°СЋС‚ СЃ ASCII '0'..'9'
            }
        }

        // РЅРµ-Р±СѓРєРІРµРЅРЅР°СЏ РєР»Р°РІРёС€Р° (Shift, РїСЂРѕР±РµР», РєРЅРѕРїРєР° РјС‹С€Рё Рё С‚.Рґ.) вЂ” РѕР±С‹С‡РЅРѕРµ РѕС‚РѕР±СЂР°Р¶РµРЅРёРµ
        return key.getDisplayName().getString();
    }

    private static void quad(BufferBuilder buf, Matrix4f matrix, float x1, float y1, float x2, float y2, int r, int g, int b, int a) {
        buf.vertex(matrix, x1, y2, 0).color(r, g, b, a).endVertex();
        buf.vertex(matrix, x2, y2, 0).color(r, g, b, a).endVertex();
        buf.vertex(matrix, x2, y1, 0).color(r, g, b, a).endVertex();
        buf.vertex(matrix, x1, y1, 0).color(r, g, b, a).endVertex();
    }
    // Проверяет прогресс игрока и показывает подсказки:
    // - "выбери команду" - один раз за сессию, со звуком;
    // - "выбери отряд" - каждый раз заново, когда игрок теряет отряд (вышел/кикнули), без звука;
    // - "выбери снаряжение" - каждый раз заново, когда игрок вступает в отряд без кита, без звука.
    private static void checkOnboardingHints(Minecraft mc) {
        if (mc.player == null) return;

        String teamName = (mc.player.getTeam() != null) ? mc.player.getTeam().getName() : "";
        boolean hasTeam = teamName.equalsIgnoreCase("Blue") || teamName.equalsIgnoreCase("Red");

        // ВАЖНО: getPersistentData() сервера НЕ синхронизируется на клиент - используем
        // реально синхронизируемые источники (как и остальной клиентский код мода).
        String myName = mc.player.getScoreboardName();
        boolean hasSquad = ClientData.clientSquads.stream()
                .anyMatch(s -> s.members.contains(myName));
        boolean hasKit = ClientData.myCurrentKit != null
                && !ClientData.myCurrentKit.isEmpty()
                && !ClientData.myCurrentKit.equalsIgnoreCase("Unassigned");

        String keyName = ModKeyBindings.OPEN_SQUAD_MENU_KEY.getTranslatedKeyMessage().getString();

        if (!hintsInitialized) {
            hintsInitialized = true;
            prevHasSquad = hasSquad;
        }

        if (prevHasSquad && !hasSquad) {
            squadHintShown = false;
        }
        if (!prevHasSquad && hasSquad) {
            kitHintShown = false;
            ClientData.squadPanelExpanded = true;
        }
        prevHasSquad = hasSquad;

        if (!hasTeam) {
            if (!teamHintShown) {
                teamHintShown = true;
                showHintToast(mc, "aas.hint.team.title", "aas.hint.team.desc", keyName, true);
            }
        } else if (!hasSquad) {
            if (!squadHintShown) {
                squadHintShown = true;
                showHintToast(mc, "aas.hint.squad.title", "aas.hint.squad.desc", keyName, false);
            }
        } else if (!hasKit) {
            if (!kitHintShown) {
                kitHintShown = true;
                showHintToast(mc, "aas.hint.kit.title", "aas.hint.kit.desc", keyName, false);
            }
        }
    }

    // Стандартный игровой toast (как ванильные туториальные подсказки).
    // withSound - только для самой первой подсказки (выбор команды), остальные тихие, чтобы не раздражать
    // при повторных срабатываниях (кик из отряда и т.п.).
    private static void showHintToast(Minecraft mc, String titleKey, String descKey, String keyName, boolean withSound) {
        Component title = Component.translatable(titleKey);
        Component desc = Component.translatable(descKey, keyName);

        net.minecraft.client.gui.components.toasts.SystemToast.addOrUpdate(
                mc.getToasts(),
                net.minecraft.client.gui.components.toasts.SystemToast.SystemToastIds.TUTORIAL_HINT,
                title,
                desc
        );

        if (withSound) {
            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 1.4F, 0.6F));
        }
    }
}