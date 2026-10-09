package com.predator.common.gameplay.debug.yautja;

import com.predator.common.registry.tag.PredatorItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * A slot in the yautja's RACK — a WHITELIST.
 * <p>
 * [stated] "yes the rack should have a white list too. the weapons, the ammo it uses, and the healing items". Accepts
 * avp_predator:yautja_rack_items (its weapons and ammunition) OR anything Yautja.isHealingItem recognises (the golden
 * apple tag, Healing II and Regeneration II potions — potions a tag cannot tell apart), and never anything in
 * yautja_forbidden (the hand caster). ⚠ The screen is the only way to hand a yautja an item: yautja never pick items up
 * off the ground. Shift-click goes through this too — vanilla's moveItemStackTo asks mayPlace.
 * <p>
 * (The HAND has its own, narrower whitelist — YautjaHandSlot.)
 */
public class YautjaGearSlot extends Slot {

    public YautjaGearSlot(Container container, int index, int x, int y) {
        super(container, index, x, y);
    }

    @Override
    public boolean mayPlace(@NotNull ItemStack stack) {
        return !stack.is(PredatorItemTags.YAUTJA_FORBIDDEN)
            && (stack.is(PredatorItemTags.YAUTJA_RACK_ITEMS)
                || com.predator.common.gameplay.entity.living.yautja.Yautja.isHealingItem(stack));
    }
}
