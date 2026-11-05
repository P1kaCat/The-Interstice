package com.interstice.client.render.veil;

import com.interstice.Interstice;
import com.interstice.client.ClientState;
import com.interstice.client.config.ClientConfig;
import com.interstice.entity.AdherentEntity;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import foundry.veil.api.client.render.light.renderer.LightRenderer;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * A faint red Veil point light at the eyes of the nearest few creatures, so a motionless creature in the
 * dark tints the wall and the floor around its head. Same Veil API and the same safety net as
 * {@link LampLightManager}: if Veil's light renderer is unavailable (or dynamic lighting is off, or we
 * are outside the Interstice) no light is created and the emissive eyes alone remain.
 * <p>
 * The eye layer offers a position every frame it draws a creature; {@link #update} (start of the next
 * world render) keeps lights for the {@code MAX} nearest offers within {@code RANGE} blocks of the camera.
 */
public final class CreatureEyeLights {
    private static final int MAX = 6;
    private static final double RANGE = 22.0;

    private record Offer(int id, double x, double y, double z, float k, double d2) {}

    private static final List<Offer> offers = new ArrayList<>();
    private static final Int2ObjectOpenHashMap<LightRenderHandle<PointLightData>> lights = new Int2ObjectOpenHashMap<>();
    private static boolean veilOk = true;
    private static Vec3 cam = Vec3.ZERO;
    public static int active;

    private CreatureEyeLights() {}

    public static void offer(AdherentEntity e, double x, double y, double z, float k) {
        // a sanity check on the pose-stack assumption: the eyes are on the creature, not elsewhere
        if (e.position().distanceToSqr(x, y, z) > 25) return;
        double d2 = cam.distanceToSqr(x, y, z);
        if (d2 < RANGE * RANGE) offers.add(new Offer(e.getId(), x, y, z, k, d2));
    }

    public static void update(Vec3 camera) {
        cam = camera;
        ClientConfig cfg = ClientConfig.INSTANCE;
        if (!veilOk || !ClientState.inInterstice || !cfg.dynamicLighting) {
            clear();
            offers.clear();
            return;
        }
        LightRenderer renderer;
        try {
            renderer = VeilRenderSystem.renderer().getLightRenderer();
        } catch (Throwable t) {
            veilOk = false;
            Interstice.LOG.warn("Veil light renderer unavailable; creature eye lights disabled", t);
            clear();
            return;
        }
        offers.sort((a, b) -> Double.compare(a.d2, b.d2));
        Int2ObjectOpenHashMap<LightRenderHandle<PointLightData>> keep = new Int2ObjectOpenHashMap<>();
        for (int i = 0; i < Math.min(MAX, offers.size()); i++) {
            Offer o = offers.get(i);
            if (keep.containsKey(o.id)) continue;
            LightRenderHandle<PointLightData> h = lights.remove(o.id);
            if (h == null || !h.isValid()) {
                try {
                    h = renderer.addLight(new PointLightData());
                } catch (Throwable t) {
                    veilOk = false;
                    Interstice.LOG.warn("Failed to create a Veil light; creature eye lights disabled", t);
                    break;
                }
            }
            PointLightData p = h.getLightData();
            p.setPosition(o.x, o.y, o.z);
            p.setRadius(3.0f);
            p.setColor(1f, 0.05f, 0.03f);
            p.setBrightness(0.42f * Math.min(1.6f, o.k));
            p.setInscatteringStrength(0f);
            h.markDirty();
            keep.put(o.id, h);
        }
        clear();
        lights.putAll(keep);
        active = lights.size();
        offers.clear();
    }

    public static void clear() {
        for (LightRenderHandle<PointLightData> h : lights.values()) {
            try {
                if (h.isValid()) h.free();
            } catch (Throwable ignored) {
            }
        }
        lights.clear();
        active = 0;
    }
}
