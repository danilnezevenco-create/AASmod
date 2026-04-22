package com.example.aas;

import com.example.aas.block.ModBlocks;
import com.example.aas.client.ClientConfigRegistry; // <--- НОВЫЙ ИМПОРТ (вместо AASConfigScreen)
import com.example.aas.command.ModCommands;
import com.example.aas.config.AASConfig;
import com.example.aas.entity.ModEntities;
import com.example.aas.item.ModCreativeModeTabs;
import com.example.aas.item.ModItems;
import com.example.aas.network.PacketHandler;
import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod("aas")
public class AASMod {
    private static final Logger LOGGER = LogUtils.getLogger();

    public AASMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);

        // Регистрируем конфиг (общий для клиента и сервера)
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, AASConfig.SPEC);

        // === ИСПРАВЛЕННАЯ РЕГИСТРАЦИЯ ЭКРАНА КОНФИГА ===
        // Мы используем DistExecutor, чтобы вызвать метод из ClientConfigRegistry.
        // Так как AASConfigScreen не упоминается в этом файле, сервер не попытается его загрузить.
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientConfigRegistry::registerConfigScreen);
        // ===============================================

        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModEntities.register(modEventBus);
        ModCreativeModeTabs.register(modEventBus);
        com.example.aas.menu.ModMenuTypes.register(modEventBus);

        com.example.aas.sound.ModSounds.register(modEventBus);

        MinecraftForge.EVENT_BUS.register(this);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            PacketHandler.register();
        });
    }

    private void clientSetup(final FMLClientSetupEvent event) {
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("AAS Server starting...");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        ModCommands.register(event.getDispatcher());
    }
}