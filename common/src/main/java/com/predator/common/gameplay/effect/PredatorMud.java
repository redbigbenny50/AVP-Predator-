package com.predator.common.gameplay.effect;

import com.predator.common.registry.init.PredatorMobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.function.ToIntFunction;

/**
 * Side-agnostic view of the mud thermal cloak.
 * <h2>Why this class has to exist</h2> {@code entity.hasEffect(...)} is the obvious way to ask, and it is wrong on the
 * client for anything that is not the local player. Verified against 1.21.1 on both loaders:
 * {@code LivingEntity.onEffectAdded}, {@code onEffectUpdated} and {@code onEffectRemoved} only send
 * {@code ClientboundUpdateMobEffectPacket} to the entity's <em>passengers</em>; {@code ServerPlayer} overrides them to
 * also send to itself; and {@code ServerEntity.sendPairingData} sends no effect packets at all. NeoForge does not patch
 * any of it.
 * <p>
 * So an observing client's copy of a muddy cow has an empty {@code activeEffects} map, forever. The thermal-cloak
 * checks in {@code PredatorVisionClassification} and {@code PredatorHeatMaterials} were reading that empty map — which
 * means mud has never actually hidden anything from a predator looking at it. This routes those checks through state
 * the server explicitly broadcasts instead.
 * <p>
 * ⚠ Common code — no client imports. The client view is injected at client init, same as {@code PredatorCloak}.
 */
public final class PredatorMud {

    /** How often the server re-broadcasts remaining duration. Clients interpolate between refreshes. */
    public static final int SYNC_INTERVAL_TICKS = 20;

    /**
     * Duration the overlay treats as "freshly caked" when it has nothing better to compare against. Mud applied for
     * longer than this simply sits at full thickness until it drops below the mark.
     */
    public static final int REFERENCE_DURATION_TICKS = 1200;

    private static volatile ToIntFunction<LivingEntity> clientView = entity -> 0;

    private PredatorMud() {
        throw new UnsupportedOperationException();
    }

    public static void setClientView(ToIntFunction<LivingEntity> view) {
        clientView = view;
    }

    /** {@return remaining mud duration in ticks, or 0 when the entity is not muddy} */
    public static int remainingTicks(@Nullable LivingEntity entity) {
        if (entity == null) {
            return 0;
        }

        if (entity.level().isClientSide) {
            return clientView.applyAsInt(entity);
        }

        var instance = entity.getEffect(PredatorMobEffects.getMudHolder());
        return instance == null ? 0 : instance.getDuration();
    }

    /** {@return {@code true} when this entity is caked in mud and therefore invisible to thermal} */
    public static boolean isMuddy(@Nullable LivingEntity entity) {
        return remainingTicks(entity) > 0;
    }

    /**
     * {@return how thickly the mud reads, 0..1} — full while there is plenty of time left, thinning as it dries and
     * flakes off. Drives the screen overlay's alpha so the wearer can see their own cover wearing out.
     */
    public static float thickness(@Nullable LivingEntity entity, int referenceDuration) {
        var remaining = remainingTicks(entity);

        if (remaining <= 0) {
            return 0.0F;
        }

        var reference = Math.max(1, referenceDuration);
        return Math.min(1.0F, (float) remaining / (float) reference);
    }
}
