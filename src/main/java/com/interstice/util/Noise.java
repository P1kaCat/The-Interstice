package com.interstice.util;

/**
 * Small deterministic value-noise helpers built only on {@link Hash}. Values are
 * identical on client and server for the same seed, which is required because
 * the client reconstructs wall geometry, chain anchors and horde lanes locally.
 */
public final class Noise {
    private Noise() {}

    private static double smooth(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    /** 1D value noise in [-1, 1]. */
    public static double value1(long seed, double x) {
        long xi = (long) Math.floor(x);
        double f = smooth(x - xi);
        double a = Hash.signed(Hash.of(seed, xi));
        double b = Hash.signed(Hash.of(seed, xi + 1));
        return a + (b - a) * f;
    }

    /** 2D value noise in [-1, 1]. */
    public static double value2(long seed, double x, double y) {
        long xi = (long) Math.floor(x), yi = (long) Math.floor(y);
        double fx = smooth(x - xi), fy = smooth(y - yi);
        double a = Hash.signed(Hash.of(seed, xi, yi));
        double b = Hash.signed(Hash.of(seed, xi + 1, yi));
        double c = Hash.signed(Hash.of(seed, xi, yi + 1));
        double d = Hash.signed(Hash.of(seed, xi + 1, yi + 1));
        double ab = a + (b - a) * fx;
        double cd = c + (d - c) * fx;
        return ab + (cd - ab) * fy;
    }

    /** 3D value noise in [-1, 1]. */
    public static double value3(long seed, double x, double y, double z) {
        long zi = (long) Math.floor(z);
        double fz = smooth(z - zi);
        double a = value2(Hash.of(seed, zi), x, y);
        double b = value2(Hash.of(seed, zi + 1), x, y);
        return a + (b - a) * fz;
    }

    public static double fbm1(long seed, double x, int octaves) {
        double sum = 0, amp = 1, norm = 0;
        for (int i = 0; i < octaves; i++) {
            sum += value1(Hash.of(seed, i), x) * amp;
            norm += amp;
            amp *= 0.5;
            x *= 2.03;
        }
        return sum / norm;
    }

    public static double fbm2(long seed, double x, double y, int octaves) {
        double sum = 0, amp = 1, norm = 0;
        for (int i = 0; i < octaves; i++) {
            sum += value2(Hash.of(seed, i), x, y) * amp;
            norm += amp;
            amp *= 0.5;
            x *= 2.03;
            y *= 2.03;
        }
        return sum / norm;
    }
}
