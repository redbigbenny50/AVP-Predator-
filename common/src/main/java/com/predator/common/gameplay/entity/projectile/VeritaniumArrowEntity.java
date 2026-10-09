package com.predator.common.gameplay.entity.projectile;

import com.predator.common.registry.init.PredatorEntityTypes;
import com.predator.common.registry.init.item.PredatorItems;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The veritanium arrow in flight. [stated] "These arrows bypass armor defense."
 * <p>
 * ⚠ The armour bypass is NOT here: vanilla builds the arrow's damage source inside AbstractArrow.onHitEntity, out of
 * reach. It lives on the VICTIM side instead — MixinLivingEntity_VeritaniumArrowArmor skips the armour step for any
 * damage whose direct entity is one of these, and Yautja does the same in its own tier-armour override. Everything else
 * is a normal arrow: speed-scaled damage, pickup, crits, Power and Punch.
 */
public class VeritaniumArrowEntity extends AbstractArrow {

    public VeritaniumArrowEntity(EntityType<? extends VeritaniumArrowEntity> type, Level level) {
        super(type, level);
    }

    public VeritaniumArrowEntity(Level level, LivingEntity owner, ItemStack pickup, @Nullable ItemStack weapon) {
        super(PredatorEntityTypes.VERITANIUM_ARROW.get(), owner, level, pickup, weapon);
    }

    public VeritaniumArrowEntity(Level level, double x, double y, double z, ItemStack pickup, @Nullable ItemStack weapon) {
        super(PredatorEntityTypes.VERITANIUM_ARROW.get(), x, y, z, level, pickup, weapon);
    }

    @Override
    protected @NotNull ItemStack getDefaultPickupItem() {
        return new ItemStack(PredatorItems.VERITANIUM_ARROW.get());
    }
}
