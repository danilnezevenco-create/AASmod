package com.example.aas.client;

import com.example.aas.block.*;
import com.example.aas.config.AASConfig;
import com.example.aas.entity.AGS30Entity;
import com.example.aas.entity.M2BrowningEntity;
import com.example.aas.item.AGSAmmoItem;
import com.example.aas.item.M2AmmoItem;
import com.example.aas.item.ModItems;
import net.minecraft.client.resources.language.I18n;
import com.example.aas.world.AASWorldData;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.example.aas.client.gui.AASMapRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = "aas", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AASOverlay {

    // === РЕСУРСЫ ФЛАГОВ ===
    private static final ResourceLocation FLAG_UKRAINE = new ResourceLocation("aas", "textures/gui/flags/ukraine.png");
    private static final ResourceLocation FLAG_RUSSIA = new ResourceLocation("aas", "textures/gui/flags/russia.png");
    private static final ResourceLocation FLAG_USA = new ResourceLocation("aas", "textures/gui/flags/usa.png");
    private static final ResourceLocation FLAG_NATO = new ResourceLocation("aas", "textures/gui/flags/nato.png");
    private static final ResourceLocation FLAG_BLUEFOR = new ResourceLocation("aas", "textures/gui/flags/bluefor.png");
    private static final ResourceLocation FLAG_REDFOR = new ResourceLocation("aas", "textures/gui/flags/redfor.png");
    private static final ResourceLocation FLAG_INSURGENCY = new ResourceLocation("aas", "textures/gui/flags/insurgency.png");
    private static final ResourceLocation FLAG_PMC = new ResourceLocation("aas", "textures/gui/flags/pmc.png");
    private static final ResourceLocation FLAG_GERMANY = new ResourceLocation("aas", "textures/gui/flags/germany.png");
    private static final ResourceLocation FLAG_MILITIA = new ResourceLocation("aas", "textures/gui/flags/militia.png");
    private static final ResourceLocation VIGNETTE_TEXTURE = new ResourceLocation("aas", "textures/misc/vignette.png");
    private static final ResourceLocation ARROW_TEX = new ResourceLocation("aas", "textures/gui/capture_arrow.png");
    private static final ResourceLocation BUILD_ICON = new ResourceLocation("aas", "textures/gui/build_icon.png");
    private static final ResourceLocation DIG_ICON = new ResourceLocation("aas", "textures/gui/dig_icon.png");
    private static final ResourceLocation SHOVEL_ICON = new ResourceLocation("aas", "textures/gui/shovel_icon.png");
    private static final ResourceLocation ICON_PLUS = new ResourceLocation("aas", "textures/gui/map_icons/medic_plus.png");
    private static final ResourceLocation ICON_PING = new ResourceLocation("aas", "textures/gui/map_icons/ping_eye.png");
    private static final ResourceLocation MOVE_TEXTURE = new ResourceLocation("aas", "textures/gui/map_icons/marker_move.png");
    private static final ResourceLocation MOVE_TEX_BRAVO = new ResourceLocation("aas", "textures/gui/map_icons/marker_move_bravo.png");
    private static final ResourceLocation PING_TEX_BRAVO = new ResourceLocation("aas", "textures/gui/map_icons/ping_eye_bravo.png");
    private static final ResourceLocation MOVE_TEX_CHARLIE = new ResourceLocation("aas", "textures/gui/map_icons/marker_move_charlie.png");
    private static final ResourceLocation PING_TEX_CHARLIE = new ResourceLocation("aas", "textures/gui/map_icons/ping_eye_charlie.png");
    // Нам понадобится метод получения иконок, похожий на тот, что в рендерере карты
    private static AASMapRenderer HUD_SIDE_MAP;
    // === НОВЫЕ ТЕКСТУРЫ ДЛЯ ПОДСКАЗКИ СТРОИТЕЛЬСТВА ===
    private static final ResourceLocation MOUSE_LEFT = new ResourceLocation("aas", "textures/gui/dig_icon.png");
    private static final ResourceLocation MOUSE_RIGHT = new ResourceLocation("aas", "textures/gui/build_icon.png");
    private static final ResourceLocation ICON_ROTATE = new ResourceLocation("aas", "textures/gui/icon_rotate.png");
    private static final ResourceLocation ICON_CONFIRM = new ResourceLocation("aas", "textures/gui/icon_confirm.png");

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        // МЕНЯЕМ HOTBAR НА CHAT_PANEL: Чат никогда не скрывается модами на технику!
        if (event.getOverlay() != VanillaGuiOverlay.CHAT_PANEL.type()) return;

        GuiGraphics gui = event.getGuiGraphics();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();

        // 1. Тикеты
        renderTickets(gui, mc, width);
        renderVotePanel(gui, mc, height);

        // 2. Логика захвата точек
        if (ClientData.allCapturePoints != null) {
            for (AASWorldData.CapturePoint cp : ClientData.allCapturePoints) {
                if (cp.intersectsAny(mc.player.getBoundingBox())) {
                    renderCapturePoint(gui, mc, width, height);
                    break;
                }
            }
        }

        // 3. Остальные элементы интерфейса
        renderBuildProgress(gui, mc, width, height);
        renderHubMaterials(gui, mc, width, height);
        renderVehicleAmmo(gui, mc, width, height);
        renderSupplyTruckInfo(gui, mc, width, height);
        renderPlacementHints(gui, mc, width, height);
        renderCaptureNotifications(gui, mc, width, height);
        renderCompass(gui, mc, width, event.getPartialTick());
        renderCMDVotePanel(gui, mc, width);
        renderArtStrikeRequest(gui, mc, width);
        renderReviveProgress(gui, mc, width, height);
        renderSquadLeaderPlaytime(gui, mc, width);
        // === ДОПИСАННАЯ ЧАСТЬ ДЛЯ НОКОВ ===

        // 4. Рендеринг виньетки для самого игрока (ИСПРАВЛЕНО: заменено player на mc.player)
        if (mc.player.getPersistentData().getBoolean("AAS_IsDowned")) {
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();

            // Устанавливаем очень темный красный цвет и высокую непрозрачность (0.9f)
            RenderSystem.setShaderColor(0.5f, 0f, 0f, 0.9f);
            gui.blit(VIGNETTE_TEXTURE, 0, 0, 0, 0, width, height, width, height);

            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }

        /// 5. Рендеринг списка раненых для Медика
        String currentKit = mc.player.getPersistentData().getString("AAS_CurrentKit");
        if ("Medic".equalsIgnoreCase(currentKit)) {
            renderMedicUI(gui, mc, width);
        }
        renderRadioSpeakers(gui, mc, height);
        renderVoiceSpeakers(gui, mc, height);
        if (ClientData.isMapOpen || ClientData.mapTransition > 0) {
            renderSideMap(gui, mc, width, height, event.getPartialTick());
        }
        if (ClientData.isGameStarted && ClientData.invasionPrepTicks > 0 && ClientData.gameMode.equalsIgnoreCase("INVASION")) {
            renderPrepTimer(gui, mc, width);
        }
    }
    private static void renderPrepTimer(GuiGraphics gui, Minecraft mc, int screenWidth) {
        int ticks = ClientData.invasionPrepTicks;
        int totalSeconds = ticks / 20;
        int mins = totalSeconds / 60;
        int secs = totalSeconds % 60;
        String timeStr = String.format("%02d:%02d", mins, secs);

        // Логика мигания красным (меньше минуты)
        int textColor = 0xFFFFFFFF;
        if (totalSeconds < 60) {
            if ((System.currentTimeMillis() / 500) % 2 == 0) {
                textColor = 0xFFFF5555;
            }
        }

        // Масштабирование под компас
        int configVal = AASConfig.COMPASS_SCALE.get();
        float scaleFactor = (configVal == 1) ? 1f / 1.5f : (configVal == 3) ? 1.5f : 1.0f;

        gui.pose().pushPose();
        gui.pose().translate(screenWidth / 2f, 0, 0);
        gui.pose().scale(scaleFactor, scaleFactor, 1.0f);

        // === ИЗМЕНЕНИЯ ТУТ ===
        int yStart = 55;   // БЫЛО 38. Теперь таймер опущен ниже.
        int boxWidth = 36; // Немного сузил рамку, так как теперь в ней только цифры
        int boxHeight = 11;
        // =====================

        // 1. Рисуем рамку и фон
        gui.fill(-boxWidth / 2, yStart, boxWidth / 2, yStart + boxHeight, 0x90000000);
        gui.renderOutline(-boxWidth / 2, yStart, boxWidth, boxHeight, 0xFFFFFFFF);

        // 2. Текст "PREPARATION" УДАЛЕН отсюда.

        // 3. Рисуем только время 00:00
        // Центрируем текст по вертикали внутри новой рамки
        gui.drawCenteredString(mc.font, timeStr, 0, yStart + 2, textColor);

        gui.pose().popPose();
    }
    private static final long SQUAD_LEADER_PLAYTIME_DURATION_MS = 10_000L;

    private static void renderSquadLeaderPlaytime(GuiGraphics gui, Minecraft mc, int screenWidth) {
        if (mc.options.hideGui) return;
        if (!ClientData.showSquadLeaderPlaytime || ClientData.squadLeaderPlaytimeHours < 0) return;

        long elapsed = System.currentTimeMillis() - ClientData.squadLeaderPlaytimeShownAt;
        if (elapsed > SQUAD_LEADER_PLAYTIME_DURATION_MS) {
            ClientData.showSquadLeaderPlaytime = false;
            return;
        }

        String text = I18n.get("aas.hud.squad_leader_playtime",
                ClientData.squadLeaderPlaytimeName, ClientData.squadLeaderPlaytimeHours);

        int paddingX = 6;
        int paddingY = 4;
        int textWidth = mc.font.width(text);
        int boxWidth = textWidth + paddingX * 2;
        int boxHeight = mc.font.lineHeight + paddingY * 2;

        int x2 = screenWidth - 6;
        int x1 = x2 - boxWidth;
        int y1 = 34; // было 6 — опустили ниже, под тикеты/компас
        int y2 = y1 + boxHeight;

        gui.fill(x1, y1, x2, y2, 0x90000000);
        gui.renderOutline(x1, y1, boxWidth, boxHeight, 0x40FFFFFF);
        gui.drawString(mc.font, text, x1 + paddingX, y1 + paddingY, 0xFFFFFFFF, true);
    }
    // Добавьте вызов этого метода в onRenderOverlay (в самый конец)
