package com.interstice.registry;

import com.interstice.Interstice;
import com.interstice.entity.AdherentEntity;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;

public final class ModEntities {
    public static final EntityType<AdherentEntity> ADHERENT = Registry.register(BuiltInRegistries.ENTITY_TYPE, Interstice.id("adherent"),
            EntityType.Builder.of(AdherentEntity::new, MobCategory.MONSTER)
                    .sized(1.4f, 1.8f)
                    .eyeHeight(1.3f)
                    .clientTrackingRange(12)
                    .updateInterval(2)
                    .build("adherent"));

    public static final Item ADHERENT_SPAWN_EGG = Registry.register(BuiltInRegistries.ITEM, Interstice.id("adherent_spawn_egg"),
            new SpawnEggItem(ADHERENT, 0x5a5650, 0x1b1a19, new Item.Properties()));

    /** Falling dust / debris, spawned client-side by earthquakes and structural events. */
    public static final SimpleParticleType FALLING_DUST = Registry.register(BuiltInRegistries.PARTICLE_TYPE, Interstice.id("falling_dust"), FabricParticleTypes.simple());
    /** Slow drifting motes that catch the light near the camera. */
    public static final SimpleParticleType MOTE = Registry.register(BuiltInRegistries.PARTICLE_TYPE, Interstice.id("mote"), FabricParticleTypes.simple());

    public static void init() {
        FabricDefaultAttributeRegistry.register(ADHERENT, AdherentEntity.createAttributes());
    }

    private ModEntities() {}
}
