package com.interstice.client.render.threshold;

import com.interstice.Interstice;
import com.interstice.client.ClientState;
import com.interstice.world.threshold.ThresholdLayout;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Level 00, The Threshold: the whole corridor (floor, walls, ceiling, lamps, frames), drawn twisted about its axis
 * <b>relative to the viewer</b>: each slice is turned by {@link ThresholdLayout#relativeTwist}, so the slice the
 * player stands in is upright (they walk on a flat floor, on the invisible collision shell) while the corridor
 * ahead turns over through up to 180 degrees and, looking back, the entrance hangs upside down. Walking on, the
 * floor straightens under the feet as it goes: the player seems to walk up the wall onto the ceiling.
 * <p>
 * Half-block slices and one-block cells keep the bend smooth. Light is baked per vertex in the corridor's own frame
 * (the ceiling lamps), so it turns with the architecture. The far end is the white rift: an opaque full-bright face,
 * with white light spilling additively onto the last metres of walls, floor and ceiling.
 */
public final class ThresholdRenderer {
    private static final ResourceLocation WALL = Interstice.id("textures/block/threshold_wall.png");
    private static final ResourceLocation FLOOR = Interstice.id("textures/block/threshold_floor.png");
    private static final ResourceLocation LAMP = Interstice.id("textures/block/threshold_light.png");
    private static final double Z0 = ThresholdLayout.START_Z, Z1 = ThresholdLayout.RIFT_Z;
    /** Texture repeat: one per 4 blocks, continuous along and across (no seam between slices). */
    private static final double TEX = 4.0;
    private static final double A = ThresholdLayout.HALF_SIZE;
    private static final float[][] UV = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};

    private static double viewerZ;

    private ThresholdRenderer() {}

    // ------------------------------------------------------------------ the corridor's frame

    private static double rot(double z) {
        return ThresholdLayout.relativeTwist(z, viewerZ);
    }

    private static double wx(double u, double v, double z) {
        double t = rot(z);
        return ThresholdLayout.AXIS_X + u * Math.cos(t) - v * Math.sin(t);
    }

    private static double wy(double u, double v, double z) {
        double t = rot(z);
        return ThresholdLayout.AXIS_Y + u * Math.sin(t) + v * Math.cos(t);
    }

    /** Baked block light (0..15) at corridor-local (u, v, z): the ceiling light slot, and the tear's glow at the end. */
    private static int light(double u, double v, double z) {
        double b = 0.6 + 11.0 * Math.exp(-(u * u * 0.05 + (v - A) * (v - A) * 0.03));   // down to the floor (about 5 at the floor)
        double dz = Z1 - z, tv = v - (ThresholdLayout.TEAR_Y - ThresholdLayout.AXIS_Y);
        b += 9.0 * Math.exp(-dz * dz / 30.0) * Math.exp(-(u * u + tv * tv) / 7.0);   // a glow, not a glare: the tear must stand out
        return (int) Math.max(0, Math.min(15, Math.round(b)));
    }

    private static void vertex(VertexConsumer vc, Vec3 cam, double u, double v, double z, float tu, float tv, float nx, float ny, float nz, int packed) {
        int lt = packed != Integer.MIN_VALUE ? packed : LightTexture.pack(light(u, v, z), 0);
        vc.addVertex((float) (wx(u, v, z) - cam.x), (float) (wy(u, v, z) - cam.y), (float) (z - cam.z)).setColor(255, 255, 255, 255).setUv(tu, tv)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(lt).setNormal(nx, ny, nz);
    }

    /** A quad in corridor-local coordinates, made to face the local normal (nu, nv, nz) whatever the corner order. */
    private static void quad(VertexConsumer vc, Vec3 cam, double[][] p, float[][] uv, double nu, double nv, double nzl, int packed) {
        double zm = (p[0][2] + p[2][2]) / 2, t = rot(zm);
        float nx = (float) (nu * Math.cos(t) - nv * Math.sin(t)), ny = (float) (nu * Math.sin(t) + nv * Math.cos(t)), nz = (float) nzl;
        double ax = wx(p[1][0], p[1][1], p[1][2]) - wx(p[0][0], p[0][1], p[0][2]), ay = wy(p[1][0], p[1][1], p[1][2]) - wy(p[0][0], p[0][1], p[0][2]);
        double az = p[1][2] - p[0][2];
        double bx = wx(p[3][0], p[3][1], p[3][2]) - wx(p[0][0], p[0][1], p[0][2]), by = wy(p[3][0], p[3][1], p[3][2]) - wy(p[0][0], p[0][1], p[0][2]);
        double bz = p[3][2] - p[0][2];
        double cx = ay * bz - az * by, cy = az * bx - ax * bz, cz = ax * by - ay * bx;
        int[] order = cx * nx + cy * ny + cz * nz >= 0 ? new int[]{0, 1, 2, 3} : new int[]{0, 3, 2, 1};
        for (int i : order) vertex(vc, cam, p[i][0], p[i][1], p[i][2], uv[i][0], uv[i][1], nx, ny, nz, packed);
    }

    // ------------------------------------------------------------------ drawing

    public static void render(Camera camera, MultiBufferSource.BufferSource buffers) {
        if (!ClientState.inThreshold) return;
        Vec3 cam = camera.getPosition();
        viewerZ = cam.z;

        // walls and ceiling: smooth concrete; the two end walls
        VertexConsumer wall = buffers.getBuffer(RenderType.entitySolid(WALL));
        face(wall, cam, A, -A, A, A, -1, 0);    // right wall (u = +A), facing -u
        face(wall, cam, -A, A, -A, -A, 1, 0);   // left wall
        face(wall, cam, A, A, -A, A, 0, -1);    // ceiling (v = +A), facing down
        endWall(wall, cam, Z0, 1);
        endWall(wall, cam, Z1, -1);
        buffers.endBatch(RenderType.entitySolid(WALL));

        VertexConsumer floor = buffers.getBuffer(RenderType.entitySolid(FLOOR));
        face(floor, cam, -A, -A, A, -A, 0, 1);  // floor (v = -A), facing up
        buffers.endBatch(RenderType.entitySolid(FLOOR));

        // one continuous light slot down the middle of the ceiling: it coils into a spiral where the corridor turns
        VertexConsumer slot = buffers.getBuffer(RenderType.entitySolid(LAMP));
        double v = A - 0.012;
        // thin slices where it turns (a long twisted quad shows its gradient as chevrons), split in two across
        for (double z = Z0; z < Z1 - 1e-6; ) {
            boolean turning = z > ThresholdLayout.TWIST_START - 1 && z < ThresholdLayout.TWIST_END + 1;
            double z2 = Math.min(Z1, z + (turning ? 0.1 : 0.5));
            float ta = (float) (z / TEX), tb = (float) (z2 / TEX);
            quad(slot, cam, new double[][]{{-0.22, v, z}, {0, v, z}, {0, v, z2}, {-0.22, v, z2}}, new float[][]{{0, ta}, {0.5f, ta}, {0.5f, tb}, {0, tb}}, 0, -1, 0, LightTexture.FULL_BRIGHT);
            quad(slot, cam, new double[][]{{0, v, z}, {0.22, v, z}, {0.22, v, z2}, {0, v, z2}}, new float[][]{{0.5f, ta}, {1, ta}, {1, tb}, {0.5f, tb}}, 0, -1, 0, LightTexture.FULL_BRIGHT);
            z = z2;
        }
        buffers.endBatch(RenderType.entitySolid(LAMP));

        tear(buffers, cam);
    }

    /** One face of the corridor from edge (u0, v0) to (u1, v1): one-block cells across, half-block slices along. */
    private static void face(VertexConsumer vc, Vec3 cam, double u0, double v0, double u1, double v1, double nu, double nv) {
        int cells = (int) Math.round(2 * A);
        for (double z = Z0; z < Z1 - 1e-6; z += 0.5) {
            double z2 = Math.min(Z1, z + 0.5);
            float ta = (float) (z / TEX), tb = (float) (z2 / TEX);
            for (int j = 0; j < cells; j++) {
                double s0 = j / (double) cells, s1 = (j + 1) / (double) cells;
                double ua = u0 + (u1 - u0) * s0, va = v0 + (v1 - v0) * s0, ub = u0 + (u1 - u0) * s1, vb = v0 + (v1 - v0) * s1;
                float pa = (float) (s0 * 2 * A / TEX), pb = (float) (s1 * 2 * A / TEX);
                float[][] uv = {{pa, ta}, {pb, ta}, {pb, tb}, {pa, tb}};
                quad(vc, cam, new double[][]{{ua, va, z}, {ub, vb, z}, {ub, vb, z2}, {ua, va, z2}}, uv, nu, nv, 0, Integer.MIN_VALUE);
            }
        }
    }

    /** An end wall at z (the back one, or the one with the tear), one-block cells, continuous texture. */
    private static void endWall(VertexConsumer vc, Vec3 cam, double z, int facing) {
        for (double u = -A; u < A - 1e-6; u += 1)
            for (double v = -A; v < A - 1e-6; v += 1) {
                float pa = (float) ((u + A) / TEX), pb = (float) ((u + 1 + A) / TEX), qa = (float) ((A - v) / TEX), qb = (float) ((A - v - 1) / TEX);
                float[][] uv = {{pa, qa}, {pb, qa}, {pb, qb}, {pa, qb}};
                quad(vc, cam, new double[][]{{u, v, z}, {u + 1, v, z}, {u + 1, v + 1, z}, {u, v + 1, z}}, uv, 0, 0, facing, Integer.MIN_VALUE);
            }
    }

    // ------------------------------------------------------------------ the white rift: a tear in the end wall

    /** Deterministic jagged profile in [-1, 1] along the tear (a different one for each lip), barely shimmering. */
    private static double jag(double s, int lip, double t) {
        return 0.55 * Math.sin(s * 9.3 + lip * 2.1) + 0.3 * Math.sin(s * 23.7 + lip * 5.3) + 0.15 * Math.sin(s * 51.0 + lip)
                + 0.06 * Math.sin(t * 2.3 + s * 7 + lip * 3);
    }

    /** A point of one lip (-1 left, +1 right) at s in [-1, 1] (bottom to top), pushed out by g; corridor-local. */
    private static double[] lip(double s, int lip, double t, double g) {
        double k = Math.max(0, 1 - Math.abs(s));
        // a thin slit, widest a little below the middle, tapering to sharp points
        double taper = Math.pow(k, 1.25) * (1 + 0.5 * k);
        double w = ThresholdLayout.TEAR_HALF_WIDTH * taper * (1 + 0.35 * jag(s, lip, t)) + g * Math.pow(k, 0.9);
        double meander = 0.11 * Math.sin(s * 4.7) + 0.04 * Math.sin(s * 12.1 + 1);
        double u = meander + lip * Math.max(0.0, w);
        double v = ThresholdLayout.TEAR_Y - ThresholdLayout.AXIS_Y + s * (ThresholdLayout.TEAR_HALF_HEIGHT + g * 0.12);
        return new double[]{u, v};
    }

    private static void tearVertex(VertexConsumer vc, Vec3 cam, double[] p, double z, float a) {
        vc.addVertex((float) (wx(p[0], p[1], z) - cam.x), (float) (wy(p[0], p[1], z) - cam.y), (float) (z - cam.z)).setColor(1f, 1f, 1f, a);
    }

    /** A strip between two outlines (inner alpha ai, outer alpha ao), both windings. */
    private static void band(VertexConsumer vc, Vec3 cam, double[] i0, double[] o0, double[] o1, double[] i1, double z, float ai, float ao) {
        double[][] p = {i0, o0, o1, i1};
        float[] al = {ai, ao, ao, ai};
        for (int[] o : new int[][]{{0, 1, 2, 3}, {0, 3, 2, 1}})
            for (int k : o) tearVertex(vc, cam, p[k], z, al[k]);
    }

    private static void tear(MultiBufferSource.BufferSource buffers, Vec3 cam) {
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        double t = ClientState.time / 20.0;
        int n = 64;
        double z = Z1 - 0.01;
        for (int i = 0; i < n; i++) {
            double s0 = -1 + 2.0 * i / n, s1 = -1 + 2.0 * (i + 1) / n;
            for (int side = -1; side <= 1; side += 2) {
                double[] e0 = lip(s0, side, t, 0), e1 = lip(s1, side, t, 0);
                // soft glow fading out from the lips: a tight bright one and a wide faint one
                band(vc, cam, e0, lip(s0, side, t, 0.18), lip(s1, side, t, 0.18), e1, z - 0.002, 0.75f, 0f);
                band(vc, cam, e0, lip(s0, side, t, 0.75), lip(s1, side, t, 0.75), e1, z - 0.001, 0.28f, 0f);
            }
            // the blinding core between the lips
            double[] l0 = lip(s0, -1, t, 0), r0 = lip(s0, 1, t, 0), l1 = lip(s1, -1, t, 0), r1 = lip(s1, 1, t, 0);
            band(vc, cam, l0, r0, r1, l1, z - 0.003, 1f, 1f);
        }
        buffers.endBatch(RenderType.lightning());
    }
}
