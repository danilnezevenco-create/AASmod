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
    public static final RegistryObject<Item> BLUE_APC = ITEMS.register("blue_apc",
            () -> new VehicleMarkerItem("BLUE", "APC", 20, 40)); // 40 матов
    public static final RegistryObject<Item> BLUE_TANK = ITEMS.register("blue_tank",
            () -> new VehicleMarkerItem("BLUE", "TANK", 40, 30));
    public static final RegistryObject<Item> BLUE_HELICOPTER = ITEMS.register("blue_helicopter",
            () -> new VehicleMarkerItem("BLUE", "HELICOPTER", 16, 20));
    public static final RegistryObject<Item> RED_APC = ITEMS.register("red_apc",
            () -> new VehicleMarkerItem("RED", "APC", 20, 40));
    public static final RegistryObject<Item> RED_TANK = ITEMS.register("red_tank",
            () -> new VehicleMarkerItem("RED", "TANK", 40, 30));
    public static final RegistryObject<Item> RED_HELICOPTER = ITEMS.register("red_helicopter",
            () -> new VehicleMarkerItem("RED", "HELICOPTER", 16, 20));

    public static final RegistryObject<Item> BLUE_COMBAT_VEHICLE = ITEMS.register("blue_combat_vehicle",
            () -> new VehicleMarkerItem("BLUE", "Combat Vehicle", 10, 30));
    public static final RegistryObject<Item> BLUE_INFANTRY_VEHICLE = ITEMS.register("blue_infantry_vehicle",
            () -> new VehicleMarkerItem("BLUE", "Infantry Vehicle", 5, 20));
    public static final RegistryObject<Item> BLUE_BOAT = ITEMS.register("blue_boat",
            () -> new VehicleMarkerItem("BLUE", "BOAT", 5, 10));
    public static final RegistryObject<Item> BLUE_MOTORCYCLE = ITEMS.register("blue_motorcycle",
            () -> new VehicleMarkerItem("BLUE", "Motorcycle", 2, 10));
    public static final RegistryObject<Item> BLUE_LIGHT_SUPPLY = ITEMS.register("blue_light_supply",
            () -> new SupplyTruckMarkerItem("BLUE", 5, "Light Supply", 80));
    public static final RegistryObject<Item> BLUE_ATGM_CARRIER = ITEMS.register("blue_atgm_carrier",
            () -> new VehicleMarkerItem("BLUE", "ATGM Carrier", 10, 20));
    public static final RegistryObject<Item> BLUE_HEAVY_SUPPLY = ITEMS.register("blue_heavy_supply",
            () -> new SupplyTruckMarkerItem("BLUE", 10, "Heavy Supply", 100)); // 10 тикетов, 100 матов
    public static final RegistryObject<Item> BLUE_SPG = ITEMS.register("blue_spg",
            () -> new VehicleMarkerItem("BLUE", "SPG", 40, 30)); // 40 тикетов, 30 матов

    public static final RegistryObject<Item> RED_HEAVY_SUPPLY = ITEMS.register("red_heavy_supply",
            () -> new SupplyTruckMarkerItem("RED", 10, "Heavy Supply", 100));
    public static final RegistryObject<Item> RED_SPG = ITEMS.register("red_spg",
            () -> new VehicleMarkerItem("RED", "SPG", 40, 30));
    public static final RegistryObject<Item> RED_ATGM_CARRIER = ITEMS.register("red_atgm_carrier",
            () -> new VehicleMarkerItem("RED", "ATGM Carrier", 10, 20));
    public static final RegistryObject<Item> RED_BOAT = ITEMS.register("red_boat",
            () -> new VehicleMarkerItem("RED", "BOAT", 5, 10));
    public static final RegistryObject<Item> RED_COMBAT_VEHICLE = ITEMS.register("red_combat_vehicle",
            () -> new VehicleMarkerItem("RED", "Combat Vehicle", 10, 30));
    public static final RegistryObject<Item> RED_INFANTRY_VEHICLE = ITEMS.register("red_infantry_vehicle",
            () -> new VehicleMarkerItem("RED", "Infantry Vehicle", 5, 20));

    // === НОВЫЕ МОДИФИКАТОРЫ (CAS) ===
    public static final RegistryObject<Item> BLUE_CAS_HELICOPTER = ITEMS.register("blue_cas_helicopter",
            () -> new VehicleMarkerItem("BLUE", "CAS Helicopter", 40, 20));
    public static final RegistryObject<Item> RED_CAS_HELICOPTER = ITEMS.register("red_cas_helicopter",
            () -> new VehicleMarkerItem("RED", "CAS Helicopter", 40, 20));

    public static final RegistryObject<Item> BLUE_CAS_FIGHTER = ITEMS.register("blue_cas_fighter",
            () -> new VehicleMarkerItem("BLUE", "CAS Fighter", 50, 10));
    public static final RegistryObject<Item> RED_CAS_FIGHTER = ITEMS.register("red_cas_fighter",
            () -> new VehicleMarkerItem("RED", "CAS Fighter", 50, 10));

    // СТАТИК ЗУ — 0 МАТЕРИАЛОВ (ПОПОЛНЯТЬСЯ НЕЛЬЗЯ)
    public static final RegistryObject<Item> BLUE_STATIC_ZU = ITEMS.register("blue_static_zu",
            () -> new VehicleMarkerItem("BLUE", "Static ZU", 0, 0));
    public static final RegistryObject<Item> BLUE_MOBILE_ZU = ITEMS.register("blue_mobile_zu",
            () -> new VehicleMarkerItem("BLUE", "Mobile ZU", 40, 20));

    public static final RegistryObject<Item> RED_STATIC_ZU = ITEMS.register("red_static_zu",
            () -> new VehicleMarkerItem("RED", "Static ZU", 0, 0));
    public static final RegistryObject<Item> RED_MOBILE_ZU = ITEMS.register("red_mobile_zu",
            () -> new VehicleMarkerItem("RED", "Mobile ZU", 40, 20));
    public static final RegistryObject<Item> RED_MOTORCYCLE = ITEMS.register("red_motorcycle",
            () -> new VehicleMarkerItem("RED", "Motorcycle", 2, 10));
    public static final RegistryObject<Item> RED_LIGHT_SUPPLY = ITEMS.register("red_light_supply",
            () -> new SupplyTruckMarkerItem("RED", 5, "Light Supply", 80));

    // === ТЕХНИКА СНАБЖЕНИЯ ===
    public static final RegistryObject<Item> BLUE_SUPPLY_MARKER = ITEMS.register("blue_supply_marker",
            () -> new SupplyTruckMarkerItem("BLUE", 5, "Supply Truck", 100)); // 100 матов
    public static final RegistryObject<Item> RED_SUPPLY_MARKER = ITEMS.register("red_supply_marker",
            () -> new SupplyTruckMarkerItem("RED", 5, "Supply Truck", 100));
    public static final RegistryObject<Item> BLUE_SUPPLY_HELICOPTER = ITEMS.register("blue_supply_helicopter",
            () -> new SupplyTruckMarkerItem("BLUE", 20, "Supply Helicopter", 100));
    public static final RegistryObject<Item> RED_SUPPLY_HELICOPTER = ITEMS.register("red_supply_helicopter",
            () -> new SupplyTruckMarkerItem("RED", 20, "Supply Helicopter", 100));

    // === СПАВНЕР И ИНСТРУМЕНТЫ ===
    public static final RegistryObject<Item> VEHICLE_SPAWNER_ITEM = ITEMS.register("vehicle_spawner",
            () -> new BlockItem(ModBlocks.VEHICLE_SPAWNER_BLOCK.get(), new Item.Properties()));
    // В ModItems.java:
    public static final RegistryObject<Item> KIT_SETUP_ITEM = ITEMS.register("kit_setup",
            () -> new com.example.aas.item.KitSetupItem());
    public static final RegistryObject<Item> ENTRENCHING_TOOL = ITEMS.register("entrenching_tool", EntrenchingToolItem::new);
    public static final RegistryObject<Item> SQUAD_LEADER_RADIO = ITEMS.register("squad_leader_radio", RallyItem::new);
    public static final RegistryObject<Item> OFFICER_PHONE = ITEMS.register("officer_phone", com.example.aas.item.OfficerPhoneItem::new);
    public static final RegistryObject<Item> AGS_AMMO = ITEMS.register("ags_ammo_box", AGSAmmoItem::new);
    public static final RegistryObject<Item> M2_AMMO = ITEMS.register("m2_ammo_box", M2AmmoItem::new);
    public static final RegistryObject<Item> AGS_PROJECTILE_ITEM = ITEMS.register("ags_projectile", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> MAIN_SUPPLY_ITEM = ITEMS.register("main_supply",
            () -> new BlockItem(ModBlocks.MAIN_SUPPLY_BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<Item> AMMO_BAG = ITEMS.register("ammo_bag",
            () -> new BlockItem(ModBlocks.AMMO_BAG_BLOCK.get(), new Item.Properties().durability(4)));
    public static final RegistryObject<Item> GAME_START_TRIGGER_ITEM = ITEMS.register("game_start_trigger",
            () -> new BlockItem(ModBlocks.GAME_START_TRIGGER.get(), new Item.Properties()));
    public static final RegistryObject<Item> INVASION_PREP_TRIGGER_ITEM = ITEMS.register("invasion_prep_trigger",
            () -> new BlockItem(ModBlocks.INVASION_PREP_TRIGGER.get(), new Item.Properties()));
    public static final RegistryObject<Item> BINOCULARS = ITEMS.register("binoculars", BinocularsItem::new);
    public static final RegistryObject<Item> WALL_BLOCK_ITEM = ITEMS.register("wall_block",
            () -> new BlockItem(ModBlocks.WALL_BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<Item> BARBED_WIRE_ITEM = ITEMS.register("barbed_wire",
            () -> new BlockItem(ModBlocks.BARBED_WIRE_BLOCK.get(), new Item.Properties()));
    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}