package com.interstice.client.effects;

import com.interstice.Interstice;
import com.interstice.client.ClientState;
import com.interstice.client.config.ClientConfig;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.screenshake.type.ScreenShakeType;
import net.minecraft.util.RandomSource;

/**
 * Earthquake camera shake through Veil's screen-shake system. Strength follows
 * the live quake signal (so it swells and fades with the rumble) and is scaled
 * by the accessibility setting; 0 disables it entirely.
 */
public final class QuakeShake extends ScreenShakeType {
    private static QuakeShake active;

    private QuakeShake(int length) {
        super(RandomSource.create(), length);
    }

    @Override
    protected float getStrength() {
        float s = ClientConfig.INSTANCE.cameraShake;
        if (ClientConfig.INSTANCE.reduceIntenseEffects) s *= 0.5f;
        return (ClientState.quake + thud) * s * 0.42f;
    }

    /** A short extra tremor (a Burden's step close by), decaying over about half a second. */
    private static float thud;
    private static long thudAt;

    public static void thud(float k) {
        thud = Math.max(thud, k);
        thudAt = System.currentTimeMillis();
        if (ClientConfig.INSTANCE.cameraShake <= 0f) return;
        if (active == null || active.isRemoved()) {
            try {
                active = new QuakeShake(20 * 30);
                VeilRenderSystem.renderer().getScreenShakeManager().addScreenShake(active);
            } catch (Throwable t) {
                active = null;
            }
        }
    }

    /** Ensures a shake is running while a quake is active. */
    public static void update() {
        if (thud > 0) thud = Math.max(0f, thud * (1f - (System.currentTimeMillis() - thudAt) / 600f));
        if (!ClientState.inInterstice || ClientState.quake + thud <= 0.01f || ClientConfig.INSTANCE.cameraShake <= 0f) {
            if (active != null && ClientState.quake + thud <= 0.01f) {
                active.remove();
                active = null;
            }
            return;
        }
        if (active == null || active.isRemoved()) {
            try {
                active = new QuakeShake(20 * 30);
                VeilRenderSystem.renderer().getScreenShakeManager().addScreenShake(active);
            } catch (Throwable t) {
                Interstice.LOG.warn("Veil screen shake unavailable", t);
                active = null;
            }
        }
    }
}
