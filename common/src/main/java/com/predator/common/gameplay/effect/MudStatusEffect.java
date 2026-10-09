package com.predator.common.gameplay.effect;

import com.blib.api.common.color.v1.Color;
import com.predator.Predator;
import com.predator.common.network.packet.S2CMudStatePayload;
import com.predator.common.registry.init.PredatorMobEffects;
import com.predator.mixin.MobEffectInstanceDurationAccessor;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * Mud status effect — a thermal-vision cloak. Caked-on mud insulates the wearer's heat signature so a predator's
 * thermal vision reads them as world-cold instead of as a foreground entity (game-world reference: Dutch in the 1987
 * film). The effect is consumed by {@link com.predator.client.vision.PredatorVisionClassification}, which downgrades a
 * THERMAL-visible classification to BACKGROUND while the effect is active. Other vision modes are unaffected, so an
 * em-tagged mob covered in mud still shows up under EM.
 * <p>
 * Particles are suppressed entirely by tagging this effect into {@code BLibMobEffectTags#NO_PARTICLES} (the mud should
 * be silent, not announced by an obvious purple swirl). Milk does NOT cure mud — handled the same way, via
 * {@code BLibMobEffectTags#MILK_IMMUNE}. Mud washes off the moment the wearer goes underwater, handled by the per-tick
 * check below.
 */
public class MudStatusEffect extends MobEffect {

    public MudStatusEffect() {
        super(MobEffectCategory.NEUTRAL, Color.ofOpaque(0x6B4226).getColor());
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        // Default for most effects is "tick rarely" (gated by amplifier). We want to check water state every tick
        // so the cloak washes off the instant the wearer dives in.
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.isUnderWater()) {
            // removeEffect inside applyEffectTick mid-iteration of LivingEntity.tickEffects can trigger CME, but
            // tickEffects has a try/catch around the iteration specifically for this case. Safe in practice.
            entity.removeEffect(PredatorMobEffects.getMudHolder());
            broadcast(entity, 0);
            return true;
        }

        var washingFast = drainFaster(entity);
        broadcastPeriodically(entity, washingFast);
        return true;
    }

    /**
     * Rain and standing water rinse mud off at roughly double speed.
     * <p>
     * Full submersion is still the hard reset above — go under and it is gone in one tick, no matter how thick. This is
     * the softer case: getting rained on, or wading through the shallows, where the mud survives but visibly does not
     * last. Vanilla ticks the duration down by one in {@code MobEffectInstance.tick} immediately after this method
     * returns, so taking one extra tick here is exactly a 2x drain.
     * <p>
     * {@code isInRain()} is private on {@code Entity}, so rain is derived as "in water or rain, but not in water" —
     * which resolves to the same thing without another accessor. Shallow water is folded in at the same rate on the
     * reasoning that wading should not preserve mud better than drizzle does.
     *
     * @return {@code true} when the mud is being washed away faster than normal
     */
    private boolean drainFaster(LivingEntity entity) {
        if (entity.level().isClientSide || !entity.isInWaterOrRain()) {
            return false;
        }

        var instance = entity.getEffect(PredatorMobEffects.getMudHolder());

        // Leave the last tick alone and let vanilla expire it normally, rather than racing it to zero.
        if (instance == null || instance.isInfiniteDuration() || instance.getDuration() <= 1) {
            return false;
        }

        ((MobEffectInstanceDurationAccessor) instance).predator$setDuration(instance.getDuration() - 1);
        return true;
    }

    /**
     * ⚠ Mud has to be broadcast by hand. Vanilla never sends mob-effect packets to tracking clients — only to an
     * entity's passengers, plus {@code ServerPlayer} to itself — and {@code ServerEntity.sendPairingData} sends none at
     * all. Without this, an observing predator's client sees an empty effect map on every muddy mob and the thermal
     * cloak silently does nothing, which is exactly how it behaved before this was added.
     * <p>
     * This hook is the right home for it because {@link #shouldApplyEffectTickThisTick} already forces a tick every
     * tick for the water check, so the set of entities reaching here is precisely the set that has mud — no sweep, no
     * bookkeeping.
     */
    private void broadcastPeriodically(LivingEntity entity, boolean washingFast) {
        if (entity.level().isClientSide) {
            return;
        }

        var instance = entity.getEffect(PredatorMobEffects.getMudHolder());

        if (instance == null) {
            return;
        }

        // Halve the interval while the mud is washing off fast: the client derives remaining duration from an expiry
        // stamp, so an accelerated drain makes its estimate run long until the next refresh corrects it.
        var interval = washingFast ? PredatorMud.SYNC_INTERVAL_TICKS / 2 : PredatorMud.SYNC_INTERVAL_TICKS;

        // Offset by entity id so a herd of muddy mobs spreads its packets across the interval instead of spiking.
        var phase = Math.floorMod(entity.getId(), interval);

        if (Math.floorMod(entity.level().getGameTime(), interval) != phase) {
            return;
        }

        broadcast(entity, instance.getDuration());
    }

    private void broadcast(LivingEntity entity, int remainingTicks) {
        if (entity.level().isClientSide) {
            return;
        }

        var payload = new S2CMudStatePayload(entity.getId(), remainingTicks);
        Predator.MOD.networking().sendToAllClientsTrackingEntity(entity, payload);

        // Trackers exclude the entity itself, and a muddy player needs their own copy for the screen overlay ramp.
        if (entity instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            Predator.MOD.networking().sendToClient(serverPlayer, payload);
        }
    }
}
