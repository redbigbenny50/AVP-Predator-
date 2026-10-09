package com.predator.common.gameplay.cloak;

import com.predator.common.registry.tag.PredatorEntityTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * The predator cloaking device — shared, side-agnostic entry point for "is this entity's cloak field up?".
 * <p>
 * ⚠ This class lives in common and MUST NOT reference any client class. The client view is injected as a plain
 * {@link Predicate} at client init instead (see {@code PredatorCloakClientState}). Referencing a client class from
 * common is the trap that makes {@code runDatagen} report BUILD SUCCESSFUL while generating nothing.
 * <h2>The two states</h2>
 * <ul>
 * <li>{@link #isCloaked} — the device is engaged and the wearer is not inside a melee-reveal window. This is the bit
 * the server owns and broadcasts to every tracking client, because a cloak has to be seen (or not seen) by
 * <em>everyone</em>, not just the wearer.</li>
 * <li>{@link #isConcealed} — {@code isCloaked} AND not in water/rain. Water shorts the field out: the wearer becomes
 * fully visible and gains the charged-creeper arcing overlay. Water state is derivable on both sides from the entity
 * itself, so it is deliberately NOT part of the synced bit.</li>
 * </ul>
 * <h2>Why not a MobEffect</h2> Vanilla only sends {@code ClientboundUpdateMobEffectPacket} to an entity's passengers
 * (and to a {@code ServerPlayer} about itself). Nothing broadcasts effects to tracking clients, so
 * {@code entity.hasEffect(...)} is always false on an observer's client for anything they are not riding. An effect
 * could never carry cloak state.
 * <h2>Why not vanilla INVISIBILITY</h2> Three reasons, all verified against 1.21.1: {@code LivingEntityRenderer} only
 * nulls the <em>body</em> render type, so armour and item layers still draw; {@code getVisibilityPercent} multiplies by
 * armour coverage, so a fully-armoured wearer is still spotted at 70% of normal range; and the nulled render type
 * removes the very geometry the shimmer needs to draw onto.
 */
public final class PredatorCloak {

    /** Cooldown after the field is forced down by damage, or by the wearer switching it off. 60 seconds. */
    public static final int COOLDOWN_TICKS = 1200;

    /** Total damage absorbed while cloaked before the field collapses. 8.0 = four hearts. */
    public static final float DAMAGE_BREAK_THRESHOLD = 8.0F;

    /** How long the field stays down after the wearer lands a melee hit. 2 seconds, then it re-engages for free. */
    public static final int MELEE_REVEAL_TICKS = 40;

    /**
     * How long the field survives continuous water or rain before it gives out and goes into overload.
     * <p>
     * Short exposure is free on purpose — dashing across a stream or taking an accidental plunge shorts the field out
     * visibly and then it recovers, no cooldown. Standing in it is what kills the device. 10 seconds.
     */
    public static final int WATER_TOLERANCE_TICKS = 200;

    /**
     * How long the wearer must be CONTINUOUSLY dry before the soak counter resets. 1.5 seconds.
     * <p>
     * Without this, jumping in and out of water cleared the counter on every airborne frame, so bunny-hopping through a
     * river kept the cloak alive indefinitely and bypassed the water limit entirely.
     */
    public static final int WATER_GRACE_TICKS = 30;

    /** How often the "Cloak overloaded" action-bar line is refreshed while the cooldown runs. */
    public static final int OVERLOAD_MESSAGE_INTERVAL_TICKS = 20;

    private static volatile Predicate<LivingEntity> clientView = entity -> false;

    private PredatorCloak() {
        throw new UnsupportedOperationException();
    }

    /** Installed once at client init so common code can ask the client cache without importing it. */
    public static void setClientView(Predicate<LivingEntity> view) {
        clientView = view;
    }

    /**
     * @return {@code true} when the entity's cloak field is engaged. Does NOT account for water — callers that care
     *         about what is actually rendered or targetable want {@link #isConcealed}.
     */
    public static boolean isCloaked(@Nullable LivingEntity entity) {
        if (entity == null) {
            return false;
        }

        return entity.level().isClientSide
            ? clientView.test(entity)
            : PredatorCloakManager.isCloaked(entity);
    }

    /** {@return {@code true} when the entity is cloaked AND the field is not being shorted out by water or rain} */
    public static boolean isConcealed(@Nullable LivingEntity entity) {
        return isCloaked(entity) && !entity.isInWaterOrRain();
    }

    /**
     * {@return {@code true} when the cloak is up but water or rain is arcing across it} — the charged-creeper overlay
     * condition. The wearer renders normally underneath; the arcing is what gives them away.
     */
    public static boolean isShortingOut(@Nullable LivingEntity entity) {
        return isCloaked(entity) && entity.isInWaterOrRain();
    }

    /**
     * {@return {@code true} when {@code observer} is one of the things the cloak simply does not work on}
     * <p>
     * Driven by the {@code avp_predator:cloak_immune} entity-type tag rather than hardcoded, so a datapack can add a
     * modded hunter without a rebuild. Default population is every xenomorph (they have no eyes — the cloak is not even
     * a factor for them) plus the warden (hunts purely by vibration).
     */
    public static boolean isImmune(@Nullable Entity observer) {
        return observer != null && observer.getType().is(PredatorEntityTypeTags.CLOAK_IMMUNE);
    }
}
