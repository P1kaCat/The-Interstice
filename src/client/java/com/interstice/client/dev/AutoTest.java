package com.interstice.client.dev;

import com.interstice.Interstice;
import com.interstice.client.ClientState;
import com.interstice.client.config.ClientConfig;
import com.interstice.core.IntersticeServer;
import com.interstice.entity.AdherentEntity;
import com.interstice.entity.species.Species;
import com.interstice.event.stampede.StampedePhase;
import com.interstice.world.IntersticeTeleport;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * DEVELOPMENT ONLY. Scripted visual smoke test, enabled only when the file
 * {@code interstice_autotest.flag} exists in the game directory (never in a
 * normal install). In a singleplayer world it enters the dimension, stages a
 * sequence of events, captures screenshots into {@code screenshots/} and quits.
 */
public final class AutoTest {
    private static Boolean enabled;
    private static int ticks;
    private static int step;
    private static int stepStart;

    private AutoTest() {}

    private static boolean enabled(Minecraft mc) {
        if (enabled == null) enabled = Files.exists(mc.gameDirectory.toPath().resolve("interstice_autotest.flag"));
        return enabled;
    }

    private static void server(Minecraft mc, Consumer<MinecraftServer> action) {
        MinecraftServer s = mc.getSingleplayerServer();
        if (s != null) s.execute(() -> action.accept(s));
    }

    private static void shot(Minecraft mc, String name) {
        if (!name.startsWith("seq_") || name.endsWith("_000")) {
            int client = 0;
            String where = "";
            if (mc.level != null) for (var e : mc.level.entitiesForRendering()) if (e instanceof AdherentEntity a) { client++; where = a.position() + " " + a.mode() + " " + a.behavior(); }
            Interstice.LOG.info("[autotest] {}: subject={} alive={} pos={} | client adherents={} {} | fps {} | mesh skinning {} ms/s, {} vertices/s", name,
                    subject != null, subject != null && subject.isAlive(), subject != null ? subject.position() : null, client, where, mc.getFps(),
                    String.format("%.2f", com.interstice.client.render.creatures.mesh.SkinnedMeshPose.nanosLastSecond / 1e6),
                    com.interstice.client.render.creatures.mesh.SkinnedMeshPose.verticesLastSecond);
        }
        Screenshot.grab(mc.gameDirectory, "interstice_" + name + ".png", mc.getMainRenderTarget(), msg -> Interstice.LOG.info("[autotest] {}", msg.getString()));
    }

    private static boolean after(int t) {
        return ticks - stepStart >= t;
    }

    private static void next() {
        step++;
        stepStart = ticks + 1;
        Interstice.LOG.info("[autotest] step {}", step);
    }

    private static Boolean modelMode;
    /** Flag content "model bench": only the benchmarks, with vsync off and no frame cap (restored at the end). */
    private static boolean benchOnly;
    /** Flag content "model turn": only the per-species turn-in-place sequences. */
    private static boolean turnOnly;
    private static boolean savedVsync;
    private static int savedFps;
    private static AdherentEntity subject;

    private static boolean modelMode(Minecraft mc) {
        if (modelMode == null) {
            try {
                String flag = Files.readString(mc.gameDirectory.toPath().resolve("interstice_autotest.flag"));
                modelMode = flag.contains("model");
                benchOnly = flag.contains("bench");
                turnOnly = flag.contains("turn");
            } catch (Exception e) {
                modelMode = false;
            }
        }
        return modelMode;
    }

    /**
     * Creature family validation sequence (flag file content "model"). A daylit superflat overworld with
     * an armor stand (about 2 blocks, player size) as the scale reference:
     * <ol>
     *   <li>family line-up (profile, three-quarter);</li>
     *   <li>per species: front, profile, three-quarter and rear stills; tracked walk and run sequences;
     *       fall then recovery;</li>
     *   <li>in the Interstice, per species: clinging to the real wall, then climbing (night vision on, for
     *       readability); then the whole family in the dimension's own light and fog (no night vision);</li>
     *   <li>benchmarks: 40 real creatures with AI around the player; then a Stampede horde passage.</li>
     * </ol>
     * Every capture logs the scene state; benchmarks log FPS, skinning cost and server tick time.
     */
    private static int frame;
    private static double groundY;
    private static int si, phase;
    private static final java.util.List<AdherentEntity> extras = new java.util.ArrayList<>();
    /** Rest body lengths in blocks (models/<species>_validation.txt), to frame each species. */
    private static final float[] LENGTH = {3.27f, 2.08f, 4.47f, 4.07f, 3.65f, 3.53f, 3.41f, 3.89f};
    private static final float[] WALK = {0.045f, 0.06f, 0.08f, 0.035f, 0.03f, 0.04f, 0.04f, 0.045f};
    private static final float[] CLIMB = {0.034f, 0.05f, 0.05f, 0.022f, 0.02f, 0.03f, 0.03f, 0.034f};

