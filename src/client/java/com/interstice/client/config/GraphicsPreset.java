package com.interstice.client.config;

/**
 * Graphics presets. Every value is consumed by a real system:
 * <ul>
 *   <li>lightBudget: max simultaneous Veil dynamic lights (LampLightManager)</li>
 *   <li>lightRange: max distance at which fixtures get a dynamic light</li>
 *   <li>inscattering: Veil light in-scattering (volumetric shafts) on flood lamps</li>
 *   <li>hordeBudget: client-side visual horde instances (HordeRenderer)</li>
 *   <li>hordeMidBudget: instances drawn with the articulated mid-LOD model</li>
 *   <li>particleBudget: ambient/event particles spawned per second</li>
 *   <li>chainLod: chain link mesh resolution (0..3)</li>
 *   <li>chainRange: distance at which chains are still drawn</li>
 *   <li>fogSteps: ray-march steps of the volumetric fog shader</li>
 *   <li>bloomPasses: whether the multi-pass bloom pipeline runs</li>
 * </ul>
 */
public enum GraphicsPreset {
    LOW(10, 80, false, 350, 40, 40, 0, 220, 4, false),
    MEDIUM(20, 110, false, 900, 90, 90, 1, 320, 8, true),
    HIGH(40, 140, true, 1800, 160, 150, 2, 460, 14, true),
    ULTRA(64, 190, true, 3200, 260, 240, 3, 640, 22, true);

    public final int lightBudget;
    public final int lightRange;
    public final boolean inscattering;
    public final int hordeBudget;
    public final int hordeMidBudget;
    public final int particleBudget;
    public final int chainLod;
    public final int chainRange;
    public final int fogSteps;
    public final boolean bloomPasses;

    GraphicsPreset(int lightBudget, int lightRange, boolean inscattering, int hordeBudget, int hordeMidBudget, int particleBudget,
                   int chainLod, int chainRange, int fogSteps, boolean bloomPasses) {
        this.lightBudget = lightBudget;
        this.lightRange = lightRange;
        this.inscattering = inscattering;
        this.hordeBudget = hordeBudget;
        this.hordeMidBudget = hordeMidBudget;
        this.particleBudget = particleBudget;
        this.chainLod = chainLod;
        this.chainRange = chainRange;
        this.fogSteps = fogSteps;
        this.bloomPasses = bloomPasses;
    }

    public GraphicsPreset next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
