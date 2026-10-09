package com.predator.mixin;

import com.mojang.datafixers.util.Either;
import com.predator.common.gameplay.hunt.HuntDirector;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Refuses sleep on the nights a Hunter is coming — [stated] "you cant skip the hunt". Returns vanilla's
 * {@code OTHER_PROBLEM}, which carries no message of its own, and sends ours to the action bar instead.
 */
@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayer_HuntSleep {

    @Inject(method = "startSleepInBed", at = @At("HEAD"), cancellable = true)
    private void avp_predator$refuseSleepOnHuntNight(
        BlockPos pos,
        CallbackInfoReturnable<Either<Player.BedSleepingProblem, Unit>> callback
    ) {
        var self = (ServerPlayer) (Object) this;

        if (HuntDirector.refusesSleep(self)) {
            self.displayClientMessage(
                Component.literal("You cannot rest. Something is watching you.").withStyle(ChatFormatting.DARK_RED),
                true
            );
            callback.setReturnValue(Either.left(Player.BedSleepingProblem.OTHER_PROBLEM));
        }
    }
}
