package com.example.aas.client;

import com.example.aas.block.*;
import com.example.aas.entity.AGS30Entity;
import com.example.aas.entity.M2BrowningEntity;
import com.example.aas.item.AGSAmmoItem;
import com.example.aas.item.M2AmmoItem;
import com.example.aas.item.ModItems;
import com.example.aas.world.AASWorldData;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
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

@Mod.EventBusSubscriber(modid = "aas", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AASOverlay {

    // === РЕСУРСЫ ФЛАГОВ ===
    private static final ResourceLocation FLAG_UKRAINE = new ResourceLocation("aas", "textures/gui/flags/ukraine.png");
    private static final ResourceLocation FLAG_RUSSIA = new ResourceLocation("aas", "textures/gui/flags/russia.png");
    private static final ResourceLocation FLAG_USA = new ResourceLocation("aas", "textures/gui/flags/usa.png");
    private static final ResourceLocation FLAG_NATO = new ResourceLocation("aas", "textures/gui/flags/nato.png");
    private static final ResourceLocation FLAG_BLUEFOR = new ResourceLocation("aas", "textures/gui/flags/bluefor.png");
    private static final ResourceLocation FLAG_REDFOR = new ResourceLocation("aas", "textures/gui/flags/redfor.png");
    private static final ResourceLocation VIGNETTE_TEXTURE = new ResourceLocation("aas", "textures/misc/vignette.png");
    private static AASMapRenderer HUD_SIDE_MAP;

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

        // 2. Логика захвата точек
        if (ClientData.allCapturePoints != null) {
            for (AASWorldData.CapturePoint cp : ClientData.allCapturePoints) {
                if (mc.player.getBoundingBox().intersects(cp.area)) {
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
        if (ClientData.isMapOpen || ClientData.mapTransition > 0) {
            renderSideMap(gui, mc, width, height, event.getPartialTick());
        }
    }

    private static void renderDownedUI(GuiGraphics gui, Minecraft mc, int width) {
        String currentKit = mc.player.getPersistentData().getString("AAS_CurrentKit");
        boolean amIMedic = "Medic".equalsIgnoreCase(currentKit);
        long now = System.currentTimeMillis();
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
    // PATH: src\main\java\com\example\aas\client\AASOverlay.java
    private static void renderHubMaterials(GuiGraphics gui, Minecraft mc, int width, int height) {
        if (mc.hitResult == null) return;

        int mats = -1;
        String team = "NEUTRAL";

        if (mc.hitResult.getType() == HitResult.Type.BLOCK) {
            BlockHitResult hit = (BlockHitResult) mc.hitResult;
            BlockEntity be = mc.level.getBlockEntity(hit.getBlockPos());
            if (be instanceof com.example.aas.block.HubBlockEntity hub) {
                mats = hub.getMaterials();
                team = hub.getTeam();
            }
        }
        else if (mc.hitResult.getType() == HitResult.Type.ENTITY) {
            net.minecraft.world.phys.EntityHitResult hit = (net.minecraft.world.phys.EntityHitResult) mc.hitResult;
            if (hit.getEntity() instanceof com.example.aas.entity.SupplyCrateEntity crate) {
                mats = crate.getMaterials();
                team = crate.getTeamOwner();
            }
        }

        if (mats != -1) {
            int teamColor = 0xFFFFFFFF; // Белый
            if (team.equalsIgnoreCase("BLUE")) teamColor = 0xFF5555FF; // Синий
            else if (team.equalsIgnoreCase("RED")) teamColor = 0xFFFF5555; // Красный

            String text = "Materials: " + mats;
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

    // === ЛОГИКА ОТОБРАЖЕНИЯ СТРОИТЕЛЬСТВА (4 КВАДРАТИКА) ===
    private static void renderBuildProgress(GuiGraphics gui, Minecraft mc, int width, int height) {
        ItemStack mainHand = mc.player.getMainHandItem();
        ItemStack offHand = mc.player.getOffhandItem();
        boolean holdingTool = mainHand.getItem() == ModItems.ENTRENCHING_TOOL.get() ||
                offHand.getItem() == ModItems.ENTRENCHING_TOOL.get();

        if (!holdingTool) return;

        if (mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) return;
        BlockHitResult blockHit = (BlockHitResult) mc.hitResult;
        BlockEntity be = mc.level.getBlockEntity(blockHit.getBlockPos());

        if (be == null) return;

        float progress = -1.0f;
        String structureTeam = "NEUTRAL";

        if (be instanceof HubBlockEntity hub) {
            progress = hub.getPercentage();
            structureTeam = hub.getTeam();
        } else if (be instanceof WallBlockEntity wall) {
            progress = wall.getPercentage();
            structureTeam = wall.getTeam();
        } else if (be instanceof BarbedWireBlockEntity wire) {
            progress = wire.getPercentage();
            structureTeam = wire.getTeam();
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
        }

        if (progress < 0) return;

        String playerTeam = "NEUTRAL";
        if (mc.player.getTeam() != null) {
            playerTeam = mc.player.getTeam().getName();
        }

        if (!structureTeam.equals("NEUTRAL") && !structureTeam.equalsIgnoreCase(playerTeam) && !mc.player.isCreative()) {
            return;
        }

        int startY = height - 46;
        int boxSize = 8;
        int gap = 3;
        int totalWidth = (4 * boxSize) + (3 * gap);
        int startX = (width - totalWidth) / 2;

        for (int i = 0; i < 4; i++) {
            int x = startX + i * (boxSize + gap);
            int y = startY;
            gui.fill(x - 1, y - 1, x + boxSize + 1, y + boxSize + 1, 0xFF000000);
            gui.fill(x, y, x + boxSize, y + boxSize, 0x80404040);

            float thresholdStart = i * 0.25f;
            if (progress > thresholdStart) {
                float fillAmount = (progress - thresholdStart) / 0.25f;
                if (fillAmount > 1.0f) fillAmount = 1.0f;
                int fillWidth = (int) (boxSize * fillAmount);
                gui.fill(x, y, x + fillWidth, y + boxSize, 0xFFFFFFFF);
            }
        }
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
        // Если сервер говорит, что мы не на точке — ничего не рисуем (фикс углов цилиндра)
        if (!ClientData.isInsidePoint) return;

        int centerX = width / 2;
        int bottomY = height - 55;
        int flagWidth = 32;
        int flagHeight = 18;
        int flagX = centerX - (flagWidth / 2);
        int flagY = bottomY - flagHeight;

        // Чисто белый цвет для нейтральных точек (фикс серого флага)
        int color = 0xFFFFFFFF;
        String factionToRender = "none";

        if (ClientData.pointOwner.equals("BLUE")) {
            color = 0xFF3366CC;
            factionToRender = ClientData.BLUE_FACTION;
        } else if (ClientData.pointOwner.equals("RED")) {
            color = 0xFFCC3333;
            factionToRender = ClientData.RED_FACTION;
        }

        // ВАЖНО: Сброс цвета перед рисованием текстуры, чтобы не было "белого наложения"
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // Черная обводка флага
        gui.fill(flagX - 1, flagY - 1, flagX + flagWidth + 1, flagY + flagHeight + 1, 0xFF000000);

        ResourceLocation flagTexture = getFlagTexture(factionToRender);

        if (flagTexture != null && !factionToRender.equals("none")) {
            gui.blit(flagTexture, flagX, flagY, flagWidth, flagHeight, 0.0F, 0.0F, flagWidth, flagHeight, flagWidth, flagHeight);
        } else {
            // Если флага нет (нейтрал или ошибка) — заливаем цветом команды (белым для нейтрала)
            gui.fill(flagX, flagY, flagX + flagWidth, flagY + flagHeight, color);
        }

        // Имя точки
        String name = ClientData.pointName;
        drawOutlinedString(gui, mc, name, centerX - (mc.font.width(name) / 2), flagY - 10, 0xFFFFFFFF);

        // Статус "ENEMY!" или время блокировки
        if (ClientData.isContested) {
            drawOutlinedString(gui, mc, "ENEMY!", centerX - (mc.font.width("ENEMY!") / 2), flagY - 22, 0xFFFFAA00);
        }

        if (ClientData.isLocked) {
            String status = ClientData.nextObjectiveName;
            if (!status.isEmpty()) {
                int statusColor = status.contains(":") ? 0xFFFF0000 : 0xFFAAAAAA;
                drawOutlinedString(gui, mc, status, centerX - (mc.font.width(status) / 2), flagY + flagHeight + 10, statusColor);
            }
        }

        // Вызов метода отрисовки полосок (который ниже)
        renderProgressBars(gui, mc, centerX, flagY + flagHeight + 3, color);
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

    private static void renderSupplyTruckInfo(GuiGraphics gui, Minecraft mc, int width, int height) {
        Entity ridingEntity = mc.player.getVehicle();
        if (ridingEntity == null) return;
        Entity supplyTruck = getSupplyTruckEntity(ridingEntity);
        if (supplyTruck != null) {
            int crates = supplyTruck.getPersistentData().getInt("AAS_SupplyAmmo");
            boolean isCharging = false;
            if (crates < 2) {
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
            String text = "Supplies: " + crates + " / 2";
            int textWidth = mc.font.width(text);
            int x = width - textWidth - 10;
            int y = height - 25;
            drawOutlinedString(gui, mc, text, x, y, color);
            if (isCharging) {
                String reloadText = "RELOADING...";
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
            String text = "Ammo: " + currentAmmo + " / " + maxAmmo;
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
    private static void renderSideMap(GuiGraphics gui, Minecraft mc, int screenWidth, int screenHeight, float partialTick) {
        if (HUD_SIDE_MAP == null) HUD_SIDE_MAP = new AASMapRenderer();

        float speed = 0.08f;
        float target = ClientData.isMapOpen ? 1.0f : 0.0f;

        // Линейный переход
        ClientData.mapTransition = Mth.lerp(speed, ClientData.mapTransition, target);

        // Добавляем Sin-сглаживание (Ease In/Out), чтобы движение было органичным
        float smoothTransition = Mth.sin(ClientData.mapTransition * (float)Math.PI / 2.0f);

        if (!ClientData.isMapOpen && ClientData.mapTransition < 0.001f) {
            ClientData.mapTransition = 0f;
            return;
        }

        // РАЗМЕР КАРТЫ (квадрат по высоте экрана)
        int mapSize = screenHeight - 40;
        int yPos = 20;

        // РАСЧЕТ ПОЗИЦИИ X (ДЛЯ ВЫЕЗДА СПРАВА)
        // hiddenX = screenWidth + 20 (спрятана за правым краем)
        // visibleX = screenWidth - mapSize - 10 (показана с отступом 10 от правого края)
        float hiddenX = (float) screenWidth + 20;
        float visibleX = (float) screenWidth - mapSize - 10;

        int xPos = (int) Mth.lerp(ClientData.mapTransition, hiddenX, visibleX);

        // Инициализируем карту
        HUD_SIDE_MAP.init(xPos, yPos, mapSize);

        // Устанавливаем масштаб (если добавили метод в AASMapRenderer)
        // HUD_SIDE_MAP.setMapScale(1.5);

        // РИСУЕМ РАМКУ (Белые линии сверху, снизу и СЛЕВА, так как правая часть у края экрана)
        gui.fill(xPos - 2, yPos - 2, xPos + mapSize, yPos, 0xFFFFFFFF);             // Верх
        gui.fill(xPos - 2, yPos + mapSize, xPos + mapSize, yPos + mapSize + 2, 0xFFFFFFFF); // Низ
        gui.fill(xPos - 2, yPos, xPos, yPos + mapSize, 0xFFFFFFFF);                 // Левая грань (торцевая)

        // Фоновая заливка (черная)
        gui.fill(xPos, yPos, xPos + mapSize, yPos + mapSize, 0xFF000000);

        // Отрисовка самой карты
        HUD_SIDE_MAP.render(gui, -1, -1, partialTick);
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
            default:
                return null;
        }
    }
}