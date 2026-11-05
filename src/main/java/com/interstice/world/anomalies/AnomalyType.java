package com.interstice.world.anomalies;

import com.interstice.registry.ModBlocks;
import com.interstice.util.Hash;
import com.interstice.world.IntersticeLayout;
import com.interstice.world.generation.ChunkWriter;
import com.interstice.world.structures.StructurePass;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * Spatial anomalies: architecture that should not be there. Every anomaly is
 * fully contained inside one chunk column (local coordinates 1..14), so the
 * framework is chunk-safe by construction. {@code minLevel} is compared with
 * {@link IntersticeLayout#anomalyLevel}, which rises with distance from the
 * arrival point: the deeper the player goes, the stranger it gets.
 * <p>
 * These are deliberate visual/structural illusions, not true non-Euclidean
 * geometry.
 */
public enum AnomalyType {
    FLOATING_DEBRIS(0.0) {
        @Override
        void place(ChunkWriter w, int x, int fy, int z, long h) {
            int n = 4 + (int) (Hash.unit(h, 1) * 6);
            for (int i = 0; i < n; i++) {
                int bx = x - 5 + (int) (Hash.unit(h, i, 2) * 11);
                int bz = z - 5 + (int) (Hash.unit(h, i, 3) * 11);
                int by = fy + 4 + (int) (Hash.unit(h, i, 4) * 12);
                int s = 1 + (int) (Hash.unit(h, i, 5) * 2);
                w.fill(bx, by, bz, bx + s - 1, by + s - 1, bz + s - 1, Hash.unit(h, i, 6) < 0.3 ? ModBlocks.RUSTED_PLATE.defaultBlockState() : ModBlocks.CRACKED_CONCRETE.defaultBlockState());
            }
        }
    },
    STAIRS_TO_NOWHERE(0.05) {
        @Override
        void place(ChunkWriter w, int x, int fy, int z, long h) {
            BlockState stair = ModBlocks.CONCRETE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST);
            for (int i = 0; i < 12; i++) {
                int sx = x - 6 + i;
                for (int dz = 0; dz < 2; dz++) {
                    w.set(sx, fy + i, z + dz, stair);
                    for (int y = fy; y < fy + i; y++) w.set(sx, y, z + dz, ModBlocks.CONCRETE_PANEL.defaultBlockState());
                }
            }
        }
    },
    LONE_DOORFRAME(0.1) {
        @Override
        void place(ChunkWriter w, int x, int fy, int z, long h) {
            BlockState frame = ModBlocks.DARK_METAL.defaultBlockState();
            for (int y = fy; y < fy + 4; y++) {
                w.set(x - 1, y, z, frame);
                w.set(x + 1, y, z, frame);
            }
            w.set(x, fy + 3, z, frame);
            BlockState door = Blocks.IRON_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
            w.set(x, fy, z, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
            w.set(x, fy + 1, z, door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
            w.set(x, fy + 4, z, ModBlocks.EMERGENCY_LAMP.defaultBlockState());
        }
    },
    LAMP_GARDEN(0.2) {
        @Override
        void place(ChunkWriter w, int x, int fy, int z, long h) {
            for (int i = -1; i <= 1; i++)
                for (int j = -1; j <= 1; j++)
                    w.setIfAir(x + i * 3, fy, z + j * 3, ModBlocks.INDUSTRIAL_LAMP.defaultBlockState());
        }
    },
    HANGING_ROOM(0.35) {
        @Override
        void place(ChunkWriter w, int x, int fy, int z, long h) {
            StructurePass.placeBunker(w, x - 3, z - 3, 7, 7, fy + 20, Direction.UP, false);
            BlockState inv = ModBlocks.CONCRETE_STAIRS.defaultBlockState().setValue(StairBlock.HALF, Half.TOP).setValue(StairBlock.FACING, Direction.WEST);
            for (int i = 0; i < 4; i++) w.set(x - 2 + i, fy + 18 - i, z, inv);
        }
    },
    DUPLICATES(0.45) {
        @Override
        void place(ChunkWriter w, int x, int fy, int z, long h) {
            for (int i = -2; i <= 2; i++) {
                int bz = z + i * 3;
                w.fill(x - 1, fy, bz, x, fy + 1, bz + 1, ModBlocks.CONCRETE_PANEL.defaultBlockState());
                w.set(x, fy + 2, bz, ModBlocks.EMERGENCY_LAMP.defaultBlockState());
            }
        }
    },
    WELL(0.55) {
        @Override
        void place(ChunkWriter w, int x, int fy, int z, long h) {
            for (int i = -1; i <= 1; i++)
                for (int j = -1; j <= 1; j++)
                    for (int y = IntersticeLayout.PIT_BOTTOM + 1; y < fy; y++)
                        w.set(x + i, y, z + j, y <= IntersticeLayout.PIT_BOTTOM + 4 ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState());
            w.set(x, IntersticeLayout.PIT_BOTTOM, z, ModBlocks.FLOOD_LAMP.defaultBlockState());
        }
    },
    INVERTED_STAIRS(0.6) {
        @Override
        void place(ChunkWriter w, int x, int fy, int z, long h) {
            BlockState inv = ModBlocks.CONCRETE_STAIRS.defaultBlockState().setValue(StairBlock.HALF, Half.TOP).setValue(StairBlock.FACING, Direction.SOUTH);
            for (int i = 0; i < 10; i++) w.set(x, fy + 24 - i, z - 5 + i, inv);
            w.set(x, fy + 25, z - 5, ModBlocks.INDUSTRIAL_LAMP.defaultBlockState());
        }
    };

    public final double minLevel;

    AnomalyType(double minLevel) {
        this.minLevel = minLevel;
    }

    abstract void place(ChunkWriter w, int x, int fy, int z, long hash);
}
