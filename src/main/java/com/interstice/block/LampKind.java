package com.interstice.block;

/** Light fixture families. Colours are linear RGB used by the Veil light manager. */
public enum LampKind {
    INDUSTRIAL(0.62f, 0.78f, 1.0f, 11f, 1.6f),
    EMERGENCY(1.0f, 0.08f, 0.04f, 8f, 1.2f),
    FLOOD(0.85f, 0.92f, 1.0f, 46f, 2.6f),
    SODIUM(1.0f, 0.62f, 0.28f, 13f, 1.4f);

    public final float r, g, b, radius, brightness;

    LampKind(float r, float g, float b, float radius, float brightness) {
        this.r = r;
        this.g = g;
        this.b = b;
        this.radius = radius;
        this.brightness = brightness;
    }
}
