package com.interstice.client.mixin;

import com.interstice.client.audio.EfxProcessor;
import com.mojang.blaze3d.audio.Channel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hooks OpenAL source start to apply EFX occlusion low-pass and reverb sends. */
@Mixin(Channel.class)
public abstract class ChannelMixin {
    @Shadow @Final private int source;

    @Inject(method = "play", at = @At("HEAD"))
    private void interstice$beforePlay(CallbackInfo ci) {
        EfxProcessor.onSourcePlay(this.source);
    }
}
