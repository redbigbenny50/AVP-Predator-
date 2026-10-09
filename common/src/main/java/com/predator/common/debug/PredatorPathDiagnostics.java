package com.predator.common.debug;

import com.blib.api.common.goap.v1.GOAPSensors;
import com.blib.api.common.goap.v1.GOAPUser;
import com.just.ai.goap.StateKey;
import com.predator.Predator;
import com.predator.common.gameplay.entity.living.yautja.Yautja;
import com.predator.common.gameplay.entity.living.yautja.ai.YautjaSensors;
import com.predator.common.gameplay.entity.living.yautja.caster.PlasmaCaster;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * A greppable, log-based account of what a yautja is targeting, planning and pathing.
 * <h2>Why a log line and not the particle trail</h2> {@link PredatorPathDebug} shows WHERE a mob is going; this says
 * WHY, and it says it in a form a tester can send back. The two answer different halves of the same question and are
 * toggled separately.
 * <h2>Everything here is a READ</h2> ⚠ Nothing in this class may change behaviour — no retargeting, no path requests,
 * no state writes on the entity. A diagnostic that alters what it measures is worse than none, and the bug it is
 * chasing is precisely the kind that would hide behind that.
 * <h2>Reading a line</h2>
 *
 * <pre>
 * [pathdiag] yautja 1425 | tgt=player@11.2 los=Y chg=1 | goap hasTgt=Y melee=N bored=N plan=Y
 *                        | nav=pursuit NAVIGATING node=3/12 GROUND fails=0 | climb=N air=0 | 133,94,39
 * </pre>
 * <ul>
 * <li><b>chg</b> — how many times the target has CHANGED. A number that climbs while a single player is being chased is
 * target churn, which is what looks like spinning and aggroing onto nothing.</li>
 * <li><b>goap hasTgt / melee / bored</b> — read straight out of the GOAP world state, so they say what the graph
 * believes rather than what the entity looks like. {@code hasTgt=N} while {@code tgt=} names something is the two
 * disagreeing, which should now be impossible.</li>
 * <li><b>nav</b> — stalk or pursuit, i.e. which of the two navigators is live. Flipping every line means the target is
 * flickering in and out.</li>
 * <li><b>fails</b> — consecutive path failures. Climbing means it wants somewhere it cannot route to.</li>
 * </ul>
 */
public final class PredatorPathDiagnostics {

    /** Ticks between summary lines per entity. Two seconds: readable in a log, cheap with a dozen yautja loaded. */
    private static final int SUMMARY_INTERVAL_TICKS = 40;

    private static final String PREFIX = "[pathdiag] ";

    /**
     * Per-entity counters.
     * <p>
     * ⚠ A WeakHashMap, so an unloaded or dead yautja takes its entry with it — the same shape avp_alien had to adopt
     * for its egg-shelve counter after an action-scoped blackboard silently reset it every retry.
     */
    private static final Map<Yautja, State> STATES = new WeakHashMap<>();

    private static boolean enabled;

    private PredatorPathDiagnostics() {
        throw new UnsupportedOperationException();
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean value) {
        enabled = value;

        if (!value) {
            STATES.clear();
        }
    }

    // -----------------------------------------------------------------------------------------------------------
    // Events — logged the moment they happen, never throttled
    // -----------------------------------------------------------------------------------------------------------

    /**
     * Logged the moment the navigator gives up on a route.
     * <p>
     * ⚠ An EVENT, not a summary field. A failure that happens and is retried between two-second samples never shows up
     * in a periodic line, and that is exactly the case worth seeing — a yautja quietly failing to path four times a
     * second looks identical to one standing still.
     */
    public static void onPathFailed(Yautja yautja, int failures) {
        if (!enabled) {
            return;
        }

        var navState = yautja.getPathNavigator().getState();

        Predator.LOGGER.info(
            "{}yautja {} | PATH FAILED n={} dest={} tgt={} pos={}",
            PREFIX,
            yautja.getId(),
            failures,
            describe(navState.getTargetPos()),
            describe(yautja.getTarget()),
            pos(yautja)
        );
    }

    /**
     * Logged when a yautja WANTS to climb and does not.
     * <p>
     * ⚠ "It never climbed" is the single least debuggable report there is, because silence covers every cause: no
     * target, target not high enough, no hold within reach, cooldown, in water. Naming the reason turns it into one
     * line that says which.
     */
    /** The fall-back leap wanted to go but every bearing was blocked — the silent case worth seeing in a log. */
    public static void onFallBackBlocked(Yautja yautja) {
        if (!enabled) {
            return;
        }

        Predator.LOGGER.info(
            "{}yautja {} | FALL BACK blocked (no clear bearing) tgt={} pos={}",
            PREFIX,
            yautja.getId(),
            describe(yautja.getTarget()),
            yautja.blockPosition()
        );
    }

