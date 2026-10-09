package com.predator.mixin;

import com.predator.common.gameplay.entity.projectile.VeritaniumArrowEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * [stated] veritanium arrows "bypass armor defense". ⚠ On the VICTIM: vanilla builds an arrow's damage source inside
 * AbstractArrow.onHitEntity, where it cannot be changed, so the armour step is skipped here for any damage whose DIRECT
 * entity is a veritanium arrow. Armour points and toughness no longer reduce it; Protection enchantments still do (a
 * separate step). Yautja override this method with their tier armour and repeat the check there.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity_VeritaniumArrowArmor {

    @Inject(method = "getDamageAfterArmorAbsorb", at = @At("HEAD"), cancellable = true)
    private void avp_predator$veritaniumArrowPierces(DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {
        if (source.getDirectEntity() instanceof VeritaniumArrowEntity) {
            cir.setReturnValue(amount);
        }
    }
}
