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
import com.example.aas.network.PacketPlaceRadialMarker;
import com.example.aas.client.gui.WorldMarkerRadialScreen;
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
    // === Радиальное меню установки меток (удержание PLACE_PING_KEY) ===
    private static boolean radialKeyHeld = false;       // клавиша сейчас зажата (после первого press)
    private static long radialKeyPressTime = 0L;        // System.currentTimeMillis() в момент press
    private static boolean radialScreenOpened = false;  // экран уже открыт этим удержанием
    private static boolean pingKeyPhysicallyDown = false;
    private static final long RADIAL_HOLD_MS = 180L;    // порог удержания перед открытием меню

    private static final ResourceLocation MARKER_ATTACK_3D = new ResourceLocation("aas", "textures/gui/map_icons/marker_attack.png");
    private static final ResourceLocation MARKER_DEFEND_3D = new ResourceLocation("aas", "textures/gui/map_icons/marker_defend.png");
    private static final ResourceLocation MARKER_BUILD_3D  = new ResourceLocation("aas", "textures/gui/map_icons/marker_build.png");

    private static final ResourceLocation MARKER_ATTACK_BRAVO = new ResourceLocation("aas", "textures/gui/map_icons/marker_attack_bravo.png");
    private static final ResourceLocation MARKER_DEFEND_BRAVO = new ResourceLocation("aas", "textures/gui/map_icons/marker_defend_bravo.png");
    private static final ResourceLocation MARKER_BUILD_BRAVO  = new ResourceLocation("aas", "textures/gui/map_icons/marker_build_bravo.png");

    private static final ResourceLocation MARKER_ATTACK_CHARLIE = new ResourceLocation("aas", "textures/gui/map_icons/marker_attack_charlie.png");
    private static final ResourceLocation MARKER_DEFEND_CHARLIE = new ResourceLocation("aas", "textures/gui/map_icons/marker_defend_charlie.png");
    private static final ResourceLocation MARKER_BUILD_CHARLIE  = new ResourceLocation("aas", "textures/gui/map_icons/marker_build_charlie.png");
    private static final ResourceLocation REVIVE_MARKER_ICON = new ResourceLocation("aas", "textures/gui/heartbeat.png");
    private static int reviveTargetId = -1;        // кому в последний раз послали START
    private static int reviveResendCooldown = 0;   // тики до повторного START, если сервер не подтвердил
    private static final int REVIVE_RESEND_TICKS = 10;
    private static final double REVIVE_RANGE = 3.0;
    // Цель, на которую игрок сейчас наводится и может поднять (для визуальной подсказки "F")
    private static Entity currentRevivePromptTarget = null;
    // Минимальный косинус угла между направлением взгляда и направлением на цель,
    // чтобы считать, что игрок "навёл прицел на раненого" (~28°)
    private static final double REVIVE_AIM_DOT_THRESHOLD = 0.88;
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
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientData.lastVictory = null;
    }
    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
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

    // ОБЪЕДИНЁННЫЙ МЕТОД ТИКА
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // ФИКС: таймер сбрасывается ТОЛЬКО если игрок жив, не в нокауте И у него нет открытого экрана смерти
        if (mc.player.isAlive() && !mc.player.getPersistentData().getBoolean("AAS_IsDowned") && !(mc.screen instanceof AASDeathScreen)) {
            if (ClientData.globalDeathTimestamp != 0) {
                // Дополнительная проверка: не сбрасываем, если мы просто переходим между менюшками AAS
                if (!(mc.screen instanceof com.example.aas.client.gui.PlayerKitSelectScreen)) {
                    ClientData.globalDeathTimestamp = 0;
                    ClientData.deathFadePlayed = false;
                }
            }
        }

        // === Радиальное меню меток: если клавиша зажата дольше порога — открываем меню ===
        if (radialKeyHeld && !radialScreenOpened && mc.screen == null) {
            long held = System.currentTimeMillis() - radialKeyPressTime;
            if (held >= RADIAL_HOLD_MS && pingKeyPhysicallyDown) {
                int group = getMySquadGroup(mc);
                if (group < 0) {
                    // Не SL и не FTL — метки ставить нельзя, меню вообще не открываем
                    radialKeyHeld = false;
                } else {
                    radialScreenOpened = true;
                    mc.setScreen(new WorldMarkerRadialScreen(group));
                }
            } else if (!pingKeyPhysicallyDown) {
                radialKeyHeld = false;
            }
        }

        if (ClientData.voteActive && ClientData.voteTimer > 0) {
            // Каждые 20 тиков (1 секунда) уменьшаем визуальный таймер
            if (mc.level.getGameTime() % 20 == 0) {
                ClientData.voteTimer--;
            }
        }

        // 1. Отдача (Recoil)
        RecoilHandler.clientTick();


        // 3. ЛОГИКА СТРЕЛЬБЫ ИЗ ТЕХНИКИ
        Entity vehicle = mc.player.getVehicle();
        if (vehicle instanceof M2BrowningEntity || vehicle instanceof AGS30Entity) {
            long windowId = mc.getWindow().getWindow();
            boolean isLeftBtnDown = GLFW.glfwGetMouseButton(windowId, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;

            if (isLeftBtnDown && mc.screen == null) {
                PacketHandler.INSTANCE.sendToServer(new PacketVehicleShoot());
                while (mc.options.keyAttack.consumeClick()) { }
            }
        }
        // Добавляем проверку mc.player.isAlive()
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

        // Обновляем цель для визуальной подсказки "зажмите F" (наводка + нужный предмет в руке)
        Entity lockedTarget = (revivingHeld && ClientData.reviveProgressOther >= 0f)
                ? mc.level.getEntity(reviveTargetId) : null;
        currentRevivePromptTarget = lockedTarget != null ? lockedTarget : findRevivePromptTarget(mc);
        // ==== Подсказки для новичков ====
        if (hintCheckDelay > 0) {
            hintCheckDelay--;
        } else {
            checkOnboardingHints(mc);
        }
    }



    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (mc.player.getPersistentData().getBoolean("AAS_IsDraggingAlly")) {
            if (event.getInput().forwardImpulse > 0) {
                event.getInput().forwardImpulse = 0f;
            }
            // При желании можно ограничить и рывки в стороны:
            // event.getInput().leftImpulse *= 0.5f;
        }
    }

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (ClientData.isMapOpen && mc.screen == null) {
            double delta = event.getScrollDelta();
            if (delta != 0) {
                ClientData.zoomMiniMap(delta);   // было: ClientData.zoomMap(delta);
                event.setCanceled(true);
            }
        }
    }
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        // Маркер "зажмите F" рисуем независимо от наличия отряда (поэтому до выхода ниже)
        if (currentRevivePromptTarget != null && currentRevivePromptTarget.isAlive()) {
            renderRevivePrompt(event.getPoseStack(), event.getCamera().getPosition(), currentRevivePromptTarget, mc, event.getPartialTick());
        }

        String myName = mc.player.getScoreboardName();
        com.example.aas.world.AASWorldData.Squad mySquad = null;

        for (com.example.aas.world.AASWorldData.Squad s : ClientData.clientSquads) {
            if (s.members.contains(myName)) {
                mySquad = s;
                break;
            }
        }

        if (mySquad == null) return;

        // Определяем роль игрока
        boolean isSL = mySquad.leader.equals(myName);
        boolean isBravo = mySquad.bravoMembers.contains(myName) || mySquad.bravoLeader.equals(myName);
        boolean isCharlie = mySquad.charlieMembers.contains(myName) || mySquad.charlieLeader.equals(myName);

        PoseStack poseStack = event.getPoseStack();
        Vec3 cameraPos = event.getCamera().getPosition();
        long gameTime = mc.level.getGameTime();
        float blinkAlpha = 0.45f + (float)Math.sin(gameTime * 0.4f) * 0.25f;

        // --- 1. ПИНГИ (ГЛАЗА) ---
        // Пинг лидера видят ВСЕ в отряде
        if (mySquad.pingPos != null && gameTime < mySquad.pingExpiry) {
            render3DMarker(poseStack, cameraPos, mySquad.pingPos, PING_TEXTURE, mc, blinkAlpha, 0.5);
        }
        // Пинги Bravo/Charlie теперь видит весь отряд, а не только СЛ и участников соответствующего фаертима
        if (mySquad.bravoPingPos != null && gameTime < mySquad.bravoPingExpiry) {
            render3DMarker(poseStack, cameraPos, mySquad.bravoPingPos, PING_TEX_BRAVO, mc, blinkAlpha, 0.5);
        }
        if (mySquad.charliePingPos != null && gameTime < mySquad.charliePingExpiry) {
            render3DMarker(poseStack, cameraPos, mySquad.charliePingPos, PING_TEX_CHARLIE, mc, blinkAlpha, 0.5);
        }

        // --- 2. Метки SL / Bravo FTL / Charlie FTL (Move / Attack / Defend / Build) ---
        // В мире рисуем ТОЛЬКО метки, поставленные в мире (пинг / колесо): isPhysical == true.
        // Метки, поставленные через карту (isPhysical == false), остаются только на карте и компасе.
        renderWorldSquadMarker(poseStack, cameraPos, mySquad.marker, 0, gameTime, mc);
        renderWorldSquadMarker(poseStack, cameraPos, mySquad.bravoMarker, 1, gameTime, mc);
        renderWorldSquadMarker(poseStack, cameraPos, mySquad.charlieMarker, 2, gameTime, mc);
    }

    /** group: 0 = SL, 1 = Bravo, 2 = Charlie. */
    private static void renderWorldSquadMarker(PoseStack poseStack, Vec3 cameraPos,
                                               com.example.aas.world.AASWorldData.SquadMarker m,
                                               int group, long gameTime, Minecraft mc) {
        if (m == null || !m.isPhysical || gameTime >= m.expiryTick) return;

        ResourceLocation tex;
        if (m.type == 0) {
            tex = group == 1 ? MOVE_TEX_BRAVO : group == 2 ? MOVE_TEX_CHARLIE : MOVE_TEXTURE;
        } else {
            tex = radialMarkerTexture(m.type, group);
        }
        if (tex == null) return;

        render3DMarker(poseStack, cameraPos, new BlockPos(m.x, m.y, m.z), tex, mc, 1.0f, 0.5);
    }

    /** type: 1-Attack, 2-Defend, 3-Build. squadGroup: 0-SL, 1-Bravo, 2-Charlie. */
    private static ResourceLocation radialMarkerTexture(int type, int squadGroup) {
        switch (type) {
            case 1: return squadGroup == 1 ? MARKER_ATTACK_BRAVO : squadGroup == 2 ? MARKER_ATTACK_CHARLIE : MARKER_ATTACK_3D;
            case 2: return squadGroup == 1 ? MARKER_DEFEND_BRAVO : squadGroup == 2 ? MARKER_DEFEND_CHARLIE : MARKER_DEFEND_3D;
            case 3: return squadGroup == 1 ? MARKER_BUILD_BRAVO  : squadGroup == 2 ? MARKER_BUILD_CHARLIE  : MARKER_BUILD_3D;
            default: return null;
        }
    }
    /** 0 = SL, 1 = Bravo FTL, 2 = Charlie FTL, -1 = не лидер. */
    private static int getMySquadGroup(Minecraft mc) {
        String myName = mc.player.getScoreboardName();
        for (com.example.aas.world.AASWorldData.Squad s : ClientData.clientSquads) {
            if (s.leader.equals(myName)) return 0;
            if (s.bravoLeader.equals(myName)) return 1;
            if (s.charlieLeader.equals(myName)) return 2;
        }
        return -1;
    }
    @SubscribeEvent
    public static void onRenderOverlayPre(RenderGuiOverlayEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Если прицел (Crosshair) готовится к рендеру
        if (event.getOverlay() == net.minecraftforge.client.gui.overlay.VanillaGuiOverlay.CROSSHAIR.type()) {
            boolean usingBinoculars = mc.player.isUsingItem() && mc.player.getUseItem().getItem() == ModItems.BINOCULARS.get();
            Entity vehicle = mc.player.getVehicle();
            boolean aimingInVehicle = (vehicle instanceof AGS30Entity ags && ags.isAiming()) ||
                    (vehicle instanceof M2BrowningEntity m2 && m2.isAiming());

            if (usingBinoculars || aimingInVehicle || ClientData.reviveProgressOther >= 0f) {
                event.setCanceled(true);
            }
        }

        // НОВОЕ: скрываем ванильный список игроков (TAB) пока матч идёт,
        // т.к. TAB занят под открытие карты
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

        // Проверяем роль: только SL или FTL могут ставить метку
        for (com.example.aas.world.AASWorldData.Squad s : ClientData.clientSquads) {
            if (s.leader.equals(myName) || s.bravoLeader.equals(myName) || s.charlieLeader.equals(myName)) {
                canPing = true;
                break;
            }
        }

        if (canPing) {
            boolean isShift = net.minecraft.client.gui.screens.Screen.hasShiftDown();
            PacketHandler.INSTANCE.sendToServer(new PacketPlacePing(isShift));
            mc.player.playSound(ModSounds.MAP_MARKER_PLACE.get(), 1.0f, 1.0f);
            return true; // метку реально поставили
        }
        return false; // не было прав — pick-block не трогаем
    }
    /**
     * Нажатие клавиши/кнопки установки метки.
     * Shift зажат -> как и раньше, мгновенно ставим метку движения (не трогаем).
     * Без Shift -> НЕ ставим метку сразу, а запускаем таймер удержания:
     *   - если клавиша отпущена быстро (< RADIAL_HOLD_MS) -> обычный "глазик" (см. handleRadialKeyRelease)
     *   - если клавиша удержана дольше порога -> открываем радиальное меню (см. onClientTick)
     */
    private static void handleRadialKeyPress(Minecraft mc) {
        pingKeyPhysicallyDown = true; // ← добавили

        if (net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
            tryPlacePingLogic(mc);
            return;
        }
        radialKeyHeld = true;
        radialScreenOpened = false;
        radialKeyPressTime = System.currentTimeMillis();
    }

    /**
     * Отпускание клавиши/кнопки установки метки.
     * Если меню уже было открыто по удержанию — про закрытие/установку метки
     * заботится сам WorldMarkerRadialScreen (его mouseReleased/keyReleased).
     * Если меню НЕ успело открыться (короткий тап) — ставим обычную метку-глаз,
     * как это было раньше при обычном клике.
     */
    private static void handleRadialKeyRelease(Minecraft mc) {
        pingKeyPhysicallyDown = false; // ← добавили, ДО проверки на radialKeyHeld

        if (!radialKeyHeld) return;
        boolean wasScreenOpened = radialScreenOpened;
        radialKeyHeld = false;
        radialScreenOpened = false;
        if (!wasScreenOpened) {
            tryPlacePingLogic(mc);
        }
    }
    /**
     * Универсальный вспомогательный метод для отрисовки 3D маркера
     */
    private static void render3DMarker(PoseStack poseStack, Vec3 cameraPos, BlockPos pos,
                                       ResourceLocation texture, Minecraft mc, float alpha, double heightOffset) {
        poseStack.pushPose();

        double renderX = pos.getX() + 0.5 - cameraPos.x;
        double renderY = pos.getY() + heightOffset - cameraPos.y;
        double renderZ = pos.getZ() + 0.5 - cameraPos.z;
        poseStack.translate(renderX, renderY, renderZ);

        poseStack.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());

        float scale = 0.025f;
        double distSq = mc.player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        if (distSq > 100) {
            scale *= (float) (Math.sqrt(distSq) / 10.0);
        }
        scale = Math.min(scale, 0.25f);
        poseStack.scale(-scale, -scale, scale);

        Matrix4f matrix = poseStack.last().pose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.setShaderTexture(0, texture);
        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buf = tesselator.getBuilder();
        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        int a = Mth.clamp((int) (alpha * 255f), 0, 255);
        float h = 8f; // половина размера иконки (16x16 в юнитах метки)
        buf.vertex(matrix, -h, -h, 0).uv(0f, 0f).color(255, 255, 255, a).endVertex();
        buf.vertex(matrix, -h,  h, 0).uv(0f, 1f).color(255, 255, 255, a).endVertex();
        buf.vertex(matrix,  h,  h, 0).uv(1f, 1f).color(255, 255, 255, a).endVertex();
        buf.vertex(matrix,  h, -h, 0).uv(1f, 0f).color(255, 255, 255, a).endVertex();
        tesselator.end();

        RenderSystem.enableDepthTest();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
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

        if (ModKeyBindings.PLACE_PING_KEY.matchesMouse(event.getButton())) {
            if (event.getAction() == GLFW.GLFW_PRESS) {
                if (mc.screen == null) {
                    event.setCanceled(true); // не даём сконфликтовать с Pick Block
                    handleRadialKeyPress(mc);
                }
            } else if (event.getAction() == GLFW.GLFW_RELEASE) {
                handleRadialKeyRelease(mc);
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
            // Зум для бинокля
            ItemStack activeStack = mc.player.getUseItem();
            if (activeStack.getItem() == ModItems.BINOCULARS.get()) {
                event.setNewFovModifier(event.getFovModifier() * (1.0f / 8.0f));
            }

            // Ваша существующая логика для техники...
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
            System.out.println("DEBUG: Loading Binocular Shader"); // ТЕСТ
            mc.tell(() -> mc.gameRenderer.loadEffect(BINOCULAR_SHADER));
            wasUsingBinoculars = true;
        } else if (!isUsingBinoculars && wasUsingBinoculars) {
            System.out.println("DEBUG: Shutting down Shader"); // ТЕСТ
            mc.tell(() -> mc.gameRenderer.shutdownEffect());
            wasUsingBinoculars = false;
        }
    }


    @SubscribeEvent
    public static void onOpenGui(ScreenEvent.Opening event) {
        if (event.getScreen() instanceof DeathScreen && !(event.getScreen() instanceof AASDeathScreen)) {
            // Если игрок умер, но таймер уже запущен (был в нокауте), НЕ ПЕРЕЗАПИСЫВАЕМ
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
        // --- ЛОГИКА ГОЛОСОВАНИЯ ЗА CMD (F7/F8) ---
        String myTeam = mc.player.getTeam() != null ? mc.player.getTeam().getName().toUpperCase() : "NEUTRAL";
        boolean isBlue = myTeam.equals("BLUE");

        // Проверяем статус голосования именно для СВОЕЙ команды
        boolean myTeamCmdActive = isBlue ? ClientData.blueCmdVoteActive : ClientData.redCmdVoteActive;
        String myCandidateName = isBlue ? ClientData.blueCmdCandidateName : ClientData.redCmdCandidateName;

        if (myTeamCmdActive && mc.screen == null) {
            // Кандидат не может голосовать за самого себя
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
        if (ModKeyBindings.PLACE_PING_KEY.matches(event.getKey(), event.getScanCode())) {
            if (event.getAction() == GLFW.GLFW_PRESS) {
                if (mc.screen == null) {
                    handleRadialKeyPress(mc);
                }
            } else if (event.getAction() == GLFW.GLFW_RELEASE) {
                handleRadialKeyRelease(mc);
            }
            return;
        }
        // 1. ЛОГИКА ГОЛОСОВАНИЯ (F9/F10)
        if (ClientData.voteActive && mc.screen == null && event.getAction() == GLFW.GLFW_PRESS) {
            if (event.getKey() == GLFW.GLFW_KEY_F9) {
                PacketHandler.INSTANCE.sendToServer(new PacketVoteAction(true));
                // event.setCanceled(true); // УДАЛЕНО, так как вызывает краш
                return;
            } else if (event.getKey() == GLFW.GLFW_KEY_F10) {
                PacketHandler.INSTANCE.sendToServer(new PacketVoteAction(false));
                // event.setCanceled(true); // УДАЛЕНО, так как вызывает краш
                return;
            }
        }

        // 2. СБРОС ЯЩИКА (X)
        if (event.getAction() == GLFW.GLFW_PRESS && ModKeyBindings.DROP_SUPPLY_KEY.matches(event.getKey(), event.getScanCode())) {
            if (mc.player.getVehicle() != null) {
                PacketHandler.INSTANCE.sendToServer(new PacketDropCrate());
            }
        }

        // 3. МЕНЮ ОТРЯДОВ (K)
        if (event.getAction() == GLFW.GLFW_PRESS && ModKeyBindings.OPEN_SQUAD_MENU_KEY.matches(event.getKey(), event.getScanCode())) {
            // РАЗРЕШАЕМ открытие, если экран пустой ИЛИ если открыт экран нока
            if (mc.screen == null || mc.screen instanceof DownedScreen) {
                String teamName = (mc.player.getTeam() != null) ? mc.player.getTeam().getName() : "";
                boolean isValidTeam = teamName.equalsIgnoreCase("Blue") || teamName.equalsIgnoreCase("Red");

                if (!isValidTeam) {
                    mc.setScreen(new TeamSelectionScreen());
                } else {
                    mc.setScreen(new SquadSelectionScreen());
                }
            }
        }

        // 4. КАРТА (TAB)
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
        // 5. СТАТИСТИКА (Caps Lock, перебиндится в настройках управления)
        if (event.getAction() == GLFW.GLFW_PRESS && ModKeyBindings.OPEN_STATS_KEY.matches(event.getKey(), event.getScanCode())) {
            if (mc.screen == null) {
                if (ClientData.lastVictory != null) {
                    // После конца матча CAPS снова открывает экран победы (до /aas resetvictoryscreen или нового матча)
                    mc.setScreen(new com.example.aas.client.gui.VictoryScreen(ClientData.lastVictory, true));
                } else {
                    mc.setScreen(new com.example.aas.client.gui.StatisticsScreen());
                }
            } else if (mc.screen instanceof com.example.aas.client.gui.VictoryScreen) {
                mc.setScreen(null);
            } else if (mc.screen instanceof com.example.aas.client.gui.StatisticsScreen) {
                mc.setScreen(null); // повторное нажатие закрывает экран
            }
        }
    }

    @SubscribeEvent
    public static void onGuiOpen(ScreenEvent.Opening event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        if (AASConfig.PREVENT_VEHICLE_INVENTORY_ACCESS.get() &&
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
    private static float initialPitchOnFall = 0f; // Фиксируем взгляд в момент удара
    private static boolean wasDownedLastFrame = false;

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        boolean isDowned = mc.player.getPersistentData().getBoolean("AAS_IsDowned") && mc.player.isAlive();

        // 1. Логика входа в состояние (фиксация начальной точки)
        if (isDowned) {
            if (!wasDownedLastFrame) {
                initialPitchOnFall = event.getPitch(); // Запоминаем, куда смотрели
                wasDownedLastFrame = true;
            }
            if (downedCameraProgress < 1.0f) downedCameraProgress += 0.07f; // Скорость "падения"
        } else {
            wasDownedLastFrame = false;
            if (downedCameraProgress > 0.0f) downedCameraProgress -= 0.05f;
        }

        if (downedCameraProgress > 0) {
            float p = downedCameraProgress;

            // 2. ЭФФЕКТ "РУХНУВШЕГО ТЕЛА"
            // Сначала (первые 30% падения) персонаж немного "клюёт" вниз (stumble)
            float stumble = 0;
            if (p < 0.4f) {
                stumble = Mth.sin(p * (float)Math.PI * 2.5f) * 12f;
            }

            // Основной переход к -60 градусам
            float targetPitch = -60.0f;
            float smoothPitch = Mth.lerp(p, initialPitchOnFall, targetPitch) + stumble;
            event.setPitch(smoothPitch);

            // 3. НАКЛОН ГОЛОВЫ (Roll)
            // При падении голова заваливается на бок на 35 градусов
            event.setRoll(p * 35f);

            // 4. ДИНАМИЧЕСКАЯ ТРЯСКА
            // В момент "приземления" (когда p > 0.8) добавляем лёгкий удар
            if (isDowned && p > 0.8f) {
                float force = (p - 0.8f) * 10f;
                event.setYaw(event.getYaw() + (mc.level.random.nextFloat() - 0.5f) * force);
                event.setPitch(event.getPitch() + (mc.level.random.nextFloat() - 0.5f) * force);
            }

            // 5. ИМИТАЦИЯ ДЫХАНИЯ (когда уже лежит)
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
            if (!(e instanceof Player p) || p == mc.player) continue;
            // ВАЖНО: persistentData("AAS_IsDowned") на клиенте проставляется ТОЛЬКО для
            // ЛОКАЛЬНОГО игрока (см. ClientHooks.handleDownedState). Для ВСЕХ остальных
            // игроков статус "в ноке" синхронизируется только через ClientData.DOWNED_PLAYERS,
            // поэтому проверка на persistentData здесь всегда возвращала false для союзников
            // и F никогда не находил цель для подъёма.
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
     * Ищет раненого союзника, на которого игрок сейчас навёл взгляд, держа в руке
     * правильный предмет для подъёма (AASConfig.REVIVE_ITEM). Используется ТОЛЬКО для
     * визуальной подсказки — сам подъём по-прежнему работает через findNearestDownedAlly
     * (по ближайшему в радиусе), чтобы не ломать возможность поднять игрока,
     * лежащего почти вплотную (у них очень низкий хитбокс в позе SWIMMING, и узкий
     * рейкаст по нему часто промахивается).
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
            // См. комментарий в findNearestDownedAlly — для чужих игроков нужно проверять
            // именно ClientData.DOWNED_PLAYERS, а не их persistentData.
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
     * Иконный 3D-маркер над раненым: чёрный полупрозрачный квадрат с белой обводкой,
     * буква "F" в центре и подпись справа.
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

        // Размер маркера уменьшен в 4 раза по сравнению с исходным (0.025f -> 0.00625f)
        float scale = 0.00625f;
        double dist = mc.player.distanceTo(target);
        scale *= (float) Math.max(1.0, dist / 2.5);
        scale = Math.min(scale, 0.0125f);
        poseStack.scale(-scale, -scale, scale);

        Matrix4f matrix = poseStack.last().pose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();

        // --- Идёт подъём: вместо буквы F рисуем иконку с кольцом ---
        float revProgress = ClientData.reviveProgressOther;
        if (revProgress >= 0f) {
            com.example.aas.client.gui.ReviveIconRenderer.draw(
                    matrix, REVIVE_MARKER_ICON, 0f, 0f, 20f, 12.5f, 15.5f, revProgress);
            RenderSystem.enableDepthTest();
            poseStack.popPose();
            return;
        }

        // --- Обычная подсказка: рамка без заливки + буква + текст ---
        RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buf = tesselator.getBuilder();

        float half = 13f;
        float t = 2.2f; // толщина обводки

        buf.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        quad(buf, matrix, -half, -half, half, -half + t, 255, 255, 255, 255);   // верх
        quad(buf, matrix, -half, half - t, half, half, 255, 255, 255, 255);     // низ
        quad(buf, matrix, -half, -half, -half + t, half, 255, 255, 255, 255);   // лево
        quad(buf, matrix, half - t, -half, half, half, 255, 255, 255, 255);     // право
        tesselator.end();

        RenderSystem.setShaderColor(1, 1, 1, 1);

        Font font = mc.font;
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();

        String letter = getReviveKeyLabel();
        float lw = font.width(letter);
        font.drawInBatch(letter, -lw / 2f, -4f, 0xFFFFFFFF, false,
                matrix, buffer, Font.DisplayMode.SEE_THROUGH, 0, 15728880);

        poseStack.pushPose();
        poseStack.translate(half + 6f, -4f, 0f);
        Matrix4f textMatrix = poseStack.last().pose();
        font.drawInBatch(I18n.get("aas.hud.revive_prompt"), 0, 0, 0xFFFFFFFF, false,
                textMatrix, buffer, Font.DisplayMode.SEE_THROUGH, 0, 15728880);
        poseStack.popPose();

        buffer.endBatch();

        RenderSystem.enableDepthTest();
        poseStack.popPose();
    }

    /**
     * Возвращает отображаемое имя текущей клавиши подъёма (ModKeyBindings.REVIVE_KEY).
     * Для букв и цифр (A-Z, 0-9) всегда возвращает латинский символ напрямую из
     * GLFW-кода клавиши (он равен ASCII-коду), чтобы буква не переводилась/не
     * зависела от языка игры. Для остальных клавиш (Shift, пробел, кнопки мыши и т.д.)
     * используется ванильное отображаемое имя клавиши.
     */
    private static String getReviveKeyLabel() {
        InputConstants.Key key = ModKeyBindings.REVIVE_KEY.getKey();

        if (key.getType() == InputConstants.Type.KEYSYM) {
            int v = key.getValue();
            if (v >= GLFW.GLFW_KEY_A && v <= GLFW.GLFW_KEY_Z) {
                return String.valueOf((char) v); // GLFW_KEY_A..Z совпадают с ASCII 'A'..'Z'
            }
            if (v >= GLFW.GLFW_KEY_0 && v <= GLFW.GLFW_KEY_9) {
                return String.valueOf((char) v); // GLFW_KEY_0..9 совпадают с ASCII '0'..'9'
            }
        }

        // не-буквенная клавиша (Shift, пробел, кнопка мыши и т.д.) — обычное отображение
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