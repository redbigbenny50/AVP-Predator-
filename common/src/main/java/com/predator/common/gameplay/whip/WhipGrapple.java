package com.predator.common.gameplay.whip;

import com.predator.common.registry.init.PredatorSoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The pull, run from the player's tick: reel the player to a block anchor, or yank a mob to the player.
 * <p>
 * Chain of Souls keeps its equivalent in a PlayerMixin with the grapple stored on the player. This keeps the same shape
 * but in a plain server-side map, so the whip can be deleted without touching the player class at all — [stated]
 * "compartmentalize this ... so i can say remove it."
 */
public final class WhipGrapple {

    /** Live hooks, by owner. Server side only; cleared when a hook dies. */
    private static final Map<UUID, WhipHookEntity> ACTIVE = new HashMap<>();

    /** Players owed fall-damage immunity, by the game time it expires. */
    private static final Map<UUID, Long> FALL_GRACE = new HashMap<>();

    /** How far past the anchor still counts as "arrived" rather than "let it pull me back", blocks. */
    private static final double OVERSHOOT_RANGE = 6.0D;

    private WhipGrapple() {
        throw new UnsupportedOperationException();
    }

    public static void register(LivingEntity owner, WhipHookEntity hook) {
        ACTIVE.put(owner.getUUID(), hook);
    }

    public static @Nullable WhipHookEntity hookOf(LivingEntity owner) {
        var hook = ACTIVE.get(owner.getUUID());

        if (hook != null && !hook.isAlive()) {
            ACTIVE.remove(owner.getUUID());
            return null;
        }

        return hook;
    }

    /**
     * {@return whether this player currently has a hook out}
     * <p>
     * ⚠ Public because the GAUNTLET asks now. [stated] "we would be splitting the grapple hook to its own item ... its
     * a gauntlet item not consumed. It fires from the gauntlet."
     */
    public static boolean isGrappling(LivingEntity owner) {
        return hookOf(owner) != null;
    }

    /**
     * Fires the hook. {@return whether it left the gauntlet}
     * <p>
     * ⚠⚠ MOVED HERE FROM WhipItem.use. The whip is a left-click weapon now and owns none of this; the gauntlet's
     * launcher calls it. The cooldown is the GAUNTLET'S, applied by the launcher, so this does not set one.
     */
    public static boolean fire(Level level, Player player) {
        if (level.isClientSide || isGrappling(player)) {
            return false;
        }

        var hook = new WhipHookEntity(level, player, ItemStack.EMPTY);

        level.addFreshEntity(hook);
        register(player, hook);
        level.playSound(
            null,
            player.blockPosition(),
            PredatorSoundEvents.GRAPPLE_CHAIN_DEPLOY.get(),
            SoundSource.PLAYERS,
            1.0F,
            1.0F
        );

        return true;
    }

    public static void release(LivingEntity owner) {
        var hook = ACTIVE.remove(owner.getUUID());

        if (hook != null) {
            hook.discard();

            // ⚠ HERE, NOT AT THE CALL SITES. Every ending routes through release() — arriving at a ledge, letting go
            // with a second right click, yanking a mob in, the owner dying, the hook going out of range — so the
            // chain winds in exactly once however the grapple finished.
            owner.level()
                .playSound(
                    null,
                    owner.blockPosition(),
                    PredatorSoundEvents.GRAPPLE_CHAIN_RETRACT.get(),
                    net.minecraft.sounds.SoundSource.PLAYERS,
                    1.0F,
                    1.0F
                );
        }

        grantFallGrace(owner);
    }

    /** [stated] "no fall damage while grappling" — and for a moment after, or the ledge boost becomes a death trap. */
    /**
     * A MOB's grapple — the yautja's chain whip. Same hook, same bite, same yank as the player's; aimed at its TARGET
     * rather than along its look, because a hook has to actually connect.
     * <p>
     * ⚠ The hook's constructor launches it along the owner's rotation; {@code shoot} then replaces that velocity with
     * the aim at the target.
     */
    public static boolean fireAt(Level level, LivingEntity owner, LivingEntity target) {
        return fireAt(level, owner, target, new Vec3(target.getX(), target.getY(0.5D), target.getZ()));
    }

