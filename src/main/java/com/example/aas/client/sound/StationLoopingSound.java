package com.example.aas.client.sound;

import com.example.aas.block.VehicleStationBlock;
import com.example.aas.block.VehicleStationBlockEntity;
import com.example.aas.sound.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

public class StationLoopingSound extends AbstractTickableSoundInstance {
    private final VehicleStationBlockEntity station;

    public StationLoopingSound(VehicleStationBlockEntity station) {
        super(ModSounds.STATION_IDLE.get(), net.minecraft.sounds.SoundSource.BLOCKS, net.minecraft.util.RandomSource.create());
        this.station = station;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.6F;

        // Обязательно добавьте эти строки:
        this.attenuation = net.minecraft.client.resources.sounds.SoundInstance.Attenuation.LINEAR;
        this.x = (double)station.getBlockPos().getX() + 0.5D;
        this.y = (double)station.getBlockPos().getY() + 0.5D;
        this.z = (double)station.getBlockPos().getZ() + 0.5D;
    }

    @Override
    public void tick() {
        if (this.station.isRemoved() || !this.station.getBlockState().getValue(VehicleStationBlock.CONSTRUCTED)) {
            this.stop();
        }
    }
    public void stopSound() {
        this.stop();
    }
}