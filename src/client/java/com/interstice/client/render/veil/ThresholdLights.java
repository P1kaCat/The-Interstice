package com.interstice.client.render.veil;

import com.interstice.Interstice;
import com.interstice.client.ClientState;
import com.interstice.client.config.ClientConfig;
import com.interstice.world.threshold.ThresholdLayout;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import foundry.veil.api.client.render.light.renderer.LightRenderer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Level 00, The Threshold: the drawn lights really light. Veil point lights sit along the ceiling's light slot
 * (every 2 blocks, cold white, reaching the floor) and just in front of the white tear (strong, with a little in-scattering for its
 * glow). The corridor is drawn twisted relative to the viewer (ThresholdRenderer), so the lights are placed through
 * the same transform every frame: they turn with the architecture.
 */
public final class ThresholdLights {
    private static final double SPACING = 2.0, RANGE = 40;
    private static final List<LightRenderHandle<PointLightData>> slot = new ArrayList<>();
    private static LightRenderHandle<PointLightData> tear;
    private static boolean veilOk = true;

    private ThresholdLights() {}

    public static void update(Vec3 cam) {
        if (!ClientState.inThreshold || !veilOk || !ClientConfig.INSTANCE.dynamicLighting) {
            clear();
            return;
        }
        LightRenderer renderer;
        try {
            renderer = VeilRenderSystem.renderer().getLightRenderer();
        } catch (Throwable t) {
            veilOk = false;
            Interstice.LOG.warn("Veil light renderer unavailable; Threshold lights disabled", t);
            return;
        }
        try {
            double vz = cam.z;
            double v = ThresholdLayout.HALF_SIZE - 0.35;   // just under the slot
            int idx = 0;
            for (double z = ThresholdLayout.START_Z + 1.25; z < ThresholdLayout.RIFT_Z - 0.5; z += SPACING) {
                if (Math.abs(z - cam.z) > RANGE) continue;
                LightRenderHandle<PointLightData> h;
                if (idx < slot.size() && slot.get(idx).isValid()) h = slot.get(idx);
                else {
                    h = renderer.addLight(new PointLightData());
                    if (idx < slot.size()) slot.set(idx, h); else slot.add(h);
                }
                PointLightData p = h.getLightData();
                p.setPosition(ThresholdLayout.worldX(0, v, z, vz), ThresholdLayout.worldY(0, v, z, vz), z);
                p.setRadius(10f);   // reaches the floor (4.7 blocks below) with light to spare
                p.setColor(0.86f, 0.92f, 1f);
                p.setBrightness(0.55f);   // they overlap (every 2 blocks, radius 10): 1.6 washed the corridor white
                h.markDirty();
                idx++;
            }
            while (slot.size() > idx) free(slot.remove(slot.size() - 1));
            // the tear: a strong white light just in front of it
            if (tear == null || !tear.isValid()) tear = renderer.addLight(new PointLightData());
            PointLightData p = tear.getLightData();
            double tz = ThresholdLayout.RIFT_Z - 1.6, tv = ThresholdLayout.TEAR_Y - ThresholdLayout.AXIS_Y;
            p.setPosition(ThresholdLayout.worldX(0, tv, tz, vz), ThresholdLayout.worldY(0, tv, tz, vz), tz);
            p.setRadius(12f);
            p.setColor(1f, 1f, 1f);
            double breath = 0.9 + 0.1 * Math.sin(ClientState.time / 20.0 * 1.3);
            p.setBrightness((float) (2.2 * breath));   // stronger burnt the end wall to the tear's white
            p.setInscatteringStrength(ClientConfig.INSTANCE.volumetricLight ? 0.35f : 0f);
            tear.markDirty();
        } catch (Throwable t) {
            veilOk = false;
            Interstice.LOG.warn("Failed to update Threshold lights; disabled", t);
            clear();
        }
    }

    private static void free(LightRenderHandle<?> h) {
        try {
            if (h != null && h.isValid()) h.free();
        } catch (Throwable ignored) {
        }
    }

    public static void clear() {
        for (LightRenderHandle<PointLightData> h : slot) free(h);
        slot.clear();
        free(tear);
        tear = null;
    }
}
