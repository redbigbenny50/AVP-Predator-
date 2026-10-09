package com.predator.compatibility.guns;

import com.predator.common.gameplay.entity.living.yautja.Yautja;
import com.predator.common.registry.init.PredatorGameRules;
import com.predator.compatibility.pointblank.PointBlankBulletResistance;
import com.predator.compatibility.tacz.TaczBulletResistance;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Brings third-party gun mods into line with this mod suite's own guns against YAUTJA — [stated] "those tacz and vics
 * point blank balance changes that were made to alien. they need to apply to this module as well along with the game
 * rule to disable them." A port of avp_alien's {@code GunDamageParity}: same multipliers, same budget, same floor, same
 * game rule.
 * <h2>What it does</h2>
 * <ul>
 * <li>Each supported gun mod's damage is scaled by its parity multiplier: TACZ {@link TaczBulletResistance} 0.242,
 * Point Blank {@link PointBlankBulletResistance} 0.300 — each derived against avp_human's median sustained dps of
 * 20.</li>
 * <li>Each yautja then has one budget of {@link #DPS_CEILING} damage per second from them, shared by every gun mod, so
 * a player running both cannot double it by alternating weapons. 160 is avp_human's strongest gun, Old Painless.</li>
 * <li>Past the budget a hit still deals {@link #FLOOR} of its scaled damage — never 0. A 0-damage {@code hurt()} skips
 * vanilla's hurt reaction (no red flash, no sound, no knockback) and reads as immunity; a small hit visibly lands.</li>
 * </ul>
 * <h2>Behind the shared {@code gunBalancing} game rule, ON by default</h2> One switch for xenomorphs and yautja alike.
 * Off: every supported mod deals its own unmodified damage. See {@link PredatorGameRules#GUN_BALANCING}.
 * <h2>Where it sits in the yautja's defences</h2> Applied in {@code Yautja.hurt} after the dodge, the hit window and
 * the deflect roll — so only hits that would really land use up the budget — and alongside the hunter and adrenaline
 * reductions. ⚠ The yautja's own two-tick hit window already applies to every gun, avp_human's included, so it keeps
 * the comparison fair rather than adding to it.
 * <h2>⚠ Explosions are never scaled</h2> Launchers and grenades from either mod pass through untouched.
 */
public final class GunDamageParity {

    private static final float UNAFFECTED = 1.0F;

    /** Share of a hit's scaled damage that always lands once the budget is spent — the middle of the stated 10-15%. */
    public static final float FLOOR = 0.125F;

    /** Most third-party gun dps any one yautja takes — avp_human's best gun, Old Painless, sustains 160. */
    public static final float DPS_CEILING = 160F;

    private static final int WINDOW_TICKS = 20;

    /**
     * Per-yautja rolling windows, weakly held so a dead or unloaded yautja takes its entry with it. Server thread only.
     */
    private static final Map<Yautja, DamageWindow> WINDOWS = new WeakHashMap<>();

    /**
     * {@return the multiplier for an incoming hit} — 1 when it is not third-party gunfire, when it is an explosion, on
     * the client, or with {@code gunBalancing} off.
     */
    public static float damageMultiplier(Yautja target, DamageSource damageSource, float incomingDamage) {
        if (damageSource.is(DamageTypeTags.IS_EXPLOSION) || target.level().isClientSide) {
            return UNAFFECTED;
        }

        // ⭐ THE OPT-OUT, read per hit so /gamerule gunBalancing false applies from the next shot.
        if (!target.level().getGameRules().getBoolean(PredatorGameRules.GUN_BALANCING)) {
            return UNAFFECTED;
        }

        var parity = parityFor(target, damageSource);

        if (parity == UNAFFECTED) {
            return UNAFFECTED;
        }

        return parity * sustainedFireThrottle(target, incomingDamage * parity);
    }

    /** {@return whether this hit is a supported third-party gun's} — for rules that must not treat it as melee. */
    public static boolean isThirdPartyGunfire(Yautja target, DamageSource damageSource) {
        return parityFor(target, damageSource) != UNAFFECTED;
    }

    /**
     * TACZ by its damage-type tag; Point Blank, which uses plain {@code player_attack}, by the hit it is delivering
     * right now (MixinHurtingItem_PointBlankHit).
     */
    private static float parityFor(Yautja target, DamageSource damageSource) {
        if (TaczBulletResistance.isTaczBullet(damageSource)) {
            return TaczBulletResistance.DPS_PARITY_MULTIPLIER;
        }

        if (PointBlankBulletResistance.isPointBlankHit(target)) {
            return PointBlankBulletResistance.DPS_PARITY_MULTIPLIER;
        }

        return UNAFFECTED;
    }

    /** {@return the share of {@code scaledDamage} that lands, in FLOOR..1} */
    private static float sustainedFireThrottle(Yautja target, float scaledDamage) {
        if (scaledDamage <= 0F) {
            // TACZ's second hurt() per bullet carries 0 when the round has no armour-ignore share.
            return 1F;
        }

        var now = target.tickCount;
        var window = WINDOWS.computeIfAbsent(target, $ -> new DamageWindow());

        // ⚠ A plain started flag — avp_alien's original window began at Integer.MIN_VALUE and its reset test
        // overflowed, so the "per second" budget was really per lifetime. Not repeated here.
        if (!window.started || now - window.startTick >= WINDOW_TICKS || now < window.startTick) {
            window.started = true;
            window.startTick = now;
            window.damage = 0F;
        }

        var remaining = DPS_CEILING * (WINDOW_TICKS / 20F) - window.damage;

        if (remaining <= 0F) {
            return FLOOR;
        }

        window.damage += Math.min(scaledDamage, remaining);

        if (scaledDamage <= remaining) {
            return 1F;
        }

        return Math.max(remaining / scaledDamage, FLOOR);
    }

    private static final class DamageWindow {

        private boolean started;

        private int startTick;

        private float damage;
    }

    private GunDamageParity() {}
}
