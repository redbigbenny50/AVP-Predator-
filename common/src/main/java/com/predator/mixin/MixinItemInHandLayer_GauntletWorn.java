package com.predator.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.predator.client.animation.item.GauntletHeldGuard;
import com.predator.common.gameplay.item.GauntletItem;
import com.predator.common.registry.init.item.PredatorItems;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Skips the third-person IN-HAND render of a gauntlet that is being WORN.
 * <p>
 * ⚠ The worn gauntlet is drawn on the arm by {@code GauntletArmLayer}; without this it would also draw a second time
 * hanging off the hand by its {@code thirdperson_*} display transform. A gauntlet in the MAIN hand is not worn and
 * still renders as an ordinary held item.
 * <p>
 * ⚠ When it is NOT worn (main hand, or any non-player holder) the same hook is where the held gauntlet's clips get
 * stopped — {@link GauntletHeldGuard} — because this is the one place in third person that has the holder and the stack
 * together.
 * <p>
 * ⚠ Cancelling at HEAD is safe alongside {@code MixinItemInHandLayer_CombiStickStab}: its push at HEAD is gated on the
 * stack being a combi stick, so for a gauntlet nothing has been pushed when this returns.
 */
@Mixin(ItemInHandLayer.class)
public abstract class MixinItemInHandLayer_GauntletWorn {

    @Inject(method = "renderArmWithItem", at = @At("HEAD"), cancellable = true)
    private void avp_predator$skipWornGauntlet(
        LivingEntity entity,
        ItemStack stack,
        ItemDisplayContext displayContext,
        HumanoidArm arm,
        PoseStack poseStack,
        MultiBufferSource buffer,
        int packedLight,
        CallbackInfo callback
    ) {
        if (
            entity instanceof Player player
                && stack.is(PredatorItems.GAUNTLET.get())
                && arm == GauntletItem.arm(player)
                && GauntletItem.isEquipped(player, stack)
        ) {
            callback.cancel();

            return;
        }

        // Not worn: a held gauntlet must not still be running the worn clips — see the guard.
        GauntletHeldGuard.stopIfNotWorn(entity, stack);
    }
}
