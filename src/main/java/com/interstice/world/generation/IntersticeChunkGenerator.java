package com.interstice.world.generation;

import com.interstice.Interstice;
import com.interstice.world.IntersticeLayout;
import com.interstice.world.anomalies.AnomalyPass;
import com.interstice.world.structures.StructurePass;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Deterministic generator for THE INTERSTICE.
 * <p>
 * Passes, in order: terrain (walls, floor, shafts, passages) &rarr; structures
 * (walkways, beams, platforms, shelters, pipes, lights, landmarks) &rarr;
 * anomalies. All passes are pure functions of the layout seed and world
 * coordinates; there is no cross-chunk state and no synchronous access to
 * neighbouring chunks, so generation runs safely on worldgen worker threads.
 */
public class IntersticeChunkGenerator extends ChunkGenerator {
    public static final MapCodec<IntersticeChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource)
    ).apply(i, IntersticeChunkGenerator::new));

    public IntersticeChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    /** Layout seed derived from the world seed through the level's RandomState (identical on every server start). */
    public static long layoutSeed(RandomState randomState) {
        return randomState.getOrCreateRandomFactory(Interstice.id("layout")).at(0, 0, 0).nextLong();
    }

    private static volatile IntersticeLayout cached;

    public static IntersticeLayout layout(RandomState randomState) {
        long seed = layoutSeed(randomState);
        IntersticeLayout l = cached;
        if (l == null || l.seed() != seed) {
            l = new IntersticeLayout(seed);
            cached = l;
        }
        return l;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk) {
        ChunkWriter w = new ChunkWriter(chunk, layout(randomState));
        TerrainPass.run(w);
        StructurePass.run(w);
        AnomalyPass.run(w);
        Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.WORLD_SURFACE_WG, Heightmap.Types.OCEAN_FLOOR_WG,
                Heightmap.Types.MOTION_BLOCKING, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Heightmap.Types.WORLD_SURFACE, Heightmap.Types.OCEAN_FLOOR));
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState, BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunk, GenerationStep.Carving carving) {
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structureManager, RandomState randomState, ChunkAccess chunk) {
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
    }

    @Override
    public int getGenDepth() {
        return IntersticeLayout.MAX_Y - IntersticeLayout.MIN_Y + 1;
    }

    @Override
    public int getSeaLevel() {
        return IntersticeLayout.MIN_Y;
    }

    @Override
    public int getMinY() {
        return IntersticeLayout.MIN_Y;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState randomState) {
        IntersticeLayout L = layout(randomState);
        if (L.wallDepth(x + 0.5, z + 0.5) >= 0) return IntersticeLayout.WALL_TOP;
        return L.floorY(x, z);
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
        int h = getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, randomState);
        BlockState[] states = new BlockState[level.getHeight()];
        for (int i = 0; i < states.length; i++) {
            int y = level.getMinBuildHeight() + i;
            states[i] = y < h ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
        }
        return new NoiseColumn(level.getMinBuildHeight(), states);
    }

    @Override
    public void addDebugScreenInfo(List<String> list, RandomState randomState, BlockPos pos) {
        IntersticeLayout L = layout(randomState);
        list.add(String.format("Interstice: depth %.1f, width %.1f, anomaly %.2f", L.wallDepth(pos.getX(), pos.getZ()), L.halfWidth(pos.getZ()) * 2, L.anomalyLevel(pos.getZ())));
    }
}
