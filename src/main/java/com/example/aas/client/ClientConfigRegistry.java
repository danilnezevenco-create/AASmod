package com.example.aas.client;

import com.example.aas.client.gui.AASConfigScreen;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;

public class ClientConfigRegistry {
    // Этот метод будет вызван только на клиенте
    public static void registerConfigScreen() {
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new AASConfigScreen(parent))
        );
    }
}