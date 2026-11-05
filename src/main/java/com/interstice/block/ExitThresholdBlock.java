package com.interstice.block;

import com.interstice.registry.ModWorld;
import com.interstice.world.IntersticeTeleport;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The end of the Corridor: a black, intangible surface at the back of the exit doorway in the far bulkhead.
 * Walking into it takes the player out of THE INTERSTICE (to where they came from, like {@code /interstice leave}).
 * It cannot be mined or pushed.
 */
public class ExitThresholdBlock extends Block {
    public static final MapCodec<ExitThresholdBlock> CODEC = simpleCodec(ExitThresholdBlock::new);

    public ExitThresholdBlock(BlockBehaviour.Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide || !(entity instanceof ServerPlayer p) || !ModWorld.isInterstice(level) || p.isSpectator()) return;
        if (p.isOnPortalCooldown()) return;
        p.setPortalCooldown();
        IntersticeTeleport.leave(p);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(8) == 0) level.addParticle(ParticleTypes.ASH, pos.getX() + random.nextDouble(), pos.getY() + random.nextDouble(), pos.getZ() - 0.1, 0, 0, -0.02);
    }
}
