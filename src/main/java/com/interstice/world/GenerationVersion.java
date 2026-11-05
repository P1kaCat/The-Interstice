package com.interstice.world;

import com.interstice.Interstice;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * World-generation version of the Interstice dimension in the current save.
 * <ul>
 *   <li>1: the original endless Corridor (no regions, no buildings, no ends).</li>
 *   <li>2: the finite Corridor of the 2026-11 rework ({@link CorridorPlan}): regions, explorable buildings,
 *       loot, bulkheads and an exit.</li>
 * </ul>
 * Existing saves are never silently changed: a save whose Interstice dimension already has generated region
 * files but no version marker keeps version 1 (chunks generated later in it continue the old layout seamlessly).
 * A new dimension gets the current version, written to {@code dimensions/interstice/the_interstice/interstice_generation.txt}.
 * To move an old save to the new generation, delete that dimension's folder (its content is lost) or write
 * {@code 2} into the marker file before loading (old and new chunks will then not match where they meet).
 */
public final class GenerationVersion {
    public static final int LEGACY = 1, CURRENT = 2;
    private static volatile int current = CURRENT;
    private static volatile boolean forced;

    private GenerationVersion() {}

    public static int get() {
        return current;
    }

    /** Test servers (and tools) pin the version regardless of what is on disk. */
    public static void force(int version) {
        current = version;
        forced = true;
    }

    /** Called when a server starts, before its levels are created. */
    public static void detect(MinecraftServer server) {
        if (forced) return;
        Path dim = server.getWorldPath(LevelResource.ROOT).resolve("dimensions").resolve(Interstice.MOD_ID).resolve("the_interstice");
        Path marker = dim.resolve("interstice_generation.txt");
        int v = CURRENT;
        try {
            if (Files.exists(marker)) {
                v = Integer.parseInt(Files.readString(marker).trim());
            } else if (hasRegions(dim.resolve("region"))) {
                v = LEGACY;
                Interstice.LOG.info("Interstice: this save's dimension predates generation version {}; keeping version {} (see GenerationVersion)", CURRENT, LEGACY);
                write(marker, v);
            } else {
                write(marker, v);
            }
        } catch (IOException | NumberFormatException e) {
            Interstice.LOG.warn("Interstice: could not read/write the generation marker ({}); using version {}", e.toString(), v);
        }
        current = v;
        Interstice.LOG.info("Interstice: world generation version {}", v);
    }

    private static boolean hasRegions(Path region) throws IOException {
        if (!Files.isDirectory(region)) return false;
        try (Stream<Path> s = Files.list(region)) {
            return s.anyMatch(p -> p.getFileName().toString().endsWith(".mca"));
        }
    }

    private static void write(Path marker, int v) throws IOException {
        Files.createDirectories(marker.getParent());
        Files.writeString(marker, Integer.toString(v));
    }
}
