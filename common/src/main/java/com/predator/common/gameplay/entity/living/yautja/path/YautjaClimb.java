package com.predator.common.gameplay.entity.living.yautja.path;

import com.predator.common.debug.PredatorPathDiagnostics;
import com.predator.common.gameplay.entity.living.yautja.Yautja;
import com.predator.common.gameplay.entity.living.yautja.YautjaMovement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Climbing walls, cliffs and trees.
 * <h2>⚠⚠ Two reasons this could not be built on anything that already exists</h2>
 * <ol>
 * <li><b>BLib cannot path it.</b> None of the pathfinder's 43 features describes a vertical surface. It will route to
 * the foot of a wall and stop, so climbing has to be a reaction on arrival, exactly like {@link YautjaJump}.</li>
 * <li><b>Vanilla's climbable path can only produce ONE speed.</b> {@code LivingEntity.handleOnClimbable} hardcodes the
 * ascent at 0.2 blocks per tick and clamps horizontal movement to 0.15. Since he wants a slow climb AND a fast one, the
 * velocity has to be driven here instead of handing the entity to vanilla's ladder physics.</li>
 * </ol>
 * {@code Yautja.onClimbable()} still returns true while attached, because that is what resets fall distance and stops a
 * climb ending in fall damage — the physics are ours, the bookkeeping is vanilla's.
 * <h2>Which speed</h2> His rule: "slow climb is treated like walking if hes following something or trying to scale a
 * building… if hes chasing something or charging something he would use the fast climb." So the fast climb is exactly
 * "has an attack target", and the two speeds are the ground walk and run speeds, which keeps a scaling hunter legible
 * against one that is coming for you.
 */
public final class YautjaClimb {

    /** Blocks of wall that must stand above the yautja's feet before climbing beats walking around. */
    private static final int MIN_WALL_HEIGHT = 2;

    /** How far up to look for the top of the wall. Beyond this it climbs anyway; it just cannot see the end. */
    private static final int MAX_SCAN_HEIGHT = 24;

    /** Sideways push that keeps it pinned to the surface while ascending. */
    private static final double CLING_PUSH = 0.08;

    /** Upward impulse used to mantle over the lip once the top is reached. */
    private static final double MANTLE_UP = 0.42;

    /** Forward push over the lip, enough to land on the top face rather than slide back down. */
    private static final double MANTLE_FORWARD = 0.34;

    /** Oct 9 - the highest the top of the face may be above its feet for the mantle hop to land, in blocks. */
    private static final double MANTLE_REACH = 1.1;

    /** Ticks of no wall contact before it lets go, so a one-tick gap in a tree trunk does not drop it. */
    private static final int GRACE_TICKS = 4;

    /**
     * Ticks before it may grab again after falling off.
     * <p>
     * ⚠⚠ THIS IS THE ANTI-PING-PONG GUARD. Without it: lose grip, fall, land against the same wall,
     * {@code horizontalCollision} is true again, re-attach, lose grip… a visible stutter against any surface it cannot
     * actually hold. A mantle is a SUCCESSFUL finish and deliberately does not set this, so cresting one lip and
     * immediately starting the next wall still works.
     */
    private static final int REATTACH_COOLDOWN_TICKS = 20;

    /** Blocks the target must be above before climbing is worth considering. */
    private static final double MIN_TARGET_HEIGHT = 3.0;

    /** Blocks. How far away horizontally the target may be and still be worth climbing to. */
    // Oct 8: 8 -> 16. ClimbStart now walks the yautja to the side of a structure that reaches the prey, which can be up
    // to 12 blocks from the prey's column; at 8 it arrived at the right wall and then did not want to climb it.
    private static final double MAX_TARGET_HORIZONTAL = 16.0;

    /** His figure: leaves overhead are climbable if air is within this many blocks above them. */
    private static final int CANOPY_REACH = 3;

    /**
     * Descent is slower than ascent.
     * <p>
     * ⚠ Not for realism — for control. Coming down at full chase speed overshoots the bottom, and a yautja that arrives
     * below its target at speed reads as a fall rather than a climb.
     */
    private static final double DESCENT_SPEED_FRACTION = 0.7;

    /** Ticks the overhang hop runs for. Four at the speeds below carries it roughly 2 up and 1.2 out. */
    private static final int VAULT_TICKS = 4;

    /** Blocks per tick upward during the hop. Enough to clear a one-block lip in the time above. */
    private static final double VAULT_UP = 0.5;

    /** And outward, to get clear of the lip rather than into its underside. */
    private static final double VAULT_OUT = 0.3;

    private YautjaClimb() {
        throw new UnsupportedOperationException();
    }

    /**
     * Called once per server tick, before {@link YautjaJump}.
     * <p>
     * Attaching is deliberately permissive about WHAT the surface is — any solid block face qualifies, which is what
     * makes walls, cliffs and tree trunks all work without a block list to maintain. What it is strict about is whether
     * climbing is worth it: at least {@value #MIN_WALL_HEIGHT} blocks of wall, or the mob would climb every fence post
     * it walked into.
     */
    /**
     * Ticks before a yautja stopped by an immovable roof will try to climb again — long enough that it goes and does
     * something else rather than bouncing up the same wall.
     */
    private static final int ROOF_REFUSAL_COOLDOWN_TICKS = 100;

    /** Rise per tick below which a climber is not making progress. A real climb moves ~0.1+ blocks a tick. */
    private static final double STALL_RISE = 0.02D;

    /** Ticks of no progress before the climb is abandoned. One second. */
    private static final int STALL_TICKS = 20;

    public static void tick(Yautja yautja) {
        if (yautja.level().isClientSide) {
            return;
        }

        if (yautja.isClimbing()) {
            continueClimb(yautja);
            return;
        }

        tryAttach(yautja);
    }

