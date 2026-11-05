package com.interstice.entity.ai;

import com.interstice.block.WardEmitterBlock;

import com.interstice.core.IntersticeServer;
import com.interstice.entity.AdherentEntity;
import com.interstice.entity.AdherentEntity.Behavior;
import com.interstice.entity.AdherentEntity.Mode;
import com.interstice.entity.species.Species;
import com.interstice.entity.species.Species.GroundDisposition;
import com.interstice.registry.ModSounds;
import com.interstice.world.IntersticeLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.EnumSet;

/**
 * Ground "brain" of a fallen creature. States:
 * RECOVER &rarr; SEARCH &harr; INVESTIGATE &rarr; HUNT, plus RETREAT (walk to the
 * nearest wall and climb back into the dark).
 * <p>
 * What it does with what it perceives depends on its {@link GroundDisposition} (rolled per individual
 * from its species): a HUNTER can hunt; an INVESTIGATOR searches and then watches from a distance; a
 * FLEER runs from people; a HIDER goes to the foot of a wall and stays still; a STUNNED creature lies on
 * the ground for a long time first. No fallen creature can be assumed to behave like the previous one.
 * <p>
 * The creature only ever acts on what it perceives with its species' senses: sight
 * ({@link Perception#visibility}), hearing ({@link NoiseMap}), smell ({@link ScentTrail}), the calls of the
 * others, and its own memory (last-known position, the heading the player had, the places it has already
 * searched). Hiding in the dark, crouching, staying still and breaking line of sight all genuinely work; so does
 * outlasting it.
 * <p>
 * Searching is a hunt, not a wander. Having lost a player it goes to where they were last seen, then on to
 * where they were heading; it follows their scent if it has a nose for it; it then goes through the places a
 * player would hide (dark, enclosed, low-ceilinged corners nearby), never the same one twice; some species
 * wait in ambush instead. A creature that spots or loses a player calls, and the others nearby converge.
 */
public class AdherentGroundGoal extends Goal {
    private final AdherentEntity mob;
    private float awareness;
    private ServerPlayer target;
    private Vec3 lastKnown;
    /** The player's horizontal velocity (blocks per tick) when last seen: where they were heading. */
    private Vec3 lastHeading = Vec3.ZERO;
    private long lastSeen = -10000;
    /** A player half-seen: the creature turns towards them until it makes them out (or loses them). */
    private ServerPlayer glimpse;
    private long glimpseUntil;
    private Vec3 poi;
    private Vec3 searchCenter;
    private Vec3 searchPoint;
    private int searchPoints;
    private int lookAround;
    private long lastNoiseCheck;
    private long lastStimulus;
    private boolean retreating;
    private boolean alerted;
    private boolean fleeing, hiding;
    private int modeTimer;
    private Vec3 fleeFrom;

    /** How the search goes once there is nothing left to investigate. */
    enum Phase { WANDER, PURSUE, TRACK, SWEEP, AMBUSH }

    private Phase phase = Phase.WANDER;
    private int phaseTimer;
    private Vec3 pursuit;
    private Vec3 scentPoint;
    private long scentTick = -1;
    private Vec3 sweepSpot;
    private int sweepPause;
    private int sweeps;
    /** Places already searched (a few blocks apart); never searched twice while they are remembered. */
    private final ArrayDeque<Vec3> searched = new ArrayDeque<>();
    private long lastCall = -10000;

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

    /** The search phase (tests, debug). */
    public String phase() {
        return phase.name();
    }

    /**
     * Navigation speed multiplier giving this fraction of the species' chase speed. A ground mob's speed grows with the
     * square of its movement attribute (about 44 * s^2 blocks per second on stone), hence the square root.
     */
    private double pace(double fraction) {
        Species.Traits t = senses();
        return Math.sqrt(t.chase * fraction / 44.0) / t.groundSpeed;
    }

    /** A walking pace: this fraction of the chase speed, but never past what still looks like a walk for the species. */
    private double walkPace(double fraction) {
        Species.Traits t = senses();
        double bps = Math.min(t.chase * fraction, mob.walkMaxSpeed());
        return Math.sqrt(bps / 44.0) / t.groundSpeed;
    }

