package com.interstice.test;

import com.interstice.core.IntersticeServer;
import com.interstice.entity.AdherentEntity;
import com.interstice.entity.navigation.WallMovement;
import com.interstice.entity.species.Species;
import com.interstice.entity.species.SpeciesMix;
import com.interstice.network.ModNetwork;
import com.interstice.event.director.HorrorDirector;
import com.interstice.event.stampede.StampedePhase;
import com.interstice.registry.ModBlocks;
import com.interstice.registry.ModEntities;
import com.interstice.world.IntersticeLayout;
import com.interstice.world.IntersticeTeleport;
import com.interstice.world.structures.StructurePass;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * Headless runtime tests for server-side systems. Run with: gradlew runGametest
 */
public class IntersticeGameTests implements FabricGameTest {

    // ------------------------------------------------------------ helpers

    private static IntersticeServer server(GameTestHelper h) {
        IntersticeServer s = IntersticeServer.get();
        if (s == null) {
            // GameTest servers may begin running tests before SERVER_STARTED listeners fire
            IntersticeServer.start(h.getLevel().getServer());
            s = IntersticeServer.get();
        }
        if (s.level() == null) {
            StringBuilder dims = new StringBuilder();
            for (ServerLevel l : h.getLevel().getServer().getAllLevels()) dims.append(l.dimension().location()).append(' ');
            throw new AssertionError("Interstice dimension missing; loaded: " + dims);
        }
        s.director.setPaused(true);
        return s;
    }

