package com.interstice.world.wheat;

import com.interstice.registry.ModBlocks;
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
import net.minecraft.world.level.block.FenceBlock;
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
 * Level 02, The Wheat Farm (see {@link WheatLayout}): ground, the track, fences, the turbines' footings and the rift.
 * The wheat itself and the turbines are drawn by the client; the ground's top block in the fields is a golden field
 * surface, so the landscape reads as wheat out to the horizon where no stalks are drawn.
 */
public class WheatChunkGenerator extends ChunkGenerator {
    public static final MapCodec<WheatChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource)
    ).apply(i, WheatChunkGenerator::new));

    public WheatChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk) {
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ(), minY = chunk.getMinBuildHeight();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockState soil = ModBlocks.GRIME_SOIL.defaultBlockState(), floor = ModBlocks.ABYSSAL_ROCK.defaultBlockState();
        BlockState field = ModBlocks.WHEAT_FIELD.defaultBlockState(), path = ModBlocks.FARM_PATH.defaultBlockState();
        BlockState fence = ModBlocks.WORN_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true).setValue(FenceBlock.SOUTH, true);
        for (int dz = 0; dz < 16; dz++) {
            for (int dx = 0; dx < 16; dx++) {
                int x = x0 + dx, z = z0 + dz;
                int gy = WheatLayout.groundY(x, z);
                chunk.setBlockState(p.set(x, minY, z), floor, false);
                for (int y = minY + 1; y < gy - 1; y++) chunk.setBlockState(p.set(x, y, z), soil, false);
                chunk.setBlockState(p.set(x, gy - 1, z), WheatLayout.onRoute(x, z) ? path : field, false);
                if (WheatLayout.isFence(x, z)) {
                    boolean n = WheatLayout.isFence(x, z - 1), s = WheatLayout.isFence(x, z + 1);
                    chunk.setBlockState(p.set(x, gy, z), fence.setValue(FenceBlock.NORTH, n).setValue(FenceBlock.SOUTH, s), false);
                }
                if (z == WheatLayout.RIFT_Z && x == 0)
                    for (int y = gy; y < gy + 2; y++) chunk.setBlockState(p.set(x, y, z), ModBlocks.RIFT.defaultBlockState(), false);
            }
        }
        FarmHouse.build(chunk, x0, z0);
        Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.WORLD_SURFACE_WG, Heightmap.Types.OCEAN_FLOOR_WG,
                Heightmap.Types.MOTION_BLOCKING, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Heightmap.Types.WORLD_SURFACE, Heightmap.Types.OCEAN_FLOOR));
        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState, BiomeManager biomeManager, StructureManager structureManager,
                             ChunkAccess chunk, GenerationStep.Carving carving) {
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structureManager, RandomState randomState, ChunkAccess chunk) {
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
    }

    @Override
    public int getGenDepth() {
        return 384;
    }

    @Override
    public int getSeaLevel() {
        return -64;
    }

    @Override
    public int getMinY() {
        return -64;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState randomState) {
        return WheatLayout.groundY(x, z);
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
        BlockState[] states = new BlockState[level.getHeight()];
        int gy = WheatLayout.groundY(x, z);
        for (int i = 0; i < states.length; i++) {
            int y = level.getMinBuildHeight() + i;
            states[i] = y < gy ? ModBlocks.GRIME_SOIL.defaultBlockState() : Blocks.AIR.defaultBlockState();
        }
        return new NoiseColumn(level.getMinBuildHeight(), states);
    }

    @Override
    public void addDebugScreenInfo(List<String> list, RandomState randomState, BlockPos pos) {
        list.add("Interstice: Level 02, The Wheat Farm (track " + pos.getZ() + " / " + WheatLayout.ROUTE_END + ")");
    }
}
