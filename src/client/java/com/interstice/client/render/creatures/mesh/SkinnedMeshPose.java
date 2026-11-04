package com.interstice.client.render.creatures.mesh;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;

/**
 * Poses and skins a {@link SkinnedMeshAsset} on the CPU, then emits it through a vanilla
 * {@link VertexConsumer} (so vanilla lighting, overlay and Veil's deferred lights apply).
 * <p>
 * Pipeline per frame: reset pose, add weighted animation clips (additive offsets from the rest
 * pose; Catmull-Rom sampling identical to the authoring tool's validation), compute bone
 * matrices (G = G_parent * T(pivot - parentPivot + pos) * Rz * Ry * Rx), skin each vertex with up to
 * three weighted bones (linear blend skinning), then convert Blockbench space (Y up, ground 0) to
 * Minecraft entity model space (X mirrored, Y down, ground at 24 px) and emit triangles.
 */
public final class SkinnedMeshPose {
    public static long nanosLastSecond, nanosAccum, verticesLastSecond, verticesAccum;
    private static long second;

    private final SkinnedMeshAsset asset;
    private final float[] rot, pos;
    private final Matrix4f[] global, skin;
    private final float[] sm;          // skin matrices flattened, 12 floats per bone (3x4)
    private float[] skinned = new float[0], skinnedN = new float[0];
    private int lodSkinned = -1;

    public SkinnedMeshPose(SkinnedMeshAsset asset) {
        this.asset = asset;
        int n = asset.boneCount();
        rot = new float[n * 3];
        pos = new float[n * 3];
        global = new Matrix4f[n];
        skin = new Matrix4f[n];
        for (int i = 0; i < n; i++) {
            global[i] = new Matrix4f();
            skin[i] = new Matrix4f();
        }
        sm = new float[n * 12];
    }

    public SkinnedMeshAsset asset() {
        return asset;
    }

    public void reset() {
        java.util.Arrays.fill(rot, 0f);
        java.util.Arrays.fill(pos, 0f);
    }

    /** Adds a clip at {@code seconds} with the given weight (offsets are additive from the rest pose). */
    public void add(SkinnedMeshAsset.Clip clip, float seconds, float weight) {
        if (clip == null || weight <= 0.001f) return;
        float t = clip.loop ? (float) (seconds - Math.floor(seconds / clip.length) * clip.length)
                : Math.max(0f, Math.min(clip.length, seconds));
        for (SkinnedMeshAsset.Channel ch : clip.channels) {
            float[] dst = ch.rotation ? rot : pos;
            int o = ch.bone * 3;
            for (int k = 0; k < 3; k++) dst[o + k] += weight * sample(ch, t, k, clip.loop);
        }
    }

    public void addRotation(int bone, float x, float y, float z) {
        rot[bone * 3] += x;
        rot[bone * 3 + 1] += y;
        rot[bone * 3 + 2] += z;
    }

    public int bone(String name) {
        for (int i = 0; i < asset.boneNames.length; i++) if (asset.boneNames[i].equals(name)) return i;
        return -1;
    }

