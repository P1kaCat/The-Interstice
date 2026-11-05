package com.interstice.client.render.veil;

import com.interstice.Interstice;
import com.interstice.block.FloodLampBlock;
import com.interstice.block.LampBlock;
import com.interstice.block.LampKind;
import com.interstice.client.ClientState;
import com.interstice.client.config.ClientConfig;
import com.interstice.client.config.GraphicsPreset;
import com.interstice.util.Hash;
import com.interstice.world.IntersticeLayout;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.light.data.LightData;
import foundry.veil.api.client.render.light.data.PointLightData;
import foundry.veil.api.client.render.light.data.SpotLightData;
import foundry.veil.api.client.render.light.renderer.LightRenderHandle;
import foundry.veil.api.client.render.light.renderer.LightRenderer;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Attaches Veil dynamic lights to light fixtures.
 * <p>
 * Discovery: each loaded chunk is scanned once (only sections whose palette
 * may contain a fixture), plus {@code animateTick} notifications for fixtures
 * placed later. Selection: every frame the nearest fixtures within the preset's
 * range get a Veil light, up to the preset's light budget; lights beyond the
 * budget are released. Behaviour: deterministic per-fixture malfunctions
 * (flicker, dead lamps), event-driven failures during quakes, red emergency
 * lighting during the Stampede. (The invisible distant "beacons", point lights of 85 blocks with strong in-scattering,
 * were removed on 2026-11-05: they lit the chains evenly from end to end and greyed the air; the wall searchlights
 * now light the heights from a fixture one can see.)
 */
public final class LampLightManager {
    private static final class Lamp {
        final long pos;
        final LampKind kind;
        final Direction facing;
        final long hash;
        /** A long-throw flood lamp high on a wall, aimed across the corridor at the chains and the far wall. */
        final boolean high;
        LightRenderHandle<? extends LightData> handle;
        double dist2;
        /** 0..1: a light just given a Veil handle fades in instead of popping on. */
        float fade;

        Lamp(long pos, LampKind kind, Direction facing) {
            this.pos = pos;
            this.kind = kind;
            this.facing = facing;
            this.hash = Hash.mix(pos * 31 + kind.ordinal());
            this.high = kind == LampKind.SPOTLIGHT;
        }
    }

    private static final Long2ObjectOpenHashMap<Lamp> lamps = new Long2ObjectOpenHashMap<>();
    private static final Long2ObjectOpenHashMap<LongArrayList> byChunk = new Long2ObjectOpenHashMap<>();
    private static final List<Lamp> active = new ArrayList<>();
    private static final List<Lamp> candidates = new ArrayList<>();
    private static boolean veilOk = true;

    public static int activeLights, knownFixtures;
    /** The spot beams lit this frame (flood lamps), for the dust drifting through them ({@code BeamDust}). */
    public static final List<com.interstice.client.effects.BeamDust.Beam> spotBeams = new ArrayList<>();
    public static double nearestFixture = Double.MAX_VALUE;

    private LampLightManager() {}

    // ------------------------------------------------------------- discovery

    private static boolean isFixture(BlockState s) {
        Block b = s.getBlock();
        return b instanceof LampBlock || b instanceof FloodLampBlock;
    }

