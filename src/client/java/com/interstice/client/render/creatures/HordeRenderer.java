package com.interstice.client.render.creatures;

import com.interstice.Interstice;
import com.interstice.client.ClientState;
import com.interstice.client.config.ClientConfig;
import com.interstice.client.config.GraphicsPreset;
import com.interstice.event.stampede.StampedePhase;
import com.interstice.util.Hash;
import com.interstice.world.IntersticeLayout;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import foundry.veil.api.client.render.CullFrustum;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Client-side visual horde (far and mid distance tiers of the hybrid
 * simulation; the near tier is real server entities).
 * <p>
 * Every instance is a closed-form function of (stampede seed, index, time):
 * lane height, wall side, speed, position in the pack and whether/when it loses
 * its grip. Nothing is simulated per tick and nothing is networked, so
 * thousands of creatures cost only arithmetic plus the vertices actually drawn.
 * <ul>
 *   <li>Far: wall-aligned animated silhouette quads (4-frame sprite).</li>
 *   <li>Mid: the articulated Adherent model posed with a run cycle,
 *       up to the preset's mid budget, nearest first by construction.</li>
 * </ul>
 * Outside the Stampede, a sparse population of distant crawlers lives on the
 * walls beyond the range of real creatures; they freeze during premonitions and
 * scatter during quakes.
 */
public final class HordeRenderer {
    public static final ResourceLocation SPRITE = Interstice.id("textures/entity/horde_sprite.png");

    private static AdherentModel model;
    private static long hordeKey = Long.MIN_VALUE;
    /** Z reference the pack is built around (the listener's position when the horde began). */
    public static double refZ;

    public static int farDrawn, midDrawn, ambientDrawn, simulated;

    private HordeRenderer() {}

    private static AdherentModel model() {
        if (model == null) model = new AdherentModel(Minecraft.getInstance().getEntityModels().bakeLayer(AdherentModel.LAYER));
        return model;
    }

    public static boolean hordeVisible() {
        return ClientState.hordeStart != Long.MIN_VALUE && ClientState.phase.between(StampedePhase.HORDE_APPROACH, StampedePhase.GROUND_SEARCH);
    }

    public static void render(ClientLevel level, Camera camera, MultiBufferSource.BufferSource buffers, Object frustum, float partialTick) {
        farDrawn = midDrawn = ambientDrawn = simulated = 0;
        IntersticeLayout L = ClientState.layout;
        if (L == null) return;
        Vec3 cam = camera.getPosition();
        CullFrustum cull = frustum instanceof CullFrustum cf ? cf : null;
        GraphicsPreset preset = ClientConfig.INSTANCE.preset;
        PoseStack ps = new PoseStack();

        if (hordeVisible()) {
            if (hordeKey != ClientState.hordeStart) {
                hordeKey = ClientState.hordeStart;
                refZ = cam.z;
            }
            // Pass 1: evaluate instances, queue far/mid. Pass 2: draw each render type in turn.
            // (Requesting a second render type from the BufferSource ends the previous batch.)
            midCount = farCount = 0;
            collectHorde(level, L, cam, cull, preset);
            VertexConsumer far = buffers.getBuffer(RenderType.entityCutoutNoCull(SPRITE));
            for (int i = 0; i < farCount; i++) {
                int o = i * FAR_STRIDE;
                quadOnWall(far, (int) farQ[o], (int) farQ[o + 1], farQ[o + 2], farQ[o + 3], farQ[o + 4], (float) farQ[o + 5], (int) farQ[o + 6],
                        (float) farQ[o + 7], LightTexture.pack(2, 0), farQ[o + 8] > 0);
            }
            buffers.endBatch(RenderType.entityCutoutNoCull(SPRITE));
            if (midCount > 0) {
                VertexConsumer mid = buffers.getBuffer(RenderType.entityCutoutNoCull(AdherentRenderer.TEXTURE));
                for (int i = 0; i < midCount; i++) {
                    int o = i * MID_STRIDE;
                    long ms = (long) ((midQ[o + 6] / (2 * Math.PI)) * AdherentAnimations.RUN.lengthInSeconds() * 1000 + midQ[o + 7] * 700);
                    drawModel(ps, mid, (int) midQ[o], (float) midQ[o + 1], midQ[o + 2], midQ[o + 3], midQ[o + 4], (int) midQ[o + 5], (float) midQ[o + 8], ms);
                }
                buffers.endBatch(RenderType.entityCutoutNoCull(AdherentRenderer.TEXTURE));
            }
            farDrawn = farCount;
            midDrawn = midCount;
        }
        drawAmbient(level, L, cam, cull, preset, buffers);
        buffers.endBatch(RenderType.entityCutoutNoCull(SPRITE));
        buffers.endBatch(RenderType.entityCutoutNoCull(AdherentRenderer.TEXTURE));
    }

