package com.interstice.event.stampede;

import com.interstice.config.ServerConfig;
import com.interstice.core.IntersticeServer;
import com.interstice.Interstice;
import com.interstice.entity.AdherentEntity;
import com.interstice.entity.species.Species;
import com.interstice.entity.species.SpeciesMix;
import com.interstice.event.VoiceEvents;
import com.interstice.network.ModNetwork;
import com.interstice.world.IntersticeLayout;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server-authoritative Stampede state machine.
 * <p>
 * The horde itself is a <b>hybrid simulation</b>:
 * <ul>
 *   <li>Far and mid distance: thousands of client-side visual instances derived
 *       deterministically from (seed, phase start, direction). Not synchronised
 *       individually; costs one packet per phase change.</li>
 *   <li>Near each player: a capped number of real, server-authoritative
 *       {@link AdherentEntity} runners on the walls, some of which really fall
 *       and become ground searchers.</li>
 * </ul>
 * The horde is multispecies: each event draws a composition ({@link SpeciesMix}) from the configured
 * weights with random variation and occasional "surges" of one species; real runners are picked from it
 * and clients colour their visual horde with the same weights. Species differ in speed, fall chance and
 * survival; watchers notice the premonition first, clingers mostly hold on and react late.
 * <p>
 * The corridor is effectively infinite along Z, so each player experiences the
 * horde passing them locally while the phase timeline is shared by everyone.
 */
public final class StampedeController {
    private final IntersticeServer s;
    private StampedePhase phase = StampedePhase.IDLE;
    private long phaseStart;
    private int phaseDuration;
    private long seed;
    private int direction = 1;
    private float intensity = 1f;
    private SpeciesMix mix = SpeciesMix.fromConfig(null, true);
    private long lastStampedeEnd = -1_000_000;
    private boolean whisperSent;
    private final Map<UUID, PlayerState> players = new HashMap<>();

    private static final class PlayerState {
        int fallsRemaining;
        int fallsDone;
        int survivors;
    }

    public StampedeController(IntersticeServer s) {
        this.s = s;
    }

    public StampedePhase phase() {
        return phase;
    }

    public boolean active() {
        return phase != StampedePhase.IDLE;
    }

    public long lastStampedeEnd() {
        return lastStampedeEnd;
    }

    public int phaseTicksLeft(long now) {
        return phase == StampedePhase.IDLE ? 0 : (int) Math.max(0, phaseStart + phaseDuration - now);
    }

    public boolean suppressesAmbient() {
        return phase.between(StampedePhase.EARTHQUAKE, StampedePhase.AFTERMATH);
    }

    public boolean searchersShouldRetreat() {
        return phase == StampedePhase.AFTERMATH || phase == StampedePhase.RECOVERY;
    }

    // ---------------------------------------------------------------- control

    public void start(ServerLevel level, float intensity) {
        RandomSource r = level.random;
        this.seed = r.nextLong();
        this.direction = r.nextBoolean() ? 1 : -1;
        this.intensity = intensity;
        this.players.clear();
        rollMix(r);
        enter(level, StampedePhase.PREMONITION);
    }

    public void stop(ServerLevel level) {
        enter(level, StampedePhase.IDLE);
        for (AdherentEntity e : level.getEntities(ALL, e -> e.role == AdherentEntity.Role.RUNNER)) e.discard();
    }

    private static final net.minecraft.world.level.entity.EntityTypeTest<net.minecraft.world.entity.Entity, AdherentEntity> ALL =
            net.minecraft.world.level.entity.EntityTypeTest.forClass(AdherentEntity.class);

    private void rollMix(RandomSource r) {
        ServerConfig c = ServerConfig.INSTANCE;
        mix = SpeciesMix.fromConfig(c.stampedeSpeciesWeights, true).vary(r, c.stampedeCompositionVariation, c.stampedeSurgeChance, c.stampedeSurgeFactor);
        Interstice.LOG.info("Stampede composition: {}", mix);
    }

    public SpeciesMix mix() {
        return mix;
    }

    /** Replaces this event's composition (commands, tests). */
    public void setMix(ServerLevel level, SpeciesMix m) {
        mix = m;
        ModNetwork.sendToLevel(level, sync());
    }

