package com.interstice.client;

import com.interstice.Interstice;
import com.interstice.block.LampHooks;
import com.interstice.client.audio.AudioDirector;
import com.interstice.client.audio.VoicePlayer;
import com.interstice.client.config.ClientConfig;
import com.interstice.client.effects.DustEffects;
import com.interstice.client.effects.DustParticle;
import com.interstice.client.effects.QuakeShake;
import com.interstice.client.render.atmosphere.AtmosphereController;
import com.interstice.client.render.atmosphere.IntersticeDimensionEffects;
import com.interstice.client.render.atmosphere.IntersticeSkyRenderer;
import com.interstice.client.render.creatures.AdherentRenderer;
import com.interstice.client.render.veil.LampLightManager;
import com.interstice.client.render.veil.VeilIntegration;
import com.interstice.client.ui.IntersticeConfigScreen;
import com.interstice.client.ui.IntersticeHud;
import com.interstice.event.stampede.StampedePhase;
import com.interstice.network.ModNetwork;
import com.interstice.registry.ModBlocks;
import com.interstice.registry.ModEntities;
import com.interstice.registry.ModWorld;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.DimensionRenderingRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import org.lwjgl.glfw.GLFW;

/**
 * Client entrypoint. Everything under com.interstice.client is client-only
 * (Loom split source sets), so a dedicated server never loads these classes.
 */
public class IntersticeClient implements ClientModInitializer {
    private static KeyMapping settingsKey;
    private static boolean wasInInterstice;
    private static StampedePhase lastPhase = StampedePhase.IDLE;

    @Override
    public void onInitializeClient() {
        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("veil")) {
            throw new IllegalStateException("The Interstice requires Veil (FoundryMC) 4.5.0+ on the client: https://modrinth.com/mod/veil");
        }
        ClientConfig.load();

        // The Corridor's 32x32 water (the only vanilla block left in its generation, a fluid): a built-in pack,
        // on by default, can be turned off in Options > Resource Packs. Our own textures (tools/assets/make_corridor_copies.py).
        net.fabricmc.fabric.api.resource.ResourceManagerHelper.registerBuiltinResourcePack(
                Interstice.id("corridor_water"),
                net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(Interstice.MOD_ID).orElseThrow(),
                net.minecraft.network.chat.Component.literal("Interstice: Corridor water (32x)"),
                net.fabricmc.fabric.api.resource.ResourcePackActivationType.DEFAULT_ENABLED);

        com.interstice.client.render.blocks.ConnectedTextures.init();   // connected textures and material transitions

