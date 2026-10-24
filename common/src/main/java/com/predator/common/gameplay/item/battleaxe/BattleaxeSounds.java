package com.predator.common.gameplay.item.battleaxe;

import com.predator.common.gameplay.entity.living.yautja.Yautja;
import com.predator.common.gameplay.entity.living.yautja.animation.YautjaAttackAnimation;
import com.predator.common.registry.init.PredatorSoundEvents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Every battleaxe sound, for the player and the yautja alike, and WHEN each one plays.
 * <h2>His recordings</h2> [stated] Oct 10 - "these are the battle axe sounds ... 3 strikes because theres 3 attacks".
 * Equip, put-away, three strikes. The slam's downswing reuses strike 2 at 0.9 pitch (sounds.json), so nothing extra is
 * shipped for it.
 * <h2>⭐ The rule all the timing follows: the loudest moment of the swing lands ON the hit</h2> Each strike is a whoosh
 * that builds to a peak, measured from the files at 25 ms resolution: strike 1 peaks 0.30 s in, strike 2 0.30 s, strike
 * 3 0.18 s (0.33 s for strike 2 slowed to 0.9 pitch). Starting a strike when the clip starts would put that peak on the
 * wind-up of the slower swings and miss the blade entirely, so each is started PEAK-TICKS before the impact frame the
 * combat code already uses ({@link YautjaAttackAnimation#impactTicks()}).
 * <ul>
 * <li>Horizontal: impact tick 11, peak 6 ⇒ starts on tick 5.</li>
 * <li>Left diagonal: impact tick 10, peak 6 ⇒ starts on tick 4.</li>
 * <li>Right diagonal: impact tick 7, peak 4 ⇒ starts on tick 3.</li>
 * <li>Yautja slam: impact tick 17, peak 7 ⇒ starts on tick 10, as the axe tops out overhead.</li>
 * <li>Player slam: the leap lands on tick 12 ⇒ starts on tick 5, one tick before the top of the leap - [stated] "at the
 * height of the axe going into the fall".</li>
 * </ul>
 * ⚠ The strikes play whether or not the blow connects - they are the SWING. The hit itself keeps its own crit sound.
 * <p>
 * [stated] "the strikes might need to be adjusted to have different pitches if needed." The three recordings are
 * already distinct sounds, so each keeps its own pitch and only wobbles by {@link #STRIKE_PITCH_SPREAD} per swing so a
 * repeated combo does not sound canned.
 */
public final class BattleaxeSounds {

    /** Ticks from the start of each strike file to its loudest point (25 ms envelope of the shipped files). */
    public static int HORIZONTAL_PEAK_TICKS = 6;

    public static int LEFT_PEAK_TICKS = 6;

    public static int RIGHT_PEAK_TICKS = 4;

    /** Strike 2 at 0.9 pitch: 0.30 s / 0.9 = 0.33 s. */
    public static int SLAM_SWING_PEAK_TICKS = 7;

    /** ±5% per swing. Small on purpose - see the class note. */
    public static float STRIKE_PITCH_SPREAD = 0.05F;

    /**
     * Vanilla's air physics for a living entity (LivingEntity.travel, read from the 1.21.1 jar): gravity, then drag.
     */
    private static final double GRAVITY = 0.08D;

    private static final double AIR_DRAG = 0.98D;

    /**
     * A yautja's strike sound waiting for its start tick, server side.
     * <p>
     * ⚠ Weak keys: a yautja that unloads or dies with a swing pending is simply dropped, never kept alive by this map.
     * The battleaxe cooldown (18 ticks) is longer than any wait here (5 at most), so a yautja never has two pending.
     */
    private static final Map<Yautja, Pending> PENDING = new WeakHashMap<>();

    /** ⚠ Remembers the WEAPON too, so a yautja that swapped weapons in the wait never plays the old one's swing. */
    private record Pending(
        int tick,
        SoundEvent sound,
        Item weapon
    ) {}

    private BattleaxeSounds() {
        throw new UnsupportedOperationException();
    }

    /** {@return the strike for one step of the yautja's battleaxe combo, or null for anything that is not one} */
    public static @Nullable SoundEvent strikeFor(YautjaAttackAnimation step) {
        return switch (step) {
            case BATTLEAXE_HORIZONTAL -> PredatorSoundEvents.BATTLEAXE_STRIKE_HORIZONTAL.get();
            case BATTLEAXE_LEFT -> PredatorSoundEvents.BATTLEAXE_STRIKE_LEFT.get();
            case BATTLEAXE_RIGHT -> PredatorSoundEvents.BATTLEAXE_STRIKE_RIGHT.get();
            default -> null;
        };
    }

    private static int peakTicks(YautjaAttackAnimation step) {
        return switch (step) {
            case BATTLEAXE_HORIZONTAL -> HORIZONTAL_PEAK_TICKS;
            case BATTLEAXE_LEFT -> LEFT_PEAK_TICKS;
            case BATTLEAXE_RIGHT -> RIGHT_PEAK_TICKS;
            default -> 0;
        };
    }

    /**
     * Queues the swing sound for a battleaxe step the yautja has just started, timed so its peak lands on the impact
     * frame. Does nothing for a step that is not a battleaxe swing.
     */
    public static void scheduleYautjaStrike(Yautja yautja, YautjaAttackAnimation step) {
        var sound = strikeFor(step);

        if (sound == null || yautja.level().isClientSide) {
            return;
        }

        schedule(yautja, sound, step.impactTicks(), peakTicks(step));
    }

    /**
     * Queues any yautja weapon swing so its loudest point ({@code peakTicks} into the file) lands on the impact frame
     * ({@code impactTicks} after the clip starts). Shared: the plasma sword uses it too (PlasmaSwordItem).
     * <p>
     * ⚠ One pending swing per yautja - a new one replaces an old one. Every melee cooldown is longer than any wait.
     */
    public static void schedule(Yautja yautja, SoundEvent sound, int impactTicks, int peakTicks) {
        if (yautja.level().isClientSide) {
            return;
        }

        var delay = Math.max(0, impactTicks - peakTicks);

        if (delay == 0) {
            play(yautja, sound, varied(yautja));

            return;
        }

        PENDING.put(yautja, new Pending(yautja.tickCount + delay, sound, yautja.getMainHandItem().getItem()));
    }

    /** Plays a queued swing once its tick arrives. Called every server tick from YautjaCombat.tickPendingStrike. */
    public static void tickYautja(Yautja yautja) {
        var pending = PENDING.get(yautja);

        if (pending == null || yautja.tickCount < pending.tick()) {
            return;
        }

        PENDING.remove(yautja);

        // ⚠ A yautja that died or put the weapon away in the wait does not swing it.
        if (!yautja.isAlive() || !yautja.getMainHandItem().is(pending.weapon())) {
            return;
        }

        play(yautja, pending.sound(), varied(yautja));
    }

    /** {@return ticks after the slam clip starts at which the downswing sound begins} */
    public static int yautjaSlamSwingTick(int impactTicks) {
        return Math.max(0, impactTicks - SLAM_SWING_PEAK_TICKS);
    }

    /**
     * {@return airborne ticks after a player's slam leap at which the downswing sound begins}
     * <p>
     * ⚠ Worked out from {@link BattleaxeItem#LEAP_VELOCITY} with vanilla's own air physics rather than written down, so
     * retuning the leap keeps the sound on the landing. 0.44 lands on tick 12 ⇒ 5.
     */
    public static int playerSlamSwingTick() {
        var velocity = BattleaxeItem.LEAP_VELOCITY;
        var height = 0.0D;
        var ticks = 0;

        do {
            height += velocity;
            velocity = (velocity - GRAVITY) * AIR_DRAG;
            ticks++;
        } while (height > 0.0D && ticks < 40);

        return Math.max(1, ticks - SLAM_SWING_PEAK_TICKS);
    }

    /** The slam's downswing, for whoever is swinging. */
    public static void playSlamSwing(LivingEntity user) {
        play(user, PredatorSoundEvents.BATTLEAXE_SLAM_SWING.get(), 1.0F);
    }

    /**
     * A player's connecting swing: any of the three strikes.
     * <p>
     * ⚠ Hits only, the same as vanilla: Player.attack is the only server-side event a swing produces, and vanilla's own
     * swing sounds (sweep, strong, weak) are hit-only too. A miss reaches the server as a bare arm-swing packet that is
     * also sent every tick while mining, so hooking it would whoosh on every block chopped.
     */
    public static void playPlayerStrike(LivingEntity player) {
        var sound = switch (player.getRandom().nextInt(3)) {
            case 0 -> PredatorSoundEvents.BATTLEAXE_STRIKE_HORIZONTAL.get();
            case 1 -> PredatorSoundEvents.BATTLEAXE_STRIKE_LEFT.get();
            default -> PredatorSoundEvents.BATTLEAXE_STRIKE_RIGHT.get();
        };

        play(player, sound, varied(player));
    }

    /** Taking the axe out ({@code drawn}) or putting it away. */
    public static void playEquip(LivingEntity user, boolean drawn) {
        play(
            user,
            drawn ? PredatorSoundEvents.BATTLEAXE_EQUIP.get() : PredatorSoundEvents.BATTLEAXE_PUTAWAY.get(),
            varied(user) * 0.5F + 0.5F
        );
    }

    /** A per-swing pitch: 1.0 give or take {@link #STRIKE_PITCH_SPREAD}. */
    public static float varied(LivingEntity user) {
        return 1.0F + (user.getRandom().nextFloat() - user.getRandom().nextFloat()) * STRIKE_PITCH_SPREAD;
    }

    /**
     * Server side only; everyone in range hears it from the user's position. ⚠ The entity's own sound source, so a
     * player's axe follows the Players slider and a yautja's the Hostile one.
     */
    public static void play(LivingEntity user, SoundEvent sound, float pitch) {
        if (user.level().isClientSide || user.isSilent()) {
            return;
        }

        user.level().playSound(null, user.getX(), user.getY(), user.getZ(), sound, user.getSoundSource(), 1.0F, pitch);
    }
}
