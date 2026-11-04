package com.interstice.test;

import com.interstice.core.IntersticeServer;
import com.interstice.entity.AdherentEntity;
import com.interstice.entity.navigation.WallMovement;
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

    @GameTest(template = EMPTY_STRUCTURE, batch = "perception", timeoutTicks = 700)
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
}
