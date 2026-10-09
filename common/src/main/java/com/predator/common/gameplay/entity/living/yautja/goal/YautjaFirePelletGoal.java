package com.predator.common.gameplay.entity.living.yautja.goal;

import com.predator.common.gameplay.entity.living.yautja.Yautja;
import com.predator.common.gameplay.entity.living.yautja.ai.YautjaCombat;
import com.predator.common.gameplay.entity.living.yautja.animation.YautjaAttackAnimation;
import com.predator.common.gameplay.entity.living.yautja.util.YautjaPredicates;
import com.predator.common.gameplay.entity.projectile.FirePelletProjectile;
import com.predator.common.registry.init.item.PredatorItems;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.HitResult;

/**
 * Fire pellets — pass 3. [stated] "fire pellet(used to burn down foilage cover and wooden structures/obsticles)".
 * <p>
 * When its target is HIDDEN and the first thing between them is FLAMMABLE — leaves, wood, wool, anything lava could
 * ignite — it fires a pellet at that block, which sets fire on the face it strikes (FirePelletProjectile, unchanged).
 * The cover burns and the prey is exposed.
 * <ul>
 * <li>⚠ Never when mobGriefing is off: fire spreads and destroys blocks, the same rule its mines and block breaking
 * follow.</li>
 * <li>⚠ Same gauntlet rules as the dart, net and whip: not with the battleaxe in hand; needs pellets in the rack, never
 * spends them.</li>
 * <li>A clear line of sight is not this goal's business — it only fires when the prey is behind something that
 * burns.</li>
 * </ul>
 */
public class YautjaFirePelletGoal extends Goal {

    public static int COOLDOWN_TICKS = 20 * 5;

    private static final double MAX_RANGE = 24.0D;

    private static final float PELLET_SPEED = 1.5F;

    private final Yautja yautja;

    private int nextShotTick;

    public YautjaFirePelletGoal(Yautja yautja) {
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

        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return;
        }

        if (yautja.getMainHandItem().getItem() instanceof com.predator.common.gameplay.item.battleaxe.BattleaxeItem) {
            return;
        }

        if (!yautja.hasAmmo(PredatorItems.FIRE_PELLET.get())) {
            return;
        }

        var target = yautja.getTarget();

        if (target == null || !target.isAlive() || !YautjaPredicates.isValidTarget(yautja, target) || yautja.hasLineOfSight(target)) {
            return;
        }

        if (yautja.distanceToSqr(target) > MAX_RANGE * MAX_RANGE) {
            return;
        }

        // What stands between them? Only flammable cover is worth a pellet.
        var eye = yautja.getEyePosition();
        var hit = level.clip(
            new ClipContext(eye, target.getBoundingBox().getCenter(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, yautja)
        );

        if (hit.getType() != HitResult.Type.BLOCK || !level.getBlockState(hit.getBlockPos()).ignitedByLava()) {
            return;
        }

        nextShotTick = yautja.tickCount + COOLDOWN_TICKS;
        YautjaCombat.faceTarget(yautja, target);
        yautja.playAttackAnimation(YautjaAttackAnimation.WRIST_FIRE);

        var pellet = new FirePelletProjectile(level, yautja);
        var aim = hit.getLocation().subtract(eye);

        pellet.setPos(eye.x, eye.y - 0.3D, eye.z);
        pellet.shoot(aim.x, aim.y, aim.z, PELLET_SPEED, 0.0F);
        level.addFreshEntity(pellet);
    }
}
