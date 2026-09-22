package com.example.aas.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// Предупреждение над хотбаром, если Simple Voice Chat не подключен к серверу.
// Источник состояния - ClientData.voicechatConnected (обновляется из AASVoicechatPlugin).
@Mod.EventBusSubscriber(modid = "aas", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class VoicechatStatusOverlay {

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) return;
        if (ClientData.voicechatConnected) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        GuiGraphics gui = event.getGuiGraphics();
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();

        Component title = Component.translatable("aas.voicechat.disconnected.title");
        Component subtitle = Component.translatable("aas.voicechat.disconnected.subtitle");

        int boxWidth = Math.max(mc.font.width(title), mc.font.width(subtitle)) + 16;
        int centerX = width / 2;
        int y = height - 58; // прямо над хотбаром

        gui.fill(centerX - boxWidth / 2, y - 4, centerX + boxWidth / 2, y + 24, 0xA0000000);
        gui.drawCenteredString(mc.font, title, centerX, y, 0xFFFF5555);
        gui.drawCenteredString(mc.font, subtitle, centerX, y + 12, 0xFFFFFFFF);
    }
}