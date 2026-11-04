package com.interstice.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Sight model for searching creatures. Visibility is the product of:
 * field of view, line of sight (two rays: eyes and torso), local light at the
 * player, crouching, player motion, distance and surrounding cover. The result
 * feeds an awareness value that rises and decays over time; creatures never
 * read the player's position unless this (or hearing) lets them.
 */
public final class Perception {
    public static final double SIGHT_RANGE = 40.0;

    private Perception() {}

    public static float visibility(Mob self, ServerPlayer p, double playerSpeed) {
        if (p.isSpectator() || p.isCreative() || !p.isAlive()) return 0f;
        Vec3 eye = self.getEyePosition();
        Vec3 target = p.getEyePosition();
        double dist = eye.distanceTo(target);
        if (dist > SIGHT_RANGE) return 0f;

        // field of view relative to head direction
        Vec3 look = Vec3.directionFromRotation(0, self.getYHeadRot());
        Vec3 to = target.subtract(eye).multiply(1, 0, 1).normalize();
        double dot = look.x * to.x + look.z * to.z;
        float fov;
        if (dot > 0.34) fov = 1f;           // ~70 degrees either side
        else if (dot > -0.2) fov = 0.3f;    // periphery
        else fov = dist < 3.5 ? 0.5f : 0f;  // behind: only very close presence is felt

        if (fov <= 0) return 0f;

        Level level = self.level();
        int rays = 0;
        if (clear(level, self, eye, target)) rays++;
        if (clear(level, self, eye, p.position().add(0, 0.5, 0))) rays++;
        if (rays == 0) return 0f;
        float los = rays == 2 ? 1f : 0.55f;

        int light = level.getMaxLocalRawBrightness(p.blockPosition());
        float lightF = 0.18f + 0.82f * (light / 15f);
        float stealth = p.isShiftKeyDown() ? 0.42f : 1f;
        if (p.isInvisible()) stealth *= 0.1f;
        float motion = 0.75f + (float) Mth.clamp(playerSpeed / 0.2, 0, 1) * 0.9f;
        float distF = (float) (1.0 - dist / SIGHT_RANGE);
        distF = distF * (0.4f + 0.6f * distF);
        float cover = 1f - 0.11f * enclosure(level, p.blockPosition());

        return fov * los * lightF * stealth * motion * distF * cover;
    }

    private static boolean clear(Level level, Mob self, Vec3 a, Vec3 b) {
        return level.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, self)).getType() == HitResult.Type.MISS;
    }

    /** 0..5: how enclosed the player is (four sides at eye height plus a low ceiling). */
    public static int enclosure(Level level, BlockPos feet) {
        BlockPos eye = feet.above();
        int n = 0;
        for (var d : new BlockPos[]{eye.north(), eye.south(), eye.east(), eye.west()}) {
            if (level.getBlockState(d).isSolidRender(level, d)) n++;
        }
        for (int i = 2; i <= 4; i++) {
            BlockPos up = feet.above(i);
            if (level.getBlockState(up).isSolidRender(level, up)) {
                n++;
                break;
            }
        }
        return n;
    }

    /** Hearing with simple occlusion: walls between halve the effective radius. */
    public static boolean hears(Mob self, NoiseMap.Noise n) {
        Vec3 ear = self.getEyePosition();
        double d = ear.distanceTo(n.pos());
        if (d > n.radius()) return false;
        boolean occluded = !clear(self.level(), self, ear, n.pos());
        return !occluded || d < n.radius() * 0.5;
    }
}
