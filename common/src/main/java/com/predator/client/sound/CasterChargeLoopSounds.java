package com.predator.client.sound;

import com.predator.common.gameplay.item.HandCasterItem;
import com.predator.common.registry.init.PredatorSoundEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;

/**
 * The hand caster's HOLD loop, client side — the second of two separate sounds: [stated] "the charge then the hold
 * loop", the same as the plasma bow's draw then hold.
 * <ul>
 * <li>The server plays the charge sound when the button goes down (HandCasterItem.startCharge).</li>
 * <li>This loop is queued to start {@link HandCasterItem#CHARGE_TICKS} later, when that charge sound has finished and
 * the caster is fully charged, and hums at full volume for as long as the button is held, following the shooter.</li>
 * <li>Released before then, the queued loop never starts at all. Released after, it drops out in a few ticks and the
 * fire sound takes over.</li>
 * </ul>
 * Driven by the charge state the server already broadcasts to everyone tracking the shooter (themselves included), so
 * every nearby player hears it.
 */
public final class CasterChargeLoopSounds {

    private static final float VOLUME = 0.9F;

    private static final int RELEASE_FADE_TICKS = 4;

    private static final Map<Integer, Loop> LOOPS = new HashMap<>();

    private CasterChargeLoopSounds() {}

    /** From the charge-state packet. */
    public static void onCharge(int entityId, boolean charging) {
        var minecraft = Minecraft.getInstance();

        if (minecraft.level == null) {
            return;
        }

        LOOPS.values().removeIf(Loop::isFinished);
        var loop = LOOPS.get(entityId);

        if (!charging) {
            if (loop != null) {
                loop.release();
            }

            return;
        }

        if (loop != null && !loop.isReleased()) {
            return;
        }

        var entity = minecraft.level.getEntity(entityId);

        if (entity == null) {
            return;
        }

        loop = new Loop(entity);
        LOOPS.put(entityId, loop);
        // ⚠ Queued, not played: it begins once the charge sound has run its course. A released charge stops the
        // queued instance first, and canPlaySound then refuses it when its turn comes.
        minecraft.getSoundManager().playDelayed(loop, HandCasterItem.CHARGE_TICKS);
    }

    private static final class Loop extends AbstractTickableSoundInstance {

        private final Entity shooter;

        private int releaseTicks = -1;

        Loop(Entity shooter) {
            super(PredatorSoundEvents.CASTER_CHARGE_LOOP.get(), SoundSource.PLAYERS, RandomSource.create());
            this.shooter = shooter;
            this.looping = true;
            this.delay = 0;
            this.volume = VOLUME;
            follow();
        }

        void release() {
            if (releaseTicks < 0) {
                releaseTicks = 0;
            }
        }

        boolean isReleased() {
            return releaseTicks >= 0;
        }

        /** Has the sound engine started it (it only ticks once playing)? */
        private boolean started;

        /**
         * Released and faded, or released while still queued — in which case it never starts, never ticks, and would
         * otherwise sit in the map forever.
         */
        boolean isFinished() {
            return isStopped() || isReleased() && (!started || releaseTicks >= RELEASE_FADE_TICKS);
        }

        private void follow() {
            this.x = shooter.getX();
            this.y = shooter.getEyeY();
            this.z = shooter.getZ();
        }

        /** Checked by the sound engine when the queued loop's turn comes. */
        @Override
        public boolean canPlaySound() {
            return !isReleased() && !shooter.isRemoved();
        }

        @Override
        public void tick() {
            started = true;

            if (shooter.isRemoved()) {
                stop();
                return;
            }

            follow();

            if (releaseTicks >= 0) {
                releaseTicks++;
                this.volume = VOLUME * Math.max(0.0F, 1.0F - (float) releaseTicks / RELEASE_FADE_TICKS);

                if (releaseTicks >= RELEASE_FADE_TICKS) {
                    stop();
                }
            }
        }
    }
}
