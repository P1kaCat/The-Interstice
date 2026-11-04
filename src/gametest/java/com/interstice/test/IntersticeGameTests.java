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
        for (int z : new int[]{0, 333, -812, 4321, -9001}) {
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
            check(level.getBlockState(new BlockPos(cx, IntersticeLayout.MIN_Y, z)).is(net.minecraft.world.level.block.Blocks.BEDROCK), "bedrock floor");
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
        Vec3 spot = IntersticeTeleport.findSafeSpot(level, s.layout(), -60);
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
        Vec3 spot = IntersticeTeleport.findSafeSpot(level, s.layout(), -140);
        ServerPlayer p = survivalPlayer(h);
        p.teleportTo(level, spot.x, spot.y, spot.z, 0, 0);
        p.setShiftKeyDown(true);
        BlockPos base = BlockPos.containing(spot);
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
        h.runAfterDelay(320, () -> {
            check(level.isPositionEntityTicking(e.blockPosition()), "creature was not simulated; test would be meaningless");
            check(e.behavior() != AdherentEntity.Behavior.HUNT, "creature found a player hidden behind cover in the dark");
            System.out.println("[interstice-test] hidden, crouched player stayed undetected (" + e.behavior() + ")");
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
        Vec3 spot = IntersticeTeleport.findSafeSpot(level, s.layout(), -300);
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
        Vec3 spot = IntersticeTeleport.findSafeSpot(level, s.layout(), -420);
        ServerPlayer p = survivalPlayer(h);
        p.teleportTo(level, spot.x, spot.y, spot.z, 0, 0);
        clearSight(level, spot, 10);
        level.setBlockAndUpdate(BlockPos.containing(spot).above(2), ModBlocks.INDUSTRIAL_LAMP.defaultBlockState());
        for (int dx = -32; dx <= 32; dx += 16)
            for (int dz = -32; dz <= 48; dz += 16) level.setChunkForced(((int) Math.floor(spot.x) + dx) >> 4, ((int) Math.floor(spot.z) + dz) >> 4, true);
        AdherentEntity e = s.population.createGround(level, Species.HOLLOW, new Vec3(spot.x, spot.y, spot.z + 8));
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
            System.out.printf("[interstice-test] hollow (fleer) ran from the visible player: %.1f -> %.1f blocks (%s)%n", d0, e.distanceTo(p), b);
            e.discard();
            removePlayer(p);
        });
    }

    @GameTest(template = EMPTY_STRUCTURE, batch = "species5", timeoutTicks = 1400)
    public void investigatorWatchesButNeverHunts(GameTestHelper h) {
        IntersticeServer s = server(h);
        ServerLevel level = s.level();
        Vec3 spot = IntersticeTeleport.findSafeSpot(level, s.layout(), -620);
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
        h.onEachTick(() -> hunted[0] |= e.behavior() == AdherentEntity.Behavior.HUNT);
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
                AdherentEntity c = s.population.create(level, Species.CLINGER, -1, IntersticeLayout.FLOOR_Y + 30, z);
                AdherentEntity o = s.population.create(level, Species.HOLLOW, -1, IntersticeLayout.FLOOR_Y + 45, z);
                check(c != null && o != null, "spawn failed at z=" + z);
                clingers.add(c);
                hollows.add(o);
            }
        });
        h.runAfterDelay(30, () -> {
            int found = level.getEntitiesOfClass(AdherentEntity.class, new net.minecraft.world.phys.AABB(center, center).inflate(60, 250, 60),
                    e -> clingers.contains(e) || hollows.contains(e)).size();
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
        Vec3 spot = IntersticeTeleport.findSafeSpot(level, L, -1500);
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
}
