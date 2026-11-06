package com.interstice.world.levels;

import com.interstice.Interstice;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * The five levels of THE INTERSTICE, in the order they are crossed. Each is its own dimension with its own generator;
 * a rift at the end of a level opens on the next one ({@link com.interstice.block.RiftBlock}).
 * <ul>
 *   <li>00 The Threshold: a short twisting passage, the white rift (no creatures);</li>
 *   <li>01 The Corridor: the original dimension, {@code interstice:the_interstice};</li>
 *   <li>02 The Wheat Farm, 03 The Compression, 04 The Impossible: their dimensions are registered as they are built;
 *       a rift to a level whose dimension is not loaded leads out of THE INTERSTICE instead.</li>
 * </ul>
 */
public enum IntersticeLevel {
    THRESHOLD(0, "threshold", "The Threshold"),
    CORRIDOR(1, "the_interstice", "The Corridor"),
    WHEAT_FARM(2, "wheat_farm", "The Wheat Farm"),
    COMPRESSION(3, "compression", "The Compression"),
    IMPOSSIBLE(4, "impossible", "The Impossible");

    public final int number;
    public final ResourceKey<Level> dimension;
    public final String title;

    IntersticeLevel(int number, String path, String title) {
        this.number = number;
        this.dimension = ResourceKey.create(Registries.DIMENSION, Interstice.id(path));
        this.title = title;
    }

    /** The level after this one, or null after the last. */
    public IntersticeLevel next() {
        int i = ordinal() + 1;
        return i < values().length ? values()[i] : null;
    }

    /** The level this dimension is, or null outside THE INTERSTICE. */
    public static IntersticeLevel of(ResourceKey<Level> dimension) {
        for (IntersticeLevel l : values()) if (l.dimension == dimension || l.dimension.equals(dimension)) return l;
        return null;
    }

    public static IntersticeLevel of(Level level) {
        return level == null ? null : of(level.dimension());
    }

    public String label() {
        return String.format("Level %02d - %s", number, title);
    }
}
