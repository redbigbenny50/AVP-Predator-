package com.predator.common.gameplay.entity.living.yautja.ai;

import com.blib.api.common.goap.v1.GOAPSensors;
import com.just.ai.goap.Agent;
import com.just.ai.goap.graph.Graph;
import com.just.ai.goap.plan.ReplanPolicies;
import com.predator.common.gameplay.entity.living.yautja.Yautja;

/**
 * The yautja's GOAP graph.
 * <h2>Wiring, for the record</h2> ⭐ BLib mixes into {@code LivingEntity} itself: {@code MixinLivingEntity_GOAPUser}
 * gives EVERY living entity an agent and ticks it server-side, doing nothing while {@code blib$getGOAPGraphOrNull}
 * returns null. So converting a mob is implementing {@code GOAPUser} and handing back a graph — there is no agent to
 * construct and no tick to write.
 * <h2>⚠⚠ The sensors package is not optional</h2> {@link #applyAgentProperties} replans on health dropping and on
 * catching fire, which READS {@code GOAPSensors.HEALTH_RATIO} and {@code IS_ON_FIRE}. A graph carrying that policy
 * without {@link #addSensorsPackage} logs "no sensor exists for key" and the replan silently never fires. That warning
 * is the tell for any dead behaviour here.
 */
public final class YautjaGOAP {

    public static final Graph<Yautja> GRAPH = Graph.<Yautja>builder()
        .apply(YautjaGOAP::addSensorsPackage)
        .apply(YautjaGOAP::addCombatPackage)
        .apply(YautjaGOAP::addIdlePackage)
        .build();

    /** The three the replan policy depends on, plus ground contact. */
    public static Graph.Builder<Yautja> addSensorsPackage(Graph.Builder<Yautja> builder) {
        return builder
            .addSensor(GOAPSensors.IS_ON_GROUND)
            .addSensor(GOAPSensors.IS_ON_FIRE)
            .addSensor(GOAPSensors.HEALTH_RATIO);
    }

    /**
     * Hunt and kill.
     * <p>
     * The three BLib compose sensors chain off {@link YautjaSensors#NEARBY_ATTACKABLE_TARGETS}, so supplying that one
     * list is what makes "has a target" and "nearest target" exist at all.
     */
    public static Graph.Builder<Yautja> addCombatPackage(Graph.Builder<Yautja> builder) {
        return builder
            .addGoal(YautjaCombat.KILL_TARGET)
            .addAction(YautjaCombat.MOVE_TO_TARGET)
            .addAction(YautjaCombat.MELEE_ATTACK)
            .addSensor(YautjaSensors.NEARBY_ATTACKABLE_TARGETS)
            .addSensor(GOAPSensors.NEAREST_ATTACKABLE_TARGETS)
            .addSensor(GOAPSensors.NEAREST_ATTACKABLE_TARGET)
            .addSensor(GOAPSensors.HAS_ATTACK_TARGET)
            .addSensor(YautjaSensors.IS_TARGET_IN_MELEE_RANGE)
            // ⚠ MOVE_TO_TARGET reads this, so it MUST be registered here — a policy carrying a key with no sensor logs
            // "no sensor exists for key" and the replan silently never fires. Exactly the trap this class warns about.
            .addSensor(YautjaSensors.IS_HOLDING_RANGE);
    }

    /** Roam when there is nothing worth hunting. */
    public static Graph.Builder<Yautja> addIdlePackage(Graph.Builder<Yautja> builder) {
        return builder
            .addGoal(YautjaCombat.SATISFY_BOREDOM)
            .addAction(YautjaCombat.WANDER)
            .addSensor(YautjaSensors.IS_BORED);
    }

    /**
     * When the agent throws its plan away and thinks again.
     * <p>
     * ⚠ The 20-tick interval matters more than it looks. A GOAP action is just {@code perform()} every tick, so a
     * replan that fires too often is how avp_alien's idle wander ended up re-pathing 28 times per tick and eating a
     * tenth of the server's time. Anything added here must be idempotent per tick.
     */
    public static Agent.Builder<Yautja> applyAgentProperties(Agent.Builder<Yautja> builder) {
        // ⚠ No plan executor is set, deliberately — LivingEntityAgent already installs a ConcurrentPlanExecutor
        // with BLib's ActionMaskPlanResolver. avp_alien's applyBaseAgentProperties sets ONLY the replan policy for
        // the same reason; overriding the executor here would throw away the mask-based concurrency the caster
        // depends on.
        return builder
            .withReplanPolicy(
                ReplanPolicies.anyOf(
                    ReplanPolicies.ifNoActivePlans(),
                    ReplanPolicies.custom(context -> context.agent().getActor().tickCount % 20 == 0),
                    ReplanPolicies.custom(context -> context.agent().getActor().isOnFire())
                )
            );
    }

    private YautjaGOAP() {
        throw new UnsupportedOperationException();
    }
}
