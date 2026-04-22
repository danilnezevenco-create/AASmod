package com.example.aas.client;

import com.example.aas.entity.AGS30Entity;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "aas", value = Dist.CLIENT)
public class AGSScopeOverlay {

    // Путь к вашей текстуре
    private static final ResourceLocation SCOPE_TEXTURE = new ResourceLocation("aas", "textures/gui/ags_scope.png");

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Entity vehicle = mc.player.getVehicle();

        // Проверяем, сидит ли игрок в AGS-30 и целится ли он
        if (vehicle instanceof AGS30Entity ags && ags.isAiming()) {

            // 1. Скрываем стандартный прицел (крестик)
            if (event.getOverlay() == VanillaGuiOverlay.CROSSHAIR.type()) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onRenderOverlayPost(RenderGuiOverlayEvent.Post event) {
        // Рисуем наш прицел поверх всего
        if (event.getOverlay() == VanillaGuiOverlay.HELMET.type()) { // Рисуем в слое шлема или hotbar
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;

            Entity vehicle = mc.player.getVehicle();

            if (vehicle instanceof AGS30Entity ags && ags.isAiming()) {
                int width = mc.getWindow().getGuiScaledWidth();
                int height = mc.getWindow().getGuiScaledHeight();

                GuiGraphics gui = event.getGuiGraphics();

                // Настройка рендера
                RenderSystem.disableDepthTest();
                RenderSystem.depthMask(false);
                RenderSystem.defaultBlendFunc();
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                RenderSystem.setShaderTexture(0, SCOPE_TEXTURE);

                // Рисуем текстуру на весь экран
                // blit(texture, x, y, width, height, uOffset, vOffset, uWidth, vHeight, textureWidth, textureHeight)
                gui.blit(SCOPE_TEXTURE, 0, 0, width, height, 0.0F, 0.0F, width, height, width, height);

                RenderSystem.depthMask(true);
                RenderSystem.enableDepthTest();
            }
        }
    }
}