    private Vec3 wardFrom;
    /** Ticks of the rear-and-cry at the start of a hunt (matches the client's REAR_BEAT of 1.5 s). */
    private static final int HUNT_BEAT = 30;
    private long huntStart = -1000;
    private int mountTimer = -1;

    /** How far a running ward keeps this species away (0: it does not care). */
    private double wardRadius() {
        return switch (mob.species()) {
            case BURDEN, CLINGER -> 0;
            case HOLLOW -> WardEmitterBlock.RADIUS + 4;
            default -> WardEmitterBlock.RADIUS;
        };
    }

    /** Looks for a running ward a little beyond its radius, so a fast creature knows of it before it is inside. */
    private Vec3 findWard(ServerLevel level) {
        double r = wardRadius();
        if (r <= 0) return null;
        r += 6;
        BlockPos c = mob.blockPosition();
        int ri = (int) Math.ceil(r);
        Vec3 best = null;
        double bestD = r * r;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int dy = -3; dy <= 3; dy++)
            for (int dx = -ri; dx <= ri; dx++)
                for (int dz = -ri; dz <= ri; dz++) {
                    m.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                    if (!WardEmitterBlock.isActive(level.getBlockState(m))) continue;
                    double d = mob.position().distanceToSqr(Vec3.atCenterOf(m));
                    if (d < bestD) {
                        bestD = d;
                        best = Vec3.atCenterOf(m);
                    }
                }
        return best;
    }

    /** Inside a ward's radius: back off out of it, head turned to the hum. True while it is busy doing that. */
    private boolean repelTick(long now) {
        double r = wardRadius();
        Vec3 away = mob.position().subtract(wardFrom).multiply(1, 0, 1);
        double d = away.length();
        if (d > r) return false;
        away = d < 1e-3 ? new Vec3(1, 0, 0) : away.scale(1 / d);
        if (mob.getNavigation().isDone() || mob.tickCount % 10 == 0) {
            Vec3 to = wardFrom.add(away.scale(r + 2.5));
            mob.getNavigation().moveTo(to.x, to.y, to.z, walkPace(0.42));
        }
        mob.getLookControl().setLookAt(wardFrom.x, wardFrom.y, wardFrom.z);
        if (mob.behavior() == Behavior.HUNT) mob.setBehavior(Behavior.INVESTIGATE);
        return true;
    }

    private Species.Traits senses() {
        return mob.species().t;
    }

    @Override
    public void tick() {
        IntersticeServer s = IntersticeServer.get();
        if (s == null || !(mob.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();

        if (mob.behavior() == Behavior.STUNNED) {
            mob.getNavigation().stop();
            if (mob.decrementBehaviorTimer() <= 0 && mob.resumeAfterStun) {
                // dazzled, not beaten: it shakes it off and goes on looking (harder)
                mob.resumeAfterStun = false;
                mob.setBehavior(Behavior.SEARCH);
                awareness = Math.max(awareness, 0.5f);
                return;
            }
            if (mob.behaviorTimer() <= 0) {
                // it comes to, slowly; it will not be a hunter
                mob.disposition = mob.getRandom().nextBoolean() ? GroundDisposition.INVESTIGATOR : GroundDisposition.FLEER;
                mob.setBehavior(Behavior.RECOVER);
                mob.setBehaviorTimer((int) (90 * senses().recoverMul));
            }
            return;
        }

        if (mob.behavior() == Behavior.RECOVER) {
            mob.getNavigation().stop();
            if (mob.decrementBehaviorTimer() <= 0) {
                mob.setBehavior(Behavior.SEARCH);
                searchCenter = mob.position();
                mob.playSound(ModSounds.CREATURE_SNIFF, 1.2f, 0.8f * senses().pitch);
                if (mob.disposition == GroundDisposition.FLEER) startFlee(null);
                else if (mob.disposition == GroundDisposition.HIDER) {
                    hiding = true;
                    modeTimer = 600 + mob.getRandom().nextInt(900);
                }
            }
            return;
        }

        if (retreating) {
            retreatTick(s);
            return;
        }

        if ((now + mob.getId()) % 4 == 0) perceive(s, level, now);

        // a running ward: most species will not go near it (see WardEmitterBlock)
        if ((now + mob.getId()) % 10 == 0) wardFrom = findWard(level);
        if (wardFrom != null && repelTick(now)) return;

        if (fleeing) {
            fleeTick();
            return;
        }
        if (hiding) {
            hideTick(s);
            return;
        }

        if (s.stampede.searchersShouldRetreat() || now - lastStimulus > senses().persistence) {
            if (awareness < 0.6f) {
                retreating = true;
                mob.setBehavior(Behavior.CLIMB);
                return;
            }
        }

        boolean seenRecently = target != null && now - lastSeen < 10;
        if (mob.disposition == GroundDisposition.FLEER && awareness > 0.3f && target != null) {
            startFlee(target.position());
            return;
        }
        if (mob.disposition == GroundDisposition.INVESTIGATOR && awareness >= 1.0f && seenRecently) {
            if (mob.behavior() != Behavior.STARE) mob.setBehavior(Behavior.STARE);   // watches, keeps its distance
        } else if (mob.disposition == GroundDisposition.HUNTER && awareness >= 1.0f && seenRecently) {
            if (mob.behavior() != Behavior.HUNT) {
                mob.setBehavior(Behavior.HUNT);
                huntStart = now;
                phase = Phase.WANDER;
                if (!alerted) {
                    mob.playSound(com.interstice.registry.SpeciesSounds.of(mob.species()).alert, 3.2f, 0.9f + mob.getRandom().nextFloat() * 0.2f);
                    alerted = true;
                    call(level, now, lastKnown, 0.75f);
                }
            }
        }

        mob.wantsToClimb = false;   // only the hunt sets it, tick by tick
        switch (mob.behavior()) {
            case HUNT -> huntTick(level, now);
            case STARE -> watchTick(now);
            case INVESTIGATE -> investigateTick(level, now);
            default -> searchTick(level, now);
        }
        if (glimpse != null && now < glimpseUntil && glimpse.isAlive()
                && mob.behavior() != Behavior.HUNT && mob.behavior() != Behavior.STARE) {
            // something moved over there: it turns to look, and freezes while it makes it out
            mob.getLookControl().setLookAt(glimpse, 30f, 30f);
            if (awareness > 0.25f) mob.getNavigation().stop();
        }
    }

    // ------------------------------------------------------------------ senses

    private void perceive(IntersticeServer s, ServerLevel level, long now) {
        float best = 0f;
        ServerPlayer bestP = null;
        double range = Perception.sightRange(mob);
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(mob) > range * range) continue;
            float v = Perception.visibility(mob, p, s.track(p).speed);
            if (v > best) {
                best = v;
                bestP = p;
            }
        }
        if (bestP != null && best > 0.02f) {
            // close and clearly seen: the alarm is almost immediate (about a second at 5 blocks); far or dim: a few seconds
            double close = Math.max(0, 1 - Math.sqrt(bestP.distanceToSqr(mob)) / 16.0);
            awareness += best * (mob.behavior() == Behavior.HUNT ? 0.25f : (float) (0.26 + 0.35 * close));
            target = bestP;
            lastKnown = bestP.position();
            lastHeading = s.track(bestP).vel;
            lastSeen = now;
            lastStimulus = now;
            glimpse = bestP;
            glimpseUntil = now + 30;
            s.track(bestP).lastEncounterTick = now;
        } else {
            awareness -= mob.behavior() == Behavior.HUNT ? 0.006f : 0.012f;
        }

        for (NoiseMap.Noise n : s.noise.audibleFrom(mob.getEyePosition(), lastNoiseCheck, senses().hearing)) {
            if (n.source() == mob || !Perception.hears(mob, n)) continue;
            double d = mob.position().distanceTo(n.pos());
            double jitter = d * 0.14 / Math.max(0.6, senses().hearing);   // keen ears place a sound better
            Vec3 guess = n.pos().add(mob.getRandom().nextGaussian() * jitter, 0, mob.getRandom().nextGaussian() * jitter);
            if (n.source() instanceof ServerPlayer || n.source() instanceof com.interstice.entity.ThrownLure) {
                awareness = Math.min(0.92f, awareness + 0.16f * (n.radius() / 18f) * Math.min(1.5f, senses().hearing));
                poi = guess;
                lastStimulus = now;
            } else if (poi == null) {
                poi = guess; // curiosity: a sibling hit the ground
            }
        }
        lastNoiseCheck = now;

        // smell: a scent mark fresher than the one it follows leads it on (it never reveals the player by itself)
        if (mob.behavior() != Behavior.HUNT && (now + mob.getId()) % 8 == 0) {
            ScentTrail.Lead lead = ScentTrail.sniff(s, level, mob.position(), senses().smell, now, scentTick);
            if (lead != null) {
                if (scentTick < 0) mob.playSound(ModSounds.CREATURE_SNIFF, 1.3f, 0.7f + mob.getRandom().nextFloat() * 0.2f);
                scentPoint = lead.pos();
                scentTick = lead.tick();
                lastStimulus = now;
                awareness = Math.max(awareness, 0.35f);
                if (phase != Phase.PURSUE && !fleeing && !hiding) setPhase(Phase.TRACK, 120);
            }
        }
        awareness = Mth.clamp(awareness, 0f, 1.25f);

        if (mob.behavior() != Behavior.HUNT && mob.behavior() != Behavior.STARE && !fleeing && !hiding
                && awareness > 0.3f && (poi != null || lastKnown != null) && phase != Phase.TRACK) {
            if (lastKnown != null && now - lastSeen < 40) poi = lastKnown;
            if (poi != null && mob.behavior() != Behavior.INVESTIGATE) {
                mob.setBehavior(Behavior.INVESTIGATE);
                lookAround = 0;
            }
        }
    }

    /** Another creature called: something is there. */
    public void hearCall(Vec3 at, float strength, long now) {
        if (mob.behavior() == Behavior.HUNT || fleeing || retreating || mob.disposition == GroundDisposition.HIDER
                || mob.behavior() == Behavior.STUNNED || mob.behavior() == Behavior.RECOVER) return;
        double jitter = 2.5;
        poi = at.add(mob.getRandom().nextGaussian() * jitter, 0, mob.getRandom().nextGaussian() * jitter);
        awareness = Math.max(awareness, strength);
        lastStimulus = now;
        if (mob.disposition == GroundDisposition.FLEER) return;
        mob.setBehavior(Behavior.INVESTIGATE);
        lookAround = 0;
        phase = Phase.WANDER;
    }

    /** Calls the others nearby to a place (rate-limited); the call itself is heard. */
    private void call(ServerLevel level, long now, Vec3 at, float strength) {
        if (at == null || now - lastCall < 100) return;
        lastCall = now;
        mob.playSound(com.interstice.registry.SpeciesSounds.of(mob.species()).vocal, 2.2f, 0.6f * senses().pitch);
        for (AdherentEntity o : level.getEntitiesOfClass(AdherentEntity.class, mob.getBoundingBox().inflate(32),
                o -> o != mob && o.mode() == Mode.GROUND && o.groundGoal != null)) {
            o.groundGoal.hearCall(at, strength, now);
        }
    }

    // ------------------------------------------------------------------ states

    private void huntTick(ServerLevel level, long now) {
        if (target == null || !target.isAlive() || target.isSpectator() || target.level() != mob.level()) {
            target = null;
            mob.setBehavior(Behavior.SEARCH);
            return;
        }
        if (now - lastSeen > 70) {
            // lost them: call the others, go where they were last seen, then on to where they were heading
            call(level, now, lastKnown, 0.5f);
            poi = lastKnown;
            Vec3 ahead = lastHeading.multiply(1, 0, 1).scale(35);
            if (ahead.length() > 12) ahead = ahead.normalize().scale(12);
            pursuit = lastKnown == null ? null : lastKnown.add(ahead);
            awareness = 0.7f;
            alerted = false;
            mob.setBehavior(Behavior.INVESTIGATE);
            lookAround = 0;
            return;
        }
        mob.getLookControl().setLookAt(target, 40f, 40f);
        // the first moment of the hunt: it rears and cries (the client plays the rear for REAR_BEAT); it does not
        // move while it does, then it launches itself
        if (now - huntStart < HUNT_BEAT) {
            mob.getNavigation().stop();
            float yaw = (float) (Mth.atan2(target.getZ() - mob.getZ(), target.getX() - mob.getX()) * Mth.RAD_TO_DEG) - 90f;
            mob.setYRot(Mth.approachDegrees(mob.getYRot(), yaw, 20f));
            mob.yBodyRot = mob.getYRot();
            return;
        }
        // prey up on a roof or a ledge, close: straight at the facade and up it, instead of the nearest point
        // the paths reach (which is often inside the building, through a window, under the prey)
        double above = target.getY() - mob.getY();
        boolean climb = now - lastSeen < 40 && above > 2.5 && mob.species() != Species.BURDEN
                && Math.hypot(target.getX() - mob.getX(), target.getZ() - mob.getZ()) < 12;
        // once it is going up, the roof edge hides the prey: it knows where it is, it does not give up half way
        if (!climb && mob.wantsToClimbCommit > 0 && above > -0.2 && mob.species() != Species.BURDEN) climb = true;
        mob.wantsToClimbCommit = climb ? (mob.horizontalCollision || mob.onClimbable() ? 60 : Math.max(0, mob.wantsToClimbCommit - 1)) : 0;
        if (climb) lastSeen = Math.max(lastSeen, now - 5);
        mob.wantsToClimb = climb;
        if (climb) {
            mob.getNavigation().stop();
            mob.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), pace(1.0));
            // at the top of the facade: haul itself over the edge onto the roof
            if (mob.horizontalCollision && above < 1.4 && above > -0.3) {
                Vec3 d = new Vec3(target.getX() - mob.getX(), 0, target.getZ() - mob.getZ());
                if (d.lengthSqr() > 1e-4) d = d.normalize();
                mob.setDeltaMovement(d.x * 0.22, 0.34, d.z * 0.22);
            }
        } else if (now - lastSeen < 6) {
            mob.getNavigation().moveTo(target, pace(1.0));
        } else if (lastKnown != null) {
            mob.getNavigation().moveTo(lastKnown.x, lastKnown.y, lastKnown.z, pace(0.9));
        }
        mob.tryAttack(target);
    }

    /** Investigator: stands at a distance and watches; backs away when approached; never attacks. */
    private void watchTick(long now) {
        if (target == null || !target.isAlive() || now - lastSeen > 200) {
            target = null;
            mob.setBehavior(Behavior.SEARCH);
            return;
        }
        mob.getLookControl().setLookAt(target, 10f, 10f);   // slow head
        double d = mob.distanceTo(target);
        if (d < 6.5) {
            Vec3 away = mob.position().subtract(target.position()).multiply(1, 0, 1).normalize();
            Vec3 to = mob.position().add(away.scale(6));
            if (mob.getNavigation().isDone() || mob.tickCount % 15 == 0) mob.getNavigation().moveTo(to.x, to.y, to.z, walkPace(0.35));
        } else {
            mob.getNavigation().stop();
        }
    }

    private void startFlee(Vec3 from) {
        fleeing = true;
        hiding = false;
        fleeFrom = from;
        modeTimer = 100 + mob.getRandom().nextInt(80);
        mob.setBehavior(Behavior.FLEE);
        if (from != null) mob.playSound(com.interstice.registry.SpeciesSounds.of(mob.species()).vocal, 1.2f, 1.25f * senses().pitch);
    }

    /** Fleer: runs away from the threat (or just away), then goes back up a wall. */
    private void fleeTick() {
        if (fleeFrom == null) {
            ServerPlayer p = (ServerPlayer) mob.level().getNearestPlayer(mob, 32);
            fleeFrom = p != null ? p.position() : mob.position().add(mob.getRandom().nextGaussian(), 0, mob.getRandom().nextGaussian());
        }
        if (mob.getNavigation().isDone() || mob.tickCount % 20 == 0) {
            Vec3 away = mob.position().subtract(fleeFrom).multiply(1, 0, 1);
            away = away.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : away.normalize();
            Vec3 to = mob.position().add(away.scale(14));
            mob.getNavigation().moveTo(to.x, to.y, to.z, pace(1.05));
        }
        if (--modeTimer <= 0) {
            fleeing = false;
            retreating = true;
            mob.setBehavior(Behavior.CLIMB);
        }
    }

    /** Hider: goes to the foot of the nearest wall, stays pressed and still, flees if found. */
    private void hideTick(IntersticeServer s) {
        IntersticeLayout L = s.layout();
        if (L == null) return;
        if (target != null && awareness > 0.5f && mob.distanceTo(target) < 5) {
            startFlee(target.position());
            return;
        }
        if (mob.behavior() != Behavior.HIDE) {
            int side = L.sideOf(mob.getX(), mob.getZ());
            double depth = L.wallDepth(mob.getX(), mob.getZ());
            if (depth > -4 || mob.horizontalCollision) {
                mob.getNavigation().stop();
                mob.setBehavior(Behavior.HIDE);
            } else if (mob.getNavigation().isDone() || mob.tickCount % 30 == 0) {
                double x = L.faceX(side, mob.getZ()) - side * 2.5;
                mob.getNavigation().moveTo(x, mob.getY(), mob.getZ(), walkPace(0.4));
            }
            return;
        }
        mob.getNavigation().stop();
        if (--modeTimer <= 0) {
            hiding = false;
            retreating = true;
            mob.setBehavior(Behavior.CLIMB);
        }
    }

    private void investigateTick(ServerLevel level, long now) {
        if (poi == null) {
            mob.setBehavior(Behavior.SEARCH);
            return;
        }
        double d = mob.position().distanceTo(poi);
        if (d > 2.2 && lookAround == 0) {
            // purposeful: faster the surer it is
            double speed = awareness > 0.6f ? walkPace(0.42) : walkPace(0.35);
            if (mob.getNavigation().isDone() || mob.tickCount % 20 == 0) mob.getNavigation().moveTo(poi.x, poi.y, poi.z, speed);
            mob.getLookControl().setLookAt(poi.x, poi.y + 1, poi.z);
            return;
        }
        // arrived: stand and listen, sweeping the head
        mob.getNavigation().stop();
        if (lookAround == 0) {
            lookAround = 50 + mob.getRandom().nextInt(40);
            mob.playSound(ModSounds.CREATURE_SNIFF, 1.4f, 0.75f + mob.getRandom().nextFloat() * 0.3f);
        }
        float a = (lookAround * 0.09f) + mob.getId();
        mob.getLookControl().setLookAt(mob.getX() + Mth.sin(a) * 6, mob.getEyeY() + Mth.sin(a * 0.37f), mob.getZ() + Mth.cos(a) * 6);
        if (--lookAround <= 0) {
            remember(poi);
            searchCenter = poi;
            poi = null;
            searchPoints = 0;
            searchPoint = null;
            mob.setBehavior(Behavior.SEARCH);
            // what next: wait for them to come out, or go on to where they were heading, or comb the area
            if (pursuit != null && mob.getRandom().nextFloat() < ambushChance()) {
                setPhase(Phase.AMBUSH, 200 + mob.getRandom().nextInt(260));
            } else if (pursuit != null) {
                setPhase(Phase.PURSUE, 260);
            } else {
                setPhase(Phase.SWEEP, 0);
            }
        }
    }

    /** Species that would rather wait than chase. */
    private float ambushChance() {
        return switch (mob.species()) {
            case CLINGER -> 0.6f;
            case WATCHER -> 0.4f;
            case HOLLOW, BURDEN -> 0.25f;
            default -> 0.12f;
        };
    }

    private void setPhase(Phase p, int timer) {
        phase = p;
        phaseTimer = timer;
        searchPoint = null;
        sweepSpot = null;
        sweepPause = 0;
        if (p != Phase.AMBUSH && mob.behavior() == Behavior.HIDE) mob.setBehavior(Behavior.SEARCH);
    }

    private void searchTick(ServerLevel level, long now) {
        if (mob.behavior() != Behavior.SEARCH && !(phase == Phase.AMBUSH && mob.behavior() == Behavior.HIDE)) mob.setBehavior(Behavior.SEARCH);
        if (searchCenter == null) searchCenter = mob.position();
        switch (phase) {
            case PURSUE -> pursueTick();
            case TRACK -> trackTick();
            case SWEEP -> sweepTick(level);
            case AMBUSH -> ambushTick();
            default -> wanderTick();
        }
    }

    /** Where they were heading: straight there, fast. */
    private void pursueTick() {
        if (pursuit == null || --phaseTimer <= 0 || mob.position().distanceToSqr(pursuit) < 2.5 * 2.5) {
            if (pursuit != null) {
                searchCenter = pursuit;
                remember(pursuit);
            }
            pursuit = null;
            setPhase(Phase.SWEEP, 0);
            mob.playSound(ModSounds.CREATURE_SNIFF, 1.2f, 0.85f);
            return;
        }
        if (mob.getNavigation().isDone() || mob.tickCount % 20 == 0) mob.getNavigation().moveTo(pursuit.x, pursuit.y, pursuit.z, pace(0.9));
        mob.getLookControl().setLookAt(pursuit.x, pursuit.y + 1, pursuit.z);
    }

    /** Nose down along the trail; the senses keep moving the scent point forwards. */
    private void trackTick() {
        if (scentPoint == null || --phaseTimer <= 0) {
            if (scentPoint != null) searchCenter = scentPoint;
            setPhase(Phase.SWEEP, 0);
            return;
        }
        if (mob.position().distanceToSqr(scentPoint) < 1.6 * 1.6) {
            mob.getNavigation().stop();   // at the mark: a fresher one (picked up by the nose) moves it on
        } else if (mob.getNavigation().isDone() || mob.tickCount % 10 == 0) {
            mob.getNavigation().moveTo(scentPoint.x, scentPoint.y, scentPoint.z, walkPace(0.42));
            phaseTimer = Math.max(phaseTimer, 60);
        }
        mob.getLookControl().setLookAt(scentPoint.x, scentPoint.y, scentPoint.z);
    }

    /** Waits, pressed low and still, facing where the player disappeared; any stimulus ends it. */
    private void ambushTick() {
        mob.getNavigation().stop();
        if (mob.behavior() != Behavior.HIDE) mob.setBehavior(Behavior.HIDE);
        if (lastKnown != null) mob.getLookControl().setLookAt(lastKnown.x, lastKnown.y + 1, lastKnown.z, 4f, 4f);
        if (--phaseTimer <= 0) {
            mob.setBehavior(Behavior.SEARCH);
            setPhase(pursuit != null ? Phase.PURSUE : Phase.SWEEP, 260);
        }
    }

    /** Goes through the places someone would hide, best first, each one once. */
    private void sweepTick(ServerLevel level) {
        if (sweepSpot == null) {
            if (sweeps >= 6) {
                // nothing here: drift the search along the corridor
                searchCenter = searchCenter.add(mob.getRandom().nextGaussian() * 10, 0, mob.getRandom().nextGaussian() * 14);
                sweeps = 0;
            }
            sweepSpot = hidingSpot(level, searchCenter);
            if (sweepSpot == null) {
                setPhase(Phase.WANDER, 0);
                return;
            }
            sweeps++;
            phaseTimer = 300;
            mob.getNavigation().moveTo(sweepSpot.x, sweepSpot.y, sweepSpot.z, walkPace(0.3));
        }
        if (sweepPause > 0) {
            // at the spot: still, head sweeping, listening
            mob.getNavigation().stop();
            float a = sweepPause * 0.11f + mob.getId();
            mob.getLookControl().setLookAt(mob.getX() + Mth.sin(a) * 5, mob.getEyeY() - 0.3, mob.getZ() + Mth.cos(a) * 5);
            if (--sweepPause == 0) {
                remember(sweepSpot);
                sweepSpot = null;
            }
            return;
        }
        if (mob.position().distanceToSqr(sweepSpot) < 2.0 * 2.0 || --phaseTimer <= 0) {
            sweepPause = 30 + mob.getRandom().nextInt(30);
            if (mob.getRandom().nextInt(3) == 0) mob.playSound(ModSounds.CREATURE_SNIFF, 1.0f, 0.9f);
        } else if (mob.getNavigation().isDone() || mob.tickCount % 20 == 0) {
            mob.getNavigation().moveTo(sweepSpot.x, sweepSpot.y, sweepSpot.z, walkPace(0.3));
            mob.getLookControl().setLookAt(sweepSpot.x, sweepSpot.y + 0.5, sweepSpot.z);
        }
    }

    /** The old random prowl, slower: used when there is no lead at all. */
    private void wanderTick() {
        if (searchPoint == null || mob.getNavigation().isDone()) {
            if (searchPoint != null && mob.getRandom().nextInt(3) == 0) {
                // pause and listen
                mob.getNavigation().stop();
                if (mob.getRandom().nextInt(4) == 0) mob.playSound(ModSounds.CREATURE_SNIFF, 1.0f, 0.9f);
            }
            double ang = mob.getRandom().nextDouble() * Math.PI * 2;
            double r = 3 + mob.getRandom().nextDouble() * 9;
            searchPoint = searchCenter.add(Math.cos(ang) * r, 0, Math.sin(ang) * r);
            mob.getNavigation().moveTo(searchPoint.x, searchPoint.y, searchPoint.z, walkPace(0.22));
            if (++searchPoints > 6) {
                // drift the search area along the corridor; now and then comb it properly
                searchCenter = searchCenter.add(mob.getRandom().nextGaussian() * 10, 0, mob.getRandom().nextGaussian() * 14);
                searchPoints = 0;
                if (mob.getRandom().nextFloat() < 0.5f) setPhase(Phase.SWEEP, 0);
            }
        }
    }

    private void remember(Vec3 at) {
        if (at == null) return;
        searched.addLast(at);
        if (searched.size() > 24) searched.removeFirst();
    }

    private boolean alreadySearched(Vec3 at) {
        for (Vec3 v : searched) if (v.distanceToSqr(at) < 3.5 * 3.5) return true;
        return false;
    }

    /**
     * The most promising hiding place within 14 blocks of {@code center} that it has not searched yet: dark,
     * enclosed, under a low ceiling (a crawl space), not too far. Null if none.
     */
    private Vec3 hidingSpot(ServerLevel level, Vec3 center) {
        Vec3 best = null;
        double bestScore = -1e9;
        for (int i = 0; i < 28; i++) {
            double ang = mob.getRandom().nextDouble() * Math.PI * 2;
            double r = 3 + mob.getRandom().nextDouble() * 11;
            BlockPos col = BlockPos.containing(center.x + Math.cos(ang) * r, center.y, center.z + Math.sin(ang) * r);
            BlockPos feet = standable(level, col);
            if (feet == null) continue;
            Vec3 at = Vec3.atBottomCenterOf(feet);
            if (alreadySearched(at)) continue;
            int light = level.getMaxLocalRawBrightness(feet);
            boolean lowCeiling = !level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
            double score = Perception.enclosure(level, feet) * 0.8 + (15 - light) / 15.0 * 2.0 + (lowCeiling ? 1.5 : 0)
                    - at.distanceTo(mob.position()) * 0.05 + mob.getRandom().nextDouble() * 0.3;
            if (score > bestScore) {
                bestScore = score;
                best = at;
            }
        }
        return best;
    }

    /** A floor position in this column within 3 blocks of its y where something one block tall could be. */
    private static BlockPos standable(Level level, BlockPos col) {
        for (int dy = 3; dy >= -3; dy--) {
            BlockPos p = col.above(dy);
            if (level.getBlockState(p).getCollisionShape(level, p).isEmpty()
                    && !level.getBlockState(p.below()).getCollisionShape(level, p.below()).isEmpty()) return p;
        }
        return null;
    }

    private void retreatTick(IntersticeServer s) {
        IntersticeLayout L = s.layout();
        if (L == null) return;
        int side = L.sideOf(mob.getX(), mob.getZ());
        double depth = L.wallDepth(mob.getX(), mob.getZ());
        if (depth > -3.5 || mob.horizontalCollision && depth > -9) {
            // at the foot of the wall: stop, face it, rear up against it (the client plays the rear), then go up
            if (mountTimer < 0) {
                mountTimer = 16;
                mob.getNavigation().stop();
                mob.setBehavior(Behavior.CLIMB);
            }
            double wx = L.faceX(side, mob.getZ());
            mob.getLookControl().setLookAt(wx, mob.getEyeY() + 2, mob.getZ());
            float yaw = side < 0 ? 90f : 270f;
            mob.setYRot(Mth.approachDegrees(mob.getYRot(), yaw, 15f));
            mob.yBodyRot = mob.getYRot();
            if (--mountTimer > 0) return;
            mountTimer = -1;
            if (mob.attachToWall(side, mob.getY() + 1.0, mob.getZ())) {
                mob.beginClimbAway();
            } else {
                mob.discard();
            }
            return;
        }
        if (mob.getNavigation().isDone() || mob.tickCount % 30 == 0) {
            double x = L.faceX(side, mob.getZ()) - side * 2.0;
            mob.getNavigation().moveTo(x, IntersticeLayout.FLOOR_Y, mob.getZ() + side * 2, walkPace(0.4));
        }
    }
}
