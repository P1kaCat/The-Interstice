package com.interstice.entity.navigation;

import com.interstice.world.IntersticeLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Constrained wall locomotion.
 * <p>
 * A wall creature lives on a 2D surface parameterised by (y, z) on one side of
 * the corridor. Each tick it moves in that plane; its X coordinate is then
 * re-solved against the real blocks by casting a short horizontal probe from
 * the corridor towards the wall. When the surface jumps (a buttress, a recessed
 * panel, a pipe), X is eased towards the new surface so the creature visibly
 * clambers over the obstacle instead of teleporting.
 * <p>
 * This is a deliberate, reliable simplification of arbitrary surface
 * navigation: creatures can never walk onto ceilings or wrap around corners
 * outside the two main walls.
 */
public final class WallMovement {
    private WallMovement() {}

    /**
     * @return X at which the creature's centre should sit to cling to the wall,
     * or NaN when no wall surface was found within reach.
     */
    public static double surfaceX(Level level, IntersticeLayout layout, int side, double y, double z, double halfWidth) {
        double face = layout.faceX(side, z);
        int start = (int) Math.floor(face - side * 9.5);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int by = (int) Math.floor(y + 0.6);
        int bz = (int) Math.floor(z);
        for (int i = 0; i < 26; i++) {
            int x = start + side * i;
            p.set(x, by, bz);
            if (!level.isLoaded(p)) return Double.NaN;
            if (!level.getBlockState(p).getCollisionShape(level, p).isEmpty()) {
                double edge = side < 0 ? x + 1.0 : x;
                return edge - side * (halfWidth + 0.03);
            }
        }
        return Double.NaN;
    }

    /** Eases the current X towards the solved surface. */
    public static double approach(double currentX, double targetX) {
        if (Double.isNaN(targetX)) return currentX;
        double delta = targetX - currentX;
        double step = Math.abs(delta) > 2.0 ? 0.45 : Math.abs(delta) * 0.6 + 0.02;
        if (Math.abs(delta) <= step) return targetX;
        return currentX + Math.signum(delta) * step;
    }

    public static double clampY(double y) {
        return Math.max(IntersticeLayout.FLOOR_Y + 1.5, Math.min(IntersticeLayout.WALL_TOP - 3, y));
    }
}
