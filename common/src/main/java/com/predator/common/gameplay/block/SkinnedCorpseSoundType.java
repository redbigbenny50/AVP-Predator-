package com.predator.common.gameplay.block;

import com.predator.common.registry.init.PredatorSoundEvents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.SoundType;
import org.jetbrains.annotations.NotNull;

/**
 * The skinned corpse's block sounds. [stated] "make that the sound while mining it repeated and then a louder one for
 * the actual break."
 * <ul>
 * <li><b>Hit</b> — vanilla plays it every 4 ticks while the block is being mined: {@code corpse.hit}, a short cut taken
 * from the slash.</li>
 * <li><b>Break</b> — vanilla plays it once when a player breaks the block: {@code corpse.break}, the full slash,
 * compressed and louder.</li>
 * <li>Step / place / fall — slime, as before. The corpse has no collision, so step and fall barely ever play.</li>
 * </ul>
 * <h2>⚠ The two getters are overridden, not passed to the constructor</h2> A {@code SoundType} is built when the
 * block's properties are built, which can be before this mod's sound events are bound. Resolving the holders inside the
 * getters defers that to the first time a sound actually plays — the same trick NeoForge's {@code DeferredSoundType}
 * uses, done here without depending on a loader.
 * <h2>⚠ Pitch is baked into the files</h2> Vanilla plays a block's hit sound at HALF its pitch and its break sound at
 * 0.8. The files are authored 2x and 1.25x higher to match, so in game they sound like the original recording.
 */
public class SkinnedCorpseSoundType extends SoundType {

    public SkinnedCorpseSoundType() {
        super(
            1.0F,
            1.0F,
            SoundEvents.SLIME_BLOCK_BREAK,
            SoundEvents.SLIME_BLOCK_STEP,
            SoundEvents.SLIME_BLOCK_PLACE,
            SoundEvents.SLIME_BLOCK_HIT,
            SoundEvents.SLIME_BLOCK_FALL
        );
    }

    @Override
    public @NotNull SoundEvent getBreakSound() {
        return PredatorSoundEvents.CORPSE_BREAK.get();
    }

    @Override
    public @NotNull SoundEvent getHitSound() {
        return PredatorSoundEvents.CORPSE_HIT.get();
    }
}
