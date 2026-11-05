package com.interstice.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Corridor's beds (the hospital bed, the worn bed): two-block scenery with the vanilla bed's properties
 * ({@code facing} points from the foot to the head, {@code part}) so the generator places them like beds. Nobody
 * sleeps in THE INTERSTICE: they have no block entity and no use action. Breaking one half removes the other.
 */
public class CotBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<CotBlock> CODEC = simpleCodec(CotBlock::new);
    public static final EnumProperty<BedPart> PART = BlockStateProperties.BED_PART;
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 9, 16);

    public CotBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, BedPart.FOOT));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, PART);
    }

    @Override
    protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return SHAPE;
    }

    private static Direction towardsOther(BlockState s) {
        return s.getValue(PART) == BedPart.FOOT ? s.getValue(FACING) : s.getValue(FACING).getOpposite();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction d = ctx.getHorizontalDirection();
        BlockPos head = ctx.getClickedPos().relative(d);
        Level level = ctx.getLevel();
        return level.getBlockState(head).canBeReplaced(ctx) && level.getWorldBorder().isWithinBounds(head)
                ? defaultBlockState().setValue(FACING, d) : null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) {
            level.setBlock(pos.relative(state.getValue(FACING)), state.setValue(PART, BedPart.HEAD), Block.UPDATE_ALL);
            state.updateNeighbourShapes(level, pos, Block.UPDATE_ALL);
        }
    }

    @Override
    protected BlockState updateShape(BlockState s, Direction dir, BlockState other, LevelAccessor level, BlockPos pos, BlockPos otherPos) {
        if (dir == towardsOther(s)) {
            boolean paired = other.is(this) && other.getValue(PART) != s.getValue(PART) && other.getValue(FACING) == s.getValue(FACING);
            return paired ? s : Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(s, dir, other, level, pos, otherPos);
    }
}
