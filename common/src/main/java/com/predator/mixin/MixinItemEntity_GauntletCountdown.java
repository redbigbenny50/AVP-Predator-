package com.predator.mixin;

import com.predator.common.gameplay.gauntlet.destruct.GauntletSelfDestruct;
import com.predator.common.registry.init.item.PredatorItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A counting gauntlet dropped on the ground keeps counting — and goes off where it lies. Item entities have no item
 * tick of their own, so this is the one hook that reaches a stack in the world. [stated] "dropping it into the world
 * shouldnt stop the countdown either." The registry would fire it anyway; this keeps the position and the sound current
 * and detonates it on the tick instead of the backstop's.
 */
@Mixin(ItemEntity.class)
public abstract class MixinItemEntity_GauntletCountdown {

    @Inject(method = "tick", at = @At("HEAD"))
    private void avp_predator$tickGauntletCountdown(CallbackInfo callback) {
        var self = (ItemEntity) (Object) this;
        var stack = self.getItem();

        if (
            !(self.level() instanceof ServerLevel level) || !stack.is(PredatorItems.GAUNTLET.get()) || !GauntletSelfDestruct.isCounting(
                stack
            )
        ) {
            return;
        }

        GauntletSelfDestruct.observe(level, stack, self.position(), self::discard);

        // [stated] seen through walls "like spectral arrows" — the dropped gauntlet glows while it counts, and stops
        // the
        // moment it does not (the rule turned off defuses it in observe).
        self.setGlowingTag(!self.isRemoved() && GauntletSelfDestruct.isCounting(stack));
    }
}