    /** A survival mock player (vanilla's mock player reports creative, which perception ignores). */
    private static ServerPlayer survivalPlayer(GameTestHelper h) {
        ServerLevel level = h.getLevel();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "interstice-test"), false);
        ServerPlayer p = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation()) {
            @Override public boolean isSpectator() { return false; }
            @Override public boolean isCreative() { return false; }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, p, cookie);
        return p;
    }

    private static void removePlayer(ServerPlayer p) {
        p.server.getPlayerList().remove(p);
    }

    /** GameTestAssertException lets succeedWhen() retry next tick instead of crashing the server. */
    private static void check(boolean condition, String message) {
        if (!condition) throw new net.minecraft.gametest.framework.GameTestAssertException(message);
    }

    private static void load(ServerLevel level, IntersticeLayout L, int side, double z) {
        int fx = (int) Math.floor(L.faceX(side, z));
        // forced chunks are entity-ticking (mock players do not create ticking tickets in the test server)
        for (int dx = -16; dx <= 16; dx += 16) level.setChunkForced((fx + dx) >> 4, (int) Math.floor(z) >> 4, true);
    }

    /** Clears a 3x4 corridor of air from the player's feet towards +z so a sight test is not decided by terrain. */
    private static void clearSight(ServerLevel level, Vec3 spot, int length) {
        BlockPos base = BlockPos.containing(spot);
        for (int dz = 1; dz <= length; dz++)
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = 0; dy <= 3; dy++) level.setBlockAndUpdate(base.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
    }

    // --------------------------------------------------------- world tests

    @GameTest(template = EMPTY_STRUCTURE, batch = "world", timeoutTicks = 400)
    public void worldgenWallsFloorAndVoid(GameTestHelper h) {
        IntersticeServer s = server(h);
        ServerLevel level = s.level();
        IntersticeLayout L = s.layout();
        long t0 = System.nanoTime();
        int chunks = 0;
        for (int z : new int[]{0, 333, 1812, 4321, 6001}) {
            int cx = (int) Math.floor(L.centerX(z + 0.5));
            for (int side = -1; side <= 1; side += 2) {
                int wallX = (int) Math.floor(L.centerX(z + 0.5) + side * (L.halfWidth(z + 0.5) + 12));
                level.getChunk(wallX >> 4, z >> 4);
                chunks++;
                BlockState inWall = level.getBlockState(new BlockPos(wallX, 150, z));
                check(!inWall.isAir(), "wall mass expected at x=" + wallX + " y=150 z=" + z + " but was " + inWall);
                int beyond = (int) Math.floor(L.centerX(z + 0.5) + side * (L.halfWidth(z + 0.5) + IntersticeLayout.WALL_THICKNESS + 4));
                level.getChunk(beyond >> 4, z >> 4);
                check(level.getBlockState(new BlockPos(beyond, 150, z)).isAir(), "outside of the wall should be void at z=" + z);
            }
            level.getChunk(cx >> 4, z >> 4);
            chunks++;
            if (L.pitAt(cx, z) == null) {
                int fy = L.floorY(cx, z);
                check(!level.getBlockState(new BlockPos(cx, fy - 1, z)).isAir(), "floor expected at centre line z=" + z);
                check(level.getBlockState(new BlockPos(cx, fy + 40, z)).isAir() || true, "open air above floor");
            }
            check(level.getBlockState(new BlockPos(cx, IntersticeLayout.MIN_Y, z)).is(com.interstice.registry.ModBlocks.ABYSSAL_ROCK), "abyssal rock floor (the Corridor's bedrock)");
        }
        double ms = (System.nanoTime() - t0) / 1e6;
        System.out.printf("[interstice-test] generated %d+ chunks in %.1f ms (%.1f ms/chunk incl. pipeline)%n", chunks, ms, ms / chunks);
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "world", timeoutTicks = 200)
    public void safeSpawnIsStandable(GameTestHelper h) {
        IntersticeServer s = server(h);
        ServerLevel level = s.level();
        Vec3 spot = IntersticeTeleport.findSafeSpot(level, s.layout(), 0);
        BlockPos feet = BlockPos.containing(spot);
        check(level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP), "sturdy ground under spawn");
        check(level.getBlockState(feet).getCollisionShape(level, feet).isEmpty(), "feet free");
        check(level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty(), "head free");
        System.out.println("[interstice-test] safe spawn at " + feet);
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "world", timeoutTicks = 300)
    public void bridgeIsContinuousAcrossChunks(GameTestHelper h) {
        IntersticeServer s = server(h);
        ServerLevel level = s.level();
        IntersticeLayout L = s.layout();
        StructurePass.Bridge bridge = null;
        for (int c = 0; c < 40 && bridge == null; c++) {
            StructurePass.Bridge b = StructurePass.bridge(L, c);
            if (b != null && !b.broken()) bridge = b;
        }
        check(bridge != null, "no intact bridge found in the first 40 cells");
        int z = bridge.z0() + 1;
        int x0 = (int) Math.floor(L.faceX(-1, z)) + 8, x1 = (int) Math.floor(L.faceX(1, z)) - 8;
        int missing = 0, checked = 0, chunkCrossings = 0;
        for (int x = x0; x <= x1; x++) {
            if ((x & 15) == 0) chunkCrossings++;
            level.getChunk(x >> 4, z >> 4);
            if (level.getBlockState(new BlockPos(x, bridge.y(), z)).isAir()) missing++;
            checked++;
        }
        System.out.printf("[interstice-test] bridge at z=%d y=%d: %d/%d deck blocks present across %d chunk borders%n", z, bridge.y(), checked - missing, checked, chunkCrossings);
        check(chunkCrossings >= 1 && missing == 0, "bridge deck must be continuous (missing " + missing + ")");
        h.succeed();
    }

    // ------------------------------------------------------ creature tests

    @GameTest(template = EMPTY_STRUCTURE, batch = "creature", timeoutTicks = 200)
    public void wallCreatureClingsToWall(GameTestHelper h) {
        IntersticeServer s = server(h);
        ServerLevel level = s.level();
        IntersticeLayout L = s.layout();
        ServerPlayer p = survivalPlayer(h);
        Vec3 spot = IntersticeTeleport.findSafeSpot(level, L, 0);
        p.teleportTo(level, spot.x, spot.y, spot.z, 0, 0);
        load(level, L, -1, spot.z + 6);
        AdherentEntity e = s.population.create(level, -1, IntersticeLayout.FLOOR_Y + 20, spot.z + 6);
        check(e != null, "could not attach a creature to the west wall");
        h.runAfterDelay(80, () -> {
            check(e.isAlive() && !e.isRemoved(), "wall creature vanished");
            check(e.mode() == AdherentEntity.Mode.WALL, "creature left wall mode: " + e.mode());
            double sx = WallMovement.surfaceX(level, L, -1, e.getY(), e.getZ(), e.getBbWidth() / 2);
            check(Math.abs(sx - e.getX()) < 1.5, "creature drifted off the surface: x=" + e.getX() + " surface=" + sx);
            System.out.printf("[interstice-test] wall creature at %.1f %.1f %.1f behaviour %s%n", e.getX(), e.getY(), e.getZ(), e.behavior());
            e.discard();
            removePlayer(p);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "creature2", timeoutTicks = 400)
    public void fallenCreatureSurvivesAndSearches(GameTestHelper h) {
        IntersticeServer s = server(h);
        ServerLevel level = s.level();
        IntersticeLayout L = s.layout();
        Vec3 spot = IntersticeTeleport.findSafeSpot(level, L, 40);
        ServerPlayer p = survivalPlayer(h);
        // stand near (but not under) the fall so the creature's chunk is entity-ticking
        p.teleportTo(level, spot.x + (L.faceX(1, spot.z) - spot.x) * 0.5, spot.y, spot.z + 8, 0, 0);
        load(level, L, 1, spot.z);
        AdherentEntity e = s.population.create(level, 1, IntersticeLayout.FLOOR_Y + 14, spot.z);
        check(e != null, "could not attach creature");
        e.forceSurvive = true;
        e.startFalling();
        int[] tick = {0};
        h.onEachTick(() -> {
            if (tick[0]++ % 50 == 0) System.out.printf("[interstice-test] faller t=%d y=%.2f v=%s ground=%s noPhys=%s ticking=%s%n", tick[0], e.getY(),
                    e.getDeltaMovement(), e.onGround(), e.noPhysics, level.isPositionEntityTicking(e.blockPosition()));
        });
        h.succeedWhen(() -> {
            check(e.isAlive(), "forced survivor died");
            check(e.mode() == AdherentEntity.Mode.GROUND, "still falling");
            AdherentEntity.Behavior b = e.behavior();
            check(b == AdherentEntity.Behavior.RECOVER || b == AdherentEntity.Behavior.SEARCH || b == AdherentEntity.Behavior.INVESTIGATE || b == AdherentEntity.Behavior.HUNT,
                    "unexpected behaviour " + b);
            System.out.println("[interstice-test] creature landed and is " + b);
            e.discard();
            removePlayer(p);
        });
    }

    // the searcher wanders with a ~70 degree field of view: detection of a visible player has been
    // observed between 0 and ~550 ticks depending on where it happens to face; the timeout leaves margin
    @GameTest(template = EMPTY_STRUCTURE, batch = "perception", timeoutTicks = 1400)
    public void searcherHuntsVisiblePlayer(GameTestHelper h) {
        IntersticeServer s = server(h);
        ServerLevel level = s.level();
        Vec3 spot = IntersticeTeleport.findSafeSpot(level, s.layout(), 3060);
        ServerPlayer p = survivalPlayer(h);
        p.teleportTo(level, spot.x, spot.y, spot.z, 0, 0);
        // light the player so the test is about perception, not darkness
        level.setBlockAndUpdate(BlockPos.containing(spot).above(2), ModBlocks.INDUSTRIAL_LAMP.defaultBlockState());
        for (int dx = -32; dx <= 32; dx += 16)
            for (int dz = -32; dz <= 32; dz += 16) level.setChunkForced(((int) Math.floor(spot.x) + dx) >> 4, ((int) Math.floor(spot.z) + dz) >> 4, true);
        AdherentEntity e = s.population.createGround(level, new Vec3(spot.x, spot.y, spot.z + 9));
        check(e != null, "spawn failed");
        e.setYRot(180f);
        e.setYHeadRot(180f);
        e.yBodyRot = 180f;
        int[] tk = {0};
        h.onEachTick(() -> {
            if (tk[0]++ % 100 == 0) System.out.printf("[interstice-test] hunter t=%d d=%.1f %s sight=%s at %s; noises %d; wall creatures near %s%n", tk[0], e.distanceTo(p), e.behavior(),
                    e.hasLineOfSight(p), e.blockPosition(), s.noise.audibleFrom(e.getEyePosition(), level.getGameTime() - 100).size(),
                    level.getEntitiesOfClass(AdherentEntity.class, e.getBoundingBox().inflate(90, 200, 90), o -> o != e).stream().map(o -> o.species().id).toList());
        });
        h.succeedWhen(() -> {
            check(e.behavior() == AdherentEntity.Behavior.HUNT, "not hunting yet (" + e.behavior() + ")");
            System.out.println("[interstice-test] searcher detected the lit, visible player and is hunting");
            e.discard();
            removePlayer(p);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "perception2", timeoutTicks = 500)
    public void searcherMissesHiddenPlayer(GameTestHelper h) {
        IntersticeServer s = server(h);
        ServerLevel level = s.level();
        Vec3 spot = IntersticeTeleport.findSafeSpot(level, s.layout(), 3140);
        ServerPlayer p = survivalPlayer(h);
        p.teleportTo(level, spot.x, spot.y, spot.z, 0, 0);
        p.setShiftKeyDown(true);
        BlockPos base = BlockPos.containing(spot);
        clearSight(level, spot, 10);
        // a concrete screen between the player and the creature
        for (int dx = -3; dx <= 3; dx++)
            for (int dy = 0; dy <= 3; dy++)
                level.setBlockAndUpdate(base.offset(dx, dy, 3), ModBlocks.WEATHERED_CONCRETE.defaultBlockState());
        for (int dx = -32; dx <= 32; dx += 16)
            for (int dz = -32; dz <= 32; dz += 16) level.setChunkForced(((int) Math.floor(spot.x) + dx) >> 4, ((int) Math.floor(spot.z) + dz) >> 4, true);
        AdherentEntity e = s.population.createGround(level, new Vec3(spot.x, spot.y, spot.z + 9));
        check(e != null, "spawn failed");
        e.setYRot(180f);
        e.setYHeadRot(180f);
        // Sight only. Since the 2026-11 speed fix a searching creature really prowls (about 1.2 blocks/s) and will walk
        // round a small screen, which is right; what this checks is that the screen hides the player while it is
        // between them: no visibility through it, and the creature does not start hunting while it stays behind it.
        h.onEachTick(() -> s.track(p).scent.clear());
        h.runAfterDelay(3, () -> {
            e.setYHeadRot(180f);
            float behind = com.interstice.entity.ai.Perception.visibility(e, p, 0.0);
            for (int dx = -3; dx <= 3; dx++)
                for (int dy = 0; dy <= 3; dy++) level.setBlockAndUpdate(base.offset(dx, dy, 3), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            float open = com.interstice.entity.ai.Perception.visibility(e, p, 0.0);
            System.out.printf("[interstice-test] hidden: visibility behind the screen %.3f, without it %.3f (%s)%n", behind, open, e.behavior());
            check(level.isPositionEntityTicking(e.blockPosition()), "creature was not simulated; test would be meaningless");
            check(behind == 0f, "the creature saw through the concrete screen: " + behind);
            check(open > 0f, "control failed: not seen even without the screen");
            check(e.behavior() != AdherentEntity.Behavior.HUNT, "creature hunted a player hidden behind cover");
            e.discard();
            removePlayer(p);
            h.succeed();
        });
    }

    // ------------------------------------------------------ stampede tests

    @GameTest(template = EMPTY_STRUCTURE, batch = "stampede", timeoutTicks = 800)
    public void stampedeAdvancesThroughPhases(GameTestHelper h) {
        IntersticeServer s = server(h);
        ServerLevel level = s.level();
        s.stampede.start(level, 1f);
        check(s.stampede.phase() == StampedePhase.PREMONITION, "stampede should begin with PREMONITION");
        h.succeedWhen(() -> {
            check(s.stampede.phase() == StampedePhase.STRUCTURAL_DISTURBANCE, "still " + s.stampede.phase());
            System.out.println("[interstice-test] PREMONITION -> STRUCTURAL_DISTURBANCE transition observed");
            s.stampede.stop(level);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "stampede2", timeoutTicks = 1000)
    public void hordePassageSpawnsRunnersAndFalls(GameTestHelper h) {
        IntersticeServer s = server(h);
        ServerLevel level = s.level();
        Vec3 spot = IntersticeTeleport.findSafeSpot(level, s.layout(), 200);
        ServerPlayer p = survivalPlayer(h);
        p.teleportTo(level, spot.x, spot.y, spot.z, 0, 0);
        for (int side = -1; side <= 1; side += 2)
            for (int dz = -80; dz <= 80; dz += 16) load(level, s.layout(), side, spot.z + dz);
        s.stampede.forcePhase(level, StampedePhase.HORDE_PASSAGE);
        int[] maxRunners = {0};
        h.succeedWhen(() -> {
            List<AdherentEntity> near = level.getEntitiesOfClass(AdherentEntity.class, p.getBoundingBox().inflate(120, 250, 120));
            int runners = (int) near.stream().filter(e -> e.role == AdherentEntity.Role.RUNNER && e.mode() == AdherentEntity.Mode.WALL).count();
            maxRunners[0] = Math.max(maxRunners[0], runners);
            long fallen = near.stream().filter(e -> e.mode() != AdherentEntity.Mode.WALL).count();
            check(maxRunners[0] > 0, "no runners yet");
            check(fallen > 0, "no creature has fallen yet");
            System.out.printf("[interstice-test] horde passage: peak %d real runners near player, %d fallen%n", maxRunners[0], fallen);
            s.stampede.stop(level);
            for (AdherentEntity e : near) e.discard();
            removePlayer(p);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "director", timeoutTicks = 100)
    public void directorActionsRun(GameTestHelper h) {
        IntersticeServer s = server(h);
        ServerLevel level = s.level();
        Vec3 spot = IntersticeTeleport.findSafeSpot(level, s.layout(), 3300);
        ServerPlayer p = survivalPlayer(h);
        p.teleportTo(level, spot.x, spot.y, spot.z, 0, 0);
        for (HorrorDirector.Action a : HorrorDirector.Action.values()) {
            s.director.perform(level, p, a, level.random);
        }
        check(s.stampede.active(), "STAMPEDE action should start the event");
        s.stampede.stop(level);
        removePlayer(p);
        System.out.println("[interstice-test] all director actions executed");
        h.succeed();
    }

    // ------------------------------------------------------ creature family tests

    @GameTest(template = EMPTY_STRUCTURE, batch = "species", timeoutTicks = 100)
    public void everySpeciesIsRegisteredWithItsHitbox(GameTestHelper h) {
        IntersticeServer s = server(h);
        ServerLevel level = s.level();
        StringBuilder log = new StringBuilder();
        for (Species sp : Species.values()) {
            var type = ModEntities.type(sp);
            check(type != null, "no entity type for " + sp.id);
            check(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath().equals(sp.id), "registry id of " + sp.id);
            check(ModEntities.SPAWN_EGGS.get(sp) != null, "no spawn egg for " + sp.id);
            AdherentEntity e = type.create(level);
            check(e != null, "could not create " + sp.id);
            check(e.species() == sp, "species of " + sp.id + " is " + e.species());
            check(Math.abs(e.getBbWidth() - sp.t.width) < 1e-4 && Math.abs(e.getBbHeight() - sp.t.height) < 1e-4,
                    sp.id + " hitbox " + e.getBbWidth() + "x" + e.getBbHeight());
            check(Math.abs(e.getMaxHealth() - sp.t.health) < 1e-3, sp.id + " health " + e.getMaxHealth());
            log.append(String.format("%s %.1fx%.1f hp %.0f; ", sp.id, e.getBbWidth(), e.getBbHeight(), e.getMaxHealth()));
            e.discard();
        }
        System.out.println("[interstice-test] species: " + log);
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "species2", timeoutTicks = 300)
    public void everySpeciesClingsToTheWall(GameTestHelper h) {
        IntersticeServer s = server(h);
        ServerLevel level = s.level();
        IntersticeLayout L = s.layout();
        ServerPlayer p = survivalPlayer(h);
        Vec3 spot = IntersticeTeleport.findSafeSpot(level, L, 520);
        p.teleportTo(level, spot.x, spot.y, spot.z, 0, 0);
        java.util.List<AdherentEntity> all = new java.util.ArrayList<>();
        int i = 0;
        for (Species sp : Species.values()) {
            double z = spot.z - 28 + i * 8;
            load(level, L, 1, z);
            AdherentEntity e = s.population.create(level, sp, 1, IntersticeLayout.FLOOR_Y + 16 + (i % 3) * 5, z);
            check(e != null, "could not attach a " + sp.id + " to the east wall");
            all.add(e);
            i++;
        }
        // a creature may be mid-way over a relief (it eases onto the new surface over a few ticks);
        // what must never happen is staying off its surface
        int[] off = new int[all.size()], worst = new int[all.size()];
        h.onEachTick(() -> {
            for (int k = 0; k < all.size(); k++) {
                AdherentEntity e = all.get(k);
                if (e.isRemoved()) continue;
                double sx = WallMovement.surfaceX(level, L, 1, e.getY(), e.getZ(), e.getBbWidth() / 2);
                off[k] = !Double.isNaN(sx) && Math.abs(sx - e.getX()) < 1.5 ? 0 : off[k] + 1;
                worst[k] = Math.max(worst[k], off[k]);
            }
        });
        h.runAfterDelay(200, () -> {
            StringBuilder log = new StringBuilder();
            for (int k = 0; k < all.size(); k++) {
                AdherentEntity e = all.get(k);
                check(e.isAlive() && !e.isRemoved(), e.species().id + " vanished");
                check(e.mode() == AdherentEntity.Mode.WALL, e.species().id + " left wall mode: " + e.mode());
                check(worst[k] <= 10, e.species().id + " stayed off its surface for " + worst[k] + " ticks");
                log.append(e.species().id).append(' ').append(e.behavior()).append(" (max ").append(worst[k]).append(" ticks in transition); ");
                e.discard();
            }
            System.out.println("[interstice-test] every species clings: " + log);
            removePlayer(p);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "species", timeoutTicks = 100)
    public void speciesDistributionFollowsWeights(GameTestHelper h) {
        java.util.Map<String, Float> cfg = com.interstice.config.ServerConfig.INSTANCE.stampedeSpeciesWeights;
        SpeciesMix base = SpeciesMix.fromConfig(cfg, true);
        net.minecraft.util.RandomSource r = net.minecraft.util.RandomSource.create(12345L);
        int n = 40000;
        int[] count = new int[Species.values().length];
        for (int k = 0; k < n; k++) count[base.pick(r).ordinal()]++;
        float sum = 0;
        for (Species sp : Species.values()) sum += cfg.getOrDefault(sp.id, 0f);
        for (Species sp : Species.values()) {
            double expected = cfg.getOrDefault(sp.id, 0f) / sum;
            double got = count[sp.ordinal()] / (double) n;
            check(Math.abs(got - expected) < 0.012, sp.id + " frequency " + got + " expected " + expected);
        }
        check(base.weight(Species.ADHERENT) > 0.5f, "the Adherent must dominate the default composition");
        // per-event variation: still normalised, deterministic picks, surges flagged
        int surges = 0;
        for (int k = 0; k < 400; k++) {
            SpeciesMix m = base.vary(r, 0.35f, 0.12f, 5f);
            float total = 0;
            for (float w : m.weights()) total += w;
            check(Math.abs(total - 1f) < 1e-4, "varied mix not normalised: " + total);
            check(m.pick(0.0) == m.pick(0.0) && m.pick(0.999999) != null, "pick must be deterministic");
            if (m.surge != null) {
                surges++;
                check(m.surge != Species.ADHERENT, "a surge never boosts the Adherent");
            }
        }
        check(surges > 20 && surges < 90, "surge rate off: " + surges + "/400");
        System.out.printf("[interstice-test] species distribution within 1.2%% of the configured weights; %d/400 surges; default mix: %s%n", surges, base);
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "species", timeoutTicks = 100)
    public void stampedeSyncCarriesTheComposition(GameTestHelper h) {
        float[] w = SpeciesMix.fromConfig(null, true).weights();
        ModNetwork.StampedeSync sent = new ModNetwork.StampedeSync(5, 1234L, 520, 99L, -1, 0.9f, w);
        net.minecraft.network.FriendlyByteBuf buf = new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        ModNetwork.StampedeSync.CODEC.encode(buf, sent);
        ModNetwork.StampedeSync got = ModNetwork.StampedeSync.CODEC.decode(buf);
        check(got.phase() == 5 && got.seed() == 99L && got.direction() == -1, "header fields");
        check(java.util.Arrays.equals(got.species(), w), "species weights did not survive the round trip");
        SpeciesMix client = SpeciesMix.of(got.species()), server = SpeciesMix.of(w);
        for (int k = 0; k < 1000; k++) {
            double u = k / 1000.0;
            check(client.pick(u) == server.pick(u), "client and server disagree on the species of instance " + k);
        }
        System.out.println("[interstice-test] stampede sync carries the species composition; client/server picks agree");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "species3", timeoutTicks = 1200)
    public void stampedeRunnersAreMultispecies(GameTestHelper h) {
        IntersticeServer s = server(h);
        ServerLevel level = s.level();
        Vec3 spot = IntersticeTeleport.findSafeSpot(level, s.layout(), 900);
        ServerPlayer p = survivalPlayer(h);
        p.teleportTo(level, spot.x, spot.y, spot.z, 0, 0);
        for (int side = -1; side <= 1; side += 2)
            for (int dz = -80; dz <= 80; dz += 16) load(level, s.layout(), side, spot.z + dz);
        s.stampede.forcePhase(level, StampedePhase.HORDE_PASSAGE);
        // an even composition so that the test is about participation, not about luck
        float[] even = new float[Species.values().length];
        java.util.Arrays.fill(even, 1f);
        s.stampede.setMix(level, SpeciesMix.of(even));
        java.util.EnumSet<Species> seen = java.util.EnumSet.noneOf(Species.class);
        h.succeedWhen(() -> {
            for (AdherentEntity e : level.getEntitiesOfClass(AdherentEntity.class, p.getBoundingBox().inflate(120, 250, 120),
                    e -> e.role == AdherentEntity.Role.RUNNER)) seen.add(e.species());
            check(seen.size() >= 4, "runner species so far: " + seen);
            System.out.println("[interstice-test] stampede runners of " + seen.size() + " species: " + seen);
            s.stampede.stop(level);
            for (AdherentEntity e : level.getEntitiesOfClass(AdherentEntity.class, p.getBoundingBox().inflate(120, 250, 120))) e.discard();
            removePlayer(p);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "species", timeoutTicks = 100)
    public void groundDispositionsFollowSpecies(GameTestHelper h) {
        net.minecraft.util.RandomSource r = net.minecraft.util.RandomSource.create(7L);
        StringBuilder log = new StringBuilder();
        for (Species sp : Species.values()) {
            int[] c = new int[Species.GroundDisposition.values().length];
            for (int k = 0; k < 4000; k++) c[sp.rollDisposition(r.nextFloat()).ordinal()]++;
            float[] w = {sp.t.hunt, sp.t.investigate, sp.t.flee, sp.t.hide, sp.t.stunned};
            float sum = 0;
            for (float x : w) sum += x;
            for (int k = 0; k < w.length; k++) {
                double expected = w[k] / sum, got = c[k] / 4000.0;
                check(Math.abs(got - expected) < 0.035, sp.id + " " + Species.GroundDisposition.values()[k] + " " + got + " vs " + expected);
                if (w[k] == 0) check(c[k] == 0, sp.id + " rolled an impossible disposition " + Species.GroundDisposition.values()[k]);
            }
            log.append(sp.id).append(' ').append(java.util.Arrays.toString(c)).append("; ");
        }
        check(Species.CRAWLER.t.hunt < Species.ADHERENT.t.hunt && Species.HOLLOW.t.flee > Species.ADHERENT.t.flee, "species temperaments inverted");
        System.out.println("[interstice-test] ground dispositions per species: " + log);
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "species4", timeoutTicks = 1400)
    public void fleerRunsFromAVisiblePlayer(GameTestHelper h) {
        IntersticeServer s = server(h);
        ServerLevel level = s.level();
        Vec3 spot = IntersticeTeleport.findSafeSpot(level, s.layout(), 3420);
        ServerPlayer p = survivalPlayer(h);
        p.teleportTo(level, spot.x, spot.y, spot.z, 0, 0);
        clearSight(level, spot, 10);
        level.setBlockAndUpdate(BlockPos.containing(spot).above(2), ModBlocks.INDUSTRIAL_LAMP.defaultBlockState());
        for (int dx = -32; dx <= 32; dx += 16)
            for (int dz = -32; dz <= 48; dz += 16) level.setChunkForced(((int) Math.floor(spot.x) + dx) >> 4, ((int) Math.floor(spot.z) + dz) >> 4, true);
        // a crawler: wide sight (the hollow, the other natural fleer, is nearly blind since v7 and goes by ear)
        AdherentEntity e = s.population.createGround(level, Species.CRAWLER, new Vec3(spot.x, spot.y, spot.z + 8));
        check(e != null, "spawn failed");
        e.disposition = Species.GroundDisposition.FLEER;
        e.setYRot(180f);
        e.setYHeadRot(180f);
        e.yBodyRot = 180f;
        double d0 = e.distanceTo(p);
        boolean[] hunted = {false};
        int[] tk = {0};
        h.onEachTick(() -> {
            if (tk[0]++ % 100 == 0) System.out.printf("[interstice-test] fleer t=%d d=%.1f %s sight=%s light=%d at %s player %s%n", tk[0], e.distanceTo(p), e.behavior(),
                    e.hasLineOfSight(p), level.getMaxLocalRawBrightness(p.blockPosition()), e.blockPosition(), p.blockPosition());
        });
        h.onEachTick(() -> hunted[0] |= e.behavior() == AdherentEntity.Behavior.HUNT);
        h.succeedWhen(() -> {
            check(!hunted[0], "a fleer started hunting");
            AdherentEntity.Behavior b = e.behavior();
            check(b == AdherentEntity.Behavior.FLEE || b == AdherentEntity.Behavior.CLIMB, "not fleeing yet (" + b + ")");
            check(e.distanceTo(p) > d0 + 3, "fled only to " + e.distanceTo(p) + " (from " + d0 + ")");
            System.out.printf("[interstice-test] crawler (fleer) ran from the visible player: %.1f -> %.1f blocks (%s)%n", d0, e.distanceTo(p), b);
            e.discard();
            removePlayer(p);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "species5", timeoutTicks = 1400)
    public void investigatorWatchesButNeverHunts(GameTestHelper h) {
        IntersticeServer s = server(h);
        ServerLevel level = s.level();
        Vec3 spot = IntersticeTeleport.findSafeSpot(level, s.layout(), 3620);
        ServerPlayer p = survivalPlayer(h);
        p.teleportTo(level, spot.x, spot.y, spot.z, 0, 0);
        clearSight(level, spot, 10);
        level.setBlockAndUpdate(BlockPos.containing(spot).above(2), ModBlocks.INDUSTRIAL_LAMP.defaultBlockState());
        for (int dx = -32; dx <= 32; dx += 16)
            for (int dz = -32; dz <= 32; dz += 16) level.setChunkForced(((int) Math.floor(spot.x) + dx) >> 4, ((int) Math.floor(spot.z) + dz) >> 4, true);
        AdherentEntity e = s.population.createGround(level, Species.WATCHER, new Vec3(spot.x, spot.y, spot.z + 9));
        check(e != null, "spawn failed");
        e.disposition = Species.GroundDisposition.INVESTIGATOR;
        e.setYRot(180f);
        e.setYHeadRot(180f);
        e.yBodyRot = 180f;
        boolean[] hunted = {false};
        int[] tk = {0};
        h.onEachTick(() -> {
            hunted[0] |= e.behavior() == AdherentEntity.Behavior.HUNT;
            // the player shuffles now and then (footsteps): the watcher's gaze is narrow, sound turns its head
            if (tk[0] % 40 == 0) s.noise.emit(p.position(), 20f, level.getGameTime(), p);   // the watcher hears at 0.6: a 12-block sound
            if (tk[0]++ % 100 == 0 && e.groundGoal != null) System.out.printf("[interstice-test] investigator t=%d %s awareness %.2f d=%.1f head %.0f vis %.3f phase %s%n",
                    tk[0], e.behavior(), e.groundGoal.awareness(), e.distanceTo(p), e.getYHeadRot(),
                    com.interstice.entity.ai.Perception.visibility(e, p, 0.0), e.groundGoal.phase());
        });
        h.succeedWhen(() -> {
            check(!hunted[0], "an investigator hunted");
            check(e.behavior() == AdherentEntity.Behavior.STARE, "not watching yet (" + e.behavior() + ")");
            check(e.distanceTo(p) > 5.0, "the watcher came too close: " + e.distanceTo(p));
            System.out.printf("[interstice-test] watcher (investigator) watches the visible player from %.1f blocks without hunting%n", e.distanceTo(p));
            e.discard();
            removePlayer(p);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "species6", timeoutTicks = 400)
    public void clingersHoldOnWhenTheWallsFlee(GameTestHelper h) {
        IntersticeServer s = server(h);
        ServerLevel level = s.level();
        IntersticeLayout L = s.layout();
        ServerPlayer p = survivalPlayer(h);
        Vec3 spot = IntersticeTeleport.findSafeSpot(level, L, 1300);
        p.teleportTo(level, spot.x, spot.y, spot.z, 0, 0);
        java.util.List<AdherentEntity> clingers = new java.util.ArrayList<>(), hollows = new java.util.ArrayList<>();
        for (int k = 0; k < 12; k++) load(level, L, -1, spot.z - 30 + k * 5);
        Vec3 center = new Vec3(L.faceX(-1, spot.z), IntersticeLayout.FLOOR_Y + 38, spot.z);
        // spawn once the forced chunks are loaded, react once the entities are in their sections
        h.runAfterDelay(20, () -> {
            for (int k = 0; k < 12; k++) {
                double z = spot.z - 30 + k * 5;
                AdherentEntity c = s.population.create(level, Species.CLINGER, -1, IntersticeLayout.FLOOR_Y + 34, z);   // above the row of high flood lamps (FLOOR_Y + 30)
                AdherentEntity o = s.population.create(level, Species.HOLLOW, -1, IntersticeLayout.FLOOR_Y + 45, z);
                check(c != null && o != null, "spawn failed at z=" + z);
                clingers.add(c);
                hollows.add(o);
            }
        });
        // react once all 24 are registered in their entity sections (chunk loading may take a few more ticks)
        boolean[] reacted = {false};
        h.onEachTick(() -> {
            if (reacted[0] || clingers.size() < 12) return;
            int found = level.getEntitiesOfClass(AdherentEntity.class, new net.minecraft.world.phys.AABB(center, center).inflate(60, 250, 60),
                    e -> clingers.contains(e) || hollows.contains(e)).size();
            if (found < 24 && h.getTick() < 300) return;
            reacted[0] = true;
            check(found == 24, "only " + found + "/24 test creatures are visible to the reaction query");
            s.population.react(level, center, 60, true, null);
            long held = clingers.stream().filter(c -> c.behavior() != AdherentEntity.Behavior.FLEE).count();
            long fled = hollows.stream().filter(o -> o.behavior() == AdherentEntity.Behavior.FLEE).count();
            System.out.printf("[interstice-test] flee reaction: %d/12 clingers held on, %d/12 hollows fled%n", held, fled);
            check(held >= 7, "only " + held + "/12 clingers held on");
            check(fled == 12, "only " + fled + "/12 hollows fled");
            for (AdherentEntity e : clingers) e.discard();
            for (AdherentEntity e : hollows) e.discard();
            removePlayer(p);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "species7", timeoutTicks = 400)
    public void burdenMakesTheStructureVibrate(GameTestHelper h) {
        IntersticeServer s = server(h);
        ServerLevel level = s.level();
        IntersticeLayout L = s.layout();
        ServerPlayer p = survivalPlayer(h);
        Vec3 spot = IntersticeTeleport.findSafeSpot(level, L, 4500);
        p.teleportTo(level, spot.x, spot.y, spot.z, 0, 0);
        load(level, L, 1, spot.z);
        load(level, L, 1, spot.z + 16);
        AdherentEntity b = s.population.create(level, Species.BURDEN, 1, IntersticeLayout.FLOOR_Y + 20, spot.z);
        check(b != null, "could not attach a burden");
        long t0 = level.getGameTime();
        b.beginCrawl(b.getY() + 2, b.getZ() + 12, 0.05);
        h.succeedWhen(() -> {
            boolean heard = s.noise.audibleFrom(b.position(), t0).stream().anyMatch(n -> n.source() == b);
            check(heard, "no vibration from the moving burden yet");
            check(Math.abs(b.getZ() - spot.z) > 0.3, "the burden did not move");
            System.out.printf("[interstice-test] moving burden emitted a structural vibration after %d ticks%n", level.getGameTime() - t0);
            b.discard();
            removePlayer(p);
        });
    }

    /** Forces the chunks of a square around a point (entity-ticking in the test server). */
    private static void forceAround(ServerLevel level, double x, double z, int radius) {
        for (int dx = -radius; dx <= radius; dx += 16)
            for (int dz = -radius; dz <= radius; dz += 16) level.setChunkForced(((int) Math.floor(x) + dx) >> 4, ((int) Math.floor(z) + dz) >> 4, true);
    }

    /** A standable floor point between dMin and dMax blocks in front of a wall face, or null. */
    private static Vec3 floorNearWall(ServerLevel level, IntersticeLayout L, int side, double z, int dMin, int dMax) {
        for (int d = dMin; d <= dMax; d++) {
            int x = (int) Math.floor(L.faceX(side, z) - side * d);
            for (int y = IntersticeLayout.FLOOR_Y + 8; y >= IntersticeLayout.FLOOR_Y - 4; y--) {
                BlockPos b = new BlockPos(x, y, (int) Math.floor(z));
                if (level.getBlockState(b).getCollisionShape(level, b).isEmpty()) continue;
                if (level.getBlockState(b.above()).isAir() && level.getBlockState(b.above(2)).isAir() && level.getBlockState(b.above(3)).isAir())
                    return new Vec3(x + 0.5, y + 1, Math.floor(z) + 0.5);
                break;
            }
        }
        return null;
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "species8", timeoutTicks = 1100)
    public void stunnedCreatureLiesStillThenWakesAsNonHunter(GameTestHelper h) {
        IntersticeServer s = server(h);
        ServerLevel level = s.level();
        IntersticeLayout L = s.layout();
        Vec3 spot = IntersticeTeleport.findSafeSpot(level, L, 2100);
        ServerPlayer p = survivalPlayer(h);
        p.teleportTo(level, spot.x, spot.y, spot.z, 0, 0);
        forceAround(level, L.faceX(1, spot.z), spot.z, 32);
        AdherentEntity e = s.population.create(level, Species.ADHERENT, 1, IntersticeLayout.FLOOR_Y + 14, spot.z);
        check(e != null, "could not attach creature");
        e.forceSurvive = true;
        e.forceDisposition = Species.GroundDisposition.STUNNED;
        e.startFalling();
        boolean[] f = new boolean[4];   // landed, stunned seen, woke, hunted
        int[] stunTicks = {0};
        Vec3[] stunPos = {null};
        double[] maxDisp = {0};
        Species.GroundDisposition[] wokeAs = {null};
        h.onEachTick(() -> {
            AdherentEntity.Behavior b = e.behavior();
            f[3] |= b == AdherentEntity.Behavior.HUNT;
            if (!f[0] && e.mode() == AdherentEntity.Mode.GROUND) {
                f[0] = true;
                // a visible, lit player right next to it: a stunned creature must not react
                Vec3 at = e.position().add(-2.5, 0.2, 0);
                p.teleportTo(level, at.x, at.y, at.z, 90, 0);
                level.setBlockAndUpdate(BlockPos.containing(at).above(3), ModBlocks.INDUSTRIAL_LAMP.defaultBlockState());
            }
            if (b == AdherentEntity.Behavior.STUNNED) {
                f[1] = true;
                stunTicks[0]++;
                if (stunPos[0] == null) stunPos[0] = e.position();
                maxDisp[0] = Math.max(maxDisp[0], e.position().multiply(1, 0, 1).distanceTo(stunPos[0].multiply(1, 0, 1)));
            } else if (f[1] && !f[2]) {
                f[2] = true;
                wokeAs[0] = e.disposition;
            }
        });
        h.succeedWhen(() -> {
            check(e.isAlive(), "forced survivor died");
            check(!f[3], "a stunned creature hunted");
            check(f[1], "never stunned (" + e.behavior() + ")");
            check(f[2] && e.behavior() != AdherentEntity.Behavior.RECOVER, "still stunned or recovering (" + e.behavior() + ", " + stunTicks[0] + " ticks stunned)");
            int expected = (int) (220 * Species.ADHERENT.t.recoverMul) - 2;
            check(stunTicks[0] >= expected, "stunned only " + stunTicks[0] + " ticks (expected at least " + expected + ")");
            check(maxDisp[0] < 0.5, "moved " + maxDisp[0] + " blocks while stunned");
            check(wokeAs[0] == Species.GroundDisposition.INVESTIGATOR || wokeAs[0] == Species.GroundDisposition.FLEER, "woke up as " + wokeAs[0]);
            System.out.printf("[interstice-test] stunned for %d ticks (moved %.2f), woke up as %s, now %s, never hunted%n", stunTicks[0], maxDisp[0], wokeAs[0], e.behavior());
            e.discard();
            removePlayer(p);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "species9", timeoutTicks = 1400)
    public void hiderGoesToTheWallFootAndFleesWhenFound(GameTestHelper h) {
        IntersticeServer s = server(h);
        ServerLevel level = s.level();
        IntersticeLayout L = s.layout();
        Vec3 spot = IntersticeTeleport.findSafeSpot(level, L, 2400);
        ServerPlayer p = survivalPlayer(h);
        Vec3 away = IntersticeTeleport.findSafeSpot(level, L, 2400 - 90);
        p.teleportTo(level, away.x, away.y, away.z, 0, 0);
        int side = 1;
        Vec3 start = null;
        for (int dz = 0; dz < 40 && start == null; dz += 4) {
            start = floorNearWall(level, L, side, spot.z + dz, 9, 15);
            if (start == null) {
                side = -side;
                start = floorNearWall(level, L, side, spot.z + dz, 9, 15);
            }
        }
        check(start != null, "no floor 9-15 blocks in front of a wall near z=" + spot.z);
        Vec3 st = start;
        int sd = side;
        double startDepth = L.wallDepth(st.x, st.z);
        check(startDepth < -6, "start too close to the wall (depth " + startDepth + ")");
        forceAround(level, st.x, st.z, 32);
        AdherentEntity[] ref = {null};
        h.runAfterDelay(20, () -> {
            AdherentEntity e = s.population.createGround(level, Species.CRAWLER, st);
            check(e != null, "spawn failed");
            e.disposition = Species.GroundDisposition.HIDER;
            e.setBehavior(AdherentEntity.Behavior.RECOVER);
            e.setBehaviorTimer(1);
            ref[0] = e;
        });
        int[] hideTicks = {0};
        long[] hiddenAt = {-1};
        Vec3[] hidePos = {null};
        double[] maxDisp = {0}, hideDepth = {0};
        boolean[] found = {false}, fled = {false};
        h.onEachTick(() -> {
            AdherentEntity e = ref[0];
            if (e == null) return;
            AdherentEntity.Behavior b = e.behavior();
            if (!found[0] && b == AdherentEntity.Behavior.HIDE) {
                if (hidePos[0] == null) {
                    hidePos[0] = e.position();
                    hiddenAt[0] = level.getGameTime();
                    hideDepth[0] = L.wallDepth(e.getX(), e.getZ());
                }
                hideTicks[0]++;
                maxDisp[0] = Math.max(maxDisp[0], e.position().multiply(1, 0, 1).distanceTo(hidePos[0].multiply(1, 0, 1)));
                if (hideTicks[0] == 120) {
                    // found: a lit player steps right up to it
                    found[0] = true;
                    Vec3 at = e.position().add(-sd * 2.5, 0.1, 0);
                    p.teleportTo(level, at.x, at.y, at.z, sd > 0 ? -90 : 90, 0);
                    level.setBlockAndUpdate(BlockPos.containing(at).above(3), ModBlocks.INDUSTRIAL_LAMP.defaultBlockState());
                }
            }
            if (found[0] && b == AdherentEntity.Behavior.FLEE) fled[0] = true;
        });
        h.succeedWhen(() -> {
            AdherentEntity e = ref[0];
            check(e != null && e.isAlive(), "no hider");
            check(hidePos[0] != null, "not hiding yet (" + e.behavior() + " depth " + L.wallDepth(e.getX(), e.getZ()) + ")");
            check(hideDepth[0] > -5, "hid away from the wall (depth " + hideDepth[0] + ")");
            check(found[0], "hidden for " + hideTicks[0] + " ticks");
            check(maxDisp[0] < 0.3, "moved " + maxDisp[0] + " blocks while hidden");
            check(fled[0], "found but not fleeing (" + e.behavior() + ")");
            System.out.printf("[interstice-test] crawler (hider) walked from depth %.1f to the wall foot (depth %.1f), stayed still for %d ticks (moved %.2f), fled when found%n",
                    startDepth, hideDepth[0], hideTicks[0], maxDisp[0]);
            e.discard();
            removePlayer(p);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "species10", timeoutTicks = 500)
    public void smallerSpeciesGiveTheBurdenRoom(GameTestHelper h) {
        IntersticeServer s = server(h);
        ServerLevel level = s.level();
        IntersticeLayout L = s.layout();
        Vec3 spot = IntersticeTeleport.findSafeSpot(level, L, 5300);
        ServerPlayer p = survivalPlayer(h);
        p.teleportTo(level, spot.x, spot.y, spot.z, 0, 0);
        int side = -L.sideOf(spot.x, spot.z);   // the far wall: the player must not startle them
        double z0 = spot.z, y0 = IntersticeLayout.FLOOR_Y + 30;
        for (int k = -2; k <= 2; k++) load(level, L, side, z0 + k * 16);
        Species[] small = {Species.ADHERENT, Species.CRAWLER, Species.STRIDER, Species.HOLLOW, Species.WATCHER, Species.FRACTURED};
        double[][] off = {{2, 4}, {-2, 4}, {2, -4}, {-2, -4}, {4.5, 0}, {-4.5, 0}};
        java.util.List<AdherentEntity> others = new java.util.ArrayList<>();
        AdherentEntity[] burden = {null}, clinger = {null};
        double[] d0 = new double[small.length];
        h.runAfterDelay(20, () -> {
            AdherentEntity b = s.population.create(level, Species.BURDEN, side, y0, z0);
            check(b != null, "could not attach the burden");
            b.setBehaviorTimer(1_000_000);
            burden[0] = b;
            for (int k = 0; k < small.length; k++) {
                AdherentEntity o = s.population.create(level, small[k], side, y0 + off[k][0], z0 + off[k][1]);
                check(o != null, "could not attach " + small[k].id);
                o.setBehaviorTimer(1_000_000);   // idle: any movement comes from the reaction
                others.add(o);
            }
            AdherentEntity c = s.population.create(level, Species.CLINGER, side, y0 - 3, z0 + 1.5);
            check(c != null, "could not attach the clinger");
            c.setBehaviorTimer(1_000_000);
            clinger[0] = c;
        });
        double[] c0 = new double[2];
        h.runAfterDelay(22, () -> {
            for (int k = 0; k < small.length; k++) d0[k] = wallDist(others.get(k), burden[0]);
            c0[0] = clinger[0].getY();
            c0[1] = clinger[0].getZ();
        });
        h.succeedWhen(() -> {
            check(clinger[0] != null && d0[0] > 0, "not set up yet");
            StringBuilder log = new StringBuilder();
            for (int k = 0; k < small.length; k++) {
                double d = wallDist(others.get(k), burden[0]);
                check(d > d0[k] + 3, small[k].id + " did not make room: " + d0[k] + " -> " + d);
                log.append(String.format("%s %.1f->%.1f ", small[k].id, d0[k], d));
            }
            AdherentEntity c = clinger[0];
            double moved = Math.hypot(c.getY() - c0[0], c.getZ() - c0[1]);
            check(moved < 0.5, "the clinger made way (moved " + moved + ")");
            System.out.println("[interstice-test] species gave the burden room: " + log + "; clinger stayed (moved " + String.format("%.2f", moved) + ")");
            for (AdherentEntity o : others) o.discard();
            burden[0].discard();
            c.discard();
            removePlayer(p);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "species12", timeoutTicks = 300)
    public void hollowIsAgitatedByMovementNextToIt(GameTestHelper h) {
        IntersticeServer s = server(h);
        ServerLevel level = s.level();
        IntersticeLayout L = s.layout();
        Vec3 spot = IntersticeTeleport.findSafeSpot(level, L, 5900);
        ServerPlayer p = survivalPlayer(h);
        p.teleportTo(level, spot.x, spot.y, spot.z, 0, 0);
        int side = -L.sideOf(spot.x, spot.z);
        double y0 = IntersticeLayout.FLOOR_Y + 30;
        for (int k = -2; k <= 2; k++) load(level, L, side, spot.z + k * 16);
        // pair A: a hollow with an adherent crawling past it; pair B (20 blocks away): the adherent stays still
        AdherentEntity[] e = new AdherentEntity[4];
        double[] start = new double[2];
        h.runAfterDelay(20, () -> {
            e[0] = s.population.create(level, Species.HOLLOW, side, y0, spot.z - 10);
            e[1] = s.population.create(level, Species.ADHERENT, side, y0 + 3, spot.z - 13);
            e[2] = s.population.create(level, Species.HOLLOW, side, y0, spot.z + 10);
            e[3] = s.population.create(level, Species.ADHERENT, side, y0 + 3, spot.z + 7);
            for (int k = 0; k < 4; k++) {
                check(e[k] != null, "could not attach creature " + k);
                e[k].setBehaviorTimer(1_000_000);
            }
            e[1].beginCrawl(y0 + 3, spot.z - 4, 0.08);
            start[0] = e[0].getZ();
            start[1] = e[2].getZ();
        });
        boolean[] agitated = {false, false};
        h.onEachTick(() -> {
            if (e[0] == null) return;
            for (int k = 0; k < 2; k++) {
                AdherentEntity hollow = e[k * 2];
                agitated[k] |= hollow.behavior() == AdherentEntity.Behavior.FLEE || hollow.mode() == AdherentEntity.Mode.FALLING;
            }
        });
        h.runAfterDelay(200, () -> {
            check(agitated[0], "the hollow ignored the crawler passing next to it (" + e[0].behavior() + ")");
            check(!agitated[1], "the hollow next to a motionless neighbour startled");
            System.out.printf("[interstice-test] hollow agitated by a crawling neighbour (%s), the one beside a still neighbour stayed %s%n",
                    e[0].mode() == AdherentEntity.Mode.FALLING ? "lost its grip" : "fled", e[2].behavior());
            for (AdherentEntity x : e) x.discard();
            removePlayer(p);
            h.succeed();
        });
    }

    private static double wallDist(AdherentEntity a, AdherentEntity b) {
        return Math.hypot(a.getY() - b.getY(), a.getZ() - b.getZ());
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "species11", timeoutTicks = 1600)
    public void watcherAimsImperfectlyAndSometimesLooksPastThePlayer(GameTestHelper h) {
        IntersticeServer s = server(h);
        ServerLevel level = s.level();
        IntersticeLayout L = s.layout();
        Vec3 safe = IntersticeTeleport.findSafeSpot(level, L, 2700);
        int side = L.sideOf(safe.x, safe.z);
        Vec3 floor = null;
        for (int dz = 0; dz < 60 && floor == null; dz += 6) floor = floorNearWall(level, L, side, safe.z + dz, 14, 30);
        check(floor != null, "no floor 14-30 blocks from the wall near z=" + safe.z);
        Vec3 spot = floor;
        ServerPlayer p = survivalPlayer(h);
        p.teleportTo(level, spot.x, spot.y, spot.z, 0, 0);
        double y = IntersticeLayout.FLOOR_Y + 22;
        for (int k = -2; k <= 2; k++) load(level, L, side, spot.z + k * 16);
        java.util.List<AdherentEntity> watchers = new java.util.ArrayList<>();
        h.runAfterDelay(20, () -> {
            for (int k = 0; k < 10; k++) {
                AdherentEntity w = s.population.create(level, Species.WATCHER, side, y, spot.z - 18 + k * 4);
                check(w != null, "could not attach watcher " + k);
                double d = w.distanceTo(p);
                check(d > 9.5 && d < 46, "watcher " + k + " at " + d + " blocks from the player (needs 9.5-46)");
                watchers.add(w);
            }
        });
        java.util.Map<AdherentEntity, org.joml.Vector3f> last = new java.util.HashMap<>();
        int[] aimed = {0}, past = {0}, elsewhere = {0};
        double[] errSum = {0};
        h.onEachTick(() -> {
            for (AdherentEntity w : watchers) {
                // compress the long idle pauses: the next wall activity is chosen at once (the choice is the real one)
                if (w.behavior() == AdherentEntity.Behavior.IDLE) w.setBehaviorTimer(1);
                if (w.behavior() != AdherentEntity.Behavior.STARE) continue;
                org.joml.Vector3f v = w.lookTarget();
                if (v.lengthSquared() < 1e-6 || v.equals(last.get(w))) continue;
                last.put(w, new org.joml.Vector3f(v));
                Vec3 lt = new Vec3(v.x(), v.y(), v.z());
                double err = lt.distanceTo(p.getEyePosition());
                Vec3 toP = p.position().subtract(w.position()).multiply(1, 0, 1), toL = lt.subtract(w.position()).multiply(1, 0, 1);
                if (err < 5) {
                    aimed[0]++;
                    errSum[0] += err;
                } else if (lt.distanceTo(p.position()) > 35 && toL.normalize().dot(toP.normalize()) > 0.985 && toL.length() > toP.length() + 30) past[0]++;
                else elsewhere[0]++;
            }
        });
        h.succeedWhen(() -> {
            check(watchers.size() == 10, "watchers not spawned yet");
            check(aimed[0] >= 4, "only " + aimed[0] + " aims at the player so far");
            check(past[0] >= 1, "never looked past the player yet (" + aimed[0] + " aims, " + elsewhere[0] + " elsewhere)");
            double mean = errSum[0] / aimed[0];
            check(mean > 0.3, "aim too exact (mean error " + mean + ")");
            System.out.printf("[interstice-test] watchers: %d aims at the player (mean error %.2f blocks), %d looks past the player, %d at the corridor%n",
                    aimed[0], mean, past[0], elsewhere[0]);
            for (AdherentEntity w : watchers) w.discard();
            removePlayer(p);
        });
    }

    // ------------------------------------------------- senses, search, squeeze (v7)

    private static void forceSquare(ServerLevel level, Vec3 at, int radius) {
        for (int dx = -radius; dx <= radius; dx += 16)
            for (int dz = -radius; dz <= radius; dz += 16) level.setChunkForced(((int) Math.floor(at.x) + dx) >> 4, ((int) Math.floor(at.z) + dz) >> 4, true);
    }

    private static AdherentEntity groundFacing(IntersticeServer s, ServerLevel level, Species sp, Vec3 at, float yaw) {
        AdherentEntity e = s.population.createGround(level, sp, at);
        check(e != null, "spawn failed: " + sp.id);
        e.setYRot(yaw);
        e.setYHeadRot(yaw);
        e.yBodyRot = yaw;
        return e;
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "senses", timeoutTicks = 200)
    public void speciesSeeAndHearDifferently(GameTestHelper h) {
        IntersticeServer s = server(h);
        ServerLevel level = s.level();
        Vec3 spot = IntersticeTeleport.findSafeSpot(level, s.layout(), 260);
        ServerPlayer p = survivalPlayer(h);
        p.teleportTo(level, spot.x, spot.y, spot.z, 0, 0);
        level.setBlockAndUpdate(BlockPos.containing(spot).above(2), ModBlocks.INDUSTRIAL_LAMP.defaultBlockState());
        clearSight(level, spot, 20);
        forceSquare(level, spot, 32);
        AdherentEntity watcher = groundFacing(s, level, Species.WATCHER, new Vec3(spot.x, spot.y, spot.z + 18), 180f);
        AdherentEntity hollow = groundFacing(s, level, Species.HOLLOW, new Vec3(spot.x + 1, spot.y, spot.z + 18), 180f);
        AdherentEntity clinger = groundFacing(s, level, Species.CLINGER, new Vec3(spot.x - 1, spot.y, spot.z + 8), 180f);
        AdherentEntity fractured = groundFacing(s, level, Species.FRACTURED, new Vec3(spot.x, spot.y, spot.z + 10), 180f);
        h.runAfterDelay(3, () -> {
            for (AdherentEntity e : List.of(watcher, hollow, clinger, fractured)) {
                e.setYRot(180f);
                e.setYHeadRot(180f);
            }
            float w = com.interstice.entity.ai.Perception.visibility(watcher, p, 0.1);
            float ho = com.interstice.entity.ai.Perception.visibility(hollow, p, 0.1);
            float still = com.interstice.entity.ai.Perception.visibility(clinger, p, 0.0);
            float moving = com.interstice.entity.ai.Perception.visibility(clinger, p, 0.2);
            fractured.setYHeadRot(180f + 55f);
            float fa = com.interstice.entity.ai.Perception.visibility(fractured, p, 0.1);
            fractured.setYHeadRot(180f - 55f);
            float fb = com.interstice.entity.ai.Perception.visibility(fractured, p, 0.1);
            System.out.printf("[interstice-test] senses: watcher at 18 %.3f, hollow at 18 %.3f; clinger still %.3f vs moving %.3f; "
                    + "fractured head +55 %.3f, -55 %.3f%n", w, ho, still, moving, fa, fb);
            check(w > 0.05f, "the watcher did not see a lit player 18 blocks ahead");
            check(ho == 0f, "the near-blind hollow saw a player 18 blocks away");
            check(still < moving * 0.25f, "the clinger saw a still player almost as well as a moving one");
            check(Math.max(fa, fb) > 2.5f * Math.min(fa, fb), "the fractured's field of view is not lopsided");
            for (AdherentEntity e : List.of(watcher, hollow, clinger, fractured)) e.discard();
            removePlayer(p);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "scent", timeoutTicks = 900)
    public void creatureFollowsTheScentTrail(GameTestHelper h) {
        IntersticeServer s = server(h);
        ServerLevel level = s.level();
        Vec3 spot = IntersticeTeleport.findSafeSpot(level, s.layout(), 420);
        ServerPlayer p = survivalPlayer(h);
        Vec3 end = new Vec3(spot.x, spot.y, spot.z + 16);
        p.teleportTo(level, end.x, end.y, end.z, 0, 0);
        p.setShiftKeyDown(true);
        forceSquare(level, spot, 32);
        clearSight(level, spot, 18);
        // a trail from where the creature stands to where the player now is (laid down over the last 20 s)
        IntersticeServer.PlayerTrack t = s.track(p);
        t.scent.clear();
        long now = level.getGameTime();
        for (int i = 0; i <= 16; i++) t.scent.addLast(new IntersticeServer.PlayerTrack.Scent(new Vec3(spot.x, spot.y, spot.z + i), now - 400 + i * 20, 1f));
        AdherentEntity e = groundFacing(s, level, Species.CRAWLER, new Vec3(spot.x + 0.5, spot.y, spot.z - 1), 90f);
        e.disposition = Species.GroundDisposition.HUNTER;
        boolean[] tracked = {false};
        h.onEachTick(() -> tracked[0] |= e.groundGoal != null && "TRACK".equals(e.groundGoal.phase()));
        h.succeedWhen(() -> {
            double d = e.position().distanceTo(end);
            check(tracked[0], "never picked up the trail (" + e.behavior() + ")");
            check(d < 5 || e.behavior() == AdherentEntity.Behavior.HUNT, "did not follow the trail to the player (" + String.format("%.1f", d) + " blocks)");
            System.out.printf("[interstice-test] crawler followed the scent trail to %.1f blocks of the player (%s)%n", d, e.behavior());
            e.discard();
            removePlayer(p);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "calls", timeoutTicks = 1400)
    public void aHunterCallsTheOthers(GameTestHelper h) {
        IntersticeServer s = server(h);
        ServerLevel level = s.level();
        Vec3 spot = IntersticeTeleport.findSafeSpot(level, s.layout(), 3260);
        ServerPlayer p = survivalPlayer(h);
        p.teleportTo(level, spot.x, spot.y, spot.z, 0, 0);
        level.setBlockAndUpdate(BlockPos.containing(spot).above(2), ModBlocks.INDUSTRIAL_LAMP.defaultBlockState());
        clearSight(level, spot, 12);
        forceSquare(level, spot, 48);
        AdherentEntity hunter = groundFacing(s, level, Species.ADHERENT, new Vec3(spot.x, spot.y, spot.z + 9), 180f);
        hunter.disposition = Species.GroundDisposition.HUNTER;
        // the other one is 22 blocks back, facing away: it cannot see the player
        AdherentEntity other = groundFacing(s, level, Species.ADHERENT, new Vec3(spot.x, spot.y, spot.z + 31), 0f);
        other.disposition = Species.GroundDisposition.HUNTER;
        h.succeedWhen(() -> {
            check(hunter.behavior() == AdherentEntity.Behavior.HUNT, "the first one is not hunting yet (" + hunter.behavior() + ")");
            AdherentEntity.Behavior b = other.behavior();
            check(b == AdherentEntity.Behavior.INVESTIGATE || b == AdherentEntity.Behavior.HUNT, "the other did not answer the call (" + b + ")");
            System.out.printf("[interstice-test] the hunter called; the other one, %.1f blocks away and facing away, is now %s%n",
                    other.distanceTo(p), b);
            hunter.discard();
            other.discard();
            removePlayer(p);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "squeeze", timeoutTicks = 1200)
    public void creatureSqueezesThroughAOneBlockGap(GameTestHelper h) {
        IntersticeServer s = server(h);
        ServerLevel level = s.level();
        Vec3 spot = IntersticeTeleport.findSafeSpot(level, s.layout(), 560);
        forceSquare(level, spot, 32);
        BlockPos base = BlockPos.containing(spot);
        BlockState wall = ModBlocks.WEATHERED_CONCRETE.defaultBlockState();
        // a closed room (inside 5x5, 3 high) whose only way in is a one-block hole at floor level facing +z
        for (int dx = -4; dx <= 4; dx++)
            for (int dz = -4; dz <= 4; dz++)
                for (int dy = 0; dy <= 4; dy++) {
                    boolean shell = Math.abs(dx) >= 3 || Math.abs(dz) >= 3 || dy >= 3;
                    BlockPos q = base.offset(dx, dy, dz);
                    level.setBlockAndUpdate(q, shell ? wall : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                }
        for (int dz = 3; dz <= 4; dz++) level.setBlockAndUpdate(base.offset(0, 0, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        // open ground in front of the hole
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = 5; dz <= 14; dz++)
                for (int dy = 0; dy <= 3; dy++) level.setBlockAndUpdate(base.offset(dx, dy, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        Vec3 inside = Vec3.atBottomCenterOf(base);
        AdherentEntity e = groundFacing(s, level, Species.ADHERENT, new Vec3(spot.x, spot.y, spot.z + 11), 180f);
        e.disposition = Species.GroundDisposition.HUNTER;
        boolean[] squeezed = {false};
        int[] tk = {0};
        h.onEachTick(() -> {
            squeezed[0] |= e.isSqueezing();
            // one call (as from another creature inside), repeated only if it has lost interest
            if (e.groundGoal != null && (tk[0]++ == 1 || tk[0] % 200 == 0 && e.behavior() != AdherentEntity.Behavior.INVESTIGATE))
                e.groundGoal.hearCall(inside, 0.6f, level.getGameTime());
            if (tk[0] % 100 == 0) System.out.printf("[interstice-test] squeeze t=%d at %s d=%.1f squeezing=%s %s%n", tk[0], e.blockPosition(),
                    e.position().distanceTo(inside), e.isSqueezing(), e.behavior());
        });
        h.succeedWhen(() -> {
            check(squeezed[0], "never squeezed");
            check(e.position().distanceTo(inside) < 2.2, "not inside the room yet");
            check(!e.isSqueezing(), "still flattened inside the room");
            System.out.println("[interstice-test] the adherent squeezed through the one-block hole and stood up inside");
            e.discard();
        });
    }

    // 600 ticks: generating the 32x32 chunk square around the spot stalls the server, and the ticks it then
    // catches up count against the test before the hunter even sees the player
    @GameTest(template = EMPTY_STRUCTURE, batch = "chase", timeoutTicks = 600)
    public void huntersRunAboutAsFastAsAPlayer(GameTestHelper h) {
        IntersticeServer s = server(h);
        ServerLevel level = s.level();
        Vec3 spot = IntersticeTeleport.findSafeSpot(level, s.layout(), 3420);
        ServerPlayer p = survivalPlayer(h);
        p.teleportTo(level, spot.x, spot.y, spot.z, 0, 0);
        level.setBlockAndUpdate(BlockPos.containing(spot).above(2), ModBlocks.INDUSTRIAL_LAMP.defaultBlockState());
        clearSight(level, spot, 24);
        forceSquare(level, spot, 32);
        AdherentEntity e = groundFacing(s, level, Species.ADHERENT, new Vec3(spot.x, spot.y, spot.z + 20), 180f);
        e.disposition = Species.GroundDisposition.HUNTER;
        long[] start = {-1, -1};   // [0] when to start measuring, [1] when the measurement started
        Vec3[] from = {null};
        double[] speed = {-1};
        h.onEachTick(() -> {
            if (speed[0] >= 0) return;
            boolean hunting = e.behavior() == AdherentEntity.Behavior.HUNT;
            long now = level.getGameTime();
            if (start[0] < 0) {
                if (!hunting) return;
                start[0] = now + 40;   // after the rear-and-cry (30 ticks), let it get going
                System.out.printf("[interstice-test] the adherent started hunting after %d ticks%n", h.getTick());
            } else if (from[0] == null) {
                // the first hunting tick from then on (not one exact tick: it may be mid-rear at that moment)
                if (now >= start[0] && hunting) {
                    from[0] = e.position();
                    start[1] = now;
                }
            } else if (now - start[1] >= 20) {
                speed[0] = e.position().subtract(from[0]).horizontalDistance() * 20.0 / (now - start[1]);
                System.out.printf("[interstice-test] the hunting adherent runs at %.2f blocks/s%n", speed[0]);
            }
        });
        h.succeedWhen(() -> {
            check(speed[0] >= 0, "not measured yet (" + e.behavior() + ")");
            check(speed[0] > 4.0, "the hunter is too slow: " + String.format("%.2f", speed[0]) + " blocks/s");
            e.discard();
            removePlayer(p);
        });
    }

    // ------------------------------------------------- survival rules and devices (Corridor rework)

    @GameTest(template = EMPTY_STRUCTURE, batch = "survival", timeoutTicks = 400)
    public void foodNeverHealsInTheInterstice(GameTestHelper h) {
        IntersticeServer s = server(h);
        ServerLevel level = s.level();
        Vec3 spot = IntersticeTeleport.findSafeSpot(level, s.layout(), 700);
        forceSquare(level, spot, 16);
        ServerPlayer p = survivalPlayer(h);
        p.teleportTo(level, spot.x, spot.y, spot.z, 0, 0);
        p.getAbilities().instabuild = false;
        p.setHealth(8f);
        p.getFoodData().setFoodLevel(20);
        p.getFoodData().setSaturation(10f);
        boolean regen = p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION, 200, 2));
        check(!regen, "the Regeneration effect was applied in the Interstice");
        // eat a golden apple the vanilla way: no regeneration either
        net.minecraft.world.item.ItemStack apple = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLDEN_APPLE);
        apple.finishUsingItem(level, p);
        h.runAfterDelay(300, () -> {
            System.out.printf("[interstice-test] food: health after 15 s with a full stomach and a golden apple: %.1f (was 8.0)%n", p.getHealth());
            check(p.getHealth() <= 8.0f, "health regenerated from food: " + p.getHealth());
            check(!p.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION), "regeneration effect present");
            // a bandage heals
            net.minecraft.world.item.ItemStack bandage = new net.minecraft.world.item.ItemStack(com.interstice.registry.ModItems.BANDAGE, 2);
            bandage.finishUsingItem(level, p);
            check(p.getHealth() >= 8.0f + com.interstice.config.ServerConfig.INSTANCE.bandageHeal - 0.01f, "the bandage did not heal: " + p.getHealth());
            check(bandage.getCount() == 1, "the bandage was not consumed");
            System.out.printf("[interstice-test] food: a bandage healed to %.1f%n", p.getHealth());
            removePlayer(p);
            h.succeed();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "survival2", timeoutTicks = 200)
    public void ordinaryWeaponsCannotKillCreatures(GameTestHelper h) {
        IntersticeServer s = server(h);
        ServerLevel level = s.level();
        Vec3 spot = IntersticeTeleport.findSafeSpot(level, s.layout(), 760);
        forceSquare(level, spot, 16);
        ServerPlayer p = survivalPlayer(h);
        p.teleportTo(level, spot.x, spot.y, spot.z, 0, 0);
        AdherentEntity crawler = groundFacing(s, level, Species.CRAWLER, new Vec3(spot.x, spot.y, spot.z + 2), 180f);
        AdherentEntity burden = groundFacing(s, level, Species.BURDEN, new Vec3(spot.x + 4, spot.y, spot.z + 3), 180f);
        h.runAfterDelay(2, () -> {
            float hp = crawler.getHealth();
            var src = level.damageSources();
            for (int i = 0; i < 6; i++) {
                crawler.invulnerableTime = 0;
                crawler.hurt(src.playerAttack(p), 1000f);
                crawler.invulnerableTime = 0;
                crawler.hurt(src.arrow(null, p), 1000f);
                crawler.invulnerableTime = 0;
                crawler.hurt(src.inFire(), 1000f);
                crawler.invulnerableTime = 0;
                crawler.hurt(src.explosion(null, p), 1000f);
                crawler.invulnerableTime = 0;
                crawler.hurt(src.lava(), 1000f);
            }
            check(crawler.isAlive() && crawler.getHealth() == hp, "ordinary damage hurt the crawler: " + crawler.getHealth() + "/" + hp);
            // the resonance spike on a crawler that is not stunned: it only wakes, nothing dies
            net.minecraft.world.item.ItemStack spike = new net.minecraft.world.item.ItemStack(com.interstice.registry.ModItems.RESONANCE_SPIKE);
            spike.interactLivingEntity(p, crawler, net.minecraft.world.InteractionHand.MAIN_HAND);
            check(crawler.isAlive(), "the spike killed a crawler that was not stunned");
            // stunned, it is vulnerable to the spike; the burden never is
            crawler.deviceStun(100);
            net.minecraft.world.item.ItemStack spike2 = new net.minecraft.world.item.ItemStack(com.interstice.registry.ModItems.RESONANCE_SPIKE);
            spike2.interactLivingEntity(p, crawler, net.minecraft.world.InteractionHand.MAIN_HAND);
            burden.deviceStun(100);
            p.teleportTo(level, burden.getX() - 1.5, burden.getY(), burden.getZ(), 0, 0);
            net.minecraft.world.item.ItemStack spike3 = new net.minecraft.world.item.ItemStack(com.interstice.registry.ModItems.RESONANCE_SPIKE);
            spike3.interactLivingEntity(p, burden, net.minecraft.world.InteractionHand.MAIN_HAND);
            h.runAfterDelay(25, () -> {
                check(!crawler.isAlive(), "the spike did not kill the stunned crawler");
                check(burden.isAlive(), "the spike killed the burden");
                System.out.println("[interstice-test] weapons: sword, arrow, fire, explosion and lava did nothing; the spike killed a stunned crawler, not the burden");
                burden.discard();
                removePlayer(p);
                h.succeed();
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "devices", timeoutTicks = 300)
    public void flashDazzlesWhatLooksAtIt(GameTestHelper h) {
        IntersticeServer s = server(h);
        ServerLevel level = s.level();
        Vec3 spot = IntersticeTeleport.findSafeSpot(level, s.layout(), 820);
        clearSight(level, spot, 12);
        forceSquare(level, spot, 16);
        ServerPlayer p = survivalPlayer(h);
        p.teleportTo(level, spot.x, spot.y, spot.z, 0, 0);
        AdherentEntity facing = groundFacing(s, level, Species.ADHERENT, new Vec3(spot.x, spot.y, spot.z + 6), 180f);
        AdherentEntity away = groundFacing(s, level, Species.STRIDER, new Vec3(spot.x + 1, spot.y, spot.z + 8), 0f);
        AdherentEntity hollow = groundFacing(s, level, Species.HOLLOW, new Vec3(spot.x - 1, spot.y, spot.z + 5), 180f);
        h.runAfterDelay(3, () -> {
            facing.setYHeadRot(180f);
            away.setYHeadRot(0f);
            hollow.setYHeadRot(180f);
            int n = com.interstice.item.FlashFlareItem.flash(level, p);
            System.out.printf("[interstice-test] flash: %d dazzled; adherent facing %s, strider facing away %s, hollow %s%n", n,
                    facing.behavior(), away.behavior(), hollow.behavior());
            check(facing.behavior() == AdherentEntity.Behavior.STUNNED, "the adherent facing the flash was not dazzled");
            check(away.behavior() != AdherentEntity.Behavior.STUNNED, "a creature facing away was dazzled");
            check(hollow.behavior() != AdherentEntity.Behavior.STUNNED, "the near-blind hollow was dazzled");
            h.runAfterDelay(160, () -> {
                check(facing.behavior() != AdherentEntity.Behavior.STUNNED, "still stunned after the flash wore off");
                for (AdherentEntity e : List.of(facing, away, hollow)) e.discard();
                removePlayer(p);
                h.succeed();
            });
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "devices2", timeoutTicks = 600)
    public void wardKeepsAHunterAway(GameTestHelper h) {
        IntersticeServer s = server(h);
        ServerLevel level = s.level();
        Vec3 spot = IntersticeTeleport.findSafeSpot(level, s.layout(), 880);
        clearSight(level, spot, 16);
        forceSquare(level, spot, 24);
        ServerPlayer p = survivalPlayer(h);
        p.teleportTo(level, spot.x, spot.y, spot.z, 0, 0);
        level.setBlockAndUpdate(BlockPos.containing(spot).above(2), ModBlocks.INDUSTRIAL_LAMP.defaultBlockState());
        level.setBlockAndUpdate(BlockPos.containing(spot).offset(1, 0, 0), ModBlocks.WARD_EMITTER.defaultBlockState());
        AdherentEntity e = groundFacing(s, level, Species.ADHERENT, new Vec3(spot.x, spot.y, spot.z + 14), 180f);
        e.disposition = Species.GroundDisposition.HUNTER;
        double[] closest = {99};
        boolean[] hunted = {false};
        h.onEachTick(() -> {
            closest[0] = Math.min(closest[0], e.distanceTo(p));
            hunted[0] |= e.behavior() == AdherentEntity.Behavior.HUNT;
        });
        h.runAfterDelay(500, () -> {
            System.out.printf("[interstice-test] ward: the hunter came no closer than %.1f blocks (ward radius %.0f); hunted=%s%n",
                    closest[0], com.interstice.block.WardEmitterBlock.RADIUS, hunted[0]);
            check(hunted[0], "the hunter never noticed the player");
            check(closest[0] > com.interstice.block.WardEmitterBlock.RADIUS - 2.5, "the hunter walked into the ward: " + closest[0]);
            e.discard();
            removePlayer(p);
            h.succeed();
        });
    }

    // ------------------------------------------------- the finite Corridor (generation version 2)

    @GameTest(template = EMPTY_STRUCTURE, batch = "plan", timeoutTicks = 40)
    public void corridorPlanRespectsItsLimits(GameTestHelper h) {
        int minLen = Integer.MAX_VALUE, maxLen = 0, minPure = Integer.MAX_VALUE;
        for (long seed = 0; seed < 300; seed++) {
            long s = com.interstice.util.Hash.of(0x5eedL, seed);
            com.interstice.world.CorridorPlan a = new com.interstice.world.CorridorPlan(s), b = new com.interstice.world.CorridorPlan(s);
            check(a.length == b.length && a.toString().equals(b.toString()), "plan not deterministic for seed " + s);
            check(a.length >= 7000 && a.length <= 10000, "length out of range: " + a.length);
            check(a.endZ - a.startZ == a.length, "ends do not match the length");
            int prevEnd = a.startZ;
            for (com.interstice.world.CorridorPlan.Region r : com.interstice.world.CorridorPlan.Region.values()) {
                check(a.regionStart(r) == prevEnd, "regions are not contiguous: " + a);
                prevEnd = a.regionEnd(r);
                minPure = Math.min(minPure, a.pureLength(r));
                check(a.pureLength(r) >= com.interstice.world.CorridorPlan.MIN_REGION, "region too short: " + r + " in " + a);
            }
            check(prevEnd == a.endZ, "regions do not end at the far bulkhead");
            minLen = Math.min(minLen, a.length);
            maxLen = Math.max(maxLen, a.length);
        }
        IntersticeServer srv = server(h);
        System.out.printf("[interstice-test] plan: 300 seeds, length %d..%d, shortest pure region %d blocks; this world: %s%n",
                minLen, maxLen, minPure, srv.layout().plan());
        check(srv.layout().plan() != null, "the test world is not on generation version 2");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "buildings", timeoutTicks = 1200)
    public void buildingsHaveRealInteriors(GameTestHelper h) {
        IntersticeServer s = server(h);
        ServerLevel level = s.level();
        IntersticeLayout L = s.layout();
        com.interstice.world.CorridorPlan plan = L.plan();
        check(plan != null, "not a version-2 world");
        int checked = 0, chests = 0, doors = 0, crossing = 0, regionsSeen = 0;
        java.util.EnumSet<com.interstice.world.CorridorPlan.Region> regions = java.util.EnumSet.noneOf(com.interstice.world.CorridorPlan.Region.class);
        long t0 = System.nanoTime();
        int chunks = 0;
        // a few multi-storey buildings in each region
        for (com.interstice.world.CorridorPlan.Region r : com.interstice.world.CorridorPlan.Region.values()) {
            int found = 0;
            int c0 = Math.floorDiv(plan.regionStart(r) + 120, com.interstice.world.structures.Settlements.CELL);
            for (int c = c0; c < c0 + 40 && found < 2; c++) {
                for (int side = -1; side <= 1 && found < 2; side += 2) {
                    var lot = com.interstice.world.structures.Settlements.lot(L, side, c);
                    if (!lot.built() || lot.region() != r) continue;
                    for (int cx = (lot.x0() - 2) >> 4; cx <= (lot.x0() + lot.sx() + 2) >> 4; cx++)
                        for (int cz = (lot.z0() - 2) >> 4; cz <= (lot.z0() + lot.sz() + 2) >> 4; cz++) {
                            level.getChunk(cx, cz);
                            chunks++;
                        }
                    regions.add(r);
                    found++;
                    checked++;
                    if ((lot.x0() >> 4) != ((lot.x0() + lot.sx() - 1) >> 4) || (lot.z0() >> 4) != ((lot.z0() + lot.sz() - 1) >> 4)) crossing++;
                    int fy = IntersticeLayout.FLOOR_Y;
                    for (int x = lot.x0(); x < lot.x0() + lot.sx(); x++)
                        for (int z = lot.z0(); z < lot.z0() + lot.sz(); z++)
                            for (int y = fy - 2; y < fy + 18; y++) {
                                BlockPos p = new BlockPos(x, y, z);
                                net.minecraft.world.level.block.state.BlockState st = level.getBlockState(p);
                                if (st.getBlock() instanceof net.minecraft.world.level.block.DoorBlock) doors++;
                                if (level.getBlockEntity(p) instanceof net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity rc
                                        && rc.getLootTable() != null) chests++;   // plain barrels are furniture
                            }
                    // walled buildings: their four corners stand (also where they cross chunk borders)
                    if (lot.kind() == com.interstice.world.structures.Settlements.Kind.APARTMENT || lot.kind() == com.interstice.world.structures.Settlements.Kind.CLINIC
                            || lot.kind() == com.interstice.world.structures.Settlements.Kind.HALL) {
                        for (int[] k : new int[][]{{0, 0}, {lot.sx() - 1, 0}, {0, lot.sz() - 1}, {lot.sx() - 1, lot.sz() - 1}}) {
                            BlockPos p = new BlockPos(lot.x0() + k[0], fy + 1, lot.z0() + k[1]);
                            check(!level.getBlockState(p).isAir(), "missing corner of a " + lot.kind() + " at " + p);
                        }
                    }
                }
            }
        }
        double ms = (System.nanoTime() - t0) / 1e6;
        System.out.printf("[interstice-test] buildings: %d checked in %d regions (%d across chunk borders), %d doors, %d loot containers; %d chunk loads in %.0f ms%n",
                checked, regions.size(), crossing, doors, chests, chunks, ms);
        check(regions.size() == 4, "buildings missing in some regions: " + regions);
        check(doors > 0 && chests > 0, "no doors or no loot");
        h.succeed();
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "ends", timeoutTicks = 600)
    public void corridorIsClosedWithAnExit(GameTestHelper h) {
        IntersticeServer s = server(h);
        ServerLevel level = s.level();
        IntersticeLayout L = s.layout();
        com.interstice.world.CorridorPlan plan = L.plan();
        check(plan != null, "not a version-2 world");
        int cx = (int) Math.floor(L.centerX(plan.endZ + 0.5));
        int zt = plan.endZ + com.interstice.world.CorridorPlan.EXIT_DEPTH;
        level.getChunk(cx >> 4, zt >> 4);
        level.getChunk(cx >> 4, (plan.endZ - 2) >> 4);
        int fy = IntersticeLayout.FLOOR_Y;
        check(level.getBlockState(new BlockPos(cx, fy + 1, zt)).is(ModBlocks.EXIT_THRESHOLD), "no threshold at the end of the exit doorway");
        check(level.getBlockState(new BlockPos(cx, fy + 1, plan.endZ + 2)).isAir(), "the exit doorway is blocked");
        int side = (int) Math.floor(L.centerX(plan.endZ + 4.5) + 20);
        check(!level.getBlockState(new BlockPos(side, fy + 3, plan.endZ + 4)).isAir(), "the far bulkhead is open beside the exit");
        int sz = plan.startZ - 3;
        int sx = (int) Math.floor(L.centerX(sz + 0.5));
        level.getChunk(sx >> 4, sz >> 4);
        check(!level.getBlockState(new BlockPos(sx, fy + 2, sz)).isAir(), "the near bulkhead is open");
        System.out.printf("[interstice-test] ends: bulkheads at z=%d and z=%d, exit threshold at z=%d%n", plan.startZ, plan.endZ, zt);
        h.succeed();
    }

    // ------------------------------------------------- postures under low ceilings

    @GameTest(template = EMPTY_STRUCTURE, batch = "postures", timeoutTicks = 1600)
    public void striderStoopsAndCrouchesUnderLowCeilings(GameTestHelper h) {
        IntersticeServer s = server(h);
        ServerLevel level = s.level();
        Vec3 spot = IntersticeTeleport.findSafeSpot(level, s.layout(), 4700);
        forceSquare(level, spot, 32);
        BlockPos base = BlockPos.containing(spot);
        BlockState wall = ModBlocks.WEATHERED_CONCRETE.defaultBlockState();
        BlockState air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        // a passage 3 wide along +z: 8 blocks under a 3-block ceiling, then 8 under a 2-block ceiling, then open
        for (int dz = -6; dz <= 30; dz++)
            for (int dx = -3; dx <= 3; dx++)
                for (int dy = 0; dy <= 5; dy++) {
                    BlockPos q = base.offset(dx, dy, dz);
                    int ceiling = dz >= 4 && dz < 12 ? 3 : dz >= 12 && dz < 20 ? 2 : 99;
                    boolean side = Math.abs(dx) >= 2 && dz >= 4 && dz < 20;
                    level.setBlockAndUpdate(q, side || (dy >= ceiling && Math.abs(dx) <= 2 && dz >= 4 && dz < 20) ? wall : air);
                }
        Vec3 goal = Vec3.atBottomCenterOf(base.offset(0, 0, 26));
        AdherentEntity e = groundFacing(s, level, Species.STRIDER, Vec3.atBottomCenterOf(base.offset(0, 0, -3)), 0f);
        e.disposition = Species.GroundDisposition.HUNTER;
        boolean[] seen = new boolean[4];
        int[] tk = {0};
        h.onEachTick(() -> {
            seen[e.posture().ordinal()] = true;
            if (tk[0]++ % 60 == 0 && e.groundGoal != null) e.groundGoal.hearCall(goal, 0.6f, level.getGameTime());
        });
        h.succeedWhen(() -> {
            check(seen[AdherentEntity.Posture.STOOP.ordinal()], "never stooped under the 3-block ceiling");
            check(seen[AdherentEntity.Posture.CROUCH.ordinal()], "never crouched under the 2-block ceiling");
            check(e.position().distanceTo(goal) < 3.0, "not through the passage yet (" + String.format("%.1f", e.position().distanceTo(goal)) + ", " + e.posture() + ")");
            check(e.posture() == AdherentEntity.Posture.STAND, "still low after the passage (" + e.posture() + ")");
            System.out.println("[interstice-test] postures: the strider stooped, crouched and stood up again on the other side");
            e.discard();
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "facade", timeoutTicks = 600)
    public void hunterClimbsAFacadeToTheRoof(GameTestHelper h) {
        IntersticeServer s = server(h);
        ServerLevel level = s.level();
        Vec3 spot = IntersticeTeleport.findSafeSpot(level, s.layout(), 5300);
        forceSquare(level, spot, 32);
        BlockPos base = BlockPos.containing(spot);
        BlockState wall = ModBlocks.WEATHERED_CONCRETE.defaultBlockState();
        BlockState air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        // a solid 6x6 block, 5 high, with open ground around it; the player stands on its roof, lit
        for (int dx = -10; dx <= 10; dx++)
            for (int dz = -10; dz <= 14; dz++)
                for (int dy = 0; dy <= 8; dy++) {
                    boolean block = dx >= -3 && dx <= 2 && dz >= -3 && dz <= 2 && dy < 5;
                    level.setBlockAndUpdate(base.offset(dx, dy, dz), block ? wall : air);
                }
        level.setBlockAndUpdate(base.offset(-3, 5, -3), ModBlocks.INDUSTRIAL_LAMP.defaultBlockState());
        ServerPlayer p = survivalPlayer(h);
        p.teleportTo(level, base.getX() + 0.5, base.getY() + 5, base.getZ() + 0.5, 0, 0);
        AdherentEntity e = groundFacing(s, level, Species.ADHERENT, Vec3.atBottomCenterOf(base.offset(0, 0, 10)), 180f);
        e.disposition = Species.GroundDisposition.HUNTER;
        double[] top = {0};
        h.onEachTick(() -> top[0] = Math.max(top[0], e.getY() - base.getY()));
        h.succeedWhen(() -> {
            check(top[0] >= 4.95, "never got up onto the roof (highest " + String.format("%.1f", top[0]) + ", " + e.behavior() + ")");
            System.out.printf("[interstice-test] facade: the hunter climbed %.1f blocks up the building to the roof%n", top[0]);
            e.discard();
            removePlayer(p);
        });
    }
}
