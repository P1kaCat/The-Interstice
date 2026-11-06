package com.interstice.world.wheat;

import com.interstice.util.Hash;

/**
 * Level 02, The Wheat Farm: a long straight farm track running along +Z through endless wheat, fenced on both
 * sides, under a fixed golden evening. Pure functions of coordinates (the layout is the same in every world, the
 * landscape's small irregularities are hashed from position), so chunk borders cannot show seams.
 * <ul>
 *   <li>the track: x -1..1, from z 0 to {@link #ROUTE_END}, perfectly flat; fences at x +-3 (with a few gaps);</li>
 *   <li>the fields: everywhere else, a field surface ({@code wheat_field}) on gently rolling ground; the stalks
 *       themselves are drawn by the client (WheatRenderer) near the player;</li>
 *   <li>wind turbines on both sides at long intervals ({@link #turbine});</li>
 *   <li>the rift at the end of the track ({@link #RIFT_Z}).</li>
 * </ul>
 */
public final class WheatLayout {
    private WheatLayout() {}

    public static final int GROUND_Y = 64;
    public static final int ROUTE_HALF = 1, FENCE_X = 3;
    public static final int ROUTE_END = 1600;
    /** The farmhouse at the end of the track: front wall at HOUSE_Z, x -5..5, 11 deep, 8 high walls, gable roof. */
    public static final int HOUSE_Z = ROUTE_END + 8, HOUSE_HALF = 5, HOUSE_DEPTH = 11, HOUSE_WALL = 8;
    /** The rift: in front of the house's fake door (x 0), at this z. */
    public static final int RIFT_Z = HOUSE_Z - 1;
    public static final double SPAWN_X = 0.5, SPAWN_Z = 2.5;
    /**
     * The wind farm: TURBINE_ROWS rows on each side (from ~70 to ~1000 blocks out), turbines every TURBINE_CELL blocks
     * along z in each row (rows staggered), from TURBINE_Z0 to TURBINE_Z1: it fills the landscape to the horizon.
     */
    public static final int TURBINE_CELL = 140, TURBINE_ROWS = 7, TURBINE_Z0 = -500, TURBINE_Z1 = ROUTE_END + 700;
    public static final double TURBINE_HEIGHT = 82, BLADE_LENGTH = 36;

    /** Ground surface height (the top block is at this y - 1). */
    public static int groundY(int x, int z) {
        return GROUND_Y;   // perfectly flat: block-sized rolls showed as dark steps across the fields
    }

    /** The farmhouse's plot (with its porch and eaves): no wheat there. */
    public static boolean isHouse(int x, int z) {
        return Math.abs(x) <= HOUSE_HALF + 2 && z >= HOUSE_Z - 3 && z <= HOUSE_Z + HOUSE_DEPTH + 1;
    }

    public static boolean onRoute(int x, int z) {
        return Math.abs(x) <= ROUTE_HALF && z >= -2 && z <= RIFT_Z + 2;
    }

    /** The track and its fenced verges, from the start to the house: no wheat there (behind the start the field closes over). */
    public static boolean inTrackStrip(int x, int z) {
        return Math.abs(x) <= FENCE_X && z >= -2 && z <= HOUSE_Z;
    }

    public static boolean isFence(int x, int z) {
        if (Math.abs(x) != FENCE_X || z < 0 || z > ROUTE_END) return false;
        // a few missing stretches, as on any old farm
        return Hash.unit(Hash.of(911, x, Math.floorDiv(z, 9)), 0) > 0.08;
    }

    /** The turbine of cell c in this row on this side (null if that one is missing): its base position. */
    public static int[] turbine(int c, int side, int row) {
        long h = Hash.of(913, c, side * 16 + row);
        if (Hash.unit(h, 0) < 0.15) return null;
        int x = side * (70 + row * 135 + (int) (Hash.unit(h, 1) * 50));
        int z = c * TURBINE_CELL + (row * 47) % TURBINE_CELL + (int) (Hash.unit(h, 2) * 40);
        if (z < TURBINE_Z0 || z > TURBINE_Z1) return null;
        return new int[]{x, z};
    }

    /** Deterministic phase (radians) of a turbine's rotor, so every client sees the same blade positions. */
    public static double turbinePhase(int c, int side, int row) {
        return Hash.unit(Hash.of(914, c, side * 16 + row), 0) * Math.PI * 2;
    }

    /** Value noise in [-1, 1], smooth (bilinear on hashed lattice values with smoothstep). */
    private static double smooth(double x, double z, int salt) {
        int x0 = (int) Math.floor(x), z0 = (int) Math.floor(z);
        double fx = x - x0, fz = z - z0;
        fx = fx * fx * (3 - 2 * fx);
        fz = fz * fz * (3 - 2 * fz);
        double a = lattice(x0, z0, salt), b = lattice(x0 + 1, z0, salt), c = lattice(x0, z0 + 1, salt), d = lattice(x0 + 1, z0 + 1, salt);
        return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fz;
    }

    private static double lattice(int x, int z, int salt) {
        return Hash.unit(Hash.of(912 + salt, x, z), 0) * 2 - 1;
    }
}
