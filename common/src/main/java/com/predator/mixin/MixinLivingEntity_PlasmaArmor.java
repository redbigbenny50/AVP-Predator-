package com.predator.mixin;

import com.predator.common.gameplay.entity.projectile.PlasmaBoltProjectile;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * [stated] Oct 30: a plasma caster bolt "bypasses 80% armor and ignores toughness" - the hand caster's and the
 * yautja's. Against PLAYERS it is softer: [stated] "For players have it only ignore 50% armor and toughness" - half
 * their armour and half their toughness still count.
 * <p>
 * Vanilla's armour step, with the armour counted at a fifth and toughness at nothing: the same formula, so armour still
 * wears down as it would. Protection enchantments are a separate, later step and still apply. Yautja replace this
 * method with their tier armour and repeat the rule there; the veritanium arrow's full bypass is its own mixin and
 * unaffected.
 * </p>
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity_PlasmaArmor {

    private static final float ARMOR_COUNTED = 0.2F;

    private static final float TOUGHNESS_COUNTED = 0.0F;

    private static final float PLAYER_ARMOR_COUNTED = 0.5F;

    private static final float PLAYER_TOUGHNESS_COUNTED = 0.5F;

    @Shadow
    protected abstract void hurtArmor(DamageSource source, float amount);

    @Shadow
    public abstract int getArmorValue();

    @Inject(method = "getDamageAfterArmorAbsorb", at = @At("HEAD"), cancellable = true)
    private void avp_predator$plasmaPiercesArmor(DamageSource source, float amount, CallbackInfoReturnable<Float> cir) {
        if (!(source.getDirectEntity() instanceof PlasmaBoltProjectile) || source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            return;
        }
        var self = (LivingEntity) (Object) this;
        hurtArmor(source, amount);
        var player = self instanceof net.minecraft.world.entity.player.Player;
        var armor = getArmorValue() * (player ? PLAYER_ARMOR_COUNTED : ARMOR_COUNTED);
        var toughness = (float) self.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS)
            * (player ? PLAYER_TOUGHNESS_COUNTED : TOUGHNESS_COUNTED);
        cir.setReturnValue(CombatRules.getDamageAfterAbsorb(self, amount, source, armor, toughness));
    }
}
