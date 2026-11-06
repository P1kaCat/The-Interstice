package com.interstice.client.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;

/**
 * A rift as a free-standing tear of light (levels 02 and on): a thin vertical slit with jagged, faintly shimmering
 * lips tapering to sharp points, a blinding core and soft gradient halos, drawn additively and seen from both sides,
 * in the plane x-y at the given z. (Level 00 draws its own, through its twisted frame.)
 */
public final class RiftTearRenderer {
    private RiftTearRenderer() {}

    private static double jag(double s, int lip, double t) {
        return 0.55 * Math.sin(s * 9.3 + lip * 2.1) + 0.3 * Math.sin(s * 23.7 + lip * 5.3) + 0.15 * Math.sin(s * 51.0 + lip)
                + 0.06 * Math.sin(t * 2.3 + s * 7 + lip * 3);
    }

    private static double[] lip(double cx, double cy, double halfH, double halfW, double s, int lip, double t, double g) {
        double k = Math.max(0, 1 - Math.abs(s));
        double taper = Math.pow(k, 1.25) * (1 + 0.5 * k);
        double w = halfW * taper * (1 + 0.35 * jag(s, lip, t)) + g * Math.pow(k, 0.9);
        double meander = 0.11 * Math.sin(s * 4.7) + 0.04 * Math.sin(s * 12.1 + 1);
        return new double[]{cx + meander + lip * Math.max(0, w), cy + s * (halfH + g * 0.12)};
    }

    private static void band(VertexConsumer vc, Vec3 cam, double[] i0, double[] o0, double[] o1, double[] i1, double z, float ai, float ao) {
        double[][] p = {i0, o0, o1, i1};
        float[] al = {ai, ao, ao, ai};
        for (int[] o : new int[][]{{0, 1, 2, 3}, {0, 3, 2, 1}})
            for (int k : o)
                vc.addVertex((float) (p[k][0] - cam.x), (float) (p[k][1] - cam.y), (float) (z - cam.z)).setColor(1f, 1f, 1f, al[k]);
    }

    public static void draw(MultiBufferSource.BufferSource buffers, Vec3 cam, double cx, double cy, double z, double halfH, double halfW, double time) {
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        int n = 64;
        for (int i = 0; i < n; i++) {
            double s0 = -1 + 2.0 * i / n, s1 = -1 + 2.0 * (i + 1) / n;
            for (int side = -1; side <= 1; side += 2) {
                double[] e0 = lip(cx, cy, halfH, halfW, s0, side, time, 0), e1 = lip(cx, cy, halfH, halfW, s1, side, time, 0);
                band(vc, cam, e0, lip(cx, cy, halfH, halfW, s0, side, time, 0.22), lip(cx, cy, halfH, halfW, s1, side, time, 0.22), e1, z, 0.75f, 0f);
                band(vc, cam, e0, lip(cx, cy, halfH, halfW, s0, side, time, 0.9), lip(cx, cy, halfH, halfW, s1, side, time, 0.9), e1, z, 0.28f, 0f);
            }
            double[] l0 = lip(cx, cy, halfH, halfW, s0, -1, time, 0), r0 = lip(cx, cy, halfH, halfW, s0, 1, time, 0);
            double[] l1 = lip(cx, cy, halfH, halfW, s1, -1, time, 0), r1 = lip(cx, cy, halfH, halfW, s1, 1, time, 0);
            band(vc, cam, l0, r0, r1, l1, z, 1f, 1f);
        }
        buffers.endBatch(RenderType.lightning());
    }
}
