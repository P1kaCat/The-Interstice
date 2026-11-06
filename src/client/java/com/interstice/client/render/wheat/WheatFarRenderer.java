package com.interstice.client.render.wheat;

import com.interstice.Interstice;
import com.interstice.client.ClientState;
import com.interstice.world.wheat.WheatLayout;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Level 02's far field: what lies beyond the loaded chunks, so the whole farm can be seen to the house at its end.
 * Drawn only outside the loaded radius (and a little inside it, slightly lower than the real surface, so the two
 * overlap without a gap): the golden field as a ground plane in 16-block tiles near and 64-block tiles far, the
 * track as a strip, the two fence lines as thin rails, and the farmhouse as a simple massing (walls, gable roof,
 * chimney). The turbines draw themselves to the horizon (TurbineRenderer). The long evening haze (FogRendererMixin)
 * fades it all towards the horizon colour.
 */
public final class WheatFarRenderer {
    private static final ResourceLocation FIELD = Interstice.id("textures/level/wheat_canopy.png");
    private static final ResourceLocation PATH = Interstice.id("textures/block/farm_path.png");
    private static final ResourceLocation WOOD = Interstice.id("textures/block/floorboards.png");
    private static final ResourceLocation PLASTER = Interstice.id("textures/block/plaster_wall.png");
    private static final ResourceLocation BRICK = Interstice.id("textures/block/grimy_brick.png");
    private static final ResourceLocation WINDOW = Interstice.id("textures/level/window_lit.png");
    private static final int LIGHT = LightTexture.pack(0, 15);
    private static final double X_EXTENT = 1600, Z_BEFORE = 400, Z_AFTER = 500;

    private WheatFarRenderer() {}

    public static void render(Camera camera, MultiBufferSource.BufferSource buffers) {
        if (!ClientState.inWheatFarm) return;
        Vec3 cam = camera.getPosition();
        int rd = Minecraft.getInstance().options.getEffectiveRenderDistance();
        double near = Math.max(32, (rd - 1.5) * 16);   // inside this the real chunks are drawn
        double y0 = WheatLayout.GROUND_Y - 0.06;

        VertexConsumer field = buffers.getBuffer(RenderType.entitySolid(FIELD));
        double zMin = -Z_BEFORE, zMax = WheatLayout.HOUSE_Z + Z_AFTER;
        for (double x = -X_EXTENT; x < X_EXTENT; x += 64)
            for (double z = zMin; z < zMax; z += 64) {
                if (outside(cam, x, z, x + 64, z + 64, near + 64)) {
                    ground(field, cam, x, z, x + 64, z + 64, y0);
                } else {
                    for (double sx = x; sx < x + 64; sx += 16)
                        for (double sz = z; sz < z + 64; sz += 16)
                            if (outside(cam, sx, sz, sx + 16, sz + 16, near)) ground(field, cam, sx, sz, sx + 16, sz + 16, y0);
                            else if (beyondStalks(cam, sx, sz, sx + 16, sz + 16)) canopyOverSoil(field, cam, sx, sz, sx + 16, sz + 16);
                }
            }
        buffers.endBatch(RenderType.entitySolid(FIELD));

        // the track and the fences, in 16-block pieces beyond the loaded radius
        VertexConsumer path = buffers.getBuffer(RenderType.entitySolid(PATH));
        for (double z = 0; z < WheatLayout.HOUSE_Z; z += 16)
            if (outside(cam, -1, z, 2, z + 16, near)) flat(path, cam, -WheatLayout.ROUTE_HALF, z, WheatLayout.ROUTE_HALF + 1, Math.min(z + 16, WheatLayout.HOUSE_Z), y0 + 0.02, 1);
        buffers.endBatch(RenderType.entitySolid(PATH));
        VertexConsumer wood = buffers.getBuffer(RenderType.entityCutoutNoCull(WOOD));
        for (double z = 0; z < WheatLayout.ROUTE_END; z += 16) {
            for (int side = -1; side <= 1; side += 2) {
                double fx = side * WheatLayout.FENCE_X + 0.5;
                if (!outside(cam, fx - 1, z, fx + 1, z + 16, near)) continue;
                rail(wood, cam, fx, z, z + 16, WheatLayout.GROUND_Y + 0.38, 0.16);
                rail(wood, cam, fx, z, z + 16, WheatLayout.GROUND_Y + 0.78, 0.16);
                for (double pz = z; pz < z + 16; pz += 4) post(wood, cam, fx, pz + 0.5, WheatLayout.GROUND_Y, WheatLayout.GROUND_Y + 1.1);
            }
        }
        buffers.endBatch(RenderType.entityCutoutNoCull(WOOD));
        // the farmhouse's massing, while its chunks are not loaded
        double hx0 = -WheatLayout.HOUSE_HALF, hx1 = WheatLayout.HOUSE_HALF + 1, hz0 = WheatLayout.HOUSE_Z, hz1 = WheatLayout.HOUSE_Z + WheatLayout.HOUSE_DEPTH;
        if (outside(cam, hx0, hz0, hx1, hz1, near)) house(buffers, cam, hx0, hx1, hz0, hz1);
        houseLights(buffers, cam);
    }

