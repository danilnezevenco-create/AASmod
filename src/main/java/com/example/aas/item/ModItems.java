package com.example.aas.item;

import com.example.aas.block.ModBlocks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, "aas");
    // === СУЩЕСТВУЮЩАЯ БОЕВАЯ ТЕХНИКА ===
    public static final RegistryObject<Item> AMMO_BAG = ITEMS.register("ammo_bag",
            () -> new BlockItem(ModBlocks.AMMO_BAG_BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<Item> BLUE_APC = ITEMS.register("blue_apc",
            () -> new VehicleMarkerItem("BLUE", "APC", 20));
    public static final RegistryObject<Item> BLUE_TANK = ITEMS.register("blue_tank",
            () -> new VehicleMarkerItem("BLUE", "TANK", 40));
    public static final RegistryObject<Item> BLUE_HELICOPTER = ITEMS.register("blue_helicopter",
            () -> new VehicleMarkerItem("BLUE", "HELICOPTER", 16));
    public static final RegistryObject<Item> RED_APC = ITEMS.register("red_apc",
            () -> new VehicleMarkerItem("RED", "APC", 20));
    public static final RegistryObject<Item> RED_TANK = ITEMS.register("red_tank",
            () -> new VehicleMarkerItem("RED", "TANK", 40));
    public static final RegistryObject<Item> RED_HELICOPTER = ITEMS.register("red_helicopter",
            () -> new VehicleMarkerItem("RED", "HELICOPTER", 16));
    // PATH: src\main\java\com\example\aas\item\ModItems.java
    public static final RegistryObject<Item> BLUE_COMBAT_VEHICLE = ITEMS.register("blue_combat_vehicle",
            () -> new VehicleMarkerItem("BLUE", "Combat Vehicle", 10));
    public static final RegistryObject<Item> BLUE_INFANTRY_VEHICLE = ITEMS.register("blue_infantry_vehicle",
            () -> new VehicleMarkerItem("BLUE", "Infantry Vehicle", 5));
    // В блоке предметов BLUE TEAM
    public static final RegistryObject<Item> BLUE_BOAT = ITEMS.register("blue_boat",
            () -> new VehicleMarkerItem("BLUE", "BOAT", 5));

    // В блоке предметов RED TEAM
    public static final RegistryObject<Item> RED_BOAT = ITEMS.register("red_boat",
            () -> new VehicleMarkerItem("RED", "BOAT", 5));
    public static final RegistryObject<Item> RED_COMBAT_VEHICLE = ITEMS.register("red_combat_vehicle",
            () -> new VehicleMarkerItem("RED", "Combat Vehicle", 10));
    public static final RegistryObject<Item> RED_INFANTRY_VEHICLE = ITEMS.register("red_infantry_vehicle",
            () -> new VehicleMarkerItem("RED", "Infantry Vehicle", 5));

    // === НОВЫЕ МОДИФИКАТОРЫ (CAS) ===
    // CAS Helicopter: -40 тикетов
    public static final RegistryObject<Item> BLUE_CAS_HELICOPTER = ITEMS.register("blue_cas_helicopter",
            () -> new VehicleMarkerItem("BLUE", "CAS Helicopter", 40));
    public static final RegistryObject<Item> RED_CAS_HELICOPTER = ITEMS.register("red_cas_helicopter",
            () -> new VehicleMarkerItem("RED", "CAS Helicopter", 40));

    // CAS Fighter: -50 тикетов
    public static final RegistryObject<Item> BLUE_CAS_FIGHTER = ITEMS.register("blue_cas_fighter",
            () -> new VehicleMarkerItem("BLUE", "CAS Fighter", 50));
    public static final RegistryObject<Item> RED_CAS_FIGHTER = ITEMS.register("red_cas_fighter",
            () -> new VehicleMarkerItem("RED", "CAS Fighter", 50));
    public static final RegistryObject<Item> BLUE_STATIC_ZU = ITEMS.register("blue_static_zu",
            () -> new VehicleMarkerItem("BLUE", "Static ZU", 0));
    public static final RegistryObject<Item> BLUE_MOBILE_ZU = ITEMS.register("blue_mobile_zu",
            () -> new VehicleMarkerItem("BLUE", "Mobile ZU", 40));

    // В блоке предметов красной команды:
    public static final RegistryObject<Item> RED_STATIC_ZU = ITEMS.register("red_static_zu",
            () -> new VehicleMarkerItem("RED", "Static ZU", 0));
    public static final RegistryObject<Item> RED_MOBILE_ZU = ITEMS.register("red_mobile_zu",
            () -> new VehicleMarkerItem("RED", "Mobile ZU", 40));

    // === СПАВНЕР И ИНСТРУМЕНТЫ ===
    public static final RegistryObject<Item> VEHICLE_SPAWNER_ITEM = ITEMS.register("vehicle_spawner",
            () -> new BlockItem(ModBlocks.VEHICLE_SPAWNER_BLOCK.get(), new Item.Properties()));
    // В ModItems.java:
    public static final RegistryObject<Item> KIT_SETUP_ITEM = ITEMS.register("kit_setup",
            () -> new com.example.aas.item.KitSetupItem());
    public static final RegistryObject<Item> ENTRENCHING_TOOL = ITEMS.register("entrenching_tool", EntrenchingToolItem::new);
    public static final RegistryObject<Item> SQUAD_LEADER_RADIO = ITEMS.register("squad_leader_radio", RallyItem::new);
    public static final RegistryObject<Item> AGS_AMMO = ITEMS.register("ags_ammo_box", AGSAmmoItem::new);
    public static final RegistryObject<Item> M2_AMMO = ITEMS.register("m2_ammo_box", M2AmmoItem::new);
    public static final RegistryObject<Item> AGS_PROJECTILE_ITEM = ITEMS.register("ags_projectile", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> MAIN_SUPPLY_ITEM = ITEMS.register("main_supply",
            () -> new BlockItem(ModBlocks.MAIN_SUPPLY_BLOCK.get(), new Item.Properties()));

    // === ТЕХНИКА СНАБЖЕНИЯ ===
    public static final RegistryObject<Item> BLUE_SUPPLY_MARKER = ITEMS.register("blue_supply_marker",
            () -> new SupplyTruckMarkerItem("BLUE", 5, "Supply Truck"));
    public static final RegistryObject<Item> RED_SUPPLY_MARKER = ITEMS.register("red_supply_marker",
            () -> new SupplyTruckMarkerItem("RED", 5, "Supply Truck"));
    public static final RegistryObject<Item> BLUE_SUPPLY_HELICOPTER = ITEMS.register("blue_supply_helicopter",
            () -> new SupplyTruckMarkerItem("BLUE", 20, "Supply Helicopter"));
    public static final RegistryObject<Item> RED_SUPPLY_HELICOPTER = ITEMS.register("red_supply_helicopter",
            () -> new SupplyTruckMarkerItem("RED", 20, "Supply Helicopter"));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}