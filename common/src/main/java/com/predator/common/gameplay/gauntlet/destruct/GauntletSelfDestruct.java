package com.predator.common.gameplay.gauntlet.destruct;

import com.predator.common.gameplay.explosion.plasma.PlasmaDetonation;
import com.predator.common.registry.init.PredatorDataComponents;
import com.predator.common.registry.init.PredatorGameRules;
import com.predator.common.registry.init.PredatorSoundEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * The gauntlet's self-destruct: arming, the countdown, disarming, detonation. One class owns the rules; the block, the
 * item tick, the dropped-item tick and the screens only call in.
 * <h2>His spec (Sep 10)</h2>
 * <ul>
 * <li>Armed from the WORN gui only: four panels, each click sets a blank panel to 9; four 9s = armed. Any panel click
 * while armed blanks it = cancelled. Nothing counts while worn.</li>
 * <li>The countdown STARTS when the armed gauntlet is placed as a block. 40 seconds, four panels left to right, each 9
 * → 0 then blank over ten seconds; the last blank is the blast.</li>
 * <li>Counting, it cannot be worn; it can be picked up (shift + right click), carried, dropped, placed again — the
 * countdown follows the stack ({@link GauntletDestructRegistry} is the backstop).</li>
 * <li>Disarm: right-click the block, match a four-distinct-digit code. Break it (hardness 40): contents spill, the
 * gauntlet is destroyed, no blast.</li>
 * </ul>
 * <h2>⚠ Dials are non-final statics</h2>
 */
public final class GauntletSelfDestruct {

    public static final int PANELS = 4;

    /** [stated] 20 seconds total, so each panel runs 9 → 0 over five — two digits a second. */
    public static final int COUNTDOWN_SECONDS = 20;

    public static final int COUNTDOWN_TICKS = COUNTDOWN_SECONDS * 20;

    /** Ticks one panel is lit for. */
    public static final int TICKS_PER_PANEL = COUNTDOWN_TICKS / PANELS;

    /** Ticks one DIGIT is shown for. */
    public static final int TICKS_PER_DIGIT = TICKS_PER_PANEL / 10;

    /**
     * ⚠ The beep is ONCE A SECOND, not once a digit — [stated] "1 beep a second ... each 2 numbers would be a beep".
     */
    public static final int TICKS_PER_BEEP = 20;

    /**
     * ⚠ VOLUME IS RANGE. Vanilla's audible radius is 16 blocks x volume (and a flat 16 for anything at or below 1).
     * [stated] "needs to reach further ... 3x the range so it can be heard outside the crater radius and some
     * distance": 4.5 gives 72 blocks, three times the 24-block crater. Loudness at the bomb is unchanged — the client
     * clamps a nearby sound's gain — so this only widens who hears it.
     */
    public static float COUNTDOWN_VOLUME = 4.5F;

    /**
     * Pitch per column, left to right. [stated] "column one ... plays this sound for each digit change then column 2
     * gets a higher pitch column 3 gets higher, and column 4 is highest then the boom." Even semitone-ish steps;
     * Minecraft clamps pitch to 0.5-2.0, so these stay well inside.
     */
    public static float[] COLUMN_PITCH = { 1.0F, 1.2F, 1.45F, 1.75F };

    private GauntletSelfDestruct() {
        throw new UnsupportedOperationException();
    }

    // ------------------------------------------------------------------ state

    public static boolean isArmed(ItemStack stack) {
        return Boolean.TRUE.equals(stack.get(PredatorDataComponents.DESTRUCT_ARMED.get()));
    }

    /** {@return whether the countdown is running — armed AND placed at least once} */
    public static boolean isCounting(ItemStack stack) {
        return isArmed(stack) && stack.has(PredatorDataComponents.DESTRUCT_DEADLINE.get());
    }

    public static long deadline(ItemStack stack) {
        return stack.getOrDefault(PredatorDataComponents.DESTRUCT_DEADLINE.get(), Long.MIN_VALUE);
    }

    public static int code(ItemStack stack) {
        return stack.getOrDefault(PredatorDataComponents.DESTRUCT_CODE.get(), 0);
    }

    public static @Nullable UUID id(ItemStack stack) {
        return stack.get(PredatorDataComponents.DESTRUCT_ID.get());
    }

