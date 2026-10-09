package com.predator.mixin;

import com.predator.client.screen.GauntletTabButton;
import com.predator.common.gameplay.item.GauntletItem;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a gauntlet tab to the player inventory screen.
 * <p>
 * <strong>⚠⚠ IT EXTENDS AbstractContainerScreen, AND THAT IS REQUIRED — NOT OPTIONAL.</strong> I removed the
 * {@code extends} in favour of {@code @Shadow} and the game refused to load:
 *
 * <pre>
 *   InvalidMixinException: &#64;Shadow method addRenderableWidget ... was not located in the
 *   target class net.minecraft.client.gui.screens.inventory.InventoryScreen
 * </pre>
 *
 * <strong>&#64;Shadow resolves against the TARGET CLASS ITSELF, not its superclasses.</strong> Checked against the jar:
 * {@code InventoryScreen} declares only {@code init()}; {@code leftPos} and {@code topPos} come from
 * {@code AbstractContainerScreen}, and {@code minecraft} and {@code addRenderableWidget} from {@code Screen}. The
 * shadowed FIELDS happened to resolve anyway — methods are stricter, which is why only this one threw.
 * <p>
 * Extending a class in the target's hierarchy is how inherited members become visible without shadowing them at all.
 * <p>
 * <strong>⚠ The constructor is required by javac, not by mixin.</strong> {@code AbstractContainerScreen} has no no-arg
 * constructor, so an abstract subclass must declare one. Mixin does NOT merge constructors from a mixin class, so this
 * never reaches the target. My earlier version passed {@code super(null, null, null)}; a real signature is the correct
 * idiom.
 * <p>
 * <strong>⚠ Left edge, and only while a gauntlet is worn.</strong> JEI, curios and the recipe book all crowd the right
 * side and below, and the tab sits OUTSIDE the panel so it cannot cover a slot if another mod shifts things.
 */
@Mixin(InventoryScreen.class)
public abstract class MixinInventoryScreen_GauntletTab extends AbstractContainerScreen<InventoryMenu> {

    private MixinInventoryScreen_GauntletTab(InventoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void avp_predator$addGauntletTab(CallbackInfo callback) {
        var player = minecraft == null ? null : minecraft.player;

        if (player == null || GauntletItem.equipped(player).isEmpty()) {
            return;
        }

        addRenderableWidget(new GauntletTabButton(leftPos - GauntletTabButton.WIDTH + 4, topPos + 8));
    }
}
