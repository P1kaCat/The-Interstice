package com.interstice.client.render.atmosphere;

/**
 * The Corridor's lightmap: vanilla block light only leaves a faint glow around the fixtures, so the floors are dark
 * and it is the Veil lights (lamps, flood lamps, searchlights, the flashlight) that actually light things.
 * <ul>
 *   <li>the brightness (gamma) setting is ignored in THE INTERSTICE ({@code LightTextureMixin} hands vanilla 0
 *       instead): its lift of the shadows would grey the whole corridor;</li>
 *   <li>each colour is scaled by a steep curve of the block light level: full light keeps most of its value, mid
 *       levels drop to a third, the dark stays nearly black.</li>
 * </ul>
 * A plain scale, applied to the vanilla value before it is written: the small per-frame torch flicker of vanilla stays
 * as small as it is (the previous version recovered the pre-gamma value from the rounded output, which turned that
 * flicker into jumps of a quarter of the brightness). Rendering only: light levels (perception, spawning) are untouched.
 */
public final class CorridorLightmap {
    private static final float[] KEEP = new float[16];

    static {
        for (int i = 0; i < 16; i++) KEEP[i] = 0.18f + 0.12f * (float) Math.pow(i / 15f, 3.0);   // a trace of light everywhere (shapes when one squints), 30% at full light
    }

    private CorridorLightmap() {}

    public static int adjust(int block, int abgr) {
        float k = KEEP[Math.max(0, Math.min(15, block))];
        int r = Math.round((abgr & 0xFF) * k), g = Math.round(((abgr >> 8) & 0xFF) * k), b = Math.round(((abgr >> 16) & 0xFF) * k);
        return (abgr & 0xFF000000) | (b << 16) | (g << 8) | r;
    }
}
