package com.interstice.world.generation;

import com.interstice.world.IntersticeLayout;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;

/**
 * Chunk-clipped block writer using world coordinates. Every structure pass
 * describes its geometry in world space and simply lets this class discard
 * writes outside the current chunk: this is what makes multi-chunk features
 * (bridges, beams, landmarks) connect seamlessly across chunk borders while
 * each chunk is generated independently and deterministically.
 */
public final class ChunkWriter {
    public final ChunkAccess chunk;
    public final IntersticeLayout layout;
    public final int minX, minZ, maxX, maxZ;
    private final int minY, maxY;

    public ChunkWriter(ChunkAccess chunk, IntersticeLayout layout) {
        this.chunk = chunk;
        this.layout = layout;
        this.minX = chunk.getPos().getMinBlockX();
        this.minZ = chunk.getPos().getMinBlockZ();
        this.maxX = minX + 15;
        this.maxZ = minZ + 15;
        this.minY = chunk.getMinBuildHeight();
        this.maxY = chunk.getMaxBuildHeight() - 1;
    }

    public boolean inside(int x, int y, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ && y >= minY && y <= maxY;
    }

    public boolean overlapsX(int x0, int x1) {
        return x1 >= minX && x0 <= maxX;
    }

    public boolean overlapsZ(int z0, int z1) {
        return z1 >= minZ && z0 <= maxZ;
    }

    public void set(int x, int y, int z, BlockState state) {
        if (!inside(x, y, z)) return;
        state = VanillaSubstitutes.swap(state);                         // the Corridor's own copies of vanilla blocks
        state = com.interstice.block.MacroTileBlock.at(state, x, z);   // textures spanning 2x2 blocks: pick the quarter
        LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
        BlockState old = section.setBlockState(x & 15, y & 15, z & 15, state, false);
        // a container replaced by something else must not leave its pending block entity behind
        if (old.hasBlockEntity() && old.getBlock() != state.getBlock()) chunk.removeBlockEntity(new net.minecraft.core.BlockPos(x, y, z));
    }

    public BlockState get(int x, int y, int z) {
        if (!inside(x, y, z)) return Blocks.VOID_AIR.defaultBlockState();
        LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
        return section.getBlockState(x & 15, y & 15, z & 15);
    }

    public void setIfAir(int x, int y, int z, BlockState state) {
        if (inside(x, y, z) && get(x, y, z).isAir()) set(x, y, z, state);
    }

    public void fill(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
        int ax = Math.max(Math.min(x0, x1), minX), bx = Math.min(Math.max(x0, x1), maxX);
        int az = Math.max(Math.min(z0, z1), minZ), bz = Math.min(Math.max(z0, z1), maxZ);
        int ay = Math.max(Math.min(y0, y1), minY), by = Math.min(Math.max(y0, y1), maxY);
        for (int x = ax; x <= bx; x++)
            for (int z = az; z <= bz; z++)
                for (int y = ay; y <= by; y++)
                    set(x, y, z, state);
    }

    public void fillIfAir(int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
        int ax = Math.max(Math.min(x0, x1), minX), bx = Math.min(Math.max(x0, x1), maxX);
        int az = Math.max(Math.min(z0, z1), minZ), bz = Math.min(Math.max(z0, z1), maxZ);
        int ay = Math.max(Math.min(y0, y1), minY), by = Math.min(Math.max(y0, y1), maxY);
        for (int x = ax; x <= bx; x++)
            for (int z = az; z <= bz; z++)
                for (int y = ay; y <= by; y++)
                    setIfAir(x, y, z, state);
    }

    /**
     * A block entity to create with the chunk (e.g. a loot chest): the NBT is kept as pending block-entity data
     * and turned into the real block entity when the chunk is promoted. Ignored outside this chunk.
     */
    public void blockEntity(int x, int y, int z, net.minecraft.nbt.CompoundTag tag) {
        if (!inside(x, y, z)) return;
        tag.putInt("x", x);
        tag.putInt("y", y);
        tag.putInt("z", z);
        chunk.setBlockEntityNbt(tag);
    }

    /** A loot container at (x, y, z) filled on first opening from {@code lootTable} (the block itself is set by the caller). */
    public void lootChest(int x, int y, int z, String blockEntityId, String lootTable, long seed) {
        net.minecraft.nbt.CompoundTag t = new net.minecraft.nbt.CompoundTag();
        t.putString("id", VanillaSubstitutes.blockEntityId(blockEntityId));
        t.putString("LootTable", lootTable);
        t.putLong("LootTableSeed", seed);
        blockEntity(x, y, z, t);
    }

    /** World X of a point at wall-depth {@code d} on the given side (negative d = in the corridor). */
    public int xAt(int side, double d, int z) {
        return (int) Math.floor(layout.centerX(z + 0.5) + side * (layout.halfWidth(z + 0.5) + d));
    }
}
