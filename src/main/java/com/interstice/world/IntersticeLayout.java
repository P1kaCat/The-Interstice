package com.interstice.world;

import com.interstice.util.Hash;
import com.interstice.util.Noise;

/**
 * The macro-architecture of THE INTERSTICE as pure, deterministic functions of a
 * single layout seed.
 * <p>
 * The corridor runs along the Z axis. Two colossal walls bound it on the west
 * (side = -1) and east (side = +1). Everything here is shared by the server-side
 * chunk generator and by client systems (distant wall rendering, chain placement,
 * horde lanes, audio origins), so it must never touch Minecraft world state.
 * <p>
 * Hierarchy:
 * <ol>
 *   <li>Macro: corridor centre line, half-width, anomaly level ({@link #centerX}, {@link #halfWidth}).</li>
 *   <li>Wall surface: buttresses, recessed panels, ledges ({@link #relief}).</li>
 *   <li>Structures: cell-based features evaluated in {@code world.structures}.</li>
 *   <li>Decoration and atmosphere: lamps, pipes, anomalies.</li>
 * </ol>
 */
public final class IntersticeLayout {
    public static final int MIN_Y = -64;
    public static final int MAX_Y = 319;
    public static final int FLOOR_Y = 64;
    /** Highest Y of block-built walls. Above this the client renders the continuation. */
    public static final int WALL_TOP = 300;
    public static final int WALL_THICKNESS = 26;
    public static final int PIT_BOTTOM = -50;
    /** Virtual height the rendered walls (and hanging chains) continue to. */
    public static final int VIRTUAL_TOP = 1400;

    public static final int WEST = -1;
    public static final int EAST = 1;

    private final long seed;
    private final long sCenter, sWidth, sWidthFine, sButtress, sPanel, sLedge, sFloor, sAnomaly;

    public IntersticeLayout(long seed) {
        this.seed = seed;
        this.sCenter = Hash.of(seed, 101);
        this.sWidth = Hash.of(seed, 102);
        this.sWidthFine = Hash.of(seed, 103);
        this.sButtress = Hash.of(seed, 104);
        this.sPanel = Hash.of(seed, 105);
        this.sLedge = Hash.of(seed, 106);
        this.sFloor = Hash.of(seed, 107);
        this.sAnomaly = Hash.of(seed, 108);
    }

    public long seed() {
        return seed;
    }

    public long sub(long salt) {
        return Hash.of(seed, salt);
    }

    // ------------------------------------------------------------------ macro

    public double centerX(double z) {
        return 7.0 * Noise.fbm1(sCenter, z / 700.0, 2);
    }

    public double halfWidth(double z) {
        return 46.0 + 17.0 * Noise.fbm1(sWidth, z / 540.0, 3) + 4.0 * Noise.value1(sWidthFine, z / 70.0);
    }

    /** X coordinate of the base wall face on the given side (relief excluded). */
    public double faceX(int side, double z) {
        return centerX(z) + side * halfWidth(z);
    }

    /**
     * 0 near the origin, rising to 1 far away. Drives how incoherent the
     * architecture becomes (anomaly framework, relief jitter).
     */
    public double anomalyLevel(double z) {
        double d = Math.abs(z) - 450.0;
        if (d <= 0) return 0.0;
        double n = 0.15 * Noise.value1(sAnomaly, z / 300.0);
        return Math.max(0.0, Math.min(1.0, d / 3600.0 + n));
    }

    /**
     * Depth of the point into the nearer wall: negative inside the corridor,
     * 0 on the base face, positive inside the wall mass.
     */
    public double wallDepth(double x, double z) {
        double c = centerX(z);
        return Math.abs(x - c) - halfWidth(z);
    }

    public int sideOf(double x, double z) {
        return x < centerX(z) ? WEST : EAST;
    }

    // ------------------------------------------------------------ wall relief

    public static final int BUTTRESS_CELL = 36;

    /** Buttress description for the cell containing z on the given side. */
    public Buttress buttress(int side, int cell) {
        long h = Hash.of(sButtress, side, cell);
        int offset = 3 + (int) (Hash.unit(h, 1) * 20);
        int width = 4 + (int) (Hash.unit(h, 2) * 6);
        int protrusion = 2 + (int) (Hash.unit(h, 3) * 5);
        double topRoll = Hash.unit(h, 4);
        int top = topRoll < 0.35 ? WALL_TOP : FLOOR_Y + 40 + (int) (Hash.unit(h, 5) * 200);
        boolean metal = Hash.unit(h, 6) < 0.3;
        return new Buttress(cell * BUTTRESS_CELL + offset, width, protrusion, top, metal);
    }

    public record Buttress(int z0, int width, int protrusion, int top, boolean metal) {
        public boolean containsZ(int z) {
            return z >= z0 && z < z0 + width;
        }
    }

