package com.interstice.world.generation;

import com.interstice.registry.ModBlocks;
import com.interstice.util.Hash;
import com.interstice.util.Noise;
import com.interstice.world.IntersticeLayout;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Macro pass: walls (with relief and maintenance passages), the corridor floor,
 * drainage gutters and deep shafts. Evaluated column by column from pure layout
 * functions, so neighbouring chunks always agree.
 */
public final class TerrainPass {
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();

    public static final int PASSAGE_Y = IntersticeLayout.FLOOR_Y + 1;
    public static final int PASSAGE_HEIGHT = 3;
    public static final double PASSAGE_D0 = 6, PASSAGE_D1 = 9;

    private TerrainPass() {}

    public static void run(ChunkWriter w) {
        IntersticeLayout L = w.layout;
        int minY = IntersticeLayout.MIN_Y;
        int top = IntersticeLayout.MAX_Y;
        long matSeed = L.sub(401);

        // Per-z caches: everything wall-related depends on z only (plus y).
        int[][] reliefW = new int[16][];
        int[][] reliefE = new int[16][];
        double[] cx = new double[16], hw = new double[16];
        for (int lz = 0; lz < 16; lz++) {
            int z = w.minZ + lz;
            cx[lz] = L.centerX(z + 0.5);
            hw[lz] = L.halfWidth(z + 0.5);
        }

        for (int lz = 0; lz < 16; lz++) {
            int z = w.minZ + lz;
            for (int lx = 0; lx < 16; lx++) {
                int x = w.minX + lx;
                double off = x + 0.5 - cx[lz];
                int side = off < 0 ? IntersticeLayout.WEST : IntersticeLayout.EAST;
                double d = Math.abs(off) - hw[lz];
                int[][] reliefCache = side < 0 ? reliefW : reliefE;
                boolean nearWall = d > -12;
                int[] relief = null;
                if (nearWall) {
                    if (reliefCache[lz] == null) {
                        int[] r = new int[top - minY + 1];
                        for (int y = minY; y <= top; y++) r[y - minY] = y < IntersticeLayout.FLOOR_Y - 2 ? 0 : L.relief(side, y, z);
                        reliefCache[lz] = r;
                    }
                    relief = reliefCache[lz];
                }
                IntersticeLayout.Pit pit = d < -8 ? L.pitAt(x, z) : null;
                // the finite Corridor (version 2): beyond either end everything is bulkhead, with the exit at the far end
                com.interstice.world.CorridorPlan plan = L.plan();
                double beyond = plan == null ? 0 : plan.beyond(z + 0.5);
                if (beyond > 0) {
                    bulkhead(w, L, plan, matSeed, x, z, d, beyond);
                    continue;
                }
                int floorY = L.floorY(x, z);
                boolean doorway = nearWall && isDoorway(L, side, z);
                boolean passageCol = nearWall && d >= PASSAGE_D0 && d < PASSAGE_D1 && !isCollapsed(L, side, z);

                for (int y = minY; y <= top; y++) {
                    BlockState s;
                    if (y == minY) {
                        s = BEDROCK;
                    } else if (nearWall && d >= -relief[y - minY] && y >= floorY - 1) {
                        // inside the wall mass
                        if (d >= IntersticeLayout.WALL_THICKNESS) {
                            s = y < IntersticeLayout.FLOOR_Y ? ModBlocks.WEATHERED_CONCRETE.defaultBlockState() : AIR;
                        } else if (y >= IntersticeLayout.WALL_TOP) {
                            s = AIR;
                        } else if (y >= PASSAGE_Y && y < PASSAGE_Y + PASSAGE_HEIGHT && (passageCol || (doorway && d < PASSAGE_D0))) {
                            s = AIR;
                        } else {
                            s = wallMaterial(L, matSeed, side, x, y, z, d, relief[y - minY]);
                        }
                    } else if (y < floorY) {
                        if (pit != null && pit.contains(x, z) && y > IntersticeLayout.PIT_BOTTOM) {
                            if (y <= IntersticeLayout.PIT_BOTTOM + 3) s = WATER;
                            else if (pit.grated() && y == floorY - 1) s = ModBlocks.METAL_GRATE.defaultBlockState();
                            else s = AIR;
                        } else if (pit != null && y == floorY - 1) {
                            s = ModBlocks.DARK_METAL.defaultBlockState(); // rim
                        } else if (y == floorY - 1) {
                            if (d > -3.0 && d < -1.0) s = ModBlocks.METAL_GRATE.defaultBlockState();
                            else s = floorMaterial(L, matSeed, x, z, d);
                        } else if (y == floorY - 2 && d > -3.0 && d < -1.0) {
                            s = AIR; // drainage channel under the grate
                        } else {
                            s = ModBlocks.WEATHERED_CONCRETE.defaultBlockState();
                        }
                    } else {
                        s = AIR;
                    }
                    if (s != AIR) w.set(x, y, z, s);
                }

                // ladder out of each shaft
                if (pit != null && pit.contains(x, z) && !pit.grated() && x == pit.x0() && z == pit.z0() + pit.sz() / 2) {
                    BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.EAST);
                    for (int y = IntersticeLayout.PIT_BOTTOM + 1; y < floorY; y++) w.set(x, y, z, ladder);
                }
            }
        }
    }

    /** Corridor floor, by region in a version-2 world (asphalt and pavements, plate and grates, tiles, bare rubble). */
    static BlockState floorMaterial(IntersticeLayout L, long seed, int x, int z, double d) {
        com.interstice.world.CorridorPlan plan = L.plan();
        if (plan == null) return floorMaterial(seed, x, z);
        double off = Math.abs(x + 0.5 - com.interstice.world.structures.Settlements.streetCenter(L, z + 0.5));
        double n = Noise.value2(seed, x / 5.0, z / 5.0);
        com.interstice.world.structures.Settlements.Cross cross = com.interstice.world.structures.Settlements.crossing(L, z, z);
        return switch (plan.regionForColumn(x, z)) {
            case FORGOTTEN_DISTRICT -> {
                if (cross != null && d < -5) {
                    if (cross.plaza()) yield n > 0.6 ? ModBlocks.CRACKED_CONCRETE.defaultBlockState() : ModBlocks.SIDEWALK.defaultBlockState();
                    // zebra crossing where the cross street meets the main road
                    if (off < com.interstice.world.structures.Settlements.STREET_HALF + 3 && off > com.interstice.world.structures.Settlements.STREET_HALF - 2
                            && Math.floorMod(z, 2) == 0) yield ModBlocks.CONCRETE_PANEL.defaultBlockState();
                    yield n > 0.62 ? ModBlocks.CONCRETE_RUBBLE.defaultBlockState() : ModBlocks.ASPHALT.defaultBlockState();
                }
                if (off < com.interstice.world.structures.Settlements.STREET_HALF - 1) {
                    // the centre line: exactly one column (the one holding the street's centre), dashed
                    if (x == (int) Math.floor(com.interstice.world.structures.Settlements.streetCenter(L, z + 0.5)) && Math.floorMod(z, 6) < 3)
                        yield ModBlocks.ASPHALT_LINE.defaultBlockState();
                    yield n > 0.6 ? ModBlocks.CONCRETE_RUBBLE.defaultBlockState() : ModBlocks.ASPHALT.defaultBlockState();
                }
                yield n > 0.62 ? ModBlocks.CRACKED_CONCRETE.defaultBlockState() : n < -0.55 ? ModBlocks.CONCRETE_FLOOR.defaultBlockState() : ModBlocks.SIDEWALK.defaultBlockState();
            }
            case IRONWORKS -> n > 0.62 ? ModBlocks.RUSTED_PLATE.defaultBlockState() : n > 0.4 ? ModBlocks.OXIDIZED_PLATE.defaultBlockState()
                    : n < -0.6 ? ModBlocks.DARK_METAL.defaultBlockState() : floorMaterial(seed, x, z);
            case CONTAMINATED_WARD -> {
                double inf = plan.progress(com.interstice.world.CorridorPlan.Region.CONTAMINATED_WARD, z);
                if (n > 0.9 - inf * 0.7) yield ModBlocks.FLESH_GROWTH.defaultBlockState();
                yield n < -0.5 ? ModBlocks.STAINED_TILE.defaultBlockState() : ModBlocks.CLINIC_FLOOR.defaultBlockState();
            }
            case DEAD_EXPANSE -> n > 0.35 ? ModBlocks.CONCRETE_RUBBLE.defaultBlockState() : n < -0.3 ? ModBlocks.CRACKED_CONCRETE.defaultBlockState() : floorMaterial(seed, x, z);
        };
    }

    /**
     * Columns beyond either end of a version-2 Corridor: a solid bulkhead up to the wall top, its face panelled.
     * In the far bulkhead, a doorway (the exit) leads to the threshold block that takes the player out.
     */
    private static void bulkhead(ChunkWriter w, IntersticeLayout L, com.interstice.world.CorridorPlan plan, long matSeed, int x, int z, double d, double beyond) {
        int minY = IntersticeLayout.MIN_Y;
        double off = x + 0.5 - L.centerX(z + 0.5);
        boolean far = z > plan.endZ;
        int depthIn = far ? z - plan.endZ : plan.startZ - z;
        boolean doorway = far && Math.abs(off) <= com.interstice.world.CorridorPlan.EXIT_HALF_WIDTH && depthIn <= com.interstice.world.CorridorPlan.EXIT_DEPTH;
        for (int y = minY; y <= IntersticeLayout.WALL_TOP - 1; y++) {
            BlockState s;
            if (y == minY) s = BEDROCK;
            else if (y < IntersticeLayout.FLOOR_Y - 1) s = ModBlocks.WEATHERED_CONCRETE.defaultBlockState();
            else if (doorway && y >= IntersticeLayout.FLOOR_Y && y < IntersticeLayout.FLOOR_Y + com.interstice.world.CorridorPlan.EXIT_HEIGHT) {
                if (depthIn == com.interstice.world.CorridorPlan.EXIT_DEPTH) s = ModBlocks.EXIT_THRESHOLD.defaultBlockState();
                else s = AIR;
            } else if (doorway && y == IntersticeLayout.FLOOR_Y - 1) s = ModBlocks.DARK_METAL.defaultBlockState();
            else if (depthIn <= 1 || (far && Math.abs(off) <= com.interstice.world.CorridorPlan.EXIT_HALF_WIDTH + 1 && depthIn <= com.interstice.world.CorridorPlan.EXIT_DEPTH)) {
                // the face: panels, a band of dark metal every 12 blocks, the exit framed in metal
                int ly = y - IntersticeLayout.FLOOR_Y;
                if (far && Math.abs(off) <= com.interstice.world.CorridorPlan.EXIT_HALF_WIDTH + 1) s = ModBlocks.DARK_METAL.defaultBlockState();
                else if (Math.floorMod(ly, 12) == 0) s = ModBlocks.DARK_METAL.defaultBlockState();
                else s = Math.floorMod(x, 6) == 0 ? ModBlocks.CONCRETE_PANEL.defaultBlockState() : wallMaterial(L, matSeed, 1, x, y, z, 1, 0);
            } else s = ModBlocks.WEATHERED_CONCRETE.defaultBlockState();
            w.set(x, y, z, s);
        }
    }

    static BlockState floorMaterial(long seed, int x, int z) {
        double n = Noise.value2(seed, x / 5.0, z / 5.0);
        if (n > 0.62) return ModBlocks.CONCRETE_RUBBLE.defaultBlockState();
        if (n < -0.55) return ModBlocks.STAINED_CONCRETE.defaultBlockState();
        if (Math.floorMod(x, 8) == 0 || Math.floorMod(z, 8) == 0) return ModBlocks.CONCRETE_PANEL.defaultBlockState();
        return ModBlocks.CONCRETE_FLOOR.defaultBlockState();
    }

    static BlockState wallMaterial(IntersticeLayout L, long seed, int side, int x, int y, int z, double d, int relief) {
        if (d > 3) return ModBlocks.WEATHERED_CONCRETE.defaultBlockState();
        if (d < 0 && relief > 0) {
            IntersticeLayout.Buttress b = L.buttress(side, Math.floorDiv(z, IntersticeLayout.BUTTRESS_CELL));
            if (b.metal() && b.containsZ(z)) {
                return Math.floorMod(y, 7) == 0 ? ModBlocks.DARK_METAL.defaultBlockState() : ModBlocks.RUSTED_PLATE.defaultBlockState();
            }
            return Math.floorMod(y, 9) == 0 ? ModBlocks.CONCRETE_PANEL.defaultBlockState() : ModBlocks.WEATHERED_CONCRETE.defaultBlockState();
        }
        if (y < IntersticeLayout.FLOOR_Y + 5) return ModBlocks.STAINED_CONCRETE.defaultBlockState();
        int ly = y - IntersticeLayout.FLOOR_Y;
        if (Math.floorMod(ly, 16) == 0 || Math.floorMod(z, 24) == 0) return ModBlocks.CONCRETE_PANEL.defaultBlockState();
        double streak = Noise.value2(seed, (x + z) / 3.0, y / 40.0);
        if (streak > 0.6) return ModBlocks.STAINED_CONCRETE.defaultBlockState();
        double crack = Noise.value3(Hash.of(seed, 7), x / 6.0, y / 6.0, z / 6.0);
        if (crack > 0.58) return ModBlocks.CRACKED_CONCRETE.defaultBlockState();
        return ModBlocks.WEATHERED_CONCRETE.defaultBlockState();
    }

    // ---------------------------------------------------- maintenance passages

    public static boolean isDoorway(IntersticeLayout L, int side, int z) {
        int c = Math.floorDiv(z, 40);
        long h = Hash.of(L.sub(402), side, c);
        if (Hash.unit(h, 0) > 0.7) return false;
        int z0 = c * 40 + 5 + (int) (Hash.unit(h, 1) * 28);
        return z >= z0 && z < z0 + 2;
    }

    public static int doorwayZ(IntersticeLayout L, int side, int cell) {
        long h = Hash.of(L.sub(402), side, cell);
        if (Hash.unit(h, 0) > 0.7) return Integer.MIN_VALUE;
        return cell * 40 + 5 + (int) (Hash.unit(h, 1) * 28);
    }

    public static boolean isCollapsed(IntersticeLayout L, int side, int z) {
        int c = Math.floorDiv(z, 80);
        long h = Hash.of(L.sub(403), side, c);
        if (Hash.unit(h, 0) > 0.22) return false;
        int z0 = c * 80 + (int) (Hash.unit(h, 1) * 60);
        int len = 6 + (int) (Hash.unit(h, 2) * 8);
        return z >= z0 && z < z0 + len;
    }
}
