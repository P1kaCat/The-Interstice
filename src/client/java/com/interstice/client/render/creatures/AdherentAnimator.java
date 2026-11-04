package com.interstice.client.render.creatures;

import com.interstice.entity.AdherentEntity;
import com.interstice.entity.AdherentEntity.Behavior;
import com.interstice.entity.AdherentEntity.Mode;
import net.minecraft.client.animation.AnimationDefinition;
import net.minecraft.util.Mth;

import java.util.Arrays;

/**
 * Plays the Adherent's keyframed Blockbench animations.
 * <p>
 * Base layers (idle, walk, run, rear, fall, recover) are crossfaded so their weights sum to
 * one; overlays (listen, jaws, screech) are added on top. Locomotion layers are driven by the
 * distance actually travelled (one cycle per WALK_CYCLE_BLOCKS / RUN_CYCLE_BLOCKS), so steps
 * keep pace with movement; idle and fall run on time; rear, recover and screech start when
 * their behaviour begins and hold their last frame.
 */
public final class AdherentAnimator {
    /** Render scale (model pixels are this much larger in the world). */
    public static final float SCALE = 1.6f;
    private static final double WALK_CYCLE_BLOCKS = 1.6, RUN_CYCLE_BLOCKS = 2.6;

    public enum Layer {
        IDLE(AdherentAnimations.IDLE), WALK(AdherentAnimations.WALK), RUN(AdherentAnimations.RUN),
        REAR(AdherentAnimations.REAR), FALL(AdherentAnimations.FALL), RECOVER(AdherentAnimations.RECOVER),
        LISTEN(AdherentAnimations.LISTEN), JAWS(AdherentAnimations.JAWS), SCREECH(AdherentAnimations.SCREECH);

        public final AnimationDefinition def;

        Layer(AnimationDefinition def) {
            this.def = def;
        }
    }

    public static final class State {
        final float[] weight = new float[Layer.values().length];
        final float[] target = new float[Layer.values().length];
        final double[] started = new double[Layer.values().length];
        double time, lastTime = Double.NaN;
        double lastX = Double.NaN, lastY, lastZ;
        double stride;
        float speed;
        public float heading;
        Behavior lastBehavior;
        boolean initialised;

        public float weight(Layer l) {
            return weight[l.ordinal()];
        }

        public long timeMs(Layer l) {
            return switch (l) {
                case WALK -> (long) (stride / WALK_CYCLE_BLOCKS * l.def.lengthInSeconds() * 1000);
                case RUN -> (long) (stride / RUN_CYCLE_BLOCKS * l.def.lengthInSeconds() * 1000);
                case IDLE, FALL, LISTEN, JAWS -> (long) (time * 50);
                default -> (long) ((time - started[l.ordinal()]) * 50);
            };
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

        Behavior b = e.behavior();
        if (b != s.lastBehavior) {
            onEnter(s, b);
            s.lastBehavior = b;
        }
        targets(s, b);
        float k = s.initialised ? (float) (1 - Math.exp(-dt * 0.3)) : 1f;
        for (int i = 0; i < s.weight.length; i++) s.weight[i] += (s.target[i] - s.weight[i]) * k;
        s.initialised = true;
    }

    private static void onEnter(State s, Behavior b) {
        switch (b) {
            case STARE -> s.started[Layer.REAR.ordinal()] = s.time;
            case RECOVER -> s.started[Layer.RECOVER.ordinal()] = s.time;
            case HUNT -> s.started[Layer.SCREECH.ordinal()] = s.time;
            default -> {
            }
        }
    }

    private static void targets(State s, Behavior b) {
        float[] t = s.target;
        Arrays.fill(t, 0f);
        boolean screeching = s.started[Layer.SCREECH.ordinal()] > 0
                && s.time - s.started[Layer.SCREECH.ordinal()] < AdherentAnimations.SCREECH.lengthInSeconds() * 20;
        float moving = Mth.clamp(s.speed / 0.03f, 0f, 1f);
        switch (b) {
            case RUN, HUNT -> {
                t[Layer.RUN.ordinal()] = 1f;
                if (b == Behavior.HUNT) t[(screeching ? Layer.SCREECH : Layer.JAWS).ordinal()] = 1f;
            }
            case STARE -> t[Layer.REAR.ordinal()] = 1f;
            case FALL -> t[Layer.FALL.ordinal()] = 1f;
            case RECOVER -> t[Layer.RECOVER.ordinal()] = 1f;
            default -> {
                t[Layer.WALK.ordinal()] = moving;
                t[Layer.IDLE.ordinal()] = 1f - moving;
                if (b == Behavior.SEARCH || b == Behavior.INVESTIGATE) t[Layer.LISTEN.ordinal()] = 1f;
            }
        }
    }
}
