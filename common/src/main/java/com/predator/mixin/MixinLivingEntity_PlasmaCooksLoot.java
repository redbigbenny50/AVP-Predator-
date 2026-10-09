package com.predator.mixin;

import com.predator.common.gameplay.item.PlasmaDamage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A mob killed by plasma drops COOKED food. [stated] "if you kill a mob that drops food that can be cooked it needs to
 * drop cooked food. right now it drops raw food despite being on fire."
 * <h2>Why it dropped raw</h2> Vanilla already cooks a drop (furnace_smelt) when the mob "is_on_fire" at death, or when
 * the killer's main-hand item has a #smelts_loot enchantment like Fire Aspect — read from vanilla's cow loot table. The
 * plasma weapons set fire in CODE, and a player's sword does it in hurtEnemy, which vanilla runs AFTER the damage
 * (checked in Player.attack's bytecode). So when the blow that ignites is also the blow that kills, the mob is dead
 * before it burns.
 * <h2>The fix</h2> Just before the loot rolls, a plasma kill makes sure the mob counts as on fire. Vanilla's OWN rule
 * then cooks it — so every cookable drop cooks, other mods' included, exactly as it would for a burning mob.
 * Fire-immune mobs are left alone, as vanilla would.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity_PlasmaCooksLoot {

    @Inject(method = "dropAllDeathLoot", at = @At("HEAD"))
    private void avp_predator$plasmaCooksLoot(ServerLevel level, DamageSource source, CallbackInfo callback) {
        var self = (LivingEntity) (Object) this;

        if (PlasmaDamage.isPlasma(source) && !self.fireImmune() && self.getRemainingFireTicks() <= 0) {
            self.setRemainingFireTicks(20);
        }
    }
}
