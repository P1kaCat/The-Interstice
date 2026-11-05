package com.interstice.client.render.creatures;

import com.interstice.client.render.creatures.mesh.SkinnedMeshAsset;
import com.interstice.client.render.creatures.mesh.SkinnedMeshPose;
import com.interstice.entity.AdherentEntity;
import com.interstice.entity.AdherentEntity.Behavior;
import com.interstice.entity.AdherentEntity.Mode;
import net.minecraft.util.Mth;
import org.joml.Vector3f;

import java.util.Arrays;

/**
 * Drives the Adherent's authored animations (models/adherent.bbmodel, exported to
 * models/entity/adherent.json).
 * <p>
 * Base layers are crossfaded so their weights sum to one: idle, turn (in place), walk, run (ground), cling, climb
 * (walls), rear, fall, recover. Overlays are added on top: observe (late, slow neck/head turn) and
 * threat (neck tension, progressive mouth opening). Locomotion clips are driven by distance actually
 * travelled using each clip's cycle length in blocks, so planted hands and feet keep pace with the
 * ground; other clips run on time or from the moment their behaviour started.
 * <p>
 * Hunting starts with a rear-and-threat beat before the run. On walls the model is oriented by the
 * renderer so the wall is its ground; cling/climb are dedicated wall animations (body pressed to the
 * surface, limbs splayed, hands gripping), not the ground walk rotated.
 */
public final class AdherentAnimator {
    public static final float SCALE = 1.0f;          // the mesh is authored at true size
    private static final float REAR_BEAT = 1.5f;     // seconds of rearing/threat before a hunt run

    public enum Layer {
        IDLE("idle", true), TURN("turn", true), WALK("walk", true), RUN("run", true), STOOP("stoop", true), CROUCH("crouch", true),
        CRAWL("crawl", true), CLING("cling", true), CLIMB("climb", true),
        REAR("rear", true), FALL("fall", true), RECOVER("recover", true), STUNNED("recover", true),
        OBSERVE("observe", false), THREAT("threat", false), SEARCH("search", false);

        public final String clip;
        final boolean base;

        Layer(String clip, boolean base) {
            this.clip = clip;
            this.base = base;
        }
    }

    public static final class State {
        final float[] weight = new float[Layer.values().length];
        final float[] target = new float[Layer.values().length];
        final double[] started = new double[Layer.values().length];
        double time, lastTime = Double.NaN;
        double lastX = Double.NaN, lastY, lastZ;
        double stride;
        public float speed;
        /** World velocity and smoothed acceleration (blocks per tick, per tick^2), for the moss dynamics. */
        double vx, vy, vz;
        public final Vector3f accelWorld = new Vector3f();
        /** Gravity and acceleration in Blockbench space (set by the renderer from the body's orientation). */
        public final Vector3f gravity = new Vector3f(0, -1, 0), accelModel = new Vector3f();
        public float frameDt;
        /** Accumulated body yaw (degrees, signed) driving the turn clip; smoothed yaw rate in degrees per tick. */
        double turn;
        float turnRate, lastYaw = Float.NaN;
        public float heading;
        float lookYaw;
        double huntStart = -1;
        /** 0..1: how far the body is squeezed sideways (crawling through a one-block gap). */
        public float squeeze;
        /** 0 on the ground .. 1 on the wall, eased: the body turns between the two instead of snapping. */
        public float wallBlend = Float.NaN;
        /** 0..1: going up a facade on the ground (rising while barely moving across): the body turns upright. */
        public float climbTilt;
        Behavior lastBehavior;
        boolean initialised;
        public SkinnedMeshPose pose;

        public float weight(Layer l) {
            return weight[l.ordinal()];
        }
    }

    private AdherentAnimator() {}

    public static State state(AdherentEntity e) {
        if (!(e.clientState instanceof State)) e.clientState = new State();
        return (State) e.clientState;
    }