    /** As {@link #fireAt(Level, LivingEntity, LivingEntity)}, aimed at a chosen point — a led shot at a flyer. */
    public static boolean fireAt(Level level, LivingEntity owner, LivingEntity target, Vec3 aimPoint) {
        if (level.isClientSide || isGrappling(owner)) {
            return false;
        }

        var hook = new WhipHookEntity(level, owner, ItemStack.EMPTY);
        var aim = aimPoint.subtract(hook.position());

        hook.shoot(aim.x, aim.y, aim.z, WhipTuning.HOOK_SPEED, 0.0F);
        level.addFreshEntity(hook);
        register(owner, hook);
        level.playSound(
            null,
            owner.blockPosition(),
            PredatorSoundEvents.GRAPPLE_CHAIN_DEPLOY.get(),
            owner.getSoundSource(),
            1.0F,
            1.0F
        );

        return true;
    }

    /**
     * A mob's grapple, per tick: YANK what it hooked. [stated] "to grapple and pull enemies to it or to pull enemies
     * over trip mines".
     * <p>
     * ⚠ A MOB NEVER REELS ITSELF IN. Where a player's grapple treats a missed shot (a block) or a too-heavy catch as an
     * anchor to be pulled toward, a yautja simply lets go — being dragged into the thing it failed to pull is not a
     * tactic, it is a malfunction.
     */
    public static void tickMob(LivingEntity owner) {
        var hook = hookOf(owner);

        if (hook == null || !hook.isAnchored()) {
            return;
        }

        var hooked = hook.anchoredEntity();

        // ⚠ A PLAYER IS NEVER TOO HEAVY FOR A YAUTJA'S CHAIN. [stated] "when it does you get pulled to it." Knockback
        // resistance from armour or another mod (0.6 and up) would otherwise make a hooked player count as an anchor,
        // and a mob lets go of an anchor.
        if (hooked != null && (!hook.isTooHeavy() || hooked instanceof Player)) {
            yank(owner, hooked);

            return;
        }

        release(owner);
    }

    /** The hop that lifts a grounded target so it is not dragged along the floor — only while it is on the ground. */
    private static final double GROUND_HOP = 0.25D;

    /**
     * The chain lets go on its own after this — the hook's whole life, flight included. At the yank's 1.35 blocks a
     * tick even a catch at the full 32-block range is in hand well inside it; anything still hanging on is stuck.
     */
    public static int MAX_YANK_TICKS = 60;

    public static void grantFallGrace(LivingEntity owner) {
        FALL_GRACE.put(owner.getUUID(), owner.level().getGameTime() + WhipTuning.FALL_GRACE_TICKS);
    }

    public static boolean hasFallGrace(LivingEntity owner) {
        var until = FALL_GRACE.get(owner.getUUID());

        if (until == null) {
            return false;
        }

        if (owner.level().getGameTime() > until) {
            FALL_GRACE.remove(owner.getUUID());
            return false;
        }

        return true;
    }

    /** Called every server tick for a player holding a live hook. */
    public static void tick(Player player) {
        var hook = hookOf(player);

        if (hook == null || !hook.isAnchored()) {
            return;
        }

        var hooked = hook.anchoredEntity();

        if (hooked != null && !hook.isTooHeavy()) {
            yank(player, hooked);
            return;
        }

        reel(player, hook.position());
    }

    /** A mob light enough to move comes to the player. */
    /**
     * Drags what the hook bit toward whoever threw it.
     * <p>
     * 🚨 THE "IT JUST FLIES" BUG — [stated, relayed] "swapping to another item before the chain whip comes back
     * causes.. this.. its like that one trident bug when it has loyalty but your inventory is full ... the creature
     * just flies". The vertical pull was {@code Math.max(pull.y, 0.25)}: EVERY tick the creature was forced UPWARD at
     * least 0.25, whatever direction the thrower actually was — and the chain only lets go within 2 blocks. So a
     * creature ABOVE the thrower (their screenshot, looking up) was pushed further up instead of down, never came
     * within 2 blocks, and rose until the hook's 32-block range snapped the chain. Even a level target rose while being
     * drawn in, and could be more than 2 blocks overhead by the time it was over the thrower. Swapping items was not
     * the cause — the pull runs for every player every tick whatever they hold — but it may be why they could not let
     * go: the manual release only works with the chain whip selected as the gauntlet's ammo.
     * <p>
     * NOW: the pull follows the thrower in all three directions, so a creature above comes DOWN. The small hop that
     * lifted a target off the ground (so it did not drag along the floor) is kept — but only while it is actually ON
     * the ground and not above the thrower. And as a backstop the chain lets go on its own after
     * {@link #MAX_YANK_TICKS}. The yautja's whip uses this same pull and is fixed with it.
     */
    private static void yank(LivingEntity player, LivingEntity hooked) {
        var toPlayer = player.position().subtract(hooked.position());
        var hook = hookOf(player);

        if (toPlayer.lengthSqr() < 4.0D || hook != null && hook.tickCount > MAX_YANK_TICKS) {
            WhipGrapple.release(player);
            return;
        }

        var pull = toPlayer.normalize().scale(WhipTuning.YANK_STRENGTH);
        var lift = hooked.onGround() && toPlayer.y > -1.0D ? Math.max(pull.y, GROUND_HOP) : pull.y;

        hooked.setDeltaMovement(pull.x, lift, pull.z);
        hooked.hurtMarked = true;
        hooked.hasImpulse = true;
    }

