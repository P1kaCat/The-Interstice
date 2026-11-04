package com.interstice.client.ui;

import com.interstice.client.ClientState;
import com.interstice.client.audio.AudioDirector;
import com.interstice.client.audio.EfxProcessor;
import com.interstice.client.audio.VoicePlayer;
import com.interstice.client.config.ClientConfig;
import com.interstice.client.effects.DustEffects;
import com.interstice.client.render.atmosphere.IntersticeSkyRenderer;
import com.interstice.client.render.chains.ChainRenderer;
import com.interstice.client.render.creatures.HordeRenderer;
import com.interstice.client.render.veil.LampLightManager;
import com.interstice.client.render.veil.VeilIntegration;
import com.interstice.entity.AdherentEntity;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * Minimal HUD: no meters, no markers. Only (a) the optional whisper subtitle,
 * drawn as a faint lowercase line low on screen, and (b) development
 * diagnostics when enabled.
 */
public final class IntersticeHud {
    private static int realCreatures;
    private static long lastCount;

    private IntersticeHud() {}

    public static void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.options.hideGui) return;
        drawWhisper(g, mc);
        if (ClientConfig.INSTANCE.diagnostics || ClientState.debug) drawDiagnostics(g, mc);
    }

    private static void drawWhisper(GuiGraphics g, Minecraft mc) {
        if (!ClientConfig.INSTANCE.voiceSubtitles || VoicePlayer.subtitle.isEmpty()) return;
        long dt = mc.level.getGameTime() - VoicePlayer.subtitleAt;
        if (dt < 0 || dt > 70) return;
        float a = dt < 10 ? dt / 10f : dt > 45 ? 1f - (dt - 45) / 25f : 1f;
        int alpha = (int) (Mth.clamp(a, 0, 1) * 150);
        if (alpha < 8) return;
        Font f = mc.font;
        String s = VoicePlayer.subtitle;
        int w = f.width(s);
        int x = (g.guiWidth() - w) / 2, y = (int) (g.guiHeight() * 0.72f);
        g.drawString(f, s, x, y, (alpha << 24) | 0xB8BCC2, false);
    }

    private static void drawDiagnostics(GuiGraphics g, Minecraft mc) {
        long now = mc.level.getGameTime();
        if (now - lastCount >= 20) {
            lastCount = now;
            int n = 0;
            for (Entity e : mc.level.entitiesForRendering()) if (e instanceof AdherentEntity) n++;
            realCreatures = n;
        }
        String[] lines = {
                "THE INTERSTICE / diagnostics",
                "fps " + mc.getFps() + "  preset " + ClientConfig.INSTANCE.preset + "  post " + VeilIntegration.activePipeline(),
                "veil lights " + LampLightManager.activeLights + " / fixtures known " + LampLightManager.knownFixtures,
                "chains " + ChainRenderer.chainsVisible + "  links " + ChainRenderer.linksDrawn + "  sky quads " + IntersticeSkyRenderer.quadsDrawn,
                "horde sim " + HordeRenderer.simulated + "  far " + HordeRenderer.farDrawn + "  mid " + HordeRenderer.midDrawn + "  ambient " + HordeRenderer.ambientDrawn,
                "real creatures (client) " + realCreatures + "  particles/s " + DustEffects.spawnedLastSecond,
                String.format("creature mesh skinning %.2f ms/s, %d vertices/s",
                        com.interstice.client.render.creatures.mesh.SkinnedMeshPose.nanosLastSecond / 1e6,
                        com.interstice.client.render.creatures.mesh.SkinnedMeshPose.verticesLastSecond),
                "audio one-shots " + AudioDirector.oneShots + "  efx " + (EfxProcessor.available() ? "on (" + EfxProcessor.processed + ")" : "off"),
                String.format("phase %s %.0f%%  quake %.2f prem %.2f silence %.2f emer %.2f", ClientState.phase, ClientState.phaseProgress() * 100,
                        ClientState.quake, ClientState.premonition, Math.max(ClientState.silence, AudioDirector.silenceLevel), ClientState.emergency),
        };
        int y = 4;
        for (String l : lines) {
            g.fill(2, y - 1, 4 + mc.font.width(l) + 2, y + 9, 0x88000000);
            g.drawString(mc.font, l, 4, y, 0xFFC9CDD2, false);
            y += 10;
        }
        for (String l : ClientState.serverDebug.split("\n")) {
            if (l.isEmpty()) continue;
            g.fill(2, y - 1, 4 + mc.font.width(l) + 2, y + 9, 0x88000000);
            g.drawString(mc.font, "srv " + l, 4, y, 0xFFE0A090, false);
            y += 10;
        }
    }
}
