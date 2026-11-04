package com.interstice.entity;

import com.interstice.config.ServerConfig;
import com.interstice.core.IntersticeServer;
import com.interstice.entity.ai.AdherentGroundGoal;
import com.interstice.entity.navigation.WallMovement;
import com.interstice.entity.species.Species;
import com.interstice.entity.species.Species.GroundDisposition;
import com.interstice.entity.species.Species.WallStyle;
import com.interstice.registry.ModEntities;
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
 * A creature of the Adherent family (the Adherent itself and the related species, see {@link Species}).
 * One class for the whole family: the species (fixed by the entity type) parametrises wall locomotion,
 * reactions, falls, the ground disposition, sounds and size.
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

    public enum Behavior { IDLE, CRAWL, RUN, FALL, RECOVER, SEARCH, INVESTIGATE, HUNT, STARE, FLEE, CLIMB, HIDE, STUNNED }

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
    /** Tests and commands: the disposition taken on landing instead of the species roll (null = roll). */
    @Nullable public GroundDisposition forceDisposition;
    private int attackCooldown;
    public long spawnTick;

    /** What a fallen creature does on the ground (rolled per individual from its species' weights). */
    public GroundDisposition disposition = GroundDisposition.HUNTER;
    private final Species species;
    // wall-plane velocity (y, z): species inertia
    private double velY, velZ;
    private int stutter;
    private boolean pausing, watchingPlayer;

    /** Opaque per-entity client animation state (owned by the client renderer). */
    public Object clientState;

    public AdherentEntity(EntityType<? extends Monster> type, Level level) {
        this(type, level, Species.ADHERENT);
    }

    public AdherentEntity(EntityType<? extends Monster> type, Level level, Species species) {
        super(type, level);
        this.species = species;
        this.xpReward = 0;
    }

    public Species species() {
        return species;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createAttributes(Species.ADHERENT);
    }

    public static AttributeSupplier.Builder createAttributes(Species sp) {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, sp.t.health)
                .add(Attributes.MOVEMENT_SPEED, sp.t.groundSpeed)
                .add(Attributes.ATTACK_DAMAGE, 7.0 * sp.t.damageMul)
                .add(Attributes.FOLLOW_RANGE, 48.0)
                .add(Attributes.STEP_HEIGHT, sp == Species.CRAWLER ? 1.0 : 1.1)
                .add(Attributes.KNOCKBACK_RESISTANCE, Math.min(1.0, 0.4 * Math.sqrt(sp.t.mass)));
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

    /** Starts a crawl on the wall towards (y, z) at the given speed. */
    public void beginCrawl(double y, double z, double speed) {
        if (mode() != Mode.WALL) return;
        setBehavior(Behavior.CRAWL);
        targetY = WallMovement.clampY(y);
        targetZ = z;
        moveSpeed = speed;
        behaviorTimer = 1200;
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
        Species.Traits t = species.t;
        Behavior b = behavior();
        ServerPlayer near = (ServerPlayer) level().getNearestPlayer(this, 9.0);
        if (near != null && !near.isSpectator() && role == Role.AMBIENT && (b == Behavior.IDLE || b == Behavior.CRAWL)) {
            if (random.nextFloat() < t.stareWhenStartled) beginStare(aimAt(near), 30 + random.nextInt(40) + (t.style == WallStyle.STILL ? 200 : 0));
            else beginFlee(60 + random.nextInt(60));
            b = behavior();
        }
        if (role == Role.AMBIENT && (tickCount + getId()) % 20 == 0) {
            social();
            b = behavior();
        }
        double y = getY(), z = getZ();
        switch (b) {
            case IDLE -> {
                if (--behaviorTimer <= 0) chooseWallActivity(s);
                if (random.nextInt(260) == 0) setLookTarget(position().add(-side() * 5, random.nextGaussian() * 6, random.nextGaussian() * 6));
                glide();
                y += velY;
                z += velZ;
            }
            case CRAWL, FLEE, CLIMB -> {
                double dy = targetY - y, dz = targetZ - z;
                double dist = Math.sqrt(dy * dy + dz * dz);
                boolean timeout = b != Behavior.CLIMB && --behaviorTimer <= 0;
                if (dist < 0.4 || timeout) {
                    if (b == Behavior.CLIMB) { discard(); return; }
                    setBehavior(Behavior.IDLE);
                    behaviorTimer = t.idleMin + random.nextInt(Math.max(1, t.idleMax - t.idleMin));
                } else {
                    double speed = Math.min(moveSpeed * pace(b), dist);
                    double wy = dy / dist * speed, wz = dz / dist * speed;
                    if (t.style == WallStyle.LURCH) {   // sideways drift of the asymmetric gait
                        double lat = 0.05 * Math.sin(tickCount * 0.23 + getId());
                        wy += -dz / dist * lat;
                        wz += dy / dist * lat;
                    }
                    double a = b == Behavior.FLEE ? Math.max(t.accel, 0.3) : t.accel;
                    velY += (wy - velY) * a;
                    velZ += (wz - velZ) * a;
                    y += velY;
                    z += velZ;
                    if (t.style == WallStyle.HEAVY) vibrate(s);
                }
                if (b == Behavior.CLIMB && y > IntersticeLayout.WALL_TOP - 6) { discard(); return; }
                if (b == Behavior.FLEE && y > IntersticeLayout.WALL_TOP - 6) { discard(); return; }
            }
            case RUN -> {
                z += runDirection * runSpeed;
                laneY += (random.nextDouble() - 0.5) * 0.08;
                y = laneY + Math.sin((tickCount + getId()) * 0.35) * 0.18;
                if (t.style == WallStyle.HEAVY) vibrate(s);
            }
            case STARE -> {
                if (t.style == WallStyle.WATCH && watchingPlayer && (tickCount + getId()) % 70 == 0) {
                    // re-aim late and imperfectly; sometimes past the player
                    ServerPlayer p = (ServerPlayer) level().getNearestPlayer(this, 48.0);
                    if (p != null) setLookTarget(random.nextFloat() < 0.25f ? beyond(p) : aimAt(p));
                }
                if (--behaviorTimer <= 0) {
                    setLookTarget(null);
                    watchingPlayer = false;
                    setBehavior(Behavior.IDLE);
                    behaviorTimer = 40 + random.nextInt(200);
                }
                glide();
                y += velY;
                z += velZ;
            }
            default -> setBehavior(Behavior.IDLE);
        }
        y = WallMovement.clampY(y);
        double tx = WallMovement.surfaceX(level(), s.layout(), side(), y, z, getBbWidth() / 2);
        if (Double.isNaN(tx)) {
            if (role == Role.RUNNER) { discard(); return; }
            // no surface there (an edge or an opening): stay put
            tx = getX();
            y = getY();
            z = getZ();
            velY = velZ = 0;
        }
        double x = WallMovement.approach(getX(), tx);
        setPos(x, y, z);
        if (b == Behavior.RUN || b == Behavior.FLEE) {
            int every = species == Species.BURDEN ? 10 : species == Species.CRAWLER ? 4 : 6;
            if (tickCount % every == 0) playSound(ModSounds.CREATURE_STEP, (b == Behavior.RUN ? 0.6f : 0.35f) * t.volume,
                    (0.8f + random.nextFloat() * 0.4f) * t.pitch);
        }
    }

    /** Slows the wall-plane velocity when not travelling (heavy species glide to a stop). */
    private void glide() {
        double k = 1 - Math.max(0.15, species.t.accel);
        velY *= k;
        velZ *= k;
        if (Math.abs(velY) < 1e-3) velY = 0;
        if (Math.abs(velZ) < 1e-3) velZ = 0;
    }

    /** Species rhythm multiplier on the travel speed this tick. */
    private double pace(Behavior b) {
        if (b == Behavior.FLEE) return 1.0;
        switch (species.t.style) {
            case DART -> {   // dashes and freezes; sometimes a sharp change of direction
                if (--stutter <= 0) {
                    pausing = !pausing;
                    stutter = pausing ? 4 + random.nextInt(10) : 8 + random.nextInt(14);
                    if (!pausing && random.nextFloat() < 0.3f) {
                        targetY = WallMovement.clampY(getY() + random.nextGaussian() * species.t.rangeY);
                        targetZ = getZ() + random.nextGaussian() * species.t.rangeZ;
                    }
                }
                return pausing ? 0.0 : 1.35;
            }
            case IRREGULAR -> {   // hesitations, uneven pace
                if (stutter > 0) {
                    stutter--;
                    return 0.0;
                }
                if (random.nextFloat() < 0.02f) stutter = 15 + random.nextInt(30);
                return 0.3 + 1.2 * (0.5 + 0.5 * Math.sin(tickCount * 0.31 + getId()) * Math.sin(tickCount * 0.077));
            }
            case LURCH -> {   // long-short rhythm of uneven limbs
                return (tickCount + getId()) % 18 < 12 ? 1.4 : 0.25;
            }
            default -> {
                return 1.0;
            }
        }
    }

    /** Burden: the structure answers its weight (sound, dust, a vibration others can hear). */
    private void vibrate(IntersticeServer s) {
        double v = Math.sqrt(velY * velY + velZ * velZ) + (behavior() == Behavior.RUN ? runSpeed : 0);
        if (v < 0.01 || (tickCount + getId()) % 22 != 0) return;
        playSound(ModSounds.CREATURE_STEP, 2.6f, 0.42f + random.nextFloat() * 0.08f);
        s.noise.emit(position(), 16, level().getGameTime(), this);
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(ModEntities.FALLING_DUST, getX() + side() * 0.4, getY() - 0.5, getZ(), 5, 0.8, 0.6, 0.8, 0.0);
        }
    }

    /** A look target near the player's eyes, off by a little: attention, not a laser. */
    private Vec3 aimAt(ServerPlayer p) {
        double err = species.t.style == WallStyle.WATCH ? 1.2 : 0.6;
        return p.getEyePosition().add(random.nextGaussian() * err, random.nextGaussian() * err * 0.5, random.nextGaussian() * err);
    }

    /** A point behind and beyond the player: something the player cannot see. */
    private Vec3 beyond(ServerPlayer p) {
        Vec3 away = p.position().subtract(position()).multiply(1, 0, 1);
        away = away.lengthSqr() < 1e-6 ? new Vec3(0, 0, 1) : away.normalize();
        return p.position().add(away.scale(40 + random.nextDouble() * 60)).add(0, 6 + random.nextDouble() * 30, 0);
    }

    /** Neighbour reactions on the same wall (bounded box query, every 20 ticks, staggered by id). */
    private void social() {
        Behavior b = behavior();
        if (b != Behavior.IDLE && b != Behavior.CRAWL) return;
        Species.Traits t = species.t;
        if (t.style == WallStyle.STILL) return;   // clingers do not make way for anything
        for (AdherentEntity o : level().getEntitiesOfClass(AdherentEntity.class, getBoundingBox().inflate(7),
                e -> e != this && e.mode() == Mode.WALL && e.side() == side())) {
            double dy = getY() - o.getY(), dz = getZ() - o.getZ();
            double d = Math.sqrt(dy * dy + dz * dz) + 1e-3;
            boolean oMoving = o.behavior() == Behavior.CRAWL || o.behavior() == Behavior.RUN || o.behavior() == Behavior.FLEE;
            if (o.species.t.mass > t.mass * 2.2 && d < 6) {
                // smaller species give a heavy one room
                setBehavior(Behavior.CRAWL);
                targetY = WallMovement.clampY(getY() + dy / d * 8);
                targetZ = getZ() + dz / d * 8;
                moveSpeed = t.crawlSpeedMax;
                behaviorTimer = 140;
                return;
            }
            if (t.style == WallStyle.IRREGULAR && oMoving && d < 5 && b == Behavior.IDLE) {
                // a Hollow is agitated by movement next to it; rarely it loses its grip
                setBehavior(Behavior.FLEE);
                targetY = WallMovement.clampY(getY() + dy / d * 4 + random.nextGaussian());
                targetZ = getZ() + dz / d * 4;
                moveSpeed = 0.18;
                behaviorTimer = 40;
                if (random.nextFloat() < 0.02f) startFalling();
                return;
            }
            if (t.style == WallStyle.DART && b == Behavior.CRAWL && o.species.t.mass > t.mass && d < 3.5) {
                // crawlers slip around larger bodies instead of through them
                targetZ += (dz >= 0 ? 1 : -1) * 3;
                targetY = WallMovement.clampY(targetY + (dy >= 0 ? 1 : -1) * 2);
            }
        }
    }

    private void chooseWallActivity(IntersticeServer s) {
        Species.Traits t = species.t;
        float r = random.nextFloat();
        if (t.style == WallStyle.WATCH && r < 0.45f) {
            ServerPlayer p = (ServerPlayer) level().getNearestPlayer(this, 48.0);
            setBehavior(Behavior.STARE);
            behaviorTimer = 200 + random.nextInt(600);
            if (p != null && !p.isSpectator() && random.nextFloat() < 0.6f) {
                watchingPlayer = true;
                setLookTarget(random.nextFloat() < 0.3f ? beyond(p) : aimAt(p));
            } else {
                watchingPlayer = false;
                setLookTarget(position().add(-side() * (10 + random.nextDouble() * 20), random.nextGaussian() * 25,
                        (random.nextBoolean() ? 1 : -1) * (60 + random.nextDouble() * 80)));
            }
            return;
        }
        if (r < t.crawlChance) {
            setBehavior(Behavior.CRAWL);
            double ry = t.style == WallStyle.LONG_STRIDE ? t.rangeY * 0.4 : t.rangeY;
            targetY = WallMovement.clampY(getY() + random.nextGaussian() * ry);
            targetZ = getZ() + random.nextGaussian() * t.rangeZ;
            moveSpeed = t.crawlSpeedMin + random.nextDouble() * (t.crawlSpeedMax - t.crawlSpeedMin);
            behaviorTimer = t.style == WallStyle.HEAVY ? 1400 : t.style == WallStyle.LONG_STRIDE ? 900 : 400;
        } else if (r < t.crawlChance + t.gatherChance) {
            // gather: drift towards a neighbour on the same wall, same species first
            AdherentEntity other = null;
            for (AdherentEntity e : level().getEntitiesOfClass(AdherentEntity.class, getBoundingBox().inflate(40), e -> e != this && e.mode() == Mode.WALL && e.side() == side())) {
                if (other == null || (e.species == species && other.species != species)) other = e;
                if (other.species == species) break;
            }
            if (other != null) {
                setBehavior(Behavior.CRAWL);
                targetY = other.getY() + random.nextGaussian() * 2.5;
                targetZ = other.getZ() + random.nextGaussian() * 2.5;
                moveSpeed = Math.max(t.crawlSpeedMin, 0.07);
                behaviorTimer = 600;
            } else {
                behaviorTimer = 100 + random.nextInt(300);
            }
        } else if (t.style == WallStyle.STILL && random.nextFloat() < 0.06f) {
            // a clinger's rare sudden jerk after minutes of nothing
            setBehavior(Behavior.CRAWL);
            targetY = WallMovement.clampY(getY() + random.nextGaussian() * 1.5);
            targetZ = getZ() + random.nextGaussian() * 2.0;
            moveSpeed = 0.32;
            velY = velZ = 0;
            behaviorTimer = 25;
        } else {
            behaviorTimer = t.idleMin + random.nextInt(Math.max(1, t.idleMax - t.idleMin)); // remain perfectly still
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
            if (species == Species.BURDEN) {   // the impact is felt
                s.noise.emit(position(), 40, level().getGameTime(), this);
                playSound(ModSounds.CREATURE_LAND, 5.0f, 0.45f);
                if (level() instanceof ServerLevel sl) sl.sendParticles(ModEntities.FALLING_DUST, getX(), getY() + 0.5, getZ(), 40, 2.0, 0.8, 2.0, 0.02);
            }
            float deathChance = (height > 40 ? 0.6f : height > 18 ? 0.35f : 0.1f) * species.t.deathMul;
            if (isInWater()) deathChance *= 0.3f;
            if (!forceSurvive && random.nextFloat() < deathChance) {
                hurt(damageSources().fall(), getMaxHealth() * 4);
                return;
            }
            setMode(Mode.GROUND);
            role = Role.SEARCHER;
            // forced survivors (the guaranteed searcher of a Stampede, tests) keep the full brain
            disposition = forceDisposition != null ? forceDisposition
                    : forceSurvive ? GroundDisposition.HUNTER : species.rollDisposition(random.nextFloat());
            if (disposition == GroundDisposition.STUNNED) {
                setBehavior(Behavior.STUNNED);
                behaviorTimer = (int) ((220 + random.nextInt(200)) * species.t.recoverMul);
            } else {
                setBehavior(Behavior.RECOVER);
                behaviorTimer = (int) ((70 + random.nextInt(60)) * species.t.recoverMul);
            }
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
        float dmg = ServerConfig.INSTANCE.creatureAttackDamage * species.t.damageMul;
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
            if (random.nextFloat() < 0.5f * (1 - species.t.holdOnChance)) startFalling(); else beginFlee(80);
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
        return species.t.ambientInterval;
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * species.t.pitch;
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
        playSound(ModSounds.CREATURE_STEP, 0.35f * species.t.volume, (0.9f + random.nextFloat() * 0.3f) * species.t.pitch);
    }

    @Override
    protected float getSoundVolume() {
        return 1.6f * species.t.volume;
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
