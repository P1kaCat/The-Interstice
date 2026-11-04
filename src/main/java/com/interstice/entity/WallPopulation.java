package com.interstice.entity;

import com.interstice.config.ServerConfig;
import com.interstice.core.IntersticeServer;
import com.interstice.registry.ModEntities;
import com.interstice.world.IntersticeLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Keeps a small, budgeted population of real wall creatures around each player.
 * Spawns out of the player's view cone and despawns creatures nobody is near.
 * Uses bounded per-player box queries (entity sections) instead of global scans.
 */
public final class WallPopulation {
    private final IntersticeServer s;

    public WallPopulation(IntersticeServer s) {
        this.s = s;
    }

    public void tick(ServerLevel level) {
        long now = level.getGameTime();
        if (now % 40 != 7) return;
        boolean suppressed = s.stampede.suppressesAmbient();
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator()) continue;
            List<AdherentEntity> near = level.getEntitiesOfClass(AdherentEntity.class, p.getBoundingBox().inflate(90, 200, 90),
                    e -> e.role == AdherentEntity.Role.AMBIENT);
            if (!suppressed && near.size() < ServerConfig.INSTANCE.ambientWallCreaturesPerPlayer) {
                spawnAmbient(level, p);
            }
        }
        if (now % 200 == 7) {
            for (ServerPlayer p : level.players()) {
                for (AdherentEntity e : level.getEntitiesOfClass(AdherentEntity.class, p.getBoundingBox().inflate(160, 250, 160))) {
                    Vec3 ep = e.position();
                    double nearest = Double.MAX_VALUE;
                    for (ServerPlayer q : level.players()) nearest = Math.min(nearest, q.position().distanceToSqr(ep));
                    if (nearest > 120 * 120) e.discard();
                }
            }
        }
    }

    public AdherentEntity spawnAmbient(ServerLevel level, ServerPlayer p) {
        RandomSource r = level.random;
        IntersticeLayout L = s.layout();
        if (L == null) return null;
        Vec3 look = p.getLookAngle();
        for (int attempt = 0; attempt < 6; attempt++) {
            double dz = (r.nextBoolean() ? 1 : -1) * (24 + r.nextDouble() * 50);
            double z = p.getZ() + dz;
            // prefer spawning behind the player's gaze
            if (Math.signum(dz) == Math.signum(look.z) && Math.abs(look.z) > 0.5 && attempt < 4) continue;
            int side = r.nextBoolean() ? -1 : 1;
            double y = Math.max(IntersticeLayout.FLOOR_Y + 8, p.getY() + 4 + r.nextDouble() * 70);
            AdherentEntity e = create(level, side, y, z);
            if (e != null) return e;
        }
        return null;
    }

    public AdherentEntity create(ServerLevel level, int side, double y, double z) {
        if (!level.isLoaded(BlockPos.containing(s.layout().faceX(side, z), y, z))) return null;
        AdherentEntity e = ModEntities.ADHERENT.create(level);
        if (e == null) return null;
        if (!e.attachToWall(side, y, z)) {
            e.discard();
            return null;
        }
        e.finalizeSpawn(level, level.getCurrentDifficultyAt(e.blockPosition()), MobSpawnType.EVENT, null);
        e.setBehaviorTimer(40 + level.random.nextInt(300));
        level.addFreshEntity(e);
        return e;
    }

    public AdherentEntity createGround(ServerLevel level, Vec3 pos) {
        AdherentEntity e = ModEntities.ADHERENT.create(level);
        if (e == null) return null;
        e.setPos(pos);
        e.setMode(AdherentEntity.Mode.GROUND);
        e.role = AdherentEntity.Role.SEARCHER;
        e.setBehavior(AdherentEntity.Behavior.SEARCH);
        e.finalizeSpawn(level, level.getCurrentDifficultyAt(e.blockPosition()), MobSpawnType.EVENT, null);
        level.addFreshEntity(e);
        return e;
    }

    public int countAll(ServerLevel level) {
        return level.getEntities(ModEntities.ADHERENT, e -> true).size();
    }

    /** Make every wall creature near a point react to a vibration or event. */
    public void react(ServerLevel level, Vec3 center, double radius, boolean flee, Vec3 stareAt) {
        for (AdherentEntity e : level.getEntitiesOfClass(AdherentEntity.class, new net.minecraft.world.phys.AABB(center, center).inflate(radius, 250, radius))) {
            if (e.mode() != AdherentEntity.Mode.WALL || e.role != AdherentEntity.Role.AMBIENT) continue;
            if (flee) e.beginFlee(60 + level.random.nextInt(80));
            else e.beginStare(stareAt, 80 + level.random.nextInt(160));
        }
    }
}
