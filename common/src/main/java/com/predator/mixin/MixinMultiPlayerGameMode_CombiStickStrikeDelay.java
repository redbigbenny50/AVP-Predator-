package com.predator.mixin;

import com.predator.client.input.CombiStickDelayedBlockStrike;
import com.predator.common.gameplay.item.combistick.CombiStickItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Holds BLOCK breaking back until the thrust lands, matching the entity strike delay.
 * <p>
 * <strong>⚠⚠ BLOCK DAMAGE DOES NOT GO THROUGH Player.attack.</strong> It runs through
 * {@code MultiPlayerGameMode.startDestroyBlock} and {@code continueDestroyBlock} and the destroy-progress system, which
 * is an entirely separate path. {@code CombiStickDelayedStrike} only ever deferred ENTITY damage, so blocks kept
 * breaking on the click while the spear was still winding back — the symptom looked identical to the bug that had
 * supposedly been fixed.
 * <p>
 * <strong>⚠ Suppressed rather than deferred.</strong> Mining is a CONTINUOUS action, not a single event: there is no
 * one moment to replay later. Blocking progress until the thrust arrives lines the two up without inventing a queue for
 * something that repeats every tick anyway.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class MixinMultiPlayerGameMode_CombiStickStrikeDelay {

    @Inject(method = "startDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void avp_predator$holdStartDestroy(
        BlockPos pos,
        Direction face,
        CallbackInfoReturnable<Boolean> callback
    ) {
        if (CombiStickDelayedBlockStrike.defer(pos, face)) {
            callback.setReturnValue(false);
        }
    }

    /**
     * ⚠⚠ continueDestroyBlock IS SUPPRESSED, NOT DEFERRED — and the two are different problems.
     * {@code startDestroyBlock} is a single event that can be replayed later; continuous mining fires every tick and
     * has no one moment to replay. Blocking it during the wind-up keeps a held button from chipping away at the block
     * before the point lands.
     */
    @Inject(method = "continueDestroyBlock", at = @At("HEAD"), cancellable = true)
    private void avp_predator$holdContinueDestroy(
        BlockPos pos,
        Direction face,
        CallbackInfoReturnable<Boolean> callback
    ) {
        var player = Minecraft.getInstance().player;

        if (player != null && CombiStickItem.isExtendedInHand(player)) {
            callback.setReturnValue(false);
        }
    }

}
