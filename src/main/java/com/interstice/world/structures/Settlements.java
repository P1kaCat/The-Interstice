package com.interstice.world.structures;

import com.interstice.registry.ModBlocks;
import com.interstice.util.Hash;
import com.interstice.util.Noise;
import com.interstice.world.CorridorPlan;
import com.interstice.world.CorridorPlan.Region;
import com.interstice.world.IntersticeLayout;
import com.interstice.world.generation.ChunkWriter;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;

/**
 * Ground-level settlements of the finite Corridor (generation version 2): the buildings, yards, shelters and
 * checkpoints that fill the floor between the two walls, region by region.
 * <p>
 * The floor is cut into {@link #CELL}-block lots along Z on each side of a central street. Every lot is a pure
 * function of the layout seed ({@link #lot}); a building is described in world space and written through the
 * chunk-clipping {@link ChunkWriter}, so it connects across chunk borders like every other structure.
 * <p>
 * A building backs onto the service alley along the wall (its back wall stays {@link #WALL_MARGIN} blocks clear
 * of the wall face, past the buttresses), faces the street, and keeps the street at least {@link #STREET_HALF}
 * blocks wide on its side. Lots are separated by alleys. Interiors are real: a back hallway on every floor,
 * rooms opening onto it (some with doors that close), stairs between floors, windows, furniture, closets and
 * lockers to hide in, and loot chests whose tables depend on the building ({@code data/interstice/loot_table/chests}).
 */
public final class Settlements {
    private Settlements() {}

    public static final int CELL = 24;
    public static final int WALL_MARGIN = 10;
    public static final int STREET_HALF = 7;
    public static final int FLOOR_H = 4;

    public enum Kind { NONE, KIOSK, CONTAINERS, APARTMENT, SHOP, UTILITY, COURTYARD, HALL, HALL_TAIL, YARD, CLINIC, LAB, RUIN, CHECKPOINT, SHELTER, DEBRIS }

    /** A lot: footprint {@code [x0, x0+sx) x [z0, z0+sz)}; side -1 = west row, +1 = east row. */
    public record Lot(Kind kind, Region region, int side, int cell, int x0, int z0, int sx, int sz, int floors, long hash) {
        public boolean contains(int x, int z, int margin) {
            return x >= x0 - margin && x < x0 + sx + margin && z >= z0 - margin && z < z0 + sz + margin;
        }

        public boolean built() {
            return kind != Kind.NONE && kind != Kind.HALL_TAIL;
        }
    }

    private static final Lot NONE = new Lot(Kind.NONE, null, 0, 0, 0, 0, 0, 0, 0, 0);

    // ------------------------------------------------------------------ lots

    /** The lot of a cell on a side (pure). {@link Kind#NONE} when empty or impossible there. */
    public static Lot lot(IntersticeLayout L, int side, int cell) {
        CorridorPlan plan = L.plan();
        if (plan == null) return NONE;
        long h = Hash.of(L.sub(1201), side, cell);
        int cz0 = cell * CELL;
        if (!plan.inside(cz0 - 40) || !plan.inside(cz0 + CELL + 40)) return NONE;
        if (Math.abs(cz0 + CELL / 2) < 36) return NONE;   // keep the arrival area open
        Region region = plan.regionForLot(cz0 + CELL / 2.0, h);
        double roll = Hash.unit(h, 1);
        Kind kind;
        int len, depth, floors = 1;
        switch (region) {
            case FORGOTTEN_DISTRICT -> {
                if (roll < 0.52) { kind = Kind.APARTMENT; floors = 2 + (int) (Hash.unit(h, 2) * 3); }
                else if (roll < 0.72) kind = Kind.SHOP;
                else if (roll < 0.84) kind = Kind.UTILITY;
                else if (roll < 0.97) kind = Kind.COURTYARD;
                else kind = Kind.NONE;
                len = 14 + (int) (Hash.unit(h, 3) * 7);
                depth = 9 + (int) (Hash.unit(h, 4) * 5);
            }
            case IRONWORKS -> {
                // halls take two cells (an even cell and the next); the odd cell of a hall is its tail
                if ((cell & 1) == 1) {
                    Lot prev = lot(L, side, cell - 1);
                    if (prev.kind == Kind.HALL) return new Lot(Kind.HALL_TAIL, region, side, cell, prev.x0, prev.z0, prev.sx, prev.sz, 1, h);
                }
                Region next = plan.regionForLot(cz0 + CELL * 1.5, Hash.of(L.sub(1201), side, cell + 1));
                if ((cell & 1) == 0 && roll < 0.6 && next == Region.IRONWORKS) {
                    kind = Kind.HALL;
                    len = CELL + 14 + (int) (Hash.unit(h, 3) * 8);
                    depth = 13 + (int) (Hash.unit(h, 4) * 7);
                } else if (roll < 0.85) {
                    kind = Kind.YARD;
                    len = 16 + (int) (Hash.unit(h, 3) * 5);
                    depth = 10 + (int) (Hash.unit(h, 4) * 6);
                } else {
                    kind = Kind.UTILITY;
                    len = 12 + (int) (Hash.unit(h, 3) * 6);
                    depth = 9 + (int) (Hash.unit(h, 4) * 3);
                }
            }
            case CONTAMINATED_WARD -> {
                if (roll < 0.5) { kind = Kind.CLINIC; floors = 1 + (int) (Hash.unit(h, 2) * 2.4); }
                else if (roll < 0.78) kind = Kind.LAB;
                else if (roll < 0.95) kind = Kind.SHELTER;
                else kind = Kind.NONE;
                len = 15 + (int) (Hash.unit(h, 3) * 6);
                depth = 10 + (int) (Hash.unit(h, 4) * 4);
            }
            default -> {   // DEAD_EXPANSE: long empty stretches, a few things to make for
                // still sparse, but never a long run without something to hide behind or in
                if (roll < 0.14) kind = Kind.SHELTER;
                else if (roll < 0.30) kind = Kind.RUIN;
                else if (roll < 0.36) kind = Kind.CHECKPOINT;
                else if (roll < 0.52) kind = Kind.DEBRIS;
                else kind = Kind.NONE;
                len = 10 + (int) (Hash.unit(h, 3) * 8);
                depth = 8 + (int) (Hash.unit(h, 4) * 4);
                if (kind == Kind.SHELTER) { len = 9 + (int) (Hash.unit(h, 3) * 3); depth = 8; }
            }
        }
        if (kind == Kind.NONE) return NONE;
        int z0 = cz0 + 2 + (int) (Hash.unit(h, 5) * Math.max(1, (kind == Kind.HALL ? 2 * CELL : CELL) - 4 - len));
        int z1 = z0 + len - 1;
        if (L.nearLandmark(z0 + len / 2, 90)) return NONE;
        if (crossing(L, z0 - 2, z1 + 2) != null) return NONE;   // a cross street runs here
        // the back wall clears the wall face (and its buttresses); the front keeps the street open
        double backLimit = side < 0 ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        double streetLimit = side < 0 ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
        // the setback from the wall varies a lot from one lot to the next: no straight rows of facades
        double setback = WALL_MARGIN + Math.pow(Hash.unit(h, 6), 1.4) * 15;
        if (kind == Kind.HALL) setback = WALL_MARGIN + Hash.unit(h, 6) * 5;
        for (int i = 0; i <= 4; i++) {
            double z = z0 + (z1 - z0) * i / 4.0 + 0.5;
            double face = L.faceX(side, z);
            double back = face - side * setback;
            double street = streetCenter(L, z) + side * STREET_HALF;
            backLimit = side < 0 ? Math.max(backLimit, back) : Math.min(backLimit, back);
            streetLimit = side < 0 ? Math.min(streetLimit, street) : Math.max(streetLimit, street);
        }
        int maxDepth = (int) Math.floor(Math.abs(streetLimit - backLimit));
        if (kind == Kind.DEBRIS || kind == Kind.COURTYARD || kind == Kind.YARD) depth = Math.min(depth, maxDepth);
        if (maxDepth < 8) return NONE;
        depth = Math.min(depth, maxDepth);
        int x0 = side < 0 ? (int) Math.ceil(backLimit) : (int) Math.floor(backLimit) - depth + 1;
        Lot lot = new Lot(kind, region, side, cell, x0, z0, depth, len, floors, h);
        // never across a shaft
        for (int i = 0; i < 5; i++) {
            int px = i == 4 ? x0 + depth / 2 : (i & 1) == 0 ? x0 - 1 : x0 + depth;
            int pz = i == 4 ? z0 + len / 2 : (i & 2) == 0 ? z0 - 1 : z0 + len;
            if (L.pitAt(px, pz) != null) return NONE;
        }
        return lot;
    }

    // ----------------------------------------------------------- the streets

    /**
     * Centre line of the main street: in a version-2 world it wanders from one side to the other where the Corridor
     * is wide enough (a straight road down the middle looked like a runway).
     */
    public static double streetCenter(IntersticeLayout L, double z) {
        if (L.plan() == null) return L.centerX(z);
        double room = Math.max(0, L.halfWidth(z) - 28);
        return L.centerX(z) + room * 0.6 * Noise.fbm1(L.sub(1210), z / 170.0, 2);
    }

    public static final int CROSS_CELL = 80;

