package com.interstice.client.render.chains;

import com.interstice.util.Hash;
import com.interstice.world.IntersticeLayout;
import com.interstice.world.structures.Landmarks;
import com.interstice.world.structures.StructurePass;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic placement of the giant chains, reconstructed on the client
 * from the layout seed. No chain is ever sent over the network or stored in
 * the world: they are pure functions of (seed, cell).
 */
public final class ChainField {
    public static final int CELL = 120;

    /** SPAN: wall to wall. HANG: from the dark above. ANCHOR: the anchor landmark. BROKEN: torn, one end on a wall, the other loose. */
    public enum Kind { SPAN, HANG, ANCHOR, BROKEN }

    public static final class ChainDef {
        public final long id;
        public final Kind kind;
        /** SPAN: west anchor. HANG/ANCHOR: top point. */
        public final double ax, ay, az;
        /** SPAN: east anchor. HANG/ANCHOR: bottom point. */
        public final double bx, by, bz;
        public final double sag;
        public final float scale;
        public final float swingAmp;
        public final float omega;
        public final float phase;
        public final boolean weight;
        // dynamic state (client simulation)
        public float impulse, impulseVel;
        public float vibration;
        public float sideImpulse, sideVel;

        ChainDef(long id, Kind kind, double ax, double ay, double az, double bx, double by, double bz, double sag, float scale,
                 float swingAmp, float omega, float phase, boolean weight) {
            this.id = id;
            this.kind = kind;
            this.ax = ax; this.ay = ay; this.az = az;
            this.bx = bx; this.by = by; this.bz = bz;
            this.sag = sag;
            this.scale = scale;
            this.swingAmp = swingAmp;
            this.omega = omega;
            this.phase = phase;
            this.weight = weight;
        }

