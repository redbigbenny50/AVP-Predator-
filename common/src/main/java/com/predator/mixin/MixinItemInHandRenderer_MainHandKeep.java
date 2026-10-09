package com.predator.mixin;

import com.blib.mod.common.registry.init.BLibDataComponents;
import com.predator.common.gameplay.item.HandCasterItem;
import com.predator.common.gameplay.item.battleaxe.BattleaxeItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ⚠⚠ NO RE-EQUIP DROP WHEN A HELD ITEM'S OWN STATE CHANGES — the main-hand twin of
 * MixinItemInHandRenderer_GauntletHeld.
 * <p>
 * Vanilla compares the stack it drew last tick with the one held now; if ItemStack.matches fails it lowers the hand and
 * raises it again. The hand caster rewrites its shot count and fire time on EVERY shot, and the battleaxe stamps its
 * slam — so each shot and each slam "put the arm away and popped it back up". When the remembered and current stacks
 * are the same item with the same BLib AZ_ID, the remembered one is swapped for the current one BEFORE vanilla
 * compares, so it sees no change. Switching to a DIFFERENT hand caster or axe still re-equips, as it should. ⚠ Needs
 * both items registered with AzIdentityRegistry (PredatorClient), which is what gives a stack its AZ_ID.
 */
@Mixin(ItemInHandRenderer.class)
public abstract class MixinItemInHandRenderer_MainHandKeep {

    @Shadow
    private ItemStack mainHandItem;

    @Inject(method = "tick", at = @At("HEAD"))
    private void avp_predator$keepHeldWeaponEquipped(CallbackInfo callback) {
        var player = Minecraft.getInstance().player;

        if (player == null || mainHandItem == null) {
            return;
        }

        var current = player.getMainHandItem();

        if (
            current != mainHandItem
                && current.getItem() == mainHandItem.getItem()
                && (current.getItem() instanceof HandCasterItem || current.getItem() instanceof BattleaxeItem)
                && avp_predator$mainHandSameIdentity(current, mainHandItem)
        ) {
            mainHandItem = current;
        }
    }

    /**
     * Oct 7 - renamed and marked unique: both hand mixins declared a private "sameIdentity" in the same target class,
     * so Mixin skipped the second ("Method overwrite conflict for sameIdentity" in the log). It only worked because the
     * two bodies were identical.
     */
    @org.spongepowered.asm.mixin.Unique
    private static boolean avp_predator$mainHandSameIdentity(ItemStack a, ItemStack b) {
        var idA = a.get(BLibDataComponents.AZ_ID.get());

        return idA != null && idA.equals(b.get(BLibDataComponents.AZ_ID.get()));
    }
}
