package com.example.aas.client.gui;

import com.example.aas.client.ClientData;
import com.example.aas.network.PacketHandler;
import com.example.aas.network.PacketTeamSelect;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class TeamSelectionScreen extends Screen {

    private static final ResourceLocation FLAG_UKRAINE = new ResourceLocation("aas", "textures/gui/flags/ukraine.png");
    private static final ResourceLocation FLAG_RUSSIA = new ResourceLocation("aas", "textures/gui/flags/russia.png");
    private static final ResourceLocation FLAG_USA = new ResourceLocation("aas", "textures/gui/flags/usa.png");
    private static final ResourceLocation FLAG_NATO = new ResourceLocation("aas", "textures/gui/flags/nato.png");
    private static final ResourceLocation FLAG_BLUEFOR = new ResourceLocation("aas", "textures/gui/flags/bluefor.png");
    private static final ResourceLocation FLAG_REDFOR = new ResourceLocation("aas", "textures/gui/flags/redfor.png");

    public TeamSelectionScreen() {
        super(Component.literal("Choose Team"));
    }

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        // ... (код рендера без изменений, кроме вызова getFlagTexture) ...
        this.renderBackground(gui);
        gui.fill(0, 0, this.width, this.height, 0x80000000);

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int flagWidth = 64;
        int flagHeight = 36;
        int offset = 60;

        int blueX = centerX - offset - flagWidth;
        int blueY = centerY - (flagHeight / 2);
        boolean isHoveringBlue = (mouseX >= blueX && mouseX <= blueX + flagWidth && mouseY >= blueY && mouseY <= blueY + flagHeight + 20);
        ResourceLocation blueFlag = getFlagTexture(ClientData.BLUE_FACTION);

        if (blueFlag != null) {
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            gui.blit(blueFlag, blueX, blueY, 0, 0, flagWidth, flagHeight, flagWidth, flagHeight);
        } else {
            gui.fill(blueX, blueY, blueX + flagWidth, blueY + flagHeight, 0xFF0000AA);
        }
        if (isHoveringBlue) {
            gui.renderOutline(blueX - 1, blueY - 1, flagWidth + 2, flagHeight + 2, 0xFFFFFFFF);
        }
        String blueTeamName = ClientData.customBlueName; // Используем имя из пакета
        if (ClientData.BLUE_FACTION != null && !ClientData.BLUE_FACTION.equals("none") && !ClientData.BLUE_FACTION.equals("bluefor")) {
            blueTeamName = ClientData.BLUE_FACTION.replace("_", " ").toUpperCase();
        }
        int blueJoinColor = isHoveringBlue ? 0xFFFFFFFF : 0xFFFFAA00;
        gui.drawCenteredString(this.font, Component.literal("JOIN").withStyle(ChatFormatting.GOLD), blueX + flagWidth / 2, blueY + flagHeight + 10, blueJoinColor);
        gui.drawCenteredString(this.font, Component.literal(blueTeamName).withStyle(ChatFormatting.BLUE), blueX + flagWidth / 2, blueY - 15, 0xFFFFFF);

        int redX = centerX + offset;
        int redY = centerY - (flagHeight / 2);
        boolean isHoveringRed = (mouseX >= redX && mouseX <= redX + flagWidth && mouseY >= redY && mouseY <= redY + flagHeight + 20);
        ResourceLocation redFlag = getFlagTexture(ClientData.RED_FACTION);

        if (redFlag != null) {
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            gui.blit(redFlag, redX, redY, 0, 0, flagWidth, flagHeight, flagWidth, flagHeight);
        } else {
            gui.fill(redX, redY, redX + flagWidth, redY + flagHeight, 0xFFAA0000);
        }
        if (isHoveringRed) {
            gui.renderOutline(redX - 1, redY - 1, flagWidth + 2, flagHeight + 2, 0xFFFFFFFF);
        }
        String redTeamName = ClientData.customRedName;
        if (ClientData.RED_FACTION != null && !ClientData.RED_FACTION.equals("none") && !ClientData.RED_FACTION.equals("redfor")) {
            redTeamName = ClientData.RED_FACTION.replace("_", " ").toUpperCase();
        }
        int redJoinColor = isHoveringRed ? 0xFFFFFFFF : 0xFFFFAA00;
        gui.drawCenteredString(this.font, Component.literal("JOIN").withStyle(ChatFormatting.GOLD), redX + flagWidth / 2, redY + flagHeight + 10, redJoinColor);
        gui.drawCenteredString(this.font, Component.literal(redTeamName).withStyle(ChatFormatting.RED), redX + flagWidth / 2, redY - 15, 0xFFFFFF);

        super.render(gui, mouseX, mouseY, partialTick);
    }

    // ... (mouseClicked без изменений)
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int centerX = this.width / 2;
            int centerY = this.height / 2;
            int flagWidth = 64;
            int flagHeight = 36;
            int offset = 60;
            int blueX = centerX - offset - flagWidth;
            int blueY = centerY - (flagHeight / 2);
            if (mouseX >= blueX && mouseX <= blueX + flagWidth && mouseY >= blueY && mouseY <= blueY + flagHeight + 20) {
                PacketHandler.INSTANCE.sendToServer(new PacketTeamSelect("BLUE"));
                this.onClose();
                return true;
            }
            int redX = centerX + offset;
            int redY = centerY - (flagHeight / 2);
            if (mouseX >= redX && mouseX <= redX + flagWidth && mouseY >= redY && mouseY <= redY + flagHeight + 20) {
                PacketHandler.INSTANCE.sendToServer(new PacketTeamSelect("RED"));
                this.onClose();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    @Override
    public boolean isPauseScreen() { return false; }

    private ResourceLocation getFlagTexture(String faction) {
        if (faction == null || faction.equals("none")) return null;
        switch (faction.toLowerCase()) {
            case "ukraine": return FLAG_UKRAINE;
            case "russia": return FLAG_RUSSIA;
            case "usa": return FLAG_USA;
            case "bluefor": return new ResourceLocation("aas", "textures/gui/flags/bluefor.png");
            case "redfor": return new ResourceLocation("aas", "textures/gui/flags/redfor.png");
            case "nato": return FLAG_NATO;
            default: return null;
        }
    }
}