    private static void tryAttach(Yautja yautja) {
        // ⚠⚠ A LADDER OR VINE IS NOT A WALL. On a vanilla climbable the BLib planner routes straight up the column and
        // vanilla's own climbable physics carries the body; the wall-climb must stay out of it. It used to fire here on
        // the collision with the rungs, read the ladder column (or the wall behind it) as something to scale, and then
        // fight the rungs with its own physics — lost grip, grace, regrip, cooldown — which is the stutter he saw.
        // ⚠ EXCEPT under a canopy. A vine or ladder that ends beneath leaves is where his rule "keep going up THROUGH
        // the canopy" takes over: the planner cannot route through leaves, but the wall-climb's canopy ascent can, so
        // it is allowed to engage from the top of a climbable when leaves are overhead and it wants up.
        if (onVanillaClimbable(yautja) && !(wantsToClimbToTarget(yautja) && canAscendThroughCanopy(yautja))) {
            return;
        }

        // Swimming has its own vertical movement; the two must never both drive velocity.
        // ⚠⚠ Oct 5 - UNDER water, not IN it. [stated] "he wasnt able to jump out of the water. i think they should be
        // able
        // to either climb out". Refusing whenever the feet were wet made any pool with walls two blocks high a cell:
        // the
        // climb would not start from it and vanilla's exit hop only clears one block. With the head above the surface
        // there is no swimming to fight - it is standing in water against a wall - so the climb may take it.
        // A mantle out of the water (YautjaWaterExit) owns the velocity while it runs, so the climb waits for it.
        // ⚠ Oct 5 review: and never while NoAI - avp_alien webs captured hosts into a chamber with NoAI, and the water
        // rule above no longer keeps a webbed yautja in a flooded chamber from deciding to climb.
        if (
            yautja.isUnderWater()
                || YautjaWaterExit.isMantling(yautja)
                || yautja.isNoAi()
                || yautja.isPassenger()
                || yautja.tickCount < yautja.getNextClimbAttachTick()
        ) {
            if (wantsToClimbToTarget(yautja)) {
                PredatorPathDiagnostics.onClimbRefused(
                    yautja,
                    yautja.isUnderWater() || YautjaWaterExit.isMantling(yautja)
                        ? "inWater"
                        : yautja.isPassenger() ? "riding" : "reattachCooldown"
                );
            }

            return;
        }

        // ⚠ Airborne contact counts, and that is his running-jump-to-cliff case: "he can do a running jump and grab
        // onto the cliff and climb up". Requiring onGround would have made a leap at a cliff face bounce off it.
        // ⚠⚠ TWO REASONS TO GRAB, AND THE SECOND IS THE POINT OF THIS FEATURE. Bumping into a wall was the only
        // trigger, so a yautja never DECIDED to climb — and it cannot decide by pathfinding either, because
        // none of BLib's features describes a vertical surface: the planner returns NO_PATH for prey up a tree
        // and the yautja mills around the trunk. Wanting to reach something above has to be a reason of its own.
        var wanted = wantsToClimbToTarget(yautja);

        if (!yautja.horizontalCollision && !wanted) {
            return;
        }

        // ⚠⚠ A BUMP ON THE WAY THROUGH A DOORWAY IS NOT A WALL TO CLIMB. The planner routes a 2.48-tall yautja through
        // a
        // 1x2 gap as a CRAWL node and the posture view crouches it 1.5 blocks out — but the wall-climb ran every tick
        // on horizontalCollision, and the lintel or the door frame is a sturdy face two blocks high, so a shoulder
        // brushing the frame while turning in became "attach, climb". [tester] "trouble going into 1x2 holes ...
        // they tend to climb around the wall". A collision only counts as a wall when the navigator is NOT in the
        // middle of following a route: no path, or the next waypoint is well above it (a genuine climb). While
        // crawling, or being told to crawl, it never counts. A target overhead (wanted) still climbs, as before.
        if (!wanted && (yautja.isCrawling() || isFollowingLevelRoute(yautja))) {
            return;
        }

        // From here on it WANTS to climb, so every early return is a reason worth naming in the log.

        var facing = facing(yautja);
        var wall = wallPosition(yautja, facing);

        // ⚠ Nothing in front but it wants to be up there: look for a hold on any side. Walking into a trunk
        // aims the body at it for free; DECIDING to climb one does not.
        if (wall == null) {
            var hold = findAdjacentHold(yautja);

            if (hold == null) {
                if (wanted) {
                    PredatorPathDiagnostics.onClimbRefused(yautja, "noHoldWithinReach");
                }

                return;
            }

            // Turn to face it, so the cling push drives INTO the trunk rather than past it.
            yautja.setYRot(hold.toYRot());
            yautja.yBodyRot = hold.toYRot();

            facing = facing(yautja);
            wall = wallPosition(yautja, facing);
        }

        if (wall == null || wallHeightAbove(yautja, wall) < MIN_WALL_HEIGHT) {
            if (wanted) {
                PredatorPathDiagnostics.onClimbRefused(
                    yautja,
                    wall == null ? "noWallInFront" : "wallTooShort"
                );
            }

            return;
        }

        yautja.setClimbing(true);

        // ⚠ A fresh climb starts with a fresh stall count, or leftovers from the last one could cut it short.
        yautja.setClimbStallTicks(0);
        PredatorPathDiagnostics.clearClimbRefusal(yautja);
        PredatorPathDiagnostics.onClimbAttach(yautja, wallHeightAbove(yautja, wall));
    }

