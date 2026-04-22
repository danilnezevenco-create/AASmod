package com.example.aas.client.gui;

import com.example.aas.client.ClientData;
import com.example.aas.network.PacketHandler;
import com.example.aas.network.PacketSquadAction;
import com.example.aas.network.PacketSquadChat;
import com.example.aas.network.PacketRequestKitMenu;
import com.example.aas.world.AASWorldData;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.lwjgl.glfw.GLFW;
import net.minecraft.core.BlockPos;
import java.util.*;
import java.util.stream.Collectors;

public class SquadSelectionScreen extends Screen {

    private static final int SIDEBAR_WIDTH = 170;
    private static final int TOP_BAR_HEIGHT = 30;

    private static final ResourceLocation LOCK_ICON = new ResourceLocation("aas", "textures/gui/squad_lock.png");
    private static final ResourceLocation ARROW_DOWN = new ResourceLocation("aas", "textures/gui/arrow_down.png");
    private static final ResourceLocation ARROW_UP = new ResourceLocation("aas", "textures/gui/arrow_up.png");
    private static final ResourceLocation CENTER_ICON = new ResourceLocation("minecraft", "textures/item/compass_16.png");

    private static final ResourceLocation FLAG_UKRAINE = new ResourceLocation("aas", "textures/gui/flags/ukraine.png");
    private static final ResourceLocation FLAG_RUSSIA = new ResourceLocation("aas", "textures/gui/flags/russia.png");
    private static final ResourceLocation FLAG_USA = new ResourceLocation("aas", "textures/gui/flags/usa.png");
    private static final ResourceLocation FLAG_NATO = new ResourceLocation("aas", "textures/gui/flags/nato.png");
    private static final ResourceLocation FLAG_BLUEFOR = new ResourceLocation("aas", "textures/gui/flags/bluefor.png");
    private static final ResourceLocation FLAG_REDFOR = new ResourceLocation("aas", "textures/gui/flags/redfor.png");

    private EditBox nameInput;
    private Button createButton;
    private EditBox chatInput;
    private Button chatModeButton;
    private int chatMode = 1;

    private int mapX, mapY, mapSize;

    private final Set<Integer> expandedSquads = new HashSet<>();
    private final AASMapRenderer mapRenderer = new AASMapRenderer();


    public SquadSelectionScreen() {
        super(Component.literal("Squad Selection"));
    }

    @Override
    protected void init() {
        super.init();
        boolean isInSquad = isPlayerInSquad();
        String myName = this.minecraft.player.getScoreboardName();

        for (AASWorldData.Squad s : ClientData.clientSquads) {
            if (s.members.contains(myName)) {
                expandedSquads.add(s.id);
            }
        }

        // --- ЛЕВАЯ ПАНЕЛЬ ---
        nameInput = new EditBox(this.font, 10, this.height - 55, SIDEBAR_WIDTH - 20, 20, Component.literal("Squad Name"));
        nameInput.setMaxLength(12);
        nameInput.setVisible(!isInSquad);
        this.addRenderableWidget(nameInput);

        createButton = Button.builder(Component.literal("Create Squad"), button -> {
                    String name = nameInput.getValue();
                    PacketHandler.INSTANCE.sendToServer(new PacketSquadAction(0, 0, name));
                })
                .bounds(10, this.height - 30, SIDEBAR_WIDTH - 20, 20)
                .build();
        createButton.visible = !isInSquad;
        this.addRenderableWidget(createButton);

        // --- ПРАВАЯ ПАНЕЛЬ (КАРТА) ---
        int rightAreaWidth = this.width - SIDEBAR_WIDTH;
        int mapMargin = 2;
        int availableHeight = this.height - TOP_BAR_HEIGHT - 50;

        // ПРИСВАИВАЕМ ЗНАЧЕНИЯ ПОЛЯМ КЛАССА (без "int" в начале!)
        this.mapSize = Math.min(rightAreaWidth - (mapMargin * 2), availableHeight);
        this.mapX = this.width - mapSize - mapMargin;
        this.mapY = TOP_BAR_HEIGHT + mapMargin;

        mapRenderer.init(this.mapX, this.mapY, this.mapSize);

        // Чат
        int inputY = this.height - 25;
        int chatX = SIDEBAR_WIDTH + 5;
        int chatWidth = this.width - SIDEBAR_WIDTH - 10;

        chatModeButton = Button.builder(getChatModeText(), button -> {
            chatMode++;
            if (chatMode > 2) chatMode = 0;
            button.setMessage(getChatModeText());
        }).bounds(chatX, inputY, 50, 20).build();
        this.addRenderableWidget(chatModeButton);

        chatInput = new EditBox(this.font, chatX + 55, inputY, chatWidth - 55, 20, Component.literal("Chat"));
        chatInput.setMaxLength(256);
        this.addRenderableWidget(chatInput);
    }

