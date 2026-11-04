package com.interstice.client.render.creatures.mesh;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.interstice.Interstice;
import com.interstice.entity.species.Species;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A skinned creature mesh loaded from a JSON asset produced by tools/creature/export_adherent.py
 * from the editable Blockbench source (models/adherent.bbmodel).
 * <p>
 * Space: Blockbench pixels, Y up, ground at y=0, front towards -Z. Each vertex has up to three
 * bone influences. Bones have pivots and no rest rotation; animations add rotations (degrees,
 * applied as Rz*Ry*Rx) and position offsets (pixels) per bone.
 */
public final class SkinnedMeshAsset {
    public static final class Lod {
        public final float[] positions, normals, uvs, weights;
        public final int[] joints, indices;
        /** Triangles of the eyes (indices into this LOD), drawn again in the emissive pass; empty if none. */
        public final int[] eyeIndices;
        public final int vertexCount;

        Lod(JsonObject o) {
            positions = floats(o.getAsJsonArray("positions"));
            normals = floats(o.getAsJsonArray("normals"));
            uvs = floats(o.getAsJsonArray("uvs"));
            weights = floats(o.getAsJsonArray("weights"));
            joints = ints(o.getAsJsonArray("joints"));
            indices = ints(o.getAsJsonArray("indices"));
            eyeIndices = o.has("eyeIndices") ? ints(o.getAsJsonArray("eyeIndices")) : new int[0];
            vertexCount = positions.length / 3;
        }
    }

    public static final class Channel {
        public final int bone;
        public final boolean rotation;
        public final float[] times, values;

        Channel(JsonObject o) {
            bone = o.get("bone").getAsInt();
            rotation = "rot".equals(o.get("type").getAsString());
            times = floats(o.getAsJsonArray("times"));
            values = floats(o.getAsJsonArray("values"));
        }
    }

    public static final class Clip {
        public final String name;
        public final float length;
        public final boolean loop;
        /** Blocks travelled per cycle for distance-driven locomotion (0 for time-driven clips). */
        public final float cycleBlocks;
        /** Degrees of body yaw per cycle for rotation-driven clips (turning in place; 0 otherwise). */
        public final float cycleDegrees;
        public final Channel[] channels;

        Clip(String name, JsonObject o) {
            this.name = name;
            length = o.get("length").getAsFloat();
            loop = o.get("loop").getAsBoolean();
            cycleBlocks = o.has("cycleBlocks") ? o.get("cycleBlocks").getAsFloat() : 0f;
            cycleDegrees = o.has("cycleDegrees") ? o.get("cycleDegrees").getAsFloat() : 0f;
            JsonArray a = o.getAsJsonArray("channels");
            channels = new Channel[a.size()];
            for (int i = 0; i < channels.length; i++) channels[i] = new Channel(a.get(i).getAsJsonObject());
        }
    }

    public final String[] boneNames;
    public final int[] parents;
    public final float[] pivots;
    /** Hanging bones (moss strands): rest direction (unit, Blockbench space) per bone, or NaN when not hanging. */
    public final float[] dangle;
    public final int[] dangleBones;
    public final Lod[] lods;
    public final Map<String, Clip> clips = new HashMap<>();

    private SkinnedMeshAsset(JsonObject root) {
        JsonArray bones = root.getAsJsonArray("bones");
        boneNames = new String[bones.size()];
        parents = new int[bones.size()];
        pivots = new float[bones.size() * 3];
        dangle = new float[bones.size() * 3];
        java.util.Arrays.fill(dangle, Float.NaN);
        java.util.List<Integer> hanging = new java.util.ArrayList<>();
        for (int i = 0; i < bones.size(); i++) {
            JsonObject b = bones.get(i).getAsJsonObject();
            boneNames[i] = b.get("name").getAsString();
            parents[i] = b.get("parent").getAsInt();
            JsonArray p = b.getAsJsonArray("pivot");
            for (int k = 0; k < 3; k++) pivots[i * 3 + k] = p.get(k).getAsFloat();
            if (parents[i] >= i) throw new IllegalStateException("bones must be ordered parent-first");
            if (b.has("dangle")) {
                JsonArray d = b.getAsJsonArray("dangle");
                for (int k = 0; k < 3; k++) dangle[i * 3 + k] = d.get(k).getAsFloat();
                hanging.add(i);
            }
        }
        dangleBones = hanging.stream().mapToInt(Integer::intValue).toArray();
        JsonArray l = root.getAsJsonArray("lods");
        lods = new Lod[l.size()];
        for (int i = 0; i < lods.length; i++) lods[i] = new Lod(l.get(i).getAsJsonObject());
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("animations").entrySet()) {
            clips.put(e.getKey(), new Clip(e.getKey(), e.getValue().getAsJsonObject()));
        }
    }

    public int boneCount() {
        return boneNames.length;
    }

    public Clip clip(String name) {
        return clips.get(name);
    }

    private static float[] floats(JsonArray a) {
        float[] f = new float[a.size()];
        for (int i = 0; i < f.length; i++) f[i] = a.get(i).getAsFloat();
        return f;
    }

    private static int[] ints(JsonArray a) {
        int[] f = new int[a.size()];
        for (int i = 0; i < f.length; i++) f[i] = a.get(i).getAsInt();
        return f;
    }

    // ------------------------------------------------------------------ loading / cache

    private static final SkinnedMeshAsset[] CACHE = new SkinnedMeshAsset[Species.values().length];
    private static final boolean[] FAILED = new boolean[Species.values().length];

    /** Cleared on resource reload. */
    public static void invalidate() {
        java.util.Arrays.fill(CACHE, null);
        java.util.Arrays.fill(FAILED, false);
    }

    public static SkinnedMeshAsset adherent() {
        return of(Species.ADHERENT);
    }

    /** The species' mesh (models/entity/&lt;species&gt;.json); falls back to the Adherent's if it is missing. */
    public static SkinnedMeshAsset of(Species sp) {
        int i = sp.ordinal();
        if (CACHE[i] == null && !FAILED[i]) {
            ResourceLocation id = Interstice.id("models/entity/" + sp.id + ".json");
            try {
                Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(id);
                if (res.isEmpty()) throw new IllegalStateException("missing " + id);
                try (Reader r = res.get().openAsReader()) {
                    CACHE[i] = new SkinnedMeshAsset(JsonParser.parseReader(r).getAsJsonObject());
                }
                Interstice.LOG.info("Loaded {} mesh: {} bones, {} vertices (LOD1 {}), {} animations", sp.id,
                        CACHE[i].boneCount(), CACHE[i].lods[0].vertexCount, CACHE[i].lods[CACHE[i].lods.length - 1].vertexCount, CACHE[i].clips.size());
            } catch (Exception e) {
                FAILED[i] = true;
                Interstice.LOG.error("Could not load the {} mesh asset", sp.id, e);
            }
        }
        if (CACHE[i] == null && sp != Species.ADHERENT) return of(Species.ADHERENT);
        return CACHE[i];
    }
}
