package com.interstice.client;

import com.interstice.event.stampede.StampedePhase;
import com.interstice.network.ModNetwork;
import com.interstice.registry.ModWorld;
import com.interstice.world.IntersticeLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Client mirror of the server's event state plus derived presentation
 * signals. Every client system (fog, lights, chains, audio, horde, shake)
 * reads the same signals, which is what keeps sound, light and motion
 * synchronised during events.
 */
public final class ClientState {
    private ClientState() {}

    public static IntersticeLayout layout;
    public static boolean inInterstice;

    // ---- stampede mirror
    public static StampedePhase phase = StampedePhase.IDLE;
    public static long phaseStart;
    public static int phaseDuration = 1;
    public static long stampedeSeed;
    public static int stampedeDirection = 1;
    public static float stampedeIntensity = 1f;
    /** Species composition of the current Stampede (visual horde instances pick from it by hash). */
    public static com.interstice.entity.species.SpeciesMix stampedeMix = com.interstice.entity.species.SpeciesMix.of(new float[]{1f});
    /** Game time at which the current horde run began (start of HORDE_APPROACH). */
    public static long hordeStart = Long.MIN_VALUE;

    public static final List<ModNetwork.EnvEvent> events = new ArrayList<>();

    // ---- debug
    public static boolean debug;
    public static String serverDebug = "";

    // ---- derived signals (0..1 unless noted), updated every frame
    public static float quake;
    public static float premonition;
    public static float silence;
    public static float chainExcitation;
    public static float chainFreeze;
    public static float lightFailure;
    public static float emergency;
    public static float hordeActive;
    public static float searchTension;
    public static double time;

    public static void onLayout(long seed) {
        if (layout == null || layout.seed() != seed) layout = new IntersticeLayout(seed);
    }

    public static void onStampede(ModNetwork.StampedeSync p) {
        StampedePhase np = StampedePhase.values()[p.phase()];
        if (np == StampedePhase.HORDE_APPROACH || (np.between(StampedePhase.HORDE_PASSAGE, StampedePhase.GROUND_SEARCH) && hordeStart == Long.MIN_VALUE)) {
            // late joiners reconstruct the horde start from the phase timeline
            hordeStart = np == StampedePhase.HORDE_APPROACH ? p.phaseStart() : p.phaseStart() - (np == StampedePhase.HORDE_PASSAGE ? 260 : 780);
        }
        if (np == StampedePhase.IDLE || np == StampedePhase.PREMONITION) hordeStart = Long.MIN_VALUE;
        phase = np;
        phaseStart = p.phaseStart();
        phaseDuration = Math.max(1, p.phaseDuration());
        stampedeSeed = p.seed();
        stampedeDirection = p.direction();
        stampedeIntensity = p.intensity();
        stampedeMix = com.interstice.entity.species.SpeciesMix.of(p.species());
    }

    public static void onEnvEvent(ModNetwork.EnvEvent e) {
        events.add(e);
    }

    public static void reset() {
        layout = null;
        phase = StampedePhase.IDLE;
        hordeStart = Long.MIN_VALUE;
        events.clear();
        quake = premonition = silence = chainExcitation = chainFreeze = lightFailure = emergency = hordeActive = searchTension = 0;
    }

    public static float phaseProgress() {
        return Mth.clamp((float) ((time - phaseStart) / phaseDuration), 0f, 1f);
    }

    public static double phaseTicks() {
        return time - phaseStart;
    }

    /** Envelope 0..1 with linear attack/release fractions. */
    public static float envelope(double t, double duration, double attack, double release) {
        if (t < 0 || t > duration) return 0f;
        double a = duration * attack, r = duration * release;
        if (t < a) return (float) (t / a);
        if (t > duration - r) return (float) ((duration - t) / r);
        return 1f;
    }

    public static void update(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        inInterstice = level != null && ModWorld.isInterstice(level) && layout != null;
        if (level == null) return;
        time = level.getGameTime() + partialTick;

        float q = 0, sil = 0, chain = 0, freeze = 0, fail = 0, emer = 0, horde = 0, prem = 0, search = 0;
        double pt = phaseTicks();
        float pp = phaseProgress();
        switch (phase) {
            case PREMONITION -> {
                prem = pp;
                chain = 0.25f * pp;
                fail = pp > 0.5f ? 0.15f : 0f;
            }
            case STRUCTURAL_DISTURBANCE -> {
                prem = 1f;
                chain = 0.35f;
                double silenceStart = phaseDuration - 70;
                if (pt > silenceStart) {
                    float s = (float) Mth.clamp((pt - silenceStart) / 20.0, 0, 1);
                    sil = s;
                    freeze = s;
                    chain = 0.35f * (1 - s);
                }
            }
            case EARTHQUAKE -> {
                prem = 1f - pp;
                chain = 1f;
                fail = 0.6f;
                emer = Mth.clamp((pp - 0.55f) * 3f, 0, 1);
            }
            case HORDE_APPROACH -> {
                emer = 1f;
                chain = 0.45f;
                horde = pp;
            }
            case HORDE_PASSAGE -> {
                emer = 1f;
                chain = 0.6f;
                horde = 1f;
            }
            case CREATURE_FALLS -> {
                emer = 1f;
                chain = 0.4f * (1 - pp);
                horde = 1f - pp * 0.7f;
            }
            case GROUND_SEARCH -> {
                emer = 0.55f;
                sil = 0.6f;
                search = 1f;
                horde = 0.3f * (1 - pp);
            }
            case AFTERMATH -> {
                emer = 0.55f * (1 - pp);
                sil = 0.6f * (1 - pp);
                search = 1 - pp;
            }
            case RECOVERY -> sil = 0.2f * (1 - pp);
            default -> {
            }
        }

        Iterator<ModNetwork.EnvEvent> it = events.iterator();
        while (it.hasNext()) {
            ModNetwork.EnvEvent e = it.next();
            double t = time - e.startTick();
            if (t > e.duration() + 20) {
                it.remove();
                continue;
            }
            switch (e.kind()) {
                case ModNetwork.EnvEvent.QUAKE -> {
                    float env = envelope(t, e.duration(), 0.15, 0.35);
                    q = Math.max(q, env * e.intensity());
                    chain = Math.max(chain, env * Math.min(1f, e.intensity() * 1.4f));
                    fail = Math.max(fail, env * e.intensity() * 0.8f);
                }
                case ModNetwork.EnvEvent.CHAIN_SHUDDER -> chain = Math.max(chain, envelope(t, e.duration(), 0.05, 0.7) * e.intensity());
                case ModNetwork.EnvEvent.LIGHT_FAILURE -> fail = Math.max(fail, envelope(t, e.duration(), 0.02, 0.2));
                case ModNetwork.EnvEvent.SILENCE -> sil = Math.max(sil, envelope(t, e.duration(), 0.1, 0.3));
                default -> {
                }
            }
        }

        quake = q;
        silence = sil;
        chainExcitation = chain;
        chainFreeze = freeze;
        lightFailure = fail;
        emergency = emer;
        hordeActive = horde;
        premonition = prem;
        searchTension = search;
    }
}
