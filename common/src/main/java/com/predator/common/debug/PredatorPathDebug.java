package com.predator.common.debug;

import com.blib.api.common.pathfinding.v1.navigator.PathNavigatorApi;
import com.blib.api.common.pathfinding.v1.node.PathPosture;
import com.blib.api.common.pathfinding.v1.path.BLibPath;
import com.blib.api.common.pathfinding.v1.terrain.TerrainType;
import com.predator.common.gameplay.entity.living.yautja.Yautja;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import org.joml.Vector3f;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Draws a BLib path as a particle trail, per watching player.
 * <h2>Why this exists next to BLib's own debug system</h2> BLib already ships a far more capable path debugger —
 * {@code PathSearchDebugRecorder}, open-node and rejection snapshots, edge types, timing phases, a client render
 * channel. It answers "why did the search do that". This answers "where is it going", which is the question you
 * actually have 90% of the time, and it answers it by pointing at the world instead of at a data structure.
 * <h2>⚠ Zero BLib changes</h2> Everything here reads public API: {@code getPathNavigator().getState().getCurrentPath()}
 * hands back the live {@link BLibPath}, and each {@code PathNode} already carries its position, {@link TerrainType} and
 * {@link PathPosture}. So this needs no BLib rebuild, no version bump, and it cannot affect pathing — it only reads.
 * <h2>Reading the colours</h2>
 * <ul>
 * <li><b>Orange flame</b> — ordinary ground node.</li>
 * <li><b>Blue soul flame</b> — the node is crawled, not walked (a gap it has to squeeze through).</li>
 * <li><b>Bubbles</b> — a water node. A vertical run of these is a swim up or down.</li>
 * <li><b>White end rod</b> — the node it is heading to right now. Watch this to see progress, or the lack of it.</li>
 * <li><b>Green</b> — the last node, i.e. where it thinks it is going.</li>
 * </ul>
 * A path that stops short of the green marker, or a green marker somewhere absurd, is the usual tell.
 */
public final class PredatorPathDebug {

    /**
     * ⚠ Concurrent because commands run on the server thread while entity ticks may be dispatched from the same thread
     * but through different call sites, and a debug toy must never be able to throw a ConcurrentModificationException
     * into the tick loop.
     */
    private static final Set<UUID> WATCHERS = ConcurrentHashMap.newKeySet();

    /** How often to redraw. Every tick floods the packet stream for no extra readability. */
    private static final int DRAW_INTERVAL_TICKS = 5;

    /** Blocks. A watcher further away than this from the mob sees nothing, so a busy world stays cheap. */
    private static final double WATCH_RADIUS = 96.0;

    /** Cap on nodes drawn per path, so a 400-node route cannot spike the packet count. */
    private static final int MAX_NODES_DRAWN = 128;

    /** Climb marker colour. Not used by any node type, so it can never be read as part of the route. */
    private static final Vector3f CLIMB_PURPLE = new Vector3f(0.72F, 0.28F, 0.94F);

    private PredatorPathDebug() {
        throw new UnsupportedOperationException();
    }

    public static boolean toggle(ServerPlayer player, boolean enabled) {
        return enabled ? WATCHERS.add(player.getUUID()) : WATCHERS.remove(player.getUUID());
    }

    public static boolean isWatching(ServerPlayer player) {
        return WATCHERS.contains(player.getUUID());
    }

    public static int watcherCount() {
        return WATCHERS.size();
    }

    /**
     * Called from a BLib-navigated mob's server tick. Each mob draws its own path, which means no server-tick event
     * hook and no registry of navigating entities to keep in sync.
     * <p>
     * ⚠ Returns immediately when nobody is watching, and that check is the FIRST thing it does — with the debug off
     * this costs one empty-set test per mob per tick and nothing else.
     */
    public static void draw(Mob mob, PathNavigatorApi navigator) {
        if (WATCHERS.isEmpty() || mob.tickCount % DRAW_INTERVAL_TICKS != 0) {
            return;
        }

        if (!(mob.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        // ⚠⚠ THE CLIMB IS NOT PART OF THE PATH AND NEVER WILL BE, so the trail alone can never show it.
        // BLib's TerrainType is GROUND and WATER; there is no vertical node, and YautjaClimb is a reaction
        // that drives velocity directly outside the planner. A yautja climbing perfectly and one refusing to
        // climb draw the SAME ground-only trail, which makes the trail actively misleading for this feature.
        // Marking the climber itself is the only honest way to show it.
        drawClimbMarker(serverLevel, mob);

        var path = navigator.getState().getCurrentPath();

        if (path == null || path.getNodeCount() == 0) {
            return;
        }

        for (var player : serverLevel.players()) {
            if (!WATCHERS.contains(player.getUUID())) {
                continue;
            }

            if (player.distanceToSqr(mob) > WATCH_RADIUS * WATCH_RADIUS) {
                continue;
            }

            drawFor(serverLevel, player, path);
        }
    }

    /**
     * Purple sparks on a yautja that is climbing, drawn at its actual position each redraw.
     * <p>
     * ⚠ Deliberately a colour nothing else in this debug uses. Orange, blue, bubbles, white and green are all path
     * nodes; this is the one marker that is NOT a node, and it should not be mistakable for one.
     */
    private static void drawClimbMarker(ServerLevel level, Mob mob) {
        if (!(mob instanceof Yautja yautja) || !yautja.isClimbing()) {
            return;
        }

        for (var player : level.players()) {
            if (!WATCHERS.contains(player.getUUID()) || player.distanceToSqr(mob) > WATCH_RADIUS * WATCH_RADIUS) {
                continue;
            }

            level.sendParticles(
                player,
                new DustParticleOptions(CLIMB_PURPLE, 0.6F),
                true,
                mob.getX(),
                mob.getY() + mob.getBbHeight() * 0.5,
                mob.getZ(),
                2,
                0.2,
                0.3,
                0.2,
                0.0
            );
        }
    }

    private static void drawFor(ServerLevel level, ServerPlayer player, BLibPath path) {
        var count = Math.min(path.getNodeCount(), MAX_NODES_DRAWN);
        var current = path.getCurrentNodeIndex();
        var last = path.getNodeCount() - 1;

        for (var index = 0; index < count; index++) {
            var node = path.getNode(index);

            level.sendParticles(
                player,
                particleFor(node.getTerrainType(), node.getPosture(), index == current, index == last),
                true,
                node.getX() + 0.5,
                node.getY() + 0.5,
                node.getZ() + 0.5,
                1,
                0.0,
                0.0,
                0.0,
                0.0
            );
        }
    }

    /**
     * ⚠ Ordering matters. The "heading here" and "destination" markers win over terrain, because when a path is
     * misbehaving those two are what you are looking for; knowing the stuck node is also a water node is secondary.
     */
    private static ParticleOptions particleFor(
        TerrainType terrain,
        PathPosture posture,
        boolean isCurrent,
        boolean isLast
    ) {
        if (isLast) {
            return ParticleTypes.HAPPY_VILLAGER;
        }

        if (isCurrent) {
            return ParticleTypes.END_ROD;
        }

        if (posture == PathPosture.CRAWLING) {
            return ParticleTypes.SOUL_FIRE_FLAME;
        }

        return terrain == TerrainType.WATER ? ParticleTypes.BUBBLE : ParticleTypes.FLAME;
    }
}
