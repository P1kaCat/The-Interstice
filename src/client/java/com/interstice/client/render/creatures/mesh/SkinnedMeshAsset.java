package com.interstice.client.render.creatures.mesh;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.interstice.Interstice;
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
        public final int vertexCount;

        Lod(JsonObject o) {
            positions = floats(o.getAsJsonArray("positions"));
            normals = floats(o.getAsJsonArray("normals"));
            uvs = floats(o.getAsJsonArray("uvs"));
            weights = floats(o.getAsJsonArray("weights"));
            joints = ints(o.getAsJsonArray("joints"));
            indices = ints(o.getAsJsonArray("indices"));
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
        public final Channel[] channels;

        Clip(String name, JsonObject o) {
            this.name = name;
            length = o.get("length").getAsFloat();
            loop = o.get("loop").getAsBoolean();
            cycleBlocks = o.has("cycleBlocks") ? o.get("cycleBlocks").getAsFloat() : 0f;
            JsonArray a = o.getAsJsonArray("channels");
            channels = new Channel[a.size()];
            for (int i = 0; i < channels.length; i++) channels[i] = new Channel(a.get(i).getAsJsonObject());
        }
    }

    public final String[] boneNames;
    public final int[] parents;
    public final float[] pivots;
    public final Lod[] lods;
    public final Map<String, Clip> clips = new HashMap<>();

    private SkinnedMeshAsset(JsonObject root) {
        JsonArray bones = root.getAsJsonArray("bones");
        boneNames = new String[bones.size()];
        parents = new int[bones.size()];
        pivots = new float[bones.size() * 3];
        for (int i = 0; i < bones.size(); i++) {
            JsonObject b = bones.get(i).getAsJsonObject();
            boneNames[i] = b.get("name").getAsString();
            parents[i] = b.get("parent").getAsInt();
            JsonArray p = b.getAsJsonArray("pivot");
            for (int k = 0; k < 3; k++) pivots[i * 3 + k] = p.get(k).getAsFloat();
            if (parents[i] >= i) throw new IllegalStateException("bones must be ordered parent-first");
        }
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

    private static final ResourceLocation ADHERENT = Interstice.id("models/entity/adherent.json");
    private static SkinnedMeshAsset adherent;
    private static boolean failed;

    /** Cleared on resource reload. */
    public static void invalidate() {
        adherent = null;
        failed = false;
    }

    public static SkinnedMeshAsset adherent() {
        if (adherent == null && !failed) {
            try {
                Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(ADHERENT);
                if (res.isEmpty()) throw new IllegalStateException("missing " + ADHERENT);
                try (Reader r = res.get().openAsReader()) {
                    adherent = new SkinnedMeshAsset(JsonParser.parseReader(r).getAsJsonObject());
                }
                Interstice.LOG.info("Loaded Adherent mesh: {} bones, {} vertices, {} animations",
                        adherent.boneCount(), adherent.lods[0].vertexCount, adherent.clips.size());
            } catch (Exception e) {
                failed = true;
                Interstice.LOG.error("Could not load the Adherent mesh asset", e);
            }
        }
        return adherent;
    }
}
