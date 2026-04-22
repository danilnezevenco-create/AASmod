// PATH: src\main\java\com\example\aas\client\ClientModEvents.java
package com.example.aas.client;

import com.example.aas.block.ModBlocks;
import com.example.aas.client.renderer.*;
import com.example.aas.entity.ModEntities;
import com.example.aas.menu.ModMenuTypes;
import com.example.aas.client.gui.VehicleSpawnerScreen;
import com.example.aas.client.gui.KitEditorScreen; // <--- ДОБАВЛЕН ИМПОРТ ЭКРАНА
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = "aas", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {;
        event.registerEntityRenderer(ModEntities.M2_BROWNING.get(), M2BrowningRenderer::new);
        event.registerEntityRenderer(ModEntities.M2_BULLET.get(), M2BulletRenderer::new);
        event.registerEntityRenderer(ModEntities.AGS_30.get(), AGS30Renderer::new);
        event.registerBlockEntityRenderer(ModBlocks.VEHICLE_SPAWNER_BE.get(), VehicleSpawnerRenderer::new);
        event.registerEntityRenderer(ModEntities.AGS_30_GRENADE.get(), (context) -> new ThrownItemRenderer<>(context, 0.5F, true));

        // Рендерер для ящика
        event.registerEntityRenderer(ModEntities.SUPPLY_CRATE.get(), SupplyCrateRenderer::new);
    }

    @SubscribeEvent
    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        // Пусто
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // Регистрация меню спавнера
            MenuScreens.register(ModMenuTypes.VEHICLE_SPAWNER_MENU.get(), VehicleSpawnerScreen::new);

            // === ВОТ ЭТА СТРОКА РЕШАЕТ ПРОБЛЕМУ ===
            // Регистрация меню настройки китов
            MenuScreens.register(ModMenuTypes.KIT_EDITOR_MENU.get(), KitEditorScreen::new);
        });
    }
}