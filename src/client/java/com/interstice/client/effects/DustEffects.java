package com.interstice.client.effects;

import com.interstice.client.ClientState;
import com.interstice.client.config.ClientConfig;
import com.interstice.registry.ModEntities;
import com.interstice.world.IntersticeLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Budgeted, event-driven dust: ambient motes around the camera, trickles from
 * the walls during premonitions, heavy falls during earthquakes and where the
 * horde runs. The per-second budget comes from the graphics preset times the
 * particle setting; spawning stops when the budget for the second is spent.
 */
public final class DustEffects {
    private static final RandomSource rng = RandomSource.create();
    private static int spentThisSecond;
    private static long second;
    public static int spawnedLastSecond;

    private DustEffects() {}

    private static int budget() {
        return (int) (ClientConfig.INSTANCE.preset.particleBudget * ClientConfig.INSTANCE.particles);
    }

    private static boolean take() {
        Minecraft mc = Minecraft.getInstance();
        long s = mc.level == null ? 0 : mc.level.getGameTime() / 20;
        if (s != second) {
            second = s;
            spawnedLastSecond = spentThisSecond;
            spentThisSecond = 0;
        }
        if (spentThisSecond >= budget()) return false;
        spentThisSecond++;
        return true;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || !ClientState.inInterstice || mc.isPaused()) return;
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        IntersticeLayout L = ClientState.layout;
        int perTick = Math.max(1, budget() / 20);

        // motes
        for (int i = 0; i < Math.max(1, perTick / 6); i++) {
            if (rng.nextFloat() > 0.5f || !take()) continue;
            level.addParticle(ModEntities.MOTE, cam.x + rng.nextGaussian() * 7, cam.y + rng.nextGaussian() * 3, cam.z + rng.nextGaussian() * 7,
                    0, 0, 0);
        }
        float q = ClientState.quake, prem = ClientState.premonition, horde = ClientState.hordeActive;
        int heavy = (int) (perTick * (q * 1.5f + prem * 0.25f + horde * 0.5f));
        for (int i = 0; i < heavy; i++) {
            if (!take()) break;
            double x, y, z;
            if (rng.nextFloat() < 0.6f) {
                int side = rng.nextBoolean() ? -1 : 1;
                z = cam.z + rng.nextGaussian() * 25;
                x = L.faceX(side, z) - side * (0.6 + rng.nextDouble() * 2);
                y = cam.y + 4 + rng.nextDouble() * 45;
            } else {
                x = cam.x + rng.nextGaussian() * 10;
                z = cam.z + rng.nextGaussian() * 10;
                y = cam.y + 8 + rng.nextDouble() * 20;
            }
            level.addParticle(ModEntities.FALLING_DUST, x, y, z, rng.nextGaussian() * 0.01, -0.05, rng.nextGaussian() * 0.01);
        }
    }

    /** A localised burst from a wall surface (debris sound origins use this). */
    public static void burstAt(double x, double y, double z, int side, int count) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        for (int i = 0; i < count; i++) {
            if (!take()) return;
            mc.level.addParticle(ModEntities.FALLING_DUST, x + rng.nextGaussian() * 0.8, y + rng.nextGaussian() * 0.8, z + rng.nextGaussian() * 1.5,
                    -side * rng.nextDouble() * 0.03, -0.02, rng.nextGaussian() * 0.01);
        }
    }
}
