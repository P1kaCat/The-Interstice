package com.interstice.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.interstice.Interstice;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Presentation settings (config/interstice-client.json). Every field is read live by a render/audio system. */
public final class ClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("interstice-client.json");

    public static ClientConfig INSTANCE = new ClientConfig();

    public GraphicsPreset preset = GraphicsPreset.HIGH;
    /** 0.4 .. 1.6: multiplies fog density. */
    public float fogDensity = 1.0f;
    /** Black fog: visibility distance multiplier (the fog still always closes before the render distance). */
    public float fogDistance = 1.0f;
    /** Black fog: how fast the walls disappear upwards (0.5 = higher, 2 = sooner). */
    public float verticalFog = 1.0f;
    /** The pre-rework rendered wall continuation above y=300 (off: the walls vanish into the black fog instead). */
    public boolean legacyWallContinuation = false;
    public boolean dynamicLighting = true;
    public boolean volumetricLight = true;
    public boolean postProcessing = true;
    public boolean bloom = true;
    /** 0 .. 2: multiplier on particle budgets. */
    public float particles = 1.0f;
    /** 0 .. 3: overrides the preset's chain detail when >= 0. */
    public int chainDetail = -1;
    /** 0 .. 1.5: camera shake strength. 0 disables shake entirely. */
    public float cameraShake = 1.0f;
    /** Softens flicker, strobing and sudden brightness changes. */
    public boolean reduceFlashing = false;
    /** Disables chromatic aberration, grain bursts and other intense effects. */
    public boolean reduceIntenseEffects = false;
    public float ambienceVolume = 1.0f;
    public float voiceVolume = 1.0f;
    public boolean environmentalAudio = true;
    public boolean voiceSubtitles = true;
    public boolean diagnostics = false;

    public int chainLod() {
        return chainDetail >= 0 ? Math.min(3, chainDetail) : preset.chainLod;
    }

    public static void load() {
        try {
            if (Files.exists(PATH)) {
                try (Reader r = Files.newBufferedReader(PATH)) {
                    ClientConfig c = GSON.fromJson(r, ClientConfig.class);
                    if (c != null) INSTANCE = c;
                }
            }
            if (INSTANCE.preset == null) INSTANCE.preset = GraphicsPreset.HIGH;
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
}
