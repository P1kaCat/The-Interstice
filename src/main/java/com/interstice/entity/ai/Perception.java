package com.interstice.entity.ai;

import com.interstice.entity.AdherentEntity;
import com.interstice.entity.species.Species;
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
 * read the player's position unless this (or hearing, or smell) lets them.
 * <p>
 * Every term is shaped by the species' senses ({@code Species.Traits}): the width of the field of view and of
 * the periphery, where the gaze points (the Fractured looks off to its left), how far it sees, whether it needs
 * light, whether it only sees what moves (the Clinger), and how far it hears.
 */
public final class Perception {
    public static final double SIGHT_RANGE = 40.0;

    private Perception() {}

    /** The senses of a creature (the default, Adherent-like ones for any other mob). */
    private static Species.Traits senses(Mob self) {
        return self instanceof AdherentEntity a ? a.species().t : Species.ADHERENT.t;
    }

    public static double sightRange(Mob self) {
        return senses(self).sightRange;
    }

    public static float visibility(Mob self, ServerPlayer p, double playerSpeed) {
        if (p.isSpectator() || p.isCreative() || !p.isAlive()) return 0f;
        Species.Traits t = senses(self);
        Vec3 eye = self.getEyePosition();
        Vec3 target = p.getEyePosition();
        double dist = eye.distanceTo(target);
        double range = t.sightRange;
        if (dist > range) return 0f;

        // field of view around the gaze (head direction, turned by the species' offset)
        Vec3 look = Vec3.directionFromRotation(0, self.getYHeadRot() - t.fovOffset);
        Vec3 to = target.subtract(eye).multiply(1, 0, 1).normalize();
        double angle = Math.toDegrees(Math.acos(Mth.clamp(look.x * to.x + look.z * to.z, -1.0, 1.0)));
        float fov;
        if (angle <= t.fov) fov = 1f;
        else if (angle <= t.periphery) fov = 0.3f;   // periphery
        else fov = dist < 3.5 ? 0.5f : 0f;           // behind: only very close presence is felt

        if (fov <= 0) return 0f;

        Level level = self.level();
        int rays = 0;
        if (clear(level, self, eye, target)) rays++;
        if (clear(level, self, eye, p.position().add(0, 0.5, 0))) rays++;
        if (rays == 0) return 0f;
        float los = rays == 2 ? 1f : 0.55f;

        int light = level.getMaxLocalRawBrightness(p.blockPosition());
        // a lit flashlight in hand makes the player a light source (and the beam itself is seen from afar)
        if (com.interstice.item.FlashlightItem.isLitIn(p)) light = Math.max(light, 13);
        float lightF = t.darkVision + (1f - t.darkVision) * (0.18f + 0.82f * (light / 15f));
        float stealth = p.isShiftKeyDown() ? 0.42f : 1f;
        if (p.isInvisible()) stealth *= 0.1f;
        float moving = (float) Mth.clamp(playerSpeed / 0.2, 0, 1);
        float motion = 0.75f + moving * 0.9f;
        // eyes that only catch movement: a player who keeps still all but disappears
        motion *= (1f - t.motionOnly) + t.motionOnly * (float) Mth.clamp(playerSpeed / 0.06, 0, 1);
        float distF = (float) (1.0 - dist / range);
        distF = distF * (0.4f + 0.6f * distF);
        float cover = 1f - 0.11f * enclosure(level, p.blockPosition());

        return fov * los * lightF * stealth * motion * distF * cover;
    }

    /**
     * Line of sight between two points: walls, closed doors, furniture and solid cover block it; glass, panes, iron
     * bars, chains, leaves and fences do not (a player behind a window is seen, one in a closet is not).
     */
    public static boolean clear(Level level, Mob self, Vec3 a, Vec3 b) {
        ClipContext ctx = new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, self);
        Boolean hit = net.minecraft.world.level.BlockGetter.traverseBlocks(a, b, ctx, (c, pos) -> {
            net.minecraft.world.level.block.state.BlockState s = level.getBlockState(pos);
            if (s.isAir() || seeThrough(s)) return null;
            net.minecraft.world.phys.shapes.VoxelShape shape = c.getBlockShape(s, level, pos);
            return level.clipWithInteractionOverride(c.getFrom(), c.getTo(), pos, shape, s) != null ? Boolean.TRUE : null;
        }, c -> Boolean.FALSE);
        return !hit;
    }

    private static boolean seeThrough(net.minecraft.world.level.block.state.BlockState s) {
        return s.is(net.minecraft.tags.BlockTags.IMPERMEABLE) || s.getBlock() instanceof net.minecraft.world.level.block.IronBarsBlock
                || s.getBlock() instanceof net.minecraft.world.level.block.ChainBlock || s.is(net.minecraft.tags.BlockTags.LEAVES)
                || s.is(net.minecraft.tags.BlockTags.FENCES) || s.is(net.minecraft.tags.BlockTags.FENCE_GATES)
                || s.getBlock() instanceof net.minecraft.world.level.block.ScaffoldingBlock
                || s.is(net.minecraft.world.level.block.Blocks.COBWEB) || s.is(com.interstice.registry.ModBlocks.DUST_WEB);
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

    /** Hearing with simple occlusion: walls between halve the effective radius; the species scales it. */
    public static boolean hears(Mob self, NoiseMap.Noise n) {
        Vec3 ear = self.getEyePosition();
        double d = ear.distanceTo(n.pos());
        double r = n.radius() * senses(self).hearing;
        if (d > r) return false;
        boolean occluded = !clear(self.level(), self, ear, n.pos());
        return !occluded || d < r * 0.5;
    }
}
