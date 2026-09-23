package com.example.aas.client.gui;

import com.example.aas.client.ClientData;
import com.example.aas.network.*;
import com.example.aas.network.MapPlayerInfo;
import com.example.aas.world.AASWorldData;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
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
    private SquadButton applyCmdButton;
    private static final int SIDEBAR_WIDTH = 195;
    private static final int TOP_BAR_HEIGHT = 30;

    private static final ResourceLocation LOCK_ICON = new ResourceLocation("aas", "textures/gui/squad_lock.png");
    private static final ResourceLocation ARROW_DOWN = new ResourceLocation("aas", "textures/gui/arrow_down.png");
    private static final ResourceLocation ARROW_UP = new ResourceLocation("aas", "textures/gui/arrow_up.png");
    private static final ResourceLocation CENTER_ICON = new ResourceLocation("minecraft", "textures/item/compass_16.png");
    private static final ResourceLocation CIRCLE_BADGE = new ResourceLocation("aas", "textures/gui/map_icons/player_circle.png");
    private static final ResourceLocation ICON_DEAD      = new ResourceLocation("aas", "textures/gui/stats/deaths.png");
    private static final ResourceLocation ICON_HEARTBEAT = new ResourceLocation("aas", "textures/gui/heartbeat.png");
    private static final ResourceLocation ICON_DISCONNECT = new ResourceLocation("aas", "textures/gui/disconnect.png");

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

    private EditBox nameInput;
    private Button createButton;
    private EditBox chatInput;
    private Button chatModeButton;
    private int chatMode = 1;
    private boolean showContextMenu = false;
    private int contextMenuX = 0;
    private int contextMenuY = 0;
    private String contextTargetPlayer = "";
    private int contextTargetSquadId = -1;
    private boolean contextTargetIsSquad = false;

    private int mapX, mapY, mapSize;

    private final Set<Integer> expandedSquads = new HashSet<>();
    private int lastOwnSquadId = -1; // РѕС‚СЃР»РµР¶РёРІР°РµРј СЃРІРѕР№ РѕС‚СЂСЏРґ, С‡С‚РѕР±С‹ Р°РІС‚РѕСЂР°Р·РІРѕСЂР°С‡РёРІР°С‚СЊ РµРіРѕ РїСЂРё СЃРѕР·РґР°РЅРёРё/РІС…РѕРґРµ Р±РµР· РїРµСЂРµРѕС‚РєСЂС‹С‚РёСЏ СЌРєСЂР°РЅР°
    private final AASMapRenderer mapRenderer = new AASMapRenderer();


    public SquadSelectionScreen() {
        super(Component.literal("Squad Selection"));
    }

    @Override
    protected void init() {
        super.init();
        String myName = this.minecraft.player.getScoreboardName();
        String myTeam = getPlayerTeam().toUpperCase();
        boolean isInSquad = isPlayerInSquad();

        // Р С›Р В±РЎР‰Р ВµР Т‘Р С‘Р Р…РЎРЏР ВµР С Р С—Р С•Р С‘РЎРѓР С” РЎРѓР Р†Р С•Р ВµР С–Р С• Р С•РЎвЂљРЎР‚РЎРЏР Т‘Р В° Р С‘ РЎР‚Р В°РЎРѓРЎв‚¬Р С‘РЎР‚Р ВµР Р…Р С‘Р Вµ РЎРѓР С—Р С‘РЎРѓР С”Р В°
        AASWorldData.Squad mySquad = null;
        for (AASWorldData.Squad s : ClientData.clientSquads) {
            if (s.members.contains(myName)) {
                expandedSquads.add(s.id);
                if (s.leader.equals(myName)) mySquad = s;
                lastOwnSquadId = s.id; // РґРѕР±Р°РІРёС‚СЊ СЌС‚Сѓ СЃС‚СЂРѕРєСѓ
            }
        }

        int teamCMDId_init = myTeam.contains("BLUE") ? ClientData.blueCMDId : ClientData.redCMDId;
        this.applyCmdButton = new SquadButton(10, 10, SIDEBAR_WIDTH - 20, 20, Component.literal("APPLY FOR CMD"), b -> {
            PacketHandler.INSTANCE.sendToServer(new PacketRequestCMD());
            b.visible = false;
        });
        this.addRenderableWidget(this.applyCmdButton);

        // Р СџР С•Р В»РЎРЏ Р Р†Р Р†Р С•Р Т‘Р В° (nameInput Р С‘ createButton)
        nameInput = new EditBox(this.font, 10, this.height - 55, SIDEBAR_WIDTH - 20, 20, Component.literal("Squad Name"));
        nameInput.setMaxLength(12);
        nameInput.setVisible(!isInSquad);
        this.addRenderableWidget(nameInput);

        createButton = this.addRenderableWidget(new SquadButton(10, this.height - 30, SIDEBAR_WIDTH - 20, 20, Component.literal("Create Squad"), button -> {
            PacketHandler.INSTANCE.sendToServer(new PacketSquadAction(0, 0, nameInput.getValue()));
            this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(com.example.aas.sound.ModSounds.SQUAD_JOIN.get(), 1.0F));
        }));
        createButton.visible = !isInSquad;

        // Р С™Р В°РЎР‚РЎвЂљР В°
        int rightAreaWidth = this.width - SIDEBAR_WIDTH;
        int mapMargin = 2;
        int availableHeight = this.height - TOP_BAR_HEIGHT - 50;
        this.mapSize = Math.min(rightAreaWidth - (mapMargin * 2), availableHeight);
        this.mapX = this.width - mapSize - mapMargin;
        this.mapY = TOP_BAR_HEIGHT + mapMargin;
        mapRenderer.init(this.mapX, this.mapY, this.mapSize);

        // Р В§Р В°РЎвЂљ
        int inputY = this.height - 25;
        int chatX = SIDEBAR_WIDTH + 5;
        int chatWidth = this.width - SIDEBAR_WIDTH - 10;
        chatModeButton = this.addRenderableWidget(new SquadButton(chatX, inputY, 50, 20, getChatModeText(), button -> {
            chatMode++;
            if (chatMode > 2) chatMode = 0;
            button.setMessage(getChatModeText());
        }));

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
        if (this.applyCmdButton != null) {
            String myName = this.minecraft.player.getScoreboardName();
            String myTeam = getPlayerTeam().toUpperCase();

            AASWorldData.Squad mySquad = null;
            for (AASWorldData.Squad s : ClientData.clientSquads) {
                if (s.members.contains(myName)) {
                    mySquad = s;
                    break;
                }
            }

            // 2. Р С›Р С—РЎР‚Р ВµР Т‘Р ВµР В»РЎРЏР ВµР С Р Т‘Р В°Р Р…Р Р…РЎвЂ№Р Вµ Р С™Р С›Р СњР С™Р В Р вЂўР СћР СњР С› Р вЂќР вЂєР Р‡ Р СљР С›Р вЂўР в„ў Р С”Р С•Р СР В°Р Р…Р Т‘РЎвЂ№
            boolean isBlue = myTeam.contains("BLUE");
            int myTeamCMDId = isBlue ? ClientData.blueCMDId : ClientData.redCMDId;
            boolean myTeamVoteActive = isBlue ? ClientData.blueCmdVoteActive : ClientData.redCmdVoteActive;

            // 3. Р С™Р Р…Р С•Р С—Р С”Р В° Р Р†Р С‘Р Т‘Р Р…Р В° Р ВµРЎРѓР В»Р С‘:
            // - Р Р‡ Р Р† Р С•РЎвЂљРЎР‚РЎРЏР Т‘Р Вµ
            // - Р Р‡ Р В»Р С‘Р Т‘Р ВµРЎР‚ РЎРѓР Р†Р С•Р ВµР С–Р С• Р С•РЎвЂљРЎР‚РЎРЏР Т‘Р В°
            // - Р вЂ™ Р СР С•Р ВµР в„– Р С”Р С•Р СР В°Р Р…Р Т‘Р Вµ Р ВµРЎвЂ°Р Вµ Р Р…Р ВµРЎвЂљ Р С™Р С•Р СР В°Р Р…Р Т‘Р С‘РЎР‚Р В°
            // - Р вЂ™ Р СР С•Р ВµР в„– Р С”Р С•Р СР В°Р Р…Р Т‘Р Вµ Р С—РЎР‚РЎРЏР СР С• РЎРѓР ВµР в„–РЎвЂЎР В°РЎРѓ Р Р…Р Вµ Р С‘Р Т‘Р ВµРЎвЂљ Р С–Р С•Р В»Р С•РЎРѓР С•Р Р†Р В°Р Р…Р С‘Р Вµ
            boolean isLeader = (mySquad != null && mySquad.leader.equals(myName));

            this.applyCmdButton.visible = (isLeader && myTeamCMDId == -1 && !myTeamVoteActive);
        }
        boolean isInSquad = isPlayerInSquad();
        if (nameInput.isVisible() == isInSquad) {
            nameInput.setVisible(!isInSquad);
            createButton.visible = !isInSquad;
        }
        // РђРІС‚РѕСЂР°Р·РІРѕСЂР°С‡РёРІР°РЅРёРµ СЃС‚СЂРѕРєРё РѕС‚СЂСЏРґР° РїСЂРё СЃРѕР·РґР°РЅРёРё/РІС…РѕРґРµ, РїРѕРєР° СЌРєСЂР°РЅ РѕС‚РєСЂС‹С‚
        String myNameTick = this.minecraft.player.getScoreboardName();
        AASWorldData.Squad currentOwnSquad = null;
        for (AASWorldData.Squad s : ClientData.clientSquads) {
            if (s.members.contains(myNameTick)) {
                currentOwnSquad = s;
                break;
            }
        }
        int currentOwnSquadId = currentOwnSquad != null ? currentOwnSquad.id : -1;
        if (currentOwnSquadId != lastOwnSquadId) {
            if (currentOwnSquadId != -1) {
                expandedSquads.add(currentOwnSquadId);
            }
            lastOwnSquadId = currentOwnSquadId;
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
                this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(com.example.aas.sound.ModSounds.SQUAD_JOIN.get(), 1.0F));
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private List<String> getSortedMembers(AASWorldData.Squad squad) {
        List<String> sorted = new ArrayList<>();
        // 1. Р РЋР С”Р Р†Р В°Р Т‘ Р В»Р С‘Р Т‘Р ВµРЎР‚
        if (!squad.leader.isEmpty() && squad.members.contains(squad.leader)) sorted.add(squad.leader);

        // 2. Alpha (Р Р…Р Вµ Р Р† РЎвЂћР В°Р ВµРЎР‚РЎвЂљР С‘Р СР Вµ Р С‘ Р Р…Р Вµ Р РЋР вЂє)
        for (String m : squad.members) {
            if (!m.equals(squad.leader) && !squad.bravoMembers.contains(m) && !squad.charlieMembers.contains(m)) {
                sorted.add(m);
            }
        }

        // 3. Bravo FTL
        if (!squad.bravoLeader.isEmpty() && squad.members.contains(squad.bravoLeader)) sorted.add(squad.bravoLeader);
        // 4. Bravo Members
        for (String m : squad.bravoMembers) {
            if (!m.equals(squad.bravoLeader) && squad.members.contains(m)) sorted.add(m);
        }

        // 5. Charlie FTL
        if (!squad.charlieLeader.isEmpty() && squad.members.contains(squad.charlieLeader)) sorted.add(squad.charlieLeader);
        // 6. Charlie Members
        for (String m : squad.charlieMembers) {
            if (!m.equals(squad.charlieLeader) && squad.members.contains(m)) sorted.add(m);
        }

        return sorted;
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        boolean isDowned = this.minecraft.player != null && this.minecraft.player.getPersistentData().getBoolean("AAS_IsDowned");

        // 1. Р В¤Р С•Р Р… (Р ВµРЎРѓР В»Р С‘ Р Р† Р Р…Р С•Р С”Р Вµ - Р С”РЎР‚Р В°РЎРѓР Р…РЎвЂ№Р в„–, Р ВµРЎРѓР В»Р С‘ Р Р…Р ВµРЎвЂљ - Р С•Р В±РЎвЂ№РЎвЂЎР Р…РЎвЂ№Р в„–)
        if (isDowned) {
            // Р С›РЎвЂљРЎР‚Р С‘РЎРѓР С•Р Р†Р С”Р В° Р С”РЎР‚Р В°РЎРѓР Р…Р С•Р в„– Р Р†Р С‘Р Р…РЎРЉР ВµРЎвЂљР С”Р С‘/РЎвЂћР С•Р Р…Р В°
            RenderSystem.disableDepthTest();
            RenderSystem.enableBlend();
            gui.fill(0, 0, this.width, this.height, 0x50FF0000);
            RenderSystem.enableDepthTest();
        }

        // Р С›Р В±РЎвЂ№РЎвЂЎР Р…РЎвЂ№Р Вµ Р С—Р В°Р Р…Р ВµР В»Р С‘
        gui.fill(0, 0, SIDEBAR_WIDTH, this.height, 0xCC000000);
        gui.fill(SIDEBAR_WIDTH, 0, this.width, TOP_BAR_HEIGHT, 0xCC000000);
        gui.fill(SIDEBAR_WIDTH, TOP_BAR_HEIGHT, this.width, this.height, 0x95000000);

        renderSquadList(gui, mouseX, mouseY);
        mapRenderer.render(gui, mouseX, mouseY, partialTick);
        renderChatHistory(gui, mapY, mapSize);
        renderTopBar(gui);

        super.render(gui, mouseX, mouseY, partialTick);

        if (showContextMenu) {
            renderContextMenu(gui, mouseX, mouseY);
        }

        // Контекстное меню удаления метки (серый квадратик с красной иконкой удаления под меткой)
        mapRenderer.renderMarkerDeleteMenu(gui, mouseX, mouseY);

        // 2. Р СћР В°Р в„–Р СР ВµРЎР‚ (РЎР‚Р С‘РЎРѓРЎС“Р ВµР С Р СџР С›Р вЂ™Р вЂўР В Р Тђ Р Р†РЎРѓР ВµР С–Р С• Р Р† РЎРѓР В°Р СР С•Р С Р С”Р С•Р Р…РЎвЂ Р Вµ)
        if (isDowned) {
            renderDownedTimerHUD(gui);
        }
    }

    private void renderDownedTimerHUD(GuiGraphics gui) {
        int maxSeconds = com.example.aas.config.AASConfig.MAX_DOWNED_TIME_SECONDS.get();
        long startTime = ClientData.globalDeathTimestamp;
        if (startTime == 0) return;

        long elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000;
        long remaining = maxSeconds - elapsedSeconds;
        if (remaining < 0) remaining = 0;

        String text = "BLEEDING OUT: " + remaining + "s";
        int tw = this.font.width(text);
        int cx = this.width / 2;

        // Р РЋР Т‘Р Р†Р С‘Р Р…РЎС“Р В» Р Р…Р В° y=35, РЎвЂЎРЎвЂљР С•Р В±РЎвЂ№ Р В±РЎвЂ№Р В»Р С• Р СџР С›Р вЂќ Р С—Р С•Р В»Р С•РЎРѓР С”Р С•Р в„– РЎвЂљР С‘Р С”Р ВµРЎвЂљР С•Р Р† (TOP_BAR_HEIGHT = 30)
        int yPos = 35;

        gui.fill(cx - (tw/2) - 5, yPos, cx + (tw/2) + 5, yPos + 12, 0xAA000000);
        gui.renderOutline(cx - (tw/2) - 5, yPos, tw + 10, 12, 0xFFFF5555);
        gui.drawCenteredString(this.font, text, cx, yPos + 2, 0xFFFF5555);
    }
    @Override
    public void onClose() {
        if (this.minecraft.player != null && this.minecraft.player.getPersistentData().getBoolean("AAS_IsDowned")) {
            this.minecraft.setScreen(new DownedScreen());
        } else {
            super.onClose();
        }
    }
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mapRenderer.mouseScrolled(mouseX, mouseY, delta)) return true;
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    // PATH: src\main\java\com\example\aas\client\gui\SquadSelectionScreen.java

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // --- Меню удаления метки на карте (если оно открыто - забирает клик на себя) ---
        if (mapRenderer.isMarkerDeleteMenuOpen()) {
            if (mapRenderer.handleMarkerDeleteMenuClick(mouseX, mouseY, button)) return true;
        }

        // --- 0. Р вЂєР С›Р вЂњР ВР С™Р С’ Р вЂ™Р РЋР СџР вЂєР В«Р вЂ™Р С’Р В®Р В©Р вЂўР вЂњР С› Р С™Р С›Р СњР СћР вЂўР С™Р РЋР СћР СњР С›Р вЂњР С› Р СљР вЂўР СњР В® ---
        if (showContextMenu) {
            // FIX: РЎР‚Р В°Р Р…РЎРЉРЎв‚¬Р Вµ Р В»РЎР‹Р В±Р С•Р в„– Р С”Р В»Р С‘Р С” (Р вЂєР С™Р Сљ Р С‘Р В»Р С‘ Р СџР С™Р Сљ) Р В±Р ВµР В·РЎС“РЎРѓР В»Р С•Р Р†Р Р…Р С• Р С—Р С•Р С–Р В»Р С•РЎвЂ°Р В°Р В»РЎРѓРЎРЏ (return true),
            // Р Т‘Р В°Р В¶Р Вµ Р ВµРЎРѓР В»Р С‘ Р С•Р Р… Р С—РЎР‚Р С‘РЎв‚¬РЎвЂР В»РЎРѓРЎРЏ Р СР С‘Р СР С• Р СР ВµР Р…РЎР‹. Р ВР В·-Р В·Р В° РЎРЊРЎвЂљР С•Р С–Р С• Р СџР С™Р Сљ Р С—Р С• Р вЂќР В Р Р€Р вЂњР С›Р СљР Р€ Р С•РЎвЂљРЎР‚РЎРЏР Т‘РЎС“/Р С‘Р С–РЎР‚Р С•Р С”РЎС“,
            // Р С”Р С•Р С–Р Т‘Р В° Р СР ВµР Р…РЎР‹ РЎС“Р В¶Р Вµ Р В±РЎвЂ№Р В»Р С• Р С•РЎвЂљР С”РЎР‚РЎвЂ№РЎвЂљР С•, РЎвЂљР С•Р В»РЎРЉР С”Р С• Р В·Р В°Р С”РЎР‚РЎвЂ№Р Р†Р В°Р В» РЎРѓРЎвЂљР В°РЎР‚Р С•Р Вµ Р СР ВµР Р…РЎР‹ Р С‘ "РЎРѓРЎР‰Р ВµР Т‘Р В°Р В»" Р С”Р В»Р С‘Р С”,
            // Р Р…Р Вµ Р Т‘Р С•Р В»Р ВµРЎвЂљР В°РЎРЏ Р Т‘Р С• Р В±Р В»Р С•Р С”Р В° РІвЂћвЂ“3 Р Р…Р С‘Р В¶Р Вµ, Р С”Р С•РЎвЂљР С•РЎР‚РЎвЂ№Р в„– Р С•РЎвЂљР С”РЎР‚РЎвЂ№Р Р†Р В°Р ВµРЎвЂљ Р Р…Р С•Р Р†Р С•Р Вµ Р СР ВµР Р…РЎР‹. Р СџРЎР‚Р С‘РЎвЂ¦Р С•Р Т‘Р С‘Р В»Р С•РЎРѓРЎРЉ
            // Р С”Р В»Р С‘Р С”Р В°РЎвЂљРЎРЉ Р Т‘Р Р†Р В°Р В¶Р Т‘РЎвЂ№. Р СћР ВµР С—Р ВµРЎР‚РЎРЉ Р С—Р С•Р С–Р В»Р С•РЎвЂ°Р В°Р ВµР С Р С”Р В»Р С‘Р С” РЎвЂљР С•Р В»РЎРЉР С”Р С• Р ВµРЎРѓР В»Р С‘ РЎР‚Р ВµР В°Р В»РЎРЉР Р…Р С• Р Р…Р В°Р В¶Р В°Р В»Р С‘ Р С—РЎС“Р Р…Р С”РЎвЂљ Р СР ВµР Р…РЎР‹,
            // Р В»Р С‘Р В±Р С• РЎРЊРЎвЂљР С• Р В±РЎвЂ№Р В» Р вЂєР С™Р Сљ (РЎвЂЎРЎвЂљР С•Р В±РЎвЂ№ Р Р…Р Вµ РЎвЂљРЎР‚Р С‘Р С–Р С–Р ВµРЎР‚Р С‘РЎвЂљРЎРЉ Р Р†Р С‘Р Т‘Р В¶Р ВµРЎвЂљРЎвЂ№ Р С—Р С•Р Т‘ Р СР ВµР Р…РЎР‹). Р СџР С™Р Сљ Р СР С‘Р СР С• Р СР ВµР Р…РЎР‹
            // Р С—РЎР‚Р С•Р Р†Р В°Р В»Р С‘Р Р†Р В°Р ВµРЎвЂљРЎРѓРЎРЏ Р Р† РЎРЊРЎвЂљР С•РЎвЂљ Р В¶Р Вµ Р С•Р В±РЎР‚Р В°Р В±Р С•РЎвЂљРЎвЂЎР С‘Р С” РЎРѓР С•Р В±РЎвЂ№РЎвЂљР С‘РЎРЏ Р С‘ РЎРѓРЎР‚Р В°Р В·РЎС“ Р С•РЎвЂљР С”РЎР‚РЎвЂ№Р Р†Р В°Р ВµРЎвЂљ Р Р…РЎС“Р В¶Р Р…Р С•Р Вµ Р СР ВµР Р…РЎР‹.
            boolean menuOptionClicked = false;
            if (button == 0) { // Р вЂєР С™Р Сљ Р С—Р С• Р С•РЎвЂљР С”РЎР‚РЎвЂ№РЎвЂљР С•Р СРЎС“ Р СР ВµР Р…РЎР‹
                String myName = this.minecraft.player.getScoreboardName();
                AASWorldData.Squad s = null;
                for (AASWorldData.Squad sq : ClientData.clientSquads) {
                    if (sq.id == contextTargetSquadId) { s = sq; break; }
                }

                if (s != null) {
                    List<String> options = new ArrayList<>();

                    if (contextTargetIsSquad) {
                        options.add("Disband Squad");
                    } else {
                        boolean amISL = s.leader.equals(myName);
                        boolean amIBravoFTL = s.bravoLeader.equals(myName);
                        boolean amICharlieFTL = s.charlieLeader.equals(myName);

                        if (amISL) {
                            options.add("Promote to SL");
                            if (!s.bravoLeader.equals(contextTargetPlayer) && !s.charlieLeader.equals(contextTargetPlayer)) {
                                options.add("Set FTL Bravo");
                                options.add("Set FTL Charlie");
                            }
                            options.add("Add to Bravo");
                            options.add("Add to Charlie");
                            options.add("Remove from FT");
                            options.add("Kick from Squad");
                        } else if (amIBravoFTL) {
                            if (!s.leader.equals(contextTargetPlayer) && !s.charlieLeader.equals(contextTargetPlayer)) options.add("Pass FTL Bravo");
                            options.add("Add to Bravo");
                            options.add("Remove from FT");
                        } else if (amICharlieFTL) {
                            if (!s.leader.equals(contextTargetPlayer) && !s.bravoLeader.equals(contextTargetPlayer)) options.add("Pass FTL Charlie");
                            options.add("Add to Charlie");
                            options.add("Remove from FT");
                        }
                    }

                    int w = ClientData.squadMenuWidth(this.font, options);
                    int h = options.size() * 12 + 4;

                    // Р вЂўРЎРѓР В»Р С‘ Р С”Р В»Р С‘Р С”Р Р…РЎС“Р В»Р С‘ РЎР‚Р С•Р Р†Р Р…Р С• Р С—Р С• Р С—РЎС“Р Р…Р С”РЎвЂљРЎС“ Р СР ВµР Р…РЎР‹
                    if (mouseX >= contextMenuX && mouseX <= contextMenuX + w && mouseY >= contextMenuY && mouseY <= contextMenuY + h) {
                        int clickedIdx = (int) (mouseY - contextMenuY - 2) / 12;
                        if (clickedIdx >= 0 && clickedIdx < options.size()) {
                            String opt = options.get(clickedIdx);

                            if (contextTargetIsSquad) {
                                if (opt.equals("Disband Squad")) {
                                    PacketHandler.INSTANCE.sendToServer(new PacketSquadAction(11, s.id, ""));
                                }
                            } else {
                                // Р С›РЎвЂљР С—РЎР‚Р В°Р Р†Р В»РЎРЏР ВµР С Р С—Р В°Р С”Р ВµРЎвЂљ Р Р…Р В° РЎРѓР ВµРЎР‚Р Р†Р ВµРЎР‚ Р Р† Р В·Р В°Р Р†Р С‘РЎРѓР С‘Р СР С•РЎРѓРЎвЂљР С‘ Р С•РЎвЂљ Р Р†РЎвЂ№Р В±РЎР‚Р В°Р Р…Р Р…Р С•Р С–Р С• Р С—РЎС“Р Р…Р С”РЎвЂљР В°
                                if (opt.equals("Promote to SL")) PacketHandler.INSTANCE.sendToServer(new PacketSquadAction(4, s.id, contextTargetPlayer));
                                else if (opt.equals("Set FTL Bravo") || opt.equals("Pass FTL Bravo")) PacketHandler.INSTANCE.sendToServer(new PacketSquadAction(6, s.id, contextTargetPlayer));
                                else if (opt.equals("Set FTL Charlie") || opt.equals("Pass FTL Charlie")) PacketHandler.INSTANCE.sendToServer(new PacketSquadAction(7, s.id, contextTargetPlayer));
                                else if (opt.equals("Add to Bravo")) PacketHandler.INSTANCE.sendToServer(new PacketSquadAction(8, s.id, contextTargetPlayer));
                                else if (opt.equals("Add to Charlie")) PacketHandler.INSTANCE.sendToServer(new PacketSquadAction(9, s.id, contextTargetPlayer));
                                else if (opt.equals("Remove from FT")) PacketHandler.INSTANCE.sendToServer(new PacketSquadAction(10, s.id, contextTargetPlayer));
                                else if (opt.equals("Kick from Squad")) PacketHandler.INSTANCE.sendToServer(new PacketSquadAction(3, s.id, contextTargetPlayer));
                            }

                            playClickSound();
                            menuOptionClicked = true;
                        }
                    }
                }
            }

            showContextMenu = false;
            if (menuOptionClicked || button == 0) {
                return true;
            }
            // Р СџР С™Р Сљ Р СР С‘Р СР С• Р СР ВµР Р…РЎР‹: Р Р…Р Вµ Р Р†Р С•Р В·Р Р†РЎР‚Р В°РЎвЂ°Р В°Р ВµР С true Р В·Р Т‘Р ВµРЎРѓРЎРЉ РІР‚вЂќ Р С—РЎР‚Р С•Р Р†Р В°Р В»Р С‘Р Р†Р В°Р ВµР СРЎРѓРЎРЏ Р Р…Р С‘Р В¶Р Вµ Р Р† РЎРЊРЎвЂљР С•Р С Р В¶Р Вµ
            // Р Р†РЎвЂ№Р В·Р С•Р Р†Р Вµ mouseClicked(), РЎвЂЎРЎвЂљР С•Р В±РЎвЂ№ Р В±Р В»Р С•Р С” РІвЂћвЂ“3 РЎРѓРЎР‚Р В°Р В·РЎС“ Р С•РЎвЂљР С”РЎР‚РЎвЂ№Р В» Р Р…Р С•Р Р†Р С•Р Вµ Р СР ВµР Р…РЎР‹.
        }

        // --- 1. Р СџР В Р С›Р вЂ™Р вЂўР В Р Р‡Р вЂўР Сљ Р С™Р С’Р В Р СћР Р€ ---
        if (mapRenderer.isMouseOver(mouseX, mouseY)) {
            if (mapRenderer.mouseClicked(mouseX, mouseY, button)) {
                if (button == 1) handleMapRightClick(mouseX, mouseY);
                return true;
            }
        }

        // --- 2. Р С›Р вЂР В«Р В§Р СњР В«Р вЂў Р С™Р вЂєР ВР С™Р В (Р СћР ВµР С”РЎРѓРЎвЂљР С•Р Р†РЎвЂ№Р Вµ Р С—Р С•Р В»РЎРЏ, Р С™Р Р…Р С•Р С—Р С”Р В° Create Squad Р С‘ РЎвЂљ.Р Т‘.) ---
        if (super.mouseClicked(mouseX, mouseY, button)) return true;


        // --- 3. Р СџР В Р С’Р вЂ™Р В«Р в„ў Р С™Р вЂєР ВР С™ Р СџР С› Р РЋР СџР ВР РЋР С™Р Р€ (Р вЂ™Р В«Р вЂ”Р С›Р вЂ™ Р С™Р С›Р СњР СћР вЂўР С™Р РЋР СћР СњР С›Р вЂњР С› Р СљР вЂўР СњР В®) ---
        if (button == 1 && mouseX < SIDEBAR_WIDTH && mouseY < (this.height - 60)) {
            String myName = this.minecraft.player.getScoreboardName();
            String myTeam = getPlayerTeam().toUpperCase();
            String myDim = this.minecraft.level.dimension().location().toString();

            // --- Р ВР РЋР СџР В Р С’Р вЂ™Р вЂєР вЂўР СњР ВР вЂў Р СћР Р€Р Сћ ---
            boolean isBlue = myTeam.contains("BLUE");
            int teamCMDId = isBlue ? ClientData.blueCMDId : ClientData.redCMDId;
            boolean myTeamVoteActive = isBlue ? ClientData.blueCmdVoteActive : ClientData.redCmdVoteActive;

            boolean amISL_Anywhere = false;
            for (AASWorldData.Squad s : ClientData.clientSquads) {
                if (s.leader.equals(myName)) { amISL_Anywhere = true; break; }
            }

            // Р ВРЎРѓР С—Р С•Р В»РЎРЉР В·РЎС“Р ВµР С myTeamVoteActive Р Р†Р СР ВµРЎРѓРЎвЂљР С• cmdVoteActive
            int currentY = (amISL_Anywhere && teamCMDId == -1 && !myTeamVoteActive) ? 35 : 10;
            // ------------------------------------------

            // Р СџРЎР‚Р С•Р Р†Р ВµРЎР‚Р С”Р В°: РЎРЏ Р С™Р С•Р СР В°Р Р…Р Т‘Р С‘РЎР‚ РЎРѓР Р†Р С•Р ВµР в„– Р С”Р С•Р СР В°Р Р…Р Т‘РЎвЂ№?
            // Р вЂ™Р С’Р вЂ“Р СњР С›: РЎРѓРЎвЂЎР С‘РЎвЂљР В°Р ВµР С РЎвЂЎР ВµРЎР‚Р ВµР В· ClientData.clientSquads (РЎРѓР С‘Р Р…РЎвЂ¦РЎР‚Р С•Р Р…Р С‘Р В·Р С‘РЎР‚РЎС“Р ВµРЎвЂљРЎРѓРЎРЏ PacketSyncSquads),
            // Р В° Р СњР вЂў РЎвЂЎР ВµРЎР‚Р ВµР В· player.getPersistentData() РІР‚вЂќ РЎРЊРЎвЂљР С‘ РЎвЂљР ВµР С–Р С‘ Р Р†РЎвЂ№РЎРѓРЎвЂљР В°Р Р†Р В»РЎРЏРЎР‹РЎвЂљРЎРѓРЎРЏ РЎвЂљР С•Р В»РЎРЉР С”Р С• Р Р…Р В° РЎРѓР ВµРЎР‚Р Р†Р ВµРЎР‚Р Вµ
            // Р С‘ Р Р…Р Вµ РЎР‚Р В°РЎРѓРЎРѓРЎвЂ№Р В»Р В°РЎР‹РЎвЂљРЎРѓРЎРЏ Р С”Р В»Р С‘Р ВµР Р…РЎвЂљРЎС“ Р С—Р С• РЎРѓР ВµРЎвЂљР С‘, Р С—Р С•РЎРЊРЎвЂљР С•Р СРЎС“ Р Р…Р В° РЎР‚Р ВµР В°Р В»РЎРЉР Р…Р С•Р С dedicated-РЎРѓР ВµРЎР‚Р Р†Р ВµРЎР‚Р Вµ
            // (Р Р…Р Вµ singleplayer) Р С—РЎР‚Р С•Р Р†Р ВµРЎР‚Р С”Р В° Р Р†РЎРѓР ВµР С–Р Т‘Р В° Р В±РЎвЂ№Р В»Р В° false Р С‘ Р СР ВµР Р…РЎР‹ Р С”Р С•Р СР В°Р Р…Р Т‘Р С‘РЎР‚Р В° Р Р…Р Вµ Р С—Р С•РЎРЏР Р†Р В»РЎРЏР В»Р С•РЎРѓРЎРЉ.
            AASWorldData.Squad myOwnSquadForCmdCheck = getMySquad(myName);
            boolean amICommander = (isPlayerInSquad() && myOwnSquadForCmdCheck != null
                    && myOwnSquadForCmdCheck.id == teamCMDId
                    && myOwnSquadForCmdCheck.leader.equals(myName));

            List<AASWorldData.Squad> myTeamSquads = ClientData.clientSquads.stream()
                    .filter(s -> s.team.equalsIgnoreCase(myTeam))
                    .filter(s -> s.dimension != null && s.dimension.equals(myDim))
                    .collect(Collectors.toList());

            // Р РЋР С•РЎР‚РЎвЂљР С‘РЎР‚Р С•Р Р†Р С”Р В° (РЎвЂЎРЎвЂљР С•Р В±РЎвЂ№ РЎРѓР С•Р Р†Р С—Р В°Р Т‘Р В°Р В»Р С• РЎРѓ Р С•РЎвЂљРЎР‚Р С‘РЎРѓР С•Р Р†Р С”Р С•Р в„–)
            myTeamSquads.sort((s1, s2) -> {
                if (s1.id == teamCMDId && teamCMDId != -1) return -1;
                if (s2.id == teamCMDId && teamCMDId != -1) return 1;
                return Integer.compare(s1.id, s2.id);
            });

            for (AASWorldData.Squad squad : myTeamSquads) {
                // 1. Р СџР В Р С›Р вЂ™Р вЂўР В Р С™Р С’ Р С™Р вЂєР ВР С™Р С’ Р СџР С› Р РЃР С’Р СџР С™Р вЂў Р С›Р СћР В Р Р‡Р вЂќР С’ (Р С™Р С•Р СР В°Р Р…Р Т‘Р С‘РЎР‚РЎРѓР С”Р В°РЎРЏ РЎвЂћРЎС“Р Р…Р С”РЎвЂ Р С‘РЎРЏ)
                if (mouseY >= currentY && mouseY <= currentY + 11) {
                    if (amICommander) {
                        showContextMenu = true;
                        contextTargetIsSquad = true; // Р В¦Р ВµР В»РЎРЉ - Р С•РЎвЂљРЎР‚РЎРЏР Т‘
                        contextTargetSquadId = squad.id;
                        contextMenuX = (int) mouseX;
                        contextMenuY = (int) mouseY;
                        playClickSound();
                        return true;
                    }
                }

                currentY += squad.leader.isEmpty() ? 12 : 21; // РџСЂРѕРїСѓСЃРєР°РµРј Р·Р°РіРѕР»РѕРІРѕРє РѕС‚СЂСЏРґР° (+ СЃС‚СЂРѕРєСѓ Р»РёРґРµСЂР°)

                if (expandedSquads.contains(squad.id)) {
                    if (squad.members.contains(myName)) {
                        List<String> sortedMembers = getSortedMembers(squad);
                        for (String member : sortedMembers) {
                            // Р ТђР С‘РЎвЂљР В±Р С•Р С”РЎРѓ РЎРѓРЎвЂљРЎР‚Р С•Р С”Р С‘ Р С‘Р С–РЎР‚Р С•Р С”Р В°: Р Р†РЎвЂ№РЎРѓР С•РЎвЂљР В° РЎРѓРЎвЂљРЎР‚Р С•Р С”Р С‘ 12, Р С‘РЎРѓР С—Р С•Р В»РЎРЉР В·РЎС“Р ВµР С +11
                            // Р Т‘Р В»РЎРЏ РЎРѓР С•Р С–Р В»Р В°РЎРѓР С•Р Р†Р В°Р Р…Р Р…Р С•РЎРѓРЎвЂљР С‘ РЎРѓ РЎР‚Р ВµР Р…Р Т‘Р ВµРЎР‚Р С•Р С Р С‘ РЎРѓ AASDeathScreen
                            if (mouseY >= currentY && mouseY <= currentY + 11 && mouseX >= 30) {
                                if (!member.equals(myName)) {
                                    boolean amISL = squad.leader.equals(myName);
                                    boolean amIBravo = squad.bravoLeader.equals(myName);
                                    boolean amICharlie = squad.charlieLeader.equals(myName);

                                    // Р СџРЎР‚Р С•Р Р†Р ВµРЎР‚Р С”Р В° Р С—РЎР‚Р В°Р Р†: Р СћР С•Р В»РЎРЉР С”Р С• Р вЂєР С‘Р Т‘Р ВµРЎР‚ Р С‘Р В»Р С‘ Р С™Р С•Р СР В°Р Р…Р Т‘Р С‘РЎР‚РЎвЂ№ Р В¤Р Сћ Р СР С•Р С–РЎС“РЎвЂљ Р С•РЎвЂљР С”РЎР‚РЎвЂ№Р Р†Р В°РЎвЂљРЎРЉ Р СР ВµР Р…РЎР‹
                                    if (amISL || amIBravo || amICharlie) {
                                        showContextMenu = true;
                                        contextTargetIsSquad = false; // Р В¦Р ВµР В»РЎРЉ - Р С‘Р С–РЎР‚Р С•Р С”
                                        contextMenuX = (int) mouseX;
                                        contextMenuY = (int) mouseY;
                                        contextTargetPlayer = member;
                                        contextTargetSquadId = squad.id;
                                        playClickSound();
                                        return true; // Р С™Р В»Р С‘Р С” Р С•Р В±РЎР‚Р В°Р В±Р С•РЎвЂљР В°Р Р…
                                    }
                                }
                            }
                            currentY += 12;
                        }
                    } else {
                        currentY += squad.members.size() * 12; // Р СџРЎР‚Р С•Р С—РЎС“РЎРѓР С”Р В°Р ВµР С РЎвЂЎРЎС“Р В¶Р С•Р в„– РЎРѓР С”Р Р†Р В°Р Т‘
                    }
                    currentY += 4;
                }
                currentY += 4;
            }
        }

        // --- 4. Р вЂєР вЂўР вЂ™Р В«Р в„ў Р С™Р вЂєР ВР С™ Р СџР С› Р РЋР СџР ВР РЋР С™Р Р€ (Р В Р С’Р вЂ”Р вЂ™Р С›Р В Р С’Р В§Р ВР вЂ™Р С’Р СњР ВР вЂў/Р вЂ™Р В«Р вЂР С›Р В  Р С™Р ВР СћР С’ Р В Р Сћ.Р вЂќ.) ---
        if (button == 0 && mouseX < SIDEBAR_WIDTH && mouseY < (this.height - 60)) {
            // Р вЂ™РЎвЂ№Р В·Р С•Р Р† Р Р†Р В°РЎв‚¬Р ВµР С–Р С• РЎРѓРЎвЂљР В°РЎР‚Р С•Р С–Р С• Р СР ВµРЎвЂљР С•Р Т‘Р В° (Р С”Р С•РЎвЂљР С•РЎР‚РЎвЂ№Р в„– Р СРЎвЂ№ Р С•Р В±Р Р…Р С•Р Р†Р С‘Р В»Р С‘ РЎР‚Р В°Р Р…Р ВµР Вµ, РЎС“Р Т‘Р В°Р В»Р С‘Р Р† Р С•РЎвЂљРЎвЂљРЎС“Р Т‘Р В° Р С”Р Р…Р С•Р С—Р С”Р С‘ P/K)
            handleSquadListClicks(mouseX, mouseY);
            return true;
        }

        return false;
    }
    private void handleSquadListClicks(double mouseX, double mouseY) {
        String myName = this.minecraft.player.getScoreboardName();
        String myTeam = getPlayerTeam().toUpperCase();
        String myDim = this.minecraft.level.dimension().location().toString();
        boolean amIInSquad = isPlayerInSquad();

        boolean isBlue = myTeam.contains("BLUE");
        int teamCMDId = isBlue ? ClientData.blueCMDId : ClientData.redCMDId;
        boolean myTeamVoteActive = isBlue ? ClientData.blueCmdVoteActive : ClientData.redCmdVoteActive;

        boolean amISquadLeaderAnywhere = false;
        for (AASWorldData.Squad s : ClientData.clientSquads) {
            if (s.leader.equals(myName)) { amISquadLeaderAnywhere = true; break; }
        }

        int currentY = (amISquadLeaderAnywhere && teamCMDId == -1 && !myTeamVoteActive) ? 35 : 10;

        List<AASWorldData.Squad> myTeamSquads = ClientData.clientSquads.stream()
                .filter(s -> s.team.equalsIgnoreCase(myTeam))
                .filter(s -> s.dimension != null && s.dimension.equals(myDim))
                .collect(Collectors.toList());

        myTeamSquads.sort((s1, s2) -> {
            if (s1.id == teamCMDId && teamCMDId != -1) return -1;
            if (s2.id == teamCMDId && teamCMDId != -1) return 1;
            return Integer.compare(s1.id, s2.id);
        });

        for (AASWorldData.Squad squad : myTeamSquads) {
            boolean isMySquad = squad.members.contains(myName);
            boolean amILeader = squad.leader.equals(myName);
            boolean isExpanded = expandedSquads.contains(squad.id);

            // 1. Р В Р В°РЎРѓРЎРѓРЎвЂЎР С‘РЎвЂљРЎвЂ№Р Р†Р В°Р ВµР С РЎв‚¬Р С‘РЎР‚Р С‘Р Р…РЎС“ РЎвЂљР ВµР С”РЎРѓРЎвЂљР В° Р Т‘Р ВµР в„–РЎРѓРЎвЂљР Р†Р С‘РЎРЏ (JOIN/LEAVE), Р С”Р В°Р С” Р Р† РЎР‚Р ВµР Р…Р Т‘Р ВµРЎР‚Р Вµ
            String actionText = "";
            if (isMySquad) actionText = "LEAVE";
            else if (!amIInSquad) {
                if (squad.isLocked) actionText = "LOCKED";
                else if (squad.members.size() >= 9) actionText = "FULL";
                else actionText = "JOIN";
            }

            int actionWidth = actionText.isEmpty() ? 0 : this.font.width(actionText);
            int actionX = actionWidth > 0 ? (SIDEBAR_WIDTH - actionWidth - 10) : 0;

            // 2. Р В Р В°РЎРѓРЎРѓРЎвЂЎР С‘РЎвЂљРЎвЂ№Р Р†Р В°Р ВµР С РЎвЂљР С•РЎвЂЎР Р…РЎвЂ№Р Вµ Р С”Р С•Р С•РЎР‚Р Т‘Р С‘Р Р…Р В°РЎвЂљРЎвЂ№ РЎРѓРЎвЂљРЎР‚Р ВµР В»Р С•РЎвЂЎР С”Р С‘ Р С‘ Р В·Р В°Р СР С”Р В° (Р Р† РЎвЂљР С•РЎвЂЎР Р…Р С•РЎРѓРЎвЂљР С‘ Р С”Р В°Р С” Р Р† renderSquadList)
            int arrowX = (actionX > 0 ? actionX : SIDEBAR_WIDTH - 10) - 12;
            int lockX = arrowX - 12;

            // --- Р С›Р вЂР В Р С’Р вЂР С›Р СћР С™Р С’ Р С™Р вЂєР ВР С™Р С›Р вЂ™ ---

            // Р С™Р В»Р С‘Р С” Р С—Р С• РЎвЂљР ВµР С”РЎРѓРЎвЂљРЎС“ (JOIN/LEAVE)
            if (actionWidth > 0 && mouseX >= actionX && mouseX <= actionX + actionWidth && mouseY >= currentY && mouseY <= currentY + 9) {
                if (isMySquad) {
                    PacketHandler.INSTANCE.sendToServer(new PacketSquadAction(2, squad.id, ""));
                    this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(com.example.aas.sound.ModSounds.SQUAD_LEAVE.get(), 1.0F));
                } else if (!squad.isLocked && squad.members.size() < 9) {
                    PacketHandler.INSTANCE.sendToServer(new PacketSquadAction(1, squad.id, ""));
                    this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(com.example.aas.sound.ModSounds.SQUAD_JOIN.get(), 1.0F));
                }
                playClickSound();
                return;
            }

            // Р С™Р В»Р С‘Р С” Р С—Р С• РЎРѓРЎвЂљРЎР‚Р ВµР В»Р С•РЎвЂЎР С”Р Вµ (РЎР‚Р В°Р В·Р Р†Р ВµРЎР‚Р Р…РЎС“РЎвЂљРЎРЉ/РЎРѓР Р†Р ВµРЎР‚Р Р…РЎС“РЎвЂљРЎРЉ)
            if (mouseX >= arrowX && mouseX <= arrowX + 10 && mouseY >= currentY && mouseY <= currentY + 10) {
                if (isExpanded) expandedSquads.remove(squad.id);
                else expandedSquads.add(squad.id);
                playClickSound();
                return;
            }

            if (amILeader && mouseX >= lockX && mouseX <= lockX + 10 && mouseY >= currentY && mouseY <= currentY + 10) {
                PacketHandler.INSTANCE.sendToServer(new PacketSquadAction(5, squad.id, ""));
                playClickSound();
                return;
            }

            // Р›РљРњ РїРѕ РЅРёРєСѓ Р»РёРґРµСЂР° РїРѕРґ РЅР°Р·РІР°РЅРёРµРј РѕС‚СЂСЏРґР°: РІС‹РґРµР»РёС‚СЊ/СЃРЅСЏС‚СЊ РІС‹РґРµР»РµРЅРёРµ РЅР° РєР°СЂС‚Рµ
            if (!squad.leader.isEmpty()
                    && mouseX >= 24 && mouseX <= 24 + this.font.width(squad.leader)
                    && mouseY >= currentY + 9 && mouseY < currentY + 18) {
                ClientData.toggleHighlight(squad.leader);
                playClickSound();
                return;
            }

            // РџСЂРѕРїСѓСЃРєР°РµРј Р·Р°РіРѕР»РѕРІРѕРє (+ СЃС‚СЂРѕРєСѓ СЃ РёРјРµРЅРµРј Р»РёРґРµСЂР°, РµСЃР»Рё РѕРЅР° РµСЃС‚СЊ)
            currentY += squad.leader.isEmpty() ? 12 : 21;

            // Р С™Р В»Р С‘Р С”Р С‘ Р С—Р С• Р С‘Р С–РЎР‚Р С•Р С”Р В°Р С Р Р†Р Р…РЎС“РЎвЂљРЎР‚Р С‘ (Р С”Р Р…Р С•Р С—Р С”Р В° Р С™)
            if (isExpanded) {
                List<String> sortedMembers = getSortedMembers(squad);
                for (String member : sortedMembers) {
                    if (isMySquad && member.equals(myName)) {
                        // Р С™Р С•Р С•РЎР‚Р Т‘Р С‘Р Р…Р В°РЎвЂљР В° Р С”Р Р…Р С•Р С—Р С”Р С‘ Р С™ (x=30)
                        if (mouseX >= 30 && mouseX <= 40 && mouseY >= currentY && mouseY <= currentY + 10) {
                            PacketHandler.INSTANCE.sendToServer(new PacketRequestKitMenu());
                            playClickSound();
                            return;
                        }
                    }

                    // Р›РљРњ РїРѕ РЅРёРєСѓ РёРіСЂРѕРєР°: РІС‹РґРµР»РёС‚СЊ/СЃРЅСЏС‚СЊ РІС‹РґРµР»РµРЅРёРµ РЅР° РєР°СЂС‚Рµ
                    if (isOverMemberNick(member, member.equals(myName), mouseX)
                            && mouseY >= currentY && mouseY < currentY + 12) {
                        ClientData.toggleHighlight(member);
                        playClickSound();
                        return;
                    }
                    currentY += 12;
                }
                currentY += 4;
            }
            currentY += 4;
        }
    }

    // Р ВРЎвЂ°Р ВµРЎвЂљ Р С•РЎвЂљРЎР‚РЎРЏР Т‘, Р Р† Р С”Р С•РЎвЂљР С•РЎР‚Р С•Р С РЎРѓР С•РЎРѓРЎвЂљР С•Р С‘РЎвЂљ РЎС“Р С”Р В°Р В·Р В°Р Р…Р Р…РЎвЂ№Р в„– Р С‘Р С–РЎР‚Р С•Р С”, РЎРѓРЎР‚Р ВµР Т‘Р С‘ Р С”Р В»Р С‘Р ВµР Р…РЎвЂљРЎРѓР С”Р С‘РЎвЂ¦ Р Т‘Р В°Р Р…Р Р…РЎвЂ№РЎвЂ¦
    private AASWorldData.Squad getMySquad(String playerName) {
        for (AASWorldData.Squad s : ClientData.clientSquads) {
            if (s.members.contains(playerName)) return s;
        }
        return null;
    }

    private List<AASWorldData.Squad> getSortedSquads() {
        String myTeam = getPlayerTeam();
        String myDim = this.minecraft.level.dimension().location().toString();
        int teamCMDId = myTeam.equalsIgnoreCase("Blue") ? ClientData.blueCMDId : ClientData.redCMDId;

        List<AASWorldData.Squad> list = ClientData.clientSquads.stream()
                .filter(s -> s.team.equalsIgnoreCase(myTeam))
                .filter(s -> s.dimension != null && s.dimension.equals(myDim))
                .collect(Collectors.toList());

        list.sort((s1, s2) -> {
            if (s1.id == teamCMDId && teamCMDId != -1) return -1;
            if (s2.id == teamCMDId && teamCMDId != -1) return 1;
            return Integer.compare(s1.id, s2.id);
        });
        return list;
    }

    private void handleMapRightClick(double mouseX, double mouseY) {
        // Р ВР вЂ”Р СљР вЂўР СњР вЂўР СњР С›: Р ВРЎРѓР С—Р С•Р В»РЎРЉР В·РЎС“Р ВµР С Р Р…Р С•Р Р†РЎвЂ№Р в„– Р СР ВµРЎвЂљР С•Р Т‘ Р С—РЎР‚Р С•Р Р†Р ВµРЎР‚Р С”Р С‘
        if (!isSquadLeaderOrFTL(this.minecraft.player)) {
            this.minecraft.player.displayClientMessage(Component.literal("Only SL and FTLs can place markers!").withStyle(ChatFormatting.RED), true);
            return;
        }

        // Если под курсором есть метка, которую игрок вправе удалить - открываем
        // маленькое меню удаления вместо радиального меню размещения новой метки.
        if (mapRenderer.tryOpenMarkerDeleteMenu(mouseX, mouseY)) {
            return;
        }

        // 2. Р СџР С•Р В»РЎС“РЎвЂЎР В°Р ВµР С Р СР В°РЎРѓРЎв‚¬РЎвЂљР В°Р В± Р С‘ РЎвЂ Р ВµР Р…РЎвЂљРЎР‚ Р С‘Р В· РЎР‚Р ВµР Р…Р Т‘Р ВµРЎР‚Р ВµРЎР‚Р В°
        double bpp = mapRenderer.getBlocksPerPixel();
        double centerX = mapRenderer.getCenterX(this.minecraft.player);
        double centerZ = mapRenderer.getCenterZ(this.minecraft.player);

        // 3. Р В Р В°РЎРѓРЎРѓРЎвЂЎР С‘РЎвЂљРЎвЂ№Р Р†Р В°Р ВµР С Р СР С‘РЎР‚Р С•Р Р†РЎвЂ№Р Вµ Р С”Р С•Р С•РЎР‚Р Т‘Р С‘Р Р…Р В°РЎвЂљРЎвЂ№ РЎвЂљР С•РЎвЂЎР С”Р С‘ Р С”Р В»Р С‘Р С”Р В°
        // Р ВРЎРѓР С—Р С•Р В»РЎРЉР В·РЎС“Р ВµР С Р С—Р С•Р В»РЎРЏ mapX, mapY, mapSize, Р С”Р С•РЎвЂљР С•РЎР‚РЎвЂ№Р Вµ Р С‘Р Р…Р С‘РЎвЂ Р С‘Р В°Р В»Р С‘Р В·Р С‘РЎР‚Р С•Р Р†Р В°Р Р…РЎвЂ№ Р Р† Р СР ВµРЎвЂљР С•Р Т‘Р Вµ init() РЎРЊР С”РЎР‚Р В°Р Р…Р В°
        int targetX = (int) (centerX + ((mouseX - (this.mapX + this.mapSize / 2.0)) * bpp));
        int targetZ = (int) (centerZ + ((mouseY - (this.mapY + this.mapSize / 2.0)) * bpp));

        // === Р вЂ™Р С›Р Сћ Р СћР Р€Р Сћ Р вЂњР вЂєР С’Р вЂ™Р СњР С›Р вЂў Р ВР вЂ”Р СљР вЂўР СњР вЂўР СњР ВР вЂў ===
        // Р вЂ™Р СР ВµРЎРѓРЎвЂљР С• SquadMarkerRadialScreen Р С•РЎвЂљР С”РЎР‚РЎвЂ№Р Р†Р В°Р ВµР С Р Р…Р В°РЎв‚¬Р Вµ Р Р…Р С•Р Р†Р С•Р Вµ Р вЂњР вЂєР С’Р вЂ™Р СњР С›Р вЂў РЎвЂљР В°Р С”РЎвЂљР С‘РЎвЂЎР ВµРЎРѓР С”Р С•Р Вµ Р СР ВµР Р…РЎР‹
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

    private boolean isSquadLeaderOrFTL(Player player) {
        String pName = player.getScoreboardName();
        for (AASWorldData.Squad s : ClientData.clientSquads) {
            if (s.leader.equals(pName) || s.bravoLeader.equals(pName) || s.charlieLeader.equals(pName)) {
                return true;
            }
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


    // Р В Р С‘РЎРѓРЎС“Р ВµРЎвЂљ Р С”РЎР‚РЎС“Р С–Р В»РЎвЂ№Р в„– Р В·Р Р…Р В°РЎвЂЎР С•Р С” (Р С—Р ВµРЎР‚Р ВµР С”РЎР‚Р В°РЎв‚¬Р ВµР Р…Р Р…РЎС“РЎР‹ РЎвЂљР ВµР С”РЎРѓРЎвЂљРЎС“РЎР‚РЎС“ player_circle.png) РЎРѓ РЎвЂЎР С‘РЎРѓР В»Р С•Р С Р С—Р С• РЎвЂ Р ВµР Р…РЎвЂљРЎР‚РЎС“.
    // Р ВРЎРѓР С—Р С•Р В»РЎРЉР В·РЎС“Р ВµРЎвЂљРЎРѓРЎРЏ Р Т‘Р В»РЎРЏ Р Р…Р С•Р СР ВµРЎР‚Р В° Р С•РЎвЂљРЎР‚РЎРЏР Т‘Р В° Р Р† Р Т‘РЎС“РЎвЂ¦Р Вµ РЎР‚Р ВµРЎвЂћР ВµРЎР‚Р ВµР Р…РЎРѓР В° (РЎвЂ Р Р†Р ВµРЎвЂљР Р…Р С•Р в„– Р С”РЎР‚РЎС“Р В¶Р С•Р С” РЎРѓР В»Р ВµР Р†Р В° Р С•РЎвЂљ Р Р…Р В°Р В·Р Р†Р В°Р Р…Р С‘РЎРЏ).
    private void drawBadgeCircle(GuiGraphics gui, int x, int y, int size, int colorARGB, String label) {
        float a = ((colorARGB >> 24) & 0xFF) / 255f;
        float r = ((colorARGB >> 16) & 0xFF) / 255f;
        float g = ((colorARGB >> 8) & 0xFF) / 255f;
        float b = (colorARGB & 0xFF) / 255f;

        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(r, g, b, a);
        gui.blit(CIRCLE_BADGE, x, y, 0, 0, size, size, size, size);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

        if (label != null && !label.isEmpty()) {
            gui.drawCenteredString(this.font, label, x + size / 2, y + (size - 8) / 2, 0xFFFFFFFF);
        }
    }

    private void renderSquadList(GuiGraphics gui, int mouseX, int mouseY) {
        String myName = this.minecraft.player.getScoreboardName();
        String myTeam = getPlayerTeam().toUpperCase();
        String myDim = this.minecraft.level.dimension().location().toString();
        boolean amIInSquad = isPlayerInSquad();

        // --- Р ВР РЋР СџР В Р С’Р вЂ™Р вЂєР вЂўР СњР ВР вЂў Р СћР Р€Р Сћ ---
        boolean isBlue = myTeam.contains("BLUE");
        int teamCMDId = isBlue ? ClientData.blueCMDId : ClientData.redCMDId;
        boolean myTeamVoteActive = isBlue ? ClientData.blueCmdVoteActive : ClientData.redCmdVoteActive;

        boolean amISquadLeaderAnywhere = false;
        for (AASWorldData.Squad s : ClientData.clientSquads) {
            if (s.leader.equals(myName)) { amISquadLeaderAnywhere = true; break; }
        }

        // Р С™Р Р…Р С•Р С—Р С”Р В° Р Р†Р С‘Р Т‘Р Р…Р В° РЎвЂљР С•Р В»РЎРЉР С”Р С• Р ВµРЎРѓР В»Р С‘ Р СРЎвЂ№ Р В»Р С‘Р Т‘Р ВµРЎР‚, CMD Р Р…Р ВµРЎвЂљ Р С‘ Р Р…Р В°РЎв‚¬Р Вµ Р С–Р С•Р В»Р С•РЎРѓР С•Р Р†Р В°Р Р…Р С‘Р Вµ Р Р…Р Вµ Р В°Р С”РЎвЂљР С‘Р Р†Р Р…Р С•
        int currentY = (amISquadLeaderAnywhere && teamCMDId == -1 && !myTeamVoteActive) ? 35 : 10;

        List<AASWorldData.Squad> myTeamSquads = ClientData.clientSquads.stream()
                .filter(s -> s.team.equalsIgnoreCase(myTeam))
                .filter(s -> s.dimension != null && s.dimension.equals(myDim))
                .collect(Collectors.toList());

        // 5. Р РЋР С•РЎР‚РЎвЂљР С‘РЎР‚Р С•Р Р†Р С”Р В° (CMD Р Р†РЎРѓР ВµР С–Р Т‘Р В° Р С—Р ВµРЎР‚Р Р†РЎвЂ№Р в„–)
        myTeamSquads.sort((s1, s2) -> {
            if (s1.id == teamCMDId && teamCMDId != -1) return -1;
            if (s2.id == teamCMDId && teamCMDId != -1) return 1;
            return Integer.compare(s1.id, s2.id);
        });

        int index = 1;
        for (AASWorldData.Squad squad : myTeamSquads) {
            boolean isMySquad = squad.members.contains(myName);
            boolean amILeader = squad.leader.equals(myName);
            boolean isExpanded = expandedSquads.contains(squad.id);

            // Р вЂєР С›Р вЂњР ВР С™Р С’ CMD
            boolean isCMD = (squad.id == teamCMDId && teamCMDId != -1);
            String prefix = isCMD ? "[CMD] " : "";
            int squadNameColor = isCMD ? 0xFF55FF55 : 0xFFFFD700;

            // Р В¦Р Р†Р ВµРЎвЂљР Р…Р С•Р в„– Р С”РЎР‚РЎС“Р В¶Р С•Р С” РЎРѓ Р Р…Р С•Р СР ВµРЎР‚Р С•Р С Р С•РЎвЂљРЎР‚РЎРЏР Т‘Р В°:
            // - Р В¦Р СљР вЂќ РІР‚вЂќ РЎРѓР Р†Р С•Р в„– РЎвЂ Р Р†Р ВµРЎвЂљ (Р С”Р В°Р С” Р В±РЎвЂ№Р В»Р С•, Р В·Р ВµР В»РЎвЂР Р…РЎвЂ№Р в„–)
            // - РЎвЂљР Р†Р С•Р в„– Р С•РЎвЂљРЎР‚РЎРЏР Т‘ (Р Р…Р Вµ Р В¦Р СљР вЂќ) РІР‚вЂќ rgba(37, 150, 190)
            // - Р С•РЎРѓРЎвЂљР В°Р В»РЎРЉР Р…РЎвЂ№Р Вµ (РЎвЂЎРЎС“Р В¶Р С‘Р Вµ) Р С•РЎвЂљРЎР‚РЎРЏР Т‘РЎвЂ№ РІР‚вЂќ РЎРѓР С‘Р Р…Р С‘Р в„– 0xFF3399FF, Р С”Р В°Р С” РЎР‚Р В°Р Р…РЎРЉРЎв‚¬Р Вµ
            int badgeColor;
            if (isCMD) badgeColor = 0xFF4CD964;
            else if (isMySquad) badgeColor = 0xFF00FF00;
            else badgeColor = 0xFF3399FF;
            drawBadgeCircle(gui, 4, currentY - 3, 14, badgeColor, String.valueOf(index));

            String squadNameOnly = prefix + squad.name;
            gui.drawString(this.font, squadNameOnly, 24, currentY, squadNameColor, false);
            int nameCursorX = 24 + this.font.width(squadNameOnly) + 5;

            String countText = squad.members.size() + "/9";
            gui.drawString(this.font, countText, nameCursorX, currentY, 0xFFAAAAAA, false);

            // РРјСЏ Р»РёРґРµСЂР° РѕС‚СЂСЏРґР° вЂ” С‚СѓСЃРєР»С‹Рј С†РІРµС‚РѕРј, Р’РўРћР РћР™ СЃС‚СЂРѕРєРѕР№ РїРѕРґ РЅР°Р·РІР°РЅРёРµРј РѕС‚СЂСЏРґР°
            boolean hasLeaderLine = !squad.leader.isEmpty();
            if (hasLeaderLine) {
                gui.drawString(this.font, squad.leader, 24, currentY + 9, ClientData.isHighlighted(squad.leader) ? 0xFFFFFFFF : 0xFF999999, false);
            }

            String actionText = "";
            int actionColor = 0xFFFFFFFF;
            boolean clickable = true;

            if (isMySquad) {
                actionText = "LEAVE"; actionColor = 0xFFFF5555;
            } else if (!amIInSquad) { // Р СћР ВµР С—Р ВµРЎР‚РЎРЉ amIInSquad Р Р†Р С‘Р Т‘Р ВµР Р…!
                if (squad.isLocked) { actionText = "LOCKED"; actionColor = 0xFFFFAA00; clickable = false; }
                else if (squad.members.size() >= 9) { actionText = "FULL"; actionColor = 0xFF888888; clickable = false; }
                else { actionText = "JOIN"; actionColor = 0xFF55FF55; }
            }

            int actionX = 0;
            if (!actionText.isEmpty()) {
                int actionWidth = this.font.width(actionText);
                actionX = SIDEBAR_WIDTH - actionWidth - 10;
                boolean hover = mouseX >= actionX && mouseX <= actionX + actionWidth && mouseY >= currentY && mouseY <= currentY + 9;
                int finalColor = (hover && clickable) ? 0xFFFFFFFF : actionColor;
                gui.drawString(this.font, actionText, actionX, currentY, finalColor, false);
            }

            int arrowX = (actionX > 0 ? actionX : SIDEBAR_WIDTH - 10) - 12;
            RenderSystem.enableBlend();
            if (isExpanded) {
                RenderSystem.setShaderColor(1.0f, 0.8f, 0.2f, 1.0f);
                gui.blit(ARROW_DOWN, arrowX, currentY + 1, 0, 0, 8, 8, 8, 8);
            } else {
                RenderSystem.setShaderColor(0.7f, 0.7f, 0.7f, 1.0f);
                gui.blit(ARROW_UP, arrowX, currentY + 1, 0, 0, 8, 8, 8, 8);
            }
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);

            if (amILeader || squad.isLocked) {
                int lockX = arrowX - 12;
                if (squad.isLocked) RenderSystem.setShaderColor(1.0f, 0.8f, 0.2f, 1.0f);
                else RenderSystem.setShaderColor(0.6f, 0.6f, 0.6f, 1.0f);
                gui.blit(LOCK_ICON, lockX, currentY + 1, 0, 0, 8, 8, 8, 8);
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            }

            // Р•СЃР»Рё Сѓ РѕС‚СЂСЏРґР° РµСЃС‚СЊ Р»РёРґРµСЂ вЂ” СЂРµР·РµСЂРІРёСЂСѓРµРј РґРѕРїРѕР»РЅРёС‚РµР»СЊРЅС‹Рµ 9px РїРѕРґ РµРіРѕ РёРјСЏ
            currentY += hasLeaderLine ? 21 : 12;

            if (isExpanded) {
                List<String> sortedMembers = getSortedMembers(squad);

                // РџСЂРµРґРІР°СЂРёС‚РµР»СЊРЅС‹Р№ РїСЂРѕС…РѕРґ: Р Р…Р В°РЎвЂ¦Р С•Р Т‘Р С‘Р С Р Т‘Р С‘Р В°Р С—Р В°Р В·Р С•Р Р… РЎРѓРЎвЂљРЎР‚Р С•Р С” Р С”Р В°Р В¶Р Т‘Р С•Р С–Р С• РЎвЂћР В°Р ВµРЎР‚РЎвЂљР С‘Р СР В° (Bravo/Charlie),
                // РЎвЂЎРЎвЂљР С•Р В±РЎвЂ№ Р Р…Р В°РЎР‚Р С‘РЎРѓР С•Р Р†Р В°РЎвЂљРЎРЉ Р С›Р вЂќР СњР Р€ РЎРѓР С—Р В»Р С•РЎв‚¬Р Р…РЎС“РЎР‹ Р С—Р С•Р В»Р С•РЎРѓР С”РЎС“ Р В±Р ВµР В· РЎР‚Р В°Р В·РЎР‚РЎвЂ№Р Р†Р С•Р Р† Р Р…Р В° Р Р†РЎРѓР ВµРЎвЂ¦ Р ВµР С–Р С• Р В±Р С•Р в„–РЎвЂ Р С•Р Р† РЎРѓРЎР‚Р В°Р В·РЎС“,
                // Р Р†Р СР ВµРЎРѓРЎвЂљР С• Р С•РЎвЂљР Т‘Р ВµР В»РЎРЉР Р…Р С•Р С–Р С• Р С”РЎС“РЎРѓР С•РЎвЂЎР С”Р В° Р Р…Р В° Р С”Р В°Р В¶Р Т‘РЎС“РЎР‹ РЎРѓРЎвЂљРЎР‚Р С•Р С”РЎС“.
                int membersStartY = currentY;
                int bravoFirstIdx = -1, bravoLastIdx = -1;
                int charlieFirstIdx = -1, charlieLastIdx = -1;
                for (int i = 0; i < sortedMembers.size(); i++) {
                    String m = sortedMembers.get(i);
                    boolean mInBravo = m.equals(squad.bravoLeader) || squad.bravoMembers.contains(m);
                    boolean mInCharlie = m.equals(squad.charlieLeader) || squad.charlieMembers.contains(m);
                    if (mInBravo) {
                        if (bravoFirstIdx == -1) bravoFirstIdx = i;
                        bravoLastIdx = i;
                    }
                    if (mInCharlie) {
                        if (charlieFirstIdx == -1) charlieFirstIdx = i;
                        charlieLastIdx = i;
                    }
                }
                if (bravoFirstIdx != -1) {
                    int barY1 = membersStartY + bravoFirstIdx * 12;
                    int barY2 = membersStartY + bravoLastIdx * 12 + 10;
                    gui.fill(20, barY1, 21, barY2, 0xFFCC77FF);
                }
                if (charlieFirstIdx != -1) {
                    int barY1 = membersStartY + charlieFirstIdx * 12;
                    int barY2 = membersStartY + charlieLastIdx * 12 + 10;
                    gui.fill(20, barY1, 21, barY2, 0xFF5FE075);
                }

                for (String member : sortedMembers) {
                    boolean isLeaderMember = member.equals(squad.leader);
                    boolean isOnline = this.minecraft.getConnection().getPlayerInfo(member) != null;

                    int col = 0xFFFFFFFF;
                    if (isLeaderMember) col = 0xFFFFD700;
                    else if (member.equals(squad.bravoLeader)) col = 0xFFFF55FF;   // РЎР‚Р С•Р В·Р С•Р Р†РЎвЂ№Р в„–/magenta
                    else if (squad.bravoMembers.contains(member)) col = 0xFFAA00AA; // РЎвЂљРЎвЂР СР Р…Р С•-РЎвЂћР С‘Р С•Р В»Р ВµРЎвЂљР С•Р Р†РЎвЂ№Р в„–
                    else if (member.equals(squad.charlieLeader)) col = 0xFF55FF55;  // Р В·Р ВµР В»РЎвЂР Р…РЎвЂ№Р в„–
                    else if (squad.charlieMembers.contains(member)) col = 0xFF00AA00; // РЎвЂљРЎвЂР СР Р…Р С•-Р В·Р ВµР В»РЎвЂР Р…РЎвЂ№Р в„–

                    if (!isOnline) col = 0xFFAAAAAA;
                    if (ClientData.isHighlighted(member)) col = 0xFFFFFFFF; // РІС‹РґРµР»РµРЅ РЅР° РєР°СЂС‚Рµ - Р±РµР»С‹Р№ РЅРёРє

                    // Р вЂРЎС“Р С”Р Р†РЎС“ РЎР‚Р С‘РЎРѓРЎС“Р ВµР С РЎвЂљР С•Р В»РЎРЉР С”Р С• РЎС“ Р В¤Р СћР вЂє (Р В»Р С‘Р Т‘Р ВµРЎР‚Р В° РЎвЂћР В°Р ВµРЎР‚РЎвЂљР С‘Р СР В°): Bravo -> "B", Charlie -> "C".
                    boolean isBravoFTL = member.equals(squad.bravoLeader);
                    boolean isCharlieFTL = member.equals(squad.charlieLeader);
                    if (isBravoFTL) {
                        gui.drawString(this.font, "B", 8, currentY + 1, 0xFFCC77FF, false);
                    } else if (isCharlieFTL) {
                        gui.drawString(this.font, "C", 8, currentY + 1, 0xFF5FE075, false);
                    }

                    int xOffset = 30;

                    if (isMySquad && member.equals(myName)) {
                        int btnX = xOffset;
                        boolean btnHover = mouseX >= btnX && mouseX <= btnX + 10 && mouseY >= currentY && mouseY <= currentY + 10;
                        gui.fill(btnX, currentY, btnX + 10, currentY + 10, btnHover ? 0xFF666666 : 0xFF444444);
                        gui.drawString(this.font, "K", btnX + 2, currentY + 1, 0xFFFFFFFF, false);
                        xOffset += 14;
                    }

                    String kName = ClientData.playerKits.getOrDefault(member, "Unassigned");
                    if (!isOnline) {
                        // Игрок дисконектнулся — рисуем иконку дисконекта (без tint)
                        RenderSystem.enableBlend();
                        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 0.7f);
                        gui.blit(ICON_DISCONNECT, xOffset, currentY, 0, 0, 10, 10, 10, 10);
                        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                        xOffset += 12;
                    } else if (!kName.equals("Unassigned") && !kName.isEmpty()) {
                        ResourceLocation kitIcon = new ResourceLocation("aas", "textures/gui/kits/" + kName.toLowerCase().replace(" ", "_") + ".png");
                        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
                        gui.blit(kitIcon, xOffset, currentY, 0, 0, 10, 10, 10, 10);
                        xOffset += 12;
                    }

                    gui.drawString(this.font, member, xOffset, currentY + 1, col, false);

                    // Р§РµСЂРµРї / РЅРѕРє вЂ” РІ СЃР°РјРѕРј РїСЂР°РІРѕРј РјРµСЃС‚Рµ СЃР°Р№РґР±Р°СЂР°, С‚Р°Рє Р¶Рµ РєР°Рє РІ SquadPanelOverlay
                    MapPlayerInfo memberInfo = ClientData.mapPlayers.get(member);
                    boolean memberIsDead   = memberInfo != null && memberInfo.isDead;
                    boolean memberIsDowned = memberInfo != null && memberInfo.isDowned;

                    if (memberIsDead || memberIsDowned) {
                        RenderSystem.enableBlend();
                        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                        int statusIconX = SIDEBAR_WIDTH - 16;
                        int statusIconY = currentY;
                        if (memberIsDead) {
                            gui.blit(ICON_DEAD, statusIconX, statusIconY, 8, 8, 0f, 0f, 10, 10, 10, 10);
                        } else {
                            gui.blit(ICON_HEARTBEAT, statusIconX, statusIconY, 8, 8, 0f, 0f, 36, 36, 36, 36);
                        }
                        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                    }

                    currentY += 12;
                }
                currentY += 4;
            }
            currentY += 4;
            index++;
        }
    }

    private void renderContextMenu(GuiGraphics gui, int mx, int my) {
        String myName = this.minecraft.player.getScoreboardName();
        AASWorldData.Squad s = null;
        for(AASWorldData.Squad sq : ClientData.clientSquads) { if(sq.id == contextTargetSquadId) s = sq; }
        if (s == null) { showContextMenu = false; return; }

        List<String> options = new ArrayList<>();

        if (contextTargetIsSquad) {
            // Р СљР ВµР Р…РЎР‹ Р С”Р С•Р СР В°Р Р…Р Т‘Р С‘РЎР‚Р В° Р Р…Р В° РЎв‚¬Р В°Р С—Р С”Р Вµ Р С•РЎвЂљРЎР‚РЎРЏР Т‘Р В°
            options.add("Disband Squad");
        } else {
            boolean amISL = s.leader.equals(myName);
            boolean amIBravoFTL = s.bravoLeader.equals(myName);
            boolean amICharlieFTL = s.charlieLeader.equals(myName);

            if (amISL) {
                options.add("Promote to SL");
                if (!s.bravoLeader.equals(contextTargetPlayer) && !s.charlieLeader.equals(contextTargetPlayer)) {
                    options.add("Set FTL Bravo");
                    options.add("Set FTL Charlie");
                }
                options.add("Add to Bravo");
                options.add("Add to Charlie");
                options.add("Remove from FT");
                options.add("Kick from Squad");
            } else if (amIBravoFTL) {
                if (!s.leader.equals(contextTargetPlayer) && !s.charlieLeader.equals(contextTargetPlayer)) {
                    options.add("Pass FTL Bravo");
                }
                options.add("Add to Bravo");
                options.add("Remove from FT");
            } else if (amICharlieFTL) {
                if (!s.leader.equals(contextTargetPlayer) && !s.bravoLeader.equals(contextTargetPlayer)) {
                    options.add("Pass FTL Charlie");
                }
                options.add("Add to Charlie");
                options.add("Remove from FT");
            }
        }

        if (options.isEmpty()) { showContextMenu = false; return; }

        int w = ClientData.squadMenuWidth(this.font, options);
        int h = options.size() * 12 + 4;
        gui.fill(contextMenuX, contextMenuY, contextMenuX + w, contextMenuY + h, 0xEE111111);
        gui.renderOutline(contextMenuX, contextMenuY, w, h, 0xFF555555);

        for (int i = 0; i < options.size(); i++) {
            int y = contextMenuY + 2 + (i * 12);
            boolean hover = mx >= contextMenuX && mx <= contextMenuX + w && my >= y && my < y + 12;
            if (hover) gui.fill(contextMenuX + 1, y, contextMenuX + w - 1, y + 12, 0xFF444444);
            gui.drawString(this.font, ClientData.squadMenuOption(options.get(i)), contextMenuX + 4, y + 2, hover ? 0xFFFFFF : 0xAAAAAA, false);
        }
    }

    private void renderTopBar(GuiGraphics gui) {
        String myName = this.minecraft.player.getScoreboardName();
        String myTeam = getPlayerTeam();
        String teamUpper = myTeam.toUpperCase();

        // 1. Р СњР В°РЎвЂ¦Р С•Р Т‘Р С‘Р С РЎРѓР Р†Р С•Р в„– Р С•РЎвЂљРЎР‚РЎРЏР Т‘
        AASWorldData.Squad mySquad = null;
        for (AASWorldData.Squad s : ClientData.clientSquads) {
            if (s.members.contains(myName)) {
                mySquad = s;
                break;
            }
        }

        int tickets = 0;
        ResourceLocation flag = null;

        // 2. Р ВРЎРѓР С—Р С•Р В»РЎРЉР В·РЎС“Р ВµР С teamUpper Р Т‘Р В»РЎРЏ Р Р†РЎвЂ№Р В±Р С•РЎР‚Р В° РЎвЂљР С‘Р С”Р ВµРЎвЂљР С•Р Р† Р С‘ РЎвЂћР В»Р В°Р С–Р В°
        if (teamUpper.contains("BLUE")) {
            tickets = ClientData.BLUE_TICKETS;
            flag = getFlagTexture(ClientData.BLUE_FACTION);
        } else if (teamUpper.contains("RED")) {
            tickets = ClientData.RED_TICKETS;
            flag = getFlagTexture(ClientData.RED_FACTION);
        }

        // 3. Р вЂєР С›Р вЂњР ВР С™Р С’ Р С™Р СњР С›Р СџР С™Р В CMD
        boolean isBlue = teamUpper.contains("BLUE");
        int myTeamCMDId = isBlue ? ClientData.blueCMDId : ClientData.redCMDId;
        boolean myTeamVoteActive = isBlue ? ClientData.blueCmdVoteActive : ClientData.redCmdVoteActive;

        boolean isLeader = (mySquad != null && mySquad.leader.equals(myName));
        this.applyCmdButton.visible = (isLeader && myTeamCMDId == -1 && !myTeamVoteActive);

        // === 4. Р В¦Р вЂўР СњР СћР В Р ВР В Р С›Р вЂ™Р С’Р СњР ВР вЂў Р СџР С› Р В­Р С™Р В Р С’Р СњР Р€ ===
        String ticketText = String.valueOf(tickets);
        int textWidth = this.font.width(ticketText);
        int iconSize = 12;
        int gap = 5;
        int flagWidth = 32;
        int flagHeight = 18;

        // Р РЋРЎвЂЎР С‘РЎвЂљР В°Р ВµР С Р С•Р В±РЎвЂ°РЎС“РЎР‹ РЎв‚¬Р С‘РЎР‚Р С‘Р Р…РЎС“ Р Р†РЎРѓР ВµР С–Р С• Р В±Р В»Р С•Р С”Р В° (РЎвЂћР В»Р В°Р С– + Р С‘Р С”Р С•Р Р…Р С”Р В° + РЎвЂљР ВµР С”РЎРѓРЎвЂљ + Р С•РЎвЂљРЎРѓРЎвЂљРЎС“Р С—РЎвЂ№)
        int totalContentWidth = textWidth + gap + iconSize;
        if (flag != null) {
            totalContentWidth += flagWidth + gap;
        }

        // Р ВР РЋР СџР В Р С’Р вЂ™Р вЂєР вЂўР СњР ВР вЂў: Р СћР ВµР С—Р ВµРЎР‚РЎРЉ Р В±Р ВµРЎР‚Р ВµР С РЎвЂ Р ВµР Р…РЎвЂљРЎР‚ Р вЂ™Р РЋР вЂўР вЂњР С› РЎРЊР С”РЎР‚Р В°Р Р…Р В° (this.width / 2)
        int topBarCenterX = this.width / 2;
        int currentX = topBarCenterX - (totalContentWidth / 2);

        // Р С›РЎвЂљРЎР‚Р С‘РЎРѓР С•Р Р†Р С”Р В° РЎвЂћР В»Р В°Р С–Р В°
        if (flag != null) {
            gui.pose().pushPose();
            gui.pose().translate(0, 0, 100);
            int flagY = (TOP_BAR_HEIGHT - flagHeight) / 2;
            gui.blit(flag, currentX, flagY, 0, 0, flagWidth, flagHeight, flagWidth, flagHeight);
            gui.pose().popPose();

            currentX += flagWidth + gap; // Р РЋР Т‘Р Р†Р С‘Р С–Р В°Р ВµР С Р С”РЎС“РЎР‚РЎРѓР С•РЎР‚ Р Р†Р С—РЎР‚Р В°Р Р†Р С•
        }

        // Р С›РЎвЂљРЎР‚Р С‘РЎРѓР С•Р Р†Р С”Р В° Р С‘Р С”Р С•Р Р…Р С”Р С‘ Р В±Р С‘Р В»Р ВµРЎвЂљР В°
        ResourceLocation ticketIcon = new ResourceLocation("aas", "textures/gui/minimap_tickets.png");
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        int iconY = (TOP_BAR_HEIGHT - iconSize) / 2;
        gui.blit(ticketIcon, currentX, iconY, iconSize, iconSize, 0, 0, 16, 16, 16, 16);

        currentX += iconSize + gap; // Р РЋР Т‘Р Р†Р С‘Р С–Р В°Р ВµР С Р С”РЎС“РЎР‚РЎРѓР С•РЎР‚ Р Р†Р С—РЎР‚Р В°Р Р†Р С•

        // Р С›РЎвЂљРЎР‚Р С‘РЎРѓР С•Р Р†Р С”Р В° РЎвЂљР ВµР С”РЎРѓРЎвЂљР В°
        int textY = (TOP_BAR_HEIGHT - 8) / 2 + 1; // Р В¦Р ВµР Р…РЎвЂљРЎР‚Р С‘РЎР‚РЎС“Р ВµР С РЎв‚¬РЎР‚Р С‘РЎвЂћРЎвЂљ Р С—Р С• Р Р†Р ВµРЎР‚РЎвЂљР С‘Р С”Р В°Р В»Р С‘
        gui.drawString(this.font, ticketText, currentX, textY, 0xFFFFFFFF, false);
    }

    // РџРѕРїР°Р» Р»Рё РєСѓСЂСЃРѕСЂ РїРѕ РЅРёРєСѓ РёРіСЂРѕРєР° (РІРјРµСЃС‚Рµ СЃ РёРєРѕРЅРєРѕР№ РєРёС‚Р°) РІ СЃС‚СЂРѕРєРµ СЃРїРёСЃРєР° РѕС‚СЂСЏРґР°.
    // РћС‚СЃС‚СѓРїС‹ С‚Р°РєРёРµ Р¶Рµ, РєР°Рє РІ renderSquadList: Сѓ СЃРµР±СЏ СЃР»РµРІР° РєРЅРѕРїРєР° K (30..40), РЅРёРє РЅР°С‡РёРЅР°РµС‚СЃСЏ СЃ 44.
    private boolean isOverMemberNick(String member, boolean isSelf, double mouseX) {
        int start = isSelf ? 44 : 30;
        int end = start;
        String kName = ClientData.playerKits.getOrDefault(member, "Unassigned");
        if (!kName.equals("Unassigned") && !kName.isEmpty()) end += 12; // РёРєРѕРЅРєР° РєРёС‚Р°
        end += this.font.width(member);
        return mouseX >= start && mouseX <= end;
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
    private static class SquadButton extends Button {
        public SquadButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(GuiGraphics gui, int mouseX, int mouseY, float partialTicks) {
            if (!this.visible) return;

            // Р РЋРЎвЂљР С‘Р В»РЎРЉ: РЎРЏРЎР‚Р С”Р В°РЎРЏ РЎР‚Р В°Р СР С”Р В° Р С—РЎР‚Р С‘ Р Р…Р В°Р Р†Р ВµР Т‘Р ВµР Р…Р С‘Р С‘
            int borderColor = this.isHovered() ? 0xFFFFFFFF : 0xFF999999;

            // Р вЂўРЎРѓР В»Р С‘ Р С”Р Р…Р С•Р С—Р С”Р В° Р Р†РЎвЂ№Р С”Р В»РЎР‹РЎвЂЎР ВµР Р…Р В° (РЎС“Р В¶Р Вµ Р Р…Р В°Р В¶Р В°РЎвЂљР В° Р С‘Р В»Р С‘ Р Р…Р ВµР В»РЎРЉР В·РЎРЏ Р Р…Р В°Р В¶Р В°РЎвЂљРЎРЉ)
            if (!this.active) borderColor = 0xFF444444;

            // Р В¤Р С•Р Р…
            gui.fill(getX(), getY(), getX() + width, getY() + height, 0xCC111111);
            // Р С›Р С”Р В°Р Р…РЎвЂљР С•Р Р†Р С”Р В°
            gui.renderOutline(getX(), getY(), width, height, borderColor);

            int textColor = this.active ? 0xFFFFFFFF : 0xFF777777;

            // Р СћР ВµР С”РЎРѓРЎвЂљ Р С—Р С• РЎвЂ Р ВµР Р…РЎвЂљРЎР‚РЎС“
            gui.drawCenteredString(Minecraft.getInstance().font, this.getMessage(), getX() + width / 2, getY() + (height - 8) / 2, textColor);

            // Р СљР В°Р В»Р ВµР Р…РЎРЉР С”Р С‘Р в„– Р В±Р ВµР В»РЎвЂ№Р в„– РЎС“Р С–Р С•Р В»Р С•Р С” РЎРѓР Р…Р С‘Р В·РЎС“ Р С—РЎР‚Р С‘ Р Р…Р В°Р Р†Р ВµР Т‘Р ВµР Р…Р С‘Р С‘
            if (this.active && this.isHovered()) {
                gui.fill(getX(), getY() + height - 2, getX() + 2, getY() + height, 0xFFFFFFFF);
            }
        }
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
            case "bluefor": return FLAG_BLUEFOR;
            case "redfor": return FLAG_REDFOR;
            case "insurgency": return FLAG_INSURGENCY;
            case "pmc": return FLAG_PMC;
            case "germany": return FLAG_GERMANY;
            case "militia": return FLAG_MILITIA;
            default: return null;
        }
    }

    @Override
    public boolean isPauseScreen() { return false; }
}