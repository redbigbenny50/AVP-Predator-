package com.predator.client.sound;

import com.predator.common.network.packet.S2CDestructLaughPayload;
import com.predator.common.registry.init.PredatorSoundEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The self-destruct laugh, client side. One laugh per counting gauntlet, pinned to wherever the gauntlet is (worn,
 * dropped or placed — the server reports its position), and faded out over a second when the server stops reporting it:
 * [stated] "it would fade out if its disarmed."
 * <h2>Why a client sound and not a plain server playSound</h2> A server can start a sound but can only CUT one, never
 * fade it, and a 20-second sound started at one spot stays there while the wearer runs off. A ticking client sound can
 * follow the gauntlet and fade.
 */
public final class DestructLaughSounds {

    /** Reports arrive every {@code REPORT_INTERVAL} server ticks; this many ticks without one means it has stopped. */
    private static final int SILENCE_BEFORE_FADE = 12;

    private static final int FADE_TICKS = 20;

    /** A client that first hears of a countdown later than this does not start the laugh — it would be out of step. */
    private static final int LATE_START_LIMIT = 30;

    /** Loud enough to carry like the countdown beeps (vanilla attenuates over 16 blocks per unit of volume). */
    private static final float VOLUME = 3.0F;

    private static final Map<UUID, Laugh> LAUGHS = new HashMap<>();

    private DestructLaughSounds() {}

    public static void onReport(S2CDestructLaughPayload payload) {
        var minecraft = Minecraft.getInstance();

        if (minecraft.level == null) {
            return;
        }

        LAUGHS.values().removeIf(Laugh::isStopped);

        var laugh = LAUGHS.get(payload.id());

        if (laugh == null) {
            if (payload.elapsedTicks() > LATE_START_LIMIT) {
                return;
            }

            laugh = new Laugh(payload.x(), payload.y(), payload.z());
            LAUGHS.put(payload.id(), laugh);
            minecraft.getSoundManager().play(laugh);
        }

        laugh.report(payload.x(), payload.y(), payload.z());
    }

    private static final class Laugh extends AbstractTickableSoundInstance {

        private int ticksSinceReport;

        private int fadeTicks;

        Laugh(double x, double y, double z) {
            super(PredatorSoundEvents.GAUNTLET_DESTRUCT_LAUGH.get(), SoundSource.BLOCKS, RandomSource.create());
            this.x = x;
            this.y = y;
            this.z = z;
            this.volume = VOLUME;
            this.looping = false;
        }

        void report(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.ticksSinceReport = 0;
        }

        @Override
        public void tick() {
            ticksSinceReport++;

            if (ticksSinceReport <= SILENCE_BEFORE_FADE) {
                return;
            }

            fadeTicks++;
            volume = VOLUME * Math.max(0.0F, 1.0F - (float) fadeTicks / FADE_TICKS);

            if (fadeTicks >= FADE_TICKS) {
                stop();
            }
        }
    }
}
