package com.interstice.world.structures;

import com.interstice.registry.ModBlocks;
import com.interstice.util.Hash;
import com.interstice.world.IntersticeLayout;
import com.interstice.world.generation.ChunkWriter;
import com.interstice.world.generation.TerrainPass;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Structure layer: suspended walkways, giant cross beams, wall platforms,
 * shelters, pipes and industrial lighting. Each feature is defined by a cell
 * along Z (or an XZ cell) and written through the chunk-clipping
 * {@link ChunkWriter}, so features spanning several chunks stay continuous.
 */
public final class StructurePass {
    private StructurePass() {}

    static final BlockState GRATE = ModBlocks.METAL_GRATE.defaultBlockState();
    static final BlockState DARK = ModBlocks.DARK_METAL.defaultBlockState();
    static final BlockState RUST = ModBlocks.RUSTED_PLATE.defaultBlockState();
    static final BlockState PANEL = ModBlocks.CONCRETE_PANEL.defaultBlockState();
    static final BlockState CONCRETE = ModBlocks.WEATHERED_CONCRETE.defaultBlockState();
    static final BlockState FLOOR = ModBlocks.CONCRETE_FLOOR.defaultBlockState();
    static final BlockState RUBBLE = ModBlocks.CONCRETE_RUBBLE.defaultBlockState();
    static final BlockState LAMP = ModBlocks.INDUSTRIAL_LAMP.defaultBlockState();
    static final BlockState SODIUM = ModBlocks.SODIUM_LAMP.defaultBlockState();
    static final BlockState EMERGENCY = ModBlocks.EMERGENCY_LAMP.defaultBlockState();
    static final BlockState AIR = Blocks.AIR.defaultBlockState();
    static final BlockState BARS_X = ModBlocks.RUSTED_BARS.defaultBlockState().setValue(IronBarsBlock.EAST, true).setValue(IronBarsBlock.WEST, true);
    static final BlockState BARS_Z = ModBlocks.RUSTED_BARS.defaultBlockState().setValue(IronBarsBlock.NORTH, true).setValue(IronBarsBlock.SOUTH, true);

    static BlockState flood(Direction dir) {
        return ModBlocks.FLOOD_LAMP.defaultBlockState().setValue(DirectionalBlock.FACING, dir);
    }

    public static void run(ChunkWriter w) {
        bridges(w);
        beams(w);
        for (int side = -1; side <= 1; side += 2) {
            platforms(w, side);
            pipes(w, side);
            wallLamps(w, side);
            wallUplights(w, side);
            highFloods(w, side);
            passageDetails(w, side);
            vents(w, side);
        }
        if (w.layout.plan() == null) bunkers(w);   // version 2: shelters are part of the settlements
        lampPosts(w);
        Landmarks.run(w);
        Settlements.run(w);
    }

    // -------------------------------------------------------------- lamp posts

