package com.interstice.mixin;

import com.interstice.survival.SurvivalRules;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Food restores hunger but never health in THE INTERSTICE: the hunger-based natural regeneration is off there. */
@Mixin(FoodData.class)
public abstract class FoodDataMixin {
    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/GameRules;getBoolean(Lnet/minecraft/world/level/GameRules$Key;)Z"))
    private boolean interstice$noFoodRegeneration(GameRules rules, GameRules.Key<GameRules.BooleanValue> key, Player player) {
        if (key == GameRules.RULE_NATURAL_REGENERATION && SurvivalRules.isInterstice(player.level())) return false;
        return rules.getBoolean(key);
    }
}