    private static void continueClimb(Yautja yautja) {
        // 🚨🚨 PREY BELOW MEANS GET DOWN. Nothing used to end a climb because the target had dropped beneath it —
        // isTargetBelow only stopped it MANTLING over the top — so a yautja that had gone up the wall kept climbing
        // UP, away from what it was hunting. [stated] "it stayed on the wall didnt jump down to attack the skeleton."
        // It lets go now, and the pathing takes it from the ground.
        // ⚠ The drop is REAL fall damage, counted from where it lets go (every climbing tick zeroes fallDistance). From
        // an ordinary wall that is a couple of hearts against 130+ health; from a very tall cliff it would genuinely
        // hurt. That is left as-is deliberately rather than granting fall immunity nobody asked for.
        // ⚠ A vault in progress is allowed to finish — letting go mid-lunge over a lip would drop it off the edge.
        if (isTargetBelow(yautja) && yautja.getClimbVaultTicks() <= 0 && !wantsToClimbToTarget(yautja)) {
            yautja.setClimbing(false);
            yautja.setClimbHanging(false);
            yautja.setClimbStallTicks(0);
            yautja.setClimbAttachCooldown(REATTACH_COOLDOWN_TICKS);
            PredatorPathDiagnostics.onClimbDetach(yautja, "preyBelow");

            return;
        }

        // 🚨🚨 THE BACKSTOP, AND IT MUST SIT HERE — ABOVE THE VAULT. [stated, correcting me] "those were glass blocks",
        // not panes. A glass roof ONE block thick with air above it reads as a one-block LIP, so the climb tried to
        // VAULT over it — swing out and up onto the far side. Inside a box there is no far side: the vault failed,
        // expired, and was tried again, forever. The vault returns early, so a backstop placed below it (where the
        // earlier cut put it, and which also skipped vault ticks on purpose) never ran. Measuring rise here, before
        // anything can return, catches a failed vault the same as a failed climb: a second of pushing upward without
        // gaining height ends it, whatever the ceiling is made of and whatever the climb was trying to do.
        // ⚠ A real vault RISES as it lunges, so a successful one resets this almost every tick; only a vault into
        // something solid sits still long enough to trip it.
        if (yautja.getY() - yautja.yo < STALL_RISE) {
            yautja.setClimbStallTicks(yautja.getClimbStallTicks() + 1);

            if (yautja.getClimbStallTicks() > STALL_TICKS) {
                yautja.setClimbStallTicks(0);
                yautja.setClimbVaultTicks(0);
                yautja.setClimbing(false);
                yautja.setClimbHanging(false);
                yautja.setClimbAttachCooldown(ROOF_REFUSAL_COOLDOWN_TICKS);
                PredatorPathDiagnostics.onClimbDetach(yautja, "stalled");
                // Oct 8: a stall is a failed climb - two toward the same prey start a siege (YautjaSiege).
                com.predator.common.gameplay.entity.living.yautja.YautjaSiege.noteClimbFailure(yautja);

                return;
            }
        } else {
            yautja.setClimbStallTicks(0);
        }

        // ⚠⚠ THE VAULT OWNS THE BODY WHILE IT RUNS, AND THAT IS THE WHOLE TRICK. Mid-hop the yautja is out in
        // open air beside the lip with no wall in front of it, so every ordinary check — wall present, head
        // clear, at the top — would fire and drop it. Suspending them for the duration is what turns a fall
        // into a hop.
        if (yautja.getClimbVaultTicks() > 0) {
            yautja.setClimbVaultTicks(yautja.getClimbVaultTicks() - 1);
            yautja.setClimbLostContactTicks(0);
            yautja.fallDistance = 0.0F;
            yautja.hasImpulse = true;

            return;
        }

        // ⚠ Oct 5 - under water, not merely wet: a climb that starts in a pool has its feet in the water for the first
        // few ticks, and letting go then made climbing out impossible. Sinking back under still ends it.
        if (yautja.isUnderWater()) {
            yautja.setClimbing(false);
            yautja.setClimbAttachCooldown(REATTACH_COOLDOWN_TICKS);
            return;
        }

        var facing = facing(yautja);
        var wall = wallPosition(yautja, facing);

        if (wall == null) {
            // ⚠⚠ LOOK AROUND THE TRUNK BEFORE LETTING GO. The log showed the whole failure: attach with a
            // 7-block wall, climb one or two blocks, lostContact, drop, reattachCooldown, retry — over and
            // over on the same tree. An acacia trunk leans, so the FACE being held simply ends a couple of
            // blocks up even though the tree continues. Checking only the face in front turned that into a
            // fall instead of a step sideways.
            var regrip = findAdjacentHold(yautja);

            if (regrip != null) {
                yautja.setYRot(regrip.toYRot());
                yautja.yBodyRot = regrip.toYRot();
                yautja.setClimbLostContactTicks(0);

                ascend(yautja, facing(yautja));

                return;
            }

            // ⚠⚠ HIS RULE: keep going up THROUGH the canopy. A trunk ends at the branches, so a yautja that only
            // climbs solid faces stops under the leaves and hangs there. If leaves are overhead and there is open
            // air within CANOPY_REACH above them, the leaves ARE the route.
            //
            // ⚠ It can physically do this already: MixinBlockBehaviour_YautjaIgnoreLeaves returns an empty collision
            // shape for a yautja unless it is ABOVE the block, so pushing UP into leaves passes straight through
            // while standing on top of them still works.
            if (canAscendThroughCanopy(yautja)) {
                ascend(yautja, facing);

                return;
            }

            // Nothing to hold and no canopy overhead. Grace period so a gap between logs does not drop it.
            yautja.setClimbLostContactTicks(yautja.getClimbLostContactTicks() + 1);

            if (yautja.getClimbLostContactTicks() > GRACE_TICKS) {
                // Oct 9 - before letting go where the face simply ENDED under a ledge, try the shimmy: a hanging
                // spot along the wall with open air above may still lead up (the same search the roof case uses).
                if (traverseToClearColumn(yautja, facing(yautja))) {
                    yautja.setClimbLostContactTicks(0);
                    return;
                }

                yautja.setClimbing(false);
                yautja.setClimbVaultTicks(0);
                yautja.setClimbAttachCooldown(REATTACH_COOLDOWN_TICKS);
                PredatorPathDiagnostics.onClimbDetach(yautja, "lostContact");

                // Oct 9 - well up a face with its prey still above, running out of wall is a failed climb, not a slip:
                // it counts toward the siege / grenade spot rather than starting the same climb again.
                if (
                    yautja.getTarget() != null && yautja.getTarget().getY() - yautja.getY() >= MIN_TARGET_HEIGHT * 0.5
                        && !yautja.onGround()
                ) {
                    com.predator.common.gameplay.entity.living.yautja.YautjaSiege.noteClimbFailure(yautja);
                }
            }

            return;
        }

        yautja.setClimbLostContactTicks(0);

        // ⚠ Only when going UP. Mantling while descending would haul it back onto the surface it is climbing off.
        if (!isTargetBelow(yautja) && isAtTop(yautja, wall)) {
            mantle(yautja, facing);
            return;
        }

        // ⚠ A BRANCH OVERHEAD IS A REASON TO GO ROUND, NOT TO STOP. Still holding a good face but with solid
        // wood directly above is the acacia case he photographed: two sides of the trunk work and two are
        // blocked by a limb. Rotating to a side with clear air continues the climb; without it the yautja
        // grinds into the branch until it loses grip.
        // ⚠ Hanging is set whenever a lip is overhead and cleared the moment it is not, so the pose and the
        // hitbox follow the geometry rather than needing to be unwound by whatever changes it next.
        yautja.setClimbHanging(isHeadBlocked(yautja) && isOneBlockOverhang(yautja));

        if (isHeadBlocked(yautja) && !canAscendThroughCanopy(yautja)) {
            var around = findHoldWithClearSpaceAbove(yautja);

            if (around != null) {
                yautja.setYRot(around.toYRot());
                yautja.yBodyRot = around.toYRot();

                ascend(yautja, facing(yautja));

                return;
            }

            // ⚠ A ONE-BLOCK LIP IS A HOP, NOT A WALL. His rule: solid overhead with open air above THAT means
            // it swings out and up onto the far face and carries on. Two blocks of overhang is a genuine
            // stop — which is what keeps a roof overhang defensive instead of decorative.
            //
            // ⚠ Tried BEFORE breaking, deliberately: going over a ledge leaves the world intact, and a
            // yautja that vaults a lip reads far better than one that chews a hole through it.
            // 🚨🚨 BREAK A WEAK CEILING FIRST, THEN VAULT, THEN GIVE UP. [stated] "Its supposed to break easy to break
            // blocks ... I boxed it in a box of Blackstone and glass and it didnt break the walls or the ceiling to get
            // at prey or escape." The order used to be vault, THEN break — and a one-block-thick glass roof reads as a
            // LIP, so it vaulted, the vault failed against the roof, the stall backstop fired and it let go, never once
            // reaching the break. Glass, planks, wool and the rest are now smashed on contact; the vault is kept for a
            // real lip it cannot break (stone, say), and only a roof that is neither breakable nor vaultable ends it.
            if (clearOverhead(yautja)) {
                return;
            }

            // Oct 8 - ⚠ ONLY HOP A LIP THE HOP CAN CLEAR. The check above is the lip's THICKNESS; a platform one
            // block thick that juts out two or more blocks passed it, and the hop (about 1.2 out, 2 up) slammed into
            // its underside, fell and tried again until shot (tester video). The block one step further out over the
            // yautja's head must be open for the hop to come down on the far side.
            if (isOneBlockOverhang(yautja) && hopClearsLip(yautja, facing)) {
                startVault(yautja, facing);

                return;
            }

            // Oct 8 - GO AROUND BEFORE GIVING UP. On a narrow pillar the face that runs clear to the top is round the
            // corner, not beside the yautja, so findHoldWithClearSpaceAbove never saw it; along a wider wall it may be
            // several blocks over. Shimmy along the wall - round outside corners and into inside ones - to the
            // nearest hanging spot with open air above, and climb on from there.
            if (traverseToClearColumn(yautja, facing)) {
                return;
            }

            yautja.setClimbing(false);
            yautja.setClimbVaultTicks(0);
            yautja.setClimbHanging(false);
            yautja.setClimbAttachCooldown(ROOF_REFUSAL_COOLDOWN_TICKS);
            PredatorPathDiagnostics.onClimbDetach(yautja, "roofBlocked");
            com.predator.common.gameplay.entity.living.yautja.YautjaSiege.noteClimbFailure(yautja);

            return;
        }

        ascend(yautja, facing);
    }

