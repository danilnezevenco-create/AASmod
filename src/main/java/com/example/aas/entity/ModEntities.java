package com.example.aas.entity;

import com.example.aas.entity.M2BulletEntity;
import com.example.aas.entity.M2BrowningEntity;
import com.example.aas.entity.AGS30Entity;
import com.example.aas.entity.AGS30GrenadeEntity;
import com.example.aas.entity.SupplyCrateEntity;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "aas");

    // M2 Browning (Пулемет)
    public static final RegistryObject<EntityType<M2BrowningEntity>> M2_BROWNING = ENTITY_TYPES.register("m2_browning",
            () -> EntityType.Builder.<M2BrowningEntity>of(M2BrowningEntity::new, MobCategory.MISC)
                    .sized(1.5f, 1.5f)
                    .build(new ResourceLocation("aas", "m2_browning").toString()));

    // Пуля M2
    public static final RegistryObject<EntityType<M2BulletEntity>> M2_BULLET = ENTITY_TYPES.register("m2_bullet",
            () -> EntityType.Builder.<M2BulletEntity>of(M2BulletEntity::new, MobCategory.MISC)
                    .sized(0.1f, 0.1f)
                    .clientTrackingRange(128)
                    .updateInterval(20)
                    .setShouldReceiveVelocityUpdates(true)
                    .build(new ResourceLocation("aas", "m2_bullet").toString()));

    // AGS-30 (Гранатомет)
    public static final RegistryObject<EntityType<AGS30Entity>> AGS_30 = ENTITY_TYPES.register("ags_30",
            () -> EntityType.Builder.<AGS30Entity>of(AGS30Entity::new, MobCategory.MISC)
                    .sized(1.5f, 1.5f)
                    .build(new ResourceLocation("aas", "ags_30").toString()));

    // === ГРАНАТА AGS (УМЕНЬШЕНА В 2 РАЗА) ===
    public static final RegistryObject<EntityType<AGS30GrenadeEntity>> AGS_30_GRENADE = ENTITY_TYPES.register("ags_30_grenade",
            () -> EntityType.Builder.<AGS30GrenadeEntity>of(AGS30GrenadeEntity::new, MobCategory.MISC)
                    .sized(0.1f, 0.1f) // Было 0.2f, стало 0.1f
                    .clientTrackingRange(512)
                    .updateInterval(10)
                    .setShouldReceiveVelocityUpdates(true)
                    .build(new ResourceLocation("aas", "ags_30_grenade").toString()));

    // Ящик снабжения
    public static final RegistryObject<EntityType<SupplyCrateEntity>> SUPPLY_CRATE =
            ENTITY_TYPES.register("supply_crate",
                    () -> EntityType.Builder.<SupplyCrateEntity>of(SupplyCrateEntity::new, MobCategory.MISC)
                            .sized(0.9f, 0.9f)
                            .clientTrackingRange(64)
                            .updateInterval(10)
                            .setShouldReceiveVelocityUpdates(true)
                            .build(new ResourceLocation("aas", "supply_crate").toString()));

    public static void register(IEventBus eventBus) {
        ENTITY_TYPES.register(eventBus);
    }
}