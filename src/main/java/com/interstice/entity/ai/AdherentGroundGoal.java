package com.interstice.entity.ai;

import com.interstice.core.IntersticeServer;
import com.interstice.entity.AdherentEntity;
import com.interstice.entity.AdherentEntity.Behavior;
import com.interstice.entity.AdherentEntity.Mode;
import com.interstice.registry.ModSounds;
import com.interstice.world.IntersticeLayout;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Ground "brain" of a fallen Adherent. States:
 * RECOVER &rarr; SEARCH &harr; INVESTIGATE &rarr; HUNT, plus RETREAT (walk to the
 * nearest wall and climb back into the dark).
 * <p>
 * The creature only ever acts on what it perceives: sight
 * ({@link Perception#visibility}), hearing ({@link NoiseMap}) and its own
 * memory (last-known position). Hiding in the dark, crouching, staying still and
 * breaking line of sight all genuinely work.
 */
public class AdherentGroundGoal extends Goal {
    private final AdherentEntity mob;
    private float awareness;
    private ServerPlayer target;
    private Vec3 lastKnown;
    private long lastSeen = -10000;
    private Vec3 poi;
    private Vec3 searchCenter;
    private Vec3 searchPoint;
    private int searchPoints;
    private int lookAround;
    private long lastNoiseCheck;
    private long lastStimulus;
    private boolean retreating;
    private boolean alerted;

    public AdherentGroundGoal(AdherentEntity mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return mob.mode() == Mode.GROUND;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        long now = mob.level().getGameTime();
        lastStimulus = now;
        lastNoiseCheck = now;
        searchCenter = mob.position();
    }

    public float awareness() {
        return awareness;
    }

    @Override
    public void tick() {
        IntersticeServer s = IntersticeServer.get();
        if (s == null || !(mob.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();

        if (mob.behavior() == Behavior.RECOVER) {
            mob.getNavigation().stop();
            if (mob.decrementBehaviorTimer() <= 0) {
                mob.setBehavior(Behavior.SEARCH);
                searchCenter = mob.position();
                mob.playSound(ModSounds.CREATURE_SNIFF, 1.2f, 0.8f);
            }
            return;
        }

        if (retreating) {
            retreatTick(s);
            return;
        }

        if ((now + mob.getId()) % 4 == 0) perceive(s, level, now);

        if (s.stampede.searchersShouldRetreat() || now - lastStimulus > 2400) {
            if (awareness < 0.6f) {
                retreating = true;
                mob.setBehavior(Behavior.CLIMB);
                return;
            }
        }

        boolean seenRecently = target != null && now - lastSeen < 10;
        if (awareness >= 1.0f && seenRecently) {
            if (mob.behavior() != Behavior.HUNT) {
                mob.setBehavior(Behavior.HUNT);
                if (!alerted) {
                    mob.playSound(ModSounds.CREATURE_ALERT, 3.0f, 0.9f + mob.getRandom().nextFloat() * 0.2f);
                    alerted = true;
                }
            }
        }

        switch (mob.behavior()) {
            case HUNT -> huntTick(now);
            case INVESTIGATE -> investigateTick();
            default -> searchTick();
        }
    }

    private void perceive(IntersticeServer s, ServerLevel level, long now) {
        float best = 0f;
        ServerPlayer bestP = null;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(mob) > Perception.SIGHT_RANGE * Perception.SIGHT_RANGE) continue;
            float v = Perception.visibility(mob, p, s.track(p).speed);
            if (v > best) {
                best = v;
                bestP = p;
            }
        }
        if (bestP != null && best > 0.02f) {
            awareness += best * (mob.behavior() == Behavior.HUNT ? 0.2f : 0.09f);
            target = bestP;
            lastKnown = bestP.position();
            lastSeen = now;
            lastStimulus = now;
            s.track(bestP).lastEncounterTick = now;
        } else {
            awareness -= mob.behavior() == Behavior.HUNT ? 0.006f : 0.012f;
        }

        for (NoiseMap.Noise n : s.noise.audibleFrom(mob.getEyePosition(), lastNoiseCheck)) {
            if (n.source() == mob || !Perception.hears(mob, n)) continue;
            double d = mob.position().distanceTo(n.pos());
            double jitter = d * 0.14;
            Vec3 guess = n.pos().add(mob.getRandom().nextGaussian() * jitter, 0, mob.getRandom().nextGaussian() * jitter);
            if (n.source() instanceof ServerPlayer) {
                awareness = Math.min(0.92f, awareness + 0.16f * (n.radius() / 18f));
                poi = guess;
                lastStimulus = now;
            } else if (poi == null) {
                poi = guess; // curiosity: a sibling hit the ground
            }
        }
        lastNoiseCheck = now;
        awareness = Mth.clamp(awareness, 0f, 1.25f);

        if (mob.behavior() != Behavior.HUNT && awareness > 0.3f && (poi != null || lastKnown != null)) {
            if (lastKnown != null && now - lastSeen < 40) poi = lastKnown;
            if (mob.behavior() != Behavior.INVESTIGATE) {
                mob.setBehavior(Behavior.INVESTIGATE);
                lookAround = 0;
            }
        }
    }

    private void huntTick(long now) {
        if (target == null || !target.isAlive() || target.isSpectator() || target.level() != mob.level()) {
            target = null;
            mob.setBehavior(Behavior.SEARCH);
            return;
        }
        if (now - lastSeen > 70) {
            // lost them: go where they were last seen
            poi = lastKnown;
            awareness = 0.7f;
            alerted = false;
            mob.setBehavior(Behavior.INVESTIGATE);
            lookAround = 0;
            return;
        }
        mob.getLookControl().setLookAt(target, 40f, 40f);
        if (now - lastSeen < 6) {
            mob.getNavigation().moveTo(target, 1.22);
        } else if (lastKnown != null) {
            mob.getNavigation().moveTo(lastKnown.x, lastKnown.y, lastKnown.z, 1.15);
        }
        mob.tryAttack(target);
    }

    private void investigateTick() {
        if (poi == null) {
            mob.setBehavior(Behavior.SEARCH);
            return;
        }
        double d = mob.position().distanceTo(poi);
        if (d > 2.2 && lookAround == 0) {
            if (mob.getNavigation().isDone() || mob.tickCount % 20 == 0) mob.getNavigation().moveTo(poi.x, poi.y, poi.z, 0.9);
            mob.getLookControl().setLookAt(poi.x, poi.y + 1, poi.z);
            return;
        }
        // arrived: stand and listen, sweeping the head
        mob.getNavigation().stop();
        if (lookAround == 0) {
            lookAround = 60 + mob.getRandom().nextInt(40);
            mob.playSound(ModSounds.CREATURE_SNIFF, 1.4f, 0.75f + mob.getRandom().nextFloat() * 0.3f);
        }
        float a = (lookAround * 0.09f) + mob.getId();
        mob.getLookControl().setLookAt(mob.getX() + Mth.sin(a) * 6, mob.getEyeY() + Mth.sin(a * 0.37f), mob.getZ() + Mth.cos(a) * 6);
        if (--lookAround <= 0) {
            searchCenter = poi;
            poi = null;
            searchPoints = 0;
            searchPoint = null;
            mob.setBehavior(Behavior.SEARCH);
        }
    }

    private void searchTick() {
        if (mob.behavior() != Behavior.SEARCH) mob.setBehavior(Behavior.SEARCH);
        if (searchCenter == null) searchCenter = mob.position();
        if (searchPoint == null || mob.getNavigation().isDone()) {
            if (searchPoint != null && mob.getRandom().nextInt(3) == 0) {
                // pause and listen
                mob.getNavigation().stop();
                if (mob.getRandom().nextInt(4) == 0) mob.playSound(ModSounds.CREATURE_SNIFF, 1.0f, 0.9f);
            }
            double ang = mob.getRandom().nextDouble() * Math.PI * 2;
            double r = 3 + mob.getRandom().nextDouble() * 9;
            searchPoint = searchCenter.add(Math.cos(ang) * r, 0, Math.sin(ang) * r);
            mob.getNavigation().moveTo(searchPoint.x, searchPoint.y, searchPoint.z, 0.55);
            if (++searchPoints > 6) {
                // drift the search area along the corridor
                searchCenter = searchCenter.add(mob.getRandom().nextGaussian() * 10, 0, mob.getRandom().nextGaussian() * 14);
                searchPoints = 0;
            }
        }
    }

    private void retreatTick(IntersticeServer s) {
        IntersticeLayout L = s.layout();
        if (L == null) return;
        int side = L.sideOf(mob.getX(), mob.getZ());
        double depth = L.wallDepth(mob.getX(), mob.getZ());
        if (depth > -3.5 || mob.horizontalCollision && depth > -9) {
            if (mob.attachToWall(side, mob.getY() + 1.0, mob.getZ())) {
                mob.beginClimbAway();
            } else {
                mob.discard();
            }
            return;
        }
        if (mob.getNavigation().isDone() || mob.tickCount % 30 == 0) {
            double x = L.faceX(side, mob.getZ()) - side * 2.0;
            mob.getNavigation().moveTo(x, IntersticeLayout.FLOOR_Y, mob.getZ() + side * 2, 0.8);
        }
    }
}