    /** One tick of upward movement, hugging whatever it is holding. */
    private static void ascend(Yautja yautja, Vec3 facing) {
        yautja.setDeltaMovement(
            facing.x * CLING_PUSH,
            climbSpeed(yautja),
            facing.z * CLING_PUSH
        );

        yautja.hasImpulse = true;
        yautja.fallDistance = 0.0F;
    }

    /** Over the lip and back onto its feet. */
                                               /**
                                                * Oct 9 - where and when each yautja last mantled, and how many times
                                                * there in a row.
                                                */
    private record MantleTry(
        BlockPos pos,
        long tick,
        int count
    ) {}

    private static final java.util.Map<Yautja, MantleTry> MANTLE_TRIES = new java.util.WeakHashMap<>();

    /** Oct 9 - mantles at the same spot within this many ticks count as one repeating attempt. */
    private static final long MANTLE_REPEAT_TICKS = 200L;

    private static void mantle(Yautja yautja, Vec3 facing) {
        // Oct 9 - A MANTLE THAT KEEPS NOT LANDING IS A FAILED CLIMB. Mantling again within 10 s at (nearly) the same
        // spot means the last one did not put it on top. The second repeat records a climb failure - which is what
        // starts the siege and sends it to a grenade spot - and holds it off the wall for a while, instead of hopping
        // at the same top forever.
        var now = yautja.level().getGameTime();
        var here = yautja.blockPosition();
        var last = MANTLE_TRIES.get(yautja);
        var count = last != null && now - last.tick() <= MANTLE_REPEAT_TICKS && last.pos().closerThan(here, 2.5)
            ? last.count() + 1
            : 1;
        MANTLE_TRIES.put(yautja, new MantleTry(here, now, count));

        if (count >= 3) {
            MANTLE_TRIES.remove(yautja);
            yautja.setClimbing(false);
            yautja.setClimbHanging(false);
            yautja.setClimbAttachCooldown(ROOF_REFUSAL_COOLDOWN_TICKS);
            PredatorPathDiagnostics.onClimbDetach(yautja, "mantleFailing");
            com.predator.common.gameplay.entity.living.yautja.YautjaSiege.noteClimbFailure(yautja);
            return;
        }

        yautja.setDeltaMovement(facing.x * MANTLE_FORWARD, MANTLE_UP, facing.z * MANTLE_FORWARD);
        yautja.hasImpulse = true;
        yautja.setClimbing(false);
        // Oct 8 - FIX (tester: "got stuck on the other side of the platform with no attempt further to repathfind").
        // The chase path was planned from the ground; once on top it is useless. Ask for a fresh one now.
        yautja.getPathNavigator().requestReplan();
        com.predator.common.gameplay.entity.living.yautja.YautjaSounds.climb(yautja);
        PredatorPathDiagnostics.onClimbDetach(yautja, "mantle");
    }