    private Component getChatModeText() {
        switch (chatMode) {
            case 0: return Component.literal("ALL").withStyle(ChatFormatting.LIGHT_PURPLE);
            case 1: return Component.literal("TEAM").withStyle(ChatFormatting.BLUE);
            case 2: return Component.literal("SQUAD").withStyle(ChatFormatting.GREEN);
            default: return Component.literal("???");
        }
    }


    @Override
    public void tick() {
        super.tick();
        nameInput.tick();
        chatInput.tick();
        boolean isInSquad = isPlayerInSquad();
        if (nameInput.isVisible() == isInSquad) {
            nameInput.setVisible(!isInSquad);
            createButton.visible = !isInSquad;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (chatInput.isFocused()) {
                String msg = chatInput.getValue().trim();
                if (!msg.isEmpty()) {
                    PacketHandler.INSTANCE.sendToServer(new PacketSquadChat(msg, chatMode));
                    chatInput.setValue("");
                }
                return true;
            }
            if (nameInput.isFocused() && nameInput.isVisible()) {
                String name = nameInput.getValue();
                PacketHandler.INSTANCE.sendToServer(new PacketSquadAction(0, 0, name));
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private List<String> getSortedMembers(AASWorldData.Squad squad) {
        List<String> list = new ArrayList<>(squad.members);
        if (list.contains(squad.leader)) {
            list.remove(squad.leader);
            list.add(0, squad.leader);
        }
        return list;
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        gui.fill(0, 0, SIDEBAR_WIDTH, this.height, 0xFF000000);
        gui.fill(SIDEBAR_WIDTH, 0, this.width, TOP_BAR_HEIGHT, 0xFF000000);
        gui.fill(SIDEBAR_WIDTH, TOP_BAR_HEIGHT, this.width, this.height, 0xAA000000);

        renderSquadList(gui, mouseX, mouseY);

        // РЕНДЕР КАРТЫ ЧЕРЕЗ НОВЫЙ КЛАСС
        mapRenderer.render(gui, mouseX, mouseY, partialTick);

        // Иконка "Прицел" на кнопке рецентра
        int rightAreaWidth = this.width - SIDEBAR_WIDTH;
        int mapMargin = 2;
        int availableHeight = this.height - TOP_BAR_HEIGHT - 50;
        int mapSize = Math.min(rightAreaWidth - (mapMargin * 2), availableHeight);
        int mapX = this.width - mapSize - mapMargin;
        int mapY = TOP_BAR_HEIGHT + mapMargin;

        renderChatHistory(gui, mapY, mapSize);
        renderTopBar(gui);

        super.render(gui, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mapRenderer.mouseScrolled(mouseX, mouseY, delta)) return true;
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 1. Проверяем карту
        if (mapRenderer.isMouseOver(mouseX, mouseY)) {
            if (mapRenderer.mouseClicked(mouseX, mouseY, button)) {
                if (button == 1) { // ПКМ по карте
                    handleMapRightClick(mouseX, mouseY);
                }
                return true;
            }
        }

        // 2. Обычные клики
        if (super.mouseClicked(mouseX, mouseY, button)) return true;

        // 3. Список отрядов
        if (button == 0 && mouseX < SIDEBAR_WIDTH && mouseY < (this.height - 60)) {
            handleSquadListClicks(mouseX, mouseY);
            return true;
        }
        return false;
    }

    private void handleMapRightClick(double mouseX, double mouseY) {
        // 1. Проверяем, является ли игрок лидером (через существующий метод в этом же классе)
        if (!isSquadLeader(this.minecraft.player)) {
            this.minecraft.player.displayClientMessage(Component.literal("Only Squad Leaders can place markers!").withStyle(ChatFormatting.RED), true);
            return;
        }

        // 2. Получаем масштаб и центр из рендерера
        double bpp = mapRenderer.getBlocksPerPixel();
        double centerX = mapRenderer.getCenterX(this.minecraft.player);
        double centerZ = mapRenderer.getCenterZ(this.minecraft.player);

        // 3. Рассчитываем мировые координаты точки клика
        // Используем поля mapX, mapY, mapSize, которые инициализированы в методе init() экрана
        int targetX = (int) (centerX + ((mouseX - (this.mapX + this.mapSize / 2.0)) * bpp));
        int targetZ = (int) (centerZ + ((mouseY - (this.mapY + this.mapSize / 2.0)) * bpp));

        // === ВОТ ТУТ ГЛАВНОЕ ИЗМЕНЕНИЕ ===
        // Вместо SquadMarkerRadialScreen открываем наше новое ГЛАВНОЕ тактическое меню
        this.minecraft.setScreen(new TacticalMapRadialScreen(targetX, targetZ));
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        mapRenderer.mouseReleased(button);
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (mapRenderer.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    private void handleSquadListClicks(double mouseX, double mouseY) {
        String myName = this.minecraft.player.getScoreboardName();
        String myTeam = getPlayerTeam();
        boolean amIInSquad = isPlayerInSquad();
        String myDim = this.minecraft.level.dimension().location().toString();

        List<AASWorldData.Squad> myTeamSquads = ClientData.clientSquads.stream()
                .filter(s -> s.team.equalsIgnoreCase(myTeam))
                .filter(s -> s.dimension != null && s.dimension.equals(myDim))
                .collect(Collectors.toList());

        int currentY = 10;

        for (AASWorldData.Squad squad : myTeamSquads) {
            boolean isMySquad = squad.members.contains(myName);
            boolean amILeader = squad.leader.equals(myName);
            boolean isExpanded = expandedSquads.contains(squad.id);

            String actionText = "";
            boolean clickable = true;
            if (isMySquad) actionText = "LEAVE";
            else if (!amIInSquad) {
                if (squad.isLocked) { actionText = "LOCKED"; clickable = false; }
                else if (squad.members.size() >= 9) { actionText = "FULL"; clickable = false; }
                else actionText = "JOIN";
            }

            int actionWidth = this.font.width(actionText);
            int actionX = SIDEBAR_WIDTH - actionWidth - 10;
            int arrowX = (actionX > 0 ? actionX : SIDEBAR_WIDTH - 10) - 12;
            int lockX = arrowX - 12;

            if (amILeader) {
                if (mouseX >= lockX && mouseX <= lockX + 8 && mouseY >= currentY && mouseY <= currentY + 9) {
                    PacketHandler.INSTANCE.sendToServer(new PacketSquadAction(5, squad.id, ""));
                    playClickSound(); return;
                }
            }
            if (mouseX >= arrowX && mouseX <= arrowX + 8 && mouseY >= currentY && mouseY <= currentY + 9) {
                if (isExpanded) expandedSquads.remove(squad.id); else expandedSquads.add(squad.id);
                playClickSound(); return;
            }
            if (!actionText.isEmpty() && clickable) {
                if (mouseX >= actionX && mouseX <= actionX + actionWidth && mouseY >= currentY && mouseY <= currentY + 9) {
                    int act = isMySquad ? 2 : 1;
                    PacketHandler.INSTANCE.sendToServer(new PacketSquadAction(act, squad.id, ""));
                    playClickSound(); return;
                }
            }
            currentY += 12;

            if (isExpanded) {
                List<String> sortedMembers = getSortedMembers(squad);
                for (String member : sortedMembers) {

                    // ЕСЛИ КЛИКНУЛИ НА КНОПКУ [K] ВЫБОРА КИТА
                    if (isMySquad && member.equals(myName)) {
                        int btnX = 30; // Стартовый X для кнопки K
                        if (mouseX >= btnX && mouseX <= btnX + 10 && mouseY >= currentY && mouseY <= currentY + 10) {
                            PacketHandler.INSTANCE.sendToServer(new PacketRequestKitMenu());
                            playClickSound();
                            return;
                        }
                    }

                    if (amILeader && !member.equals(myName)) {
                        int kX = SIDEBAR_WIDTH - 25;
                        if (mouseX >= kX && mouseX <= kX + 10 && mouseY >= currentY && mouseY <= currentY + 9) {
                            PacketHandler.INSTANCE.sendToServer(new PacketSquadAction(3, squad.id, member));
                            playClickSound(); return;
                        }
                        int pX = SIDEBAR_WIDTH - 40;
                        if (mouseX >= pX && mouseX <= pX + 10 && mouseY >= currentY && mouseY <= currentY + 9) {
                            PacketHandler.INSTANCE.sendToServer(new PacketSquadAction(4, squad.id, member));
                            playClickSound(); return;
                        }
                    }
                    currentY += 12;
                }
                currentY += 4;
            }
            currentY += 4;
        }
    }

    private boolean isSquadLeader(Player player) {
        String pName = player.getScoreboardName();
        for (AASWorldData.Squad s : ClientData.clientSquads) {
            if (s.leader.equals(pName)) return true;
        }
        return false;
    }

    private void renderChatHistory(GuiGraphics gui, int mapY, int mapSize) {
        int chatX = SIDEBAR_WIDTH + 10;
        int chatBottomY = this.height - 35;
        int chatTopY = mapY + mapSize + 10;

        int count = 0;
        for (Component msg : ClientData.menuChatHistory) {
            int y = chatBottomY - (count * 10);
            if (y < chatTopY) break;
            gui.drawString(this.font, msg, chatX, y, 0xFFFFFFFF, true);
            count++;
        }
    }

    private void renderSquadList(GuiGraphics gui, int mouseX, int mouseY) {
        String myName = this.minecraft.player.getScoreboardName();
        String myTeam = getPlayerTeam();
        boolean amIInSquad = isPlayerInSquad();
        String myDim = this.minecraft.level.dimension().location().toString();

        List<AASWorldData.Squad> myTeamSquads = ClientData.clientSquads.stream()
                .filter(s -> s.team.equalsIgnoreCase(myTeam))
                .filter(s -> s.dimension != null && s.dimension.equals(myDim))
                .collect(Collectors.toList());

        int currentY = 10;
        int index = 1;

        for (AASWorldData.Squad squad : myTeamSquads) {
            boolean isMySquad = squad.members.contains(myName);
            boolean amILeader = squad.leader.equals(myName);
            boolean isExpanded = expandedSquads.contains(squad.id);

            gui.drawString(this.font, index + ".", 5, currentY, 0xFFFFFFFF, false);
            String squadDisplayName = squad.name + " (" + squad.members.size() + "/9)";
            gui.drawString(this.font, squadDisplayName, 25, currentY, 0xFFFFD700, false);

            String actionText = "";
            int actionColor = 0xFFFFFFFF;
            boolean clickable = true;

            if (isMySquad) {
                actionText = "LEAVE"; actionColor = 0xFFFF5555;
            } else if (!amIInSquad) {
                if (squad.isLocked) { actionText = "LOCKED"; actionColor = 0xFFFFAA00; clickable = false; }
                else if (squad.members.size() >= 9) { actionText = "FULL"; actionColor = 0xFF888888; clickable = false; }
                else { actionText = "JOIN"; actionColor = 0xFF55FF55; }
            }

            int actionX = 0;
            int actionWidth = 0;
            if (!actionText.isEmpty()) {
                actionWidth = this.font.width(actionText);
                actionX = SIDEBAR_WIDTH - actionWidth - 10;
                boolean hover = mouseX >= actionX && mouseX <= actionX + actionWidth && mouseY >= currentY && mouseY <= currentY + 9;
                int finalColor = (hover && clickable) ? 0xFFFFFFFF : actionColor;
                gui.drawString(this.font, actionText, actionX, currentY, finalColor, false);
            }

            int arrowX = (actionX > 0 ? actionX : SIDEBAR_WIDTH - 10) - 12;
            int arrowSize = 8;
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            if (isExpanded) {
                RenderSystem.setShaderColor(1.0f, 0.8f, 0.2f, 1.0f);
                gui.blit(ARROW_DOWN, arrowX, currentY + 1, 0, 0, arrowSize, arrowSize, arrowSize, arrowSize);
            } else {
                RenderSystem.setShaderColor(0.7f, 0.7f, 0.7f, 1.0f);
                gui.blit(ARROW_UP, arrowX, currentY + 1, 0, 0, arrowSize, arrowSize, arrowSize, arrowSize);
            }
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

            if (amILeader || squad.isLocked) {
                int lockX = arrowX - 12;
                if (squad.isLocked) RenderSystem.setShaderColor(1.0f, 0.8f, 0.2f, 1.0f);
                else RenderSystem.setShaderColor(0.6f, 0.6f, 0.6f, 1.0f);
                gui.blit(LOCK_ICON, lockX, currentY + 1, 0, 0, 8, 8, 8, 8);
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            }

            currentY += 12;

            if (isExpanded) {
                List<String> sortedMembers = getSortedMembers(squad);
                for (String member : sortedMembers) {
                    boolean isLeaderMember = member.equals(squad.leader);

                    // ПРЕФИКС ПОЛНОСТЬЮ УБРАН
                    String prefix = "";
                    int col = isLeaderMember ? 0xFFFFD700 : 0xFFAAAAAA;

                    int xOffset = 30;

                    // КНОПКА [K] ТОЛЬКО У САМОГО ИГРОКА
                    if (isMySquad && member.equals(myName)) {
                        int btnX = xOffset;
                        boolean btnHover = mouseX >= btnX && mouseX <= btnX + 10 && mouseY >= currentY && mouseY <= currentY + 10;
                        gui.fill(btnX, currentY, btnX + 10, currentY + 10, btnHover ? 0xFF666666 : 0xFF444444);
                        gui.drawString(this.font, "K", btnX + 2, currentY + 1, 0xFFFFFFFF, false);
                        xOffset += 14;
                    }

                    // ЛОГИКА КИТА
                    String kitName = ClientData.playerKits.getOrDefault(member, "Unassigned");

                    if (kitName != null && !kitName.equalsIgnoreCase("Unassigned") && !kitName.isEmpty()) {
                        ResourceLocation kitIcon = new ResourceLocation("aas", "textures/gui/kits/" + kitName.toLowerCase().replace(" ", "_") + ".png");
                        RenderSystem.enableBlend();
                        RenderSystem.defaultBlendFunc();
                        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                        gui.blit(kitIcon, xOffset, currentY, 0, 0, 10, 10, 10, 10);
                        xOffset += 12; // Отступ после иконки до ника
                    }

                    // Рисуем ник (префикс теперь пустой "")
                    gui.drawString(this.font, prefix + member, xOffset, currentY + 1, col, false);

                    if (amILeader && !member.equals(myName)) {
                        int kickX = SIDEBAR_WIDTH - 25;
                        boolean hKick = mouseX >= kickX && mouseX <= kickX + 10 && mouseY >= currentY && mouseY <= currentY + 9;
                        gui.drawString(this.font, "K", kickX, currentY + 1, hKick ? 0xFFFF5555 : 0xFF888888, false);

                        int promX = SIDEBAR_WIDTH - 40;
                        boolean hProm = mouseX >= promX && mouseX <= promX + 10 && mouseY >= currentY && mouseY <= currentY + 9;
                        gui.drawString(this.font, "P", promX, currentY + 1, hProm ? 0xFF55FF55 : 0xFF888888, false);
                    }
                    currentY += 12;
                }
                currentY += 4;
            }
            currentY += 4;
            index++;
        }
    }

    private void renderTopBar(GuiGraphics gui) {
        String myTeam = getPlayerTeam();
        int tickets = 0;
        ResourceLocation flag = null;
        if (myTeam.equalsIgnoreCase("Blue")) {
            tickets = ClientData.BLUE_TICKETS;
            flag = getFlagTexture(ClientData.BLUE_FACTION);
        } else if (myTeam.equalsIgnoreCase("Red")) {
            tickets = ClientData.RED_TICKETS;
            flag = getFlagTexture(ClientData.RED_FACTION);
        }
        int rightEdge = this.width - 10;
        String ticketText = "Tickets: " + tickets;
        int textWidth = this.font.width(ticketText);
        gui.drawString(this.font, ticketText, rightEdge - textWidth, 11, 0xFFFFFFFF, false);
        if (flag != null) {
            gui.pose().pushPose();
            gui.pose().translate(0, 0, 100);
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            int flagX = rightEdge - textWidth - 15 - 32;
            int flagY = (TOP_BAR_HEIGHT - 18) / 2;
            gui.blit(flag, flagX, flagY, 0, 0, 32, 18, 32, 18);
            gui.pose().popPose();
        }
    }

    private void playClickSound() {
        this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    private boolean isPlayerInSquad() {
        String myName = this.minecraft.player.getScoreboardName();
        for (AASWorldData.Squad s : ClientData.clientSquads) {
            if (s.members.contains(myName)) return true;
        }
        return false;
    }

    private String getPlayerTeam() {
        if (this.minecraft.player.getTeam() != null) {
            return this.minecraft.player.getTeam().getName();
        }
        return "NEUTRAL";
    }

    private ResourceLocation getFlagTexture(String faction) {
        if (faction == null || faction.equalsIgnoreCase("none")) return null;
        switch (faction.toLowerCase()) {
            case "ukraine": return FLAG_UKRAINE;
            case "russia": return FLAG_RUSSIA;
            case "usa": return FLAG_USA;
            case "nato": return FLAG_NATO;
            default: return null;
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}