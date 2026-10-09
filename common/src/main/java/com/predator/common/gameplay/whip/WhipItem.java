package com.predator.common.gameplay.whip;

import com.predator.common.registry.init.PredatorDataComponents;
import com.predator.common.registry.init.PredatorSoundEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * The whip: LEFT click cracks it, RIGHT click fires the grapple.
 * <p>
 * Both halves are the same cord — {@link WhipLashEntity} for the swing, {@link WhipHookEntity} for the hook — so the
 * weapon reads as one object rather than two tools sharing a sprite.
 * <h2>Enchanting</h2> Tagged as sword-enchantable in datagen, so Sharpness, Fire Aspect and Looting all apply and are
 * honoured by both the lash and the hook. [stated] "let it be enchantable with sword fire enchantment."
 */
public class WhipItem extends Item {

    /** Ticks between equip sounds, so switching back and forth does not machine-gun it. */
    private static final int EQUIP_SOUND_COOLDOWN_TICKS = 10;

    public WhipItem() {
        super(new Item.Properties().stacksTo(1).durability(Tiers.DIAMOND.getUses()));
    }

    /**
     * ⚠⚠ CALLED FROM MixinLivingEntity_WhipSwing, NOT FROM AN OVERRIDE. {@code Item.onEntitySwing} is a NeoForge
     * extension that does not exist in {@code :common}, so the swing is caught on {@code LivingEntity.swing} instead —
     * which fires for a left click at AIR as well as at a target, and there is nothing to "attack" at six blocks.
     * <p>
     * ⚠ The grapple's cooldown is what stops a right click also cracking the whip: {@link #use} sets it before the hand
     * animation runs, so {@link #canSwing} refuses.
     */
    public static void onSwing(Player player, ItemStack stack) {
        if (!player.level().isClientSide && canSwing(player)) {
            swing(player, stack);
        }
    }

    private static boolean canSwing(Player player) {
        return !player.getCooldowns().isOnCooldown(player.getMainHandItem().getItem());
    }

    private static void swing(Player player, ItemStack stack) {
        var level = player.level();

        level.addFreshEntity(new WhipLashEntity(level, player, stack));
        // [stated] "this is when its extending as you hit the left mouse button."
        level.playSound(
            null,
            player.blockPosition(),
            PredatorSoundEvents.WHIP_ATTACK.get(),
            SoundSource.PLAYERS,
            1.0F,
            WhipCord.variedPitch(level.getRandom(), WhipTuning.SOUND_PITCH_SPREAD)
        );
        player.getCooldowns().addCooldown(stack.getItem(), WhipTuning.LASH_COOLDOWN_TICKS);
        stack.hurtAndBreak(1, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
    }

    /**
     * Plays the coil when the whip is first brought out.
     * <p>
     * ⚠⚠ ON THE CHANGE, NOT EVERY TICK. inventoryTick runs twenty times a second for every stack a player carries; the
     * sound belongs only to the tick where this stack BECOMES the selected one. [stated] "this is when you equip the
     * whip". The short cooldown stops a player flicking between two hotbar slots from firing it repeatedly.
     */
    @Override
    public void inventoryTick(@NotNull ItemStack stack, @NotNull Level level, @NotNull Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);

        if (level.isClientSide || !(entity instanceof Player player)) {
            return;
        }

        var wasSelected = stack.getOrDefault(PredatorDataComponents.WHIP_WAS_SELECTED.get(), false);

        if (selected == wasSelected) {
            return;
        }

        stack.set(PredatorDataComponents.WHIP_WAS_SELECTED.get(), selected);

        if (selected && player.tickCount > EQUIP_SOUND_COOLDOWN_TICKS) {
            level.playSound(
                null,
                player.blockPosition(),
                PredatorSoundEvents.WHIP_EQUIP.get(),
                SoundSource.PLAYERS,
                0.8F,
                // ⚠ HALF SPREAD. Coiling is the same motion every time, so it should wander less than a crack,
                // whose sound depends on how the cord travelled.
                WhipCord.variedPitch(level.getRandom(), WhipTuning.SOUND_PITCH_SPREAD * 0.5F)
            );
        }
    }

    /**
     * ⚠⚠ NO RIGHT-CLICK BEHAVIOUR AT ALL, DELIBERATELY. The grapple moved to the gauntlet as the chain whip — [stated]
     * "we would be splitting the grapple hook to its own item and removing it from the whip nows the time" — so the
     * whip is a left-click weapon and nothing else.
     * <p>
     * ⚠ PASS, not consume: with nothing to do on a right click, the click must reach the worn gauntlet, which is now
     * where the grapple lives. The BLOCKS_GAUNTLET_FIRE tag entry for the whip is removed for the same reason.
     */

    /** {@return whether the cord is out, for the model predicate that swaps to the empty-handle sprite} */
    public static boolean isExtended(ItemStack stack, @NotNull LivingEntity holder) {
        return holder instanceof Player player && WhipGrapple.hookOf(player) != null;
    }

    /** Server tick for every player: runs the reel, and nothing at all when no hook is out. */
    public static void tickPlayer(Player player) {
        if (player.level() instanceof ServerLevel) {
            WhipGrapple.tick(player);
        }
    }
}
