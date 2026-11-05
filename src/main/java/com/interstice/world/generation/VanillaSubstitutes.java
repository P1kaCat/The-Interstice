package com.interstice.world.generation;

import com.interstice.registry.ModBlocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * The functional vanilla blocks the generator places (doors, chests, beds, ladders, workstations...) are written as
 * the Corridor's own copies (32x32 textures, same silhouettes and properties): {@link ChunkWriter#set} passes every
 * state through {@link #swap}. Properties are copied by name where the copy has them (facing, half, hinge, open,
 * part, axis...). The generator code keeps naming the vanilla blocks, so the layout and its tests are unchanged.
 */
public final class VanillaSubstitutes {
    private static Map<Block, Block> map;

    private VanillaSubstitutes() {}

    private static Map<Block, Block> map() {
        if (map == null) {
            Map<Block, Block> m = new IdentityHashMap<>();
            m.put(Blocks.SPRUCE_DOOR, ModBlocks.WORN_DOOR);
            m.put(Blocks.DARK_OAK_DOOR, ModBlocks.ROTTEN_DOOR);
            m.put(Blocks.BIRCH_DOOR, ModBlocks.PALE_DOOR);
            m.put(Blocks.IRON_DOOR, ModBlocks.STEEL_DOOR);
            m.put(Blocks.LADDER, ModBlocks.RUSTED_LADDER);
            m.put(Blocks.STONE_BUTTON, ModBlocks.CONCRETE_BUTTON);
            m.put(Blocks.SPRUCE_PRESSURE_PLATE, ModBlocks.WORN_PRESSURE_PLATE);
            m.put(Blocks.CHEST, ModBlocks.SUPPLY_LOCKER);
            m.put(Blocks.BARREL, ModBlocks.SUPPLY_DRUM);
            m.put(Blocks.WHITE_BED, ModBlocks.HOSPITAL_BED);
            m.put(Blocks.GRAY_BED, ModBlocks.WORN_BED);
            m.put(Blocks.LIGHT_GRAY_BED, ModBlocks.WORN_BED);
            m.put(Blocks.BROWN_BED, ModBlocks.WORN_BED);
            m.put(Blocks.BOOKSHELF, ModBlocks.ROTTEN_BOOKSHELF);
            m.put(Blocks.CAULDRON, ModBlocks.RUSTED_BASIN);
            m.put(Blocks.SMOKER, ModBlocks.RUSTED_STOVE);
            m.put(Blocks.BLAST_FURNACE, ModBlocks.INDUSTRIAL_FURNACE);
            m.put(Blocks.BREWING_STAND, ModBlocks.CHEM_STAND);
            m.put(Blocks.ANVIL, ModBlocks.SCRAP_ANVIL);
            m.put(Blocks.PISTON, ModBlocks.HYDRAULIC_PRESS);
            m.put(Blocks.HOPPER, ModBlocks.RUSTED_FUNNEL);
            m.put(Blocks.LIGHTNING_ROD, ModBlocks.ANTENNA_ROD);
            m.put(Blocks.CHAIN, ModBlocks.RUSTED_CHAIN);
            m.put(Blocks.COBWEB, ModBlocks.DUST_WEB);
            m.put(Blocks.HANGING_ROOTS, ModBlocks.DEAD_ROOTS);
            m.put(Blocks.SCULK_CATALYST, ModBlocks.FLESH_NODE);
            m.put(Blocks.BEDROCK, ModBlocks.ABYSSAL_ROCK);
            map = m;
        }
        return map;
    }

    public static BlockState swap(BlockState s) {
        Block to = map().get(s.getBlock());
        if (to == null) return s;
        BlockState out = to.defaultBlockState();
        for (Property<?> p : s.getProperties()) out = copy(out, to.getStateDefinition().getProperty(p.getName()), s.getValue(p));
        return out;
    }

    /** The block entity id to write for a container the generator names by its vanilla id. */
    public static String blockEntityId(String id) {
        return "minecraft:chest".equals(id) ? "minecraft:barrel" : id;   // lockers and drums are barrels underneath
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static BlockState copy(BlockState s, Property q, Comparable<?> value) {
        return q != null && q.getPossibleValues().contains(value) ? s.setValue(q, (Comparable) value) : s;
    }
}
