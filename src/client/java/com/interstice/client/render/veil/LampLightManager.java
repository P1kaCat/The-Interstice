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
 * lighting during the Stampede, and a handful of very distant high "beacons"
 * with strong in-scattering that reveal silhouettes through the fog.
 */
public final class LampLightManager {
    private static final class Lamp {
        final long pos;
        final LampKind kind;
        final Direction facing;
        final long hash;
        LightRenderHandle<? extends LightData> handle;
        double dist2;

        Lamp(long pos, LampKind kind, Direction facing) {
            this.pos = pos;
            this.kind = kind;
            this.facing = facing;
            this.hash = Hash.mix(pos * 31 + kind.ordinal());
        }
    }

    private static final Long2ObjectOpenHashMap<Lamp> lamps = new Long2ObjectOpenHashMap<>();
    private static final Long2ObjectOpenHashMap<LongArrayList> byChunk = new Long2ObjectOpenHashMap<>();
    private static final List<Lamp> active = new ArrayList<>();
    private static final List<Lamp> candidates = new ArrayList<>();
    private static final List<LightRenderHandle<PointLightData>> beacons = new ArrayList<>();
    private static boolean veilOk = true;

    public static int activeLights, knownFixtures;
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
        else if (s.getBlock() instanceof FloodLampBlock) {
            kind = LampKind.FLOOD;
            facing = s.getValue(DirectionalBlock.FACING);
        } else return;
        lamps.put(pos, new Lamp(pos, kind, facing));
    }

    public static void clear() {
        for (Lamp l : lamps.values()) release(l);
        lamps.clear();
        byChunk.clear();
        active.clear();
        for (LightRenderHandle<PointLightData> h : beacons) safeFree(h);
        beacons.clear();
    }

    // ------------------------------------------------------------- per frame

    public static void update(Level level, Vec3 cam) {
        knownFixtures = lamps.size();
        ClientConfig cfg = ClientConfig.INSTANCE;
        if (!veilOk || !ClientState.inInterstice || !cfg.dynamicLighting) {
            for (Lamp l : active) release(l);
            active.clear();
            for (LightRenderHandle<PointLightData> h : beacons) safeFree(h);
            beacons.clear();
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
            double r = l.kind == LampKind.FLOOD ? range2 * 2.2 : range2;
            if (l.dist2 < r && !isDead(l)) candidates.add(l);
        }
        nearestFixture = Math.sqrt(nearestFixture);
        candidates.sort((a, b) -> Double.compare(a.dist2, b.dist2));
        int budget = preset.lightBudget;
        for (int i = budget; i < candidates.size(); i++) release(candidates.get(i));
        if (candidates.size() > budget) candidates.subList(budget, candidates.size()).clear();
        for (Lamp l : active) if (!candidates.contains(l)) release(l);
        active.clear();

        double t = ClientState.time;
        for (Lamp l : candidates) {
            if (l.handle == null || !l.handle.isValid()) l.handle = create(renderer, l, preset);
            if (l.handle == null) continue;
            float b = brightness(l, t, cfg);
            LightData data = l.handle.getLightData();
            data.setBrightness(b);
            l.handle.markDirty();
            active.add(l);
        }
        updateBeacons(renderer, cam, preset, t);
        activeLights = active.size() + beacons.size();
    }

    private static LightRenderHandle<? extends LightData> create(LightRenderer renderer, Lamp l, GraphicsPreset preset) {
        double x = BlockPos.getX(l.pos) + 0.5, y = BlockPos.getY(l.pos) + 0.5, z = BlockPos.getZ(l.pos) + 0.5;
        try {
            if (l.kind == LampKind.FLOOD) {
                SpotLightData s = new SpotLightData();
                Vector3f dir = l.facing.step();
                s.getPositionMutable().set(x + dir.x * 0.6, y + dir.y * 0.6, z + dir.z * 0.6);
                // Veil spot lights shine along -Z of their orientation
                s.getOrientationMutable().set(new Quaternionf().rotationTo(0, 0, -1, dir.x, dir.y, dir.z));
                s.setAngle((float) Math.toRadians(32));
                s.setDistance(l.kind.radius);
                s.setSize(0.6f);
                s.setColor(l.kind.r, l.kind.g, l.kind.b);
                s.setBrightness(l.kind.brightness);
                s.setInscatteringStrength(preset.inscattering && ClientConfig.INSTANCE.volumetricLight ? 0.9f : 0f);
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
        return Hash.unit(l.hash, 1) < 0.08 && l.kind != LampKind.EMERGENCY;
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
        // malfunctioning fixtures
        if (Hash.unit(l.hash, 3) < 0.15) b *= flicker(l.hash, t, calm);
        // event-driven failure: each lamp has its own threshold, so failure spreads unevenly
        if (fail > 0 && Hash.unit(l.hash, 4) < fail) {
            b *= calm ? 0.3f : flicker(l.hash ^ 0x5bd1e995L, t * 2.5, false) * 0.6f;
        }
        // during the Stampede the normal lights dim and the emergency circuit takes over
        b *= 1f - emer * 0.55f;
        return b;
    }

    private static float flicker(long h, double t, boolean calm) {
        double n = Math.sin(t * (0.9 + Hash.unit(h, 5) * 2.1)) + Math.sin(t * 3.7 + Hash.unit(h, 6) * 9) * 0.6 + Math.sin(t * 11.3) * 0.25;
        if (calm) return 0.75f + (float) n * 0.08f;
        if (n > 0.9) return 0.05f;
        return Mth.clamp(0.75f + (float) n * 0.25f, 0.2f, 1.1f);
    }

    /** Distant high beacons along the walls: big radius, strong in-scattering. */
    private static void updateBeacons(LightRenderer renderer, Vec3 cam, GraphicsPreset preset, double t) {
        IntersticeLayout L = ClientState.layout;
        int wanted = preset.inscattering && ClientConfig.INSTANCE.volumetricLight ? 3 : 1;
        int cell = (int) Math.floor(cam.z / 190.0);
        int idx = 0;
        for (int c = cell - 1; c <= cell + 1 && idx < wanted; c++) {
            long h = Hash.of(L.sub(901), c);
            if (Hash.unit(h, 0) > 0.75) continue;
            int side = Hash.unit(h, 1) < 0.5 ? -1 : 1;
            double z = c * 190.0 + Hash.unit(h, 2) * 190;
            double y = IntersticeLayout.FLOOR_Y + 150 + Hash.unit(h, 3) * 330;
            double x = L.faceX(side, z) - side * 4;
            LightRenderHandle<PointLightData> handle;
            if (idx < beacons.size()) handle = beacons.get(idx);
            else {
                PointLightData p = new PointLightData();
                try {
                    handle = renderer.addLight(p);
                } catch (Throwable e) {
                    veilOk = false;
                    return;
                }
                beacons.add(handle);
            }
            PointLightData p = handle.getLightData();
            boolean red = Hash.unit(h, 4) < 0.3 || ClientState.emergency > 0.6f;
            p.setPosition(x, y, z);
            p.setRadius(85f);
            if (red) p.setColor(1f, 0.12f, 0.06f); else p.setColor(0.55f, 0.68f, 1f);
            p.setBrightness((red ? 1.5f : 1.1f) * (1f - ClientState.lightFailure * 0.6f));
            p.setInscatteringStrength(preset.inscattering && ClientConfig.INSTANCE.volumetricLight ? 1.6f : 0f);
            handle.markDirty();
            idx++;
        }
        while (beacons.size() > idx) safeFree(beacons.remove(beacons.size() - 1));
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
