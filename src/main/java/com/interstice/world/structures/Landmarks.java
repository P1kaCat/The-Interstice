package com.interstice.world.structures;

import com.interstice.registry.ModBlocks;
import com.interstice.util.Hash;
import com.interstice.world.IntersticeLayout;
import com.interstice.world.generation.ChunkWriter;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

import static com.interstice.world.structures.StructurePass.*;

/**
 * Rare monumental landmarks (one per ~1100 blocks) that give the player
 * something recognisable to navigate by.
 */
public final class Landmarks {
    private Landmarks() {}

    public static void run(ChunkWriter w) {
        IntersticeLayout L = w.layout;
        for (int c = Math.floorDiv(w.minZ - 80, IntersticeLayout.LANDMARK_CELL); c <= Math.floorDiv(w.maxZ + 80, IntersticeLayout.LANDMARK_CELL); c++) {
            IntersticeLayout.Landmark lm = L.landmark(c);
            if (!w.overlapsZ(lm.z() - 70, lm.z() + 70)) continue;
            switch (lm.type()) {
                case GATE -> gate(w, lm);
                case PYLON -> pylon(w, lm);
                case ANCHOR -> anchor(w, lm);
                case FALLEN_SLAB -> fallenSlab(w, lm);
                case SILENT_HALL -> silentHall(w, lm);
            }
        }
    }

    private static void gate(ChunkWriter w, IntersticeLayout.Landmark lm) {
        int zc = lm.z();
        int top = IntersticeLayout.FLOOR_Y + 176;
        for (int side = -1; side <= 1; side += 2) {
            for (int z = zc - 7; z <= zc + 7; z++) {
                for (int d = -14; d <= 1; d++) {
                    int x = w.xAt(side, d, z);
                    for (int y = IntersticeLayout.FLOOR_Y - 1; y < top; y++) {
                        BlockState s = Math.floorMod(y, 12) == 0 ? DARK : (d == -14 || z == zc - 7 || z == zc + 7) ? PANEL : CONCRETE;
                        w.set(x, y, z, s);
                    }
                }
                if (Math.floorMod(z - zc, 4) == 0) w.setIfAir(w.xAt(side, -15, z), IntersticeLayout.FLOOR_Y + 9, z, EMERGENCY);
            }
            w.setIfAir(w.xAt(side, -15, zc), IntersticeLayout.FLOOR_Y + 60, zc, flood(side < 0 ? Direction.EAST : Direction.WEST));
        }
        int xl = w.xAt(-1, -1, zc), xr = w.xAt(1, -1, zc);
        for (int x = xl; x <= xr; x++) {
            for (int z = zc - 7; z <= zc + 7; z++)
                for (int y = top - 36; y < top; y++) w.set(x, y, z, y == top - 36 ? RUST : CONCRETE);
            if (Math.floorMod(x, 8) == 0) w.setIfAir(x, top - 37, zc, EMERGENCY);
        }
    }

    private static void pylon(ChunkWriter w, IntersticeLayout.Landmark lm) {
        int zc = lm.z();
        int xc = (int) Math.floor(w.layout.centerX(zc));
        int top = IntersticeLayout.FLOOR_Y + 170;
        for (int x = xc - 4; x <= xc + 3; x++)
            for (int z = zc - 4; z <= zc + 3; z++)
                for (int y = IntersticeLayout.FLOOR_Y - 1; y < top; y++)
                    w.set(x, y, z, Math.floorMod(y, 10) == 0 ? RUST : DARK);
        for (int y = IntersticeLayout.FLOOR_Y + 12; y < top; y += 24) {
            w.setIfAir(xc - 5, y, zc, EMERGENCY);
            w.setIfAir(xc + 4, y, zc, EMERGENCY);
            w.setIfAir(xc, y, zc - 5, EMERGENCY);
            w.setIfAir(xc, y, zc + 4, EMERGENCY);
        }
        w.set(xc, top, zc, flood(Direction.UP));
    }

    private static void anchor(ChunkWriter w, IntersticeLayout.Landmark lm) {
        int zc = lm.z();
        int xc = (int) Math.floor(w.layout.centerX(zc));
        for (int x = xc - 6; x <= xc + 5; x++)
            for (int z = zc - 6; z <= zc + 5; z++) {
                boolean ring = x == xc - 6 || x == xc + 5 || z == zc - 6 || z == zc + 5;
                for (int y = IntersticeLayout.FLOOR_Y - 1; y < IntersticeLayout.FLOOR_Y + 7; y++)
                    w.set(x, y, z, ring ? RUST : DARK);
            }
        for (int x = xc - 1; x <= xc; x++)
            for (int z = zc - 1; z <= zc; z++)
                w.set(x, IntersticeLayout.FLOOR_Y + 7, z, RUST);
        w.setIfAir(xc - 8, IntersticeLayout.FLOOR_Y, zc, flood(Direction.UP));
        w.setIfAir(xc + 7, IntersticeLayout.FLOOR_Y, zc, flood(Direction.UP));
    }

    /** Anchor-landmark chain origin, shared with the client chain field. */
    public static double[] anchorTop(IntersticeLayout L, IntersticeLayout.Landmark lm) {
        return new double[]{Math.floor(L.centerX(lm.z())), IntersticeLayout.FLOOR_Y + 8, lm.z()};
    }

    private static void fallenSlab(ChunkWriter w, IntersticeLayout.Landmark lm) {
        int zc = lm.z();
        int side = Hash.unit(lm.hash(), 7) < 0.5 ? -1 : 1;
        for (int z = zc - 60; z <= zc + 60; z++) {
            if (z < w.minZ || z > w.maxZ) continue;
            double t = (z - (zc - 60)) / 120.0;
            int ys = IntersticeLayout.FLOOR_Y + (int) Math.round(t * 92);
            double cx = w.layout.centerX(z + 0.5);
            int xa = (int) Math.floor(cx), xb = w.xAt(side, 1, z);
            for (int x = Math.min(xa, xb); x <= Math.max(xa, xb); x++) {
                for (int y = ys - 3; y <= ys; y++) w.set(x, y, z, y == ys ? PANEL : CONCRETE);
                if (Hash.unit(lm.hash(), x, z) < 0.04) w.setIfAir(x, ys + 1, z, RUBBLE);
            }
            if (Math.floorMod(z, 16) == 0) w.setIfAir(xa, ys - 4, z, EMERGENCY);
        }
    }

    private static void silentHall(ChunkWriter w, IntersticeLayout.Landmark lm) {
        int zc = lm.z();
        int xc = (int) Math.floor(w.layout.centerX(zc));
        // seven identical shelters in a row: the repetition is the point
        for (int i = -3; i <= 3; i++) {
            int z0 = zc + i * 14 - 4;
            if (!w.overlapsZ(z0 - 1, z0 + 9)) continue;
            StructurePass.placeBunker(w, xc - 4, z0, 8, 8, IntersticeLayout.FLOOR_Y, Direction.WEST, true);
        }
    }
}
