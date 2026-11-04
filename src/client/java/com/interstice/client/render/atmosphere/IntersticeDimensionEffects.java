package com.interstice.client.render.atmosphere;

import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.world.phys.Vec3;

/** No sky, no clouds, no ground horizon: only fog colour, driven by {@link AtmosphereController}. */
public final class IntersticeDimensionEffects extends DimensionSpecialEffects {
    public IntersticeDimensionEffects() {
        super(Float.NaN, false, SkyType.NONE, false, false);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
        return new Vec3(AtmosphereController.fogR, AtmosphereController.fogG, AtmosphereController.fogB);
    }

    @Override
    public boolean isFoggyAt(int x, int z) {
        return false;
    }
}
