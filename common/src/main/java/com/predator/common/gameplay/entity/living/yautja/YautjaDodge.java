package com.predator.common.gameplay.entity.living.yautja;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * The dodge roll — a yautja throwing itself clear of a blast or a swing.
 * <h2>His spec</h2> "if an explosion happens near the yautja or a rocket is heading toward it then it will do a dodge
 * which is a roll. when it does this it absorbs 60% of the explosion damage and when fighting melee it will dodge out
 * of the way and disorient the player and not count that hit. it has a 10s cool down."
 * <h2>⚠⚠ WHY THIS EXISTS AT ALL — the rocket is the ONE weapon the hurt window cannot touch</h2> Every automatic weapon
 * was tamed by the 5-tick window because the window limits RATE. An explosion is a single hit event of 87 raw damage,
 * so it takes no reduction from that rule whatsoever, and the M6B kills a Clan Leader in 24 seconds against a sword's
 * 74. The dodge is the answer to per-hit damage that the window is structurally unable to give.
 * <h2>The two halves are deliberately unequal</h2> ⚠ Melee is negated ENTIRELY and an explosion is only reduced to 40%.
 * A yautja can sidestep a sword; it cannot sidestep a ten-block blast radius, and pretending otherwise would make the
 * rocket useless rather than costly.
 */
public final class YautjaDodge {

    /**
     * Eight seconds, his revised figure.
     * <p>
     * ⚠ This is the dial that decides how much the dodge is worth, NOT the 60%. At 10s it fired once per 3.3 rockets;
     * at 8s it is roughly one in 2.7. The absorption sets how good one dodge is, the cooldown sets how often one
     * happens, and the second matters more against anything that fires repeatedly.
     */
    public static final int COOLDOWN_TICKS = 160;

    /** How long the roll owns the body. Matches the 1 second {@code evade.roll} clip. */
    public static final int ROLL_TICKS = 20;

    /** What survives a dodged explosion — his 60% absorbed. */
    public static final float EXPLOSION_DAMAGE_TAKEN = 0.4F;

    /**
     * Blocks the yautja displaces sideways.
     * <p>
     * ⚠ THIS IS THE DISORIENTATION, and it needs no effect to achieve it. The player swings, the hit does not land, and
     * the target is suddenly beside or behind them. Applying nausea on top would be a second, worse version of the same
     * idea — one the player reads as a debuff rather than as being outplayed.
     */
    private static final double ROLL_DISTANCE = 2.2;

    /** Upward component, so the roll clears low obstacles instead of scraping into them. */
    private static final double ROLL_LIFT = 0.28;

    /** Blocks squared. Inside this a live explosive counts as a threat even if it is sitting still. */
    private static final double SETTLED_EXPLOSIVE_RADIUS_SQR = 25.0;

    private YautjaDodge() {
        throw new UnsupportedOperationException();
    }

    /**
     * {@return whether this damage is something a yautja can roll away from}
     * <p>
     * ⚠ Melee is identified the same way the close-quarters bonus identifies it — a player attack whose direct entity
     * IS the attacker, which excludes arrows. One definition of "melee" across both rules, so a bow can never get the
     * damage bonus and can never be dodged either.
     */
    public static boolean isDodgeable(DamageSource source) {
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            return true;
        }

