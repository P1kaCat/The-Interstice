package com.interstice.item;

import com.interstice.entity.ThrownLure;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Rattle lure: a tin full of bolts on a clockwork. Thrown, it lands and rattles for {@link ThrownLure#RATTLE_TICKS}
 * ticks; every creature that hears it goes to look (the Hollow and the Burden from far away). It does not hide the
 * thrower: throwing is itself a small noise, and smell still leads to where you are.
 */
public class LureItem extends Item {
    public LureItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.5f, 0.5f);
        if (!level.isClientSide) {
            ThrownLure lure = new ThrownLure(level, player);
            lure.setItem(stack.copyWithCount(1));
            lure.shootFromRotation(player, player.getXRot(), player.getYRot(), 0f, 1.1f, 1.0f);
            level.addFreshEntity(lure);
        }
        player.getCooldowns().addCooldown(this, 30);
        if (!player.getAbilities().instabuild) stack.shrink(1);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
