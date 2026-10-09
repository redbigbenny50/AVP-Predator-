package com.predator.client.input;

import com.predator.Predator;
import com.predator.client.net.ClientNetState;
import com.predator.common.network.packet.C2SNetStruggleMashPayload;
import net.minecraft.client.Minecraft;

/**
 * Client half of the net struggle bar.
 * <h2>⚠⚠ DELIBERATELY IDENTICAL TO avp_alien'S HostStruggleInputHandler</h2> Same keys — SPACE or LEFT-CLICK — same
 * edge detection, same swallowing of the click. His ruling was that a netted player struggles "like you can struggle
 * free from a facehugger or a drone capturing you", and that only holds if it is the same muscle memory, not a similar
 * one.
 * <h2>Why the keys are consumed</h2> ⚠ Both are swallowed so vanilla does not additionally process them. A left-click
 * would otherwise swing the player's weapon while they mash, and a jump would fire every time — turning a struggle into
 * an accidental attack combo.
 * <h2>No grab state needs syncing</h2> The client already knows it is netted: {@code ClientNetState} is populated by
 * the same payload that drives the overlay. Rate-limiting lives on the server, so this only reports edges.
 */
public final class NetStruggleInputHandler {

    private static boolean jumpWasDown;

    private static boolean attackWasDown;

    private NetStruggleInputHandler() {
        throw new UnsupportedOperationException();
    }

    public static void handle(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null || minecraft.screen != null) {
            jumpWasDown = false;
            attackWasDown = false;

            return;
        }

        if (!ClientNetState.isNetted(minecraft.player.getId())) {
            jumpWasDown = false;
            attackWasDown = false;

            return;
        }

        var jumpDown = minecraft.options.keyJump.isDown();
        var attackDown = minecraft.options.keyAttack.isDown();
        var mashed = (jumpDown && !jumpWasDown) || (attackDown && !attackWasDown);

        jumpWasDown = jumpDown;
        attackWasDown = attackDown;

        if (mashed) {
            Predator.MOD.networking().sendToServer(C2SNetStruggleMashPayload.INSTANCE);
        }

        while (minecraft.options.keyAttack.consumeClick()) {
            // Swallow the swing: the click is a struggle input, not an attack.
        }

        while (minecraft.options.keyJump.consumeClick()) {
            // Swallow the jump: it is a struggle input.
        }
    }
}
