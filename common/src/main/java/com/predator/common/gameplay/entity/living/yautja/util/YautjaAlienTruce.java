package com.predator.common.gameplay.entity.living.yautja.util;

import com.predator.common.gameplay.entity.living.yautja.Yautja;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Oct 5 - the comics alliance, yautja side. [stated] "like in the comics the yautja and humans will team up to fight
 * the aliens. if the marines and yautja do encounter aliens allow them both to focus on the aliens and not eachother
 * then have a 90s truce period where the two wont attack eachother."
 * <p>
 * A yautja within {@value #CONTACT_RADIUS} blocks of a live xenomorph it would hunt - or fighting one - is in ALIEN
 * CONTACT; from then until {@value #TRUCE_SECONDS} seconds after the last contact no avp_human marine is a valid target
 * for it. That one gate covers everything: every weapon goal, the caster, the grenades and the GOAP sensor all ask
 * {@link YautjaPredicates#isValidTarget}. A Hunter on a hunt keeps its player - players are not part of the truce - but
 * leaves the marines guarding them alone while it lasts. avp_human runs the mirror image, so neither mod needs the
 * other.
 * </p>
 * <p>
 * Cost: one bounding-box query per yautja per second at most, however many times a tick the predicates ask; weak keys,
 * so a removed yautja or a closed world is collected with nothing to clean up. Server thread only.
 * </p>
 */
public final class YautjaAlienTruce {

    /** How close a xenomorph has to be to count as contact, in blocks. */
    public static final double CONTACT_RADIUS = 24.0;

    /** How long the truce lasts after the last contact, in seconds. */
    public static final int TRUCE_SECONDS = 90;

    private static final int TRUCE_TICKS = TRUCE_SECONDS * 20;

    private static final int RECHECK_TICKS = 20;

    /** Per yautja: [last check game time, truce until game time]. */
    private static final Map<Yautja, long[]> STATE = new WeakHashMap<>();

    private YautjaAlienTruce() {
        throw new UnsupportedOperationException();
    }

    /**
     * {@return whether the truce spares {@code other} from this yautja} True for a marine while the truce holds -
     * unless that marine is itself going after a yautja.
     * <p>
     * ⚠ Pre-merge review: each side measures contact from where it stands, so a marine further from the xenos can be
     * outside the truce while this yautja is inside it. A marine honouring the truce never keeps a yautja as its target
     * (avp_human drops it), so one that DOES is not honouring it, and the yautja may answer it.
     * </p>
     *
     * @param yautja the yautja
     * @param other  the candidate
     */
    public static boolean spares(Yautja yautja, Entity other) {
        if (!isMarine(other) || !isActive(yautja)) {
            return false;
        }

        return !(other instanceof net.minecraft.world.entity.Mob mob && mob.getTarget() instanceof Yautja);
    }

    /**
     * {@return whether this yautja is honouring the truce right now}
     *
     * @param yautja the yautja
     */
    public static boolean isActive(Yautja yautja) {
        if (yautja.level().isClientSide) {
            return false;
        }

        var now = yautja.level().getGameTime();
        var state = STATE.computeIfAbsent(yautja, $ -> new long[] { Long.MIN_VALUE / 2, 0L });

        if (now - state[0] >= RECHECK_TICKS || now < state[0]) {
            state[0] = now;

            if (hasContact(yautja)) {
                state[1] = now + TRUCE_TICKS;
            }
        }

        return now < state[1];
    }

    private static boolean hasContact(Yautja yautja) {
        var target = yautja.getTarget();

        if (target != null && isXeno(target)) {
            return true;
        }

        return !yautja.level()
            .getEntitiesOfClass(LivingEntity.class, yautja.getBoundingBox().inflate(CONTACT_RADIUS), YautjaAlienTruce::isContact)
            .isEmpty();
    }

    /**
     * Review pass 1 - contact is a xenomorph nearby, OR a marine nearby that is itself fighting one, and the xenos that
     * count are the SAME on both sides (every live one but an egg or a captive, worthy prey or not). Each side measures
     * from where it stands; without this a marine near the xenos honoured the truce while a yautja a little further out
     * did not, and only the marine would have been holding fire.
     */
    private static boolean isContact(LivingEntity entity) {
        return isXeno(entity)
            || (isMarine(entity)
                && entity instanceof net.minecraft.world.entity.Mob mob
                && mob.getTarget() != null
                && isXeno(mob.getTarget()));
    }

    /**
     * {@return whether {@code entity} is an avp_human marine} By registry id, so avp_human does not have to be
     * installed.
     *
     * @param entity the entity
     */
    public static boolean isMarine(Entity entity) {
        var key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());

        return "avp_human".equals(key.getNamespace()) && key.getPath().startsWith("marine");
    }

    /**
     * A live xenomorph that counts as contact: any avp_alien mob but an egg, and not one held captive with its AI off.
     */
    private static boolean isXeno(LivingEntity entity) {
        if (!entity.isAlive() || (entity instanceof net.minecraft.world.entity.Mob mob && mob.isNoAi())) {
            return false;
        }

        var key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());

        return "avp_alien".equals(key.getNamespace()) && !key.getPath().contains("ovomorph");
    }
}
