// PATH: src/main/java/com/example/aas/client/ModKeyBindings.java
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
            "key.aas.show_map",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_TAB,
            "key.categories.aas"
    );
    public static final KeyMapping SHOW_NICKNAMES_KEY = new KeyMapping(
            "key.aas.show_nicknames",
            KeyConflictContext.UNIVERSAL,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_SHIFT,
            "key.categories.aas"
    );
    public static final KeyMapping PLACE_PING_KEY = new KeyMapping(
            "key.aas.place_ping",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_MIDDLE,
            "key.categories.aas"
    );
    public static final KeyMapping OPEN_STATS_KEY = new KeyMapping(
            "key.aas.open_stats",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_CAPS_LOCK,
            "key.categories.aas"
    );

    // === сворачивание/разворачивание панели отряда (левый нижний угол) ===
    public static final KeyMapping TOGGLE_SQUAD_PANEL_KEY = new KeyMapping(
            "key.aas.toggle_squad_panel",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_Y,
            "key.categories.aas"
    );

    // === НОВОЕ: удержание для подъёма раненого союзника (по умолчанию F, можно перебиндить) ===
    public static final KeyMapping REVIVE_KEY = new KeyMapping(
            "key.aas.revive",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F,
            "key.categories.aas"
    );

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(DROP_SUPPLY_KEY);
        event.register(OPEN_SQUAD_MENU_KEY);
        event.register(SHOW_MAP_KEY);
        event.register(SHOW_NICKNAMES_KEY);
        event.register(PLACE_PING_KEY);
        event.register(OPEN_STATS_KEY);
        event.register(TOGGLE_SQUAD_PANEL_KEY);
        event.register(REVIVE_KEY);
    }
}