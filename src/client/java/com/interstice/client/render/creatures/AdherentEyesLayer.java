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
 * Pure red stays below the bloom threshold of the atmosphere pipeline (luminance about 0.26 against a
 * 0.5 threshold), so the eyes do not bloom: they read as two or three hard points in the dark.
 */
public class AdherentEyesLayer extends RenderLayer<AdherentEntity, AdherentMeshModel> {
    private static final ResourceLocation[] TEXTURES = new ResourceLocation[Species.values().length];

    static {
        for (Species sp : Species.values()) TEXTURES[sp.ordinal()] = Interstice.id("textures/entity/" + sp.id + "_eyes.png");
    }

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
        p.emitEyes(pose.last(), buffers.getBuffer(RenderType.eyes(TEXTURES[entity.species().ordinal()])),
                0xF000F0, OverlayTexture.NO_OVERLAY, FastColor.ARGB32.color(255, c, c, c));
        // where the eyes are in the world: the entity pose stack is camera-relative (translation only)
        pose.last().pose().transformPosition(centre);
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        CreatureEyeLights.offer(entity, cam.x + centre.x, cam.y + centre.y, cam.z + centre.z, k);
    }
}
