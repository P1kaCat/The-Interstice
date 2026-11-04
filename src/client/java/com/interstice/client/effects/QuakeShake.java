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
        return ClientState.quake * s * 0.42f;
    }

    /** Ensures a shake is running while a quake is active. */
    public static void update() {
        if (!ClientState.inInterstice || ClientState.quake <= 0.01f || ClientConfig.INSTANCE.cameraShake <= 0f) {
            if (active != null && ClientState.quake <= 0.01f) {
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
