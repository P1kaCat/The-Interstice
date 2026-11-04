package com.interstice.registry;

import com.interstice.Interstice;
import com.interstice.block.FloodLampBlock;
import com.interstice.block.LampBlock;
import com.interstice.block.LampKind;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public final class ModBlocks {
    public static final List<Block> ALL = new ArrayList<>();

    // Structural materials are deliberately very hard: the walls are not meant to be tunnelled through.
    private static BlockBehaviour.Properties concrete() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(22f, 1200f).sound(SoundType.STONE).requiresCorrectToolForDrops();
    }

    private static BlockBehaviour.Properties metal() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(25f, 1200f).sound(SoundType.METAL).requiresCorrectToolForDrops();
    }

    public static final Block WEATHERED_CONCRETE = register("weathered_concrete", Block::new, concrete());
    public static final Block CONCRETE_PANEL = register("concrete_panel", Block::new, concrete());
    public static final Block STAINED_CONCRETE = register("stained_concrete", Block::new, concrete());
    public static final Block CRACKED_CONCRETE = register("cracked_concrete", Block::new, concrete());
    public static final Block CONCRETE_FLOOR = register("concrete_floor", Block::new, concrete());
    public static final Block CONCRETE_RUBBLE = register("concrete_rubble", Block::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(3f, 30f).sound(SoundType.GRAVEL));
    public static final Block RUSTED_PLATE = register("rusted_plate", Block::new, metal());
    public static final Block DARK_METAL = register("dark_metal", Block::new, metal());
    public static final Block METAL_GRATE = register("metal_grate", Block::new,
            metal().noOcclusion().isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false).isRedstoneConductor((s, l, p) -> false));
    public static final Block RUSTED_PIPE = register("rusted_pipe", RotatedPillarBlock::new, metal());

    public static final Block INDUSTRIAL_LAMP = register("industrial_lamp", p -> new LampBlock(LampKind.INDUSTRIAL, p),
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4f).sound(SoundType.LANTERN).lightLevel(s -> 12).noOcclusion());
    public static final Block SODIUM_LAMP = register("sodium_lamp", p -> new LampBlock(LampKind.SODIUM, p),
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(4f).sound(SoundType.LANTERN).lightLevel(s -> 13).noOcclusion());
    public static final Block EMERGENCY_LAMP = register("emergency_lamp", p -> new LampBlock(LampKind.EMERGENCY, p),
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(4f).sound(SoundType.LANTERN).lightLevel(s -> 5).noOcclusion());
    public static final Block FLOOD_LAMP = register("flood_lamp", FloodLampBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6f).sound(SoundType.METAL).lightLevel(s -> 15));

    private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties props) {
        Block block = Registry.register(BuiltInRegistries.BLOCK, Interstice.id(name), factory.apply(props));
        Registry.register(BuiltInRegistries.ITEM, Interstice.id(name), new BlockItem(block, new Item.Properties()));
        ALL.add(block);
        return block;
    }

    public static void init() {}

    private ModBlocks() {}
}
