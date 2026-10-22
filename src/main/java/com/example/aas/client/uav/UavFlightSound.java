package com.example.aas.client.uav;

import com.example.aas.entity.ReconUavEntity;
import com.example.aas.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

/** Looping flight sound that follows the UAV entity and stops when it is removed (destroyed / untracked). */
public class UavFlightSound extends AbstractTickableSoundInstance {
    /** > 1 widens the audible range (linear attenuation, ~16 * volume blocks). */
    private static final float VOLUME = 5.0f;

    private final ReconUavEntity uav;

    private UavFlightSound(ReconUavEntity uav) {
        super(ModSounds.UAV_FLIGHT.get(), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.uav = uav;
        this.looping = true;
        this.delay = 0;
        this.volume = VOLUME;
        this.pitch = 1.0f;
        this.x = uav.getX();
        this.y = uav.getY();
        this.z = uav.getZ();
    }

    /** Called from the client-side tick of the entity. */
    public static void start(ReconUavEntity uav) {
        Minecraft.getInstance().getSoundManager().play(new UavFlightSound(uav));
    }

    @Override
    public boolean canStartSilent() { return true; }

    @Override
    public void tick() {
        if (uav.isRemoved() || !uav.isAlive()) {
            this.stop();
            return;
        }
        this.x = uav.getX();
        this.y = uav.getY();
        this.z = uav.getZ();
        // the person looking through the UAV does not hear it
        this.volume = UavViewClient.isViewing() ? 0.0f : VOLUME;
    }
}
