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
    private SkinnedMeshPose hordePose;

    public AdherentMeshModel() {
        super(RenderType::entityCutoutNoCull);
    }

    @Override
    public void setupAnim(AdherentEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        SkinnedMeshAsset asset = SkinnedMeshAsset.adherent();
        current = null;
        if (asset == null) return;
        AdherentAnimator.State s = AdherentAnimator.state(entity);
        if (s.pose == null || s.pose.asset() != asset) s.pose = new SkinnedMeshPose(asset);
        AdherentAnimator.apply(s, s.pose);
        s.pose.skin(0);
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
    public boolean renderHorde(PoseStack poseStack, VertexConsumer consumer, int light, int overlay, float seconds) {
        SkinnedMeshAsset asset = SkinnedMeshAsset.adherent();
        if (asset == null) return false;
        if (hordePose == null || hordePose.asset() != asset) hordePose = new SkinnedMeshPose(asset);
        hordePose.reset();
        hordePose.add(asset.clip("climb"), seconds, 1f);
        hordePose.skin(1);
        hordePose.emit(poseStack.last(), consumer, light, overlay, 0xFFFFFFFF);
        return true;
    }
}
