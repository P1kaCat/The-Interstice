package com.interstice.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/**
 * A floor material whose texture spans 2x2 blocks: each block shows the quarter of a larger texture that matches
 * its position ({@link #TILE} = (x & 1) + 2 (z & 1)), so stains, cracks and grain run on across the joints instead
 * of repeating on every block. The world generator sets the quarter for every block it writes
 * ({@code ChunkWriter.set}); a block placed by hand picks its own from its position.
 */
public class MacroTileBlock extends Block {
    public static final MapCodec<MacroTileBlock> CODEC = simpleCodec(MacroTileBlock::new);
    public static final IntegerProperty TILE = IntegerProperty.create("tile", 0, 3);

    public MacroTileBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(TILE, 0));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(TILE);
    }

    public static int tileAt(int x, int z) {
        return (x & 1) + 2 * (z & 1);
    }

    public static BlockState at(BlockState s, int x, int z) {
        return s.getBlock() instanceof MacroTileBlock ? s.setValue(TILE, tileAt(x, z)) : s;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        BlockPos p = ctx.getClickedPos();
        return defaultBlockState().setValue(TILE, tileAt(p.getX(), p.getZ()));
    }
}
