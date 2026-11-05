package com.interstice.client.render.blocks;

import com.interstice.Interstice;
import com.interstice.registry.ModBlocks;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Connected textures and material transitions, the mod's own (no Continuity needed), 2026-11-05.
 *
 * <ul>
 *   <li><b>Connected</b>: same blocks side by side merge their frames into one larger panel (glass, rusted and
 *       oxidized plates, shipping containers, corrugated sheet, hazard plating). Each face is drawn as four
 *       quadrants, each taking one of five tiles (isolated, centre, horizontal, vertical, inner corner) built from two
 *       textures: the framed one and a frameless one (PixelLab inpainting, {@code tools/assets/make_connected.py}).</li>
 *   <li><b>Transitions</b>: flesh growth, grime soil and rubble spill a ragged edge onto the neighbouring floor or
 *       wall faces in the same plane (an overlay drawn in the cutout layer over the neighbour's own texture).</li>
 * </ul>
 * The baked models of the listed blocks are wrapped after baking ({@link CorridorBlockModel}); item models are left
 * alone.
 */
public final class ConnectedTextures {
    /** A connected-texture block: its tile sets, one per texture variant (chosen per position). */
    public record Connected(String[] variants, boolean sidesOnly) {}

    /** A material that spills onto its neighbours, with the name of its overlay set. */
    public record Spread(Block spreader, String overlay) {}

    static final Map<ResourceLocation, Connected> CONNECTED = new HashMap<>();
    static final Map<ResourceLocation, List<Spread>> RECEIVERS = new HashMap<>();
    static final Map<Block, Spread> SPREADERS = new IdentityHashMap<>();

    private ConnectedTextures() {}

    private static void connected(Block b, String... variants) {
        CONNECTED.put(BuiltInRegistries.BLOCK.getKey(b), new Connected(variants, false));
    }

    /** Connected on its four sides only; the top and bottom keep the block's own texture (bookshelves). */
    private static void connectedSides(Block b, String... variants) {
        CONNECTED.put(BuiltInRegistries.BLOCK.getKey(b), new Connected(variants, true));
    }

    private static void spread(Block spreader, String overlay, Block... receivers) {
        Spread s = new Spread(spreader, overlay);
        SPREADERS.put(spreader, s);
        for (Block r : receivers) RECEIVERS.computeIfAbsent(BuiltInRegistries.BLOCK.getKey(r), k -> new ArrayList<>()).add(s);
    }

    public static void init() {
        connected(ModBlocks.GRIMY_GLASS, "grimy_glass");
        connected(ModBlocks.RUSTED_PLATE, "rusted_plate", "rusted_plate_1", "rusted_plate_2", "rusted_plate_3");
        connected(ModBlocks.OXIDIZED_PLATE, "oxidized_plate");
        connected(ModBlocks.CONTAINER_RED, "container_red");
        connected(ModBlocks.CONTAINER_TEAL, "container_teal");
        connected(ModBlocks.CORRUGATED_METAL, "corrugated_metal");
        connected(ModBlocks.HAZARD_STRIPE, "hazard_stripe");
        connectedSides(ModBlocks.ROTTEN_BOOKSHELF, "rotten_bookshelf");

        spread(ModBlocks.FLESH_GROWTH, "flesh",
                ModBlocks.WEATHERED_CONCRETE, ModBlocks.CRACKED_CONCRETE, ModBlocks.STAINED_CONCRETE, ModBlocks.CONCRETE_PANEL,
                ModBlocks.CONCRETE_FLOOR, ModBlocks.CLINIC_TILE, ModBlocks.STAINED_TILE, ModBlocks.CLINIC_FLOOR, ModBlocks.PLASTER_WALL,
                ModBlocks.PLASTER_CRACKED, ModBlocks.GRIMY_BRICK, ModBlocks.DARK_METAL, ModBlocks.RUSTED_PLATE, ModBlocks.SIDEWALK,
                ModBlocks.ASPHALT, ModBlocks.FLOORBOARDS);
        spread(ModBlocks.GRIME_SOIL, "soil",
                ModBlocks.SIDEWALK, ModBlocks.ASPHALT, ModBlocks.ASPHALT_LINE, ModBlocks.CONCRETE_FLOOR, ModBlocks.CRACKED_CONCRETE,
                ModBlocks.WEATHERED_CONCRETE, ModBlocks.FLOORBOARDS);
        // the sound plaster creeps over the edges of a cracked patch, so the patch reads as a ragged hole, not a square
        spread(ModBlocks.PLASTER_WALL, "plaster", ModBlocks.PLASTER_CRACKED);
        spread(ModBlocks.CONCRETE_RUBBLE, "rubble",
                ModBlocks.CONCRETE_FLOOR, ModBlocks.SIDEWALK, ModBlocks.ASPHALT, ModBlocks.ASPHALT_LINE, ModBlocks.FLOORBOARDS,
                ModBlocks.CLINIC_FLOOR, ModBlocks.TAR_ROOF, ModBlocks.WEATHERED_CONCRETE, ModBlocks.CRACKED_CONCRETE);

        ModelLoadingPlugin.register(ctx -> ctx.modifyModelAfterBake().register((model, c) -> {
            var top = c.topLevelId();
            if (model == null || top == null || "inventory".equals(top.variant())) return model;
            Connected con = CONNECTED.get(top.id());
            List<Spread> spreads = RECEIVERS.get(top.id());
            if (con == null && spreads == null) return model;
            return new CorridorBlockModel(model, con, spreads == null ? List.of() : spreads);
        }));
    }

    static ResourceLocation tile(String variant, int tile) {
        return Interstice.id("block/ctm/" + variant + "_" + tile);
    }

    static ResourceLocation overlay(String set, char side, int variant) {
        return Interstice.id("block/overlay/" + set + "_" + side + variant);
    }
}
