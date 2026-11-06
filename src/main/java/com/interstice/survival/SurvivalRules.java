package com.interstice.survival;

import com.interstice.Interstice;
import com.interstice.registry.ModBlocks;
import com.interstice.registry.ModWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Survival rules of THE INTERSTICE, shared by the mixins, the creatures and the items.
 * <ul>
 *   <li>Food restores hunger only: natural regeneration from a full hunger bar is disabled in every Interstice
 *       dimension ({@code FoodDataMixin}, {@code PlayerMixin}), and the Regeneration effect cannot be applied there
 *       ({@code LivingEntityMixin}), so golden apples, suspicious stew or potions cannot bypass it. Healing comes
 *       from the medical items.</li>
 *   <li>The creatures cannot be killed by ordinary combat: only {@link #canHarmCreature} sources hurt them.</li>
 * </ul>
 */
public final class SurvivalRules {
    private SurvivalRules() {}

    /** Damage dealt by the rare species-specific neutralisation device (the resonance spike). */
    public static final ResourceKey<DamageType> SEVERANCE = ResourceKey.create(Registries.DAMAGE_TYPE, Interstice.id("severance"));

    /** True in every dimension of THE INTERSTICE (one today). */
    public static boolean isInterstice(Level level) {
        return ModWorld.isAnyLevel(level);   // the same rules in all five levels
    }

    public static DamageSource severance(Level level, Entity attacker) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(SEVERANCE), attacker);
    }

    /**
     * Whether a damage source may hurt a Corridor creature. Allowed: the severance device, /kill and the void
     * (anything that bypasses invulnerability), and the environment the creatures already live with (their own
     * falls, suffocation, drowning). Everything a player can do with ordinary means is refused: melee, arrows,
     * tridents, explosions, fire and lava, thorns, potions, other mobs.
     */
    public static boolean canHarmCreature(DamageSource source) {
        if (source.is(SEVERANCE)) return true;
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return true;   // /kill, the void
        if (source.getEntity() != null || source.getDirectEntity() != null) return false;
        return source.is(DamageTypes.FALL) || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.DROWN)
                || source.is(DamageTypes.CRAMMING);
    }

    /**
     * How much louder a footstep is on this surface (creature hearing). Metal rings, debris crunches, soft
     * floors muffle.
     */
    public static float footstepLoudness(Level level, BlockPos feet) {
        BlockState s = level.getBlockState(feet.below());
        if (s.isAir()) s = level.getBlockState(feet.below(2));
        if (s.is(ModBlocks.METAL_GRATE) || s.is(ModBlocks.RUSTED_PLATE) || s.is(ModBlocks.DARK_METAL)
                || s.is(Blocks.IRON_BLOCK) || s.is(Blocks.IRON_TRAPDOOR) || s.is(Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE)
                || s.is(Blocks.CUT_COPPER) || s.is(Blocks.WAXED_CUT_COPPER) || s.is(Blocks.WEATHERED_CUT_COPPER)) return 1.6f;
        if (s.is(ModBlocks.CONCRETE_RUBBLE) || s.is(Blocks.GRAVEL) || s.is(Blocks.SUSPICIOUS_GRAVEL)) return 1.3f;
        BlockState at = level.getBlockState(feet);
        if (at.is(BlockTags.WOOL_CARPETS) || at.is(Blocks.MOSS_CARPET) || s.is(BlockTags.WOOL) || s.is(Blocks.MOSS_BLOCK)) return 0.65f;
        if (s.is(BlockTags.PLANKS) || s.is(BlockTags.WOODEN_SLABS)) return 1.15f;   // old floorboards creak
        return 1f;
    }
}
