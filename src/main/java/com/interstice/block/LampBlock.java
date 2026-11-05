package com.interstice.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Industrial and emergency fixtures. The vanilla light level keeps the world
 * readable without Veil; the client lamp manager attaches a Veil dynamic light
 * to each fixture near the camera (see client.render.veil.LampLightManager).
 * <p>
 * A fixture is always mounted on something: {@link #FACING} is the direction it points, away from its support
 * (UP: standing on the floor or a post, DOWN: hanging from a ceiling, horizontal: a sconce on a wall). The world
 * generator sets it, and {@code LampMounts} fixes any fixture left without a support.
 */
public class LampBlock extends Block {
    public static final MapCodec<LampBlock> CODEC = simpleCodec(p -> new LampBlock(LampKind.INDUSTRIAL, p));
    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    private static final VoxelShape UP = Block.box(4, 0, 4, 12, 9, 12);
    private static final VoxelShape DOWN = Block.box(4, 6, 4, 12, 16, 12);
    private static final VoxelShape SOUTH = Block.box(4, 4, 0, 12, 12, 9);   // on the north wall, pointing south
    private static final VoxelShape NORTH = Block.box(4, 4, 7, 12, 12, 16);
    private static final VoxelShape EAST = Block.box(0, 4, 4, 9, 12, 12);
    private static final VoxelShape WEST = Block.box(7, 4, 4, 16, 12, 12);

    private final LampKind kind;

    public LampBlock(LampKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP));
    }

    public LampKind kind() {
        return kind;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getClickedFace());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case UP -> UP;
            case DOWN -> DOWN;
            case SOUTH -> SOUTH;
            case NORTH -> NORTH;
            case EAST -> EAST;
            case WEST -> WEST;
        };
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // animateTick only runs on the client: lets the light manager discover
        // fixtures that were placed after their chunk was scanned.
        LampHooks.notifySeen(level, pos, kind);
    }

    /** A lamp of this state pointing {@code facing} (mounted on the opposite side). */
    public static BlockState facing(BlockState lamp, Direction facing) {
        return lamp.getBlock() instanceof LampBlock ? lamp.setValue(FACING, facing) : lamp;
    }
}
