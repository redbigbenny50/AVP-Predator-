package com.predator.common.gameplay.entity.projectile;

import com.predator.common.gameplay.entity.living.yautja.YautjaTier;
import com.predator.common.registry.init.PredatorEntityTypes;
import com.predator.common.registry.init.item.PredatorItems;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import org.jetbrains.annotations.NotNull;

/**
 * The thrown combi stick.
 * <h2>⚠⚠ IT ALWAYS COMES BACK</h2> ⚠⚠ IT NO LONGER DROPS WITH LOYALTY — his members did not want that, so the drop is
 * now PLAIN and a player adds the enchantment themselves if they want it back.
 * <p>
 * The return was never hardcoded here and still is not: this is an {@code AbstractArrow}, so vanilla reads Loyalty off
 * the thrown stack exactly as it does for a trident. Removing the free enchantment changed WHAT DROPS, not what the
 * projectile can do — an enchanted stick returns, an unenchanted one sticks where it lands.
 * <h2>Thrown by a yautja, it costs nothing</h2> ⚠ The yautja builds a FRESH stack for the projectile and never touches
 * its own hand — exactly what vanilla's Drowned does with its trident ({@code new ItemStack(Items.TRIDENT)}, hand slot
 * untouched). That is why a yautja can throw repeatedly and still visibly be holding its spear.
 */
public class CombiStickProjectile extends AbstractArrow {

    /**
     * {@return whether the spear is embedded in a block}
     * <p>
     * ⚠ {@code inGround} is protected on AbstractArrow, so the RENDERER cannot read it — hence this accessor. It is
     * used to pull the drawn model back out of the ground; see CombiStickRenderer.
     */
    public boolean isStuckInGround() {
        return inGround;
    }

    /** ⚠ Above a trident's 8: this is a two-metre bladed shaft and the drop is meant to be worth hunting for. */
    private static final float DAMAGE = 10.0F;

    public CombiStickProjectile(EntityType<? extends AbstractArrow> entityType, Level level) {
        super(entityType, level);
    }

    public CombiStickProjectile(Level level, LivingEntity thrower, ItemStack stack) {
        super(PredatorEntityTypes.COMBI_STICK.get(), thrower, level, stack, null);

        // ⚠ Scaled by the THROWER'S TIER — see YautjaTier.scaleThrown. A player's throw is unchanged.
        setBaseDamage(YautjaTier.scaleThrown(thrower, DAMAGE));
    }

    /**
     * ⚠ The stack it becomes when picked up, which is the stack it was THROWN with — enchantments and all. Returning a
     * plain new item here would strip the Loyalty off the spear the first time anyone caught it.
     */
    @Override
    protected @NotNull ItemStack getDefaultPickupItem() {
        return new ItemStack(PredatorItems.COMBI_STICK.get());
    }

    @Override
    protected @NotNull SoundEvent getDefaultHitGroundSoundEvent() {
        return SoundEvents.TRIDENT_HIT_GROUND;
    }

    /**
     * ⚠ A yautja's thrown spear must not be catchable, or a player would farm them by standing still. Only a spear a
     * PLAYER threw can be picked back up, which is what the pickup rule set at construction already encodes — this only
     * guards the case where the owner is gone.
     */
    @Override
    protected boolean tryPickup(@NotNull Player player) {
        // ⚠⚠ A CREATIVE THROWER NEVER LOSES THE STACK, SO PICKING IT UP DUPLICATES IT. The throw only shrinks the
        // held stack when the player is NOT in creative — so in creative you keep the spear AND collect the thrown
        // one, and every throw-and-retrieve cycle mints a new item. Collect it silently instead.
        if (player.getAbilities().instabuild) {
            discard();

            return false;
        }

        return getOwner() == player && super.tryPickup(player);
    }

    @Override
    protected void onHitEntity(@NotNull EntityHitResult result) {
        // [stated] "the throw would be just like a trident" — and a trident hits FLAT, not by speed. This hit for ~22
        // at
        // Youngblood and ~44 at Clan Leader, and a player's full-power throw for ~30. It is now exactly DAMAGE, scaled
        // by the thrower's tier for a yautja. See hitFlat.
        hitFlat(result);

        if (!level().isClientSide) {
            playSound(SoundEvents.TRIDENT_HIT, 1.0F, 1.0F);
        }
    }

    /**
     * Makes vanilla's arrow hit deal exactly {@code getBaseDamage()}, flat.
     * <p>
     * 🚨🚨 VANILLA MULTIPLIES AN ARROW'S DAMAGE BY ITS SPEED — AbstractArrow.onHitEntity deals ceil(speed x base). So a
     * "base" of 6 flying at 3.2 hit for ~20, and every number set on this projectile was silently ~2-3x what was meant.
     * Setting the base to (intended / current speed) for the duration of the hit makes that product come out to exactly
     * the intended damage, whatever the projectile has slowed to — then the real base is put back.
     * <p>
     * ⚠ The random crit-arrow bonus vanilla adds on top (up to half the damage again, plus two) is suppressed for the
     * hit too, so the number is the number.
     */
    private void hitFlat(EntityHitResult result) {
        var intended = getBaseDamage();
        var speed = getDeltaMovement().length();
        var crit = isCritArrow();

        setBaseDamage(speed > 1.0E-3D ? intended / speed : intended);
        setCritArrow(false);

        try {
            super.onHitEntity(result);
        } finally {
            setBaseDamage(intended);
            setCritArrow(crit);
        }
    }
}
