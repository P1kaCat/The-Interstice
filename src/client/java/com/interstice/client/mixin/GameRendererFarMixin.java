package com.interstice.client.mixin;

import com.interstice.client.ClientState;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Level 02 is drawn far beyond the loaded chunks (the farmhouse 1,600 blocks off, the wind farm to the horizon):
 * vanilla's far clip plane (render distance x 4) would cut it all off, so there it is pushed out to 5,000 blocks.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererFarMixin {
    @Inject(method = "getDepthFar", at = @At("RETURN"), cancellable = true)
    private void interstice$farInTheWheat(CallbackInfoReturnable<Float> cir) {
        if (ClientState.inWheatFarm) cir.setReturnValue(Math.max(cir.getReturnValue(), 5000f));
    }
}