    /**
     * {@return blocks per tick to ascend at}
     * <p>
     * ⚠ Real blocks per tick, via {@link YautjaMovement}. The movement-speed attribute is scaled by 3.15 in this
     * ecosystem and is NOT a distance — using it raw here would climb three times too fast.
     */
    private static double climbSpeed(Yautja yautja) {
        var attribute = yautja.getAttributeValue(Attributes.MOVEMENT_SPEED);

        var speed = yautja.getTarget() != null
            ? YautjaMovement.chaseBlocksPerTick(attribute)
            : YautjaMovement.walkBlocksPerTick(attribute);

        // ⚠⚠ DOWN IS A DIRECTION TOO. "if a predator can climb up they also should be able to climb down" —
        // and the alternative is worse than looking silly: a yautja that can only ascend reaches a canopy,
        // loses its target, and steps off. Descending is the difference between a hunter leaving a tree and a
        // hunter falling out of one.
        return isTargetBelow(yautja) ? -speed * DESCENT_SPEED_FRACTION : speed;
    }

    /**
     * {@return whether whatever it is chasing is meaningfully below it}
     * <p>
     * ⚠ With no target it climbs UP by default. A yautja with nothing to chase that started descending would ride the
     * wall back to the ground the moment it grabbed one.
     */
    private static boolean isTargetBelow(Yautja yautja) {
        var target = yautja.getTarget();

        return target != null && yautja.getY() - target.getY() >= MIN_TARGET_HEIGHT;
    }

    /**
     * {@return whether the yautja wants to be somewhere above it}
     * <p>
     * ⚠ Deliberately generous about the horizontal distance and strict about the vertical. Prey two blocks up a slope
     * is a walk; prey {@value #MIN_TARGET_HEIGHT} blocks up is something the pathfinder cannot solve, and that is the
     * only case worth climbing for.
     */
    private static boolean wantsToClimbToTarget(Yautja yautja) {
        var target = yautja.getTarget();

        if (target == null || !target.isAlive()) {
            return false;
        }

        if (target.getY() - yautja.getY() < MIN_TARGET_HEIGHT) {
            return false;
        }

        var dx = target.getX() - yautja.getX();
        var dz = target.getZ() - yautja.getZ();

        return dx * dx + dz * dz <= MAX_TARGET_HORIZONTAL * MAX_TARGET_HORIZONTAL;
    }

    /**
     * {@return a horizontal direction with something grippable in it, or null}
     * <p>
     * ⚠ Uses the same {@code isFaceSturdy} test as {@link #wallPosition}, so a trunk qualifies and a fence post or
     * trapdoor does not. Checking all four sides matters — a yautja deciding to climb has no reason to already be
     * pointing at the tree.
     */
    private static @Nullable Direction findAdjacentHold(Yautja yautja) {
        for (var direction : Direction.Plane.HORIZONTAL) {
            var toward = new Vec3(direction.getStepX(), 0.0, direction.getStepZ());
            var candidate = wallPosition(yautja, toward);

            if (candidate != null && wallHeightAbove(yautja, candidate) >= MIN_WALL_HEIGHT) {
                return direction;
            }
        }

        return null;
    }

    /**
     * {@return whether leaves overhead can be climbed through to open air}
     * <p>
     * His rule exactly: the block above is a leaf, and within {@value #CANOPY_REACH} of that there is air. The air
     * check is what stops it burrowing endlessly upward into a solid mass of foliage — it climbs THROUGH a canopy, not
     * INTO one.
     */
    private static boolean canAscendThroughCanopy(Yautja yautja) {
        var level = yautja.level();
        var head = BlockPos.containing(yautja.getX(), yautja.getY() + Yautja.STANDING_HEIGHT, yautja.getZ());

        if (!level.getBlockState(head).is(BlockTags.LEAVES)) {
            return false;
        }

        for (var offset = 1; offset <= CANOPY_REACH; offset++) {
            if (level.getBlockState(head.above(offset)).isAir()) {
                return true;
            }
        }

        return false;
    }

