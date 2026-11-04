package com.interstice.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Indirection that lets common block code notify client systems without
 * referencing client classes (dedicated-server safe).
 */
public final class LampHooks {
    public interface Listener {
        void onLampSeen(Level level, BlockPos pos, LampKind kind);
    }

    private static volatile Listener listener;

    private LampHooks() {}

    public static void setListener(Listener l) {
        listener = l;
    }

    public static void notifySeen(Level level, BlockPos pos, LampKind kind) {
        Listener l = listener;
        if (l != null && level.isClientSide) l.onLampSeen(level, pos, kind);
    }
}
