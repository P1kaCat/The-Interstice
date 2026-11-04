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
 * Base layers are crossfaded so their weights sum to one: idle, walk, run (ground), cling, climb
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
        IDLE("idle", true), WALK("walk", true), RUN("run", true), CLING("cling", true), CLIMB("climb", true),
        REAR("rear", true), FALL("fall", true), RECOVER("recover", true),
        OBSERVE("observe", false), THREAT("threat", false);

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
        float speed;
        public float heading;
        float lookYaw;
        double huntStart = -1;
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
            switch (b) {
                case STARE, INVESTIGATE -> s.started[Layer.OBSERVE.ordinal()] = s.time;
                case RECOVER -> s.started[Layer.RECOVER.ordinal()] = s.time;
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
        targets(s, b, mode);
        float k = s.initialised ? (float) (1 - Math.exp(-dt * 0.22)) : 1f;
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

    private static void targets(State s, Behavior b, Mode mode) {
        float[] t = s.target;
        Arrays.fill(t, 0f);
        float moving = Mth.clamp(s.speed / 0.03f, 0f, 1f);
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
        switch (b) {
            case HUNT -> {
                boolean beat = s.huntStart >= 0 && (s.time - s.huntStart) / 20.0 < REAR_BEAT;
                t[(beat ? Layer.REAR : Layer.RUN).ordinal()] = 1f;
                t[Layer.THREAT.ordinal()] = beat ? 1f : 0.45f;
            }
            case RUN, FLEE -> t[Layer.RUN.ordinal()] = 1f;
            case FALL -> t[Layer.FALL.ordinal()] = 1f;
            case RECOVER -> t[Layer.RECOVER.ordinal()] = 1f;
            case STARE -> {
                t[Layer.IDLE.ordinal()] = 1f;
                t[Layer.OBSERVE.ordinal()] = 1f;
            }
            default -> {
                t[Layer.WALK.ordinal()] = moving;
                t[Layer.IDLE.ordinal()] = 1f - moving;
                if (b == Behavior.INVESTIGATE && moving < 0.5f) t[Layer.OBSERVE.ordinal()] = 1f;
            }
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
            if (clip == null) continue;
            float seconds;
            if (clip.cycleBlocks > 0) seconds = (float) (s.stride / clip.cycleBlocks * clip.length);
            else if (l == Layer.IDLE || l == Layer.CLING || l == Layer.FALL) seconds = (float) (s.time / 20.0);
            else seconds = (float) ((s.time - s.started[l.ordinal()]) / 20.0);
            pose.add(clip, seconds, w);
        }
        if (Math.abs(s.lookYaw) > 0.1f) {
            int neck = pose.bone("neck"), head = pose.bone("head");
            if (neck >= 0) pose.addRotation(neck, 0, s.lookYaw * 0.4f, 0);
            if (head >= 0) pose.addRotation(head, 0, s.lookYaw * 0.6f, 0);
        }
    }
}
