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

    public static boolean enter(ServerPlayer player) {
        IntersticeServer s = IntersticeServer.get();
        if (s == null) return false;
        ServerLevel level = s.level();
        IntersticeLayout L = s.layout();
        if (level == null || L == null) return false;
        if (player.level() != level) {
            player.setAttached(RETURN_POINT, new ReturnPoint(player.level().dimension().location(), player.getX(), player.getY(), player.getZ()));
        }
        Vec3 spot = findSafeSpot(level, L, 0);
        player.teleportTo(level, spot.x, spot.y, spot.z, 0f, 0f);
        return true;
    }

    public static void leave(ServerPlayer player) {
        ReturnPoint rp = player.getAttached(RETURN_POINT);
        ServerLevel target = null;
        if (rp != null) target = player.server.getLevel(ResourceKey.create(Registries.DIMENSION, rp.dimension()));
        if (target == null || target.dimension() == ModWorld.INTERSTICE) {
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
        for (int i = 0; i < 64; i++) {
            int z = startZ + (i % 2 == 0 ? i / 2 : -(i / 2 + 1)) * 4;
            int x = (int) Math.floor(L.centerX(z + 0.5));
            for (int dx = 0; dx <= 12; dx += 3) {
                int xx = x + ((dx / 3) % 2 == 0 ? dx : -dx);
                if (L.pitAt(xx, z) != null || StructurePass.bunkerAt(L, xx, z) != null) continue;
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