    private static Species species() {
        return Species.values()[si];
    }

    private static double dist(Species sp) {
        return 2.5 + 1.25 * Math.max(LENGTH[sp.ordinal()], 1.6 * sp.t.height);
    }

    private static ServerPlayer sp(MinecraftServer s) {
        return s.getPlayerList().getPlayers().get(0);
    }

    private static void posture(AdherentEntity e, float yaw, AdherentEntity.Behavior b) {
        e.setYRot(yaw);
        e.yBodyRot = yaw;
        e.yBodyRotO = yaw;
        e.setYHeadRot(yaw);
        if (e.behavior() != b) e.setBehavior(b);
    }

    private static void seq(Minecraft mc, String name, int t, int every) {
        if (t % every == 0) shot(mc, "seq_" + name + "_" + String.format("%03d", frame++));
    }

    private static void nextPhase() {
        phase++;
        stepStart = ticks + 1;
    }

    private static void clearExtras() {
        for (AdherentEntity e : extras) e.discard();
        extras.clear();
    }

    private static void bench(Minecraft mc, String label) {
        MinecraftServer s = mc.getSingleplayerServer();
        java.util.Map<String, Integer> bySpecies = new java.util.TreeMap<>();
        int client = 0;
        for (var e : mc.level.entitiesForRendering()) {
            if (e instanceof AdherentEntity a) {
                client++;
                bySpecies.merge(a.species().id, 1, Integer::sum);
            }
        }
        Interstice.LOG.info("[autotest] BENCH {}: preset {} vsync {} cap {} | fps {} | client creatures {} {} | skinning {} ms/s, {} vertices/s | horde simulated {} far {} mid {} | server tick {} ms",
                label, ClientConfig.INSTANCE.preset, mc.options.enableVsync().get(), mc.options.framerateLimit().get(), mc.getFps(), client, bySpecies,
                String.format("%.2f", com.interstice.client.render.creatures.mesh.SkinnedMeshPose.nanosLastSecond / 1e6),
                com.interstice.client.render.creatures.mesh.SkinnedMeshPose.verticesLastSecond,
                com.interstice.client.render.creatures.HordeRenderer.simulated, com.interstice.client.render.creatures.HordeRenderer.farDrawn,
                com.interstice.client.render.creatures.HordeRenderer.midDrawn,
                s == null ? "?" : String.format("%.2f", s.getAverageTickTimeNanos() / 1e6));
    }

