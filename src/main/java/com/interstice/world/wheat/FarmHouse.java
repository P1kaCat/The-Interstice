package com.interstice.world.wheat;

import com.interstice.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * The farmhouse at the end of Level 02's track: a closed two-storey house of white plaster on a concrete footing,
 * log corner posts, a wooden band between the floors, dark windows, a wooden gable roof (ridge along x), a brick
 * chimney and a small porch. Its front door cannot be opened (an iron-type door with the pale door's look); the rift
 * stands in front of it ({@link WheatLayout#RIFT_Z}). Written cell by cell, only the cells inside the chunk.
 */
public final class FarmHouse {
    private FarmHouse() {}

    private static void set(ChunkAccess c, int x0, int z0, BlockPos.MutableBlockPos p, int x, int y, int z, BlockState s) {
        if (x < x0 || x >= x0 + 16 || z < z0 || z >= z0 + 16) return;
        c.setBlockState(p.set(x, y, z), s, false);
    }

    public static void build(ChunkAccess c, int x0, int z0) {
        int hz = WheatLayout.HOUSE_Z, hh = WheatLayout.HOUSE_HALF, depth = WheatLayout.HOUSE_DEPTH, wall = WheatLayout.HOUSE_WALL;
        if (x0 > hh + 2 || x0 + 15 < -hh - 2 || z0 > hz + depth + 2 || z0 + 15 < hz - 4) return;
        int gy = WheatLayout.GROUND_Y;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockState plaster = ModBlocks.PLASTER_WALL.defaultBlockState(), boards = ModBlocks.FLOORBOARDS.defaultBlockState();
        BlockState log = ModBlocks.ROTTEN_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        BlockState footing = ModBlocks.WEATHERED_CONCRETE.defaultBlockState(), brick = ModBlocks.GRIMY_BRICK.defaultBlockState();
        BlockState air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        int xa = -hh, xb = hh, za = hz, zb = hz + depth - 1;
        // footing and floor
        for (int x = xa - 1; x <= xb + 1; x++) for (int z = za - 1; z <= zb + 1; z++) set(c, x0, z0, p, x, gy - 1, z, footing);
        // walls (hollow, closed)
        for (int x = xa; x <= xb; x++)
            for (int z = za; z <= zb; z++) {
                boolean edgeX = x == xa || x == xb, edgeZ = z == za || z == zb;
                for (int y = gy; y < gy + wall; y++) {
                    BlockState s;
                    if (edgeX && edgeZ) s = log;
                    else if (edgeX || edgeZ) s = y == gy + 4 ? boards : plaster;
                    else s = air;
                    set(c, x0, z0, p, x, y, z, s);
                }
                set(c, x0, z0, p, x, gy + wall, z, boards);   // ceiling of the upper floor (under the roof)
            }
        // windows: dark panes in the long walls and the side walls
        BlockState paneX = ModBlocks.BLACKOUT_PANE.defaultBlockState().setValue(IronBarsBlock.EAST, true).setValue(IronBarsBlock.WEST, true);
        BlockState paneZ = ModBlocks.BLACKOUT_PANE.defaultBlockState().setValue(IronBarsBlock.NORTH, true).setValue(IronBarsBlock.SOUTH, true);
        for (int wx : new int[]{-3, 3})
            for (int y : new int[]{gy + 1, gy + 2, gy + 5, gy + 6}) {
                set(c, x0, z0, p, wx, y, za, paneX);
                set(c, x0, z0, p, wx, y, zb, paneX);
            }
        for (int wz : new int[]{za + 3, za + 7})
            for (int y : new int[]{gy + 1, gy + 2, gy + 5, gy + 6}) {
                set(c, x0, z0, p, xa, y, wz, paneZ);
                set(c, x0, z0, p, xb, y, wz, paneZ);
            }
        // the door that does not open
        BlockState door = ModBlocks.FAKE_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH)
                .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT).setValue(DoorBlock.OPEN, false);
        set(c, x0, z0, p, 0, gy, za, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        set(c, x0, z0, p, 0, gy + 1, za, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        // gable roof, ridge along x, eaves overhanging by one
        BlockState up = ModBlocks.WORN_BENCH.defaultBlockState().setValue(StairBlock.FACING, Direction.SOUTH);
        BlockState down = ModBlocks.WORN_BENCH.defaultBlockState().setValue(StairBlock.FACING, Direction.NORTH);
        int half = (depth + 1) / 2;
        for (int k = 0; k <= half; k++) {
            int y = gy + wall + k;
            int zf = za - 1 + k, zr = zb + 1 - k;
            for (int x = xa - 1; x <= xb + 1; x++) {
                if (zf < zr) {
                    set(c, x0, z0, p, x, y, zf, up);
                    set(c, x0, z0, p, x, y, zr, down);
                } else if (zf == zr) {
                    set(c, x0, z0, p, x, y, zf, boards);   // the ridge
                }
            }
            // the gables: plaster triangles closing the roof's ends
            for (int z = zf + 1; z < zr; z++) {
                set(c, x0, z0, p, xa, y, z, plaster);
                set(c, x0, z0, p, xb, y, z, plaster);
            }
        }
        // the chimney, at the back right, through the roof
        for (int y = gy + wall; y < gy + wall + half + 3; y++) set(c, x0, z0, p, 3, y, zb - 2, brick);
        // the porch: two posts and a small canopy over the door
        BlockState post = ModBlocks.WORN_FENCE.defaultBlockState();
        for (int px : new int[]{-2, 2}) for (int y = gy; y < gy + 3; y++) set(c, x0, z0, p, px, y, za - 2, post);
        for (int x = -2; x <= 2; x++) for (int z = za - 2; z < za; z++) set(c, x0, z0, p, x, gy + 3, z, boards);
    }
}
