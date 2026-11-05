package com.interstice.client.render.creatures;

import com.interstice.Interstice;
import com.interstice.client.render.creatures.mesh.SkinnedMeshPose;
import com.interstice.client.render.veil.CreatureEyeLights;
import com.interstice.entity.AdherentEntity;
import com.interstice.entity.species.Species;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The eyes: small, deep red, recessed in the sockets. They are drawn a second time with the vanilla
 * "eyes" render type (additive, full bright, ignores light and shadow, still fogged), from a per-species
 * emissive atlas ({@code textures/entity/<species>_eyes.png}) in which only the eyes are lit. This is the
 * part that works everywhere. On top of it, when Veil's dynamic lighting is available, the nearest few
 * creatures also cast a faint red point light from their eyes ({@link CreatureEyeLights}).
 * <p>
 * Corridor rework (2026-11-05): the atlases are graded to a saturated crimson ({@code tools/creature/eye_glow.py}),
 * the emissive pass is drawn twice (additive: twice as bright, which also survives the black fog a little longer
 * than the body), and a small soft halo is drawn around the eyes with the same additive, depth-tested render type:
 * it never shows through walls, it is fogged like the rest, and it does not need bloom. Pure red stays below
 * the bloom threshold of the atmosphere pipeline, so whatever bloom there is stays restrained.
 */
public class AdherentEyesLayer extends RenderLayer<AdherentEntity, AdherentMeshModel> {
    private static final ResourceLocation[] TEXTURES = new ResourceLocation[Species.values().length];

    static {
        for (Species sp : Species.values()) TEXTURES[sp.ordinal()] = Interstice.id("textures/entity/" + sp.id + "_eyes.png");
    }

    private static final ResourceLocation HALO = Interstice.id("textures/entity/eye_halo.png");
    /** Halo radius (blocks) and strength; small on purpose: a glint around the eyes, not a glowing ball. */
    private static final float HALO_RADIUS = 0.2f, HALO_STRENGTH = 0.55f;

    private final Vector3f centre = new Vector3f();

    public AdherentEyesLayer(RenderLayerParent<AdherentEntity, AdherentMeshModel> parent) {
        super(parent);
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, AdherentEntity entity, float limbSwing,
                       float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        SkinnedMeshPose p = getParentModel().current();
        if (p == null || entity.isInvisible()) return;
        float k = AdherentAnimator.eyeGlow(entity, partialTick);
        if (k <= 0.01f) return;
        if (!p.eyeCentre(centre)) return;   // an asset without eyes
        int c = Mth.clamp((int) (255 * Math.min(1f, k)), 0, 255);
        var eyes = buffers.getBuffer(RenderType.eyes(TEXTURES[entity.species().ordinal()]));
        int col = FastColor.ARGB32.color(255, c, c, c);
        p.emitEyes(pose.last(), eyes, 0xF000F0, OverlayTexture.NO_OVERLAY, col);
        p.emitEyes(pose.last(), eyes, 0xF000F0, OverlayTexture.NO_OVERLAY, col);   // second additive pass: brighter
        // where the eyes are in the world: the entity pose stack is camera-relative (translation only)
        pose.last().pose().transformPosition(centre);
        net.minecraft.client.Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 cam = camera.getPosition();
        CreatureEyeLights.offer(entity, cam.x + centre.x, cam.y + centre.y, cam.z + centre.z, k);
        halo(buffers, camera, centre, k);
    }

    /** A camera-facing soft halo at the eyes (camera-relative coordinates, the entity pose is translation only). */
    private static void halo(MultiBufferSource buffers, net.minecraft.client.Camera camera, Vector3f at, float k) {
        float dist = at.length();
        // a little stronger with distance so the eyes stay readable as they shrink on screen, until the fog takes them
        float s = HALO_STRENGTH * Math.min(1f, k) * Mth.clamp(0.6f + dist / 24f, 0.6f, 1.5f);
        int c = Mth.clamp((int) (255 * s), 0, 255);
        if (c < 3) return;
        Vector3f l = new Vector3f(camera.getLeftVector()).mul(HALO_RADIUS);
        Vector3f u = new Vector3f(camera.getUpVector()).mul(HALO_RADIUS);
        // pulled a little towards the camera so the halo is not cut by the creature's own face
        Vector3f o = new Vector3f(at).mul(1f - Math.min(0.3f, 0.12f / Math.max(0.5f, dist)));
        var vc = buffers.getBuffer(RenderType.eyes(HALO));
        org.joml.Matrix4f id = new org.joml.Matrix4f();
        int color = FastColor.ARGB32.color(255, c, c, c);
        vertex(vc, id, o.x + l.x + u.x, o.y + l.y + u.y, o.z + l.z + u.z, 0, 0, color);
        vertex(vc, id, o.x + l.x - u.x, o.y + l.y - u.y, o.z + l.z - u.z, 0, 1, color);
        vertex(vc, id, o.x - l.x - u.x, o.y - l.y - u.y, o.z - l.z - u.z, 1, 1, color);
        vertex(vc, id, o.x - l.x + u.x, o.y - l.y + u.y, o.z - l.z + u.z, 1, 0, color);
    }

    private static void vertex(com.mojang.blaze3d.vertex.VertexConsumer vc, org.joml.Matrix4f m, float x, float y, float z, float u, float v, int color) {
        vc.addVertex(m, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(0, 1, 0);
    }
}
