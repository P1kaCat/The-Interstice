package com.interstice.registry;

import com.interstice.Interstice;
import com.interstice.entity.species.Species;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

import java.util.EnumMap;

/**
 * The sound identity of each species (Corridor rework). Every species has the same five families of events,
 * with its own files ({@code sounds/creature/<species>/}):
 * <ul>
 *   <li>{@code step}: a limb meeting the ground, played by the client exactly when the animation puts it down;</li>
 *   <li>{@code scrape}: a limb meeting the wall (climbing), likewise animation-synchronised;</li>
 *   <li>{@code breath}: the idle ambient sound (the Clinger has no breathing: small finger clicks instead);</li>
 *   <li>{@code vocal}: alert / threat vocalisation;</li>
 *   <li>{@code click}: body and joint sounds (the Strider's creaking joints, the Watcher's head turning, the
 *       Hollow's listening clicks, the Fractured's misaligned double clicks).</li>
 * </ul>
 * Plus the region beds and the environment's reactions. All are development syntheses
 * ({@code tools/audio/gen_corridor_audio.py}); docs/CORRIDOR_AUDIO.md specifies the final recordings.
 */
public final class SpeciesSounds {
    public final SoundEvent step, scrape, breath, vocal, click, land, hurt, alert;

    private SpeciesSounds(Species sp) {
        String p = "creature." + sp.id + ".";
        step = reg(p + "step");
        scrape = reg(p + "scrape");
        breath = reg(p + "breath");
        vocal = reg(p + "vocal");
        click = reg(p + "click");
        land = reg(p + "land");
        hurt = reg(p + "hurt");
        alert = reg(p + "alert");
    }

    private static final EnumMap<Species, SpeciesSounds> BY_SPECIES = new EnumMap<>(Species.class);

    static {
        for (Species sp : Species.values()) BY_SPECIES.put(sp, new SpeciesSounds(sp));
    }

    public static SpeciesSounds of(Species sp) {
        return BY_SPECIES.get(sp);
    }

    // region beds (looped by the client's AudioDirector, mixed by the region weights at the camera)
    public static final SoundEvent AMBIENT_DISTRICT = reg("ambient.forgotten_district");
    public static final SoundEvent AMBIENT_IRONWORKS = reg("ambient.ironworks");
    public static final SoundEvent AMBIENT_WARD = reg("ambient.contaminated_ward");
    public static final SoundEvent AMBIENT_EXPANSE = reg("ambient.dead_expanse");

    // the environment answering (the Burden's weight, the Stampede, metal underfoot, chains under tension)
    public static final SoundEvent BUILDING_RATTLE = reg("env.building_rattle");
    public static final SoundEvent DUST_FALL = reg("env.dust_fall");
    public static final SoundEvent METAL_CONTACT = reg("env.metal_contact");
    public static final SoundEvent CHAIN_TENSION = reg("env.chain_tension");

    /** The Stampede announcing itself from far down the Corridor, just before the great quake. */
    public static final SoundEvent HORDE_SCREAM = reg("event.horde_scream");

    private static SoundEvent reg(String name) {
        return Registry.register(BuiltInRegistries.SOUND_EVENT, Interstice.id(name), SoundEvent.createVariableRangeEvent(Interstice.id(name)));
    }

    public static void init() {}

    private SpeciesSounds() {
        step = scrape = breath = vocal = click = land = hurt = alert = null;
    }
}
