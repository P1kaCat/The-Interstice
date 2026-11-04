package com.interstice.client.render.creatures.mesh;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Poses and skins a {@link SkinnedMeshAsset} on the CPU, then emits it through a vanilla
 * {@link VertexConsumer} (so vanilla lighting, overlay and Veil's deferred lights apply).
 * <p>
 * Pipeline per frame: reset pose, add weighted animation clips (additive offsets from the rest
 * pose; Catmull-Rom sampling identical to the authoring tool's validation), compute bone
 * matrices (G = G_parent * T(pivot - parentPivot + pos) * Rz * Ry * Rx), skin each vertex with up to
 * three weighted bones (linear blend skinning), then convert Blockbench space (Y up, ground 0) to
 * Minecraft entity model space (X mirrored, Y down, ground at 24 px) and emit triangles.
 * <p>
 * Hanging bones (moss strands) are not animated by clips: each frame their direction is a damped spring
 * pulled towards gravity (in Blockbench space, given by the renderer from the body's orientation), kicked
 * by the body's acceleration and dragged by its rotation, then turned into the bone's local rotation.
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

    /** Gravity and the body's acceleration (blocks/tick^2) in Blockbench space; set by the renderer each frame. */
    public final Vector3f gravity = new Vector3f(0, -1, 0);
    public final Vector3f accel = new Vector3f();
    /** Ticks since the previous frame for the moss dynamics; 0 = no dynamics (the strands just hang). */
    public float simDt;
    private static final float HANG = 0.85f, SPRING = 0.16f, DAMP = 0.7f, INERTIA = 7f;
    private final float[] dDir, dVel;
    private boolean dInit;
    private final Vector3f vRest = new Vector3f(), vT = new Vector3f(), vD = new Vector3f(), vV = new Vector3f();
    private final Quaternionf q = new Quaternionf();

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
        dDir = new float[n * 3];
        dVel = new float[n * 3];
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
            g.translate(tx, ty, tz);
            if (p >= 0 && !Float.isNaN(asset.dangle[b * 3])) hang(b, g, global[p]);
            else g.rotateZ((float) Math.toRadians(rot[b * 3 + 2]))
                    .rotateY((float) Math.toRadians(rot[b * 3 + 1]))
                    .rotateX((float) Math.toRadians(rot[b * 3]));
            Matrix4f s = skin[b].set(g).translate(-pv[b * 3], -pv[b * 3 + 1], -pv[b * 3 + 2]);
            int o = b * 12;
            sm[o] = s.m00(); sm[o + 1] = s.m10(); sm[o + 2] = s.m20(); sm[o + 3] = s.m30();
            sm[o + 4] = s.m01(); sm[o + 5] = s.m11(); sm[o + 6] = s.m21(); sm[o + 7] = s.m31();
            sm[o + 8] = s.m02(); sm[o + 9] = s.m12(); sm[o + 10] = s.m22(); sm[o + 11] = s.m32();
        }
    }

    /** A moss strand: spring its direction towards gravity, then rotate the bone from its rest direction to it. */
    private void hang(int b, Matrix4f g, Matrix4f parent) {
        int o = b * 3;
        float rx = asset.dangle[o], ry = asset.dangle[o + 1], rz = asset.dangle[o + 2];
        parent.transformDirection(vRest.set(rx, ry, rz)).normalize();
        vT.set(vRest).mul(1 - HANG).add(gravity.x * HANG, gravity.y * HANG, gravity.z * HANG);
        if (vT.lengthSquared() < 1e-6f) vT.set(gravity);
        vT.normalize();
        if (!dInit || simDt <= 0f) {
            vD.set(vT);
            vV.zero();
        } else {
            float dt = Math.min(simDt, 2f);
            vD.set(dDir[o], dDir[o + 1], dDir[o + 2]);
            vV.set(dVel[o], dVel[o + 1], dVel[o + 2]);
            // spring towards the hanging direction; the body's acceleration throws the strand the other way
            vV.add((vT.x - vD.x) * SPRING * dt - accel.x * INERTIA * dt, (vT.y - vD.y) * SPRING * dt - accel.y * INERTIA * dt,
                    (vT.z - vD.z) * SPRING * dt - accel.z * INERTIA * dt);
            vV.mul((float) Math.pow(DAMP, dt));
            vD.add(vV.x * dt, vV.y * dt, vV.z * dt);
            // never fold back into the body
            float c = vD.normalize().dot(vRest);
            if (c < -0.25f) vD.add(vRest.x * (-0.25f - c) * 1.5f, vRest.y * (-0.25f - c) * 1.5f, vRest.z * (-0.25f - c) * 1.5f).normalize();
        }
        dDir[o] = vD.x; dDir[o + 1] = vD.y; dDir[o + 2] = vD.z;
        dVel[o] = vV.x; dVel[o + 1] = vV.y; dVel[o + 2] = vV.z;
        // into the parent's frame (rigid: the inverse rotation is the transpose)
        float lx = parent.m00() * vD.x + parent.m01() * vD.y + parent.m02() * vD.z;
        float ly = parent.m10() * vD.x + parent.m11() * vD.y + parent.m12() * vD.z;
        float lz = parent.m20() * vD.x + parent.m21() * vD.y + parent.m22() * vD.z;
        g.rotate(q.rotationTo(rx, ry, rz, lx, ly, lz));
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
        if (asset.dangleBones.length > 0) dInit = true;
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

    /** Emits only the eyes of the last skinned LOD (the emissive pass); false when the asset has no eyes. */
    public boolean emitEyes(PoseStack.Pose pose, VertexConsumer vc, int light, int overlay, int color) {
        if (lodSkinned < 0) return false;
        SkinnedMeshAsset.Lod lod = asset.lods[Math.min(lodSkinned, asset.lods.length - 1)];
        int[] idx = lod.eyeIndices;
        if (idx.length == 0) return false;
        float[] uv = lod.uvs;
        for (int i = 0; i + 2 < idx.length; i += 3) {
            vertex(pose, vc, idx[i], uv, light, overlay, color);
            vertex(pose, vc, idx[i + 1], uv, light, overlay, color);
            vertex(pose, vc, idx[i + 2], uv, light, overlay, color);
            vertex(pose, vc, idx[i + 2], uv, light, overlay, color);
        }
        return true;
    }

    /** Floats per cached eye index entry: position (3), normal (3), uv (2). */
    public static final int EYE_STRIDE = 8;

    /** Number of eye index entries of the last skinned LOD (0 before skin() or without eyes). */
    public int eyeEntryCount() {
        if (lodSkinned < 0) return 0;
        return asset.lods[Math.min(lodSkinned, asset.lods.length - 1)].eyeIndices.length;
    }

    /**
     * Copies the skinned eye vertices of the last skinned LOD into {@code dst} at {@code off}
     * ({@link #EYE_STRIDE} floats per index entry), so a later emissive pass can draw them without
     * re-skinning the whole mesh. Returns the number of entries written.
     */
    public int copyEyes(float[] dst, int off) {
        int n = eyeEntryCount();
        if (n == 0) return 0;
        SkinnedMeshAsset.Lod lod = asset.lods[Math.min(lodSkinned, asset.lods.length - 1)];
        int[] idx = lod.eyeIndices;
        float[] uv = lod.uvs;
        for (int i = 0; i < n; i++) {
            int v = idx[i], o = off + i * EYE_STRIDE;
            dst[o] = skinned[v * 3];
            dst[o + 1] = skinned[v * 3 + 1];
            dst[o + 2] = skinned[v * 3 + 2];
            dst[o + 3] = skinnedN[v * 3];
            dst[o + 4] = skinnedN[v * 3 + 1];
            dst[o + 5] = skinnedN[v * 3 + 2];
            dst[o + 6] = uv[v * 2];
            dst[o + 7] = uv[v * 2 + 1];
        }
        return n;
    }

    /** Emits eyes cached by {@link #copyEyes} as triangles (degenerate quads), like {@link #emitEyes}. */
    public static void emitCachedEyes(PoseStack.Pose pose, VertexConsumer vc, float[] src, int off, int count, int light, int overlay, int color) {
        for (int i = 0; i + 2 < count; i += 3) {
            cachedVertex(pose, vc, src, off + i * EYE_STRIDE, light, overlay, color);
            cachedVertex(pose, vc, src, off + (i + 1) * EYE_STRIDE, light, overlay, color);
            cachedVertex(pose, vc, src, off + (i + 2) * EYE_STRIDE, light, overlay, color);
            cachedVertex(pose, vc, src, off + (i + 2) * EYE_STRIDE, light, overlay, color);
        }
    }

    private static void cachedVertex(PoseStack.Pose pose, VertexConsumer vc, float[] s, int o, int light, int overlay, int color) {
        vc.addVertex(pose, s[o] / 16f, s[o + 1] / 16f, s[o + 2] / 16f)
                .setColor(color)
                .setUv(s[o + 6], s[o + 7])
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, s[o + 3], s[o + 4], s[o + 5]);
    }

    /** Mean position of the eyes of the last skinned LOD, in Minecraft model space (blocks); false if none. */
    public boolean eyeCentre(Vector3f out) {
        if (lodSkinned < 0) return false;
        int[] idx = asset.lods[Math.min(lodSkinned, asset.lods.length - 1)].eyeIndices;
        if (idx.length == 0) return false;
        float x = 0, y = 0, z = 0;
        for (int v : idx) {
            x += skinned[v * 3];
            y += skinned[v * 3 + 1];
            z += skinned[v * 3 + 2];
        }
        out.set(x / idx.length / 16f, y / idx.length / 16f, z / idx.length / 16f);
        return true;
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
