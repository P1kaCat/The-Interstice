package com.interstice.item;

import com.interstice.core.IntersticeServer;
import com.interstice.entity.AdherentEntity;
import com.interstice.entity.species.Species;
import com.interstice.survival.SurvivalRules;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * The only lethal device: a tuned spike driven into a creature that is lying dazzled or stunned. It resonates
 * with the lighter bodies of the Crawler, the Hollow and the Fractured and kills them; the others have no known
 * lethal countermeasure: against them the spike only rings, wakes the creature and is lost. Single use, rare.
 */
public class ResonanceSpikeItem extends Item {
    public ResonanceSpikeItem(Properties props) {
        super(props);
    }

    public static boolean vulnerable(Species sp) {
        return sp == Species.CRAWLER || sp == Species.HOLLOW || sp == Species.FRACTURED;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (!(target instanceof AdherentEntity e)) return InteractionResult.PASS;
        if (player.level().isClientSide) return InteractionResult.SUCCESS;
        if (player.distanceToSqr(e) > 3.2 * 3.2) return InteractionResult.FAIL;
        boolean down = e.behavior() == AdherentEntity.Behavior.STUNNED;
        player.level().playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.BELL_RESONATE, SoundSource.PLAYERS, 1.6f, vulnerable(e.species()) ? 0.7f : 1.4f);
        IntersticeServer s = IntersticeServer.get();
        if (s != null) s.noise.emit(e.position(), 20, player.level().getGameTime(), player);
        if (down && vulnerable(e.species())) {
            e.hurt(SurvivalRules.severance(player.level(), player), e.getMaxHealth() * 10);
        } else {
            e.wake();
        }
        if (!player.getAbilities().instabuild) stack.shrink(1);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.interstice.resonance_spike.desc").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }
}
