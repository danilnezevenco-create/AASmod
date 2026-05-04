// PATH: src\main\java\com\example\aas\config\AASConfig.java
package com.example.aas.config;
import net.minecraftforge.common.ForgeConfigSpec;

public class AASConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    // Геймплей
    public static final ForgeConfigSpec.BooleanValue LOW_TICKETS_SIREN;
    public static final ForgeConfigSpec.BooleanValue AGS_PROJECTILE_DESTRUCTION;
    public static final ForgeConfigSpec.BooleanValue AMMO_STACK_DESTRUCTION;
    public static final ForgeConfigSpec.DoubleValue DIGGING_SPEED_MULTIPLIER;
    public static final ForgeConfigSpec.BooleanValue PREVENT_BLOCK_BREAKING;
    public static final ForgeConfigSpec.BooleanValue PREVENT_ALL_ITEM_DROPS;
    public static final ForgeConfigSpec.IntValue HUB_RESUPPLY_COST;
    public static final ForgeConfigSpec.BooleanValue ENABLE_KNOCKOUT;
    public static final ForgeConfigSpec.ConfigValue<String> REVIVE_ITEM;
    public static final ForgeConfigSpec.IntValue REVIVE_COOLDOWN_SECONDS;
    public static final ForgeConfigSpec.BooleanValue HUB_SPAWN_COSTS_MATERIALS;
    public static final ForgeConfigSpec.IntValue HUB_SPAWN_MATERIAL_COST;
    public static final ForgeConfigSpec.BooleanValue MAIN_SUPPLY_HEALING;
    public static final ForgeConfigSpec.IntValue MAIN_SUPPLY_HEAL_RADIUS;
    public static final ForgeConfigSpec.BooleanValue ALLOW_BREAKING_DEFENSES;
    public static final ForgeConfigSpec.BooleanValue AUTO_GIVE_SL_RADIO;
    public static final ForgeConfigSpec.BooleanValue HUB_PLACEMENT_REQUIRES_CRATE;
    public static final ForgeConfigSpec.IntValue MAX_DOWNED_TIME_SECONDS;

    // Радиусы и баланс
    public static final ForgeConfigSpec.IntValue MIN_HUB_DISTANCE;
    public static final ForgeConfigSpec.IntValue MIN_RALLY_POINT_DISTANCE;
    public static final ForgeConfigSpec.IntValue MAX_HUBS_PER_TEAM;
    public static final ForgeConfigSpec.IntValue HUB_BLOCK_RADIUS;
    public static final ForgeConfigSpec.IntValue RALLY_BLOCK_RADIUS;
    public static final ForgeConfigSpec.IntValue HUB_SOUND_RADIUS;
    public static final ForgeConfigSpec.IntValue HUB_BUILD_RADIUS;
    public static final ForgeConfigSpec.IntValue CRATE_BUILD_RADIUS;
    public static final ForgeConfigSpec.IntValue HUB_BLOCK_ENEMY_COUNT;
    public static final ForgeConfigSpec.IntValue RALLY_BLOCK_ENEMY_COUNT;

    // Имена команд
    public static final ForgeConfigSpec.ConfigValue<String> BLUE_TEAM_CUSTOM_NAME;
    public static final ForgeConfigSpec.ConfigValue<String> RED_TEAM_CUSTOM_NAME;

    static {
        BUILDER.push("Gameplay Settings");
        AGS_PROJECTILE_DESTRUCTION = BUILDER.comment("Grenade destruction").define("agsProjectileDestruction", true);
        AMMO_STACK_DESTRUCTION = BUILDER.comment("Ammo explosion destruction").define("ammoStackDestruction", true);
        DIGGING_SPEED_MULTIPLIER = BUILDER.comment("Digging speed multiplier").defineInRange("diggingSpeedMultiplier", 1.0, 0.1, 10.0);
        PREVENT_BLOCK_BREAKING = BUILDER.comment("Prevent players from breaking blocks").define("preventBlockBreaking", false);
        ALLOW_BREAKING_DEFENSES = BUILDER.comment("Allow players to break walls and barbed wire even if PREVENT_BLOCK_BREAKING is true").define("allowBreakingDefenses", true);
        PREVENT_ALL_ITEM_DROPS = BUILDER.comment("Prevent item dropping in survival when game is started").define("preventAllItemDrops", false);
        HUB_RESUPPLY_COST = BUILDER.comment("Cost for kit resupply").defineInRange("hubResupplyCost", 15, 0, 1000);
        HUB_PLACEMENT_REQUIRES_CRATE = BUILDER.comment("Does placing a FOB require a Supply Crate nearby? (Consumes the crate, crate gives 0 mats to FOB)")
                .define("hubPlacementRequiresCrate", false);
        HUB_SPAWN_COSTS_MATERIALS = BUILDER.comment("Does spawning at FOB cost materials?").define("hubSpawnCostsMaterials", false);
        HUB_SPAWN_MATERIAL_COST = BUILDER.comment("Material cost to spawn at FOB").defineInRange("hubSpawnMaterialCost", 10, 0, 1000);
        MAIN_SUPPLY_HEALING = BUILDER.comment("Give regeneration near Main Base?").define("mainSupplyHealing", true);
        MAIN_SUPPLY_HEAL_RADIUS = BUILDER.comment("Radius for Main Base healing").defineInRange("mainSupplyHealRadius", 5, 1, 50);
        AUTO_GIVE_SL_RADIO = BUILDER.comment("Automatically give radio to new Squad Leaders").define("autoGiveSlRadio", false);
        LOW_TICKETS_SIREN = BUILDER.comment("Play siren at 50 tickets").define("lowTicketsSiren", true);
        BUILDER.pop();

        ENABLE_KNOCKOUT = BUILDER.comment("Enable knockout mechanic").define("enableKnockout", true);
        REVIVE_ITEM = BUILDER.comment("Registry name of the item used to revive (e.g. 'minecraft:paper')").define("reviveItem", "minecraft:paper");
        REVIVE_COOLDOWN_SECONDS = BUILDER.comment("Time in seconds where dying again results in instant death").defineInRange("reviveCooldownSeconds", 120, 0, 600);
        MAX_DOWNED_TIME_SECONDS = BUILDER.comment("Max time in downed state before bleeding out (seconds)")
                .defineInRange("maxDownedTimeSeconds", 180, 5, 3600);

        BUILDER.push("Balance Settings");
        MIN_HUB_DISTANCE = BUILDER.defineInRange("minHubDistance", 150, 0, 10000);
        MIN_RALLY_POINT_DISTANCE = BUILDER.defineInRange("minRallyPointDistance", 150, 0, 10000);
        MAX_HUBS_PER_TEAM = BUILDER.defineInRange("maxHubsPerTeam", 8, 1, 100);
        HUB_BLOCK_RADIUS = BUILDER.defineInRange("hubBlockRadius", 40, 5, 200);
        RALLY_BLOCK_RADIUS = BUILDER.defineInRange("rallyBlockRadius", 40, 5, 200);
        HUB_SOUND_RADIUS = BUILDER.defineInRange("hubSoundRadius", 15, 1, 128);
        HUB_BUILD_RADIUS = BUILDER.defineInRange("hubBuildRadius", 50, 10, 200);
        CRATE_BUILD_RADIUS = BUILDER.defineInRange("crateBuildRadius", 50, 5, 200);
        HUB_BLOCK_ENEMY_COUNT = BUILDER.defineInRange("hubBlockEnemyCount", 3, 1, 20);
        RALLY_BLOCK_ENEMY_COUNT = BUILDER.defineInRange("rallyBlockEnemyCount", 1, 1, 20);
        BUILDER.pop();

        BUILDER.push("Faction Settings");
        BLUE_TEAM_CUSTOM_NAME = BUILDER.define("blueTeamCustomName", "BLUEFOR");
        RED_TEAM_CUSTOM_NAME = BUILDER.define("redTeamCustomName", "REDFOR");
        BUILDER.pop();

        SPEC = BUILDER.build();
    }
}