package com.predator.common.gameplay.entity.living.yautja.ai;

import com.predator.common.gameplay.entity.living.yautja.Yautja;
import com.predator.common.gameplay.item.grenade.GrenadeKind;
import com.predator.common.gameplay.item.grenade.YautjaGrenadeItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Oct 8 - BACK OFF AND THROW. [stated] "if it couldn't get to the player why didnt it just try to use a grenade" ...
 * "yes add that".
 * <p>
 * A grenade is only thrown at prey it can SEE, from {@value #MIN_DISTANCE}-ish to {@value #MAX_DISTANCE} blocks
 * (YautjaGrenadeGoal). A yautja standing under the prey's platform has neither, so it never threw. When the prey is out
 * of reach above it and either nothing climbable leads up there or a climb toward it has already failed, and it is
 * carrying a grenade, this picks a spot on its own level, {@value #MIN_DISTANCE}-{@value #MAX_DISTANCE} blocks out from
 * the prey, with a clear line of sight to it - and the chase walks it there. The grenade goal does the rest with its
 * own rules unchanged (safe distance per grenade kind, the cooldown, a Hunter's breaching reserve).
 * <p>
 * Out of grenades, it goes back to trying to climb. One search per yautja per {@value #CACHE_TICKS} ticks.
 */
public final class ThrowStand {

    /** Not closer than this - also keeps it outside the explosive grenades' blast. */
    private static final double MIN_DISTANCE = 10.0;

    /** Inside the grenade goal's 24-block range with room to spare. */
    private static final double MAX_DISTANCE = 18.0;

    private static final int DIRECTIONS = 24;

    private static final int LEVEL_TOLERANCE = 3;

    private static final int CACHE_TICKS = 40;

    /** Standing eye height of a yautja, for the sight check from a candidate spot. */
    private static final double EYE_HEIGHT = 2.6;

    private record Cached(
        @Nullable Vec3 stand,
        BlockPos preyPos,
        int tick
    ) {}

    private static final Map<Yautja, Cached> CACHE = new WeakHashMap<>();

    private ThrowStand() {}

    /** {@return whether it carries any grenade} */
    public static boolean hasGrenade(Yautja yautja) {
        for (var kind : GrenadeKind.values()) {
            if (yautja.getInventory().hasItem(YautjaGrenadeItem.forKind(kind))) {
                return true;
            }
        }

        return false;
    }

    /** {@return a spot to throw from at the prey, or null} */
    public static @Nullable Vec3 find(Yautja yautja, LivingEntity prey) {
        var preyPos = prey.blockPosition();
        var cached = CACHE.get(yautja);

        if (cached != null && yautja.tickCount - cached.tick() < CACHE_TICKS && cached.preyPos().distManhattan(preyPos) <= 3) {
            return cached.stand();
        }

        var stand = search(yautja, prey);
        CACHE.put(yautja, new Cached(stand, preyPos, yautja.tickCount));
        return stand;
    }

    private static @Nullable Vec3 search(Yautja yautja, LivingEntity prey) {
        var level = yautja.level();
        var feetY = yautja.getBlockY();
        var preyEye = prey.getEyePosition();
        Vec3 best = null;
        var bestDistance = Double.MAX_VALUE;

        for (var ring = MIN_DISTANCE; ring <= MAX_DISTANCE; ring += 4.0) {
            for (var step = 0; step < DIRECTIONS; step++) {
                var angle = step * (Math.PI * 2.0 / DIRECTIONS);
                var x = prey.getX() + Math.cos(angle) * ring;
                var z = prey.getZ() + Math.sin(angle) * ring;

                for (var dy = -LEVEL_TOLERANCE; dy <= LEVEL_TOLERANCE; dy++) {
                    var feet = BlockPos.containing(x, feetY + dy, z);

                    if (!ClimbStart.isStandable(level, feet)) {
                        continue;
                    }

                    var here = Vec3.atBottomCenterOf(feet);
                    var distance = here.distanceTo(yautja.position());

                    // The sight check is the expensive part - only for spots that would beat the best so far.
                    if (distance >= bestDistance) {
                        continue;
                    }

                    var eye = here.add(0.0, EYE_HEIGHT, 0.0);
                    var hit = level.clip(new ClipContext(eye, preyEye, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, yautja));

                    if (hit.getType() == HitResult.Type.MISS) {
                        best = here;
                        bestDistance = distance;
                    }
                }
            }
        }

        return best;
    }
}
