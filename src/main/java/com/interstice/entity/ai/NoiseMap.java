package com.interstice.entity.ai;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Short-lived record of sounds made in the dimension (footsteps, landings,
 * broken blocks, doors, combat, creatures hitting the ground). Creatures query
 * it instead of reading player positions directly: they only learn where a
 * player is from what they hear and see.
 */
public final class NoiseMap {
    public record Noise(Vec3 pos, float radius, long tick, Entity source) {}

    private static final int LIFETIME = 40;
    private final ArrayDeque<Noise> noises = new ArrayDeque<>();

    public void emit(Vec3 pos, float radius, long tick, Entity source) {
        if (radius <= 0) return;
        noises.addLast(new Noise(pos, radius, tick, source));
        if (noises.size() > 256) noises.removeFirst();
    }

    public void prune(long now) {
        Iterator<Noise> it = noises.iterator();
        while (it.hasNext()) {
            if (now - it.next().tick > LIFETIME) it.remove();
            else break;
        }
    }

    /** Noises emitted after {@code since} that could reach {@code listener} (before occlusion). */
    public List<Noise> audibleFrom(Vec3 listener, long since) {
        List<Noise> out = new ArrayList<>(2);
        for (Noise n : noises) {
            if (n.tick <= since) continue;
            if (n.pos.distanceToSqr(listener) <= n.radius * n.radius) out.add(n);
        }
        return out;
    }

    public int size() {
        return noises.size();
    }
}
