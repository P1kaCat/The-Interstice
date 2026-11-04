package com.interstice.client.render.veil;

import com.interstice.Interstice;
import com.interstice.client.ClientState;
import com.interstice.client.config.ClientConfig;
import com.interstice.client.render.atmosphere.AtmosphereController;
import com.interstice.client.render.chains.ChainRenderer;
import com.interstice.client.render.creatures.HordeRenderer;
import com.interstice.world.IntersticeLayout;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.post.PostPipeline;
import foundry.veil.api.client.render.post.PostProcessingManager;
import foundry.veil.api.event.VeilRenderLevelStageEvent;
import foundry.veil.fabric.event.FabricVeilPostProcessingEvent;
import foundry.veil.fabric.event.FabricVeilRenderLevelStageEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * The Veil rendering subsystem of THE INTERSTICE.
 * <ul>
 *   <li>Post pipeline {@code interstice:atmosphere}: depth-aware volumetric
 *       height fog (ray-marched), multi-pass bloom (Veil dual-filter blur
 *       shaders), exposure, colour grading, vignette, grain and quake
 *       chromatic aberration. A no-bloom variant is swapped in when bloom is off.</li>
 *   <li>Uniforms are pushed every frame from {@link AtmosphereController} in
 *       Veil's pre-post-processing event.</li>
 *   <li>Custom geometry (giant chains, horde) is drawn in Veil's render-stage
 *       event after entities, so it receives Veil deferred lights.</li>
 *   <li>Dynamic lights: {@link LampLightManager}.</li>
 * </ul>
 */
public final class VeilIntegration {
    public static final ResourceLocation ATMOSPHERE = Interstice.id("atmosphere");
    public static final ResourceLocation ATMOSPHERE_NO_BLOOM = Interstice.id("atmosphere_nobloom");
    private static boolean failed;
    private static ResourceLocation activePipeline;
    public static int customStageDraws;

    private VeilIntegration() {}

    public static void init() {
        FabricVeilPostProcessingEvent.PRE.register((name, pipeline, context) -> {
            if (name.equals(ATMOSPHERE) || name.equals(ATMOSPHERE_NO_BLOOM)) applyUniforms(pipeline);
        });
        FabricVeilRenderLevelStageEvent.EVENT.register((stage, levelRenderer, bufferSource, matrixStack, frustumMatrix, projectionMatrix, renderTick, deltaTracker, camera, frustum) -> {
            if (stage != VeilRenderLevelStageEvent.Stage.AFTER_ENTITIES || !ClientState.inInterstice) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) return;
            try {
                ChainRenderer.render(mc.level, camera, bufferSource, frustum);
                HordeRenderer.render(mc.level, camera, bufferSource, frustum, deltaTracker.getGameTimeDeltaPartialTick(false));
                customStageDraws++;
            } catch (Throwable t) {
                if (!failed) Interstice.LOG.error("Interstice custom geometry rendering failed", t);
                failed = true;
            }
        });
    }

    /** Called once per frame on the render thread. */
    public static void frame() {
        ClientConfig cfg = ClientConfig.INSTANCE;
        ResourceLocation wanted = null;
        if (ClientState.inInterstice && cfg.postProcessing) {
            wanted = cfg.bloom && cfg.preset.bloomPasses ? ATMOSPHERE : ATMOSPHERE_NO_BLOOM;
        }
        if (wanted == activePipeline) return;
        try {
            PostProcessingManager post = VeilRenderSystem.renderer().getPostProcessingManager();
            if (activePipeline != null) post.remove(activePipeline);
            if (wanted != null) post.add(900, wanted);
            activePipeline = wanted;
        } catch (Throwable t) {
            if (!failed) Interstice.LOG.error("Could not toggle Veil post pipeline", t);
            failed = true;
        }
    }

    public static ResourceLocation activePipeline() {
        return activePipeline;
    }

    private static void applyUniforms(PostPipeline p) {
        ClientConfig cfg = ClientConfig.INSTANCE;
        float t = (float) (ClientState.time / 20.0);
        p.getUniformSafe("Time").setFloat(t % 3600f);
        p.getUniformSafe("FogColor").setVector(AtmosphereController.fogR, AtmosphereController.fogG, AtmosphereController.fogB);
        p.getUniformSafe("FogDensity").setFloat(AtmosphereController.density);
        p.getUniformSafe("FogEnd").setFloat(AtmosphereController.fogEnd);
        p.getUniformSafe("FogSteps").setInt(cfg.preset.fogSteps);
        p.getUniformSafe("FloorY").setFloat(IntersticeLayout.FLOOR_Y);
        p.getUniformSafe("Emergency").setFloat(AtmosphereController.emergency);
        p.getUniformSafe("Quake").setFloat(ClientState.quake);
        p.getUniformSafe("Exposure").setFloat(AtmosphereController.exposure);
        p.getUniformSafe("Vignette").setFloat(AtmosphereController.vignette);
        p.getUniformSafe("Desaturation").setFloat(AtmosphereController.desaturation);
        p.getUniformSafe("Grain").setFloat(AtmosphereController.grain);
        p.getUniformSafe("Aberration").setFloat(AtmosphereController.aberration);
        p.getUniformSafe("BloomStrength").setFloat(cfg.reduceFlashing ? 0.45f : 0.8f);
        p.getUniformSafe("Silence").setFloat(ClientState.silence);
    }
}