    /**
     * The player goes to the anchor — and, on arrival, up. [stated] "if it could also lift you up to that might be good
     * to get you onto the blocks above your grapple." Without the boost you simply slam into the block's face and slide
     * down it.
     */
    private static void reel(Player player, Vec3 anchor) {
        var toAnchor = anchor.subtract(player.position());
        var distance = toAnchor.length();

        player.fallDistance = 0.0F;
        grantFallGrace(player);

        // ⚠⚠ ARRIVAL MUST SCALE WITH SPEED, OR IT IS JUMPED CLEAN OVER. A fixed 2-block window is only ever tested
        // once per tick: travelling faster than 2 blocks a tick you are outside it, then past the anchor, and the
        // pull reverses — [stated] "it swings me back and fourth like it wants to launch me but holds on and pulls
        // me backwards like a rubber band". The window is now at least as wide as this tick's travel.
        var speed = player.getDeltaMovement().length();
        var arrival = Math.max(WhipTuning.ARRIVAL_DISTANCE, speed);

        // Past it counts as arrived too: once the anchor is behind you the rope has done its job, and pulling again
        // can only drag you back.
        var overshot = speed > 1.0E-4D && player.getDeltaMovement().dot(toAnchor) < 0.0D && distance < OVERSHOOT_RANGE;

        if (distance <= arrival || overshot) {
            var forward = toAnchor.lengthSqr() < 1.0E-4D ? player.getLookAngle() : toAnchor.normalize();

            player.setDeltaMovement(
                player.getDeltaMovement().x + forward.x * WhipTuning.LEDGE_FORWARD,
                WhipTuning.LEDGE_BOOST,
                player.getDeltaMovement().z + forward.z * WhipTuning.LEDGE_FORWARD
            );
            player.hurtMarked = true;
            WhipGrapple.release(player);
            return;
        }

        // ⚠⚠ SET THE VELOCITY, DO NOT ADD TO IT. Adding a pull every tick compounds: at 0.14 x 8 that is +1.12
        // blocks per tick ADDED each tick, so after a second the player is doing far more than the reel ever
        // intended and blows through the arrival window — the overshoot half of the same report. Writing a capped
        // velocity makes the approach speed predictable and the arrival window meaningful.
        var strength = player.isShiftKeyDown() ? WhipTuning.REEL_CROUCH_SCALE : 1.0D;
        var wanted = Math.min(WhipTuning.REEL_MAX_SPEED, WhipTuning.REEL_STRENGTH * distance + WhipTuning.REEL_MIN_SPEED);
        var pull = toAnchor.normalize().scale(wanted * strength);

        // ⚠ PINNED AGAINST A FACE? CLIMB IT. A grapple to a point at the same height pulls purely sideways, so the
        // player just grinds into the wall and never arrives. When the last move was blocked horizontally, add lift
        // so they ride up the face into the arrival boost — which is what makes a level hookshot feel like one.
        if (player.horizontalCollision) {
            pull = new Vec3(pull.x, Math.max(pull.y, WhipTuning.REEL_WALL_CLIMB), pull.z);
        }

        // Keep the vertical component of gravity out of it while reeling, so a long horizontal pull does not sag.
        player.setDeltaMovement(pull);
        player.hurtMarked = true;
        player.resetFallDistance();
    }
}
