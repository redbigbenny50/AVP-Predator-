package com.predator.mixin;

import com.predator.common.gameplay.net.NettedMob;
import com.predator.common.gameplay.net.PredatorNet;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Cutting a captured creature loose.
 * <p>
 * [stated] "once captured they cant escape unless you let them out. you do this by rightclicking on the net with a
 * sword or axe." Tag-driven, so a modded blade works. ⚠ This runs BEFORE vanilla's own interaction, so cutting a netted
 * cow free never also opens a breeding or leashing interaction on the same click.
 */
@Mixin(Mob.class)
public abstract class MixinMob_NetCutFree {

    @Inject(method = "mobInteract", at = @At("HEAD"), cancellable = true)
    private void avp_predator$cutNetFree(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> callback) {
        var self = (Mob) (Object) this;

        if (!(self instanceof NettedMob netted) || !netted.avp_predator$isNetted()) {
            return;
        }

        var held = player.getItemInHand(hand);

        if (!held.is(ItemTags.SWORDS) && !held.is(ItemTags.AXES)) {
            return;
        }

        if (!self.level().isClientSide) {
            PredatorNet.release(self);
            self.level().playSound(null, self.blockPosition(), SoundEvents.SHEEP_SHEAR, SoundSource.PLAYERS, 1.0F, 1.1F);
            held.hurtAndBreak(1, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        }

        callback.setReturnValue(InteractionResult.sidedSuccess(self.level().isClientSide));
    }
}
