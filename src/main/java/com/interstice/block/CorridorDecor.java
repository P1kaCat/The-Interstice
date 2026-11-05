package com.interstice.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Corridor's own versions of the vanilla furniture and workstations that stood in its buildings (cauldron,
 * smoker, blast furnace, brewing stand, anvil, piston, hopper, lightning rod, hanging roots). They keep the vanilla
 * silhouettes but are scenery: nothing to cook, brew or push in a dead city. See {@code VanillaSubstitutes}.
 */
public final class CorridorDecor {
    private CorridorDecor() {}

    public static final VoxelShape BASIN = Shapes.join(Shapes.block(),
            Shapes.or(Block.box(0, 0, 4, 16, 3, 12), Block.box(4, 0, 0, 12, 3, 16), Block.box(2, 0, 2, 14, 3, 14), Block.box(2, 4, 2, 14, 16, 14)),
            BooleanOp.ONLY_FIRST);
    public static final VoxelShape FUNNEL = Shapes.or(Block.box(0, 10, 0, 16, 16, 16), Block.box(4, 4, 4, 12, 10, 12), Block.box(6, 0, 6, 10, 4, 10));
    public static final VoxelShape ROD = Block.box(6, 0, 6, 10, 16, 10);
    public static final VoxelShape STAND = Block.box(2, 0, 2, 14, 14, 14);
    private static final VoxelShape ANVIL_BASE = Block.box(2, 0, 2, 14, 4, 14);
    public static final VoxelShape ANVIL_X = Shapes.or(ANVIL_BASE, Block.box(3, 4, 4, 13, 5, 12), Block.box(4, 5, 6, 12, 10, 10), Block.box(0, 10, 3, 16, 16, 13));
    public static final VoxelShape ANVIL_Z = Shapes.or(ANVIL_BASE, Block.box(4, 4, 3, 12, 5, 13), Block.box(6, 5, 4, 10, 10, 12), Block.box(3, 10, 0, 13, 16, 16));

    /** A fixed-shape piece of scenery (basin, funnel, rod, lab stand, roots). */
    public static class Shaped extends Block {
        public static final MapCodec<Shaped> CODEC = simpleCodec(p -> new Shaped(p, Shapes.block()));
        private final VoxelShape shape;

        public Shaped(Properties props, VoxelShape shape) {
            super(props);
            this.shape = shape;
        }

        @Override
        protected MapCodec<? extends Block> codec() {
            return CODEC;
        }

        @Override
        protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
            return shape;
        }
    }

    /** A horizontally facing piece of scenery: stove, furnace (full cubes) and anvil (anvil silhouette). */
    public static class Facing extends HorizontalDirectionalBlock {
        public static final MapCodec<Facing> CODEC = simpleCodec(p -> new Facing(p, false));
        private final boolean anvil;

        public Facing(Properties props, boolean anvil) {
            super(props);
            this.anvil = anvil;
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        }

        @Override
        protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
            return CODEC;
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(FACING);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext ctx) {
            Direction d = ctx.getHorizontalDirection();
            return defaultBlockState().setValue(FACING, anvil ? d.getClockWise() : d.getOpposite());
        }

        @Override
        protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
            if (!anvil) return Shapes.block();
            return s.getValue(FACING).getAxis() == Direction.Axis.X ? ANVIL_X : ANVIL_Z;
        }
    }

    /** The hydraulic press: a full block whose ram faces any of the six directions (the piston's place). */
    public static class Press extends DirectionalBlock {
        public static final MapCodec<Press> CODEC = simpleCodec(Press::new);

        public Press(Properties props) {
            super(props);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.DOWN));
        }

        @Override
        protected MapCodec<? extends DirectionalBlock> codec() {
            return CODEC;
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
            b.add(FACING);
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext ctx) {
            return defaultBlockState().setValue(FACING, ctx.getNearestLookingDirection().getOpposite());
        }
    }
}