    /** Floor-standing light posts along the corridor: keeps nearby traversal readable. Some are dead. */
    private static void lampPosts(ChunkWriter w) {
        IntersticeLayout L = w.layout;
        for (int c = Math.floorDiv(w.minZ, 34); c <= Math.floorDiv(w.maxZ, 34); c++) {
            long h = Hash.of(L.sub(511), c);
            int z = c * 34 + (int) (Hash.unit(h, 0) * 34);
            if (z < w.minZ || z > w.maxZ) continue;
            int side = (c & 1) == 0 ? -1 : 1;
            // version 2: street lights stand on the pavement edges of the (wandering) main street
            int x = L.plan() != null ? (int) Math.floor(Settlements.streetCenter(L, z + 0.5) + side * (Settlements.STREET_HALF + 1 + Hash.unit(h, 1) * 2))
                    : (int) Math.floor(L.centerX(z + 0.5) + side * (6 + Hash.unit(h, 1) * 12));
            if (x < w.minX || x > w.maxX) continue;
            if (L.pitAt(x, z) != null || bunkerAt(L, x, z) != null || L.nearLandmark(z, 75)) continue;
            if (L.plan() != null) {
                // version 2: street lights in the District, a few in the Ironworks and the Ward, almost none in the Expanse
                if (!L.plan().inside(z) || Settlements.lotAt(L, x, z, 2) != null) continue;
                double keep = switch (L.plan().regionAt(z)) {
                    case FORGOTTEN_DISTRICT -> 1.0;
                    case IRONWORKS -> 0.6;
                    case CONTAMINATED_WARD -> 0.5;
                    case DEAD_EXPANSE -> 0.18;
                };
                if (Hash.unit(h, 7) > keep) continue;
            }
            int fy = L.floorY(x, z);
            int height = 4 + (int) (Hash.unit(h, 2) * 3);
            for (int y = fy; y < fy + height; y++) w.set(x, y, z, DARK);
            double roll = Hash.unit(h, 3);
            if (roll < 0.18) continue; // dead post: no fixture left
            w.set(x, fy + height, z, roll < 0.55 ? SODIUM : LAMP);
        }
    }

    // ----------------------------------------------------------------- bridges

    public static final int BRIDGE_CELL = 150;

    public record Bridge(int z0, int y, boolean broken, int gapHalf, long hash) {}

    public static Bridge bridge(IntersticeLayout L, int cell) {
        long h = Hash.of(L.sub(501), cell);
        if (Hash.unit(h, 0) > 0.55) return null;
        int z0 = cell * BRIDGE_CELL + 20 + (int) (Hash.unit(h, 1) * 110);
        if (L.nearLandmark(z0, 80)) return null;
        int y = IntersticeLayout.FLOOR_Y + 22 + (int) (Hash.unit(h, 2) * 130);
        boolean broken = Hash.unit(h, 3) < 0.35;
        return new Bridge(z0, y, broken, 3 + (int) (Hash.unit(h, 4) * 6), h);
    }

