package com.predator.common.gameplay.entity.living.yautja.ai;

import com.blib.api.common.goap.v1.GOAPSensors;
import com.blib.api.common.goap.v1.action.ActionMasks;
import com.blib.api.common.goap.v1.action.BLibAction;
import com.blib.api.common.goap.v1.action.impl.NeoMoveToPosAction;
import com.blib.api.common.goap.v1.action.impl.NeoWanderAction;
import com.just.ai.goap.action.Action;
import com.just.ai.goap.condition.expression.Expressions;
import com.just.ai.goap.goal.Goal;
import com.predator.common.gameplay.entity.living.yautja.Yautja;
import com.predator.common.gameplay.entity.living.yautja.YautjaMovement;
import com.predator.common.gameplay.entity.living.yautja.animation.YautjaAttackAnimation;
import com.predator.common.gameplay.item.battleaxe.BattleaxeItem;
import com.predator.common.gameplay.item.combistick.CombiStickItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * The yautja's goals and actions.
 * <h2>How an action ends</h2> ⚠⚠ {@code Action.Signal} is only ABORT or CONTINUE — there is no "done". An action
 * finishes when the WORLD STATE comes to satisfy its effect container, which means every effect must name a key some
 * sensor actually flips. An effect on a key nothing senses is an action that runs forever.
 * <h2>Masks are the concurrency model</h2> BLib runs up to five plans at once and rejects a candidate whose mask
 * overlaps a running plan's, unioned across every action in it. So the masks below are a contract about what can happen
 * simultaneously, not decoration.
 */
public final class YautjaCombat {

    /** How often it can swing. Matches the 5-tick windup the old {@code DelayedAttackGoal} was constructed with. */
    private static final int ATTACK_COOLDOWN_TICKS = 20;

    /**
     * Ticks between battleaxe swings.
     * <p>
     * 🚨 WAS 14, WHICH CUT EVERY SWING OFF. Each battleaxe clip moves until 0.75 s — 15 ticks at normal speed, 17.6 at
     * its 0.85 playback — and the next swing restarts the attack track, so at 14 no swing ever finished. That is a
     * large part of why the combos looked like a blur. 18 lets each complete, and is still faster than a player's
     * once-a-second — [stated] "the yautja though swings it faster than the player because they are stronger".
     */
    private static final int BATTLEAXE_COOLDOWN_TICKS = 18;

    private static int attackCooldown(Yautja yautja) {
        if (yautja.getMainHandItem().getItem() instanceof BattleaxeItem) {
            return BATTLEAXE_COOLDOWN_TICKS;
        }

        // ⚠ The half-speed stab runs 27 ticks. At the ordinary 20 the next blow restarted the attack track before the
        // stab finished, cutting off the very motion it was slowed down to show.
        if (yautja.getMainHandItem().getItem() instanceof CombiStickItem) {
            return COMBI_STICK_COOLDOWN_TICKS;
        }

        return ATTACK_COOLDOWN_TICKS;
    }

    /** Longer than the half-speed stab (27 ticks), so a combo never cuts one off. */
    private static final int COMBI_STICK_COOLDOWN_TICKS = 28;

    /** How far away a held blow may still connect, squared. Reach plus the step the prey may have taken. */
    private static final double STRIKE_REACH_SQR = 4.5D * 4.5D;

    /** The front arc a held blow must still be inside to land: 55 degrees either side of straight ahead. */
    private static final double FRONT_ARC_COS = Math.cos(Math.toRadians(55.0D));

    /** Shortest pause between wanders. A hunter that never stands still reads as a wind-up toy. */
    private static final int MIN_REST_TICKS = 60;

    /** Randomised extra pause on top, so a group of them does not move in lockstep. */
    private static final int EXTRA_REST_TICKS = 140;

    /** Blocks. How far ahead of fleeing prey the chase will aim. */
    private static final double MAX_INTERCEPT_LEAD = 8.0;

    /**
     * Blocks above the yautja before prey counts as elevated.
     * <p>
     * ⚠ Matches {@code YautjaClimb.MIN_TARGET_HEIGHT} and {@code YautjaThrowGoal.MIN_TARGET_HEIGHT}. All three are the
     * same judgement — "that is above me, not up a step" — and they have to agree, or the yautja will walk to a trunk
     * it then refuses to climb, or throw at prey it was about to reach.
     */
    private static final double ELEVATED_PREY_HEIGHT = 3.0;

