package com.interstice.client.render.chains;

import com.interstice.Interstice;
import com.interstice.client.ClientState;
import com.interstice.client.config.ClientConfig;
import com.interstice.client.config.GraphicsPreset;
import com.mojang.blaze3d.vertex.VertexConsumer;
import foundry.veil.api.client.render.CullFrustum;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Animates and draws the giant chains.
 * <p>
 * Motion model per chain: a slow deterministic pendulum (shared time base, so
 * every client sees the same swing), a damped impulse oscillator excited by
 * events (impacts, earthquakes, shudders), and a high-frequency transverse
 * vibration driven by the global "chain excitation" signal. During the silence
 * before a Stampede, a freeze factor pulls all motion to rest.
 * <p>
 * Rendering: arc-length link placement along the evaluated curve, distance
 * LOD between four reusable link meshes, Veil sphere frustum culling per link,
 * a draw-distance limit from the graphics preset and alternating 90&deg; link roll.
 * Vertices are transformed on the CPU into camera-relative space.
 */
public final class ChainRenderer {
    public static final ResourceLocation TEXTURE = Interstice.id("textures/entity/chain_link.png");

    private static ChainField field;
    private static final List<ChainField.ChainDef> visible = new ArrayList<>();
    private static double lastTime = Double.NaN;
    /** The giant chains are heavy: everything they do happens at half the speed the motion model was tuned at. */
    private static final float SWING_SPEED = 0.5f;
    /** Rusted iron in the dark: the links are drawn at 62% of their texture's brightness. */
    private static final int TINT = 158;
    private static final RandomSource rng = RandomSource.create();

    // stats
    public static int chainsVisible, linksDrawn;

    // nearest audible chain point (for the audio director)
    public static double nearX, nearY, nearZ, nearDist = Double.MAX_VALUE;
    public static float nearEnergy;

    // scratch
    private static final int MAX_SAMPLES = 260;
    private static final double[] sx = new double[MAX_SAMPLES], sy = new double[MAX_SAMPLES], sz = new double[MAX_SAMPLES], sa = new double[MAX_SAMPLES];
    private static double ox, oy, oz;

    private ChainRenderer() {}

    public static ChainField field() {
        if (ClientState.layout == null) return null;
        if (field == null || field.layout() != ClientState.layout) field = new ChainField(ClientState.layout);
        return field;
    }

    /** Applies an impulse to chains near a world position (impacts, landings). */
    public static void kick(double x, double y, double z, float strength) {
        ChainField f = field();
        if (f == null) return;
        List<ChainField.ChainDef> tmp = new ArrayList<>();
        f.collect(z, 260, tmp);
        for (ChainField.ChainDef c : tmp) {
            double dz = Math.abs((c.az + c.bz) * 0.5 - z);
            float k = strength / (1f + (float) dz / 60f);
            c.impulseVel += k * (rng.nextBoolean() ? 1 : -1);
            c.sideVel += k * 0.6f * (rng.nextBoolean() ? 1 : -1);
            c.vibration = Math.min(1.2f, c.vibration + k * 0.5f);
        }
    }

    private static void simulate(double timeTicks) {
        double dt = Double.isNaN(lastTime) ? 0 : Mth.clamp((timeTicks - lastTime) / 20.0, 0, 0.1);
        lastTime = timeTicks;
        float freeze = ClientState.chainFreeze;
        float quake = ClientState.quake;
        float excitation = ClientState.chainExcitation;
        for (ChainField.ChainDef c : visible) {
            float target = excitation * 0.65f;
            c.vibration += (float) ((target - c.vibration) * Math.min(1, dt * (target > c.vibration ? 3 : 0.6)));
            float w = c.omega * 2.2f * SWING_SPEED;
            float damp = 0.18f + freeze * 3f;
            c.impulseVel += (float) ((-w * w * c.impulse - 2 * damp * w * c.impulseVel) * dt);
            c.impulse += (float) (c.impulseVel * dt);
            c.sideVel += (float) ((-w * w * c.sideImpulse - 2 * damp * w * c.sideVel) * dt);
            c.sideImpulse += (float) (c.sideVel * dt);
            if (quake > 0.05f) {
                c.impulseVel += (float) (rng.nextGaussian() * quake * 1.4 * dt);
                c.sideVel += (float) (rng.nextGaussian() * quake * 1.0 * dt);
            }
        }
    }

    private static final List<ChainField.ChainDef> nearPlayer = new ArrayList<>();
    private static long lastClank;