    private static void modelTick(Minecraft mc) {
        var p = mc.player;
        mc.options.hideGui = true;
        int t = ticks - stepStart;
        Species sp = species();
        if (benchOnly && (step == 1 || step == 2 || step == 4 || step == 5)) {
            if (step == 1) {
                savedVsync = mc.options.enableVsync().get();
                savedFps = mc.options.framerateLimit().get();
                mc.options.enableVsync().set(false);
                mc.getWindow().updateVsync(false);
                mc.options.framerateLimit().set(260);   // 260 = unlimited
                mc.getWindow().setFramerateLimit(260);
            }
            if (step == 5) server(mc, s -> sp(s).setGameMode(net.minecraft.world.level.GameType.CREATIVE));
            next();
            return;
        }
        if (turnOnly && (step == 1 || (step >= 3 && step < 8))) {   // "model turn": only the turn sequences
            if (step >= 3) step = 7;
            next();
            return;
        }
        switch (step) {
            case 0 -> {   // overworld stage
                if (after(60)) {
                    server(mc, s -> {
                        var level = s.overworld();
                        level.setDayTime(6000);
                        s.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false, s);
                        s.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_WEATHER_CYCLE).set(false, s);
                        level.setWeatherParameters(6000, 0, false, false);
                        ServerPlayer pl = sp(s);
                        // the save may have been left in the Interstice: use the overworld ground, not the player's y
                        groundY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, 0, 6);
                        pl.removeEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION);
                        pl.teleportTo(level, 0.5, groundY, 0.5, 0f, 10f);
                        var stand = net.minecraft.world.entity.EntityType.ARMOR_STAND.create(level);
                        if (stand != null) {
                            stand.moveTo(-3.2, groundY, 8.0, 180f, 0f);
                            level.addFreshEntity(stand);
                        }
                    });
                    next();
                }
            }
            case 1 -> {   // family line-up
                if (t == 0) server(mc, s -> {
                    var level = s.overworld();
                    Species[] all = Species.values();
                    for (int i = 0; i < all.length; i++) {
                        AdherentEntity e = IntersticeServer.get().population.createGround(level, all[i], new Vec3((i - 3.5) * 4.9 + 0.5, groundY, 17.0));
                        if (e != null) {
                            e.setNoAi(true);
                            posture(e, 90f, AdherentEntity.Behavior.IDLE);
                            extras.add(e);
                        }
                    }
                    var stand = net.minecraft.world.entity.EntityType.ARMOR_STAND.create(level);
                    if (stand != null) {
                        stand.moveTo(-21.0, groundY, 17.0, 180f, 0f);
                        level.addFreshEntity(stand);
                    }
                });
                place(mc, 0.5, -6.0, 0f, 8f);
                if (t == 60) shot(mc, "family_lineup_profile");
                if (t == 62) server(mc, s -> extras.forEach(e -> posture(e, 145f, AdherentEntity.Behavior.IDLE)));
                if (t == 100) shot(mc, "family_lineup_three_quarter");
                if (t == 102) {
                    server(mc, s -> clearExtras());
                    si = 0;
                    phase = 0;
                    next();
                }
            }
            case 2 -> overworldSpecies(mc, sp, t);
            case 3 -> {   // into the Interstice
                if (t == 0) server(mc, s -> {
                    IntersticeServer.get().director.setPaused(true);
                    if (subject != null) subject.discard();
                    IntersticeTeleport.enter(sp(s));
                });
                if (t == 160) server(mc, s -> {
                    IntersticeServer is = IntersticeServer.get();
                    ServerPlayer pl = sp(s);
                    int side = is.layout().sideOf(pl.getX(), pl.getZ());
                    double faceX = is.layout().faceX(side, pl.getZ());
                    pl.teleportTo(is.level(), faceX - side * 9, pl.getY(), pl.getZ(), side < 0 ? 90f : -90f, -24f);
                    pl.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION, 20 * 600, 0, false, false));
                });
                if (t > 200) {
                    si = 0;
                    phase = 0;
                    next();
                }
            }
            case 4 -> wallSpecies(mc, sp, t);
            case 5 -> {   // the family in the dimension's own light and fog
                if (t == 0) server(mc, s -> {
                    IntersticeServer is = IntersticeServer.get();
                    ServerPlayer pl = sp(s);
                    pl.removeEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION);
                    pl.setGameMode(net.minecraft.world.level.GameType.CREATIVE);   // the benchmark creatures must not end the run
                    Vec3 spot = IntersticeTeleport.findSafeSpot(is.level(), is.layout(), 0);
                    pl.teleportTo(is.level(), spot.x, spot.y, spot.z, 0f, 6f);
                    Species[] all = Species.values();
                    for (int i = 0; i < all.length; i++) {
                        double a = Math.toRadians(-60 + i * 120.0 / (all.length - 1));
                        double r = 9 + (i % 2) * 4;
                        AdherentEntity e = is.population.createGround(is.level(), all[i], spot.add(Math.sin(a) * r, 0, Math.cos(a) * r));
                        if (e != null) {
                            e.setNoAi(true);
                            posture(e, 180f + (float) Math.toDegrees(a) * 0.5f, AdherentEntity.Behavior.IDLE);
                            extras.add(e);
                        }
                    }
                });
                if (t == 120) shot(mc, "family_interstice");
                if (t == 122) {
                    server(mc, s -> clearExtras());
                    next();
                }
            }
            case 6 -> {   // benchmark: 40 real creatures with AI around the player
                if (t == 0) server(mc, s -> {
                    IntersticeServer is = IntersticeServer.get();
                    ServerPlayer pl = sp(s);
                    var r = pl.getRandom();
                    for (Species x : Species.values()) {
                        for (int k = 0; k < 5; k++) {
                            AdherentEntity e;
                            if (k < 3) {
                                int side = r.nextBoolean() ? -1 : 1;
                                e = is.population.create(is.level(), x, side, pl.getY() + 2 + r.nextDouble() * 18, pl.getZ() + 8 + r.nextDouble() * 30);
                            } else {
                                e = is.population.createGround(is.level(), x, pl.position().add(r.nextGaussian() * 6, 0, 10 + r.nextDouble() * 18));
                            }
                            if (e != null) extras.add(e);
                        }
                    }
                    Interstice.LOG.info("[autotest] bench: spawned {} real creatures", extras.size());
                });
                if (t == 1) p.setXRot(-8f);
                if (t == 160) {
                    bench(mc, "40 real creatures (AI on), in view");
                    shot(mc, "bench_real_creatures");
                }
                if (t == 162) {
                    server(mc, s -> clearExtras());
                    next();
                }
            }
            case 7 -> {   // benchmark: Stampede horde passage
                if (t == 0) server(mc, s -> {
                    IntersticeServer is = IntersticeServer.get();
                    is.stampede.forcePhase(is.level(), StampedePhase.HORDE_PASSAGE);
                    Interstice.LOG.info("[autotest] bench horde composition: {}", is.stampede.mix());
                });
                if (t == 1) {
                    p.setYRot(90f);
                    p.setXRot(-28f);
                }
                if (t == 220) {
                    bench(mc, "Stampede horde passage");
                    shot(mc, "bench_horde");
                }
                if (t == 300) {
                    bench(mc, "Stampede horde passage +4 s");
                    shot(mc, "bench_horde_2");
                }
                if (t == 302) {
                    server(mc, s -> IntersticeServer.get().stampede.stop(IntersticeServer.get().level()));
                    next();
                }
            }
            case 8 -> {
                if (benchOnly && t == 0) {
                    mc.options.enableVsync().set(savedVsync);
                    mc.getWindow().updateVsync(savedVsync);
                    mc.options.framerateLimit().set(savedFps);
                    mc.getWindow().setFramerateLimit(savedFps);
                }
                if (after(30)) {
                    try { Files.deleteIfExists(Path.of(mc.gameDirectory.getPath(), "interstice_autotest.flag")); } catch (Exception ignored) {}
                    mc.stop();
                }
            }
            default -> {
            }
        }
    }

    /** Overworld captures of one species, then the next species (or the next step after the last one). */
    private static void overworldSpecies(Minecraft mc, Species sp, int t) {
        String id = sp.id;
        double d = dist(sp);
        switch (phase) {
            case 0, 1, 2, 3 -> {
                float[] yaws = {180f, 90f, 140f, 0f};
                String[] views = {"front", "profile", "three_quarter", "rear"};
                final int ph = phase;
                if (t == 0) server(mc, s -> {
                    if (ph == 0) {
                        if (subject != null) subject.discard();
                        subject = IntersticeServer.get().population.createGround(s.overworld(), sp, new Vec3(0.5, groundY, 8.0));
                        if (subject != null) subject.setNoAi(true);
                    }
                    if (subject != null) {
                        subject.setPos(0.5, groundY, 8.0);
                        posture(subject, yaws[ph], AdherentEntity.Behavior.IDLE);
                    }
                });
                place(mc, 0.5, 8.0 - d, 0f, 8f);
                if (turnOnly && t == 2) {
                    phase = 7;
                    stepStart = ticks + 1;
                } else if (t == 28) {
                    shot(mc, id + "_" + views[phase]);
                    nextPhase();
                }
            }
            case 4 -> {   // walk, camera beside it
                if (t == 0) frame = 0;
                stepMove(mc, 90f, AdherentEntity.Behavior.CRAWL, WALK[sp.ordinal()], t == 0, d);
                if (t >= 8) seq(mc, id + "_walk", t - 8, 2);
                if (t >= 8 + 44) nextPhase();
            }
            case 5 -> {   // run
                if (t == 0) frame = 0;
                stepMove(mc, 90f, AdherentEntity.Behavior.RUN, 0.2 * sp.t.runSpeedMul, t == 0, d);
                if (t >= 6) seq(mc, id + "_run", t - 6, 1);
                if (t >= 6 + 18) nextPhase();
            }
            case 6 -> {   // fall then recovery
                if (t == 0) {
                    frame = 0;
                    server(mc, s -> {
                        if (subject == null) return;
                        subject.setPos(0.5, groundY, 8.0);
                        posture(subject, 140f, AdherentEntity.Behavior.FALL);
                    });
                }
                if (t == 24) server(mc, s -> { if (subject != null) posture(subject, 140f, AdherentEntity.Behavior.RECOVER); });
                place(mc, 0.5, 8.0 - d, 0f, 10f);
                if (t >= 4) seq(mc, id + "_fall_recover", t - 4, 4);
                if (t >= 4 + 4 * 36) nextPhase();
            }
            case 7 -> {   // turning in place: 90 degrees one way, 60 back, camera at three-quarter
                if (t == 0) frame = 0;
                final int tt = t;
                server(mc, s -> {
                    if (subject == null) return;
                    subject.setPos(0.5, groundY, 8.0);
                    float yaw = 140f + (tt < 70 ? Math.max(0, tt - 10) * 1.5f : 90f - (tt - 70) * 1.5f);
                    subject.setYRot(yaw);
                    subject.setYHeadRot(yaw);
                    if (subject.behavior() != AdherentEntity.Behavior.IDLE) subject.setBehavior(AdherentEntity.Behavior.IDLE);
                });
                place(mc, 0.5 - d * 0.55, 8.0 - d * 0.75, -36f, 12f);
                if (t >= 6) seq(mc, id + "_turn", t - 6, 3);
                if (t >= 6 + 3 * 36) nextPhase();
            }
            default -> {
                server(mc, s -> {
                    if (subject != null) subject.discard();
                    subject = null;
                });
                phase = 0;
                stepStart = ticks + 1;
                if (++si >= Species.values().length) {
                    si = 0;
                    next();
                }
            }
        }
    }

    /** Wall captures of one species in the Interstice (cling still, then a climb sequence). */
    private static void wallSpecies(Minecraft mc, Species sp, int t) {
        String id = sp.id;
        switch (phase) {
            case 0 -> {
                if (t == 0) server(mc, s -> {
                    IntersticeServer is = IntersticeServer.get();
                    ServerPlayer pl = sp(s);
                    int side = is.layout().sideOf(pl.getX(), pl.getZ());
                    subject = is.population.create(is.level(), sp, side, pl.getY() + 4.0 + sp.t.height * 0.5, pl.getZ() - 1.5);
                    if (subject != null) {
                        subject.setNoAi(true);
                        subject.setBehavior(AdherentEntity.Behavior.IDLE);
                    } else {
                        Interstice.LOG.warn("[autotest] no wall surface for {}", sp.id);
                    }
                });
                if (t == 40) {
                    shot(mc, id + "_wall");
                    nextPhase();
                }
            }
            case 1 -> {
                if (t == 0) frame = 0;
                server(mc, s -> {
                    if (subject == null) return;
                    if (subject.behavior() != AdherentEntity.Behavior.CRAWL) subject.setBehavior(AdherentEntity.Behavior.CRAWL);
                    subject.setPos(subject.getX(), subject.getY() + CLIMB[sp.ordinal()], subject.getZ() + 0.008);
                });
                if (t >= 8) seq(mc, id + "_climb", t - 8, 2);
                if (t >= 8 + 40) nextPhase();
            }
            default -> {
                server(mc, s -> {
                    if (subject != null) subject.discard();
                    subject = null;
                });
                phase = 0;
                stepStart = ticks + 1;
                if (++si >= Species.values().length) {
                    si = 0;
                    next();
                }
            }
        }
    }

    /** Moves the AI-less subject along its facing so the animator sees real travel; the camera follows. */
    private static void stepMove(Minecraft mc, float yaw, AdherentEntity.Behavior behavior, double speed, boolean reset, double distance) {
        server(mc, s -> {
            if (subject == null) return;
            double r = Math.toRadians(yaw);
            if (reset) subject.setPos(0.5 + Math.sin(r) * 4.5, groundY, 8.0);
            posture(subject, yaw, behavior);
            subject.setPos(subject.getX() - Math.sin(r) * speed, subject.getY(), subject.getZ() + Math.cos(r) * speed);
        });
        track(mc, distance);
        mc.player.setYRot(0);
        mc.player.setXRot(10);
    }

    /** Keeps the camera beside the subject (same interpolation as the subject, so it stays framed). */
    private static void track(Minecraft mc, double distance) {
        if (subject == null || mc.level == null) return;
        var e = mc.level.getEntity(subject.getId());
        if (e == null) return;
        var p = mc.player;
        p.setPos(e.getX(), p.getY(), e.getZ() - distance);
        p.xo = p.xOld = e.xo;
        p.zo = p.zOld = e.zo - distance;
        p.setDeltaMovement(Vec3.ZERO);
    }

    /** Puts the camera at (x, z) on the overworld ground, looking along yaw/pitch. */
    private static void place(Minecraft mc, double x, double z, float yaw, float pitch) {
        var p = mc.player;
        p.setPos(x, p.getY(), z);
        p.xo = p.xOld = x;
        p.zo = p.zOld = z;
        p.setDeltaMovement(Vec3.ZERO);
        p.setYRot(yaw);
        p.setXRot(pitch);
    }

    public static void tick(Minecraft mc) {
        if (enabled(mc) && mc.screen != null) {
            // unattended run: accept the vanilla "experimental settings" warning for the test world
            for (var child : mc.screen.children()) {
                if (child instanceof net.minecraft.client.gui.components.Button button
                        && button.getMessage().getString().toLowerCase().contains("know what")) {
                    button.onPress();
                    Interstice.LOG.info("[autotest] dismissed the experimental-settings warning");
                    return;
                }
            }
        }
        if (!enabled(mc) || mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
        ticks++;
        if (modelMode(mc)) {
            modelTick(mc);
            return;
        }
        var p = mc.player;
        switch (step) {
            case 0 -> {
                if (after(60)) {
                    ClientConfig.INSTANCE.diagnostics = false;
                    server(mc, s -> {
                        ServerPlayer sp = s.getPlayerList().getPlayers().get(0);
                        IntersticeServer.get().director.setPaused(true);
                        IntersticeTeleport.enter(sp);
                    });
                    next();
                }
            }
            case 1 -> {
                if (after(220) && ClientState.inInterstice) {
                    p.setYRot(0);
                    p.setXRot(-8);
                    next();
                }
            }
            case 2 -> { if (after(30)) { shot(mc, "01_arrival_corridor"); next(); } }
            case 3 -> { p.setXRot(-62); p.setYRot(20); if (after(20)) { shot(mc, "02_walls_and_chains_up"); next(); } }
            case 4 -> { p.setYRot(90); p.setXRot(-15); if (after(20)) { shot(mc, "03_wall_face"); next(); } }
            case 5 -> {
                p.setYRot(0);
                p.setXRot(-20);
                server(mc, s -> IntersticeServer.get().environment.quake(IntersticeServer.get().level(), 0.9f, 220, true));
                next();
            }
            case 6 -> { if (after(70)) { shot(mc, "04_earthquake"); next(); } }
            case 7 -> {
                server(mc, s -> IntersticeServer.get().stampede.forcePhase(IntersticeServer.get().level(), StampedePhase.HORDE_APPROACH));
                next();
            }
            case 8 -> { p.setYRot(180); p.setXRot(-25); if (after(260)) { shot(mc, "05_horde_approach"); next(); } }
            case 9 -> { p.setYRot(150); p.setXRot(-35); if (after(120)) { shot(mc, "06_horde_passage"); next(); } }
            case 10 -> {
                server(mc, s -> {
                    IntersticeServer is = IntersticeServer.get();
                    is.stampede.forcePhase(is.level(), StampedePhase.GROUND_SEARCH);
                    ServerPlayer sp = s.getPlayerList().getPlayers().get(0);
                    Vec3 at = sp.position().add(0, 0, 6);
                    AdherentEntity e = is.population.createGround(is.level(), at);
                    if (e != null) {
                        e.setBehavior(AdherentEntity.Behavior.SEARCH);
                    }
                    AdherentEntity w = is.population.create(is.level(), is.layout().sideOf(sp.getX(), sp.getZ()), sp.getY() + 5, sp.getZ() + 5);
                });
                next();
            }
            case 11 -> { p.setYRot(0); p.setXRot(10); if (after(40)) { shot(mc, "07_ground_searcher"); next(); } }
            case 12 -> {
                ClientConfig.INSTANCE.diagnostics = true;
                if (after(20)) { shot(mc, "08_diagnostics"); next(); }
            }
            case 13 -> {
                server(mc, s -> IntersticeServer.get().stampede.stop(IntersticeServer.get().level()));
                ClientConfig.INSTANCE.diagnostics = false;
                p.setYRot(0);
                p.setXRot(-5);
                if (after(60)) { shot(mc, "09_after"); next(); }
            }
            case 14 -> {
                if (after(40)) {
                    Interstice.LOG.info("[autotest] finished; stopping client");
                    try {
                        Files.deleteIfExists(Path.of(mc.gameDirectory.getPath(), "interstice_autotest.flag"));
                    } catch (Exception ignored) {
                    }
                    mc.stop();
                }
            }
            default -> {
            }
        }
    }
}
