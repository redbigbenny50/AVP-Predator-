package com.predator.common.gameplay.entity.living.yautja.goal;

import com.predator.common.gameplay.entity.living.yautja.Yautja;
import com.predator.common.gameplay.entity.living.yautja.ai.YautjaCombat;
import com.predator.common.gameplay.entity.living.yautja.animation.YautjaAttackAnimation;
import com.predator.common.gameplay.entity.living.yautja.util.YautjaPredicates;
import com.predator.common.gameplay.entity.projectile.PlasmaShurikenProjectile;
import com.predator.common.registry.init.item.PredatorItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A yautja's plasma shuriken — [stated] "the plasma shuriken counts as a ranged weapon even though it fires from the
 * gauntlet."
 * <p>
 * It is the yautja's RANGED WEAPON in the loadout, but a gauntlet shot, so it is never drawn into the hand: the yautja
 * keeps its melee weapon and fires from the wrist at range, like the dart and the net. One in the air at a time — it
 * waits for it to come home (or break) before throwing the next. ⚠ Same gauntlet rules: not with the battleaxe in hand;
 * needs one in the rack, never spends it; a yautja's breaks drop no shards.
 */
public class YautjaPlasmaShurikenGoal extends Goal {

    private static final int COOLDOWN_TICKS = 40;

    /** Beyond melee, within a comfortable homing range. */
    private static final double MIN_RANGE = 6.0D;

    private static final double MAX_RANGE = 28.0D;

    private final Yautja yautja;

    private int nextShotTick;

    private @Nullable UUID inFlight;

    public YautjaPlasmaShurikenGoal(Yautja yautja) {
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
        if (!(yautja.level() instanceof ServerLevel level) || yautja.tickCount < nextShotTick) {
            return;
        }

        // One in the air at a time.
        if (inFlight != null) {
            if (level.getEntity(inFlight) instanceof PlasmaShurikenProjectile shuriken && shuriken.isAlive()) {
                return;
            }

            inFlight = null;
        }

        if (yautja.getMainHandItem().getItem() instanceof com.predator.common.gameplay.item.battleaxe.BattleaxeItem) {
            return;
        }

        if (!yautja.hasAmmo(PredatorItems.PLASMA_SHURIKEN.get())) {
            return;
        }

        var target = yautja.getTarget();

        if (target == null || !target.isAlive() || !YautjaPredicates.isValidTarget(yautja, target) || !yautja.hasLineOfSight(target)) {
            return;
        }

        var distanceSqr = yautja.distanceToSqr(target);

        if (distanceSqr < MIN_RANGE * MIN_RANGE || distanceSqr > MAX_RANGE * MAX_RANGE) {
            return;
        }

        nextShotTick = yautja.tickCount + COOLDOWN_TICKS;
        YautjaCombat.faceTarget(yautja, target);
        yautja.playAttackAnimation(YautjaAttackAnimation.WRIST_FIRE);

        var shuriken = new PlasmaShurikenProjectile(level, yautja);
        var aim = target.getBoundingBox().getCenter().subtract(yautja.getEyePosition());

        shuriken.setPos(yautja.getX(), yautja.getEyeY() - 0.3D, yautja.getZ());
        shuriken.shoot(aim.x, aim.y, aim.z, PlasmaShurikenProjectile.LAUNCH_SPEED, 0.0F);
        shuriken.lockOn(target);
        level.addFreshEntity(shuriken);
        inFlight = shuriken.getUUID();
    }
}
