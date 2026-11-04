package com.interstice.entity;

import com.interstice.config.ServerConfig;
import com.interstice.core.IntersticeServer;
import com.interstice.entity.ai.AdherentGroundGoal;
import com.interstice.entity.navigation.WallMovement;
import com.interstice.registry.ModSounds;
import com.interstice.world.IntersticeLayout;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import org.jetbrains.annotations.Nullable;

/**
 * The Adherent: a long-limbed thing that lives on the walls.
 * <p>
 * Three locomotion modes:
 * <ul>
 *   <li>{@link Mode#WALL}: constrained wall locomotion ({@link WallMovement}); never hostile.</li>
 *   <li>{@link Mode#FALLING}: ballistic, gravity-driven, after losing its grip.</li>
 *   <li>{@link Mode#GROUND}: vanilla pathfinding driven by {@link AdherentGroundGoal}
 *       (perception, investigation, hunting).</li>
 * </ul>
 * Creatures are transient (never saved to disk); populations are managed by
 * {@link WallPopulation} and the Stampede controller.
 */
public class AdherentEntity extends Monster {
    public enum Mode { WALL, FALLING, GROUND }

    public enum Behavior { IDLE, CRAWL, RUN, FALL, RECOVER, SEARCH, INVESTIGATE, HUNT, STARE, FLEE, CLIMB }

    public enum Role { AMBIENT, RUNNER, SEARCHER }

