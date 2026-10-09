package com.predator.common.gameplay.item.grenade;

import com.predator.common.gameplay.entity.projectile.YautjaGrenadeProjectile;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * A yautja grenade. Thrown on use like a snowball, a little harder, with a one-second cooldown so they cannot be
 * sprayed.
 */
public class YautjaGrenadeItem extends Item {

    /** Throw speed; a snowball is 1.5. */
    public static float THROW_SPEED = 1.3F;

    public static int COOLDOWN_TICKS = 20;

    private final GrenadeKind kind;

    public YautjaGrenadeItem(GrenadeKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public GrenadeKind kind() {
        return kind;
    }

    /** {@return the grenade item of this kind} */
    public static Item forKind(GrenadeKind kind) {
        return switch (kind) {
            case FIRE -> com.predator.common.registry.init.item.PredatorItems.PRED_GRENADE_FIRE.get();
            case STICKY -> com.predator.common.registry.init.item.PredatorItems.PRED_GRENADE_STICKY.get();
            case FREEZE -> com.predator.common.registry.init.item.PredatorItems.PRED_GRENADE_FREEZE.get();
            case IRRADIATED -> com.predator.common.registry.init.item.PredatorItems.PRED_GRENADE_IRRADIATED.get();
            default -> com.predator.common.registry.init.item.PredatorItems.PRED_GRENADE_EXPLOSIVE.get();
        };
    }

    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        var stack = player.getItemInHand(hand);

        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.6F, 0.6F);

        if (!level.isClientSide) {
            var grenade = new YautjaGrenadeProjectile(level, player, kind);

            grenade.setItem(stack.copyWithCount(1));
            grenade.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, THROW_SPEED, 1.0F);
            level.addFreshEntity(grenade);
        }

        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        stack.consume(1, player);

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
