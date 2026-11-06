package com.interstice.client.mixin;

import com.interstice.client.effects.RiftTransition;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Crossing the white rift, the "loading terrain" screen is just white: the light goes on without a break. */
@Mixin(ReceivingLevelScreen.class)
public abstract class ReceivingLevelScreenMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void interstice$white(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (RiftTransition.whiteLoadingScreen()) {
            g.fill(0, 0, g.guiWidth(), g.guiHeight(), 0xFFFFFFFF);
            ci.cancel();
        }
    }
}
