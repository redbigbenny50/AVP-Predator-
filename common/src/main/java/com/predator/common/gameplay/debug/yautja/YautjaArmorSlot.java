package com.predator.common.gameplay.debug.yautja;

import com.mojang.datafixers.util.Pair;
import com.predator.common.registry.tag.PredatorItemTags;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * One of the yautja's four armour slots in its screen.
 * <p>
 * [stated] "the armor it has on is shown as well so if you wanted to you can remove the armor too. only predator armor
 * is valid in the slot."
 * <p>
 * ⚠ Our own class because vanilla's ArmorSlot is package-private. And 1.21.1 has NO Slot.setBackground: the empty-slot
 * outline comes from overriding getNoItemIcon, which is what vanilla's own armour slots do (checked in the jar).
 */
public class YautjaArmorSlot extends Slot {

    private final EquipmentSlot equipmentSlot;

    public YautjaArmorSlot(Container container, int index, int x, int y, EquipmentSlot equipmentSlot) {
        super(container, index, x, y);
        this.equipmentSlot = equipmentSlot;
    }

    /** Predator armour only (the tag), and only the piece that belongs in THIS slot. */
    @Override
    public boolean mayPlace(@NotNull ItemStack stack) {
        return stack.is(PredatorItemTags.YAUTJA_ARMOR)
            && stack.getItem() instanceof ArmorItem armor
            && armor.getEquipmentSlot() == equipmentSlot;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
        var sprite = switch (equipmentSlot) {
            case HEAD -> InventoryMenu.EMPTY_ARMOR_SLOT_HELMET;
            case CHEST -> InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE;
            case LEGS -> InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS;
            default -> InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS;
        };

        return Pair.of(InventoryMenu.BLOCK_ATLAS, sprite);
    }
}
