package com.predator.common.gameplay.freeze;

import com.blib.api.BLibAPI;
import com.predator.Predator;
import com.predator.common.registry.init.PredatorMobEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * Freezing SOLID from avp_predator's side. The rule is avp_human's ({@code FrozenSolid}): [stated] "anything that drops
 * to 10% health, or any passive mob, becomes frozen with the cryochamber frost effect layer; frozen mobs stay frozen 5
 * minutes unless lit on fire", owned there and shared by all three mods, the same arrangement as radiation.
 * <ul>
 * <li><b>avp_human installed</b> — its entry point is called by name, so the mob gets avp_human's effect, its AI stop,
 * its immunities and its frost overlay. No compile dependency on avp_human.</li>
 * <li><b>avp_human absent</b> — a stand-in: this mod's own Frozen Solid effect with the same rule (players never;
 * vanilla's freeze-immune mobs, the Ender Dragon and the Wither never), the AI stop, and frost particles in place of
 * the overlay, which is avp_human's art.</li>
 * </ul>
 */
public final class FreezeBridge {

    private static final String OWNER = "com.human.common.gameplay.freeze.FrozenSolid";

    private static final int FROZEN_TICKS = 6000;

    private static final float HEALTH_FRACTION = 0.10F;

    private static @Nullable Method owner;

    private static boolean ownerLooked;

    private FreezeBridge() {}

    /** Freezes the mob solid if the shared rule allows it. {@return whether it is frozen solid} */
    public static boolean tryFreezeSolid(LivingEntity target) {
        if (target.level().isClientSide || !target.isAlive()) {
            return false;
        }

        var method = owner();

        if (method != null) {
            try {
                return (boolean) method.invoke(null, target);
            } catch (ReflectiveOperationException | RuntimeException exception) {
                Predator.LOGGER.warn("avp_human's freeze entry point failed; using the stand-in.", exception);
                owner = null;
            }
        }

        return standIn(target);
    }

    private static @Nullable Method owner() {
        if (!ownerLooked) {
            ownerLooked = true;

            if (BLibAPI.isModLoaded("avp_human")) {
                try {
                    owner = Class.forName(OWNER).getMethod("tryFreeze", LivingEntity.class);
                } catch (ReflectiveOperationException exception) {
                    Predator.LOGGER.warn("avp_human is installed but has no freeze entry point; using the stand-in.");
                }
            }
        }

        return owner;
    }

    private static boolean standIn(LivingEntity target) {
        var holder = PredatorMobEffects.getFrozenSolidHolder();

        if (target.hasEffect(holder)) {
            return true;
        }

        if (
            target instanceof Player || target.getType().is(EntityTypeTags.FREEZE_IMMUNE_ENTITY_TYPES)
                || target.getType() == EntityType.ENDER_DRAGON || target.getType() == EntityType.WITHER
        ) {
            return false;
        }

        if (isHostile(target) && target.getHealth() > target.getMaxHealth() * HEALTH_FRACTION) {
            return false;
        }

        target.addEffect(new MobEffectInstance(holder, FROZEN_TICKS, 0, false, false, true));

        if (target.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getY(0.5), target.getZ(), 30, 0.4, 0.6, 0.4, 0.02);
        }

        return true;
    }

    /**
     * Vanilla hostiles, and avp_human's marine ([stated] "marines are hostile and should follow the 10%") — matched by
     * id, as there is no compile dependency on avp_human. Exactly {@code avp_human:marine}, so the marine dog is not
     * caught by a name match.
     */
    private static boolean isHostile(LivingEntity target) {
        if (target instanceof Enemy) {
            return true;
        }

        var key = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());

        return "avp_human".equals(key.getNamespace()) && "marine".equals(key.getPath());
    }

    /** {@return whether this mob is frozen solid by the stand-in} — for the stand-in's AI stop. */
    public static boolean isStandInFrozen(LivingEntity target) {
        return target.hasEffect(PredatorMobEffects.getFrozenSolidHolder());
    }
}
