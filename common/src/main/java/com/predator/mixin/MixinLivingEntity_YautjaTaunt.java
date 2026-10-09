package com.predator.mixin;

import com.predator.common.gameplay.entity.living.yautja.YautjaTaunts;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hands every death to {@link YautjaTaunts}, which decides whether a yautja laughs. Runs once per death, server side.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity_YautjaTaunt {

    @Inject(method = "dropAllDeathLoot", at = @At("HEAD"))
    private void avp_predator$yautjaTaunt(ServerLevel level, DamageSource source, CallbackInfo callback) {
        YautjaTaunts.onDeath(level, (LivingEntity) (Object) this, source);
        com.predator.common.gameplay.hunt.HuntDirector.onDeath(level, (LivingEntity) (Object) this, source);
    }
}
