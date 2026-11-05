package com.interstice.item;

import com.interstice.core.IntersticeServer;
import com.interstice.entity.AdherentEntity;
import com.interstice.entity.ai.Perception;
import com.interstice.entity.species.Species;
import com.interstice.network.ModNetwork;
import com.interstice.registry.ModWorld;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Magnesium flash: a single blinding burst. Creatures on the ground within {@link #RANGE} blocks that have the
 * user in their line of sight and roughly in front of them are dazzled and lie still for a while
 * ({@link AdherentEntity#deviceStun}); creatures on the walls flinch and may let go. How long depends on the
 * species' eyes: the dark-adapted Watcher is blinded the longest, the Burden and the Crawler shake it off fast,
 * the near-blind Hollow does not care. The bang is loud: every ear around hears where it came from.
 */
public class FlashFlareItem extends Item {
    public static final double RANGE = 12.0;
    public static final int BASE_STUN = 120;

    public FlashFlareItem(Properties props) {
        super(props);
    }

    /** Stun multiplier per species: how much the light hurts those eyes. */
    public static float sensitivity(Species sp) {
        return switch (sp) {
            case WATCHER -> 1.6f;
            case CLINGER -> 1.2f;
            case ADHERENT, STRIDER, FRACTURED -> 1.0f;
            case CRAWLER -> 0.35f;
            case BURDEN -> 0.4f;
            case HOLLOW -> 0f;
        };
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 2.0f, 0.6f);
        if (!level.isClientSide && level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
            flash(sl, sp);
            player.getCooldowns().addCooldown(this, 40);
            if (!player.getAbilities().instabuild) stack.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Returns how many creatures were dazzled. */
    public static int flash(ServerLevel level, ServerPlayer p) {
        Vec3 at = p.getEyePosition();
        long now = level.getGameTime();
        if (ModWorld.isInterstice(level)) {
            IntersticeServer s = IntersticeServer.get();
            if (s != null) s.noise.emit(p.position(), 26, now, p);
            for (ServerPlayer o : level.players()) {
                if (o.distanceToSqr(at) < 96 * 96) {
                    ModNetwork.send(o, new ModNetwork.EnvEvent(ModNetwork.EnvEvent.FLASH, now, 14, 1f, 0, at.x, at.y, at.z));
                }
            }
        }
        int n = 0;
        for (AdherentEntity e : level.getEntitiesOfClass(AdherentEntity.class, new AABB(at, at).inflate(RANGE))) {
            float k = sensitivity(e.species());
            if (k <= 0 || !e.isAlive()) continue;
            Vec3 eye = e.getEyePosition();
            double d = eye.distanceTo(at);
            if (d > RANGE || !Perception.clear(level, e, eye, at)) continue;
            // only eyes turned towards the flash are blinded (the Fractured's gaze is offset like its sight)
            Vec3 to = at.subtract(eye).normalize();
            float gaze = e.getYHeadRot() + e.species().t.fovOffset;
            Vec3 look = Vec3.directionFromRotation(0, gaze);
            double cos = to.x * look.x + to.z * look.z;
            if (cos < Math.cos(Math.toRadians(e.species().t.periphery / 2.0)) && d > 3) continue;
            float near = (float) Mth.clamp(1.3 - d / RANGE, 0.4, 1.0);
            int ticks = (int) (BASE_STUN * k * near);
            if (e.mode() == AdherentEntity.Mode.GROUND) e.deviceStun(ticks);
            else e.flinch();
            n++;
        }
        return n;
    }
}
