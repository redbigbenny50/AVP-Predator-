package com.predator.mixin;

import com.predator.compatibility.pointblank.PointBlankBulletResistance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Marks a Point Blank round while it is landing, so the yautja's gun balancing can tell it from a sword swing — Point
 * Blank hurts with plain {@code player_attack}. See {@link PointBlankBulletResistance}.
 * <p>
 * ⚠ {@code @Pseudo}: Point Blank is optional. Without it the target class does not exist and this mixin is skipped. The
 * handler names only vanilla types, so nothing here needs Point Blank on the classpath. avp_alien has its own copy of
 * this hook for xenomorphs; two mods injecting into the same method is fine.
 * </p>
 */
@Pseudo
@Mixin(targets = "com.vicmatskiv.pointblank.item.HurtingItem", remap = false)
public abstract class MixinHurtingItem_PointBlankHit {

    @Inject(method = "hurtEntity", at = @At("HEAD"), remap = false)
    private void avp_predator$markPointBlankHit(
        LivingEntity shooter,
        EntityHitResult entityHitResult,
        Entity projectile,
        ItemStack gunStack,
        CallbackInfoReturnable<Float> callbackInfo
    ) {
        PointBlankBulletResistance.beginHit(entityHitResult.getEntity());
    }

    @Inject(method = "hurtEntity", at = @At("RETURN"), remap = false)
    private void avp_predator$clearPointBlankHit(
        LivingEntity shooter,
        EntityHitResult entityHitResult,
        Entity projectile,
        ItemStack gunStack,
        CallbackInfoReturnable<Float> callbackInfo
    ) {
        PointBlankBulletResistance.endHit();
    }
}
