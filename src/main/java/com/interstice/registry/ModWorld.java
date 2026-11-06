package com.interstice.registry;

import com.interstice.Interstice;
import com.interstice.world.generation.IntersticeChunkGenerator;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

public final class ModWorld {
    public static final ResourceKey<Level> INTERSTICE = ResourceKey.create(Registries.DIMENSION, Interstice.id("the_interstice"));
    public static final ResourceKey<DimensionType> INTERSTICE_TYPE = ResourceKey.create(Registries.DIMENSION_TYPE, Interstice.id("the_interstice"));

    public static final ResourceKey<Level> THRESHOLD = com.interstice.world.levels.IntersticeLevel.THRESHOLD.dimension;

    public static void init() {
        Registry.register(BuiltInRegistries.CHUNK_GENERATOR, Interstice.id("interstice"), IntersticeChunkGenerator.CODEC);
        Registry.register(BuiltInRegistries.CHUNK_GENERATOR, Interstice.id("threshold"), com.interstice.world.threshold.ThresholdChunkGenerator.CODEC);
        Registry.register(BuiltInRegistries.CHUNK_GENERATOR, Interstice.id("wheat_farm"), com.interstice.world.wheat.WheatChunkGenerator.CODEC);
    }

    /** Level 01, The Corridor (the original dimension: its creatures, Stampede, lights and layout). */
    public static boolean isInterstice(Level level) {
        return level != null && level.dimension() == INTERSTICE;
    }

    /** Any of the five levels of THE INTERSTICE (shared rules: survival, no vanilla music). */
    public static boolean isAnyLevel(Level level) {
        return com.interstice.world.levels.IntersticeLevel.of(level) != null;
    }

    public static final ResourceKey<Level> WHEAT_FARM = com.interstice.world.levels.IntersticeLevel.WHEAT_FARM.dimension;

    public static boolean isWheatFarm(Level level) {
        return level != null && level.dimension() == WHEAT_FARM;
    }

    public static boolean isThreshold(Level level) {
        return level != null && level.dimension() == THRESHOLD;
    }

    private ModWorld() {}
}
