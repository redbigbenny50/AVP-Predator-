package com.predator.mixin.cloak;

import com.predator.common.gameplay.cloak.PredatorCloakManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Landing a melee blow drops the field for two seconds, then it comes back up on its own with no cooldown.
 * <p>
 * The point is the tell, not the punishment: nearby mobs get a window to react to whatever just hit them at arm's
 * length, and the wearer gets a window to break contact. Ranged attacks deliberately do not trigger it — a hunter
 * picking targets off from cover stays hidden, which is the whole reason ranged weapons exist here.
 */
@Mixin(Player.class)
public abstract class MixinPlayer_CloakMeleeReveal {

    @Inject(method = "attack", at = @At("TAIL"))
    private void predator$revealOnMelee(Entity target, CallbackInfo callbackInfo) {
        var self = Player.class.cast(this);

        if (self.level().isClientSide) {
            return;
        }

        PredatorCloakManager.onMeleeHit(self);
    }
}
