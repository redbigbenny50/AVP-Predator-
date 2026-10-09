package com.predator.mixin;

import com.blib.mod.common.registry.init.BLibDataComponents;
import com.mojang.blaze3d.vertex.PoseStack;
import com.predator.client.animation.item.GauntletHeldGuard;
import com.predator.common.registry.init.item.PredatorItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Two first-person jobs for the gauntlet.
 * <h2>1. Held, not worn</h2> A gauntlet drawn in the local player's MAIN hand has its clips stopped before vanilla
 * places it — {@link GauntletHeldGuard}. HEAD, no pose stack changes, nothing cancelled, so it cannot interact with the
 * combi stick's mixin on this class, which only pushes for a combi stick stack.
 * <h2>2. ⚠⚠ NO RE-EQUIP DROP WHEN THE WORN GAUNTLET'S CONTENTS CHANGE</h2> Every tick vanilla compares the offhand
 * stack it drew last with the one the player holds now, and if {@code ItemStack.matches} fails it lowers the hand and
 * raises it again (0.4 per tick each way). Firing consumes a round, which rewrites the contents component, so every
 * shot "put the arm away and popped it back up" — over the top of the fire clip, which was playing the whole time.
 * Moving ammo in the GUI does the same. The worn gauntlet is one physical object whatever is in its magazine: if the
 * remembered stack and the current one are both gauntlets with the same {@code AZ_ID}, the remembered one is swapped
 * for the current one BEFORE vanilla compares, so it sees no change. NeoForge routes this through
 * {@code shouldCauseReequipAnimation} and Fabric through the raw compare; both read the same field, so one mixin covers
 * both loaders.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class MixinItemInHandRenderer_GauntletHeld {

    @Shadow
    private ItemStack offHandItem;

    @Inject(method = "tick", at = @At("HEAD"))
    private void avp_predator$keepWornGauntletEquipped(CallbackInfo callback) {
        var player = Minecraft.getInstance().player;

        if (player == null || offHandItem == null) {
            return;
        }

        var current = player.getOffhandItem();

        if (
            current != offHandItem
                && current.is(PredatorItems.GAUNTLET.get())
                && offHandItem.is(PredatorItems.GAUNTLET.get())
                && avp_predator$gauntletSameIdentity(current, offHandItem)
        ) {
            offHandItem = current;
        }
    }

    /**
     * Oct 7 - renamed and marked unique: both hand mixins declared a private "sameIdentity" in the same target class,
     * so Mixin skipped the second ("Method overwrite conflict for sameIdentity" in the log). It only worked because the
     * two bodies were identical.
     */
    @org.spongepowered.asm.mixin.Unique
    private static boolean avp_predator$gauntletSameIdentity(ItemStack a, ItemStack b) {
        var idA = a.get(BLibDataComponents.AZ_ID.get());

        return idA != null && idA.equals(b.get(BLibDataComponents.AZ_ID.get()));
    }

    @Inject(method = "renderArmWithItem", at = @At("HEAD"))
    private void avp_predator$stopHeldGauntlet(
        AbstractClientPlayer player,
        float partialTick,
        float pitch,
        InteractionHand hand,
        float swingProgress,
        ItemStack stack,
        float equipProgress,
        PoseStack poseStack,
        MultiBufferSource buffer,
        int packedLight,
        CallbackInfo callback
    ) {
        GauntletHeldGuard.stopIfNotWorn(player, stack);
    }
}
