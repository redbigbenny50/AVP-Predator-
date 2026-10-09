package com.predator.common.gameplay.item.bow;

import com.predator.common.gameplay.entity.projectile.PlasmaBoltArrowProjectile;
import com.predator.common.registry.init.PredatorDataComponents;
import com.predator.common.registry.init.PredatorSoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

/**
 * A plasma bow: draws like a bow, needs no arrows, fires a bolt that bursts.
 * <p>
 * [stated] "plasma bow doesnt use arrows it shoots red bolts ... it doesnt lodge the bolts into objects they burst
 * against them and enemies in red particles", and "unlimited ammo for players too yes its like built in infiniti".
 * <h2>⚠ IT STILL EXTENDS BowItem</h2> Not for the ammo — for everything else: the draw timing, the use animation, the
 * pull-stage model predicates and {@code getPowerForTime}. His three pull sprites say it charges rather than firing
 * instantly, and all of that comes free from the parent.
 * <h2>⚠⚠ BUT use() AND releaseUsing() ARE OURS</h2> Vanilla's both consult {@code Player.getProjectile}, which is empty
 * with no arrows in the bag — so inheriting them would mean a bow that refuses to draw and refuses to fire, the exact
 * opposite of built-in Infinity. They are reimplemented here WITHOUT the ammo checks and without ever consuming a
 * stack.
 */
public class PlasmaBowItem extends BowItem {

    /** Ticks before it can be drawn again. Unlimited ammo needs SOME limit or it is a machine gun. */
    public static int COOLDOWN_TICKS = 12;

    /** ⚠ Tougher than the veritanium bow: it costs no arrows, so durability is its only real limit. */
    private static final int DURABILITY = 768;

    /** ⚠ Stops a bow in the hotbar announcing itself the moment a player logs in. */
    private static final int EQUIP_SOUND_GRACE_TICKS = 10;

    /**
     * ⚠⚠ ON THE CHANGE, NOT EVERY TICK — the same shape the whip, disc and shuriken use, for the same reason:
     * inventoryTick runs twenty times a second for every stack a player carries.
     */
    @Override
    public void inventoryTick(@NotNull ItemStack stack, @NotNull Level level, @NotNull Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, level, entity, slot, selected);

        if (level.isClientSide || !(entity instanceof Player player)) {
            return;
        }

        var wasSelected = stack.getOrDefault(PredatorDataComponents.PLASMA_BOW_WAS_SELECTED.get(), false);

        if (selected == wasSelected) {
            return;
        }

        stack.set(PredatorDataComponents.PLASMA_BOW_WAS_SELECTED.get(), selected);

        if (selected && player.tickCount > EQUIP_SOUND_GRACE_TICKS) {
            level.playSound(
                null,
                player.blockPosition(),
                PredatorSoundEvents.PLASMA_BOW_EQUIP.get(),
                SoundSource.PLAYERS,
                1.0F,
                1.0F
            );
        }
    }

    /** Ticks to a full draw. ⚠ The hold loop begins here, once the draw clip has finished. */
    private static final int DRAW_TICKS = 18;

    /** Below this draw fraction the shot is refused, as vanilla does. */
    private static final float MINIMUM_POWER = 0.1F;

    public PlasmaBowItem() {
        super(new Properties().stacksTo(1).durability(DURABILITY));
    }

    /** ⚠ Never consulted for ammo (see the class note); this only keeps the parent's contract honest. */
    @Override
    public @NotNull Predicate<ItemStack> getAllSupportedProjectiles() {
        return stack -> stack.is(Items.ARROW);
    }

    /** Always draws — there is no ammo to check. */
    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        var stack = player.getItemInHand(hand);

        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }

        player.startUsingItem(hand);

        // [stated] "draw plays when drawing the the bow back". One shot, at the start.
        level.playSound(null, player.blockPosition(), PredatorSoundEvents.PLASMA_BOW_DRAW.get(), SoundSource.PLAYERS, 1.0F, 1.0F);

        return InteractionResultHolder.consume(stack);
    }

    /**
     * Starts the hold loop once the draw is complete.
     * <p>
     * ⚠⚠ AFTER THE DRAW CLIP, NOT AT THE START. [stated] "hold plays on loop while its drawn back being held" — the
     * draw sound is 0.90 s and a full draw is 20 ticks, so beginning the loop at DRAW_TICKS lets one finish before the
     * other begins instead of stacking two plasma sounds on top of each other.
     * <p>
     * ⚠ CLIENT ONLY. The loop is a client sound instance because it must be able to STOP; see PlasmaBowHoldSound.
     */
    @Override
    public void onUseTick(@NotNull Level level, @NotNull LivingEntity entity, @NotNull ItemStack stack, int remaining) {
        super.onUseTick(level, entity, stack, remaining);

        if (!level.isClientSide || !(entity instanceof Player player)) {
            return;
        }

        if (getUseDuration(stack, entity) - remaining == DRAW_TICKS) {
            com.predator.client.sound.PlasmaBowClientSounds.startHold(player);
        }
    }

    @Override
    public void releaseUsing(@NotNull ItemStack stack, @NotNull Level level, @NotNull LivingEntity shooter, int timeLeft) {
        if (!(shooter instanceof Player player)) {
            return;
        }

        var power = getPowerForTime(getUseDuration(stack, shooter) - timeLeft);

        if (power < MINIMUM_POWER) {
            return;
        }

        if (!level.isClientSide) {
            var bolt = new PlasmaBoltArrowProjectile(level, shooter);

            bolt.shootFromRotation(
                shooter,
                shooter.getXRot(),
                shooter.getYRot(),
                0.0F,
                // ⚠⚠ CONSTANT SPEED. [stated] "if its not fully charged it moves slow. Lets make the bolt travel
                // the same speed regardless of charge but the damage changes." A plasma bolt is FIRED, not thrown:
                // its velocity comes from the weapon, so a weak charge means a weaker bolt, not a slower one.
                PlasmaBoltArrowProjectile.LAUNCH_SPEED,
                1.0F
            );
            bolt.setChargeDamage(power);
            bolt.setCritArrow(power >= 1.0F);
            level.addFreshEntity(bolt);

            stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        }

        level.playSound(
            null,
            player.getX(),
            player.getY(),
            player.getZ(),
            PredatorSoundEvents.PLASMA_BOW_FIRE.get(),
            SoundSource.PLAYERS,
            1.0F,
            // ⚠ Still rises slightly with draw power, as vanilla's bow does — a full charge should sound like one.
            0.92F + power * 0.16F
        );

        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        player.awardStat(Stats.ITEM_USED.get(this));
    }
}