    /**
     * The farmhouse's lit windows (warm, full bright, near and far alike) and, from afar, a soft warm halo around it:
     * a light at the end of the track to walk towards.
     */
    private static void houseLights(MultiBufferSource.BufferSource buffers, Vec3 cam) {
        int gy = WheatLayout.GROUND_Y, za = WheatLayout.HOUSE_Z, zb = WheatLayout.HOUSE_Z + WheatLayout.HOUSE_DEPTH;
        int xa = -WheatLayout.HOUSE_HALF, xb = WheatLayout.HOUSE_HALF + 1;
        VertexConsumer w = buffers.getBuffer(RenderType.entitySolid(WINDOW));
        int full = LightTexture.FULL_BRIGHT;
        for (int wx : new int[]{-3, 3}) {
            win(w, cam, wx, gy + 1, za - 0.02, wx + 1, gy + 3, za - 0.02, full);              // front, ground floor
            if (wx > 0) win(w, cam, wx, gy + 5, za - 0.02, wx + 1, gy + 7, za - 0.02, full);  // one lit upstairs
            win(w, cam, wx, gy + 1, zb + 0.02, wx + 1, gy + 3, zb + 0.02, full);
        }
        for (int wz : new int[]{za + 3, za + 7}) {
            win(w, cam, xa - 0.02, gy + 1, wz, xa - 0.02, gy + 3, wz + 1, full);
            win(w, cam, xb + 0.02, gy + 1, wz, xb + 0.02, gy + 3, wz + 1, full);
        }
        buffers.endBatch(RenderType.entitySolid(WINDOW));
        double hx = 0.5, hy = gy + 3.5, hz = za;
        double d = Math.sqrt((hx - cam.x) * (hx - cam.x) + (hz - cam.z) * (hz - cam.z));
        float a = (float) (0.32 * Math.min(1, Math.max(0, (d - 120) / 500)));
        if (a <= 0.005f) return;
        // a camera-facing disc of warm light, fading out from its centre
        VertexConsumer g = buffers.getBuffer(RenderType.lightning());
        double rx = cam.z - hz, rz = hx - cam.x, rl = Math.sqrt(rx * rx + rz * rz) + 1e-6;
        rx /= rl;
        rz /= rl;
        double size = 18 + d * 0.012;
        int n = 24;
        for (int i = 0; i < n; i++) {
            double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
            double[][] p = {{hx, hy, hz}, {hx + rx * Math.cos(a0) * size, hy + Math.sin(a0) * size * 0.6, hz + rz * Math.cos(a0) * size},
                    {hx + rx * Math.cos(a1) * size, hy + Math.sin(a1) * size * 0.6, hz + rz * Math.cos(a1) * size}, {hx, hy, hz}};
            float[] al = {a, 0f, 0f, a};
            for (int[] o : new int[][]{{0, 1, 2, 3}, {0, 3, 2, 1}})
                for (int k : o)
                    g.addVertex((float) (p[k][0] - cam.x), (float) (p[k][1] - cam.y), (float) (p[k][2] - cam.z)).setColor(1f, 0.72f, 0.38f, al[k]);
        }
        buffers.endBatch(RenderType.lightning());
    }

    private static void win(VertexConsumer vc, Vec3 cam, double x0, double y0, double z0, double x1, double y1, double z1, int light) {
        for (int[] o : new int[][]{{0, 1, 2, 3}, {0, 3, 2, 1}}) {
            double[][] p = {{x0, y0, z0}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z0}};
            float[][] uv = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
            for (int k : o)
                vc.addVertex((float) (p[k][0] - cam.x), (float) (p[k][1] - cam.y), (float) (p[k][2] - cam.z)).setColor(255, 255, 255, 255)
                        .setUv(uv[k][0], uv[k][1]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(0, 0, -1);
        }
    }

