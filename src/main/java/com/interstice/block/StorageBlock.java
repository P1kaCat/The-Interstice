package com.interstice.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.BarrelBlock;

/**
 * The Corridor's containers (the supply locker in place of the chest, the supply drum in place of the barrel).
 * They are barrels underneath: 27 slots, an open state, the barrel's block entity and menu, loot tables as before.
 * The barrel block entity type accepts them through {@code BlockEntityTypeMixin}.
 */
public class StorageBlock extends BarrelBlock {
    public static final MapCodec<StorageBlock> CODEC = simpleCodec(StorageBlock::new);

    public StorageBlock(Properties props) {
        super(props);
    }

    @Override
    public MapCodec<BarrelBlock> codec() {
        @SuppressWarnings("unchecked") MapCodec<BarrelBlock> c = (MapCodec<BarrelBlock>) (MapCodec<?>) CODEC;
        return c;
    }
}
