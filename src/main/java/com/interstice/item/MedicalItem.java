package com.interstice.item;

import com.interstice.core.IntersticeServer;
import com.interstice.registry.ModWorld;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Bandages, medical kits and emergency injectors: the only way to recover health in THE INTERSTICE (food never
 * heals there). Applying takes time and makes a little noise; bandages and kits are interrupted when the user
 * takes damage (an injector is too quick to interrupt). Amounts and durations come from the server config.
 */
public class MedicalItem extends Item {
    private final Supplier<Float> heal;
    private final IntSupplier useTicks;
    private final boolean interruptible;
    private final float noise;
    private final SoundEvent sound;

    public MedicalItem(Properties props, Supplier<Float> heal, IntSupplier useTicks, boolean interruptible, float noise, SoundEvent sound) {
        super(props);
        this.heal = heal;
        this.useTicks = useTicks;
        this.interruptible = interruptible;
        this.noise = noise;
        this.sound = sound;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return Math.max(1, useTicks.getAsInt());
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isHurt()) return InteractionResultHolder.fail(stack);
        player.startUsingItem(hand);
        if (!level.isClientSide) level.playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, 0.6f, 1f);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        // hit while applying it: the bandage slips, start again
        if (interruptible && entity.hurtTime == entity.hurtDuration - 1 && entity.hurtTime > 0) {
            entity.stopUsingItem();
            return;
        }
        if (!level.isClientSide && noise > 0 && remaining % 20 == 0 && entity instanceof ServerPlayer sp && ModWorld.isInterstice(level)) {
            IntersticeServer s = IntersticeServer.get();
            if (s != null) s.noise.emit(sp.position(), noise, level.getGameTime(), sp);
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide) {
            entity.heal(heal.get());
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), sound, SoundSource.PLAYERS, 0.5f, 1.3f);
        }
        if (!(entity instanceof Player p && p.getAbilities().instabuild)) stack.shrink(1);
        return stack;
    }
}
