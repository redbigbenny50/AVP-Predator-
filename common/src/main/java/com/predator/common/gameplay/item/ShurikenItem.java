package com.predator.common.gameplay.item;

import com.predator.common.gameplay.entity.projectile.ShurikenProjectile;
import com.predator.common.gameplay.whip.WhipCord;
import com.predator.common.registry.init.PredatorDataComponents;
import com.predator.common.registry.init.PredatorSoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

public class ShurikenItem extends Item {

    /** How far the shuriken's sounds wander from their recorded pitch. Matches the disc's and the whip's. */
    private static final float SOUND_PITCH_SPREAD = 0.12F;

    /** ⚠ Stops a hotbar of shuriken announcing themselves the moment a player logs in. */
    private static final int EQUIP_SOUND_GRACE_TICKS = 10;

    /**
     * ⚠⚠ ON THE CHANGE, NOT EVERY TICK — the same shape as the whip's and the disc's, for the same reason:
     * inventoryTick runs twenty times a second for every stack a player carries.
     */
    @Override
    public void inventoryTick(@NotNull ItemStack stack, @NotNull Level level, @NotNull Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);

        if (level.isClientSide || !(entity instanceof Player player)) {
            return;
        }

        var wasSelected = stack.getOrDefault(PredatorDataComponents.SHURIKEN_WAS_SELECTED.get(), false);

        if (selected == wasSelected) {
            return;
        }

        stack.set(PredatorDataComponents.SHURIKEN_WAS_SELECTED.get(), selected);

