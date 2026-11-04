package com.interstice.client.render.atmosphere;

import com.interstice.client.ClientState;
import com.interstice.client.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * Single source of truth for atmospheric parameters. Targets are derived from
 * event signals and settings; current values ease towards them so every
 * transition (fog thickening during a quake, red emergency grading, the
 * pre-Stampede silence) is smooth. Read by the vanilla fog mixin, the sky
 * renderer and the Veil post-processing uniforms.
 */
public final class AtmosphereController {
    private AtmosphereController() {}

    public static float fogStart = 4f, fogEnd = 120f;
    public static float fogR = 0.085f, fogG = 0.092f, fogB = 0.105f;
    public static float exposure = 1.3f;
    public static float density = 1f;
    public static float emergency;
    public static float vignette = 0.35f;
    public static float desaturation = 0.3f;
    public static float grain = 0.04f;
    public static float aberration;

    private static long lastNanos;

    public static void update() {
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0.016f : Mth.clamp((now - lastNanos) / 1e9f, 0f, 0.1f);
        lastNanos = now;

        ClientConfig cfg = ClientConfig.INSTANCE;
        Minecraft mc = Minecraft.getInstance();
        float renderDist = mc.gameRenderer.getRenderDistance();

        float q = ClientState.quake, prem = ClientState.premonition, emer = ClientState.emergency;
        float dens = cfg.fogDensity * (1f + q * 0.15f + prem * 0.1f + ClientState.hordeActive * 0.1f);
        float tEnd = Math.min(renderDist, 170f) / Math.max(0.3f, dens);
        float tStart = 2f + 6f * (1f - Math.min(1f, dens));

        float tr = 0.085f, tg = 0.092f, tb = 0.105f;
        tr = Mth.lerp(emer * 0.4f, tr, 0.13f);
        tg = Mth.lerp(emer * 0.4f, tg, 0.045f);
        tb = Mth.lerp(emer * 0.4f, tb, 0.04f);
        float dark = 1f - ClientState.lightFailure * 0.35f;
        tr *= dark; tg *= dark; tb *= dark;
        // dust haze during quakes is warmer and lighter
        tr = Mth.lerp(q * 0.25f, tr, 0.12f);
        tg = Mth.lerp(q * 0.25f, tg, 0.11f);
        tb = Mth.lerp(q * 0.25f, tb, 0.095f);

        float k = 1f - (float) Math.exp(-dt * 1.6f);
        fogEnd = Mth.lerp(k, fogEnd, tEnd);
        fogStart = Mth.lerp(k, fogStart, tStart);
        fogR = Mth.lerp(k, fogR, tr);
        fogG = Mth.lerp(k, fogG, tg);
        fogB = Mth.lerp(k, fogB, tb);
        density = Mth.lerp(k, density, dens);
        emergency = Mth.lerp(k, emergency, emer);

        float tExposure = 1.3f - ClientState.lightFailure * 0.25f - ClientState.searchTension * 0.08f;
        exposure = Mth.lerp(k * 0.5f, exposure, tExposure);
        vignette = Mth.lerp(k, vignette, 0.32f + ClientState.searchTension * 0.25f + q * 0.15f + ClientState.silence * 0.1f);
        desaturation = Mth.lerp(k, desaturation, 0.28f + ClientState.silence * 0.25f);
        boolean intense = !cfg.reduceIntenseEffects;
        grain = intense ? 0.035f + q * 0.05f + ClientState.searchTension * 0.02f : 0.015f;
        aberration = intense ? q * 0.0035f + ClientState.hordeActive * 0.0008f : 0f;
    }
}
