package com.interstice.event;

import com.interstice.core.IntersticeServer;
import com.interstice.network.ModNetwork;
import com.interstice.network.ModNetwork.EnvEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Short environmental events. The server decides <i>that</i> something happens
 * and broadcasts compact parameters; each client turns them into synchronized
 * sound, chain motion, light failures, dust and camera shake.
 */
public final class EnvironmentEvents {
    private final IntersticeServer s;
    private long quakeEnd;

    public EnvironmentEvents(IntersticeServer s) {
        this.s = s;
    }

    public boolean quakeActive(long now) {
        return now < quakeEnd;
    }

    public void tick(ServerLevel level) {
    }

    public void quake(ServerLevel level, float intensity, int duration, boolean reactCreatures) {
        long now = level.getGameTime();
        quakeEnd = Math.max(quakeEnd, now + duration);
        broadcast(level, new EnvEvent(EnvEvent.QUAKE, now, duration, intensity, level.random.nextLong(), 0, 0, 0));
        if (reactCreatures) {
            for (ServerPlayer p : level.players()) s.population.react(level, p.position(), 80, true, null);
        }
    }

    public void chainShudder(ServerLevel level, float intensity) {
        broadcast(level, new EnvEvent(EnvEvent.CHAIN_SHUDDER, level.getGameTime(), 120, intensity, level.random.nextLong(), 0, 0, 0));
    }

    public void lightFailure(ServerPlayer p, int duration) {
        ServerLevel level = p.serverLevel();
        ModNetwork.send(p, new EnvEvent(EnvEvent.LIGHT_FAILURE, level.getGameTime(), duration, 1f, level.random.nextLong(), p.getX(), p.getY(), p.getZ()));
    }

    public void silence(ServerPlayer p, int duration) {
        ServerLevel level = p.serverLevel();
        ModNetwork.send(p, new EnvEvent(EnvEvent.SILENCE, level.getGameTime(), duration, 1f, 0, 0, 0, 0));
    }

    public void distantImpact(ServerLevel level, Vec3 at, float intensity) {
        broadcast(level, new EnvEvent(EnvEvent.DISTANT_IMPACT, level.getGameTime(), 100, intensity, level.random.nextLong(), at.x, at.y, at.z));
    }

    /** Wall creatures near the player stop and stare at something the player cannot see. */
    public void stare(ServerPlayer p, Vec3 at, int duration) {
        ServerLevel level = p.serverLevel();
        s.population.react(level, p.position(), 70, false, at);
        ModNetwork.send(p, new EnvEvent(EnvEvent.STARE, level.getGameTime(), duration, 1f, 0, at.x, at.y, at.z));
    }

    public void flee(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        s.population.react(level, p.position(), 70, true, null);
        ModNetwork.send(p, new EnvEvent(EnvEvent.FLEE, level.getGameTime(), 60, 1f, 0, p.getX(), p.getY(), p.getZ()));
    }

    private void broadcast(ServerLevel level, EnvEvent e) {
        ModNetwork.sendToLevel(level, e);
    }
}