    public static @Nullable UUID armer(ItemStack stack) {
        return stack.get(PredatorDataComponents.DESTRUCT_ARMER.get());
    }

    // ------------------------------------------------------------------ arming (worn)

    /** Arms a worn, not-yet-counting gauntlet. Server side. */
    public static void arm(Player player, ItemStack stack) {
        if (isCounting(stack) || !PredatorGameRules.isSelfDestructEnabled(player.level())) {
            return;
        }

        stack.set(PredatorDataComponents.DESTRUCT_ARMED.get(), true);
        stack.set(PredatorDataComponents.DESTRUCT_ARMER.get(), player.getUUID());
    }

    /** Cancels an armed gauntlet that has NOT started counting. Once counting, only the code or breaking it will do. */
    public static boolean cancelArming(ItemStack stack) {
        if (isCounting(stack)) {
            return false;
        }

        clear(stack);

        return true;
    }

    // ------------------------------------------------------------------ countdown

    /** Starts the countdown on placement. Server side. Idempotent: a re-placed counting gauntlet keeps its deadline. */
    public static void startCountdown(ServerLevel level, ItemStack stack, Vec3 pos) {
        if (!isArmed(stack) || isCounting(stack)) {
            return;
        }

        // ⚠ Rule off: an armed gauntlet placed is just a gauntlet placed. It stays armed for when the rule is on.
        if (!PredatorGameRules.isSelfDestructEnabled(level)) {
            return;
        }

        var id = UUID.randomUUID();
        var deadline = level.getGameTime() + COUNTDOWN_TICKS;

        stack.set(PredatorDataComponents.DESTRUCT_ID.get(), id);
        stack.set(PredatorDataComponents.DESTRUCT_DEADLINE.get(), deadline);
        stack.set(PredatorDataComponents.DESTRUCT_CODE.get(), generateCode(level.getRandom()));
        GauntletDestructRegistry.get(level.getServer()).register(id, level.dimension(), pos, deadline, armer(stack));
    }

    /**
     * Called every server tick by whatever currently holds a counting gauntlet. Refreshes the registry, plays the
     * per-second tick, and detonates when due. {@code onDetonated} removes the stack from wherever it was.
     */
    public static void observe(ServerLevel level, ItemStack stack, Vec3 pos, Runnable onDetonated) {
        if (!isCounting(stack)) {
            return;
        }

        // ⚠ Rule turned off mid-countdown: defused where it stands, nothing goes off.
        if (!PredatorGameRules.isSelfDestructEnabled(level)) {
            forget(level, stack);
            return;
        }

        var id = id(stack);
        var registry = GauntletDestructRegistry.get(level.getServer());
        var now = level.getGameTime();
        var deadline = deadline(stack);

        if (id != null && !registry.contains(id)) {
            // ⚠ The backstop already fired this one (or it was disarmed elsewhere): this copy is spent.
            onDetonated.run();
            return;
        }

        if (now >= deadline) {
            if (id != null) {
                registry.remove(id);
            }

            onDetonated.run();
            PlasmaDetonation.detonate(level, pos, armer(stack));
            return;
        }

        if (id != null) {
            registry.touch(id, level.dimension(), pos);
        }

        // One beep a second — five per column at 20 s, so every second digit change is a beep. The pitch is the
        // COLUMN's, so a column's beeps share a pitch and each column steps up: the rise tells you how little is left
        // without reading the panels.
        var remainingTicks = deadline - now;

        // The laugh: reported to nearby players a few times a second. The client starts it on the first report,
        // keeps it on the gauntlet, and fades it once reports stop — disarmed, defused or gone off.
        if (id != null && remainingTicks % LAUGH_REPORT_INTERVAL == 0) {
            reportLaugh(level, id, pos, COUNTDOWN_TICKS - (int) remainingTicks);
        }

        if (remainingTicks % TICKS_PER_BEEP == 0) {
            var elapsed = COUNTDOWN_TICKS - (int) remainingTicks;
            var column = Mth.clamp(elapsed / TICKS_PER_PANEL, 0, PANELS - 1);

            level.playSound(
                null,
                pos.x,
                pos.y,
                pos.z,
                PredatorSoundEvents.GAUNTLET_DESTRUCT_COUNTDOWN.get(),
                SoundSource.BLOCKS,
                COUNTDOWN_VOLUME,
                COLUMN_PITCH[column]
            );
        }
    }

