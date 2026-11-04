package com.interstice.core;

import com.interstice.entity.ai.NoiseMap;
import com.interstice.event.EnvironmentEvents;
import com.interstice.event.director.HorrorDirector;
import com.interstice.event.stampede.StampedeController;
import com.interstice.entity.WallPopulation;
import com.interstice.network.ModNetwork;
import com.interstice.registry.ModWorld;
import com.interstice.world.IntersticeLayout;
import com.interstice.world.generation.IntersticeChunkGenerator;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-authoritative state for the dimension: event scheduling, the noise
 * map creatures listen to, creature population and per-player tracking.
 * One instance per running server; nothing here is touched by client code.
 */
public final class IntersticeServer {
    private static IntersticeServer instance;

    public final MinecraftServer server;
    public final NoiseMap noise = new NoiseMap();
    public final StampedeController stampede = new StampedeController(this);
    public final EnvironmentEvents environment = new EnvironmentEvents(this);
    public final HorrorDirector director = new HorrorDirector(this);
    public final WallPopulation population = new WallPopulation(this);
    public final Set<UUID> debugPlayers = new HashSet<>();
    private final Map<UUID, PlayerTrack> tracks = new HashMap<>();
    private IntersticeLayout layout;

    private IntersticeServer(MinecraftServer server) {
        this.server = server;
    }

    public static void start(MinecraftServer server) {
        instance = new IntersticeServer(server);
    }

    public static void stop() {
        instance = null;
    }

    public static IntersticeServer get() {
        return instance;
    }

    public ServerLevel level() {
        return server.getLevel(ModWorld.INTERSTICE);
    }

    public IntersticeLayout layout() {
        if (layout == null) {
            ServerLevel l = level();
            if (l == null) return null;
            layout = IntersticeChunkGenerator.layout(l.getChunkSource().randomState());
        }
        return layout;
    }

    public long time() {
        ServerLevel l = level();
        return l == null ? 0 : l.getGameTime();
    }

    public PlayerTrack track(ServerPlayer p) {
        return tracks.computeIfAbsent(p.getUUID(), u -> new PlayerTrack());
    }

    public void forget(ServerPlayer p) {
        tracks.remove(p.getUUID());
        stampede.forget(p);
    }

    public void syncTo(ServerPlayer p) {
        IntersticeLayout l = layout();
        if (l != null) ModNetwork.send(p, new ModNetwork.LayoutSync(l.seed()));
        stampede.syncTo(p);
    }

    /** Called every server tick (END). */
    public void tick() {
        ServerLevel level = level();
        if (level == null) return;
        long now = level.getGameTime();
        noise.prune(now);
        for (ServerPlayer p : level.players()) updateTrack(level, p, now);
        stampede.tick(level);
        environment.tick(level);
        director.tick(level);
        population.tick(level);
        if (now % 20 == 0 && !debugPlayers.isEmpty()) sendDebug(level);
    }

    private void updateTrack(ServerLevel level, ServerPlayer p, long now) {
        PlayerTrack t = track(p);
        Vec3 pos = p.position();
        if (t.last != null) {
            double dx = pos.x - t.last.x, dz = pos.z - t.last.z;
            t.speed = t.speed * 0.6 + Math.sqrt(dx * dx + dz * dz) * 0.4;
        }
        t.last = pos;
        t.ticksInDimension++;
        boolean onGround = p.onGround();
        if (!p.isSpectator() && !p.isCreative()) {
            if (onGround && !t.wasOnGround && t.lastFall > 2.0f) noise.emit(pos, 10 + Math.min(10, t.lastFall), now, p);
            if (!onGround && t.wasOnGround && p.getDeltaMovement().y > 0.2) noise.emit(pos, 7, now, p);
            if (now % 5 == 0 && onGround) {
                if (p.isSprinting()) noise.emit(pos, 17, now, p);
                else if (!p.isShiftKeyDown() && t.speed > 0.06) noise.emit(pos, 6.5f, now, p);
            }
        }
        t.lastFall = p.fallDistance;
        t.wasOnGround = onGround;
    }

    private void sendDebug(ServerLevel level) {
        int creatures = population.countAll(level);
        String text = "phase " + stampede.phase() + " (" + stampede.phaseTicksLeft(level.getGameTime()) + "t left)"
                + "\ndirector " + director.describe()
                + "\nreal creatures " + creatures + "  noises " + noise.size()
                + "\nquake " + environment.quakeActive(level.getGameTime());
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            if (debugPlayers.contains(p.getUUID())) ModNetwork.send(p, new ModNetwork.Debug(true, text));
        }
    }

    public static final class PlayerTrack {
        public Vec3 last;
        public double speed;
        public long ticksInDimension;
        public boolean wasOnGround = true;
        public float lastFall;
        public float tension;
        public long lastEncounterTick;
    }
}
