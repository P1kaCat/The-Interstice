package com.interstice.entity.ai;

import com.interstice.core.IntersticeServer;
import com.interstice.core.IntersticeServer.PlayerTrack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

/**
 * Smell: players leave a fading trail on the ground ({@link PlayerTrack#scent}). A creature picks up a mark when
 * it is within its smell range of it, a range that shrinks as the mark ages (and is smaller for the faint mark of
 * a sneaking player). Of the marks it can smell it follows the freshest, so walking from mark to mark it runs the
 * trail forwards, towards where the player went. Smell never reveals a player directly: it only leads.
 */
public final class ScentTrail {
    public record Lead(Vec3 pos, long tick, ServerPlayer player) {}

    private ScentTrail() {}

    /** The freshest scent mark this nose can pick up from {@code at}, newer than {@code after}; null if none. */
    @Nullable
    public static Lead sniff(IntersticeServer s, ServerLevel level, Vec3 at, float smell, long now, long after) {
        if (smell <= 0) return null;
        Lead best = null;
        for (ServerPlayer p : level.players()) {
            if (p.isCreative() || p.isSpectator()) continue;
            PlayerTrack t = s.track(p);
            for (PlayerTrack.Scent m : t.scent) {
                if (m.tick() <= after || (best != null && m.tick() <= best.tick())) continue;
                double age = (now - m.tick()) / (double) PlayerTrack.SCENT_LIFE;
                if (age >= 1) continue;
                double r = smell * m.strength() * (1 - age);
                if (m.pos().distanceToSqr(at) <= r * r && Math.abs(m.pos().y - at.y) < 4) best = new Lead(m.pos(), m.tick(), p);
            }
        }
        return best;
    }
}
