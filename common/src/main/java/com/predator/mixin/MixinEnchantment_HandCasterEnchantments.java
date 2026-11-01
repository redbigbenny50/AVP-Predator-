package com.predator.mixin;

import com.predator.common.gameplay.item.HandCasterItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * [stated] Oct 30: the hand caster can be enchanted - "no Infiniti or mending" - and "Quickcharge enchantment reduces
 * charge time".
 * <p>
 * It takes bow enchantments (Power, Punch, Flame) through the bow-enchantable tag. Infinity uses that same tag, so it
 * is REFUSED here. Quick Charge is a crossbow enchantment; joining the crossbow tag would also let in Multishot and
 * Piercing, which the caster does not use, so Quick Charge alone is ALLOWED here instead. Both are found by what they
 * DO (Infinity's ammo-use effect, Quick Charge's charge-time effect), not by name, so a datapack that renames them is
 * still caught. Mending needs durability, which the caster has none of.
 * </p>
 * <p>
 * All three checks are hooked: the enchanting table asks isPrimaryItem, the anvil, books and /enchant ask canEnchant
 * (which reads the tag directly), and other code asks isSupportedItem.
 * </p>
 */
@Mixin(Enchantment.class)
public abstract class MixinEnchantment_HandCasterEnchantments {

    @Shadow
    public abstract net.minecraft.core.component.DataComponentMap effects();

    @Inject(method = "isSupportedItem", at = @At("HEAD"), cancellable = true)
    private void avp_predator$handCasterSupported(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        decide(stack, cir);
    }

    @Inject(method = "isPrimaryItem", at = @At("HEAD"), cancellable = true)
    private void avp_predator$handCasterPrimary(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        decide(stack, cir);
    }

    @Inject(method = "canEnchant", at = @At("HEAD"), cancellable = true)
    private void avp_predator$handCasterAnvil(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        decide(stack, cir);
    }

    private void decide(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!(stack.getItem() instanceof HandCasterItem)) {
            return;
        }
        if (effects().has(EnchantmentEffectComponents.AMMO_USE)) {
            cir.setReturnValue(false); // Infinity
        } else if (effects().has(EnchantmentEffectComponents.CROSSBOW_CHARGE_TIME)) {
            cir.setReturnValue(true); // Quick Charge
        }
    }
}
