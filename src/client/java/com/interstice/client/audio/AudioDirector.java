package com.interstice.client.audio;

import com.interstice.client.ClientState;
import com.interstice.client.config.ClientConfig;
import com.interstice.client.effects.DustEffects;
import com.interstice.client.render.chains.ChainRenderer;
import com.interstice.client.render.creatures.HordeRenderer;
import com.interstice.client.render.veil.LampLightManager;
import com.interstice.event.stampede.StampedePhase;
import com.interstice.network.ModNetwork;
import com.interstice.registry.ModSounds;
import com.interstice.util.Noise;
import com.interstice.world.IntersticeLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Environmental audio director.
 * <p>
 * Layers: a long drone bed, moving air, electrical hum that follows the
 * nearest fixture, a sub-bass premonition tone and a positional horde roar
 * that travels with the pack. On top, one-shots are scheduled stochastically
 * with a slowly wandering density (so pacing never settles), each placed at a
 * plausible origin: impacts far along the walls (also kicking nearby chains),
 * chain groans at the closest chain point (scaled by that chain's actual
 * motion), structural groans inside the nearest wall, debris high above the
 * listener (with matching falling dust), distant scuttling on the walls,
 * and rare unexplained sounds behind the listener. The director also opens
 * occasional windows of near-total silence.
 */
public final class AudioDirector {
    private static final RandomSource rng = RandomSource.create();
    private static LoopSound drone, air, hum, premonition, horde;
    private static long silenceUntil;
    private static long nextSilenceRoll;
    private static boolean quakeSounded;
    private static long lastQuakeEvent = Long.MIN_VALUE;
    public static int oneShots;
    public static float silenceLevel;

    private AudioDirector() {}

    public static void stopAll() {
        for (LoopSound l : new LoopSound[]{drone, air, hum, premonition, horde}) if (l != null) l.finish();
        drone = air = hum = premonition = horde = null;
    }

    private static float amb() {
        return ClientConfig.INSTANCE.ambienceVolume;
    }

    /** 0..1 ducking factor applied to all ambience. */
    private static float duck() {
        float s = Math.max(ClientState.silence, silenceLevel);
        float voice = VoicePlayer.duck();
        return (1f - s) * voice;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || !ClientState.inInterstice) {
            stopAll();
            return;
        }
        long now = mc.level.getGameTime();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();

        // director-owned silence windows
        if (now > nextSilenceRoll) {
            nextSilenceRoll = now + 1200 + rng.nextInt(2400);
            if (rng.nextFloat() < 0.18f && !ClientState.phase.between(StampedePhase.EARTHQUAKE, StampedePhase.CREATURE_FALLS)) {
                silenceUntil = now + 300 + rng.nextInt(500);
            }
        }
        float targetSilence = now < silenceUntil ? 0.92f : 0f;
        silenceLevel += (targetSilence - silenceLevel) * 0.02f;

        ensureLoops(mc);
        if (horde != null) {
            // the roar is anchored where the pack is relative to the listener
            double front = hordeFrontZ();
            horde.tx = cam.x;
            horde.ty = cam.y + 25;
            horde.tz = Math.abs(front - cam.z) < 60 ? cam.z + Math.signum(front - cam.z) * 6 : front;
        }

