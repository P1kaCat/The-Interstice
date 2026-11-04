package com.interstice.entity.species;

import java.util.Locale;

/**
 * The creature family. Every species shares the Adherent's body plan (same 24-bone skeleton, same clip
 * set) and the same entity class; what differs is described here, as data, and read by the shared
 * systems (wall locomotion, ground brain, Stampede, horde visuals, sounds, spawning).
 * <p>
 * Adding a ninth species: add a constant here, generate its model with
 * {@code tools/creature/species_generator.py}, export it, and give it weights in the config maps.
 * Nothing else needs a species-specific branch, except optional behaviour flags in {@link WallStyle}.
 */
public enum Species {
    ADHERENT(new Traits()
            .body(1.4f, 1.8f, 1.3f, 34, 1.0f, 0.22f, 1.0f)
            .wall(WallStyle.STEADY, 0.05f, 0.13f, 10, 16, 80, 580, 0.55f, 0.20f, 0.15f, 0.30f)
            .stampede(65f, 1.0f, 1.0f, 1.0f, 0.0f)
            .ground(0.55f, 0.20f, 0.10f, 0.05f, 0.10f, 1.0f)
            .voice(1.0f, 1.0f, 220)
            .horde(1.0f, 1.0f, 0.55f)),
    CRAWLER(new Traits()
            .body(0.9f, 1.0f, 0.7f, 18, 0.6f, 0.30f, 0.35f)
            .wall(WallStyle.DART, 0.13f, 0.22f, 6, 9, 20, 160, 0.70f, 0.05f, 0.5f, 0.10f)
            .stampede(12f, 1.15f, 0.30f, 0.6f, 0.0f)
            .ground(0.05f, 0.15f, 0.45f, 0.35f, 0.0f, 0.5f)
            .voice(1.45f, 0.6f, 160)
            .horde(0.62f, 0.95f, 0.45f)),
    STRIDER(new Traits()
            .body(1.5f, 2.6f, 2.2f, 30, 0.9f, 0.26f, 1.4f)
            .wall(WallStyle.LONG_STRIDE, 0.08f, 0.12f, 8, 28, 160, 900, 0.50f, 0.05f, 0.12f, 0.30f)
            .stampede(8f, 1.25f, 1.0f, 1.3f, 0.0f)
            .ground(0.20f, 0.35f, 0.15f, 0.0f, 0.30f, 1.6f)
            .voice(0.75f, 1.2f, 300)
            .horde(1.35f, 1.45f, 0.6f)),
    BURDEN(new Traits()
            .body(2.2f, 2.4f, 1.9f, 80, 1.6f, 0.16f, 4.0f)
            .wall(WallStyle.HEAVY, 0.03f, 0.05f, 6, 10, 400, 1400, 0.40f, 0.0f, 0.03f, 0.20f)
            .stampede(5f, 0.8f, 0.5f, 0.7f, 0.0f)
            .ground(0.45f, 0.25f, 0.0f, 0.0f, 0.30f, 1.4f)
            .voice(0.55f, 2.2f, 280)
            .horde(1.3f, 1.25f, 0.42f)),
    CLINGER(new Traits()
            .body(1.6f, 1.2f, 0.9f, 26, 0.8f, 0.18f, 0.9f)
            .wall(WallStyle.STILL, 0.02f, 0.04f, 3, 4, 2400, 7200, 0.10f, 0.0f, 0.4f, 0.85f)
            .stampede(3f, 0.9f, 0.15f, 1.0f, 0.85f)
            .ground(0.10f, 0.20f, 0.20f, 0.50f, 0.0f, 1.0f)
            .voice(0.9f, 0.4f, 900)
            .horde(1.05f, 0.7f, 0.5f)),
    HOLLOW(new Traits()
            .body(1.1f, 1.7f, 1.4f, 16, 0.7f, 0.20f, 0.6f)
            .wall(WallStyle.IRREGULAR, 0.05f, 0.12f, 8, 12, 60, 400, 0.55f, 0.10f, 0.25f, 0.20f)
            .stampede(3f, 0.95f, 2.5f, 1.4f, 0.0f)
            .ground(0.05f, 0.15f, 0.55f, 0.15f, 0.10f, 1.2f)
            .voice(1.15f, 0.8f, 180)
            .horde(1.05f, 1.05f, 0.68f)),
    WATCHER(new Traits()
            .body(1.3f, 2.3f, 2.1f, 28, 0.8f, 0.20f, 1.0f)
            .wall(WallStyle.WATCH, 0.03f, 0.06f, 5, 8, 400, 2000, 0.25f, 0.05f, 0.05f, 0.60f)
            .stampede(2f, 0.9f, 0.8f, 1.0f, 0.0f)
            .ground(0.10f, 0.60f, 0.10f, 0.20f, 0.0f, 1.0f)
            .voice(0.85f, 0.5f, 1200)
            .horde(1.2f, 1.2f, 0.52f)),
    FRACTURED(new Traits()
            .body(1.5f, 1.9f, 1.6f, 34, 1.1f, 0.22f, 1.1f)
            .wall(WallStyle.LURCH, 0.06f, 0.14f, 9, 14, 80, 600, 0.55f, 0.10f, 0.2f, 0.30f)
            .stampede(2f, 1.0f, 1.3f, 1.0f, 0.0f)
            .ground(0.40f, 0.25f, 0.15f, 0.05f, 0.15f, 1.1f)
            .voice(0.9f, 1.0f, 240)
            .horde(1.15f, 1.15f, 0.5f));

