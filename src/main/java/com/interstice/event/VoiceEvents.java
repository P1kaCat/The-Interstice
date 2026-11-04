package com.interstice.event;

import com.interstice.config.ServerConfig;
import com.interstice.network.ModNetwork;
import com.interstice.registry.ModSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;

/**
 * The voice. Lines are data-driven by id ("voice.&lt;id&gt;" sound events with
 * matching subtitle keys), so new recordings and narrative beats can be added
 * without code changes beyond registering the id in {@link ModSounds#VOICE_LINES}.
 * <p>
 * Context rules live in the horror director: the voice is not a narrator,
 * its intent is never stated, and some lines are deliberately ambiguous.
 */
public final class VoiceEvents {
    private VoiceEvents() {}

    public static boolean isLine(String id) {
        return Arrays.asList(ModSounds.VOICE_LINES).contains(id);
    }

    public static void say(ServerPlayer p, String line, float volume) {
        if (!ServerConfig.INSTANCE.voiceEnabled || !isLine(line)) return;
        ModNetwork.send(p, new ModNetwork.Voice(line, volume));
    }

    public static void sayToLevel(ServerLevel level, String line, float volume) {
        for (ServerPlayer p : level.players()) say(p, line, volume);
    }
}
