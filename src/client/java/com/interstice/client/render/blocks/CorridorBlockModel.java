package com.interstice.client.render.blocks;

import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.mesh.MutableQuadView;
import net.fabricmc.fabric.api.renderer.v1.mesh.QuadEmitter;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * A block model with connected textures and/or material transitions ({@link ConnectedTextures}). Connected faces
 * replace the wrapped model's quads with four quadrant quads (UV-locked, so each samples its own quarter of the
 * chosen tile); transitions add a cutout overlay per neighbouring spreader on top of the wrapped model's quads.
 */
final class CorridorBlockModel extends ForwardingBakedModel {
    private static final Direction[] DIRS = Direction.values();
    private static RenderMaterial cutout;

    private final ConnectedTextures.Connected connected;
    private final List<ConnectedTextures.Spread> spreads;
    private final Map<net.minecraft.resources.ResourceLocation, TextureAtlasSprite> sprites = new HashMap<>();

    CorridorBlockModel(BakedModel wrapped, ConnectedTextures.Connected connected, List<ConnectedTextures.Spread> spreads) {
        this.wrapped = wrapped;
        this.connected = connected;
        this.spreads = spreads;
    }

    @Override
    public boolean isVanillaAdapter() {
        return false;
    }

    private TextureAtlasSprite sprite(net.minecraft.resources.ResourceLocation id) {
        return sprites.computeIfAbsent(id, k -> {
            TextureAtlasSprite sp = Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS).getSprite(k);
            if (sp.contents().name().equals(net.minecraft.client.renderer.texture.MissingTextureAtlasSprite.getLocation()))
                com.interstice.Interstice.LOG.warn("Interstice: connected/transition texture {} is missing from the block atlas", k);
            return sp;
        });
    }

    private static RenderMaterial cutout() {
        if (cutout == null) cutout = RendererAccess.INSTANCE.getRenderer().materialFinder().blendMode(BlendMode.CUTOUT_MIPPED).find();
        return cutout;
    }

    /** The world direction of a face's "right" and "up", as {@link QuadEmitter#square} lays the face out. */
    private static Direction right(Direction f) {
        return switch (f) {
            case UP, DOWN, SOUTH -> Direction.EAST;
            case NORTH -> Direction.WEST;
            case WEST -> Direction.SOUTH;
            case EAST -> Direction.NORTH;
        };
    }

    private static Direction up(Direction f) {
        return switch (f) {
            case UP -> Direction.NORTH;
            case DOWN -> Direction.SOUTH;
            default -> Direction.UP;
        };
    }

    @Override
    public void emitBlockQuads(BlockAndTintGetter view, BlockState state, BlockPos pos, Supplier<RandomSource> random, RenderContext ctx) {
        if (connected == null) super.emitBlockQuads(view, state, pos, random, ctx);
        else emitConnected(view, state, pos, ctx);
        if (!spreads.isEmpty()) emitTransitions(view, state, pos, ctx);
    }

    private static boolean joins(BlockAndTintGetter view, Block block, BlockPos p, Direction face) {
        return view.getBlockState(p).is(block) && !view.getBlockState(p.relative(face)).is(block);
    }

    private void emitConnected(BlockAndTintGetter view, BlockState state, BlockPos pos, RenderContext ctx) {
        String[] vs = connected.variants();
        String variant = vs[vs.length == 1 ? 0 : (int) Math.floorMod(Mth.getSeed(pos) >> 7, (long) vs.length)];
        Block block = state.getBlock();
        QuadEmitter e = ctx.getEmitter();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (Direction f : DIRS) {
            if (connected.sidesOnly() && f.getAxis() == Direction.Axis.Y) {
                // the block's own top or bottom, as a plain face
                var quads = wrapped.getQuads(state, f, net.minecraft.util.RandomSource.create(42));
                if (quads.isEmpty()) continue;
                e.square(f, 0, 0, 1, 1, 0);
                e.spriteBake(quads.get(0).getSprite(), MutableQuadView.BAKE_LOCK_UV);
                e.color(-1, -1, -1, -1);
                e.emit();
                continue;
            }
            Direction r = right(f), u = up(f);
            for (int qx = -1; qx <= 1; qx += 2) {
                Direction hx = qx < 0 ? r.getOpposite() : r;
                boolean h = joins(view, block, m.setWithOffset(pos, hx), f);
                for (int qy = -1; qy <= 1; qy += 2) {
                    Direction vy = qy < 0 ? u.getOpposite() : u;
                    boolean v = joins(view, block, m.setWithOffset(pos, vy), f);
                    int tile;
                    if (!h && !v) tile = 0;
                    else if (h && !v) tile = 2;
                    else if (!h) tile = 3;
                    else tile = joins(view, block, m.setWithOffset(pos, hx).move(vy), f) ? 1 : 4;
                    float left = qx < 0 ? 0 : 0.5f, bottom = qy < 0 ? 0 : 0.5f;
                    e.square(f, left, bottom, left + 0.5f, bottom + 0.5f, 0);
                    e.spriteBake(sprite(ConnectedTextures.tile(variant, tile)), MutableQuadView.BAKE_LOCK_UV);
                    e.color(-1, -1, -1, -1);
                    e.emit();
                }
            }
        }
    }

    private void emitTransitions(BlockAndTintGetter view, BlockState state, BlockPos pos, RenderContext ctx) {
        QuadEmitter e = ctx.getEmitter();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int variant = (int) (Mth.getSeed(pos) >>> 11 & 1);
        for (Direction f : DIRS) {
            if (!Block.shouldRenderFace(state, view, pos, f, pos.relative(f))) continue;
            Direction r = right(f), u = up(f);
            for (int k = 0; k < 4; k++) {
                Direction d = switch (k) { case 0 -> r.getOpposite(); case 1 -> r; case 2 -> u; default -> u.getOpposite(); };
                char side = "lrtb".charAt(k);
                BlockState n = view.getBlockState(m.setWithOffset(pos, d));
                ConnectedTextures.Spread s = ConnectedTextures.SPREADERS.get(n.getBlock());
                if (s == null || !spreads.contains(s)) continue;
                // the spreader's own face in this plane must be open, or there is no surface to spill across
                BlockState front = view.getBlockState(m.move(f));
                if (front.isSolidRender(view, m)) continue;
                e.square(f, 0, 0, 1, 1, 0);
                e.spriteBake(sprite(ConnectedTextures.overlay(s.overlay(), side, variant)), MutableQuadView.BAKE_LOCK_UV);
                e.color(-1, -1, -1, -1);
                e.material(cutout());
                e.emit();
            }
        }
    }
}
