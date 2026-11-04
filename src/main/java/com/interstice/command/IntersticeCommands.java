package com.interstice.command;

import com.interstice.config.ServerConfig;
import com.interstice.core.IntersticeServer;
import com.interstice.entity.AdherentEntity;
import com.interstice.event.VoiceEvents;
import com.interstice.event.director.HorrorDirector;
import com.interstice.event.stampede.StampedePhase;
import com.interstice.network.ModNetwork;
import com.interstice.registry.ModSounds;
import com.interstice.registry.ModWorld;
import com.interstice.world.IntersticeLayout;
import com.interstice.world.IntersticeTeleport;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;

/**
 * /interstice development commands (permission level 2: operators / cheats enabled).
 */
public final class IntersticeCommands {
    private static final com.mojang.brigadier.exceptions.SimpleCommandExceptionType NOT_LOADED =
            new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal("The Interstice is not loaded."));
    private static final com.mojang.brigadier.exceptions.SimpleCommandExceptionType NOT_INSIDE =
            new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(Component.literal("You must be inside The Interstice (/interstice enter)."));

    private IntersticeCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("interstice").requires(src -> src.hasPermission(2))
                .then(Commands.literal("enter").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    if (!IntersticeTeleport.enter(p)) return fail(c, "The Interstice dimension is not loaded.");
                    return 1;
                }))
                .then(Commands.literal("leave").executes(c -> {
                    IntersticeTeleport.leave(c.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("stampede")
                        .executes(c -> stampedeStart(c, 1f))
                        .then(Commands.literal("start").executes(c -> stampedeStart(c, 1f))
                                .then(Commands.argument("intensity", FloatArgumentType.floatArg(0.1f, 2f))
                                        .executes(c -> stampedeStart(c, FloatArgumentType.getFloat(c, "intensity")))))
                        .then(Commands.literal("stop").executes(c -> {
                            IntersticeServer s = server(c);
                            s.stampede.stop(level(c, s));
                            return ok(c, "Stampede stopped.");
                        }))
                        .then(Commands.literal("phase").then(Commands.argument("phase", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(StampedePhase.values()).map(p -> p.name().toLowerCase()), b))
                                .executes(c -> {
                                    StampedePhase p = StampedePhase.byName(StringArgumentType.getString(c, "phase"));
                                    if (p == null) return fail(c, "Unknown phase.");
                                    IntersticeServer s = server(c);
                                    s.stampede.forcePhase(level(c, s), p);
                                    return ok(c, "Stampede phase: " + p);
                                }))))
                .then(Commands.literal("quake")
                        .executes(c -> quake(c, 0.6f))
                        .then(Commands.argument("intensity", FloatArgumentType.floatArg(0.05f, 1.5f))
                                .executes(c -> quake(c, FloatArgumentType.getFloat(c, "intensity")))))
                .then(Commands.literal("spawn")
                        .then(Commands.literal("wall").executes(c -> spawnWall(c)))
                        .then(Commands.literal("ground").executes(c -> spawnGround(c)))
                        .then(Commands.literal("faller").executes(c -> spawnFaller(c))))
                .then(Commands.literal("whisper")
                        .executes(c -> whisper(c, "hide"))
                        .then(Commands.argument("line", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(ModSounds.VOICE_LINES, b))
                                .executes(c -> whisper(c, StringArgumentType.getString(c, "line")))))
                .then(Commands.literal("state").executes(IntersticeCommands::state))
                .then(Commands.literal("debug").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    IntersticeServer s = server(c);
                    boolean on = !s.debugPlayers.remove(p.getUUID());
                    if (on) s.debugPlayers.add(p.getUUID());
                    ModNetwork.send(p, new ModNetwork.Debug(on, ""));
                    return ok(c, "Diagnostics " + (on ? "enabled" : "disabled"));
                }))
                .then(Commands.literal("director")
                        .then(Commands.literal("pause").executes(c -> { server(c).director.setPaused(true); return ok(c, "Director paused."); }))
                        .then(Commands.literal("resume").executes(c -> { server(c).director.setPaused(false); return ok(c, "Director resumed."); }))
                        .then(Commands.literal("tension").then(Commands.argument("value", FloatArgumentType.floatArg(0, 1)).executes(c -> {
                            IntersticeServer s = server(c);
                            s.director.setTension(level(c, s), FloatArgumentType.getFloat(c, "value"));
                            return ok(c, "Tension set.");
                        })))
                        .then(Commands.literal("frequency").then(Commands.argument("value", FloatArgumentType.floatArg(0.05f, 20f)).executes(c -> {
                            ServerConfig.INSTANCE.eventFrequency = FloatArgumentType.getFloat(c, "value");
                            ServerConfig.save();
                            return ok(c, "Event frequency x" + ServerConfig.INSTANCE.eventFrequency);
                        })))
                        .then(Commands.literal("trigger").then(Commands.argument("action", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(HorrorDirector.Action.values()).map(a -> a.name().toLowerCase()), b))
                                .executes(c -> {
                                    String name = StringArgumentType.getString(c, "action").toUpperCase();
                                    HorrorDirector.Action a;
                                    try { a = HorrorDirector.Action.valueOf(name); } catch (IllegalArgumentException e) { return fail(c, "Unknown action."); }
                                    IntersticeServer s = server(c);
                                    ServerPlayer p = c.getSource().getPlayerOrException();
                                    s.director.perform(level(c, s), p, a, p.getRandom());
                                    return ok(c, "Director action: " + a);
                                }))))
        );
    }

    private static IntersticeServer server(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        IntersticeServer s = IntersticeServer.get();
        if (s == null || s.level() == null) throw NOT_LOADED.create();
        return s;
    }

    private static ServerLevel level(CommandContext<CommandSourceStack> c, IntersticeServer s) {
        return s.level();
    }

    private static ServerPlayer playerInDimension(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        if (!ModWorld.isInterstice(p.level())) throw NOT_INSIDE.create();
        return p;
    }

    private static int stampedeStart(CommandContext<CommandSourceStack> c, float intensity) throws CommandSyntaxException {
        IntersticeServer s = server(c);
        s.stampede.start(level(c, s), intensity);
        return ok(c, "Stampede started (premonition).");
    }

    private static int quake(CommandContext<CommandSourceStack> c, float intensity) throws CommandSyntaxException {
        IntersticeServer s = server(c);
        s.environment.quake(level(c, s), intensity, (int) (80 + intensity * 120), true);
        return ok(c, "Earthquake " + intensity);
    }

    private static int spawnWall(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = playerInDimension(c);
        IntersticeServer s = server(c);
        IntersticeLayout L = s.layout();
        int side = L.sideOf(p.getX(), p.getZ());
        AdherentEntity e = s.population.create(s.level(), side, p.getY() + 6, p.getZ() + 4);
        return e != null ? ok(c, "Spawned a wall creature on the " + (side < 0 ? "west" : "east") + " wall.") : fail(c, "No wall surface found.");
    }

    private static int spawnGround(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = playerInDimension(c);
        IntersticeServer s = server(c);
        Vec3 pos = p.position().add(p.getLookAngle().multiply(8, 0, 8));
        AdherentEntity e = s.population.createGround(s.level(), new Vec3(pos.x, p.getY(), pos.z));
        return e != null ? ok(c, "Spawned a ground searcher.") : fail(c, "Spawn failed.");
    }

    private static int spawnFaller(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = playerInDimension(c);
        IntersticeServer s = server(c);
        int side = s.layout().sideOf(p.getX(), p.getZ());
        AdherentEntity e = s.population.create(s.level(), side, p.getY() + 28, p.getZ() + 6);
        if (e == null) return fail(c, "No wall surface found.");
        e.forceSurvive = true;
        e.startFalling();
        return ok(c, "A creature lost its grip.");
    }

    private static int whisper(CommandContext<CommandSourceStack> c, String line) throws CommandSyntaxException {
        if (!VoiceEvents.isLine(line)) return fail(c, "Unknown line. Lines: " + String.join(", ", ModSounds.VOICE_LINES));
        ServerPlayer p = c.getSource().getPlayerOrException();
        ModNetwork.send(p, new ModNetwork.Voice(line, 1f));
        return 1;
    }

    private static int state(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        IntersticeServer s = server(c);
        ServerLevel l = s.level();
        long now = l.getGameTime();
        String msg = "Stampede: " + s.stampede.phase() + " (" + s.stampede.phaseTicksLeft(now) + " ticks left)\n"
                + "Director: " + s.director.describe() + "\n"
                + "Real creatures: " + s.population.countAll(l) + ", players in dimension: " + l.players().size() + "\n"
                + "Quake active: " + s.environment.quakeActive(now) + ", event frequency x" + ServerConfig.INSTANCE.eventFrequency;
        return ok(c, msg);
    }

    private static int ok(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    private static int fail(CommandContext<CommandSourceStack> c, String msg) {
        c.getSource().sendFailure(Component.literal(msg));
        return 0;
    }
}