        net.fabricmc.fabric.api.resource.ResourceManagerHelper.get(net.minecraft.server.packs.PackType.CLIENT_RESOURCES)
                .registerReloadListener(new net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener() {
                    @Override
                    public net.minecraft.resources.ResourceLocation getFabricId() {
                        return Interstice.id("adherent_mesh");
                    }

                    @Override
                    public void onResourceManagerReload(net.minecraft.server.packs.resources.ResourceManager manager) {
                        com.interstice.client.render.creatures.mesh.SkinnedMeshAsset.invalidate();
                    }
                });
        for (com.interstice.entity.species.Species sp : com.interstice.entity.species.Species.values()) {
            EntityRendererRegistry.register(ModEntities.type(sp), ctx -> new AdherentRenderer(ctx, sp));
        }
        EntityRendererRegistry.register(ModEntities.LURE, net.minecraft.client.renderer.entity.ThrownItemRenderer::new);
        ParticleFactoryRegistry.getInstance().register(ModEntities.FALLING_DUST, DustParticle.DustProvider::new);
        ParticleFactoryRegistry.getInstance().register(ModEntities.MOTE, DustParticle.MoteProvider::new);
        // the 3D flashlight shows its lens lit or dark
        net.minecraft.client.renderer.item.ItemProperties.register(com.interstice.registry.ModItems.FLASHLIGHT, Interstice.id("lit"),
                (stack, level, entity, seed) -> com.interstice.item.FlashlightItem.isLit(stack) ? 1f : 0f);
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderType.cutout(), ModBlocks.METAL_GRATE, ModBlocks.INDUSTRIAL_LAMP, ModBlocks.SODIUM_LAMP, ModBlocks.EMERGENCY_LAMP, ModBlocks.GROWTH_VEINS, ModBlocks.WARD_EMITTER);
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderType.translucent(), ModBlocks.GRIMY_GLASS_PANE, ModBlocks.GRIMY_GLASS, ModBlocks.BLACKOUT_PANE);
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderType.cutout(), ModBlocks.RUSTED_BARS);
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderType.cutout(), ModBlocks.WORN_DOOR, ModBlocks.ROTTEN_DOOR, ModBlocks.PALE_DOOR, ModBlocks.STEEL_DOOR,
                ModBlocks.RUSTED_LADDER, ModBlocks.CHEM_STAND, ModBlocks.DUST_WEB, ModBlocks.DEAD_ROOTS, ModBlocks.RUSTED_CHAIN, ModBlocks.RUSTED_FUNNEL,
                ModBlocks.RUSTED_BASIN, ModBlocks.HOSPITAL_BED, ModBlocks.WORN_BED);
        // the spawn eggs have their own drawn 32x32 textures: no two-colour tint
        net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry.ITEM.register((stack, layer) -> -1,
                ModEntities.SPAWN_EGGS.values().toArray(new net.minecraft.world.level.ItemLike[0]));

        DimensionRenderingRegistry.registerDimensionEffects(Interstice.id("the_interstice"), new IntersticeDimensionEffects());
        DimensionRenderingRegistry.registerSkyRenderer(ModWorld.INTERSTICE, new IntersticeSkyRenderer());

        LampHooks.setListener(LampLightManager::onLampSeen);
        ClientChunkEvents.CHUNK_LOAD.register(LampLightManager::onChunkLoad);
        ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> LampLightManager.onChunkUnload(chunk));

        ClientPlayNetworking.registerGlobalReceiver(ModNetwork.LayoutSync.TYPE, (p, ctx) -> ClientState.onLayout(p.seed(), p.version()));
        ClientPlayNetworking.registerGlobalReceiver(ModNetwork.StampedeSync.TYPE, (p, ctx) -> {
            ClientState.onStampede(p);
            if (ClientState.phase != lastPhase) {
                lastPhase = ClientState.phase;
                AudioDirector.onPhase(ClientState.phase);
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(ModNetwork.EnvEvent.TYPE, (p, ctx) -> {
            ClientState.onEnvEvent(p);
            AudioDirector.onEnvEvent(p);
            if (p.kind() == ModNetwork.EnvEvent.FLASH) com.interstice.client.render.veil.DeviceLights.flash(p.x(), p.y(), p.z(), p.duration());
        });
        ClientPlayNetworking.registerGlobalReceiver(ModNetwork.Voice.TYPE, (p, ctx) -> VoicePlayer.play(p.line(), p.volume()));
        ClientPlayNetworking.registerGlobalReceiver(ModNetwork.Debug.TYPE, (p, ctx) -> {
            ClientState.debug = p.enabled();
            if (!p.serverText().isEmpty() || !p.enabled()) ClientState.serverDebug = p.enabled() ? p.serverText() : "";
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientState.reset();
            LampLightManager.clear();
            com.interstice.client.render.veil.CreatureEyeLights.clear();
            com.interstice.client.render.veil.DeviceLights.clear();
            AudioDirector.stopAll();
            lastPhase = StampedePhase.IDLE;
        });

        WorldRenderEvents.START.register(ctx -> {
            ClientState.update(ctx.tickCounter().getGameTimeDeltaPartialTick(false));
            if (ClientState.inInterstice) AtmosphereController.update();
            VeilIntegration.frame();
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) LampLightManager.update(mc.level, ctx.camera().getPosition());
            com.interstice.client.render.veil.CreatureEyeLights.update(ctx.camera().getPosition());
            com.interstice.client.render.veil.DeviceLights.update(ctx.camera().getPosition(), ctx.tickCounter().getGameTimeDeltaPartialTick(false));
        });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            ClientState.update(0f);
            boolean in = ClientState.inInterstice;
            if (wasInInterstice && !in) {
                LampLightManager.clear();
                com.interstice.client.render.veil.CreatureEyeLights.clear();
                AudioDirector.stopAll();
            }
            wasInInterstice = in;
            AudioDirector.tick();
            if (ClientState.inInterstice && mc.player != null) com.interstice.client.render.chains.ChainRenderer.collide(mc.player);
            DustEffects.tick();
            QuakeShake.update();
            while (settingsKey.consumeClick()) mc.setScreen(new IntersticeConfigScreen(mc.screen));
            com.interstice.client.dev.AutoTest.tick(mc);
        });

        HudRenderCallback.EVENT.register(IntersticeHud::render);
        settingsKey = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.interstice.settings", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F7, "key.categories.interstice"));

        VeilIntegration.init();
        Interstice.LOG.info("The Interstice client initialised");
    }
}
