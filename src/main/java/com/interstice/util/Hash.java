package com.interstice.util;

/**
 * Stateless, platform-independent hashing used by every deterministic system
 * (world generation, chain placement, horde simulation). Never use java.util.Random
 * or Minecraft noise here: the client must reproduce identical values from the
 * synchronized layout seed.
 */
public final class Hash {
    private Hash() {}

    public static long mix(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }

    public static long of(long seed, long a) {
        return mix(seed ^ mix(a + 0x9E3779B97F4A7C15L));
    }

    public static long of(long seed, long a, long b) {
        return mix(of(seed, a) ^ mix(b * 0xC2B2AE3D27D4EB4FL + 0x165667B19E3779F9L));
    }

    public static long of(long seed, long a, long b, long c) {
        return mix(of(seed, a, b) ^ mix(c * 0x27D4EB2F165667C5L + 0x85EBCA77C2B2AE63L));
    }

    /** Uniform double in [0, 1). */
    public static double unit(long h) {
        return (h >>> 11) * 0x1.0p-53;
    }

    public static double unit(long seed, long a) { return unit(of(seed, a)); }
    public static double unit(long seed, long a, long b) { return unit(of(seed, a, b)); }
    public static double unit(long seed, long a, long b, long c) { return unit(of(seed, a, b, c)); }

    /** Uniform int in [min, max] inclusive. */
    public static int range(long h, int min, int max) {
        return min + (int) Math.floor(unit(h) * (max - min + 1));
    }

    public static double signed(long h) {
        return unit(h) * 2.0 - 1.0;
    }
}
