package com.predator.common.gameplay.item.battleaxe;

import com.predator.common.registry.init.PredatorDataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where the player's battleaxe is in the slam — the ONE place both the third-person pose and the first-person view
 * read.
 * <p>
 * [stated] "its should make you jump swing back with the axe while youre in the air then swing down when you land so
 * the motion jump while raising the axe, it holds, then slams down when you land. Also the axe head doesnt hit the
 * ground it kind of lands at your center. the axe needs to rotate downward maybe 45 degrees when you land".
 * <p>
 * Two weights, each 0 to 1: {@code raise} (axe up and back) and {@code strike} (axe driven down). Read from the two
 * moments the server stamps on the axe — the leap and the landing — so every client, not just the swinger, sees it.
 * <ul>
 * <li>AIRBORNE: raise climbs to 1 over {@link #RAISE_TICKS} and HOLDS there until it lands.</li>
 * <li>LANDED: raise hands over to strike in {@link #STRIKE_TICKS} (the chop), strike holds for {@link #HOLD_TICKS} (the
 * head in the dirt), then eases back to the normal carry over {@link #RETURN_TICKS}.</li>
 * </ul>
 */
public final class BattleaxeSlamPose {

    public static int RAISE_TICKS = 4;

    public static int STRIKE_TICKS = 2;

    public static int HOLD_TICKS = 5;

    public static int RETURN_TICKS = 5;

    /** Never raised longer than this — a leap that somehow never lands does not freeze the arms up. */
    private static final int MAX_AIRBORNE_TICKS = 60;

    /**
     * ⚠ MUST MATCH {@code BattleaxeItem.MIN_AIRBORNE_TICKS}. The server ignores ground contact for the first couple of
     * ticks so the launch tick itself is not read as a landing; the client has to ignore exactly the same window or it
     * would smash instantly on the tick the leap begins.
     */
    private static final int MIN_AIRBORNE_TICKS = 3;

    public static final Pose NONE = new Pose(0.0F, 0.0F);

    public record Pose(
        float raise,
        float strike
    ) {

        public boolean active() {
            return raise > 0.0F || strike > 0.0F;
        }
    }

    private BattleaxeSlamPose() {
        throw new UnsupportedOperationException();
    }

    /**
     * ⚠⚠ CLIENT-SIDE LANDINGS, KEYED BY ENTITY ID. [stated] Sep 25: the smash completed about a quarter second AFTER
     * touching down - "charge jump land smash" instead of smashing on contact.
     * <p>
     * That gap was NOT the strike animating too slowly ({@link #STRIKE_TICKS} is 2 ticks). It was WHEN the strike was
     * allowed to begin. The trigger was {@code BATTLEAXE_SLAM_AT}, a data component the SERVER stamps on seeing
     * {@code onGround()}, which then has to travel back to every client as an updated ItemStack before anyone can start
     * the chop. That round trip is the delay, and it scales with ping - barely visible in single player, worse the
     * further away the server is. Speeding up the strike would have made the chop snappier and left the gap exactly
     * where it was.
     * </p>
     * <p>
     * ⭐ So the VISUAL now triggers locally: every client already knows when a player lands, because position and
     * {@code onGround} are synced for all entities. The first render tick on which a raised player is on the ground is
     * the moment of the smash, for the swinger and every bystander alike, at zero latency.
     * </p>
     * <p>
     * ⚠ The server still stamps the component and still owns the damage - it remains the authority, and the fallback
     * below reads it for anyone who starts tracking the player mid-move and so never saw the touchdown.
     * </p>
     */
    private static final Map<Integer, Float> LOCAL_LANDINGS = new ConcurrentHashMap<>();

    /** @param now game time plus the partial tick, for smooth motion between ticks */
    public static Pose of(ItemStack stack, float now) {
        return of(stack, now, null);
    }

    /**
     * @param holder the player being rendered, so the landing can be seen locally instead of waited for. Null falls
     *               back to the server's stamp.
     */
    public static Pose of(ItemStack stack, float now, @Nullable Player holder) {
        var leapAt = stack.get(PredatorDataComponents.BATTLEAXE_LEAP_AT.get());
        var slamAt = stack.get(PredatorDataComponents.BATTLEAXE_SLAM_AT.get());

        if (leapAt != null && (slamAt == null || slamAt < leapAt)) {
            var airborne = now - leapAt;

            if (airborne < 0.0F || airborne > MAX_AIRBORNE_TICKS) {
                forget(holder);

                return NONE;
            }

            // Airborne and on the ground = this is the touchdown, and it is the first frame to notice it.
            if (holder != null && airborne >= MIN_AIRBORNE_TICKS && holder.onGround()) {
                var landedAt = LOCAL_LANDINGS.computeIfAbsent(holder.getId(), $ -> now);

                return struck(now - landedAt);
            }

            forget(holder);

            return new Pose(Mth.clamp(airborne / RAISE_TICKS, 0.0F, 1.0F), 0.0F);
        }

        // The server's stamp has arrived. Prefer the landing this client actually saw, so the chop does not restart.
        if (holder != null) {
            var landedAt = LOCAL_LANDINGS.get(holder.getId());

            if (landedAt != null) {
                var since = now - landedAt;

                if (since >= 0.0F && since < STRIKE_TICKS + HOLD_TICKS + RETURN_TICKS) {
                    return struck(since);
                }

                LOCAL_LANDINGS.remove(holder.getId());
            }
        }

        if (slamAt == null) {
            return NONE;
        }

        var landed = now - slamAt;

        return landed < 0.0F ? NONE : struck(landed);
    }

    /** The chop, hold and return, measured from the moment of contact - wherever that moment came from. */
    private static Pose struck(float landed) {
        if (landed < STRIKE_TICKS) {
            var k = landed / STRIKE_TICKS;

            return new Pose(1.0F - k, k);
        }

        if (landed < STRIKE_TICKS + HOLD_TICKS) {
            return new Pose(0.0F, 1.0F);
        }

        var easing = (landed - STRIKE_TICKS - HOLD_TICKS) / RETURN_TICKS;

        return easing >= 1.0F ? NONE : new Pose(0.0F, 1.0F - easing);
    }

    private static void forget(@Nullable Player holder) {
        if (holder != null) {
            LOCAL_LANDINGS.remove(holder.getId());
        }
    }

    /** ⚠ Entity ids are per-session; a stale one would make a stranger's axe chop next world. */
    public static void clear() {
        LOCAL_LANDINGS.clear();
    }
}
