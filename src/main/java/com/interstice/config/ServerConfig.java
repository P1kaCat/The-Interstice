package com.interstice.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.interstice.Interstice;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Gameplay settings owned by the server (config/interstice-server.json).
 * Client presentation settings live in the client-only ClientConfig.
 */
public final class ServerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("interstice-server.json");

    public static ServerConfig INSTANCE = new ServerConfig();

    /** Multiplier on how often the horror director acts (0.25 = rare, 4 = development testing). */
    public float eventFrequency = 1.0f;
    public boolean directorEnabled = true;
    public boolean stampedeEnabled = true;
    /** Minimum minutes between two natural Stampedes (divided by eventFrequency). */
    public float minMinutesBetweenStampedes = 14f;
    public int ambientWallCreaturesPerPlayer = 5;
    public int maxRunnersPerPlayer = 8;
    public int minFallsPerPlayer = 2;
    public int maxFallsPerPlayer = 5;
    public int maxSearchersPerPlayer = 4;
    public float creatureAttackDamage = 7f;
    public boolean voiceEnabled = true;

    // ---- survival (health points: 2 = one heart; durations in ticks)
    public float bandageHeal = 4f;
    public int bandageUseTicks = 50;
    public float medkitHeal = 14f;
    public int medkitUseTicks = 110;
    public float injectorHeal = 8f;
    public int injectorUseTicks = 12;

    // ---- creature family (species ids: adherent, crawler, strider, burden, clinger, hollow, watcher, fractured)
    /** Stampede composition before per-event variation (relative weights). */
    public java.util.Map<String, Float> stampedeSpeciesWeights = weights(65, 12, 8, 5, 3, 3, 2, 2);
    /** Ambient wall population outside the Stampede (relative weights). */
    public java.util.Map<String, Float> ambientSpeciesWeights = weights(50, 12, 7, 4, 12, 6, 6, 3);
    /** Multiplier on each species' chance to lose its grip during the Stampede (1 = species default). */
    public java.util.Map<String, Float> speciesFallChanceMultiplier = weights(1, 1, 1, 1, 1, 1, 1, 1);
    /** Spread of the per-event random variation of the composition (log-normal sigma; 0 = none). */
    public float stampedeCompositionVariation = 0.35f;
    /** Chance that a Stampede is dominated by an unusual species ("surge"), and how strongly. */
    public float stampedeSurgeChance = 0.12f;
    public float stampedeSurgeFactor = 5f;

    private static java.util.Map<String, Float> weights(float... v) {
        java.util.Map<String, Float> m = new java.util.LinkedHashMap<>();
        com.interstice.entity.species.Species[] sp = com.interstice.entity.species.Species.values();
        for (int i = 0; i < sp.length && i < v.length; i++) m.put(sp[i].id, v[i]);
        return m;
    }

    public float fallMultiplier(com.interstice.entity.species.Species s) {
        Float v = speciesFallChanceMultiplier == null ? null : speciesFallChanceMultiplier.get(s.id);
        return v == null ? 1f : Math.max(0f, v);
    }

    public static void load() {
        try {
            if (Files.exists(PATH)) {
                try (Reader r = Files.newBufferedReader(PATH)) {
                    ServerConfig c = GSON.fromJson(r, ServerConfig.class);
                    if (c != null) INSTANCE = c;
                }
            }
            save();
        } catch (Exception e) {
            Interstice.LOG.warn("Could not read {}, using defaults", PATH, e);
        }
    }

    public static void save() {
        try {
            Files.createDirectories(PATH.getParent());
            try (Writer w = Files.newBufferedWriter(PATH)) {
                GSON.toJson(INSTANCE, w);
            }
        } catch (Exception e) {
            Interstice.LOG.warn("Could not write {}", PATH, e);
        }
    }

    private ServerConfig() {}
}
