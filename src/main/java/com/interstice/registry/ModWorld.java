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

    public static void init() {
        Registry.register(BuiltInRegistries.CHUNK_GENERATOR, Interstice.id("interstice"), IntersticeChunkGenerator.CODEC);
    }

    public static boolean isInterstice(Level level) {
        return level != null && level.dimension() == INTERSTICE;
    }

    private ModWorld() {}
}
