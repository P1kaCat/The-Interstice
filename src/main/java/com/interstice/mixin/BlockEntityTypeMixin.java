package com.interstice.mixin;

import com.interstice.block.StorageBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The Corridor's supply lockers and drums use the barrel's block entity: let the barrel type accept them. */
@Mixin(BlockEntityType.class)
public abstract class BlockEntityTypeMixin {
    @Inject(method = "isValid", at = @At("HEAD"), cancellable = true)
    private void interstice$acceptStorage(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this == BlockEntityType.BARREL && state.getBlock() instanceof StorageBlock) cir.setReturnValue(true);
    }
}
