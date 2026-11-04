package com.interstice.event.stampede;

/**
 * Phases of the Stampede. Durations are in ticks; {@code jitter} adds a random
 * amount so the rhythm is never exactly the same twice. The same enum is used
 * by the client to derive presentation from (phase, phase start tick).
 */
public enum StampedePhase {
    IDLE(0, 0),
    PREMONITION(320, 160),
    STRUCTURAL_DISTURBANCE(200, 60),
    EARTHQUAKE(200, 40),
    HORDE_APPROACH(260, 60),
    HORDE_PASSAGE(520, 120),
    CREATURE_FALLS(240, 60),
    GROUND_SEARCH(1100, 400),
    AFTERMATH(400, 100),
    RECOVERY(1200, 400);

    public final int baseDuration, jitter;

    StampedePhase(int baseDuration, int jitter) {
        this.baseDuration = baseDuration;
        this.jitter = jitter;
    }

    public StampedePhase next() {
        return this == RECOVERY ? IDLE : values()[ordinal() + 1];
    }

    public boolean atLeast(StampedePhase p) {
        return ordinal() >= p.ordinal();
    }

    public boolean between(StampedePhase a, StampedePhase b) {
        return ordinal() >= a.ordinal() && ordinal() <= b.ordinal();
    }

    public static StampedePhase byName(String s) {
        for (StampedePhase p : values()) if (p.name().equalsIgnoreCase(s)) return p;
        return null;
    }
}
