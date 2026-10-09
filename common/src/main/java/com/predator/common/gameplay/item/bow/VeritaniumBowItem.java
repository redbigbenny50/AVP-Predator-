package com.predator.common.gameplay.item.bow;

import com.predator.common.gameplay.entity.living.yautja.YautjaTier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * A veritanium bow: an ordinary bow that hits harder.
 * <p>
 * [stated] "veritanium bow is like a normal bow uses arrows but has a 1.3x multiplier for arrow damage."
 * <h2>Why createProjectile and not releaseUsing</h2> Overriding the release would mean re-implementing draw power,
 * crits, Infinity and arrow pickup. {@code createProjectile} is the one seam where the arrow exists and nothing has
 * been decided about it yet, so the multiplier lands on top of everything vanilla already worked out — Power included.
 */
public class VeritaniumBowItem extends BowItem {

    /** [stated] 1.3x arrow damage. */
    public static double DAMAGE_MULTIPLIER = 1.3D;

    public VeritaniumBowItem() {
        super(new Properties().stacksTo(1).durability(Tiers.DIAMOND.getUses()));
    }

    @Override
    protected @NotNull Projectile createProjectile(
        @NotNull Level level,
        @NotNull LivingEntity shooter,
        @NotNull ItemStack weapon,
        @NotNull ItemStack ammo,
        boolean isCrit
    ) {
        var projectile = super.createProjectile(level, shooter, weapon, ammo, isCrit);

        if (projectile instanceof AbstractArrow arrow) {
            // ⚠ MULTIPLIED, NOT SET — Power and the draw-strength scaling vanilla already applied are preserved.
            // A yautja's muscle tier then scales it again, as it does for every hand-powered weapon.
            arrow.setBaseDamage(YautjaTier.scaleThrown(shooter, (float) (arrow.getBaseDamage() * DAMAGE_MULTIPLIER)));
        }

        return projectile;
    }
}
