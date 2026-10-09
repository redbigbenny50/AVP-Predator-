package com.predator.compatibility.pointblank;

import net.minecraft.world.entity.Entity;

/**
 * Pic's Point Blank's side of the gun balancing on yautja — the same multiplier avp_alien uses on xenomorphs. The
 * budget, the floor and the game rule are shared in {@link com.predator.compatibility.guns.GunDamageParity}.
 * <h2>The multiplier</h2> avp_human's median 20.0 dps against Point Blank's own median: 0.300. Copied from avp_alien's
 * {@code PointBlankBulletResistance} — ⚠ change both together or the same gun hits the two species differently.
 * <h2>🚨 Point Blank has no damage type of its own</h2> Its bullets hurt with vanilla {@code player_attack}, exactly
 * like a sword swing, so the damage source alone cannot tell them apart. {@code MixinHurtingItem_PointBlankHit} marks
 * the entity a Point Blank round is landing on for the duration of that one call, and the yautja's {@code hurt()} —
 * which runs inside it — asks {@link #isPointBlankHit}.
 * <p>
 * ⚠ The same mark keeps a Point Blank round from earning the yautja's close-quarters MELEE bonus: to the damage source
 * it looks exactly like a sword.
 * </p>
 * Its launchers and grenades are explosions, which are never scaled.
 */
public final class PointBlankBulletResistance {

    public static final float DPS_PARITY_MULTIPLIER = 0.300F;

    /** The entity a Point Blank round is landing on right now, on this thread. */
    private static final ThreadLocal<Entity> CURRENT_TARGET = new ThreadLocal<>();

    public static void beginHit(Entity target) {
        CURRENT_TARGET.set(target);
    }

    public static void endHit() {
        CURRENT_TARGET.remove();
    }

    public static boolean isPointBlankHit(Entity target) {
        return target != null && CURRENT_TARGET.get() == target;
    }

    private PointBlankBulletResistance() {}
}
