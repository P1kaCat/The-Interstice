package com.interstice.world.threshold;

import net.minecraft.util.Mth;

/**
 * Level 00, The Threshold: fixed coordinates, the same in every world (it is meant to be one exact place).
 * <p>
 * A straight corridor with a square 5 x 5 section runs along +Z from {@link #START_Z} to {@link #END_Z}. Its blocks
 * are invisible (barriers: they only give the collision); what the player sees is drawn by the client
 * ({@code ThresholdRenderer}): the same corridor, twisted about its axis through 180 degrees over its length,
 * <b>relative to where the player stands</b> ({@link #relativeTwist}). The slice under the player's feet is always
 * upright, so they walk on a flat floor; ahead, the corridor turns over; looking back, the entrance is upside down.
 * Because the section is square and the twist is about its centre, the rotated corridor never crosses the space
 * around the player. The corridor is plain and straight for 32 blocks before it starts to turn; its far end is a
 * concrete wall split by a white tear, the rift ({@link #RIFT_Z}).
 */
public final class ThresholdLayout {
    private ThresholdLayout() {}

    /** The walking surface (the floor blocks are one lower). */
    public static final int FLOOR_Y = 64;
    /** The inside spans x -2..2 (5 block columns) and y FLOOR_Y .. FLOOR_Y + 4. */
    public static final int HALF_WIDTH = 2, HEIGHT = 5;
    public static final int START_Z = -4;
    /** 32 blocks of plain straight corridor, then the twist over 30 blocks, then 8 straight blocks to the end wall. */
    public static final int TWIST_START = START_Z + 32, TWIST_END = TWIST_START + 30;
    public static final int END_Z = TWIST_END + 8;
    /** The end wall: the rift is a tear in it, at the corridor's centre column (x 0). */
    public static final int RIFT_Z = END_Z + 1;
    /** The tear: centre height and half height (world), half width at its widest. */
    public static final double TEAR_Y = FLOOR_Y + 2.05, TEAR_HALF_HEIGHT = 2.0, TEAR_HALF_WIDTH = 0.2;

    /** The corridor's axis (world coordinates) and half size (its section is square). */
    public static final double AXIS_X = 0.5, AXIS_Y = FLOOR_Y + HEIGHT / 2.0, HALF_SIZE = HEIGHT / 2.0;

    /** Where a player arrives: the start of the corridor, facing along it. */
    public static final double SPAWN_X = 0.5, SPAWN_Y = FLOOR_Y, SPAWN_Z = START_Z + 1.5;

    /** The corridor's total twist (radians) at this z: 0 before TWIST_START, pi after TWIST_END, smooth in between. */
    public static double twist(double z) {
        double t = Mth.clamp((z - TWIST_START) / (double) (TWIST_END - TWIST_START), 0.0, 1.0);
        return Math.PI * t * t * (3 - 2 * t);
    }

    /** The rotation at z as seen by a player standing at zPlayer: their own slice is always upright. */
    public static double relativeTwist(double z, double zPlayer) {
        return twist(z) - twist(Mth.clamp(zPlayer, START_Z, RIFT_Z));
    }

    /** World x of corridor-local (u, v) at z, as drawn for a viewer at zViewer (the twisted corridor). */
    public static double worldX(double u, double v, double z, double zViewer) {
        double t = relativeTwist(z, zViewer);
        return AXIS_X + u * Math.cos(t) - v * Math.sin(t);
    }

    /** World y of corridor-local (u, v) at z, as drawn for a viewer at zViewer. */
    public static double worldY(double u, double v, double z, double zViewer) {
        double t = relativeTwist(z, zViewer);
        return AXIS_Y + u * Math.sin(t) + v * Math.cos(t);
    }

    /** Is (x, y, z) inside the tear (the column x 0..1 of the end wall)? */
    public static boolean inRift(double x, double y, double z) {
        return z >= RIFT_Z - 0.3 && x >= -0.2 && x <= 1.2 && y >= FLOOR_Y - 0.5 && y <= FLOOR_Y + HEIGHT;
    }
}
