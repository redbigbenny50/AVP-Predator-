package com.predator.common.gameplay.debug.yautja;

import com.predator.common.gameplay.entity.living.yautja.Yautja;
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
import org.jetbrains.annotations.Nullable;

/**
 * A yautja's eight carried slots, opened by a creative player for testing.
 * <h2>⚠⚠ A DEBUG TOOL, GATED THREE WAYS</h2> Creative mode, an empty main hand, and the {@code yautjaDebugInventory}
 * gamerule (default OFF). Anything else falls through to the yautja's normal interaction, so this cannot affect play
 * even when the code is shipped.
 * <h2>Removing it</h2> Everything lives in {@code common.gameplay.debug.yautja} and {@code client.screen.debug} plus
 * one menu type, one gamerule and the hook in {@code Yautja.mobInteract}. See DEBUG_INVENTORY.txt.
 */
public class YautjaInventoryMenu extends AbstractContainerMenu {

    // ⚠⚠ ITEM positions (a slot's frame is one pixel up-left of these). They MUST match the slots drawn in
    // textures/gui/container/yautja_inventory.png — the texture was generated from these same numbers.
    private static final int SLOT_SIZE = 18;

    private static final int ARMOR_X = 44;

    private static final int ARMOR_Y = 18;

    private static final int HAND_SLOT_X = 133;

    private static final int HAND_SLOT_Y = 45;

    private static final int RACK_X = 8;

    private static final int RACK_Y = 107;

    /** [stated] his mockup: two rows of eight. */
    private static final int RACK_COLUMNS = 8;

    private static final int PLAYER_INVENTORY_X = 8;

    private static final int PLAYER_INVENTORY_Y = 158;

    private static final int PLAYER_HOTBAR_Y = 226;

    /** Every yautja slot comes first in the menu: rack, hand, then armour — SLOT_COUNT of them. */
    private static final int YAUTJA_SLOTS = YautjaInventoryContainer.SLOT_COUNT;

    private final Container container;

    private final @Nullable Yautja yautja;

    /** The yautja's entity id, synced to the client so the screen can name what it is holding. */
    private final DataSlot yautjaId = DataSlot.standalone();

    private final Inventory playerInventory;

    /**
     * Client constructor.
     * <p>
     * 🚨🚨 NO EXTRA DATA BUFFER. The two-argument {@code openMenu(provider, buffer)} is a NEOFORGE EXTENSION — vanilla
     * has only {@code openMenu(MenuProvider)} — so passing the yautja's id that way compiles in a NeoForge harness and
     * fails in {@code :common}. avp_human's marine menu dodges this by handing the client a null mob, but then the
     * screen can never show anything about it.
     * <p>
     * ⚠ THE ID TRAVELS IN A DATA SLOT INSTEAD. That is vanilla's own mechanism for syncing an int to an open menu, it
     * costs nothing, and it works identically on both loaders — see {@link #yautjaId}.
     */
    public YautjaInventoryMenu(int containerId, Inventory playerInventory) {
        // 🚨🚨 SIZED FOR THE HAND SLOT TOO. The menu has INVENTORY_SIZE + 1 slots since the main hand was added, and
        // the server syncs one item per SLOT — a container of only INVENTORY_SIZE overruns the moment the contents
        // packet arrives: "ArrayIndexOutOfBoundsException: Index 8 out of bounds for length 8", client-side, the
        // instant the screen opens. [stated] "game explodees if you open a preds inventory".
        this(containerId, playerInventory, new SimpleContainer(YautjaInventoryContainer.SLOT_COUNT), null);
    }

