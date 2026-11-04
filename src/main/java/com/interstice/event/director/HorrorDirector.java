package com.interstice.event.director;

import com.interstice.config.ServerConfig;
import com.interstice.core.IntersticeServer;
import com.interstice.entity.AdherentEntity;
import com.interstice.entity.ai.Perception;
import com.interstice.event.VoiceEvents;
import com.interstice.event.stampede.StampedePhase;
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
 * Invisible pacing director.
 * <p>
 * Tracks a hidden tension value per player (never displayed) that grows with
 * time spent exposed in the open, recent encounters and nearby creatures, and
 * slows down in shelter. At irregular intervals it picks a weighted action;
 * "nothing" is always a strong candidate, so long stretches of quiet are normal.
 * Major events reset tension and impose a recovery period, so the player is
 * never punished back-to-back.
 */
public final class HorrorDirector {
    public enum Action { NOTHING, DISTANT_IMPACT, CHAIN_SHUDDER, LIGHT_FAILURE, SILENCE, STARE, FLEE, MINOR_QUAKE, VOICE, STAMPEDE, BURDEN_MOVES }

    private final IntersticeServer s;
    private boolean paused;
    private long nextAction = 600;
    private Action lastAction = Action.NOTHING;
    private long lastVoice = -100000;
    private final Map<UUID, Integer> stampedeVoiceStage = new HashMap<>();

    public HorrorDirector(IntersticeServer s) {
        this.s = s;
    }

    public void setPaused(boolean p) {
        paused = p;
    }

    public boolean paused() {
        return paused;
    }

    public void setTension(ServerLevel level, float t) {
        for (ServerPlayer p : level.players()) s.track(p).tension = t;
    }

    public String describe() {
        ServerLevel level = s.level();
        float avg = 0;
        int n = 0;
        if (level != null) for (ServerPlayer p : level.players()) { avg += s.track(p).tension; n++; }
        long left = level == null ? 0 : Math.max(0, nextAction - level.getGameTime());
        return String.format("%s tension %.2f next %ds last %s", paused ? "PAUSED" : "on", n == 0 ? 0 : avg / n, left / 20, lastAction);
    }

    public void tick(ServerLevel level) {
        long now = level.getGameTime();
        if (now % 20 != 3) return;
        ServerConfig c = ServerConfig.INSTANCE;
        float freq = Math.max(0.05f, c.eventFrequency);

        List<ServerPlayer> players = level.players().stream().filter(p -> !p.isSpectator()).toList();
        if (players.isEmpty()) return;

        // ---- tension model
        for (ServerPlayer p : players) {
            IntersticeServer.PlayerTrack t = s.track(p);
            int enclosure = Perception.enclosure(level, p.blockPosition());
            float exposure = enclosure <= 1 ? 1.3f : enclosure >= 3 ? 0.45f : 1f;
            int near = level.getEntitiesOfClass(AdherentEntity.class, p.getBoundingBox().inflate(32), e -> true).size();
            float d = 0.0032f * freq * exposure + near * 0.0008f;
            if (now - t.lastEncounterTick < 200) d += 0.01f;
            if (s.stampede.active()) d = 0;
            t.tension = Math.min(1f, t.tension + d);
        }

        stampedeVoices(level, players, now);

        if (!c.directorEnabled || paused || s.stampede.active() || now < nextAction) return;

        RandomSource r = level.random;
        ServerPlayer focus = players.get(r.nextInt(players.size()));
        IntersticeServer.PlayerTrack ft = s.track(focus);
        float tension = ft.tension;

        double[] w = new double[Action.values().length];
        w[Action.NOTHING.ordinal()] = 3.0 + (1 - tension) * 3 + (lastAction != Action.NOTHING ? 2 : 0);
        w[Action.DISTANT_IMPACT.ordinal()] = 2.0;
        w[Action.CHAIN_SHUDDER.ordinal()] = 1.5;
        w[Action.LIGHT_FAILURE.ordinal()] = 0.4 + tension;
        w[Action.SILENCE.ordinal()] = 0.9;
        w[Action.STARE.ordinal()] = 1.2;
        w[Action.FLEE.ordinal()] = 0.6;
        w[Action.MINOR_QUAKE.ordinal()] = 0.2 + 0.8 * tension;
        w[Action.VOICE.ordinal()] = now - lastVoice > 6000 ? 0.4 * tension : 0;
        boolean stampedeReady = c.stampedeEnabled && tension > 0.62f && ft.ticksInDimension > 3600
                && now - s.stampede.lastStampedeEnd() > (long) (c.minMinutesBetweenStampedes * 1200 / freq);
        w[Action.STAMPEDE.ordinal()] = stampedeReady ? 2.5 * tension : 0;
        w[Action.BURDEN_MOVES.ordinal()] = 0.35 + 0.4 * tension;

        Action a = pick(w, r);
        perform(level, focus, a, r);
        lastAction = a;
        long base = a == Action.NOTHING ? 300 + r.nextInt(700) : 700 + r.nextInt(1300);
        nextAction = now + (long) (base / freq);
    }