    /** Catmull-Rom, looping neighbours wrap (the last key duplicates the first). */
    static float sample(SkinnedMeshAsset.Channel ch, float t, int comp, boolean loop) {
        float[] ts = ch.times, v = ch.values;
        int n = ts.length;
        if (n == 1 || t <= ts[0]) return v[comp];
        if (t >= ts[n - 1]) return v[(n - 1) * 3 + comp];
        int i = 0;
        int lo = 0, hi = n - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (ts[mid] <= t) {
                i = mid;
                lo = mid + 1;
            } else hi = mid - 1;
        }
        i = Math.min(i, n - 2);
        float f = (t - ts[i]) / (ts[i + 1] - ts[i]);
        float p0 = at(v, i - 1, n, comp, loop), p1 = at(v, i, n, comp, loop), p2 = at(v, i + 1, n, comp, loop), p3 = at(v, i + 2, n, comp, loop);
        return 0.5f * ((2 * p1) + (-p0 + p2) * f + (2 * p0 - 5 * p1 + 4 * p2 - p3) * f * f + (-p0 + 3 * p1 - 3 * p2 + p3) * f * f * f);
    }

    private static float at(float[] v, int k, int n, int comp, boolean loop) {
        if (loop) {
            int m = n - 1;
            k = ((k % m) + m) % m;
        } else {
            k = Math.max(0, Math.min(n - 1, k));
        }
        return v[k * 3 + comp];
    }

    private void computeMatrices() {
        float[] pv = asset.pivots;
        for (int b = 0; b < asset.boneCount(); b++) {
            int p = asset.parents[b];
            float tx = pv[b * 3] - (p >= 0 ? pv[p * 3] : 0) + pos[b * 3];
            float ty = pv[b * 3 + 1] - (p >= 0 ? pv[p * 3 + 1] : 0) + pos[b * 3 + 1];
            float tz = pv[b * 3 + 2] - (p >= 0 ? pv[p * 3 + 2] : 0) + pos[b * 3 + 2];
            Matrix4f g = global[b];
            if (p >= 0) g.set(global[p]);
            else g.identity();
            g.translate(tx, ty, tz)
                    .rotateZ((float) Math.toRadians(rot[b * 3 + 2]))
                    .rotateY((float) Math.toRadians(rot[b * 3 + 1]))
                    .rotateX((float) Math.toRadians(rot[b * 3]));
            Matrix4f s = skin[b].set(g).translate(-pv[b * 3], -pv[b * 3 + 1], -pv[b * 3 + 2]);
            int o = b * 12;
            sm[o] = s.m00(); sm[o + 1] = s.m10(); sm[o + 2] = s.m20(); sm[o + 3] = s.m30();
            sm[o + 4] = s.m01(); sm[o + 5] = s.m11(); sm[o + 6] = s.m21(); sm[o + 7] = s.m31();
            sm[o + 8] = s.m02(); sm[o + 9] = s.m12(); sm[o + 10] = s.m22(); sm[o + 11] = s.m32();
        }
    }

    /** Skins the given LOD into internal buffers (Minecraft model space, pixels). */
    public void skin(int lodIndex) {
        long t0 = System.nanoTime();
        SkinnedMeshAsset.Lod lod = asset.lods[Math.min(lodIndex, asset.lods.length - 1)];
        computeMatrices();
        int n = lod.vertexCount;
        if (skinned.length != n * 3) {
            skinned = new float[n * 3];
            skinnedN = new float[n * 3];
        }
        float[] P = lod.positions, N = lod.normals, W = lod.weights;
        int[] J = lod.joints;
        for (int v = 0; v < n; v++) {
            float px = P[v * 3], py = P[v * 3 + 1], pz = P[v * 3 + 2];
            float nx = N[v * 3], ny = N[v * 3 + 1], nz = N[v * 3 + 2];
            float ox = 0, oy = 0, oz = 0, qx = 0, qy = 0, qz = 0;
            for (int k = 0; k < 3; k++) {
                float w = W[v * 3 + k];
                if (w <= 0f) continue;
                int o = J[v * 3 + k] * 12;
                // sm holds the 3x4 matrix row by row: [r0c0 r0c1 r0c2 r0c3 | r1c0 ... | r2c0 ...]
                ox += w * (sm[o] * px + sm[o + 1] * py + sm[o + 2] * pz + sm[o + 3]);
                oy += w * (sm[o + 4] * px + sm[o + 5] * py + sm[o + 6] * pz + sm[o + 7]);
                oz += w * (sm[o + 8] * px + sm[o + 9] * py + sm[o + 10] * pz + sm[o + 11]);
                qx += w * (sm[o] * nx + sm[o + 1] * ny + sm[o + 2] * nz);
                qy += w * (sm[o + 4] * nx + sm[o + 5] * ny + sm[o + 6] * nz);
                qz += w * (sm[o + 8] * nx + sm[o + 9] * ny + sm[o + 10] * nz);
            }
            // Blockbench space -> Minecraft model space (x mirrored, y down, ground at 24 px)
            skinned[v * 3] = -ox;
            skinned[v * 3 + 1] = 24f - oy;
            skinned[v * 3 + 2] = oz;
            float len = (float) Math.sqrt(qx * qx + qy * qy + qz * qz);
            if (len < 1e-6f) len = 1f;
            skinnedN[v * 3] = -qx / len;
            skinnedN[v * 3 + 1] = -qy / len;
            skinnedN[v * 3 + 2] = qz / len;
        }
        lodSkinned = lodIndex;
        stats(System.nanoTime() - t0, n);
    }

    /** World-space-free query: Blockbench-space position of a rest point carried by a bone (after skin()). */
    public float boneY(int bone, float rx, float ry, float rz) {
        int o = bone * 12;
        return sm[o + 4] * rx + sm[o + 5] * ry + sm[o + 6] * rz + sm[o + 7];
    }

    /** Emits the last skinned LOD as triangles (degenerate quads for QUADS render types). */
    public void emit(PoseStack.Pose pose, VertexConsumer vc, int light, int overlay, int color) {
        if (lodSkinned < 0) return;
        SkinnedMeshAsset.Lod lod = asset.lods[Math.min(lodSkinned, asset.lods.length - 1)];
        int[] idx = lod.indices;
        float[] uv = lod.uvs;
        for (int i = 0; i + 2 < idx.length; i += 3) {
            vertex(pose, vc, idx[i], uv, light, overlay, color);
            vertex(pose, vc, idx[i + 1], uv, light, overlay, color);
            vertex(pose, vc, idx[i + 2], uv, light, overlay, color);
            vertex(pose, vc, idx[i + 2], uv, light, overlay, color);
        }
    }

    private void vertex(PoseStack.Pose pose, VertexConsumer vc, int v, float[] uv, int light, int overlay, int color) {
        vc.addVertex(pose, skinned[v * 3] / 16f, skinned[v * 3 + 1] / 16f, skinned[v * 3 + 2] / 16f)
                .setColor(color)
                .setUv(uv[v * 2], uv[v * 2 + 1])
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, skinnedN[v * 3], skinnedN[v * 3 + 1], skinnedN[v * 3 + 2]);
    }

    private static void stats(long nanos, int verts) {
        long s = System.currentTimeMillis() / 1000;
        if (s != second) {
            second = s;
            nanosLastSecond = nanosAccum;
            verticesLastSecond = verticesAccum;
            nanosAccum = 0;
            verticesAccum = 0;
        }
        nanosAccum += nanos;
        verticesAccum += verts;
    }
}
