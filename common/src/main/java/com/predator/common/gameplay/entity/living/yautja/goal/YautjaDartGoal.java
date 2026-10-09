package com.predator.common.gameplay.entity.living.yautja.goal;

import com.predator.common.gameplay.entity.living.yautja.Yautja;
import com.predator.common.gameplay.entity.living.yautja.ai.YautjaSensors;
import com.predator.common.gameplay.entity.living.yautja.util.YautjaPredicates;
import com.predator.common.gameplay.entity.projectile.VeritaniumDartProjectile;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Firing darts from the wrist bracer while swimming.
 * <h2>His spec</h2> "these are what he would shoot at the player when swimming as an attack… it wont have an attack
 * animation, it kinda shoots it while its swimming, so it will fire from the predator at the player in a straight
 * line."
 * <h2>⚠ Why swimming specifically</h2> Not a limitation — a niche. In water the yautja cannot close for melee, cannot
 * use the wrist blades and would not decloak for the caster, so it would otherwise just paddle at you. The bracer is
 * the one weapon that works with both hands busy swimming, which is exactly why it fires here and nowhere else yet.
 * <h2>No goal flags, deliberately</h2> Like the plasma caster, this claims nothing. The swim is GOAP's, and a dart
 * leaving the bracer must not interrupt it — the yautja keeps swimming at you while it shoots.
 */
public class YautjaDartGoal extends Goal {

    /** Ticks between darts. Fast enough to pressure a swimmer, slow enough to be dodged by moving. */
    private static final int COOLDOWN_TICKS = 30;

    /** Blocks. Beyond this it does not bother; the dart flies flat but the yautja still has to see you. */
    private static final double MAX_RANGE = 24.0;

    /** Launch speed. High and flat — the low gravity does the rest. */
    private static final float VELOCITY = 2.2F;

    /** Spread. Small: a blow-dart is aimed, not sprayed. */
    private static final float INACCURACY = 1.0F;

    private final Yautja yautja;

    private int nextShotTick;

    public YautjaDartGoal(Yautja yautja) {
        this.yautja = yautja;
    }

    @Override
    public boolean canUse() {
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return true;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (yautja.level().isClientSide || !yautja.isInWater()) {
            return;
        }

        // 🚨🚨 THESE GUARDS LIVE HERE, NOT IN canUse. canContinueToUse returns true for good, and a goal only asks
        // canUse when it STARTS — which is the tick it spawns. The battleaxe guard used to sit in canUse and so never
        // ran again after that first tick: a yautja that picked the axe up later kept firing its launcher anyway.

        // TWO HANDS ON THE AXE — [stated] "it would stop it using gauntlet weapons so would need to put it away to
        // fire".
        if (yautja.getMainHandItem().getItem() instanceof com.predator.common.gameplay.item.battleaxe.BattleaxeItem) {
            return;
        }

        // NO AMMUNITION, NO SHOT. Never spent — only required. See Yautja.hasAmmo.
        if (!yautja.hasAmmo(com.predator.common.registry.init.item.PredatorItems.VERITANIUM_DART.get())) {
            return;
        }

        if (yautja.tickCount < nextShotTick) {
            return;
        }

        var target = yautja.getTarget();

        if (
            target == null
                || !target.isAlive()
                || !YautjaPredicates.isValidTarget(yautja, target)
                || yautja.distanceToSqr(target) > MAX_RANGE * MAX_RANGE
                // ⚠⚠ NOTHING STOPPED IT FIRING AT CONTACT RANGE. His rule: "the melee is only for when it gets
                // up close, it still prefers the darts in water at distance." Without a minimum the yautja both
                // punched and fired in the same tick at zero range, which is neither behaviour.
                //
                // ⚠ Reads YautjaSensors.meleeReachSqr — the SAME number the melee action uses to decide it is in
                // range. One boundary, so there is no band where it does both and none where it does neither.
                || yautja.distanceToSqr(target) <= YautjaSensors.meleeReachSqr(yautja)
                || !yautja.hasLineOfSight(target)
        ) {
            return;
        }

        nextShotTick = yautja.tickCount + COOLDOWN_TICKS;

        var dart = new VeritaniumDartProjectile(yautja.level(), yautja);

        // Aimed at centre mass rather than the feet, and NOT led: a dart this flat arrives almost immediately, so
        // leading the target would miss in the opposite direction.
        var dx = target.getX() - yautja.getX();
        var dy = target.getY(0.5) - dart.getY();
        var dz = target.getZ() - yautja.getZ();

        dart.shoot(dx, dy, dz, VELOCITY, INACCURACY);
        yautja.level().addFreshEntity(dart);

        yautja.level()
            .playSound(
                null,
                yautja.blockPosition(),
                SoundEvents.ARROW_SHOOT,
                SoundSource.HOSTILE,
                1.0F,
                1.4F
            );
    }
}
