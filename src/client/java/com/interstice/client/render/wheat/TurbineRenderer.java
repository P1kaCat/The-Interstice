package com.interstice.client.render.wheat;

import com.interstice.Interstice;
import com.interstice.client.ClientState;
import com.interstice.world.wheat.WheatLayout;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

/**
 * Level 02, The Wheat Farm: the wind turbines, as smooth meshes (no blocks): a tapered round mast, a rounded nacelle,
 * a nose cone, and three slender twisted blades with an aerofoil section that tapers from root to tip. They face
 * the wind (the same direction as the wheat's waves) and turn at a steady, frame-rate independent speed from the
 * game time and a per-turbine phase, so every client sees the same rotor. Fewer segments far away.
 */
public final class TurbineRenderer {
    private static final ResourceLocation PAINT = Interstice.id("textures/level/turbine.png");
    private static final double DRAW_DISTANCE = 2200, OMEGA = 0.85;
    /** The wind blows towards +x+z (as in the wheat shader); turbines face into it. */
    private static final double WX = 0.8, WZ = 0.6;

    private TurbineRenderer() {}

    public static void render(Camera camera, MultiBufferSource.BufferSource buffers) {
        if (!ClientState.inWheatFarm) return;
        Vec3 cam = camera.getPosition();
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(PAINT));   // both sides: no winding to get wrong
        int c0 = (int) Math.floor((Math.max(cam.z - DRAW_DISTANCE, WheatLayout.TURBINE_Z0) - WheatLayout.TURBINE_CELL) / WheatLayout.TURBINE_CELL);
        int c1 = (int) Math.floor(Math.min(cam.z + DRAW_DISTANCE, WheatLayout.TURBINE_Z1) / WheatLayout.TURBINE_CELL);
        double time = ClientState.time / 20.0;
        for (int c = c0; c <= c1; c++) {
            for (int side = -1; side <= 1; side += 2) {
                for (int row = 0; row < WheatLayout.TURBINE_ROWS; row++) {
                    int[] t = WheatLayout.turbine(c, side, row);
                    if (t == null) continue;
                    double bx = t[0] + 0.5, bz = t[1] + 0.5, by = WheatLayout.groundY(t[0], t[1]);
                    double d = Math.hypot(bx - cam.x, bz - cam.z);
                    if (d > DRAW_DISTANCE) continue;
                    double rotor = WheatLayout.turbinePhase(c, side, row) + time * OMEGA;
                    if (d > 750) silhouette(vc, cam, bx, by, bz, rotor);   // far: a few faces only
                    else turbine(vc, cam, bx, by, bz, d < 120 ? 24 : d < 300 ? 14 : 8, rotor);
                }
            }
        }
        buffers.endBatch(RenderType.entityCutoutNoCull(PAINT));
    }

    // ---------------------------------------------------------------- geometry

    private static void turbine(VertexConsumer vc, Vec3 cam, double bx, double by, double bz, int seg, double rotor) {
        double h = WheatLayout.TURBINE_HEIGHT;
        // the mast: a tapered round tower
        ring(vc, cam, bx, by, bz, 3.0, 1.6, h, seg);
        // the nacelle: a horizontal capsule along the wind axis, centred on the mast top
        double hx = bx, hy = by + h + 1.2, hz = bz;
        Vector3d ax = new Vector3d(-WX, 0, -WZ);   // nose points into the wind
        capsule(vc, cam, hx - ax.x * 1.8, hy, hz - ax.z * 1.8, ax, 1.8, 8.0, seg);
        // the hub and its nose cone
        double nx = hx + ax.x * 1.9, nz = hz + ax.z * 1.9;
        cone(vc, cam, nx, hy, nz, ax, 1.7, 2.8, seg);
        // three blades
        for (int k = 0; k < 3; k++) blade(vc, cam, nx + ax.x * 0.4, hy, nz + ax.z * 0.4, ax, rotor + k * Math.PI * 2 / 3, seg);
    }

    /**
     * A far turbine: a four-sided tapered mast, a box nacelle and three flat tapered blades (about 16 faces):
     * enough for a silhouette in the haze, turning like the near ones.
     */
    private static void silhouette(VertexConsumer vc, Vec3 cam, double bx, double by, double bz, double rotor) {
        double h = WheatLayout.TURBINE_HEIGHT, r0 = 3.0, r1 = 1.6;
        for (int i = 0; i < 4; i++) {
            double a0 = i * Math.PI / 2 + Math.PI / 4, a1 = a0 + Math.PI / 2;
            put(vc, cam, bx + Math.cos(a0) * r0, by, bz + Math.sin(a0) * r0, 0, 1, Math.cos(a0), 0, Math.sin(a0));
            put(vc, cam, bx + Math.cos(a0) * r1, by + h, bz + Math.sin(a0) * r1, 0, 0, Math.cos(a0), 0, Math.sin(a0));
            put(vc, cam, bx + Math.cos(a1) * r1, by + h, bz + Math.sin(a1) * r1, 1, 0, Math.cos(a1), 0, Math.sin(a1));
            put(vc, cam, bx + Math.cos(a1) * r0, by, bz + Math.sin(a1) * r0, 1, 1, Math.cos(a1), 0, Math.sin(a1));
        }
        double hy = by + h + 1.2;
        Vector3d ax = new Vector3d(-WX, 0, -WZ);
        Vector3d[] f = frame(ax);
        // nacelle: a box along the wind axis
        double[][] box = {{-4.5, 1.6}, {2.6, 1.6}};
        for (int k = 0; k < 4; k++) {
            double a0 = k * Math.PI / 2, a1 = a0 + Math.PI / 2;
            double[] p0 = pt(bx, hy, bz, ax, f, box[0][0], 1.6, a0), p1 = pt(bx, hy, bz, ax, f, box[1][0], 1.6, a0);
            double[] p2 = pt(bx, hy, bz, ax, f, box[1][0], 1.6, a1), p3 = pt(bx, hy, bz, ax, f, box[0][0], 1.6, a1);
            quad(vc, cam, p0, p1, p2, p3, bx, hy, bz, ax, 0, 1, 0, 1);
        }
        // three flat blades
        double nx = bx + ax.x * 3.0, nz = bz + ax.z * 3.0;
        for (int k = 0; k < 3; k++) {
            double ang = rotor + k * Math.PI * 2 / 3;
            Vector3d span = new Vector3d(f[0]).mul(Math.cos(ang)).add(new Vector3d(f[1]).mul(Math.sin(ang)));
            Vector3d chord = new Vector3d(span).cross(ax).normalize();
            double len = WheatLayout.BLADE_LENGTH;
            double[] r = {nx + span.x * 1.5 + chord.x * 1.6, hy + span.y * 1.5 + chord.y * 1.6, nz + span.z * 1.5 + chord.z * 1.6};
            double[] l = {nx + span.x * 1.5 - chord.x * 1.6, hy + span.y * 1.5 - chord.y * 1.6, nz + span.z * 1.5 - chord.z * 1.6};
            double[] tr = {nx + span.x * len + chord.x * 0.4, hy + span.y * len + chord.y * 0.4, nz + span.z * len + chord.z * 0.4};
            double[] tl = {nx + span.x * len - chord.x * 0.4, hy + span.y * len - chord.y * 0.4, nz + span.z * len - chord.z * 0.4};
            put(vc, cam, l[0], l[1], l[2], 0, 1, ax.x, ax.y, ax.z);
            put(vc, cam, r[0], r[1], r[2], 1, 1, ax.x, ax.y, ax.z);
            put(vc, cam, tr[0], tr[1], tr[2], 1, 0, ax.x, ax.y, ax.z);
            put(vc, cam, tl[0], tl[1], tl[2], 0, 0, ax.x, ax.y, ax.z);
        }
    }

    private static final int LIGHT = LightTexture.pack(15, 15);   // lit by the open evening sky

    private static void put(VertexConsumer vc, Vec3 cam, double x, double y, double z, float u, float v, double nx, double ny, double nz) {
        vc.addVertex((float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z)).setColor(255, 240, 222, 255).setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LIGHT).setNormal((float) nx, (float) ny, (float) nz);
    }

    /** A tapered vertical tube from y to y + h, radius r0 at the bottom and r1 at the top. */
    private static void ring(VertexConsumer vc, Vec3 cam, double x, double y, double z, double r0, double r1, double h, int seg) {
        int rows = 6;
        for (int j = 0; j < rows; j++) {
            double ya = y + h * j / rows, yb = y + h * (j + 1) / rows;
            double ra = r0 + (r1 - r0) * j / rows, rb = r0 + (r1 - r0) * (j + 1) / rows;
            for (int i = 0; i < seg; i++) {
                double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
                float u0 = (float) i / seg, u1 = (float) (i + 1) / seg, v0 = (float) j / rows * 3, v1 = (float) (j + 1) / rows * 3;
                put(vc, cam, x + Math.cos(a0) * ra, ya, z + Math.sin(a0) * ra, u0, v0, Math.cos(a0), 0, Math.sin(a0));
                put(vc, cam, x + Math.cos(a0) * rb, yb, z + Math.sin(a0) * rb, u0, v1, Math.cos(a0), 0, Math.sin(a0));
                put(vc, cam, x + Math.cos(a1) * rb, yb, z + Math.sin(a1) * rb, u1, v1, Math.cos(a1), 0, Math.sin(a1));
                put(vc, cam, x + Math.cos(a1) * ra, ya, z + Math.sin(a1) * ra, u1, v0, Math.cos(a1), 0, Math.sin(a1));
            }
        }
    }

    /** An orthonormal frame (u, v) perpendicular to axis a. */
    private static Vector3d[] frame(Vector3d a) {
        Vector3d up = Math.abs(a.y) < 0.9 ? new Vector3d(0, 1, 0) : new Vector3d(1, 0, 0);
        Vector3d u = new Vector3d(a).cross(up).normalize();
        Vector3d v = new Vector3d(u).cross(a).normalize();
        return new Vector3d[]{u, v};
    }

    /** A capsule centred at (x, y, z) along axis a: radius r, length len, rounded ends. */
    private static void capsule(VertexConsumer vc, Vec3 cam, double x, double y, double z, Vector3d a, double r, double len, int seg) {
        Vector3d[] f = frame(a);
        int rows = 10;
        for (int j = 0; j < rows; j++) {
            double s0 = -1 + 2.0 * j / rows, s1 = -1 + 2.0 * (j + 1) / rows;
            double rad0 = r * Math.sqrt(Math.max(0.05, 1 - Math.pow(Math.abs(s0), 6))), rad1 = r * Math.sqrt(Math.max(0.05, 1 - Math.pow(Math.abs(s1), 6)));
            double c0 = s0 * len / 2, c1 = s1 * len / 2;
            for (int i = 0; i < seg; i++) {
                double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
                double[] p00 = pt(x, y, z, a, f, c0, rad0, a0), p01 = pt(x, y, z, a, f, c1, rad1, a0);
                double[] p11 = pt(x, y, z, a, f, c1, rad1, a1), p10 = pt(x, y, z, a, f, c0, rad0, a1);
                quad(vc, cam, p00, p01, p11, p10, x, y, z, a, (float) i / seg, (float) (i + 1) / seg, (float) j / rows, (float) (j + 1) / rows);
            }
        }
    }

    /** A cone from base centre (x, y, z) along axis a: base radius r, length len (a rounded nose). */
    private static void cone(VertexConsumer vc, Vec3 cam, double x, double y, double z, Vector3d a, double r, double len, int seg) {
        Vector3d[] f = frame(a);
        int rows = 6;
        for (int j = 0; j < rows; j++) {
            double s0 = (double) j / rows, s1 = (double) (j + 1) / rows;
            double rad0 = r * Math.sqrt(1 - s0 * s0), rad1 = r * Math.sqrt(Math.max(0, 1 - s1 * s1));
            for (int i = 0; i < seg; i++) {
                double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
                double[] p00 = pt(x, y, z, a, f, s0 * len, rad0, a0), p01 = pt(x, y, z, a, f, s1 * len, rad1, a0);
                double[] p11 = pt(x, y, z, a, f, s1 * len, rad1, a1), p10 = pt(x, y, z, a, f, s0 * len, rad0, a1);
                quad(vc, cam, p00, p01, p11, p10, x, y, z, a, (float) i / seg, (float) (i + 1) / seg, (float) s0, (float) s1);
            }
        }
    }

    private static double[] pt(double x, double y, double z, Vector3d a, Vector3d[] f, double along, double rad, double ang) {
        double c = Math.cos(ang) * rad, s = Math.sin(ang) * rad;
        return new double[]{x + a.x * along + f[0].x * c + f[1].x * s, y + a.y * along + f[0].y * c + f[1].y * s, z + a.z * along + f[0].z * c + f[1].z * s};
    }

    /** A quad of a surface of revolution about axis a through (cx, cy, cz): normals point away from the axis. */
    private static void quad(VertexConsumer vc, Vec3 cam, double[] p00, double[] p01, double[] p11, double[] p10, double cx, double cy, double cz,
                             Vector3d a, float u0, float u1, float v0, float v1) {
        double[][] ps = {p00, p01, p11, p10};
        float[][] uv = {{u0, v0}, {u0, v1}, {u1, v1}, {u1, v0}};
        for (int k = 0; k < 4; k++) {
            double[] p = ps[k];
            double rx = p[0] - cx, ry = p[1] - cy, rz = p[2] - cz;
            double along = rx * a.x + ry * a.y + rz * a.z;
            double nx = rx - a.x * along, ny = ry - a.y * along, nz = rz - a.z * along;
            double nl = Math.sqrt(nx * nx + ny * ny + nz * nz) + 1e-6;
            put(vc, cam, p[0], p[1], p[2], uv[k][0], uv[k][1], nx / nl, ny / nl, nz / nl);
        }
    }

    /**
     * One blade from the hub at (x, y, z), in the rotor plane (perpendicular to the axis a), at angle ang: an aerofoil
     * section (rounded leading edge, thin trailing edge) whose chord narrows from root to tip and whose pitch twists.
     */
    private static void blade(VertexConsumer vc, Vec3 cam, double x, double y, double z, Vector3d a, double ang, int seg) {
        Vector3d[] f = frame(a);
        Vector3d span = new Vector3d(f[0]).mul(Math.cos(ang)).add(new Vector3d(f[1]).mul(Math.sin(ang)));
        Vector3d chordDir = new Vector3d(span).cross(a).normalize();
        double len = WheatLayout.BLADE_LENGTH;
        int rows = seg >= 20 ? 14 : 8, sec = 8;
        double[][][] ring = new double[rows + 1][sec][3];
        for (int j = 0; j <= rows; j++) {
            double s = (double) j / rows;
            double chord = 3.6 * Math.pow(1 - s, 0.75) + 0.5 - (s < 0.08 ? (0.08 - s) * 12 : 0);
            chord = Math.max(0.5, chord);
            double thick = chord * 0.16;
            double twist = Math.toRadians(16 - 14 * s);
            Vector3d cd = new Vector3d(chordDir).mul(Math.cos(twist)).add(new Vector3d(a).mul(Math.sin(twist)));
            Vector3d td = new Vector3d(a).mul(Math.cos(twist)).sub(new Vector3d(chordDir).mul(Math.sin(twist)));
            for (int k = 0; k < sec; k++) {
                // aerofoil outline: t in [0, 2pi), x along the chord (0 leading .. 1 trailing), y thickness
                double t = k * Math.PI * 2 / sec;
                double xc = 0.5 - 0.5 * Math.cos(t);
                double yc = Math.sin(t) * thick * (1.1 - xc) * (k < sec / 2 ? 1 : 0.6);
                double off = (xc - 0.3) * chord;
                double px = x + span.x * (1.6 + s * len) + cd.x * off + td.x * yc;
                double py = y + span.y * (1.6 + s * len) + cd.y * off + td.y * yc;
                double pz = z + span.z * (1.6 + s * len) + cd.z * off + td.z * yc;
                ring[j][k] = new double[]{px, py, pz};
            }
        }
        for (int j = 0; j < rows; j++) {
            for (int k = 0; k < sec; k++) {
                double[] p00 = ring[j][k], p01 = ring[j + 1][k], p11 = ring[j + 1][(k + 1) % sec], p10 = ring[j][(k + 1) % sec];
                // normal from the section's centre line
                double sj = (j + 0.5) / rows;
                double cx = x + span.x * (1.6 + sj * len), cy = y + span.y * (1.6 + sj * len), cz = z + span.z * (1.6 + sj * len);
                double[][] ps = {p00, p01, p11, p10};
                float[][] uv = {{(float) k / sec, (float) j / rows}, {(float) k / sec, (float) (j + 1) / rows}, {(float) (k + 1) / sec, (float) (j + 1) / rows}, {(float) (k + 1) / sec, (float) j / rows}};
                for (int q = 0; q < 4; q++) {
                    double[] p = ps[q];
                    double rx = p[0] - cx, ry = p[1] - cy, rz = p[2] - cz;
                    double along = rx * span.x + ry * span.y + rz * span.z;
                    double nx = rx - span.x * along, ny = ry - span.y * along, nz = rz - span.z * along;
                    double nl = Math.sqrt(nx * nx + ny * ny + nz * nz) + 1e-6;
                    put(vc, cam, p[0], p[1], p[2], uv[q][0], uv[q][1], nx / nl, ny / nl, nz / nl);
                }
            }
        }
    }
}
