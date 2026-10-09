package com.predator.mixin;

import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes {@code MobEffectInstance.duration} for writing.
 * <p>
 * Needed because there is no public way to shorten a running effect. {@code mapDuration} looks like the answer but is a
 * pure read — vanilla assigns its result itself ({@code this.duration = this.mapDuration(d -> d - 1)}) — and
 * {@code addEffect} with a shorter instance is ignored outright, since {@code MobEffectInstance.update} only accepts a
 * replacement that is longer or stronger. Removing and re-adding mid-tick would work but means tearing the effect down
 * and rebuilding it sixty times a minute in the rain, inside the very iteration that is ticking it.
 * <p>
 * Used only by {@code MudStatusEffect} to drain mud faster in the rain.
 */
@Mixin(MobEffectInstance.class)
public interface MobEffectInstanceDurationAccessor {

    @Accessor("duration")
    void predator$setDuration(int duration);
}
