package com.predator.common.gameplay.item;

import com.predator.common.gameplay.menu.GauntletContents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * The cloaking device.
 * <h2>His spec for using one while a gauntlet is worn</h2> "if a player has one inside their gauntlet already and its
 * equipped and they try to use a cloaking device it should say 'you have a cloak equipped to your bracer already use
 * [hot key] to activate'. If they dont have one in the gauntlet and its equipped and they try to use the device, have
 * it auto place inside the gauntlet."
 * <h2>⚠⚠ THE MESSAGE NAMES THE PLAYER'S ACTUAL KEY, NOT "C"</h2> {@link Component#keybind} resolves client-side to
 * whatever the player has bound. Hardcoding "C" would be wrong for anyone who rebound it — and telling someone to press
 * a key that does nothing is worse than saying nothing.
 * <h2>⚠ Auto-fitting is a courtesy, not a rule</h2> It only happens when a gauntlet is EQUIPPED and its housing is
 * EMPTY. With no gauntlet worn the device behaves as it always did, so nothing is taken out of the player's hands by
 * surprise.
 */
public class CloakingDeviceItem extends Item {

    public CloakingDeviceItem() {
        super(new Item.Properties().stacksTo(1).rarity(Rarity.RARE).fireResistant());
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, Player player, @NotNull InteractionHand hand) {
        var stack = player.getItemInHand(hand);
        var gauntlet = GauntletItem.equipped(player);

        // ⚠ No gauntlet worn — unchanged behaviour. This method exists for the gauntlet case only.
        if (gauntlet.isEmpty()) {
            return InteractionResultHolder.pass(stack);
        }

        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }

        var contents = new GauntletContents(gauntlet);
        var housing = contents.getItem(GauntletContents.CLOAK_SLOT);

        if (!housing.isEmpty()) {
            player.displayClientMessage(
                Component.translatable(
                    "gauntlet.avp_predator.cloak_already_fitted",
                    Component.keybind("key.avp_predator.toggle_gauntlet_cloak")
                ),
                true
            );

            return InteractionResultHolder.fail(stack);
        }

        // ⚠ ONE device moves across, not the stack. The housing holds a single core, and shrinking by one leaves any
        // spares where the player put them rather than silently swallowing them.
        var fitted = stack.copyWithCount(1);

        contents.setItem(GauntletContents.CLOAK_SLOT, fitted);
        stack.shrink(1);

        player.displayClientMessage(
            Component.translatable(
                "gauntlet.avp_predator.cloak_fitted",
                Component.keybind("key.avp_predator.toggle_gauntlet_cloak")
            ),
            true
        );

        level.playSound(
            null,
            player.getX(),
            player.getY(),
            player.getZ(),
            SoundEvents.ARMOR_EQUIP_NETHERITE.value(),
            SoundSource.PLAYERS,
            0.7F,
            1.2F
        );

        return InteractionResultHolder.consume(stack);
    }
}
