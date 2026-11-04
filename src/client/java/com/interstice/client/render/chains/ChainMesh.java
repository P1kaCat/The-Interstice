package com.interstice.client.render.chains;

/**
 * Procedurally generated chain-link geometry (an elongated torus around a
 * stadium-shaped centre line), built once per LOD and reused for every link
 * of every chain. Stored as flat arrays of quads: 4 vertices x (pos3, normal3, uv2).
 * <p>
 * Local axes: X along the chain, Y across the link (ring plane), Z through the ring.
 */
public final class ChainMesh {
    public static final float LINK_LENGTH = 3.6f;
    public static final float LINK_WIDTH = 2.1f;
    public static final float TUBE = 0.42f;

    public final float[] data;
    public final int quads;

    private static final ChainMesh[] LODS = {
            new ChainMesh(8, 3),
            new ChainMesh(12, 4),
            new ChainMesh(18, 6),
            new ChainMesh(26, 8)
    };

    public static ChainMesh lod(int i) {
        return LODS[Math.max(0, Math.min(LODS.length - 1, i))];
    }

    private ChainMesh(int pathSegments, int tubeSegments) {
        float R = LINK_WIDTH / 2f - TUBE;
        float c = LINK_LENGTH / 2f - R - TUBE;
        // sample centre line
        float straight = 2 * c, arc = (float) Math.PI * R;
        float perimeter = 2 * straight + 2 * arc;
        float[] px = new float[pathSegments + 1], py = new float[pathSegments + 1], nx = new float[pathSegments + 1], ny = new float[pathSegments + 1];
        for (int i = 0; i <= pathSegments; i++) {
            float s = perimeter * i / pathSegments;
            float x, y, ox, oy;
            if (s < straight) { // top edge, left to right
                x = -c + s; y = R; ox = 0; oy = 1;
            } else if (s < straight + arc) { // right cap
                float a = (float) (Math.PI / 2 - (s - straight) / R);
                x = c + R * (float) Math.cos(a); y = R * (float) Math.sin(a); ox = (float) Math.cos(a); oy = (float) Math.sin(a);
            } else if (s < 2 * straight + arc) { // bottom edge, right to left
                x = c - (s - straight - arc); y = -R; ox = 0; oy = -1;
            } else { // left cap
                float a = (float) (-Math.PI / 2 - (s - 2 * straight - arc) / R);
                x = -c + R * (float) Math.cos(a); y = R * (float) Math.sin(a); ox = (float) Math.cos(a); oy = (float) Math.sin(a);
            }
            px[i] = x; py[i] = y; nx[i] = ox; ny[i] = oy;
        }
        quads = pathSegments * tubeSegments;
        data = new float[quads * 4 * 8];
        int k = 0;
        for (int i = 0; i < pathSegments; i++) {
            for (int j = 0; j < tubeSegments; j++) {
                int[][] corners = {{i, j}, {i + 1, j}, {i + 1, j + 1}, {i, j + 1}};
                for (int[] cc : corners) {
                    int pi = cc[0], tj = cc[1];
                    double v = Math.PI * 2 * tj / tubeSegments;
                    float cv = (float) Math.cos(v), sv = (float) Math.sin(v);
                    float nX = nx[pi] * cv, nY = ny[pi] * cv, nZ = sv;
                    data[k++] = px[pi] + TUBE * nX;
                    data[k++] = py[pi] + TUBE * nY;
                    data[k++] = TUBE * nZ;
                    data[k++] = nX;
                    data[k++] = nY;
                    data[k++] = nZ;
                    data[k++] = (float) pi / pathSegments * 4f;
                    data[k++] = (float) tj / tubeSegments;
                }
            }
        }
    }
}