    /** How far down to look for the footing under elevated prey. Taller than any tree worth climbing. */
    private static final int MAX_GROUND_SCAN = 32;

    /** Blocks. How far from under the prey to look for something worth climbing. */
    private static final int CLIMB_APPROACH_RADIUS = 4;

    /** ⚠ Matches YautjaClimb.MIN_WALL_HEIGHT — the climb refuses anything shorter, so do not walk to one. */
    private static final int CLIMBABLE_COLUMN_HEIGHT = 2;

    // -----------------------------------------------------------------------------------------------------------
    // Combat
    // -----------------------------------------------------------------------------------------------------------

    public static final Goal KILL_TARGET = Goal.builder("YautjaKillTarget")
        .addPrecondition(GOAPSensors.HAS_ATTACK_TARGET.key(), Expressions.Boolean.isTrue())
        .addDesiredCondition(GOAPSensors.HAS_ATTACK_TARGET.key().asDerived(), Expressions.Boolean.isFalse())
        .build();

    /**
     * Closes the distance.
     * <p>
     * ⭐ Routed through {@link NeoMoveToPosAction}, which is the GOAP action that drives BLib's own pathfinder. It only
     * does so because {@code Yautja} implements {@code PathNavigatorUser} — that interface IS the switch away from
     * vanilla navigation, and the water behaviour hangs off which navigator the entity hands back.
     * <p>
     * ⚠ It aims at an INTERCEPT point rather than at the target's feet. Now that the chase outruns a sprinting player
     * (see {@link YautjaMovement#CHASE_SPEED_MODIFIER}), pathing to where prey currently is means arriving where it
     * used to be, every tick, forever. The xenomorphs lead their targets for the same reason.
     */
    public static final Action<Yautja> MOVE_TO_TARGET = BLibAction.<Yautja>builder("YautjaMoveToTarget")
        .addMasks(ActionMasks.MOVE, ActionMasks.LOOK)
        .markInterruptible()
        .addPrecondition(GOAPSensors.HAS_ATTACK_TARGET.key(), Expressions.Boolean.isTrue())
        .addPrecondition(YautjaSensors.IS_TARGET_IN_MELEE_RANGE_KEY, Expressions.Boolean.isFalse())
        // ⚠⚠ THE FALL-BACK LEAP'S HOLD. Without this the chase resumes on the tick it lands and walks it straight back
        // into the crowd it just left, inside the caster's 7-block cut-off. [stated] "supress with window ending
        // early" — YautjaFallBackGoal ends the hold itself once the distance is bought.
        .addPrecondition(YautjaSensors.IS_HOLDING_RANGE_KEY, Expressions.Boolean.isFalse())
        .addEffect(YautjaSensors.IS_TARGET_IN_MELEE_RANGE_KEY.asDerived(), true)
        .withPerformCallback(context -> {
            var yautja = context.getActor();
            var target = context.getWorldState().getOrNull(GOAPSensors.NEAREST_ATTACKABLE_TARGET.key());

            if (target == null || target.isNone()) {
                return Action.Signal.ABORT;
            }

            var prey = target.unwrap();

            yautja.getLookControl().setLookAt(prey);

            // Prey up a tree: walk to the trunk, not at the sky. See groundBeneathElevatedPrey.
            var approach = groundBeneathElevatedPrey(yautja, prey);

            var result = NeoMoveToPosAction.perform(
                context,
                approach != null ? approach : interceptPoint(yautja, prey),
                YautjaMovement.CHASE_SPEED_MODIFIER,
                // ⚠⚠ THE FOURTH ARGUMENT IS allowBlockBreaking, AND THE THREE-ARG OVERLOAD PASSES FALSE.
                // It overrides the navigator's own features per call:
                // features.with(PathfindingFeature.BLOCK_BREAKING, allowBlockBreaking)
                // so a yautja configured to break wood still would not, because every chase asked for the
                // feature to be OFF. Two independent switches had to be on and neither was.
                // ⚠ AND ONLY WHEN mobGriefing IS ON, like every vanilla mob that breaks blocks. BLib does not check the
                // gamerule itself, so without this a server could not switch yautja block breaking off.
                yautja.level().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING)
            );

            return result == NeoMoveToPosAction.Result.NO_PATH
                ? Action.Signal.ABORT
                : Action.Signal.CONTINUE;
        })
        .withFinishCallback(NeoMoveToPosAction::onFinish)
        .build();

    /**
     * Swings.
     * <p>
     * Claims USE_MAIN_HAND and LOOK but NOT move — so it cannot fight the plasma caster, which claims nothing, and a
     * yautja can be swinging at one thing while the caster burns another. That was the whole point of the caster's
     * flagless goal, now enforced by the resolver instead of by hand.
     */
    public static final Action<Yautja> MELEE_ATTACK = BLibAction.<Yautja>builder("YautjaMeleeAttack")
        .addMasks(ActionMasks.USE_MAIN_HAND, ActionMasks.LOOK)
        .addPrecondition(YautjaSensors.IS_TARGET_IN_MELEE_RANGE_KEY, Expressions.Boolean.isTrue())
        .addEffect(GOAPSensors.HAS_ATTACK_TARGET.key().asDerived(), false)
        .withPerformCallback(context -> {
            var yautja = context.getActor();
            var target = context.getWorldState().getOrNull(GOAPSensors.NEAREST_ATTACKABLE_TARGET.key());

            if (target == null || target.isNone()) {
                return Action.Signal.ABORT;
            }

            var prey = target.unwrap();

            yautja.getLookControl().setLookAt(prey);

            if (yautja.tickCount - yautja.getLastMeleeAttackTick() >= attackCooldown(yautja)) {
                yautja.setLastMeleeAttackTick(yautja.tickCount);
                strike(yautja, prey);
            }

            return Action.Signal.CONTINUE;
        })
        .build();

    // -----------------------------------------------------------------------------------------------------------
    // Idle
    // -----------------------------------------------------------------------------------------------------------

    public static final Goal SATISFY_BOREDOM = Goal.builder("YautjaSatisfyBoredom")
        .addPrecondition(YautjaSensors.IS_BORED_KEY, Expressions.Boolean.isTrue())
        .addDesiredCondition(YautjaSensors.IS_BORED_KEY.asDerived(), Expressions.Boolean.isFalse())
        .build();

    /**
     * Roams at walking pace.
     * <p>
     * ⚠ Speed 1.0, meaning the raw movement attribute — that is what makes this the STRUT and the chase the RUN. The
     * two used to be identical, which is why the run clip never played.
     */
    public static final Action<Yautja> WANDER = BLibAction.<Yautja>builder("YautjaWander")
        .addMask(ActionMasks.MOVE)
        .markInterruptible()
        .addEffect(YautjaSensors.IS_BORED_KEY.asDerived(), false)
        .withPerformCallback(context -> NeoWanderAction.perform(context, 12, 6, 1.0, arrived -> {
            NeoWanderAction.onFinish(arrived);

            // Arriving is what makes the boredom goal satisfiable — see YautjaSensors.IS_BORED.
            var yautja = arrived.getActor();
            yautja.restFor(MIN_REST_TICKS + yautja.getRandom().nextInt(EXTRA_REST_TICKS));
        }))
        .withFinishCallback(NeoWanderAction::onFinish)
        .build();

    /**
     * One blow of the melee combo — slash, slash, stab, bare — with the stab piercing armour. <strong>How 10% armour
     * piercing is actually done</strong> ⚠⚠ Vanilla has no partial armour bypass. A damage type either ignores armour
     * completely or not at all, so "10%" cannot be expressed as a flag. Landing a second, armour-ignoring hit would not
     * work either: the target's invulnerability window swallows it.
     * <p>
     * So the damage is pre-scaled instead. {@code CombatRules.getDamageAfterAbsorb} is run twice — once with the
     * target's real armour, once with 90% of it — and the base damage multiplied by the ratio between the results. The
     * armour then absorbs the boosted figure and what lands is exactly what 10% less armour would have let through. One
     * hit, one event, arithmetically the real thing.
     * <p>
     * ⚠ An unarmoured target divides identically top and bottom, so the ratio is 1 and the stab is a normal blow. That
     * is correct: there is nothing to pierce.
     */
    /** {@return the attack this swing should use} */
    private static YautjaAttackAnimation selectStep(Yautja yautja, boolean armed) {
        // ⚠⚠ SWIMMING IS A PUNCH AND NOTHING ELSE. The combo — slash, slash, stab, bare — is authored for a
        // yautja standing on its feet: three of its four steps swing wrist blades from a braced stance that
        // does not exist mid-water. His call, and the tester saw the punch precisely because it is the one
        // step of the four that still reads underwater.
        //
        // ⚠ Gated on isSwimming(), NOT isInWater(), and the two differ. isSwimming is set from isUnderWater,
        // which is the same condition YautjaBodyState uses to pick the swim clip — so the invariant holds both
        // ways: whenever the body is swimming the attack is a punch, and whenever it is wading in the shallows
        // and standing normally it still gets the full combo.
        //
        // ⚠ Does NOT call nextMeleeStep(), so the combo is not consumed. A yautja that punches its way across
        // a river and reaches the bank picks the sequence back up where it left off instead of restarting.
        if (yautja.isSwimming()) {
            return YautjaAttackAnimation.BARE;
        }

        // [stated] against an unarmed player a Hunter fights "bare handed putting away all its weapons including wrist
        // blades" — the punch is the one step of the combo with no blade in it. Like the swim punch, it leaves the
        // combo where it was.
        if (yautja.isHuntBareHanded()) {
            return YautjaAttackAnimation.BARE;
        }

        // ⚠ The combi stick has its own combo and its own clips, so it is checked before the generic armed
        // branch — WEAPON is the sword-and-axe swing and would look wrong on a spear.
        if (yautja.getMainHandItem().getItem() instanceof CombiStickItem) {
            return yautja.nextSpearStep();
        }

        // ⚠ The battleaxe has its own three clips and must be checked BEFORE the generic armed branch: BattleaxeItem
        // extends AxeItem, so the armed test below would claim it and play the one-handed weapon swing instead.
        if (yautja.getMainHandItem().getItem() instanceof BattleaxeItem) {
            return yautja.nextBattleaxeStep();
        }

        return armed ? YautjaAttackAnimation.WEAPON : yautja.nextMeleeStep();
    }

    private static void strike(Yautja yautja, LivingEntity prey) {
        // Armed, so the whole combo is replaced by the weapon swing — see YautjaAttackAnimation.WEAPON.
        var armed = yautja.getMainHandItem().getItem() instanceof net.minecraft.world.item.SwordItem
            || yautja.getMainHandItem().getItem() instanceof net.minecraft.world.item.AxeItem;
        var step = selectStep(yautja, armed);
        var base = (float) yautja.getAttributeValue(Attributes.ATTACK_DAMAGE) * step.damageMultiplier();
        var source = yautja.damageSources().mobAttack(yautja);
        var damage = base * armourPiercingScale(prey, source, base, step.armourPiercing());

        // 🚨 FACE IT FIRST. [stated] "its able to kill the skeleton with it even when the yautja is looking the
        // opposite way." Nothing turned the body before a blow, so the swing played pointing wherever it happened to
        // face while the damage landed on something behind it.
        faceTarget(yautja, prey);
        yautja.playAttackAnimation(step);
        yautja.swing(InteractionHand.MAIN_HAND);

        // [stated] his attack sounds "when it swings its weapons" — and the yell for "a crit". A combo's finishing
        // blow (the step with knockback) is the yell; every other swing is a grunt, on most swings.
        if (step.knockback() > 0.0F) {
            com.predator.common.gameplay.entity.living.yautja.YautjaSounds.heavyAttack(yautja);
        } else {
            com.predator.common.gameplay.entity.living.yautja.YautjaSounds.attack(yautja);
        }

        if (step.impactTicks() <= 0) {
            land(yautja, prey, damage, step);

            return;
        }

        // ⚠⚠ HELD TO THE CLIP'S IMPACT FRAME. The hit waits for the point to arrive — see YautjaAttackAnimation.
        yautja.setPendingStrike(prey, damage, step, yautja.tickCount + step.impactTicks());
    }

    /**
     * Lands a strike that was held for its impact frame, if it still can.
     * <p>
     * ⚠ The prey had those ticks to move. It connects only if it is still alive, still within reach, and still IN FRONT
     * — a blow that has swung past its target must not land behind the yautja.
     */
    public static void tickPendingStrike(Yautja yautja) {
        var prey = yautja.getPendingStrikePrey();

        if (prey == null || yautja.tickCount < yautja.getPendingStrikeTick()) {
            return;
        }

        var damage = yautja.getPendingStrikeDamage();
        var step = yautja.getPendingStrikeStep();

        yautja.clearPendingStrike();

        if (!prey.isAlive() || yautja.distanceToSqr(prey) > STRIKE_REACH_SQR || !isInFront(yautja, prey)) {
            return;
        }

        land(yautja, prey, damage, step);
    }

    private static void land(Yautja yautja, LivingEntity prey, float damage, YautjaAttackAnimation step) {
        if (!prey.hurt(yautja.damageSources().mobAttack(yautja), damage)) {
            // ⚠ A blow that did not land (blocked, invulnerable) makes no hit sound — the same as vanilla, which only
            // plays its attack sound for a hit that actually dealt damage.
            return;
        }

        // 🚨 THE HIT SOUND. [stated] "when an enemy is hit with it theres no soundeffect it should make a hit sound
        // like
        // a sword or axe." A PLAYER's hit gets its sound from vanilla's Player.attack; a yautja's strike only ever
        // called
        // hurt(), so all anyone heard was the victim's own hurt noise and no weapon at all — for every yautja weapon,
        // not just the battleaxe. It now plays the same sounds a player's hit does: the knockback clang for the spear
        // stab, which is the combo's knockback finisher, and the strong hit for everything else.
        // ⚠⚠ THE BATTLEAXE ALWAYS CRITS — sparks and the crit sound on every landed blow, the same as a player's. See
        // BattleaxeItem.hurtEnemy for the ruling. The sparks are vanilla's own crit packet, broadcast to everyone
        // watching the yautja, so they look exactly like a jump crit.
        // [stated] the plasma sword "sets things on fire" — a yautja's plasma sword blow too. (A yautja's strike never
        // goes
        // through the item's own hurtEnemy, so it is done here.)
        if (yautja.getMainHandItem().getItem() instanceof com.predator.common.gameplay.item.PlasmaSwordItem) {
            prey.igniteForSeconds(com.predator.common.gameplay.item.PlasmaSwordItem.IGNITE_SECONDS);
        }

        var battleaxe = yautja.getMainHandItem().getItem() instanceof BattleaxeItem;

        if (battleaxe && yautja.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            serverLevel.getChunkSource()
                .broadcast(
                    yautja,
                    new net.minecraft.network.protocol.game.ClientboundAnimatePacket(
                        prey,
                        net.minecraft.network.protocol.game.ClientboundAnimatePacket.CRITICAL_HIT
                    )
                );
        }

        yautja.level()
            .playSound(
                null,
                prey.getX(),
                prey.getY(),
                prey.getZ(),
                battleaxe
                    ? SoundEvents.PLAYER_ATTACK_CRIT
                    : step.knockback() > 0.0F ? SoundEvents.PLAYER_ATTACK_KNOCKBACK : SoundEvents.PLAYER_ATTACK_STRONG,
                yautja.getSoundSource(),
                1.0F,
                1.0F
            );

        // ⚠ Applied AFTER the hurt, or vanilla's own hit knockback overwrites it. The stab is the combo finisher
        // and the shove is what makes it one — it resets the distance a spear wants to fight at.
        if (step.knockback() > 0.0F) {
            var away = prey.position().subtract(yautja.position());
            prey.knockback(step.knockback(), -away.x, -away.z);
        }
    }

    /**
     * Turns body, head and facing onto the prey in one go, so the clip plays toward it.
     * <p>
     * ⚠ Public because the spear THROW uses it too — a throw started with the target behind used to wind up facing the
     * wrong way and only drift round through head-look.
     */
    public static void faceTarget(Yautja yautja, LivingEntity prey) {
        var dx = prey.getX() - yautja.getX();
        var dz = prey.getZ() - yautja.getZ();
        var yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);

        yautja.setYRot(yaw);
        yautja.yBodyRot = yaw;
        yautja.yHeadRot = yaw;
        yautja.getLookControl().setLookAt(prey);
    }

    /** {@return whether the prey is within the yautja's front arc} */
    private static boolean isInFront(Yautja yautja, LivingEntity prey) {
        var toPrey = prey.position().subtract(yautja.position()).multiply(1, 0, 1);

        if (toPrey.lengthSqr() < 1.0E-4D) {
            return true;
        }

        var facing = net.minecraft.world.phys.Vec3.directionFromRotation(0.0F, yautja.getYRot());

        return facing.dot(toPrey.normalize()) >= FRONT_ARC_COS;
    }

    private static float armourPiercingScale(LivingEntity prey, DamageSource source, float base, float piercing) {
        if (piercing <= 0.0F) {
            return 1.0F;
        }

        var armour = (float) prey.getAttributeValue(Attributes.ARMOR);
        var toughness = (float) prey.getAttributeValue(Attributes.ARMOR_TOUGHNESS);

        var normal = CombatRules.getDamageAfterAbsorb(prey, base, source, armour, toughness);
        var pierced = CombatRules.getDamageAfterAbsorb(prey, base, source, armour * (1.0F - piercing), toughness);

        return normal <= 0.0F ? 1.0F : pierced / normal;
    }

    /**
     * {@return where to path so the yautja arrives where the prey is GOING, not where it was}
     * <p>
     * Leads by the target's own velocity, scaled by how long the yautja will take to cover the gap at its own speed.
     */
    /**
     * {@return the ground beneath prey that is above and out of reach, or null when it is reachable normally}
     * <p>
     * <b>⚠⚠ THIS IS WHY A YAUTJA CIRCLED A TREE INSTEAD OF CLIMBING IT.</b> The chase paths to the PREY'S POSITION, and
     * prey standing on a canopy is a point in mid-air. BLib cannot route to mid-air, so every replan came back NO_PATH
     * and the yautja milled about at whatever range it happened to be — never touching the trunk. And the climb only
     * attaches to a hold it is ALREADY beside, so it never got the chance to grab.
     * <p>
     * Walking to the ground DIRECTLY BELOW the prey is a reachable destination and, for anything in a tree, that ground
     * is the foot of the trunk. Arriving there puts a grippable face within reach and the climb takes over.
     * <p>
     * ⚠ Only when the prey is genuinely ABOVE. Redirecting a normal chase to the floor under a running player would
     * make the yautja aim behind them every time they jumped.
     */
    private static @Nullable Vec3 groundBeneathElevatedPrey(Yautja yautja, LivingEntity prey) {
        if (prey.getY() - yautja.getY() < ELEVATED_PREY_HEIGHT) {
            return null;
        }

        var level = yautja.level();
        var scan = BlockPos.containing(prey.getX(), prey.getY(), prey.getZ());

        // ⚠⚠ SKIPS LEAVES ON THE WAY DOWN, AND THAT IS THE WHOLE CASE THIS EXISTS FOR. LeavesBlock does not override
        // getCollisionShape, so a leaf is a FULL COLLISION CUBE. Prey standing on a canopy therefore has "footing"
        // directly beneath it, and a naive descent stops on the first leaf and hands back a point up in the branches
        // — mid-air, unroutable, and the yautja circles the tree exactly as before.
        for (var drop = 0; drop < MAX_GROUND_SCAN; drop++) {
            var below = scan.below();
            var state = level.getBlockState(below);

            if (!state.is(BlockTags.LEAVES) && state.isCollisionShapeFullBlock(level, below)) {
                // Oct 8 - FIX (tester: "stood and stared up at me", "unable to recognize its further connected to the
                // pillar"). Under a TREE the first solid block below the prey is the ground, so the trunk is right
                // there. Under a PLATFORM it is the platform the prey is standing on - this used to aim the yautja at a
                // spot beside the prey ON TOP, which it cannot path to, so it walked to the nearest point (under the
                // overhang) and stood there. When the "ground" found is still well above the yautja, find where the
                // structure can actually be climbed from instead.
                if (scan.getY() - yautja.getY() > ELEVATED_PREY_HEIGHT) {
                    var start = ClimbStart.find(yautja, prey);

                    // Oct 8 - [stated] "if it couldn't get to the player why didnt it just try to use a grenade": no
                    // climb leads up there, or one already failed - and it has a grenade - so back off to where it can
                    // see the prey and let the grenade goal throw. See ThrowStand.
                    if (
                        (start == null
                            || com.predator.common.gameplay.entity.living.yautja.YautjaSiege.hasRecentClimbFailure(yautja, prey))
                            && ThrowStand.hasGrenade(yautja)
                    ) {
                        var stand = ThrowStand.find(yautja, prey);

                        if (stand != null) {
                            return stand;
                        }
                    }

                    return start != null ? start : approachBesideClimbable(yautja, scan);
                }

                return approachBesideClimbable(yautja, scan);
            }

            scan = below;
        }

        return null;
    }

    /**
     * {@return a standing spot beside something climbable near {@code ground}, or {@code ground} itself}
     * <p>
     * ⚠ Straight down from the prey is not necessarily beside the trunk — a canopy is far wider than the tree, so prey
     * near its edge drops to open ground several blocks out. {@code YautjaClimb} only grabs a hold it is ALREADY
     * adjacent to, so arriving "under the prey" is not the same as arriving somewhere it can climb.
     * <p>
     * This looks for a cell whose neighbour is a sturdy column and walks there instead. Falling back to the plain
     * ground point is deliberate: even without a trunk it is a reachable destination directly under the prey, which is
     * where the throw goal wants the yautja anyway.
     */
    private static Vec3 approachBesideClimbable(Yautja yautja, BlockPos ground) {
        var level = yautja.level();

        for (var radius = 0; radius <= CLIMB_APPROACH_RADIUS; radius++) {
            for (var dx = -radius; dx <= radius; dx++) {
                for (var dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }

                    var candidate = ground.offset(dx, 0, dz);

                    if (!level.getBlockState(candidate).isAir()) {
                        continue;
                    }

                    for (var side : Direction.Plane.HORIZONTAL) {
                        if (isClimbableColumn(yautja, candidate.relative(side))) {
                            return Vec3.atBottomCenterOf(candidate);
                        }
                    }
                }
            }
        }

        return Vec3.atBottomCenterOf(ground);
    }

    /**
     * {@return whether this column is something {@code YautjaClimb} would actually grab}
     * <p>
     * ⚠ Same two tests the climb itself uses — a sturdy face and at least two blocks of it. Walking the yautja to a
     * fence post it will then refuse to climb is worse than not walking it anywhere.
     */
    private static boolean isClimbableColumn(Yautja yautja, BlockPos base) {
        var level = yautja.level();

        for (var height = 0; height < CLIMBABLE_COLUMN_HEIGHT; height++) {
            var pos = base.above(height);

            if (!level.getBlockState(pos).isCollisionShapeFullBlock(level, pos)) {
                return false;
            }
        }

        return true;
    }

    private static Vec3 interceptPoint(Yautja yautja, LivingEntity prey) {
        var velocity = prey.getDeltaMovement();
        var horizontal = new Vec3(velocity.x, 0.0, velocity.z);

        if (horizontal.length() < 1.0E-4) {
            return prey.position();
        }

        // ⚠ BLOCKS per tick, not the raw attribute. BLib scales movement speed by 3.15, so dividing a distance in
        // blocks by the attribute silently under-led every shot by about a third.
        var blocksPerTick = YautjaMovement.chaseBlocksPerTick(
            yautja.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)
        );

        if (blocksPerTick <= 0.0) {
            return prey.position();
        }

        var ticksToClose = yautja.distanceTo(prey) / blocksPerTick;
        var lead = horizontal.scale(ticksToClose);

        // ⚠ Capped. Prey sprinting away at long range produces a lead of tens of blocks, and pathing to a point that
        // far past the target is how a chaser ends up running somewhere nobody is.
        if (lead.length() > MAX_INTERCEPT_LEAD) {
            lead = lead.normalize().scale(MAX_INTERCEPT_LEAD);
        }

        return prey.position().add(lead);
    }

    private YautjaCombat() {
        throw new UnsupportedOperationException();
    }
}
