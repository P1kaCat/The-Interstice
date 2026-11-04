package com.interstice.client.audio;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import java.util.function.DoubleSupplier;

/**
 * A looping bed whose volume (and optionally position) is driven every tick by
 * the audio director. Volume changes are eased so layers cross-fade instead of
 * cutting.
 */
public class LoopSound extends AbstractTickableSoundInstance {
    private final DoubleSupplier target;
    private float current;
    private boolean finished;
    public double tx, ty, tz;
    private final boolean positional;

    public LoopSound(SoundEvent event, DoubleSupplier targetVolume, boolean positional) {
        super(event, SoundSource.AMBIENT, RandomSource.create());
        this.target = targetVolume;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.0001f;
        this.positional = positional;
        this.relative = !positional;
        this.attenuation = positional ? SoundInstance.Attenuation.LINEAR : SoundInstance.Attenuation.NONE;
    }

    public void finish() {
        finished = true;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        float t = finished ? 0f : (float) target.getAsDouble();
        current += (t - current) * 0.06f;
        volume = Math.max(0.0001f, current);
        if (positional) {
            x = tx;
            y = ty;
            z = tz;
        }
        if (finished && current < 0.002f) stop();
    }
}
