package com.interstice.client.dev;

import com.interstice.Interstice;
import com.interstice.client.ClientState;
import com.interstice.client.config.ClientConfig;
import com.interstice.core.IntersticeServer;
import com.interstice.entity.AdherentEntity;
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
    private static AdherentEntity subject;

    private static boolean modelMode(Minecraft mc) {
        if (modelMode == null) {
            try {
                modelMode = Files.readString(mc.gameDirectory.toPath().resolve("interstice_autotest.flag")).contains("model");
            } catch (Exception e) {
                modelMode = false;
            }
        }
        return modelMode;
    }

    /**
     * Creature validation sequence. Neutral, daylit superflat overworld with an armor stand as a
     * player-size reference: still views (front, profile, three-quarter), then frame sequences for
     * animated GIFs (walk, run, rear + threat, fall + recover). Then the Interstice: on a wall
     * (cling, climb) and in the dimension light and fog.
     */
    private static int frame;

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

    private static void seq(Minecraft mc, String name, int every) {
        if ((ticks - stepStart) % every == 0) shot(mc, "seq_" + name + "_" + String.format("%03d", frame++));
    }

    private static void modelTick(Minecraft mc) {
        var p = mc.player;
        mc.options.hideGui = true;
        int t = ticks - stepStart;
        switch (step) {
            case 0 -> {
                if (after(60)) {
                    server(mc, s -> {
                        var level = s.overworld();
                        level.setDayTime(6000);
                        s.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false, s);
                        s.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_WEATHER_CYCLE).set(false, s);
                        level.setWeatherParameters(6000, 0, false, false);
                        ServerPlayer pl = sp(s);
                        // the save may have been left in the Interstice: use the overworld ground, not the player's y
                        double gy = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, 0, 6);
                        pl.removeEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION);
                        pl.teleportTo(level, 0.5, gy, 0.5, 0f, 10f);
                        subject = IntersticeServer.get().population.createGround(level, new Vec3(0.5, gy, 6.5));
                        if (subject != null) {
                            subject.setNoAi(true);
                            posture(subject, 180f, AdherentEntity.Behavior.IDLE);
                        }
                        var stand = net.minecraft.world.entity.EntityType.ARMOR_STAND.create(level);
                        if (stand != null) {
                            stand.moveTo(3.2, gy, 6.5, 180f, 0f);
                            level.addFreshEntity(stand);
                        }
                    });
                    next();
                }
            }
            case 1 -> { p.setYRot(0); p.setXRot(12); if (after(80)) { shot(mc, "v1_front"); next(); } }
            case 2 -> {
                server(mc, s -> { if (subject != null) posture(subject, 90f, AdherentEntity.Behavior.IDLE); });
                if (after(30)) { shot(mc, "v2_profile"); next(); }
            }
            case 3 -> {
                server(mc, s -> { if (subject != null) posture(subject, 140f, AdherentEntity.Behavior.IDLE); });
                if (after(30)) { shot(mc, "v3_three_quarter"); next(); }
            }
            case 4 -> {
                if (t == 0) frame = 0;
                stepMove(mc, 90f, AdherentEntity.Behavior.CRAWL, 0.045, t == 0);
                if (t >= 10) seq(mc, "walk", 2);
                if (t >= 10 + 88) next();
            }
            case 5 -> {
                if (t == 0) frame = 0;
                stepMove(mc, 90f, AdherentEntity.Behavior.RUN, 0.2, t == 0);
                if (t >= 6) seq(mc, "run", 1);
                if (t >= 6 + 36) next();
            }
            case 6 -> {
                if (t == 0) {
                    frame = 0;
                    server(mc, s -> {
                        if (subject == null) return;
                        subject.setPos(0.5, sp(s).getY(), 8.0);
                        posture(subject, 90f, AdherentEntity.Behavior.IDLE);
                    });
                }
                if (t == 20) server(mc, s -> { if (subject != null) posture(subject, 90f, AdherentEntity.Behavior.HUNT); });
                home(mc, -6);
                if (t >= 18) seq(mc, "rear", 2);
                if (t >= 18 + 40) next();
            }
            case 7 -> {
                if (t == 0) {
                    frame = 0;
                    server(mc, s -> {
                        if (subject == null) return;
                        subject.setPos(0.5, sp(s).getY(), 7.0);
                        posture(subject, 140f, AdherentEntity.Behavior.FALL);
                    });
                }
                if (t == 24) server(mc, s -> { if (subject != null) posture(subject, 140f, AdherentEntity.Behavior.RECOVER); });
                home(mc, 12);
                if (t >= 4) seq(mc, "fall_recover", 2);
                if (t >= 4 + 96) next();
            }
            case 8 -> {
                if (t == 0) {
                    frame = 0;
                    server(mc, s -> {
                        IntersticeServer is = IntersticeServer.get();
                        is.director.setPaused(true);
                        if (subject != null) subject.discard();
                        IntersticeTeleport.enter(sp(s));
                    });
                }
                if (t == 160) server(mc, s -> {
                    IntersticeServer is = IntersticeServer.get();
                    ServerPlayer pl = sp(s);
                    int side = is.layout().sideOf(pl.getX(), pl.getZ());
                    double faceX = is.layout().faceX(side, pl.getZ());
                    pl.teleportTo(is.level(), faceX - side * 8, pl.getY(), pl.getZ(), side < 0 ? 90f : -90f, -22f);
                    pl.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION, 20 * 60, 0, false, false));
                    subject = is.population.create(is.level(), side, pl.getY() + 4.5, pl.getZ() - 2.0);
                    if (subject != null) {
                        subject.setNoAi(true);
                        subject.setBehavior(AdherentEntity.Behavior.IDLE);
                    }
                });
                if (t == 230) shot(mc, "v4_wall_cling");
                if (t > 232) next();
            }
            case 9 -> {
                if (t == 0) frame = 0;
                server(mc, s -> {
                    if (subject == null) return;
                    if (subject.behavior() != AdherentEntity.Behavior.CRAWL) subject.setBehavior(AdherentEntity.Behavior.CRAWL);
                    subject.setPos(subject.getX(), subject.getY() + 0.034, subject.getZ() + 0.008);
                });
                p.setXRot(-30);
                if (t >= 8) seq(mc, "climb", 2);
                if (t >= 8 + 80) next();
            }
            case 10 -> {
                if (t == 0) server(mc, s -> {
                    IntersticeServer is = IntersticeServer.get();
                    if (subject != null) subject.discard();
                    ServerPlayer pl = sp(s);
                    pl.removeEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION);
                    Vec3 spot = IntersticeTeleport.findSafeSpot(is.level(), is.layout(), 0);
                    pl.teleportTo(is.level(), spot.x, spot.y, spot.z, 0f, 8f);
                    subject = is.population.createGround(is.level(), spot.add(0, 0, 6.5));
                    if (subject != null) {
                        subject.setNoAi(true);
                        posture(subject, 150f, AdherentEntity.Behavior.IDLE);
                    }
                });
                if (t == 120) shot(mc, "v5_in_dimension");
                if (t > 124) next();
            }
            case 11 -> {
                if (after(30)) {
                    try { Files.deleteIfExists(Path.of(mc.gameDirectory.getPath(), "interstice_autotest.flag")); } catch (Exception ignored) {}
                    mc.stop();
                }
            }
            default -> {
            }
        }
    }

    /** Moves the AI-less subject along its facing so the animator sees real travel. */
    private static void stepMove(Minecraft mc, float yaw, AdherentEntity.Behavior behavior, double speed, boolean reset) {
        server(mc, s -> {
            if (subject == null) return;
            double r = Math.toRadians(yaw);
            if (reset) subject.setPos(0.5 + Math.sin(r) * 4.5, sp(s).getY(), 7.5);
            posture(subject, yaw, behavior);
            subject.setPos(subject.getX() - Math.sin(r) * speed, subject.getY(), subject.getZ() + Math.cos(r) * speed);
        });
        track(mc, 7.0);
        mc.player.setYRot(0);
        mc.player.setXRot(12);
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

    /** Puts the camera back at the reference spot facing the subject area. */
    private static void home(Minecraft mc, float pitch) {
        var p = mc.player;
        p.setPos(0.5, p.getY(), 0.5);
        p.xo = p.xOld = 0.5;
        p.zo = p.zOld = 0.5;
        p.setDeltaMovement(Vec3.ZERO);
        p.setYRot(0);
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
