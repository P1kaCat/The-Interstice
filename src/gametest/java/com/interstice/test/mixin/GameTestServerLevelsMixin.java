package com.interstice.test.mixin;

import com.google.common.collect.ImmutableList;
import com.interstice.Interstice;
import com.interstice.registry.ModWorld;
import com.interstice.world.generation.IntersticeChunkGenerator;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.FixedBiomeSource;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.WorldData;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.concurrent.Executor;

/**
 * TEST-ONLY. Vanilla's GameTest server bakes its dimensions from the flat preset
 * with an empty level-stem registry, so datapack dimensions (like ours) are not
 * created there, although they are in every normal world. This mixin creates
 * the Interstice level exactly like {@code MinecraftServer#createLevels} does
 * for any extra dimension, and only when running on a GameTestServer.
 */
@Mixin(MinecraftServer.class)
public abstract class GameTestServerLevelsMixin {
    @Shadow @Final private Map<ResourceKey<Level>, ServerLevel> levels;
    @Shadow @Final private Executor executor;
    @Shadow @Final protected LevelStorageSource.LevelStorageAccess storageSource;
    @Shadow @Final protected WorldData worldData;

    @Inject(method = "createLevels", at = @At("TAIL"))
    private void interstice$addDimensionForGameTests(ChunkProgressListener listener, CallbackInfo ci) {
        MinecraftServer self = (MinecraftServer) (Object) this;
        if (!(self instanceof GameTestServer) || levels.containsKey(ModWorld.INTERSTICE)) return;
        var access = self.registryAccess();
        Holder<DimensionType> type = access.registryOrThrow(Registries.DIMENSION_TYPE).getHolderOrThrow(ModWorld.INTERSTICE_TYPE);
        Holder<Biome> biome = access.registryOrThrow(Registries.BIOME).getHolderOrThrow(ResourceKey.create(Registries.BIOME, Interstice.id("the_interstice")));
        LevelStem stem = new LevelStem(type, new IntersticeChunkGenerator(new FixedBiomeSource(biome)));
        long seed = BiomeManager.obfuscateSeed(worldData.worldGenOptions().seed());
        ServerLevel overworld = levels.get(Level.OVERWORLD);
        ServerLevel level = new ServerLevel(self, executor, storageSource, new DerivedLevelData(worldData, worldData.overworldData()),
                ModWorld.INTERSTICE, stem, listener, worldData.isDebugWorld(), seed, ImmutableList.of(), false, overworld.getRandomSequences());
        levels.put(ModWorld.INTERSTICE, level);
        Interstice.LOG.info("[gametest] created {} for headless tests", ModWorld.INTERSTICE.location());
    }
}
