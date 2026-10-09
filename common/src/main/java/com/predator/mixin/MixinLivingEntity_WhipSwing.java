package com.predator.mixin;

import com.predator.common.gameplay.whip.WhipItem;
import com.predator.common.registry.init.item.PredatorItems;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Cracks the whip on a left click.
 * <p>
 * ⚠ {@code Item.onEntitySwing} is a NeoForge extension and does not exist in {@code :common}, so the swing is caught
 * here instead. {@code LivingEntity.swing} fires for a left click at AIR as well as at a target, which is what the whip
 * needs — there is nothing to "attack" six blocks away.
 */
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity_WhipSwing {

    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Z)V", at = @At("HEAD"))
    private void avp_predator$crackWhip(InteractionHand hand, boolean updateSelf, CallbackInfo callback) {
        if (hand != InteractionHand.MAIN_HAND || !((Object) this instanceof Player player)) {
            return;
        }

        var stack = player.getMainHandItem();

        if (stack.is(PredatorItems.WHIP.get())) {
            WhipItem.onSwing(player, stack);
        }
    }
}
