package com.example.aas.sound;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, "aas");
    public static final RegistryObject<SoundEvent> RALLY_IDLE = registerSoundEvent("rally_idle");
    public static final RegistryObject<SoundEvent> HUB_IDLE = registerSoundEvent("hub_idle");
    public static final RegistryObject<SoundEvent> M2_LOAD = registerSoundEvent("m2_load");
    public static final RegistryObject<SoundEvent> M2_UNLOAD = registerSoundEvent("m2_unload");
    public static final RegistryObject<SoundEvent> SIREN_ALARM = registerSoundEvent("siren_alarm");
    public static final RegistryObject<SoundEvent> PLAYER_DOWNED = registerSoundEvent("player_downed");
    public static final RegistryObject<SoundEvent> AGS_SHOOT = registerSoundEvent("ags_shoot");
    public static final RegistryObject<SoundEvent> HELP_SCREAM = registerSoundEvent("help_scream");
    public static final RegistryObject<SoundEvent> RADIO_OPEN = registerSoundEvent("radio_open");
    public static final RegistryObject<SoundEvent> M2_SHOOT = registerSoundEvent("m2_shoot");
    public static final RegistryObject<SoundEvent> SHOVEL_DIG = registerSoundEvent("shovel_dig");

    private static RegistryObject<SoundEvent> registerSoundEvent(String name) {
        ResourceLocation id = new ResourceLocation("aas", name);
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }

    public static void register(IEventBus eventBus) {
        SOUND_EVENTS.register(eventBus);
    }
}