    private static final EntityDataAccessor<Byte> DATA_MODE = SynchedEntityData.defineId(AdherentEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_BEHAVIOR = SynchedEntityData.defineId(AdherentEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_SIDE = SynchedEntityData.defineId(AdherentEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Vector3f> DATA_LOOK = SynchedEntityData.defineId(AdherentEntity.class, EntityDataSerializers.VECTOR3);

    // ---- server state
    public Role role = Role.AMBIENT;
    private int behaviorTimer;
    private double targetY, targetZ;
    private double moveSpeed;
    public int runDirection = 1;
    public double runSpeed;
    private double laneY;
    private double fallStartY;
    public boolean forceSurvive;
    private int attackCooldown;
    public long spawnTick;

    /** Opaque per-entity client animation state (owned by the client renderer). */
    public Object clientState;

    public AdherentEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        this.xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 34.0)
                .add(Attributes.MOVEMENT_SPEED, 0.22)
                .add(Attributes.ATTACK_DAMAGE, 7.0)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.STEP_HEIGHT, 1.1)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_MODE, (byte) Mode.WALL.ordinal());
        builder.define(DATA_BEHAVIOR, (byte) Behavior.IDLE.ordinal());
        builder.define(DATA_SIDE, (byte) -1);
        builder.define(DATA_LOOK, new Vector3f());
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new AdherentGroundGoal(this));
    }

    // ------------------------------------------------------------- accessors

    public Mode mode() { return Mode.values()[entityData.get(DATA_MODE)]; }

    public Behavior behavior() { return Behavior.values()[entityData.get(DATA_BEHAVIOR)]; }

    public int side() { return entityData.get(DATA_SIDE); }

    public Vector3f lookTarget() { return entityData.get(DATA_LOOK); }

    public void setMode(Mode m) {
        entityData.set(DATA_MODE, (byte) m.ordinal());
        boolean wall = m == Mode.WALL;
        this.noPhysics = wall;
        this.setNoGravity(wall);
    }

    public void setBehavior(Behavior b) { entityData.set(DATA_BEHAVIOR, (byte) b.ordinal()); }

    public void setSide(int side) { entityData.set(DATA_SIDE, (byte) (side < 0 ? -1 : 1)); }

    public void setLookTarget(@Nullable Vec3 v) {
        entityData.set(DATA_LOOK, v == null ? new Vector3f() : new Vector3f((float) v.x, (float) v.y, (float) v.z));
    }

    public void setBehaviorTimer(int t) { this.behaviorTimer = t; }

    // ------------------------------------------------------------ placement

    /** Attach to a wall at the given height/position. Returns false if no surface was found. */
    public boolean attachToWall(int side, double y, double z) {
        IntersticeServer s = IntersticeServer.get();
        if (s == null || s.layout() == null) return false;
        double x = WallMovement.surfaceX(level(), s.layout(), side, y, z, getBbWidth() / 2);
        if (Double.isNaN(x)) return false;
        setSide(side);
        setMode(Mode.WALL);
        setPos(x, y, z);
        targetY = y;
        targetZ = z;
        laneY = y;
        return true;
    }

    public void beginRun(int direction, double speed) {
        role = Role.RUNNER;
        runDirection = direction;
        runSpeed = speed;
        laneY = getY();
        setBehavior(Behavior.RUN);
    }

    public void beginStare(Vec3 at, int ticks) {
        if (mode() != Mode.WALL) return;
        setLookTarget(at);
        setBehavior(Behavior.STARE);
        behaviorTimer = ticks;
    }

    public void beginFlee(int ticks) {
        if (mode() != Mode.WALL) return;
        setBehavior(Behavior.FLEE);
        behaviorTimer = ticks;
        targetY = WallMovement.clampY(getY() + 30 + random.nextInt(40));
        targetZ = getZ() + (random.nextBoolean() ? 1 : -1) * (10 + random.nextInt(20));
        moveSpeed = 0.42 + random.nextDouble() * 0.15;
    }

    public void beginClimbAway() {
        if (mode() != Mode.WALL) return;
        setBehavior(Behavior.CLIMB);
        targetY = IntersticeLayout.WALL_TOP;
        targetZ = getZ() + (random.nextBoolean() ? 1 : -1) * 20;
        moveSpeed = 0.25;
    }

    public void startFalling() {
        if (mode() != Mode.WALL) return;
        setMode(Mode.FALLING);
        setBehavior(Behavior.FALL);
        fallStartY = getY();
        double along = role == Role.RUNNER ? runDirection * runSpeed * 0.55 : 0;
        setDeltaMovement(-side() * (0.25 + random.nextDouble() * 0.2), 0.12, along);
        playSound(ModSounds.CREATURE_SCREAM, 3.5f, 0.85f + random.nextFloat() * 0.3f);
    }

    // ------------------------------------------------------------------ tick

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && mode() == Mode.WALL) {
            setDeltaMovement(Vec3.ZERO);
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (attackCooldown > 0) attackCooldown--;
        IntersticeServer s = IntersticeServer.get();
        if (s == null || s.layout() == null) return;
        switch (mode()) {
            case WALL -> wallStep(s);
            case FALLING -> fallStep(s);
            case GROUND -> {
            }
        }
    }

    private void wallStep(IntersticeServer s) {
        Behavior b = behavior();
        ServerPlayer near = (ServerPlayer) level().getNearestPlayer(this, 9.0);
        if (near != null && !near.isSpectator() && role == Role.AMBIENT && (b == Behavior.IDLE || b == Behavior.CRAWL)) {
            if (random.nextFloat() < 0.3f) beginStare(near.getEyePosition(), 30 + random.nextInt(40));
            else beginFlee(60 + random.nextInt(60));
            b = behavior();
        }
        double y = getY(), z = getZ();
        switch (b) {
            case IDLE -> {
                if (--behaviorTimer <= 0) chooseWallActivity(s);
                if (random.nextInt(260) == 0) setLookTarget(position().add(-side() * 5, random.nextGaussian() * 6, random.nextGaussian() * 6));
            }
            case CRAWL, FLEE, CLIMB -> {
                double dy = targetY - y, dz = targetZ - z;
                double dist = Math.sqrt(dy * dy + dz * dz);
                boolean timeout = b != Behavior.CLIMB && --behaviorTimer <= 0;
                if (dist < 0.4 || timeout) {
                    if (b == Behavior.CLIMB) { discard(); return; }
                    setBehavior(Behavior.IDLE);
                    behaviorTimer = 80 + random.nextInt(500);
                } else {
                    double step = Math.min(moveSpeed, dist);
                    y += dy / dist * step;
                    z += dz / dist * step;
                }
                if (b == Behavior.CLIMB && y > IntersticeLayout.WALL_TOP - 6) { discard(); return; }
                if (b == Behavior.FLEE && y > IntersticeLayout.WALL_TOP - 6) { discard(); return; }
            }
            case RUN -> {
                z += runDirection * runSpeed;
                laneY += (random.nextDouble() - 0.5) * 0.08;
                y = laneY + Math.sin((tickCount + getId()) * 0.35) * 0.18;
            }
            case STARE -> {
                if (--behaviorTimer <= 0) {
                    setLookTarget(null);
                    setBehavior(Behavior.IDLE);
                    behaviorTimer = 40 + random.nextInt(200);
                }
            }
            default -> setBehavior(Behavior.IDLE);
        }
        y = WallMovement.clampY(y);
        double tx = WallMovement.surfaceX(level(), s.layout(), side(), y, z, getBbWidth() / 2);
        if (Double.isNaN(tx)) {
            if (role == Role.RUNNER) { discard(); return; }
            tx = getX();
        }
        double x = WallMovement.approach(getX(), tx);
        setPos(x, y, z);
        if (b == Behavior.RUN || b == Behavior.FLEE) {
            if (tickCount % 6 == 0) playSound(ModSounds.CREATURE_STEP, b == Behavior.RUN ? 0.6f : 0.35f, 0.8f + random.nextFloat() * 0.4f);
        }
    }

    private void chooseWallActivity(IntersticeServer s) {
        float r = random.nextFloat();
        if (r < 0.55f) {
            setBehavior(Behavior.CRAWL);
            targetY = WallMovement.clampY(getY() + random.nextGaussian() * 10);
            targetZ = getZ() + random.nextGaussian() * 16;
            moveSpeed = 0.05 + random.nextDouble() * 0.08;
            behaviorTimer = 400;
        } else if (r < 0.75f) {
            // gather: drift towards a neighbour on the same wall
            AdherentEntity other = null;
            for (AdherentEntity e : level().getEntitiesOfClass(AdherentEntity.class, getBoundingBox().inflate(40), e -> e != this && e.mode() == Mode.WALL && e.side() == side())) {
                other = e;
                break;
            }
            if (other != null) {
                setBehavior(Behavior.CRAWL);
                targetY = other.getY() + random.nextGaussian() * 2.5;
                targetZ = other.getZ() + random.nextGaussian() * 2.5;
                moveSpeed = 0.07;
                behaviorTimer = 600;
            } else {
                behaviorTimer = 100 + random.nextInt(300);
            }
        } else {
            behaviorTimer = 120 + random.nextInt(600); // remain perfectly still
        }
    }

    private void fallStep(IntersticeServer s) {
        if (onGround() || isInWater()) {
            double height = fallStartY - getY();
            s.noise.emit(position(), 22, level().getGameTime(), this);
            playSound(ModSounds.CREATURE_LAND, 3.0f, 0.7f + random.nextFloat() * 0.2f);
            // crush anyone underneath
            for (ServerPlayer p : ((ServerLevel) level()).getPlayers(p -> p.distanceToSqr(this) < 2.5 && !p.isSpectator())) {
                p.hurt(damageSources().fallingBlock(this), 6f);
            }
            float deathChance = height > 40 ? 0.6f : height > 18 ? 0.35f : 0.1f;
            if (isInWater()) deathChance *= 0.3f;
            if (!forceSurvive && random.nextFloat() < deathChance) {
                hurt(damageSources().fall(), getMaxHealth() * 4);
                return;
            }
            setMode(Mode.GROUND);
            role = Role.SEARCHER;
            setBehavior(Behavior.RECOVER);
            behaviorTimer = 70 + random.nextInt(60);
            hurt(damageSources().fall(), Math.min(getHealth() - 4, (float) height * 0.35f));
        } else if (getY() < IntersticeLayout.PIT_BOTTOM - 5) {
            discard();
        }
    }

    public int behaviorTimer() { return behaviorTimer; }

    public int decrementBehaviorTimer() { return --behaviorTimer; }

    // --------------------------------------------------------------- combat

    public boolean tryAttack(ServerPlayer target) {
        if (attackCooldown > 0) return false;
        if (distanceToSqr(target) > 5.3 || !hasLineOfSight(target)) return false;
        attackCooldown = 24;
        swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        float dmg = ServerConfig.INSTANCE.creatureAttackDamage;
        return target.hurt(damageSources().mobAttack(this), dmg);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false; // falls are resolved explicitly in fallStep
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean r = super.hurt(source, amount);
        if (r && !level().isClientSide && mode() == Mode.WALL && isAlive()) {
            if (random.nextFloat() < 0.5f) startFalling(); else beginFlee(80);
        }
        return r;
    }

    // -------------------------------------------------------------- misc

    @Override
    public boolean shouldBeSaved() {
        return false; // transient population; managed at runtime
    }

    @Override
    public boolean removeWhenFarAway(double d) {
        return d > 140 * 140;
    }

    @Override
    public boolean isPushable() {
        return mode() != Mode.WALL;
    }

    @Override
    protected void doPush(net.minecraft.world.entity.Entity entity) {
        if (mode() != Mode.WALL) super.doPush(entity);
    }

    @Override
    public boolean onClimbable() {
        return false;
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.CREATURE_IDLE;
    }

    @Override
    public int getAmbientSoundInterval() {
        return 220;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return ModSounds.CREATURE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.CREATURE_DEATH;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(ModSounds.CREATURE_STEP, 0.35f, 0.9f + random.nextFloat() * 0.3f);
    }

    @Override
    protected float getSoundVolume() {
        return 1.6f;
    }

    @Override
    public SoundSource getSoundSource() {
        return SoundSource.HOSTILE;
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, @Nullable SpawnGroupData data) {
        spawnTick = level.getLevel().getGameTime();
        return super.finalizeSpawn(level, difficulty, reason, data);
    }

    public float headTwitchSeed() {
        return Mth.frac(getId() * 0.618034f);
    }
}
