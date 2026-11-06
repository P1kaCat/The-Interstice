package com.interstice.client.mixin;

import com.interstice.client.ClientState;
import com.interstice.client.render.atmosphere.CorridorLightmap;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** THE INTERSTICE's lightmap (see {@link CorridorLightmap}): no gamma lift, block light darkened. */
@Mixin(LightTexture.class)
public abstract class LightTextureMixin {
    /** The second option read in updateLightTexture is the brightness (gamma); the first is the darkness effect scale. */
    @Redirect(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;", ordinal = 1))
    private Object interstice$noGamma(OptionInstance<?> option) {
        return ClientState.inInterstice || ClientState.inThreshold ? (Object) Double.valueOf(0.0) : option.get();
    }

    @Redirect(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/NativeImage;setPixelRGBA(III)V"))
    private void interstice$darken(NativeImage image, int blockIndex, int skyIndex, int abgr) {
        // the full-sky row is left alone: the world here has no sky light (it never uses it), while the GUI, held-item
        // icons, text and full-bright things are drawn with block 15 / sky 15 and must keep their normal brightness
        boolean world = (ClientState.inInterstice || ClientState.inThreshold) && skyIndex < 15;
        image.setPixelRGBA(blockIndex, skyIndex, world ? CorridorLightmap.adjust(blockIndex, abgr) : abgr);
    }
}
