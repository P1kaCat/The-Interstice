package com.interstice.client.render.wheat;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Level 02's sky: a smooth evening dome (no blocky clouds, no vanilla sunset band): pale violet-blue overhead, warm
 * gold at the horizon, deepest gold towards a large low sun straight ahead along the track (+Z), with a soft halo.
 * The fog colour is the horizon's, so the fields fade into it.
 */
public final class WheatSky implements DimensionRenderingRegistry.SkyRenderer {
    /** Horizon colour, also the fog colour. */
    public static final float HR = 0.95f, HG = 0.74f, HB = 0.50f;
    public static final double SUN_ELEV = Math.toRadians(5.5);

    /** Dimension effects: no clouds, fog in the horizon's colour. */
    public static final class Effects extends DimensionSpecialEffects {
        public Effects() {
            super(Float.NaN, true, SkyType.NORMAL, false, false);
        }

        @Override
        public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
            return new Vec3(HR, HG, HB);
        }

        @Override
        public boolean isFoggyAt(int x, int z) {
            return false;
        }
    }

    @Override
    public void render(WorldRenderContext ctx) {
        PoseStack ps = new PoseStack();
        ps.mulPose(ctx.positionMatrix());
        Matrix4f m = ps.last().pose();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        double r = 400;
        int az = 48, el = 20;
        for (int i = 0; i < az; i++) {
            double a0 = i * Math.PI * 2 / az, a1 = (i + 1) * Math.PI * 2 / az;
            for (int j = 0; j < el; j++) {
                double e0 = -0.25 + (Math.PI / 2 + 0.25) * j / el, e1 = -0.25 + (Math.PI / 2 + 0.25) * (j + 1) / el;
                vert(b, m, r, a0, e0);
                vert(b, m, r, a0, e1);
                vert(b, m, r, a1, e1);
                vert(b, m, r, a1, e0);
            }
        }
        BufferUploader.drawWithShader(b.buildOrThrow());
        // the sun: a bright disc and its halo, low ahead along the track
        b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        // smaller and brighter: a white-hot disc, a tight glare, a wide faint glow (the god rays do the rest)
        sun(b, m, r * 0.98, 0.045, 1f, 1f, 0.97f, 1f, 1f);
        sun(b, m, r * 0.982, 0.075, 1f, 0.96f, 0.85f, 1f, 0f);
        sun(b, m, r * 0.985, 0.2, 1f, 0.86f, 0.58f, 0.5f, 0f);
        sun(b, m, r * 0.99, 0.6, 1f, 0.78f, 0.45f, 0.16f, 0f);
        BufferUploader.drawWithShader(b.buildOrThrow());
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
    }

    /** Sky colour for a direction at azimuth a (0 = +Z, the track's direction) and elevation e. */
    private static float[] colour(double a, double e) {
        double up = Math.max(0, Math.sin(e));
        double towardSun = Math.max(0, Math.cos(a)) * Math.exp(-Math.max(0, e - SUN_ELEV) * 3.0);
        float[] zenith = {0.46f, 0.47f, 0.66f}, horizon = {HR, HG, HB}, glow = {1.0f, 0.70f, 0.36f};
        float k = (float) Math.pow(up, 0.55);
        float[] c = new float[3];
        for (int i = 0; i < 3; i++) {
            c[i] = horizon[i] + (zenith[i] - horizon[i]) * k;
            c[i] += (glow[i] - c[i]) * (float) (towardSun * 0.55);
        }
        if (e < 0) for (int i = 0; i < 3; i++) c[i] *= (float) (1 + e * 1.2);   // below the horizon: a little darker
        return c;
    }

    private static void vert(BufferBuilder b, Matrix4f m, double r, double a, double e) {
        float[] c = colour(a, e);
        float x = (float) (Math.sin(a) * Math.cos(e) * r), y = (float) (Math.sin(e) * r), z = (float) (Math.cos(a) * Math.cos(e) * r);
        b.addVertex(m, x, y, z).setColor(c[0], c[1], c[2], 1f);
    }

    /** A disc around the sun's direction, angular radius rad, colour fading from alpha ai (centre) to ao (rim). */
    private static void sun(BufferBuilder b, Matrix4f m, double r, double rad, float cr, float cg, float cb, float ai, float ao) {
        double sx = 0, sy = Math.sin(SUN_ELEV), sz = Math.cos(SUN_ELEV);
        double ux = 1, uy = 0, uz = 0;                         // perpendicular axes on the sky
        double vx = 0, vy = Math.cos(SUN_ELEV), vz = -Math.sin(SUN_ELEV);
        int n = 40;
        for (int i = 0; i < n; i++) {
            double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
            float[] c0 = rim(sx, sy, sz, ux, uy, uz, vx, vy, vz, rad, a0, r), c1 = rim(sx, sy, sz, ux, uy, uz, vx, vy, vz, rad, a1, r);
            b.addVertex(m, (float) (sx * r), (float) (sy * r), (float) (sz * r)).setColor(cr, cg, cb, ai);
            b.addVertex(m, c0[0], c0[1], c0[2]).setColor(cr, cg, cb, ao);
            b.addVertex(m, c1[0], c1[1], c1[2]).setColor(cr, cg, cb, ao);
            b.addVertex(m, (float) (sx * r), (float) (sy * r), (float) (sz * r)).setColor(cr, cg, cb, ai);
        }
    }

    private static float[] rim(double sx, double sy, double sz, double ux, double uy, double uz, double vx, double vy, double vz, double rad, double a, double r) {
        double c = Math.cos(a) * Math.tan(rad), s = Math.sin(a) * Math.tan(rad);
        double x = sx + ux * c + vx * s, y = sy + uy * c + vy * s, z = sz + uz * c + vz * s;
        double l = Math.sqrt(x * x + y * y + z * z);
        return new float[]{(float) (x / l * r), (float) (y / l * r), (float) (z / l * r)};
    }
}
