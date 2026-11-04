package com.interstice.world.anomalies;

import com.interstice.util.Hash;
import com.interstice.world.IntersticeLayout;
import com.interstice.world.generation.ChunkWriter;
import com.interstice.world.structures.StructurePass;

import java.util.ArrayList;
import java.util.List;

/** Chooses at most one chunk-contained anomaly per chunk, weighted by anomaly level. */
public final class AnomalyPass {
    private AnomalyPass() {}

    public static void run(ChunkWriter w) {
        IntersticeLayout L = w.layout;
        int cx = w.minX + 8, cz = w.minZ + 8;
        if (L.wallDepth(cx, cz) > -14) return;
        if (Math.abs(cz) < 48) return;
        double level = L.anomalyLevel(cz);
        long h = Hash.of(L.sub(601), w.minX >> 4, w.minZ >> 4);
        if (Hash.unit(h, 0) > 0.03 + 0.22 * level) return;
        if (L.pitAt(cx, cz) != null || StructurePass.bunkerAt(L, cx, cz) != null || L.nearLandmark(cz, 80)) return;

        List<AnomalyType> pool = new ArrayList<>();
        for (AnomalyType t : AnomalyType.values()) if (t.minLevel <= level) pool.add(t);
        AnomalyType type = pool.get((int) (Hash.unit(h, 1) * pool.size()));
        type.place(w, cx, L.floorY(cx, cz), cz, h);
    }

    public static AnomalyType anomalyForChunk(IntersticeLayout L, int chunkX, int chunkZ) {
        int cx = (chunkX << 4) + 8, cz = (chunkZ << 4) + 8;
        if (L.wallDepth(cx, cz) > -14 || Math.abs(cz) < 48) return null;
        double level = L.anomalyLevel(cz);
        long h = Hash.of(L.sub(601), chunkX, chunkZ);
        if (Hash.unit(h, 0) > 0.03 + 0.22 * level) return null;
        List<AnomalyType> pool = new ArrayList<>();
        for (AnomalyType t : AnomalyType.values()) if (t.minLevel <= level) pool.add(t);
        return pool.get((int) (Hash.unit(h, 1) * pool.size()));
    }
}