    /**
     * Protrusion of the wall surface towards the corridor (blocks) at the given
     * height and position. Positive = wall extends further into the corridor,
     * negative = recessed panel.
     */
    public int relief(int side, int y, int z) {
        if (y >= WALL_TOP - 8) {
            return 0; // flat crown so the rendered continuation lines up
        }
        int r = 0;
        int cell = Math.floorDiv(z, BUTTRESS_CELL);
        for (int c = cell - 1; c <= cell; c++) {
            Buttress b = buttress(side, c);
            if (b.containsZ(z) && y < b.top) {
                int p = b.protrusion;
                int toTop = b.top - y;
                if (toTop < 6) p = Math.max(1, p * toTop / 6);
                r = Math.max(r, p);
            }
        }
        if (r == 0) {
            // recessed panels on a 24 x 32 grid
            int pz = Math.floorDiv(z, 24), py = Math.floorDiv(y - FLOOR_Y, 32);
            long h = Hash.of(sPanel, side, pz, py);
            if (Hash.unit(h) < 0.32) {
                int lz = Math.floorMod(z, 24), ly = Math.floorMod(y - FLOOR_Y, 32);
                if (lz >= 3 && lz < 21 && ly >= 4 && ly < 28) r = -2;
            }
        }
        // ledges: thin horizontal shelves the creatures use as runways
        int lc = Math.floorDiv(z, 96);
        long lh = Hash.of(sLedge, side, lc);
        for (int i = 0; i < 2; i++) {
            if (Hash.unit(lh, i, 0) < 0.6) {
                int ly = FLOOR_Y + 18 + (int) (Hash.unit(lh, i, 1) * 150);
                int lz0 = lc * 96 + (int) (Hash.unit(lh, i, 2) * 40);
                int len = 20 + (int) (Hash.unit(lh, i, 3) * 56);
                if (y == ly && z >= lz0 && z < lz0 + len) r = Math.max(r, 2 + (int) (Hash.unit(lh, i, 4) * 2));
            }
        }
        double lvl = anomalyLevel(z);
        if (lvl > 0.3) {
            double n = Noise.value3(sAnomaly, side * 31.0 + z / 9.0, y / 9.0, side);
            if (n > 1.0 - lvl * 0.5) r += (int) Math.round((n - 0.5) * 6 * lvl);
        }
        return r;
    }

    // ------------------------------------------------------------------ floor

    public int floorY(int x, int z) {
        double d = wallDepth(x + 0.5, z + 0.5);
        if (d > -4) return FLOOR_Y;
        double n = Noise.fbm2(sFloor, x / 23.0, z / 23.0, 2);
        return FLOOR_Y + (int) Math.round(n * 1.4);
    }

    // ------------------------------------------------------------------- pits

    public static final int PIT_CELL = 44;

    public record Pit(int x0, int z0, int sx, int sz, boolean grated) {
        public boolean contains(int x, int z) {
            return x >= x0 && x < x0 + sx && z >= z0 && z < z0 + sz;
        }

        public boolean containsWithRim(int x, int z) {
            return x >= x0 - 1 && x <= x0 + sx && z >= z0 - 1 && z <= z0 + sz;
        }
    }

    /** Pit for a cell or null. Pits are always fully inside the corridor. */
    public Pit pit(int cx, int cz) {
        long h = Hash.of(sub(201), cx, cz);
        if (Hash.unit(h, 0) > 0.2) return null;
        int sx = 5 + (int) (Hash.unit(h, 1) * 7);
        int sz = 5 + (int) (Hash.unit(h, 2) * 7);
        int x0 = cx * PIT_CELL + 3 + (int) (Hash.unit(h, 3) * (PIT_CELL - sx - 6));
        int z0 = cz * PIT_CELL + 3 + (int) (Hash.unit(h, 4) * (PIT_CELL - sz - 6));
        if (Math.abs(z0) < 40) return null; // keep the arrival area intact
        // must lie fully inside the corridor with a margin
        for (int i = 0; i < 4; i++) {
            int px = (i & 1) == 0 ? x0 - 1 : x0 + sx;
            int pz = (i & 2) == 0 ? z0 - 1 : z0 + sz;
            if (wallDepth(px + 0.5, pz + 0.5) > -9) return null;
        }
        return new Pit(x0, z0, sx, sz, Hash.unit(h, 5) < 0.3);
    }

    public Pit pitAt(int x, int z) {
        Pit p = pit(Math.floorDiv(x, PIT_CELL), Math.floorDiv(z, PIT_CELL));
        return p != null && p.containsWithRim(x, z) ? p : null;
    }

    // -------------------------------------------------------------- landmarks

    public static final int LANDMARK_CELL = 1100;

    public enum LandmarkType { GATE, PYLON, ANCHOR, FALLEN_SLAB, SILENT_HALL }

    public record Landmark(LandmarkType type, int z, long hash) {}

    public Landmark landmark(int cell) {
        long h = Hash.of(sub(301), cell);
        int z = cell * LANDMARK_CELL + LANDMARK_CELL / 2 + (int) (Hash.signed(Hash.of(h, 1)) * 220);
        if (Math.abs(z) < 160) z += 320 * (z >= 0 ? 1 : -1);
        LandmarkType[] types = LandmarkType.values();
        return new Landmark(types[(int) (Hash.unit(h, 2) * types.length)], z, h);
    }

    /** Is z within {@code margin} of any landmark centre? */
    public boolean nearLandmark(int z, int margin) {
        int cell = Math.floorDiv(z, LANDMARK_CELL);
        for (int c = cell - 1; c <= cell + 1; c++) {
            if (Math.abs(landmark(c).z() - z) < margin) return true;
        }
        return false;
    }
}
