package com.example.aas.client;

import com.example.aas.client.gui.AASMapRenderer;
import com.example.aas.client.gui.TacticalMapRadialScreen;
import com.example.aas.network.*;
import com.example.aas.network.MapPlayerInfo;
import com.example.aas.world.AASWorldData;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.Team;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.ChatFormatting;

public class AASDeathScreen extends DeathScreen {
    private final long deathTimestamp;
    private final int respawnTimeTotal;
    private EditBox chatInput;
    private Button chatModeButton;
    private int chatMode = 1; // 0 = ALL, 1 = TEAM, 2 = SQUAD
    // Р вЂєР С•Р С–Р С‘Р С”Р В° Р С•РЎвЂљРЎР‚РЎРЏР Т‘Р С•Р Р†
    private final Set<Integer> expandedSquads = new HashSet<>();
    private int lastOwnSquadId = -1; // РѕС‚СЃР»РµР¶РёРІР°РµРј СЃРІРѕР№ РѕС‚СЂСЏРґ, С‡С‚РѕР±С‹ Р°РІС‚РѕСЂР°Р·РІРѕСЂР°С‡РёРІР°С‚СЊ РµРіРѕ РїСЂРё СЃРѕР·РґР°РЅРёРё/РІС…РѕРґРµ Р±РµР· РїРµСЂРµРѕС‚РєСЂС‹С‚РёСЏ СЌРєСЂР°РЅР°
    private final AASMapRenderer mapRenderer = new AASMapRenderer();
    private static final int SIDEBAR_WIDTH = 195;

    // Р Р€Р С—РЎР‚Р В°Р Р†Р В»Р ВµР Р…Р С‘Р Вµ Р С•РЎвЂљРЎР‚РЎРЏР Т‘Р С•Р С
    private SquadButton applyCmdButton;
    private EditBox nameInput;
    private Button createButton;

    // Р С™Р С•Р Р…РЎвЂљР ВµР С”РЎРѓРЎвЂљР Р…Р С•Р Вµ Р СР ВµР Р…РЎР‹ (Р СџР С™Р Сљ Р С—Р С• Р С‘Р С–РЎР‚Р С•Р С”РЎС“ Р Р† Р С•РЎвЂљРЎР‚РЎРЏР Т‘Р Вµ)
    private boolean showContextMenu = false;
    private int contextMenuX = 0;
    private int contextMenuY = 0;
    private String contextTargetPlayer = "";
    private int contextTargetSquadId = -1;
    private boolean contextTargetIsSquad = false;

    // Р вЂєР С•Р С–Р С‘Р С”Р В° РЎРѓР С—Р В°Р Р†Р Р…Р В°
    private String selectedSpawnType = "";
    private String selectedDisplayName = "NONE";
    private Button deployButton;

    // Р СћР ВµР С”РЎРѓРЎвЂљРЎС“РЎР‚РЎвЂ№
    private static final ResourceLocation ARROW_DOWN = new ResourceLocation("aas", "textures/gui/arrow_down.png");
    private static final ResourceLocation ARROW_UP = new ResourceLocation("aas", "textures/gui/arrow_up.png");
    private static final ResourceLocation LOCK_ICON = new ResourceLocation("aas", "textures/gui/squad_lock.png");
    private static final ResourceLocation TICKET_ICON = new ResourceLocation("aas", "textures/gui/minimap_tickets.png");
    private static final ResourceLocation VOICE_ICON = new ResourceLocation("aas", "textures/gui/voice_icon.png");
    private static final ResourceLocation RADIO_ICON = new ResourceLocation("aas", "textures/gui/voice_icon_radio.png");
    private static final ResourceLocation CIRCLE_BADGE = new ResourceLocation("aas", "textures/gui/map_icons/player_circle.png");
    private static final ResourceLocation ICON_DEAD      = new ResourceLocation("aas", "textures/gui/stats/deaths.png");
    private static final ResourceLocation ICON_HEARTBEAT = new ResourceLocation("aas", "textures/gui/heartbeat.png");

    public AASDeathScreen(Component cause, boolean hardcore) {
        super(cause != null ? cause : Component.empty(), hardcore);

        // Р В¤Р ВР С™Р РЋ: Р вЂўРЎРѓР В»Р С‘ Р СРЎвЂ№ Р С•РЎвЂљР С”РЎР‚РЎвЂ№Р Р†Р В°Р ВµР С РЎРЊР С”РЎР‚Р В°Р Р… РЎРѓР СР ВµРЎР‚РЎвЂљР С‘, Р В° Р С–Р В»Р С•Р В±Р В°Р В»РЎРЉР Р…РЎвЂ№Р в„– РЎвЂљР В°Р в„–Р СР ВµРЎР‚ Р ВµРЎвЂ°Р Вµ Р Р…Р Вµ Р В·Р В°Р С—РЎС“РЎвЂ°Р ВµР Р…
        // (Р Р…Р В°Р С—РЎР‚Р С‘Р СР ВµРЎР‚, Р СР С–Р Р…Р С•Р Р†Р ВµР Р…Р Р…Р В°РЎРЏ РЎРѓР СР ВµРЎР‚РЎвЂљРЎРЉ Р В±Р ВµР В· Р Р…Р С•Р С”Р В°РЎС“РЎвЂљР В°), Р В·Р В°Р С—РЎС“РЎРѓР С”Р В°Р ВµР С Р ВµР С–Р С• Р С›Р вЂќР ВР Сњ Р В Р С’Р вЂ”.
        if (ClientData.globalDeathTimestamp == 0) {
            ClientData.globalDeathTimestamp = System.currentTimeMillis();
        }

        // Р СџРЎР‚Р С‘Р Р†РЎРЏР В·РЎвЂ№Р Р†Р В°Р ВµР С Р В»Р С•Р С”Р В°Р В»РЎРЉР Р…РЎС“РЎР‹ Р С—Р ВµРЎР‚Р ВµР СР ВµР Р…Р Р…РЎС“РЎР‹ РЎРЊР С”РЎР‚Р В°Р Р…Р В° Р С” Р С–Р В»Р С•Р В±Р В°Р В»РЎРЉР Р…Р С•Р в„–
        this.deathTimestamp = ClientData.globalDeathTimestamp;

        // Р вЂР ВµРЎР‚Р ВµР С Р Р†РЎР‚Р ВµР СРЎРЏ РЎР‚Р ВµРЎРѓР С—Р В°РЎС“Р Р…Р В° Р С‘Р В· Р С”Р С•Р Р…РЎвЂћР С‘Р С–Р В°, Р С—РЎР‚Р С‘РЎРѓР В»Р В°Р Р…Р Р…Р С•Р С–Р С• РЎРѓР ВµРЎР‚Р Р†Р ВµРЎР‚Р С•Р С
        this.respawnTimeTotal = ClientData.RESPAWN_TIME > 0 ? ClientData.RESPAWN_TIME : 10;

        String myName = Minecraft.getInstance().getUser().getName();
        for (AASWorldData.Squad s : ClientData.clientSquads) {
            if (s.members.contains(myName)) {
                expandedSquads.add(s.id);
                lastOwnSquadId = s.id; // РґРѕР±Р°РІРёС‚СЊ СЌС‚Сѓ СЃС‚СЂРѕРєСѓ
            }
        }
    }

    @Override
    protected void init() {
        this.clearWidgets();

        // Р С™Р С’Р В Р СћР С’ Р СњР С’ Р вЂ™Р РЋР В® Р вЂ™Р В«Р РЋР С›Р СћР Р€ Р В­Р С™Р В Р С’Р СњР С’ (Р С”Р В°РЎРѓР В°Р ВµРЎвЂљРЎРѓРЎРЏ Р Р†Р ВµРЎР‚РЎвЂ¦Р Р…Р ВµР С–Р С• Р С‘ Р Р…Р С‘Р В¶Р Р…Р ВµР С–Р С• Р С”РЎР‚Р В°РЎРЏ) - Р С—Р С•Р В»Р Р…Р С•РЎРѓРЎвЂљРЎРЉРЎР‹ РЎР‚Р В°Р В±Р С•РЎвЂЎР В°РЎРЏ,
        // Р С”Р В°Р С” Р Р† SquadSelectionScreen: Р С—РЎР‚Р С‘Р В±Р В»Р С‘Р В¶Р ВµР Р…Р С‘Р Вµ/Р С•РЎвЂљР Т‘Р В°Р В»Р ВµР Р…Р С‘Р Вµ Р С”Р С•Р В»Р ВµРЎРѓР С•Р С Р С‘ Р С—Р ВµРЎР‚Р ВµРЎвЂљР В°РЎРѓР С”Р С‘Р Р†Р В°Р Р…Р С‘Р Вµ Р вЂєР С™Р Сљ/Р СџР С™Р Сљ.
        int mapSize = this.height;
        int mapX = this.width - mapSize;
        mapRenderer.init(mapX, 0, mapSize);

        // Р С™Р Р…Р С•Р С—Р С”Р В° "Р СџР С›Р вЂќР С’Р СћР В¬ Р вЂ”Р С’Р Р‡Р вЂ™Р С™Р Р€ Р СњР С’ Р С™Р С›Р СљР С’Р СњР вЂќР ВР В Р С’"
        this.applyCmdButton = this.addRenderableWidget(new SquadButton(10, 10, SIDEBAR_WIDTH - 20, 20,
                Component.literal("APPLY FOR CMD"), b -> {
            PacketHandler.INSTANCE.sendToServer(new PacketRequestCMD());
            b.visible = false;
        }));

        // Р СџР С•Р В»Р Вµ Р С‘Р СР ВµР Р…Р С‘ + Р С”Р Р…Р С•Р С—Р С”Р В° РЎРѓР С•Р В·Р Т‘Р В°Р Р…Р С‘РЎРЏ Р С•РЎвЂљРЎР‚РЎРЏР Т‘Р В° (Р Р†Р С‘Р Т‘Р Р…РЎвЂ№ РЎвЂљР С•Р В»РЎРЉР С”Р С• Р ВµРЎРѓР В»Р С‘ Р С‘Р С–РЎР‚Р С•Р С” Р Р…Р Вµ Р Р† Р С•РЎвЂљРЎР‚РЎРЏР Т‘Р Вµ)
        boolean isInSquad = isPlayerInSquad();

        this.nameInput = this.addRenderableWidget(
                new EditBox(this.font, 10, this.height - 90, SIDEBAR_WIDTH - 20, 20, Component.literal("Squad Name")));
        this.nameInput.setMaxLength(12);
        this.nameInput.setVisible(!isInSquad);

        this.createButton = this.addRenderableWidget(new SquadButton(10, this.height - 65, SIDEBAR_WIDTH - 20, 20, Component.literal("Create Squad"), b -> {
            PacketHandler.INSTANCE.sendToServer(new PacketSquadAction(0, 0, nameInput.getValue()));
            this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(com.example.aas.sound.ModSounds.SQUAD_JOIN.get(), 1.0F));
        }));
        this.createButton.visible = !isInSquad;

