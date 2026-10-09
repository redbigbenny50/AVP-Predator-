package com.predator.mixin;

import com.predator.common.gameplay.item.combistick.CombiStickDelayedStrike;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Defers a combi stick's damage until the thrust actually lands.
 * <p>
 * <strong>⚠ HEAD and cancellable.</strong> Vanilla applies the whole attack — damage, knockback, durability,
 * enchantments, sweep — inside {@code Player.attack}. Cancelling and re-calling it later is what keeps every one of
 * those behaviours intact; reimplementing the damage would have quietly dropped enchantment effects and mod hooks.
 * <p>
 * <strong>⚠ Registered as a COMMON mixin, not client.</strong> The deferral has to happen on the server or the damage
 * is applied there on time and only the client sees the delay.
 */
@Mixin(Player.class)
public abstract class MixinPlayer_CombiStickDelayedStrike {

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void avp_predator$deferCombiStickStrike(Entity target, CallbackInfo callback) {
        if (CombiStickDelayedStrike.defer((Player) (Object) this, target)) {
            callback.cancel();
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void avp_predator$releaseCombiStickStrike(CallbackInfo callback) {
        CombiStickDelayedStrike.tick((Player) (Object) this);
    }
}
