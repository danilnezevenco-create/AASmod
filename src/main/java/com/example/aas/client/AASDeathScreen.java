// PATH: src\main\java\com\example\aas\client\AASDeathScreen.java
package com.example.aas.client;

import com.example.aas.config.AASConfig;
import com.example.aas.network.PacketHandler;
import com.example.aas.network.PacketRespawnRequest;
import com.example.aas.world.AASWorldData;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Team;

import java.util.ArrayList;
import java.util.List;

public class AASDeathScreen extends DeathScreen {
    private int respawnTimeTotal = 10;
    private long deathTimestamp;
    private final Component cause;

    private Button mainSpawnButton;
    private Button rallySpawnButton;
    private Button hubSpawnButton;
    private Button disconnectButton;

    private boolean isHubListOpen = false;
    private List<Button> hubButtons = new ArrayList<>();

    private float scrollAmount = 0.0f;
    private boolean isScrolling = false;
    private static final int VISIBLE_ITEMS = 4;
    private static final int ITEM_HEIGHT = 25;
    private static final int LIST_HEIGHT = VISIBLE_ITEMS * ITEM_HEIGHT;

    private int listLeft, listRight, listTop, listBottom;

    public AASDeathScreen(Component cause, boolean hardcore) {
        super(cause, hardcore);
        this.cause = cause;
        this.deathTimestamp = System.currentTimeMillis();
        this.respawnTimeTotal = ClientData.RESPAWN_TIME > 0 ? ClientData.RESPAWN_TIME : 10;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        this.clearWidgets();
        this.hubButtons.clear();

        if (!isHubListOpen) scrollAmount = 0.0f;

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        this.listLeft = centerX - 105;
        this.listRight = centerX + 105;
        this.listTop = centerY + 58;
        this.listBottom = listTop + LIST_HEIGHT;

        Team team = this.minecraft.player.getTeam();
        String currentDim = this.minecraft.level.dimension().location().toString();

        boolean hasSpecificSpawn = false;
        if (team != null) {
            String myTeam = team.getName();
            if (myTeam.equalsIgnoreCase("Blue")) {
                hasSpecificSpawn = ClientData.blueSpawns.containsKey(currentDim) || ClientData.blueSpawns.containsKey("minecraft:overworld");
            } else if (myTeam.equalsIgnoreCase("Red")) {
                hasSpecificSpawn = ClientData.redSpawns.containsKey(currentDim) || ClientData.redSpawns.containsKey("minecraft:overworld");
            }
        } else {
            hasSpecificSpawn = ClientData.neutralSpawns.containsKey(currentDim);
        }

        String mainText = hasSpecificSpawn ? "MAIN BASE" : "SPAWN";

        mainSpawnButton = Button.builder(Component.literal(mainText), button -> {
            PacketHandler.INSTANCE.sendToServer(new PacketRespawnRequest("MAIN"));
            this.minecraft.player.respawn();
            this.minecraft.setScreen(null);
        }).bounds(centerX - 105, centerY + 10, 100, 20).build();

        mainSpawnButton.active = false;
        this.addRenderableWidget(mainSpawnButton);

        rallySpawnButton = Button.builder(Component.literal("SQUAD RALLY"), button -> {
            PacketHandler.INSTANCE.sendToServer(new PacketRespawnRequest("RALLY"));
            this.minecraft.player.respawn();
            this.minecraft.setScreen(null);
        }).bounds(centerX + 5, centerY + 10, 100, 20).build();

        rallySpawnButton.active = false;
        this.addRenderableWidget(rallySpawnButton);

        // Логика проверки наличия хабов для текста
        boolean hubsExist = false;
        if (team != null) {
            String myTeam = team.getName();
            hubsExist = ClientData.clientHubs.stream()
                    .anyMatch(h -> h.team.equalsIgnoreCase(myTeam) && h.constructed && h.dimension.equals(currentDim));
        }

        String hubBtnText = hubsExist ? "HUB SPAWN \u25BC" : "NO HUBS";

        hubSpawnButton = Button.builder(Component.literal(hubBtnText), button -> {
            isHubListOpen = !isHubListOpen;
            if (!isHubListOpen) scrollAmount = 0.0f;
            if (isHubListOpen) createHubButtons();
        }).bounds(centerX - 50, centerY + 35, 100, 20).build();

        this.addRenderableWidget(hubSpawnButton);

        disconnectButton = Button.builder(Component.literal("Disconnect"), button -> {
            this.minecraft.level.disconnect();
            this.minecraft.clearLevel();
            this.minecraft.setScreen(new net.minecraft.client.gui.screens.TitleScreen());
        }).bounds(centerX - 50, this.height - 30, 100, 20).build();

        this.addRenderableWidget(disconnectButton);

        if (isHubListOpen) {
            createHubButtons();
        }
    }

