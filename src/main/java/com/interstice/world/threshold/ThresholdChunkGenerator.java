package com.interstice.world.threshold;

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
import net.minecraft.world.level.block.LightBlock;
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
 * Level 00, The Threshold. The only real blocks are invisible (see {@link ThresholdLayout}): the corridor's shell of
 * barriers (floor, walls, ceiling, back wall), a few invisible light sources and the rift filling its far end. The
 * visible corridor, twisted, is drawn by the client. Fixed coordinates, so generation is deterministic and chunk borders
 * cannot show seams.
 */
public class ThresholdChunkGenerator extends ChunkGenerator {
    public static final MapCodec<ThresholdChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource)
    ).apply(i, ThresholdChunkGenerator::new));

    public ThresholdChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk) {
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        BlockState wall = Blocks.BARRIER.defaultBlockState();
        BlockState rift = ModBlocks.RIFT.defaultBlockState();
        BlockState light = Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, 6);
        int fy = ThresholdLayout.FLOOR_Y, hw = ThresholdLayout.HALF_WIDTH, h = ThresholdLayout.HEIGHT;
        // the corridor's shell is all invisible: floor, walls, ceiling, back wall (the client draws the twisted corridor)
        for (int z = Math.max(z0, ThresholdLayout.START_Z - 1); z <= Math.min(z0 + 15, ThresholdLayout.RIFT_Z + 1); z++) {
            for (int x = Math.max(x0, -hw - 1); x <= Math.min(x0 + 15, hw + 1); x++) {
                for (int y = fy - 1; y <= fy + h; y++) {
                    boolean shell = x == -hw - 1 || x == hw + 1 || y == fy - 1 || y == fy + h
                            || z == ThresholdLayout.START_Z - 1 || z == ThresholdLayout.RIFT_Z + 1;
                    BlockState s;
                    if (shell) s = wall;
                    else if (z == ThresholdLayout.RIFT_Z) s = x == 0 && y < fy + 4 ? rift : wall;   // the end wall; the tear at x 0
                    else if (x == 0 && y == fy + h - 1 && Math.floorMod(z, 4) == 2) s = light;       // a dim glow for the player and the hand
                    else continue;
                    chunk.setBlockState(p.set(x, y, z), s, false);
                }
            }
        }
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
        return Math.abs(x) <= ThresholdLayout.HALF_WIDTH + 1 && z >= ThresholdLayout.START_Z - 1 && z <= ThresholdLayout.RIFT_Z + 1
                ? ThresholdLayout.FLOOR_Y : level.getMinBuildHeight();
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
        BlockState[] states = new BlockState[level.getHeight()];
        java.util.Arrays.fill(states, Blocks.AIR.defaultBlockState());
        return new NoiseColumn(level.getMinBuildHeight(), states);
    }

    @Override
    public void addDebugScreenInfo(List<String> list, RandomState randomState, BlockPos pos) {
        list.add("Interstice: Level 00, The Threshold (twist " + Math.round(Math.toDegrees(ThresholdLayout.twist(pos.getZ() + 0.5))) + " deg)");
    }
}
