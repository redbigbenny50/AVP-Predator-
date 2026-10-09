package com.predator.mixin;

import com.predator.common.gameplay.item.GauntletItem;
import com.predator.common.gameplay.menu.GauntletContents;
import com.predator.common.registry.tag.PredatorItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Picked-up gauntlet ammunition goes back into the WORN gauntlet before the main inventory.
 * <h2>⚠ Why {@code Inventory.add(ItemStack)}</h2> Both pickup paths end here: a dropped net is an {@code ItemEntity}
 * ({@code playerTouch} → {@code inventory.add}), and a stuck dart is an {@code AbstractArrow} ({@code tryPickup} →
 * {@code inventory.add(getPickupItem())}). One hook covers both, plus {@code /give}, which is fine — ammo handed to a
 * player who is wearing the launcher belongs in the launcher.
 * <h2>Rules, as he set them</h2> Only while a gauntlet is worn. Partial stacks of the same round are topped up first,
 * then empty ammo slots; whatever does not fit carries on into the main inventory exactly as vanilla would. The cloak
 * slot is never touched, and only items in the {@code gauntlet_ammunition} tag qualify — the same rule the GUI's ammo
 * slots enforce.
 * <p>
 * ⚠ The stack is shrunk IN PLACE. Vanilla's callers read the original count before calling add (for the pickup
 * animation) and check {@code isEmpty()} after, so a partial move behaves like a partial vanilla merge; a full move
 * returns true here so the arrow / item entity is discarded.
 * <p>
 * ⚠ Server only. Pickups are server-side; a client-side add (creative) must not touch the synced component.
 */
@Mixin(Inventory.class)
public abstract class MixinInventory_GauntletAmmoPickup {

    @Shadow
    @Final
    public Player player;

    @Inject(method = "add(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void avp_predator$refillWornGauntlet(ItemStack stack, CallbackInfoReturnable<Boolean> callback) {
        if (stack.isEmpty() || player.level().isClientSide || !stack.is(PredatorItemTags.GAUNTLET_AMMUNITION)) {
            return;
        }

        var gauntlet = GauntletItem.equipped(player);

        if (gauntlet.isEmpty()) {
            return;
        }

        var contents = new GauntletContents(gauntlet);

        // Pass 1: top up slots already holding this round.
        for (var slot = 0; slot < GauntletContents.AMMO_SLOTS && !stack.isEmpty(); slot++) {
            var existing = contents.getItem(slot);

            if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, stack)) {
                continue;
            }

            var room = existing.getMaxStackSize() - existing.getCount();

            if (room <= 0) {
                continue;
            }

            var moved = Math.min(room, stack.getCount());

            contents.setItem(slot, existing.copyWithCount(existing.getCount() + moved));
            stack.shrink(moved);
        }

        // Pass 2: empty ammo slots.
        for (var slot = 0; slot < GauntletContents.AMMO_SLOTS && !stack.isEmpty(); slot++) {
            if (!contents.getItem(slot).isEmpty()) {
                continue;
            }

            var moved = Math.min(stack.getMaxStackSize(), stack.getCount());

            contents.setItem(slot, stack.copyWithCount(moved));
            stack.shrink(moved);
        }

        if (stack.isEmpty()) {
            callback.setReturnValue(true);
        }
    }
}