    public static void update(AdherentEntity e, float partialTick) {
        State s = state(e);
        s.time = e.tickCount + partialTick;
        double dt = Double.isNaN(s.lastTime) ? 0 : Mth.clamp(s.time - s.lastTime, 0, 0.5);
        s.lastTime = s.time;
        double x = Mth.lerp(partialTick, e.xo, e.getX());
        double y = Mth.lerp(partialTick, e.yo, e.getY());
        double z = Mth.lerp(partialTick, e.zo, e.getZ());
        Mode mode = e.mode();
        if (!Double.isNaN(s.lastX) && dt > 0) {
            double dx = x - s.lastX, dy = y - s.lastY, dz = z - s.lastZ;
            double d = mode == Mode.WALL ? Math.sqrt(dy * dy + dz * dz) : Math.sqrt(dx * dx + dz * dz);
            if (d < 2) {
                double nvx = dx / dt, nvy = dy / dt, nvz = dz / dt;
                float ka = (float) Math.min(1, dt * 0.5);
                s.accelWorld.lerp(new Vector3f((float) ((nvx - s.vx) / dt), (float) ((nvy - s.vy) / dt), (float) ((nvz - s.vz) / dt)), ka);
                s.vx = nvx;
                s.vy = nvy;
                s.vz = nvz;
                s.stride += d;
                s.speed += ((float) (d / dt) - s.speed) * (float) Math.min(1, dt * 0.35);
                if (mode == Mode.WALL && d / dt > 0.01) {
                    float h = (float) Math.atan2(dy, dz);
                    s.heading += Mth.wrapDegrees((h - s.heading) * Mth.RAD_TO_DEG) * Mth.DEG_TO_RAD * (float) Math.min(1, dt * 0.25);
                }
            }
        }
        s.lastX = x;
        s.lastY = y;
        s.lastZ = z;
        s.frameDt = (float) dt;
        // turning in place: the turn clip advances with the rotation actually made (forwards when the yaw grows)
        float bodyYaw = Mth.rotLerp(partialTick, e.yBodyRotO, e.yBodyRot);
        if (!Float.isNaN(s.lastYaw) && dt > 0) {
            float dyaw = Mth.wrapDegrees(bodyYaw - s.lastYaw);
            if (Math.abs(dyaw) < 45f) {
                s.turn += dyaw;
                s.turnRate += ((float) (dyaw / dt) - s.turnRate) * (float) Math.min(1, dt * 0.4);
            }
        }
        s.lastYaw = bodyYaw;

        Behavior b = e.behavior();
        if (b != s.lastBehavior) {
            switch (b) {
                case STARE, INVESTIGATE -> s.started[Layer.OBSERVE.ordinal()] = s.time;
                case RECOVER -> s.started[Layer.RECOVER.ordinal()] = s.time;
                case SEARCH -> s.started[Layer.SEARCH.ordinal()] = s.time;
                case HUNT -> {
                    s.huntStart = s.time;
                    s.started[Layer.REAR.ordinal()] = s.time;
                    s.started[Layer.THREAT.ordinal()] = s.time;
                }
                default -> {
                }
            }
            s.lastBehavior = b;
        }
        targets(s, b, mode, SkinnedMeshAsset.of(e.species()), e.posture());
        float k = s.initialised ? (float) (1 - Math.exp(-dt * 0.22)) : 1f;
        s.squeeze += ((e.isSqueezing() ? 1f : 0f) - s.squeeze) * (float) Math.min(1, dt * 0.18);
        float hs = (float) Math.sqrt(s.vx * s.vx + s.vz * s.vz);
        boolean climbing = mode == Mode.GROUND && s.vy > 0.06 && hs < Math.max(0.08, s.vy * 1.5);
        s.climbTilt += ((climbing ? 1f : 0f) - s.climbTilt) * (float) Math.min(1, dt * (climbing ? 0.25 : 0.12));
        float wallTarget = mode == Mode.WALL ? 1f : 0f;
        if (Float.isNaN(s.wallBlend) || mode == Mode.FALLING) s.wallBlend = wallTarget;
        else s.wallBlend += (wallTarget - s.wallBlend) * (float) Math.min(1, dt * 0.16);
        for (int i = 0; i < s.weight.length; i++) s.weight[i] += (s.target[i] - s.weight[i]) * k;
        // the head turns late and slowly towards what the creature is looking at
        float wantYaw = 0f;
        Vector3f look = e.lookTarget();
        if ((b == Behavior.STARE || b == Behavior.INVESTIGATE) && look.lengthSquared() > 0 && mode != Mode.WALL) {
            float yaw = (float) Math.toDegrees(Math.atan2(look.x - e.getX(), look.z - e.getZ()));
            wantYaw = Mth.clamp(Mth.wrapDegrees(-yaw - e.yBodyRot), -70f, 70f);
        }
        if (s.time - s.started[Layer.OBSERVE.ordinal()] > 8) s.lookYaw += (wantYaw - s.lookYaw) * (float) Math.min(1, dt * 0.06);
        s.initialised = true;
    }

    /** Eye glow per species (the Watcher's are the brightest; the Clinger's barely show). */
    private static final float[] GLOW = {1.0f, 1.1f, 0.85f, 0.9f, 0.6f, 0.7f, 1.6f, 1.2f};
    /** The passing horde's eyes: dimmer than a single creature's, there are hundreds of them. */
    public static final float GLOW_HORDE = 0.7f;

