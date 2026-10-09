package com.predator.mixin.cloak;

import com.predator.Predator;
import com.predator.common.gameplay.item.CloakingDeviceItem;
import com.predator.common.network.packet.C2SToggleCloakPayload;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Turns left-click-while-holding-the-device into a cloak toggle.
 * <p>
 * This intercept is necessary rather than stylistic: a left click on air never reaches the server at all in vanilla —
 * it produces a swing animation and nothing else — so {@code Item#use} and the block-break path both miss the case that
 * matters. Cancelling {@code startAttack} also means holding the device can neither mine nor swing at a mob, which is
 * the right trade for a dedicated gadget.
 */
@Mixin(Minecraft.class)
public abstract class MixinMinecraft_CloakToggle {

    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void predator$toggleCloakOnLeftClick(CallbackInfoReturnable<Boolean> cir) {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;

        if (player == null || !(player.getMainHandItem().getItem() instanceof CloakingDeviceItem)) {
            return;
        }

        Predator.MOD.networking().sendToServer(C2SToggleCloakPayload.INSTANCE);
        player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        cir.setReturnValue(false);
    }
}