    private static Action pick(double[] w, RandomSource r) {
        double sum = 0;
        for (double v : w) sum += v;
        double x = r.nextDouble() * sum;
        for (int i = 0; i < w.length; i++) {
            x -= w[i];
            if (x <= 0) return Action.values()[i];
        }
        return Action.NOTHING;
    }

    public void perform(ServerLevel level, ServerPlayer p, Action a, RandomSource r) {
        IntersticeLayout L = s.layout();
        IntersticeServer.PlayerTrack t = s.track(p);
        switch (a) {
            case NOTHING -> {
            }
            case DISTANT_IMPACT -> {
                if (L == null) return;
                double z = p.getZ() + (r.nextBoolean() ? 1 : -1) * (90 + r.nextDouble() * 160);
                int side = r.nextBoolean() ? -1 : 1;
                Vec3 at = new Vec3(L.faceX(side, z), IntersticeLayout.FLOOR_Y + 60 + r.nextDouble() * 400, z);
                s.environment.distantImpact(level, at, 0.6f + r.nextFloat() * 0.4f);
                t.tension += 0.03f;
            }
            case CHAIN_SHUDDER -> s.environment.chainShudder(level, 0.4f + r.nextFloat() * 0.6f);
            case LIGHT_FAILURE -> s.environment.lightFailure(p, 80 + r.nextInt(160));
            case SILENCE -> s.environment.silence(p, 400 + r.nextInt(500));
            case STARE -> {
                Vec3 at = p.position().add((r.nextDouble() - 0.5) * 30, 40 + r.nextDouble() * 60, (r.nextBoolean() ? 1 : -1) * (80 + r.nextDouble() * 80));
                s.environment.stare(p, at, 160 + r.nextInt(200));
                t.tension += 0.05f;
            }
            case FLEE -> s.environment.flee(p);
            case MINOR_QUAKE -> {
                s.environment.quake(level, 0.25f + r.nextFloat() * 0.25f, 60 + r.nextInt(80), true);
                t.tension = Math.max(0, t.tension - 0.1f);
            }
            case VOICE -> {
                lastVoice = level.getGameTime();
                int enclosure = Perception.enclosure(level, p.blockPosition());
                String line;
                if (enclosure <= 1 && p.isSprinting()) line = "they_hear";
                else if (enclosure >= 3) line = r.nextBoolean() ? "stay" : "not_yet";
                else line = r.nextFloat() < 0.5f ? "look_up" : "closer"; // not every word can be trusted
                VoiceEvents.say(p, line, 0.9f);
            }
            case BURDEN_MOVES -> {
                // somewhere out of sight, something heavy starts to move: heard and felt before it is seen
                if (L == null) return;
                java.util.List<AdherentEntity> heavy = level.getEntitiesOfClass(AdherentEntity.class, p.getBoundingBox().inflate(90, 250, 90),
                        e -> e.species() == com.interstice.entity.species.Species.BURDEN && e.mode() == AdherentEntity.Mode.WALL);
                AdherentEntity b = heavy.isEmpty() ? null : heavy.get(0);
                if (b == null) {
                    double z = p.getZ() + (r.nextBoolean() ? 1 : -1) * (45 + r.nextDouble() * 30);
                    b = s.population.create(level, com.interstice.entity.species.Species.BURDEN, r.nextBoolean() ? -1 : 1,
                            Math.max(IntersticeLayout.FLOOR_Y + 20, p.getY() + 25 + r.nextDouble() * 40), z);
                }
                if (b != null) b.beginCrawl(b.getY() + r.nextGaussian() * 6, b.getZ() + (r.nextBoolean() ? 1 : -1) * (8 + r.nextDouble() * 8), 0.045);
                t.tension += 0.04f;
            }
            case STAMPEDE -> {
                s.stampede.start(level, 0.8f + r.nextFloat() * 0.2f);
                for (ServerPlayer q : level.players()) s.track(q).tension = 0f;
            }
        }
    }

    /** Context-sensitive lines during the Stampede's search: sometimes help, sometimes not. */
    private void stampedeVoices(ServerLevel level, List<ServerPlayer> players, long now) {
        StampedePhase ph = s.stampede.phase();
        if (ph == StampedePhase.IDLE) {
            stampedeVoiceStage.clear();
            return;
        }
        for (ServerPlayer p : players) {
            int stage = stampedeVoiceStage.getOrDefault(p.getUUID(), 0);
            if (ph == StampedePhase.GROUND_SEARCH && stage == 0) {
                List<AdherentEntity> near = level.getEntitiesOfClass(AdherentEntity.class, p.getBoundingBox().inflate(11),
                        e -> e.mode() == AdherentEntity.Mode.GROUND);
                if (!near.isEmpty() && level.random.nextFloat() < 0.25f) {
                    VoiceEvents.say(p, p.isShiftKeyDown() ? "dont_move" : "not_yet", 0.85f);
                    stampedeVoiceStage.put(p.getUUID(), 1);
                }
            } else if (ph == StampedePhase.AFTERMATH && stage < 2 && level.random.nextFloat() < 0.15f) {
                VoiceEvents.say(p, "go", 0.8f);
                stampedeVoiceStage.put(p.getUUID(), 2);
            }
        }
    }
}