    public static float speciesGlow(AdherentEntity e) {
        return GLOW[e.species().ordinal()];
    }

    /**
     * Eye intensity, 0..1+: brightest when the creature is still or staring, dimmer while it moves, a slow
     * pulse, and now and then a blink (a few ticks dark). Dying eyes fade.
     */
    public static float eyeGlow(AdherentEntity e, float partialTick) {
        State s = state(e);
        float still = 1f - Mth.clamp(s.speed / 0.06f, 0f, 1f);
        Behavior b = e.behavior();
        float k = 0.55f + 0.45f * still;
        if (b == Behavior.STARE || b == Behavior.HUNT) k = 1f;
        if (b == Behavior.STUNNED) k = 0.25f;
        double t = e.tickCount + partialTick;
        k *= 0.88f + 0.12f * (float) Math.sin(t * 0.07 + e.getId());
        // a blink: 4 ticks in every 140-260
        long period = 140 + Math.floorMod(e.getId() * 7919L, 120);
        if (Math.floorMod((long) t + e.getId() * 31L, period) < 4) k *= 0.05f;
        if (e.deathTime > 0) k *= Math.max(0f, 1f - e.deathTime / 12f);
        return k * speciesGlow(e);
    }

    /**
     * The walk is fine up to WALK_MAX_HZ cycles a second; the run needs at least RUN_MIN_HZ of its own (much longer)
     * cycles a second, or it is a slow-motion bounding run with flight phases: hopping. Between the two speeds the
     * gaits blend; the AI keeps its intermediate speeds out of that band (AdherentGroundGoal.pace).
     */
    private static final float WALK_MAX_HZ = 2.4f, RUN_MIN_HZ = 1.35f;

    /**
     * 0..1: how much of the run the current speed calls for. The walk clip is played by distance travelled, so a
     * fast creature on the walk clip would move its legs frantically (the v6 "hop"); past about one and a quarter
     * walk cycles a second it runs instead.
     */
    private static float runMix(State s, SkinnedMeshAsset asset) {
        SkinnedMeshAsset.Clip walk = asset == null ? null : asset.clip("walk");
        SkinnedMeshAsset.Clip run = asset == null ? null : asset.clip("run");
        if (walk == null || walk.cycleBlocks <= 0) return s.speed > 0.15f ? 1f : 0f;
        float bps = s.speed * 20f;
        float walkMax = walk.cycleBlocks * WALK_MAX_HZ;
        float runMin = run != null && run.cycleBlocks > 0 ? run.cycleBlocks * RUN_MIN_HZ : walkMax * 1.3f;
        if (runMin <= walkMax) runMin = walkMax * 1.15f;
        float x = Mth.clamp((bps - walkMax) / (runMin - walkMax), 0f, 1f);
        return x * x * (3 - 2 * x);
    }

    private static void gait(float[] t, float moving, float run) {
        t[Layer.WALK.ordinal()] += moving * (1f - run);
        t[Layer.RUN.ordinal()] += moving * run;
        t[Layer.IDLE.ordinal()] += 1f - moving;
    }