        if (duck() < 0.15f) return; // silence: nothing else plays
        scheduleOneShots(mc, cam, now);
    }

    /** Coordinated sound for server events, dispatched as the packet arrives. */
    public static void onEnvEvent(ModNetwork.EnvEvent e) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        switch (e.kind()) {
            case ModNetwork.EnvEvent.QUAKE -> {
                play(ModSounds.EVENT_QUAKE, cam.x, cam.y - 10, cam.z, 2.5f * e.intensity() + 0.6f, 0.9f + rng.nextFloat() * 0.1f, true);
                play(ModSounds.ENV_RUMBLE, cam.x, cam.y - 6, cam.z, 1.5f * e.intensity(), 0.7f, true);
                ChainRenderer.kick(cam.x, cam.y, cam.z, 1.2f * e.intensity());
            }
            case ModNetwork.EnvEvent.DISTANT_IMPACT -> {
                play(ModSounds.ENV_IMPACT_FAR, e.x(), e.y(), e.z(), 6f * e.intensity(), 0.7f + rng.nextFloat() * 0.2f, false);
                ChainRenderer.kick(e.x(), e.y(), e.z(), 1.6f * e.intensity());
            }
            case ModNetwork.EnvEvent.CHAIN_SHUDDER -> {
                ChainRenderer.kick(cam.x, cam.y, cam.z, 0.9f * e.intensity());
                if (ChainRenderer.nearDist < 200) play(ModSounds.ENV_CHAIN_IMPACT, ChainRenderer.nearX, ChainRenderer.nearY, ChainRenderer.nearZ, 5f, 0.5f, false);
            }
            case ModNetwork.EnvEvent.STARE -> play(ModSounds.ENV_UNEXPLAINED, e.x(), e.y(), e.z(), 3f, 0.6f, false);
            case ModNetwork.EnvEvent.LIGHT_FAILURE -> play(ModSounds.ENV_LAMP_FAIL, cam.x, cam.y + 4, cam.z, 0.8f, 1f, false);
            case ModNetwork.EnvEvent.FLEE -> play(ModSounds.ENV_SCUTTLE_FAR, cam.x + 12, cam.y + 15, cam.z, 3f, 1.2f, false);
            default -> {
            }
        }
    }

    /** Coordinated transitions on Stampede phase changes. */
    public static void onPhase(StampedePhase phase) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        switch (phase) {
            case PREMONITION -> play(ModSounds.ENV_RUMBLE, cam.x, cam.y - 20, cam.z + ClientState.stampedeDirection * -150, 4f, 0.5f, false);
            case STRUCTURAL_DISTURBANCE -> {
                play(ModSounds.ENV_STRUCTURE_GROAN, cam.x, cam.y + 30, cam.z, 5f, 0.5f, false);
                play(ModSounds.ENV_IMPACT_FAR, cam.x, cam.y + 200, cam.z - ClientState.stampedeDirection * 120, 8f, 0.45f, false);
            }
            default -> {
            }
        }
    }

    private static void ensureLoops(Minecraft mc) {
        if (drone == null) drone = start(mc, new LoopSound(ModSounds.AMBIENT_DRONE, () -> 0.55 * amb() * duck(), false));
        if (air == null) air = start(mc, new LoopSound(ModSounds.AMBIENT_AIR, () -> (0.35 + 0.25 * ClientState.quake) * amb() * duck(), false));
        if (hum == null) hum = start(mc, new LoopSound(ModSounds.AMBIENT_HUM, () -> {
            double d = LampLightManager.nearestFixture;
            double near = d < 12 ? 1 - d / 12 : 0;
            return near * 0.35 * amb() * duck() * (1 - ClientState.lightFailure * 0.7);
        }, false));
        if (premonition == null) premonition = start(mc, new LoopSound(ModSounds.EVENT_PREMONITION,
                () -> ClientState.premonition * 0.9 * amb() * (1 - ClientState.chainFreeze), false));
        if (horde == null) horde = start(mc, new LoopSound(ModSounds.EVENT_HORDE, () -> {
            if (!HordeRenderer.hordeVisible()) return 0;
            return (0.4 + 2.4 * ClientState.hordeActive) * amb();
        }, true));
        for (LoopSound l : new LoopSound[]{drone, air, hum, premonition, horde}) {
            if (l != null && !mc.getSoundManager().isActive(l) && l.isStopped()) {
                // restart beds that the engine dropped (e.g. sound reload)
                if (l == drone) drone = null;
                else if (l == air) air = null;
                else if (l == hum) hum = null;
                else if (l == premonition) premonition = null;
                else if (l == horde) horde = null;
            }
        }
    }

    private static LoopSound start(Minecraft mc, LoopSound s) {
        mc.getSoundManager().play(s);
        return s;
    }

    private static double hordeFrontZ() {
        double t = ClientState.time - ClientState.hordeStart;
        // matches HordeRenderer: the pack front reaches the reference point after ~260 ticks
        return HordeRenderer.refZ + ClientState.stampedeDirection * (t - 260) * 0.8;
    }

    private static void scheduleOneShots(Minecraft mc, Vec3 cam, long now) {
        IntersticeLayout L = ClientState.layout;
        double pace = 0.6 + 0.8 * (Noise.value1(77, now / 900.0) * 0.5 + 0.5); // slowly wandering density
        float d = duck();
        float q = ClientState.quake, prem = ClientState.premonition, chain = ClientState.chainExcitation;

        if (chance(pace / (25 * 20.0))) {
            double z = cam.z + (rng.nextBoolean() ? 1 : -1) * (60 + rng.nextDouble() * 160);
            int side = rng.nextBoolean() ? -1 : 1;
            double y = IntersticeLayout.FLOOR_Y + 40 + rng.nextDouble() * 420;
            play(ModSounds.ENV_IMPACT_FAR, L.faceX(side, z), y, z, 5f * d, 0.6f + rng.nextFloat() * 0.3f, false);
            ChainRenderer.kick(L.faceX(side, z), y, z, 0.7f);
        }
        if (ChainRenderer.nearDist < 140 && chance(pace * (1 + chain * 4) / (16 * 20.0))) {
            float energy = Math.min(1.5f, 0.3f + ChainRenderer.nearEnergy);
            play(ModSounds.ENV_CHAIN_GROAN, ChainRenderer.nearX, ChainRenderer.nearY, ChainRenderer.nearZ, 4f * energy * d, 0.5f + rng.nextFloat() * 0.25f, false);
        }
        if (ChainRenderer.nearDist < 120 && ChainRenderer.nearEnergy > 0.9f && chance(1 / 40.0)) {
            play(ModSounds.ENV_CHAIN_IMPACT, ChainRenderer.nearX, ChainRenderer.nearY, ChainRenderer.nearZ, 5f * d, 0.55f + rng.nextFloat() * 0.2f, false);
        }
        if (chance(pace * (1 + q * 6 + prem * 2) / (28 * 20.0))) {
            int side = L.sideOf(cam.x, cam.z);
            double z = cam.z + (rng.nextDouble() - 0.5) * 40;
            play(ModSounds.ENV_STRUCTURE_GROAN, L.faceX(side, z) + side * 4, cam.y + 10 + rng.nextDouble() * 30, z, 3f * d, 0.6f + rng.nextFloat() * 0.3f, false);
        }
        if (chance(pace * (1 + q * 8) / (20 * 20.0))) {
            int side = rng.nextBoolean() ? -1 : 1;
            double z = cam.z + (rng.nextDouble() - 0.5) * 50;
            double y = cam.y + 25 + rng.nextDouble() * 60;
            double x = L.faceX(side, z) - side * 1.5;
            play(ModSounds.ENV_DEBRIS, x, y, z, 2.5f * d, 0.8f + rng.nextFloat() * 0.3f, false);
            DustEffects.burstAt(x, y, z, side, 14);
        }
        if (chance(pace * (1 + ClientState.hordeActive * 6) / (15 * 20.0))) {
            int side = rng.nextBoolean() ? -1 : 1;
            double z = cam.z + (rng.nextBoolean() ? 1 : -1) * (30 + rng.nextDouble() * 60);
            play(ModSounds.ENV_SCUTTLE_FAR, L.faceX(side, z), cam.y + (rng.nextDouble() - 0.3) * 40, z, 2.5f * d, 0.8f + rng.nextFloat() * 0.4f, false);
        }
        if (chance(pace / (70 * 20.0))) {
            Vec3 look = mc.player.getLookAngle();
            double dist = 18 + rng.nextDouble() * 22;
            play(ModSounds.ENV_UNEXPLAINED, cam.x - look.x * dist, cam.y + (rng.nextDouble() - 0.5) * 8, cam.z - look.z * dist, 2f * d, 0.7f + rng.nextFloat() * 0.5f, false);
        }
        if (ClientState.lightFailure > 0.3f && chance(1 / 30.0)) {
            play(ModSounds.ENV_LAMP_FAIL, cam.x + (rng.nextDouble() - 0.5) * 20, cam.y + 3 + rng.nextDouble() * 6, cam.z + (rng.nextDouble() - 0.5) * 20, 1.2f, 0.9f + rng.nextFloat() * 0.3f, false);
        }
    }

    private static boolean chance(double p) {
        return rng.nextDouble() < p;
    }

    public static void play(SoundEvent e, double x, double y, double z, float volume, float pitch, boolean global) {
        if (volume <= 0.01f) return;
        Minecraft mc = Minecraft.getInstance();
        float v = volume * ClientConfig.INSTANCE.ambienceVolume;
        SoundInstance s = new SimpleSoundInstance(e.getLocation(), SoundSource.AMBIENT, v, pitch, rng, false, 0,
                global ? SoundInstance.Attenuation.NONE : SoundInstance.Attenuation.LINEAR, x, y, z, false);
        mc.getSoundManager().play(s);
        oneShots++;
    }
}
