package com.interstice.client.audio;

import com.interstice.Interstice;
import com.interstice.client.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/**
 * Plays the voice: listener-relative, a few centimetres from one ear, dry
 * (EFX skips relative sources), and ducks the ambience around it. The optional
 * subtitle is drawn by the HUD as a faint lowercase line, never as an objective.
 */
public final class VoicePlayer {
    private static final RandomSource rng = RandomSource.create();
    private static long playedAt = Long.MIN_VALUE;
    public static String subtitle = "";
    public static long subtitleAt;

    private VoicePlayer() {}

    public static void play(String line, float volume) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float v = volume * ClientConfig.INSTANCE.voiceVolume;
        float side = rng.nextBoolean() ? 0.35f : -0.35f;
        mc.getSoundManager().play(new SimpleSoundInstance(Interstice.id("voice." + line), SoundSource.VOICE, v, 1f, rng, false, 0,
                SoundInstance.Attenuation.NONE, side, 0.05, 0.15, true));
        playedAt = mc.level.getGameTime();
        subtitle = net.minecraft.network.chat.Component.translatable("subtitles.interstice.voice." + line).getString();
        subtitleAt = playedAt;
    }

    /** Ambience multiplier: dips while the voice speaks. */
    public static float duck() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return 1f;
        long dt = mc.level.getGameTime() - playedAt;
        if (dt < 0 || dt > 80) return 1f;
        return dt < 50 ? 0.35f : 0.35f + (dt - 50) / 30f * 0.65f;
    }
}
