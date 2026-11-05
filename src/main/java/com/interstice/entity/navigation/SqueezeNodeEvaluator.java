package com.interstice.entity.navigation;

import com.interstice.entity.AdherentEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/**
 * Walk node evaluator for creatures that can flatten themselves: paths are planned for a one-block body, so
 * they go through one-block gaps (under a slab of ceiling, through a hole in a wall). The entity squeezes
 * (crawl clip, 0.9-block hitbox) when its full-size body would not fit where it is going, and stands up again
 * when there is room ({@link AdherentEntity#updateSqueeze()}). Species that cannot squeeze plan as usual.
 */
public class SqueezeNodeEvaluator extends WalkNodeEvaluator {
    @Override
    public void prepare(PathNavigationRegion region, Mob mob) {
        super.prepare(region, mob);
        if (mob instanceof AdherentEntity a && a.species().t.squeeze) {
            this.entityWidth = 1;
            this.entityHeight = 1;
            this.entityDepth = 1;
        } else if (mob instanceof AdherentEntity) {
            // the Burden cannot crawl, but it can crouch under a two-block ceiling
            this.entityHeight = Math.min(this.entityHeight, 2);
        }
    }
}
