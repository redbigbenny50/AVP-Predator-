package com.predator.mixin;

import com.predator.common.gameplay.hunt.SkinnedCorpses;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Brackets a death's drops so an unwatched yautja kill can put them in a skinned corpse instead of on the ground. The
 * rules and the capture itself live in {@link SkinnedCorpses}; this only marks where the drops start and stop.
 * <p>
 * ⚠ {@code dropAllDeathLoot} is the right bracket on BOTH loaders: vanilla drops everything inside it, and NeoForge's
 * patch collects the drops and re-adds them after {@code LivingDropsEvent} — still inside it (checked in the patched
 * bytecode).
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity_SkinnedCorpse {

    @Inject(method = "dropAllDeathLoot", at = @At("HEAD"))
    private void avp_predator$beginCorpseCapture(ServerLevel level, DamageSource source, CallbackInfo callback) {
        SkinnedCorpses.beginDeathDrops(level, (LivingEntity) (Object) this, source);
    }

    @Inject(method = "dropAllDeathLoot", at = @At("RETURN"))
    private void avp_predator$finishCorpseCapture(ServerLevel level, DamageSource source, CallbackInfo callback) {
        SkinnedCorpses.finishDeathDrops(level, (LivingEntity) (Object) this);
    }
}
