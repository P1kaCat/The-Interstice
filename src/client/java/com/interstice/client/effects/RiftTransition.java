package com.interstice.client.effects;

import com.interstice.client.ClientState;
import com.interstice.world.threshold.ThresholdLayout;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Crossing the white rift: the screen goes white as the player steps into the light, stays white while the next
 * level loads (the loading screen is painted white too, {@code ReceivingLevelScreenMixin}), and the new level fades
 * in from white once it is there. No loading screen is ever seen.
 */
public final class RiftTransition {
    /** 0..1, how white the screen is. */
    public static float white;
    /** A white-out ordered by the server (Level 02's path keeper), 0..1. */
    public static float scriptedWhite;
    private static boolean crossing;
    private static ResourceKey<Level> from;
    private static long arrived = -1;

    private RiftTransition() {}

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        ResourceKey<Level> dim = mc.level.dimension();
        if (ClientState.inThreshold) {
            double x = mc.player.getX(), y = mc.player.getY(), z = mc.player.getZ();
            // the light rises over the last blocks before the doorway, total in it
            double toRift = ThresholdLayout.RIFT_Z - 0.3 - z;
            float target = (float) Math.max(0, Math.min(1, 1 - toRift / 2.5));
            if (ThresholdLayout.inRift(x, y, z)) {
                target = 1f;
                if (!crossing) {
                    crossing = true;
                    from = dim;
                }
            }
            if (!crossing) white += (target * 0.85f - white) * 0.35f;
            else white = 1f;
        }
        if (crossing && from != null && dim != from) {
            // arrived: hold the white a moment while the chunks come in, then fade
            if (arrived < 0) arrived = mc.level.getGameTime();
            long held = mc.level.getGameTime() - arrived;
            if (held > 10) white = Math.max(0f, white - 0.035f);
            if (white <= 0f) {
                crossing = false;
                from = null;
                arrived = -1;
            }
        } else if (!ClientState.inThreshold && !crossing) {
            white = Math.max(0f, white - 0.05f);
        }
    }

    public static void renderHud(GuiGraphics g, DeltaTracker delta) {
        float w = Math.max(white, scriptedWhite);
        if (w <= 0.003f) return;
        int a = Math.round(Math.min(1f, w) * 255);
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), (a << 24) | 0xFFFFFF);
    }

    /** While crossing, the loading screen is all white. */
    public static boolean whiteLoadingScreen() {
        return crossing;
    }
}
