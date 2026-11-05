package com.interstice.client.audio;

import com.interstice.client.ClientState;
import com.interstice.client.config.ClientConfig;
import com.interstice.client.effects.QuakeShake;
import com.interstice.client.render.creatures.AdherentAnimator;
import com.interstice.client.render.creatures.mesh.SkinnedMeshAsset;
import com.interstice.client.render.creatures.mesh.SkinnedMeshPose;
import com.interstice.entity.AdherentEntity;
import com.interstice.entity.species.Species;
import com.interstice.registry.ModEntities;
import com.interstice.registry.SpeciesSounds;
import com.interstice.survival.SurvivalRules;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Footsteps in step with the animation. Every frame a creature is drawn near the camera, the height of each limb
 * tip (fingers and toes) above its support surface is read from the skinned pose (Blockbench space: y is the
 * distance from the floor, or from the wall when the creature is on one); when a tip that was lifted comes down to
 * the surface, that limb has landed and its species' {@code step} (on the ground) or {@code scrape} (on a wall) is
 * played at the creature, with a metal layer on metal floors.
 * <p>
 * The Burden is heard from inside buildings: each of its steps within {@link #BURDEN_RANGE} blocks rattles loose
 * glass and metal around the listener and shakes dust from the ceiling when the listener is under a roof, with a
 * small camera tremor when it is close.
 * <p>
 * Voice limiting: at most {@link #MAX_PER_SECOND} contact sounds a second (the nearest creatures win, since they are
 * drawn and tested first in practice and far ones are skipped beyond {@link #RANGE}); per creature, at most one
 * contact per {@link #MIN_GAP_MS} ms. Far crowds are left to the Stampede's horde bed.
 */
public final class CreatureFootsteps {
    public static final double RANGE = 26, BURDEN_RANGE = 44;
    public static final int MAX_PER_SECOND = 14;
    private static final long MIN_GAP_MS = 70;
    private static final String[] LIMBS = {"fingers_l", "fingers_r", "toes_l", "toes_r"};
    /** Contact hysteresis (blocks above the surface): a tip must rise above UP before a landing below DOWN counts. */
    private static final float UP = 0.16f, DOWN = 0.07f;

    private static final class Track {
        final boolean[] armed = new boolean[4];
        final long[] limbLast = new long[4];
        long last;
        int count;
    }

    private static final Int2ObjectOpenHashMap<Track> tracks = new Int2ObjectOpenHashMap<>();
    private static final Int2ObjectOpenHashMap<int[]> limbBones = new Int2ObjectOpenHashMap<>();
    private static long second;
    private static int budget = MAX_PER_SECOND;
    public static int playedLastSecond, playedAccum;

    private CreatureFootsteps() {}

    public static void update(AdherentEntity e, SkinnedMeshPose pose) {
        if (pose == null || !ClientState.inInterstice) return;
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        Species sp = e.species();
        double range = sp == Species.BURDEN ? BURDEN_RANGE : RANGE;
        double d2 = cam.distanceToSqr(e.position());
        if (d2 > range * range || e.mode() == AdherentEntity.Mode.FALLING) return;
        AdherentAnimator.State st = AdherentAnimator.state(e);
        if (st.speed < 0.015f) {   // standing still: nothing lands
            Track t = tracks.get(e.getId());
            if (t != null) java.util.Arrays.fill(t.armed, false);
            return;
        }
        SkinnedMeshAsset asset = pose.asset();
        int[] bones = limbBones.computeIfAbsent(System.identityHashCode(asset), k -> {
            int[] b = new int[LIMBS.length];
            for (int i = 0; i < LIMBS.length; i++) b[i] = pose.bone(LIMBS[i]);
            return b;
        });
        Track t = tracks.computeIfAbsent(e.getId(), k -> new Track());
        long now = System.currentTimeMillis();
        if (now / 1000 != second) {
            second = now / 1000;
            budget = MAX_PER_SECOND;
            playedLastSecond = playedAccum;
            playedAccum = 0;
        }
        float scale = AdherentAnimator.SCALE;
        // the gait decides the cadence: a walk is a soft, spaced rhythm (the hands barely heard), a run a rapid one
        SkinnedMeshAsset.Clip walk = asset.clip("walk");
        float bps = st.speed * 20f;
        float cycle = walk != null && walk.cycleBlocks > 0 ? walk.cycleBlocks : 1f;
        boolean running = bps > cycle * 2.4f || e.behavior() == AdherentEntity.Behavior.HUNT || e.behavior() == AdherentEntity.Behavior.FLEE;
        float cycleSeconds = cycle / Math.max(0.3f, bps);
        long creatureGap = running ? MIN_GAP_MS : 150;
        long limbGap = (long) (1000 * cycleSeconds * (running ? 0.4f : 0.55f));
        for (int i = 0; i < bones.length; i++) {
            int b = bones[i];
            if (b < 0) continue;
            float px = asset.pivots[b * 3], py = asset.pivots[b * 3 + 1], pz = asset.pivots[b * 3 + 2];
            float h = pose.boneY(b, px, py, pz) / 16f * scale;
            if (h > UP) t.armed[i] = true;
            else if (h < DOWN && t.armed[i]) {
                t.armed[i] = false;
                if (now - t.last >= creatureGap && now - t.limbLast[i] >= limbGap && budget > 0) {
                    t.last = now;
                    t.limbLast[i] = now;
                    budget--;
                    playedAccum++;
                    contact(mc, e, sp, i, Math.sqrt(d2), t.count++, running);
                }
            }
        }
    }

    private static void contact(Minecraft mc, AdherentEntity e, Species sp, int limb, double dist, int n, boolean running) {
        Level level = e.level();
        SpeciesSounds s = SpeciesSounds.of(sp);
        boolean wall = e.mode() == AdherentEntity.Mode.WALL;
        boolean hand = limb < 2;   // fingers_l, fingers_r
        // walking: the feet carry the rhythm, the hands only brush the floor; running: every limb strikes
        float gait = running ? (hand ? 0.85f : 1.1f) : (hand ? 0.3f : 0.6f);
        float vol = sp.t.volume * (wall ? 0.55f : 1f) * gait;
        float pitch = sp.t.pitch * (running ? 1.0f : 0.88f) * (0.92f + level.random.nextFloat() * 0.16f);
        // the Fractured limps: its right side lands heavier and lower
        if (sp == Species.FRACTURED && (limb & 1) == 1) { vol *= 1.25f; pitch *= 0.85f; }
        SoundEvent ev = wall ? s.scrape : s.step;
        level.playLocalSound(e.getX(), e.getY(), e.getZ(), ev, SoundSource.HOSTILE, vol, pitch, false);
        if (!wall && SurvivalRules.footstepLoudness(level, e.blockPosition()) >= 1.5f && sp != Species.CLINGER) {
            level.playLocalSound(e.getX(), e.getY(), e.getZ(), SpeciesSounds.METAL_CONTACT, SoundSource.HOSTILE, vol * 0.45f, pitch * (sp == Species.BURDEN ? 0.6f : 1f), false);
        }
        if (sp == Species.STRIDER && n % 3 == 0) level.playLocalSound(e.getX(), e.getY() + 2, e.getZ(), s.click, SoundSource.HOSTILE, 0.5f, pitch, false);
        if (sp == Species.BURDEN && !wall) burdenWeight(mc, e, dist);
    }

    /** The Burden's weight, felt where the listener is: rattling, dust from the ceiling, a small tremor. */
    private static void burdenWeight(Minecraft mc, AdherentEntity e, double dist) {
        if (mc.player == null || dist > BURDEN_RANGE) return;
        float k = (float) (1 - dist / BURDEN_RANGE);
        Level level = e.level();
        BlockPos head = mc.player.blockPosition().above(2);
        boolean roof = false;
        for (int i = 0; i < 6 && !roof; i++) roof = !level.getBlockState(head.above(i)).isAir();
        Vec3 p = mc.player.position();
        if (roof && level.random.nextFloat() < 0.35f + k * 0.6f) {
            level.playLocalSound(p.x + level.random.nextGaussian() * 3, p.y + 2, p.z + level.random.nextGaussian() * 3,
                    SpeciesSounds.BUILDING_RATTLE, SoundSource.AMBIENT, 0.25f + 0.55f * k, 0.9f + level.random.nextFloat() * 0.2f, false);
            if (k > 0.35f) {
                level.playLocalSound(p.x, p.y + 2.5, p.z, SpeciesSounds.DUST_FALL, SoundSource.AMBIENT, 0.3f * k, 1f, false);
                int n = (int) (6 * k * ClientConfig.INSTANCE.particles);
                for (int i = 0; i < n; i++) {
                    level.addParticle(ModEntities.FALLING_DUST, p.x + level.random.nextGaussian() * 2.5, p.y + 2.6, p.z + level.random.nextGaussian() * 2.5, 0, -0.02, 0);
                }
            }
        }
        if (k > 0.55f) QuakeShake.thud(0.25f * (k - 0.55f) / 0.45f);
    }

    public static void forget(int id) {
        tracks.remove(id);
    }

    public static void clear() {
        tracks.clear();
    }
}