    public static void onClimbRefused(Yautja yautja, String reason) {
        if (!enabled) {
            return;
        }

        var state = STATES.computeIfAbsent(yautja, key -> new State());

        // Once per reason per entity — this is evaluated every tick and would otherwise bury the log.
        if (reason.equals(state.lastClimbRefusal)) {
            return;
        }

        state.lastClimbRefusal = reason;

        Predator.LOGGER.info(
            "{}yautja {} | CLIMB REFUSED reason={} tgt={} dy={} pos={}",
            PREFIX,
            yautja.getId(),
            reason,
            describe(yautja.getTarget()),
            yautja.getTarget() == null ? "-" : String.format("%.1f", yautja.getTarget().getY() - yautja.getY()),
            pos(yautja)
        );
    }

    /** Cleared when a climb actually starts, so the next refusal after a success is reported again. */
    public static void clearClimbRefusal(Yautja yautja) {
        var state = STATES.get(yautja);

        if (state != null) {
            state.lastClimbRefusal = null;
        }
    }

    public static void onJump(Yautja yautja, boolean running, int gapBlocks) {
        if (!enabled) {
            return;
        }

        Predator.LOGGER.info(
            "{}yautja {} | JUMP {} gap={} pos={}",
            PREFIX,
            yautja.getId(),
            running ? "running" : "standing",
            gapBlocks,
            pos(yautja)
        );
    }

    public static void onClimbAttach(Yautja yautja, int wallHeight) {
        if (!enabled) {
            return;
        }

        Predator.LOGGER.info("{}yautja {} | CLIMB attach wall={} pos={}", PREFIX, yautja.getId(), wallHeight, pos(yautja));
    }

    /** What a climb did about something in its way — closed a trapdoor, broke a block, or gave up. */
    public static void onClimbObstacle(Yautja yautja, String action, net.minecraft.core.BlockPos at) {
        if (!enabled) {
            return;
        }

        Predator.LOGGER.info("{}yautja {} | CLIMB {} at={} pos={}", PREFIX, yautja.getId(), action, describe(at), pos(yautja));
    }

    public static void onClimbDetach(Yautja yautja, String reason) {
        if (!enabled) {
            return;
        }

        Predator.LOGGER.info("{}yautja {} | CLIMB detach reason={} pos={}", PREFIX, yautja.getId(), reason, pos(yautja));
    }

    // -----------------------------------------------------------------------------------------------------------
    // Summary
    // -----------------------------------------------------------------------------------------------------------

    /** Called from the yautja's server tick. Returns immediately when off — one boolean test per entity per tick. */
    public static void tick(Yautja yautja) {
        if (!enabled || yautja.level().isClientSide) {
            return;
        }

        // Oct 8 - A DEBUG TOOL MUST NEVER TAKE THE SERVER DOWN. The tester's crash came from here; anything else that
        // throws while reporting is logged once and the report for this tick is skipped, never rethrown.
        try {
            tickReport(yautja);
        } catch (RuntimeException exception) {
            if (!reportFailureLogged) {
                reportFailureLogged = true;
                Predator.LOGGER.warn("[path-debug] report failed and was skipped (logged once): {}", exception.toString());
            }
        }
    }

    /** Oct 8 - set once the first report failure has been logged. */
    private static boolean reportFailureLogged;

    private static void tickReport(Yautja yautja) {
        var state = STATES.computeIfAbsent(yautja, key -> new State());
        var target = yautja.getTarget();

        // The target change is an EVENT, so it is reported the instant it happens rather than waiting for the next
        // summary — churn at tick resolution is invisible on a two-second sample.
        if (target != state.lastTarget) {
            state.changes++;

            Predator.LOGGER.info(
                "{}yautja {} | TARGET -> {} (was {}) d={} los={}",
                PREFIX,
                yautja.getId(),
                describe(target),
                describe(state.lastTarget),
                target == null ? "-" : String.format("%.1f", yautja.distanceTo(target)),
                target == null ? "-" : (yautja.hasLineOfSight(target) ? "Y" : "N")
            );

            state.lastTarget = target;
        }

        var failures = yautja.getPathNavigator().getState().getConsecutiveFailures();

        if (failures > state.lastFailureCount) {
            onPathFailed(yautja, failures);
        }

        state.lastFailureCount = failures;

        if (yautja.tickCount - state.lastSummaryTick < SUMMARY_INTERVAL_TICKS) {
            return;
        }

        state.lastSummaryTick = yautja.tickCount;
        logSummary(yautja, state, target);
    }

