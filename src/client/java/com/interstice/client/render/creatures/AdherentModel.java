package com.interstice.client.render.creatures;

import com.interstice.Interstice;
import com.interstice.entity.AdherentEntity;
import net.minecraft.client.animation.KeyframeAnimations;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import org.joml.Vector3f;

/**
 * The Adherent. Geometry and animations are authored in Blockbench
 * (models/adherent.bbmodel) and compiled by tools/bbmodel/bbmodel_to_java.py into
 * {@link AdherentGeometry} and {@link AdherentAnimations}. Each frame the pose is reset to
 * the rest pose and every active animation layer is added with its blend weight
 * (see {@link AdherentAnimator}).
 */
public class AdherentModel extends HierarchicalModel<AdherentEntity> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Interstice.id("adherent"), "main");
    private static final Vector3f CACHE = new Vector3f();

    private final ModelPart root;

    public AdherentModel(ModelPart root) {
        this.root = root;
    }

    public static LayerDefinition createLayer() {
        return AdherentGeometry.createLayer();
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(AdherentEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        AdherentAnimator.State s = AdherentAnimator.state(entity);
        for (AdherentAnimator.Layer layer : AdherentAnimator.Layer.values()) {
            float w = s.weight(layer);
            if (w > 0.002f) KeyframeAnimations.animate(this, layer.def, s.timeMs(layer), w, CACHE);
        }
    }

    /** Pose used by the horde's mid-distance instances: the run cycle at an explicit time. */
    public void poseRun(long timeMs) {
        root.getAllParts().forEach(ModelPart::resetPose);
        KeyframeAnimations.animate(this, AdherentAnimations.RUN, timeMs, 1f, CACHE);
    }
}
