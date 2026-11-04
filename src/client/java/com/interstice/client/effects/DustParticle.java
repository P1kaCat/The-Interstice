package com.interstice.client.effects;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/** Falling concrete dust and drifting motes. */
public class DustParticle extends TextureSheetParticle {
    private final boolean mote;
    private final float baseAlpha;

    protected DustParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, SpriteSet sprites, boolean mote) {
        super(level, x, y, z, vx, vy, vz);
        this.mote = mote;
        this.xd = vx;
        this.yd = vy;
        this.zd = vz;
        this.pickSprite(sprites);
        if (mote) {
            this.gravity = -0.0005f;
            this.friction = 0.99f;
            this.lifetime = 120 + random.nextInt(160);
            this.quadSize = 0.02f + random.nextFloat() * 0.03f;
            float c = 0.75f + random.nextFloat() * 0.2f;
            this.setColor(c, c, c * 0.97f);
            this.baseAlpha = 0.35f + random.nextFloat() * 0.3f;
        } else {
            this.gravity = 0.06f + random.nextFloat() * 0.05f;
            this.friction = 0.96f;
            this.lifetime = 60 + random.nextInt(80);
            this.quadSize = 0.06f + random.nextFloat() * 0.12f;
            float c = 0.42f + random.nextFloat() * 0.15f;
            this.setColor(c, c * 0.97f, c * 0.92f);
            this.baseAlpha = 0.55f + random.nextFloat() * 0.3f;
        }
        this.alpha = 0f;
    }

    @Override
    public void tick() {
        super.tick();
        float life = (float) age / lifetime;
        float fadeIn = Math.min(1f, age / 8f);
        float fadeOut = 1f - Math.max(0f, (life - 0.7f) / 0.3f);
        this.alpha = baseAlpha * fadeIn * fadeOut;
        if (mote) {
            this.xd += (random.nextFloat() - 0.5f) * 0.0015f;
            this.zd += (random.nextFloat() - 0.5f) * 0.0015f;
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public static final class DustProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public DustProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new DustParticle(level, x, y, z, vx, vy, vz, sprites, false);
        }
    }

    public static final class MoteProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public MoteProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new DustParticle(level, x, y, z, vx, vy, vz, sprites, true);
        }
    }
}