    private static void bridges(ChunkWriter w) {
        IntersticeLayout L = w.layout;
        for (int c = Math.floorDiv(w.minZ - 20, BRIDGE_CELL); c <= Math.floorDiv(w.maxZ + 20, BRIDGE_CELL); c++) {
            Bridge b = bridge(L, c);
            if (b == null || !w.overlapsZ(b.z0 - 2, b.z0 + 8)) continue;
            int zc = b.z0 + 1;
            int xl = w.xAt(-1, -0.0, zc) - 2;
            int xr = w.xAt(1, -0.0, zc) + 2;
            double cx = L.centerX(zc);
            for (int x = xl; x <= xr; x++) {
                if (b.broken) {
                    int jag = (int) (Hash.unit(b.hash, x, 9) * 3);
                    if (Math.abs(x - cx) < b.gapHalf + jag) continue;
                }
                for (int dz = 0; dz < 3; dz++) {
                    w.set(x, b.y, b.z0 + dz, dz == 1 ? GRATE : DARK);
                }
                w.set(x, b.y - 1, b.z0 + 1, RUST); // spine
                w.setIfAir(x, b.y + 1, b.z0 - 1, BARS_X);
                w.setIfAir(x, b.y + 1, b.z0 + 3, BARS_X);
                if (Math.floorMod(x, 12) == 0) {
                    w.setIfAir(x, b.y - 2, b.z0 + 1, Hash.unit(b.hash, x, 3) < 0.25 ? EMERGENCY : LAMP);
                }
            }
            // access ladder on the west side, on a free-standing steel post
            int lx = w.xAt(-1, -9, b.z0 + 4);
            for (int y = IntersticeLayout.FLOOR_Y - 1; y < b.y; y++) {
                w.set(lx, y, b.z0 + 4, DARK);
                w.set(lx, y, b.z0 + 5, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
            }
            for (int x = lx - 1; x <= lx + 1; x++)
                for (int z = b.z0 + 3; z <= b.z0 + 6; z++)
                    if (!(x == lx && z == b.z0 + 5)) w.set(x, b.y, z, x == lx && z == b.z0 + 4 ? DARK : GRATE);
        }
    }

    // ------------------------------------------------------------------- beams

    public static final int BEAM_CELL = 230;

    public record Beam(int z0, int zLen, int y0, int yLen) {}

    public static Beam beam(IntersticeLayout L, int cell) {
        long h = Hash.of(L.sub(502), cell);
        if (Hash.unit(h, 0) > 0.65) return null;
        return new Beam(cell * BEAM_CELL + 10 + (int) (Hash.unit(h, 1) * 190), 6 + (int) (Hash.unit(h, 2) * 5),
                232 + (int) (Hash.unit(h, 3) * 48), 6 + (int) (Hash.unit(h, 4) * 7));
    }

    private static void beams(ChunkWriter w) {
        IntersticeLayout L = w.layout;
        for (int c = Math.floorDiv(w.minZ - 16, BEAM_CELL); c <= Math.floorDiv(w.maxZ + 16, BEAM_CELL); c++) {
            Beam b = beam(L, c);
            if (b == null || !w.overlapsZ(b.z0, b.z0 + b.zLen)) continue;
            int zc = b.z0 + b.zLen / 2;
            int xl = w.xAt(-1, 0, zc) - 3, xr = w.xAt(1, 0, zc) + 3;
            for (int x = xl; x <= xr; x++) {
                for (int z = b.z0; z < b.z0 + b.zLen; z++) {
                    for (int y = b.y0; y < b.y0 + b.yLen; y++) {
                        boolean shell = y == b.y0 || z == b.z0 || z == b.z0 + b.zLen - 1 || y == b.y0 + b.yLen - 1;
                        w.set(x, y, z, y == b.y0 ? RUST : shell ? DARK : CONCRETE);
                    }
                }
                if (Math.floorMod(x - xl, 14) == 7) w.set(x, b.y0 - 1, zc, flood(Direction.DOWN));
            }
        }
    }

    // --------------------------------------------------------------- platforms

    private static void platforms(ChunkWriter w, int side) {
        IntersticeLayout L = w.layout;
        for (int c = Math.floorDiv(w.minZ - 16, 64); c <= Math.floorDiv(w.maxZ + 16, 64); c++) {
            long h = Hash.of(L.sub(503), side, c);
            if (Hash.unit(h, 0) > 0.5) continue;
            int z0 = c * 64 + 4 + (int) (Hash.unit(h, 1) * 40);
            int len = 5 + (int) (Hash.unit(h, 2) * 10);
            int prot = 4 + (int) (Hash.unit(h, 3) * 5);
            int y = IntersticeLayout.FLOOR_Y + 10 + (int) (Hash.unit(h, 4) * 110);
            if (!w.overlapsZ(z0 - 2, z0 + len + 2)) continue;
            for (int z = z0; z < z0 + len; z++) {
                for (int d = -prot; d <= 2; d++) {
                    int x = w.xAt(side, d, z);
                    w.set(x, y, z, d == -prot ? DARK : PANEL);
                    if (d == -prot) w.setIfAir(x, y + 1, z, BARS_Z);
                }
                w.set(w.xAt(side, -1, z), y - 1, z, RUST);
            }
            w.setIfAir(w.xAt(side, -1, z0 + len / 2), y + 3, z0 + len / 2, com.interstice.block.LampBlock.facing(Hash.unit(h, 5) < 0.3 ? SODIUM : LAMP, side < 0 ? Direction.EAST : Direction.WEST));
            if (y < IntersticeLayout.FLOOR_Y + 44 && Hash.unit(h, 6) < 0.7) {
                int zl = z0 + 1;
                int xp = w.xAt(side, -prot - 1, zl);
                for (int yy = IntersticeLayout.FLOOR_Y - 1; yy <= y; yy++) {
                    w.set(xp, yy, zl, DARK);
                    w.set(xp, yy, zl + 1, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.SOUTH));
                }
            }
        }
    }

