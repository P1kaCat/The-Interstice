package com.interstice.client.render.creatures;

import com.interstice.client.render.creatures.mesh.SkinnedMeshAsset;
import com.interstice.client.render.creatures.mesh.SkinnedMeshPose;
import com.interstice.entity.AdherentEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.RenderType;

/**
 * Entity model adapter for the skinned Adherent mesh. The vanilla mob renderer still handles body
 * rotation, death, the red hurt overlay, lighting and render types; this class poses, skins and
 * emits the organic mesh instead of model parts.
 */
public class AdherentMeshModel extends EntityModel<AdherentEntity> {
    private SkinnedMeshPose current;

    public AdherentMeshModel() {
        super(RenderType::entityCutoutNoCull);
    }

    /** Real entities farther than this (blocks) are skinned on the reduced LOD (no moss; eyes kept). */
    public static final double LOD1_DISTANCE = 20.0;

    /** The pose skinned for the entity being rendered (render layers use it: the eyes). */
    public SkinnedMeshPose current() {
        return current;
    }

    @Override
    public void setupAnim(AdherentEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        SkinnedMeshAsset asset = SkinnedMeshAsset.of(entity.species());
        current = null;
        if (asset == null) return;
        AdherentAnimator.State s = AdherentAnimator.state(entity);
        if (s.pose == null || s.pose.asset() != asset) s.pose = new SkinnedMeshPose(asset);
        AdherentAnimator.apply(s, s.pose);
        net.minecraft.world.phys.Vec3 cam = net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        boolean far = entity.distanceToSqr(cam) > LOD1_DISTANCE * LOD1_DISTANCE;
        s.pose.gravity.set(s.gravity);
        s.pose.accel.set(s.accelModel);
        s.pose.simDt = s.frameDt;
        s.pose.skin(far ? 1 : 0);
        current = s.pose;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer consumer, int light, int overlay, int color) {
        if (current != null) current.emit(poseStack.last(), consumer, light, overlay, color);
    }

    /**
     * Horde mid-distance instances: the run clip at an explicit time, on the reduced LOD.
     * The caller has already set up the pose stack (orientation, 180 degree flip, ground offset).
     */
    private final SkinnedMeshPose[] hordePoses = new SkinnedMeshPose[com.interstice.entity.species.Species.values().length];

    public boolean renderHorde(PoseStack poseStack, VertexConsumer consumer, int light, int overlay, float seconds) {
        return renderHorde(com.interstice.entity.species.Species.ADHERENT, poseStack, consumer, light, overlay, seconds);
    }

    /** Horde instance posed {@code cycles} climb cycles into its loop (each species has its own clip length). */
    public boolean renderHordeCycle(com.interstice.entity.species.Species sp, PoseStack poseStack, VertexConsumer consumer, int light, int overlay, float cycles) {
        SkinnedMeshAsset asset = SkinnedMeshAsset.of(sp);
        if (asset == null) return false;
        SkinnedMeshAsset.Clip c = asset.clip("climb");
        return renderHorde(sp, poseStack, consumer, light, overlay, cycles * (c != null ? c.length : 2.6f));
    }

    /** The eyes of a horde instance (emissive pass): the same pose as {@link #renderHordeCycle}. */
    public void renderHordeEyes(com.interstice.entity.species.Species sp, PoseStack poseStack, VertexConsumer consumer, float cycles, float glow) {
        SkinnedMeshAsset asset = SkinnedMeshAsset.of(sp);
        if (asset == null) return;
        SkinnedMeshAsset.Clip c = asset.clip("climb");
        SkinnedMeshPose hordePose = hordePoses[sp.ordinal()];
        if (hordePose == null || hordePose.asset() != asset) hordePoses[sp.ordinal()] = hordePose = new SkinnedMeshPose(asset);
        hordePose.reset();
        hordePose.add(c, cycles * (c != null ? c.length : 2.6f), 1f);
        hordePose.skin(1);
        int k = Math.max(0, Math.min(255, (int) (255 * glow)));
        hordePose.emitEyes(poseStack.last(), consumer, 0xF000F0, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                net.minecraft.util.FastColor.ARGB32.color(255, k, k, k));
    }

    public boolean renderHorde(com.interstice.entity.species.Species sp, PoseStack poseStack, VertexConsumer consumer, int light, int overlay, float seconds) {
        SkinnedMeshAsset asset = SkinnedMeshAsset.of(sp);
        if (asset == null) return false;
        SkinnedMeshPose hordePose = hordePoses[sp.ordinal()];
        if (hordePose == null || hordePose.asset() != asset) hordePoses[sp.ordinal()] = hordePose = new SkinnedMeshPose(asset);
        hordePose.reset();
        hordePose.add(asset.clip("climb"), seconds, 1f);
        hordePose.skin(1);
        hordePose.emit(poseStack.last(), consumer, light, overlay, 0xFFFFFFFF);
        return true;
    }
}