    @Override
    public void tick() {
        super.tick();
        // 20 тиков = 1 секунда в Minecraft
        if (this.minecraft.level != null && this.minecraft.level.getGameTime() % 20 == 0) {
            // Мы вызываем init(), чтобы пересобрать список кнопок с новыми данными (материалы, статус блокировки)
            this.init();
        }
    }

    private void createHubButtons() {
        hubButtons.clear();
        Team team = this.minecraft.player.getTeam();
        if (team == null) return;

        String myTeam = team.getName();
        String myDimension = this.minecraft.level.dimension().location().toString();
        int centerX = this.width / 2;

        // Читаем настройки конфига
        boolean spawnCosts = ClientData.serverHubSpawnCosts;
        int spawnCost = ClientData.serverHubSpawnCostAmount;

        for (com.example.aas.world.AASWorldData.HubInfo hub : ClientData.clientHubs) {
            if (hub.team.equalsIgnoreCase(myTeam) && hub.constructed && hub.dimension.equals(myDimension)) {

                boolean isBlocked = hub.isBlocked;
                // Теперь проверяем по серверным данным
                boolean notEnoughMats = spawnCosts && hub.materials < spawnCost;

                String coordText = "X:" + hub.pos.getX() + " Z:" + hub.pos.getZ();
                String pointInfo = getNearestPointInfo(hub.pos);

                String status = "";
                if (isBlocked) {
                    status = " [!] OVERRUN";
                } else if (spawnCosts) {
                    // Показываем текущие материалы (они уже синхронизируются в HubInfo)
                    status = " [" + hub.materials + "/" + spawnCost + " Mats]";
                }

                Component btnTextComp = Component.literal(coordText + " | " + pointInfo + status);

                // Красим текст в красный, если спавн невозможен
                if (isBlocked || notEnoughMats) {
                    btnTextComp = btnTextComp.copy().withStyle(ChatFormatting.RED);
                } else {
                    btnTextComp = btnTextComp.copy().withStyle(ChatFormatting.WHITE);
                }

                Button btn = Button.builder(btnTextComp, b -> {
                    String payload = "HUB:" + hub.pos.getX() + ":" + hub.pos.getY() + ":" + hub.pos.getZ();
                    PacketHandler.INSTANCE.sendToServer(new PacketRespawnRequest(payload));
                    this.minecraft.player.respawn();
                    this.minecraft.setScreen(null);
                }).bounds(centerX - 100, 0, 200, 20).build();

                // Если материалов нет или хаб заблокирован — кнопка не нажимается
                if (isBlocked || notEnoughMats) {
                    btn.active = false;
                }

                btn.visible = false; // Будет отображено через скролл-лист в методе render
                hubButtons.add(btn);
                this.addRenderableWidget(btn);
            }
        }
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        gui.fill(0, 0, this.width, this.height, 0xFF000000);

        gui.drawCenteredString(this.font, this.title, this.width / 2, 30, 0xFFFFFF);
        if (this.cause != null) {
            gui.drawCenteredString(this.font, this.cause, this.width / 2, 50, 0xFFFFFF);
        }

        long secondsLeft = respawnTimeTotal - ((System.currentTimeMillis() - deathTimestamp) / 1000);
        if (secondsLeft < 0) secondsLeft = 0;

        int timerColor = secondsLeft > 0 ? 0xFFAA00 : 0x00FF00;
        gui.drawCenteredString(this.font, "RESPAWN IN: " + secondsLeft, this.width / 2, (this.height / 2) - 15, timerColor);

        boolean timerFinished = (secondsLeft == 0);

        mainSpawnButton.active = timerFinished;
        // 1. Проверяем, закончился ли таймер возрождения

        // 2. Ищем, есть ли у нашей команды хотя бы один готовый Хаб в этом мире
        boolean hasAtLeastOneHub = false;
        Team team = this.minecraft.player.getTeam();
        if (team != null) {
            String myTeam = team.getName();
            String myDimension = this.minecraft.level.dimension().location().toString();

            for (com.example.aas.world.AASWorldData.HubInfo hub : ClientData.clientHubs) {
                // Условия: команда совпадает, Хаб достроен, мир совпадает
                if (hub.team.equalsIgnoreCase(myTeam) && hub.constructed && hub.dimension.equals(myDimension)) {
                    // Опционально: можно добавить проверку !hub.isBlocked,
                    // чтобы кнопка не горела, если все хабы заблокированы врагом
                    if (!hub.isBlocked) {
                        hasAtLeastOneHub = true;
                        break;
                    }
                }
            }
        }

        // 3. Кнопка активна только если таймер вышел И есть куда прыгать
        hubSpawnButton.active = timerFinished && hasAtLeastOneHub;

        boolean hasSquadRally = false;
        String currentDim = this.minecraft.level.dimension().location().toString();
        String myName = this.minecraft.player.getScoreboardName();

        for (AASWorldData.Squad s : ClientData.clientSquads) {
            if (s.members.contains(myName) && s.rallyPos != null) {
                if (s.rallyDimension != null && s.rallyDimension.equals(currentDim)) {
                    hasSquadRally = true;
                    break;
                }
            }
        }
        rallySpawnButton.active = timerFinished && hasSquadRally;

        for (Button b : hubButtons) b.visible = false;

        if (isHubListOpen && timerFinished && !hubButtons.isEmpty()) {
            gui.fill(listLeft, listTop, listRight, listBottom, 0x90000000);
            gui.enableScissor(listLeft, listTop, listRight, listBottom);

            int currentY = (int) (listTop - scrollAmount);

            for (Button btn : hubButtons) {
                btn.setY(currentY);
                if (currentY + 20 >= listTop && currentY <= listBottom) {
                    btn.visible = true;
                    btn.render(gui, mouseX, mouseY, partialTick);
                } else {
                    btn.visible = false;
                }
                currentY += ITEM_HEIGHT;
            }

            gui.disableScissor();

            int contentHeight = hubButtons.size() * ITEM_HEIGHT;
            if (contentHeight > LIST_HEIGHT) {
                int scrollBarX = listLeft - 6;
                int scrollBarWidth = 4;
                int scrollBarHeight = LIST_HEIGHT;

                gui.fill(scrollBarX, listTop, scrollBarX + scrollBarWidth, listTop + scrollBarHeight, 0xFF202020);

                float ratio = (float) LIST_HEIGHT / (float) contentHeight;
                int thumbHeight = (int) (scrollBarHeight * ratio);
                if (thumbHeight < 10) thumbHeight = 10;

                float maxScroll = contentHeight - LIST_HEIGHT;
                int thumbY = listTop + (int) ((scrollAmount / maxScroll) * (scrollBarHeight - thumbHeight));

                gui.fill(scrollBarX, thumbY, scrollBarX + scrollBarWidth, thumbY + thumbHeight, 0xFF808080);
                gui.fill(scrollBarX, thumbY, scrollBarX + scrollBarWidth - 1, thumbY + thumbHeight - 1, 0xFFC0C0C0);
            }
        }

        mainSpawnButton.render(gui, mouseX, mouseY, partialTick);
        rallySpawnButton.render(gui, mouseX, mouseY, partialTick);
        hubSpawnButton.render(gui, mouseX, mouseY, partialTick);
        disconnectButton.render(gui, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (isHubListOpen && !hubButtons.isEmpty()) {
            int contentHeight = hubButtons.size() * ITEM_HEIGHT;
            if (contentHeight > LIST_HEIGHT) {
                float maxScroll = contentHeight - LIST_HEIGHT;
                scrollAmount -= (float) (delta * 15.0);
                scrollAmount = Mth.clamp(scrollAmount, 0, maxScroll);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isHubListOpen && !hubButtons.isEmpty()) {
            int scrollBarX = listLeft - 6;
            if (mouseX >= scrollBarX - 2 && mouseX <= scrollBarX + 8 && mouseY >= listTop && mouseY <= listBottom) {
                isScrolling = true;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        isScrolling = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isScrolling && isHubListOpen) {
            int contentHeight = hubButtons.size() * ITEM_HEIGHT;
            if (contentHeight > LIST_HEIGHT) {
                float maxScroll = contentHeight - LIST_HEIGHT;
                int barHeight = LIST_HEIGHT;
                float ratio = (float) LIST_HEIGHT / (float) contentHeight;
                int thumbHeight = (int) (barHeight * ratio);
                if (thumbHeight < 10) thumbHeight = 10;
                float scrollPerPixel = maxScroll / (barHeight - thumbHeight);
                scrollAmount += (float) (dragY * scrollPerPixel);
                scrollAmount = Mth.clamp(scrollAmount, 0, maxScroll);
                return true;
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private String getNearestPointInfo(BlockPos hubPos) {
        if (ClientData.allCapturePoints.isEmpty()) return "Wilderness";
        AASWorldData.CapturePoint nearest = null;
        double minDistanceSq = Double.MAX_VALUE;

        for (AASWorldData.CapturePoint cp : ClientData.allCapturePoints) {
            Vec3 center = cp.area.getCenter();
            double dSq = hubPos.distToCenterSqr(center.x, center.y, center.z);
            if (dSq < minDistanceSq) {
                minDistanceSq = dSq;
                nearest = cp;
            }
        }

        if (nearest != null) {
            int distMeters = (int) Math.sqrt(minDistanceSq);
            return nearest.name + " (" + distMeters + "m)";
        }
        return "Unknown";
    }
}