package com.interstice.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A static ward: a battery-driven emitter that hums at a pitch most of the creatures cannot stand. While it runs
 * (about three minutes, {@link #CHARGE} steps of {@link #STEP_TICKS} ticks) creatures on the ground keep out of
 * {@link #RADIUS} blocks around it ({@code AdherentGroundGoal}). The Burden does not care, the Clinger does not
 * notice, the Hollow hates it from further away. When the charge is gone it is a dead box.
 */
public class WardEmitterBlock extends Block {
    public static final MapCodec<WardEmitterBlock> CODEC = simpleCodec(WardEmitterBlock::new);
    public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, 15);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final int STEP_TICKS = 240;
    public static final double RADIUS = 7.0;
    private static final VoxelShape SHAPE = Block.box(4, 0, 4, 12, 10, 12);

    public WardEmitterBlock(BlockBehaviour.Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(CHARGE, 15).setValue(LIT, true));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(CHARGE, LIT);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if (!level.isClientSide && state.getValue(LIT) && !old.is(this)) level.scheduleTick(pos, this, STEP_TICKS);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) return;
        int c = state.getValue(CHARGE) - 1;
        if (c <= 0) {
            level.setBlock(pos, state.setValue(CHARGE, 0).setValue(LIT, false), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.BEACON_DEACTIVATE, SoundSource.BLOCKS, 0.6f, 1.6f);
            return;
        }
        level.setBlock(pos, state.setValue(CHARGE, c), Block.UPDATE_CLIENTS);
        level.scheduleTick(pos, this, STEP_TICKS);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) return;
        if (random.nextInt(3) == 0) level.addParticle(ParticleTypes.ELECTRIC_SPARK, pos.getX() + 0.5, pos.getY() + 0.7, pos.getZ() + 0.5,
                (random.nextDouble() - 0.5) * 0.1, 0.05, (random.nextDouble() - 0.5) * 0.1);
        if (random.nextInt(30) == 0) level.playLocalSound(pos, SoundEvents.BEACON_AMBIENT, SoundSource.BLOCKS, 0.5f, 1.7f, false);
    }

    public static boolean isActive(BlockState s) {
        return s.getBlock() instanceof WardEmitterBlock && s.getValue(LIT);
    }
}
