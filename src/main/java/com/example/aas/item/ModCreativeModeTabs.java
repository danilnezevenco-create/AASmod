package com.example.aas.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public class ModCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "aas");

    public static final RegistryObject<CreativeModeTab> AAS_TAB = CREATIVE_MODE_TABS.register("aas_tab",
            () -> CreativeModeTab.builder()
                    .icon(() -> new ItemStack(ModItems.SQUAD_LEADER_RADIO.get()))
                    .title(Component.literal("Advance And Secure"))
                    .displayItems((pParameters, pOutput) -> {

                        pOutput.accept(ModItems.SQUAD_LEADER_RADIO.get());
                        pOutput.accept(ModItems.ENTRENCHING_TOOL.get());
                        pOutput.accept(ModItems.VEHICLE_SPAWNER_ITEM.get());

                        pOutput.accept(ModItems.M2_AMMO.get());
                        pOutput.accept(ModItems.AGS_AMMO.get());
                        pOutput.accept(ModItems.AMMO_BAG.get());
                        // === BLUE TEAM ===
                        pOutput.accept(ModItems.BLUE_APC.get());
                        pOutput.accept(ModItems.BLUE_TANK.get());
                        pOutput.accept(ModItems.BLUE_HELICOPTER.get());
                        pOutput.accept(ModItems.BLUE_CAS_HELICOPTER.get()); // New
                        pOutput.accept(ModItems.BLUE_CAS_FIGHTER.get());    // New
                        pOutput.accept(ModItems.BLUE_COMBAT_VEHICLE.get());
                        pOutput.accept(ModItems.BLUE_INFANTRY_VEHICLE.get());
                        pOutput.accept(ModItems.BLUE_SUPPLY_MARKER.get());
                        pOutput.accept(ModItems.BLUE_SUPPLY_HELICOPTER.get());
                        pOutput.accept(ModItems.BLUE_STATIC_ZU.get());
                        pOutput.accept(ModItems.BLUE_MOBILE_ZU.get());
                        pOutput.accept(ModItems.BLUE_BOAT.get());
                        // === RED TEAM ===
                        pOutput.accept(ModItems.RED_APC.get());
                        pOutput.accept(ModItems.RED_TANK.get());
                        pOutput.accept(ModItems.RED_HELICOPTER.get());
                        pOutput.accept(ModItems.RED_CAS_HELICOPTER.get()); // New
                        pOutput.accept(ModItems.RED_CAS_FIGHTER.get());    // New
                        pOutput.accept(ModItems.RED_COMBAT_VEHICLE.get());
                        pOutput.accept(ModItems.RED_INFANTRY_VEHICLE.get());
                        pOutput.accept(ModItems.RED_SUPPLY_MARKER.get());
                        pOutput.accept(ModItems.RED_SUPPLY_HELICOPTER.get());
                        pOutput.accept(ModItems.RED_STATIC_ZU.get());
                        pOutput.accept(ModItems.RED_MOBILE_ZU.get());
                        pOutput.accept(ModItems.RED_BOAT.get());
                        pOutput.accept(ModItems.MAIN_SUPPLY_ITEM.get());
                        pOutput.accept(ModItems.KIT_SETUP_ITEM.get());
                    })
                    .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}