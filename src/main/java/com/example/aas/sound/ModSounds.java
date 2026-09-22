package com.example.aas.sound;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, "aas");
    public static final RegistryObject<SoundEvent> RALLY_IDLE = registerSoundEvent("rally_idle");
    public static final RegistryObject<SoundEvent> HUB_IDLE = registerSoundEvent("hub_idle");
    public static final RegistryObject<SoundEvent> RADIO_BEEP = registerSoundEvent("radio_beep");
    public static final RegistryObject<SoundEvent> M2_LOAD = registerSoundEvent("m2_load");
    public static final RegistryObject<SoundEvent> M2_UNLOAD = registerSoundEvent("m2_unload");
    public static final RegistryObject<SoundEvent> SIREN_ALARM = registerSoundEvent("siren_alarm");
    public static final RegistryObject<SoundEvent> PLAYER_DOWNED = registerSoundEvent("player_downed");
    public static final RegistryObject<SoundEvent> AGS_SHOOT = registerSoundEvent("ags_shoot");
    public static final RegistryObject<SoundEvent> HELP_SCREAM = registerSoundEvent("help_scream");
    public static final RegistryObject<SoundEvent> RADIO_OPEN = registerSoundEvent("radio_open");
    public static final RegistryObject<SoundEvent> M2_SHOOT = registerSoundEvent("m2_shoot");
    public static final RegistryObject<SoundEvent> SHOVEL_DIG = registerSoundEvent("shovel_dig");
    // Добавьте в список существующих RegistryObject
    public static final RegistryObject<SoundEvent> MAP_MARKER_PLACE = registerSoundEvent("map_marker_place");
    public static final RegistryObject<SoundEvent> BLUEPRINT_PLACE = registerSoundEvent("blueprint_place");
    public static final RegistryObject<SoundEvent> STATION_IDLE = registerSoundEvent("station_idle");
    public static final RegistryObject<SoundEvent> TEAM_SELECT = registerSoundEvent("team_select");
    public static final RegistryObject<SoundEvent> KIT_SELECT = registerSoundEvent("kit_select");
    public static final RegistryObject<SoundEvent> SQUAD_JOIN = registerSoundEvent("squad_join");
    public static final RegistryObject<SoundEvent> SQUAD_LEAVE = registerSoundEvent("squad_leave");
    // В файле ModSounds.java добавьте:
    public static final Map<String, List<RegistryObject<SoundEvent>>> FACTION_SCREAMS = new HashMap<>();

    static {
        String[] factions = {"ukraine", "russia", "usa", "nato", "bluefor", "redfor", "insurgency", "pmc", "germany", "militia"};
        for (String faction : factions) {
            List<RegistryObject<SoundEvent>> screams = new ArrayList<>();
            for (int i = 1; i <= 3; i++) {
                screams.add(registerSoundEvent(faction + "_scream_" + i));
            }
            FACTION_SCREAMS.put(faction, screams);
        }
    }
    private static RegistryObject<SoundEvent> registerSoundEvent(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(new ResourceLocation("aas", name)));
    }

    public static void register(IEventBus eventBus) {
        SOUND_EVENTS.register(eventBus);
    }
}