package com.predator.common.gameplay.item;

import com.predator.common.gameplay.entity.projectile.VeritaniumArrowEntity;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * [stated] "veritanium arrow ... it makes 4 arrows. These arrows bypass armor defense." An ArrowItem, and in the
 * vanilla minecraft:arrows tag, so every bow — vanilla and veritanium — fires it, and dispensers shoot it.
 */
public class VeritaniumArrowItem extends ArrowItem {

    public VeritaniumArrowItem(Properties properties) {
        super(properties);
    }

    @Override
    public @NotNull AbstractArrow createArrow(
        @NotNull Level level,
        @NotNull ItemStack ammo,
        @NotNull LivingEntity shooter,
        @Nullable ItemStack weapon
    ) {
        return new VeritaniumArrowEntity(level, shooter, ammo.copyWithCount(1), weapon);
    }

    @Override
    public @NotNull Projectile asProjectile(
        @NotNull Level level,
        @NotNull Position position,
        @NotNull ItemStack stack,
        @NotNull Direction direction
    ) {
        var arrow = new VeritaniumArrowEntity(level, position.x(), position.y(), position.z(), stack.copyWithCount(1), null);

        arrow.pickup = AbstractArrow.Pickup.ALLOWED;

        return arrow;
    }
}
