package com.interstice.world;

import com.interstice.util.Hash;
import com.interstice.util.Noise;

/**
 * The finite Corridor (generation version 2): a seed-determined length of 7,000 to 10,000 blocks along Z,
 * closed at both ends by bulkheads, divided into four major regions ("biomes") in a fixed order of decay:
 * the Forgotten District, the Ironworks, the Contaminated Ward and the Dead Expanse.
 * <p>
 * Pure function of the layout seed, shared by the server generator and the client (ambience, chains, fog),
 * like the rest of {@link IntersticeLayout}. Each region is at least {@link #MIN_REGION} blocks long outside
 * its transition zones; between two regions a {@link #TRANSITION}-block zone mixes their buildings.
 */
public final class CorridorPlan {
    public enum Region {
        FORGOTTEN_DISTRICT("forgotten_district"),
        IRONWORKS("ironworks"),
        CONTAMINATED_WARD("contaminated_ward"),
        DEAD_EXPANSE("dead_expanse");

        public final String id;

        Region(String id) {
            this.id = id;
        }
    }

    public static final int MIN_LENGTH = 7000, MAX_LENGTH = 10000;
    /** Minimum length of a region outside its transition zones. */
    public static final int MIN_REGION = 1000;
    /** Length of the mixed zone between two regions (centred on the boundary). */
    public static final int TRANSITION = 400;
    /** The player arrives near z = 0: the Corridor starts this far behind the arrival point. */
    public static final int START_Z = -320;
    /** Thickness of the bulkheads that close both ends. */
    public static final int BULKHEAD = 40;

    public final int length, startZ, endZ;
    /** Region boundaries: region i spans [bounds[i], bounds[i + 1]). */
    private final int[] bounds;
    private final long seed;

    public CorridorPlan(long seed) {
        this.seed = seed;
        this.length = MIN_LENGTH + (int) (Hash.unit(Hash.of(seed, 1)) * (MAX_LENGTH - MIN_LENGTH + 1));
        this.startZ = START_Z;
        this.endZ = startZ + length;
        Region[] r = Region.values();
        int n = r.length;
        // each region: its minimum (plus half a transition at each inner end), the rest shared by random weights
        int[] min = new int[n];
        int total = 0;
        for (int i = 0; i < n; i++) {
            min[i] = MIN_REGION + (i > 0 ? TRANSITION / 2 : 0) + (i < n - 1 ? TRANSITION / 2 : 0);
            total += min[i];
        }
        int extra = Math.max(0, length - total);
        double[] w = new double[n];
        double sw = 0;
        for (int i = 0; i < n; i++) {
            w[i] = 0.6 + Hash.unit(Hash.of(seed, 10 + i));
            sw += w[i];
        }
        bounds = new int[n + 1];
        bounds[0] = startZ;
        int z = startZ;
        for (int i = 0; i < n; i++) {
            z += min[i] + (i == n - 1 ? 0 : (int) (extra * w[i] / sw));
            bounds[i + 1] = i == n - 1 ? endZ : z;
        }
    }

    public boolean inside(double z) {
        return z >= startZ && z <= endZ;
    }

    /** Blocks beyond either end (0 inside the Corridor). */
    public double beyond(double z) {
        return Math.max(0, Math.max(startZ - z, z - endZ));
    }

    public int regionStart(Region r) {
        return bounds[r.ordinal()];
    }

    public int regionEnd(Region r) {
        return bounds[r.ordinal() + 1];
    }

    /** Region at z (clamped to the first and last region beyond the ends). */
    public Region regionAt(double z) {
        Region[] r = Region.values();
        for (int i = 0; i < r.length; i++) if (z < bounds[i + 1]) return r[i];
        return r[r.length - 1];
    }

    /**
     * Region used to pick the content of something placed at z (a building lot): inside a transition zone the
     * boundary is jittered per lot, so the two regions interleave instead of meeting on a straight line.
     */
    public Region regionForLot(double z, long lotHash) {
        double j = (Hash.unit(lotHash, 77) - 0.5) * TRANSITION + 18 * Noise.value1(seed, z / 90.0);
        return regionAt(z + j);
    }

    /**
     * Region of one floor column: the boundary wanders in blobs (low-frequency noise) and frays at the edges (per
     * column dither), across the whole transition zone, so one floor material bleeds into the next.
     */
    public Region regionForColumn(int x, int z) {
        double blob = Noise.value2(Hash.of(seed, 5), x / 11.0, z / 11.0);
        double fray = Hash.unit(Hash.of(seed, 6), x, z) - 0.5;
        return regionAt(z + (blob * 0.7 + fray * 0.6) * TRANSITION * 0.55);
    }

    /** 0 at the start of the region, 1 at its end (used for gradual changes such as the Ward's infestation). */
    public double progress(Region r, double z) {
        double a = regionStart(r), b = regionEnd(r);
        return Math.max(0, Math.min(1, (z - a) / Math.max(1, b - a)));
    }

    /**
     * Weight of each region at z, smooth across the transition zones (sums to 1). For the client's soundscape and
     * atmosphere: crossing into a new region fades the old one out over the transition.
     */
    public float[] weights(double z) {
        Region[] r = Region.values();
        float[] out = new float[r.length];
        for (int i = 0; i < r.length; i++) {
            double lo = i == 0 ? Double.NEGATIVE_INFINITY : bounds[i];
            double hi = i == r.length - 1 ? Double.POSITIVE_INFINITY : bounds[i + 1];
            double a = ramp(z, lo - TRANSITION / 2.0, lo + TRANSITION / 2.0);
            double b = 1 - ramp(z, hi - TRANSITION / 2.0, hi + TRANSITION / 2.0);
            out[i] = (float) Math.max(0, Math.min(a, b));
        }
        float s = 0;
        for (float f : out) s += f;
        if (s > 0) for (int i = 0; i < out.length; i++) out[i] /= s;
        return out;
    }

    private static double ramp(double z, double a, double b) {
        if (Double.isInfinite(a)) return 1;
        if (z <= a) return 0;
        if (z >= b) return 1;
        double t = (z - a) / (b - a);
        return t * t * (3 - 2 * t);
    }

    /** Pure length of a region outside the transition zones. */
    public int pureLength(Region r) {
        int i = r.ordinal(), n = Region.values().length;
        return regionEnd(r) - regionStart(r) - (i > 0 ? TRANSITION / 2 : 0) - (i < n - 1 ? TRANSITION / 2 : 0);
    }

    // ------------------------------------------------------------- the exit

    /** Half width and height of the exit doorway cut into the far bulkhead. */
    public static final int EXIT_HALF_WIDTH = 4, EXIT_HEIGHT = 12, EXIT_DEPTH = 8;

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder("Corridor ").append(length).append(" blocks [").append(startZ).append(", ").append(endZ).append("]");
        for (Region r : Region.values()) b.append("; ").append(r.id).append(' ').append(regionStart(r)).append("..").append(regionEnd(r));
        return b.toString();
    }
}
