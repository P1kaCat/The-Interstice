package com.interstice.mixin;

import com.interstice.survival.SurvivalRules;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The peaceful-difficulty regeneration (in {@code Player.aiStep}) is off in THE INTERSTICE too. */
@Mixin(Player.class)
public abstract class PlayerMixin {
    @Redirect(method = "aiStep", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/GameRules;getBoolean(Lnet/minecraft/world/level/GameRules$Key;)Z"))
    private boolean interstice$noPeacefulRegeneration(GameRules rules, GameRules.Key<GameRules.BooleanValue> key) {
        Player self = (Player) (Object) this;
        if (key == GameRules.RULE_NATURAL_REGENERATION && SurvivalRules.isInterstice(self.level())) return false;
        return rules.getBoolean(key);
    }
}
