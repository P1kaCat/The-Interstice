package com.interstice.registry;

import com.interstice.Interstice;
import com.interstice.entity.AdherentEntity;
import com.interstice.entity.species.Species;
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
    /**
     * One entity type per species of the creature family (same entity class, species-specific hitbox,
     * attributes, renderer asset and spawn egg). {@link #ADHERENT} is kept as the historical constant.
     */
    public static final java.util.EnumMap<Species, EntityType<AdherentEntity>> TYPES = new java.util.EnumMap<>(Species.class);
    public static final java.util.EnumMap<Species, Item> SPAWN_EGGS = new java.util.EnumMap<>(Species.class);
    private static final int[][] EGG_COLOURS = {
            {0x5a5650, 0x1b1a19}, {0x3a3636, 0x121111}, {0x7a746e, 0x2a2523}, {0x4a403d, 0x221b1a},
            {0x6b6b68, 0x3a3a38}, {0x958e88, 0x4a3a3a}, {0x6e6a69, 0x241c1c}, {0x5c5350, 0x5a2a2a}};

    static {
        for (Species sp : Species.values()) {
            EntityType<AdherentEntity> type = Registry.register(BuiltInRegistries.ENTITY_TYPE, Interstice.id(sp.id),
                    EntityType.Builder.<AdherentEntity>of((t, l) -> new AdherentEntity(t, l, sp), MobCategory.MONSTER)
                            .sized(sp.t.width, sp.t.height)
                            .eyeHeight(sp.t.eyeHeight)
                            .clientTrackingRange(sp == Species.STRIDER || sp == Species.BURDEN ? 14 : 12)
                            .updateInterval(2)
                            .build(sp.id));
            TYPES.put(sp, type);
            int[] c = EGG_COLOURS[sp.ordinal() % EGG_COLOURS.length];
            SPAWN_EGGS.put(sp, Registry.register(BuiltInRegistries.ITEM, Interstice.id(sp.id + "_spawn_egg"),
                    new SpawnEggItem(type, c[0], c[1], new Item.Properties())));
        }
    }

    public static final EntityType<AdherentEntity> ADHERENT = TYPES.get(Species.ADHERENT);
    public static final Item ADHERENT_SPAWN_EGG = SPAWN_EGGS.get(Species.ADHERENT);

    public static EntityType<AdherentEntity> type(Species s) {
        return TYPES.get(s);
    }

    /** A thrown rattle lure (protective device). */
    public static final EntityType<com.interstice.entity.ThrownLure> LURE = Registry.register(BuiltInRegistries.ENTITY_TYPE, Interstice.id("rattle_lure"),
            EntityType.Builder.<com.interstice.entity.ThrownLure>of(com.interstice.entity.ThrownLure::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f).clientTrackingRange(6).updateInterval(10).build("rattle_lure"));

    /** Falling dust / debris, spawned client-side by earthquakes and structural events. */
    public static final SimpleParticleType FALLING_DUST = Registry.register(BuiltInRegistries.PARTICLE_TYPE, Interstice.id("falling_dust"), FabricParticleTypes.simple());
    /** Slow drifting motes that catch the light near the camera. */
    public static final SimpleParticleType MOTE = Registry.register(BuiltInRegistries.PARTICLE_TYPE, Interstice.id("mote"), FabricParticleTypes.simple());

    public static void init() {
        for (Species sp : Species.values()) FabricDefaultAttributeRegistry.register(TYPES.get(sp), AdherentEntity.createAttributes(sp));
    }

    private ModEntities() {}
}