        // Р С™Р Р…Р С•Р С—Р С”Р В° DEPLOY
        // Р вЂ™ AASDeathScreen.java, Р Р†Р Р…РЎС“РЎвЂљРЎР‚Р С‘ Р С‘Р Р…Р С‘РЎвЂ Р С‘Р В°Р В»Р С‘Р В·Р В°РЎвЂ Р С‘Р С‘ Р С”Р Р…Р С•Р С—Р С”Р С‘ deployButton
        this.deployButton = this.addRenderableWidget(new DeployButton(10, this.height - 35, 100, 25, Component.literal("DEPLOY"), b -> {
            if (!selectedSpawnType.isEmpty()) {
                // Р РЋР вЂР В Р С’Р РЋР В«Р вЂ™Р С’Р вЂўР Сљ Р вЂ™Р РЋР Рѓ Р СџР вЂўР В Р вЂўР вЂќ Р В Р вЂўР РЋР СџР С’Р вЂ™Р СњР С›Р Сљ
                ClientData.globalDeathTimestamp = 0;
                ClientData.deathFadeStartTime = 0;
                ClientData.deathFadePlayed = false;

                PacketHandler.INSTANCE.sendToServer(new PacketRespawnRequest(selectedSpawnType));
                this.minecraft.player.respawn();
                this.minecraft.setScreen(null);
            }
        }));

        // Р В Р В°РЎРѓРЎвЂЎР ВµРЎвЂљРЎвЂ№ Р С—Р С•Р В·Р С‘РЎвЂ Р С‘Р в„–
        int mapOriginX = this.width - this.height; // Р вЂњРЎР‚Р В°Р Р…Р С‘РЎвЂ Р В°, Р С–Р Т‘Р Вµ Р Р…Р В°РЎвЂЎР С‘Р Р…Р В°Р ВµРЎвЂљРЎРѓРЎРЏ Р С”Р В°РЎР‚РЎвЂљР В°
        int chatX = SIDEBAR_WIDTH + 10;
        int chatAvailableWidth = mapOriginX - chatX - 10; // Р вЂ™РЎРѓРЎРЏ РЎв‚¬Р С‘РЎР‚Р С‘Р Р…Р В° Р С•РЎвЂљ Р СР ВµР Р…РЎР‹ Р Т‘Р С• Р С”Р В°РЎР‚РЎвЂљРЎвЂ№
        int modeBtnWidth = 60; // Р РЃР С‘РЎР‚Р С‘Р Р…Р В° Р Т‘Р В»РЎРЏ Р С”Р Р…Р С•Р С—Р С”Р С‘ РЎР‚Р ВµР В¶Р С‘Р СР В° (Р Р†Р В»Р ВµР В·Р ВµРЎвЂљ "SQUAD")
        int gap = 4;

        // 1. Р С™Р СњР С›Р СџР С™Р С’ Р В Р вЂўР вЂ“Р ВР СљР С’ (Р РЋРЎвЂљР В°Р В»Р В° РЎв‚¬Р С‘РЎР‚Р Вµ)
        chatModeButton = this.addRenderableWidget(new SquadButton(chatX, this.height - 40, modeBtnWidth, 20, getChatModeText(), b -> {
            chatMode = (chatMode + 1) % 3;
            b.setMessage(getChatModeText());
        }));

        // 2. Р СџР С›Р вЂєР вЂў Р вЂ™Р вЂ™Р С›Р вЂќР С’ (Р В Р В°РЎРѓРЎвЂљРЎРЏР Р…РЎС“РЎвЂљР С• Р Т‘Р С• Р С”РЎР‚Р В°РЎРЏ Р С”Р В°РЎР‚РЎвЂљРЎвЂ№)
        chatInput = new EditBox(this.font, chatX + modeBtnWidth + gap, this.height - 40,
                chatAvailableWidth - modeBtnWidth - gap, 20, Component.literal("Chat"));
        chatInput.setMaxLength(100);
        this.addRenderableWidget(chatInput);

