package com.interstice.client.render.veil;

import com.interstice.Interstice;
import com.interstice.client.ClientState;
import com.interstice.client.config.ClientConfig;
import com.interstice.item.FlashlightItem;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.data.SpotLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import foundry.veil.api.client.render.light.renderer.LightRenderer;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Lights carried or set off by players: the flashlight beam (a Veil spot light from the eyes of every nearby player
 * holding a lit flashlight, including the local one) and the magnesium flash (a short, very bright point light).
 * Same safety net as the other Veil lights: if the light renderer is unavailable nothing is created.
 */
public final class DeviceLights {
    private static final double RANGE = 64.0;
    private static final Int2ObjectOpenHashMap<LightRenderHandle<SpotLightData>> beams = new Int2ObjectOpenHashMap<>();

    private record Flash(double x, double y, double z, long start, int duration, LightRenderHandle<PointLightData>[] handle) {}

    private static final List<Flash> flashes = new ArrayList<>();
    private static boolean veilOk = true;
    public static int active;

    /** Half-angle of the flashlight cone (degrees) and its reach. */
    public static final float BEAM_ANGLE = 34f, BEAM_LENGTH = 40f;

    private DeviceLights() {}

    /**
     * Where a player's flashlight beam starts (the lamp, in the hand that holds it) and its direction (towards where
     * the player looks, 24 blocks ahead, so the spot lands under the crosshair). In first person the hand is placed
     * from the camera (bottom right of the view, or left for the off hand); otherwise from the body.
     */
    public static Vec3[] beam(Player p, float pt) {
        Minecraft mc = Minecraft.getInstance();
        boolean offhand = FlashlightItem.isLit(p.getOffhandItem()) && !FlashlightItem.isLit(p.getMainHandItem());
        boolean rightArm = (p.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT) != offhand;
        float side = rightArm ? 1f : -1f;
        Vec3 eye = p.getEyePosition(pt);
        Vec3 look = p.getViewVector(pt);
        Vec3 from;
        if (p == mc.player && mc.options.getCameraType().isFirstPerson()) {
            net.minecraft.client.Camera cam = mc.gameRenderer.getMainCamera();
            Vec3 fwd = new Vec3(cam.getLookVector()), up = new Vec3(cam.getUpVector()), left = new Vec3(cam.getLeftVector());
            from = cam.getPosition().add(fwd.scale(0.55)).add(left.scale(-0.36 * side)).add(up.scale(-0.30));
        } else {
            float yaw = (float) Math.toRadians(net.minecraft.util.Mth.rotLerp(pt, p.yBodyRotO, p.yBodyRot));
            Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)), right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
            from = p.getPosition(pt).add(0, p.getBbHeight() * 0.68, 0).add(right.scale(0.36 * side)).add(fwd.scale(0.45));
        }
        Vec3 target = eye.add(look.scale(24));
        Vec3 dir = target.subtract(from).normalize();
        // the cone's apex sits 0.6 block ahead of the lamp: the 3D flashlight (and the hand) are behind it, where Veil's
        // spot light gives nothing, so the lamp does not light itself
        from = from.add(dir.scale(0.6));
        return new Vec3[]{from, dir};
    }

    /** A flash at this position (from the server's {@code EnvEvent.FLASH}). */
    @SuppressWarnings("unchecked")
    public static void flash(double x, double y, double z, int duration) {
        flashes.add(new Flash(x, y, z, System.currentTimeMillis(), Math.max(4, duration), new LightRenderHandle[1]));
    }

    public static void update(Vec3 cam, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (!veilOk || mc.level == null || !ClientConfig.INSTANCE.dynamicLighting) {
            clear();
            return;
        }
        LightRenderer renderer;
        try {
            renderer = VeilRenderSystem.renderer().getLightRenderer();
        } catch (Throwable t) {
            veilOk = false;
            Interstice.LOG.warn("Veil light renderer unavailable; flashlight and flash lights disabled", t);
            clear();
            return;
        }
        try {
            updateBeams(mc, renderer, cam, partialTick);
            updateFlashes(renderer);
        } catch (Throwable t) {
            veilOk = false;
            Interstice.LOG.warn("Failed to update device lights; disabled", t);
            clear();
        }
        active = beams.size() + flashes.size();
    }

    private static void updateBeams(Minecraft mc, LightRenderer renderer, Vec3 cam, float pt) {
        Int2ObjectOpenHashMap<LightRenderHandle<SpotLightData>> keep = new Int2ObjectOpenHashMap<>();
        for (Player p : mc.level.players()) {
            if (!FlashlightItem.isLitIn(p) || p.distanceToSqr(cam) > RANGE * RANGE) continue;
            LightRenderHandle<SpotLightData> h = beams.remove(p.getId());
            if (h == null || !h.isValid()) h = renderer.addLight(new SpotLightData());
            SpotLightData s = h.getLightData();
            Vec3[] beam = beam(p, pt);
            Vec3 from = beam[0], dir = beam[1];
            s.getPositionMutable().set(from.x, from.y, from.z);
            // Veil takes a point to the light's space as R (p - pos) and the beam runs along that space's +Z: R maps the
            // beam direction onto +Z (the old rotationTo(-Z -> dir) aimed the beam sideways or behind the player)
            s.getOrientationMutable().set(new Quaternionf().rotationTo((float) dir.x, (float) dir.y, (float) dir.z, 0, 0, 1));
            // Veil's spot shader: the cone's edge is setSize (radians), setAngle only the width of the soft edge inside it
            // (falloff = smoothstep(size, size - angle, angle to the axis)); the old size 0.2 made an 11-degree spot
            s.setSize((float) Math.toRadians(BEAM_ANGLE));
            s.setAngle((float) Math.toRadians(16));
            s.setDistance(BEAM_LENGTH);
            s.setColor(1f, 0.93f, 0.8f);
            float flicker = ClientState.lightFailure > 0.3f && (System.currentTimeMillis() / 90 + p.getId()) % 7 == 0 ? 0.3f : 1f;
            s.setBrightness(2.4f * flicker);
            s.setInscatteringStrength(ClientConfig.INSTANCE.volumetricLight ? 0.45f : 0f);
            h.markDirty();
            keep.put(p.getId(), h);
        }
        for (LightRenderHandle<SpotLightData> h : beams.values()) free(h);
        beams.clear();
        beams.putAll(keep);
    }

    private static void updateFlashes(LightRenderer renderer) {
        long now = System.currentTimeMillis();
        for (Iterator<Flash> it = flashes.iterator(); it.hasNext(); ) {
            Flash f = it.next();
            float t = (now - f.start) / (f.duration * 50f);
            if (t >= 1f) {
                free(f.handle[0]);
                it.remove();
                continue;
            }
            if (f.handle[0] == null || !f.handle[0].isValid()) f.handle[0] = renderer.addLight(new PointLightData());
            PointLightData p = f.handle[0].getLightData();
            p.setPosition(f.x, f.y, f.z);
            p.setRadius(22f);
            p.setColor(1f, 0.97f, 0.92f);
            // instant peak, fast fall-off with a short afterglow
            float k = t < 0.08f ? 1f : (float) Math.pow(1f - (t - 0.08f) / 0.92f, 2.4);
            p.setBrightness(9f * k);
            p.setInscatteringStrength(0.3f * k);
            f.handle[0].markDirty();
        }
    }

    private static void free(LightRenderHandle<?> h) {
        try {
            if (h != null && h.isValid()) h.free();
        } catch (Throwable ignored) {
        }
    }

    public static void clear() {
        for (LightRenderHandle<SpotLightData> h : beams.values()) free(h);
        beams.clear();
        for (Flash f : flashes) free(f.handle[0]);
        flashes.clear();
        active = 0;
    }
}
