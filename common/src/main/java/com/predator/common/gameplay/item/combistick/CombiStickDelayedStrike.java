package com.predator.common.gameplay.item.combistick;

import com.predator.common.registry.init.item.PredatorItems;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Holds a combi stick's damage back until the spear is actually extended.
 * <h2>⚠⚠ WHY THIS EXISTS</h2> Vanilla applies damage the INSTANT the attack button is pressed, at {@code attackTime} 0.
 * The stab animation peaks its thrust at 30% of the swing — roughly 8 ticks in at the 26-tick swing length — so the
 * target was being hurt about four tenths of a second BEFORE the point arrived. You could break a block or damage a mob
 * while the spear was still winding back.
 * <p>
 * The alternative was compressing the animation so the peak lands on tick 0, which would undo the readable thrust we
 * built. A spear should connect at full extension, so the STRIKE moves rather than the animation.
 * <h2>⚠ Server-side only</h2> The map is keyed by player UUID and only ever touched from the server thread through
 * {@code Player.attack} and {@code Player.tick}. It must not be used client-side — the client's attack is predictive
 * and would double-fire.
 */
public final class CombiStickDelayedStrike {

    /**
     * ⚠ Must equal AVP_COMBI_SWING_TICKS * AVP_THRUST_END from the render mixins (26 * 0.24). If the swing length or
     * the thrust window changes, this has to move with them or the hit drifts off the point again.
     */
    public static final int STRIKE_DELAY_TICKS = 6;

    /** ⚠ Must equal AVP_COMBI_SWING_TICKS in MixinLivingEntity_CombiStickSwingDuration. */
    public static final int SWING_TICKS = 26;

    /**
     * ⚠ A strike is dropped if the target moves further than this from where the player stands when it lands. Without
     * it, holding the button while a mob runs away would still connect from across the room — the delay would have
     * turned a melee weapon into a ranged one.
     */
    private static final double MAX_STRIKE_RANGE_SQR = 36.0;

    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private CombiStickDelayedStrike() {
        throw new UnsupportedOperationException();
    }

    /** {@return true if this attack was deferred and the caller should cancel} */
    public static boolean defer(Player player, Entity target) {
        if (player.level().isClientSide || !CombiStickItem.isExtendedInHand(player)) {
            return false;
        }

        // ⚠ Re-entrancy guard. release() calls Player.attack again, which comes straight back through here; without
        // this the strike would defer itself forever and never land.
        var pending = PENDING.get(player.getUUID());

        if (pending != null && pending.releasing) {
            return false;
        }

        // ⚠ A new swing REPLACES a pending one rather than queueing. Two spears' worth of damage from one animation
        // would be a duplication bug, and the player only ever has one spear out.
        PENDING.put(player.getUUID(), new Pending(target, player.tickCount + STRIKE_DELAY_TICKS));

        return true;
    }

    /** Called every server tick for the player; applies the strike once the thrust has landed. */
    public static void tick(Player player) {
        if (player.level().isClientSide) {
            return;
        }

        var pending = PENDING.get(player.getUUID());

        if (pending == null || pending.releasing || player.tickCount < pending.dueTick) {
            return;
        }

        PENDING.remove(player.getUUID());

        var target = pending.target;

        // ⚠ Everything can change during those 8 ticks: the target dies, despawns, or walks off. All three have to
        // be survivable, because the alternative is a crash or a hit from impossible range.
        if (!target.isAlive() || target.isRemoved()) {
            return;
        }

        if (player.distanceToSqr(target) > MAX_STRIKE_RANGE_SQR) {
            return;
        }

        // ⚠ Dropping the spear mid-thrust cancels the strike — the point never arrived.
        if (!player.getMainHandItem().is(PredatorItems.COMBI_STICK.get())) {
            return;
        }

        pending.releasing = true;
        PENDING.put(player.getUUID(), pending);

        player.attack(target);

        PENDING.remove(player.getUUID());
    }

    /** ⚠ Called on logout so a disconnected player cannot leave an entry behind. */
    public static void forget(Player player) {
        PENDING.remove(player.getUUID());
    }

    private static final class Pending {

        private final Entity target;

        private final int dueTick;

        private boolean releasing;

        private Pending(Entity target, int dueTick) {
            this.target = target;
            this.dueTick = dueTick;
        }
    }
}