    /** A cross street (across the whole Corridor, wall to wall); a plaza is a wide one with a monument. */
    public record Cross(int z0, int width, boolean plaza) {
        public boolean contains(int z) {
            return z >= z0 && z < z0 + width;
        }
    }

    /** The cross street of a cell, or null (District and Ward streets are cut every 50-110 blocks). */
    public static Cross cross(IntersticeLayout L, int cell) {
        CorridorPlan plan = L.plan();
        if (plan == null) return null;
        long h = Hash.of(L.sub(1211), cell);
        int z0 = cell * CROSS_CELL + 15 + (int) (Hash.unit(h, 0) * (CROSS_CELL - 40));
        if (!plan.inside(z0 - 50) || !plan.inside(z0 + 70) || Math.abs(z0) < 30 || L.nearLandmark(z0, 70)) return null;
        Region r = plan.regionAt(z0);
        if (r == Region.DEAD_EXPANSE) return null;
        if (r == Region.IRONWORKS && Hash.unit(h, 1) < 0.5) return null;
        boolean plaza = r != Region.IRONWORKS && Hash.unit(h, 2) < 0.22;
        int width = plaza ? 18 + (int) (Hash.unit(h, 3) * 6) : 6 + (int) (Hash.unit(h, 3) * 3);
        return new Cross(z0, width, plaza);
    }

    /** The cross street overlapping [z0, z1], or null. */
    public static Cross crossing(IntersticeLayout L, int z0, int z1) {
        if (L.plan() == null) return null;
        for (int c = Math.floorDiv(z0, CROSS_CELL) - 1; c <= Math.floorDiv(z1, CROSS_CELL); c++) {
            Cross x = cross(L, c);
            if (x != null && x.z0 <= z1 && x.z0 + x.width - 1 >= z0) return x;
        }
        return null;
    }

    /**
     * A second row: where the main building leaves a deep strip between its front and the street, a smaller
     * building stands in front of it across a 3-block alley (shops on the street, sheds, labs).
     */
    public static Lot frontLot(IntersticeLayout L, int side, int cell) {
        Lot back = lot(L, side, cell);
        if (!back.built() || back.kind == Kind.HALL || back.kind == Kind.DEBRIS || back.kind == Kind.SHELTER) return NONE;
        long h = Hash.of(back.hash, 77);
        if (Hash.unit(h, 0) > 0.75) return NONE;
        int len = Math.min(back.sz, 10 + (int) (Hash.unit(h, 1) * 8));
        int z0 = back.z0 + (int) (Hash.unit(h, 2) * Math.max(1, back.sz - len));
        int z1 = z0 + len - 1;
        double streetLimit = side < 0 ? Double.POSITIVE_INFINITY : Double.NEGATIVE_INFINITY;
        for (int i = 0; i <= 2; i++) {
            double street = streetCenter(L, z0 + (z1 - z0) * i / 2.0 + 0.5) + side * STREET_HALF;
            streetLimit = side < 0 ? Math.min(streetLimit, street) : Math.max(streetLimit, street);
        }
        int frontOfBack = side < 0 ? back.x0 + back.sx - 1 : back.x0;
        int start = frontOfBack - side * 4;   // across a 3-block alley
        int room = (int) Math.floor(Math.abs(streetLimit - start));
        if (room < 7) return NONE;
        int depth = Math.min(room, 7 + (int) (Hash.unit(h, 3) * 5));
        Kind kind = switch (back.region) {
            case FORGOTTEN_DISTRICT -> Hash.unit(h, 4) < 0.6 ? Kind.SHOP : Kind.KIOSK;
            case IRONWORKS -> Hash.unit(h, 4) < 0.6 ? Kind.UTILITY : Kind.CONTAINERS;
            case CONTAMINATED_WARD -> Hash.unit(h, 4) < 0.6 ? Kind.LAB : Kind.KIOSK;
            case DEAD_EXPANSE -> Kind.RUIN;
        };
        int x0 = side < 0 ? start : start - depth + 1;
        for (int i = 0; i < 5; i++) {
            int px = i == 4 ? x0 + depth / 2 : (i & 1) == 0 ? x0 - 1 : x0 + depth;
            int pz = i == 4 ? z0 + len / 2 : (i & 2) == 0 ? z0 - 1 : z0 + len;
            if (L.pitAt(px, pz) != null) return NONE;
        }
        return new Lot(kind, back.region, side, cell, x0, z0, depth, len, 1, h);
    }

    // ------------------------------------------------------------- islands

    public static final int ISLAND_CELL = 40;

    /**
     * A small structure standing in the middle of a wide street (a kiosk, a stack of containers, a heap of debris),
     * only where both rows of buildings leave at least 6 blocks of street on each side of it. {@link Kind#NONE} if not.
     */
    public static Lot island(IntersticeLayout L, int cell) {
        CorridorPlan plan = L.plan();
        if (plan == null) return NONE;
        long h = Hash.of(L.sub(1202), cell);
        if (Hash.unit(h, 0) > 0.45) return NONE;
        int zc = cell * ISLAND_CELL + 8 + (int) (Hash.unit(h, 1) * (ISLAND_CELL - 16));
        if (!plan.inside(zc - 60) || !plan.inside(zc + 60) || Math.abs(zc) < 50 || L.nearLandmark(zc, 90)) return NONE;
        Region region = plan.regionForLot(zc, h);
        Kind kind = switch (region) {
            case FORGOTTEN_DISTRICT, CONTAMINATED_WARD -> Hash.unit(h, 2) < 0.6 ? Kind.KIOSK : Kind.DEBRIS;
            case IRONWORKS -> Hash.unit(h, 2) < 0.7 ? Kind.CONTAINERS : Kind.DEBRIS;
            case DEAD_EXPANSE -> Hash.unit(h, 2) < 0.5 ? Kind.RUIN : Kind.DEBRIS;
        };
        int sx = kind == Kind.CONTAINERS ? 5 : 5 + (int) (Hash.unit(h, 3) * 4);
        int sz = kind == Kind.CONTAINERS ? 8 + (int) (Hash.unit(h, 4) * 8) : 6 + (int) (Hash.unit(h, 4) * 6);
        int z0 = zc - sz / 2, z1 = z0 + sz - 1;
        double minHalf = Double.MAX_VALUE;
        for (int i = 0; i <= 2; i++) minHalf = Math.min(minHalf, L.halfWidth(z0 + (z1 - z0) * i / 2.0));
        if (minHalf < 30) return NONE;
        if (crossing(L, z0 - 3, z1 + 3) != null) return NONE;
        int x0 = (int) Math.floor(streetCenter(L, zc) + (Hash.unit(h, 5) - 0.5) * 6) - sx / 2;
        // keep the street open on both sides
        for (int side = -1; side <= 1; side += 2)
            for (int c = Math.floorDiv(z0 - 30, CELL); c <= Math.floorDiv(z1 + 6, CELL); c++) {
                for (Lot s : new Lot[]{lot(L, side, c), frontLot(L, side, c)}) {
                    if (!s.built() || s.z0 > z1 + 6 || s.z0 + s.sz < z0 - 6) continue;
                    int gap = side < 0 ? x0 - (s.x0 + s.sx) : s.x0 - (x0 + sx);
                    if (gap < 6) return NONE;
                }
            }
        for (int i = 0; i < 5; i++) {
            int px = i == 4 ? x0 + sx / 2 : (i & 1) == 0 ? x0 - 1 : x0 + sx;
            int pz = i == 4 ? zc : (i & 2) == 0 ? z0 - 1 : z1 + 1;
            if (L.pitAt(px, pz) != null) return NONE;
        }
        return new Lot(kind, region, Hash.unit(h, 6) < 0.5 ? -1 : 1, -1 - cell, x0, z0, sx, sz, 1, h);
    }

    /** The built lot or island covering (x, z) (with a margin), or null. Used by pits, lamp posts and spawning. */
    public static Lot lotAt(IntersticeLayout L, int x, int z, int margin) {
        if (L.plan() == null) return null;
        int side = L.sideOf(x + 0.5, z + 0.5);
        int cell = Math.floorDiv(z, CELL);
        for (int c = cell - 2; c <= cell + 1; c++) {
            Lot lot = lot(L, side, c);
            if (lot.built() && lot.contains(x, z, margin)) return lot;
            Lot front = frontLot(L, side, c);
            if (front.built() && front.contains(x, z, margin)) return front;
        }
        int ic = Math.floorDiv(z, ISLAND_CELL);
        for (int c = ic - 1; c <= ic + 1; c++) {
            Lot lot = island(L, c);
            if (lot.built() && lot.contains(x, z, margin)) return lot;
        }
        return null;
    }

    // ------------------------------------------------------------- generation