    /** {@return the unit vector the body is facing, flattened} */
    private static Vec3 facing(Yautja yautja) {
        var radians = yautja.yBodyRot * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(radians), 0.0, Mth.cos(radians));
    }

    /**
     * {@return the block being held onto, or null if there is no surface in front}
     * <p>
     * Checked at chest height rather than at the feet: a yautja standing on a slab or a root would otherwise read the
     * step it can simply walk up as a wall.
     */
    /**
     * {@return whether the navigator is actively following a route whose next waypoint is not a climb} That is: it has
     * a path, is not done, and either the posture view is calling for a crawl or the target centre sits within a block
     * above the feet. A collision in that state is a doorway edge, a corner or another mob — the route is the answer,
     * not the wall.
     */
    private static boolean isFollowingLevelRoute(Yautja yautja) {
        var navigator = yautja.getPathNavigator();
        var state = navigator.getState();

        if (!state.isNavigating() || state.isDone()) {
            return false;
        }

        if (navigator.getPostureView().shouldCrawl()) {
            return true;
        }

        var target = state.getCurrentTargetCenter();

        return target != null && target.y - yautja.getY() < 1.0;
    }

    /** {@return whether the yautja stands in, or is pressing into, a block in {@code minecraft:climbable}} */
    private static boolean onVanillaClimbable(Yautja yautja) {
        var level = yautja.level();

        if (level.getBlockState(yautja.blockPosition()).is(BlockTags.CLIMBABLE)) {
            return true;
        }

        var facing = facing(yautja);
        var ahead = BlockPos.containing(yautja.getX() + facing.x, yautja.getY() + 0.5, yautja.getZ() + facing.z);

        return level.getBlockState(ahead).is(BlockTags.CLIMBABLE);
    }

    private static BlockPos wallPosition(Yautja yautja, Vec3 facing) {
        var chest = BlockPos.containing(
            yautja.getX() + facing.x,
            yautja.getY() + Yautja.STANDING_HEIGHT * 0.5,
            yautja.getZ() + facing.z
        );

        return isGrippable(yautja, chest, facing) ? chest : null;
    }

    /**
     * {@return whether this block presents a face solid enough to hang off}
     * <p>
     * ⚠⚠ {@code isFaceSturdy}, NOT {@code isSolid}. That distinction is the difference between climbing and the bug he
     * described from earlier attempts — mobs snagging on the sides of trapdoors. A trapdoor, a fence post, a pane, a
     * wall segment: all answer TRUE to {@code isSolid} and none of them is something a body can rest against. BLib
     * makes the same distinction internally, using {@code isCollisionShapeFullBlock} for support and reserving
     * {@code isSolid} for "is this an obstruction".
     * <p>
     * The face tested is the one pointing back AT the yautja, so a block that is sturdy on top but not on its side (a
     * slab, a stair) correctly refuses a grip.
     */
    private static boolean isGrippable(Yautja yautja, BlockPos pos, Vec3 facing) {
        var level = yautja.level();
        var towardYautja = Direction.getNearest(-facing.x, 0.0, -facing.z);

        return level.getBlockState(pos).isFaceSturdy(level, pos, towardYautja);
    }

    /** {@return how many solid blocks continue upward from the wall block} */
    private static int wallHeightAbove(Yautja yautja, BlockPos wall) {
        var height = 0;

        for (var offset = 0; offset < MAX_SCAN_HEIGHT; offset++) {
            if (!yautja.level().getBlockState(wall.above(offset)).isCollisionShapeFullBlock(yautja.level(), wall.above(offset))) {
                break;
            }

            height++;
        }

        return height;
    }

    /**
     * Deals with a block directly overhead that is stopping the climb.
     * <p>
     * ⚠ ORDER MATTERS: close, then break, then give up. An OPEN trapdoor in a wall is the classic climb-blocker and
     * shutting it costs nothing and destroys nothing.
     * <p>
     * ⚠ Breaking uses {@code YautjaPathing.canBreak} — the SAME rule the pathfinder uses — so the climb can never smash
     * something the walk would have routed around. Doors, trapdoors and fence gates are excluded there, which is why
     * closing exists as a separate step rather than falling out of the break rule.
     */
    /** {@return whether the way up is now open} ⚠ False means a roof it cannot shift. */
    private static boolean clearOverhead(Yautja yautja) {
        var level = yautja.level();
        var cleared = false;

        // Oct 8: every block the head would hit, not only the one above the centre (see isHeadBlocked).
        for (var head : headCells(yautja)) {
            var state = level.getBlockState(head);

            // An open trapdoor above: shut it and stand on it.
            if (state.hasProperty(BlockStateProperties.OPEN) && state.getValue(BlockStateProperties.OPEN)) {
                level.setBlockAndUpdate(head, state.setValue(BlockStateProperties.OPEN, false));
                PredatorPathDiagnostics.onClimbObstacle(yautja, "closedTrapdoor", head);
                cleared = true;
                continue;
            }

            // ⚠ THE SAME RULE THE PATHFINDER USES — hardness window plus blacklist — so a yautja never breaks through
            // a ceiling it would refuse to walk through, or the other way round. And never when mobGriefing is off.
            if (
                level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING)
                    && YautjaPathing.canBreak(state)
            ) {
                level.destroyBlock(head, false);
                PredatorPathDiagnostics.onClimbObstacle(yautja, "brokeThrough", head);
                cleared = true;
            }
        }

        // Anything left that it may not break falls through to the hop and the traverse on the next pass.
        if (!cleared) {
            PredatorPathDiagnostics.onClimbRefused(yautja, "overheadBlocked");
        }

        return cleared;
    }

    /**
     * {@return whether the thing overhead is exactly one block thick with open air above it}
     * <p>
     * ⚠ Checks the column above the yautja itself, not above the wall. The lip that stops a climb is the one jutting
     * out OVER the climber — a ledge, a roof edge, a mountain step.
     */
    private static boolean isOneBlockOverhang(Yautja yautja) {
        var level = yautja.level();
        var head = BlockPos.containing(yautja.getX(), yautja.getY() + Yautja.STANDING_HEIGHT, yautja.getZ());
        var above = head.above();

        if (!level.getBlockState(head).isCollisionShapeFullBlock(level, head)) {
            return false;
        }

        // Two blocks of overhang is a hard stop, per his rule.
        return !level.getBlockState(above).isCollisionShapeFullBlock(level, above);
    }

    /**
     * Oct 8 - {@return whether the hop's landing side is open} - the cell one step further out from the wall, at the
     * height of the lip over the yautja's head, must not be solid, or the lip is deeper than the hop can carry it.
     */
    private static boolean hopClearsLip(Yautja yautja, Vec3 facing) {
        var level = yautja.level();
        var outward = Direction.getNearest(-facing.x, 0.0, -facing.z);
        var lipOut = BlockPos.containing(yautja.getX(), yautja.getY() + Yautja.STANDING_HEIGHT, yautja.getZ())
            .relative(outward);

        return level.getBlockState(lipOut).getCollisionShape(level, lipOut).isEmpty();
    }

    /** Oct 8 - steps of a shimmy along a wall before it gives up and the climb ends. */
    private static final int MAX_TRAVERSE_STEPS = 8;

    /** Oct 8 - spots examined per search, so a huge wall can never cost much. */
    private static final int MAX_TRAVERSE_NODES = 96;

    /** Oct 8 - ticks between shimmy steps, so it reads as moving hand over hand, not sliding. */
    private static final int TRAVERSE_STEP_TICKS = 3;

    /** Oct 8 - the tick of each yautja's last shimmy step (server thread; dies with the yautja). */
    private static final java.util.Map<Yautja, Integer> LAST_TRAVERSE_STEP = new java.util.WeakHashMap<>();

    /**
     * Oct 8 - SHIMMY ALONG THE WALL TO A WAY UP. {@return true while it is moving}
     * <p>
     * A breadth-first search over hanging spots: a cell where the standing body fits with a grippable face beside it at
     * chest height. Neighbours are the four cells beside it (along a wall, or turning into an inside corner) and the
     * four diagonals when the body fits through the corner between them (round an outside corner - the pillar case).
     * The goal is the nearest spot with open air over its head. One step is taken every few ticks, then the next tick
     * searches again from where it is, so it always heads for the nearest way up from its CURRENT spot and needs no
     * remembered path. Bounded by {@link #MAX_TRAVERSE_STEPS} steps and {@link #MAX_TRAVERSE_NODES} spots.
     */
    private static boolean traverseToClearColumn(Yautja yautja, Vec3 facing) {
        var level = yautja.level();
        var start = BlockPos.containing(yautja.getX(), yautja.getY(), yautja.getZ());
        var parents = new java.util.HashMap<BlockPos, BlockPos>();
        var depth = new java.util.HashMap<BlockPos, Integer>();
        var queue = new java.util.ArrayDeque<BlockPos>();
        BlockPos goal = null;

        parents.put(start, start);
        depth.put(start, 0);
        queue.add(start);

        while (!queue.isEmpty() && parents.size() < MAX_TRAVERSE_NODES) {
            var current = queue.poll();
            var currentDepth = depth.get(current);

            if (current != start && hasOpenAirOverhead(yautja, current)) {
                goal = current;
                break;
            }

            if (currentDepth >= MAX_TRAVERSE_STEPS) {
                continue;
            }

            for (var next : hangNeighbours(yautja, current)) {
                if (parents.putIfAbsent(next, current) == null) {
                    depth.put(next, currentDepth + 1);
                    queue.add(next);
                }
            }
        }

        if (goal == null) {
            return false;
        }

        var last = LAST_TRAVERSE_STEP.get(yautja);

        // Keep the stall backstop quiet while it shimmies - no height is gained on purpose.
        yautja.setClimbStallTicks(0);
        yautja.fallDistance = 0.0F;

        if (last != null && yautja.tickCount - last < TRAVERSE_STEP_TICKS) {
            yautja.setDeltaMovement(Vec3.ZERO);
            return true;
        }

        var step = goal;

        while (!parents.get(step).equals(start)) {
            step = parents.get(step);
        }

        var wallSide = grippableSide(yautja, step, Direction.getNearest(facing.x, 0.0, facing.z));

        if (wallSide == null) {
            return false;
        }

        yautja.setPos(step.getX() + 0.5, yautja.getY(), step.getZ() + 0.5);
        yautja.setYRot(wallSide.toYRot());
        yautja.yBodyRot = wallSide.toYRot();
        yautja.setDeltaMovement(Vec3.ZERO);
        LAST_TRAVERSE_STEP.put(yautja, yautja.tickCount);
        PredatorPathDiagnostics.onClimbObstacle(yautja, "traversed", step);

        return true;
    }

    /** {@return the hanging spots one shimmy step from this one} */
    private static java.util.List<BlockPos> hangNeighbours(Yautja yautja, BlockPos from) {
        var result = new java.util.ArrayList<BlockPos>(8);

        for (var direction : Direction.Plane.HORIZONTAL) {
            var beside = from.relative(direction);

            if (isHangSpot(yautja, beside)) {
                result.add(beside);
            }

            // Round an outside corner: the diagonal, through whichever side cell the body fits in.
            var diagonal = beside.relative(direction.getClockWise());

            if (
                isHangSpot(yautja, diagonal)
                    && (isBodySpaceClear(yautja, beside) || isBodySpaceClear(yautja, from.relative(direction.getClockWise())))
            ) {
                result.add(diagonal);
            }
        }

        return result;
    }

    /** {@return whether the standing body fits here with a grippable face beside it at chest height} */
    private static boolean isHangSpot(Yautja yautja, BlockPos feet) {
        return isBodySpaceClear(yautja, feet) && grippableSide(yautja, feet, null) != null;
    }

    /**
     * {@return a side of this spot with a grippable face at chest height - the preferred one if it qualifies - or null}
     */
    private static @Nullable Direction grippableSide(Yautja yautja, BlockPos feet, @Nullable Direction preferred) {
        var level = yautja.level();
        var chest = feet.above((int) (Yautja.STANDING_HEIGHT * 0.5));

        if (preferred != null) {
            var wall = chest.relative(preferred);

            if (level.getBlockState(wall).isFaceSturdy(level, wall, preferred.getOpposite())) {
                return preferred;
            }
        }

        for (var direction : Direction.Plane.HORIZONTAL) {
            var wall = chest.relative(direction);

            if (level.getBlockState(wall).isFaceSturdy(level, wall, direction.getOpposite())) {
                return direction;
            }
        }

        return null;
    }

    /** {@return whether the cell over the standing head at this spot is open - the climb can carry on up} */
    private static boolean hasOpenAirOverhead(Yautja yautja, BlockPos feet) {
        var level = yautja.level();
        var aboveHead = feet.above(Mth.ceil(Yautja.STANDING_HEIGHT));

        return level.getBlockState(aboveHead).getCollisionShape(level, aboveHead).isEmpty();
    }

    /** {@return whether the yautja's standing body fits in the column starting at feet} */
    private static boolean isBodySpaceClear(Yautja yautja, BlockPos feet) {
        var level = yautja.level();

        for (var dy = 0; dy < Mth.ceil(Yautja.STANDING_HEIGHT); dy++) {
            var cell = feet.above(dy);

            if (!level.getBlockState(cell).getCollisionShape(level, cell).isEmpty()) {
                return false;
            }
        }

        return true;
    }

    /**
     * Launches the hop: out from the wall to clear the lip, and up to land level with its top face.
     * <p>
     * ⚠ Outward as well as upward. Straight up is blocked by the very block being cleared, so the yautja has to swing
     * away from the surface first — which is why the vault suspends the wall checks rather than trying to keep hold of
     * something.
     * <p>
     * ⚠ When it lands, the ordinary re-grip in {@link #continueClimb} finds whatever face is now beside it. Nothing
     * here has to know what it will grab, which is why this works on a ledge, a roof edge or a mountain step alike.
     */
    private static void startVault(Yautja yautja, Vec3 facing) {
        yautja.setClimbVaultTicks(VAULT_TICKS);
        yautja.setDeltaMovement(-facing.x * VAULT_OUT, VAULT_UP, -facing.z * VAULT_OUT);
        yautja.hasImpulse = true;
        yautja.fallDistance = 0.0F;
        com.predator.common.gameplay.entity.living.yautja.YautjaSounds.climb(yautja);

        PredatorPathDiagnostics.onClimbObstacle(
            yautja,
            "vaultedOverhang",
            BlockPos.containing(yautja.getX(), yautja.getY() + Yautja.STANDING_HEIGHT, yautja.getZ())
        );
    }

    /** {@return whether solid wood or stone sits directly over the yautja's head} */
    private static boolean isHeadBlocked(Yautja yautja) {
        // Oct 8 - THE WHOLE HEAD, NOT ONE COLUMN. This tested only the block above the yautja's CENTRE. Hugging a
        // wall, its body overlaps the next column too, so a lip jutting over that edge stopped it dead while this
        // said "clear": it pushed up into the lip, stalled, let go and tried again (tester video). Every block the
        // hitbox would move into counts now.
        return !headCells(yautja).isEmpty();
    }

    /**
     * Oct 8 - {@return every block with collision in the half-block just above the yautja's standing head, across its
     * full width} - what an upward move would actually hit.
     */
    private static java.util.List<BlockPos> headCells(Yautja yautja) {
        var level = yautja.level();
        var half = yautja.getBbWidth() / 2.0 - 1.0E-4;
        var top = yautja.getY() + Yautja.STANDING_HEIGHT;
        var cells = new java.util.ArrayList<BlockPos>(4);

        for (
            var pos : BlockPos.betweenClosed(
                Mth.floor(yautja.getX() - half),
                Mth.floor(top),
                Mth.floor(yautja.getZ() - half),
                Mth.floor(yautja.getX() + half),
                Mth.floor(top + 0.5),
                Mth.floor(yautja.getZ() + half)
            )
        ) {
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                cells.add(pos.immutable());
            }
        }

        return cells;
    }

    /**
     * {@return a side of the trunk that is grippable AND has clear air above it, or null}
     * <p>
     * ⚠ Both conditions matter. A hold with a branch over it is where the yautja already is, so returning it would only
     * rotate the mob on the spot — the point is a side it can actually continue up.
     */
    private static @Nullable Direction findHoldWithClearSpaceAbove(Yautja yautja) {
        var level = yautja.level();

        for (var direction : Direction.Plane.HORIZONTAL) {
            var toward = new Vec3(direction.getStepX(), 0.0, direction.getStepZ());

            if (wallPosition(yautja, toward) == null) {
                continue;
            }

            // The cell the body would occupy a block higher, on that side of the trunk.
            var above = BlockPos.containing(
                yautja.getX() - toward.x,
                yautja.getY() + Yautja.STANDING_HEIGHT,
                yautja.getZ() - toward.z
            );

            // ⚠ Genuinely EMPTY, not merely "not a full cube" — a pane counts as in the way.
            if (level.getBlockState(above).getCollisionShape(level, above).isEmpty()) {
                return direction;
            }
        }

        return null;
    }

    /** True once there is clear air in front at head height — the lip is level with the hands. */
    /**
     * {@return whether the gripped wall ends at head height, i.e. there is a lip to mantle onto}
     * <p>
     * ⚠⚠ PROBES THE WALL COLUMN, NOT "ONE BLOCK AHEAD". The old probe was {@code pos + facing} at head height. On a
     * bare wall that lands inside the wall block; on a vine-covered trunk the foothold is not flush with the log, so
     * the probe landed in the VINE column instead — and a vine block is not solid, so every vine read as the top of the
     * wall. Mantle into the log, fail, drop onto the vines, re-grip, 88 times in 75 seconds in the tester's log
     * ({@code CLIMB attach wall=2} / {@code CLIMB detach reason=mantle}). Where the vines had gaps it read correctly,
     * which is the "mix of vines and gaps" he described. The wall block is resolved every tick anyway; its column is
     * the only honest place to look.
     */
    private static boolean isAtTop(Yautja yautja, BlockPos wall) {
        var headY = Mth.floor(yautja.getY() + Yautja.STANDING_HEIGHT);
        var overhead = new BlockPos(wall.getX(), headY, wall.getZ());
        var level = yautja.level();

        if (level.getBlockState(overhead).isSolid() || level.getBlockState(overhead.relative(Direction.UP)).isSolid()) {
            return false;
        }

        // Oct 9 - THE LANDING MUST FIT THE WHOLE YAUTJA. [tester] "the yautja continue to hop in place repeatedly when
        // trying to climb that final block" - the debug log shows it climbing 18 blocks, "detach reason=mantle" at the
        // top, then falling and starting again, over and over. The old test looked at the two cells beside its head
        // only. Where the face ends just under a ledge or overhang, those two cells are open but there is no room to
        // stand on top - the mantle lunges into the underside, it drops off the wall, and the climb repeats. Now the
        // top counts only if the first open cell on top of the face has room for its full standing height.
        var feetY = Mth.floor(yautja.getY());
        var landingY = Integer.MIN_VALUE;

        for (var y = feetY; y <= headY; y++) {
            var cell = new BlockPos(wall.getX(), y, wall.getZ());

            if (level.getBlockState(cell).getCollisionShape(level, cell).isEmpty()) {
                landingY = y;
                break;
            }
        }

        if (landingY == Integer.MIN_VALUE) {
            return false;
        }

        // Oct 9 - AND IT MUST BE WITHIN HOP REACH. The mantle is one hop (MANTLE_UP 0.42, a vanilla jump - about 1.25
        // blocks of lift). It used to fire as soon as the cells beside its HEAD were open, which can be with the top of
        // the face still two blocks above its feet: the hop falls short, it slides off, and the climb starts over - the
        // "hop in place at the last block". Until the top is within a block of its feet it keeps climbing instead.
        if (landingY - yautja.getY() > MANTLE_REACH) {
            return false;
        }

        for (var y = landingY; y < landingY + Mth.ceil(Yautja.STANDING_HEIGHT); y++) {
            var cell = new BlockPos(wall.getX(), y, wall.getZ());

            if (!level.getBlockState(cell).getCollisionShape(level, cell).isEmpty()) {
                return false;
            }
        }

        return true;
    }
}