    public static void onChunkLoad(Level level, LevelChunk chunk) {
        if (!com.interstice.registry.ModWorld.isInterstice(level)) return;
        long ck = chunk.getPos().toLong();
        LongArrayList list = new LongArrayList();
        LevelChunkSection[] sections = chunk.getSections();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int si = 0; si < sections.length; si++) {
            LevelChunkSection sec = sections[si];
            if (sec == null || sec.hasOnlyAir() || !sec.maybeHas(LampLightManager::isFixture)) continue;
            int baseY = chunk.getSectionYFromSectionIndex(si) << 4;
            for (int y = 0; y < 16; y++)
                for (int z = 0; z < 16; z++)
                    for (int x = 0; x < 16; x++) {
                        BlockState s = sec.getBlockState(x, y, z);
                        if (!isFixture(s)) continue;
                        p.set(chunk.getPos().getMinBlockX() + x, baseY + y, chunk.getPos().getMinBlockZ() + z);
                        add(p.asLong(), s);
                        list.add(p.asLong());
                    }
        }
        if (!list.isEmpty()) byChunk.put(ck, list);
    }

    public static void onChunkUnload(LevelChunk chunk) {
        LongArrayList list = byChunk.remove(chunk.getPos().toLong());
        if (list == null) return;
        for (long pos : list) {
            Lamp l = lamps.remove(pos);
            if (l != null) release(l);
        }
    }

    public static void onLampSeen(Level level, BlockPos pos, LampKind kind) {
        long key = pos.asLong();
        if (!lamps.containsKey(key)) {
            add(key, level.getBlockState(pos));
            byChunk.computeIfAbsent(net.minecraft.world.level.ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4), k -> new LongArrayList()).add(key);
        }
    }

    private static void add(long pos, BlockState s) {
        LampKind kind;
        Direction facing = Direction.DOWN;
        if (s.getBlock() instanceof LampBlock lb) kind = lb.kind();
        else if (s.getBlock() instanceof FloodLampBlock fb) {
            kind = fb.kind();
            facing = s.getValue(DirectionalBlock.FACING);
        } else return;
        lamps.put(pos, new Lamp(pos, kind, facing));
    }

    public static void clear() {
        for (Lamp l : lamps.values()) release(l);
        lamps.clear();
        byChunk.clear();
        active.clear();
    }

    // ------------------------------------------------------------- per frame

    public static void update(Level level, Vec3 cam) {
        knownFixtures = lamps.size();
        ClientConfig cfg = ClientConfig.INSTANCE;
        if (!veilOk || !ClientState.inInterstice || !cfg.dynamicLighting) {
            for (Lamp l : active) release(l);
            active.clear();
            activeLights = 0;
            return;
        }
        LightRenderer renderer;
        try {
            renderer = VeilRenderSystem.renderer().getLightRenderer();
        } catch (Throwable t) {
            veilOk = false;
            Interstice.LOG.warn("Veil light renderer unavailable; dynamic lights disabled", t);
            return;
        }
        GraphicsPreset preset = cfg.preset;
        double range2 = (double) preset.lightRange * preset.lightRange;

        // candidate selection: nearest N within range
        candidates.clear();
        nearestFixture = Double.MAX_VALUE;
        for (Long2ObjectMap.Entry<Lamp> e : lamps.long2ObjectEntrySet()) {
            Lamp l = e.getValue();
            long p = l.pos;
            double dx = BlockPos.getX(p) + 0.5 - cam.x, dy = BlockPos.getY(p) + 0.5 - cam.y, dz = BlockPos.getZ(p) + 0.5 - cam.z;
            l.dist2 = dx * dx + dy * dy + dz * dz;
            if (l.dist2 < nearestFixture) nearestFixture = l.dist2;
            double r = l.high ? range2 * 5.0 : l.kind == LampKind.FLOOD ? range2 * 2.2 : range2;
            if (l.dist2 < r && !isDead(l)) candidates.add(l);
        }
        nearestFixture = Math.sqrt(nearestFixture);
        // the high floods light what is far away (the chains, the far wall): they rank as if 2.5x closer
        // lamps already lit rank 30% closer, so at the edge of the budget the same lamps stay lit instead of two
        // lamps trading places every frame as the camera moves (that switching read as flicker)
        candidates.sort((a, b) -> Double.compare(rank(a), rank(b)));
        int budget = preset.lightBudget;
        for (int i = budget; i < candidates.size(); i++) release(candidates.get(i));
        if (candidates.size() > budget) candidates.subList(budget, candidates.size()).clear();
        for (Lamp l : active) if (!candidates.contains(l)) release(l);
        active.clear();

        double t = ClientState.time;
        spotBeams.clear();
        for (Lamp l : candidates) {
            if (l.handle == null || !l.handle.isValid()) {
                l.handle = create(renderer, l, preset);
                l.fade = 0f;
            }
            if (l.handle == null) continue;
            l.fade = Math.min(1f, l.fade + 0.06f);
            float b = brightness(l, t, cfg) * l.fade * l.fade;
            // fade out towards the edge of the light range instead of switching off (no popping on the walls)
            double rr = l.high ? range2 * 5.0 : l.kind == LampKind.FLOOD ? range2 * 2.2 : range2;
            float edge = (float) Math.sqrt(l.dist2 / rr);
            b *= 1f - Mth.clamp((edge - 0.65f) / 0.35f, 0f, 1f);
            LightData data = l.handle.getLightData();
            data.setBrightness(b);
            l.handle.markDirty();
            active.add(l);
            if ((l.kind == LampKind.FLOOD || l.high) && b > 0.05f) {
                Vector3f d = beamDir(l);
                spotBeams.add(new com.interstice.client.effects.BeamDust.Beam(
                        BlockPos.getX(l.pos) + 0.5 + d.x * 0.6, BlockPos.getY(l.pos) + 0.5 + d.y * 0.6, BlockPos.getZ(l.pos) + 0.5 + d.z * 0.6,
                        d.x, d.y, d.z, (float) Math.toRadians(l.high ? 10 : 32), l.high ? 110f : l.kind.radius * 0.8f,
                        Math.min(1f, b / l.kind.brightness), l.kind.r, l.kind.g, l.kind.b, false));
            }
        }
        activeLights = active.size();
    }

    private static LightRenderHandle<? extends LightData> create(LightRenderer renderer, Lamp l, GraphicsPreset preset) {
        double x = BlockPos.getX(l.pos) + 0.5, y = BlockPos.getY(l.pos) + 0.5, z = BlockPos.getZ(l.pos) + 0.5;
        try {
            if (l.kind == LampKind.FLOOD || l.high) {
                SpotLightData s = new SpotLightData();
                Vector3f dir = beamDir(l);
                s.getPositionMutable().set(x + dir.x * 0.6, y + dir.y * 0.6, z + dir.z * 0.6);
                // Veil takes a point to the light's space as R (p - pos) and the beam runs along +Z there: R maps the
                // facing onto +Z (the old rotationTo(-Z -> facing) pointed the uplights at the floor)
                s.getOrientationMutable().set(new Quaternionf().rotationTo(dir.x, dir.y, dir.z, 0, 0, 1));
                // Veil's spot shader: the cone's edge is setSize (radians), setAngle the soft edge inside it (the old size
                // 1.0 gave the searchlights a 57-degree cone that flooded the plazas)
                s.setSize((float) Math.toRadians(l.high ? 10 : 32));
                s.setAngle((float) Math.toRadians(l.high ? 4 : 14));
                s.setDistance(l.kind.radius);
                s.setColor(l.kind.r, l.kind.g, l.kind.b);
                s.setBrightness(l.kind.brightness);
                s.setInscatteringStrength(preset.inscattering && ClientConfig.INSTANCE.volumetricLight ? (l.high ? 0.4f : 0.28f) : 0f);   // the searchlights' beam just shows in the air
                return renderer.addLight(s);
            }
            PointLightData p = new PointLightData();
            p.setPosition(x, y - 0.2, z);
            p.setRadius(l.kind.radius);
            p.setColor(l.kind.r, l.kind.g, l.kind.b);
            p.setBrightness(l.kind.brightness);
            p.setInscatteringStrength(preset.inscattering && ClientConfig.INSTANCE.volumetricLight ? (l.kind == LampKind.EMERGENCY ? 0.35f : 0.2f) : 0f);
            return renderer.addLight(p);
        } catch (Throwable t) {
            veilOk = false;
            Interstice.LOG.warn("Failed to create Veil light; dynamic lights disabled", t);
            return null;
        }
    }

    /** 8% of fixtures are simply dead. */
    private static boolean isDead(Lamp l) {
        return Hash.unit(l.hash, 1) < 0.08 && l.kind != LampKind.EMERGENCY && l.kind != LampKind.SPOTLIGHT;
    }

    private static float brightness(Lamp l, double t, ClientConfig cfg) {
        float base = l.kind.brightness;
        float emer = ClientState.emergency;
        float fail = ClientState.lightFailure;
        boolean calm = cfg.reduceFlashing;
        if (l.kind == LampKind.EMERGENCY) {
            float pulse = calm ? 0.85f : 0.55f + 0.45f * (float) Math.max(0, Math.sin(t * 0.33 + Hash.unit(l.hash, 2) * 6.28));
            return base * (0.18f + 2.4f * emer * pulse);
        }
        float b = base;
        // the big beams never flicker (one of them lights a whole stretch of the corridor)
        boolean big = l.kind == LampKind.FLOOD || l.kind == LampKind.SPOTLIGHT;
        // malfunctioning fixtures
        if (!big && Hash.unit(l.hash, 3) < 0.15) b *= flicker(l.hash, t, calm);
        // event-driven failure: each lamp has its own threshold, so failure spreads unevenly
        if (fail > 0 && Hash.unit(l.hash, 4) < fail) {
            b *= calm || big ? 0.4f : flicker(l.hash ^ 0x5bd1e995L, t * 2.5, false) * 0.6f;
        }
        // during the Stampede the normal lights dim and the emergency circuit takes over
        b *= 1f - emer * 0.55f;
        return b;
    }

    /**
     * A failing tube: a slow unsteady glow with now and then a short dip. {@code t} is in game ticks (it was used as
     * seconds: 11.3 rad per tick made a 36 Hz strobe with hard cuts, unbearable once the beams lit the whole corridor).
     */
    private static float flicker(long h, double t, boolean calm) {
        double s = t / 20.0;
        double n = Math.sin(s * (0.9 + Hash.unit(h, 5) * 2.1)) + Math.sin(s * 3.7 + Hash.unit(h, 6) * 9) * 0.6 + Math.sin(s * 7.3 + Hash.unit(h, 7) * 5) * 0.25;
        if (calm) return 0.8f + (float) n * 0.06f;
        // above 1.25 (a few percent of the time) the tube dips, smoothly, to a third
        float dip = (float) Mth.clamp((n - 1.25) / 0.3, 0.0, 1.0);
        return Mth.clamp(0.8f + (float) n * 0.12f, 0.5f, 1.05f) * (1f - 0.65f * dip);
    }

    /**
     * The beam's direction: the fixture's facing; the wall searchlights are tilted 6 degrees up, so with their
     * 10-degree cone the lower edge sinks only 4 degrees (7 blocks over 100, from 30 up): they light the heights (chains, far wall) and never the floor (aimed
     * level, an 18-degree cone reached the floor some 90 blocks away and lit a whole plaza evenly).
     */
    private static Vector3f beamDir(Lamp l) {
        Vector3f d = l.facing.step();
        if (l.high) d.add(0, 0.105f, 0).normalize();   // 6 degrees up
        return d;
    }

    private static double rank(Lamp l) {
        double d = l.high ? l.dist2 / 6.25 : l.dist2;
        return l.handle != null ? d * 0.49 : d;
    }

    private static void release(Lamp l) {
        if (l.handle != null) {
            safeFree(l.handle);
            l.handle = null;
        }
    }

    private static void safeFree(LightRenderHandle<?> h) {
        try {
            if (h.isValid()) h.free();
        } catch (Throwable ignored) {
        }
    }
}
