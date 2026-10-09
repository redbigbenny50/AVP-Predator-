package com.predator.common.gameplay.menu;

import com.predator.common.registry.init.PredatorMenuTypes;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * A skinned corpse too big for a chest screen — more than 6 rows. [stated] "instead of a weird second corpse cant we
 * have the inventory dynamic in size? adding extra slots if needed."
 * <h2>A window that scrolls over the corpse</h2> The screen always shows 6 rows. Those 54 slots look into the corpse at
 * {@link #scrollRow}, and scrolling moves the window: the SERVER moves it (a menu button click), so the slots' contents
 * are re-sent by the ordinary container sync. Nothing about the slot layout ever changes, which is what lets this use a
 * plain {@code MenuType} with no extra opening data on either loader.
 * <ul>
 * <li>{@link #totalRows} and {@link #scrollRow} ride to the client as data slots, for the scrollbar.</li>
 * <li>Shift-clicking OUT works on whatever is in view; shift-clicking IN finds room anywhere in the corpse, not just
 * the visible rows.</li>
 * </ul>
 */
public class CorpseScrollMenu extends AbstractContainerMenu {

    public static final int VISIBLE_ROWS = 6;

    public static final int WINDOW_SLOTS = VISIBLE_ROWS * 9;

    /** Button ids: scroll up / down a row; {@code JUMP_BASE + row} jumps straight to a row (the scrollbar). */
    public static final int BUTTON_UP = 0;

    public static final int BUTTON_DOWN = 1;

    public static final int JUMP_BASE = 100;

    private final Container corpse;

    private final DataSlot totalRows = DataSlot.standalone();

    private final DataSlot scrollRow = DataSlot.standalone();

    /** Client side: a stand-in the size of the window; the server fills it through the normal sync. */
    public CorpseScrollMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(WINDOW_SLOTS), true);
    }

    /** Server side, over a corpse of any size. */
    public CorpseScrollMenu(int containerId, Inventory playerInventory, Container corpse) {
        this(containerId, playerInventory, corpse, false);
    }

    private CorpseScrollMenu(int containerId, Inventory playerInventory, Container corpse, boolean clientStandIn) {
        super(PredatorMenuTypes.corpseScroll(), containerId);
        this.corpse = corpse;

        totalRows.set(Math.max(VISIBLE_ROWS, (corpse.getContainerSize() + 8) / 9));
        scrollRow.set(0);
        addDataSlot(totalRows);
        addDataSlot(scrollRow);

        corpse.startOpen(playerInventory.player);

        var window = clientStandIn ? corpse : new Window();

        for (var row = 0; row < VISIBLE_ROWS; row++) {
            for (var column = 0; column < 9; column++) {
                addSlot(new CorpseSlot(window, column + row * 9, 8 + column * 18, 18 + row * 18));
            }
        }

        // The player's inventory, laid out exactly as vanilla's six-row chest screen lays it.
        var offset = (VISIBLE_ROWS - 4) * 18;

        for (var row = 0; row < 3; row++) {
            for (var column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 103 + row * 18 + offset));
            }
        }

        for (var column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 8 + column * 18, 161 + offset));
        }
    }

    public int totalRows() {
        return totalRows.get();
    }

    public int scrollRow() {
        return scrollRow.get();
    }

    public int maxScrollRow() {
        return Math.max(0, totalRows.get() - VISIBLE_ROWS);
    }

    @Override
    public boolean clickMenuButton(@NotNull Player player, int id) {
        var row = switch (id) {
            case BUTTON_UP -> scrollRow.get() - 1;
            case BUTTON_DOWN -> scrollRow.get() + 1;
            default -> id >= JUMP_BASE ? id - JUMP_BASE : scrollRow.get();
        };

        scrollRow.set(Math.max(0, Math.min(maxScrollRow(), row)));
        broadcastChanges();

        return true;
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        var slot = slots.get(index);

        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        var stack = slot.getItem();
        var original = stack.copy();

        if (index < WINDOW_SLOTS) {
            // Corpse to player.
            if (!moveItemStackTo(stack, WINDOW_SLOTS, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!insertAnywhere(stack)) {
            // Player to corpse: anywhere in it, not only the rows in view.
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        return original;
    }

    /**
     * Merges into matching stacks first, then empty slots, across the whole corpse. {@return whether anything moved}
     */
    private boolean insertAnywhere(ItemStack stack) {
        var before = stack.getCount();

        for (var pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
            for (var i = 0; i < corpse.getContainerSize() && !stack.isEmpty(); i++) {
                var existing = corpse.getItem(i);

                if (pass == 0 && !existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, stack)) {
                    var room = Math.min(existing.getMaxStackSize(), corpse.getMaxStackSize()) - existing.getCount();
                    var moved = Math.min(room, stack.getCount());

                    if (moved > 0) {
                        existing.grow(moved);
                        stack.shrink(moved);
                        corpse.setChanged();
                    }
                } else if (pass == 1 && existing.isEmpty()) {
                    corpse.setItem(i, stack.split(Math.min(stack.getCount(), stack.getMaxStackSize())));
                }
            }
        }

        return stack.getCount() != before;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return corpse.stillValid(player);
    }

    @Override
    public void removed(@NotNull Player player) {
        super.removed(player);
        corpse.stopOpen(player);
    }

    /** A window slot; the ones past the end of the corpse are hidden and take nothing. */
    private final class CorpseSlot extends Slot {

        CorpseSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        private boolean inCorpse() {
            return scrollRow.get() * 9 + getContainerSlot() < totalRows.get() * 9;
        }

        @Override
        public boolean isActive() {
            return inCorpse();
        }

        @Override
        public boolean mayPlace(@NotNull ItemStack stack) {
            return inCorpse();
        }
    }

    /** The 54 visible slots, mapped onto the corpse at the current scroll. Server side only. */
    private final class Window implements Container {

        private int index(int slot) {
            return scrollRow.get() * 9 + slot;
        }

        private boolean valid(int slot) {
            return index(slot) < corpse.getContainerSize();
        }

        @Override
        public int getContainerSize() {
            return WINDOW_SLOTS;
        }

        @Override
        public boolean isEmpty() {
            return corpse.isEmpty();
        }

        @Override
        public @NotNull ItemStack getItem(int slot) {
            return valid(slot) ? corpse.getItem(index(slot)) : ItemStack.EMPTY;
        }

        @Override
        public @NotNull ItemStack removeItem(int slot, int amount) {
            return valid(slot) ? corpse.removeItem(index(slot), amount) : ItemStack.EMPTY;
        }

        @Override
        public @NotNull ItemStack removeItemNoUpdate(int slot) {
            return valid(slot) ? corpse.removeItemNoUpdate(index(slot)) : ItemStack.EMPTY;
        }

        @Override
        public void setItem(int slot, @NotNull ItemStack stack) {
            if (valid(slot)) {
                corpse.setItem(index(slot), stack);
            }
        }

        @Override
        public int getMaxStackSize() {
            return corpse.getMaxStackSize();
        }

        @Override
        public void setChanged() {
            corpse.setChanged();
        }

        @Override
        public boolean stillValid(@NotNull Player player) {
            return corpse.stillValid(player);
        }

        @Override
        public void clearContent() {
            // Never through the window.
        }
    }
}
