package com.interstice.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

/**
 * A battery flashlight. Right-click toggles it; while lit in a hand it drains one charge point per second
 * (durability = charge, {@link #CAPACITY} = 20 minutes) and lights the way (a Veil spot light on the client),
 * but a lit player is a light source to every creature's eyes ({@code Perception}). A battery recharges it.
 */
public class FlashlightItem extends Item {
    public static final int CAPACITY = 1200;

    public FlashlightItem(Properties props) {
        super(props.durability(CAPACITY));
    }

    public static boolean isLit(ItemStack stack) {
        if (!(stack.getItem() instanceof FlashlightItem) || stack.getDamageValue() >= stack.getMaxDamage() - 1) return false;
        CustomData d = stack.get(DataComponents.CUSTOM_DATA);
        return d != null && d.copyTag().getBoolean("lit");
    }

    public static void setLit(ItemStack stack, boolean lit) {
        CompoundTag t = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        t.putBoolean("lit", lit);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
    }

    /** The lit flashlight held by this player (main hand first), or empty. */
    public static ItemStack litIn(Player p) {
        if (isLit(p.getMainHandItem())) return p.getMainHandItem();
        if (isLit(p.getOffhandItem())) return p.getOffhandItem();
        return ItemStack.EMPTY;
    }

    public static boolean isLitIn(Player p) {
        return !litIn(p).isEmpty();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.getDamageValue() >= stack.getMaxDamage() - 1) {
            level.playSound(null, player.blockPosition(), SoundEvents.LEVER_CLICK, SoundSource.PLAYERS, 0.4f, 0.6f);
            return InteractionResultHolder.fail(stack);
        }
        boolean lit = !isLit(stack);
        if (!level.isClientSide) setLit(stack, lit);
        level.playSound(null, player.blockPosition(), SoundEvents.LEVER_CLICK, SoundSource.PLAYERS, 0.5f, lit ? 1.4f : 1.0f);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Draining (a durability change every second) or toggling must not replay the equip animation. */
    @Override
    public boolean allowComponentsUpdateAnimation(Player player, InteractionHand hand, ItemStack oldStack, ItemStack newStack) {
        return false;
    }

    /** Server, once a second: drains a lit flashlight held in a hand; a flat one goes out. */
    public static void drain(Player p) {
        ItemStack s = litIn(p);
        if (s.isEmpty() || p.getAbilities().instabuild) return;
        s.setDamageValue(Math.min(s.getMaxDamage() - 1, s.getDamageValue() + 1));
        if (s.getDamageValue() >= s.getMaxDamage() - 1) setLit(s, false);
    }
}
