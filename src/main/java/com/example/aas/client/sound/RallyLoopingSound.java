package com.example.aas.client.sound;

import com.example.aas.block.RallyPointBlockEntity;
import com.example.aas.sound.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

public class RallyLoopingSound extends AbstractTickableSoundInstance {
    private final RallyPointBlockEntity rally;

    public RallyLoopingSound(RallyPointBlockEntity rally) {
        super(ModSounds.RALLY_IDLE.get(), SoundSource.BLOCKS, RandomSource.create());
        this.rally = rally;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.4F; // Громкость
        this.x = rally.getBlockPos().getX() + 0.5;
        this.y = rally.getBlockPos().getY() + 0.5;
        this.z = rally.getBlockPos().getZ() + 0.5;
    }

    @Override
    public void tick() {
        if (this.rally.isRemoved()) {
            this.stopSound(); // Останавливаемся, если блок удален
        }
    }

    // НОВЫЙ МЕТОД: позволяет остановить звук извне
    public void stopSound() {
        this.stop();
    }
}