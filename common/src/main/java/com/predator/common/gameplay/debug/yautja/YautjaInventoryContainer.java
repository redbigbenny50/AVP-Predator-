package com.predator.common.gameplay.debug.yautja;

import com.blib.api.common.inventory.v1.BLibInventory;
import com.predator.common.gameplay.entity.living.yautja.Yautja;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The yautja's rack, hand and armour as one container, for its screen.
 * <p>
 * Indices: 0..INVENTORY_SIZE-1 the RACK, then the HAND, then the four ARMOUR slots, head to feet.
 * <p>
 * ⚠⚠ THE HELMET SLOT IS THE MASK. Yautja.hasMask() is literally "is the predator helmet in the head slot", so taking
 * the helmet off through this screen unmasks it, with everything that follows: it will NOT roar at half health (the
 * roar is the mask BREAKING), it may use its healing items as soon as it is under half, and it loses the mask's
 * drowning / space-suffocation protection. [agreed] That is the intended reading — you deliberately unmasked it. ⚠ Worn
 * armour is still only a costume: a yautja's protection comes from its TIER (the Sep 8 ruling), so removing a
 * chestplate does not weaken it.
 */
public class YautjaInventoryContainer implements Container {

    private static final double MAXIMUM_INTERACTION_DISTANCE = 8.0D;

    public static final int HAND_SLOT = Yautja.INVENTORY_SIZE;

    public static final int HEAD_SLOT = HAND_SLOT + 1;

    public static final int CHEST_SLOT = HAND_SLOT + 2;

    public static final int LEGS_SLOT = HAND_SLOT + 3;

    public static final int FEET_SLOT = HAND_SLOT + 4;

    public static final int SLOT_COUNT = FEET_SLOT + 1;

    private final Yautja yautja;

    private final BLibInventory inventory;

    public YautjaInventoryContainer(Yautja yautja) {
        this.yautja = yautja;
        this.inventory = yautja.getInventory();
    }

    /** {@return the equipment slot a container index maps to, or null for a rack slot} */
    public static @Nullable EquipmentSlot equipmentFor(int slotIndex) {
        if (slotIndex == HAND_SLOT) {
            return EquipmentSlot.MAINHAND;
        }

        if (slotIndex == HEAD_SLOT) {
            return EquipmentSlot.HEAD;
        }

        if (slotIndex == CHEST_SLOT) {
            return EquipmentSlot.CHEST;
        }

        if (slotIndex == LEGS_SLOT) {
            return EquipmentSlot.LEGS;
        }

        if (slotIndex == FEET_SLOT) {
            return EquipmentSlot.FEET;
        }

        return null;
    }

    @Override
    public int getContainerSize() {
        return SLOT_COUNT;
    }

    @Override
    public boolean isEmpty() {
        for (var slotIndex = 0; slotIndex < SLOT_COUNT; slotIndex++) {
            if (!getItem(slotIndex).isEmpty()) {
                return false;
            }
        }

        return true;
    }

    @Override
    public @NotNull ItemStack getItem(int slotIndex) {
        var equipment = equipmentFor(slotIndex);

        return equipment != null ? yautja.getItemBySlot(equipment) : inventory.getItemStack(slotIndex);
    }

    @Override
    public @NotNull ItemStack removeItem(int slotIndex, int count) {
        var equipment = equipmentFor(slotIndex);

        if (equipment != null) {
            var worn = yautja.getItemBySlot(equipment);

            if (worn.isEmpty() || count <= 0) {
                return ItemStack.EMPTY;
            }

            var taken = worn.split(count);

            yautja.setItemSlot(equipment, worn);

            // ⚠ The debug weapon lock only ever belongs to the HAND. Emptying the hand releases it.
            if (equipment == EquipmentSlot.MAINHAND && worn.isEmpty()) {
                yautja.setDebugWeaponLocked(false);
            }

            return taken;
        }

        var itemStack = inventory.getItemStack(slotIndex);

        if (itemStack.isEmpty() || count <= 0) {
            return ItemStack.EMPTY;
        }

        var removed = itemStack.split(count);

        inventory.setItemStack(slotIndex, itemStack);

        return removed;
    }

    @Override
    public @NotNull ItemStack removeItemNoUpdate(int slotIndex) {
        var equipment = equipmentFor(slotIndex);

        if (equipment != null) {
            var worn = yautja.getItemBySlot(equipment);

            yautja.setItemSlot(equipment, ItemStack.EMPTY);

            if (equipment == EquipmentSlot.MAINHAND) {
                yautja.setDebugWeaponLocked(false);
            }

            return worn;
        }

        var itemStack = inventory.getItemStack(slotIndex);

        inventory.setItemStack(slotIndex, ItemStack.EMPTY);

        return itemStack;
    }

    @Override
    public void setItem(int slotIndex, @NotNull ItemStack itemStack) {
        var equipment = equipmentFor(slotIndex);

        if (equipment != null) {
            yautja.setItemSlot(equipment, itemStack);

            // ⚠ Placing a weapon in the HAND locks it there (the debug weapon lock); armour never touches the lock.
            if (equipment == EquipmentSlot.MAINHAND) {
                yautja.setDebugWeaponLocked(!itemStack.isEmpty());
            }

            return;
        }

        inventory.setItemStack(slotIndex, itemStack);
    }

    @Override
    public void setChanged() {
        for (var slotIndex = 0; slotIndex < inventory.getSize(); slotIndex++) {
            inventory.setItemStack(slotIndex, inventory.getItemStack(slotIndex));
        }
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return yautja.isAlive()
            && !yautja.isRemoved()
            && yautja.distanceToSqr(player) <= MAXIMUM_INTERACTION_DISTANCE * MAXIMUM_INTERACTION_DISTANCE;
    }

    @Override
    public void clearContent() {
        inventory.clear();
    }

    public Yautja getYautja() {
        return yautja;
    }
}
