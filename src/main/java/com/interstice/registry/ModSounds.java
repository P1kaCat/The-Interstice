package com.interstice.registry;

import com.interstice.Interstice;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvent;

public final class ModSounds {
    public static final SoundEvent AMBIENT_DRONE = reg("ambient.drone");
    public static final SoundEvent AMBIENT_AIR = reg("ambient.air");
    public static final SoundEvent AMBIENT_HUM = reg("ambient.hum");

    public static final SoundEvent ENV_IMPACT_FAR = reg("env.impact_far");
    public static final SoundEvent ENV_CHAIN_GROAN = reg("env.chain_groan");
    public static final SoundEvent ENV_CHAIN_IMPACT = reg("env.chain_impact");
    public static final SoundEvent ENV_STRUCTURE_GROAN = reg("env.structure_groan");
    public static final SoundEvent ENV_RUMBLE = reg("env.rumble");
    public static final SoundEvent ENV_DEBRIS = reg("env.debris");
    public static final SoundEvent ENV_SCUTTLE_FAR = reg("env.scuttle_far");
    public static final SoundEvent ENV_UNEXPLAINED = reg("env.unexplained");
    public static final SoundEvent ENV_LAMP_FAIL = reg("env.lamp_fail");

    public static final SoundEvent EVENT_QUAKE = reg("event.quake");
    public static final SoundEvent EVENT_HORDE = reg("event.horde");
    public static final SoundEvent EVENT_PREMONITION = reg("event.premonition");

    public static final SoundEvent CREATURE_IDLE = reg("creature.idle");
    public static final SoundEvent CREATURE_HURT = reg("creature.hurt");
    public static final SoundEvent CREATURE_DEATH = reg("creature.death");
    public static final SoundEvent CREATURE_SCREAM = reg("creature.scream");
    public static final SoundEvent CREATURE_STEP = reg("creature.step");
    public static final SoundEvent CREATURE_ALERT = reg("creature.alert");
    public static final SoundEvent CREATURE_LAND = reg("creature.land");
    public static final SoundEvent CREATURE_SNIFF = reg("creature.sniff");

    public static final String[] VOICE_LINES = {"hide", "dont_move", "not_yet", "go", "they_hear", "look_up", "closer", "stay"};

    static {
        for (String line : VOICE_LINES) reg("voice." + line);
    }

    private static SoundEvent reg(String name) {
        return Registry.register(BuiltInRegistries.SOUND_EVENT, Interstice.id(name), SoundEvent.createVariableRangeEvent(Interstice.id(name)));
    }

    public static Holder<SoundEvent> holder(SoundEvent e) {
        return BuiltInRegistries.SOUND_EVENT.wrapAsHolder(e);
    }

    public static void init() {}

    private ModSounds() {}
}