    private static final int FAR_STRIDE = 9, MID_STRIDE = 9;
    private static double[] farQ = new double[FAR_STRIDE * 512];
    private static double[] midQ = new double[MID_STRIDE * 64];
    private static int farCount, midCount;

    private static void collectHorde(ClientLevel level, IntersticeLayout L, Vec3 cam, CullFrustum cull, GraphicsPreset preset) {
        double t = ClientState.time - ClientState.hordeStart;
        int dir = ClientState.stampedeDirection;
        long seed = ClientState.stampedeSeed;
        int n = (int) (preset.hordeBudget * Mth.clamp(ClientState.stampedeIntensity, 0.3f, 1.5f));
        int midBudget = preset.hordeMidBudget;
        boolean falling = ClientState.phase.atLeast(StampedePhase.HORDE_PASSAGE);
        for (int i = 0; i < n; i++) {
            long h = Hash.of(seed, i);
            int side = Hash.unit(h, 1) < 0.5 ? -1 : 1;
            double lane = IntersticeLayout.FLOOR_Y + 5 + Math.pow(Hash.unit(h, 2), 2.4) * 620;
            double v = 0.62 + Hash.unit(h, 3) * 0.45;
            double off = Math.pow(Hash.unit(h, 4), 0.85) * 640;
            double z = refZ + dir * ((t - 260) * v - off);
            if (Math.abs(z - cam.z) > 430) continue;
            simulated++;
            double y = lane + Math.sin(t * 0.3 + i) * 0.6;
            double x = L.faceX(side, z) - side * 0.35;
            double fallOut = 0;
            if (falling && Hash.unit(h, 5) < 0.025) {
                double tf = 300 + Hash.unit(h, 6) * 480;
                if (t > tf) {
                    double dtf = t - tf;
                    y -= 0.04 * dtf * dtf;
                    fallOut = Math.min(dtf * 0.3, 14);
                    x -= side * fallOut;
                    if (y < IntersticeLayout.FLOOR_Y) continue;
                }
            }
            double dx = x - cam.x, dy = y - cam.y, dz = z - cam.z;
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 < 16 * 16) continue; // the near tier is real entities
            if (cull != null && !cull.testSphere(x, y, z, 2f)) continue;
            float phase = (float) (t * v * 1.1 + i * 1.7);
            if (d2 < 64 * 64 && midCount < midBudget) {
                if ((midCount + 1) * MID_STRIDE > midQ.length) midQ = java.util.Arrays.copyOf(midQ, midQ.length * 2);
                int o = midCount++ * MID_STRIDE;
                midQ[o] = side;
                midQ[o + 1] = dir > 0 ? 0f : Mth.PI;
                midQ[o + 2] = dx + side * 0.1;
                midQ[o + 3] = dy;
                midQ[o + 4] = dz;
                midQ[o + 5] = light(level, x, y, z);
                midQ[o + 6] = fallOut > 0 ? phase * 2.5f : phase;
                midQ[o + 7] = Hash.unit(h, 7);
                midQ[o + 8] = fallOut > 0 ? t * 0.3 : 0;
            } else {
                if ((farCount + 1) * FAR_STRIDE > farQ.length) farQ = java.util.Arrays.copyOf(farQ, farQ.length * 2);
                int o = farCount++ * FAR_STRIDE;
                farQ[o] = side;
                farQ[o + 1] = dir;
                farQ[o + 2] = dx;
                farQ[o + 3] = dy;
                farQ[o + 4] = dz;
                farQ[o + 5] = 1.7 + Hash.unit(h, 9) * 0.9;
                farQ[o + 6] = ((int) (phase * 0.9)) & 3;
                farQ[o + 7] = 0.5 + Hash.unit(h, 8) * 0.25;
                farQ[o + 8] = fallOut;
            }
        }
    }

    private static void drawAmbient(ClientLevel level, IntersticeLayout L, Vec3 cam, CullFrustum cull, GraphicsPreset preset, MultiBufferSource.BufferSource buffers) {
        if (hordeVisible() && ClientState.phase.between(StampedePhase.HORDE_APPROACH, StampedePhase.CREATURE_FALLS)) return;
        VertexConsumer far = buffers.getBuffer(RenderType.entityCutoutNoCull(SPRITE));
        double t = ClientState.time;
        int perCell = Math.max(1, preset.hordeBudget / 300);
        int c0 = (int) Math.floor((cam.z - 300) / 160), c1 = (int) Math.floor((cam.z + 300) / 160);
        boolean freeze = ClientState.premonition > 0.3f || ClientState.chainFreeze > 0.5f;
        float scatter = ClientState.quake;
        for (int c = c0; c <= c1; c++) {
            long hc = Hash.of(L.sub(951), c);
            int count = perCell + (int) (Hash.unit(hc, 0) * 3);
            for (int i = 0; i < count; i++) {
                long h = Hash.of(hc, i + 1);
                int side = Hash.unit(h, 1) < 0.5 ? -1 : 1;
                double baseZ = c * 160 + Hash.unit(h, 2) * 160;
                double baseY = IntersticeLayout.FLOOR_Y + 30 + Hash.unit(h, 3) * 420;
                double speed = freeze ? 0 : 0.004 + Hash.unit(h, 4) * 0.006;
                double ang = t * speed + Hash.unit(h, 5) * 6.28;
                double z = baseZ + Math.sin(ang) * 14;
                double y = baseY + Math.sin(ang * 0.7) * 9 + scatter * 40 * Hash.unit(h, 6);
                double x = L.faceX(side, z) - side * 0.35;
                double dx = x - cam.x, dy = y - cam.y, dz = z - cam.z;
                double d2 = dx * dx + dy * dy + dz * dz;
                if (d2 < 55 * 55 || d2 > 320 * 320) continue;
                if (cull != null && !cull.testSphere(x, y, z, 2f)) continue;
                int frame = freeze ? 0 : ((int) (ang * 40)) & 3;
                quadOnWall(far, side, Math.cos(ang) > 0 ? 1 : -1, dx, dy, dz, 1.6f, frame, 0.55f, LightTexture.pack(1, 0), false);
                ambientDrawn++;
            }
        }
    }

    private static int light(ClientLevel level, double x, double y, double z) {
        int block = LightTexture.block(LevelRenderer.getLightColor(level, BlockPos.containing(x, y, z)));
        return LightTexture.pack(Math.max(2, block), 0);
    }

    /** A silhouette quad lying on the wall plane, head pointing along the run direction. */
    private static void quadOnWall(VertexConsumer vc, int side, int dir, double cx, double cy, double cz, float size, int frame, float shade,
                                   int light, boolean tumbling) {
        float hz = size, hy = size * 0.62f;
        float u0 = frame * 0.25f, u1 = u0 + 0.25f;
        if (dir < 0) { float tmp = u0; u0 = u1; u1 = tmp; }
        float x = (float) cx, y = (float) cy, z = (float) cz;
        float nx = -side;
        int col = (int) (shade * 255);
        int ov = OverlayTexture.NO_OVERLAY;
        if (tumbling) { hy = size; hz = size * 0.62f; }
        vc.addVertex(x, y - hy, z - hz).setColor(col, col, col, 255).setUv(u0, 1).setOverlay(ov).setLight(light).setNormal(nx, 0, 0);
        vc.addVertex(x, y - hy, z + hz).setColor(col, col, col, 255).setUv(u1, 1).setOverlay(ov).setLight(light).setNormal(nx, 0, 0);
        vc.addVertex(x, y + hy, z + hz).setColor(col, col, col, 255).setUv(u1, 0).setOverlay(ov).setLight(light).setNormal(nx, 0, 0);
        vc.addVertex(x, y + hy, z - hz).setColor(col, col, col, 255).setUv(u0, 0).setOverlay(ov).setLight(light).setNormal(nx, 0, 0);
    }

    private static void drawModel(PoseStack ps, VertexConsumer vc, int side, float heading, double dx, double dy, double dz, int light, float tumble, long ms) {
        AdherentModel m = model();
        m.poseRun(Math.max(0, ms));
        ps.pushPose();
        ps.translate(dx, dy, dz);
        Vector3f up = new Vector3f(-side, 0, 0);
        Vector3f fwd = new Vector3f(0, Mth.sin(heading), Mth.cos(heading));
        Vector3f back = new Vector3f(fwd).negate();
        Vector3f right = new Vector3f(up).cross(back);
        ps.translate(side * 0.42f, 0.9f, 0);
        ps.mulPose(new Quaternionf().setFromNormalized(new Matrix3f(right, up, back)));
        if (tumble != 0) ps.mulPose(new Quaternionf().rotateX(tumble));
        ps.translate(0, -0.15f, 0);
        ps.scale(AdherentAnimator.SCALE, AdherentAnimator.SCALE, AdherentAnimator.SCALE);
        ps.scale(-1, -1, 1);
        ps.translate(0, -1.501f, 0);
        m.renderToBuffer(ps, vc, light, OverlayTexture.NO_OVERLAY);
        ps.popPose();
    }
}