    public void forcePhase(ServerLevel level, StampedePhase p) {
        if (phase == StampedePhase.IDLE && p != StampedePhase.IDLE) {
            seed = level.random.nextLong();
            direction = level.random.nextBoolean() ? 1 : -1;
            players.clear();
            rollMix(level.random);
        }
        enter(level, p);
    }

    private void enter(ServerLevel level, StampedePhase p) {
        StampedePhase previous = phase;
        phase = p;
        phaseStart = level.getGameTime();
        phaseDuration = p.baseDuration + (p.jitter > 0 ? level.random.nextInt(p.jitter) : 0);
        onEnter(level, p, previous);
        ModNetwork.sendToLevel(level, sync());
    }

    private ModNetwork.StampedeSync sync() {
        return new ModNetwork.StampedeSync(phase.ordinal(), phaseStart, phaseDuration, seed, direction, intensity, mix.weights());
    }

    public void syncTo(ServerPlayer p) {
        ModNetwork.send(p, sync());
    }

    public void forget(ServerPlayer p) {
        players.remove(p.getUUID());
    }

    private void onEnter(ServerLevel level, StampedePhase p, StampedePhase previous) {
        switch (p) {
            case PREMONITION -> {
                // watchers notice first: they stop and look down the corridor; the others follow later
                for (ServerPlayer pl : level.players()) {
                    Vec3 far = pl.position().add(0, 30, -direction * 200);
                    s.population.react(level, pl.position(), 80, false, far, sp -> sp == Species.WATCHER);
                }
            }
            case EARTHQUAKE -> {
                whisperSent = false;
                s.environment.quake(level, 0.75f + 0.25f * intensity, phaseDuration + 40, false);
                for (ServerPlayer pl : level.players()) s.population.react(level, pl.position(), 90, true, null);
            }
            case HORDE_PASSAGE -> {
                for (ServerPlayer pl : level.players()) {
                    PlayerState st = new PlayerState();
                    ServerConfig c = ServerConfig.INSTANCE;
                    st.fallsRemaining = c.minFallsPerPlayer + level.random.nextInt(Math.max(1, c.maxFallsPerPlayer - c.minFallsPerPlayer + 1));
                    players.put(pl.getUUID(), st);
                }
            }
            case AFTERMATH -> {
                for (AdherentEntity e : level.getEntities(ALL, e -> e.role == AdherentEntity.Role.RUNNER)) {
                    if (e.mode() == AdherentEntity.Mode.WALL) e.beginClimbAway();
                }
                // the clingers that held on through the horde react only now that it has passed
                for (ServerPlayer pl : level.players()) {
                    for (AdherentEntity e : level.getEntitiesOfClass(AdherentEntity.class, pl.getBoundingBox().inflate(90, 250, 90),
                            e -> e.species() == Species.CLINGER && e.mode() == AdherentEntity.Mode.WALL && e.role == AdherentEntity.Role.AMBIENT)) {
                        if (level.random.nextFloat() < 0.6f) e.beginFlee(40 + level.random.nextInt(60));
                    }
                }
            }
            case IDLE -> {
                if (previous != StampedePhase.IDLE) lastStampedeEnd = level.getGameTime();
                players.clear();
            }
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------- tick

    public void tick(ServerLevel level) {
        if (phase == StampedePhase.IDLE) return;
        long now = level.getGameTime();
        long t = now - phaseStart;

        switch (phase) {
            case PREMONITION -> {
                if (t == 80) {   // the rest of the walls goes still, after the watchers
                    for (ServerPlayer pl : level.players()) {
                        Vec3 far = pl.position().add(0, 30, -direction * 200);
                        s.population.react(level, pl.position(), 80, false, far, sp -> sp != Species.WATCHER);
                    }
                }
            }
            case EARTHQUAKE -> {
                if (!whisperSent && t >= 50) {
                    whisperSent = true;
                    VoiceEvents.sayToLevel(level, "hide", 1.0f);
                }
            }
            case HORDE_PASSAGE, CREATURE_FALLS -> runnersTick(level, now, t);
            case GROUND_SEARCH -> {
                if (t == 20) ensureSearchers(level);
            }
            default -> {
            }
        }

        if (t >= phaseDuration) enter(level, phase.next());
    }

    private void runnersTick(ServerLevel level, long now, long t) {
        ServerConfig c = ServerConfig.INSTANCE;
        IntersticeLayout L = s.layout();
        if (L == null) return;
        boolean tail = phase == StampedePhase.CREATURE_FALLS;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            PlayerState st = players.computeIfAbsent(p.getUUID(), u -> {
                PlayerState n = new PlayerState();
                n.fallsRemaining = c.minFallsPerPlayer; // late joiner
                return n;
            });
            List<AdherentEntity> runners = level.getEntitiesOfClass(AdherentEntity.class, p.getBoundingBox().inflate(120, 250, 120),
                    e -> e.role == AdherentEntity.Role.RUNNER && e.mode() == AdherentEntity.Mode.WALL);

            int cap = tail ? Math.max(2, c.maxRunnersPerPlayer / 3) : c.maxRunnersPerPlayer;
            if (now % (tail ? 18 : 7) == 0 && runners.size() < cap) {
                int side = level.random.nextBoolean() ? -1 : 1;
                double z = p.getZ() - direction * (52 + level.random.nextDouble() * 12);
                double y = Math.max(IntersticeLayout.FLOOR_Y + 10, p.getY() + 10 + level.random.nextDouble() * 50);
                Species sp = mix.pick(level.random);
                AdherentEntity e = s.population.create(level, sp, side, y, z);
                if (e != null) e.beginRun(direction, (0.7 + level.random.nextDouble() * 0.35) * sp.t.runSpeedMul);
            }

            for (AdherentEntity e : runners) {
                double rel = (e.getZ() - p.getZ()) * direction; // negative = still approaching
                if (rel > 70) {
                    e.discard();
                    continue;
                }
                if (st.fallsRemaining > 0 && Math.abs(rel) < 16) {
                    float chance = (tail ? 0.08f : 0.035f) * e.species().t.fallMul * c.fallMultiplier(e.species());
                    if (level.random.nextFloat() < chance) {
                        st.fallsRemaining--;
                        st.fallsDone++;
                        e.startFalling();
                    }
                }
            }
            // force any remaining falls before the tail ends
            if (tail && phaseDuration - t < 60 && st.fallsRemaining > 0 && !runners.isEmpty()) {
                // the most fall-prone of a few random runners lets go
                AdherentEntity e = runners.get(level.random.nextInt(runners.size()));
                for (int k = 0; k < 3; k++) {
                    AdherentEntity o = runners.get(level.random.nextInt(runners.size()));
                    if (o.species().t.fallMul > e.species().t.fallMul) e = o;
                }
                e.forceSurvive = true;
                e.startFalling();
                st.fallsRemaining--;
            }
        }
    }

    /** Guarantees at least one survivor is searching near each player so hiding matters. */
    private void ensureSearchers(ServerLevel level) {
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            List<AdherentEntity> searchers = level.getEntitiesOfClass(AdherentEntity.class, p.getBoundingBox().inflate(60, 60, 60),
                    e -> e.mode() != AdherentEntity.Mode.WALL);
            if (!searchers.isEmpty()) continue;
            int side = level.random.nextBoolean() ? -1 : 1;
            double z = p.getZ() + (level.random.nextBoolean() ? 1 : -1) * (22 + level.random.nextDouble() * 14);
            AdherentEntity e = s.population.create(level, side, p.getY() + 30, z);
            if (e != null) {
                e.forceSurvive = true;
                e.startFalling();
            }
        }
        // trim to the per-player cap
        for (ServerPlayer p : level.players()) {
            List<AdherentEntity> ground = level.getEntitiesOfClass(AdherentEntity.class, p.getBoundingBox().inflate(80, 80, 80),
                    e -> e.mode() == AdherentEntity.Mode.GROUND);
            for (int i = ServerConfig.INSTANCE.maxSearchersPerPlayer; i < ground.size(); i++) ground.get(i).discard();
        }
    }
}
