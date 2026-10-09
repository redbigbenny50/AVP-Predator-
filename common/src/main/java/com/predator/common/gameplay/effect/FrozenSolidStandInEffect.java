package com.predator.common.gameplay.effect;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

/**
 * The stand-in for avp_human's Frozen Solid, used only when avp_human is not installed (see FreezeBridge). No sideways
 * motion unless a lead or a capture chain is pulling it, fire thaws it, and frost drifts off it in place of the
 * overlay.
 */
public class FrozenSolidStandInEffect extends MobEffect {

    public FrozenSolidStandInEffect() {
        super(MobEffectCategory.HARMFUL, 0x9FE6FF);
    }

    /** avp_alien's capture chain, looked up once by name — there is no compile dependency on avp_alien. */
    private static java.lang.reflect.Method chainHeld;

    private static java.lang.reflect.Method chainLinked;

    private static boolean chainLookedUp;

    /** {@return whether a lead or avp_alien's capture chain is pulling this mob} */
    private static boolean isTethered(LivingEntity entity) {
        if (!(entity instanceof net.minecraft.world.entity.Mob mob)) {
            return false;
        }

        if (mob.isLeashed()) {
            return true;
        }

        if (!chainLookedUp) {
            chainLookedUp = true;

            try {
                chainHeld = Class.forName("com.alien.common.gameplay.capture.CaptureHoldManager")
                    .getMethod("isHeld", net.minecraft.world.entity.Mob.class);
                chainLinked = Class.forName("com.alien.common.gameplay.capture.MobChainManager")
                    .getMethod("isLinked", net.minecraft.world.entity.Mob.class);
            } catch (ReflectiveOperationException | LinkageError absent) {
                chainHeld = null;
                chainLinked = null;
            }
        }

        try {
            return (chainHeld != null && (boolean) chainHeld.invoke(null, mob))
                || (chainLinked != null && (boolean) chainLinked.invoke(null, mob));
        } catch (ReflectiveOperationException failed) {
            return false;
        }
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(@NotNull LivingEntity entity, int amplifier) {
        if (entity.isOnFire()) {
            return false;
        }

        // ⚠ Pinned only when nothing is pulling it — the same rule as avp_human's own effect, so a lead or avp_alien's
        // capture chain can drag a frozen mob ([stated] Oct 4: "another way of capturing them and transporting").
        // Leading a frozen HOSTILE needs avp_human, which owns that rule; without it vanilla's lead rules stand.
        if (!isTethered(entity)) {
            var motion = entity.getDeltaMovement();
            entity.setDeltaMovement(0.0, Math.min(0.0, motion.y), 0.0);
        }

        if (entity.tickCount % 10 == 0 && entity.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SNOWFLAKE, entity.getX(), entity.getY(0.6), entity.getZ(), 3, 0.3, 0.5, 0.3, 0.0);
        }

        return true;
    }
}
