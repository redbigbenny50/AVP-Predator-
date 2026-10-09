package com.predator.common.gameplay.item;

import com.predator.common.gameplay.entity.projectile.PlasmaBoltArrowProjectile;
import com.predator.common.gameplay.entity.projectile.PlasmaBoltProjectile;
import com.predator.common.gameplay.entity.projectile.PlasmaShurikenProjectile;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * What counts as a PLASMA hit — the one place that knows, so a new plasma weapon is one line here.
 * <p>
 * ⚠ No return-value tag up here: that tag is only valid on a method, and on a class it fails the build's javadoc task
 * ("invalid use of @return").
 * <ul>
 * <li>A plasma PROJECTILE landed it: the plasma caster's and hand caster's bolt, the plasma bow's bolt, the plasma
 * shuriken.</li>
 * <li>A MELEE blow with a plasma sword in the attacker's main hand — a player's or a yautja's. (Melee = the attacker is
 * also the direct source; a sword-holder's ARROW is not a plasma hit.)</li>
 * </ul>
 */
public final class PlasmaDamage {

    private PlasmaDamage() {
        throw new UnsupportedOperationException();
    }

    public static boolean isPlasma(DamageSource source) {
        var direct = source.getDirectEntity();

        if (
            direct instanceof PlasmaBoltProjectile || direct instanceof PlasmaBoltArrowProjectile
                || direct instanceof PlasmaShurikenProjectile
        ) {
            return true;
        }

        var attacker = source.getEntity();

        return attacker != null
            && attacker == direct
            && attacker instanceof LivingEntity living
            && living.getMainHandItem().getItem() instanceof PlasmaSwordItem;
    }
}
