package com.interstice.item;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Right-click: recharges the most drained flashlight in the inventory by {@link #CHARGE} (10 minutes). */
public class BatteryItem extends Item {
    public static final int CHARGE = 600;

    public BatteryItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack battery = player.getItemInHand(hand);
        ItemStack best = ItemStack.EMPTY;
        for (ItemStack s : player.getInventory().items) {
            if (s.getItem() instanceof FlashlightItem && s.getDamageValue() > 0 && (best.isEmpty() || s.getDamageValue() > best.getDamageValue())) best = s;
        }
        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof FlashlightItem && off.getDamageValue() > 0 && (best.isEmpty() || off.getDamageValue() > best.getDamageValue())) best = off;
        if (best.isEmpty()) return InteractionResultHolder.fail(battery);
        if (!level.isClientSide) {
            best.setDamageValue(Math.max(0, best.getDamageValue() - CHARGE));
            if (!player.getAbilities().instabuild) battery.shrink(1);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.PLAYERS, 0.4f, 1.8f);
        return InteractionResultHolder.sidedSuccess(battery, level.isClientSide);
    }
}