    // ------------------------------------------------------------------- pipes

    private static void pipes(ChunkWriter w, int side) {
        IntersticeLayout L = w.layout;
        BlockState pipeZ = ModBlocks.RUSTED_PIPE.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z);
        BlockState pipeY = ModBlocks.RUSTED_PIPE.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        for (int c = Math.floorDiv(w.minZ, 128); c <= Math.floorDiv(w.maxZ, 128); c++) {
            long h = Hash.of(L.sub(504), side, c);
            int n = 1 + (int) (Hash.unit(h, 0) * 2);
            for (int i = 0; i < n; i++) {
                int y = IntersticeLayout.FLOOR_Y + 7 + (int) (Hash.unit(h, i, 1) * 64);
                int gap0 = c * 128 + (int) (Hash.unit(h, i, 2) * 128);
                int gapLen = Hash.unit(h, i, 3) < 0.5 ? 4 + (int) (Hash.unit(h, i, 4) * 10) : 0;
                for (int z = Math.max(w.minZ, c * 128); z <= Math.min(w.maxZ, c * 128 + 127); z++) {
                    if (z >= gap0 && z < gap0 + gapLen) continue;
                    for (int d = -2; d <= -1; d++) {
                        int x = w.xAt(side, d, z);
                        w.setIfAir(x, y, z, pipeZ);
                        w.setIfAir(x, y + 1, z, pipeZ);
                    }
                }
            }
        }
        for (int c = Math.floorDiv(w.minZ, 48); c <= Math.floorDiv(w.maxZ, 48); c++) {
            long h = Hash.of(L.sub(505), side, c);
            if (Hash.unit(h, 0) > 0.35) continue;
            int z = c * 48 + (int) (Hash.unit(h, 1) * 48);
            if (z < w.minZ || z > w.maxZ) continue;
            int top = IntersticeLayout.FLOOR_Y + 30 + (int) (Hash.unit(h, 2) * 200);
            for (int y = IntersticeLayout.FLOOR_Y; y < top; y++) {
                w.setIfAir(w.xAt(side, -1, z), y, z, pipeY);
                w.setIfAir(w.xAt(side, -2, z), y, z, pipeY);
            }
        }
    }

    // ------------------------------------------------------------------- lamps

    private static void wallLamps(ChunkWriter w, int side) {
        IntersticeLayout L = w.layout;
        for (int c = Math.floorDiv(w.minZ, 22); c <= Math.floorDiv(w.maxZ, 22); c++) {
            long h = Hash.of(L.sub(506), side, c);
            int z = c * 22 + (int) (Hash.unit(h, 0) * 22);
            if (z < w.minZ || z > w.maxZ) continue;
            // version 2: strings of lights climbing the walls and fading into the dark above, so the eye measures
            // the height (denser and higher than the legacy layout)
            boolean v2 = L.plan() != null;
            int levels = v2 ? 11 : 9;
            for (int k = 0; k < levels; k++) {
                double keep = v2 ? 0.85 - k * 0.05 : 0.45 * (1.0 - k / 10.0);
                if (k > 0 && Hash.unit(h, k, 1) > keep) continue;
                int y = IntersticeLayout.FLOOR_Y + 6 + k * (v2 ? 18 : 28) + (k > 0 ? (int) (Hash.unit(h, k, 2) * (v2 ? 6 : 10)) : 0);
                int relief = L.relief(side, y, z);
                int x = w.xAt(side, -relief - 1, z);
                double roll = Hash.unit(h, k, 3);
                BlockState lamp = roll < 0.12 ? EMERGENCY : roll < 0.32 ? SODIUM : LAMP;
                lamp = com.interstice.block.LampBlock.facing(lamp, side < 0 ? Direction.EAST : Direction.WEST);
                if (k >= 4 && roll < 0.2) {
                    // flood lamps are set into the wall, lens flush with the face
                    int wx = wallBlockX(w, side, x, y, z);
                    if (wx != Integer.MIN_VALUE) w.set(wx, y, z, flood(side < 0 ? Direction.EAST : Direction.WEST));
                    continue;
                }
                w.setIfAir(x, y, z, lamp);
            }
        }
    }

    /**
     * Version 2: flood lamps set into the floor at the very foot of the walls, aimed straight up, every 12 blocks or
     * so (some missing): their beams graze the walls and climb until the black fog takes them, so the walls read as
     * immensely tall. The lamp replaces the floor block (its lens flush with the floor), on a dark metal footing.
     */
    private static void wallUplights(ChunkWriter w, int side) {
        IntersticeLayout L = w.layout;
        if (L.plan() == null) return;
        for (int c = Math.floorDiv(w.minZ, 12); c <= Math.floorDiv(w.maxZ, 12); c++) {
            long h = Hash.of(L.sub(508), side, c);
            if (Hash.unit(h, 0) > 0.85) continue;
            int z = c * 12 + (int) (Hash.unit(h, 1) * 12);
            if (z < w.minZ || z > w.maxZ || !L.plan().inside(z)) continue;
            int fy = L.floorY(w.xAt(side, -3, z), z);
            int x = w.xAt(side, -L.relief(side, fy, z) - 1, z);   // the first floor column in front of the wall
            if (L.pitAt(x, z) != null || Settlements.lotAt(L, x, z, 1) != null) continue;
            if (w.get(x, fy - 1, z).isAir() || !w.get(x, fy, z).isAir()) continue;   // a floor here, and nothing on it
            w.set(x, fy - 2, z, DARK);
            w.set(x, fy - 1, z, flood(Direction.UP));
        }
    }

    /** The first solid block of the wall behind an air column at x (stepping outwards), or Integer.MIN_VALUE. */
    private static int wallBlockX(ChunkWriter w, int side, int x, int y, int z) {
        for (int i = 0; i <= 3; i++) {
            int xx = x + side * i;
            if (!w.get(xx, y, z).isAir()) return xx;
        }
        return Integer.MIN_VALUE;
    }

    /** Height of the high flood lamps above the corridor floor (see {@link #highFloods}). */
    public static final int HIGH_FLOOD_Y = IntersticeLayout.FLOOR_Y + 30;

    /**
     * Version 2: searchlights set into the walls 30 blocks above the floor, every 40 blocks on each side (the two
     * sides staggered), aimed straight across: they light the upper part of the opposite wall and the chains
     * hanging and stretched in between, dimly (long-throw spot lights in {@code LampLightManager}).
     */
    private static void highFloods(ChunkWriter w, int side) {
        IntersticeLayout L = w.layout;
        if (L.plan() == null) return;
        int y = HIGH_FLOOD_Y;   // ChunkWriter.setIfAir ignores anything outside the chunk
        int offset = side < 0 ? 0 : 20;
        for (int c = Math.floorDiv(w.minZ - offset, 40); c <= Math.floorDiv(w.maxZ - offset, 40); c++) {
            long h = Hash.of(L.sub(509), side, c);
            int z = c * 40 + offset + (int) (Hash.unit(h, 0) * 6) - 3;
            if (z < w.minZ || z > w.maxZ || !L.plan().inside(z)) continue;
            int relief = L.relief(side, y, z);
            // set into the wall: the searchlight replaces the wall block, its lens flush with the face
            int x = wallBlockX(w, side, w.xAt(side, -relief - 1, z), y, z);
            if (x == Integer.MIN_VALUE) continue;
            w.set(x, y, z, ModBlocks.WALL_SPOTLIGHT.defaultBlockState().setValue(DirectionalBlock.FACING, side < 0 ? Direction.EAST : Direction.WEST));
        }
    }

    private static void passageDetails(ChunkWriter w, int side) {
        IntersticeLayout L = w.layout;
        int py = TerrainPass.PASSAGE_Y;
        for (int z = w.minZ; z <= w.maxZ; z++) {
            if (Math.floorMod(z, 10) == 0 && !TerrainPass.isCollapsed(L, side, z)) {
                int x = w.xAt(side, 7.5, z);
                if (w.get(x, py + TerrainPass.PASSAGE_HEIGHT - 1, z).isAir()) {
                    w.set(x, py + TerrainPass.PASSAGE_HEIGHT - 1, z, com.interstice.block.LampBlock.facing(Hash.unit(L.sub(507), side, z) < 0.2 ? EMERGENCY : LAMP, Direction.DOWN));
                }
            }
            if (TerrainPass.isCollapsed(L, side, z)) {
                for (double d = TerrainPass.PASSAGE_D0; d < TerrainPass.PASSAGE_D1; d++) {
                    int x = w.xAt(side, d + 0.5, z);
                    int h = (int) (Hash.unit(L.sub(508), x, z) * 3);
                    for (int y = py; y <= py + h; y++) w.set(x, y, z, RUBBLE);
                }
            }
        }
        for (int c = Math.floorDiv(w.minZ, 40); c <= Math.floorDiv(w.maxZ, 40); c++) {
            int dz = TerrainPass.doorwayZ(L, side, c);
            if (dz == Integer.MIN_VALUE) continue;
            // red fixture above each maintenance doorway
            int relief = L.relief(side, py + 3, dz);
            w.setIfAir(w.xAt(side, -relief - 1, dz), py + 3, dz, com.interstice.block.LampBlock.facing(EMERGENCY, side < 0 ? Direction.EAST : Direction.WEST));
        }
    }

    private static void vents(ChunkWriter w, int side) {
        IntersticeLayout L = w.layout;
        for (int c = Math.floorDiv(w.minZ, 30); c <= Math.floorDiv(w.maxZ, 30); c++) {
            long h = Hash.of(L.sub(509), side, c);
            for (int k = 0; k < 6; k++) {
                if (Hash.unit(h, k, 0) > 0.2) continue;
                int z0 = c * 30 + (int) (Hash.unit(h, k, 1) * 26);
                int y0 = IntersticeLayout.FLOOR_Y + 22 + k * 36 + (int) (Hash.unit(h, k, 2) * 20);
                if (!w.overlapsZ(z0, z0 + 2)) continue;
                for (int z = z0; z < z0 + 3; z++)
                    for (int y = y0; y < y0 + 3; y++)
                        for (int d = 0; d < 5; d++) w.set(w.xAt(side, d + 0.5, z), y, z, AIR);
                for (int z = z0; z < z0 + 3; z++) w.set(w.xAt(side, 0.5, z), y0 - 1, z, DARK);
            }
        }
    }

    // ----------------------------------------------------------------- bunkers

    public static final int BUNKER_CELL = 52;

    public record Bunker(int x0, int z0, int sx, int sz, Direction door, boolean slits) {
        public boolean contains(int x, int z) {
            return x >= x0 && x < x0 + sx && z >= z0 && z < z0 + sz;
        }
    }

    public static Bunker bunker(IntersticeLayout L, int cx, int cz) {
        long h = Hash.of(L.sub(510), cx, cz);
        if (Hash.unit(h, 0) > 0.33) return null;
        int sx = 7 + (int) (Hash.unit(h, 1) * 4), sz = 7 + (int) (Hash.unit(h, 2) * 4);
        int x0 = cx * BUNKER_CELL - 26 + 4 + (int) (Hash.unit(h, 3) * (BUNKER_CELL - sx - 8));
        int z0 = cz * BUNKER_CELL - 26 + 4 + (int) (Hash.unit(h, 4) * (BUNKER_CELL - sz - 8));
        if (L.nearLandmark(z0, 90)) return null;
        for (int i = 0; i < 5; i++) {
            int px = i == 4 ? x0 + sx / 2 : (i & 1) == 0 ? x0 : x0 + sx - 1;
            int pz = i == 4 ? z0 + sz / 2 : (i & 2) == 0 ? z0 : z0 + sz - 1;
            if (L.wallDepth(px + 0.5, pz + 0.5) > -7) return null;
            if (L.pitAt(px, pz) != null) return null;
        }
        Direction[] dirs = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};
        return new Bunker(x0, z0, sx, sz, dirs[(int) (Hash.unit(h, 5) * 4)], Hash.unit(h, 6) < 0.35);
    }

    public static Bunker bunkerAt(IntersticeLayout L, int x, int z) {
        Bunker b = bunker(L, Math.floorDiv(x + 26, BUNKER_CELL), Math.floorDiv(z + 26, BUNKER_CELL));
        return b != null && b.contains(x, z) ? b : null;
    }

    private static void bunkers(ChunkWriter w) {
        IntersticeLayout L = w.layout;
        for (int cx = Math.floorDiv(w.minX + 26 - 12, BUNKER_CELL); cx <= Math.floorDiv(w.maxX + 26 + 12, BUNKER_CELL); cx++)
            for (int cz = Math.floorDiv(w.minZ + 26 - 12, BUNKER_CELL); cz <= Math.floorDiv(w.maxZ + 26 + 12, BUNKER_CELL); cz++) {
                Bunker b = bunker(L, cx, cz);
                if (b == null || !w.overlapsX(b.x0 - 1, b.x0 + b.sx) || !w.overlapsZ(b.z0 - 1, b.z0 + b.sz)) continue;
                placeBunker(w, b.x0, b.z0, b.sx, b.sz, IntersticeLayout.FLOOR_Y, b.door, b.slits);
            }
    }

    /** Low concrete shelter. Also reused by landmarks and anomalies. */
    public static void placeBunker(ChunkWriter w, int x0, int z0, int sx, int sz, int fy, Direction door, boolean slits) {
        int x1 = x0 + sx - 1, z1 = z0 + sz - 1;
        int roof = fy + 3;
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++) {
                boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
                w.set(x, fy - 1, z, FLOOR);
                w.set(x, fy - 2, z, CONCRETE);
                for (int y = fy; y < roof; y++) w.set(x, y, z, edge ? PANEL : AIR);
                w.set(x, roof, z, CONCRETE);
                if (edge && (x == x0 || x == x1) && (z == z0 || z == z1)) w.set(x, roof + 1, z, DARK);
            }
        int mx = (x0 + x1) / 2, mz = (z0 + z1) / 2;
        switch (door) {
            case NORTH -> { for (int y = fy; y < fy + 2; y++) { w.set(mx, y, z0, AIR); w.set(mx + 1, y, z0, AIR); } }
            case SOUTH -> { for (int y = fy; y < fy + 2; y++) { w.set(mx, y, z1, AIR); w.set(mx + 1, y, z1, AIR); } }
            case WEST -> { for (int y = fy; y < fy + 2; y++) { w.set(x0, y, mz, AIR); w.set(x0, y, mz + 1, AIR); } }
            default -> { for (int y = fy; y < fy + 2; y++) { w.set(x1, y, mz, AIR); w.set(x1, y, mz + 1, AIR); } }
        }
        if (slits) {
            for (int x = x0 + 2; x <= x1 - 2; x++) {
                if (door != Direction.NORTH) w.set(x, fy + 1, z0, BARS_X);
                if (door != Direction.SOUTH) w.set(x, fy + 1, z1, BARS_X);
            }
        }
        w.set(mx, roof - 1, mz, com.interstice.block.LampBlock.facing(EMERGENCY, Direction.DOWN));
    }
}