        public double length() {
            double dx = bx - ax, dy = by - ay, dz = bz - az;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    private final IntersticeLayout layout;
    private final Int2ObjectOpenHashMap<List<ChainDef>> cells = new Int2ObjectOpenHashMap<>();

    public ChainField(IntersticeLayout layout) {
        this.layout = layout;
    }

    public IntersticeLayout layout() {
        return layout;
    }

    public void collect(double camZ, double range, List<ChainDef> out) {
        int c0 = (int) Math.floor((camZ - range) / CELL) - 1;
        int c1 = (int) Math.floor((camZ + range) / CELL) + 1;
        for (int c = c0; c <= c1; c++) out.addAll(cell(c));
        if (cells.size() > 256) {
            int center = (int) Math.floor(camZ / CELL);
            cells.int2ObjectEntrySet().removeIf(e -> Math.abs(e.getIntKey() - center) > 40);
        }
    }

    private List<ChainDef> cell(int c) {
        List<ChainDef> l = cells.get(c);
        if (l == null) {
            l = build(c);
            cells.put(c, l);
        }
        return l;
    }

    private List<ChainDef> build(int c) {
        List<ChainDef> list = new ArrayList<>(3);
        IntersticeLayout L = layout;
        long h = Hash.of(L.sub(701), c);
        double density = 1.0;
        com.interstice.world.CorridorPlan plan = L.plan();
        if (plan != null) {
            double zc = c * CELL + CELL / 2.0;
            if (!plan.inside(zc)) return list;   // nothing beyond the bulkheads
            float[] wgt = plan.weights(zc);
            // the Ironworks is full of chains, the Expanse has a few, far apart
            density = wgt[0] * 0.9 + wgt[1] * 2.4 + wgt[2] * 1.0 + wgt[3] * 0.55;
        }
        int n = (int) (Hash.unit(h, 0) * 3.2 * density);
        if (c == 0) n = Math.max(n, 2); // the arrival area always shows the chains
        for (int i = 0; i < n; i++) {
            long hi = Hash.of(h, i + 1);
            double roll = Hash.unit(hi, 1);
            double z = c * CELL + 8 + Hash.unit(hi, 2) * (CELL - 16);
            float scale = 0.9f + (float) Hash.unit(hi, 3) * 0.8f;
            float phase = (float) (Hash.unit(hi, 4) * Math.PI * 2);
            boolean v2 = plan != null;
            if (roll < (v2 ? 0.66 : 0.5)) {
                double z2 = z + (Hash.unit(hi, 5) - 0.5) * 50;
                // version 2: most spans hang low enough to be seen from the street (above the roofs), a few lost high up
                double ya = v2 ? IntersticeLayout.FLOOR_Y + 30 + Math.pow(Hash.unit(hi, 6), 1.7) * 300
                        : IntersticeLayout.FLOOR_Y + 90 + Hash.unit(hi, 6) * 520;
                double yb = v2 ? Math.max(IntersticeLayout.FLOOR_Y + 30, ya + (Hash.unit(hi, 7) - 0.5) * 50)
                        : Math.max(IntersticeLayout.FLOOR_Y + 80, ya + (Hash.unit(hi, 7) - 0.5) * 160);
                double ax = L.faceX(-1, z) + 0.5, bx = L.faceX(1, z2) - 0.5;
                double span = Math.sqrt((bx - ax) * (bx - ax) + (z2 - z) * (z2 - z));
                double sag = span * (0.08 + Hash.unit(hi, 8) * 0.16);
                if (v2) sag = Math.min(sag, Math.min(ya, yb) - (IntersticeLayout.FLOOR_Y + 22));   // clear of the rooftops
                list.add(new ChainDef(hi, Kind.SPAN, ax, ya, z, bx, yb, z2, sag, scale,
                        0.05f + (float) Hash.unit(hi, 9) * 0.1f, 0.10f + (float) Hash.unit(hi, 10) * 0.08f, phase, false));
            } else {
                double hw = L.halfWidth(z) - 10;
                double x = L.centerX(z) + (Hash.unit(hi, 5) * 2 - 1) * hw;
                double bottom = IntersticeLayout.FLOOR_Y + 45 + Hash.unit(hi, 6) * 190;
                list.add(new ChainDef(hi, Kind.HANG, x, IntersticeLayout.VIRTUAL_TOP, z, x, bottom, z, 0, scale,
                        1.5f + (float) Hash.unit(hi, 7) * 4f, 0.06f + (float) Hash.unit(hi, 8) * 0.05f, phase, Hash.unit(hi, 9) < 0.7));
            }
        }
        // torn chains: one end on a wall, the rest hanging loose, swinging heavily
        int nb = (int) (Hash.unit(h, 50) * 1.6 * density);
        for (int i = 0; i < nb; i++) {
            long hi = Hash.of(h, 100 + i);
            double z = c * CELL + 6 + Hash.unit(hi, 1) * (CELL - 12);
            int side = Hash.unit(hi, 2) < 0.5 ? -1 : 1;
            double top = IntersticeLayout.FLOOR_Y + 70 + Hash.unit(hi, 3) * 180;
            double x = L.faceX(side, z) - side * 0.8;
            double len = 40 + Hash.unit(hi, 4) * Math.min(150, top - IntersticeLayout.FLOOR_Y - 12);
            list.add(new ChainDef(hi, Kind.BROKEN, x, top, z, x, top - len, z, 0, 0.9f + (float) Hash.unit(hi, 5) * 0.6f,
                    2.0f + (float) Hash.unit(hi, 6) * 3f, 0.08f + (float) Hash.unit(hi, 7) * 0.05f, (float) (Hash.unit(hi, 8) * 6.28), false));
        }
        // chains hanging from the giant beams
        for (int bc = Math.floorDiv(c * CELL, StructurePass.BEAM_CELL); bc <= Math.floorDiv(c * CELL + CELL - 1, StructurePass.BEAM_CELL); bc++) {
            StructurePass.Beam b = StructurePass.beam(L, bc);
            if (b == null) continue;
            double z = b.z0() + b.zLen() / 2.0;
            if (z < c * CELL || z >= (c + 1) * CELL) continue;
            long hb = Hash.of(L.sub(702), bc);
            int count = 1 + (int) (Hash.unit(hb, 0) * 2);
            for (int i = 0; i < count; i++) {
                double x = L.centerX(z) + (Hash.unit(hb, i, 1) * 2 - 1) * (L.halfWidth(z) - 12);
                double bottom = IntersticeLayout.FLOOR_Y + 40 + Hash.unit(hb, i, 2) * 110;
                list.add(new ChainDef(Hash.of(hb, i), Kind.HANG, x, b.y0(), z, x, bottom, z, 0, 1.1f,
                        0.6f + (float) Hash.unit(hb, i, 3) * 1.5f, 0.12f + (float) Hash.unit(hb, i, 4) * 0.08f,
                        (float) (Hash.unit(hb, i, 5) * 6.28), true));
            }
        }
        // anchor landmark: one taut chain rising from the anchor block into the dark
        for (int lc = Math.floorDiv(c * CELL, IntersticeLayout.LANDMARK_CELL); lc <= Math.floorDiv(c * CELL + CELL - 1, IntersticeLayout.LANDMARK_CELL); lc++) {
            IntersticeLayout.Landmark lm = L.landmark(lc);
            if (lm.type() != IntersticeLayout.LandmarkType.ANCHOR || lm.z() < c * CELL || lm.z() >= (c + 1) * CELL) continue;
            double[] p = Landmarks.anchorTop(L, lm);
            list.add(new ChainDef(lm.hash(), Kind.ANCHOR, p[0], IntersticeLayout.VIRTUAL_TOP, p[2], p[0], p[1], p[2], 0, 1.7f, 0.4f, 0.2f, 0f, false));
        }
        return list;
    }
}