    /**
     * Solid chains: the local player cannot walk through a giant chain. Every client tick the chains near the player
     * are evaluated (same curve as drawn, swing included) at the player's height and the player is pushed out of
     * any link it overlaps, losing the speed it had towards the chain; bumping into one rings it.
     */
    public static void collide(net.minecraft.client.player.LocalPlayer p) {
        ChainField f = field();
        if (f == null || p == null || p.isSpectator()) return;
        nearPlayer.clear();
        f.collect(p.getZ(), 24, nearPlayer);
        double t = ClientState.time / 20.0;
        double y0 = p.getY(), y1 = p.getY() + p.getBbHeight();
        double half = p.getBbWidth() / 2;
        for (ChainField.ChainDef c : nearPlayer) {
            double r = ChainMesh.LINK_WIDTH * 0.5 * c.scale * 0.85 + half;
            boolean vertical = c.kind != ChainField.Kind.SPAN;
            if (vertical) {
                double len = c.ay - c.by;
                if (y1 < c.by - 2 || y0 > c.ay || len <= 0) continue;
                if (Math.abs(c.ax - p.getX()) > 12 || Math.abs(c.az - p.getZ()) > 12) continue;
                for (double y = y0; y <= y1 + 1e-3; y += 0.6) {
                    double s = Mth.clamp((c.ay - y) / len, 0, 1);
                    eval(c, s, t);
                    if (oy - 1.8 * c.scale > y1 || oy + 1.8 * c.scale < y0) continue;
                    pushOut(p, ox, oz, r);
                }
            } else {
                if (Math.min(c.ay, c.by) - c.sag - 4 > y1) continue;
                for (int i = 0; i <= 96; i++) {
                    eval(c, i / 96.0, t);
                    if (oy - 1.0 * c.scale > y1 || oy + 1.0 * c.scale < y0) continue;
                    pushOut(p, ox, oz, r);
                }
            }
        }
    }

