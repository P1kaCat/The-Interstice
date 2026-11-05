package com.interstice.world.generation;

import com.interstice.block.LampBlock;
import com.interstice.registry.ModBlocks;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Last generation pass: every light fixture must hang from, stand on or be fixed to something. A fixture whose
 * mount side is empty (inside this chunk) is turned towards a support it has (ceiling first, then a wall, then the
 * floor); one with no support at all is hung on a chain from the nearest solid block above (up to 14 blocks) or,
 * failing that, set on a dark metal post down to the ground. Mount sides in a neighbouring chunk are trusted (the
 * generator places fixtures against walls, ceilings and posts it built itself).
 */
public final class LampMounts {
    private LampMounts() {}

    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

    public static void run(ChunkWriter w) {
        var chunk = w.chunk;
        for (int si = 0; si < chunk.getSectionsCount(); si++) {
            LevelChunkSection sec = chunk.getSection(si);
            if (sec.hasOnlyAir() || !sec.maybeHas(s -> s.getBlock() instanceof LampBlock)) continue;
            int y0 = chunk.getSectionYFromSectionIndex(si) << 4;
            for (int ly = 0; ly < 16; ly++)
                for (int lz = 0; lz < 16; lz++)
                    for (int lx = 0; lx < 16; lx++) {
                        BlockState s = sec.getBlockState(lx, ly, lz);
                        if (s.getBlock() instanceof LampBlock) fix(w, w.minX + lx, y0 + ly, w.minZ + lz, s);
                    }
        }
    }

    private static boolean solid(ChunkWriter w, int x, int y, int z) {
        if (!w.inside(x, y, z)) return true;   // trust the neighbouring chunk
        BlockState s = w.get(x, y, z);
        return !s.isAir() && !(s.getBlock() instanceof LampBlock) && s.isSolid();
    }

    private static void fix(ChunkWriter w, int x, int y, int z, BlockState s) {
        Direction f = s.getValue(LampBlock.FACING);
        Direction mount = f.getOpposite();
        if (solid(w, x + mount.getStepX(), y + mount.getStepY(), z + mount.getStepZ())) return;
        Direction nf = null;
        if (solid(w, x, y + 1, z)) nf = Direction.DOWN;
        else {
            for (Direction d : HORIZONTAL) if (solid(w, x + d.getStepX(), y, z + d.getStepZ())) { nf = d.getOpposite(); break; }
            if (nf == null && solid(w, x, y - 1, z)) nf = Direction.UP;
        }
        if (nf != null) {
            w.set(x, y, z, s.setValue(LampBlock.FACING, nf));
            return;
        }
        // nothing around: hang it on a chain from above, or stand it on a post
        for (int k = 1; k <= 14; k++) {
            if (!w.inside(x, y + k, z)) break;
            if (solid(w, x, y + k, z)) {
                for (int j = 1; j < k; j++) w.set(x, y + j, z, Blocks.CHAIN.defaultBlockState().setValue(ChainBlock.AXIS, Direction.Axis.Y));
                w.set(x, y, z, s.setValue(LampBlock.FACING, Direction.DOWN));
                return;
            }
        }
        int k = 1;
        while (k < 12 && w.inside(x, y - k, z) && !solid(w, x, y - k, z)) {
            w.set(x, y - k, z, ModBlocks.DARK_METAL.defaultBlockState());
            k++;
        }
        w.set(x, y, z, s.setValue(LampBlock.FACING, Direction.UP));
    }
}
