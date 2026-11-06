package com.interstice.block;

import com.interstice.world.IntersticeTeleport;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A rift between two levels of THE INTERSTICE: intangible and invisible as a block (each level draws its own rift),
 * it takes a player who walks into it to the next level ({@link IntersticeTeleport#throughRift}). A portal cooldown
 * stops a player arriving next to a rift from bouncing straight back through.
 */
public class RiftBlock extends Block {
    public static final MapCodec<RiftBlock> CODEC = simpleCodec(RiftBlock::new);

    public RiftBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return Shapes.block();   // so the outline and entityInside cover the whole cell
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) {
        return Shapes.empty();
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (level.isClientSide || !(entity instanceof ServerPlayer p) || p.isSpectator()) return;
        if (p.isOnPortalCooldown()) return;
        p.setPortalCooldown();
        IntersticeTeleport.throughRift(p);
    }
}