    private static void targets(State s, Behavior b, Mode mode, SkinnedMeshAsset asset, AdherentEntity.Posture posture) {
        boolean squeezing = posture == AdherentEntity.Posture.CRAWL;
        float[] t = s.target;
        Arrays.fill(t, 0f);
        float moving = Mth.clamp(s.speed / 0.03f, 0f, 1f);
        float run = runMix(s, asset);
        if (mode == Mode.WALL) {
            switch (b) {
                case FALL -> t[Layer.FALL.ordinal()] = 1f;
                default -> {
                    t[Layer.CLIMB.ordinal()] = moving;
                    t[Layer.CLING.ordinal()] = 1f - moving;
                    if (b == Behavior.STARE) t[Layer.OBSERVE.ordinal()] = 1f;
                }
            }
            return;
        }
        if (squeezing && b != Behavior.FALL && b != Behavior.STUNNED && b != Behavior.RECOVER) {
            // flattened in a one-block gap: the crawl, whatever it is doing
            t[Layer.CRAWL.ordinal()] = 1f;
            if (b == Behavior.HUNT) t[Layer.THREAT.ordinal()] = 0.3f;
            return;
        }
        if ((posture == AdherentEntity.Posture.STOOP || posture == AdherentEntity.Posture.CROUCH)
                && b != Behavior.FALL && b != Behavior.STUNNED && b != Behavior.RECOVER) {
            // under a low ceiling: the bowed or crouched gait (held still at its pose when it stops)
            t[(posture == AdherentEntity.Posture.STOOP ? Layer.STOOP : Layer.CROUCH).ordinal()] = 1f;
            if (b == Behavior.HUNT) t[Layer.THREAT.ordinal()] = 0.35f;
            else if (b == Behavior.SEARCH) t[Layer.SEARCH.ordinal()] = 0.5f;
            return;
        }
        if (s.climbTilt > 0.4f) {   // going up a facade: the climbing gait
            t[Layer.CLIMB.ordinal()] = 1f;
            return;
        }
        if (b == Behavior.CLIMB) {   // on the ground at the foot of the wall, about to go up: it rears against it
            t[Layer.REAR.ordinal()] = 1f;
            return;
        }
        switch (b) {
            case HUNT -> {
                boolean beat = s.huntStart >= 0 && (s.time - s.huntStart) / 20.0 < REAR_BEAT;
                if (beat) t[Layer.REAR.ordinal()] = 1f;
                else gait(t, moving, Math.max(run, 0.7f));
                t[Layer.THREAT.ordinal()] = beat ? 1f : 0.45f;
            }
            case RUN, FLEE -> gait(t, moving, Math.max(run, 0.7f));
            case FALL -> t[Layer.FALL.ordinal()] = 1f;
            case RECOVER -> t[Layer.RECOVER.ordinal()] = 1f;
            case STUNNED -> t[Layer.STUNNED.ordinal()] = 1f;          // lying where it landed
            case HIDE -> t[Layer.CLING.ordinal()] = 1f;               // pressed low and flat, still
            case SEARCH -> {
                gait(t, moving, run);
                t[Layer.SEARCH.ordinal()] = 1f - 0.5f * moving;       // head low, sweeping
            }
            case STARE -> {
                t[Layer.IDLE.ordinal()] = 1f;
                t[Layer.OBSERVE.ordinal()] = 1f;
            }
            default -> {
                gait(t, moving, run);
                if (b == Behavior.INVESTIGATE && moving < 0.5f) t[Layer.OBSERVE.ordinal()] = 1f;
            }
        }
        // standing but rotating: step round instead of pivoting like a statue
        float idle = t[Layer.IDLE.ordinal()];
        if (idle > 0f) {
            float turning = Mth.clamp((Math.abs(s.turnRate) - 0.3f) / 1.2f, 0f, 1f);
            t[Layer.TURN.ordinal()] = idle * turning;
            t[Layer.IDLE.ordinal()] = idle - t[Layer.TURN.ordinal()];
        }
    }

    /** Builds the frame's pose: weighted clips plus the procedural late head turn. */
    public static void apply(State s, SkinnedMeshPose pose) {
        SkinnedMeshAsset asset = pose.asset();
        pose.reset();
        for (Layer l : Layer.values()) {
            float w = s.weight[l.ordinal()];
            if (w <= 0.002f) continue;
            SkinnedMeshAsset.Clip clip = asset.clip(l.clip);
            if (clip == null && l == Layer.CRAWL) clip = asset.clip("climb");   // models exported before the crawl clip
            if (clip == null && (l == Layer.STOOP || l == Layer.CROUCH)) clip = asset.clip(l == Layer.CROUCH ? "crawl" : "walk");
            if (clip == null) continue;
            float seconds;
            if (clip.cycleBlocks > 0) seconds = (float) (s.stride / clip.cycleBlocks * clip.length);
            else if (clip.cycleDegrees > 0) seconds = (float) (s.turn / clip.cycleDegrees * clip.length);
            else if (l == Layer.STUNNED) seconds = 0.45f + 0.05f * (float) Math.sin(s.time * 0.05);   // prone, faint twitch
            else if (l == Layer.IDLE || l == Layer.CLING || l == Layer.FALL || l == Layer.SEARCH) seconds = (float) (s.time / 20.0);
            else seconds = (float) ((s.time - s.started[l.ordinal()]) / 20.0);
            pose.add(clip, seconds, w);
        }
        // while turning, the head leads into the turn (same sign convention as the look yaw)
        float lead = s.weight[Layer.TURN.ordinal()] * Mth.clamp(s.turnRate * 4f, -18f, 18f);
        float headYaw = s.lookYaw + lead;
        if (Math.abs(headYaw) > 0.1f) {
            int neck = pose.bone("neck"), head = pose.bone("head");
            if (neck >= 0) pose.addRotation(neck, 0, headYaw * 0.4f, 0);
            if (head >= 0) pose.addRotation(head, 0, headYaw * 0.6f, 0);
        }
    }
}
