package com.interstice.client.render.creatures;

import com.interstice.Interstice;
import com.interstice.entity.AdherentEntity;
import com.interstice.entity.species.Species;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Renders the Adherent. On walls the whole body is re-oriented so its belly
 * faces the wall surface and its head points along its real direction of
 * travel on the wall plane (derived from motion, not from yaw). Falling
 * creatures tumble. The body's orientation is also handed to the animator (gravity and acceleration in
 * the model's own space) so the moss hangs and swings the right way on the ground, on walls and in a
 * fall. The eyes glow through {@link AdherentEyesLayer}.
 */
public class AdherentRenderer extends MobRenderer<AdherentEntity, AdherentMeshModel> {
    public static final ResourceLocation TEXTURE = Interstice.id("textures/entity/adherent.png");
    private static final ResourceLocation[] TEXTURES = new ResourceLocation[Species.values().length];

    static {
        for (Species sp : Species.values()) TEXTURES[sp.ordinal()] = Interstice.id("textures/entity/" + sp.id + ".png");
    }

    public static ResourceLocation texture(Species sp) {
        return TEXTURES[sp.ordinal()];
    }

    private final float groundShadow;

    public AdherentRenderer(EntityRendererProvider.Context ctx) {
        this(ctx, Species.ADHERENT);
    }

    public AdherentRenderer(EntityRendererProvider.Context ctx, Species species) {
        super(ctx, new AdherentMeshModel(), 0.93f * species.t.width);
        this.groundShadow = 0.93f * species.t.width;
        addLayer(new AdherentEyesLayer(this));
    }

    private final Quaternionf orient = new Quaternionf();

    /** Gravity and acceleration into the model's space, from the orientation set up for this frame. */
    private void handOrientation(AdherentEntity entity) {
        AdherentAnimator.State st = AdherentAnimator.state(entity);
        Quaternionf inv = new Quaternionf(orient).conjugate();
        inv.transform(st.gravity.set(0, -1, 0));
        inv.transform(st.accelModel.set(st.accelWorld));
    }

    @Override
    public ResourceLocation getTextureLocation(AdherentEntity entity) {
        return TEXTURES[entity.species().ordinal()];
    }

    @Override
    public void render(AdherentEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        AdherentAnimator.update(entity, partialTick);
        this.shadowRadius = entity.mode() == AdherentEntity.Mode.WALL ? 0f : groundShadow;
        super.render(entity, yaw, partialTick, pose, buffers, light);
        com.interstice.client.audio.CreatureFootsteps.update(entity, getModel().current());
    }

