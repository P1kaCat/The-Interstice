package com.interstice.client.render.atmosphere;

import com.interstice.client.ClientState;
import com.interstice.util.Hash;
import com.interstice.world.IntersticeLayout;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Renders the part of the world that is too big to exist as blocks: the two
 * walls continuing from the top of the block world (y=300) to y=1400 and
 * beyond into the dark, with panel variation and rare distant lit openings.
 * <p>
 * The geometry is drawn during the sky pass <b>with depth writes enabled</b>,
 * at its real world position, so terrain correctly occludes it and Veil's
 * depth-aware fog shader fogs it by true distance. That is what makes the
 * walls read as one continuous, impossibly tall structure.
 */
public final class IntersticeSkyRenderer implements DimensionRenderingRegistry.SkyRenderer {
    private static final int Z_STEP = 24;
    private static final int Y_STEP = 64;

    public static int quadsDrawn;

    @Override
    public void render(WorldRenderContext ctx) {
        IntersticeLayout L = ClientState.layout;
        // Corridor rework: no fake walls above y=300 any more; the walls disappear into the black fog. The sky
        // itself stays the black clear colour. The old continuation is kept behind a setting for comparison.
        if (L == null || !com.interstice.client.config.ClientConfig.INSTANCE.legacyWallContinuation) {
            quadsDrawn = 0;
            return;
        }
        Vec3 cam = ctx.camera().getPosition();
        float far = Math.min(ctx.gameRenderer().getDepthFar() * 0.98f, 1400f);

        PoseStack ps = new PoseStack();
        ps.mulPose(ctx.positionMatrix());
        Matrix4f m = ps.last().pose();

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder b = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        quadsDrawn = 0;

        int z0 = (int) Math.floor((cam.z - far) / Z_STEP) * Z_STEP;
        int z1 = (int) Math.ceil((cam.z + far) / Z_STEP) * Z_STEP;
        int yTop = (int) Math.min(IntersticeLayout.VIRTUAL_TOP + 400, cam.y + far);
        float emer = AtmosphereController.emergency;
        long seed = L.sub(801);

        for (int side = -1; side <= 1; side += 2) {
            for (int z = z0; z < z1; z += Z_STEP) {
                double xa = L.faceX(side, z) - cam.x, xb = L.faceX(side, z + Z_STEP) - cam.x;
                for (int y = IntersticeLayout.WALL_TOP - 1; y < yTop; y += Y_STEP) {
                    int y2 = Math.min(y + Y_STEP, yTop);
                    double ya = y - cam.y, yb = y2 - cam.y;
                    long h = Hash.of(seed, side, z / Z_STEP, y / Y_STEP);
                    float v = 0.055f + (float) Hash.unit(h) * 0.03f;
                    if ((z / Z_STEP + y / Y_STEP) % 5 == 0) v *= 0.75f; // panel seams
                    float r = v, g = v * 1.02f, bl = v * 1.08f;
                    quad(b, m, xa, ya, z - cam.z, xb, yb, z + Z_STEP - cam.z, r, g, bl);
                    // rare lit openings, far up: the only hint something else is there
                    if (Hash.unit(h, 3) < 0.035) {
                        double wy = ya + 20 + Hash.unit(h, 4) * 30;
                        double wz = z + 6 + Hash.unit(h, 5) * 10 - cam.z;
                        double inset = -side * 0.15;
                        boolean red = Hash.unit(h, 6) < 0.25 || emer > 0.5f && Hash.unit(h, 7) < 0.5;
                        float lr = red ? 0.9f : 0.55f, lg = red ? 0.12f : 0.6f, lb = red ? 0.08f : 0.7f;
                        double wx = (xa + xb) * 0.5 + inset;
                        quad(b, m, wx, wy, wz, wx, wy + 3, wz + 2, lr, lg, lb);
                    }
                    quadsDrawn++;
                }
            }
        }
        MeshData mesh = b.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
        RenderSystem.enableCull();
        RenderSystem.depthMask(false);
    }

    private static void quad(BufferBuilder b, Matrix4f m, double xa, double ya, double za, double xb, double yb, double zb, float r, float g, float bl) {
        b.addVertex(m, (float) xa, (float) ya, (float) za).setColor(r, g, bl, 1f);
        b.addVertex(m, (float) xb, (float) ya, (float) zb).setColor(r, g, bl, 1f);
        b.addVertex(m, (float) xb, (float) yb, (float) zb).setColor(r * 0.92f, g * 0.92f, bl * 0.92f, 1f);
        b.addVertex(m, (float) xa, (float) yb, (float) za).setColor(r * 0.92f, g * 0.92f, bl * 0.92f, 1f);
    }
}