    private static void logSummary(Yautja yautja, State state, @Nullable LivingEntity target) {
        var navigator = yautja.getPathNavigator();
        var navState = navigator.getState();
        var path = navState.getCurrentPath();
        var node = navState.getCurrentNode();

        Predator.LOGGER.info(
            "{}yautja {} | tgt={}{} los={} chg={} | goap hasTgt={} melee={} bored={} plan={} "
                + "| nav={} {} node={}/{} {} fails={} pend={} | dest={} | caster={} deploy={} | climb={} air={} | {}",
            PREFIX,
            yautja.getId(),
            describe(target),
            target == null ? "" : String.format("@%.1f", yautja.distanceTo(target)),
            target == null ? "-" : (yautja.hasLineOfSight(target) ? "Y" : "N"),
            state.changes,
            worldFlag(yautja, GOAPSensors.HAS_ATTACK_TARGET.key()),
            worldFlag(yautja, YautjaSensors.IS_TARGET_IN_MELEE_RANGE_KEY),
            worldFlag(yautja, YautjaSensors.IS_BORED_KEY),
            hasPlan(yautja),
            target != null ? "pursuit" : "stalk",
            navState.isNavigating() ? "NAVIGATING" : (navState.isDone() ? "DONE" : "IDLE"),
            path == null ? "-" : path.getCurrentNodeIndex(),
            path == null ? "-" : path.getNodeCount(),
            node == null ? "-" : node.getTerrainType(),
            navState.getConsecutiveFailures(),
            navState.isPathPending() ? "Y" : "N",
            // ⚠ WHERE IT IS TRYING TO GO. Without this a failure line says only that pathing failed, not
            // what it failed to reach — and "walked to the wrong place" and "could not reach the right
            // place" look identical in a log otherwise.
            describe(navState.getTargetPos()),
            yautja.getCasterState(),
            // Whether the honor rule currently says the caster is justified. "caster=STOWED deploy=N" is the
            // answer to "why will it not fire" — the fight does not qualify, not that the weapon is broken.
            PlasmaCaster.shouldDeploy(yautja) ? "Y" : "N",
            yautja.isClimbing() ? "Y" : "N",
            yautja.getAirborneTicks(),
            pos(yautja)
        );
    }

    /**
     * {@return the GOAP world state's own answer for a key, or {@code ?} when it has not been sensed}
     * <p>
     * ⚠ Reads the state the agent already computed rather than re-evaluating the sensor. Re-running it here would both
     * cost a second evaluation and, worse, report a value the graph never actually saw.
     */
    private static String worldFlag(Yautja yautja, StateKey<?> key) {
        var agent = agentOf(yautja);

        if (agent == null) {
            return "?";
        }

        // ⚠⚠ A PLAIN MAP LOOKUP, NOT getOrNull. On a SensingWorldState, getOrNull TRIES TO SENSE a key that is
        // not present — from outside the agent's own sensing pass, where the sensor may not be bound. That
        // logs "no sensor exists for key" and makes the diagnostic produce warnings that look like a fault in
        // the graph it is supposed to be observing. Reading the map reports what the agent actually computed
        // and can never trigger a sense.
        // Oct 8 - FIX (tester crash, "Ticking entity" with path debug on): the agent has no world state until its
        // first sensing pass, and again between plans - this read it unguarded and crashed the server.
        var worldState = agent.getCurrentWorldState();

        if (worldState == null) {
            return "?";
        }

        var value = worldState.getMap().get(key);

        if (value == null) {
            return "?";
        }

        return Boolean.TRUE.equals(value) ? "Y" : "N";
    }

    private static String hasPlan(Yautja yautja) {
        var agent = agentOf(yautja);
        return agent == null ? "?" : (agent.hasPlan() ? "Y" : "N");
    }

    @SuppressWarnings("unchecked")
    private static com.just.ai.goap.@Nullable Agent<Yautja> agentOf(Yautja yautja) {
        var agent = ((GOAPUser<Yautja>) yautja).blib$getGOAPAgentOrNull();
        return agent == null ? null : (com.just.ai.goap.Agent<Yautja>) (Object) agent.getBackingAgent();
    }

    private static String describe(@Nullable net.minecraft.core.BlockPos pos) {
        return pos == null ? "-" : pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    private static String describe(@Nullable LivingEntity entity) {
        if (entity == null) {
            return "none";
        }

        return entity.getType().builtInRegistryHolder().key().location().getPath() + "#" + entity.getId();
    }

    private static String pos(Yautja yautja) {
        return yautja.getBlockX() + "," + yautja.getBlockY() + "," + yautja.getBlockZ();
    }

    private static final class State {

        @Nullable
        private LivingEntity lastTarget;

        private int changes;

        /**
         * ⚠⚠ NEGATIVE ONE INTERVAL, NOT Integer.MIN_VALUE. It was MIN_VALUE so the first summary would fire immediately
         * — except {@code tickCount - Integer.MIN_VALUE} OVERFLOWS back to a large negative, the {@code >= interval}
         * test never passed, and the summary line NEVER PRINTED ONCE. The whole reason the first test log came back
         * with only TARGET and JUMP events and no state at all.
         */
        private int lastSummaryTick = -SUMMARY_INTERVAL_TICKS;

        @Nullable
        private String lastClimbRefusal;

        private int lastFailureCount;
    }
}