    public static void run(ChunkWriter w) {
        IntersticeLayout L = w.layout;
        if (L.plan() == null) return;
        for (int side = -1; side <= 1; side += 2) {
            for (int c = Math.floorDiv(w.minZ - 3 * CELL, CELL); c <= Math.floorDiv(w.maxZ + 2, CELL); c++) {
                Lot front = frontLot(L, side, c);
                if (front.built() && w.overlapsZ(front.z0 - 2, front.z0 + front.sz + 1) && w.overlapsX(front.x0 - 2, front.x0 + front.sx + 1)) {
                    Frame ff = new Frame(w, front);
                    switch (front.kind) {
                        case SHOP -> shop(ff);
                        case KIOSK -> kiosk(ff);
                        case UTILITY -> utility(ff);
                        case CONTAINERS -> yard(ff);
                        case LAB -> lab(ff);
                        default -> ruin(ff);
                    }
                }
                Lot lot = lot(L, side, c);
                if (!lot.built() || !w.overlapsZ(lot.z0 - 2, lot.z0 + lot.sz + 1) || !w.overlapsX(lot.x0 - 2, lot.x0 + lot.sx + 1)) continue;
                Frame f = new Frame(w, lot);
                switch (lot.kind) {
                    case APARTMENT -> residential(f, lot.floors, false);
                    case CLINIC -> residential(f, lot.floors, true);
                    case SHOP -> shop(f);
                    case UTILITY -> utility(f);
                    case COURTYARD -> courtyard(f);
                    case HALL -> hall(f);
                    case YARD -> yard(f);
                    case LAB -> lab(f);
                    case RUIN -> ruin(f);
                    case CHECKPOINT -> checkpoint(f);
                    case SHELTER -> shelter(f);
                    case DEBRIS -> debris(f);
                    default -> {}
                }
            }
        }
        for (int c = Math.floorDiv(w.minZ - ISLAND_CELL, ISLAND_CELL); c <= Math.floorDiv(w.maxZ + 2, ISLAND_CELL); c++) {
            Lot lot = island(L, c);
            if (!lot.built() || !w.overlapsZ(lot.z0 - 2, lot.z0 + lot.sz + 1) || !w.overlapsX(lot.x0 - 2, lot.x0 + lot.sx + 1)) continue;
            Frame f = new Frame(w, lot);
            switch (lot.kind) {
                case KIOSK -> kiosk(f);
                case CONTAINERS -> yard(f);
                case RUIN -> ruin(f);
                default -> debris(f);
            }
        }
        plazas(w);
        streetDetails(w);
    }

