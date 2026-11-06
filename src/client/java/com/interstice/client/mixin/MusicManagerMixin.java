package com.interstice.client.mixin;

import com.interstice.client.ClientState;
import net.minecraft.client.sounds.MusicManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * No vanilla background music in THE INTERSTICE: the music manager is stopped (any track playing when the player
 * enters fades out with the sound engine's stop) and does not pick a new one while the player is inside. The
 * soundscape there is the {@code AudioDirector}'s. Other dimensions are untouched; on leaving, vanilla resumes
 * its normal scheduling (with its usual delay before the next track).
 */
@Mixin(MusicManager.class)
public abstract class MusicManagerMixin {
    @Shadow
    public abstract void stopPlaying();

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void interstice$silence(CallbackInfo ci) {
        if (ClientState.inInterstice || ClientState.inAnyLevel) {
            stopPlaying();
            ci.cancel();
        }
    }
}
