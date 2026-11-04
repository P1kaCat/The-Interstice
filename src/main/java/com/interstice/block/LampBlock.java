package com.interstice.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Industrial and emergency fixtures. The vanilla light level keeps the world
 * readable without Veil; the client lamp manager attaches a Veil dynamic light
 * to each fixture near the camera (see client.render.veil.LampLightManager).
 */
public class LampBlock extends Block {
    public static final MapCodec<LampBlock> CODEC = simpleCodec(p -> new LampBlock(LampKind.INDUSTRIAL, p));
    private static final VoxelShape SHAPE = Block.box(4, 4, 4, 12, 12, 12);

    private final LampKind kind;

    public LampBlock(LampKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public LampKind kind() {
        return kind;
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // animateTick only runs on the client: lets the light manager discover
        // fixtures that were placed after their chunk was scanned.
        LampHooks.notifySeen(level, pos, kind);
    }
}
