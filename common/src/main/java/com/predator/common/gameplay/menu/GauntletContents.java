package com.predator.common.gameplay.menu;

import com.predator.common.gameplay.item.gauntlet.GauntletAmmo;
import com.predator.common.registry.init.PredatorDataComponents;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import org.jetbrains.annotations.NotNull;

/**
 * The gauntlet's six slots, backed by the item stack itself.
 * <h2>⚠⚠ NO CACHED COPY. THIS IS THE ANTI-DUPLICATION DESIGN.</h2> The obvious implementation reads the component into
 * a {@code NonNullList} once and writes it back on change. That is exactly how a stack-backed container duplicates:
 * FIVE places build one of these — the menu, the launcher, the ammo cycler, the cloaking device and the open handler —
 * and each would hold its own snapshot. Whichever saved last would win, and the other's change would either vanish or
 * come back after a reopen. That is the shape of the bug he hit in the old mod.
 * <p>
 * Instead every read parses the component and every write serialises it immediately, so the STACK is the single source
 * of truth and two live instances cannot disagree. It costs a small parse per access, which is nothing against six
 * slots and correctness that cannot silently drift.
 * <h2>Layout</h2> Slots 0-4 are the ammunition strip along the top of the GUI; slot 5 is the cloak housing beside the
 * glyphs.
 */
public class GauntletContents implements Container {

    public static final int AMMO_SLOTS = 5;

    public static final int CLOAK_SLOT = 5;

    public static final int SIZE = 6;

    private final ItemStack gauntlet;

    public GauntletContents(ItemStack gauntlet) {
        this.gauntlet = gauntlet;
    }

    /** ⚠ Fresh every call. Never cache the result — that is the whole point of this class. */
    private NonNullList<ItemStack> read() {
        var items = NonNullList.withSize(SIZE, ItemStack.EMPTY);
        var stored = gauntlet.get(PredatorDataComponents.GAUNTLET_CONTENTS.get());

        if (stored != null) {
            stored.copyInto(items);
        }

        return items;
    }

    private void write(NonNullList<ItemStack> items) {
        gauntlet.set(PredatorDataComponents.GAUNTLET_CONTENTS.get(), ItemContainerContents.fromItems(items));
    }

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    public boolean isEmpty() {
        return read().stream().allMatch(ItemStack::isEmpty);
    }

    /**
     * ⚠⚠ RETURNS A LIVE-LOOKING STACK THAT IS ACTUALLY A COPY, and callers must not rely on mutating it. Vanilla's slot
     * code does {@code getItem().shrink(n)} then {@code setChanged()} — with a parsed copy the shrink would be thrown
     * away, so {@link #setChanged()} is overridden below to be a no-op rather than re-saving stale data. Every real
     * mutation goes through {@link #setItem} or {@link #removeItem}, which vanilla also calls.
     */
    @Override
    public @NotNull ItemStack getItem(int slot) {
        return read().get(slot);
    }

    @Override
    public @NotNull ItemStack removeItem(int slot, int amount) {
        var items = read();
        var removed = ContainerHelper.removeItem(items, slot, amount);

        if (!removed.isEmpty()) {
            write(items);
        }

        return removed;
    }

    @Override
    public @NotNull ItemStack removeItemNoUpdate(int slot) {
        var items = read();
        var removed = ContainerHelper.takeItem(items, slot);

        write(items);

        return removed;
    }

    @Override
    public void setItem(int slot, @NotNull ItemStack stack) {
        var items = read();

        items.set(slot, stack);
        write(items);
    }

    /**
     * ⚠ Deliberately a NO-OP. With no cached list there is nothing pending to flush, and re-writing here would
     * serialise whatever {@link #read()} last returned — potentially undoing a change another instance just made.
     */
    @Override
    public void setChanged() {}

    /** ⚠ Always true: the container lives on a stack in the player's own hand, so there is no distance to check. */
    @Override
    public boolean stillValid(@NotNull Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        write(NonNullList.withSize(SIZE, ItemStack.EMPTY));
    }

    /**
     * {@return how many rounds of this type are loaded across the ammunition strip}
     * <p>
     * ⚠ The STRIP only — a cloaking device in its housing must never read as ammunition.
     */
    public int countOf(GauntletAmmo ammo) {
        if (ammo.itemPath() == null) {
            return 0;
        }

        var items = read();
        var total = 0;

        for (var slot = 0; slot < AMMO_SLOTS; slot++) {
            if (matches(items.get(slot), ammo)) {
                total += items.get(slot).getCount();
            }
        }

        return total;
    }

    /** {@return the first strip slot holding this type, or -1} */
    public int firstSlotOf(GauntletAmmo ammo) {
        var items = read();

        for (var slot = 0; slot < AMMO_SLOTS; slot++) {
            if (matches(items.get(slot), ammo)) {
                return slot;
            }
        }

        return -1;
    }

    /** ⚠ By registry path, so the ammo enum never has to import the item registry. */
    public static boolean matches(ItemStack stack, GauntletAmmo ammo) {
        if (stack.isEmpty() || ammo.itemPath() == null) {
            return false;
        }

        var key = BuiltInRegistries.ITEM.getKey(stack.getItem());

        return "avp_predator".equals(key.getNamespace()) && ammo.itemPath().equals(key.getPath());
    }
}
