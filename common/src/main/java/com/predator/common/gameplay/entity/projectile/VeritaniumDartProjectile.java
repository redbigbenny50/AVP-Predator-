package com.predator.common.gameplay.entity.projectile;

import com.predator.common.gameplay.entity.living.yautja.YautjaTier;
import com.predator.common.registry.init.PredatorEntityTypes;
import com.predator.common.registry.init.item.PredatorItems;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * The wrist-bracer dart.
 * <h2>His spec</h2> "fired from the gauntlet… the pred fires them from his wrist bracer… it wont have an attack
 * animation, it kinda shoots it while its swimming… it does have a falloff like the arrow does but its much farther, it
 * can travel 3x as straight before falling off. think of it like a dart coming out of a blow gun."
 * <h2>Why this extends AbstractArrow rather than being written from scratch</h2> ⭐ "Like an arrow but flatter" is
 * almost exactly what {@code AbstractArrow} already is, and it brings the pieces a dart wants for free: velocity-scaled
 * damage, sticking where it lands, being picked back up as an item, and the pickup rules that keep a creative-mode shot
 * from littering. Rebuilding that on {@code Projectile} would be a lot of code to arrive at the same behaviour with
 * more ways to be wrong.
 * <h2>The flat trajectory</h2> ⚠ The range comes from gravity, not from speed. Vanilla's arrow falls at 0.05 blocks per
 * tick squared; a third of that is {@value #DART_GRAVITY}, and since drop grows with the SQUARE of flight time, a third
 * of the pull is roughly three times the flat distance — his "3x as straight before falling off". Raising the launch
 * speed instead would have made it hit harder as well, because {@code AbstractArrow} scales damage by velocity.
 */
public class VeritaniumDartProjectile extends AbstractArrow {

    /** A third of vanilla's arrow gravity (0.05). */
    private static final double DART_GRAVITY = 0.0167;

    /** Base damage before the velocity scaling {@code AbstractArrow} applies. Below an arrow's 2.0 — it is a dart. */
    private static final double BASE_DAMAGE = 1.5;

    public VeritaniumDartProjectile(EntityType<? extends VeritaniumDartProjectile> entityType, Level level) {
        super(entityType, level);
        setBaseDamage(BASE_DAMAGE);
    }

    public VeritaniumDartProjectile(Level level, LivingEntity shooter) {
        super(PredatorEntityTypes.VERITANIUM_DART.get(), shooter, level, new ItemStack(PredatorItems.VERITANIUM_DART.get()), null);

        // ⚠ TECH, not thrown: the wrist launcher supplies the force, so tier here means better kit — see
        // YautjaTier.techDamage. A player's dart is unchanged.
        setBaseDamage(YautjaTier.scaleTech(shooter, (float) BASE_DAMAGE));
    }

    @Override
    protected double getDefaultGravity() {
        return DART_GRAVITY;
    }

    @Override
    protected @NotNull ItemStack getDefaultPickupItem() {
        return new ItemStack(PredatorItems.VERITANIUM_DART.get());
    }

    /** ⚠ Vanilla's arrow hit sound as a placeholder, alongside the placeholder texture. Swap both together. */
    @Override
    protected @NotNull SoundEvent getDefaultHitGroundSoundEvent() {
        return SoundEvents.ARROW_HIT;
    }
}
