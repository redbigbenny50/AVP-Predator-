package com.predator.common.gameplay.debug.yautja;

import com.predator.common.registry.tag.PredatorItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * The yautja's MAIN HAND slot in its screen — a WHITELIST.
 * <p>
 * [stated] "you can give the yautja anything in its main hand. this is not good it needs a whitelist and to have only
 * the weapons for the yautja minus the handcaster like we did. people are giving it nether stars and all kinds of
 * things." Accepts ONLY avp_predator:yautja_hand_weapons, and never anything in yautja_forbidden (belt and braces for
 * the hand caster). One item at a time, like any held item.
 */
public class YautjaHandSlot extends Slot {

    public YautjaHandSlot(Container container, int index, int x, int y) {
        super(container, index, x, y);
    }

    @Override
    public boolean mayPlace(@NotNull ItemStack stack) {
        return stack.is(PredatorItemTags.YAUTJA_HAND_WEAPONS) && !stack.is(PredatorItemTags.YAUTJA_FORBIDDEN);
    }
}