    /** Is the rectangle entirely farther than r from the camera (horizontally)? */
    private static boolean outside(Vec3 cam, double x0, double z0, double x1, double z1, double r) {
        double dx = Math.max(0, Math.max(x0 - cam.x, cam.x - x1)), dz = Math.max(0, Math.max(z0 - cam.z, cam.z - z1));
        return dx * dx + dz * dz > r * r;
    }

    private static void v(VertexConsumer vc, Vec3 cam, double x, double y, double z, float u, float w, float nx, float ny, float nz) {
        vc.addVertex((float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z)).setColor(255, 255, 255, 255).setUv(u, w)
                .setOverlay(OverlayTexture.NO_OVERLAY).setLight(LIGHT).setNormal(nx, ny, nz);
    }

    /** Whether part of this tile lies past the radius the wheat stalks are drawn to. */
    private static boolean beyondStalks(Vec3 cam, double x0, double z0, double x1, double z1) {
        double fx = Math.max(Math.abs(x0 - cam.x), Math.abs(x1 - cam.x)), fz = Math.max(Math.abs(z0 - cam.z), Math.abs(z1 - cam.z));
        return fx * fx + fz * fz > (WheatRenderer.RADIUS - 6) * (WheatRenderer.RADIUS - 6);
    }

    /**
     * Inside the loaded chunks but past the stalks, the real ground is bare tilled soil: the canopy is laid just over
     * it there (lifted enough not to flicker at that distance), leaving out the fenced track and the house plot.
     */
    private static void canopyOverSoil(VertexConsumer vc, Vec3 cam, double x0, double z0, double x1, double z1) {
        double sz0 = -2, sz1 = WheatLayout.HOUSE_Z, fx0 = -WheatLayout.FENCE_X, fx1 = WheatLayout.FENCE_X + 1;
        // before the start and past the house: the whole width; along the track: either side of the fenced strip
        if (z0 < sz0) canopyPiece(vc, cam, x0, z0, x1, Math.min(z1, sz0));
        if (z1 > sz1) canopyPiece(vc, cam, x0, Math.max(z0, sz1), x1, z1);
        double za = Math.max(z0, sz0), zb = Math.min(z1, sz1);
        if (zb > za) {
            if (Math.min(x1, fx0) > x0) canopyPiece(vc, cam, x0, za, Math.min(x1, fx0), zb);
            if (x1 > Math.max(x0, fx1)) canopyPiece(vc, cam, Math.max(x0, fx1), za, x1, zb);
        }
    }

    /** One piece of canopy, leaving out the house plot. */
    private static void canopyPiece(VertexConsumer vc, Vec3 cam, double x0, double z0, double x1, double z1) {
        double y = WheatLayout.GROUND_Y + 0.12;
        double hx0 = -WheatLayout.HOUSE_HALF - 4, hx1 = WheatLayout.HOUSE_HALF + 5;
        double hz0 = WheatLayout.HOUSE_Z - 5, hz1 = WheatLayout.HOUSE_Z + WheatLayout.HOUSE_DEPTH + 4;
        if (!(x1 > hx0 && x0 < hx1 && z1 > hz0 && z0 < hz1)) {
            flat(vc, cam, x0, z0, x1, z1, y, 4);
            return;
        }
        if (z0 < hz0) flat(vc, cam, x0, z0, x1, hz0, y, 4);
        if (z1 > hz1) flat(vc, cam, x0, hz1, x1, z1, y, 4);
        double za = Math.max(z0, hz0), zb = Math.min(z1, hz1);
        if (x0 < hx0) flat(vc, cam, x0, za, hx0, zb, y, 4);
        if (x1 > hx1) flat(vc, cam, hx1, za, x1, zb, y, 4);
    }

    /** The field: the canopy texture once per 4 blocks (as the real surface shows it). */
    private static void ground(VertexConsumer vc, Vec3 cam, double x0, double z0, double x1, double z1, double y) {
        flat(vc, cam, x0, z0, x1, z1, y, 4);
    }

    private static void flat(VertexConsumer vc, Vec3 cam, double x0, double z0, double x1, double z1, double y, double rep) {
        float u0 = (float) (x0 / rep), u1 = (float) (x1 / rep), w0 = (float) (z0 / rep), w1 = (float) (z1 / rep);
        v(vc, cam, x0, y, z0, u0, w0, 0, 1, 0);
        v(vc, cam, x0, y, z1, u0, w1, 0, 1, 0);
        v(vc, cam, x1, y, z1, u1, w1, 0, 1, 0);
        v(vc, cam, x1, y, z0, u1, w0, 0, 1, 0);
    }

