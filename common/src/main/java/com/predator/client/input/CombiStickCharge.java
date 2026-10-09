package com.predator.client.input;

import com.predator.common.gameplay.item.combistick.CombiStickItem;
import net.minecraft.world.entity.player.Player;

/**
 * How far into a throw the player is, read from VANILLA'S USE STATE.
 * <h2>⚠⚠ THIS REPLACES CombiStickChargeHandler</h2> That class polled the LEFT mouse button by hand every frame,
 * because vanilla has no left-hold API — it needed its own Minecraft mixin to tick it and its own packet to tell the
 * server a throw had happened. The throw is a RIGHT-click hold now, which is exactly what {@code startUsingItem} and
 * {@code releaseUsing} already are, so all of that machinery is deleted and the charge is simply read off the player.
 * <p>
 * ⚠ Client-side only, and used for POSE ONLY. The server derives its own power in {@code releaseUsing} from the same
 * numbers, so nothing has to be sent and the two cannot disagree.
 */
public final class CombiStickCharge {

    /** Ticks of hold before the wind-up pose begins. */
    public static final int MIN_CHARGE_TICKS = 6;

    private CombiStickCharge() {
        throw new UnsupportedOperationException();
    }

    /** {@return ticks the throw has been held, or 0 if it is not being held} */
    public static int charge(Player player) {
        if (player == null || !player.isUsingItem() || !(player.getUseItem().getItem() instanceof CombiStickItem)) {
            return 0;
        }

        return player.getUseItem().getUseDuration(player) - player.getUseItemRemainingTicks();
    }

    public static boolean charging(Player player) {
        return charge(player) >= MIN_CHARGE_TICKS;
    }
}
