package com.predator.mixin;

import com.predator.common.gameplay.entity.living.yautja.Yautja;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Yautja hit PLAYERS 20% softer than they hit anything else.
 * <p>
 * [stated] "i dont want to lower their damage more since they should be stronger against other mobs but when it comes
 * to players i think all the yautja attacks should do 20% less damage but only when fighting players."
 * <h2>⚠ WHY ONE HOOK COVERS EVERY WEAPON</h2> Every way a yautja hurts a player ends in Player.hurt, and the damage
 * source's ENTITY is the yautja whether it swung the blow or threw the projectile — for arrows, bolts, darts, shuriken,
 * the disc and the combi stick, getEntity() is the thrower and getDirectEntity() the projectile. So the one check here
 * reaches melee, thrown and fired damage alike, and a new yautja weapon added later is covered without touching this.
 * <p>
 * ⚠ Applied at the HEAD of hurt, so it comes before vanilla's own difficulty scaling and before armour — the same place
 * the damage number enters, as if the yautja had simply dealt less.
 */
@Mixin(Player.class)
public abstract class MixinPlayer_YautjaDamageToPlayers {

    @ModifyVariable(method = "hurt", at = @At("HEAD"), argsOnly = true)
    private float avp_predator$softenYautjaDamage(float amount, DamageSource source, float original) {
        return source.getEntity() instanceof Yautja ? amount * Yautja.DAMAGE_TO_PLAYERS_MULTIPLIER : amount;
    }
}