    public YautjaInventoryMenu(int containerId, Inventory playerInventory, Container container, @Nullable Yautja yautja) {
        super(PredatorMenuTypes.yautjaInventory(), containerId);

        this.container = container;
        this.yautja = yautja;
        this.playerInventory = playerInventory;

        addDataSlot(yautjaId);

        if (yautja != null) {
            yautjaId.set(yautja.getId());
        }

        container.startOpen(playerInventory.player);

        // The yautja's own eight, in one row.
        for (var slot = 0; slot < Yautja.INVENTORY_SIZE; slot++) {
            // ⚠ YautjaGearSlot, not a plain Slot: it refuses player-only weapons (the hand caster).
            addSlot(
                new YautjaGearSlot(container, slot, RACK_X + slot % RACK_COLUMNS * SLOT_SIZE, RACK_Y + slot / RACK_COLUMNS * SLOT_SIZE)
            );
        }

        // ⚠ The hand is a WHITELIST (YautjaHandSlot): yautja weapons only — [stated] "people are giving it nether
        // stars".
        addSlot(new YautjaHandSlot(container, YautjaInventoryContainer.HAND_SLOT, HAND_SLOT_X, HAND_SLOT_Y));

        // [stated] "the armor it has on is shown as well so if you wanted to you can remove the armor too". Head to
        // feet, top to bottom, like the player's own inventory.
        addSlot(
            new YautjaArmorSlot(
                container,
                YautjaInventoryContainer.HEAD_SLOT,
                ARMOR_X,
                ARMOR_Y,
                net.minecraft.world.entity.EquipmentSlot.HEAD
            )
        );
        addSlot(
            new YautjaArmorSlot(
                container,
                YautjaInventoryContainer.CHEST_SLOT,
                ARMOR_X,
                ARMOR_Y + SLOT_SIZE,
                net.minecraft.world.entity.EquipmentSlot.CHEST
            )
        );
        addSlot(
            new YautjaArmorSlot(
                container,
                YautjaInventoryContainer.LEGS_SLOT,
                ARMOR_X,
                ARMOR_Y + 2 * SLOT_SIZE,
                net.minecraft.world.entity.EquipmentSlot.LEGS
            )
        );
        addSlot(
            new YautjaArmorSlot(
                container,
                YautjaInventoryContainer.FEET_SLOT,
                ARMOR_X,
                ARMOR_Y + 3 * SLOT_SIZE,
                net.minecraft.world.entity.EquipmentSlot.FEET
            )
        );

        for (var row = 0; row < 3; row++) {
            for (var column = 0; column < 9; column++) {
                addSlot(
                    new Slot(
                        playerInventory,
                        column + row * 9 + 9,
                        PLAYER_INVENTORY_X + column * SLOT_SIZE,
                        PLAYER_INVENTORY_Y + row * SLOT_SIZE
                    )
                );
            }
        }

        for (var column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, PLAYER_INVENTORY_X + column * SLOT_SIZE, PLAYER_HOTBAR_Y));
        }
    }

    public @Nullable Yautja yautja() {
        if (yautja != null) {
            return yautja;
        }

        var id = yautjaId.get();

        return id != 0 && playerInventory.player.level().getEntity(id) instanceof Yautja found ? found : null;
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        var slot = slots.get(index);

        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        var stack = slot.getItem();
        var copy = stack.copy();
        // ⚠ The hand counts as one of the yautja's slots for shift-click purposes, so +1.
        if (index < YAUTJA_SLOTS) {
            // Out of the yautja, into the player.
            if (!moveItemStackTo(stack, YAUTJA_SLOTS, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Into the yautja: predator armour goes to its armour slot first (each slot accepts only its own piece),
            // everything else to the RACK. ⚠ Never shift-clicked into the HAND: placing a weapon there sets the debug
            // weapon lock, which a shift-click should not do behind your back — drag it there on purpose.
            var armourFirst = YautjaInventoryContainer.HEAD_SLOT;

            if (
                !moveItemStackTo(stack, armourFirst, YAUTJA_SLOTS, false)
                    && !moveItemStackTo(stack, 0, Yautja.INVENTORY_SIZE, false)
            ) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        return copy;
    }

    /**
     * ⚠⚠ RE-EQUIP ON CLOSE. [stated] "yes closing force repicks." The yautja only re-picks a weapon when its TARGET
     * changes, so kit swapped into an idle one would sit unused until something provoked it — you would be watching the
     * old weapon and wondering why. Clearing the cached target forces a fresh pick on the next tick.
     */
    @Override
    public void removed(@NotNull Player player) {
        super.removed(player);
        container.stopOpen(player);

        if (yautja != null && !player.level().isClientSide) {
            yautja.forceWeaponRepick();
        }
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return yautja == null || (yautja.isAlive() && yautja.distanceToSqr(player) <= 64.0D);
    }
}
