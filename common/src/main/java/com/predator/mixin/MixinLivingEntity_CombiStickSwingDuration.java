package com.predator.mixin;

import com.predator.common.gameplay.item.combistick.CombiStickItem;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lengthens the swing ANIMATION while an extended combi stick is held.
 * <p>
 * <strong>⚠⚠ ATTACK SPEED DOES NOT CONTROL THE ANIMATION LENGTH. I had this wrong.</strong>
 * {@code LivingEntity.getCurrentSwingDuration()} returns a flat <strong>6 ticks</strong> — 0.3 seconds — and only haste
 * and mining fatigue change it. The {@code ATTACK_SPEED} attribute governs the COOLDOWN BETWEEN swings, not how long
 * one takes to play.
 * <p>
 * So every percentage window in the stab (wind-up to 0.15, thrust to 0.30, settle from 0.4) was spanning 6 ticks, not
 * the ~20 I quoted. The whole motion was under a third of a second, which is why it kept reading as a flick no matter
 * how the curve was shaped.
 * <p>
 * <strong>⚠ This is the dial that actually makes the stab slower.</strong> Lowering ATTACK_SPEED further would only add
 * dead time between attacks.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity_CombiStickSwingDuration {

    /**
     * Swing length in ticks while the combi stick is extended. Vanilla is 6.
     * <p>
     * ⚠ 26 is vanilla's 6 plus one second, which is what he asked for. It is a LONG swing — if it feels ponderous, 16
     * to 20 is a middle ground. Not final, so it hot-swaps.
     */
    private static int AVP_COMBI_SWING_TICKS = 26;

    @Inject(method = "getCurrentSwingDuration", at = @At("RETURN"), cancellable = true)
    private void avp_predator$slowCombiStickSwing(CallbackInfoReturnable<Integer> callback) {
        if ((Object) this instanceof Player player && CombiStickItem.isExtendedInHand(player)) {
            callback.setReturnValue(AVP_COMBI_SWING_TICKS);
        }
    }
}
