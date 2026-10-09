package com.predator.client.input;

import com.predator.common.gameplay.item.combistick.CombiStickDelayedStrike;
import com.predator.common.gameplay.item.combistick.CombiStickItem;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

/**
 * Holds a combi stick's BLOCK break until the thrust lands.
 * <h2>⚠⚠ WHY SUPPRESSION WAS NOT ENOUGH</h2> The first attempt cancelled destroy progress while {@code attackAnim > 0}.
 * That never fired: at the moment {@code startDestroyBlock} runs, the swing has not begun and {@code attackAnim} is
 * still 0. In CREATIVE the block is destroyed outright on that very call, so it broke instantly every time and the
 * guard was dead code.
 * <p>
 * So the break is DEFERRED the same way the entity strike is: cancel it, remember what was hit, and replay it once the
 * point has arrived.
 * <h2>⚠ Client-side</h2> Block breaking is client-driven — {@code MultiPlayerGameMode} sends the packets. This must not
 * be mirrored on the server or the break would happen twice.
 */
public final class CombiStickDelayedBlockStrike {

    @Nullable
    private static BlockPos pendingPos;

    @Nullable
    private static Direction pendingFace;

    private static int dueTick;

    private static boolean releasing;

    private CombiStickDelayedBlockStrike() {
        throw new UnsupportedOperationException();
    }

    /** {@return true if this break was deferred and the caller should cancel} */
    public static boolean defer(BlockPos pos, Direction face) {
        // ⚠ The re-entrancy guard: release() calls startDestroyBlock again and comes straight back through here.
        if (releasing) {
            return false;
        }

        var player = Minecraft.getInstance().player;

        if (player == null || !CombiStickItem.isExtendedInHand(player)) {
            return false;
        }

        // ⚠ A new click REPLACES the pending break rather than queueing. Holding the button down calls this every
        // tick, and a queue would fire one break per tick once the first delay elapsed.
        pendingPos = pos;
        pendingFace = face;
        dueTick = player.tickCount + CombiStickDelayedStrike.STRIKE_DELAY_TICKS;

        return true;
    }

    /** Called each client tick; performs the break once the thrust has landed. */
    public static void tick() {
        if (pendingPos == null) {
            return;
        }

        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;

        if (player == null || minecraft.gameMode == null) {
            avp_predator$clear();

            return;
        }

        if (player.tickCount < dueTick) {
            return;
        }

        var pos = pendingPos;
        var face = pendingFace;

        avp_predator$clear();

        // ⚠ Everything can change in those few ticks: the spear leaves the hand, or the block is already gone.
        if (!CombiStickItem.isExtendedInHand(player) || player.level().getBlockState(pos).isAir()) {
            return;
        }

        releasing = true;

        try {
            minecraft.gameMode.startDestroyBlock(pos, face == null ? Direction.UP : face);
        } finally {
            // ⚠ finally, not a trailing assignment: if the break throws, a stuck flag would disable the delay for
            // the rest of the session and the bug would come back looking intermittent.
            releasing = false;
        }
    }

    private static void avp_predator$clear() {
        pendingPos = null;
        pendingFace = null;
    }
}