    /** How a species moves on the wall (interpreted by the shared wall locomotion). */
    public enum WallStyle {
        /** Adherent: plain crawl. */ STEADY,
        /** Crawler: quick dashes separated by short freezes; sharp direction changes. */ DART,
        /** Strider: long journeys, mostly along the corridor. */ LONG_STRIDE,
        /** Burden: high inertia, structural vibration while moving. */ HEAVY,
        /** Clinger: minutes of immobility, rare slow repositioning, rare sudden jerks. */ STILL,
        /** Hollow: irregular speed, hesitations, agitated by movement nearby. */ IRREGULAR,
        /** Watcher: stays, turns towards things (not always the player). */ WATCH,
        /** Fractured: asymmetric lurching rhythm with a sideways drift. */ LURCH
    }

    public enum GroundDisposition {
        /** Full brain: search, investigate, hunt. */ HUNTER,
        /** Searches and investigates, then watches from a distance; never attacks. */ INVESTIGATOR,
        /** Runs away from people, then climbs back into the dark. */ FLEER,
        /** Goes to the foot of a wall and stays still for a long time. */ HIDER,
        /** Lies stunned for a long time, then gets up as an investigator or a fleer. */ STUNNED
    }

    public final Traits t;
    public final String id;

    Species(Traits t) {
        this.t = t;
        this.id = name().toLowerCase(Locale.ROOT);
    }

    public static Species byId(String id) {
        for (Species s : values()) if (s.id.equalsIgnoreCase(id)) return s;
        return null;
    }

    public static Species byOrdinal(int i) {
        Species[] v = values();
        return v[Math.floorMod(i, v.length)];
    }

    /** Picks a ground disposition from this species' weights, u uniform in [0, 1). */
    public GroundDisposition rollDisposition(float u) {
        float[] w = {t.hunt, t.investigate, t.flee, t.hide, t.stunned};
        float sum = 0;
        for (float x : w) sum += x;
        float acc = 0;
        for (int i = 0; i < w.length; i++) {
            acc += w[i] / sum;
            if (u < acc) return GroundDisposition.values()[i];
        }
        return GroundDisposition.HUNTER;
    }

    /** Species parameters. Speeds in blocks per tick, distances in blocks, durations in ticks. */
    public static final class Traits {
        // body
        public float width, height, eyeHeight, health, damageMul, groundSpeed, mass;
        // wall locomotion
        public WallStyle style;
        public float crawlSpeedMin, crawlSpeedMax, rangeY, rangeZ;
        public int idleMin, idleMax;
        public float crawlChance, gatherChance, accel, stareWhenStartled;
        // stampede
        public float stampedeWeight, runSpeedMul, fallMul, deathMul, holdOnChance;
        // ground
        public float hunt, investigate, flee, hide, stunned, recoverMul;
        // sound
        public float pitch, volume;
        public int ambientInterval;
        // horde visuals (relative to the Adherent sprite)
        public float spriteLength, spriteHeight, shade;

        Traits body(float w, float h, float eye, float health, float damageMul, float groundSpeed, float mass) {
            this.width = w; this.height = h; this.eyeHeight = eye; this.health = health; this.damageMul = damageMul;
            this.groundSpeed = groundSpeed; this.mass = mass;
            return this;
        }

        Traits wall(WallStyle style, float vMin, float vMax, float rangeY, float rangeZ, int idleMin, int idleMax,
                    float crawlChance, float gatherChance, float accel, float stareWhenStartled) {
            this.style = style; this.crawlSpeedMin = vMin; this.crawlSpeedMax = vMax; this.rangeY = rangeY; this.rangeZ = rangeZ;
            this.idleMin = idleMin; this.idleMax = idleMax; this.crawlChance = crawlChance; this.gatherChance = gatherChance;
            this.accel = accel; this.stareWhenStartled = stareWhenStartled;
            return this;
        }

        Traits stampede(float weight, float runSpeedMul, float fallMul, float deathMul, float holdOnChance) {
            this.stampedeWeight = weight; this.runSpeedMul = runSpeedMul; this.fallMul = fallMul; this.deathMul = deathMul;
            this.holdOnChance = holdOnChance;
            return this;
        }

        Traits ground(float hunt, float investigate, float flee, float hide, float stunned, float recoverMul) {
            this.hunt = hunt; this.investigate = investigate; this.flee = flee; this.hide = hide; this.stunned = stunned;
            this.recoverMul = recoverMul;
            return this;
        }

        Traits voice(float pitch, float volume, int ambientInterval) {
            this.pitch = pitch; this.volume = volume; this.ambientInterval = ambientInterval;
            return this;
        }

        Traits horde(float spriteLength, float spriteHeight, float shade) {
            this.spriteLength = spriteLength; this.spriteHeight = spriteHeight; this.shade = shade;
            return this;
        }
    }
}
