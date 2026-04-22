package com.example.aas.block;
import com.example.aas.AASMod;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, "aas");
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, "aas");
    // PATH: src\main\java\com\example\aas\block\ModBlocks.java
    public static final RegistryObject<Block> AMMO_BAG_BLOCK = BLOCKS.register("ammo_bag", AmmoBagBlock::new);
    // === БЛОКИ ===
    public static final RegistryObject<Block> BLUE_RALLY_BLOCK = BLOCKS.register("blue_rally", RallyPointBlock::new);
    public static final RegistryObject<Block> RED_RALLY_BLOCK = BLOCKS.register("red_rally", RallyPointBlock::new);
    public static final RegistryObject<Block> HUB_BLOCK = BLOCKS.register("hub_block", HubBlock::new);
    public static final RegistryObject<Block> BARBED_WIRE_BLOCK = BLOCKS.register("barbed_wire", BarbedWireBlock::new);
    public static final RegistryObject<Block> WALL_BLOCK = BLOCKS.register("wall_block", WallBlock::new);
    public static final RegistryObject<Block> M2_CONSTRUCTION_BLOCK = BLOCKS.register("m2_construction", M2ConstructionBlock::new);
    public static final RegistryObject<Block> AGS_CONSTRUCTION_BLOCK = BLOCKS.register("ags_construction", AGSConstructionBlock::new);
    public static final RegistryObject<Block> MAIN_SUPPLY_BLOCK = BLOCKS.register("main_supply", MainSupplyBlock::new);
    public static final RegistryObject<Block> SUPPLY_CRATE_VISUAL = BLOCKS.register("crate_dropped",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).noOcclusion()));
    public static final RegistryObject<Block> MORTAR_CONSTRUCTION_BLOCK = BLOCKS.register("mortar_construction", MortarConstructionBlock::new);
    public static final RegistryObject<Block> TOW_CONSTRUCTION_BLOCK = BLOCKS.register("tow_construction", TOWConstructionBlock::new);

    // Склады
    public static final RegistryObject<Block> AGS_AMMO_STACK_BLOCK = BLOCKS.register("ags_ammo_stack", AGSAmmoStackBlock::new);
    public static final RegistryObject<Block> M2_AMMO_STACK_BLOCK = BLOCKS.register("m2_ammo_stack", M2AmmoStackBlock::new);
    public static final RegistryObject<Block> MORTAR_SHELL_STACK_BLOCK = BLOCKS.register("mortar_shell_stack", MortarShellStackBlock::new);
    public static final RegistryObject<Block> TOW_MISSILE_STACK_BLOCK = BLOCKS.register("tow_missile_stack", TOWMissileStackBlock::new);

    // === BLOCK ENTITIES ===
    public static final RegistryObject<BlockEntityType<RallyPointBlockEntity>> RALLY_BE = BLOCK_ENTITIES.register("rally_be", () -> BlockEntityType.Builder.of(RallyPointBlockEntity::new, BLUE_RALLY_BLOCK.get(), RED_RALLY_BLOCK.get()).build(null));
    public static final RegistryObject<BlockEntityType<HubBlockEntity>> HUB_BE = BLOCK_ENTITIES.register("hub_be", () -> BlockEntityType.Builder.of(HubBlockEntity::new, HUB_BLOCK.get()).build(null));
    public static final RegistryObject<BlockEntityType<BarbedWireBlockEntity>> WIRE_BE = BLOCK_ENTITIES.register("wire_be", () -> BlockEntityType.Builder.of(BarbedWireBlockEntity::new, BARBED_WIRE_BLOCK.get()).build(null));
    public static final RegistryObject<BlockEntityType<AGSConstructionBlockEntity>> AGS_CONSTRUCTION_BE = BLOCK_ENTITIES.register("ags_construction_be", () -> BlockEntityType.Builder.of(AGSConstructionBlockEntity::new, AGS_CONSTRUCTION_BLOCK.get()).build(null));
    public static final RegistryObject<BlockEntityType<M2ConstructionBlockEntity>> M2_CONSTRUCTION_BE = BLOCK_ENTITIES.register("m2_construction_be", () -> BlockEntityType.Builder.of(M2ConstructionBlockEntity::new, M2_CONSTRUCTION_BLOCK.get()).build(null));
    public static final RegistryObject<BlockEntityType<WallBlockEntity>> WALL_BE = BLOCK_ENTITIES.register("wall_be", () -> BlockEntityType.Builder.of(WallBlockEntity::new, WALL_BLOCK.get()).build(null));
    public static final RegistryObject<BlockEntityType<MainSupplyBlockEntity>> MAIN_SUPPLY_BE = BLOCK_ENTITIES.register("main_supply_be", () -> BlockEntityType.Builder.of(MainSupplyBlockEntity::new, MAIN_SUPPLY_BLOCK.get()).build(null));
    public static final RegistryObject<BlockEntityType<MortarConstructionBlockEntity>> MORTAR_CONSTRUCTION_BE = BLOCK_ENTITIES.register("mortar_construction_be", () -> BlockEntityType.Builder.of(MortarConstructionBlockEntity::new, MORTAR_CONSTRUCTION_BLOCK.get()).build(null));
    public static final RegistryObject<BlockEntityType<TOWConstructionBlockEntity>> TOW_CONSTRUCTION_BE = BLOCK_ENTITIES.register("tow_construction_be", () -> BlockEntityType.Builder.of(TOWConstructionBlockEntity::new, TOW_CONSTRUCTION_BLOCK.get()).build(null));
    public static final RegistryObject<Block> VEHICLE_SPAWNER_BLOCK = BLOCKS.register("vehicle_spawner", VehicleSpawnerBlock::new);
    public static final RegistryObject<BlockEntityType<VehicleSpawnerBlockEntity>> VEHICLE_SPAWNER_BE = BLOCK_ENTITIES.register("vehicle_spawner", () -> BlockEntityType.Builder.of(VehicleSpawnerBlockEntity::new, VEHICLE_SPAWNER_BLOCK.get()).build(null));
    // === НОВЫЙ BE ДЛЯ СКЛАДОВ ПАТРОНОВ ===
// Используется и для AGS, и для M2
    public static final RegistryObject<BlockEntityType<AmmoStackBlockEntity>> AMMO_STACK_BE = BLOCK_ENTITIES.register("ammo_stack_be",
            () -> BlockEntityType.Builder.of(AmmoStackBlockEntity::new,
                    AGS_AMMO_STACK_BLOCK.get(),
                    M2_AMMO_STACK_BLOCK.get()
            ).build(null));

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        BLOCK_ENTITIES.register(bus);
    }
}