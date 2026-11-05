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
    /** The searchlights fixed high on the walls (StructurePass.highFloods), aimed across the corridor. */
    public static final Block WALL_SPOTLIGHT = register("wall_spotlight", p -> new FloodLampBlock(com.interstice.block.LampKind.SPOTLIGHT, p),
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(6f).sound(SoundType.METAL).lightLevel(s -> 10));

    // ---- Corridor building materials (one family with the concrete and the rust; replaces vanilla blocks in the regions)
    private static BlockBehaviour.Properties building() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(4f, 30f).sound(SoundType.STONE).requiresCorrectToolForDrops();
    }

    private static BlockBehaviour.Properties sheet() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5f, 30f).sound(SoundType.METAL).requiresCorrectToolForDrops();
    }

    public static final Block GRIMY_BRICK = register("grimy_brick", Block::new, building());
    public static final Block PLASTER_WALL = register("plaster_wall", Block::new, building());
    public static final Block PLASTER_CRACKED = register("plaster_cracked", Block::new, building());
    public static final Block ASPHALT = register("asphalt", com.interstice.block.MacroTileBlock::new, building());
    public static final Block ASPHALT_LINE = register("asphalt_line", com.interstice.block.MacroTileBlock::new, building());
    public static final Block SIDEWALK = register("sidewalk", com.interstice.block.MacroTileBlock::new, building());
    public static final Block FLOORBOARDS = register("floorboards", Block::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2f, 3f).sound(SoundType.WOOD));
    public static final Block TAR_ROOF = register("tar_roof", Block::new, building());
    public static final Block GRIMY_GLASS_PANE = register("grimy_glass_pane", net.minecraft.world.level.block.IronBarsBlock::new,
            BlockBehaviour.Properties.of().strength(0.3f).sound(SoundType.GLASS).noOcclusion());
    public static final Block CORRUGATED_METAL = register("corrugated_metal", Block::new, sheet());
    public static final Block STEEL_BEAM = register("steel_beam", RotatedPillarBlock::new, metal());
    public static final Block OXIDIZED_PLATE = register("oxidized_plate", Block::new, sheet());
    public static final Block HAZARD_STRIPE = register("hazard_stripe", Block::new, sheet());
    public static final Block CONTAINER_RED = register("container_red", Block::new, sheet());
    public static final Block CONTAINER_TEAL = register("container_teal", Block::new, sheet());
    public static final Block CLINIC_TILE = register("clinic_tile", Block::new, building());
    public static final Block STAINED_TILE = register("stained_tile", Block::new, building());
    public static final Block CLINIC_FLOOR = register("clinic_floor", Block::new, building());
    // ---- the Corridor's own versions of vanilla decorative blocks (stairs, slabs, walls, fences, bars, glass, logs, soil)
    public static final Block CONCRETE_STAIRS = register("concrete_stairs",
            p -> new net.minecraft.world.level.block.StairBlock(WEATHERED_CONCRETE.defaultBlockState(), p), building());
    public static final Block CONCRETE_SLAB = register("concrete_slab", net.minecraft.world.level.block.SlabBlock::new, building());
    public static final Block LAB_SLAB = register("lab_slab", net.minecraft.world.level.block.SlabBlock::new, building());
    public static final Block CONCRETE_LOW_WALL = register("concrete_low_wall", net.minecraft.world.level.block.WallBlock::new, building());
    public static final Block WORN_BENCH = register("worn_bench",
            p -> new net.minecraft.world.level.block.StairBlock(FLOORBOARDS.defaultBlockState(), p),
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2f, 3f).sound(SoundType.WOOD));
    public static final Block WORN_FENCE = register("worn_fence", net.minecraft.world.level.block.FenceBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2f, 3f).sound(SoundType.WOOD));
    public static final Block RUSTED_BARS = register("rusted_bars", net.minecraft.world.level.block.IronBarsBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5f, 6f).sound(SoundType.METAL).noOcclusion().requiresCorrectToolForDrops());
    public static final Block BLACKOUT_PANE = register("blackout_pane", net.minecraft.world.level.block.IronBarsBlock::new,
            BlockBehaviour.Properties.of().strength(0.3f).sound(SoundType.GLASS).noOcclusion());
    public static final Block GRIMY_GLASS = register("grimy_glass", net.minecraft.world.level.block.TransparentBlock::new,
            BlockBehaviour.Properties.of().strength(0.3f).sound(SoundType.GLASS).noOcclusion()
                    .isValidSpawn((s, l, p, e) -> false).isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p) -> false));
    public static final Block ROTTEN_LOG = register("rotten_log", RotatedPillarBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2f).sound(SoundType.WOOD));
    public static final Block GRIME_SOIL = register("grime_soil", Block::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).strength(0.6f).sound(SoundType.GRAVEL));

    // ---- the Corridor's own versions of the functional vanilla blocks of its buildings (2026-11-05, 32x32 PixelLab
    // textures). The generator still writes the vanilla blocks; ChunkWriter swaps them (world.generation.VanillaSubstitutes).
    private static BlockBehaviour.Properties wood() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(3f).sound(SoundType.WOOD).noOcclusion();
    }

    private static BlockBehaviour.Properties furniture() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5f, 6f).sound(SoundType.METAL).noOcclusion();
    }

    public static final Block WORN_DOOR = door("worn_door", net.minecraft.world.level.block.state.properties.BlockSetType.SPRUCE, wood());
    public static final Block ROTTEN_DOOR = door("rotten_door", net.minecraft.world.level.block.state.properties.BlockSetType.DARK_OAK, wood());
    public static final Block PALE_DOOR = door("pale_door", net.minecraft.world.level.block.state.properties.BlockSetType.BIRCH, wood());
    public static final Block STEEL_DOOR = door("steel_door", net.minecraft.world.level.block.state.properties.BlockSetType.IRON,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5f).sound(SoundType.METAL).noOcclusion().requiresCorrectToolForDrops());
    public static final Block RUSTED_LADDER = register("rusted_ladder", net.minecraft.world.level.block.LadderBlock::new,
            BlockBehaviour.Properties.of().strength(0.4f).sound(SoundType.LADDER).noOcclusion());
    public static final Block CONCRETE_BUTTON = register("concrete_button",
            p -> new net.minecraft.world.level.block.ButtonBlock(net.minecraft.world.level.block.state.properties.BlockSetType.STONE, 20, p),
            BlockBehaviour.Properties.of().noCollission().strength(0.5f));
    public static final Block WORN_PRESSURE_PLATE = register("worn_pressure_plate",
            p -> new net.minecraft.world.level.block.PressurePlateBlock(net.minecraft.world.level.block.state.properties.BlockSetType.SPRUCE, p),
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).noCollission().strength(0.5f));
    public static final Block SUPPLY_LOCKER = register("supply_locker", com.interstice.block.StorageBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(2.5f).sound(SoundType.METAL));
    public static final Block SUPPLY_DRUM = register("supply_drum", com.interstice.block.StorageBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).strength(2.5f).sound(SoundType.METAL));
    public static final Block HOSPITAL_BED = register("hospital_bed", com.interstice.block.CotBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOL).strength(0.4f).sound(SoundType.WOOL).noOcclusion());
    public static final Block WORN_BED = register("worn_bed", com.interstice.block.CotBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOL).strength(0.4f).sound(SoundType.WOOL).noOcclusion());
    public static final Block ROTTEN_BOOKSHELF = register("rotten_bookshelf", Block::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.5f).sound(SoundType.WOOD));
    public static final Block RUSTED_BASIN = register("rusted_basin", p -> new com.interstice.block.CorridorDecor.Shaped(p, com.interstice.block.CorridorDecor.BASIN), furniture());
    public static final Block RUSTED_STOVE = register("rusted_stove", p -> new com.interstice.block.CorridorDecor.Facing(p, false), furniture());
    public static final Block INDUSTRIAL_FURNACE = register("industrial_furnace", p -> new com.interstice.block.CorridorDecor.Facing(p, false),
            furniture().lightLevel(s -> 3));
    public static final Block CHEM_STAND = register("chem_stand", p -> new com.interstice.block.CorridorDecor.Shaped(p, com.interstice.block.CorridorDecor.STAND),
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(0.5f).sound(SoundType.GLASS).noOcclusion());
    public static final Block SCRAP_ANVIL = register("scrap_anvil", p -> new com.interstice.block.CorridorDecor.Facing(p, true),
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(5f, 1200f).sound(SoundType.ANVIL).noOcclusion());
    public static final Block HYDRAULIC_PRESS = register("hydraulic_press", com.interstice.block.CorridorDecor.Press::new, metal());
    public static final Block RUSTED_FUNNEL = register("rusted_funnel", p -> new com.interstice.block.CorridorDecor.Shaped(p, com.interstice.block.CorridorDecor.FUNNEL), furniture());
    public static final Block ANTENNA_ROD = register("antenna_rod", p -> new com.interstice.block.CorridorDecor.Shaped(p, com.interstice.block.CorridorDecor.ROD), furniture());
    public static final Block RUSTED_CHAIN = register("rusted_chain", net.minecraft.world.level.block.ChainBlock::new,
            BlockBehaviour.Properties.of().strength(5f, 6f).sound(SoundType.CHAIN).noOcclusion());
    public static final Block DUST_WEB = register("dust_web", net.minecraft.world.level.block.WebBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.WOOL).noCollission().strength(4f).sound(SoundType.COBWEB));
    public static final Block DEAD_ROOTS = register("dead_roots", p -> new com.interstice.block.CorridorDecor.Shaped(p, Block.box(2, 6, 2, 14, 16, 14)),
            BlockBehaviour.Properties.of().mapColor(MapColor.DIRT).noCollission().instabreak().sound(SoundType.HANGING_ROOTS));
    public static final Block FLESH_NODE = register("flesh_node", Block::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(3f).sound(SoundType.SLIME_BLOCK).lightLevel(s -> 4));
    public static final Block ABYSSAL_ROCK = register("abyssal_rock", Block::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(-1f, 3600000f).noLootTable().isValidSpawn((s, l, p, e) -> false));

    private static Block door(String name, net.minecraft.world.level.block.state.properties.BlockSetType set, BlockBehaviour.Properties props) {
        Block block = Registry.register(BuiltInRegistries.BLOCK, Interstice.id(name), new net.minecraft.world.level.block.DoorBlock(set, props));
        Registry.register(BuiltInRegistries.ITEM, Interstice.id(name), new net.minecraft.world.item.DoubleHighBlockItem(block, new Item.Properties()));
        ALL.add(block);
        return block;
    }

    /** The Ward's infestation: an organic mass on walls and floors, and a thin vein carpet. */
    public static final Block FLESH_GROWTH = register("flesh_growth", Block::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(1.5f).sound(SoundType.SLIME_BLOCK));
    public static final Block GROWTH_VEINS = register("growth_veins", net.minecraft.world.level.block.CarpetBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(0.2f).sound(SoundType.SLIME_BLOCK).noOcclusion());

    /** Protective device: a running ward keeps most creatures away for about three minutes. */
    public static final Block WARD_EMITTER = register("ward_emitter", com.interstice.block.WardEmitterBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.5f).sound(SoundType.METAL).noOcclusion()
                    .lightLevel(s -> s.getValue(com.interstice.block.WardEmitterBlock.LIT) ? 6 : 0));

    /** The far end of the finite Corridor: walking into it leaves THE INTERSTICE. */
    public static final Block EXIT_THRESHOLD = register("exit_threshold", com.interstice.block.ExitThresholdBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(-1f, 3600000f).noCollission().noLootTable().lightLevel(s -> 3));

    private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties props) {
        Block block = Registry.register(BuiltInRegistries.BLOCK, Interstice.id(name), factory.apply(props));
        Registry.register(BuiltInRegistries.ITEM, Interstice.id(name), new BlockItem(block, new Item.Properties()));
        ALL.add(block);
        return block;
    }

    public static void init() {}

    private ModBlocks() {}
}
