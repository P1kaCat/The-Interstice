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
