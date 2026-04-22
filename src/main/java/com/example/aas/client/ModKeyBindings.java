package com.example.aas.client;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
@Mod.EventBusSubscriber(modid = "aas", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModKeyBindings {

    // Сброс ящика
    public static final KeyMapping DROP_SUPPLY_KEY = new KeyMapping(
            "key.aas.drop_supply",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_X,
            "key.categories.aas"
    );

    // === ПУНКТ 2: ОТКРЫТИЕ МЕНЮ ОТРЯДОВ ===
    public static final KeyMapping OPEN_SQUAD_MENU_KEY = new KeyMapping(
            "key.aas.open_squad_menu",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "key.categories.aas"
    );
    public static final KeyMapping SHOW_MAP_KEY = new KeyMapping(
            "key.aas.show_map",           // Название для файла переводов
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_TAB,            // Кнопка по умолчанию
            "key.categories.aas"          // Категория в настройках
    );
    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(DROP_SUPPLY_KEY);
        event.register(OPEN_SQUAD_MENU_KEY);
        event.register(SHOW_MAP_KEY);
    }
}