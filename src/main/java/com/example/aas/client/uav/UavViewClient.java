package com.example.aas.client.uav;

import com.example.aas.client.ClientData;
import com.example.aas.client.gui.SquadSelectionScreen;
import com.example.aas.client.gui.WorldMarkerRadialScreen;
import com.example.aas.network.PacketHandler;
import com.example.aas.network.PacketUavView;
import com.example.aas.uav.UavExitReason;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import net.minecraftforge.fml.LogicalSide;
/**
 * Client side of looking through the UAV.
 *  - the camera is a client-only Marker entity placed on the analytic orbit (the real UAV entity may not even be
 *    tracked by this client when it is far from the body);
 *  - the body is frozen: mouse turning is captured every frame and turned into a clamped camera tilt;
 *  - Q cycles zoom 1->2->3->off, E leaves.
 */
@Mod.EventBusSubscriber(modid = "aas", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class UavViewClient {

    private static boolean viewing = false;
    private static Marker anchor;
    private static int zoomStage = 0;       // 0 = off, 1..3
    private static float viewYaw, viewPitch;
    private static float frozenYaw, origPitch;

    public static boolean isViewing() { return viewing; }
    public static int zoomStage() { return zoomStage; }

    // ------------------------------------------------------------------ enter / exit
    /** Authoritative state from the server. */
    public static void onServerState(boolean active, int reason) {
        if (active) enter();
        else exit(UavExitReason.byId(reason));
    }
    private static Vec3 frozenPos;   // где стояло тело, когда начался просмотр
    private static int holdTicks;    // после выхода держим тело, пока не вернутся его чанки

    private static void pinBody(LocalPlayer pl) {
        pl.setPos(frozenPos.x, frozenPos.y, frozenPos.z);
        pl.setDeltaMovement(Vec3.ZERO);
        pl.fallDistance = 0f;
    }

    private static boolean bodyChunksReady(Minecraft mc) {
        int cx = Mth.floor(frozenPos.x) >> 4, cz = Mth.floor(frozenPos.z) >> 4;
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++)
                if (!mc.level.getChunkSource().hasChunk(cx + dx, cz + dz)) return false;
        return true;
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.side != LogicalSide.CLIENT) return;
        Minecraft mc = Minecraft.getInstance();
        if (frozenPos == null || mc.player == null || mc.level == null || e.player != mc.player) return;
        if (holdTicks % 5 == 0) com.mojang.logging.LogUtils.getLogger().info(
                "[AAS-client] hold: y={} onGround={} dy={}", mc.player.getY(), mc.player.onGround(), mc.player.getDeltaMovement().y);
        if (viewing) { pinBody(mc.player); return; }
        if (holdTicks > 0 && !bodyChunksReady(mc)) { holdTicks--; pinBody(mc.player); return; }
        frozenPos = null;
        holdTicks = 0;
    }
    private static void enter() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !UavClientState.active) return;
        if (viewing) return;
        anchor = new Marker(EntityType.MARKER, mc.level);
        Vec3 p = UavClientState.position(mc.level.getGameTime());
        placeAnchor(p);

        LocalPlayer pl = mc.player;
        frozenYaw = pl.getYRot();
        origPitch = pl.getXRot();
        viewYaw = frozenYaw;
        viewPitch = 90f;
        zoomStage = 0;
        // pitch 0 for the whole session: no clamping at +-90 can swallow mouse deltas
        pl.setXRot(0f);
        pl.xRotO = 0f;

        viewing = true;
        mc.setCameraEntity(anchor);
        if (mc.screen != null) mc.setScreen(null);
    }

    /** Leave the camera. Shows a notification for forced exits. */
    public static void exit(UavExitReason reason) {
        if (!viewing) return;
        exitSilently();
        if (reason.notifies()) UavNoticeOverlay.push(Component.translatable(reason.langKey()));
    }

    public static void exitSilently() {
        Minecraft mc = Minecraft.getInstance();
        if (viewing && mc.player != null) {
            mc.player.setYRot(frozenYaw);
            mc.player.yRotO = frozenYaw;
            mc.player.setXRot(origPitch);
            mc.player.xRotO = origPitch;
            mc.setCameraEntity(mc.player);
        }
        viewing = false;
        zoomStage = 0;
        anchor = null;
    }

    private static void placeAnchor(Vec3 p) {
        anchor.setPos(p.x, p.y, p.z);
        anchor.xo = anchor.xOld = p.x;
        anchor.yo = anchor.yOld = p.y;
        anchor.zo = anchor.zOld = p.z;
    }

    private static float zoomScale(Minecraft mc) {
        if (zoomStage == 0) return 1f;
        double base = mc.options.fov().get();
        return (float) Mth.clamp(ClientData.uavZoomFov(zoomStage - 1) / base, 0.05, 1.0);
    }

    // ------------------------------------------------------------------ per frame
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent e) {
        if (!viewing) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || anchor == null) { exitSilently(); return; }

        if (e.phase == TickEvent.Phase.START) {
            if (!UavClientState.active) { exitSilently(); return; }
            placeAnchor(UavClientState.position(mc.level.getGameTime() + e.renderTickTime));
            if (mc.getCameraEntity() != anchor) mc.setCameraEntity(anchor);
        } else {
            // mouse look already turned the body this frame: convert to tilt and put the body back
            LocalPlayer pl = mc.player;
            float dYaw = Mth.wrapDegrees(pl.getYRot() - frozenYaw);
            float dPitch = pl.getXRot();
            float k = zoomScale(mc);
            viewYaw = Mth.wrapDegrees(viewYaw + dYaw * k);
            float maxTilt = ClientData.uavMaxTilt();
            viewPitch = Mth.clamp(viewPitch + dPitch * k, 90f - maxTilt, 90f); // 90 = straight down
            pl.setYRot(frozenYaw);
            pl.yRotO = frozenYaw;
            pl.setXRot(0f);
            pl.xRotO = 0f;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles e) {
        if (!viewing) return;
        e.setYaw(viewYaw);
        e.setPitch(viewPitch);
        e.setRoll(0f);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onFov(ViewportEvent.ComputeFov e) {
        if (viewing && zoomStage > 0) e.setFOV(ClientData.uavZoomFov(zoomStage - 1));
    }

    // ------------------------------------------------------------------ input
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (!viewing || e.phase != TickEvent.Phase.START) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) { exitSilently(); return; }
        if (UavClientState.isDowned(mc)) { exit(UavExitReason.DOWNED); return; }
        drainBlockedKeys(mc);
        debugLog(mc);
    }

    private static void drainBlockedKeys(Minecraft mc) {
        KeyMapping[] list = {mc.options.keyDrop, mc.options.keyInventory, mc.options.keySwapOffhand,
                mc.options.keyAttack, mc.options.keyUse, mc.options.keyPickItem};
        for (KeyMapping k : list) while (k.consumeClick()) { }
        for (KeyMapping k : mc.options.keyHotbarSlots) while (k.consumeClick()) { }
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key e) {
        if (!viewing) return;
        Minecraft mc = Minecraft.getInstance();
        drainBlockedKeys(mc); // the event fires right after KeyMapping.click(): eat Q/E/slots before handleKeybinds
        if (mc.screen != null || e.getAction() != GLFW.GLFW_PRESS) return;
        if (e.getKey() == GLFW.GLFW_KEY_Q) {
            zoomStage = (zoomStage + 1) % 4; // 1 -> 2 -> 3 -> off
        } else if (e.getKey() == GLFW.GLFW_KEY_E) {
            PacketHandler.INSTANCE.sendToServer(new PacketUavView(false));
            exit(UavExitReason.USER);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseButton(InputEvent.MouseButton.Pre e) {
        if (viewing && Minecraft.getInstance().screen == null) e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onScroll(InputEvent.MouseScrollingEvent e) {
        if (viewing && Minecraft.getInstance().screen == null) e.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered e) {
        if (viewing) { e.setCanceled(true); e.setSwingHand(false); }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMovement(MovementInputUpdateEvent e) {
        if (!viewing) return;
        net.minecraft.client.player.Input in = e.getInput();
        in.up = in.down = in.left = in.right = false;
        in.forwardImpulse = 0f;
        in.leftImpulse = 0f;
        in.jumping = false;
        in.shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening e) {
        if (viewing && (e.getNewScreen() instanceof SquadSelectionScreen || e.getNewScreen() instanceof WorldMarkerRadialScreen)) {
            e.setCanceled(true);
        }
    }

    // ------------------------------------------------------------------ HUD hiding
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onOverlayPre(RenderGuiOverlayEvent.Pre e) {
        if (!viewing) return;
        Object o = e.getOverlay();
        if (o == VanillaGuiOverlay.HOTBAR.type() || o == VanillaGuiOverlay.CROSSHAIR.type()
                || o == VanillaGuiOverlay.PLAYER_HEALTH.type() || o == VanillaGuiOverlay.ARMOR_LEVEL.type()
                || o == VanillaGuiOverlay.FOOD_LEVEL.type() || o == VanillaGuiOverlay.AIR_LEVEL.type()
                || o == VanillaGuiOverlay.MOUNT_HEALTH.type() || o == VanillaGuiOverlay.JUMP_BAR.type()
                || o == VanillaGuiOverlay.EXPERIENCE_BAR.type() || o == VanillaGuiOverlay.ITEM_NAME.type()
                || o == VanillaGuiOverlay.POTION_ICONS.type() || o == VanillaGuiOverlay.SCOREBOARD.type()) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onHand(RenderHandEvent e) {
        if (viewing) e.setCanceled(true);
    }

    // ------------------------------------------------------------------ lifecycle
    @SubscribeEvent
    public static void onLoggingOut(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) {
        viewing = false;
        zoomStage = 0;
        anchor = null;
        UavClientState.clear();
    }

    /** New LocalPlayer after respawn/dimension change: vanilla already re-pointed the camera, just reset our state. */
    @SubscribeEvent
    public static void onPlayerClone(net.minecraftforge.client.event.ClientPlayerNetworkEvent.Clone e) {
        viewing = false;
        zoomStage = 0;
        anchor = null;
    }
    private static int dbgTick = 0;

    private static void debugLog(Minecraft mc) {
        if (++dbgTick % 100 != 0 || anchor == null || mc.level == null) return;
        int cx = Mth.floor(anchor.getX()) >> 4, cz = Mth.floor(anchor.getZ()) >> 4;
        int loaded = 0, total = 0;
        for (int dx = -8; dx <= 8; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                total++;
                if (mc.level.getChunk(cx + dx, cz + dz, net.minecraft.world.level.chunk.ChunkStatus.FULL, false) != null) loaded++;
            }
        }
        com.mojang.logging.LogUtils.getLogger().info(
                "[AAS-client] cam=({},{},{}) camChunk=({},{}) loaded8={}/{} renderDist={} stats={}",
                (int) anchor.getX(), (int) anchor.getY(), (int) anchor.getZ(), cx, cz, loaded, total,
                mc.options.getEffectiveRenderDistance(), mc.levelRenderer.getChunkStatistics());
    }
}