        return source.is(DamageTypeTags.IS_PLAYER_ATTACK) && source.getDirectEntity() == source.getEntity();
    }

    /**
     * Throws the yautja clear, perpendicular to whatever is threatening it.
     * <p>
     * ⚠ SIDEWAYS, NOT BACKWARDS. Rolling away from an attacker is a retreat and puts the yautja out of its own melee
     * range, which turns a defensive move into a disengage it never wanted. Perpendicular keeps it in the fight.
     */
    public static void roll(Yautja yautja, Entity threat) {
        var away = yautja.position().subtract(threat.position());
        var flat = new Vec3(away.x, 0.0, away.z);

        // Directly on top of it: any direction will do, so use the facing.
        var basis = flat.lengthSqr() < 1.0E-4 ? yautja.getLookAngle() : flat.normalize();
        var sideways = new Vec3(-basis.z, 0.0, basis.x);

        // Pick whichever side it is already leaning toward, so the roll reads as a continuation of its movement.
        var motion = yautja.getDeltaMovement();

        if (sideways.dot(new Vec3(motion.x, 0.0, motion.z)) < 0.0) {
            sideways = sideways.reverse();
        }

        yautja.setDeltaMovement(sideways.x * ROLL_DISTANCE * 0.25, ROLL_LIFT, sideways.z * ROLL_DISTANCE * 0.25);
        yautja.hasImpulse = true;
    }

    /**
     * {@return whether a rocket is inbound and close enough to be worth rolling from}
     * <p>
     * ⚠ Identified by REGISTRY KEY, not by class. avp_human is an optional dependency, so naming its {@code Rocket}
     * type here would not compile without it.
     * <p>
     * ⚠ Requires the rocket to actually be CLOSING. A rocket that has already passed is not a threat, and a yautja that
     * rolled away from one flying off into the distance would look broken rather than alert.
     */
    public static Entity incomingThreat(Yautja yautja, double radius) {
        for (var entity : yautja.level().getEntities(yautja, yautja.getBoundingBox().inflate(radius))) {
            var explosive = isExplosive(entity);

            // ⚠⚠ A NET COUNTS AS A THREAT TOO — his ruling that a yautja caught by another yautja's net "is
            // another thing they would use the evade roll for". Rolling aside physically moves it out of the
            // projectile's path, so the miss is emergent rather than a special case that cancels the capture.
            if (!explosive && !isNet(entity)) {
                continue;
            }

            var toYautja = yautja.position().subtract(entity.position());

            // ⚠⚠ A THROWN GRENADE THAT HAS COME TO REST IS STILL A THREAT, unlike a rocket that has flown past.
            // A rocket only matters while it is closing; a live grenade sitting at the yautja's feet is the most
            // dangerous thing on the field and has almost no velocity at all. So: closing, OR already close.
            // ⚠ The settled-and-still-dangerous rule is for EXPLOSIVES only. A live grenade at the yautja's
            // feet is the most dangerous thing on the field; a net that has stopped moving has already hit
            // something and is gone.
            var closing = entity.getDeltaMovement().dot(toYautja) > 0.0;

            if (closing || (explosive && toYautja.lengthSqr() <= SETTLED_EXPLOSIVE_RADIUS_SQR)) {
                return entity;
            }
        }

        return null;
    }

    /** {@return whether this is a capture net in flight} */
    private static boolean isNet(Entity entity) {
        var key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());

        return "avp_predator".equals(key.getNamespace()) && "net".equals(key.getPath());
    }

    /**
     * {@return whether this entity is something that is about to go off}
     * <p>
     * ⚠ By REGISTRY KEY, not class — avp_human is optional and naming its types would not compile without it. ⚠ Vanilla
     * TNT is included deliberately: a yautja that dodges a grenade but stands in a TNT blast would look broken, and the
     * player has no way of knowing the two are handled by different code.
     */
    private static boolean isExplosive(Entity entity) {
        var key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());

        if ("minecraft".equals(key.getNamespace())) {
            return "tnt".equals(key.getPath()) || "wind_charge".equals(key.getPath());
        }

        if (!"avp_human".equals(key.getNamespace())) {
            return false;
        }

        // rocket, grenade_thrown, nuke — anything whose whole purpose is to detonate.
        return "rocket".equals(key.getPath())
            || "grenade_thrown".equals(key.getPath())
            || "nuke".equals(key.getPath());
    }

    /** {@return the thing that should be rolled away from, or null} */
    public static Entity threatFrom(DamageSource source, LivingEntity fallback) {
        var direct = source.getDirectEntity();

        return direct != null ? direct : source.getEntity() != null ? source.getEntity() : fallback;
    }
}
