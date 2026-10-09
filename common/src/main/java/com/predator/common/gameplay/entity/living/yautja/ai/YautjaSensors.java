package com.predator.common.gameplay.entity.living.yautja.ai;

import com.blib.api.common.goap.v1.GOAPSensors;
import com.just.ai.goap.StateKey;
import com.just.ai.goap.sensor.Sensor;
import com.just.ai.goap.sensor.Sensors;
import com.predator.common.gameplay.entity.living.yautja.Yautja;
import com.predator.common.gameplay.entity.living.yautja.util.YautjaAlienTruce;
import com.predator.common.gameplay.entity.living.yautja.util.YautjaPredicates;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/**
 * What a yautja knows about the world, as GOAP sees it.
 * <h2>Sensing is demand-driven and memoised</h2> A sensor only runs when some condition actually reads its key, and
 * then once per tick. Registration order in the graph is irrelevant. ⚠ The corollary is the trap: a key with NO sensor
 * registered logs "no sensor exists for key" and the behaviour depending on it is silently dead. That is the first
 * thing to grep the log for if a yautja stands still doing nothing.
 */
public final class YautjaSensors {

    /**
     * The ONE thing this yautja is hunting — whatever {@code Mob.getTarget()} says.
     * <p>
     * ⭐ Feeds {@link GOAPSensors#NEARBY_ATTACKABLE_TARGETS_KEY}, the root of BLib's own
     * {@code NEAREST_ATTACKABLE_TARGETS} → {@code NEAREST_ATTACKABLE_TARGET} → {@code HAS_ATTACK_TARGET} chain.
     * <p>
     * ⚠⚠ THIS USED TO RUN ITS OWN 37-BLOCK SCAN AND HAND BACK EVERY VALID CANDIDATE, WHICH GAVE THE YAUTJA TWO
     * DIFFERENT TARGETS AT ONCE. The vanilla target selector set {@code getTarget()}; this sensor independently picked
     * the NEAREST valid entity in a 37-block CUBE — through walls, underground, overhead — with no line of sight, no
     * persistence and no hysteresis. So the mob would path toward one entity while the caster, the climb speed and the
     * stalk/pursuit navigator switch all read another, and the "nearest" flipped between near-equal candidates every
     * tick. That is precisely the reported symptom: chasing the player, then veering off at nothing visible and
     * spinning on the spot.
     * <p>
     * Deferring to {@code getTarget()} makes the target selector the single source of truth. It already applies the
     * honor code through the same {@link YautjaPredicates#isValidTarget}, and it brings persistence, forgetting and
     * line of sight with it. The scan disappearing is a bonus: it ran once per tick per yautja.
     * <p>
     * ⚠ The plasma caster keeps its OWN independent target on purpose — that is a feature, not this inconsistency.
     */
    public static final Sensor.Mono<Yautja, List<LivingEntity>> NEARBY_ATTACKABLE_TARGETS = Sensors.map(
        GOAPSensors.NEARBY_ATTACKABLE_TARGETS_KEY,
        yautja -> {
            var target = yautja.getTarget();

            // Oct 5 - the alien truce: a marine it was already fighting (or that a stray blow set it on) is let go.
            if (target != null && YautjaAlienTruce.spares(yautja, target)) {
                yautja.setTarget(null);
                target = null;
            }

            return target != null && target.isAlive() && YautjaPredicates.isValidTarget(yautja, target)
                ? List.<LivingEntity>of(target)
                : List.<LivingEntity>of();
        }
    );

    /** True once the target is close enough to swing at. Drives the switch from closing to killing. */
    public static final StateKey.Sensed<Boolean> IS_TARGET_IN_MELEE_RANGE_KEY =
        StateKey.sensed("yautja_is_target_in_melee_range");

    public static final Sensor<Yautja> IS_TARGET_IN_MELEE_RANGE = Sensors.compose(
        GOAPSensors.NEAREST_ATTACKABLE_TARGET.key(),
        IS_TARGET_IN_MELEE_RANGE_KEY,
        (yautja, target) -> target.isSome()
            && yautja.distanceToSqr(target.unwrap()) <= meleeReachSqr(yautja)
    );

    /**
     * True when there is nothing worth hunting.
     * <p>
     * Two halves, and the second one is load-bearing. No target, because a hunter with prey in sight is never bored and
     * the idle goal must not compete with the kill goal for the MOVE mask. AND rested out — because an effect has to be
     * SATISFIABLE by the action that claims it, and wandering cannot give a yautja a target. With only the first half,
     * the wander action's effect could never come true, the plan never completed, and the yautja roamed forever without
     * ever playing its idle.
     */
    /** Whether the fall-back leap is holding it at range right now. See YautjaFallBackGoal. */
    public static final StateKey.Sensed<Boolean> IS_HOLDING_RANGE_KEY = StateKey.sensed("yautja_is_holding_range");

    public static final Sensor.Mono<Yautja, Boolean> IS_HOLDING_RANGE = Sensors.map(
        IS_HOLDING_RANGE_KEY,
        Yautja::isHoldingRange
    );

    public static final StateKey.Sensed<Boolean> IS_BORED_KEY = StateKey.sensed("yautja_is_bored");

    public static final Sensor<Yautja> IS_BORED = Sensors.compose(
        GOAPSensors.HAS_ATTACK_TARGET.key(),
        IS_BORED_KEY,
        (yautja, hasTarget) -> !Boolean.TRUE.equals(hasTarget) && yautja.tickCount >= yautja.getRestUntilTick()
    );

    /**
     * {@return the square of how close the target has to be to swing at it}
     * <p>
     * Vanilla's own melee reach formula for a mob: its own width plus the target's, squared, with a little slack. Kept
     * as one method so the attack action and this sensor can never disagree about what "in range" means — a classic way
     * to get an actor that walks to the target and then stands there.
     */
    /**
     * {@return the squared distance at which this yautja can strike}
     * <p>
     * ⚠ Public so {@code YautjaDartGoal} can use the SAME number for its minimum range. Melee and darts have to divide
     * the world at one boundary: two definitions and the yautja either punches and fires at the same time, or sits in a
     * band where it will do neither.
     */
    public static double meleeReachSqr(Yautja yautja) {
        var reach = yautja.getBbWidth() * 2.0F + 1.0;
        return reach * reach;
    }

    private YautjaSensors() {
        throw new UnsupportedOperationException();
    }
}
