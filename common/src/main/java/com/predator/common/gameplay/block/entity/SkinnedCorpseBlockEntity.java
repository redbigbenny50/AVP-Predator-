package com.predator.common.gameplay.block.entity;

import com.predator.common.registry.init.PredatorBlockEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * The skinned corpse's inventory. [stated] "inside is a players sized inventory and toolbar. with maybe some extra
 * space for armor and such too. think of it like a corpse mod."
 * <h2>Sized to what it holds</h2> [stated] "instead of a weird second corpse cant we have the inventory dynamic in
 * size? adding extra slots if needed." The corpse is made exactly as many whole rows of nine as its contents need (one
 * row at the least). Up to six rows it opens as vanilla's chest screen of that height; beyond six, as the scrolling
 * corpse screen ({@code CorpseScrollMenu}). Nothing is ever left over, so nothing ever spills.
 * <h2>Vanilla's chest screens up to six rows</h2> ⚠ Deliberately vanilla's {@link ChestMenu} for 1-6 rows, not a menu
 * of our own: every inventory-sorting mod already understands it. Only a corpse past six rows needs the scrolling menu,
 * whose type is built by the loaders ({@code PredatorMenuTypes}) because {@code MenuType}'s constructor is private in
 * the vanilla {@code :common} compiles against.
 */
public class SkinnedCorpseBlockEntity extends BaseContainerBlockEntity {

    /** The size an empty corpse starts at (and an old save without a size is read at): the original five rows. */
    public static final int SIZE = 45;

    private static final String SIZE_TAG = "CorpseSize";

    private NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);

    public SkinnedCorpseBlockEntity(BlockPos pos, BlockState state) {
        super(PredatorBlockEntityTypes.SKINNED_CORPSE.get(), pos, state);
    }

    /**
     * Fills the corpse with a victim's belongings, first free slot onward.
     *
     * @return whatever did not fit — the caller drops it on the ground so nothing is ever deleted
     */
    /** Empties the corpse and hands back everything it held — for moving it (a falling corpse). */
    public List<ItemStack> takeAll() {
        var taken = new ArrayList<ItemStack>();

        for (var i = 0; i < items.size(); i++) {
            if (!items.get(i).isEmpty()) {
                taken.add(items.get(i));
                items.set(i, ItemStack.EMPTY);
            }
        }

        setChanged();

        return taken;
    }

    public List<ItemStack> fill(List<ItemStack> stacks) {
        var overflow = new ArrayList<ItemStack>();

        // ⚠ Grown first, to whole rows: everything already inside plus everything arriving, so the loop below always
        // finds room and the overflow list stays empty. It is kept only as a guard.
        var needed = 0;

        for (var held : items) {
            if (!held.isEmpty()) {
                needed++;
            }
        }

        for (var stack : stacks) {
            if (!stack.isEmpty()) {
                needed++;
            }
        }

        resize(Math.max(9, (needed + 8) / 9 * 9));

        for (var stack : stacks) {
            if (stack.isEmpty()) {
                continue;
            }

            var placed = false;

            for (var slot = 0; slot < items.size(); slot++) {
                if (items.get(slot).isEmpty()) {
                    items.set(slot, stack.copy());
                    placed = true;
                    break;
                }
            }

            if (!placed) {
                overflow.add(stack);
            }
        }

        setChanged();

        return overflow;
    }

    /** Resizes to {@code size} slots, keeping every stack. Never shrinks below what it holds. */
    private void resize(int size) {
        if (size == items.size()) {
            return;
        }

        var resized = NonNullList.withSize(size, ItemStack.EMPTY);
        var next = 0;

        for (var held : items) {
            if (!held.isEmpty() && next < size) {
                resized.set(next++, held);
            }
        }

        items = resized;
    }

    @Override
    protected @NotNull Component getDefaultName() {
        return Component.translatable("block.avp_predator.skinned_corpse");
    }

    @Override
    protected @NotNull NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(@NotNull NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    protected @NotNull AbstractContainerMenu createMenu(int containerId, @NotNull Inventory inventory) {
        var rows = items.size() / 9;

        return switch (rows) {
            case 1 -> new ChestMenu(MenuType.GENERIC_9x1, containerId, inventory, this, 1);
            case 2 -> new ChestMenu(MenuType.GENERIC_9x2, containerId, inventory, this, 2);
            case 3 -> new ChestMenu(MenuType.GENERIC_9x3, containerId, inventory, this, 3);
            case 4 -> new ChestMenu(MenuType.GENERIC_9x4, containerId, inventory, this, 4);
            case 5 -> new ChestMenu(MenuType.GENERIC_9x5, containerId, inventory, this, 5);
            case 6 -> new ChestMenu(MenuType.GENERIC_9x6, containerId, inventory, this, 6);
            default -> new com.predator.common.gameplay.menu.CorpseScrollMenu(containerId, inventory, this);
        };
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(SIZE_TAG, items.size());
        ContainerHelper.saveAllItems(tag, items, registries);
    }

    @Override
    protected void loadAdditional(@NotNull CompoundTag tag, HolderLookup.@NotNull Provider registries) {
        super.loadAdditional(tag, registries);
        // ⚠ A corpse saved before the size was dynamic has no size tag: it is read at the five rows it was built with.
        var size = tag.contains(SIZE_TAG) ? Math.max(9, tag.getInt(SIZE_TAG)) : SIZE;

        items = NonNullList.withSize(size, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, items, registries);
    }
}
