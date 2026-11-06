package com.interstice.world;

import com.interstice.Interstice;
import com.interstice.core.IntersticeServer;
import com.interstice.registry.ModWorld;
import com.interstice.world.structures.StructurePass;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Entering and leaving the dimension, with safe arrival and a remembered return point. */
public final class IntersticeTeleport {
    public record ReturnPoint(ResourceLocation dimension, double x, double y, double z) {
        public static final Codec<ReturnPoint> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.fieldOf("dimension").forGetter(ReturnPoint::dimension),
                Codec.DOUBLE.fieldOf("x").forGetter(ReturnPoint::x),
                Codec.DOUBLE.fieldOf("y").forGetter(ReturnPoint::y),
                Codec.DOUBLE.fieldOf("z").forGetter(ReturnPoint::z)
        ).apply(i, ReturnPoint::new));
    }

    public static final AttachmentType<ReturnPoint> RETURN_POINT = AttachmentRegistry.<ReturnPoint>builder()
            .persistent(ReturnPoint.CODEC)
            .copyOnDeath()
            .buildAndRegister(Interstice.id("return_point"));

    private IntersticeTeleport() {}

    public static void init() {}

    /**
     * Into THE INTERSTICE from outside: the journey starts at Level 00, The Threshold (falls back to the Corridor if
     * that dimension is missing). The return point is remembered only when coming from outside the levels.
     */
    public static boolean enter(ServerPlayer player) {
        ServerLevel threshold = player.server.getLevel(com.interstice.registry.ModWorld.THRESHOLD);
        if (threshold == null) return enterCorridor(player);
        remember(player);
        threshold.getChunk(0, 0);
        threshold.getChunk(0, 1);
        player.teleportTo(threshold, com.interstice.world.threshold.ThresholdLayout.SPAWN_X, com.interstice.world.threshold.ThresholdLayout.SPAWN_Y,
                com.interstice.world.threshold.ThresholdLayout.SPAWN_Z, 0f, 0f);
        return true;
    }

    private static void remember(ServerPlayer player) {
        if (!com.interstice.registry.ModWorld.isAnyLevel(player.level())) {
            player.setAttached(RETURN_POINT, new ReturnPoint(player.level().dimension().location(), player.getX(), player.getY(), player.getZ()));
        }
    }

    /**
     * Through a rift: to the next level. Level 00 leads to the Corridor's safe start; past the last level whose
     * dimension exists, the rift leads out (like the Corridor's exit threshold).
     */
    public static void throughRift(ServerPlayer player) {
        com.interstice.world.levels.IntersticeLevel here = com.interstice.world.levels.IntersticeLevel.of(player.level());
        com.interstice.world.levels.IntersticeLevel next = here == null ? null : here.next();
        if (next == com.interstice.world.levels.IntersticeLevel.CORRIDOR) {
            enterCorridor(player);
            return;
        }
        ServerLevel target = next == null ? null : player.server.getLevel(next.dimension);
        if (target == null) {
            leave(player);
            return;
        }
        if (next == com.interstice.world.levels.IntersticeLevel.WHEAT_FARM) {
            int gy = com.interstice.world.wheat.WheatLayout.groundY(0, 2);
            target.getChunk(0, 0);
            player.teleportTo(target, com.interstice.world.wheat.WheatLayout.SPAWN_X, gy, com.interstice.world.wheat.WheatLayout.SPAWN_Z, 0f, 0f);
            return;
        }
        // levels 03-04 add their own arrival here as they are built
        leave(player);
    }

    /**
     * Straight to the start of a level (the {@code /interstice level} command): false if its dimension is not there.
     * The return point is remembered as for {@link #enter}.
     */
    public static boolean toLevel(ServerPlayer player, com.interstice.world.levels.IntersticeLevel target) {
        switch (target) {
            case THRESHOLD -> { return enter(player); }
            case CORRIDOR -> { return enterCorridor(player); }
            case WHEAT_FARM -> {
                ServerLevel wheat = player.server.getLevel(target.dimension);
                if (wheat == null) return false;
                remember(player);
                int gy = com.interstice.world.wheat.WheatLayout.groundY(0, 2);
                wheat.getChunk(0, 0);
                player.teleportTo(wheat, com.interstice.world.wheat.WheatLayout.SPAWN_X, gy, com.interstice.world.wheat.WheatLayout.SPAWN_Z, 0f, 0f);
                return true;
            }
            default -> { return false; }
        }
    }

    /** Into Level 01, The Corridor, at its safe start. */
    public static boolean enterCorridor(ServerPlayer player) {
        IntersticeServer s = IntersticeServer.get();
        if (s == null) return false;
        ServerLevel level = s.level();
        IntersticeLayout L = s.layout();
        if (level == null || L == null) return false;
        if (player.level() != level) remember(player);
        Vec3 spot = findSafeSpot(level, L, 0);
        player.teleportTo(level, spot.x, spot.y, spot.z, 0f, 0f);
        return true;
    }

    public static void leave(ServerPlayer player) {
        ReturnPoint rp = player.getAttached(RETURN_POINT);
        ServerLevel target = null;
        if (rp != null) target = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, rp.dimension()));
        if (target == null || com.interstice.registry.ModWorld.isAnyLevel(target)) {
            ServerLevel ow = player.server.getLevel(Level.OVERWORLD);
            BlockPos sp = ow.getSharedSpawnPos();
            player.teleportTo(ow, sp.getX() + 0.5, sp.getY(), sp.getZ() + 0.5, player.getYRot(), player.getXRot());
        } else {
            player.teleportTo(target, rp.x(), rp.y(), rp.z(), player.getYRot(), player.getXRot());
        }
        player.removeAttached(RETURN_POINT);
    }

    /** Searches along the corridor centre for solid floor with two blocks of headroom, away from shafts and shelters. */
    public static Vec3 findSafeSpot(ServerLevel level, IntersticeLayout L, int startZ) {
        // the finite Corridor: stay inside it (and out of the buildings: the street is the safe arrival)
        if (L.plan() != null) startZ = Math.max(L.plan().startZ + 24, Math.min(L.plan().endZ - 24, startZ));
        for (int i = 0; i < 64; i++) {
            int z = startZ + (i % 2 == 0 ? i / 2 : -(i / 2 + 1)) * 4;
            int x = (int) Math.floor(L.centerX(z + 0.5));
            for (int dx = 0; dx <= 12; dx += 3) {
                int xx = x + ((dx / 3) % 2 == 0 ? dx : -dx);
                if (L.pitAt(xx, z) != null || StructurePass.bunkerAt(L, xx, z) != null) continue;
                if (com.interstice.world.structures.Settlements.lotAt(L, xx, z, 1) != null) continue;
                level.getChunk(xx >> 4, z >> 4); // generate synchronously (command context)
                int fy = L.floorY(xx, z);
                for (int y = fy; y < fy + 6; y++) {
                    BlockPos feet = new BlockPos(xx, y, z);
                    BlockState below = level.getBlockState(feet.below());
                    if (below.isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP)
                            && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                            && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) {
                        return new Vec3(xx + 0.5, y, z + 0.5);
                    }
                }
            }
        }
        return new Vec3(L.centerX(startZ), IntersticeLayout.FLOOR_Y + 1, startZ + 0.5);
    }
}