// renderCaptureNotifications(gui, mc, height);
    private static void renderCompass(GuiGraphics gui, Minecraft mc, int screenWidth, float partialTick) {
        if (mc.options.hideGui) return;

        // 1. Получаем актуальное значение из конфига
        int configVal = AASConfig.COMPASS_SCALE.get();

        // 2. РАСЧЕТ МАСШТАБА (Используем configVal!)
        float scaleFactor = 1.0f;
        if (configVal == 1) {
            scaleFactor = 1f / 1.5f; // Уменьшить
        } else if (configVal == 3) {
            scaleFactor = 1.5f;      // Увеличить
        }
        // Если configVal == 2, scaleFactor останется 1.0f (стандарт)

        float centerX = screenWidth / 2f;
        float y = 8f;
        float baseWidth = 320f;
        // Ширина области отсечения тоже должна масштабироваться
        float scaledWidth = baseWidth * scaleFactor;
        float visibleRange = 70f;
        float pixelsPerDegree = scaledWidth / visibleRange;

        // Применяем трансформацию ко всему блоку компаса
        gui.pose().pushPose();

        // Сдвигаем матрицу к точке рендеринга, масштабируем и возвращаем назад
        gui.pose().translate(centerX, y, 0);
        gui.pose().scale(scaleFactor, scaleFactor, 1.0f);
        gui.pose().translate(-centerX, -y, 0);

        // 1. ЦЕНТРАЛЬНЫЕ ЭЛЕМЕНТЫ
        gui.pose().pushPose();
        gui.pose().translate(centerX, y + 2, 100);
        gui.pose().scale(0.8f, 0.8f, 1.0f);
        drawOutlinedString(gui, mc, "▼", -(mc.font.width("▼") / 2), 0, 0xFFFFFFFF);
        gui.pose().popPose();

        float playerYaw = (mc.player.getViewYRot(partialTick) % 360 + 360) % 360;

        String bearing = String.valueOf((int) playerYaw);
        gui.pose().pushPose();
        gui.pose().translate(centerX, y + 26, 100);
        drawOutlinedString(gui, mc, bearing, -(mc.font.width(bearing) / 2), 0, 0xFFFFFFFF);
        gui.pose().popPose();

        // 2. ОГРАНИЧЕНИЕ ОБЛАСТИ (Scissor)
        // Важно: Scissor работает в реальных пикселях экрана, поэтому считаем координаты аккуратно
        gui.enableScissor((int)(centerX - scaledWidth / 2), 0, (int)(centerX + scaledWidth / 2), screenHeight());

        // 3. ОТРИСОВКА ШКАЛЫ
        for (int i = (int)(playerYaw - visibleRange / 2) - 1; i <= (int)(playerYaw + visibleRange / 2) + 1; i++) {
            int degree = (i % 360 + 360) % 360;
            float xPos = centerX + (i - playerYaw) * pixelsPerDegree;

            float diffFromCenter = Math.abs(xPos - centerX);
            float edgeFading = 1.0f - (float) Math.pow(diffFromCenter / (scaledWidth / 2f), 2);
            if (edgeFading <= 0.02f) continue;
            int alpha = (int)(edgeFading * 255);

            if (degree % 15 == 0) {
                drawSmoothLine(gui, xPos, y + 10, 1.2f, 6, (alpha << 24) | 0xFFFFFF);
                String label = getDirectionLabel(degree);
                int txtCol = !Character.isDigit(label.charAt(0)) ? (alpha << 24) | 0x88CCFF : (alpha << 24) | 0xFFFFFF;
                gui.pose().pushPose();
                gui.pose().translate(xPos, y + 18, 0);
                gui.pose().scale(0.75f, 0.75f, 1.0f);
                drawOutlinedStringWithAlpha(gui, mc, label, -(mc.font.width(label) / 2), 0, txtCol, alpha);
                gui.pose().popPose();
            } else if (degree % 5 == 0) {
                drawSmoothLine(gui, xPos, y + 12, 1.0f, 4, (alpha << 24) | 0xFFFFFF);
            } else {
                drawSmoothLine(gui, xPos, y + 14, 0.8f, 1.5f, (alpha << 24) | 0xFFFFFF);
            }
        }

        // 4. МАРКЕРЫ (передаем scaledWidth иpixelsPerDegree, чтобы они попали в сетку)
        renderMarkersOnCompass(gui, mc, playerYaw, centerX, y, pixelsPerDegree, scaledWidth, visibleRange);
        renderDownedOnCompass(gui, mc, playerYaw, centerX, y, pixelsPerDegree, scaledWidth, visibleRange);
        renderSquadPingOnCompass(gui, mc, playerYaw, centerX, y, pixelsPerDegree, scaledWidth, visibleRange);
        renderSquadMarkersOnCompass(gui, mc, playerYaw, centerX, y, pixelsPerDegree, scaledWidth, visibleRange);

        gui.disableScissor();
        gui.pose().popPose(); // Закрываем общую трансформацию масштаба
    }
    // В AASOverlay.java добавьте метод и вызовите его в renderCompass
    private static void renderSquadMarkersOnCompass(GuiGraphics gui, Minecraft mc, float playerYaw, float centerX, float y, float pixelsPerDegree, float widthInPixels, float visibleRange) {
        String myName = mc.player.getScoreboardName();
        AASWorldData.Squad mySquad = null;
        for (AASWorldData.Squad s : ClientData.clientSquads) {
            if (s.members.contains(myName)) { mySquad = s; break; }
        }
        if (mySquad == null) return;

        boolean isSL      = mySquad.leader.equals(myName);
        boolean isBravo   = mySquad.bravoMembers.contains(myName) || mySquad.bravoLeader.equals(myName);
        boolean isCharlie = mySquad.charlieMembers.contains(myName) || mySquad.charlieLeader.equals(myName);

        // Метка лидера — видят все
        if (mySquad.marker != null && mySquad.marker.type == 0) {
            drawSquadCompassMarker(gui, mc, playerYaw, centerX, y, pixelsPerDegree, widthInPixels, visibleRange, mySquad.marker, MOVE_TEXTURE);
        }

        // Bravo — свои видят всегда, чужие (Charlie) тоже видят но иконка та же
        if (mySquad.bravoMarker != null && mySquad.bravoMarker.type == 0) {
            // Видят все кроме тех, кто не в отряде вообще — но мы уже внутри mySquad
            drawSquadCompassMarker(gui, mc, playerYaw, centerX, y, pixelsPerDegree, widthInPixels, visibleRange, mySquad.bravoMarker, MOVE_TEX_BRAVO);
        }

        // Charlie — аналогично
        if (mySquad.charlieMarker != null && mySquad.charlieMarker.type == 0) {
            drawSquadCompassMarker(gui, mc, playerYaw, centerX, y, pixelsPerDegree, widthInPixels, visibleRange, mySquad.charlieMarker, MOVE_TEX_CHARLIE);
        }
    }

    private static void drawSquadCompassMarker(GuiGraphics gui, Minecraft mc, float pYaw, float cX, float y, float ppd, float wPix, float vRange, AASWorldData.SquadMarker m, ResourceLocation icon) {
        double dist = Math.sqrt(mc.player.distanceToSqr(m.x + 0.5, mc.player.getY(), m.z + 0.5));
        if (dist > 5000) return;

        double dx = (m.x + 0.5) - mc.player.getX();
        double dz = (m.z + 0.5) - mc.player.getZ();
        float angle = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;
        float diff = Mth.wrapDegrees(angle - pYaw);

        if (Math.abs(diff) < vRange / 2f + 5) {
            float xPos = cX + diff * ppd;
            float edgeFading = 1.0f - (float) Math.pow(Math.abs(xPos - cX) / (wPix / 2f), 4);
            renderCompassIcon(gui, icon, xPos, y - 2, 14, Math.max(0, edgeFading));
        }
    }

    // Получение иконки по типу (0-5)
    private static ResourceLocation getSquadMarkerIcon(int type) {
        switch (type) {
            case 1: return new ResourceLocation("aas", "textures/gui/map_icons/marker_attack.png");
            case 2: return new ResourceLocation("aas", "textures/gui/map_icons/marker_defend.png");
            case 3: return new ResourceLocation("aas", "textures/gui/map_icons/marker_build.png");
            case 5: return new ResourceLocation("aas", "textures/gui/map_icons/marker_attack.png"); // Enemy
            default: return new ResourceLocation("aas", "textures/gui/map_icons/marker_move.png"); // Move
        }
    }
    private static void renderMarkersOnCompass(GuiGraphics gui, Minecraft mc, float playerYaw, float centerX, float y, float pixelsPerDegree, float widthInPixels, float visibleRange) {
        String myTeam = (mc.player.getTeam() != null) ? mc.player.getTeam().getName().toUpperCase() : "NEUTRAL";

        for (AASWorldData.MapMarker m : ClientData.activeMarkers) {
            if (!m.team.equalsIgnoreCase(myTeam) && !mc.player.isCreative()) continue;

            double dist = Math.sqrt(mc.player.distanceToSqr(m.pos.getX() + 0.5, mc.player.getY(), m.pos.getZ() + 0.5));
            if (dist > 250) continue;

            // --- ИСПРАВЛЕННЫЙ РАСЧЕТ УГЛА ---
            double dx = (m.pos.getX() + 0.5) - mc.player.getX();
            double dz = (m.pos.getZ() + 0.5) - mc.player.getZ();

            // В Minecraft Yaw 0 = South (+Z). atan2(x, z) идеально ложится в эту логику.
            // Используем (dz, dx) и вычитаем 90 градусов для синхронизации с Yaw игрока
            float angle = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;
            float diff = Mth.wrapDegrees(angle - playerYaw);

            // Если разница углов попадает в видимую область компаса
            if (Math.abs(diff) < visibleRange / 2f + 5) {
                float xPos = centerX + diff * pixelsPerDegree;

                float distAlpha = 1.0f;
                if (dist > 150) distAlpha = 0.35f;
                else if (dist > 50) distAlpha = 0.70f;

                float edgeFading = 1.0f - (float) Math.pow(Math.abs(xPos - centerX) / (widthInPixels / 2f), 4);
                float finalAlpha = distAlpha * Math.max(0, edgeFading);

                if (finalAlpha > 0.05f) {
                    ResourceLocation icon = getMarkerIconLocal(m.type);
                    renderCompassIcon(gui, icon, xPos, y - 2, 12, finalAlpha);
                }
            }
        }
    }

    private static void renderDownedOnCompass(GuiGraphics gui, Minecraft mc, float playerYaw, float centerX, float y, float pixelsPerDegree, float widthInPixels, float visibleRange) {
        if (ClientData.mapPlayers == null) return;

        String myName = mc.player.getScoreboardName();
        // Определяем нашу команду
        String myTeam = "NEUTRAL";
        if (mc.player.getTeam() != null) {
            myTeam = mc.player.getTeam().getName().toUpperCase();
        }

        for (com.example.aas.network.MapPlayerInfo info : ClientData.mapPlayers.values()) {
            // 1. Пропускаем себя
            if (info.name.equals(myName)) continue;

            // 2. Проверяем, что игрок в ноке
            if (!info.isDowned) continue;

            // 3. Проверяем, что это союзник
            if (myTeam.equals("NEUTRAL") || !info.team.equalsIgnoreCase(myTeam)) continue;

            // 4. Расчет позиции через координаты из пакета
            double dx = info.x - mc.player.getX();
            double dz = info.z - mc.player.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);

            // Видим раненых на компасе в радиусе 200 метров
            if (dist > 50) continue;

            float angle = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;
            float diff = Mth.wrapDegrees(angle - playerYaw);

            if (Math.abs(diff) < visibleRange / 2f + 5) {
                float xPos = centerX + diff * pixelsPerDegree;

                // Затухание иконки по краям компаса
                float edgeFading = 1.0f - (float) Math.pow(Math.abs(xPos - centerX) / (widthInPixels / 2f), 4);

                // Эффект мигания для привлечения внимания
                float blink = 0.8f + (float) Math.sin(System.currentTimeMillis() / 200.0) * 0.2f;
                float finalAlpha = Math.max(0, edgeFading) * blink;

                if (finalAlpha > 0.05f) {
                    renderCompassIcon(gui, ICON_PLUS, xPos, y - 2, 10, finalAlpha);
                }
            }
        }
    }

    // PATH: src\main\java\com\example\aas\client\AASOverlay.java

    private static void renderSquadPingOnCompass(GuiGraphics gui, Minecraft mc, float playerYaw, float centerX, float y, float pixelsPerDegree, float widthInPixels, float visibleRange) {
        String myName = mc.player.getScoreboardName();
        AASWorldData.Squad mySquad = null;

        for (AASWorldData.Squad s : ClientData.clientSquads) {
            if (s.members.contains(myName)) {
                mySquad = s;
                break;
            }
        }

        if (mySquad == null) return;

        long time = mc.level.getGameTime();

        // 1. Пинг Squad Leader — видят все участники отряда
        if (mySquad.pingPos != null && time < mySquad.pingExpiry) {
            drawCompassPing(gui, mc, playerYaw, centerX, y, pixelsPerDegree, widthInPixels, visibleRange, mySquad.pingPos, ICON_PING);
        }

        // 2. Пинг Bravo — теперь видит весь отряд, а не только СЛ и Bravo
        if (mySquad.bravoPingPos != null && time < mySquad.bravoPingExpiry) {
            drawCompassPing(gui, mc, playerYaw, centerX, y, pixelsPerDegree, widthInPixels, visibleRange, mySquad.bravoPingPos, PING_TEX_BRAVO);
        }

        // 3. Пинг Charlie — теперь видит весь отряд, а не только СЛ и Charlie
        if (mySquad.charliePingPos != null && time < mySquad.charliePingExpiry) {
            drawCompassPing(gui, mc, playerYaw, centerX, y, pixelsPerDegree, widthInPixels, visibleRange, mySquad.charliePingPos, PING_TEX_CHARLIE);
        }
    }

    // Вспомогательный метод для уменьшения дублирования кода
    private static void drawCompassPing(GuiGraphics gui, Minecraft mc, float playerYaw, float centerX, float y, float pixelsPerDegree, float widthInPixels, float visibleRange, net.minecraft.core.BlockPos pingPos, ResourceLocation icon) {
        double dx = pingPos.getX() + 0.5 - mc.player.getX();
        double dz = pingPos.getZ() + 0.5 - mc.player.getZ();
        float angle = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90;
        float diff = net.minecraft.util.Mth.wrapDegrees(angle - playerYaw);

        if (Math.abs(diff) < visibleRange / 2f + 5) {
            float xPos = centerX + diff * pixelsPerDegree;

            // Затухание к краям компаса
            float edgeFading = 1.0f - (float) Math.pow(Math.abs(xPos - centerX) / (widthInPixels / 2f), 4);

            // Эффект мигания (пульсации)
            float blink = 0.8f + (float) Math.sin(mc.level.getGameTime() * 0.4f) * 0.2f;
            float finalAlpha = Math.max(0, edgeFading) * blink;

            if (finalAlpha > 0.05f) {
                renderCompassIcon(gui, icon, xPos, y - 2, 12, finalAlpha);
            }
        }
    }

    private static void renderCompassIcon(GuiGraphics gui, ResourceLocation icon, float x, float y, int size, float alpha) {
        gui.pose().pushPose();
        gui.pose().translate(x - (size / 2f), y - (size / 2f), 50);
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1, 1, 1, alpha);
        gui.blit(icon, 0, 0, 0, 0, size, size, size, size);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        gui.pose().popPose();
    }

    private static ResourceLocation getMarkerIconLocal(String type) {
        String path = type.toLowerCase().replace("enemy ", "").replace(" ", "_");
        return new ResourceLocation("aas", "textures/gui/map_icons/" + path + "_marker.png");
    }

    // Нужно добавить этот метод в AASOverlay, если его нет (для работы Scissor)
    private static int screenHeight() {
        return Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }

    private static void drawSmoothLine(GuiGraphics gui, float x, float y, float width, float height, int color) {
        gui.pose().pushPose();
        gui.pose().translate(x - (width / 2f), y, 0);
        // fill принимает int, но благодаря translate(float) выше, линия рисуется плавно
        gui.fill(0, 0, Math.max(1, (int)width), (int)height, color);
        gui.pose().popPose();
    }

    private static void drawOutlinedStringWithAlpha(GuiGraphics gui, Minecraft mc, String text, int x, int y, int color, int alpha) {
        int black = (alpha << 24);
        gui.drawString(mc.font, text, x - 1, y, black, false);
        gui.drawString(mc.font, text, x + 1, y, black, false);
        gui.drawString(mc.font, text, x, y - 1, black, false);
        gui.drawString(mc.font, text, x, y + 1, black, false);
        gui.drawString(mc.font, text, x, y, color, false);
    }

    private static String getDirectionLabel(int degree) {
        switch (degree) {
            case 0: return I18n.get("aas.compass.s");
            case 45: return I18n.get("aas.compass.sw");
            case 90: return I18n.get("aas.compass.w");
            case 135: return I18n.get("aas.compass.nw");
            case 180: return I18n.get("aas.compass.n");
            case 225: return I18n.get("aas.compass.ne");
            case 270: return I18n.get("aas.compass.e");
            case 315: return I18n.get("aas.compass.se");
            default: return String.valueOf(degree);
        }
    }
    private static void renderCMDVotePanel(GuiGraphics gui, Minecraft mc, int width) {
        String myTeam = mc.player.getTeam() != null ? mc.player.getTeam().getName().toUpperCase() : "NEUTRAL";
        if (myTeam.equals("NEUTRAL")) return;

        // 1. Выбираем данные в зависимости от команды игрока
        boolean isBlue = myTeam.equals("BLUE");
        boolean active = isBlue ? ClientData.blueCmdVoteActive : ClientData.redCmdVoteActive;
        if (!active) return;

        String candidateName = isBlue ? ClientData.blueCmdCandidateName : ClientData.redCmdCandidateName;
        Map<UUID, Boolean> currentVotes = isBlue ? ClientData.blueCmdVotes : ClientData.redCmdVotes;

        // 2. Фильтруем список: только лидеры моей команды (кроме кандидата)
        var slPlayers = ClientData.mapPlayers.values().stream()
                .filter(info -> info.team.equalsIgnoreCase(myTeam))
                .filter(info -> info.isLeader && !info.name.equals(candidateName))
                .toList();

        // 3. Параметры размеров
        int headerH = 32;
        int rowH = 12;
        int footerH = 15;
        int pWidth = 150;
        int pHeight = headerH + (slPlayers.size() * rowH) + footerH;
        int x = width - pWidth - 10;
        int y = 70;

        // 4. Рендер фона и рамки
        gui.fill(x, y, x + pWidth, y + pHeight, 0xAA000000);
        gui.renderOutline(x, y, pWidth, pHeight, 0xFF55FF55);

        // Используем локальную переменную candidateName
        gui.drawCenteredString(mc.font, I18n.get("aas.hud.cmd_vote", candidateName), x + pWidth/2, y + 5, 0xFFFFD700);

        gui.pose().pushPose();
        gui.pose().scale(0.75f, 0.75f, 1.0f);
        int sx = (int)((x + pWidth/2)/0.75f);
        gui.drawCenteredString(mc.font, I18n.get("aas.hud.cmd_vote_hint"), sx, (int)((y + 16)/0.75f), 0xFFAAAAAA);
        gui.pose().popPose();

        gui.fill(x + 5, y + 28, x + pWidth - 5, y + 29, 0x55FFFFFF);

        // 5. Список голосующих (только свои)
        int curY = y + headerH;
        for (var info : slPlayers) {
            Boolean vote = currentVotes.get(info.uuid);

            String icon = "○";
            int iconCol = 0xFFAAAAAA;

            if (vote != null) {
                icon = vote ? "✔" : "✘";
                iconCol = vote ? 0xFF55FF55 : 0xFFFF5555;
            }

            gui.drawString(mc.font, icon, x + 8, curY, iconCol, true);
            int nameCol = info.name.equals(mc.player.getScoreboardName()) ? 0xFFFFFF55 : 0xFFFFFFFF;
            gui.drawString(mc.font, info.name, x + 22, curY, nameCol, true);
            curY += rowH;
        }

        // 6. Подсказка по кнопкам
        if (!mc.player.getScoreboardName().equals(candidateName)) {
            gui.drawCenteredString(mc.font, I18n.get("aas.hud.cmd_vote_keys"), x + pWidth/2, y + pHeight - 12, 0xFFBBBBBB);
        } else {
            gui.drawCenteredString(mc.font, I18n.get("aas.hud.waiting_for_votes"), x + pWidth/2, y + pHeight - 12, 0xFF55FF55);
        }
    }

    private static void renderArtStrikeRequest(GuiGraphics gui, Minecraft mc, int width) {
        if (mc.player == null) return;

        // 1. Проверяем команду игрока
        String myTeam = mc.player.getTeam() != null ? mc.player.getTeam().getName().toUpperCase() : "NEUTRAL";
        if (myTeam.equals("NEUTRAL")) return;

        // 2. Получаем данные о запросе
        int timer = myTeam.equals("BLUE") ? ClientData.blueArtTimer : ClientData.redArtTimer;
        String requester = myTeam.equals("BLUE") ? ClientData.blueArtReqName : ClientData.redArtReqName;
        BlockPos pos = myTeam.equals("BLUE") ? ClientData.blueArtPos : ClientData.redArtPos;

        // Если запроса нет или время вышло — выходим
        if (timer <= 0 || requester.isEmpty() || pos == null) return;

        // 3. ЖЕСТКАЯ ПРОВЕРКА ПРАВА ДОСТУПА (Только для CMD)
        int mySquadId = mc.player.getPersistentData().getInt("AAS_SquadID");
        boolean isSL = mc.player.getPersistentData().getBoolean("AAS_IsSquadLeader");
        int teamCmdId = myTeam.equals("BLUE") ? ClientData.blueCMDId : ClientData.redCMDId;

        // УСЛОВИЕ ВЫХОДА: Если игрок НЕ в креативе И (он не лидер ИЛИ его отряд не командирский)
        if (!mc.player.isCreative()) {
            if (!isSL || mySquadId == -1 || mySquadId != teamCmdId) {
                return; // Обычный игрок или не тот SL уходит отсюда и ничего не видит
            }
        }

        // --- ДАЛЬШЕ РИСУЕМ (только для CMD) ---
        int x = width - 160;
        int y = 140;

        gui.fill(x, y, x + 150, y + 45, 0xAA000000);
        gui.renderOutline(x, y, 150, 45, 0xFFFF5555);
        gui.drawCenteredString(mc.font, I18n.get("aas.hud.artillery_request"), x + 75, y + 5, 0xFFFF5555);

        gui.pose().pushPose();
        gui.pose().scale(0.8f, 0.8f, 1.0f);
        int sx = (int)((x + 75) / 0.8f);
        gui.drawCenteredString(mc.font, I18n.get("aas.hud.from", requester), sx, (int)((y + 18)/0.8f), 0xFFFFFFFF);
        gui.drawCenteredString(mc.font, I18n.get("aas.hud.pos", pos.getX(), pos.getZ()), sx, (int)((y + 28)/0.8f), 0xFFAAAAAA);
        gui.pose().popPose();

        gui.drawCenteredString(mc.font, I18n.get("aas.hud.artillery_keys"), x + 75, y + 35, 0xFFFFFF55);

        // Полоска прогресса (рассчитана строго на 200 тиков / 10 секунд)
        float progress = Math.max(0, timer / 200f);
        gui.fill(x + 5, y + 43, x + 5 + (int)(140 * progress), y + 44, 0xFFFFFFFF);
    }
    private static void renderCaptureNotifications(GuiGraphics gui, Minecraft mc, int screenWidth, int screenHeight) {
        if (ClientData.captureNotifications.isEmpty()) return;

        long now = System.currentTimeMillis();
        int flagW = 54;
        int flagH = 30;
        int x = (screenWidth - flagW) / 2;
        int y = 65;

        for (ClientData.CaptureNotification note : new java.util.ArrayList<>(ClientData.captureNotifications)) {
            long elapsed = now - note.startTime;
            long displayTime = note.duration + 2000; // 5 сек анимация + 2 сек висит
            long fadeTime = 1000; // 1.0 сек на затухание

            // Если общее время вышло - удаляем
            if (elapsed > displayTime + fadeTime) {
                ClientData.captureNotifications.remove(note);
                continue;
            }

            // --- РАСЧЕТ ПРОЗРАЧНОСТИ (Alpha) ---
            float overallAlpha = 1.0f;
            if (elapsed > displayTime) {
                // Плавное затухание от 1.0 до 0.0
                overallAlpha = 1.0f - ((float)(elapsed - displayTime) / fadeTime);
            }
            overallAlpha = Mth.clamp(overallAlpha, 0.0f, 1.0f);

            float progress = Math.min(1.0f, (float) elapsed / note.duration);
            int teamColor = note.team.equalsIgnoreCase("BLUE") ? 0xFF3366CC : 0xFFCC3333;

            // Логика флага
            boolean showTeamFlag = note.isNeutralized ? (progress < 1.0f) : (progress >= 1.0f);

            // 1. ФОН И ФЛАГ
            int bgAlpha = (int)(overallAlpha * 0xAA) << 24;

            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(1, 1, 1, overallAlpha);

            if (showTeamFlag) {
                // === ИСПРАВЛЕННАЯ ЛОГИКА ВЫБОРА ФЛАГА ===
                String flagTeam = note.team;
                if (note.isNeutralized) {
                    // Если точку нейтрализуют, нам нужен флаг ПРОТИВОПОЛОЖНОЙ команды (жертвы)
                    flagTeam = note.team.equalsIgnoreCase("BLUE") ? "RED" : "BLUE";
                }

                String faction = flagTeam.equalsIgnoreCase("BLUE") ? ClientData.BLUE_FACTION : ClientData.RED_FACTION;
                // =========================================

                ResourceLocation tex = getFlagTexture(faction);
                if (tex != null) {
                    gui.blit(tex, x, y, flagW, flagH, 0, 0, 64, 36, 64, 36);
                } else {
                    renderSolidWithAlpha(gui, x, y, flagW, flagH, teamColor, overallAlpha);
                }
            } else {
                renderSolidWithAlpha(gui, x, y, flagW, flagH, 0xFFFFFFFF, overallAlpha); // Белый
            }

            // 2. СТРЕЛОЧКИ
            renderNotificationArrows(gui, x, y, flagW, flagH, teamColor, overallAlpha);

            // 3. ЛИНИЯ ВОКРУГ (Исправленная)
            renderWrappingLine(gui, x, y, flagW, flagH, progress, teamColor, overallAlpha);

            // 4. ТЕКСТ
            String teamDisplayName = note.team.equalsIgnoreCase("BLUE") ? ClientData.customBlueName : ClientData.customRedName;
            String status = note.isNeutralized ? (" " + I18n.get("aas.hud.neutralized") + " ") : (" " + I18n.get("aas.hud.captured") + " ");
            String msg = (teamDisplayName + status + note.pointName).toUpperCase();

            int textAlpha = (int)(overallAlpha * 255) << 24;
            int textColor = textAlpha | 0xFFFFFF;

            gui.pose().pushPose();
            gui.pose().translate(x + flagW / 2f, y + flagH + 8, 50);
            gui.pose().scale(0.9f, 0.9f, 1.0f);
            gui.drawCenteredString(mc.font, msg, 0, 0, textColor);
            gui.pose().popPose();

            RenderSystem.setShaderColor(1, 1, 1, 1);
            break;
        }
    }

    private static void renderNotificationArrows(GuiGraphics gui, int x, int y, int w, int h, int color, float overallAlpha) {
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;

        // Мигание + затухание
        float pulse = 0.7f + (float)Math.sin(System.currentTimeMillis() / 120.0) * 0.3f;
        RenderSystem.setShaderColor(r, g, b, overallAlpha * pulse);

        int arrowSize = 12;
        int centerY = y + (h / 2) - (arrowSize / 2);

        for (int i = 0; i < 3; i++) {
            // Направление стрелок исправлено: левые смотрят вправо, правые смотрят влево
            gui.blit(ARROW_TEX, x - 18 - (i * 10), centerY, 0, 0, arrowSize, arrowSize, arrowSize, arrowSize);

            gui.pose().pushPose();
            int rx = x + w + 18 + (i * 10);
            gui.pose().translate(rx + (arrowSize / 2.0), centerY + (arrowSize / 2.0), 0);
            gui.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(180));
            gui.blit(ARROW_TEX, -arrowSize / 2, -arrowSize / 2, 0, 0, arrowSize, arrowSize, arrowSize, arrowSize);
            gui.pose().popPose();
        }
    }

    // Вспомогательный метод для закраски прямоугольника с альфой (для случая, если текстура флага не найдена)
    private static void renderSolidWithAlpha(GuiGraphics gui, int x, int y, int w, int h, int color, float alpha) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (int)(alpha * 255);
        gui.fill(x, y, x + w, y + h, (a << 24) | (r << 16) | (g << 8) | b);
    }

    private static void renderWrappingLine(GuiGraphics gui, int x, int y, int w, int h, float progress, int color, float alpha) {
        // Увеличиваем общую длину, чтобы сегменты заходили друг на друга и перекрывали углы
        float totalLen = (w + 2) + (h + 2) + (w + 2) + (h + 2);
        float cur = totalLen * progress;

        // Считаем цвет с альфой для затухания
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = (int)(alpha * 255);
        int finalColor = (a << 24) | (r << 16) | (g << 8) | b;

        // Сторона 1: ЛЕВО (Снизу вверх)
        float s1 = h + 2;
        float d1 = Math.min(cur, s1);
        if (d1 > 0) {
            // Рисуем от нижнего края (y+h+2) вверх
            gui.fill(x - 2, (int)(y + h + 2 - d1), x, y + h + 2, finalColor);
        }

        // Сторона 2: ВЕРХ (Слева направо)
        float s2 = w + 2;
        if (cur > s1) {
            float d2 = Math.min(cur - s1, s2);
            // Начинаем от x-2 (захватываем угол) и идем вправо
            gui.fill(x - 2, y - 2, (int)(x - 2 + d2), y, finalColor);
        }

        // Сторона 3: ПРАВО (Сверху вниз)
        float s3 = h + 2;
        if (cur > s1 + s2) {
            float d3 = Math.min(cur - s1 - s2, s3);
            // Начинаем от y-2 (захватываем угол) и идем вниз
            gui.fill(x + w, y - 2, x + w + 2, (int)(y - 2 + d3), finalColor);
        }

        // Сторона 4: НИЗ (Справа налево) - ЗДЕСЬ ИСПРАВЛЕНО СМЫКАНИЕ
        float s4 = w + 2;
        if (cur > s1 + s2 + s3) {
            float d4 = Math.min(cur - (s1 + s2 + s3), s4);
            // x+w+2 - это крайняя правая точка.
            // d4 идет влево до упора, пока не достигнет x-2 (где началась Сторона 1)
            gui.fill((int)(x + w + 2 - d4), y + h, x + w + 2, y + h + 2, finalColor);
        }
    }

    private static void renderPlacementHints(GuiGraphics gui, Minecraft mc, int width, int height) {
        // Проверяем, находится ли игрок в режиме "постановки" чертежа
        if (!com.example.aas.client.ClientPlacementHandler.isPlacing()) return;

        // Настройки плашки
        int uiWidth = 140;
        int uiHeight = 44;

        // Позиционируем плашку (например, справа по центру, как панель отряда, только ниже, или слева)
        // Давай поставим её в правом нижнем углу над панелью ящиков
        int xStart = (width - uiWidth) / 2;
        int yStart = height - uiHeight - 60; // Чуть выше надписи "Ammo/Supplies"

        int goldLight = 0xFFFFD700;

        // Рисуем фон
        gui.fill(xStart, yStart, xStart + uiWidth, yStart + uiHeight, 0x90000000);
        // Вертикальная золотая полоска слева для стиля (как у лопаты)
        gui.fill(xStart, yStart, xStart + 2, yStart + uiHeight, goldLight);

        // Заголовок
        gui.drawCenteredString(mc.font, I18n.get("aas.hud.build_mode"), xStart + (uiWidth / 2), yStart + 4, goldLight);

        RenderSystem.enableBlend();

        // --- 1 СТРОКА: ЛКМ (Rotate) ---
        int row1Y = yStart + 16;
        // Иконка ЛКМ
        gui.blit(MOUSE_LEFT, xStart + 8, row1Y, 0, 0, 12, 12, 12, 12);
        // Текст
        gui.drawString(mc.font, I18n.get("aas.hud.rotate"), xStart + 26, row1Y + 2, 0xFFFFFFFF, true);
        // Иконка Действия (Круговая стрелка)
        gui.blit(ICON_ROTATE, xStart + uiWidth - 20, row1Y, 0, 0, 12, 12, 12, 12);

        // --- 2 СТРОКА: ПКМ (Confirm) ---
        int row2Y = yStart + 30;
        // Иконка ПКМ
        gui.blit(MOUSE_RIGHT, xStart + 8, row2Y, 0, 0, 12, 12, 12, 12);
        // Текст
        gui.drawString(mc.font, I18n.get("aas.hud.confirm"), xStart + 26, row2Y + 2, 0xFFFFFFFF, true);
        // Иконка Действия (Галочка / Молоток)
        gui.blit(ICON_CONFIRM, xStart + uiWidth - 20, row2Y, 0, 0, 12, 12, 12, 12);

        RenderSystem.disableBlend();
    }

    private static void renderDownedUI(GuiGraphics gui, Minecraft mc, int width) {
        String currentKit = mc.player.getPersistentData().getString("AAS_CurrentKit");
        boolean amIMedic = "Medic".equalsIgnoreCase(ClientData.myCurrentKit);
        long now = mc.level.getGameTime();
        int yOffset = 60;

        for (Integer id : ClientData.DOWNED_PLAYERS) {
            Entity entity = mc.level.getEntity(id);
            if (entity instanceof net.minecraft.world.entity.player.Player downed && downed != mc.player) {
                // Проверка на союзника
                if (mc.player.getTeam() != null && downed.getTeam() != null && mc.player.getTeam().isAlliedTo(downed.getTeam())) {

                    long lastShout = downed.getPersistentData().getLong("AAS_LastMedicShoutTimeMS");
                    boolean isShouting = (now - lastShout < 3000);

                    // Медик видит всех всегда, остальные только когда союзник кричит
                    if (amIMedic || isShouting) {
                        int distance = (int) mc.player.distanceTo(downed);
                        if (distance < 150) {
                            String name = downed.getScoreboardName();
                            String text = "✚ " + name + " [" + distance + "m]";
                            int x = width - mc.font.width(text) - 10;

                            int bgColor = isShouting ? 0xAAFF0000 : 0x80AA0000;
                            gui.fill(x - 2, yOffset - 1, width - 5, yOffset + 9, bgColor);
                            gui.drawString(mc.font, text, x, yOffset, 0xFFFFFF, false);
                            yOffset += 12;
                        }
                    }
                }
            }
        }
    }
    private static final ResourceLocation VOICE_ICON_TEX = new ResourceLocation("aas", "textures/gui/voice_icon.png");
    private static final ResourceLocation VOICE_ICON_RADIO_TEX = new ResourceLocation("aas", "textures/gui/voice_icon_radio.png");

    // === Рация — ВСЕГДА сверху, фиксированная базовая позиция ===
    private static void renderRadioSpeakers(GuiGraphics gui, Minecraft mc, int height) {
        if (com.example.aas.client.ClientData.RADIO_SPEAKERS.isEmpty()) return;
        gui.pose().pushPose();
        gui.pose().translate(0, 0, 600);

        long now = System.currentTimeMillis();
        int yOffset = (height / 2) - 40; // Базовая позиция, ни от чего не зависит
        int xOffset = 5;

        for (var entry : new java.util.ArrayList<>(com.example.aas.client.ClientData.RADIO_SPEAKERS.entrySet())) {
            if (now - entry.getValue() > 300) {
                com.example.aas.client.ClientData.RADIO_SPEAKERS.remove(entry.getKey());
                continue;
            }

            String speakerName = entry.getKey();
            int color = 0xFFFFFF00; // желтый

            int textWidth = mc.font.width(speakerName) + 15;
            gui.fill(xOffset, yOffset - 2, xOffset + 5 + textWidth, yOffset + 10, 0x80000000);

            RenderSystem.enableBlend();
            float r = ((color >> 16) & 0xFF) / 255f;
            float g = ((color >> 8) & 0xFF) / 255f;
            float b = (color & 0xFF) / 255f;

            RenderSystem.setShaderColor(r, g, b, 1.0f);
            gui.blit(VOICE_ICON_RADIO_TEX, xOffset + 3, yOffset, 0, 0, 8, 8, 8, 8);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

            gui.drawString(mc.font, speakerName, xOffset + 14, yOffset, color, false);

            yOffset += 14;
        }
        gui.pose().popPose();
    }

    // === Отряд — ВСЕГДА под рацией, зависит ТОЛЬКО от RADIO_SPEAKERS.size() ===
    private static void renderVoiceSpeakers(GuiGraphics gui, Minecraft mc, int height) {
        if (com.example.aas.client.ClientData.SQUAD_SPEAKERS.isEmpty()) return;
        gui.pose().pushPose();
        gui.pose().translate(0, 0, 600);

        long now = System.currentTimeMillis();
        int radioLines = com.example.aas.client.ClientData.RADIO_SPEAKERS.size();
        int yOffset = (height / 2) - 40 + (radioLines * 14); // Сдвиг вниз на строки рации
        int xOffset = 5;

        for (var entry : new java.util.ArrayList<>(com.example.aas.client.ClientData.SQUAD_SPEAKERS.entrySet())) {
            if (now - entry.getValue() > 300) {
                com.example.aas.client.ClientData.SQUAD_SPEAKERS.remove(entry.getKey());
                continue;
            }

            String speakerName = entry.getKey();
            int color = 0xFF55FF55; // Зелёный

            int textWidth = mc.font.width(speakerName) + 15;
            gui.fill(xOffset, yOffset - 2, xOffset + 5 + textWidth, yOffset + 10, 0x80000000);

            RenderSystem.enableBlend();
            float r = ((color >> 16) & 0xFF) / 255f;
            float g = ((color >> 8) & 0xFF) / 255f;
            float b = (color & 0xFF) / 255f;

            RenderSystem.setShaderColor(r, g, b, 1.0f);
            gui.blit(VOICE_ICON_TEX, xOffset + 3, yOffset, 0, 0, 8, 8, 8, 8);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

            gui.drawString(mc.font, speakerName, xOffset + 14, yOffset, color, false);

            yOffset += 14;
        }
        gui.pose().popPose();
    }
    // PATH: src\main\java\com\example\aas\client\AASOverlay.java
    private static void renderHubMaterials(GuiGraphics gui, Minecraft mc, int width, int height) {
        if (mc.hitResult == null) return;

        int mats = -1;
        String team = "NEUTRAL";

        if (mc.hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult hit = (BlockHitResult) mc.hitResult;
            BlockPos pos = hit.getBlockPos();
            BlockState state = mc.level.getBlockState(pos); // Получаем состояние блока
            BlockEntity be = mc.level.getBlockEntity(pos);

            if (be instanceof com.example.aas.block.HubBlockEntity hub) {

                // === ПРОВЕРКА: Если ХАБ еще не построен (чертеж), выходим из метода ===
                if (state.hasProperty(HubBlock.CONSTRUCTED) && !state.getValue(HubBlock.CONSTRUCTED)) {
                    return;
                }

                mats = hub.getMaterials();
                team = hub.getTeam();
            }
        }
        // Для ящиков снабжения оставляем как есть, так как они всегда "готовы"
        else if (mc.hitResult.getType() == HitResult.Type.ENTITY) {
            net.minecraft.world.phys.EntityHitResult hit = (net.minecraft.world.phys.EntityHitResult) mc.hitResult;
            if (hit.getEntity() instanceof com.example.aas.entity.SupplyCrateEntity crate) {
                mats = crate.getMaterials();
                team = crate.getTeamOwner();
            }
        }

        // Если мы нашли материалы и HUB достроен (или это ящик), рисуем интерфейс
        if (mats != -1) {
            int teamColor = 0xFFFFFFFF;
            if (team.equalsIgnoreCase("BLUE")) teamColor = 0xFF5555FF;
            else if (team.equalsIgnoreCase("RED")) teamColor = 0xFFFF5555;

            String text = I18n.get("aas.hud.materials", mats);
            int textWidth = mc.font.width(text);
            int textX = (width - textWidth) / 2;
            int textY = height - 70;

            com.mojang.blaze3d.vertex.PoseStack pose = gui.pose();
            pose.pushPose();
            pose.translate(width / 2.0f, textY - 12, 0);
            pose.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(45));
            gui.fill(-5, -5, 5, 5, 0xFF000000);
            gui.fill(-4, -4, 4, 4, teamColor);
            pose.popPose();

            drawOutlinedString(gui, mc, text, textX, textY, 0xFFFFAA00);
        }
    }

    // === ЛОГИКА ОТОБРАЖЕНИЯ СТРОИТЕЛЬСТВА ===
    private static void renderBuildProgress(GuiGraphics gui, Minecraft mc, int width, int height) {
        ItemStack mainHand = mc.player.getMainHandItem();
        ItemStack offHand = mc.player.getOffhandItem();
        boolean holdingTool = mainHand.getItem() == ModItems.ENTRENCHING_TOOL.get() ||
                offHand.getItem() == ModItems.ENTRENCHING_TOOL.get();

        if (!holdingTool) return;

        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) return;
        BlockHitResult blockHit = (BlockHitResult) mc.hitResult;
        BlockEntity be = mc.level.getBlockEntity(blockHit.getBlockPos());
        BlockState state = mc.level.getBlockState(blockHit.getBlockPos()); // ОШИБКА БЫЛА ТУТ

        if (be == null) return;

        float progress = -1.0f;
        String structureTeam = "NEUTRAL";
        boolean finished = false;

        if (be instanceof HubBlockEntity hub) {
            progress = hub.getPercentage();
            structureTeam = hub.getTeam();
            finished = state.getValue(HubBlock.CONSTRUCTED);
        } else if (be instanceof WallBlockEntity wall) {
            progress = wall.getPercentage();
            structureTeam = wall.getTeam();
            finished = state.getValue(WallBlock.CONSTRUCTED);
        } else if (be instanceof BarbedWireBlockEntity wire) {
            progress = wire.getPercentage();
            structureTeam = wire.getTeam();
            finished = state.getValue(BarbedWireBlock.CONSTRUCTED);
        } else if (be instanceof M2ConstructionBlockEntity m2) {
            progress = m2.getPercentage();
            structureTeam = m2.getTeam();
        } else if (be instanceof AGSConstructionBlockEntity ags) {
            progress = ags.getPercentage();
            structureTeam = ags.getTeam();
        } else if (be instanceof MortarConstructionBlockEntity mortar) {
            progress = mortar.getPercentage();
            structureTeam = mortar.getTeam();
        } else if (be instanceof TOWConstructionBlockEntity tow) {
            progress = tow.getPercentage();
            structureTeam = tow.getTeam();
        } else if (be instanceof com.example.aas.block.VehicleStationBlockEntity vs) {
            progress = vs.getPercentage();
            structureTeam = vs.getTeam();
            // Проверяем состояние блока через свойство CONSTRUCTED
            finished = state.hasProperty(VehicleStationBlock.CONSTRUCTED) && state.getValue(VehicleStationBlock.CONSTRUCTED);
        }

        if (progress < 0) return;

        String playerTeam = mc.player.getTeam() != null ? mc.player.getTeam().getName() : "NEUTRAL";
        boolean isEnemy = !structureTeam.equals("NEUTRAL") && !structureTeam.equalsIgnoreCase(playerTeam) && !mc.player.isCreative();

        int uiWidth = 120;
        int uiHeight = isEnemy || finished ? 65 : 54;
        int xStart = (width / 2) - (uiWidth / 2);
        int yStart = height - 110;

        int goldLight = 0xFFFFD700;
        int goldDark = 0xFFC29100;

        gui.fill(xStart, yStart, xStart + uiWidth, yStart + uiHeight, 0x90000000);
        gui.fill(xStart, yStart, xStart + 2, yStart + uiHeight, goldLight);

        RenderSystem.enableBlend();
        gui.blit(BUILD_ICON, xStart + 8, yStart + 8, 0, 0, 12, 12, 12, 12);
        gui.drawString(mc.font, I18n.get("aas.hud.build"), xStart + 26, yStart + 10, 0xFFFFFFFF, true);

        gui.blit(DIG_ICON, xStart + 8, yStart + 24, 0, 0, 12, 12, 12, 12);
        gui.drawString(mc.font, I18n.get("aas.hud.destroy"), xStart + 26, yStart + 26, 0xFFFFFFFF, true);

        int barX = xStart + 26;
        int barY = yStart + 42;
        int barWidth = 85;
        gui.blit(SHOVEL_ICON, xStart + 8, yStart + 39, 0, 0, 12, 12, 12, 12);
        gui.fill(barX, barY, barX + barWidth, barY + 5, 0x40FFFFFF);
        int currentBarWidth = (int)(barWidth * progress);
        if (currentBarWidth > 0) {
            gui.fillGradient(barX, barY, barX + currentBarWidth, barY + 5, goldDark, goldLight);
        }

        if (finished) {
            gui.drawCenteredString(mc.font, I18n.get("aas.hud.structure_finished"), xStart + uiWidth/2, yStart + 52, 0xFFFFFF00);
        } else if (isEnemy) {
            gui.drawCenteredString(mc.font, I18n.get("aas.hud.enemy_structure"), xStart + uiWidth/2, yStart + 52, 0xFFFF5555);
        }
        RenderSystem.disableBlend();
    }

    private static void renderTickets(GuiGraphics gui, Minecraft mc, int width) {
        // Если нужно скрыть тикеты в выживании - раскомментируйте:
        // if (!mc.player.isCreative() && !mc.player.isSpectator()) return;
        if (!mc.player.isCreative() && !mc.player.isSpectator()) return;

        int boxWidth = 32;
        int boxHeight = 18;
        int gap = 6;
        int topOffset = 5;
        int centerX = width / 2;

        boolean blinkOn = (System.currentTimeMillis() / 500) % 2 == 0;
        int normalWhite = 0xFFFFFFFF;
        int alarmRed = 0xFFFF5555;

        // BLUE
        int blueX = centerX - boxWidth - (gap / 2);
        ResourceLocation blueFlag = getFlagTexture(ClientData.BLUE_FACTION);
        gui.fill(blueX - 1, topOffset - 1, blueX + boxWidth + 1, topOffset + boxHeight + 1, 0xFF000000);

        if (blueFlag != null) {
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.enableBlend();
            gui.blit(blueFlag, blueX, topOffset, boxWidth, boxHeight, 0.0F, 0.0F, boxWidth, boxHeight, boxWidth, boxHeight);
        } else {
            gui.fill(blueX, topOffset, blueX + boxWidth, topOffset + boxHeight, 0xCC3366CC);
        }

        String blueText = String.valueOf(ClientData.BLUE_TICKETS);
        int blueTextColor = ClientData.blueBleeding ? (blinkOn ? alarmRed : normalWhite) : normalWhite;
        int blueTextX = blueX + (boxWidth - mc.font.width(blueText)) / 2;
        int blueTextY = topOffset + (boxHeight - 8) / 2;
        drawOutlinedString(gui, mc, blueText, blueTextX, blueTextY, blueTextColor);

        // RED
        int redX = centerX + (gap / 2);
        ResourceLocation redFlag = getFlagTexture(ClientData.RED_FACTION);
        gui.fill(redX - 1, topOffset - 1, redX + boxWidth + 1, topOffset + boxHeight + 1, 0xFF000000);

        if (redFlag != null) {
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.enableBlend();
            gui.blit(redFlag, redX, topOffset, boxWidth, boxHeight, 0.0F, 0.0F, boxWidth, boxHeight, boxWidth, boxHeight);
        } else {
            gui.fill(redX, topOffset, redX + boxWidth, topOffset + boxHeight, 0xCCCC3333);
        }

        String redText = String.valueOf(ClientData.RED_TICKETS);
        int redTextColor = ClientData.redBleeding ? (blinkOn ? alarmRed : normalWhite) : normalWhite;
        int redTextX = redX + (boxWidth - mc.font.width(redText)) / 2;
        int redTextY = topOffset + (boxHeight - 8) / 2;
        drawOutlinedString(gui, mc, redText, redTextX, redTextY, redTextColor);
    }

    private static void drawOutlinedString(GuiGraphics gui, Minecraft mc, String text, int x, int y, int color) {
        int black = 0xFF000000;
        gui.drawString(mc.font, text, x - 1, y, black, false);
        gui.drawString(mc.font, text, x + 1, y, black, false);
        gui.drawString(mc.font, text, x, y - 1, black, false);
        gui.drawString(mc.font, text, x, y + 1, black, false);
        gui.drawString(mc.font, text, x, y, color, false);
    }

    private static void renderCapturePoint(GuiGraphics gui, Minecraft mc, int width, int height) {
        if (!ClientData.isInsidePoint) return;

        // --- КОНФИГУРАЦИЯ ---
        int flagW = 48;
        int flagH = 27;
        int xStart = width - flagW - 15;
        int yStart = 20;

        // Определение цветов
        int teamColor = 0xFFFFFFFF;
        String faction = "none";
        if (ClientData.pointOwner.equals("BLUE")) {
            teamColor = 0xFF3366CC;
            faction = ClientData.BLUE_FACTION;
        } else if (ClientData.pointOwner.equals("RED")) {
            teamColor = 0xFFCC3333;
            faction = ClientData.RED_FACTION;
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 1. НАЗВАНИЕ ТОЧКИ
        gui.pose().pushPose();
        gui.pose().scale(0.8f, 0.8f, 1.0f);
        int scaledX = (int)((xStart + (flagW / 2)) / 0.8f);
        int scaledY = (int)((yStart - 9) / 0.8f);
        gui.drawCenteredString(mc.font, ClientData.pointName, scaledX, scaledY, 0xFFFFFFFF);
        gui.pose().popPose();

        // 2. ФЛАГ И ОБВОДКА (Ширина 50 пикселей)
        int barStartX = xStart - 1;
        gui.fill(barStartX, yStart - 1, xStart + flagW + 1, yStart + flagH + 1, 0xFF000000);

        ResourceLocation flagTexture = getFlagTexture(faction);
        if (flagTexture != null && !faction.equals("none")) {
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            gui.blit(flagTexture, xStart, yStart, flagW, flagH, 0, 0, 64, 36, 64, 36);
        } else {
            gui.fill(xStart, yStart, xStart + flagW, yStart + flagH, teamColor);
        }

        // 3. ШКАЛА (4 сегмента)
        int barsY = yStart + flagH + 6;
        int segH = 4;
        int gap = 1;
        int[] segmentWidths = {12, 12, 12, 11};
        int currentX = barStartX;

        for (int i = 0; i < 4; i++) {
            int sW = segmentWidths[i];
            gui.fill(currentX, barsY, currentX + sW, barsY + segH, 0x90222222);

            float threshold = i * 0.25f;
            if (ClientData.pointProgress > threshold) {
                float boxFill = Math.min(1.0f, (ClientData.pointProgress - threshold) / 0.25f);
                int fillW = (int) (sW * boxFill);
                int fillColor = teamColor;
                if (ClientData.pointOwner.equals("NEUTRAL")) {
                    if (ClientData.pointCapturingTeam.equals("BLUE")) fillColor = 0xFF3366CC;
                    else if (ClientData.pointCapturingTeam.equals("RED")) fillColor = 0xFFCC3333;
                }
                gui.fill(currentX, barsY, currentX + fillW, barsY + segH, fillColor);
            }
            currentX += sW + gap;
        }

        // 4. ЛОГИКА СТРЕЛОЧЕК (Механика из самого начала)
        int rate = ClientData.pointCaptureRate;
        if (rate != 0) {
            int absRate = Math.min(Math.abs(rate), 4);
            boolean isForward = rate > 0;

            String capTeam = ClientData.pointCapturingTeam;
            int arrowCol = 0xFFFFFFFF;
            if (capTeam.equalsIgnoreCase("BLUE")) arrowCol = 0xFF3366CC;
            else if (capTeam.equalsIgnoreCase("RED")) arrowCol = 0xFFCC3333;

            float ar = ((arrowCol >> 16) & 0xFF) / 255f;
            float ag = ((arrowCol >> 8) & 0xFF) / 255f;
            float ab = (arrowCol & 0xFF) / 255f;

            // Если вперед — начинаем слева, если назад — начинаем справа (от края шкалы)
            double baseX = isForward ? (barStartX + 5) : (barStartX + 45);
            double arrowY = barsY + (segH / 2.0);

            for (int j = 0; j < absRate; j++) {
                gui.pose().pushPose();
                // Смещение зависит от направления
                double offsetX = isForward ? (j * 6) : (-j * 6);

                gui.pose().translate(baseX + offsetX, arrowY, 10);
                gui.pose().scale(1.05f, 1.05f, 1.0f);

                if (!isForward) {
                    gui.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(180));
                }

                RenderSystem.setShaderColor(ar, ag, ab, 1.0f);
                gui.blit(ARROW_TEX, -6, -6, 0, 0, 12, 12, 12, 12);
                gui.pose().popPose();
            }
        }

        // 5. ТЕКСТ БЛОКИРОВКИ
        if (ClientData.isLocked) {
            gui.pose().pushPose();
            gui.pose().scale(0.7f, 0.7f, 1.0f);
            int lockX = (int)((barStartX) / 0.71f);
            int lockY = (int)((barsY + 10) / 0.7f); // Сдвиг под крупные стрелки

            String lockedLabel = I18n.get("aas.hud.capture.blocked");
            gui.drawString(mc.font, lockedLabel, lockX, lockY, 0xFFFF5555, true);

            if (ClientData.lockSecondsLeft > 0) {
                long m = ClientData.lockSecondsLeft / 60;
                long s = ClientData.lockSecondsLeft % 60;
                String time = I18n.get("aas.hud.capture.time_format", m, s);
                gui.drawString(mc.font, time, lockX, lockY + 10, 0xFFCCCCCC, true);
            } else if (!ClientData.nextObjectiveName.isEmpty()) {
                String needText = I18n.get("aas.hud.capture.need", ClientData.nextObjectiveName);
                gui.drawString(mc.font, needText, lockX, lockY + 10, 0xFFCCCCCC, true);
            }

            gui.pose().popPose();
        }

        RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    private static void renderVotePanel(GuiGraphics gui, Minecraft mc, int width) {
        // 1. Логика анимации (плавный выезд слева)
        float speed = 0.05f;
        boolean shouldShow = ClientData.voteActive && ClientData.isMapOpen;
        ClientData.voteTransition = Mth.lerp(speed, ClientData.voteTransition, shouldShow ? 1.0f : 0.0f);

        if (ClientData.voteTransition <= 0.001f) return;

        int xPos = (int) Mth.lerp(ClientData.voteTransition, -180, 10);
        int yPos = 60;

        // 2. Данные команды
        String myTeam = mc.player.getTeam() != null ? mc.player.getTeam().getName().toUpperCase() : "NEUTRAL";
        var teamPlayers = ClientData.mapPlayers.values().stream()
                .filter(info -> info.team.equalsIgnoreCase(myTeam))
                .toList();

        // --- ИСПРАВЛЕННЫЙ РАСЧЕТ ВЫСОТЫ ---
        int panelWidth = 165; // Немного шире для длинных названий фракций
        int rowHeight = 12;
        int headerHeight = 32;    // Заголовок + Таймер
        int statusHeight = 28;    // Две строки статуса фракций (12+12 + отступы)
        int footerHeight = 15;    // Подсказка F9/F10

        // Итоговая высота: голова + статусы + (игроки * 12) + подвал
        int panelHeight = headerHeight + statusHeight + (teamPlayers.size() * rowHeight) + footerHeight;

        // 4. Отрисовка фона
        gui.pose().pushPose();
        gui.pose().translate(0, 0, 500);

        gui.fill(xPos, yPos, xPos + panelWidth, yPos + panelHeight, 0xAA000000);
        gui.renderOutline(xPos, yPos, panelWidth, panelHeight, 0xFFFFFFFF);

        // 5. Заголовок и Таймер
        gui.drawCenteredString(mc.font, I18n.get("aas.hud.vote_to_start"), xPos + panelWidth / 2, yPos + 5, 0xFFFFD700);
        int seconds = Math.max(0, ClientData.voteTimer);
        String timeStr = String.format("%02d:%02d", seconds / 60, seconds % 60);
        gui.drawCenteredString(mc.font, timeStr, xPos + panelWidth / 2, yPos + 16, 0xFFFFFFFF);

        // Линия под таймером
        gui.fill(xPos + 5, yPos + 28, xPos + panelWidth - 5, yPos + 29, 0x55FFFFFF);

        // 6. Статус готовности ФРАКЦИЙ (динамические названия)
        String blueName = ClientData.customBlueName;
        String redName = ClientData.customRedName;
        int blueColor = ClientData.blueReady ? 0xFF55FF55 : 0xFFFF5555;
        int redColor = ClientData.redReady ? 0xFF55FF55 : 0xFFFF5555;

        // Рисуем названия фракций и их статус
        gui.drawString(mc.font, blueName + ": " + (ClientData.blueReady ? I18n.get("aas.hud.ready") : I18n.get("aas.hud.waiting_status")), xPos + 8, yPos + 32, blueColor, true);
        gui.drawString(mc.font, redName + ": " + (ClientData.redReady ? I18n.get("aas.hud.ready") : I18n.get("aas.hud.waiting_status")), xPos + 8, yPos + 44, redColor, true);

        // 7. Список игроков команды (начинается ниже статусов фракций)
        int currentY = yPos + headerHeight + statusHeight;

        for (var info : teamPlayers) {
            Boolean vote = ClientData.votes.get(info.uuid);
            String icon = "○";
            int iconColor = 0xFFAAAAAA;

            if (vote != null) {
                icon = vote ? "✔" : "✘";
                iconColor = vote ? 0xFF55FF55 : 0xFFFF5555;
            }

            gui.drawString(mc.font, icon, xPos + 8, currentY, iconColor, true);
            int nameColor = info.name.equals(mc.player.getScoreboardName()) ? 0xFFFFFF55 : 0xFFFFFFFF;
            gui.drawString(mc.font, info.name, xPos + 22, currentY, nameColor, true);

            currentY += rowHeight;
        }

        // 8. Подсказка в самом низу
        gui.drawCenteredString(mc.font, I18n.get("aas.hud.vote_keys"), xPos + panelWidth / 2, yPos + panelHeight - 12, 0xFFBBBBBB);

        gui.pose().popPose();
    }

    // ВОТ ЭТОТ МЕТОД НУЖНО ДОБАВИТЬ В AASOverlay.java, чтобы не было ошибки!
    private static void renderProgressBars(GuiGraphics gui, Minecraft mc, int centerX, int y, int teamColor) {
        int barsTotalWidth = 80;
        int barHeight = 4;
        int barsStartX = centerX - (barsTotalWidth / 2);
        int barGap = 2;
        int singleBarWidth = (barsTotalWidth - (barGap * 3)) / 4;

        for (int i = 0; i < 4; i++) {
            int currentBarX = barsStartX + (i * (singleBarWidth + barGap));
            // Фон полоски
            gui.fill(currentBarX, y, currentBarX + singleBarWidth, y + barHeight, 0xFF444444);

            float threshold = i * 0.25f;
            if (ClientData.pointProgress > threshold) {
                float fillAmount = Math.min(1.0f, (ClientData.pointProgress - threshold) / 0.25f);
                int fillWidth = (int) (singleBarWidth * fillAmount);

                int finalBarColor = teamColor;
                if (ClientData.pointOwner.equals("NEUTRAL")) {
                    if (ClientData.pointCapturingTeam.equals("BLUE")) finalBarColor = 0xFF3366CC;
                    else if (ClientData.pointCapturingTeam.equals("RED")) finalBarColor = 0xFFCC3333;
                }
                gui.fill(currentBarX, y, currentBarX + fillWidth, y + barHeight, finalBarColor);
            }
        }
    }
    private static void renderCaptureArrows(GuiGraphics gui, Minecraft mc, int centerX, int y) {
        int rate = ClientData.pointCaptureRate;
        if (rate == 0) return;

        int absRate = Math.min(Math.abs(rate), 4);
        boolean isForward = rate > 0;

        // Берем цвет команды, которая физически воздействует на точку сейчас
        String capTeam = ClientData.pointCapturingTeam;
        int color = 0xFFFFFFFF;
        if (capTeam.equalsIgnoreCase("BLUE")) color = 0xFF3366CC;
        else if (capTeam.equalsIgnoreCase("RED")) color = 0xFFCC3333;

        RenderSystem.enableBlend();
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        RenderSystem.setShaderColor(r, g, b, 1.0f);

        int arrowSize = 12;
        int barsStartX = centerX - 40;
        int segmentWidth = 20;

        for (int i = 0; i < absRate; i++) {
            int segmentIndex = isForward ? i : (3 - i);
            int xPos = barsStartX + (segmentIndex * segmentWidth) + 2;
            int yPos = y - 4;

            if (isForward) {
                gui.blit(ARROW_TEX, xPos, yPos, 0, 0, arrowSize, arrowSize, arrowSize, arrowSize);
            } else {
                gui.pose().pushPose();
                gui.pose().translate(xPos + (arrowSize / 2.0), yPos + (arrowSize / 2.0), 0);
                gui.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(180));
                gui.blit(ARROW_TEX, -arrowSize / 2, -arrowSize / 2, 0, 0, arrowSize, arrowSize, arrowSize, arrowSize);
                gui.pose().popPose();
            }
        }
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    private static void renderSupplyTruckInfo(GuiGraphics gui, Minecraft mc, int width, int height) {
        Entity ridingEntity = mc.player.getVehicle();
        if (ridingEntity == null) return;
        Entity supplyTruck = getSupplyTruckEntity(ridingEntity);
        if (supplyTruck != null) {
            int crates = supplyTruck.getPersistentData().getInt("AAS_SupplyAmmo");
            int maxCrates = com.example.aas.config.AASConfig.SUPPLY_TRUCK_CRATES.get();
            boolean isCharging = false;

            if (crates < maxCrates) {
                BlockPos vPos = supplyTruck.blockPosition();
                for (BlockPos pos : BlockPos.betweenClosed(vPos.offset(-5, -2, -5), vPos.offset(5, 2, 5))) {
                    if (mc.level.getBlockState(pos).getBlock() instanceof MainSupplyBlock) {
                        isCharging = true;
                        break;
                    }
                }
            }
            int color = 0xFFFFFFFF;
            if (isCharging) {
                if ((System.currentTimeMillis() / 250) % 2 == 0) color = 0xFF00FF00;
                else color = 0xFFFFFF00;
            } else if (crates == 0) {
                color = 0xFFFF5555;
            }
            String text = I18n.get("aas.hud.supplies", crates, maxCrates);
            int textWidth = mc.font.width(text);
            int x = width - textWidth - 10;
            int y = height - 25;
            drawOutlinedString(gui, mc, text, x, y, color);
            if (isCharging) {
                String reloadText = I18n.get("aas.hud.reloading");
                drawOutlinedString(gui, mc, reloadText, width - mc.font.width(reloadText) - 10, y - 10, 0xFF55FF55);
            }
        }
    }

    private static Entity getSupplyTruckEntity(Entity entity) {
        if (entity.getPersistentData().getBoolean("AAS_IsSupplyTruck")) return entity;
        Entity parent = entity.getVehicle();
        if (parent != null && parent.getPersistentData().getBoolean("AAS_IsSupplyTruck")) return parent;
        return null;
    }

    private static void renderVehicleAmmo(GuiGraphics gui, Minecraft mc, int width, int height) {
        Entity vehicle = mc.player.getVehicle();
        int currentAmmo = -1;
        int maxAmmo = -1;
        if (vehicle instanceof M2BrowningEntity m2) {
            currentAmmo = m2.getAmmoCount();
            maxAmmo = M2AmmoItem.MAX_AMMO;
        } else if (vehicle instanceof AGS30Entity ags) {
            currentAmmo = ags.getAmmoCount();
            maxAmmo = AGSAmmoItem.MAX_AMMO;
        }
        if (currentAmmo != -1) {
            String text = I18n.get("aas.hud.ammo", currentAmmo, maxAmmo);
            int x = 10;
            int y = height - 40;
            int color = (currentAmmo == 0) ? 0xFFFF5555 : 0xFFFFFFFF;
            drawOutlinedString(gui, mc, text, x, y, color);
        }
    }
    private static void renderMedicUI(GuiGraphics gui, Minecraft mc, int width) {
        int yOffset = 60;
        for (Integer id : ClientData.DOWNED_PLAYERS) {
            Entity entity = mc.level.getEntity(id);
            if (entity instanceof net.minecraft.world.entity.player.Player downed && downed != mc.player) {
                int distance = (int) mc.player.distanceTo(downed);
                if (distance < 100) { // Видим раненых в радиусе 100 блоков
                    String name = downed.getScoreboardName();
                    String text = "✚ " + name + " [" + distance + "m]";
                    int x = width - mc.font.width(text) - 10;

                    // Рисуем красный фон под текстом для заметности
                    gui.fill(x - 2, yOffset - 1, width - 5, yOffset + 9, 0x80FF0000);
                    gui.drawString(mc.font, text, x, yOffset, 0xFFFFFF, false);
                    yOffset += 12;
                }
            }
        }
    }
    // Находим метод renderSideMap в AASOverlay.java и заменяем его полностью
    private static void renderSideMap(GuiGraphics gui, Minecraft mc, int screenWidth, int screenHeight, float partialTick) {
        if (HUD_SIDE_MAP == null) HUD_SIDE_MAP = new AASMapRenderer();

        float speed = 0.12f;
        float target = ClientData.isMapOpen ? 1.0f : 0.0f;
        ClientData.mapTransition = Mth.lerp(speed, ClientData.mapTransition, target);

        if (!ClientData.isMapOpen && ClientData.mapTransition < 0.001f) {
            ClientData.mapTransition = 0f;
            return;
        }

        // --- ПАРАМЕТРЫ РАЗМЕРОВ ---
        int mapSize = (int) ((screenHeight - 60) / 1.3f);
        int topBarHeight = 35;
        int sidePadding = 15;
        int bottomPadding = 5;

        int containerWidth = mapSize + (sidePadding * 2);
        int containerHeight = mapSize + topBarHeight + bottomPadding;

        // --- РАСЧЕТ ПОЗИЦИИ ---
        float hiddenX = (float) screenWidth + 20;
        float visibleX = (float) screenWidth - containerWidth - 10;
        int xPos = (int) Mth.lerp(ClientData.mapTransition, hiddenX, visibleX);

        // ВЕРТИКАЛЬНОЕ ЦЕНТРИРОВАНИЕ:
        int yPos = (screenHeight - containerHeight) / 2;

        // --- РЕНДЕР ---
        gui.fill(xPos, yPos, xPos + containerWidth, yPos + containerHeight, 0xAA000000);
        renderMinimapStatus(gui, mc, xPos, yPos, containerWidth, topBarHeight);

        HUD_SIDE_MAP.init(xPos + sidePadding, yPos + topBarHeight, mapSize);
        HUD_SIDE_MAP.render(gui, -1, -1, partialTick);
    }

    // Вспомогательный метод для отрисовки инфы над миникартой
    private static void renderMinimapStatus(GuiGraphics gui, Minecraft mc, int x, int y, int containerWidth, int topBarHeight) {
        String team = "NEUTRAL";
        if (mc.player.getTeam() != null) team = mc.player.getTeam().getName().toUpperCase();

        int tickets = team.equals("BLUE") ? ClientData.BLUE_TICKETS : (team.equals("RED") ? ClientData.RED_TICKETS : 0);
        String faction = team.equals("BLUE") ? ClientData.BLUE_FACTION : (team.equals("RED") ? ClientData.RED_FACTION : "none");
        String tText = String.valueOf(tickets);

        // Размеры элементов
        int flagW = 22;
        int flagH = 13;
        int iconSize = 12;
        int textW = mc.font.width(tText);
        int gap = 6;

        // Считаем общую ширину (Флаг + Иконка + Текст + зазоры)
        int totalContentWidth = flagW + gap + iconSize + gap + textW;

        // Координаты для отрисовки группы строго по центру подложки
        int startX = x + (containerWidth / 2) - (totalContentWidth / 2);
        int contentY = y + (topBarHeight / 2) - (flagH / 2);

        // 1. Рисуем Флаг
        ResourceLocation flagTex = getFlagTexture(faction);
        if (flagTex != null) {
            RenderSystem.setShaderColor(1, 1, 1, 1);
            gui.blit(flagTex, startX, contentY, flagW, flagH, 0, 0, 64, 36, 64, 36);
        }

        // 2. Рисуем Иконку тикетов (посередине)
        ResourceLocation ticketIcon = new ResourceLocation("aas", "textures/gui/minimap_tickets.png");
        int iconX = startX + flagW + gap;
        RenderSystem.enableBlend();
        gui.blit(ticketIcon, iconX, contentY, iconSize, iconSize, 0, 0, 16, 16, 16, 16);

        // 3. Рисуем Число тикетов (справа)
        int textX = iconX + iconSize + gap;
        gui.drawString(mc.font, tText, textX, contentY + 2, 0xFFFFFFFF, true);

        RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    private static ResourceLocation getFlagTexture(String faction) {
        if (faction == null) return null;
        switch (faction.toLowerCase()) {
            case "ukraine":
                return FLAG_UKRAINE;
            case "russia":
                return FLAG_RUSSIA;
            case "usa":
                return FLAG_USA;
            case "nato":
                return FLAG_NATO;
            case "bluefor": return FLAG_BLUEFOR;
            case "redfor": return FLAG_REDFOR;
            case "insurgency": return FLAG_INSURGENCY;
            case "pmc": return FLAG_PMC;
            case "germany": return FLAG_GERMANY;
            case "militia": return FLAG_MILITIA;
            default: return null;
        }
    }
    private static void renderReviveProgress(GuiGraphics gui, Minecraft mc, int width, int height) {
        float progress;
        String label;

        if (ClientData.reviveProgressOther >= 0f) {
            progress = ClientData.reviveProgressOther;
            label = I18n.get("aas.msg.reviving");
        } else if (ClientData.reviveProgressSelf >= 0f) {
            progress = ClientData.reviveProgressSelf;
            label = I18n.get("aas.msg.being_revived");
        } else {
            return;
        }

        int barWidth = 100;
        int barHeight = 5;
        int x = (width - barWidth) / 2;
        int y = height / 2 + 40;

        int goldLight = 0xFFFFD700;
        int goldDark = 0xFFC29100;

        // подпись — полностью белым, без цветовых стилей
        gui.drawCenteredString(mc.font, label, width / 2, y - 12, 0xFFFFFFFF);

        // фон полоски — тот же стиль, что и renderBuildProgress
        gui.fill(x - 1, y - 1, x + barWidth + 1, y + barHeight + 1, 0xFF000000);
        gui.fill(x, y, x + barWidth, y + barHeight, 0x40FFFFFF);

        int filled = (int) (barWidth * progress);
        if (filled > 0) {
            gui.fillGradient(x, y, x + filled, y + barHeight, goldDark, goldLight);
        }
    }
}