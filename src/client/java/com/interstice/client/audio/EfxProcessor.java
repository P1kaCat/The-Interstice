package com.interstice.client.audio;

import com.interstice.Interstice;
import com.interstice.client.ClientState;
import com.interstice.client.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTEfx;

/**
 * OpenAL EFX processing for sounds played inside the dimension:
 * <ul>
 *   <li><b>Occlusion low-pass</b>: a ray from the listener to the source counts
 *       solid blocks; each one lowers gain and high-frequency gain.</li>
 *   <li><b>Air absorption</b>: distant sources lose high frequencies.</li>
 *   <li><b>Reverb</b>: a long, dark EAX reverb (vast concrete space) on an
 *       auxiliary send.</li>
 * </ul>
 * Runs on Minecraft's sound thread at source start. Parameters are applied once
 * per source (moving loops keep their initial filter). If the device lacks
 * ALC_EXT_EFX, or anything fails, processing disables itself permanently and
 * vanilla audio is untouched.
 */
public final class EfxProcessor {
    private static boolean initialized, available;
    private static int slot, effect, filter;
    public static volatile int processed;

    private EfxProcessor() {}

    private static void init() {
        initialized = true;
        try {
            long ctx = ALC10.alcGetCurrentContext();
            long device = ALC10.alcGetContextsDevice(ctx);
            if (!ALC10.alcIsExtensionPresent(device, "ALC_EXT_EFX")) {
                Interstice.LOG.info("ALC_EXT_EFX not available: environmental audio uses volume only");
                return;
            }
            AL10.alGetError(); // clear any stale error before probing EAX reverb support
            slot = EXTEfx.alGenAuxiliaryEffectSlots();
            effect = EXTEfx.alGenEffects();
            EXTEfx.alEffecti(effect, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_EAXREVERB);
            if (AL10.alGetError() != AL10.AL_NO_ERROR) {
                EXTEfx.alEffecti(effect, EXTEfx.AL_EFFECT_TYPE, EXTEfx.AL_EFFECT_REVERB);
                EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_DECAY_TIME, 7.5f);
                EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_DENSITY, 1f);
                EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_GAIN, 0.32f);
                EXTEfx.alEffectf(effect, EXTEfx.AL_REVERB_GAINHF, 0.35f);
            } else {
                EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_DENSITY, 1f);
                EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_DIFFUSION, 0.95f);
                EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_GAIN, 0.32f);
                EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_GAINHF, 0.3f);
                EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_DECAY_TIME, 8.0f);
                EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_DECAY_HFRATIO, 0.45f);
                EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_REFLECTIONS_GAIN, 0.6f);
                EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_REFLECTIONS_DELAY, 0.12f);
                EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_LATE_REVERB_GAIN, 1.1f);
                EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_LATE_REVERB_DELAY, 0.06f);
                EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_ECHO_TIME, 0.25f);
                EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_ECHO_DEPTH, 0.4f);
                EXTEfx.alEffectf(effect, EXTEfx.AL_EAXREVERB_AIR_ABSORPTION_GAINHF, 0.99f);
            }
            EXTEfx.alAuxiliaryEffectSloti(slot, EXTEfx.AL_EFFECTSLOT_EFFECT, effect);
            filter = EXTEfx.alGenFilters();
            EXTEfx.alFilteri(filter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
            available = AL10.alGetError() == AL10.AL_NO_ERROR;
            Interstice.LOG.info("Interstice EFX audio {}", available ? "enabled" : "failed to initialise");
        } catch (Throwable t) {
            available = false;
            Interstice.LOG.warn("EFX initialisation failed; environmental audio disabled", t);
        }
    }

    public static boolean available() {
        return available;
    }

    public static void onSourcePlay(int source) {
        try {
            if (!ClientState.inInterstice || !ClientConfig.INSTANCE.environmentalAudio) {
                if (available) clear(source);
                return;
            }
            if (!initialized) init();
            if (!available) return;
            boolean relative = AL10.alGetSourcei(source, AL10.AL_SOURCE_RELATIVE) == AL10.AL_TRUE;
            if (relative) {
                // UI, voice, close ambience beds: dry and intimate
                clear(source);
                return;
            }
            float[] x = new float[1], y = new float[1], z = new float[1];
            AL10.alGetSource3f(source, AL10.AL_POSITION, x, y, z);
            Minecraft mc = Minecraft.getInstance();
            ClientLevel level = mc.level;
            Vec3 ear = mc.gameRenderer.getMainCamera().getPosition();
            Vec3 src = new Vec3(x[0], y[0], z[0]);
            int occl = level == null ? 0 : occlusion(level, ear, src);
            double dist = ear.distanceTo(src);
            float gain = Math.max(0.25f, 1f - occl * 0.09f);
            float gainHf = (float) (Math.exp(-0.5 * occl) * Math.exp(-dist / 260.0));
            EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAIN, gain);
            EXTEfx.alFilterf(filter, EXTEfx.AL_LOWPASS_GAINHF, Math.max(0.02f, Math.min(1f, gainHf)));
            AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, filter);
            AL11.alSource3i(source, EXTEfx.AL_AUXILIARY_SEND_FILTER, slot, 0, EXTEfx.AL_FILTER_NULL);
            processed++;
        } catch (Throwable t) {
            available = false;
            Interstice.LOG.warn("EFX processing failed; disabling", t);
        }
    }

    private static void clear(int source) {
        AL10.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, EXTEfx.AL_FILTER_NULL);
        AL11.alSource3i(source, EXTEfx.AL_AUXILIARY_SEND_FILTER, EXTEfx.AL_EFFECTSLOT_NULL, 0, EXTEfx.AL_FILTER_NULL);
    }

    /** Counts solid blocks between listener and source (max 8), stepping one block at a time. */
    private static int occlusion(ClientLevel level, Vec3 a, Vec3 b) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 1.5) return 0;
        int steps = (int) Math.min(len, 96);
        Vec3 step = d.scale(1.0 / steps);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int count = 0;
        long last = Long.MIN_VALUE;
        double px = a.x, py = a.y, pz = a.z;
        for (int i = 1; i < steps; i++) {
            px += step.x; py += step.y; pz += step.z;
            p.set(px, py, pz);
            long key = p.asLong();
            if (key == last) continue;
            last = key;
            if (!level.isLoaded(p)) continue;
            if (level.getBlockState(p).isSolidRender(level, p) && ++count >= 8) break;
        }
        return count;
    }
}
