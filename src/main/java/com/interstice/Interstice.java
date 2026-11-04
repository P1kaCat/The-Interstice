package com.interstice;

import com.interstice.command.IntersticeCommands;
import com.interstice.config.ServerConfig;
import com.interstice.core.IntersticeServer;
import com.interstice.network.ModNetwork;
import com.interstice.registry.ModBlocks;
import com.interstice.registry.ModEntities;
import com.interstice.registry.ModSounds;
import com.interstice.registry.ModWorld;
import com.interstice.world.IntersticeTeleport;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Common (client + dedicated server) entrypoint. Never references client classes. */
public class Interstice implements ModInitializer {
    public static final String MOD_ID = "interstice";
    public static final Logger LOG = LoggerFactory.getLogger("Interstice");

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    @Override
    public void onInitialize() {
        ServerConfig.load();
        ModBlocks.init();
        ModSounds.init();
        ModEntities.init();
        ModWorld.init();
        ModNetwork.init();
        IntersticeTeleport.init();

        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> IntersticeCommands.register(dispatcher));

        ServerLifecycleEvents.SERVER_STARTED.register(IntersticeServer::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> IntersticeServer.stop());
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            IntersticeServer s = IntersticeServer.get();
            if (s != null) s.tick();
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            IntersticeServer s = IntersticeServer.get();
            if (s != null && ModWorld.isInterstice(handler.player.level())) s.syncTo(handler.player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            IntersticeServer s = IntersticeServer.get();
            if (s != null) s.forget(handler.player);
        });
        ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            IntersticeServer s = IntersticeServer.get();
            if (s == null) return;
            if (ModWorld.isInterstice(destination)) s.syncTo(player);
            else s.forget(player);
        });

        // Player actions that make noise the creatures can hear.
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> noise(level, player, Vec3.atCenterOf(pos), 14));
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            noise(level, player, hit.getLocation(), 9);
            return InteractionResult.PASS;
        });
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            noise(level, player, entity.position(), 14);
            return InteractionResult.PASS;
        });
    }

    private static void noise(Level level, Player player, Vec3 at, float radius) {
        if (level.isClientSide || !ModWorld.isInterstice(level) || !(player instanceof ServerPlayer sp) || sp.isSpectator()) return;
        IntersticeServer s = IntersticeServer.get();
        if (s != null) s.noise.emit(at, radius, level.getGameTime(), player);
    }
}