        // 3. Р СљР С’Р вЂєР вЂўР СњР В¬Р С™Р С’Р Р‡ Р С™Р СњР С›Р СџР С™Р С’ QUIT (Р вЂ™ РЎРѓР В°Р СР С•Р С РЎС“Р С–Р В»РЎС“ РЎРЊР С”РЎР‚Р В°Р Р…Р В°)
        this.addRenderableWidget(new SquadButton(this.width - 45, 5, 40, 20, Component.literal("Quit"), b -> {
            if (this.minecraft.level != null) this.minecraft.level.disconnect();
            this.minecraft.setScreen(new net.minecraft.client.gui.screens.TitleScreen());
        }));
    }
    private Component getChatModeText() {
        switch (chatMode) {
            case 0: return Component.literal("ALL").withStyle(ChatFormatting.LIGHT_PURPLE);
            case 2: return Component.literal("SQUAD").withStyle(ChatFormatting.GREEN);
            default: return Component.literal("TEAM").withStyle(ChatFormatting.BLUE);
        }
    }
    @Override
    public void tick() {
        super.tick();
        if (nameInput != null) nameInput.tick();

        if (applyCmdButton != null) {
            applyCmdButton.visible = isApplyCmdVisible();
        }

        boolean isInSquad = isPlayerInSquad();
        if (nameInput != null && nameInput.isVisible() == isInSquad) {
            nameInput.setVisible(!isInSquad);
            createButton.visible = !isInSquad;
        }
        // РђРІС‚РѕСЂР°Р·РІРѕСЂР°С‡РёРІР°РЅРёРµ СЃС‚СЂРѕРєРё РѕС‚СЂСЏРґР° РїСЂСЏРјРѕ СЃ СЌРєСЂР°РЅР° СЃРјРµСЂС‚Рё
        if (this.minecraft != null && this.minecraft.player != null) {
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
    }
    private boolean isMyRallyBlocked() {
        String myName = this.minecraft.player.getScoreboardName();
        for (AASWorldData.Squad s : ClientData.clientSquads) {
            if (s.members.contains(myName)) return s.isRallyBlocked;
        }
        return false;
    }
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (chatInput.isFocused()) {
                String msg = chatInput.getValue().trim();
                if (!msg.isEmpty()) {
                    // Р С›РЎвЂљР С—РЎР‚Р В°Р Р†Р В»РЎРЏР ВµР С Р С—Р В°Р С”Р ВµРЎвЂљ Р Р…Р В° РЎРѓР ВµРЎР‚Р Р†Р ВµРЎР‚
                    PacketHandler.INSTANCE.sendToServer(new PacketSquadChat(msg, chatMode));
                    chatInput.setValue(""); // Р С›РЎвЂЎР С‘РЎвЂ°Р В°Р ВµР С Р С—Р С•Р В»Р Вµ
                }
                return true;
            }
            // Р вЂќР С•Р В±Р В°Р Р†Р В»РЎРЏР ВµР С Р В»Р С•Р С–Р С‘Р С”РЎС“ Р Т‘Р В»РЎРЏ Р С—Р С•Р В»РЎРЏ Р Р†Р Р†Р С•Р Т‘Р В° Р Р…Р В°Р В·Р Р†Р В°Р Р…Р С‘РЎРЏ Р С•РЎвЂљРЎР‚РЎРЏР Т‘Р В°
            if (nameInput != null && nameInput.isFocused() && nameInput.isVisible()) {
                String name = nameInput.getValue();
                PacketHandler.INSTANCE.sendToServer(new PacketSquadAction(0, 0, name));
                this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(com.example.aas.sound.ModSounds.SQUAD_JOIN.get(), 1.0F));
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
    private void renderChatArea(GuiGraphics gui) {
        int mapOriginX = this.width - this.height;
        int chatX = SIDEBAR_WIDTH + 10;
        int chatAvailableWidth = mapOriginX - chatX - 10;

        int chatBottomY = this.height - 45; // Р В§РЎС“РЎвЂљРЎРЉ Р Р†РЎвЂ№РЎв‚¬Р Вµ Р С—Р С•Р В»РЎРЏ Р Р†Р Р†Р С•Р Т‘Р В°
        int maxMessages = 15;

        int boxLeft = chatX;
        int boxTop = chatBottomY - (maxMessages * 10);
        int boxRight = chatX + chatAvailableWidth;
        int boxBottom = chatBottomY;

        // Р В¤Р С•Р Р… Р С•Р В±Р В»Р В°РЎРѓРЎвЂљР С‘ Р С‘РЎРѓРЎвЂљР С•РЎР‚Р С‘Р С‘ (РЎРѓР С•Р С•РЎвЂљР Р†Р ВµРЎвЂљРЎРѓРЎвЂљР Р†РЎС“Р ВµРЎвЂљ РЎв‚¬Р С‘РЎР‚Р С‘Р Р…Р Вµ Р С—Р С•Р В»РЎРЏ Р Р†Р Р†Р С•Р Т‘Р В°)
        gui.fill(boxLeft, boxTop, boxRight, boxBottom, 0x70000000);

        // Р вЂ“РЎвЂРЎРѓРЎвЂљР С”Р С• Р С•Р В±РЎР‚Р ВµР В·Р В°Р ВµР С Р Р†РЎРѓРЎвЂ, РЎвЂЎРЎвЂљР С• РЎР‚Р С‘РЎРѓРЎС“Р ВµРЎвЂљРЎРѓРЎРЏ Р В·Р В° Р С—РЎР‚Р ВµР Т‘Р ВµР В»Р В°Р СР С‘ РЎРЊРЎвЂљР С•Р С–Р С• Р С—РЎР‚РЎРЏР СР С•РЎС“Р С–Р С•Р В»РЎРЉР Р…Р С‘Р С”Р В° РІР‚вЂќ
        // РЎвЂљР ВµР С”РЎРѓРЎвЂљ РЎвЂћР С‘Р В·Р С‘РЎвЂЎР ВµРЎРѓР С”Р С‘ Р Р…Р Вµ РЎРѓР СР С•Р В¶Р ВµРЎвЂљ Р Р†РЎвЂ№Р В»Р ВµР В·РЎвЂљР С‘ Р Р…Р В° Р С”Р В°РЎР‚РЎвЂљРЎС“, РЎРѓР С”Р С•Р В»РЎРЉР С”Р С• Р В±РЎвЂ№ РЎРѓР С‘Р СР Р†Р С•Р В»Р С•Р Р† Р Р…Р С‘ Р В±РЎвЂ№Р В»Р С•
        gui.enableScissor(boxLeft, boxTop, boxRight, boxBottom);

        int count = 0;
        for (Component msg : ClientData.menuChatHistory) {
            if (count >= maxMessages) break;
            int y = chatBottomY - 10 - (count * 10);
            gui.drawString(this.font, msg, chatX + 3, y, 0xFFFFFFFF, true);
            count++;
        }

        gui.disableScissor();
    }

    @Override
    public void render(GuiGraphics gui, int mx, int my, float pt) {
        // 1. Р В¤Р С›Р Сњ Р В Р С™Р С’Р В Р СћР С’
        gui.fill(0, 0, this.width, this.height, 0xFF000000);
        mapRenderer.render(gui, mx, my, pt);
        mapRenderer.renderMarkerDeleteMenu(gui, mx, my);

        // 2. Р СџР С’Р СњР вЂўР вЂєР В Р ВР СњР СћР вЂўР В Р В¤Р вЂўР в„ўР РЋР С’ (Р РЋР В»Р ВµР Р†Р В°)
        gui.fill(0, 0, SIDEBAR_WIDTH + 140, this.height, 0xAA000000);
        gui.fill(0, 0, SIDEBAR_WIDTH, this.height, 0x22FFFFFF);

        // 3. Р вЂєР С›Р вЂњР ВР С™Р С’ Р СћР С’Р в„ўР СљР вЂўР В Р С’ Р вЂ™Р С›Р вЂ”Р В Р С›Р вЂ“Р вЂќР вЂўР СњР ВР Р‡ Р В Р СџР В Р С›Р вЂ™Р вЂўР В Р С™Р С’ Р вЂ™Р В«Р вЂР В Р С’Р СњР СњР С›Р вЂњР С› Р РЋР СџР С’Р вЂ™Р СњР С’
        long currentTime = System.currentTimeMillis();
        long elapsedSeconds = (currentTime - this.deathTimestamp) / 1000;
        int secondsLeft = (int) (respawnTimeTotal - elapsedSeconds);

        // Р вЂќР С‘Р Р…Р В°Р СР С‘РЎвЂЎР ВµРЎРѓР С”Р В°РЎРЏ Р С—РЎР‚Р С•Р Р†Р ВµРЎР‚Р С”Р В°: Р В¶Р С‘Р Р† Р В»Р С‘ Р С‘ Р Т‘Р С•РЎРѓРЎвЂљРЎС“Р С—Р ВµР Р… Р В»Р С‘ Р Р†РЎвЂ№Р В±РЎР‚Р В°Р Р…Р Р…РЎвЂ№Р в„– РЎРѓР С—Р В°Р Р†Р Р…?
        boolean isSelectedValid = false;
        boolean isSelectedBlocked = false;
        boolean isSelectedNoMaterials = false; // РЎвЂ¦Р В°Р В±РЎС“ Р Р…Р Вµ РЎвЂ¦Р Р†Р В°РЎвЂљР В°Р ВµРЎвЂљ Р СР В°РЎвЂљР ВµРЎР‚Р С‘Р В°Р В»Р С•Р Р† Р Т‘Р В»РЎРЏ РЎРѓР С—Р В°Р Р†Р Р…Р В°

        if (!selectedSpawnType.isEmpty()) {
            if (selectedSpawnType.equals("MAIN")) {
                isSelectedValid = true;
            } else if (selectedSpawnType.equals("RALLY")) {
                String myName = this.minecraft.player.getScoreboardName();
                for (AASWorldData.Squad s : ClientData.clientSquads) {
                    if (s.members.contains(myName)) {
                        if (s.rallyPos != null) {
                            if (s.isRallyBlocked) isSelectedBlocked = true;
                            else isSelectedValid = true;
                        }
                        break;
                    }
                }
            } else if (selectedSpawnType.startsWith("HUB:")) {
                String[] parts = selectedSpawnType.split(":");
                if (parts.length == 4) {
                    try {
                        BlockPos reqPos = new BlockPos(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
                        String myTeam = getPlayerTeam().toUpperCase();
                        for (AASWorldData.HubInfo h : ClientData.clientHubs) {
                            if (h.pos.equals(reqPos) && h.team.equalsIgnoreCase(myTeam) && h.constructed) {
                                boolean notEnoughMaterials = ClientData.serverHubSpawnCosts
                                        && h.materials < ClientData.serverHubSpawnCostAmount;

                                if (h.isBlocked) {
                                    isSelectedBlocked = true;
                                } else if (notEnoughMaterials) {
                                    isSelectedNoMaterials = true;
                                } else {
                                    isSelectedValid = true;
                                }
                                break;
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

// Р С›Р В±Р Р…Р С•Р Р†Р В»РЎРЏР ВµР С РЎРѓР С•РЎРѓРЎвЂљР С•РЎРЏР Р…Р С‘Р Вµ Р С”Р Р…Р С•Р С—Р С”Р С‘ DEPLOY
        if (secondsLeft > 0) {
            deployButton.active = false;
            deployButton.setMessage(Component.literal("WAIT " + secondsLeft + "s"));
        } else {
            if (selectedSpawnType.isEmpty()) {
                deployButton.active = false;
                deployButton.setMessage(Component.literal("DEPLOY"));
            } else if (isSelectedBlocked) {
                deployButton.active = false;
                deployButton.setMessage(Component.literal("BLOCKED").withStyle(ChatFormatting.RED));
            } else if (isSelectedNoMaterials) {
                deployButton.active = false;
                deployButton.setMessage(Component.literal("NO MATERIALS").withStyle(ChatFormatting.RED));
            } else if (!isSelectedValid) {
                deployButton.active = false;
                deployButton.setMessage(Component.literal("DESTROYED").withStyle(ChatFormatting.RED));
            } else {
                deployButton.active = true;
                deployButton.setMessage(Component.literal("DEPLOY"));
            }
        }

        // 4. Р С›Р СћР В Р Р‡Р вЂќР В«, Р ТђР вЂўР вЂќР вЂўР В  Р В Р вЂ™Р В«Р вЂР С›Р В  Р СћР С›Р В§Р вЂўР С™
        renderSquadList(gui, mx, my);
        renderTeamHeader(gui);
        renderSpawnSelection(gui, mx, my);

        // 5. Р вЂњР С›Р вЂєР С›Р РЋР С›Р вЂ™Р С’Р Р‡ Р С’Р С™Р СћР ВР вЂ™Р СњР С›Р РЋР СћР В¬ (Р РЋР В»Р ВµР Р†Р В° Р Р† РЎвЂ Р ВµР Р…РЎвЂљРЎР‚Р Вµ)
        renderVoiceActivity(gui);

        // 6. Р В§Р С’Р Сћ
        renderChatArea(gui);

        // 7. Р С™Р СњР С›Р СџР С™Р В Р В Р вЂ™Р ВР вЂќР вЂ“Р вЂўР СћР В« (Deploy, Quit, Р СџР С•Р В»РЎРЏ Р Р†Р Р†Р С•Р Т‘Р В°)
        for (Renderable renderable : this.renderables) {
            renderable.render(gui, mx, my, pt);
        }

        // 8. Р С™Р С›Р СњР СћР вЂўР С™Р РЋР СћР СњР С›Р вЂў Р СљР вЂўР СњР В® (Р СџР С™Р Сљ Р С—Р С• Р С‘Р С–РЎР‚Р С•Р С”РЎС“)
        if (showContextMenu) {
            renderContextMenu(gui, mx, my);
        }

        // 9. Р В­Р В¤Р В¤Р вЂўР С™Р Сћ Р вЂ”Р С’Р СћР Р€Р ТђР С’Р СњР ВР Р‡ (Fade in) РІР‚вЂќ Р В Р С‘РЎРѓРЎС“Р ВµРЎвЂљРЎРѓРЎРЏ Р С—Р С•РЎРѓР В»Р ВµР Т‘Р Р…Р С‘Р С Р С—Р С•Р Р†Р ВµРЎР‚РЎвЂ¦ Р Р†РЎРѓР ВµР С–Р С•
        // Р СњР В°РЎвЂ¦Р С•Р Т‘Р С‘Р С Р Р† AASDeathScreen.java РЎРѓР ВµР С”РЎвЂ Р С‘РЎР‹ "// 9. Р В­Р В¤Р В¤Р вЂўР С™Р Сћ Р вЂ”Р С’Р СћР Р€Р ТђР С’Р СњР ВР Р‡"
        if (ClientData.deathFadeStartTime != 0) {
            long fadeElapsed = System.currentTimeMillis() - ClientData.deathFadeStartTime;
            float alpha = 0.0f;

            if (fadeElapsed < 1000) {
                alpha = 1.0f;
            } else if (fadeElapsed < 2000) {
                alpha = 1.0f - ((fadeElapsed - 1000) / 1000.0f);
            } else {
                ClientData.deathFadeStartTime = 0; // Р вЂ”Р В°Р Р†Р ВµРЎР‚РЎв‚¬Р В°Р ВµР С Р В°Р Р…Р С‘Р СР В°РЎвЂ Р С‘РЎР‹
            }

            if (alpha > 0) {
                int alphaInt = (int)(alpha * 255);

                gui.pose().pushPose();
                // Р СџР ВµРЎР‚Р ВµР Р…Р С•РЎРѓР С‘Р С РЎРѓР В»Р С•Р в„– Р В·Р В°РЎвЂљРЎС“РЎвЂ¦Р В°Р Р…Р С‘РЎРЏ Р Р…Р В° Р С—Р ВµРЎР‚Р ВµР Т‘Р Р…Р С‘Р в„– Р С—Р В»Р В°Р Р… (Z = 1000)
                // Р В­РЎвЂљР С• Р С—Р ВµРЎР‚Р ВµР С”РЎР‚Р С•Р ВµРЎвЂљ РЎвЂ Р С‘РЎвЂћРЎР‚РЎвЂ№ Р С•РЎвЂљРЎР‚РЎРЏР Т‘Р С•Р Р†, РЎвЂљР С‘Р С”Р ВµРЎвЂљРЎвЂ№ Р С‘ Р Р†Р С•Р С•Р В±РЎвЂ°Р Вµ Р Р†РЎРѓРЎвЂ.
                gui.pose().translate(0, 0, 1000);

                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                gui.fill(0, 0, this.width, this.height, (alphaInt << 24) | 0x000000);
                RenderSystem.disableBlend();

                gui.pose().popPose();
            }
        }
    }
    private void renderVoiceActivity(GuiGraphics gui) {
        long now = System.currentTimeMillis();
        // Р РЋРЎвЂљР В°Р Р…Р Т‘Р В°РЎР‚РЎвЂљР Р…Р В°РЎРЏ Р С—Р С•Р В·Р С‘РЎвЂ Р С‘РЎРЏ Р С”Р В°Р С” Р Р† AASOverlay: Р В»Р ВµР Р†РЎвЂ№Р в„– Р С”РЎР‚Р В°Р в„–, РЎвЂ Р ВµР Р…РЎвЂљРЎР‚ Р С—Р С• Р Р†Р ВµРЎР‚РЎвЂљР С‘Р С”Р В°Р В»Р С‘
        int x = 5;
        int y = (this.height / 2) - 40;

        // 1. Р С›РЎвЂљРЎР‚Р С‘РЎРѓР С•Р Р†Р С”Р В° Р В Р В°РЎвЂ Р С‘Р С‘ (Р вЂ“Р ВµР В»РЎвЂљРЎвЂ№Р в„– РЎвЂ Р Р†Р ВµРЎвЂљ)
        for (var entry : ClientData.RADIO_SPEAKERS.entrySet()) {
            if (now - entry.getValue() < 500) {
                renderSpeakerRow(gui, x, y, entry.getKey(), 0xFFFFFF00, RADIO_ICON);
                y += 14; // Р РЋР СР ВµРЎвЂ°Р ВµР Р…Р С‘Р Вµ Р Т‘Р В»РЎРЏ РЎРѓР В»Р ВµР Т‘РЎС“РЎР‹РЎвЂ°Р ВµР в„– РЎРѓРЎвЂљРЎР‚Р С•Р С”Р С‘
            }
        }

        // 2. Р С›РЎвЂљРЎР‚Р С‘РЎРѓР С•Р Р†Р С”Р В° Р С›РЎвЂљРЎР‚РЎРЏР Т‘Р В° (Р вЂ”Р ВµР В»Р ВµР Р…РЎвЂ№Р в„– РЎвЂ Р Р†Р ВµРЎвЂљ)
        for (var entry : ClientData.SQUAD_SPEAKERS.entrySet()) {
            if (now - entry.getValue() < 500) {
                renderSpeakerRow(gui, x, y, entry.getKey(), 0xFF55FF55, VOICE_ICON);
                y += 14;
            }
        }
    }

    // Р вЂ™РЎРѓР С—Р С•Р СР С•Р С–Р В°РЎвЂљР ВµР В»РЎРЉР Р…РЎвЂ№Р в„– Р СР ВµРЎвЂљР С•Р Т‘ (Р ВµРЎРѓР В»Р С‘ Р ВµР С–Р С• Р Р…Р ВµРЎвЂљ, Р Т‘Р С•Р В±Р В°Р Р†РЎРЉ)
    private void renderSpeakerRow(GuiGraphics gui, int x, int y, String name, int color, ResourceLocation icon) {
        int tw = font.width(name) + 15;
        gui.fill(x, y - 2, x + tw + 4, y + 10, 0x80000000);
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(((color >> 16) & 0xFF)/255f, ((color >> 8) & 0xFF)/255f, (color & 0xFF)/255f, 1f);
        gui.blit(icon, x + 3, y, 0, 0, 8, 8, 8, 8);
        RenderSystem.setShaderColor(1, 1, 1, 1);
        gui.drawString(font, name, x + 14, y, color, false);
    }
    private String getSpawnPointUnderMouse(double mouseX, double mouseY) {
        Minecraft mc = Minecraft.getInstance();
        String myName = mc.player.getScoreboardName();
        double bpp = mapRenderer.getBlocksPerPixel();
        double cx = mapRenderer.getCenterX(mc.player);
        double cz = mapRenderer.getCenterZ(mc.player);
        String myTeam = getPlayerTeam().toUpperCase();
        String currentDim = mc.level.dimension().location().toString();

        // 1. Р СџРЎР‚Р С•Р Р†Р ВµРЎР‚Р С”Р В° Р СљР ВµР в„–Р Р…Р В° (Р СћР С›Р вЂєР В¬Р С™Р С› Р РЋР вЂ™Р С›Р вЂўР в„ў Р С™Р С›Р СљР С’Р СњР вЂќР В«)
        BlockPos myMainPos = myTeam.equals("BLUE") ? ClientData.blueSpawns.get(currentDim) : ClientData.redSpawns.get(currentDim);
        if (myMainPos != null && isIconHit(myMainPos, mouseX, mouseY, cx, cz, bpp)) {
            return "MAIN";
        }

        // 2. Р СџРЎР‚Р С•Р Р†Р ВµРЎР‚Р С”Р В° Р В Р В°Р В»Р В»Р С‘Р С”Р В° (Р СћР С•Р В»РЎРЉР С”Р С• РЎРѓР Р†Р С•Р ВµР С–Р С• Р С•РЎвЂљРЎР‚РЎРЏР Т‘Р В°)
        for (AASWorldData.Squad squad : ClientData.clientSquads) {
            if (squad.members.contains(myName) && squad.rallyPos != null) {
                // Р вЂќР С•Р В±Р В°Р Р†Р В»Р ВµР Р…Р В° Р С—РЎР‚Р С•Р Р†Р ВµРЎР‚Р С”Р В° !squad.isRallyBlocked
                if (!squad.isRallyBlocked && isIconHit(squad.rallyPos, mouseX, mouseY, cx, cz, bpp)) return "RALLY";
            }
        }

        // 3. Р СџРЎР‚Р С•Р Р†Р ВµРЎР‚Р С”Р В° Р ТђР С’Р вЂР С•Р Р†
        for (AASWorldData.HubInfo hub : ClientData.clientHubs) {
            // Р вЂќР С•Р В±Р В°Р Р†Р В»Р ВµР Р…Р В° Р С—РЎР‚Р С•Р Р†Р ВµРЎР‚Р С”Р В° !hub.isBlocked
            if (hub.team.equalsIgnoreCase(myTeam) && hub.constructed && !hub.isBlocked) {
                if (isIconHit(hub.pos, mouseX, mouseY, cx, cz, bpp)) {
                    return "HUB:" + hub.pos.getX() + ":" + hub.pos.getY() + ":" + hub.pos.getZ();
                }
            }
        }
        return null;
    }

    private boolean isIconHit(BlockPos pos, double mx, double my, double cx, double cz, double bpp) {
        double dx = (pos.getX() + 0.5 - cx) / bpp;
        double dz = (pos.getZ() + 0.5 - cz) / bpp;
        // Р С™Р С•Р С•РЎР‚Р Т‘Р С‘Р Р…Р В°РЎвЂљРЎвЂ№ РЎвЂ Р ВµР Р…РЎвЂљРЎР‚Р В° Р С”Р В°РЎР‚РЎвЂљРЎвЂ№
        int mapOriginX = this.width - this.height;
        int px = (int) (mapOriginX + (this.height / 2.0) + dx);
        int py = (int) (this.height / 2.0 + dz);

        double distSq = (mx - px) * (mx - px) + (my - py) * (my - py);
        return distSq < 144; // Р В Р В°Р Т‘Р С‘РЎС“РЎРѓ 12 Р С—Р С‘Р С”РЎРѓР ВµР В»Р ВµР в„–
    }

    private void renderTeamHeader(GuiGraphics gui) {
        int startX = SIDEBAR_WIDTH + 10;
        int startY = 10;

        String teamName = getPlayerTeam().toUpperCase();
        int tickets = teamName.contains("BLUE") ? ClientData.BLUE_TICKETS : ClientData.RED_TICKETS;
        String faction = teamName.contains("BLUE") ? ClientData.BLUE_FACTION : ClientData.RED_FACTION;
        String customName = teamName.contains("BLUE") ? ClientData.customBlueName : ClientData.customRedName;

        // Р В¤Р В»Р В°Р С– Р С”Р С•Р СР В°Р Р…Р Т‘РЎвЂ№
        ResourceLocation flagTex = getFlagTexture(faction);
        if (flagTex != null) {
            RenderSystem.enableBlend();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            gui.blit(flagTex, startX, startY, 32, 18, 0, 0, 64, 36, 64, 36);
        }

        // Р СњР В°Р В·Р Р†Р В°Р Р…Р С‘Р Вµ Р С”Р С•Р СР В°Р Р…Р Т‘РЎвЂ№
        gui.drawString(font, customName, startX + 38, startY, teamName.contains("BLUE") ? 0x5555FF : 0xFF5555, true);

        // Р вЂ”Р Р…Р В°РЎвЂЎР С•Р С” РЎвЂљР С‘Р С”Р ВµРЎвЂљР С•Р Р† + Р С”Р С•Р В»Р С‘РЎвЂЎР ВµРЎРѓРЎвЂљР Р†Р С• РЎвЂљР С‘Р С”Р ВµРЎвЂљР С•Р Р† Р С”Р С•Р СР В°Р Р…Р Т‘РЎвЂ№
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        gui.blit(TICKET_ICON, startX + 38, startY + 11, 0, 0, 8, 8, 8, 8);
        gui.drawString(font, String.valueOf(tickets), startX + 50, startY + 11, 0xFFD700, true);
    }

    private void renderSpawnSelection(GuiGraphics gui, int mx, int my) {
        int startX = SIDEBAR_WIDTH + 10;
        int startY = 55;

        gui.drawString(font, "SELECT SPAWN POINT:", startX, startY - 15, 0xFFAA00);

        // Р СљР ВµР в„–Р Р… Р Р…Р С‘Р С”Р С•Р С–Р Т‘Р В° Р Р…Р Вµ Р В±Р В»Р С•Р С”Р С‘РЎР‚РЎС“Р ВµРЎвЂљРЎРѓРЎРЏ
        drawSpawnOption(gui, startX, startY, 110, 24, "MAIN BASE", "MAIN", mx, my, true, false);

        startY += 30;
        // Р СџРЎР‚Р С•Р Р†Р ВµРЎР‚Р С”Р В° РЎР‚Р В°Р В»Р В»Р С‘Р С”Р В°
        boolean rallyExists = hasRally();
        boolean rallyBlocked = isMyRallyBlocked();
        drawSpawnOption(gui, startX, startY, 110, 24, "SQUAD RALLY", "RALLY", mx, my, rallyExists && !rallyBlocked, rallyExists && rallyBlocked);

        startY += 40;
        gui.drawString(font, "AVAILABLE HUBS:", startX, startY - 12, 0xAAAAAA);

        Team team = this.minecraft.player.getTeam();
        if (team != null) {
            String myTeam = team.getName();
            String myDim = this.minecraft.level.dimension().location().toString();
            int hubIdx = 1;
            for (AASWorldData.HubInfo hub : ClientData.clientHubs) {
                if (hub.team.equalsIgnoreCase(myTeam) && hub.constructed && hub.dimension.equals(myDim)) {
                    String id = "HUB:" + hub.pos.getX() + ":" + hub.pos.getY() + ":" + hub.pos.getZ();

                    boolean hubBlocked = hub.isBlocked;
                    boolean canAfford = !ClientData.serverHubSpawnCosts || hub.materials >= ClientData.serverHubSpawnCostAmount;
                    boolean active = !hubBlocked && canAfford;

                    drawSpawnOption(gui, startX, startY, 110, 20, "HUBS " + hubIdx, id, mx, my, active, hubBlocked);
                    startY += 24;
                    hubIdx++;
                }
            }
        }
    }

    private void drawSpawnOption(GuiGraphics gui, int x, int y, int w, int h, String label, String id, int mx, int my, boolean active, boolean isBlocked) {
        boolean hovered = active && mx >= x && mx <= x + w && my >= y && my <= y + h;
        boolean selected = selectedSpawnType.equals(id);

        // Р вЂўРЎРѓР В»Р С‘ Р В·Р В°Р В±Р В»Р С•Р С”Р С‘РЎР‚Р С•Р Р†Р В°Р Р…Р С• РІР‚вЂќ Р Р†РЎРѓР ВµР С–Р Т‘Р В° Р С”РЎР‚Р В°РЎРѓР Р…РЎвЂ№Р в„–, Р С‘Р Р…Р В°РЎвЂЎР Вµ Р С•Р В±РЎвЂ№РЎвЂЎР Р…Р В°РЎРЏ Р В»Р С•Р С–Р С‘Р С”Р В°
        int color = isBlocked ? 0xFFFF5555 : (active ? (selected ? 0xFF55FF55 : (hovered ? 0xFFFFFFFF : 0xBBBBBB)) : 0x555555);
        // Р вЂќР В»РЎРЏ Р В·Р В°Р В±Р В»Р С•Р С”Р С‘РЎР‚Р С•Р Р†Р В°Р Р…Р Р…Р С•Р С–Р С• Р Т‘Р ВµР В»Р В°Р ВµР С Р С—Р С•Р В»РЎС“Р С—РЎР‚Р С•Р В·РЎР‚Р В°РЎвЂЎР Р…РЎвЂ№Р в„– Р С”РЎР‚Р В°РЎРѓР Р…РЎвЂ№Р в„– РЎвЂћР С•Р Р…, РЎвЂЎРЎвЂљР С•Р В±РЎвЂ№ Р С”Р В°Р В·Р В°Р В»Р С•РЎРѓРЎРЉ "Р Р…Р В°Р В¶Р В°РЎвЂљРЎвЂ№Р С"
        int bg = isBlocked ? 0x60FF0000 : (selected ? 0x4455FF55 : (active ? 0x22FFFFFF : 0x11000000));

        String finalLabel = isBlocked ? label + " BLOCKED" : label;

        gui.fill(x, y, x + w, y + h, bg);
        gui.renderOutline(x, y, w, h, color);
        gui.drawCenteredString(font, finalLabel, x + w / 2, y + (h - 8) / 2, color);
    }

    // ===================== Р СљР вЂўР СњР В® Р С›Р СћР В Р Р‡Р вЂќР С›Р вЂ™ =====================

    private boolean isApplyCmdVisible() {
        String myName = this.minecraft.player.getScoreboardName();
        String myTeam = getPlayerTeam().toUpperCase();
        boolean isBlue = myTeam.contains("BLUE");
        int teamCMDId = isBlue ? ClientData.blueCMDId : ClientData.redCMDId;
        boolean myTeamVoteActive = isBlue ? ClientData.blueCmdVoteActive : ClientData.redCmdVoteActive;

        boolean amISquadLeaderAnywhere = false;
        for (AASWorldData.Squad s : ClientData.clientSquads) {
            if (s.leader.equals(myName)) { amISquadLeaderAnywhere = true; break; }
        }
        return amISquadLeaderAnywhere && teamCMDId == -1 && !myTeamVoteActive;
    }

    // Р ВРЎвЂ°Р ВµРЎвЂљ Р С•РЎвЂљРЎР‚РЎРЏР Т‘, Р Р† Р С”Р С•РЎвЂљР С•РЎР‚Р С•Р С РЎРѓР С•РЎРѓРЎвЂљР С•Р С‘РЎвЂљ РЎС“Р С”Р В°Р В·Р В°Р Р…Р Р…РЎвЂ№Р в„– Р С‘Р С–РЎР‚Р С•Р С”, РЎРѓРЎР‚Р ВµР Т‘Р С‘ Р С”Р В»Р С‘Р ВµР Р…РЎвЂљРЎРѓР С”Р С‘РЎвЂ¦ Р Т‘Р В°Р Р…Р Р…РЎвЂ№РЎвЂ¦
    private AASWorldData.Squad getMySquad(String playerName) {
        for (AASWorldData.Squad s : ClientData.clientSquads) {
            if (s.members.contains(playerName)) return s;
        }
        return null;
    }

    private List<AASWorldData.Squad> getMyTeamSquadsSorted() {
        String myTeam = getPlayerTeam().toUpperCase();
        String myDim = this.minecraft.level.dimension().location().toString();
        boolean isBlue = myTeam.contains("BLUE");
        int teamCMDId = isBlue ? ClientData.blueCMDId : ClientData.redCMDId;

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

    private List<String> getSortedMembers(AASWorldData.Squad squad) {
        List<String> sorted = new ArrayList<>();
        if (!squad.leader.isEmpty() && squad.members.contains(squad.leader)) sorted.add(squad.leader);

        for (String m : squad.members) {
            if (!m.equals(squad.leader) && !squad.bravoMembers.contains(m) && !squad.charlieMembers.contains(m)) {
                sorted.add(m);
            }
        }

        if (!squad.bravoLeader.isEmpty() && squad.members.contains(squad.bravoLeader)) sorted.add(squad.bravoLeader);
        for (String m : squad.bravoMembers) {
            if (!m.equals(squad.bravoLeader) && squad.members.contains(m)) sorted.add(m);
        }

        if (!squad.charlieLeader.isEmpty() && squad.members.contains(squad.charlieLeader)) sorted.add(squad.charlieLeader);
        for (String m : squad.charlieMembers) {
            if (!m.equals(squad.charlieLeader) && squad.members.contains(m)) sorted.add(m);
        }

        return sorted;
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
        boolean amIInSquad = isPlayerInSquad();

        boolean isBlue = getPlayerTeam().toUpperCase().contains("BLUE");
        int teamCMDId = isBlue ? ClientData.blueCMDId : ClientData.redCMDId;

        int currentY = isApplyCmdVisible() ? 35 : 10;

        List<AASWorldData.Squad> myTeamSquads = getMyTeamSquadsSorted();

        int index = 1;
        for (AASWorldData.Squad squad : myTeamSquads) {
            boolean isMySquad = squad.members.contains(myName);
            boolean amILeader = squad.leader.equals(myName);
            boolean isExpanded = expandedSquads.contains(squad.id);

            boolean isCMD = (squad.id == teamCMDId && teamCMDId != -1);
            String prefix = isCMD ? "[CMD] " : "";
            int squadNameColor = isCMD ? 0xFF55FF55 : 0xFFFFD700;

            // Р В¦Р Р†Р ВµРЎвЂљР Р…Р С•Р в„– Р С”РЎР‚РЎС“Р В¶Р С•Р С” РЎРѓ Р Р…Р С•Р СР ВµРЎР‚Р С•Р С Р С•РЎвЂљРЎР‚РЎРЏР Т‘Р В°:
            // - Р В¦Р СљР вЂќ РІР‚вЂќ РЎРѓР Р†Р С•Р в„– РЎвЂ Р Р†Р ВµРЎвЂљ (Р В·Р ВµР В»РЎвЂР Р…РЎвЂ№Р в„–)
            // - РЎвЂљР Р†Р С•Р в„– Р С•РЎвЂљРЎР‚РЎРЏР Т‘ (Р Р…Р Вµ Р В¦Р СљР вЂќ) РІР‚вЂќ РЎРЏРЎР‚Р С”Р С•-Р В·Р ВµР В»РЎвЂР Р…РЎвЂ№Р в„–
            // - Р С•РЎРѓРЎвЂљР В°Р В»РЎРЉР Р…РЎвЂ№Р Вµ (РЎвЂЎРЎС“Р В¶Р С‘Р Вµ) Р С•РЎвЂљРЎР‚РЎРЏР Т‘РЎвЂ№ РІР‚вЂќ РЎРѓР С‘Р Р…Р С‘Р в„–
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
            } else if (!amIInSquad) {
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
                    if (!kName.equals("Unassigned") && !kName.isEmpty()) {
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

    private void handleSquadListClicks(double mouseX, double mouseY) {
        String myName = this.minecraft.player.getScoreboardName();
        boolean amIInSquad = isPlayerInSquad();

        int currentY = isApplyCmdVisible() ? 35 : 10;
        List<AASWorldData.Squad> myTeamSquads = getMyTeamSquadsSorted();

        for (AASWorldData.Squad squad : myTeamSquads) {
            boolean isMySquad = squad.members.contains(myName);
            boolean amILeader = squad.leader.equals(myName);
            boolean isExpanded = expandedSquads.contains(squad.id);

            String actionText = "";
            if (isMySquad) actionText = "LEAVE";
            else if (!amIInSquad) {
                if (squad.isLocked) actionText = "LOCKED";
                else if (squad.members.size() >= 9) actionText = "FULL";
                else actionText = "JOIN";
            }

            int actionWidth = actionText.isEmpty() ? 0 : this.font.width(actionText);
            int actionX = actionWidth > 0 ? (SIDEBAR_WIDTH - actionWidth - 10) : 0;

            int arrowX = (actionX > 0 ? actionX : SIDEBAR_WIDTH - 10) - 12;
            int lockX = arrowX - 12;

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

            // РџСЂРѕРїСѓСЃРєР°РµРј Р·Р°РіРѕР»РѕРІРѕРє (+ СЃС‚СЂРѕРєСѓ СЃ РёРјРµРЅРµРј Р»РёРґРµСЂР°, РµСЃР»Рё РѕРЅР° РµСЃС‚СЊ)
            currentY += squad.leader.isEmpty() ? 12 : 21;

            if (isExpanded) {
                List<String> sortedMembers = getSortedMembers(squad);
                for (String member : sortedMembers) {
                    if (isMySquad && member.equals(myName)) {
                        if (mouseX >= 30 && mouseX <= 40 && mouseY >= currentY && mouseY <= currentY + 10) {
                            PacketHandler.INSTANCE.sendToServer(new PacketRequestKitMenu());
                            playClickSound();
                            return;
                        }
                    }
                    currentY += 12;
                }
                currentY += 4;
            }
            currentY += 4;
        }
    }

    private void renderContextMenu(GuiGraphics gui, int mx, int my) {
        String myName = this.minecraft.player.getScoreboardName();
        AASWorldData.Squad s = null;
        for (AASWorldData.Squad sq : ClientData.clientSquads) { if (sq.id == contextTargetSquadId) s = sq; }
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

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        // Меню удаления метки на карте (если открыто - забирает клик на себя)
        if (mapRenderer.isMarkerDeleteMenuOpen()) {
            if (mapRenderer.handleMarkerDeleteMenuClick(mx, my, btn)) return true;
        }

        // 1. Р С™Р С›Р СњР СћР вЂўР С™Р РЋР СћР СњР С›Р вЂў Р СљР вЂўР СњР В® (Р вЂ™РЎвЂ№Р В±Р С•РЎР‚ Р Т‘Р ВµР в„–РЎРѓРЎвЂљР Р†Р С‘РЎРЏ: Р С”Р С‘Р С”, Р С—РЎР‚Р С•Р СР С•РЎС“РЎвЂљ Р С‘ РЎвЂљ.Р Т‘.)
        // Р РЋР В°Р СРЎвЂ№Р в„– Р Р†РЎвЂ№РЎРѓР С•Р С”Р С‘Р в„– Р С—РЎР‚Р С‘Р С•РЎР‚Р С‘РЎвЂљР ВµРЎвЂљ: Р ВµРЎРѓР В»Р С‘ Р СР ВµР Р…РЎР‹ Р С•РЎвЂљР С”РЎР‚РЎвЂ№РЎвЂљР С•, Р С”Р В»Р С‘Р С” Р С•Р В±РЎР‚Р В°Р В±Р В°РЎвЂљРЎвЂ№Р Р†Р В°Р ВµРЎвЂљРЎРѓРЎРЏ РЎвЂљР С•Р В»РЎРЉР С”Р С• Р Р† Р Р…Р ВµР С.
        if (showContextMenu) {
            boolean optionClicked = false;
            if (btn == 0) { // Р вЂєР С™Р Сљ Р С—Р С• Р С—РЎС“Р Р…Р С”РЎвЂљРЎС“ Р СР ВµР Р…РЎР‹
                optionClicked = processContextMenuClick(mx, my);
            }
            showContextMenu = false;
            // FIX: Р С—Р С•Р С–Р В»Р С•РЎвЂ°Р В°Р ВµР С Р С”Р В»Р С‘Р С” РЎвЂљР С•Р В»РЎРЉР С”Р С• Р ВµРЎРѓР В»Р С‘ РЎР‚Р ВµР В°Р В»РЎРЉР Р…Р С• Р Р…Р В°Р В¶Р В°Р В»Р С‘ Р Р…Р В° Р С—РЎС“Р Р…Р С”РЎвЂљ Р СР ВµР Р…РЎР‹ (Р вЂєР С™Р Сљ Р С—Р С• Р С•Р С—РЎвЂ Р С‘Р С‘),
            // Р В»Р С‘Р В±Р С• РЎРЊРЎвЂљР С• Р В±РЎвЂ№Р В» Р вЂєР С™Р Сљ Р СР С‘Р СР С• Р СР ВµР Р…РЎР‹ (РЎвЂЎРЎвЂљР С•Р В±РЎвЂ№ Р Р…Р Вµ РЎвЂљРЎР‚Р С‘Р С–Р С–Р ВµРЎР‚Р С‘РЎвЂљРЎРЉ Р Р†Р С‘Р Т‘Р В¶Р ВµРЎвЂљРЎвЂ№ Р С—Р С•Р Т‘ Р СР ВµР Р…РЎР‹).
            // Р СџР С™Р Сљ Р СР С‘Р СР С• Р СР ВµР Р…РЎР‹ (Р Р…Р В°Р С—РЎР‚Р С‘Р СР ВµРЎР‚, Р С—Р С• Р Р…Р С•Р Р†Р С•Р СРЎС“ Р С•РЎвЂљРЎР‚РЎРЏР Т‘РЎС“/Р С‘Р С–РЎР‚Р С•Р С”РЎС“) Р СњР вЂў Р С—Р С•Р С–Р В»Р С•РЎвЂ°Р В°Р ВµРЎвЂљРЎРѓРЎРЏ РІР‚вЂќ
            // РЎРѓР С•Р В±РЎвЂ№РЎвЂљР С‘Р Вµ Р С—РЎР‚Р С•Р Р†Р В°Р В»Р С‘Р Р†Р В°Р ВµРЎвЂљРЎРѓРЎРЏ Р Р…Р С‘Р В¶Р Вµ Р С‘ РЎРѓРЎР‚Р В°Р В·РЎС“ Р С•РЎвЂљР С”РЎР‚РЎвЂ№Р Р†Р В°Р ВµРЎвЂљ Р Р…Р С•Р Р†Р С•Р Вµ Р С”Р С•Р Р…РЎвЂљР ВµР С”РЎРѓРЎвЂљР Р…Р С•Р Вµ Р СР ВµР Р…РЎР‹
            // Р В·Р В° РЎвЂљР С•РЎвЂљ Р В¶Р Вµ Р С”Р В»Р С‘Р С”, Р В±Р ВµР В· Р Р…Р ВµР С•Р В±РЎвЂ¦Р С•Р Т‘Р С‘Р СР С•РЎРѓРЎвЂљР С‘ Р С”Р В»Р С‘Р С”Р В°РЎвЂљРЎРЉ Р Т‘Р Р†Р В°Р В¶Р Т‘РЎвЂ№.
            if (optionClicked || btn == 0) {
                return true;
            }
        }

        // 2. Р РЋР СћР С’Р СњР вЂќР С’Р В Р СћР СњР В«Р вЂў Р вЂ™Р ВР вЂќР вЂ“Р вЂўР СћР В« (Р С™Р Р…Р С•Р С—Р С”Р С‘ DEPLOY, QUIT, Р СџР С•Р В»РЎРЏ Р Р†Р Р†Р С•Р Т‘Р В° РЎвЂЎР В°РЎвЂљР В° Р С‘ Р Р…Р В°Р В·Р Р†Р В°Р Р…Р С‘РЎРЏ Р С•РЎвЂљРЎР‚РЎРЏР Т‘Р В°)
        if (super.mouseClicked(mx, my, btn)) {
            // Р вЂўРЎРѓР В»Р С‘ Р С”Р В»Р С‘Р С”Р Р…РЎС“Р В»Р С‘ Р С—Р С• РЎвЂЎР ВµР СРЎС“-РЎвЂљР С• РЎРѓРЎвЂљР В°Р Р…Р Т‘Р В°РЎР‚РЎвЂљР Р…Р С•Р СРЎС“, Р С•Р В±Р Р…Р С•Р Р†Р В»РЎРЏР ВµР С ID Р Р†РЎвЂ№Р В±РЎР‚Р В°Р Р…Р Р…Р С•Р С–Р С• РЎРѓР С—Р В°Р Р†Р Р…Р В° Р Т‘Р В»РЎРЏ РЎР‚Р ВµР Р…Р Т‘Р ВµРЎР‚Р В°
            mapRenderer.selectedSpawnId = this.selectedSpawnType;
            return true;
        }

        // 3. Р РЋР СџР ВР РЋР С›Р С™ Р С›Р СћР В Р Р‡Р вЂќР С›Р вЂ™ Р В Р ВР вЂњР В Р С›Р С™Р С›Р вЂ™ (Р вЂ™РЎРѓРЎРЏ Р В»Р ВµР Р†Р В°РЎРЏ Р С—Р В°Р Р…Р ВµР В»РЎРЉ)
        //SIDEBAR_WIDTH = 195. Р С›Р С–РЎР‚Р В°Р Р…Р С‘РЎвЂЎР С‘Р Р†Р В°Р ВµР С Р С•Р В±Р В»Р В°РЎРѓРЎвЂљРЎРЉ, РЎвЂЎРЎвЂљР С•Р В±РЎвЂ№ Р Р…Р Вµ Р С”Р В»Р С‘Р С”Р В°РЎвЂљРЎРЉ РЎРѓР С”Р Р†Р С•Р В·РЎРЉ UI Р Р† Р С”Р В°РЎР‚РЎвЂљРЎС“.
        if (mx < SIDEBAR_WIDTH && my < (this.height - 100)) {
            // Р вЂ™РЎвЂ№Р В·РЎвЂ№Р Р†Р В°Р ВµР С Р ВµР Т‘Р С‘Р Р…РЎвЂ№Р в„– Р С•Р В±РЎР‚Р В°Р В±Р С•РЎвЂљРЎвЂЎР С‘Р С” Р Т‘Р В»РЎРЏ Р вЂєР С™Р Сљ Р С‘ Р СџР С™Р Сљ
            handleSquadListInteraction(mx, my, btn);
            return true;
        }

        // 4. Р С™Р СњР С›Р СџР С™Р В Р вЂ™Р В«Р вЂР С›Р В Р С’ Р РЋР СџР С’Р вЂ™Р СњР С’ (Р В¦Р ВµР Р…РЎвЂљРЎР‚Р В°Р В»РЎРЉР Р…Р В°РЎРЏ Р С—Р В°Р Р…Р ВµР В»РЎРЉ UI)
        if (mx >= SIDEBAR_WIDTH + 10 && mx <= SIDEBAR_WIDTH + 130) {
            if (btn == 0) { // Р СћР С•Р В»РЎРЉР С”Р С• Р вЂєР С™Р Сљ
                if (handleSpawnButtons(mx, my)) return true;
            }
        }

        // 5. Р С™Р С’Р В Р СћР С’ (Р ВР С”Р С•Р Р…Р С”Р С‘ Р С‘ Р С—Р ВµРЎР‚Р ВµРЎвЂљР В°РЎРѓР С”Р С‘Р Р†Р В°Р Р…Р С‘Р Вµ) РІР‚вЂќ Р РЋР В°Р СРЎвЂ№Р в„– Р Р…Р С‘Р В·Р С”Р С‘Р в„– Р С—РЎР‚Р С‘Р С•РЎР‚Р С‘РЎвЂљР ВµРЎвЂљ
        if (mapRenderer.isMouseOver(mx, my)) {
            if (btn == 0) { // Р вЂєР С™Р Сљ: Р вЂ™РЎвЂ№Р В±Р С•РЎР‚ РЎвЂљР С•РЎвЂЎР С”Р С‘ РЎРѓР С—Р В°Р Р†Р Р…Р В° Р С—РЎР‚РЎРЏР СР С• Р Р…Р В° Р С”Р В°РЎР‚РЎвЂљР Вµ
                String clickedSpawn = getSpawnPointUnderMouse(mx, my);
                if (clickedSpawn != null) {
                    this.selectedSpawnType = clickedSpawn;
                    this.mapRenderer.selectedSpawnId = clickedSpawn;
                    playClickSound();
                    return true;
                }
            }

            // Р СџР С™Р Сљ Р С—Р С• Р С”Р В°РЎР‚РЎвЂљР Вµ: Р С›РЎвЂљР С”РЎР‚РЎвЂ№РЎвЂљР С‘Р Вµ РЎвЂљР В°Р С”РЎвЂљР С‘РЎвЂЎР ВµРЎРѓР С”Р С‘РЎвЂ¦ Р СР ВµРЎвЂљР С•Р С” (Р Т‘Р В»РЎРЏ SL/FTL)
            if (btn == 1) {
                handleMapRightClick(mx, my);
                return true;
            }

            // Р С›Р В±РЎР‚Р В°Р В±Р С•РЎвЂљР С”Р В° Р С—Р ВµРЎР‚Р ВµРЎвЂљР В°РЎРѓР С”Р С‘Р Р†Р В°Р Р…Р С‘РЎРЏ Р С‘ Р В·РЎС“Р СР В° РЎРѓР В°Р СР С•Р в„– Р С”Р В°РЎР‚РЎвЂљР С•Р в„–
            return mapRenderer.mouseClicked(mx, my, btn);
        }

        return false;
    }

    // Р вЂўР Т‘Р С‘Р Р…Р В°РЎРЏ Р В»Р С•Р С–Р С‘Р С”Р В° Р Р†Р В·Р В°Р С‘Р СР С•Р Т‘Р ВµР в„–РЎРѓРЎвЂљР Р†Р С‘РЎРЏ РЎРѓР С• РЎРѓР С—Р С‘РЎРѓР С”Р С•Р С Р С•РЎвЂљРЎР‚РЎРЏР Т‘Р С•Р Р† (Р вЂєР С™Р Сљ Р С‘ Р СџР С™Р Сљ)
    private void handleSquadListInteraction(double mx, double my, int btn) {
        String myName = this.minecraft.player.getScoreboardName();
        boolean amIInSquad = isPlayerInSquad();
        String myTeam = getPlayerTeam().toUpperCase();
        boolean isBlue = myTeam.contains("BLUE");
        int teamCMDId = isBlue ? ClientData.blueCMDId : ClientData.redCMDId;
        // Р СџРЎР‚Р С•Р Р†Р ВµРЎР‚Р С”Р В°: РЎРЏ Р С™Р С•Р СР В°Р Р…Р Т‘Р С‘РЎР‚?
        // Р вЂ™Р С’Р вЂ“Р СњР С›: РЎРѓРЎвЂЎР С‘РЎвЂљР В°Р ВµР С РЎвЂЎР ВµРЎР‚Р ВµР В· ClientData.clientSquads (РЎРѓР С‘Р Р…РЎвЂ¦РЎР‚Р С•Р Р…Р С‘Р В·Р С‘РЎР‚РЎС“Р ВµРЎвЂљРЎРѓРЎРЏ PacketSyncSquads),
        // Р В° Р СњР вЂў РЎвЂЎР ВµРЎР‚Р ВµР В· player.getPersistentData() РІР‚вЂќ РЎРЊРЎвЂљР С‘ РЎвЂљР ВµР С–Р С‘ Р Р†РЎвЂ№РЎРѓРЎвЂљР В°Р Р†Р В»РЎРЏРЎР‹РЎвЂљРЎРѓРЎРЏ РЎвЂљР С•Р В»РЎРЉР С”Р С• Р Р…Р В° РЎРѓР ВµРЎР‚Р Р†Р ВµРЎР‚Р Вµ
        // Р С‘ Р Р…Р Вµ РЎР‚Р В°РЎРѓРЎРѓРЎвЂ№Р В»Р В°РЎР‹РЎвЂљРЎРѓРЎРЏ Р С”Р В»Р С‘Р ВµР Р…РЎвЂљРЎС“ Р С—Р С• РЎРѓР ВµРЎвЂљР С‘, Р С—Р С•РЎРЊРЎвЂљР С•Р СРЎС“ Р Р…Р В° РЎР‚Р ВµР В°Р В»РЎРЉР Р…Р С•Р С dedicated-РЎРѓР ВµРЎР‚Р Р†Р ВµРЎР‚Р Вµ
        // (Р Р…Р Вµ singleplayer) Р С—РЎР‚Р С•Р Р†Р ВµРЎР‚Р С”Р В° Р Р†РЎРѓР ВµР С–Р Т‘Р В° Р В±РЎвЂ№Р В»Р В° false Р С‘ Р СР ВµР Р…РЎР‹ Р С”Р С•Р СР В°Р Р…Р Т‘Р С‘РЎР‚Р В° Р Р…Р Вµ Р С—Р С•РЎРЏР Р†Р В»РЎРЏР В»Р С•РЎРѓРЎРЉ.
        AASWorldData.Squad myOwnSquadForCmdCheck = getMySquad(myName);
        boolean amICommander = (amIInSquad && myOwnSquadForCmdCheck != null
                && myOwnSquadForCmdCheck.id == teamCMDId
                && myOwnSquadForCmdCheck.leader.equals(myName));

        int currentY = isApplyCmdVisible() ? 35 : 10;
        List<AASWorldData.Squad> squads = getMyTeamSquadsSorted();

        for (AASWorldData.Squad squad : squads) {
            boolean isMySquad = squad.members.contains(myName);
            boolean amILeader = squad.leader.equals(myName);
            boolean isExpanded = expandedSquads.contains(squad.id);

            // Р›РљРњ РїРѕ РЅРёРєСѓ Р»РёРґРµСЂР° РїРѕРґ РЅР°Р·РІР°РЅРёРµРј РѕС‚СЂСЏРґР°: РІС‹РґРµР»РёС‚СЊ/СЃРЅСЏС‚СЊ РІС‹РґРµР»РµРЅРёРµ РЅР° РєР°СЂС‚Рµ
            if (btn == 0 && !squad.leader.isEmpty()
                    && mx >= 24 && mx <= 24 + this.font.width(squad.leader)
                    && my >= currentY + 9 && my < currentY + 18) {
                ClientData.toggleHighlight(squad.leader);
                playClickSound();
                return;
            }

            // --- Р С™Р вЂєР ВР С™ Р СџР С› Р РЃР С’Р СџР С™Р вЂў Р С›Р СћР В Р Р‡Р вЂќР С’ (Р ВР СРЎРЏ, Join, Leave, Lock, Expand) ---
            if (my >= currentY && my <= currentY + 11) {
                if (btn == 1) { // Р СџР С™Р Сљ Р С—Р С• РЎв‚¬Р В°Р С—Р С”Р Вµ РІР‚вЂќ Р СР ВµР Р…РЎР‹ Р С”Р С•Р СР В°Р Р…Р Т‘Р С‘РЎР‚Р В° (Disband Squad)
                    if (amICommander) {
                        this.contextTargetSquadId = squad.id;
                        this.contextTargetIsSquad = true;
                        this.contextMenuX = (int) mx;
                        this.contextMenuY = (int) my;
                        this.showContextMenu = true;
                        playClickSound();
                    }
                    return;
                }
                if (btn == 0) { // Р вЂєР С™Р Сљ

                    // Р вЂ™Р С’Р вЂ“Р СњР С›: РЎвЂљР В° Р В¶Р Вµ РЎРѓР В°Р СР В°РЎРЏ Р В»Р С•Р С–Р С‘Р С”Р В° Р Р†РЎвЂ№РЎвЂЎР С‘РЎРѓР В»Р ВµР Р…Р С‘РЎРЏ actionText/actionX/arrowX/lockX,
                    // РЎвЂЎРЎвЂљР С• Р С‘ Р Р† renderSquadList() РІР‚вЂќ РЎвЂЎРЎвЂљР С•Р В±РЎвЂ№ РЎвЂ¦Р С‘РЎвЂљР В±Р С•Р С”РЎРѓ Р Р†РЎРѓР ВµР С–Р Т‘Р В° РЎРѓР С•Р Р†Р С—Р В°Р Т‘Р В°Р В» РЎРѓ Р С”Р В°РЎР‚РЎвЂљР С‘Р Р…Р С”Р С•Р в„–,
                    // Р Р…Р ВµР В·Р В°Р Р†Р С‘РЎРѓР С‘Р СР С• Р С•РЎвЂљ РЎР‚Р ВµР В°Р В»РЎРЉР Р…Р С•Р в„– РЎв‚¬Р С‘РЎР‚Р С‘Р Р…РЎвЂ№ РЎРѓР В»Р С•Р Р†Р В° JOIN/LEAVE/LOCKED/FULL.
                    String actionText = "";
                    boolean clickable = true;
                    if (isMySquad) {
                        actionText = "LEAVE";
                    } else if (!amIInSquad) {
                        if (squad.isLocked) { actionText = "LOCKED"; clickable = false; }
                        else if (squad.members.size() >= 9) { actionText = "FULL"; clickable = false; }
                        else { actionText = "JOIN"; }
                    }

                    int actionWidth = actionText.isEmpty() ? 0 : this.font.width(actionText);
                    int actionX = actionText.isEmpty() ? 0 : (SIDEBAR_WIDTH - actionWidth - 10);

                    // Р Р…Р ВµР В±Р С•Р В»РЎРЉРЎв‚¬Р С•Р в„– Р В·Р В°Р С—Р В°РЎРѓ Р Р† 2px Р С—Р С• Р С”РЎР‚Р В°РЎРЏР С РЎвЂљР ВµР С”РЎРѓРЎвЂљР В° Р Т‘Р В»РЎРЏ РЎС“Р Т‘Р С•Р В±РЎРѓРЎвЂљР Р†Р В° Р С”Р В»Р С‘Р С”Р В°
                    boolean clickedAction = !actionText.isEmpty() && mx >= actionX - 2 && mx <= actionX + actionWidth + 2;

                    if (clickedAction) { // Р С™Р В»Р С‘Р С” Р С—Р С• РЎвЂљР ВµР С”РЎРѓРЎвЂљРЎС“ JOIN/LEAVE
                        if (isMySquad) {
                            PacketHandler.INSTANCE.sendToServer(new PacketSquadAction(2, squad.id, ""));
                            this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(com.example.aas.sound.ModSounds.SQUAD_LEAVE.get(), 1.0F));
                        } else if (clickable) {
                            PacketHandler.INSTANCE.sendToServer(new PacketSquadAction(1, squad.id, ""));
                            this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(com.example.aas.sound.ModSounds.SQUAD_JOIN.get(), 1.0F));
                        }
                    } else { // Р С™Р В»Р С‘Р С” Р С—Р С• Р С•РЎРѓРЎвЂљР В°Р В»РЎРЉР Р…Р С•Р в„– РЎвЂЎР В°РЎРѓРЎвЂљР С‘ (РЎРѓРЎвЂљРЎР‚Р ВµР В»Р С”Р В° РЎР‚Р В°Р В·Р Р†Р ВµРЎР‚Р Р…РЎС“РЎвЂљРЎРЉ/РЎРѓР Р†Р ВµРЎР‚Р Р…РЎС“РЎвЂљРЎРЉ, Р В·Р В°Р СР С•Р С”)
                        int arrowX = (actionX > 0 ? actionX : SIDEBAR_WIDTH - 10) - 12;
                        int lockX = arrowX - 12;

                        if (mx >= arrowX - 3 && mx <= arrowX + 11) {
                            if (isExpanded) expandedSquads.remove(squad.id);
                            else expandedSquads.add(squad.id);
                        } else if (amILeader && mx >= lockX - 3 && mx <= lockX + 11) {
                            PacketHandler.INSTANCE.sendToServer(new PacketSquadAction(5, squad.id, ""));
                        }
                    }
                    playClickSound();
                }
                return;
            }

            // РџСЂРѕРїСѓСЃРєР°РµРј Р·Р°РіРѕР»РѕРІРѕРє (+ СЃС‚СЂРѕРєСѓ СЃ РёРјРµРЅРµРј Р»РёРґРµСЂР°, РµСЃР»Рё РѕРЅР° РµСЃС‚СЊ)
            currentY += squad.leader.isEmpty() ? 12 : 21;

            // --- Р С™Р вЂєР ВР С™ Р СџР С› Р ВР вЂњР В Р С›Р С™Р С’Р Сљ Р вЂ™Р СњР Р€Р СћР В Р В ---
            if (isExpanded) {
                for (String member : getSortedMembers(squad)) {
                    if (my >= currentY && my <= currentY + 11) {
                        if (btn == 1) { // Р СџР С™Р Сљ - Р С›Р СћР С™Р В Р В«Р СћР В¬ Р СљР вЂўР СњР В® Р Р€Р СџР В Р С’Р вЂ™Р вЂєР вЂўР СњР ВР Р‡
                            boolean amFTL = squad.bravoLeader.equals(myName) || squad.charlieLeader.equals(myName);
                            if ((amILeader || amFTL) && isMySquad && !member.equals(myName)) {
                                this.contextTargetPlayer = member;
                                this.contextTargetSquadId = squad.id;
                                this.contextTargetIsSquad = false;
                                this.showContextMenu = true;
                                this.contextMenuX = (int) mx;
                                this.contextMenuY = (int) my;
                                playClickSound();
                            }
                        }
                        else if (btn == 0) { // Р вЂєР С™Р Сљ - Р С™Р СњР С›Р СџР С™Р С’ "K" (Р С™Р ВР Сћ), Р С”Р Р…Р С•Р С—Р С”Р В° РЎР‚Р ВµР Р…Р Т‘Р ВµРЎР‚Р С‘РЎвЂљРЎРѓРЎРЏ Р Р† 30..40
                            if (isMySquad && member.equals(myName) && mx >= 30 && mx <= 40) {
                                PacketHandler.INSTANCE.sendToServer(new PacketRequestKitMenu());
                                playClickSound();
                            } else if (isOverMemberNick(member, member.equals(myName), mx)) {
                                // Р›РљРњ РїРѕ РЅРёРєСѓ РёРіСЂРѕРєР°: РІС‹РґРµР»РёС‚СЊ/СЃРЅСЏС‚СЊ РІС‹РґРµР»РµРЅРёРµ РЅР° РєР°СЂС‚Рµ
                                ClientData.toggleHighlight(member);
                                playClickSound();
                            }
                        }
                        return;
                    }
                    currentY += 12;
                }
                currentY += 4;
            }
            currentY += 4;
        }
    }

    // Р С›Р В±РЎР‚Р В°Р В±Р С•РЎвЂљР С”Р В° Р Р†РЎвЂ№Р В±Р С•РЎР‚Р В° Р С—РЎС“Р Р…Р С”РЎвЂљР В° Р Р† Р С”Р С•Р Р…РЎвЂљР ВµР С”РЎРѓРЎвЂљР Р…Р С•Р С Р СР ВµР Р…РЎР‹ (Р СџРЎР‚Р С•Р СР С•РЎС“РЎвЂљ, Р С™Р С‘Р С” Р С‘ РЎвЂљ.Р Т‘.)
    // FIX: РЎвЂљР ВµР С—Р ВµРЎР‚РЎРЉ Р Р†Р С•Р В·Р Р†РЎР‚Р В°РЎвЂ°Р В°Р ВµРЎвЂљ boolean РІР‚вЂќ Р В±РЎвЂ№Р В»Р С• Р В»Р С‘ РЎР‚Р ВµР В°Р В»РЎРЉР Р…Р С• Р Р†РЎвЂ№Р С—Р С•Р В»Р Р…Р ВµР Р…Р С• Р С”Р В°Р С”Р С•Р Вµ-РЎвЂљР С• Р Т‘Р ВµР в„–РЎРѓРЎвЂљР Р†Р С‘Р Вµ
    // (Р С”Р В»Р С‘Р С” Р С—РЎР‚Р С‘РЎв‚¬Р ВµР В»РЎРѓРЎРЏ РЎвЂљР С•РЎвЂЎР Р…Р С• Р С—Р С• Р С—РЎС“Р Р…Р С”РЎвЂљРЎС“ Р СР ВµР Р…РЎР‹), РЎвЂЎРЎвЂљР С•Р В±РЎвЂ№ Р Р†РЎвЂ№Р В·РЎвЂ№Р Р†Р В°РЎР‹РЎвЂ°Р С‘Р в„– mouseClicked() Р В·Р Р…Р В°Р В»,
    // Р Р…РЎС“Р В¶Р Р…Р С• Р В»Р С‘ Р С—Р С•Р С–Р В»Р С•РЎвЂ°Р В°РЎвЂљРЎРЉ Р С”Р В»Р С‘Р С” Р С‘Р В»Р С‘ Р Т‘Р В°РЎвЂљРЎРЉ Р ВµР СРЎС“ Р С—РЎР‚Р С•Р Р†Р В°Р В»Р С‘РЎвЂљРЎРЉРЎРѓРЎРЏ Р Т‘Р В°Р В»РЎРЉРЎв‚¬Р Вµ.
    private boolean processContextMenuClick(double mx, double my) {
        AASWorldData.Squad s = null;
        for (AASWorldData.Squad sq : ClientData.clientSquads) {
            if (sq.id == contextTargetSquadId) { s = sq; break; }
        }
        if (s == null) return false;

        String myName = this.minecraft.player.getScoreboardName();
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
                    options.add("Set FTL Bravo"); options.add("Set FTL Charlie");
                }
                options.add("Add to Bravo"); options.add("Add to Charlie");
                options.add("Remove from FT"); options.add("Kick from Squad");
            } else if (amIBravoFTL) {
                if (!s.leader.equals(contextTargetPlayer) && !s.charlieLeader.equals(contextTargetPlayer)) {
                    options.add("Pass FTL Bravo");
                }
                options.add("Add to Bravo"); options.add("Remove from FT");
            } else if (amICharlieFTL) {
                if (!s.leader.equals(contextTargetPlayer) && !s.bravoLeader.equals(contextTargetPlayer)) {
                    options.add("Pass FTL Charlie");
                }
                options.add("Add to Charlie"); options.add("Remove from FT");
            }
        }

        int w = ClientData.squadMenuWidth(this.font, options);
        int h = options.size() * 12 + 4;

        if (mx >= contextMenuX && mx <= contextMenuX + w && my >= contextMenuY && my <= contextMenuY + h) {
            int clickedIdx = (int) (my - contextMenuY - 2) / 12;
            if (clickedIdx >= 0 && clickedIdx < options.size()) {
                String opt = options.get(clickedIdx);
                if (contextTargetIsSquad) {
                    if (opt.equals("Disband Squad")) {
                        PacketHandler.INSTANCE.sendToServer(new PacketSquadAction(11, s.id, ""));
                    }
                } else {
                    if (opt.equals("Promote to SL")) PacketHandler.INSTANCE.sendToServer(new PacketSquadAction(4, s.id, contextTargetPlayer));
                    else if (opt.equals("Set FTL Bravo") || opt.equals("Pass FTL Bravo")) PacketHandler.INSTANCE.sendToServer(new PacketSquadAction(6, s.id, contextTargetPlayer));
                    else if (opt.equals("Set FTL Charlie") || opt.equals("Pass FTL Charlie")) PacketHandler.INSTANCE.sendToServer(new PacketSquadAction(7, s.id, contextTargetPlayer));
                    else if (opt.equals("Add to Bravo")) PacketHandler.INSTANCE.sendToServer(new PacketSquadAction(8, s.id, contextTargetPlayer));
                    else if (opt.equals("Add to Charlie")) PacketHandler.INSTANCE.sendToServer(new PacketSquadAction(9, s.id, contextTargetPlayer));
                    else if (opt.equals("Remove from FT")) PacketHandler.INSTANCE.sendToServer(new PacketSquadAction(10, s.id, contextTargetPlayer));
                    else if (opt.equals("Kick from Squad")) PacketHandler.INSTANCE.sendToServer(new PacketSquadAction(3, s.id, contextTargetPlayer));
                }
                playClickSound();
                return true;
            }
        }
        return false;
    }

    // Р вЂєР С•Р С–Р С‘Р С”Р В° Р Р…Р В°Р В¶Р В°РЎвЂљР С‘РЎРЏ Р Р…Р В° Р С”Р Р…Р С•Р С—Р С”Р С‘ Р Р†РЎвЂ№Р В±Р С•РЎР‚Р В° РЎРѓР С—Р В°Р Р†Р Р…Р В° Р Р† Р В»Р ВµР Р†Р С•Р в„– Р С—Р В°Р Р…Р ВµР В»Р С‘ UI
    private boolean handleSpawnButtons(double mx, double my) {
        int y = 55;
        // Р СљР ВµР в„–Р Р…
        if (my >= y && my <= y + 24) { this.selectedSpawnType = "MAIN"; mapRenderer.selectedSpawnId = "MAIN"; playClickSound(); return true; }
        y += 30;
        // Р В Р В°Р В»Р В»Р С‘Р С”
        if (my >= y && my <= y + 24) {
            if (hasRally() && !isMyRallyBlocked()) { this.selectedSpawnType = "RALLY"; mapRenderer.selectedSpawnId = "RALLY"; playClickSound(); }
            return true;
        }
        y += 40;
        // Р ТђР В°Р В±РЎвЂ№
        Team team = this.minecraft.player.getTeam();
        if (team != null) {
            String myTeam = team.getName();
            String myDim = this.minecraft.level.dimension().location().toString();
            for (AASWorldData.HubInfo hub : ClientData.clientHubs) {
                if (hub.team.equalsIgnoreCase(myTeam) && hub.constructed && hub.dimension.equals(myDim)) {
                    if (my >= y && my <= y + 20) {
                        boolean notEnoughMaterials = ClientData.serverHubSpawnCosts
                                && hub.materials < ClientData.serverHubSpawnCostAmount;

                        if (!hub.isBlocked && !notEnoughMaterials) {
                            String id = "HUB:" + hub.pos.getX() + ":" + hub.pos.getY() + ":" + hub.pos.getZ();
                            this.selectedSpawnType = id;
                            mapRenderer.selectedSpawnId = id;
                            playClickSound();
                        }
                        return true;
                    }
                    y += 24;
                }
            }
        }
        return false;
    }

    private void handleMapRightClick(double mouseX, double mouseY) {
        if (!isSquadLeaderOrFTL(this.minecraft.player)) return;

        // Если под курсором есть метка, которую игрок вправе удалить - открываем
        // маленькое меню удаления вместо радиального меню размещения новой метки.
        if (mapRenderer.tryOpenMarkerDeleteMenu(mouseX, mouseY)) {
            return;
        }

        double bpp = mapRenderer.getBlocksPerPixel();
        double centerX = mapRenderer.getCenterX(this.minecraft.player);
        double centerZ = mapRenderer.getCenterZ(this.minecraft.player);
        int mapOriginX = this.width - this.height;
        int worldX = (int) (centerX + (mouseX - (mapOriginX + this.height / 2.0)) * bpp);
        int worldZ = (int) (centerZ + (mouseY - (this.height / 2.0)) * bpp);
        this.minecraft.setScreen(new TacticalMapRadialScreen(worldX, worldZ));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (mapRenderer.isMouseOver(mx, my)) {
            return mapRenderer.mouseScrolled(mx, my, delta);
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (mapRenderer.mouseDragged(mx, my, btn, dx, dy)) {
            return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        // Р вЂ™РЎвЂ№Р В·РЎвЂ№Р Р†Р В°Р ВµР С Р С•РЎРѓРЎвЂљР В°Р Р…Р С•Р Р†Р С”РЎС“ Р С—Р ВµРЎР‚Р ВµРЎвЂљР В°РЎРѓР С”Р С‘Р Р†Р В°Р Р…Р С‘РЎРЏ Р Р† РЎР‚Р ВµР Р…Р Т‘Р ВµРЎР‚Р ВµРЎР‚Р Вµ
        mapRenderer.mouseReleased(btn);
        // Р вЂ™Р С•Р В·Р Р†РЎР‚Р В°РЎвЂ°Р В°Р ВµР С РЎР‚Р ВµР В·РЎС“Р В»РЎРЉРЎвЂљР В°РЎвЂљ Р В±Р В°Р В·Р С•Р Р†Р С•Р С–Р С• Р СР ВµРЎвЂљР С•Р Т‘Р В° (РЎвЂљР ВµР С—Р ВµРЎР‚РЎРЉ РЎвЂљР С‘Р С— boolean РЎРѓР С•Р Р†Р С—Р В°Р Т‘Р В°Р ВµРЎвЂљ)
        return super.mouseReleased(mx, my, btn);
    }

    @Override public boolean shouldCloseOnEsc() { return false; }

    private boolean isSquadLeaderOrFTL(Player player) {
        String pName = player.getScoreboardName();
        for (AASWorldData.Squad s : ClientData.clientSquads) {
            if (s.leader.equals(pName) || s.bravoLeader.equals(pName) || s.charlieLeader.equals(pName)) return true;
        }
        return false;
    }

    private boolean hasRally() {
        String myName = this.minecraft.player.getScoreboardName();
        for (AASWorldData.Squad s : ClientData.clientSquads) {
            if (s.members.contains(myName)) return s.rallyPos != null;
        }
        return false;
    }

    private String getPlayerTeam() {
        if (this.minecraft.player.getTeam() != null) {
            return this.minecraft.player.getTeam().getName();
        }
        return "NEUTRAL";
    }

    private boolean isPlayerInSquad() {
        String myName = this.minecraft.player.getScoreboardName();
        for (AASWorldData.Squad s : ClientData.clientSquads) {
            if (s.members.contains(myName)) return true;
        }
        return false;
    }

    private ResourceLocation getFlagTexture(String faction) {
        if (faction == null || faction.equalsIgnoreCase("none")) return null;
        return new ResourceLocation("aas", "textures/gui/flags/" + faction.toLowerCase() + ".png");
    }

    private void playClickSound() {
        this.minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
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

    private static class DeployButton extends Button {
        public DeployButton(int x, int y, int w, int h, Component msg, OnPress press) {
            super(x, y, w, h, msg, press, DEFAULT_NARRATION);
        }
        @Override
        protected void renderWidget(GuiGraphics gui, int mx, int my, float pt) {
            int borderColor = this.isHovered() && this.active ? 0xFFFFFFFF : 0xFF999999;
            if (!this.active) borderColor = 0xFF444444;
            gui.fill(getX(), getY(), getX() + width, getY() + height, 0xCC111111);
            gui.renderOutline(getX(), getY(), width, height, borderColor);
            int textColor = this.active ? 0xFFFFFFFF : 0xFF777777;
            gui.drawCenteredString(Minecraft.getInstance().font, getMessage(), getX() + width / 2, getY() + (height - 8) / 2, textColor);
        }
    }

    private static class SquadButton extends Button {
        public SquadButton(int x, int y, int width, int height, Component message, OnPress onPress) {
            super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
        }

        @Override
        protected void renderWidget(GuiGraphics gui, int mouseX, int mouseY, float partialTicks) {
            if (!this.visible) return;

            int borderColor = this.isHovered() ? 0xFFFFFFFF : 0xFF999999;
            if (!this.active) borderColor = 0xFF444444;

            gui.fill(getX(), getY(), getX() + width, getY() + height, 0xCC111111);
            gui.renderOutline(getX(), getY(), width, height, borderColor);

            int textColor = this.active ? 0xFFFFFFFF : 0xFF777777;
            gui.drawCenteredString(Minecraft.getInstance().font, this.getMessage(), getX() + width / 2, getY() + (height - 8) / 2, textColor);

            if (this.active && this.isHovered()) {
                gui.fill(getX(), getY() + height - 2, getX() + 2, getY() + height, 0xFFFFFFFF);
            }
        }
    }
}