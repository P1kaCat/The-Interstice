package com.interstice.world.wheat;

import com.interstice.event.VoiceEvents;
import com.interstice.network.ModNetwork;
import com.interstice.registry.ModWorld;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Level 02 keeps the player on the track. Whoever goes past the fence line (through a gap, over it) hears a whisper,
 * "Do not leave... this early.", the screen goes white, and while it is white they are set back in the middle of the track,
 * facing the farmhouse, at the same distance along it; the white then fades. Creative and spectator players are left
 * alone (building, testing).
 */
public final class PathKeeper {
    /** Ticks from the whisper to the return (the client's white is full by then) and the whole fade's length. */
    private static final int RETURN_AFTER = 18, FADE = 52;
    private static final double LIMIT = WheatLayout.FENCE_X + 0.9;
    private static final Map<UUID, Long> pending = new HashMap<>();

    private PathKeeper() {}

    public static void tick(MinecraftServer server) {
        ServerLevel level = server.getLevel(ModWorld.WHEAT_FARM);
        if (level == null) return;
        long now = level.getGameTime();
        for (ServerPlayer p : level.players()) {
            if (p.isCreative() || p.isSpectator()) continue;
            Long at = pending.get(p.getUUID());
            if (at != null) {
                if (now >= at) {
                    pending.remove(p.getUUID());
                    double z = Mth.clamp(p.getZ(), WheatLayout.SPAWN_Z, WheatLayout.RIFT_Z - 3);
                    p.teleportTo(level, WheatLayout.SPAWN_X, WheatLayout.groundY(0, (int) z), z, 0f, 0f);
                    p.setDeltaMovement(0, 0, 0);
                    p.resetFallDistance();
                }
                continue;
            }
            boolean off = Math.abs(p.getX() - 0.5) > LIMIT || p.getZ() < -1.5 || p.getZ() > WheatLayout.HOUSE_Z + 1;
            if (!off) continue;
            VoiceEvents.say(p, "do_not_leave", 1.0f);
            ModNetwork.send(p, new ModNetwork.EnvEvent(ModNetwork.EnvEvent.WHITEOUT, now, FADE, 1f, 0L, p.getX(), p.getY(), p.getZ()));
            pending.put(p.getUUID(), now + RETURN_AFTER);
        }
        pending.keySet().removeIf(id -> level.getPlayerByUUID(id) == null);
    }
}