    /**
     * Plazas: where a cross street widens, a monument stands on the pavement beside the road (a plinth, a broken
     * figure of dark metal), with benches and two street lights.
     */
    private static void plazas(ChunkWriter w) {
        IntersticeLayout L = w.layout;
        for (int c = Math.floorDiv(w.minZ - 30, CROSS_CELL); c <= Math.floorDiv(w.maxZ + 2, CROSS_CELL); c++) {
            Cross x = cross(L, c);
            if (x == null || !x.plaza || !w.overlapsZ(x.z0 - 2, x.z0 + x.width + 2)) continue;
            long h = Hash.of(L.sub(1212), c);
            int side = Hash.unit(h, 0) < 0.5 ? -1 : 1;
            int zc = x.z0 + x.width / 2;
            int xc = (int) Math.floor(streetCenter(L, zc) + side * (STREET_HALF + 6));
            int fy = IntersticeLayout.FLOOR_Y;
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++)
                    for (int y = fy; y < fy + 2; y++) w.set(xc + dx, y, zc + dz, ModBlocks.CONCRETE_PANEL.defaultBlockState());
            int hgt = 3 + (int) (Hash.unit(h, 1) * 3);
            for (int y = fy + 2; y < fy + 2 + hgt; y++) w.set(xc, y, zc, ModBlocks.STEEL_BEAM.defaultBlockState());
            w.set(xc + 1, fy + 2 + hgt - 2, zc, ModBlocks.DARK_METAL.defaultBlockState());   // an arm raised, broken off
            w.set(xc, fy + 2 + hgt, zc, ModBlocks.DARK_METAL.defaultBlockState());
            for (int k = 0; k < 2; k++) {
                int bz = zc + (k == 0 ? -4 : 4);
                Direction face = k == 0 ? Direction.SOUTH : Direction.NORTH;
                for (int dx = -1; dx <= 1; dx++) w.set(xc + dx, fy, bz, ModBlocks.WORN_BENCH.defaultBlockState().setValue(StairBlock.FACING, face.getOpposite()));
                int lx = xc + side * 4;
                for (int y = fy; y < fy + 5; y++) w.set(lx, y, bz, ModBlocks.DARK_METAL.defaultBlockState());
                w.set(lx, fy + 5, bz, ModBlocks.SODIUM_LAMP.defaultBlockState());
            }
        }
    }

    /** A kiosk in the street: a small booth with a shutter-glass front, a counter, a barrel of whatever was left. */
    static void kiosk(Frame f) {
        int sx = f.sx, sz = f.sz, fy = f.fy;
        shell(f, 1, 4);
        for (int v = 1; v < sz - 1; v++) f.set(sx - 1, fy + 1, v, f.r(v, 170) < 0.6 ? f.pane(true) : Blocks.AIR.defaultBlockState());
        f.door(0, fy, sz / 2, f.back, f.p.door, f.r(171) < 0.5);
        for (int v = 1; v < sz - 1; v++) f.set(sx - 2, fy, v, ModBlocks.CONCRETE_SLAB.defaultBlockState());
        f.barrel(1, fy, 1, f.r(172) < 0.5 ? f.p.loot : null);
        if (f.r(173) < 0.4) f.set(sx / 2, fy + 2, sz / 2, ceilingLamp(ModBlocks.SODIUM_LAMP));
    }

    // ------------------------------------------------------------ the frame

    /**
     * Local building coordinates: u runs from the back wall (u = 0, alley side) to the front (u = sx - 1, street
     * side), v runs along Z (v = 0 at z0), y is absolute. Palette and decay come from the region.
     */
    static final class Frame {
        final ChunkWriter w;
        final Lot lot;
        final int sx, sz, fy;
        final Direction front, back;
        final long h;
        final Palette p;
        final double infest;
        int salt;

        Frame(ChunkWriter w, Lot lot) {
            this.w = w;
            this.lot = lot;
            this.sx = lot.sx;
            this.sz = lot.sz;
            this.fy = IntersticeLayout.FLOOR_Y;
            this.front = lot.side < 0 ? Direction.EAST : Direction.WEST;
            this.back = front.getOpposite();
            this.h = lot.hash;
            this.p = Palette.of(lot.region, lot.hash);
            CorridorPlan plan = w.layout.plan();
            this.infest = lot.region == Region.CONTAMINATED_WARD ? Math.min(1, 0.15 + 1.05 * plan.progress(Region.CONTAMINATED_WARD, lot.z0)) : 0;
        }

        int x(int u) {
            return lot.side < 0 ? lot.x0 + u : lot.x0 + sx - 1 - u;
        }

        int z(int v) {
            return lot.z0 + v;
        }

        void set(int u, int y, int v, BlockState s) {
            w.set(x(u), y, z(v), s);
        }

        BlockState get(int u, int y, int v) {
            return w.get(x(u), y, z(v));
        }

        void fill(int u0, int y0, int v0, int u1, int y1, int v1, BlockState s) {
            for (int u = Math.min(u0, u1); u <= Math.max(u0, u1); u++)
                for (int v = Math.min(v0, v1); v <= Math.max(v0, v1); v++)
                    for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) set(u, y, v, s);
        }

        double r(int... k) {
            long hh = Hash.of(h, 9000 + salt);
            for (int i : k) hh = Hash.of(hh, i);
            return Hash.unit(hh);
        }

        /** Infestation variant of a wall/floor block in the Contaminated Ward (gradual along the region). */
        BlockState grow(BlockState s, int u, int y, int v) {
            if (infest <= 0 || s.isAir()) return s;
            double n = Noise.value3(h, x(u) / 4.0, y / 4.0, z(v) / 4.0) * 0.5 + 0.5;
            if (n > 1.05 - infest * 0.55) return ModBlocks.FLESH_GROWTH.defaultBlockState();
            return s;
        }

        void wall(int u, int y, int v) {
            set(u, y, v, grow(p.wall(r(u, y, v)), u, y, v));
        }

        void door(int u, int y, int v, Direction facing, BlockState door, boolean open) {
            BlockState d = door.setValue(DoorBlock.FACING, facing).setValue(DoorBlock.OPEN, open).setValue(DoorBlock.HINGE, DoorHingeSide.LEFT);
            set(u, y, v, d.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
            set(u, y + 1, v, d.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        }

        void chest(int u, int y, int v, Direction facing, String table) {
            set(u, y, v, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing));
            w.lootChest(x(u), y, z(v), "minecraft:chest", "interstice:chests/" + table, Hash.of(h, x(u), y, z(v)));
        }

        void barrel(int u, int y, int v, String table) {
            set(u, y, v, Blocks.BARREL.defaultBlockState());
            if (table != null) w.lootChest(x(u), y, z(v), "minecraft:barrel", "interstice:chests/" + table, Hash.of(Hash.of(h, x(u), y, z(v)), 3));
        }

        /** A pane in a wall that runs along v (front/back walls) or along u (side walls). */
        BlockState pane(boolean alongV) {
            BlockState s = p.pane;
            return alongV ? s.setValue(IronBarsBlock.NORTH, true).setValue(IronBarsBlock.SOUTH, true)
                    : alongU(s);
        }

        BlockState alongU(BlockState s) {
            return s.setValue(IronBarsBlock.EAST, true).setValue(IronBarsBlock.WEST, true);
        }

        /** Iron bars in a wall running along v. */
        BlockState alongVBars() {
            return ModBlocks.RUSTED_BARS.defaultBlockState().setValue(IronBarsBlock.NORTH, true).setValue(IronBarsBlock.SOUTH, true);
        }
    }

    /** Materials of a region (with small per-building variations). */
    record Palette(BlockState wallA, BlockState wallB, BlockState floor, BlockState roof, BlockState pane, BlockState door,
                   BlockState trim, BlockState planks, String loot) {
        BlockState wall(double r) {
            return r < 0.78 ? wallA : wallB;
        }

        static Palette of(Region region, long h) {
            double v = Hash.unit(h, 31);
            BlockState glass = ModBlocks.GRIMY_GLASS_PANE.defaultBlockState();
            BlockState boards = ModBlocks.FLOORBOARDS.defaultBlockState();
            return switch (region) {
                // the District: soot-blackened brick, stained plaster or bare concrete; floorboards, tar roofs
                case FORGOTTEN_DISTRICT -> v < 0.4
                        ? new Palette(ModBlocks.GRIMY_BRICK.defaultBlockState(), ModBlocks.GRIMY_BRICK.defaultBlockState(), boards,
                        ModBlocks.TAR_ROOF.defaultBlockState(), glass, Blocks.SPRUCE_DOOR.defaultBlockState(),
                        ModBlocks.CONCRETE_PANEL.defaultBlockState(), boards, "residential")
                        : v < 0.75
                        ? new Palette(ModBlocks.PLASTER_WALL.defaultBlockState(), ModBlocks.PLASTER_CRACKED.defaultBlockState(), boards,
                        ModBlocks.TAR_ROOF.defaultBlockState(), glass, Blocks.DARK_OAK_DOOR.defaultBlockState(),
                        ModBlocks.CONCRETE_PANEL.defaultBlockState(), boards, "residential")
                        : new Palette(ModBlocks.WEATHERED_CONCRETE.defaultBlockState(), ModBlocks.STAINED_CONCRETE.defaultBlockState(), boards,
                        ModBlocks.TAR_ROOF.defaultBlockState(), glass, Blocks.SPRUCE_DOOR.defaultBlockState(),
                        ModBlocks.CONCRETE_PANEL.defaultBlockState(), boards, "residential");
                // the Ironworks: corrugated sheet and oxidised plate on steel frames, grated floors
                case IRONWORKS -> new Palette(ModBlocks.CORRUGATED_METAL.defaultBlockState(), ModBlocks.OXIDIZED_PLATE.defaultBlockState(), ModBlocks.METAL_GRATE.defaultBlockState(),
                        ModBlocks.DARK_METAL.defaultBlockState(), ModBlocks.RUSTED_BARS.defaultBlockState(), Blocks.SPRUCE_DOOR.defaultBlockState(),
                        ModBlocks.STEEL_BEAM.defaultBlockState(), boards, "industrial");
                // the Ward: grimy tiles, stained tiles, worn linoleum
                case CONTAMINATED_WARD -> new Palette(ModBlocks.CLINIC_TILE.defaultBlockState(), ModBlocks.STAINED_TILE.defaultBlockState(), ModBlocks.CLINIC_FLOOR.defaultBlockState(),
                        ModBlocks.WEATHERED_CONCRETE.defaultBlockState(), glass, Blocks.BIRCH_DOOR.defaultBlockState(),
                        ModBlocks.CONCRETE_PANEL.defaultBlockState(), boards, "medical");
                case DEAD_EXPANSE -> new Palette(ModBlocks.WEATHERED_CONCRETE.defaultBlockState(), ModBlocks.CRACKED_CONCRETE.defaultBlockState(), ModBlocks.CONCRETE_FLOOR.defaultBlockState(),
                        ModBlocks.WEATHERED_CONCRETE.defaultBlockState(), ModBlocks.RUSTED_BARS.defaultBlockState(), Blocks.SPRUCE_DOOR.defaultBlockState(),
                        ModBlocks.DARK_METAL.defaultBlockState(), boards, "expanse");
            };
        }
    }

    // ------------------------------------------------- residential and clinic

    /**
     * Apartment block (District) or clinic (Ward): {@code floors} storeys of {@link #FLOOR_H}; every storey has a
     * two-wide back hallway (u = 1..2), a partition (u = 3) and rooms (u = 4 .. sx-2) cut along v, each opening onto
     * the hallway (half of them through a door that closes). Stairs climb at the far end of the hallway. Entrances:
     * the front door (sometimes barricaded) and an always-open alley door at the near end of the hallway.
     */
    static void residential(Frame f, int floors, boolean clinic) {
        int sx = f.sx, sz = f.sz, fy = f.fy;
        if (sx < 8 || sz < 10) { utility(f); return; }
        int top = fy + floors * FLOOR_H;
        // foundations and slabs, shell
        for (int u = 0; u < sx; u++)
            for (int v = 0; v < sz; v++) {
                boolean edge = u == 0 || u == sx - 1 || v == 0 || v == sz - 1;
                f.set(u, fy - 1, v, f.p.trim);
                for (int k = 0; k < floors; k++) {
                    int base = fy + k * FLOOR_H;
                    if (k > 0) f.set(u, base - 1, v, edge ? f.p.trim : f.grow(f.p.floor, u, base - 1, v));
                    for (int y = base; y < base + FLOOR_H - 1; y++) {
                        if (edge) f.wall(u, y, v);
                        else f.set(u, y, v, Blocks.AIR.defaultBlockState());
                    }
                }
                f.set(u, top - 1, v, f.p.roof);
                if (edge && ((u + v) & 1) == 0) f.set(u, top, v, f.p.trim);   // parapet
            }
        // rooms along v: walls at the boundaries (shared by every storey)
        int[] cuts = new int[16];
        int nc = 0;
        int v = 1;
        f.salt = 1;
        while (nc < cuts.length - 1) {
            int width = 3 + (int) (f.r(nc) * 4);
            if (v + width >= sz - 1 - 2) break;
            v += width;
            cuts[nc++] = v;
            v++;
        }
        int stairV0 = sz - 6;   // the stair run occupies u = 1, v = stairV0 .. stairV0 + 3
        for (int k = 0; k < floors; k++) {
            int base = fy + k * FLOOR_H;
            f.salt = 100 + k;
            // partition between hallway and rooms
            for (int vv = 1; vv < sz - 1; vv++)
                for (int y = base; y < base + 3; y++) f.wall(3, y, vv);
            // room walls
            for (int i = 0; i < nc; i++)
                for (int u = 4; u < sx - 1; u++)
                    for (int y = base; y < base + 3; y++) f.wall(u, y, cuts[i]);
            // each room: doorway to the hallway, furniture, maybe a closet, maybe loot, rarely a lamp
            int start = 1;
            for (int i = 0; i <= nc; i++) {
                int end = i < nc ? cuts[i] - 1 : sz - 2;
                if (end - start < 1) { start = end + 2; continue; }
                int mid = (start + end) / 2;
                f.set(3, base, mid, Blocks.AIR.defaultBlockState());
                f.set(3, base + 1, mid, Blocks.AIR.defaultBlockState());
                if (f.r(i, 1) < 0.55) f.door(3, base, mid, f.front, f.p.door, f.r(i, 2) < 0.3);
                room(f, k, i, start, end, mid, base, clinic);
                start = end + 2;
            }
            // windows: front and side walls, never at room edges (closets sit there)
            for (int vv = 2; vv < sz - 2; vv += 2) {
                if (isCut(cuts, nc, vv) || isCut(cuts, nc, vv - 1) || isCut(cuts, nc, vv + 1)) continue;
                window(f, sx - 1, base, vv, true, f.r(vv, 7));
            }
            for (int u = 5; u < sx - 2; u += 2) {
                window(f, u, base, 0, false, f.r(u, 8));
                window(f, u, base, sz - 1, false, f.r(u, 9));
            }
            // stairs to the next storey
            if (k < floors - 1) {
                for (int i = 0; i < 4; i++) {
                    f.set(1, base + i, stairV0 + i, ModBlocks.CONCRETE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH).setValue(StairBlock.HALF, Half.BOTTOM));
                    for (int y = base + i + 1; y < base + FLOOR_H + 2 && y < top - 1; y++) if (y > base + 2) f.set(1, y, stairV0 + i, Blocks.AIR.defaultBlockState());
                }
                f.set(1, base + FLOOR_H - 1, stairV0 + 3, ModBlocks.CONCRETE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH));
                for (int i = 0; i < 3; i++) f.set(1, base + FLOOR_H - 1, stairV0 + i, Blocks.AIR.defaultBlockState());
            }
            // hallway: a dim lamp now and then, rubble, and holes in the floor of the upper storeys
            if (f.r(k, 20) < 0.35) f.set(2, base + 2, sz / 2, ceilingLamp(ModBlocks.EMERGENCY_LAMP));
            if (f.r(k, 21) < 0.4) f.set(2, base, 2 + (int) (f.r(k, 22) * (sz - 9)), ModBlocks.CONCRETE_RUBBLE.defaultBlockState());
        }
        // entrances: alley door (always open) and the front door, sometimes barricaded with planks (but never both)
        f.set(1, fy, 0, Blocks.AIR.defaultBlockState());
        f.set(1, fy + 1, 0, Blocks.AIR.defaultBlockState());
        f.door(2, fy, 0, Direction.NORTH, f.p.door, true);
        int fv = firstRoomMid(cuts, nc, sz);
        if (f.r(50) < 0.25) {
            f.set(sx - 1, fy, fv, f.p.planks);
            f.set(sx - 1, fy + 1, fv, Blocks.AIR.defaultBlockState());
            f.set(sx - 1, fy + 2, fv, f.p.planks);
        } else {
            f.door(sx - 1, fy, fv, f.front, f.p.door, f.r(51) < 0.4);
        }
        // front steps / porch lamp
        if (f.r(52) < 0.5) f.set(sx, fy + 2, fv + 1, com.interstice.block.LampBlock.facing(ModBlocks.SODIUM_LAMP.defaultBlockState(), f.front));   // porch light on the front wall
    }

    private static boolean isCut(int[] cuts, int n, int v) {
        for (int i = 0; i < n; i++) if (cuts[i] == v) return true;
        return false;
    }

    private static int firstRoomMid(int[] cuts, int n, int sz) {
        int end = n > 0 ? cuts[0] - 1 : sz - 2;
        return Math.max(1, (1 + end) / 2);
    }

    private static void window(Frame f, int u, int base, int v, boolean alongV, double roll) {
        BlockState s;
        if (roll < 0.45) s = f.pane(alongV);
        else if (roll < 0.75) s = Blocks.AIR.defaultBlockState();                       // broken
        else if (roll < 0.9) s = f.p.planks;                                              // boarded
        else s = alongV ? ModBlocks.BLACKOUT_PANE.defaultBlockState().setValue(IronBarsBlock.NORTH, true).setValue(IronBarsBlock.SOUTH, true)
                    : f.alongU(ModBlocks.BLACKOUT_PANE.defaultBlockState());     // blacked out
        f.set(u, base + 1, v, s);
        if (roll < 0.6 || roll >= 0.9) f.set(u, base + 2, v, s);
    }

    /** Furnishes room {@code i} of storey {@code k}: v in [v0, v1], u in [4, sx-2]. */
    private static void room(Frame f, int k, int i, int v0, int v1, int door, int base, boolean clinic) {
        int sx = f.sx;
        int u0 = 4, u1 = sx - 2;
        double kindRoll = f.r(k, i, 30);
        // a closet in the front corner: a one-block cell behind a door (the player can hide in it)
        boolean closet = f.r(k, i, 31) < (clinic ? 0.22 : 0.38) && v1 - v0 >= 2 && u1 - u0 >= 2;
        if (closet) {
            f.set(u1, base, v0, Blocks.AIR.defaultBlockState());
            f.set(u1, base + 1, v0, Blocks.AIR.defaultBlockState());
            f.set(u1, base + 2, v0, f.p.planks);
            f.set(u1 - 1, base, v0, f.p.planks);
            f.set(u1 - 1, base + 1, v0, f.p.planks);
            f.set(u1 - 1, base + 2, v0, f.p.planks);
            f.door(u1, base, v0 + 1, Direction.SOUTH, f.p.door, false);
        }
        int fv0 = closet ? v0 + 2 : v0;
        String loot = clinic ? "medical" : "residential";
        if (clinic) {
            if (kindRoll < 0.35) {                       // treatment room: beds
                for (int vv = fv0; vv <= v1 && vv - fv0 < 4; vv += 2) bed(f, u1 - 1, base, vv, f.front, Blocks.WHITE_BED.defaultBlockState());
                if (f.r(k, i, 32) < 0.45) f.chest(u0, base, v1, f.front, loot);
            } else if (kindRoll < 0.6) {                 // storage: shelves and supplies
                for (int vv = fv0; vv <= v1; vv++) f.barrel(u1, base, vv, f.r(k, i, vv, 33) < 0.25 ? loot : null);
                f.chest(u0, base, v1, f.front, loot);
            } else if (kindRoll < 0.8) {                 // quarantine: bars instead of the partition
                for (int vv = v0; vv <= v1; vv++)
                    for (int y = base; y < base + 3; y++) if (vv != door || y > base + 1) f.set(3, y, vv, ModBlocks.RUSTED_BARS.defaultBlockState().setValue(IronBarsBlock.NORTH, true).setValue(IronBarsBlock.SOUTH, true));
                bed(f, u1 - 1, base, v1 - 1 >= fv0 ? v1 - 1 : v1, f.front, Blocks.LIGHT_GRAY_BED.defaultBlockState());
            } else {                                     // office
                f.set(u1, base, fv0, ModBlocks.CONCRETE_SLAB.defaultBlockState());
                f.set(u1, base, Math.min(v1, fv0 + 1), Blocks.BREWING_STAND.defaultBlockState());
                if (f.r(k, i, 34) < 0.5) f.chest(u0, base, v1, f.front, loot);
            }
            if (f.infest > 0.3 && f.r(k, i, 35) < f.infest) f.set(u0 + 1, base + 2, (v0 + v1) / 2, Blocks.HANGING_ROOTS.defaultBlockState());
            if (f.infest > 0.5 && f.r(k, i, 36) < f.infest * 0.6) f.set(u1, base + 2, v1, Blocks.COBWEB.defaultBlockState());
        } else {
            if (kindRoll < 0.3) {                        // bedroom
                bed(f, u1 - 1, base, v1, f.front, f.r(k, i, 37) < 0.5 ? Blocks.BROWN_BED.defaultBlockState() : Blocks.GRAY_BED.defaultBlockState());
                if (f.r(k, i, 38) < 0.5) f.chest(u0, base, fv0, f.front, loot);
            } else if (kindRoll < 0.55) {                // kitchen
                f.set(u1, base, fv0, Blocks.SMOKER.defaultBlockState().setValue(net.minecraft.world.level.block.SmokerBlock.FACING, f.back));
                if (fv0 + 1 <= v1) f.set(u1, base, fv0 + 1, Blocks.CAULDRON.defaultBlockState());
                if (fv0 + 2 <= v1) f.barrel(u1, base, fv0 + 2, f.r(k, i, 39) < 0.5 ? loot : null);
            } else if (kindRoll < 0.78) {                // living room: a table, chairs, shelves
                int vm = (fv0 + v1) / 2;
                f.set(u0 + 1, base, vm, f.p.planks.getBlock() == ModBlocks.FLOORBOARDS ? ModBlocks.WORN_FENCE.defaultBlockState() : ModBlocks.WORN_FENCE.defaultBlockState());
                f.set(u0 + 1, base + 1, vm, Blocks.SPRUCE_PRESSURE_PLATE.defaultBlockState());
                f.set(u1, base, fv0, Blocks.BOOKSHELF.defaultBlockState());
                f.set(u1, base + 1, fv0, Blocks.BOOKSHELF.defaultBlockState());
                if (f.r(k, i, 40) < 0.35) f.chest(u0, base, v1, f.front, loot);
            } else {                                     // storage, the most likely to hold supplies
                for (int vv = fv0; vv <= v1; vv++) f.barrel(u1, base, vv, f.r(k, i, vv, 41) < 0.3 ? loot : null);
                if (f.r(k, i, 42) < 0.6) f.chest(u0, base, v1, f.front, loot);
            }
            // big furniture to hide behind: a wardrobe pulled out from the wall
            if (!closet && f.r(k, i, 43) < 0.25 && u1 - u0 >= 3) {
                f.set(u1 - 1, base, v0, f.p.planks);
                f.set(u1 - 1, base + 1, v0, f.p.planks);
            }
        }
        if (f.r(k, i, 44) < 0.15) f.set((u0 + u1) / 2, base + 2, (v0 + v1) / 2, ceilingLamp(ModBlocks.EMERGENCY_LAMP));
        if (f.r(k, i, 45) < 0.3) f.set(u0, base, (v0 + v1) / 2 == door ? v1 : (v0 + v1) / 2, ModBlocks.CONCRETE_RUBBLE.defaultBlockState());
    }

    private static void bed(Frame f, int u, int y, int v, Direction head, BlockState bed) {
        // foot at (u, v), head one block towards the front (u + 1)
        f.set(u, y, v, bed.setValue(BedBlock.FACING, head).setValue(BedBlock.PART, BedPart.FOOT));
        f.set(u + 1, y, v, bed.setValue(BedBlock.FACING, head).setValue(BedBlock.PART, BedPart.HEAD));
    }

    // -------------------------------------------------------- District others

    /** Corner shop: a glass front, a counter, shelves, a stock room at the back (loot). */
    static void shop(Frame f) {
        int sx = f.sx, sz = f.sz, fy = f.fy;
        shell(f, 1, 5);
        // shop window
        for (int v = 1; v < sz - 1; v++) {
            if (v == sz / 2) continue;
            for (int y = fy + 1; y <= fy + 3; y++) f.set(sx - 1, y, v, f.r(v, y, 60) < 0.5 ? f.pane(true) : Blocks.AIR.defaultBlockState());
        }
        f.door(sx - 1, fy, sz / 2, f.front, f.p.door, f.r(61) < 0.5);
        // stock room behind a partition at u = 3
        for (int v = 1; v < sz - 1; v++) for (int y = fy; y < fy + 4; y++) f.wall(3, y, v);
        f.door(3, fy, 2, f.front, f.p.door, false);
        f.chest(1, fy, sz - 2, Direction.SOUTH.getOpposite(), "residential");
        f.barrel(1, fy, sz - 3, "residential");
        f.barrel(2, fy, sz - 2, null);
        // counter and shelves
        for (int v = 2; v < sz / 2 - 1; v++) f.set(sx - 4, fy, v, ModBlocks.CONCRETE_PANEL.defaultBlockState());
        for (int v = sz / 2 + 2; v < sz - 2; v += 1) {
            f.set(5, fy, v, Blocks.BOOKSHELF.defaultBlockState());
            if (f.r(v, 62) < 0.25) f.barrel(5, fy + 1, v, "residential");
        }
        f.door(1, fy, 0, Direction.NORTH, f.p.door, false);   // back door to the alley (from the stock room)
        if (f.r(63) < 0.4) f.set(sx / 2, fy + 3, sz / 2, ceilingLamp(ModBlocks.SODIUM_LAMP));
    }

    /** Utility / maintenance building: machines, a locker to hide in, a crawlspace under a grate. */
    static void utility(Frame f) {
        int sx = f.sx, sz = f.sz, fy = f.fy;
        shell(f, 1, 5);
        f.door(sx - 1, fy, 2, f.front, Blocks.IRON_DOOR.defaultBlockState(), true);
        f.door(1, fy, 0, Direction.NORTH, f.p.door, false);
        for (int v = 2; v < sz - 2; v += 3) {
            f.set(1, fy, v, Blocks.BLAST_FURNACE.defaultBlockState().setValue(net.minecraft.world.level.block.BlastFurnaceBlock.FACING, f.front));
            f.set(1, fy + 1, v, ModBlocks.RUSTED_PIPE.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
            f.set(1, fy + 2, v, ModBlocks.RUSTED_PIPE.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
        }
        for (int v = 1; v < sz - 1; v++) f.set(Math.min(sx - 2, 3), fy + 3, v, ModBlocks.RUSTED_PIPE.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z));
        locker(f, sx - 2, fy, sz - 2, f.back);
        f.chest(sx - 2, fy, 1, Direction.SOUTH, f.lot.region == Region.IRONWORKS ? "industrial" : "residential");
        // a crawlspace: a grate over a one-block-high void running under the floor to the back
        for (int v = 3; v < sz - 3; v++) {
            f.set(sx / 2, fy - 1, v, ModBlocks.METAL_GRATE.defaultBlockState());
            f.set(sx / 2, fy - 2, v, Blocks.AIR.defaultBlockState());
        }
        if (f.r(70) < 0.6) f.set(sx / 2, fy + 3, sz / 2, ceilingLamp(ModBlocks.INDUSTRIAL_LAMP));
    }

    /** An open courtyard: low walls, a dead tree, benches, a barrel or two. */
    static void courtyard(Frame f) {
        int sx = f.sx, sz = f.sz, fy = f.fy;
        for (int u = 0; u < sx; u++)
            for (int v = 0; v < sz; v++) {
                boolean edge = u == 0 || u == sx - 1 || v == 0 || v == sz - 1;
                f.set(u, fy - 1, v, (u + v) % 5 == 0 ? ModBlocks.GRIME_SOIL.defaultBlockState() : ModBlocks.SIDEWALK.defaultBlockState());
                if (edge && f.r(u, v, 80) < 0.8 && !(u == sx - 1 && Math.abs(v - sz / 2) < 2)) f.set(u, fy, v, ModBlocks.CONCRETE_LOW_WALL.defaultBlockState());
            }
        int cu = sx / 2, cv = sz / 2;
        for (int y = fy; y < fy + 4; y++) f.set(cu, y, cv, ModBlocks.ROTTEN_LOG.defaultBlockState());
        f.set(cu + 1, fy + 3, cv, ModBlocks.WORN_FENCE.defaultBlockState());
        f.set(cu, fy + 3, cv + 1, ModBlocks.WORN_FENCE.defaultBlockState());
        f.set(2, fy, 2, ModBlocks.WORN_BENCH.defaultBlockState().setValue(StairBlock.FACING, f.back));
        f.set(2, fy, 3, ModBlocks.WORN_BENCH.defaultBlockState().setValue(StairBlock.FACING, f.back));
        f.barrel(1, fy, sz - 2, f.r(81) < 0.5 ? "residential" : null);
        f.set(sx - 2, fy, sz - 2, ModBlocks.CONCRETE_RUBBLE.defaultBlockState());
    }

    // --------------------------------------------------------------- Ironworks

    /**
     * Industrial hall: a tall steel-framed shed, a big front door, machinery, a catwalk along the back wall reached
     * by a ladder, lockers to hide in, overhead pipes and hanging chains, a grated floor that rings underfoot.
     */
    static void hall(Frame f) {
        int sx = f.sx, sz = f.sz, fy = f.fy;
        int H = 11;
        for (int u = 0; u < sx; u++)
            for (int v = 0; v < sz; v++) {
                boolean edge = u == 0 || u == sx - 1 || v == 0 || v == sz - 1;
                boolean pillar = edge && (v % 6 == 0 || u == 0 || u == sx - 1) && (v % 6 == 0);
                f.set(u, fy - 1, v, (u + v) % 7 == 0 ? ModBlocks.DARK_METAL.defaultBlockState() : ModBlocks.METAL_GRATE.defaultBlockState());
                f.set(u, fy - 2, v, ModBlocks.WEATHERED_CONCRETE.defaultBlockState());
                for (int y = fy; y < fy + H; y++) {
                    if (pillar) f.set(u, y, v, ModBlocks.STEEL_BEAM.defaultBlockState());
                    else if (edge) f.set(u, y, v, y > fy + H - 4 && v % 3 == 1 && u != 0 ? f.alongU(ModBlocks.RUSTED_BARS.defaultBlockState()) : f.p.wall(f.r(u, y, v)));
                    else f.set(u, y, v, Blocks.AIR.defaultBlockState());
                }
                // roof with skylights
                boolean sky = !edge && v % 8 == 4 && u > 2 && u < sx - 3;
                f.set(u, fy + H, v, sky ? ModBlocks.RUSTED_BARS.defaultBlockState() : ModBlocks.DARK_METAL.defaultBlockState());
            }
        // front door: 3 wide, 4 high; a side door
        int dv = sz / 2;
        for (int v = dv - 1; v <= dv + 1; v++) for (int y = fy; y < fy + 4; y++) f.set(sx - 1, y, v, Blocks.AIR.defaultBlockState());
        f.door(sx / 2, fy, sz - 1, Direction.SOUTH, f.p.door, true);
        // catwalk along the back wall at fy+6, ladder up, railing
        int cy = fy + 6;
        for (int v = 1; v < sz - 1; v++) {
            f.set(1, cy - 1, v, ModBlocks.METAL_GRATE.defaultBlockState());
            f.set(2, cy - 1, v, ModBlocks.METAL_GRATE.defaultBlockState());
            f.set(3, cy, v, f.alongVBars());
        }
        for (int y = fy; y < cy; y++) f.set(1, y, 2, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, f.front));
        f.chest(1, cy, sz - 2, Direction.NORTH, "industrial");
        // machinery islands
        for (int v = 4; v < sz - 4; v += 7) {
            int u = 5 + (int) (f.r(v, 90) * Math.max(1, sx - 9));
            machine(f, u, fy, v);
        }
        // lockers along the back wall (hiding places)
        for (int v = 4; v < sz - 2; v += 5) if (f.r(v, 91) < 0.7) locker(f, 1, fy, v, f.front);
        // overhead pipes and chains
        for (int v = 1; v < sz - 1; v++) {
            f.set(sx - 3, fy + H - 2, v, ModBlocks.RUSTED_PIPE.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z));
            if (v % 9 == 3) for (int y = fy + 4; y < fy + H; y++) f.set(sx / 2, y, v, Blocks.CHAIN.defaultBlockState());
        }
        // crates (barrels) and a lamp or two
        for (int i = 0; i < 4; i++) {
            int v = 2 + (int) (f.r(i, 92) * (sz - 4)), u = sx - 3;
            f.barrel(u, fy, v, f.r(i, 93) < 0.3 ? "industrial" : null);
            if (f.r(i, 94) < 0.5) f.barrel(u, fy + 1, v, null);
        }
        for (int v = 6; v < sz; v += 14) if (f.r(v, 95) < 0.6) f.set(sx / 2, fy + H - 1, v, ceilingLamp(ModBlocks.INDUSTRIAL_LAMP));
    }

    private static void machine(Frame f, int u, int fy, int v) {
        double r = f.r(u, v, 96);
        if (r < 0.35) {          // press
            f.fill(u, fy, v, u + 1, fy + 2, v + 1, ModBlocks.DARK_METAL.defaultBlockState());
            f.set(u, fy + 3, v, Blocks.PISTON.defaultBlockState().setValue(net.minecraft.world.level.block.piston.PistonBaseBlock.FACING, Direction.DOWN));
            f.set(u + 1, fy + 3, v + 1, Blocks.HOPPER.defaultBlockState());
        } else if (r < 0.7) {    // furnaces
            f.set(u, fy, v, Blocks.BLAST_FURNACE.defaultBlockState().setValue(net.minecraft.world.level.block.BlastFurnaceBlock.FACING, f.front));
            f.set(u, fy + 1, v, Blocks.BLAST_FURNACE.defaultBlockState().setValue(net.minecraft.world.level.block.BlastFurnaceBlock.FACING, f.front));
            f.set(u, fy + 2, v, ModBlocks.RUSTED_PIPE.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
            f.set(u + 1, fy, v, Blocks.ANVIL.defaultBlockState());
        } else {                 // a tank
            f.fill(u, fy, v, u + 2, fy + 3, v + 2, ModBlocks.RUSTED_PLATE.defaultBlockState());
            f.set(u + 1, fy + 4, v + 1, Blocks.LIGHTNING_ROD.defaultBlockState());
        }
    }

    /**
     * A locker: a one-block cell 2 high with a door, metal sides and top. Hiding inside blocks every line of
     * sight; a nose (or a Crawler through the door gap) can still find you.
     */
    private static void locker(Frame f, int u, int y, int v, Direction doorFacing) {
        BlockState m = ModBlocks.RUSTED_PLATE.defaultBlockState();
        f.set(u, y, v, Blocks.AIR.defaultBlockState());
        f.set(u, y + 1, v, Blocks.AIR.defaultBlockState());
        f.set(u, y + 2, v, m);
        f.set(u, y, v - 1, m);
        f.set(u, y + 1, v - 1, m);
        f.set(u, y, v + 1, m);
        f.set(u, y + 1, v + 1, m);
        int du = doorFacing == f.front ? 1 : -1;
        f.door(u + du, y, v, doorFacing, Blocks.SPRUCE_DOOR.defaultBlockState(), false);
    }

    /** Industrial yard: stacked containers (a door in one of them), pipe racks, an anchor block for chains. */
    static void yard(Frame f) {
        int sx = f.sx, sz = f.sz, fy = f.fy;
        for (int u = 0; u < sx; u++) for (int v = 0; v < sz; v++) f.set(u, fy - 1, v, (u * 3 + v) % 5 == 0 ? ModBlocks.RUSTED_PLATE.defaultBlockState() : ModBlocks.CONCRETE_FLOOR.defaultBlockState());
        // containers: 3 wide, 3 high, 6 long along v
        for (int i = 0; i * 7 + 6 < sz; i++) {
            int v0 = i * 7 + 1, u0 = 1;
            BlockState c = i % 2 == 0 ? ModBlocks.CONTAINER_RED.defaultBlockState() : ModBlocks.CONTAINER_TEAL.defaultBlockState();
            for (int u = u0; u < u0 + 3; u++) for (int v = v0; v < v0 + 6; v++) for (int y = fy; y < fy + 3; y++) {
                boolean shell = u == u0 || u == u0 + 2 || v == v0 || v == v0 + 5 || y == fy + 2;
                f.set(u, y, v, shell ? c : Blocks.AIR.defaultBlockState());
            }
            f.door(u0 + 2, fy, v0 + 2, f.front, Blocks.IRON_DOOR.defaultBlockState(), f.r(i, 100) < 0.6);
            if (f.r(i, 101) < 0.5) f.chest(u0 + 1, fy, v0 + 4, Direction.NORTH, "industrial");
            if (f.r(i, 102) < 0.4) for (int u = u0; u < u0 + 3; u++) for (int v = v0 + 1; v < v0 + 5; v++) f.set(u, fy + 3, v, c);
        }
        // pipe rack along the front
        for (int v = 0; v < sz; v++) {
            if (v % 5 == 0) for (int y = fy; y < fy + 5; y++) f.set(sx - 2, y, v, ModBlocks.DARK_METAL.defaultBlockState());
            f.set(sx - 2, fy + 5, v, ModBlocks.RUSTED_PIPE.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z));
        }
        if (f.r(103) < 0.5) f.set(sx - 2, fy + 4, (sz / 2) % 5 == 0 ? sz / 2 + 1 : sz / 2, ceilingLamp(ModBlocks.SODIUM_LAMP));   // under the pipe rack
    }

    // ---------------------------------------------------------------- the Ward

    /** Laboratory: one long storey, benches with glassware, a sealed specimen room, cabinets. */
    static void lab(Frame f) {
        int sx = f.sx, sz = f.sz, fy = f.fy;
        shell(f, 1, 5);
        f.door(sx - 1, fy, 2, f.front, f.p.door, false);
        f.door(1, fy, 0, Direction.NORTH, f.p.door, true);
        for (int v = 2; v < sz - 4; v += 3) {
            for (int u = 4; u < sx - 3; u++) f.set(u, fy, v, ModBlocks.LAB_SLAB.defaultBlockState());
            f.set(5, fy + 1, v, Blocks.BREWING_STAND.defaultBlockState());
            if (sx > 9) f.set(7, fy + 1, v, ModBlocks.GRIMY_GLASS.defaultBlockState());
        }
        // specimen room at the far end, behind glass
        for (int u = 1; u < sx - 1; u++) for (int y = fy; y < fy + 4; y++) f.set(u, y, sz - 4, f.pane(false));
        f.door(sx / 2, fy, sz - 4, Direction.SOUTH, f.p.door, false);
        f.chest(1, fy, sz - 2, Direction.NORTH, "medical");
        f.set(sx - 2, fy, sz - 2, Blocks.SCULK_CATALYST.defaultBlockState());
        locker(f, 1, fy, 3, f.front);
        for (int v = 1; v < sz - 1; v += 2) if (f.r(v, 110) < f.infest) f.set(4 + (int) (f.r(v, 111) * Math.max(1, sx - 6)), fy, v, vein());
        if (f.r(112) < 0.5) f.set(sx / 2, fy + 3, sz / 2, ceilingLamp(ModBlocks.EMERGENCY_LAMP));
    }

    // ------------------------------------------------------------ the Expanse

    /** What is left of a building: broken walls of uneven height, no roof, rubble, sometimes a cache. */
    static void ruin(Frame f) {
        int sx = f.sx, sz = f.sz, fy = f.fy;
        for (int u = 0; u < sx; u++)
            for (int v = 0; v < sz; v++) {
                boolean edge = u == 0 || u == sx - 1 || v == 0 || v == sz - 1;
                f.set(u, fy - 1, v, f.r(u, v, 120) < 0.2 ? ModBlocks.CONCRETE_RUBBLE.defaultBlockState() : ModBlocks.CONCRETE_FLOOR.defaultBlockState());
                if (!edge) {
                    if (f.r(u, v, 121) < 0.06) f.set(u, fy, v, ModBlocks.CONCRETE_RUBBLE.defaultBlockState());
                    continue;
                }
                double n = Noise.value2(f.h, (f.x(u) + f.z(v)) / 5.0, 0) * 0.5 + 0.5;
                int hgt = (int) (n * 7) - 1;
                for (int y = fy; y < fy + hgt; y++) f.set(u, y, v, f.p.wall(f.r(u, y, v, 122)));
            }
        if (f.r(123) < 0.45) f.chest(1, fy, 1, f.front, "expanse");
    }

    /** Abandoned checkpoint: a booth with windows, a desk, a lamp, a barrier half across the street. */
    static void checkpoint(Frame f) {
        int sx = f.sx, sz = f.sz, fy = f.fy;
        int u0 = sx - 5, v0 = sz / 2 - 2;
        for (int u = u0; u < u0 + 5; u++)
            for (int v = v0; v < v0 + 5; v++) {
                boolean edge = u == u0 || u == u0 + 4 || v == v0 || v == v0 + 4;
                f.set(u, fy - 1, v, ModBlocks.CONCRETE_FLOOR.defaultBlockState());
                for (int y = fy; y < fy + 3; y++) {
                    if (!edge) f.set(u, y, v, Blocks.AIR.defaultBlockState());
                    else if (y == fy + 1 && (u == u0 + 2 || v == v0 + 2)) f.set(u, y, v, u == u0 || u == u0 + 4 ? f.pane(true) : f.pane(false));
                    else f.set(u, y, v, ModBlocks.WEATHERED_CONCRETE.defaultBlockState());
                }
                f.set(u, fy + 3, v, ModBlocks.DARK_METAL.defaultBlockState());
            }
        f.door(u0, fy, v0 + 2, f.back, Blocks.IRON_DOOR.defaultBlockState(), true);
        f.chest(u0 + 3, fy, v0 + 1, f.back, "expanse");
        f.set(u0 + 1, fy, v0 + 3, ModBlocks.CONCRETE_SLAB.defaultBlockState());
        f.set(u0 + 2, fy + 2, v0 + 2, ceilingLamp(f.r(130) < 0.5 ? ModBlocks.EMERGENCY_LAMP : ModBlocks.SODIUM_LAMP));
        // the barrier: a line of fence posts and a lowered boom into the street
        for (int k = 1; k <= 6; k++) {
            int x = f.x(sx - 1) + (f.front == Direction.EAST ? k : -k);
            f.w.set(x, fy, f.z(v0 + 2), k % 3 == 0 ? ModBlocks.CONCRETE_LOW_WALL.defaultBlockState() : Blocks.AIR.defaultBlockState());
            f.w.set(x, fy + 1, f.z(v0 + 2), ModBlocks.ROTTEN_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
        }
    }

    /** Shelter: the low concrete bunker with a door that closes, a bed, a cache (the "shelter" loot). */
    static void shelter(Frame f) {
        int sx = Math.min(f.sx, 9), sz = Math.min(f.sz, 9), fy = f.fy;
        int x0 = Math.min(f.x(0), f.x(sx - 1)), z0 = f.z(0);
        StructurePass.placeBunker(f.w, x0, z0, sx, sz, fy, f.front, f.r(140) < 0.5);
        // the bunker cuts a 2-wide opening on its front face: a door in one half, wall in the other
        int mz = (z0 + z0 + sz - 1) / 2;
        int fx = f.front == Direction.EAST ? x0 + sx - 1 : x0;
        BlockState door = Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, f.front).setValue(DoorBlock.OPEN, false);
        f.w.set(fx, fy, mz, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        f.w.set(fx, fy + 1, mz, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        f.w.set(fx, fy, mz + 1, ModBlocks.CONCRETE_PANEL.defaultBlockState());
        f.w.set(fx, fy + 1, mz + 1, ModBlocks.CONCRETE_PANEL.defaultBlockState());
        // iron doors need a button inside and out
        int ix = f.front == Direction.EAST ? fx - 1 : fx + 1, ox = f.front == Direction.EAST ? fx + 1 : fx - 1;
        f.w.set(ix, fy + 1, mz + 1, Blocks.STONE_BUTTON.defaultBlockState()
                .setValue(net.minecraft.world.level.block.ButtonBlock.FACING, f.back)
                .setValue(net.minecraft.world.level.block.ButtonBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.WALL));
        f.w.set(ox, fy + 1, mz + 1, Blocks.STONE_BUTTON.defaultBlockState()
                .setValue(net.minecraft.world.level.block.ButtonBlock.FACING, f.front)
                .setValue(net.minecraft.world.level.block.ButtonBlock.FACE, net.minecraft.world.level.block.state.properties.AttachFace.WALL));
        int bx = f.front == Direction.EAST ? x0 + 1 : x0 + sx - 2;
        int bxHead = f.front == Direction.EAST ? x0 + 2 : x0 + sx - 3;
        f.w.set(bx, fy, z0 + 1, Blocks.GRAY_BED.defaultBlockState().setValue(BedBlock.FACING, f.front).setValue(BedBlock.PART, BedPart.FOOT));
        f.w.set(bxHead, fy, z0 + 1, Blocks.GRAY_BED.defaultBlockState().setValue(BedBlock.FACING, f.front).setValue(BedBlock.PART, BedPart.HEAD));
        int cx = f.front == Direction.EAST ? x0 + 1 : x0 + sx - 2;
        f.w.set(cx, fy, z0 + sz - 2, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, f.front));
        f.w.lootChest(cx, fy, z0 + sz - 2, "minecraft:chest", "interstice:chests/shelter", Hash.of(f.h, 141));
    }

    /** A field of debris: rubble mounds, a fallen beam, a toppled lamp post. Cover, nothing more. */
    static void debris(Frame f) {
        int sx = f.sx, sz = f.sz, fy = f.fy;
        for (int i = 0; i < 6; i++) {
            int u = (int) (f.r(i, 150) * sx), v = (int) (f.r(i, 151) * sz);
            int hgt = 1 + (int) (f.r(i, 152) * 3);
            for (int du = -1; du <= 1; du++)
                for (int dv = -1; dv <= 1; dv++)
                    for (int y = fy; y < fy + hgt - (Math.abs(du) + Math.abs(dv) > 0 ? 1 : 0); y++) f.set(u + du, y, v + dv, ModBlocks.CONCRETE_RUBBLE.defaultBlockState());
        }
        int v = sz / 2;
        for (int u = 1; u < sx - 1; u++) f.set(u, fy, v, ModBlocks.DARK_METAL.defaultBlockState());
        if (f.r(153) < 0.3) f.chest(sx / 2, fy, v + 1, f.front, "expanse");
    }

    // ----------------------------------------------------------------- shared

    /** Plain one-storey shell of height {@code h} (walls, floor, flat roof), interior cleared. */
    private static void shell(Frame f, int floors, int h) {
        int sx = f.sx, sz = f.sz, fy = f.fy;
        for (int u = 0; u < sx; u++)
            for (int v = 0; v < sz; v++) {
                boolean edge = u == 0 || u == sx - 1 || v == 0 || v == sz - 1;
                f.set(u, fy - 1, v, f.grow(f.p.floor, u, fy - 1, v));
                for (int y = fy; y < fy + h - 1; y++) {
                    if (edge) f.wall(u, y, v);
                    else f.set(u, y, v, Blocks.AIR.defaultBlockState());
                }
                f.set(u, fy + h - 1, v, f.p.roof);
            }
    }

    /**
     * Street furniture along the centre line, region by region: wrecked cars and benches in the District, pipe
     * bridges and cable reels in the Ironworks, overturned stretchers and moss in the Ward, nothing much in the Expanse.
     */
    private static void streetDetails(ChunkWriter w) {
        IntersticeLayout L = w.layout;
        CorridorPlan plan = L.plan();
        for (int c = Math.floorDiv(w.minZ, 37); c <= Math.floorDiv(w.maxZ, 37); c++) {
            long h = Hash.of(L.sub(1301), c);
            int z = c * 37 + (int) (Hash.unit(h, 0) * 30);
            if (!plan.inside(z) || Math.abs(z) < 40 || L.nearLandmark(z, 80)) continue;
            Region region = plan.regionForLot(z, h);
            double roll = Hash.unit(h, 1);
            int side = Hash.unit(h, 2) < 0.5 ? -1 : 1;
            int x = (int) Math.floor(streetCenter(L, z + 0.5) + side * (2 + Hash.unit(h, 3) * 3));
            int fy = L.floorY(x, z);
            if (L.pitAt(x, z) != null || lotAt(L, x, z, 2) != null) continue;
            switch (region) {
                case FORGOTTEN_DISTRICT -> {
                    if (roll < 0.35) car(w, x, fy, z, h);
                    else if (roll < 0.55) {
                        w.set(x, fy, z, ModBlocks.WORN_BENCH.defaultBlockState().setValue(StairBlock.FACING, side < 0 ? Direction.WEST : Direction.EAST));
                        w.set(x, fy, z + 1, ModBlocks.WORN_BENCH.defaultBlockState().setValue(StairBlock.FACING, side < 0 ? Direction.WEST : Direction.EAST));
                    } else if (roll < 0.7) {   // barricade
                        for (int k = -2; k <= 2; k++) w.set(x + k, fy, z, ModBlocks.WORN_FENCE.defaultBlockState()
                                .setValue(net.minecraft.world.level.block.FenceBlock.EAST, k < 2).setValue(net.minecraft.world.level.block.FenceBlock.WEST, k > -2));
                    }
                }
                case IRONWORKS -> {
                    if (roll < 0.4) {          // cable reel
                        w.set(x, fy, z, ModBlocks.ROTTEN_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
                        w.set(x + 1, fy, z, ModBlocks.ROTTEN_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));
                    } else if (roll < 0.6) {   // grate patch over a drain: loud to walk on
                        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) w.set(x + dx, fy - 1, z + dz, ModBlocks.METAL_GRATE.defaultBlockState());
                    }
                }
                case CONTAMINATED_WARD -> {
                    double inf = plan.progress(Region.CONTAMINATED_WARD, z);
                    if (roll < 0.3) {          // overturned stretcher
                        w.set(x, fy, z, Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT));
                        w.set(x, fy, z + 1, Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.HEAD));
                    }
                    int r = 1 + (int) (inf * 4);
                    for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++)
                        if (dx * dx + dz * dz <= r * r && Hash.unit(h, dx + 10, dz + 10) < 0.3 + inf * 0.5)
                            w.setIfAir(x + dx, fy, z + dz, vein());
                }
                case DEAD_EXPANSE -> {
                    if (roll < 0.2) for (int k = 0; k < 3; k++) w.set(x + k, fy, z, ModBlocks.CONCRETE_RUBBLE.defaultBlockState());
                }
            }
        }
    }

    /** A fixture hanging from the ceiling above it. */
    static BlockState ceilingLamp(net.minecraft.world.level.block.Block lamp) {
        return com.interstice.block.LampBlock.facing(lamp.defaultBlockState(), Direction.DOWN);
    }

    /** Sculk vein lying on the floor (a multiface block needs at least one face). */
    static BlockState vein() {
        return ModBlocks.GROWTH_VEINS.defaultBlockState();
    }

    /** A burnt-out car: a dark body, a roof, glass, flat on the street. */
    private static void car(ChunkWriter w, int x, int fy, int z, long h) {
        BlockState body = Hash.unit(h, 9) < 0.5 ? ModBlocks.DARK_METAL.defaultBlockState() : ModBlocks.OXIDIZED_PLATE.defaultBlockState();
        for (int dz = 0; dz < 4; dz++)
            for (int dx = 0; dx < 2; dx++) {
                w.set(x + dx, fy, z + dz, body);
                if (dz == 1 || dz == 2) w.set(x + dx, fy + 1, z + dz, ModBlocks.GRIMY_GLASS.defaultBlockState());
            }
        w.set(x, fy + 1, z, ModBlocks.DARK_METAL.defaultBlockState());
    }
}
