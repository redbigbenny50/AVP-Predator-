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

    /** avp_human's at-any-health freeze, for the freeze grenade. */
    private static @Nullable Method forceOwner;

    private static boolean forceOwnerLooked;

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

        return standIn(target, false);
    }

    /**
     * [stated] Oct 19 - the freeze grenade freezes anything solid at ANY health. avp_human's {@code forceFreeze} by
     * name (its freeze sound included); an older avp_human without it gets the ordinary freeze, and without avp_human
     * the stand-in does the same at any health. Players and cold-immune mobs never. {@return whether it is frozen}
     */
    public static boolean forceFreezeSolid(LivingEntity target) {
        if (target.level().isClientSide || !target.isAlive()) {
            return false;
        }

        if (!forceOwnerLooked) {
            forceOwnerLooked = true;

            if (BLibAPI.isModLoaded("avp_human")) {
                try {
                    forceOwner = Class.forName(OWNER).getMethod("forceFreeze", LivingEntity.class);
                } catch (ReflectiveOperationException exception) {
                    Predator.LOGGER.warn("avp_human has no at-any-health freeze; the freeze grenade uses the ordinary one.");
                }
            }
        }

        if (forceOwner != null) {
            try {
                return (boolean) forceOwner.invoke(null, target);
            } catch (ReflectiveOperationException | RuntimeException exception) {
                Predator.LOGGER.warn("avp_human's at-any-health freeze failed; using the ordinary one.", exception);
                forceOwner = null;
            }
        }

        if (owner() != null) {
            return tryFreezeSolid(target);
        }

        return standIn(target, true);
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

    private static boolean standIn(LivingEntity target, boolean anyHealth) {
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

        if (!anyHealth && isHostile(target) && target.getHealth() > target.getMaxHealth() * HEALTH_FRACTION) {
            return false;
        }

        target.addEffect(new MobEffectInstance(holder, FROZEN_TICKS, 0, false, false, true));

        if (target.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getY(0.5), target.getZ(), 30, 0.4, 0.6, 0.4, 0.02);
            playStandInSound(target, com.predator.common.registry.init.PredatorSoundEvents.STAND_IN_FREEZE_SOLID.get());
        }

        return true;
    }

    /**
     * avp_human's {@code soldiers} tag: the marine and every soldier type split out of it (Oct 13 - the tactical marine
     * and the Weyland-Yutani units). A tag, so no compile dependency and future soldier types need no change here.
     */
    private static final net.minecraft.tags.TagKey<net.minecraft.world.entity.EntityType<?>> AVP_HUMAN_SOLDIERS =
        net.minecraft.tags.TagKey.create(
            net.minecraft.core.registries.Registries.ENTITY_TYPE,
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("avp_human", "soldiers")
        );

    /**
     * Vanilla hostiles, and avp_human's soldiers ([stated] "marines are hostile and should follow the 10%") - matched
     * without a compile dependency on avp_human. Every soldier type is a marine underneath, so all of them follow it;
     * the marine dog is not a soldier and is not caught. The plain {@code avp_human:marine} id stays as a fallback for
     * an avp_human from before the soldiers tag existed.
     */
    private static boolean isHostile(LivingEntity target) {
        if (target instanceof Enemy) {
            return true;
        }

        if (target.getType().is(AVP_HUMAN_SOLDIERS)) {
            return true;
        }

        var key = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(target.getType());

        return "avp_human".equals(key.getNamespace()) && "marine".equals(key.getPath());
    }

    /**
     * [stated] Oct 19 - as avp_human: a real attack has a 70% chance to break a frozen mob out; a heavy fall always
     * does.
     */
    private static final float BREAK_ATTACK_DAMAGE = 2.0F;

    private static final float BREAK_FALL_DAMAGE = 4.0F;

    private static final float BREAK_ATTACK_CHANCE = 0.70F;

    /**
     * The stand-in's breakout, after a mob has actually taken damage (MixinLivingEntity_StandInIceBreak). Only when
     * avp_human is NOT installed - with it, avp_human's own rule handles its own frozen mobs. Same rule as avp_human's:
     * a hit from a mob or player, or an explosion, of 2 or more - a 70% chance; a fall of 4 or more - always; never
     * cold, never damage with no attacker. Thaws the mob and plays the ice-break sound.
     */
    public static void onHurtStandIn(LivingEntity target, net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (target.level().isClientSide || !target.isAlive() || !isStandInFrozen(target) || BLibAPI.isModLoaded("avp_human")) {
            return;
        }

        if (source.is(net.minecraft.tags.DamageTypeTags.IS_FREEZING)) {
            return;
        }

        if (source.is(net.minecraft.tags.DamageTypeTags.IS_FALL)) {
            if (amount < BREAK_FALL_DAMAGE) {
                return;
            }
        } else {
            var isAttack = source.getEntity() != null
                || source.getDirectEntity() != null
                || source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION);

            if (!isAttack || amount < BREAK_ATTACK_DAMAGE || target.getRandom().nextFloat() >= BREAK_ATTACK_CHANCE) {
                return;
            }
        }

        target.removeEffect(PredatorMobEffects.getFrozenSolidHolder());
        target.setTicksFrozen(0);
        playStandInSound(target, com.predator.common.registry.init.PredatorSoundEvents.STAND_IN_FROZEN_BREAK.get());
    }

    private static void playStandInSound(LivingEntity target, net.minecraft.sounds.SoundEvent sound) {
        target.level()
            .playSound(
                null,
                target.getX(),
                target.getY(),
                target.getZ(),
                sound,
                target.getSoundSource(),
                1.0F,
                0.9F + target.getRandom().nextFloat() * 0.2F
            );
    }

    /** {@return whether this mob is frozen solid by the stand-in} — for the stand-in's AI stop. */
    public static boolean isStandInFrozen(LivingEntity target) {
        return target.hasEffect(PredatorMobEffects.getFrozenSolidHolder());
    }
}
