package com.interstice.client.effects;

import com.interstice.client.config.ClientConfig;
import com.interstice.client.render.veil.DeviceLights;
import com.interstice.client.render.veil.LampLightManager;
import com.interstice.item.FlashlightItem;
import foundry.veil.api.client.render.post.PostPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Dust drifting through the light beams, drawn by the Veil fog shader ({@code atmosphere/fog.fsh}): for each pixel
 * the shader finds the part of the view ray inside each light cone and looks for specks in a 3D grid there; each
 * speck wobbles, twinkles and is lit only by the beam it floats in. This class picks the beams every frame (the
 * flashlights first, then the nearest flood lamps and searchlights, at most 6) and hands them to the shader.
 */
public final class BeamDust {
    /** A light cone: apex, unit direction, half-angle (radians), length, strength 0..1, colour, held in a hand or not. */
    public record Beam(double x, double y, double z, double dx, double dy, double dz, float angle, float length, float strength,
                       float r, float g, float b, boolean handheld) {}

    private static final int MAX = 6;

    private BeamDust() {}

    private static List<Beam> pick(Minecraft mc, Vec3 cam, float pt) {
        List<Beam> out = new ArrayList<>();
        if (mc.level == null || !ClientConfig.INSTANCE.dynamicLighting) return out;
        for (Player p : mc.level.players()) {
            if (!FlashlightItem.isLitIn(p) || p.distanceToSqr(cam) > 40 * 40) continue;
            Vec3[] b = DeviceLights.beam(p, pt);
            out.add(new Beam(b[0].x, b[0].y, b[0].z, b[1].x, b[1].y, b[1].z, (float) Math.toRadians(DeviceLights.BEAM_ANGLE * 0.9f),
                    DeviceLights.BEAM_LENGTH * 0.75f, 0.8f, 1f, 0.93f, 0.8f, true));
            if (out.size() >= MAX) return out;
        }
        List<Beam> spots = new ArrayList<>(LampLightManager.spotBeams);
        spots.sort((a, b) -> Double.compare(dist2(a, cam), dist2(b, cam)));
        for (Beam s : spots) {
            if (out.size() >= MAX) break;
            out.add(s);
        }
        return out;
    }

    private static double dist2(Beam b, Vec3 cam) {
        // distance from the camera to the nearest point of the beam's axis
        double vx = cam.x - b.x(), vy = cam.y - b.y(), vz = cam.z - b.z();
        double t = Math.max(0, Math.min(b.length(), vx * b.dx() + vy * b.dy() + vz * b.dz()));
        double px = vx - b.dx() * t, py = vy - b.dy() * t, pz = vz - b.dz() * t;
        return px * px + py * py + pz * pz;
    }

    /** Called with the other post uniforms, every frame. */
    public static void applyUniforms(PostPipeline p) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        float pt = mc.getTimer().getGameTimeDeltaPartialTick(false);
        List<Beam> beams = pick(mc, cam, pt);
        p.getUniformSafe("BeamCount").setInt(beams.size());
        double fov = Math.toRadians(mc.options.fov().get());
        p.getUniformSafe("PixelAngle").setFloat((float) (2 * Math.tan(fov / 2) / Math.max(1, mc.getWindow().getHeight())));
        p.getUniformSafe("DustDensity").setFloat(0.42f);
        for (int i = 0; i < beams.size(); i++) {
            Beam b = beams.get(i);
            p.getUniformSafe("Beam" + i + "Pos").setVector((float) b.x(), (float) b.y(), (float) b.z());
            p.getUniformSafe("Beam" + i + "Dir").setVector((float) b.dx(), (float) b.dy(), (float) b.dz());
            // w: share of the dust cells holding a speck in this beam (the flashlight's cone is right before the eyes: fewer)
            p.getUniformSafe("Beam" + i + "Params").setVector((float) Math.cos(b.angle()), b.length(), b.strength(), b.handheld() ? 0.4f : 1f);
            p.getUniformSafe("Beam" + i + "Color").setVector(b.r(), b.g(), b.b());
        }
    }
}