        if (selected && player.tickCount > EQUIP_SOUND_GRACE_TICKS) {
            level.playSound(
                null,
                player.blockPosition(),
                PredatorSoundEvents.SHURIKEN_EQUIP.get(),
                SoundSource.PLAYERS,
                0.9F,
                WhipCord.variedPitch(level.getRandom(), SOUND_PITCH_SPREAD * 0.5F)
            );
        }
    }

    public ShurikenItem() {
        // ⚠ His spec: "a throwable weapon that has stacks of ammo". This was stacksTo(1), so there was no ammo
        // to stack — you carried one shuriken, threw it, and it was destroyed on contact.
        super(new Properties().stacksTo(16));
    }

    /**
     * The wind-up pose while the throw is charging.
     * <p>
     * ⚠⚠ WITHOUT THIS THE ARM DOES NOTHING. {@code Item.getUseAnimation} defaults to {@code UseAnim.NONE}, which is why
     * holding right-click charged the throw with no visible sign — his "you have no idea if you held it down that
     * long". The charge maths and the release were both already correct; only the feedback was missing.
     * <p>
     * ⚠ SPEAR, not BOW. {@code PlayerRenderer} maps SPEAR to {@code ArmPose.THROW_SPEAR} — the arm cocks back over the
     * shoulder and HOLDS there, which is his "your arm pulls it back and holds until thrown". BOW maps to
     * {@code BOW_AND_ARROW}, which draws toward the face and only reads correctly with a bowstring in hand.
     */
    @Override
    public @NotNull UseAnim getUseAnimation(@NotNull ItemStack stack) {
        return UseAnim.SPEAR;
    }

    /**
     * Clicks once the moment the throw reaches full power.
     * <p>
     * The pose tells you it is charging; this tells you it is READY, which is the half a bow gets for free from its
     * three pull textures. A shuriken has one texture, so the cue is audible instead.
     * <p>
     * ⚠ Fires on the exact tick the charge crosses full, not every tick after it, and only client-side so it is a
     * private cue to the thrower rather than a noise every nearby player hears.
     */
    @Override
    public void onUseTick(
        @NotNull Level level,
        @NotNull LivingEntity entity,
        @NotNull ItemStack stack,
        int remainingUseDuration
    ) {
        if (!level.isClientSide || !(entity instanceof Player player)) {
            return;
        }

        var charge = getUseDuration(stack, entity) - remainingUseDuration;

        if (charge != FULL_CHARGE_TICKS) {
            return;
        }

        // Full charge: his own ready cue, in place of the borrowed crossbow click that used to sit here.
        // ⚠ This point is only reached when the draw ACTUALLY completes — see the charge check above — which is
        // exactly why the cue is its own clip rather than a tail on the charge sound.
        level.playSound(
            null,
            player.blockPosition(),
            PredatorSoundEvents.SHURIKEN_CHARGE_END.get(),
            SoundSource.PLAYERS,
            1.0F,
            WhipCord.variedPitch(level.getRandom(), SOUND_PITCH_SPREAD * 0.5F)
        );
    }

    @Override
    public int getUseDuration(@NotNull ItemStack stack, @NotNull LivingEntity entity) {
        return 72000;
    }

    @Override
    public void releaseUsing(@NotNull ItemStack stack, @NotNull Level level, @NotNull LivingEntity livingEntity, int timeCharged) {
        int i = this.getUseDuration(stack, livingEntity) - timeCharged;
        float powerForTime = getPowerForTime(i);
        if (powerForTime > 0.1 && livingEntity instanceof Player player && !player.getCooldowns().isOnCooldown(this)) {
            player.getCooldowns().addCooldown(this, 5);
            // [stated] "the throwing sound". ⚠ The pitch wanders, as the disc's and whip's do, because a shuriken
            // is thrown in quick succession more than anything else in the mod.
            level.playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                PredatorSoundEvents.SHURIKEN_THROW.get(),
                SoundSource.PLAYERS,
                1.0F,
                WhipCord.variedPitch(level.getRandom(), SOUND_PITCH_SPREAD)
            );

            if (!level.isClientSide) {
                var shurikenItemEntity = new ShurikenProjectile(level, player);
                // ⚠ A single-count copy. setItem(stack) on a stack of 16 hands the projectile all sixteen, so
                // the one it drops on landing would come back as a full stack — a duplication bug that only
                // appears once the item is allowed to stack.
                var thrown = stack.copy();
                thrown.setCount(1);
                shurikenItemEntity.setItem(thrown);
                shurikenItemEntity.setOwner(player);
                shurikenItemEntity.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, powerForTime * 6.5F, 1.0F);
                level.addFreshEntity(shurikenItemEntity);
            }
            player.awardStat(Stats.ITEM_USED.get(this));
            if (!player.getAbilities().instabuild)
                stack.shrink(1);
        }

        super.releaseUsing(stack, level, livingEntity, timeCharged);
    }

    /**
     * Ticks to a full-power throw. ⚠ Comes from {@link #getPowerForTime}, which divides by 20 and clamps at 1, so full
     * charge is exactly one second. Change one and the ready cue drifts out of step with the actual power.
     */
    private static final int FULL_CHARGE_TICKS = 20;

    public static float getPowerForTime(int charge) {
        var normalizedCharge = charge / 20.0F;
        normalizedCharge = (normalizedCharge * normalizedCharge + normalizedCharge * 2.0F) / 3.0F;
        if (normalizedCharge > 1.0F)
            normalizedCharge = 1.0F;
        return normalizedCharge;
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, Player player, @NotNull InteractionHand hand) {
        var itemStack = player.getItemInHand(hand);
        player.startUsingItem(hand);

        // [stated] the charge sound. ⚠ AT THE START: the clip is 1.01 s against a 20-tick (1 s) draw, so it runs the
        // length of the wind-up rather than announcing the end of it.
        level.playSound(
            null,
            player.blockPosition(),
            PredatorSoundEvents.SHURIKEN_CHARGE.get(),
            SoundSource.PLAYERS,
            1.0F,
            WhipCord.variedPitch(level.getRandom(), SOUND_PITCH_SPREAD * 0.5F)
        );
        return InteractionResultHolder.consume(itemStack);
    }
}
