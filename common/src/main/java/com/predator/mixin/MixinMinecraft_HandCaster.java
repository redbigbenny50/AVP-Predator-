package com.predator.mixin;

import com.predator.client.handcaster.HandCasterClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The hand caster owns the left click while it is held: vanilla's attack (the swing and the hit) and its held-button
 * block breaking are both switched off, and the trigger is read once a tick. See HandCasterClient.
 */
@Mixin(Minecraft.class)
public abstract class MixinMinecraft_HandCaster {

    @Shadow
    @Nullable
    public LocalPlayer player;

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void avp_predator$handCasterNoAttack(CallbackInfoReturnable<Boolean> cir) {
        if (HandCasterClient.isHolding(player)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void avp_predator$handCasterNoMining(boolean leftClick, CallbackInfo ci) {
        if (HandCasterClient.isHolding(player)) {
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void avp_predator$handCasterTrigger(CallbackInfo ci) {
        HandCasterClient.clientTick((Minecraft) (Object) this);
    }
}
