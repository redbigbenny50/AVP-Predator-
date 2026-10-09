package com.predator.common.gameplay.menu;

import com.blib.api.client.animation.v1.command.play_behavior.AzPlayBehaviors;
import com.predator.common.gameplay.item.GauntletItem;
import com.predator.common.registry.init.PredatorMenuTypes;
import com.predator.common.registry.init.PredatorSoundEvents;
import com.predator.common.registry.init.item.PredatorItems;
import com.predator.common.registry.tag.PredatorItemTags;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * The gauntlet container: five ammunition slots, one cloak housing, and the player's inventory.
 * <h2>⚠ The coordinates come straight off the GUI texture</h2> Ammunition at x=8 with an 18px pitch and y=16; cloak
 * housing at 135,45; player inventory at the vanilla positions. Changing one without the other silently misaligns the
 * whole screen, so they are constants here rather than magic numbers in the screen class.
 */
public class GauntletMenu extends AbstractContainerMenu {

    public static final int AMMO_X = 11;

    public static final int AMMO_Y = 17;

    public static final int SLOT_PITCH = 18;

    public static final int CLOAK_X = 136;

    public static final int CLOAK_Y = 36;

    private static final int INVENTORY_X = 9;

    private static final int INVENTORY_Y = 85;

    private static final int HOTBAR_Y = 143;

    private final GauntletContents contents;

    private final ItemStack gauntlet;

    private final InteractionHand hand;

    /** The live stack this menu is bound to (the offhand gauntlet for the worn GUI). */
    public ItemStack getGauntlet() {
        return gauntlet;
    }

    /**
     * Client constructor — the menu type calls this with no stack.
     * <p>
     * <strong>⚠⚠ IT MUST BIND TO THE REAL OFFHAND STACK, NOT ItemStack.EMPTY.</strong> This passed EMPTY, and
     * {@code ItemStack.EMPTY} is a shared singleton that silently DISCARDS component writes. So every slot update the
     * server synced was thrown away and the ammunition slots always rendered empty — while firing still worked, because
     * the SERVER's copy of the menu had the real stack. That mismatch is exactly what he saw: items vanish on screen
     * but still shoot.
     * <p>
     * ⚠ Falls back to EMPTY only if nothing is worn, which cannot happen through normal play — the menu can only be
     * opened with a gauntlet equipped.
     */
    public GauntletMenu(int id, Inventory inventory) {
        this(id, inventory, GauntletItem.equipped(inventory.player), InteractionHand.OFF_HAND);
    }

    public GauntletMenu(int id, Inventory inventory, ItemStack gauntlet, InteractionHand hand) {
        super(PredatorMenuTypes.gauntlet(), id);

        this.gauntlet = gauntlet;
        this.hand = hand;
        this.contents = new GauntletContents(gauntlet);

        for (var index = 0; index < GauntletContents.AMMO_SLOTS; index++) {
            addSlot(
                new Slot(contents, index, AMMO_X + index * SLOT_PITCH, AMMO_Y) {

                    /** ⚠ Only gauntlet ammunition — a tag, so a pack can add its own without touching this class. */
                    @Override
                    public boolean mayPlace(@NotNull ItemStack stack) {
                        return stack.is(PredatorItemTags.GAUNTLET_AMMUNITION);
                    }
                }
            );
        }

        addSlot(
            new Slot(contents, GauntletContents.CLOAK_SLOT, CLOAK_X, CLOAK_Y) {

                @Override
                public boolean mayPlace(@NotNull ItemStack stack) {
                    return stack.is(PredatorItemTags.CLOAK_CORES);
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            }
        );

        for (var row = 0; row < 3; row++) {
            for (var column = 0; column < 9; column++) {
                addSlot(
                    new Slot(
                        inventory,
                        column + row * 9 + 9,
                        INVENTORY_X + column * SLOT_PITCH,
                        INVENTORY_Y + row * SLOT_PITCH
                    )
                );
            }
        }

        for (var column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, INVENTORY_X + column * SLOT_PITCH, HOTBAR_Y));
        }
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int index) {
        var slot = slots.get(index);

        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        // ⚠⚠ A COPY, because GauntletContents.getItem parses the component fresh each call and hands back a value,
        // not a live reference. moveItemStackTo mutates what it is given, so the result MUST be written back
        // explicitly — setChanged() is a no-op in this container and would silently discard a partial move.
        var moving = slot.getItem().copy();
        var original = moving.copy();

        if (index < GauntletContents.SIZE) {
            if (!moveItemStackTo(moving, GauntletContents.SIZE, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(moving, 0, GauntletContents.SIZE, false)) {
            return ItemStack.EMPTY;
        }

        // ⚠ Write the remainder back whether it emptied or not. This is the line that stops a half-moved stack
        // being duplicated: without it the source keeps its original count while the destination also has the items.
        slot.set(moving.isEmpty() ? ItemStack.EMPTY : moving);

        // ⚠ Vanilla's contract — side effects on the source slot fire on a shift-click too.
        slot.onTake(player, moving);

        return original;
    }

    /**
     * ⚠ Closes if the gauntlet leaves the offhand OR stops being a gauntlet. Identity alone is not enough: a stack can
     * be consumed and replaced in place, and a menu still writing into it would resurrect its contents.
     */
    @Override
    public boolean stillValid(@NotNull Player player) {
        var held = player.getItemInHand(hand);

        return held == gauntlet && held.is(PredatorItems.GAUNTLET.get());
    }

    /**
     * ⚠ The close clip is sent from HERE, server-side, so everyone around sees the wrist door shut — the mirror of the
     * open sent when the menu is opened. {@code removed} runs on both sides; only the server sends. Only for the WORN
     * gauntlet: a menu on a main-hand one has no clip to close.
     */
    @Override
    public void removed(@NotNull Player player) {
        super.removed(player);

        if (!player.level().isClientSide && hand == InteractionHand.OFF_HAND && GauntletItem.isEquipped(player, gauntlet)) {
            GauntletItem.syncClip(player, "close", AzPlayBehaviors.PLAY_ONCE);
            player.level()
                .playSound(null, player.blockPosition(), PredatorSoundEvents.GAUNTLET_CLOSE.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
        }
    }
}