    private static void rail(VertexConsumer vc, Vec3 cam, double x, double z0, double z1, double y, double h) {
        v(vc, cam, x, y, z0, 0, 0, 1, 0, 0);
        v(vc, cam, x, y + h, z0, 0, 0.2f, 1, 0, 0);
        v(vc, cam, x, y + h, z1, (float) (z1 - z0), 0.2f, 1, 0, 0);
        v(vc, cam, x, y, z1, (float) (z1 - z0), 0, 1, 0, 0);
    }

    private static void post(VertexConsumer vc, Vec3 cam, double x, double z, double y0, double y1) {
        double r = 0.13;
        double[][] sides = {{x - r, z - r, x + r, z - r}, {x + r, z - r, x + r, z + r}, {x + r, z + r, x - r, z + r}, {x - r, z + r, x - r, z - r}};
        for (double[] s : sides) {
            v(vc, cam, s[0], y0, s[1], 0, 1, 0, 0, 1);
            v(vc, cam, s[0], y1, s[1], 0, 0, 0, 0, 1);
            v(vc, cam, s[2], y1, s[3], 0.25f, 0, 0, 0, 1);
            v(vc, cam, s[2], y0, s[3], 0.25f, 1, 0, 0, 1);
        }
    }

    private static void house(MultiBufferSource.BufferSource buffers, Vec3 cam, double x0, double x1, double z0, double z1) {
        double y0 = WheatLayout.GROUND_Y, y1 = y0 + WheatLayout.HOUSE_WALL, ridge = y1 + (WheatLayout.HOUSE_DEPTH + 1) / 2.0 + 0.5;
        double zm = (z0 + z1) / 2;
        VertexConsumer pl = buffers.getBuffer(RenderType.entityCutoutNoCull(PLASTER));
        // the four walls
        quad(pl, cam, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, 0, 0, -1);
        quad(pl, cam, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 1);
        quad(pl, cam, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1, 0, 0);
        quad(pl, cam, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, 1, 0, 0);
        // the gables (triangles as degenerate quads)
        quad(pl, cam, x0, y1, z0, x0, y1, z1, x0, ridge, zm, x0, ridge, zm, -1, 0, 0);
        quad(pl, cam, x1, y1, z0, x1, y1, z1, x1, ridge, zm, x1, ridge, zm, 1, 0, 0);
        buffers.endBatch(RenderType.entityCutoutNoCull(PLASTER));
        VertexConsumer roof = buffers.getBuffer(RenderType.entityCutoutNoCull(WOOD));
        quad(roof, cam, x0 - 1, y1, z0 - 1, x1 + 1, y1, z0 - 1, x1 + 1, ridge, zm, x0 - 1, ridge, zm, 0, 0.7f, -0.7f);
        quad(roof, cam, x0 - 1, y1, z1 + 1, x1 + 1, y1, z1 + 1, x1 + 1, ridge, zm, x0 - 1, ridge, zm, 0, 0.7f, 0.7f);
        buffers.endBatch(RenderType.entityCutoutNoCull(WOOD));
        VertexConsumer br = buffers.getBuffer(RenderType.entityCutoutNoCull(BRICK));
        double cx = 3, cz = z1 - 3, top = ridge + 2.5;
        quad(br, cam, cx, y1, cz, cx + 1, y1, cz, cx + 1, top, cz, cx, top, cz, 0, 0, -1);
        quad(br, cam, cx, y1, cz + 1, cx + 1, y1, cz + 1, cx + 1, top, cz + 1, cx, top, cz + 1, 0, 0, 1);
        quad(br, cam, cx, y1, cz, cx, y1, cz + 1, cx, top, cz + 1, cx, top, cz, -1, 0, 0);
        quad(br, cam, cx + 1, y1, cz, cx + 1, y1, cz + 1, cx + 1, top, cz + 1, cx + 1, top, cz, 1, 0, 0);
        buffers.endBatch(RenderType.entityCutoutNoCull(BRICK));
    }

    private static void quad(VertexConsumer vc, Vec3 cam, double ax, double ay, double az, double bx, double by, double bz,
                             double cx, double cy, double cz, double dx, double dy, double dz, float nx, float ny, float nz) {
        float uw = (float) Math.max(1, Math.hypot(bx - ax, bz - az)), vh = (float) Math.max(1, Math.abs(cy - ay));
        v(vc, cam, ax, ay, az, 0, vh, nx, ny, nz);
        v(vc, cam, bx, by, bz, uw, vh, nx, ny, nz);
        v(vc, cam, cx, cy, cz, uw, 0, nx, ny, nz);
        v(vc, cam, dx, dy, dz, 0, 0, nx, ny, nz);
    }
}
