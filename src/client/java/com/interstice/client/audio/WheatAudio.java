package com.interstice.client.audio;

import com.interstice.client.ClientState;
import com.interstice.registry.ModSounds;
import com.interstice.world.wheat.WheatLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * Level 02's soundscape: the open-field wind (a constant bed), the wheat rustling (its volume follows, at the
 * listener's position, the same waves and gusts the wheat shader draws, so the rustle swells as a visible gust
 * passes), and the turbines (a positional loop on the hub of the two nearest, one rotor turn long, so the blade
 * swishes keep time with the drawn blades), and now and then a single recorded gust going by from a random side.
 * No music: long stretches of near silence between gusts.
 */
public final class WheatAudio {
    private static LoopSound wind, rustle;
    private static final Map<Long, LoopSound> turbines = new HashMap<>();
    /** Ticks to the next single gust. */
    private static int gustIn = 200;

    private WheatAudio() {}

    /** The wheat shader's wave * gust at this point (see wheat.vsh), 0..1. */
    public static double gustAt(double x, double z, double time) {
        double phase = (x * 0.8 + z * 0.6) * 0.075 - time * 1.35;
        double wave = Math.sin(phase) * 0.5 + 0.5;
        double gust = Math.sin(x * 0.011 + z * 0.019 - time * 0.32) * 0.5 + 0.5;
        return wave * (0.35 + 0.65 * gust);
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!ClientState.inWheatFarm || mc.player == null) {
            stop();
            return;
        }
        SoundManager sm = mc.getSoundManager();
        if (wind == null || !sm.isActive(wind)) {
            wind = new LoopSound(ModSounds.WHEAT_WIND, () -> 0.55, false);
            sm.play(wind);
        }
        if (rustle == null || !sm.isActive(rustle)) {
            rustle = new LoopSound(ModSounds.WHEAT_RUSTLE, () -> {
                Vec3 p = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
                return 0.12 + 0.62 * gustAt(p.x, p.z, ClientState.time / 20.0);
            }, false);
            sm.play(rustle);
        }
        // now and then a single gust goes by, from a random side (recorded wind, CC0; see docs/AUDIO_CREDITS.md)
        if (--gustIn <= 0) {
            RandomSource r = mc.level.getRandom();
            gustIn = 260 + r.nextInt(520);
            double a = r.nextDouble() * Math.PI * 2;
            Vec3 c = mc.gameRenderer.getMainCamera().getPosition();
            sm.play(new SimpleSoundInstance(ModSounds.WHEAT_GUST.getLocation(), SoundSource.AMBIENT,
                    0.35f + r.nextFloat() * 0.35f, 0.85f + r.nextFloat() * 0.3f, r, false, 0,
                    SoundInstance.Attenuation.NONE, c.x + Math.cos(a) * 8, c.y + 2, c.z + Math.sin(a) * 8, false));
        }
        // the two nearest turbines within earshot
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        long[] best = {Long.MIN_VALUE, Long.MIN_VALUE};
        double[] bestD = {Double.MAX_VALUE, Double.MAX_VALUE};
        int c0 = (int) Math.floor((cam.z - 300) / WheatLayout.TURBINE_CELL), c1 = (int) Math.floor((cam.z + 300) / WheatLayout.TURBINE_CELL);
        for (int c = c0; c <= c1; c++)
            for (int side = -1; side <= 1; side += 2)
                for (int row = 0; row < 3; row++) {
                int[] t = WheatLayout.turbine(c, side, row);
                if (t == null) continue;
                double d = Math.hypot(t[0] - cam.x, t[1] - cam.z);
                if (d > 260) continue;
                long key = ((long) c << 8) | ((side + 1) << 4) | row;
                if (d < bestD[0]) { bestD[1] = bestD[0]; best[1] = best[0]; bestD[0] = d; best[0] = key; }
                else if (d < bestD[1]) { bestD[1] = d; best[1] = key; }
            }
        turbines.entrySet().removeIf(e -> {
            boolean keep = e.getKey() == best[0] || e.getKey() == best[1];
            if (!keep) e.getValue().finish();
            return !keep;
        });
        for (long key : best) {
            if (key == Long.MIN_VALUE || turbines.containsKey(key)) continue;
            int c = (int) (key >> 8), side = (int) ((key >> 4) & 15) - 1, row = (int) (key & 15);
            int[] t = WheatLayout.turbine(c, side, row);
            if (t == null) continue;
            LoopSound s = new LoopSound(ModSounds.TURBINE_WHOOSH, () -> 9.0, true);   // heard out to ~140 blocks
            s.tx = t[0] + 0.5;
            s.ty = WheatLayout.groundY(t[0], t[1]) + WheatLayout.TURBINE_HEIGHT;
            s.tz = t[1] + 0.5;
            sm.play(s);
            turbines.put(key, s);
        }
    }

    public static void stop() {
        if (wind != null) wind.finish();
        if (rustle != null) rustle.finish();
        wind = rustle = null;
        for (LoopSound s : turbines.values()) s.finish();
        turbines.clear();
    }
}
