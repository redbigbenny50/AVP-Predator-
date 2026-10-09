package com.predator.common.gameplay.item;

import com.predator.common.registry.init.PredatorTiers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.block.BaseFireBlock;
import org.jetbrains.annotations.NotNull;

/**
 * The plasma sword.
 * <ul>
 * <li>[stated] "the plasma sword deals more damage than the regular sword" — 11 against the veritanium sword's 9, on
 * the same veritanium tier: 1 (hand) + 5 (tier) + 5.</li>
 * <li>[stated] "it also sets things on fire" — every landed hit sets the target alight.</li>
 * <li>[stated] "plasma sword lights blocks by hitting it right click has no function." — hitting a flammable block
 * (wood, leaves, wool — anything lava could ignite) sets fire on the face you struck. See igniteBlock.</li>
 * </ul>
 * ⚠⚠ THE BLOCK HALF CANNOT LIVE ON THE ITEM. I checked the 1.21.1 jar: the server does NOT call Item.canAttackBlock
 * when a player starts hitting a block, so no item hook sees that moment. It is caught where the server DOES see it —
 * ServerPlayerGameMode.handleBlockBreakAction, whose start-hitting action carries the struck FACE, exactly what placing
 * the fire needs (MixinServerPlayerGameMode_PlasmaSword).
 */
public class PlasmaSwordItem extends SwordItem {

    /** 5 on top of the veritanium tier's 5 and the hand's 1: 11. */
    public static final int ATTACK_DAMAGE_BONUS = 5;

    public static final float ATTACK_SPEED = -2.4F;

    public static final float IGNITE_SECONDS = 4.0F;

    public PlasmaSwordItem(Properties properties) {
        super(
            PredatorTiers.VERITANIUM,
            properties.attributes(SwordItem.createAttributes(PredatorTiers.VERITANIUM, ATTACK_DAMAGE_BONUS, ATTACK_SPEED)).fireResistant()
        );
    }

    @Override
    public boolean hurtEnemy(@NotNull ItemStack stack, @NotNull LivingEntity target, @NotNull LivingEntity attacker) {
        target.igniteForSeconds(IGNITE_SECONDS);

        return super.hurtEnemy(stack, target, attacker);
    }

    /**
     * Sets fire on the struck face of a flammable block. ⚠ Guards: the player must be able to interact with that block
     * and position (reach, spawn protection, adventure mode) — this runs before vanilla's own checks on the action.
     */
    public static void igniteBlock(ServerLevel level, ServerPlayer player, BlockPos pos, Direction face) {
        if (player.isSpectator() || !player.canInteractWithBlock(pos, 1.0D) || !level.mayInteract(player, pos)) {
            return;
        }

        if (!level.getBlockState(pos).ignitedByLava()) {
            return;
        }

        var firePos = pos.relative(face);

        if (!BaseFireBlock.canBePlacedAt(level, firePos, face)) {
            return;
        }

        level.setBlock(firePos, BaseFireBlock.getState(level, firePos), 11);
        level.playSound(null, firePos, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.6F, 1.3F);
    }
}
