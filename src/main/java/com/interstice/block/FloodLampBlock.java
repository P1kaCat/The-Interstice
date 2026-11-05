package com.interstice.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/** High-intensity flood light (or the wall searchlight). FACING is the direction the beam points. */
public class FloodLampBlock extends DirectionalBlock {
    public static final MapCodec<FloodLampBlock> CODEC = simpleCodec(FloodLampBlock::new);
    private final LampKind kind;

    public FloodLampBlock(Properties properties) {
        this(LampKind.FLOOD, properties);
    }

    public FloodLampBlock(LampKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.DOWN));
    }

    public LampKind kind() {
        return kind;
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getClickedFace().getOpposite());
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        LampHooks.notifySeen(level, pos, kind);
    }
}
