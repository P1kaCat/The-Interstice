package com.interstice.client.mixin;

import com.interstice.client.ClientState;
import com.interstice.client.render.atmosphere.AtmosphereController;
import com.mojang.blaze3d.shaders.FogShape;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla fog fallback/base layer for the dimension. The volumetric, height-aware
 * fog is done in the Veil post pipeline; this keeps terrain fog consistent with
 * it (and still gives a coherent look when post-processing is disabled).
 */
@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
    @Shadow private static float fogRed;
    @Shadow private static float fogGreen;
    @Shadow private static float fogBlue;

    @Inject(method = "setupColor", at = @At("TAIL"))
    private static void interstice$color(Camera camera, float partialTick, ClientLevel level, int renderDistance, float darken, CallbackInfo ci) {
        if (ClientState.inWheatFarm && camera.getFluidInCamera() == FogType.NONE) {
            fogRed = com.interstice.client.render.wheat.WheatSky.HR;
            fogGreen = com.interstice.client.render.wheat.WheatSky.HG;
            fogBlue = com.interstice.client.render.wheat.WheatSky.HB;
            RenderSystem.clearColor(fogRed, fogGreen, fogBlue, 0f);
            return;
        }
        if (!ClientState.inInterstice || camera.getFluidInCamera() != FogType.NONE) return;
        fogRed = AtmosphereController.fogR;
        fogGreen = AtmosphereController.fogG;
        fogBlue = AtmosphereController.fogB;
        RenderSystem.clearColor(fogRed, fogGreen, fogBlue, 0f);
    }

    @Inject(method = "setupFog", at = @At("TAIL"))
    private static void interstice$fog(Camera camera, FogRenderer.FogMode mode, float renderDistance, boolean thick, float partialTick, CallbackInfo ci) {
        if (ClientState.inWheatFarm && camera.getFluidInCamera() == FogType.NONE) {
            // Level 02: a light evening haze over the whole length of the farm (the far LODs reach the house at the end)
            RenderSystem.setShaderFogStart(250f);
            RenderSystem.setShaderFogEnd(4200f);   // light enough to see the house 1,600 blocks off
            RenderSystem.setShaderFogShape(FogShape.SPHERE);
            return;
        }
        if (!ClientState.inInterstice || camera.getFluidInCamera() != FogType.NONE) return;
        if (mode == FogRenderer.FogMode.FOG_TERRAIN) {
            RenderSystem.setShaderFogStart(AtmosphereController.fogStart);
            // closed (black) before the last chunks, so no edge shows even without the post pipeline
            RenderSystem.setShaderFogEnd(Math.min(renderDistance * 0.9f, AtmosphereController.fogEnd * 1.6f));
            RenderSystem.setShaderFogShape(FogShape.SPHERE);
        }
    }
}