    @Override
    protected void setupRotations(AdherentEntity entity, PoseStack pose, float bob, float yBodyRot, float partialTick, float scale) {
        AdherentAnimator.State st0 = AdherentAnimator.state(entity);
        float wb = Float.isNaN(st0.wallBlend) ? (entity.mode() == AdherentEntity.Mode.WALL ? 1f : 0f) : st0.wallBlend;
        if (entity.mode() != AdherentEntity.Mode.FALLING && wb > 0.002f && wb < 0.998f) {
            // turning between the floor and the wall: the ground orientation eased into the wall one (rearing onto
            // the wall when it climbs, rolling off it onto the floor when it comes down)
            int side = entity.side();
            float a = st0.heading;
            Vector3f up = new Vector3f(-side, 0, 0);
            Vector3f fwd = new Vector3f(0, Mth.sin(a), Mth.cos(a));
            Vector3f back = new Vector3f(fwd).negate();
            Vector3f right = new Vector3f(up).cross(back);
            Quaternionf qw = new Quaternionf().setFromNormalized(new Matrix3f(right, up, back));
            Quaternionf qg = new Quaternionf().rotationY((float) Math.toRadians(180f - yBodyRot));
            float k = wb * wb * (3 - 2 * wb);
            orient.set(qg).slerp(qw, k);
            pose.translate(side * entity.getBbWidth() / 2f * k, entity.getBbHeight() / 2f * k, 0);
            pose.mulPose(new Quaternionf(orient));
            pose.translate(0, -0.15f * k, 0);
            handOrientation(entity);
            return;
        }
        if (entity.mode() == AdherentEntity.Mode.GROUND && st0.climbTilt > 0.002f) {
            // going up a facade: the body turns upright, belly to the wall it faces, head up
            float yawR = (float) Math.toRadians(yBodyRot);
            Vector3f f = new Vector3f(-Mth.sin(yawR), 0, Mth.cos(yawR));
            Vector3f up = new Vector3f(f).negate();
            Vector3f back = new Vector3f(0, -1, 0);
            Vector3f right = new Vector3f(up).cross(back);
            Quaternionf qc = new Quaternionf().setFromNormalized(new Matrix3f(right, up, back));
            Quaternionf qg = new Quaternionf().rotationY((float) Math.toRadians(180f - yBodyRot));
            float k = st0.climbTilt * st0.climbTilt * (3 - 2 * st0.climbTilt);
            orient.set(qg).slerp(qc, k);
            pose.translate(f.x * entity.getBbWidth() / 2f * k, entity.getBbHeight() / 2f * k, f.z * entity.getBbWidth() / 2f * k);
            pose.mulPose(new Quaternionf(orient));
            pose.translate(0, -0.15f * k, 0);
            handOrientation(entity);
            return;
        }
        switch (entity.mode()) {
            case WALL -> {
                int side = entity.side();
                AdherentAnimator.State st = AdherentAnimator.state(entity);
                float a = st.heading;
                // basis: up -> away from the wall, forward (-Z) -> heading on the wall plane
                Vector3f up = new Vector3f(-side, 0, 0);
                Vector3f fwd = new Vector3f(0, Mth.sin(a), Mth.cos(a));
                Vector3f back = new Vector3f(fwd).negate();
                Vector3f right = new Vector3f(up).cross(back);
                Matrix3f m = new Matrix3f(right, up, back);
                pose.translate(side * entity.getBbWidth() / 2f, entity.getBbHeight() / 2f, 0);
                orient.setFromNormalized(m);
                pose.mulPose(new Quaternionf(orient));
                pose.translate(0, -0.15f, 0);
            }
            case FALLING -> {
                super.setupRotations(entity, pose, bob, yBodyRot, partialTick, scale);
                float t = entity.tickCount + partialTick;
                pose.translate(0, 0.9f, 0);
                pose.mulPose(Axis.XP.rotationDegrees(t * 11f + entity.getId() * 37));
                pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(t * 0.3f) * 40f));
                pose.translate(0, -0.9f, 0);
                orient.rotationY((float) Math.toRadians(180f - yBodyRot))
                        .rotateX((float) Math.toRadians(t * 11f + entity.getId() * 37))
                        .rotateZ((float) Math.toRadians(Mth.sin(t * 0.3f) * 40f));
            }
            default -> {
                super.setupRotations(entity, pose, bob, yBodyRot, partialTick, scale);
                orient.rotationY((float) Math.toRadians(180f - yBodyRot));
            }
        }
        handOrientation(entity);
    }

    @Override
    protected void scale(AdherentEntity entity, PoseStack pose, float partialTick) {
        pose.scale(AdherentAnimator.SCALE, AdherentAnimator.SCALE, AdherentAnimator.SCALE);
        // squeezing through a one-block gap: the body narrows (ribs folding in), a little flatter too
        float sq = AdherentAnimator.state(entity).squeeze;
        if (sq > 0.001f) pose.scale(1f - 0.34f * sq, 1f - 0.08f * sq, 1f + 0.06f * sq);
    }

    @Override
    protected float getFlipDegrees(AdherentEntity entity) {
        return 0f; // corpses do not tip over sideways: they slump
    }
}