    private static void pushOut(net.minecraft.client.player.LocalPlayer p, double cx, double cz, double r) {
        double dx = p.getX() - cx, dz = p.getZ() - cz;
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d >= r) return;
        if (d < 1e-4) { dx = 1; dz = 0; d = 1; }
        double nx = dx / d, nz = dz / d;
        p.setPos(cx + nx * r, p.getY(), cz + nz * r);
        Vec3 v = p.getDeltaMovement();
        double into = v.x * nx + v.z * nz;
        if (into < 0) {
            p.setDeltaMovement(v.x - nx * into, v.y, v.z - nz * into);
            long now = System.currentTimeMillis();
            if (-into > 0.08 && now - lastClank > 600) {
                lastClank = now;
                p.level().playLocalSound(cx, p.getY() + 1, cz, com.interstice.registry.SpeciesSounds.METAL_CONTACT,
                        net.minecraft.sounds.SoundSource.BLOCKS, 0.8f, 0.5f, false);
            }
        }
    }

    /** Evaluates the chain centre line at s in [0,1] into (ox, oy, oz). */
    private static void eval(ChainField.ChainDef c, double s, double t) {
        float freeze = 1f - ClientState.chainFreeze;
        switch (c.kind) {
            case SPAN -> {
                double dx = c.bx - c.ax, dy = c.by - c.ay, dz = c.bz - c.az;
                // horizontal perpendicular of the span
                double nx = -dz, nz = dx;
                double nl = Math.sqrt(nx * nx + nz * nz);
                nx /= nl;
                nz /= nl;
                double theta = (c.swingAmp * Math.sin(c.omega * SWING_SPEED * t + c.phase) + c.impulse * 0.25) * freeze;
                double sag = c.sag * 4 * s * (1 - s) * (1 - 0.06 * c.vibration);
                double vib = c.vibration * 1.1 * Math.sin(Math.PI * s) * Math.sin(6 * t + s * 11);
                double cos = Math.cos(theta), sin = Math.sin(theta);
                ox = c.ax + dx * s + nx * (sin * sag + vib);
                oy = c.ay + dy * s - cos * sag;
                oz = c.az + dz * s + nz * (sin * sag + vib);
            }
            case HANG, ANCHOR, BROKEN -> {
                double len = c.ay - c.by;
                double f = c.kind == ChainField.Kind.ANCHOR ? 0 : Math.pow(s, c.kind == ChainField.Kind.BROKEN ? 1.15 : 1.4);
                // the Stampede swings the hanging chains much harder
                double amp = c.swingAmp * (1 + 2.2 * ClientState.hordeActive + 0.8 * ClientState.quake);
                double ddx = (amp * Math.sin(c.omega * SWING_SPEED * t + c.phase) + c.impulse * 2.2) * freeze;
                double ddz = (amp * 0.6 * Math.cos(c.omega * SWING_SPEED * 0.71 * t + c.phase * 1.3) + c.sideImpulse * 2.2) * freeze;
                double fv = Math.sin(Math.PI * Math.min(1, s * 1.05));
                double vibAmp = c.vibration * (c.kind == ChainField.Kind.ANCHOR ? 0.9 : 0.6);
                ox = c.ax + ddx * f + Math.sin(8 * t + s * 37) * vibAmp * fv;
                oy = c.ay - len * s;
                oz = c.az + ddz * f + Math.cos(6.5 * t + s * 29) * vibAmp * fv;
            }
        }
    }

    public static void render(ClientLevel level, Camera camera, MultiBufferSource.BufferSource buffers, Object frustum) {
        chainsVisible = 0;
        linksDrawn = 0;
        nearDist = Double.MAX_VALUE;
        ChainField f = field();
        if (f == null || !ClientState.inInterstice) return;
        ClientConfig cfg = ClientConfig.INSTANCE;
        GraphicsPreset preset = cfg.preset;
        double range = preset.chainRange;
        Vec3 cam = camera.getPosition();

        visible.clear();
        f.collect(cam.z, range, visible);
        double timeTicks = ClientState.time;
        simulate(timeTicks);
        double t = timeTicks / 20.0;

        CullFrustum cull = frustum instanceof CullFrustum cf ? cf : null;
        VertexConsumer vc = buffers.getBuffer(RenderType.entitySolid(TEXTURE));
        int baseLod = cfg.chainLod();
        BlockPos.MutableBlockPos lp = new BlockPos.MutableBlockPos();

        for (ChainField.ChainDef c : visible) {
            double len = c.length();
            int samples = Mth.clamp((int) (len / 5), 24, MAX_SAMPLES);
            // sample curve + cumulative arc length
            double acc = 0;
            boolean anyNear = false;
            for (int i = 0; i < samples; i++) {
                eval(c, i / (double) (samples - 1), t);
                sx[i] = ox; sy[i] = oy; sz[i] = oz;
                if (i > 0) {
                    double ddx = sx[i] - sx[i - 1], ddy = sy[i] - sy[i - 1], ddz = sz[i] - sz[i - 1];
                    acc += Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz);
                }
                sa[i] = acc;
                double d2 = (ox - cam.x) * (ox - cam.x) + (oy - cam.y) * (oy - cam.y) + (oz - cam.z) * (oz - cam.z);
                if (d2 < range * range) anyNear = true;
                if (d2 < nearDist * nearDist) {
                    nearDist = Math.sqrt(d2);
                    nearX = ox; nearY = oy; nearZ = oz;
                    nearEnergy = c.vibration + Math.abs(c.impulseVel) * 2 + Math.abs(c.sideVel);
                }
            }
            if (!anyNear) continue;
            chainsVisible++;

            float scale = c.scale;
            double pitch = ChainMesh.LINK_LENGTH * scale * 0.74;
            int linkCount = (int) (acc / pitch);
            int seg = 0;
            for (int li = 0; li <= linkCount; li++) {
                double a = li * pitch;
                while (seg < samples - 2 && sa[seg + 1] < a) seg++;
                double segLen = sa[seg + 1] - sa[seg];
                double u = segLen > 1e-6 ? (a - sa[seg]) / segLen : 0;
                double px = Mth.lerp(u, sx[seg], sx[seg + 1]);
                double py = Mth.lerp(u, sy[seg], sy[seg + 1]);
                double pz = Mth.lerp(u, sz[seg], sz[seg + 1]);
                double dx = px - cam.x, dy = py - cam.y, dz = pz - cam.z;
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (dist > range) continue;
                if (cull != null && !cull.testSphere(px, py, pz, 2.2f * scale)) continue;

                double tx = sx[seg + 1] - sx[seg], ty = sy[seg + 1] - sy[seg], tz = sz[seg + 1] - sz[seg];
                double tl = Math.sqrt(tx * tx + ty * ty + tz * tz);
                if (tl < 1e-6) continue;
                tx /= tl; ty /= tl; tz /= tl;

                int lod = dist < 45 ? baseLod : dist < 120 ? Math.max(0, baseLod - 1) : 0;
                lp.set(px, py, pz);
                int block = LightTexture.block(LevelRenderer.getLightColor(level, lp));
                int light = LightTexture.pack(Math.max(1, block), 0);
                drawLink(vc, ChainMesh.lod(lod), px - cam.x, py - cam.y, pz - cam.z, tx, ty, tz, (li & 1) == 1, li * 0.37f + c.id, scale, light);
                linksDrawn++;
            }
            if (c.weight && c.kind == ChainField.Kind.HANG) {
                double px = sx[samples - 1], py = sy[samples - 1], pz = sz[samples - 1];
                if (Math.abs(py - cam.y) < range && (cull == null || cull.testSphere(px, py - 2, pz, 3f))) {
                    lp.set(px, py - 2, pz);
                    int light = LightTexture.pack(Math.max(1, LightTexture.block(LevelRenderer.getLightColor(level, lp))), 0);
                    drawBox(vc, px - cam.x, py - 2.4 * scale - cam.y, pz - cam.z, 1.5 * scale, 2.2 * scale, 1.5 * scale, light);
                }
            }
        }
        buffers.endBatch(RenderType.entitySolid(TEXTURE));
    }

    private static void drawLink(VertexConsumer vc, ChainMesh mesh, double cx, double cy, double cz,
                                 double tx, double ty, double tz, boolean roll, float twistSeed, float scale, int light) {
        // basis: X = tangent, Y/Z perpendicular, rolled 90 degrees on alternate links
        double rx = 0, ry = 1, rz = 0;
        if (Math.abs(ty) > 0.9) { rx = 1; ry = 0; }
        double yx = ty * rz - tz * ry, yy = tz * rx - tx * rz, yz = tx * ry - ty * rx;
        double yl = Math.sqrt(yx * yx + yy * yy + yz * yz);
        yx /= yl; yy /= yl; yz /= yl;
        double zx = ty * yz - tz * yy, zy = tz * yx - tx * yz, zz = tx * yy - ty * yx;
        double twist = Math.sin(twistSeed * 12.9898) * 0.18 + (roll ? Math.PI / 2 : 0);
        double c = Math.cos(twist), s = Math.sin(twist);
        double Yx = yx * c + zx * s, Yy = yy * c + zy * s, Yz = yz * c + zz * s;
        double Zx = zx * c - yx * s, Zy = zy * c - yy * s, Zz = zz * c - yz * s;

        float[] d = mesh.data;
        int ov = OverlayTexture.NO_OVERLAY;
        for (int i = 0; i < d.length; i += 8) {
            float lx = d[i] * scale, ly = d[i + 1] * scale, lz = d[i + 2] * scale;
            float x = (float) (cx + tx * lx + Yx * ly + Zx * lz);
            float y = (float) (cy + ty * lx + Yy * ly + Zy * lz);
            float z = (float) (cz + tz * lx + Yz * ly + Zz * lz);
            float nx = (float) (tx * d[i + 3] + Yx * d[i + 4] + Zx * d[i + 5]);
            float ny = (float) (ty * d[i + 3] + Yy * d[i + 4] + Zy * d[i + 5]);
            float nz = (float) (tz * d[i + 3] + Yz * d[i + 4] + Zz * d[i + 5]);
            vc.addVertex(x, y, z).setColor(TINT, TINT, TINT, 255).setUv(d[i + 6], d[i + 7]).setOverlay(ov).setLight(light).setNormal(nx, ny, nz);
        }
    }

    private static void drawBox(VertexConsumer vc, double cx, double cy, double cz, double hx, double hy, double hz, int light) {
        float x0 = (float) (cx - hx), x1 = (float) (cx + hx), y0 = (float) (cy - hy), y1 = (float) (cy + hy), z0 = (float) (cz - hz), z1 = (float) (cz + hz);
        int ov = OverlayTexture.NO_OVERLAY;
        float[][] faces = {
                {x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 1},
                {x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0, 0, -1},
                {x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 1, 0, 0},
                {x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1, 0, 0},
                {x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, 1, 0},
                {x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0},
        };
        float[][] uv = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
        for (float[] f : faces) {
            for (int v = 0; v < 4; v++) {
                vc.addVertex(f[v * 3], f[v * 3 + 1], f[v * 3 + 2]).setColor(150, 140, 130, 255).setUv(uv[v][0] * 2, uv[v][1] * 2)
                        .setOverlay(ov).setLight(light).setNormal(f[12], f[13], f[14]);
            }
        }
    }
}
