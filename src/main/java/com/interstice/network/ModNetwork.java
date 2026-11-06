package com.interstice.network;

import com.interstice.Interstice;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server &rarr; client payloads. Major events are synchronised as compact
 * state (phase + start tick + seed); clients derive every visual detail
 * (thousands of horde silhouettes, chain reactions, dust) deterministically, so
 * bandwidth does not scale with the size of the spectacle.
 */
public final class ModNetwork {
    private ModNetwork() {}

    /** Layout seed of the dimension so the client can reconstruct walls, chains and lanes. */
    public record LayoutSync(long seed, int version) implements CustomPacketPayload {
        public static final Type<LayoutSync> TYPE = new Type<>(Interstice.id("layout_sync"));
        public static final StreamCodec<FriendlyByteBuf, LayoutSync> CODEC = CustomPacketPayload.codec(
                (p, b) -> { b.writeLong(p.seed); b.writeVarInt(p.version); }, b -> new LayoutSync(b.readLong(), b.readVarInt()));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Short environmental event (earthquake, chain shudder, light failure, silence, distant impact, stare cue). */
    public record EnvEvent(int kind, long startTick, int duration, float intensity, long seed, double x, double y, double z) implements CustomPacketPayload {
        public static final int QUAKE = 0, CHAIN_SHUDDER = 1, LIGHT_FAILURE = 2, SILENCE = 3, DISTANT_IMPACT = 4, STARE = 5, FLEE = 6, FLASH = 7, HORDE_SCREAM = 8,
                /** Level 02: the screen goes white (the path keeper sets the player back on the track). */
                WHITEOUT = 9;
        public static final Type<EnvEvent> TYPE = new Type<>(Interstice.id("env_event"));
        public static final StreamCodec<FriendlyByteBuf, EnvEvent> CODEC = CustomPacketPayload.codec((p, b) -> {
            b.writeVarInt(p.kind);
            b.writeLong(p.startTick);
            b.writeVarInt(p.duration);
            b.writeFloat(p.intensity);
            b.writeLong(p.seed);
            b.writeDouble(p.x);
            b.writeDouble(p.y);
            b.writeDouble(p.z);
        }, b -> new EnvEvent(b.readVarInt(), b.readLong(), b.readVarInt(), b.readFloat(), b.readLong(), b.readDouble(), b.readDouble(), b.readDouble()));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * Full Stampede state. Sent on every phase change and to late joiners. {@code species} is the event's
     * species composition (one weight per Species ordinal): clients assign species to visual horde
     * instances from it, the server picks real runners from the same weights.
     */
    public record StampedeSync(int phase, long phaseStart, int phaseDuration, long seed, int direction, float intensity, float[] species) implements CustomPacketPayload {
        public static final Type<StampedeSync> TYPE = new Type<>(Interstice.id("stampede_sync"));
        public static final StreamCodec<FriendlyByteBuf, StampedeSync> CODEC = CustomPacketPayload.codec((p, b) -> {
            b.writeVarInt(p.phase);
            b.writeLong(p.phaseStart);
            b.writeVarInt(p.phaseDuration);
            b.writeLong(p.seed);
            b.writeByte(p.direction);
            b.writeFloat(p.intensity);
            b.writeVarInt(p.species.length);
            for (float f : p.species) b.writeFloat(f);
        }, b -> {
            int phase = b.readVarInt();
            long start = b.readLong();
            int dur = b.readVarInt();
            long seed = b.readLong();
            int dir = b.readByte();
            float intensity = b.readFloat();
            int n = Math.min(64, b.readVarInt());
            float[] sp = new float[n];
            for (int i = 0; i < n; i++) sp[i] = b.readFloat();
            return new StampedeSync(phase, start, dur, seed, dir, intensity, sp);
        });

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** The voice. Played intimately close to the listener; never positional in the world. */
    public record Voice(String line, float volume) implements CustomPacketPayload {
        public static final Type<Voice> TYPE = new Type<>(Interstice.id("voice"));
        public static final StreamCodec<FriendlyByteBuf, Voice> CODEC = CustomPacketPayload.codec(
                (p, b) -> { b.writeUtf(p.line, 64); b.writeFloat(p.volume); }, b -> new Voice(b.readUtf(64), b.readFloat()));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Development diagnostics. Only sent to players who enabled them with /interstice debug. */
    public record Debug(boolean enabled, String serverText) implements CustomPacketPayload {
        public static final Type<Debug> TYPE = new Type<>(Interstice.id("debug"));
        public static final StreamCodec<FriendlyByteBuf, Debug> CODEC = CustomPacketPayload.codec(
                (p, b) -> { b.writeBoolean(p.enabled); b.writeUtf(p.serverText, 4096); }, b -> new Debug(b.readBoolean(), b.readUtf(4096)));

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void init() {
        PayloadTypeRegistry.playS2C().register(LayoutSync.TYPE, LayoutSync.CODEC);
        PayloadTypeRegistry.playS2C().register(EnvEvent.TYPE, EnvEvent.CODEC);
        PayloadTypeRegistry.playS2C().register(StampedeSync.TYPE, StampedeSync.CODEC);
        PayloadTypeRegistry.playS2C().register(Voice.TYPE, Voice.CODEC);
        PayloadTypeRegistry.playS2C().register(Debug.TYPE, Debug.CODEC);
    }

    public static void send(ServerPlayer player, CustomPacketPayload payload) {
        if (ServerPlayNetworking.canSend(player, payload.type())) ServerPlayNetworking.send(player, payload);
    }

    public static void sendToLevel(ServerLevel level, CustomPacketPayload payload) {
        for (ServerPlayer p : level.players()) send(p, payload);
    }
}
