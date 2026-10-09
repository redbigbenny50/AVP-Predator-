package com.predator.common.gameplay.entity.living.yautja.caster;

import com.predator.common.gameplay.entity.living.yautja.Yautja;
import com.predator.common.gameplay.entity.living.yautja.util.YautjaPredicates;
import com.predator.common.registry.tag.PredatorItemTags;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * Everything the shoulder plasma caster is tuned by, plus the shared maths the goal, the bolt and the animator all need
 * to agree on.
 * <h2>The honor rule</h2> A yautja does not open with this. It is a leveller, brought out only when the fight is
 * already unfair — outnumbered, facing a heavy ranged weapon it has no answer to, or facing something far bigger than
 * it. {@link #shouldDeploy} is that rule and nothing else decides it.
 * <h2>Why it fires on its own target</h2> The caster clips animate ONLY {@code gCasterMount}, {@code gCasterArm} and
 * {@code gCaster} — no body bone appears in any of the five. That is what makes independent fire honest rather than a
 * trick: the caster runs on its own animation track layered over the body, so a yautja mid-melee genuinely is shooting
 * someone else at the same time.
 */
public final class PlasmaCaster {

    // ---------------------------------------------------------------------------------------------------------------
    // Timing. Each figure is the clip length rounded up to whole ticks, so a state never ends mid-animation.
    // ---------------------------------------------------------------------------------------------------------------

    /** {@code caster.aim} is 0.5s. */
    public static final int DEPLOY_TICKS = 10;

    /** {@code caster.disarm} is 0.5s. */
    public static final int DISARM_TICKS = 10;

    /** His figure: "takes about 2 seconds" to charge, with the blue and white particles running the whole time. */
    public static final int CHARGE_TICKS = 40;

    /** {@code caster.shoot} is 0.375s = 7.5 ticks. Eight lets it finish before {@code caster.ready} resumes. */
    public static final int FIRE_TICKS = 8;

    /** Barrel cooling before the next charge starts. Full cycle is 40 + 8 + 20 = 68 ticks, a shot every ~3.4s. */
    public static final int RECOVERY_TICKS = 20;

    // ---------------------------------------------------------------------------------------------------------------
    // Engagement envelope
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * His ruling: "it can be as far as the sniper but usually the pred does it within range of the attack so probably
     * 32 or less". The sniper reaches 128; the yautja chooses not to, so 32 is the working figure.
     */
    public static final double MAX_RANGE = 32.0;

    /**
     * Below this the caster comes down and the hunt goes to melee and thrown weapons — his "if they close the distance
     * the yautja will switch". Deliberately larger than a melee reach so the switch happens BEFORE contact.
     */
    public static final double DISENGAGE_RANGE = 7.0;

    /** How many hostiles nearby count as "greatly outnumbered". */
    public static final int SWARM_COUNT = 3;

    /**
     * The radius the outnumbering is judged over — closer in than the firing envelope, because it is about pressure.
     */
    public static final double SWARM_RADIUS = 16.0;

    /**
     * "the toughest opponents". A praetorian or crusher is neither a swarm nor armed, and a yautja that let one walk in
     * unanswered would not be hunting, it would be losing. Vanilla players sit at 20, so this cannot fire on people.
     */
    public static final float HEAVY_PREY_HEALTH = 100.0F;

    /** How wide the caster can traverse before a target is simply out of its arc. */
    public static final float AIM_YAW_LIMIT = 100.0F;

    public static final float AIM_PITCH_LIMIT = 60.0F;

    /** A flyer overhead is worth craning for: the arm reaches nearly straight up for one. */
    public static final float AIRBORNE_PITCH_LIMIT = 80.0F;

    /** Clear air below a player before they count as up out of reach rather than mid-jump. */
    public static final double AIRBORNE_CLEARANCE = 3.0;

    /** Dropping faster than this per tick is a fall, not a hover. */
    public static final double FALLING_SPEED = 0.4;

    /**
     * {@return whether this is a player up in the air where a yautja cannot reach them} — gliding on an elytra, in
     * creative-style flight (a mod's flight ability), or held in the air some other way (a jetpack, a hover ability, a
     * flying mount) with at least {@link #AIRBORNE_CLEARANCE} blocks of empty air below and not dropping like a fall.
     * <p>
     * ⚠⚠ THE SPEED IS READ FROM THE POSITION, NOT getDeltaMovement(). A player moves on their own client and the server
     * only receives positions, so a ServerPlayer's delta movement is not their real velocity. The change in Y since
     * last tick (y - yo) is. ⚠ An ordinary jump peaks around 1.25 blocks, so the clearance alone never mistakes one for
     * flight.
     */
    /** Higher than the yautja by this much and it counts as above it — on a roof, a ledge, a pillar. */
    public static final double ABOVE_HEIGHT = 3.0;

    /** The yautja's chain-whip reach (YautjaWhipGoal.MAX_RANGE). */
    public static final double WHIP_RANGE = 20.0;

    /**
     * {@return whether the chain whip should take this target instead of the caster} — [stated] "if youre flying or
     * above the predator and in chainwhip range it will grapple you instead of shooting you and when it does you get
     * pulled to it." True for a flyer (see {@link #isOutOfReach}) or anything {@link #ABOVE_HEIGHT} above it, within
     * {@link #WHIP_RANGE}, in sight, while it carries a whip that is ready (or already has one out) and its hands are
     * free to use it.
     * <p>
     * ⚠ While the whip is on its cooldown the caster is free to shoot — otherwise a dodged hook would buy the flyer ten
     * untouchable seconds.
     */
    public static boolean isWhipTarget(Yautja yautja, LivingEntity target) {
        if (!isOutOfReach(target) && target.getY() - yautja.getY() < ABOVE_HEIGHT) {
            return false;
        }

        if (
            !yautja.mayUseWeapons() || !yautja.hasAmmo(com.predator.common.registry.init.item.PredatorItems.CHAIN_WHIP.get())
                || yautja.getMainHandItem().getItem() instanceof com.predator.common.gameplay.item.battleaxe.BattleaxeItem
        ) {
            return false;
        }

        if (yautja.distanceToSqr(target) > WHIP_RANGE * WHIP_RANGE || !yautja.hasLineOfSight(target)) {
            return false;
        }

        return com.predator.common.gameplay.whip.WhipGrapple.isGrappling(yautja) || yautja.tickCount >= yautja.getWhipReadyTick();
    }

    public static boolean isOutOfReach(LivingEntity target) {
        if (!(target instanceof net.minecraft.world.entity.player.Player player)) {
            return false;
        }

        if (player.isFallFlying() || player.getAbilities().flying) {
            return true;
        }

        if (player.onGround() || player.isInWater() || player.onClimbable() || player.getY() - player.yo < -FALLING_SPEED) {
            return false;
        }

        var below = player.level()
            .clip(
                new net.minecraft.world.level.ClipContext(
                    player.position(),
                    player.position().subtract(0.0, AIRBORNE_CLEARANCE, 0.0),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.ANY,
                    player
                )
            );

        return below.getType() == net.minecraft.world.phys.HitResult.Type.MISS;
    }

    /** Target re-selection cadence. Every tick is wasted work; the caster is not a twitch weapon. */
    public static final int SCAN_INTERVAL_TICKS = 10;

    // ---------------------------------------------------------------------------------------------------------------
    // The bolt
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * Damage, for scale against avp_human's own guns (read from the 0.1.11 jar): sniper 30 every 30 ticks, rocket 16
     * plus a blast every 60, pulse rifle 6 every 10. Twenty on a 68-tick cycle is a heavy hit that loses a straight
     * damage race, which is the point — the caster levels a fight, it does not win one on its own.
     */
    public static final float BOLT_DAMAGE = 20.0F;

    /** Blocks per tick. Fast enough to read as a bolt rather than a thrown object, slow enough to see coming. */
    public static final double BOLT_SPEED = 1.8;

    /** Ticks. At the speed above this covers well past {@link #MAX_RANGE}, so nothing expires mid-flight in range. */
    public static final int BOLT_LIFETIME_TICKS = 60;

    // ---------------------------------------------------------------------------------------------------------------
    // Muzzle
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * Where the barrel sits, taken from the {@code gBarrel} pivot he moved for exactly this purpose.
     * <p>
     * Geo pivot is [3.53, 34.8, 8.88] in model units, 16 to the block: 0.22 to the entity's LEFT and 2.175 up. The
     * caster sits on the left shoulder — {@code gCasterMount} is at +x and {@code gLeftArm} is at +5.08 while
     * {@code gRightArm} is at -5.08, so +x is the entity's left.
     * <p>
     * ⚠ Forward is 0 rather than the pivot's z. The pivot is the BIND pose, where the caster is folded back behind the
     * shoulder; deployed it swings forward. Spawning the bolt half a block BEHIND the yautja would look far worse than
     * spawning it at the shoulder plane, and since the bolt is aimed AT its target a small forward error is invisible
     * anyway. A left/right error is not, which is what he saw.
     */
    public static final double MUZZLE_UP = 2.175;

    public static final double MUZZLE_LEFT = 0.221;

    public static final double MUZZLE_FORWARD = 0.0;

    /**
     * Particle sizes. {@code DustParticleOptions} takes a SCALE, where 1.0 is already a chunky cube — the first pass
     * used 1.0 to 2.4 and the result buried the yautja in blue confetti. These are roughly half that.
     */
    private static final float CHARGE_PARTICLE_SIZE = 0.4F;

    /** Blocks. How wide the charge cloud starts — tight enough to sit on the gun, not on the head. */
    private static final double CHARGE_SPREAD_START = 0.16;

    /** And how tight it draws in by the moment of firing. */
    private static final double CHARGE_SPREAD_END = 0.05;

    private static final float FLASH_PARTICLE_SIZE = 1.1F;

    private static final float TRAIL_PARTICLE_SIZE = 0.3F;

    /** Was 2. Density is what makes the streak readable; the SIZE deliberately stays where it is. */
    private static final int TRAIL_PARTICLE_COUNT = 6;

    /** Was 0.05. A slightly wider scatter reads as a glow around the bolt rather than a dotted line through it. */
    private static final double TRAIL_PARTICLE_SPREAD = 0.09D;

    private static final float IMPACT_PARTICLE_SIZE = 1.0F;

    /** Ice blue, matched by eye to {@code plasma_bolt.png}. */
    private static final Vector3f ICE_BLUE = new Vector3f(0.42F, 0.78F, 1.0F);

    private static final Vector3f WHITE = new Vector3f(0.90F, 0.97F, 1.0F);

    private PlasmaCaster() {
        throw new UnsupportedOperationException();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Geometry
    // ---------------------------------------------------------------------------------------------------------------

    /** {@return the world position the bolt leaves from and the charge particles gather at} */
    public static Vec3 muzzlePosition(Yautja yautja) {
        var bodyYawRadians = yautja.yBodyRot * Mth.DEG_TO_RAD;
        var sin = Mth.sin(bodyYawRadians);
        var cos = Mth.cos(bodyYawRadians);

        // Body yaw 0 faces +z in world space, and the entity's left is then -x.
        var forwardX = -sin * MUZZLE_FORWARD;
        var forwardZ = cos * MUZZLE_FORWARD;

        // ⚠⚠ THIS WAS INVERTED, WHICH IS WHY THE CHARGE PARTICLES APPEARED ON THE WRONG SHOULDER.
        // At yaw 0 an entity faces south (+Z), and facing south your left hand points EAST (+X). The old
        // form was (-cos, -sin), which is the RIGHT vector, so every muzzle position was mirrored across
        // the body.
        var leftX = cos * MUZZLE_LEFT;
        var leftZ = sin * MUZZLE_LEFT;

        return new Vec3(
            yautja.getX() + forwardX + leftX,
            yautja.getY() + MUZZLE_UP,
            yautja.getZ() + forwardZ + leftZ
        );
    }

    /** {@return the point on a target the caster aims for — centre mass, not the feet} */
    public static Vec3 aimPointOn(LivingEntity target) {
        return new Vec3(target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ());
    }

    /** {@return whether the target sits inside the arc the caster arm can actually traverse to} */
    public static boolean isWithinArc(Yautja yautja, LivingEntity target) {
        var muzzle = muzzlePosition(yautja);
        var toTarget = aimPointOn(target).subtract(muzzle);
        var horizontal = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);

        var yaw = (float) (Mth.atan2(toTarget.z, toTarget.x) * Mth.RAD_TO_DEG) - 90.0F;
        var pitch = (float) (-(Mth.atan2(toTarget.y, horizontal) * Mth.RAD_TO_DEG));

        return Math.abs(Mth.wrapDegrees(yaw - yautja.yBodyRot)) <= AIM_YAW_LIMIT
            && Math.abs(pitch) <= (isOutOfReach(target) ? AIRBORNE_PITCH_LIMIT : AIM_PITCH_LIMIT);
    }

    /** {@return the world yaw, in degrees, from the muzzle to this target} */
    public static float aimYawTo(Yautja yautja, LivingEntity target) {
        var toTarget = aimPointOn(target).subtract(muzzlePosition(yautja));
        return (float) (Mth.atan2(toTarget.z, toTarget.x) * Mth.RAD_TO_DEG) - 90.0F;
    }

    /** {@return the pitch, in degrees, from the muzzle to this target — positive is downward, as vanilla has it} */
    public static float aimPitchTo(Yautja yautja, LivingEntity target) {
        var toTarget = aimPointOn(target).subtract(muzzlePosition(yautja));
        var horizontal = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
        return (float) (-(Mth.atan2(toTarget.y, horizontal) * Mth.RAD_TO_DEG));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // The honor rule
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * {@return whether this yautja is in a fight that justifies the caster}
     * <p>
     * Three ways in, exactly as he specified them, any one of which is enough:
     * <ol>
     * <li><b>Outnumbered</b> — {@link #SWARM_COUNT} or more hostiles inside {@link #SWARM_RADIUS}. This is what covers
     * a xenomorph swarm, and it costs nothing extra to state it that way: a swarm IS being outnumbered.</li>
     * <li><b>Matched</b> — something in range is carrying a weapon out of
     * {@link PredatorItemTags#CASTER_WORTHY_WEAPONS} (the rapid-fire caseless and drum guns, the launchers, the
     * sniper), or is a sentry turret. A sentry turret is a {@code Mob} in avp_human, so it turns up in the same scan
     * with no extra work and no hard dependency.</li>
     * <li><b>Outmatched</b> — something in range has {@link #HEAVY_PREY_HEALTH} or more maximum health.</li>
     * </ol>
     * Every candidate still has to pass {@link YautjaPredicates#isValidTarget}, so the honor code that governs what a
     * yautja will hunt at all governs this too — a wall of facehuggers is not a swarm worth a plasma bolt.
     */
    public static boolean shouldDeploy(Yautja yautja) {
        var nearby = yautja.level()
            .getEntitiesOfClass(
                LivingEntity.class,
                yautja.getBoundingBox().inflate(MAX_RANGE),
                candidate -> candidate != yautja && YautjaPredicates.isValidTarget(yautja, candidate)
            );

        var crowd = 0;

        for (var candidate : nearby) {
            // [stated] "a player who is hovering or flying out of the yautjas reach some kind of jet pack some hover
            // ability or an elytra it will use its plasma caster with very accurate shots".
            if (isOutOfReach(candidate) && !isWhipTarget(yautja, candidate)) {
                return true;
            }

            if (candidate.distanceToSqr(yautja) <= SWARM_RADIUS * SWARM_RADIUS) {
                crowd++;

                if (crowd >= SWARM_COUNT) {
                    return true;
                }
            }

            if (isCarryingHeavyWeapon(candidate) || isSentryTurret(candidate)) {
                return true;
            }

            if (candidate.getMaxHealth() >= HEAVY_PREY_HEALTH) {
                return true;
            }
        }

        return false;
    }

    /** Main hand or off hand — a marine with the smartgun slung is still the reason the caster came out. */
    public static boolean isCarryingHeavyWeapon(LivingEntity candidate) {
        return candidate.getMainHandItem().is(PredatorItemTags.CASTER_WORTHY_WEAPONS)
            || candidate.getOffhandItem().is(PredatorItemTags.CASTER_WORTHY_WEAPONS);
    }

    /**
     * Matched by registry id rather than by class.
     * <p>
     * ⚠ Deliberately dependency-free. avp_human is {@code modCompileOnly} here, and a hard class reference to it in
     * common code is the exact shape that has silently taken whole providers down before.
     */
    public static boolean isSentryTurret(LivingEntity candidate) {
        var key = BuiltInRegistries.ENTITY_TYPE.getKey(candidate.getType());
        return "avp_human".equals(key.getNamespace()) && key.getPath().contains("sentry_turret");
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Particles
    // ---------------------------------------------------------------------------------------------------------------

    /**
     * The charge tell: blue and white gathering at the barrel for the two seconds before the shot.
     * <p>
     * Vanilla particle types on purpose. Everything custom this mod has drawn has eventually had to be defended against
     * a shader pack; particles go through the pack's own pipeline, so no pack can crush them.
     */
    public static void spawnChargeParticles(ServerLevel level, Vec3 muzzle, float progress) {
        // ⚠⚠ HIS REPORT: the charge was landing on the head and the far shoulder. The POSITION was right; the
        // SPREAD was the problem — 0.55 blocks is over a third of a yautja's width, so the cloud covered the
        // whole upper body instead of the barrel. A charge tell has to read as "that gun is spinning up",
        // which means it must be tight enough to sit ON the gun.
        var spread = CHARGE_SPREAD_START - (CHARGE_SPREAD_START - CHARGE_SPREAD_END) * progress;
        var count = 1 + Math.round(progress * 2.0F);

        level.sendParticles(
            new DustParticleOptions(ICE_BLUE, CHARGE_PARTICLE_SIZE + progress * 0.2F),
            muzzle.x,
            muzzle.y,
            muzzle.z,
            count,
            spread,
            spread,
            spread,
            0.0
        );

        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, muzzle.x, muzzle.y, muzzle.z, 1, spread, spread, spread, 0.0);

        if (progress > 0.6F) {
            level.sendParticles(ParticleTypes.END_ROD, muzzle.x, muzzle.y, muzzle.z, 1, 0.1, 0.1, 0.1, 0.0);
        }
    }

    /** The flash as the bolt leaves. */
    public static void spawnMuzzleFlash(ServerLevel level, Vec3 muzzle) {
        level.sendParticles(new DustParticleOptions(WHITE, FLASH_PARTICLE_SIZE), muzzle.x, muzzle.y, muzzle.z, 8, 0.14, 0.14, 0.14, 0.0);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, muzzle.x, muzzle.y, muzzle.z, 10, 0.1, 0.1, 0.1, 0.35);
    }

    /** The tail. "the particles will give it a bit more body" — so this runs every tick of flight, not occasionally. */
    /**
     * The trail behind a bolt in flight.
     * <p>
     * ⭐ [stated] Sep 25: "add more particles to the bolt as it travels so you can see it better". DENSITY, not size -
     * the size was already cut once because a 0.8 particle outsized the 0.73-block bolt model, and going back up would
     * undo that. More particles in a slightly wider scatter reads as a brighter streak while each one stays smaller
     * than the bolt.
     * </p>
     * <p>
     * ⚠ A bolt lives {@link #BOLT_LIFETIME_TICKS} ticks and this runs once per tick per bolt, so the count is a real
     * per-shot cost. {@value #TRAIL_PARTICLE_COUNT} is chosen to be visible without flooding a firefight where several
     * casters are shooting at once.
     * </p>
     */
    public static void spawnBoltTrail(ServerLevel level, Vec3 position) {
        // ⚠ His report: the trail was reading BIGGER than the bolt and too white. The bolt is a 2-block model at
        // 0.363 scale — about 0.73 blocks — so a 0.8-scale particle really was outsizing it. The white dust and
        // the END_ROD are gone entirely; ice blue alone is what the png reads as.
        level.sendParticles(
            new DustParticleOptions(ICE_BLUE, TRAIL_PARTICLE_SIZE),
            position.x,
            position.y,
            position.z,
            TRAIL_PARTICLE_COUNT,
            TRAIL_PARTICLE_SPREAD,
            TRAIL_PARTICLE_SPREAD,
            TRAIL_PARTICLE_SPREAD,
            0.0
        );
    }

    /** The burst on contact. No explosion, no fire, no knockback beyond the hit — his rule, so it is safe up close. */
    public static void spawnImpact(ServerLevel level, Vec3 position) {
        level.sendParticles(
            new DustParticleOptions(ICE_BLUE, IMPACT_PARTICLE_SIZE),
            position.x,
            position.y,
            position.z,
            18,
            0.26,
            0.26,
            0.26,
            0.0
        );
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, position.x, position.y, position.z, 18, 0.2, 0.2, 0.2, 0.4);
        level.sendParticles(ParticleTypes.END_ROD, position.x, position.y, position.z, 6, 0.15, 0.15, 0.15, 0.05);
    }
}
