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
        Screenshot.grab(mc.gameDirectory, "interstice_" + name + ".png", mc.getMainRenderTarget(), msg -> Interstice.LOG.info("[autotest] {}", msg.getString()));
    }

    private static boolean after(int t) {
        return ticks - stepStart >= t;
    }

    private static void next() {
        step++;
        stepStart = ticks;
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

    /** Close-up turntable of the creature under light: front, side, three-quarter, hunting, on the wall. */
    private static void modelTick(Minecraft mc) {
        var p = mc.player;
        mc.options.hideGui = true;
        switch (step) {
            case 0 -> {
                if (after(60)) {
                    server(mc, s -> {
                        IntersticeServer is = IntersticeServer.get();
                        is.director.setPaused(true);
                        ServerPlayer sp = s.getPlayerList().getPlayers().get(0);
                        IntersticeTeleport.enter(sp);
                    });
                    next();
                }
            }
            case 1 -> {
                if (after(160) && ClientState.inInterstice) {
                    server(mc, s -> {
                        IntersticeServer is = IntersticeServer.get();
                        var level = is.level();
                        ServerPlayer sp = s.getPlayerList().getPlayers().get(0);
                        sp.teleportTo(level, sp.getX(), sp.getY(), sp.getZ(), 0f, 12f);
                        net.minecraft.core.BlockPos base = sp.blockPosition();
                        for (int[] o : new int[][]{{-3, 3, 2}, {3, 3, 2}, {0, 4, 5}, {-2, 0, 0}, {2, 0, 0}}) {
                            level.setBlockAndUpdate(base.offset(o[0], o[1], o[2]), com.interstice.registry.ModBlocks.INDUSTRIAL_LAMP.defaultBlockState());
                        }
                        subject = is.population.createGround(level, sp.position().add(0, 0, 5.5));
                        if (subject != null) {
                            subject.setNoAi(true);
                            subject.setBehavior(AdherentEntity.Behavior.IDLE);
                            subject.setYRot(180f);
                            subject.yBodyRot = 180f;
                            subject.setYHeadRot(180f);
                        }
                    });
                    next();
                }
            }
            case 2 -> { p.setYRot(0); p.setXRot(14); if (after(60)) { shot(mc, "m1_idle_front"); next(); } }
            case 3 -> {
                // side-on creeping walk: three frames of the gait
                walk(mc, 90f, AdherentEntity.Behavior.CRAWL, 0.07);
                if (after(30) && ticks - stepStart == 30) shot(mc, "m2a_walk");
                if (after(37) && ticks - stepStart == 37) shot(mc, "m2b_walk");
                if (after(44)) { shot(mc, "m2c_walk"); next(); }
            }
            case 4 -> {
                walk(mc, 120f, AdherentEntity.Behavior.HUNT, 0.22);
                if (after(16) && ticks - stepStart == 16) shot(mc, "m3a_hunt");
                if (after(20)) { shot(mc, "m3b_hunt"); next(); }
            }
            case 5 -> {
                server(mc, s -> {
                    if (subject == null) return;
                    ServerPlayer sp = s.getPlayerList().getPlayers().get(0);
                    subject.setPos(sp.getX(), sp.getY(), sp.getZ() + 4.5);
                    subject.setYRot(180f); subject.yBodyRot = 180f; subject.setYHeadRot(180f);
                    subject.setLookTarget(sp.getEyePosition());
                    subject.setBehavior(AdherentEntity.Behavior.STARE);
                });
                p.setYRot(0); p.setXRot(-8);
                if (after(60)) { shot(mc, "m4_rearing_stare"); next(); }
            }
            case 6 -> {
                server(mc, s -> {
                    IntersticeServer is = IntersticeServer.get();
                    if (subject != null) subject.discard();
                    ServerPlayer sp = s.getPlayerList().getPlayers().get(0);
                    int side = is.layout().sideOf(sp.getX(), sp.getZ());
                    double faceX = is.layout().faceX(side, sp.getZ());
                    sp.teleportTo(is.level(), faceX - side * 7, sp.getY() + 0, sp.getZ(), side < 0 ? 90f : -90f, -20f);
                    AdherentEntity w = is.population.create(is.level(), side, sp.getY() + 3.5, sp.getZ());
                    if (w != null) { w.setNoAi(true); w.setBehavior(AdherentEntity.Behavior.IDLE); }
                });
                next();
            }
            case 7 -> { if (after(80)) { shot(mc, "m5_on_wall"); next(); } }
            case 8 -> {
                if (after(30)) {
                    try { Files.deleteIfExists(Path.of(mc.gameDirectory.getPath(), "interstice_autotest.flag")); } catch (Exception ignored) {}
                    mc.stop();
                }
            }
            default -> {
            }
        }
    }

    /** Moves the AI-less subject along its facing so the client animator sees real travel. */
    private static void walk(Minecraft mc, float yaw, AdherentEntity.Behavior behavior, double speed) {
        server(mc, s -> {
            if (subject == null) return;
            if (subject.behavior() != behavior) {
                ServerPlayer sp = s.getPlayerList().getPlayers().get(0);
                double r = Math.toRadians(yaw);
                // start beside the camera's line of sight so the walk crosses the frame
                subject.setPos(sp.getX() + Math.sin(r) * 3, sp.getY(), sp.getZ() + 5.5 - Math.cos(r) * 3);
                subject.setBehavior(behavior);
            }
            subject.setYRot(yaw); subject.yBodyRot = yaw; subject.setYHeadRot(yaw);
            double r = Math.toRadians(yaw);
            subject.setPos(subject.getX() - Math.sin(r) * speed, subject.getY(), subject.getZ() + Math.cos(r) * speed);
        });
        mc.player.setYRot(0);
        mc.player.setXRot(12);
    }

    public static void tick(Minecraft mc) {
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
