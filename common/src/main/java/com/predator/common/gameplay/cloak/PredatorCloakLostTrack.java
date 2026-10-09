package com.predator.common.gameplay.cloak;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What happens to a mob that was already hunting the wearer when the cloak comes back up.
 * <p>
 * ⚠ The visibility gate alone does NOT cover this. {@code getVisibilityPercent} governs ACQUIRING a target; once a mob
 * has locked on it keeps that target until something clears it. So new pursuers were correctly blind while the ones
 * that saw the melee swing kept tracking perfectly — which is what made the two-second reveal a formality.
 * <h2>The rule</h2> Rather than wiping the target the instant the field returns — which would make the reveal free —
 * each pursuer is given the wearer's LAST KNOWN POSITION and keeps hunting that. It walks to where you were, and only
 * when it arrives and finds nothing does it give up. Break away during the pursuit and it never reaches you; stand
 * still and it walks right into you.
 * <p>
 * Phantoms need no special handling despite scanning only every 60 ticks: their goal acquires through
 * {@code TargetingConditions} like everything else, so the long rescan interval just means a longer tail on an existing
 * lock — exactly what this class handles.
 */
public final class PredatorCloakLostTrack {

    /** How close a pursuer must get to the remembered spot before it accepts the wearer is not there. */
    private static final double ARRIVAL_DISTANCE_SQ = 4.0;

    /** How long a pursuer will keep hunting a remembered spot before giving up regardless. 8 seconds. */
    private static final int MAX_PURSUIT_TICKS = 160;

    /** How far out to look for mobs already locked onto the wearer. */
    private static final double SEARCH_RADIUS = 48.0;

    private static final Map<UUID, Pursuit> PURSUITS = new HashMap<>();

    private PredatorCloakLostTrack() {
        throw new UnsupportedOperationException();
    }

    private record Pursuit(
        BlockPos lastKnown,
        int expiryTick
    ) {}

    /**
     * Called when the wearer's field re-engages. Every mob currently targeting them is handed the position they were
     * last seen at and keeps hunting it.
     */
    public static void onConcealed(LivingEntity wearer) {
        if (!(wearer.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        var lastKnown = wearer.blockPosition();
        var expiry = serverLevel.getServer().getTickCount() + MAX_PURSUIT_TICKS;
        var searchBox = new AABB(wearer.blockPosition()).inflate(SEARCH_RADIUS);

        for (var mob : serverLevel.getEntitiesOfClass(Mob.class, searchBox, m -> m.getTarget() == wearer)) {
            PURSUITS.put(mob.getUUID(), new Pursuit(lastKnown, expiry));
        }
    }

    /**
     * Per-tick sweep over active pursuits only — no cost when nobody is chasing a cloaked wearer.
     * <p>
     * A pursuer drops its target once it reaches the remembered spot, or when the pursuit times out, or if the wearer
     * stops being concealed (at which point ordinary targeting takes over again and this must get out of the way).
     */
    public static void tick(ServerLevel level) {
        if (PURSUITS.isEmpty()) {
            return;
        }

        var now = level.getServer().getTickCount();

        PURSUITS.entrySet().removeIf(entry -> {
            var mob = level.getEntity(entry.getKey());

            if (!(mob instanceof Mob pursuer) || pursuer.isRemoved()) {
                return true;
            }

            var pursuit = entry.getValue();
            var target = pursuer.getTarget();

            // The wearer became visible again, or the mob moved on by itself — let normal targeting resume.
            if (!(target instanceof LivingEntity wearer) || !PredatorCloak.isConcealed(wearer)) {
                return true;
            }

            if (
                now >= pursuit.expiryTick()
                    || pursuer.distanceToSqr(
                        pursuit.lastKnown().getX() + 0.5,
                        pursuit.lastKnown().getY(),
                        pursuit.lastKnown().getZ() + 0.5
                    ) <= ARRIVAL_DISTANCE_SQ
            ) {
                pursuer.setTarget(null);
                return true;
            }

            // Keep walking to the remembered spot rather than to the wearer's live position.
            pursuer.getNavigation()
                .moveTo(
                    pursuit.lastKnown().getX() + 0.5,
                    pursuit.lastKnown().getY(),
                    pursuit.lastKnown().getZ() + 0.5,
                    1.0
                );

            return false;
        });
    }
}
