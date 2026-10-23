package com.example.aas.client.uav;

import com.example.aas.entity.ReconUavEntity;
import com.example.aas.sound.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/** Looping flight sound that follows the UAV entity and stops when it is removed (destroyed / untracked). */
public class UavFlightSound extends AbstractTickableSoundInstance {
    /**
     * Audible range in blocks. The engine fixes it once when the sound starts (16 * volume at start),
     * so the start volume is RANGE / 16; afterwards the volume is adjusted every tick (see tick()).
     */
    private static final float RANGE = 315f;
    /** The old audible range: up to it the loudness curve stays exactly as it was before. */
    private static final float OLD_RANGE = 80f;
    /** Loudness (0..1) of the quiet "tail" beyond the old range. */
    private static final float TAIL = 0.12f;

    private final ReconUavEntity uav;

    private UavFlightSound(ReconUavEntity uav) {
        super(ModSounds.UAV_FLIGHT.get(), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.uav = uav;
        this.looping = true;
        this.delay = 0;
        this.volume = RANGE / 16.0f;   // only used at start: this is what sets the attenuation range
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
        if (UavViewClient.isViewing()) {
            this.volume = 0.0f;
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        double d = mc.gameRenderer.getMainCamera().getPosition().distanceTo(new Vec3(this.x, this.y, this.z));
        if (d >= RANGE) {
            this.volume = 0.0f;
            return;
        }

        // wanted final loudness: the old curve up to OLD_RANGE, then a quiet tail up to RANGE
        double oldGain = Math.max(0.0, 1.0 - d / OLD_RANGE);
        double tailGain = TAIL * (1.0 - d / RANGE);
        double want = Math.max(oldGain, tailGain);
        // the engine already attenuates linearly up to RANGE, so compensate for it
        double engine = 1.0 - d / RANGE;
        this.volume = (float) Math.min(1.0, want / engine);
    }
}