    /** {@return true and clears the countdown if {@code attempt} is the code} Server side. */
    /** How often a counting gauntlet tells nearby clients it is still counting, in ticks. */
    private static final int LAUGH_REPORT_INTERVAL = 5;

    /** Players this close hear the laugh. */
    private static final double LAUGH_REPORT_RANGE = 64.0;

    private static void reportLaugh(ServerLevel level, UUID id, Vec3 pos, int elapsedTicks) {
        var payload = new com.predator.common.network.packet.S2CDestructLaughPayload(id, pos.x, pos.y, pos.z, elapsedTicks);

        for (var player : level.players()) {
            if (player.position().closerThan(pos, LAUGH_REPORT_RANGE)) {
                com.predator.Predator.MOD.networking().sendToClient(player, payload);
            }
        }
    }

    public static boolean tryDisarm(ServerLevel level, ItemStack stack, int attempt) {
        if (!isCounting(stack) || attempt != code(stack)) {
            return false;
        }

        var id = id(stack);

        if (id != null) {
            GauntletDestructRegistry.get(level.getServer()).remove(id);
        }

        clear(stack);

        return true;
    }

    /** Forgets a counting gauntlet that was destroyed (mined). No blast. */
    public static void forget(ServerLevel level, ItemStack stack) {
        var id = id(stack);

        if (id != null) {
            GauntletDestructRegistry.get(level.getServer()).remove(id);
        }

        clear(stack);
    }

    private static void clear(ItemStack stack) {
        stack.remove(PredatorDataComponents.DESTRUCT_ARMED.get());
        stack.remove(PredatorDataComponents.DESTRUCT_DEADLINE.get());
        stack.remove(PredatorDataComponents.DESTRUCT_ID.get());
        stack.remove(PredatorDataComponents.DESTRUCT_CODE.get());
        stack.remove(PredatorDataComponents.DESTRUCT_ARMER.get());
    }

    // ------------------------------------------------------------------ display

    /** {@return whole seconds left, 0..COUNTDOWN_SECONDS} Client and server both read the same synced deadline. */
    public static int remainingSeconds(ItemStack stack, long now) {
        return Mth.clamp((int) Math.ceil((deadline(stack) - now) / 20.0), 0, COUNTDOWN_SECONDS);
    }

    /** {@return ticks left, 0..COUNTDOWN_TICKS} The display works in ticks so the digits step evenly at any length. */
    public static int remainingTicks(ItemStack stack, long now) {
        return (int) Mth.clamp(deadline(stack) - now, 0L, (long) COUNTDOWN_TICKS);
    }

    /**
     * {@return the digit each panel shows with {@code remainingTicks} left; -1 is blank} Panels burn left to right: a
     * panel shows 9 until its stretch begins, counts 9 → 0 evenly across it, and is blank after. All four blank is the
     * blast. ⚠ In TICKS, so the ten digits are spread evenly however long the countdown is — at 20 s that is a digit
     * every half second.
     */
    public static int[] countdownPanels(int remainingTicks) {
        var elapsed = COUNTDOWN_TICKS - Mth.clamp(remainingTicks, 0, COUNTDOWN_TICKS);
        var digits = new int[PANELS];

        for (var panel = 0; panel < PANELS; panel++) {
            var start = panel * TICKS_PER_PANEL;

            if (elapsed < start) {
                digits[panel] = 9;
            } else if (elapsed < start + TICKS_PER_PANEL) {
                digits[panel] = 9 - Mth.clamp((elapsed - start) * 10 / TICKS_PER_PANEL, 0, 9);
            } else {
                digits[panel] = -1;
            }
        }

        return digits;
    }

    /** Four DISTINCT digits, as ruled — no repeats. Packed as an int, leading zero allowed. */
    public static int generateCode(RandomSource random) {
        var pool = new int[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 };
        var code = 0;

        for (var i = 0; i < PANELS; i++) {
            var pick = i + random.nextInt(10 - i);
            var digit = pool[pick];

            pool[pick] = pool[i];
            code = code * 10 + digit;
        }

        return code;
    }

    /** {@return digit {@code panel} (0 = leftmost) of a packed four-digit code} */
    public static int digitAt(int code, int panel) {
        return code / (int) Math.pow(10, PANELS - 1 - panel) % 10;
    }
}
