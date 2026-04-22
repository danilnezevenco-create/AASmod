package com.example.aas.menu;

import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, "aas");

    public static final RegistryObject<MenuType<VehicleSpawnerMenu>> VEHICLE_SPAWNER_MENU =
            MENUS.register("vehicle_spawner_menu",
                    () -> IForgeMenuType.create(VehicleSpawnerMenu::new));
    // В ModMenuTypes.java:
    public static final RegistryObject<MenuType<com.example.aas.menu.KitEditorMenu>> KIT_EDITOR_MENU =
            MENUS.register("kit_editor_menu", () -> net.minecraftforge.common.extensions.IForgeMenuType.create(com.example.aas.menu.KitEditorMenu::new));
    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}