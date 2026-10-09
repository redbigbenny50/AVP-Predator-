package com.predator.mixin.cloak;

import com.predator.common.gameplay.cloak.PredatorCloakManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The mob half of the melee reveal — a cloaked yautja drops its field for two seconds after landing a blow, then
 * re-engages for free.
 * <p>
 * ⚠ A SEPARATE mixin from the player's, because they are separate code paths. A player attacks through
 * {@code Player#attack}; a mob attacks through {@code Mob#doHurtTarget}, which nothing hooked — so until now a cloaked
 * yautja could beat a target to death without ever becoming visible, while a cloaked player could not. Same rule, two
 * entry points.
 * <p>
 * Gated on the return value: {@code doHurtTarget} returns false when the blow was blocked or the target was
 * invulnerable, and a swing that connected with nothing should not give the hunter away.
 */
@Mixin(Mob.class)
public abstract class MixinMob_CloakMeleeReveal {

    @Inject(method = "doHurtTarget", at = @At("RETURN"))
    private void predator$revealOnMobMelee(Entity target, CallbackInfoReturnable<Boolean> cir) {
        if (!Boolean.TRUE.equals(cir.getReturnValue())) {
            return;
        }

        var self = Mob.class.cast(this);

        if (self.level().isClientSide) {
            return;
        }

        PredatorCloakManager.onMeleeHit(self);
    }
}
