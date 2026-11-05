package com.interstice.entity;

import com.interstice.core.IntersticeServer;
import com.interstice.registry.ModItems;
import com.interstice.registry.ModWorld;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A thrown rattle lure. It flies like a snowball; where it lands it stays and rattles: a loud noise every
 * 15 ticks for {@link #RATTLE_TICKS} ticks (the creatures' {@code NoiseMap}), then falls silent and disappears.
 */
public class ThrownLure extends ThrowableItemProjectile {
    public static final int RATTLE_TICKS = 200;
    public static final float LOUDNESS = 30f;

    private int rattle = -1;
    private Vec3 restAt;

    public ThrownLure(EntityType<? extends ThrownLure> type, Level level) {
        super(type, level);
    }

    public ThrownLure(Level level, LivingEntity owner) {
        super(com.interstice.registry.ModEntities.LURE, owner, level);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.RATTLE_LURE;
    }

    @Override
    protected void onHit(HitResult hit) {
        if (rattle >= 0) return;
        super.onHit(hit);
        rattle = RATTLE_TICKS;
        restAt = hit.getLocation();
        setDeltaMovement(Vec3.ZERO);
        setNoGravity(true);
        setPos(restAt);
    }

    @Override
    public void tick() {
        if (rattle < 0) {
            super.tick();
            if (tickCount > 200) discard();
            return;
        }
        setDeltaMovement(Vec3.ZERO);
        if (restAt != null) setPos(restAt);
        baseTick();
        if (level().isClientSide) return;
        if (rattle % 15 == 0) {
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.CHAIN_FALL, SoundSource.PLAYERS, 1.4f, 1.3f + random.nextFloat() * 0.4f);
            if (ModWorld.isInterstice(level())) {
                IntersticeServer s = IntersticeServer.get();
                // the source is the lure (null player): creatures go and look here, not at the thrower
                if (s != null) s.noise.emit(position(), LOUDNESS, level().getGameTime(), this);
            }
        }
        if (--rattle <= 0) discard();
    }

    public boolean isRattling() {
        return rattle > 0;
    }
}
