// PATH: src\main\java\com\example\aas\client\sound\HubLoopingSound.java
package com.example.aas.client.sound;

import com.example.aas.block.HubBlock;
import com.example.aas.block.HubBlockEntity;
import com.example.aas.config.AASConfig; // <-- Import Config
import com.example.aas.sound.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

public class HubLoopingSound extends AbstractTickableSoundInstance {
    private final HubBlockEntity hub;

    public HubLoopingSound(HubBlockEntity hub) {
        super(ModSounds.HUB_IDLE.get(), SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        this.hub = hub;

        this.looping = true;
        this.delay = 0;

        // === ПРИМЕНЕНИЕ КОНФИГА РАДИУСА ЗВУКА ===
        // В Minecraft громкость 1.0 = ~16 блоков.
        // Чтобы сделать радиус X, громкость = X / 16.0
        float radius = AASConfig.HUB_SOUND_RADIUS.get().floatValue();
        this.volume = radius / 16.0F;

        this.pitch = 1.0F;

        this.x = hub.getBlockPos().getX() + 0.5;
        this.y = hub.getBlockPos().getY() + 0.5;
        this.z = hub.getBlockPos().getZ() + 0.5;
    }

    @Override
    public void tick() {
        if (this.hub.isRemoved()) {
            this.stop();
            return;
        }

        if (!this.hub.getBlockState().getValue(HubBlock.CONSTRUCTED)) {
            this.stop();
            return;
        }

        // Опционально: Можно обновлять громкость динамически, если конфиг поменяли в игре
        // но для производительности лучше оставить в конструкторе или делать проверку редко.
    }

    public void stopSound() {
        this.stop();
    }
}