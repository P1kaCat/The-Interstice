package com.interstice.entity.species;

import net.minecraft.util.RandomSource;

import java.util.Arrays;
import java.util.Map;

/**
 * A weighted species distribution (one weight per {@link Species}, normalised to 1).
 * <p>
 * Used for the Stampede composition (server picks real runners from it; the same weights are sent to
 * clients, which assign species to visual horde instances deterministically from their hash) and for
 * the ambient wall population. Weights come from the config maps; nothing else hardcodes percentages.
 */
public final class SpeciesMix {
    private final float[] w = new float[Species.values().length];
    /** Species boosted for this event (a "surge"), or null. */
    public final Species surge;

    private SpeciesMix(float[] weights, Species surge) {
        System.arraycopy(weights, 0, w, 0, Math.min(weights.length, w.length));
        this.surge = surge;
        normalise();
    }

    public static SpeciesMix of(float[] weights) {
        return new SpeciesMix(weights, null);
    }

    /** Base weights from a config map (species id -> weight); species missing from the map use their default. */
    public static SpeciesMix fromConfig(Map<String, Float> map, boolean stampede) {
        float[] w = new float[Species.values().length];
        for (Species s : Species.values()) {
            Float v = map == null ? null : map.get(s.id);
            w[s.ordinal()] = Math.max(0f, v != null ? v : stampede ? s.t.stampedeWeight : s.t.stampedeWeight);
        }
        return new SpeciesMix(w, null);
    }

    /**
     * Per-event variation: every weight is multiplied by exp(N(0, variation)); with probability
     * {@code surgeChance} one species other than the Adherent is multiplied by {@code surgeFactor}.
     */
    public SpeciesMix vary(RandomSource r, float variation, float surgeChance, float surgeFactor) {
        float[] v = w.clone();
        for (int i = 0; i < v.length; i++) v[i] *= (float) Math.exp(r.nextGaussian() * variation);
        Species surge = null;
        if (r.nextFloat() < surgeChance) {
            surge = Species.values()[1 + r.nextInt(Species.values().length - 1)];
            v[surge.ordinal()] *= surgeFactor;
        }
        return new SpeciesMix(v, surge);
    }

    private void normalise() {
        float sum = 0;
        for (float x : w) sum += x;
        if (sum <= 0) {
            Arrays.fill(w, 0f);
            w[Species.ADHERENT.ordinal()] = 1f;
            return;
        }
        for (int i = 0; i < w.length; i++) w[i] /= sum;
    }

    public float weight(Species s) {
        return w[s.ordinal()];
    }

    public float[] weights() {
        return w.clone();
    }

    /** Deterministic pick from u in [0, 1): the client and server agree for the same u. */
    public Species pick(double u) {
        double acc = 0;
        for (Species s : Species.values()) {
            acc += w[s.ordinal()];
            if (u < acc) return s;
        }
        return Species.ADHERENT;
    }

    public Species pick(RandomSource r) {
        return pick(r.nextDouble());
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        for (Species s : Species.values()) {
            if (b.length() > 0) b.append(", ");
            b.append(s.id).append(' ').append(Math.round(w[s.ordinal()] * 1000) / 10f).append('%');
        }
        if (surge != null) b.append(" (surge: ").append(surge.id).append(')');
        return b.toString();
    }
}
