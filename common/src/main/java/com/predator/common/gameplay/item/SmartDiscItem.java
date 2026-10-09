package com.predator.common.gameplay.item;

import com.predator.common.gameplay.entity.projectile.SmartDiscProjectile;
import com.predator.common.gameplay.whip.WhipCord;
import com.predator.common.registry.init.PredatorDataComponents;
import com.predator.common.registry.init.PredatorSoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class SmartDiscItem extends Item {

    /** How far the disc's sounds wander from their recorded pitch. Matches the whip's. */
    private static final float SOUND_PITCH_SPREAD = 0.12F;

    /** ⚠ Stops every disc in the hotbar announcing itself the moment a player logs in. */
    private static final int EQUIP_SOUND_GRACE_TICKS = 10;

    /**
     * ⚠⚠ ON THE CHANGE, NOT EVERY TICK — same shape as the whip's equip sound, and the same reason: inventoryTick runs
     * twenty times a second for every stack a player carries, so the sound belongs only to the tick where this stack
     * BECOMES the selected one.
     */
    @Override
    public void inventoryTick(@NotNull ItemStack stack, @NotNull Level level, @NotNull Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);

        if (level.isClientSide || !(entity instanceof Player player)) {
            return;
        }

        var wasSelected = stack.getOrDefault(PredatorDataComponents.SMART_DISC_WAS_SELECTED.get(), false);

        if (selected == wasSelected) {
            return;
        }

        stack.set(PredatorDataComponents.SMART_DISC_WAS_SELECTED.get(), selected);

        if (selected && player.tickCount > EQUIP_SOUND_GRACE_TICKS) {
            level.playSound(
                null,
                player.blockPosition(),
                PredatorSoundEvents.SMART_DISC_EQUIP.get(),
                SoundSource.PLAYERS,
                0.9F,
                WhipCord.variedPitch(level.getRandom(), SOUND_PITCH_SPREAD * 0.5F)
            );
        }
    }

    public SmartDiscItem() {
        super(new Properties().stacksTo(1));
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(
        @NotNull Level level,
        @NotNull Player player,
        @NotNull InteractionHand usedHand
    ) {
        var itemInHand = player.getItemInHand(usedHand);
        if (!player.getCooldowns().isOnCooldown(this)) {
            player.getCooldowns().addCooldown(this, 5);
            // [stated] "throw is when you throw it". ⚠ The pitch wanders slightly, as the whip's do, so a player
            // throwing disc after disc does not hear the identical sample every time.
            level.playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                PredatorSoundEvents.SMART_DISC_THROW.get(),
                SoundSource.PLAYERS,
                1.0F,
                WhipCord.variedPitch(level.getRandom(), SOUND_PITCH_SPREAD)
            );

            if (!level.isClientSide) {
                var smartDiscItemEntity = new SmartDiscProjectile(level, player);
                // Single count, so the disc that returns is one disc. See ShurikenItem for why this matters.
                var thrown = itemInHand.copy();
                thrown.setCount(1);
                smartDiscItemEntity.setItem(thrown);
                smartDiscItemEntity.setOwner(player);
                smartDiscItemEntity.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 3.5F, 1.0F);
                level.addFreshEntity(smartDiscItemEntity);
            }
            player.awardStat(Stats.ITEM_USED.get(this));
            if (!player.getAbilities().instabuild)
                itemInHand.shrink(1);
            return InteractionResultHolder.sidedSuccess(itemInHand, level.isClientSide());
        } else {
            return InteractionResultHolder.fail(itemInHand);
        }
    }
}
