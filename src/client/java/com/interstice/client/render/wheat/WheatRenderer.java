package com.interstice.client.render.wheat;

import com.interstice.Interstice;
import com.interstice.client.ClientState;
import com.interstice.util.Hash;
import com.interstice.world.wheat.WheatLayout;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.client.Camera;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Level 02, The Wheat Farm: the wheat, drawn by the GPU around the player.
 * <p>
 * Each chunk of field near the camera gets a static vertex buffer (built once, a couple per frame): three clumps of
 * stalks per block, each two crossed quads of one of two PixelLab clump sprites, with a hashed position, size, angle
 * and tint. Positions are stored in world coordinates (the level is bounded, floats are precise enough). The wind is
 * done entirely in the vertex shader ({@code pinwheel/shaders/program/level/wheat.vsh}): long waves travelling
 * across the field, broad gusts, each ear trembling; the bent tops catch the light. Towards the edge of the drawn
 * radius the stalks sink into the field's golden surface block, which carries the wheat on to the horizon.
 * Buffers far from the camera are freed.
 */
public final class WheatRenderer {
    private static final ResourceLocation SHADER = Interstice.id("level/wheat");
    private static final ResourceLocation TEX = Interstice.id("textures/level/wheat_stalks.png");
    /** Drawn radius (blocks) and the chunk radius of the buffers kept. */
    public static final float RADIUS = 62f;
    private static final int CHUNK_RADIUS = 4, BUILDS_PER_FRAME = 3;
    /** Clumps per block near the player and in the outer ring of chunks. */
    private static final int CLUMPS = 5, CLUMPS_FAR = 3;

    private static final Long2ObjectOpenHashMap<VertexBuffer> meshes = new Long2ObjectOpenHashMap<>();
    private static ByteBufferBuilder bytes;
    private static boolean failed;

    private WheatRenderer() {}

    public static void render(Camera camera) {
        if (!ClientState.inWheatFarm || failed) {
            if (!ClientState.inWheatFarm) clear();
            return;
        }
        try {
            Vec3 cam = camera.getPosition();
            int ccx = ((int) Math.floor(cam.x)) >> 4, ccz = ((int) Math.floor(cam.z)) >> 4;
            // build what is missing, nearest first, a couple per frame
            int built = 0;
            for (int r = 0; r <= CHUNK_RADIUS && built < BUILDS_PER_FRAME; r++)
                for (int dx = -r; dx <= r && built < BUILDS_PER_FRAME; dx++)
                    for (int dz = -r; dz <= r && built < BUILDS_PER_FRAME; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                        long key = ChunkPos.asLong(ccx + dx, ccz + dz);
                        if (meshes.containsKey(key)) continue;
                        meshes.put(key, build(ccx + dx, ccz + dz, r >= 3 ? CLUMPS_FAR : CLUMPS));
                        built++;
                    }
            // free what is far
            List<Long> drop = new ArrayList<>();
            for (long key : meshes.keySet()) {
                if (Math.max(Math.abs(ChunkPos.getX(key) - ccx), Math.abs(ChunkPos.getZ(key) - ccz)) > CHUNK_RADIUS + 1) drop.add(key);
            }
            for (long key : drop) {
                VertexBuffer vb = meshes.remove(key);
                if (vb != null) vb.close();
            }

            ShaderProgram shader = VeilRenderSystem.setShader(SHADER);
            if (shader == null) return;
            shader.bind();
            // the stalk atlas, bound by hand on unit 0 (left to the sampler binding alone, the block atlas still bound
            // there was being sampled: noise and green moss instead of the clumps)
            int texId = net.minecraft.client.Minecraft.getInstance().getTextureManager().getTexture(TEX).getId();
            com.mojang.blaze3d.platform.GlStateManager._activeTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
            com.mojang.blaze3d.platform.GlStateManager._bindTexture(texId);
            RenderSystem.setShaderTexture(0, texId);
            int loc = shader.getUniformLocation("WheatTex");
            if (loc >= 0) org.lwjgl.opengl.GL20.glUniform1i(loc, 0);
            shader.getUniformSafe("Time").setFloat((float) ((ClientState.time / 20.0) % 3600.0));
            shader.getUniformSafe("Radius").setFloat(RADIUS);
            shader.getUniformSafe("FogColor").setVector(WheatSky.HR, WheatSky.HG, WheatSky.HB);
            shader.getUniformSafe("FogEnd").setFloat(4200f);
            shader.getUniformSafe("Light").setFloat(1.0f);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.disableCull();
            for (VertexBuffer vb : meshes.values()) {
                if (vb == null) continue;
                vb.bind();
                vb.draw();
            }
            VertexBuffer.unbind();
            ShaderProgram.unbind();
            RenderSystem.enableCull();
        } catch (Throwable t) {
            failed = true;
            Interstice.LOG.error("Wheat rendering failed; disabled", t);
        }
    }

    /** One chunk's clumps (null if the chunk has no field: an empty buffer is not kept). */
    private static VertexBuffer build(int cx, int cz, int clumps) {
        if (bytes == null) bytes = new ByteBufferBuilder(512 * 1024);
        BufferBuilder b = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        int count = 0;
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = (cx << 4) + lx, z = (cz << 4) + lz;
                if (WheatLayout.inTrackStrip(x, z) || WheatLayout.onRoute(x, z) || WheatLayout.isHouse(x, z)) continue;
                int gy = WheatLayout.groundY(x, z);
                for (int k = 0; k < clumps; k++) {
                    long h = Hash.of(921, x, z, k);
                    double px = x + 0.15 + Hash.unit(h, 0) * 0.7, pz = z + 0.15 + Hash.unit(h, 1) * 0.7;
                    double height = 1.15 + Hash.unit(h, 2) * 0.5, width = 0.75 + Hash.unit(h, 3) * 0.3;
                    double ang = Hash.unit(h, 4) * Math.PI;
                    int variant = Hash.unit(h, 5) < 0.55 ? 1 : 0;
                    // gently varied straw tones, a few paler and a few riper clumps
                    float t = (float) Hash.unit(h, 6);
                    float r = 0.94f + 0.08f * t, g = 0.90f + 0.08f * t, bl = 0.86f + 0.06f * (1 - t);
                    for (int q = 0; q < 2; q++) {
                        double a = ang + q * Math.PI / 2;
                        double dx = Math.cos(a) * width / 2, dz = Math.sin(a) * width / 2;
                        float u0 = variant * 0.5f, u1 = u0 + 0.5f;
                        b.addVertex((float) (px - dx), gy, (float) (pz - dz)).setUv(u0, 1).setColor(r, g, bl, 0f);
                        b.addVertex((float) (px + dx), gy, (float) (pz + dz)).setUv(u1, 1).setColor(r, g, bl, 0f);
                        b.addVertex((float) (px + dx), (float) (gy + height), (float) (pz + dz)).setUv(u1, 0).setColor(r, g, bl, 1f);
                        b.addVertex((float) (px - dx), (float) (gy + height), (float) (pz - dz)).setUv(u0, 0).setColor(r, g, bl, 1f);
                        count++;
                    }
                }
            }
        }
        MeshData mesh = b.build();
        if (mesh == null || count == 0) {
            if (mesh != null) mesh.close();
            return null;
        }
        VertexBuffer vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
        vb.bind();
        vb.upload(mesh);
        VertexBuffer.unbind();
        return vb;
    }

    public static void clear() {
        for (VertexBuffer vb : meshes.values()) if (vb != null) vb.close();
        meshes.clear();
    }
}
